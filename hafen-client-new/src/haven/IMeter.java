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

import haven.sloth.gui.MovableWidget;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MooNWide IMeter: extends MovableWidget, lock tab, scale/width, tip text, minimalistic style.
 */
public class IMeter extends MovableWidget {
    private static final Pattern hppat = Pattern.compile("Health: (\\d+)/(\\d+)/(\\d+)/?(\\d+)?");
    private static final Pattern stampat = Pattern.compile("(?i)Stamina:\\s*(\\d+)");
    private static final Pattern stampatRu = Pattern.compile("Стамина\\s*[:：]\\s*(\\d+)");
    private static final Pattern energypat = Pattern.compile("(?i)Energy:\\s*(\\d+)");
    private static final Pattern energypatRu = Pattern.compile("Энергия\\s*[:：]\\s*(\\d+)");

    public static final Coord off = UI.scale(22, 7);
    public static final Coord fsz = UI.scale(101, 24);
    public static final Coord msz = UI.scale(75, 10);
    public static final Coord miniOff = UI.scale(0, 5);
    /** Compact in-body utility button instead of a wide side tab. */
    public static final int METER_BTN_W = UI.scale(12);
    public static final int METER_BTN_GAP = UI.scale(4);

    Indir<Resource> bg;
    public List<Meter> meters;
    protected float scale = 1f;
    protected int width = 0;
    Text meterinfo = null;
    String drawText = null;
    String lastTip = null;
    boolean dirtyText = false;
    private int persistIdx = -1;
    private boolean typedRestoreDone = false;
    private MoonDropdownMenu headerMenu = null;

    @RName("im")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Indir<Resource> bg = ui.sess.getresv(args[0]);
	    List<Meter> meters = decmeters(args, 1);
	    String name = "meter-" + String.valueOf(args[0]);
	    try {
		if (bg != null) {
		    Resource r = bg.get();
		    if (r != null) {
			String rn = r.name;
			if (rn != null && !rn.isEmpty())
			    name = rn.replace('/', '-');
			else
			    name = r.basename();
		    }
		}
	    } catch (Exception ignore) {
	    }
	    return new IMeter(bg, meters, "meter-" + name);
	}
    }

    public static class Meter {
	public Color c;
	public int a;

	public Meter(Color c, int a) {
	    this.c = c;
	    this.a = a;
	}

	public Meter(double a, Color c) {
	    this.a = (int) (a * 100);
	    this.c = c;
	}
    }

    private static double av(Object arg) {
	if (arg instanceof Integer)
	    return ((Integer) arg).doubleValue() * 0.01;
	return Utils.dv(arg);
    }

    public static List<Meter> decmeters(Object[] args, int s) {
	if (args.length == s)
	    return Collections.emptyList();
	ArrayList<Meter> buf = new ArrayList<>();
	if (args[s] instanceof Number) {
	    for (int a = s; a < args.length; a += 2)
		buf.add(new Meter(av(args[a]), (Color) args[a + 1]));
	} else {
	    for (int a = s; a < args.length; a += 2)
		buf.add(new Meter(av(args[a + 1]), (Color) args[a]));
	}
	buf.trimToSize();
	return buf;
    }

    protected int contentW() {
	return sz.x - METER_BTN_W - METER_BTN_GAP;
    }

    private IMeter(Indir<Resource> bg, List<Meter> meters, String name) {
	super(contentsz(name), name);
	this.loadPosition = false; // IMeter uses explicit typed persistence below.
	this.bg = bg;
	this.meters = meters;
	this.scale = Utils.getpreff("scale-" + name, 1f);
	this.width = Utils.getprefi("width-" + name, 0);
    }

    private static Coord contentsz(String name) {
	int w = DefSettings.minimalisticmeter ? Utils.getprefi("width-" + name, 0) : 0;
	float scale = Utils.getpreff("scale-" + name, 1f);
	Coord base = fsz.add(w, 0).mul(scale);
	return new Coord(base.x + METER_BTN_W + METER_BTN_GAP, base.y);
    }

    /** Dedicated position key used by {@link haven.sloth.gui.MovableWidget}. */
    public String posPrefKey() {
	return (key == null) ? null : ("wpos-" + key);
    }

    public void setPersistIndex(int idx) {
	this.persistIdx = idx;
    }

    public int persistIndex() {
	return persistIdx;
    }

    public String typePosPrefKey() {
	return null;
    }

    private String meterType() {
	try {
	    Resource r = (bg == null) ? null : bg.get();
	    if(r == null)
		return null;
	    String b = r.basename();
	    if(b == null || b.isEmpty())
		return null;
	    b = b.toLowerCase(Locale.ROOT);
	    String full = (r.name != null) ? r.name.toLowerCase(Locale.ROOT) : b;
	    if(b.equals("hp") || b.contains("health") || full.contains("/hp"))
		return "hp";
	    if(b.equals("stam") || b.contains("stam") || full.contains("/stam"))
		return "stam";
	    if(b.equals("nrj") || b.contains("nrj") || b.contains("nrg") || b.contains("energy")
		|| full.contains("/nrj") || full.contains("energy"))
		return "nrj";
	} catch(Loading l) {
	    return null;
	} catch(Exception ignored) {
	    return null;
	}
	return null;
    }

    protected String typedPosKey() {
	String t = meterType();
	return (t == null) ? null : ("wpos-meter-" + t);
    }

    public IMeter(Indir<Resource> bg, String name) {
	this(bg, Collections.emptyList(), name);
    }

    protected Tex tex() {
	if(bg == null)
	    return(null);
	try {
	    return(bg.get().flayer(Resource.imgc).tex());
	} catch(Loading e) {
	    return(null);
	} catch(Resource.LoadFailedException e) {
	    return(null);
	} catch(Exception e) {
	    return(null);
	}
    }

    protected void drawBg(GOut g) {
	boolean mini = DefSettings.minimalisticmeter;
	int cw = contentW();
	if (!mini) {
	    g.chcolor(18, 12, 30, 255);
	    g.frect(off.mul(scale), msz.mul(scale));
	} else {
	    int a = Math.min(DefSettings.imetertransparency, 255);
	    g.chcolor(18, 12, 30, a);
	    Coord o = miniOff.mul(scale);
	    g.frect(o, new Coord(cw, sz.y).sub(o.mul(2)));
	}
	g.chcolor();
    }

    protected void drawMeters(GOut g) {
	boolean mini = DefSettings.minimalisticmeter;
	int cw = contentW();
	for (Meter m : meters) {
	    int w = msz.x;
	    w = (w * m.a) / 100;
	    if (!mini) {
		g.chcolor(m.c);
		g.frect(off.mul(scale), new Coord((int)(w * scale), (int)(msz.y * scale)));
	    } else {
		g.chcolor(new Color(m.c.getRed(), m.c.getGreen(), m.c.getBlue(), DefSettings.imetertransparency).darker());
		Coord o = miniOff.mul(scale);
		int barw = (cw * m.a) / 100;
		g.frect(o, new Coord(barw, sz.y).sub(o.mul(2)));
	    }
	}
	g.chcolor();
    }

    @Override
    public void draw(GOut g) {
	boolean mini = DefSettings.minimalisticmeter;
	int cw = contentW();
	drawBg(g);
	drawMeters(g);
	try {
	    Tex t = tex();
	    if (t != null && !mini)
		g.image(t, Coord.z, new Coord((int)(fsz.x * scale), (int)(fsz.y * scale)));
	    else if (mini) {
	    }
	} catch (Loading l) {
	}
	if (DefSettings.showmetertext && meterinfo != null)
	    g.aimage(meterinfo.tex(), new Coord(cw / 2 + (mini ? 0 : UI.scale(10)), sz.y / 2), 0.5, 0.5);
	drawMenuButton(g);
	super.draw(g);
    }

    @Override
    public boolean mousedown(Widget.MouseDownEvent ev) {
	if(menuHit(ev.c)) {
	    toggleHeaderMenu();
	    return true;
	}
	return super.mousedown(ev);
    }

    protected boolean altMoveHit(Coord mc, int button) {
	return ui.modflags() == 0 && button == 1 && DefSettings.simpledraging;
    }

    @Override
    public boolean mousewheel(Widget.MouseWheelEvent ev) {
	if (ui.modflags() == (UI.MOD_CTRL | UI.MOD_META)) {
	    float newscale = Math.max(Math.min(scale - 0.1f * ev.a, 5f), 1f);
	    if (newscale != scale) {
		Utils.setpreff("scale-" + key, scale = newscale);
		Coord center = c.add(sz.div(2));
		resize(contentsz(key));
		move(center.sub(sz.div(2)));
		dirtyText = true;
	    }
	    return true;
	} else if (ui.modflags() == UI.MOD_CTRL && DefSettings.minimalisticmeter) {
	    int newwidth = Math.max(Math.min(width - ev.a, 200), 0);
	    if (newwidth != width) {
		Utils.setprefi("width-" + key, width = newwidth);
		Coord center = c.add(sz.div(2));
		resize(contentsz(key));
		move(center.sub(sz.div(2)));
		dirtyText = true;
	    }
	    return true;
	}
	return super.mousewheel(ev);
    }

    public void set(List<Meter> meters) {
	this.meters = meters;
    }

    public void set(double a, Color c) {
	set(Collections.singletonList(new Meter(a, c)));
    }

    @Override
    public void uimsg(String msg, Object... args) {
	if (msg == "set") {
	    if (args.length == 1)
		set(av(args[0]), meters.isEmpty() ? Color.WHITE : meters.get(0).c);
	    else
		set(decmeters(args, 0));
	    if (MoonConfig.autoDrink && MoonConfig.autoDrinkServerHook && "stam".equals(meterType())) {
		int pct = meters.isEmpty() ? -1 : meters.get(0).a;
		GameUI gui = getparent(GameUI.class);
		if (gui != null)
		    MoonAutoDrink.onServerStaminaSet(gui, pct);
	    }
	} else if (msg.equals("tip") && args.length > 0) {
	    String tt = (String) args[0];
	    this.lastTip = tt;
	    Matcher matcher = hppat.matcher(tt);
	    String meterinfo = null;
	    if (matcher.find()) {
		if (matcher.group(4) != null)
		    meterinfo = matcher.group(1);
		else
		    meterinfo = tt.split(" ")[1];
		if (meterinfo != null && meterinfo.contains("/")) {
		    String[] hps = meterinfo.split("/");
		    meterinfo = hps[0] + "/" + hps[hps.length - 1];
		}
	    } else {
		matcher = stampat.matcher(tt);
		if (matcher.find())
		    meterinfo = matcher.group(1) + "%";
		else {
		    matcher = stampatRu.matcher(tt);
		    if (matcher.find())
			meterinfo = matcher.group(1) + "%";
		    else {
			matcher = energypat.matcher(tt);
			if (matcher.find())
			    meterinfo = matcher.group(1) + "%";
			else {
			    matcher = energypatRu.matcher(tt);
			    if (matcher.find())
				meterinfo = matcher.group(1) + "%";
			    else
				meterinfo = tt;
			}
		    }
		}
	    }
	    if (meterinfo == null)
		meterinfo = tt;
	    updatemeterinfo(meterinfo);
	} else {
	    super.uimsg(msg, args);
	}
    }

    protected void updatemeterinfo(String str) {
	if (str == null || str.isEmpty()) return;
	if (drawText == null || !drawText.equals(str)) {
	    drawText = str;
	    dirtyText = true;
	}
    }

    @Override
    public void tick(double dt) {
	if (drawText != null && dirtyText) {
	    dirtyText = false;
	    meterinfo = Text.std.render(drawText, Color.WHITE);
	}
	if(!typedRestoreDone) {
	    Coord saved = null;
	    String tk = typedPosKey();
	    if(tk != null)
		saved = Utils.getprefc(tk, null);
	    if(saved == null && persistIdx >= 0)
		saved = Utils.getprefc(String.format("wpos-meter-%d", persistIdx), null);
	    if(saved != null) {
		move(saved);
		keepInParent();
	    }
	    typedRestoreDone = true;
	}
	super.tick(dt);
    }

    @Override
    public boolean mouseup(Widget.MouseUpEvent ev) {
	boolean moved = moving();
	boolean ret = super.mouseup(ev);
	if(moved) {
	    String k = typedPosKey();
	    if(k != null) {
		Utils.setprefc(k, c);
		try { Utils.prefs().flush(); } catch(Exception ignored) {}
	    }
	    if(persistIdx >= 0) {
		Utils.setprefc(String.format("wpos-meter-%d", persistIdx), c);
		try { Utils.prefs().flush(); } catch(Exception ignored) {}
	    }
	}
	return ret;
    }

    private void toggleHeaderMenu() {
	if(headerMenu != null) {
	    closeHeaderMenu();
	    return;
	}
	headerMenu = MoonDropdownMenu.popup(this, menuAnchor(), buildMenuEntries(), () -> headerMenu = null);
    }

    private Coord menuAnchor() {
	Coord p = menuBtnPos().add(METER_BTN_W / 2, METER_BTN_W / 2);
	return MoonDropdownMenu.toRoot(this, p);
    }

    private void closeHeaderMenu() {
	if(headerMenu != null) {
	    MoonDropdownMenu menu = headerMenu;
	    headerMenu = null;
	    menu.destroy();
	}
    }

    private List<MoonDropdownMenu.Entry> buildMenuEntries() {
	List<MoonDropdownMenu.Entry> entries = new ArrayList<>();
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("shortcut.reset_pos"),
	    () -> {
		if(parent != null)
		    c = parent.sz.sub(sz).div(2);
		savePosition();
	    }));
	entries.add(MoonDropdownMenu.Entry.separator());
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr(locked() ? "ui.menu.unlock" : "ui.menu.lock"),
	    this::toggleLock));
	return entries;
    }

    private Coord menuBtnPos() {
	return Coord.of(sz.x - METER_BTN_W, (sz.y - METER_BTN_W) / 2);
    }

    private boolean menuHit(Coord c) {
	return c.isect(menuBtnPos(), Coord.of(METER_BTN_W, METER_BTN_W));
    }

    private void drawMenuButton(GOut g) {
	Coord p = menuBtnPos();
	g.chcolor(headerMenu != null ? MoonUiTheme.MENU_HOVER : new Color(52, 44, 74, 220));
	g.frect(p, Coord.of(METER_BTN_W, METER_BTN_W));
	g.chcolor(MoonUiTheme.BORDER);
	g.rect(p, Coord.of(METER_BTN_W, METER_BTN_W).sub(1, 1));
	g.chcolor(locked() ? MoonUiTheme.LOCKED : MoonUiTheme.ACCENT);
	int cx = p.x + (METER_BTN_W / 2);
	int cy = p.y + (METER_BTN_W / 2);
	g.line(Coord.of(cx - UI.scale(3), cy - UI.scale(2)), Coord.of(cx + UI.scale(3), cy - UI.scale(2)), 1);
	g.line(Coord.of(cx - UI.scale(3), cy), Coord.of(cx + UI.scale(3), cy), 1);
	g.line(Coord.of(cx - UI.scale(3), cy + UI.scale(2)), Coord.of(cx + UI.scale(3), cy + UI.scale(2)), 1);
	g.chcolor();
    }
}
