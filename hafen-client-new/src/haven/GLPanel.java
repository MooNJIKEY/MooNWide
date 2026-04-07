/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import haven.render.*;
import haven.render.gl.*;
import java.awt.Cursor;
import java.awt.Toolkit;
import haven.JOGLPanel.SyncMode;

public interface GLPanel extends UIPanel, UI.Context {
    public GLEnvironment env();
    public Area shape();
    public Pipe basestate();
    public void glswap(GL gl);

    /** Add a session UI without destroying the current one; switches input to the new UI. */
    default UI addParallelSession(UI.Runner fun) { return(null); }
    /** Show an existing session (must be registered as a parallel session). */
    default void setActiveSession(UI lui) {}
    default java.util.List<UI> sessionUIs() { return(java.util.Collections.emptyList()); }
    default void endParallelSession(UI lui) {}
    /** Register the main game UI so session switching can find it. */
    default void registerSessionUi(UI u) {}

    public static class Loop implements Console.Directory {
	public static boolean gldebug = false;
	public final GLPanel p;
	public final CPUProfile uprof = new CPUProfile(300), rprof = new CPUProfile(300);
	public final GPUProfile gprof = new GPUProfile(300);
	protected boolean bgmode = false;
	protected int fps, framelag;
	protected double avgframems = 0.0, p95framems = 0.0;
	protected int jankcount = 0;
	protected volatile int frameno;
	protected double uidle = 0.0, ridle = 0.0;
	protected long lastrcycle = 0, ridletime = 0;
	protected UI lockedui, ui;
	private final Dispatcher ed;
	private final Object uilock = new Object();
	private final java.util.concurrent.CopyOnWriteArrayList<UI> multisessionUIs = new java.util.concurrent.CopyOnWriteArrayList<>();
	private volatile UI nextActiveUi;
	private final double[] framems = new double[128];
	private final double[] framemsScratch = new double[framems.length];
	private int framemsz = 0, framemp = 0;
	private int framemetricCooldown = 0;
	private volatile double lastSwapMs = 0.0;

	public UI addParallelSession(UI.Runner fun) {
	    UI newui = new UI(p, panelCoordSize(p), fun);
	    newui.env = p.env();
	    if(p.getParent() instanceof Console.Directory)
		newui.cons.add((Console.Directory)p.getParent());
	    if(p instanceof Console.Directory)
		newui.cons.add((Console.Directory)p);
	    newui.cons.add(this);
	    synchronized(uilock) {
		if(multisessionUIs.isEmpty() && (this.ui != null) && !multisessionUIs.contains(this.ui))
		    multisessionUIs.add(this.ui);
		multisessionUIs.add(newui);
		UI prev = this.ui;
		ui = newui;
		ui.root.guprof = uprof;
		ui.root.grprof = rprof;
		ui.root.ggprof = gprof;
		while((this.lockedui != null) && (this.lockedui == prev)) {
		    try {
			uilock.wait();
		    } catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			break;
		    }
		}
	    }
	    return(newui);
	}

	public void setActiveSession(UI lui) {
	    synchronized(uilock) {
		if(!multisessionUIs.contains(lui))
		    return;
		nextActiveUi = lui;
	    }
	}

	public java.util.List<UI> sessionUIs() {
	    return(new java.util.ArrayList<>(multisessionUIs));
	}

	/** Remove a parallel session after its RemoteUI loop ends; switches to another session if needed. */
	public void registerSessionUi(UI u) {
	    if(u == null)
		return;
	    synchronized(uilock) {
		if(!multisessionUIs.contains(u))
		    multisessionUIs.add(u);
	    }
	}

	public void endParallelSession(UI lui) {
	    synchronized(uilock) {
		multisessionUIs.remove(lui);
		if(ui == lui) {
		    if(!multisessionUIs.isEmpty()) {
			nextActiveUi = multisessionUIs.get(0);
		    } else {
			UI shell = new UI(p, panelCoordSize(p), null);
			shell.env = p.env();
			if(p.getParent() instanceof Console.Directory)
			    shell.cons.add((Console.Directory)p.getParent());
			if(p instanceof Console.Directory)
			    shell.cons.add((Console.Directory)p);
			shell.cons.add(this);
			shell.root.guprof = uprof;
			shell.root.grprof = rprof;
			shell.root.ggprof = gprof;
			ui = shell;
		    }
		}
	    }
	    if(lui != null) {
		synchronized(lui) {
		    lui.destroy();
		}
	    }
	}

	/** Drawable size for UI root and {@link GOut}; max of GL shape and AWT peer so neither letterboxes alone. */
	private static Coord panelCoordSize(GLPanel p) {
	    java.awt.Component c = (java.awt.Component)p;
	    int aw = Math.max(1, c.getWidth());
	    int ah = Math.max(1, c.getHeight());
	    Area sh = p.shape();
	    if((sh != null) && sh.positive()) {
		Coord ss = sh.sz();
		return(new Coord(Math.max(aw, ss.x), Math.max(ah, ss.y)));
	    }
	    return(new Coord(aw, ah));
	}

	public Loop(GLPanel p) {
	    this.p = p;
	    ed = new Dispatcher();
	    ed.register((java.awt.Component)p);
	}

	private double framedur() {
	    GSettings gp = this.ui.gprefs;
	    double hz = gp.hz.val, bghz = gp.bghz.val;
	    if(bgmode) {
		if(bghz != Double.POSITIVE_INFINITY)
		    return(1.0 / bghz);
	    }
	    if(hz == Double.POSITIVE_INFINITY)
		return(0.0);
	    return(1.0 / hz);
	}

	private void recordFrameMetrics(double ms) {
	    framems[framemp] = ms;
	    framemp = (framemp + 1) % framems.length;
	    if(framemsz < framems.length)
		framemsz++;
	    if((framemsz < framems.length) || (framemetricCooldown-- <= 0))
		framemetricCooldown = 7;
	    else
		return;
	    double sum = 0.0;
	    int jank = 0;
	    for(int i = 0; i < framemsz; i++) {
		double v = framems[i];
		framemsScratch[i] = v;
		sum += v;
		if(v > 24.0)
		    jank++;
	    }
	    Arrays.sort(framemsScratch, 0, framemsz);
	    avgframems = (framemsz > 0) ? (sum / framemsz) : 0.0;
	    int idx = Math.max(0, Math.min(framemsz - 1, (int)Math.ceil(framemsz * 0.95) - 1));
	    p95framems = (framemsz > 0) ? framemsScratch[idx] : 0.0;
	    jankcount = jank;
	}

	private class BufferSwap implements BGL.Request {
	    final int frameno;

	    BufferSwap(int frameno) {
		this.frameno = frameno;
	    }

	    public void run(GL gl) {
		long start = System.nanoTime();
		p.glswap(gl);
		long spent = System.nanoTime() - start;
		lastSwapMs = spent / 1_000_000.0;
		ridletime += spent;
		framelag = Loop.this.frameno - frameno;
	    }
	}

	private class GLFinish implements BGL.Request {
	    public void run(GL gl) {
		long start = System.nanoTime();
		gl.glFinish();
		/* Should this count towards idle time? Who knows. */
		ridletime += System.nanoTime() - start;
	    }
	}

	private class FrameCycle implements BGL.Request {
	    public void run(GL gl) {
		long now = System.nanoTime();
		if(lastrcycle != 0) {
		    double fridle = (double)ridletime / (double)(now - lastrcycle);
		    ridle = (ridle * 0.95) + (fridle * 0.05);
		}
		lastrcycle = now;
		ridletime = 0;
	    }
	}

	public static class ProfileCycle implements BGL.Request {
	    final CPUProfile prof;
	    ProfileCycle prev;
	    CPUProfile.Frame frame;
	    Profile.Part curp;

	    ProfileCycle(CPUProfile prof, ProfileCycle prev, GLRender out) {
		this.prof = prof;
		this.prev = prev;
		out.submit(this);
	    }

	    public void run(GL gl) {
		if(prev != null) {
		    if(prev.frame != null) {
			/* The reason frame would be null is if the
			 * environment has become invalid and the previous
			 * cycle never ran. */
			prev.frame.fin();
		    }
		    prev = null;
		}
		frame = prof.new Frame();
	    }
	}

	public static class ProfilePart implements BGL.Request {
	    final ProfileCycle prof;
	    final String label;

	    ProfilePart(ProfileCycle prof, String label) {
		this.prof = prof;
		this.label = label;
	    }

	    public void run(GL gl) {
		if((prof != null) && (prof.frame != null))
		    prof.frame.part(label);
	    }
	}

	private Object prevtooltip = null;
	private Indir<Tex> prevtooltex = null;
	private Disposable freetooltex = null;
	private void drawtooltip(UI ui, GOut g) {
	    Object tooltip;
	    synchronized(ui) {
		tooltip = ui.tooltip(ui.mc);
	    }
	    Indir<Tex> tt = null;
	    if(Utils.eq(tooltip, prevtooltip)) {
		tt = prevtooltex;
	    } else {
		if(freetooltex != null) {
		    freetooltex.dispose();
		    freetooltex = null;
		}
		prevtooltip = null;
		prevtooltex = null;
		Disposable free = null;
		if(tooltip != null) {
		    if(tooltip instanceof Text) {
			Tex t = ((Text)tooltip).tex();
			tt = () -> t;
		    } else if(tooltip instanceof Tex) {
			Tex t = (Tex)tooltip;
			tt = () -> t;
		    } else if(tooltip instanceof Indir<?>) {
			@SuppressWarnings("unchecked")
			    Indir<Tex> c = (Indir<Tex>)tooltip;
			tt = c;
		    } else if(tooltip instanceof String) {
			if(((String)tooltip).length() > 0) {
			    MoonPerfOverlay.countTooltipRender();
			    Tex r = new TexI(Text.render((String)tooltip).img, false);
			    tt = () -> r;
			    free = r;
			}
		    }
		}
		prevtooltip = tooltip;
		prevtooltex = tt;
		freetooltex = free;
	    }
	    Tex tex = (tt == null) ? null : tt.get();
	    if(tex != null) {
		Coord sz = tex.sz();
		Coord pos = ui.mc.sub(sz).sub(curshotspot);
		if(pos.x < 0)
		    pos.x = 0;
		if(pos.y < 0)
		    pos.y = 0;
		Coord br = pos.add(sz);
		Coord m = UI.scale(2, 2);
		g.chcolor(244, 247, 21, 192);
		g.rect2(pos.sub(m).sub(1, 1), br.add(m).add(1, 1));
		g.chcolor(35, 35, 35, 192);
		g.frect2(pos.sub(m), br.add(m));
		g.chcolor();
		g.image(tex, pos);
	    }
	    ui.lasttip = tooltip;
	}

	private static String defaultcurs() {
	    if(Toolkit.getDefaultToolkit().getMaximumCursorColors() >= 256)
		return("awt");
	    return("tex");
	}

	private String cursmode = defaultcurs();
	private Object lastcursor = null;
	private String lastcursmode = null;
	private Coord curshotspot = Coord.z;
	private static String effectivecursmode(String prefmode) {
	    MainFrame frame = MainFrame.getInstance();
	    if((frame != null) && frame.isExclusiveFullscreen())
		return("tex");
	    return(prefmode);
	}

	private static boolean usemooncursor(Resource res) {
	    return MoonUiTheme.useMoonCursor(res);
	}

	private static Coord texcurshotspot(Resource res) {
	    return(usemooncursor(res) ? MoonUiTheme.moonCursorHotspot() : UI.scale(res.flayer(Resource.negc).cc));
	}

	private static Cursor awtcursor(Resource res) {
	    if(usemooncursor(res))
		return(UIPanel.makeawtcurs(MoonUiTheme.moonCursorImage(), MoonUiTheme.moonCursorHotspot()));
	    return(UIPanel.makeawtcurs(res.flayer(Resource.imgc).img, res.flayer(Resource.negc).cc));
	}

	private void drawtexcursor(Resource res, UI ui, GOut g) {
	    Coord dc = ui.mc.sub(curshotspot);
	    if(usemooncursor(res))
		g.image(MoonUiTheme.moonCursorTex(), dc);
	    else
		g.image(res.flayer(Resource.imgc), dc);
	}

	private void drawcursor(UI ui, GOut g) {
	    Object curs;
	    synchronized(ui) {
		curs = ui.getcurs(ui.mc);
	    }
	    String mode = effectivecursmode(cursmode);
	    if(!Utils.eq(mode, lastcursmode)) {
		lastcursor = null;
		if("tex".equals(mode))
		    p.setCursor(emptycurs);
		else
		    p.setCursor(null);
	    }
	    if(curs instanceof Resource) {
		Resource res = (Resource)curs;
		if("awt".equals(mode)) {
		    if(curs != lastcursor) {
			try {
			    curshotspot = usemooncursor(res) ? MoonUiTheme.moonCursorHotspot() : res.flayer(Resource.negc).cc;
			    p.setCursor(awtcursor(res));
			} catch(Exception e) {
			    cursmode = "tex";
			    mode = "tex";
			    lastcursor = null;
			}
		    }
		}
		if("tex".equals(mode)) {
		    if(!(lastcursor instanceof Resource))
			p.setCursor(emptycurs);
		    curshotspot = texcurshotspot(res);
		    drawtexcursor(res, ui, g);
		}
	    } else if(curs instanceof UI.Cursor) {
		if(curs != lastcursor)
		    p.setCursor(UIPanel.getsyscurs((UI.Cursor)curs));
	    } else {
		if(curs != lastcursor)
		    Warning.warn("unexpected cursor specification: %s", curs);
	    }
	    lastcursor = curs;
	    lastcursmode = mode;
	}

	private long prevfree = 0, framealloc = 0;
	@SuppressWarnings("deprecation")
	private void drawstats(UI ui, GOut g, GLRender buf) {
	    int y = g.sz().y - UI.scale(190), dy = FastText.h;
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "FPS: %d (%d%%, %d%% idle, latency %d)", fps, (int)(uidle * 100.0), (int)(ridle * 100.0), framelag);
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "Frame: avg %.1f ms, p95 %.1f ms, jank %d", avgframems, p95framems, jankcount);
	    Runtime rt = Runtime.getRuntime();
	    long free = rt.freeMemory(), total = rt.totalMemory();
	    if(free < prevfree)
		framealloc = ((prevfree - free) + (framealloc * 19)) / 20;
	    prevfree = free;
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "Mem: %,011d/%,011d/%,011d/%,011d (%,d)", free, total - free, total, rt.maxMemory(), framealloc);
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "State slots: %d", State.Slot.numslots());
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "GL progs: %d", buf.env.numprogs());
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "V-Mem: %s", buf.env.memstats());
	    MapView map = ui.root.findchild(MapView.class);
	    if((map != null) && (map.back != null)) {
		FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "Camera: %s", map.camstats());
		FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "Mapview: %s", map.stats());
		// FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "Click: Map: %s, Obj: %s", map.clmaplist.stats(), map.clobjlist.stats());
	    }
	    FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "Async: L %s, D %s", ui.loader.stats(), Defer.gstats());
	    int rqd = Resource.local().qdepth() + Resource.remote().qdepth();
	    if(rqd > 0)
		FastText.aprintf(g, new Coord(10, y -= dy), 0, 1, "RQ depth: %d (%d)", rqd, Resource.local().numloaded() + Resource.remote().numloaded());
	    synchronized(Debug.framestats) {
		for(Object line : Debug.framestats)
		    FastText.aprint(g, new Coord(10, y -= dy), 0, 1, String.valueOf(line));
	    }
	}

	private StreamOut streamout = null;

	private void display(UI ui, GLRender buf) {
	    Pipe wnd = p.basestate();
	    buf.clear(wnd, FragColor.fragcol, FColor.BLACK);
	    Pipe state = wnd.copy();
	    state.prep(new FrameInfo());
	    /* Must match p.shape() / Ortho2D viewport; p.getSize() can disagree on JOGL (letterboxing). */
	    GOut g = new GOut(buf, state, panelCoordSize(p));
	    synchronized(ui) {
		ui.draw(g);
	    }
	    if(dbtext.get())
		drawstats(ui, g, buf);
	    drawtooltip(ui, g);
	    drawcursor(ui, g);
	    if(StreamOut.path.get() != null) {
		if(streamout == null) {
		    try {
			streamout = new StreamOut(p.shape().sz(), StreamOut.path.get());
		    } catch(java.io.IOException e) {
			throw(new RuntimeException(e));
		    }
		}
		streamout.accept(buf, state);
	    }
	}

	private boolean hascause(Throwable t, Class<? extends Throwable> cl) {
	    for(Throwable c = t; c != null; c = c.getCause()) {
		if(cl.isInstance(c))
		    return(true);
	    }
	    return(false);
	}

	private boolean recovergfx(UI ui, Throwable t) {
	    boolean fboerr = hascause(t, GLFrameBuffer.FormatException.class);
	    for(Throwable c = t; !fboerr && (c != null); c = c.getCause()) {
		String msg = c.getMessage();
		if((msg != null) && msg.contains("FBO failed completeness test"))
		    fboerr = true;
	    }
	    if(!fboerr || (ui == null) || (ui.gprefs == null))
		return(false);
	    try {
		GSettings safe = ui.gprefs;
		boolean changed = false;
		if(safe.lshadow.val) {
		    safe = safe.update(null, safe.lshadow, false);
		    changed = true;
		}
		if(safe.rscale.val != 1.0f) {
		    safe = safe.update(null, safe.rscale, 1.0f);
		    changed = true;
		}
		if(safe.lightmode.val != GSettings.LightMode.SIMPLE) {
		    safe = safe.update(null, safe.lightmode, GSettings.LightMode.SIMPLE);
		    changed = true;
		}
		if(safe.maxlights.val != 0) {
		    safe = safe.update(null, safe.maxlights, 0);
		    changed = true;
		}
		if(safe.syncmode.val != SyncMode.FRAME) {
		    safe = safe.update(null, safe.syncmode, SyncMode.FRAME);
		    changed = true;
		}
		if(!changed)
		    return(false);
		ui.setgprefs(safe);
		safe.save();
		ui.error("Video settings were reset to safe mode after a framebuffer error. Restart the client if the world stays black.");
		Warning.warn("Recovered from framebuffer error by switching MooNWide to safe graphics settings");
		return(true);
	    } catch(Throwable ignored) {
		return(false);
	    }
	}

	public void run() throws InterruptedException {
	    GLRender buf = null;
	    try {
		double then = Utils.rtime();
		double[] frames = new double[128], waited = new double[frames.length];
		Fence prevframe = null;
		ProfileCycle rprofc = null;
		int framep = 0;
		while(true) {
		    UI curui = null;
		    try {
			double cycleStart = Utils.rtime();
			double fwaited = 0;
			double dispatchMs = 0.0, worldMs = 0.0, uiTickMs = 0.0, drawMs = 0.0;
			GLEnvironment env = p.env();
			buf = env.render();
			UI ui;
			synchronized(uilock) {
			    if(nextActiveUi != null) {
				this.ui = nextActiveUi;
				nextActiveUi = null;
			    }
			    this.lockedui = ui = this.ui;
			    curui = ui;
			    uilock.notifyAll();
			}
			Debug.cycle(ui.modflags());
			GSettings prefs = ui.gprefs;
			SyncMode syncmode = prefs.syncmode.val;
			CPUProfile.Current curf = profile.get() ?  CPUProfile.set(uprof.new Frame()) : null;
			GPUProfile.Frame curgf = profilegpu.get() ? gprof.new Frame(buf) : null;
			rprofc = profile.get() ? new ProfileCycle(rprof, rprofc, buf) : null;
			BufferBGL.Profile frameprof = false ? new BufferBGL.Profile() : null;
			if(frameprof != null) buf.submit(frameprof.start);
			buf.submit(new ProfilePart(rprofc, "tick"));
			if(curgf != null) curgf.part(buf, "tick");
			Fence curframe = new Fence();
			if(syncmode == SyncMode.FRAME)
			    buf.submit(curframe);

			boolean tickwait = (syncmode == SyncMode.FRAME) || (syncmode == SyncMode.TICK);
			if(!tickwait) {
			    CPUProfile.phase(curf, "dwait");
			    if(prevframe != null) {
				double now = Utils.rtime();
				prevframe.waitfor();
				prevframe = null;
				fwaited += Utils.rtime() - now;
			    }
			}

			int cfno = frameno++;
			synchronized(ui) {
			    long phaseStart = System.nanoTime();
			    CPUProfile.phase(curf, "dsp");
			    ed.dispatch(ui);
			    ui.mousehover(ui.mc);
			    dispatchMs = (System.nanoTime() - phaseStart) / 1_000_000.0;

			    phaseStart = System.nanoTime();
			    CPUProfile.phase(curf, "stick");
			    if(ui.sess != null) {
				ui.sess.glob.ctick();
				ui.sess.glob.gtick(buf);
			    }
			    worldMs = (System.nanoTime() - phaseStart) / 1_000_000.0;
			    phaseStart = System.nanoTime();
			    CPUProfile.phase(curf, "tick");
			    ui.tick();
			    ui.gtick(buf);
			    Coord rootsz = panelCoordSize(p);
			    if(!ui.root.sz.equals(rootsz))
				ui.root.resize(rootsz);
			    uiTickMs = (System.nanoTime() - phaseStart) / 1_000_000.0;
			    buf.submit(new ProfilePart(rprofc, "draw"));
			    if(curgf != null) curgf.part(buf, "draw");
			}

			if(tickwait) {
			    CPUProfile.phase(curf, "dwait");
			    if(prevframe != null) {
				double now = Utils.rtime();
				prevframe.waitfor();
				prevframe = null;
				fwaited += Utils.rtime() - now;
			    }
			}

			CPUProfile.phase(curf, "draw");
			long drawStart = System.nanoTime();
			display(ui, buf);
			drawMs = (System.nanoTime() - drawStart) / 1_000_000.0;
			CPUProfile.phase(curf, "aux");
			if(curgf != null) curgf.part(buf, "swap");
			buf.submit(new ProfilePart(rprofc, "swap"));
			buf.submit(new BufferSwap(cfno));
			if(curgf != null) curgf.fin(buf);
			if(syncmode == SyncMode.FINISH) {
			    buf.submit(new ProfilePart(rprofc, "finish"));
			    buf.submit(new GLFinish());
			}
			if(syncmode != SyncMode.FRAME)
			    buf.submit(curframe);
			buf.submit(new ProfilePart(rprofc, "wait"));
			buf.submit(new FrameCycle());
			if(frameprof != null) {
			    buf.submit(frameprof.stop);
			    buf.submit(frameprof.dump(Utils.path("frameprof")));
			}
			env.submit(buf);
			buf = null;

			CPUProfile.phase(curf, "wait");
			double now = Utils.rtime();
			double fd = framedur();
			if(then + fd > now) {
			    then += fd;
			    long nanos = (long)((then - now) * 1e9);
			    Thread.sleep(nanos / 1000000, (int)(nanos % 1000000));
			} else {
			    then = now;
			}
			fwaited += Utils.rtime() - now;
			MoonPerfOverlay.updatePhaseStats(dispatchMs, worldMs, uiTickMs, MoonPerfOverlay.worldBuildMs(), drawMs, lastSwapMs, fwaited * 1000.0);
			frames[framep] = now;
			waited[framep] = fwaited;
			{
			    double twait = 0;
			    int i = 0, ckf = framep;
			    for(; i < frames.length - 1; i++) {
				ckf = (ckf - 1 + frames.length) % frames.length;
				twait += waited[ckf];
				if(now - frames[ckf] > 1)
				    break;
			    }
			    if(now > frames[ckf]) {
				fps = (int)Math.round((i + 1) / (now - frames[ckf]));
				uidle = twait / (now - frames[ckf]);
			    }
			}
			recordFrameMetrics((Utils.rtime() - cycleStart) * 1000.0);
			MoonPerfOverlay.updateRenderStats(fps, framelag, avgframems, p95framems, jankcount);
			framep = (framep + 1) % frames.length;

			CPUProfile.end(curf);
			prevframe = curframe;
		    } catch(Throwable t) {
			MoonCrashLog.record(Thread.currentThread(), t);
			if(recovergfx(curui, t)) {
			    if(buf != null) {
				buf.dispose();
				buf = null;
			    }
			    prevframe = null;
			    then = Utils.rtime();
			    continue;
			}
			throw(new RuntimeException(t));
		    }
		}
	    } finally {
		synchronized(uilock) {
		    lockedui = null;
		    uilock.notifyAll();
		}
		if(buf != null)
		    buf.dispose();
	    }
	}

	public UI newui(UI.Runner fun) {
	    UI newui = new UI(p, panelCoordSize(p), fun);
	    newui.env = p.env();
	    if(p.getParent() instanceof Console.Directory)
		newui.cons.add((Console.Directory)p.getParent());
	    if(p instanceof Console.Directory)
		newui.cons.add((Console.Directory)p);
	    newui.cons.add(this);
	    UI prevui;
	    synchronized(uilock) {
		prevui = this.ui;
		if(fun == null) {
		    java.util.HashSet<UI> all = new java.util.HashSet<>(multisessionUIs);
		    if(this.ui != null)
			all.add(this.ui);
		    multisessionUIs.clear();
		    for(UI u : all) {
			if(u != null) {
			    synchronized(u) {
				u.destroy();
			    }
			}
		    }
		    prevui = null;
		}
		ui = newui;
		ui.root.guprof = uprof;
		ui.root.grprof = rprof;
		ui.root.ggprof = gprof;
		while((this.lockedui != null) && (this.lockedui == prevui)) {
		    try {
			uilock.wait();
		    } catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			break;
		    }
		}
	    }
	    if(prevui != null) {
		synchronized(uilock) {
		    multisessionUIs.remove(prevui);
		}
		synchronized(prevui) {
		    prevui.destroy();
		}
	    }
	    synchronized(uilock) {
		if((fun != null) && !multisessionUIs.isEmpty())
		    multisessionUIs.add(newui);
	    }
	    return(newui);
	}

	private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
	{
	    cmdmap.put("gldebug", (cons, args) -> {
		    gldebug = Utils.parsebool(args[1]);
		});
	    cmdmap.put("cursor", new Console.Command() {
		    public void run(Console cons, String[] args) {
			cursmode = args[1].intern();
			lastcursor = null;
			p.setCursor(null);
		    }
		});
	}
	public Map<String, Console.Command> findcmds() {
	    return(cmdmap);
	}

	{
	    /* glcrash is set after a BGL/GL failure so the *next* run could enable tracing.
	     * Forcing gldebug here spammed the console (DebugGL3 + huge BGL dumps) and
	     * made parallel sessions painful; clear the flag and let users opt in via `gldebug 1`. */
	    if(Utils.getprefb("glcrash", false)) {
		Warning.warn("previous GL error flag was cleared (if issues persist, run console: gldebug 1)");
		Utils.setprefb("glcrash", false);
	    }
	}

	/* XXX: This should be in UIPanel, but Java is dumb and needlessly forbids it. */
	static {
	    Console.setscmd("stats", new Console.Command() {
		    public void run(Console cons, String[] args) {
			dbtext.set(Utils.parsebool(args[1]));
		    }
		});
	    Console.setscmd("profile", new Console.Command() {
		    public void run(Console cons, String[] args) {
			if(args[1].equals("none") || args[1].equals("off")) {
			    profile.set(false);
			    profilegpu.set(false);
			} else if(args[1].equals("cpu")) {
			    profile.set(true);
			} else if(args[1].equals("gpu")) {
			    profilegpu.set(true);
			} else if(args[1].equals("all")) {
			    profile.set(true);
			    profilegpu.set(true);
			}
		    }
		});
	    Console.setscmd("moonperf", new Console.Command() {
		    public void run(Console cons, String[] args) throws Exception {
			if(args.length < 2) {
			    cons.out.write(MoonPerfOverlay.dump() + "\n");
			} else if(args[1].equals("dump")) {
			    cons.out.write(MoonPerfOverlay.dump() + "\n");
			} else {
			    MoonPerfOverlay.setDebugDetails(Utils.parsebool(args[1]));
			    cons.out.write("moonperf=" + MoonPerfOverlay.debugDetails() + "\n");
			}
		    }
		});
	}
    }
}
