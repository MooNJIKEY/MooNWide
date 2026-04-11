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
import java.util.function.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.*;
import haven.MapFile.Marker;
import haven.MapFile.PMarker;
import haven.MapFile.SMarker;
import haven.MiniMap.*;
import haven.BuddyWnd.GroupSelector;
import static haven.MCache.tilesz;
import static haven.MCache.cmaps;
import static haven.Utils.eq;
import javax.swing.JFileChooser;
import javax.swing.filechooser.*;

public class MapWnd extends Window implements Console.Directory {
    public static final Resource markcurs = Resource.local().loadwait("gfx/hud/curs/flag");
    private static final int MAP_PAD = UI.scale(12);
    private static final int TOOLBOX_W = UI.scale(244);
    private static final int TOOLGAP = UI.scale(6);
    private static final int MAPBAR_BTN = UI.scale(24);
    private static final int MAPBAR_GAP = UI.scale(4);
    private static final int MAPBAR_PAD = UI.scale(6);
    private static final int ICON_FOLLOW = 1;
    private static final int ICON_MARK = 2;
    private static final int ICON_HIDE_MARK = 3;
    private static final int ICON_DOCK = 4;
    private static final int ICON_PROVINCES = 5;
    public final MapFile file;
    public final MiniMap view;
    public final MapView mv;
    public final Toolbox tool;
    public final Collection<String> overlays = new java.util.concurrent.CopyOnWriteArraySet<>();
    public MarkerConfig markcfg = MarkerConfig.showall, cmarkers = null;
    private final Locator player;
    private final Widget toolbar;
    private final Frame viewf;
    private boolean compactmode;
    private GroupSelector colsel;
    private Button mremove;
    private Predicate<Marker> mflt = pmarkers;
    private Comparator<ListMarker> mcmp = namecmp;
    private List<ListMarker> markers = Collections.emptyList();
    private int markerseq = -1;
    private boolean domark = false;
    private int olalpha = 64;
    private final Collection<Runnable> deferred = new LinkedList<>();

    private final static Predicate<Marker> pmarkers = (m -> m instanceof PMarker);
    private final static Predicate<Marker> smarkers = (m -> m instanceof SMarker);
    private final static Comparator<ListMarker> namecmp = ((a, b) -> a.mark.nm.compareTo(b.mark.nm));
    private final static Comparator<ListMarker> typecmp = Comparator.comparing((ListMarker lm) -> lm.type).thenComparing(namecmp);

    public static final KeyBinding kb_home = KeyBinding.get("mapwnd/home", KeyMatch.forcode(KeyEvent.VK_HOME, 0));
    public static final KeyBinding kb_mark = KeyBinding.get("mapwnd/mark", KeyMatch.nil);
    public static final KeyBinding kb_hmark = KeyBinding.get("mapwnd/hmark", KeyMatch.forchar('M', KeyMatch.C));
    public static final KeyBinding kb_compact = KeyBinding.get("mapwnd/compact", KeyMatch.forchar('A', KeyMatch.M));
    public static final KeyBinding kb_prov = KeyBinding.get("mapwnd/prov", KeyMatch.nil);

    private class MapDeco extends Window.MinimalDeco {
	@Override
	protected List<MoonDropdownMenu.Entry> buildHeaderMenuEntries() {
	    List<MoonDropdownMenu.Entry> entries = new ArrayList<>();
	    entries.add(MoonDropdownMenu.Entry.action(() -> compact() ? "Show dock" : "Hide dock", () -> {
		compact(!compact());
		Utils.setprefb("compact-map", compact());
	    }));
	    entries.add(MoonDropdownMenu.Entry.action(() -> domark ? "Cancel marker mode" : "Add marker", () -> domark = !domark));
	    entries.add(MoonDropdownMenu.Entry.action(() ->
		Utils.eq(markcfg, MarkerConfig.hideall) ? "Show markers" : "Hide markers", () -> {
		    if(Utils.eq(markcfg, MarkerConfig.hideall))
			markcfg = MarkerConfig.showall;
		    else if(Utils.eq(markcfg, MarkerConfig.showall) && (cmarkers != null))
			markcfg = cmarkers;
		    else
			markcfg = MarkerConfig.hideall;
		}));
	    entries.add(MoonDropdownMenu.Entry.action(() ->
		overlays.contains("realm") ? "Hide provinces" : "Show provinces",
		() -> toggleol("realm", !overlays.contains("realm"))));
	    entries.add(MoonDropdownMenu.Entry.action("Follow", MapWnd.this::recenter));
	    entries.add(MoonDropdownMenu.Entry.separator());
	    entries.addAll(super.buildHeaderMenuEntries());
	    return entries;
	}
    }

    public MapWnd(MapFile file, MapView mv, Coord sz, String title) {
	super(sz, title, true);
	chdeco(new MapDeco());
	this.file = file;
	this.mv = mv;
	this.player = new MapLocator(mv);
	/* Resolve compact preference before toolbar buttons query compact(). */
	this.compactmode = Utils.getprefb("compact-map", false);
	viewf = add(new ViewFrame());
	view = viewf.add(new View(file));
	recenter();
	toolbar = add(new MapToolbar());
	toolbar.z(10);
	toolbar.raise();
	tool = add(new Toolbox());
	/* Old builds called setprefb("compact-map", true) every launch — reset once so map matches wide layout. */
	if(!Utils.getprefb("moon-map-compact-migrated", false)) {
	    Utils.setprefb("compact-map", false);
	    Utils.setprefb("moon-map-compact-migrated", true);
	    this.compactmode = false;
	}
	if(compactmode) {
	    tool.hide();
	    delfocusable(tool);
	} else {
	    newfocusable(tool);
	}
	resize(sz);
    }

    private void layoutToolbar() {
	if(toolbar != null) {
	    toolbar.pack();
	    toolbar.c = viewf.c.add(MAPBAR_PAD, Math.max(MAPBAR_PAD, viewf.sz.y - toolbar.sz.y - MAPBAR_PAD));
	}
    }

    @Override
    public Coord contentsz() {
	layoutToolbar();
	return(super.contentsz());
    }

    public void toggleol(String tag, boolean a) {
	if(a)
	    overlays.add(tag);
	else
	    overlays.remove(tag);
    }

    private class ViewFrame extends Frame {
	private static final Coord RESIZE_PAD = UI.scale(8, 8);
	private static final Coord RESIZE_BOX = UI.scale(18, 18);
	Coord sc = Coord.z;
	private boolean resizehover = false;

	ViewFrame() {
	    super(Coord.z, true);
	}

	@Override
	public Coord inner() {
	    return(sz);
	}

	@Override
	public Coord xlate(Coord c, boolean in) {
	    return(c);
	}

	public void resize(Coord sz) {
	    super.resize(sz);
	    sc = sz.sub(RESIZE_BOX);
	}

	public void draw(GOut g) {
	    super.draw(g);
	    drawResizeGrip(g);
	}

	public void drawframe(GOut g) {
	}

	private void drawResizeGrip(GOut g) {
	    Coord br = sz.sub(1, 1);
	    int step = UI.scale(4);
	    int len = UI.scale(10);
	    boolean active = resizehover || (drag != null);
	    int alpha = active ? 255 : 170;
	    int shadow = active ? 120 : 72;

	    g.chcolor(12, 8, 24, shadow);
	    for(int i = 0; i < 3; i++) {
		int off = UI.scale(3) + (i * step);
		g.line(Coord.of(br.x - len - off + UI.scale(1), br.y - off + UI.scale(1)),
		    Coord.of(br.x - off + UI.scale(1), br.y - len - off + UI.scale(1)), 2);
	    }

	    g.chcolor(active ? 244 : 214, active ? 224 : 196, 255, alpha);
	    for(int i = 0; i < 3; i++) {
		int off = UI.scale(3) + (i * step);
		g.line(Coord.of(br.x - len - off, br.y - off),
		    Coord.of(br.x - off, br.y - len - off), 1);
	    }

	    g.chcolor(188, 146, 246, active ? 224 : 132);
	    g.line(Coord.of(br.x - UI.scale(14), br.y), br, 1);
	    g.line(Coord.of(br.x, br.y - UI.scale(14)), br, 1);
	    g.chcolor();
	}

	private boolean resizehit(Coord c) {
	    Coord plate = sc.sub(RESIZE_PAD);
	    Coord psz = RESIZE_BOX.add(RESIZE_PAD.x * 2, RESIZE_PAD.y * 2);
	    return(c.isect(plate, psz));
	}

	@Override
	public Object tooltip(Coord c, Widget prev) {
	    if(resizehit(c))
		return("Drag corner to resize");
	    return(super.tooltip(c, prev));
	}

	@Override
	public boolean mousewheel(MouseWheelEvent ev) {
	    MiniMap v = MapWnd.this.view;
	    if(v != null && v.visible())
		return(v.mousewheel(ev.derive(ev.c.sub(v.c))));
	    return(super.mousewheel(ev));
	}

	private UI.Grab drag;
	private Coord dragc;
	public boolean mousedown(MouseDownEvent ev) {
	    Coord c = ev.c;
	    if((ev.b == 1) && resizehit(c)) {
		if(drag == null) {
		    drag = ui.grabmouse(this);
		    dragc = csz().sub(parentpos(MapWnd.this, c));
		    resizehover = true;
		    return(true);
		}
	    }
	    /* XXX: Shift-clicks that do not drag should be propagated to the map. */
	    if((ev.b == 1) && (checkhit(c) || ui.modshift)) {
		MapWnd.this.drag(parentpos(MapWnd.this, c));
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	public void mousemove(MouseMoveEvent ev) {
	    super.mousemove(ev);
	    resizehover = resizehit(ev.c);
	    if(drag != null) {
		Coord nsz = parentpos(MapWnd.this, ev.c).add(dragc);
		nsz.x = Math.max(nsz.x, UI.scale(260));
		nsz.y = Math.max(nsz.y, UI.scale(220));
		MapWnd.this.resize(nsz);
	    }
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if((ev.b == 1) && (drag != null)) {
		drag.remove();
		drag = null;
		resizehover = resizehit(ev.c);
		return(true);
	    }
	    return(super.mouseup(ev));
	}
    }

    private static final int btnw = UI.scale(95);
    public class Toolbox extends Widget {
	public final MarkerList list;
	private final Frame listf;
	private final Button pmbtn, smbtn, nobtn, tobtn, mebtn, mibtn;
	private TextEntry namesel;

	private Toolbox() {
	    super(Coord.of(TOOLBOX_W, UI.scale(280)));
	    listf = add(new Frame(Coord.of(innerw(), UI.scale(200)), false), UI.scale(8), UI.scale(74));
	    list = listf.add(new MarkerList(Coord.of(listf.inner().x, 0)), 0, 0);
	    pmbtn = add(new Button(colw(), "Placed", false) {
		    public void click() {
			mflt = pmarkers;
			markerseq = -1;
		    }
		});
	    smbtn = add(new Button(colw(), "Natural", false) {
		    public void click() {
			mflt = smarkers;
			markerseq = -1;
		    }
		});
	    nobtn = add(new Button(colw(), "By name", false) {
		    public void click() {
			mcmp = namecmp;
			markerseq = -1;
		    }
		});
	    tobtn = add(new Button(colw(), "By type", false) {
		    public void click() {
			mcmp = typecmp;
			markerseq = -1;
		    }
		});
	    mebtn = add(new Button(colw(), "Export...", false) {
		    public void click() {
			exportmap();
		    }
		});
	    mibtn = add(new Button(colw(), "Import...", false) {
		    public void click() {
			importmap();
		    }
		});
	}

	private int innerw() {
	    return sz.x - UI.scale(16);
	}

	private int colw() {
	    return (innerw() - TOOLGAP) / 2;
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.propagate(this))
		return(true);
	    return(checkhit(ev.c));
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(ev.propagate(this))
		return(true);
	    return(checkhit(ev.c));
	}

	public void resize(int h) {
	    super.resize(new Coord(sz.x, h));
	    int x = UI.scale(8);
	    int y = UI.scale(74);
	    int bottom = UI.scale(8);
	    int actionsH = (Button.hs * 3) + (TOOLGAP * 2);
	    int editorH = (namesel != null) ? (Button.hs + TOOLGAP + Button.hs + ((colsel != null) ? (TOOLGAP + UI.scale(20)) : 0)) : 0;
	    int listH = Math.max(UI.scale(120), sz.y - y - bottom - actionsH - ((namesel != null) ? (editorH + TOOLGAP) : 0));
	    listf.resize(innerw(), listH);
	    listf.c = new Coord(x, y);
	    list.resize(listf.inner());
	    int rightx = x + innerw() - colw();
	    int by = sz.y - bottom - mebtn.sz.y;
	    mebtn.c = new Coord(x, by);
	    mibtn.c = new Coord(rightx, by);
	    by -= TOOLGAP + nobtn.sz.y;
	    nobtn.c = new Coord(x, by);
	    tobtn.c = new Coord(rightx, by);
	    by -= TOOLGAP + pmbtn.sz.y;
	    pmbtn.c = new Coord(x, by);
	    smbtn.c = new Coord(rightx, by);
	    if(namesel != null) {
		namesel.c = listf.c.add(0, listf.sz.y + TOOLGAP);
		mremove.c = namesel.c.add(0, namesel.sz.y + TOOLGAP);
		if(colsel != null) {
		    colsel.c = mremove.c.add(0, mremove.sz.y + TOOLGAP);
		}
	    }
	}

	@Override
	public void draw(GOut g) {
	    g.chcolor(12, 10, 22, 192);
	    g.frect(Coord.z, sz);
	    g.chcolor(176, 134, 235, 136);
	    g.rect(Coord.z, sz.sub(1, 1));
	    g.chcolor(236, 224, 255, 255);
	    g.text("Atlas Dock", Coord.of(UI.scale(10), UI.scale(10)));
	    g.chcolor(198, 176, 232, 200);
	    String mode = (mflt == pmarkers) ? "Placed" : "Natural";
	    String sort = (mcmp == namecmp) ? "Name" : "Type";
	    g.text(String.format("Markers: %d  |  %s  |  %s", markers.size(), mode, sort), Coord.of(UI.scale(10), UI.scale(30)));
	    g.chcolor(180, 158, 220, 180);
	    g.text(domark ? "Mode: add marker" : "Mode: inspect / travel", Coord.of(UI.scale(10), UI.scale(48)));
	    g.chcolor();
	    super.draw(g);
	}
    }

    private class MapIconButton extends SIWidget {
	private final BufferedImage icon;
	private final Supplier<Boolean> active;
	private final Runnable action;
	private boolean hover = false;
	private boolean down = false;
	private boolean lastActive = false;

	MapIconButton(int kind, Supplier<Boolean> active, Runnable action) {
	    super(Coord.of(MAPBAR_BTN, MAPBAR_BTN));
	    this.icon = iconImage(kind);
	    this.active = active;
	    this.action = action;
	    this.lastActive = active.get();
	    setcanfocus(true);
	}

	private BufferedImage iconImage(int kind) {
	    int pad = UI.scale(5);
	    int iw = Math.max(1, sz.x - (pad * 2));
	    int ih = Math.max(1, sz.y - (pad * 2));
	    BufferedImage out = TexI.mkbuf(Coord.of(iw, ih));
	    java.awt.Graphics2D g = out.createGraphics();
	    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
	    g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
	    g.setColor(new Color(234, 238, 247, 240));
	    g.setStroke(new java.awt.BasicStroke(Math.max(1.4f, UI.scale(1) * 1.45f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
	    switch(kind) {
	    case ICON_FOLLOW:
		drawFollowIcon(g, iw, ih);
		break;
	    case ICON_MARK:
		drawMarkIcon(g, iw, ih, false);
		break;
	    case ICON_HIDE_MARK:
		drawMarkIcon(g, iw, ih, true);
		break;
	    case ICON_DOCK:
		drawDockIcon(g, iw, ih);
		break;
	    case ICON_PROVINCES:
		drawProvinceIcon(g, iw, ih);
		break;
	    default:
		drawFollowIcon(g, iw, ih);
		break;
	    }
	    g.dispose();
	    return out;
	}

	private void drawFollowIcon(java.awt.Graphics2D g, int w, int h) {
	    int cx = w / 2;
	    int cy = h / 2;
	    int rOuter = Math.max(UI.scale(4), Math.min(w, h) / 2 - UI.scale(2));
	    int rInner = Math.max(UI.scale(1), rOuter / 2);
	    g.drawOval(cx - rOuter, cy - rOuter, rOuter * 2, rOuter * 2);
	    g.drawOval(cx - rInner, cy - rInner, rInner * 2, rInner * 2);
	    g.drawLine(cx, UI.scale(1), cx, cy - rOuter + UI.scale(1));
	    g.drawLine(cx, cy + rOuter - UI.scale(1), cx, h - UI.scale(2));
	    g.drawLine(UI.scale(1), cy, cx - rOuter + UI.scale(1), cy);
	    g.drawLine(cx + rOuter - UI.scale(1), cy, w - UI.scale(2), cy);
	    g.fillOval(cx - UI.scale(1), cy - UI.scale(1), UI.scale(3), UI.scale(3));
	}

	private void drawMarkIcon(java.awt.Graphics2D g, int w, int h, boolean hidden) {
	    int cx = w / 2;
	    int top = UI.scale(1);
	    int pinW = Math.max(UI.scale(6), w - UI.scale(8));
	    int pinH = Math.max(UI.scale(8), h - UI.scale(5));
	    java.awt.geom.Path2D.Float pin = new java.awt.geom.Path2D.Float();
	    pin.moveTo(cx, top + pinH);
	    pin.curveTo(cx - pinW / 2.0f, top + pinH - UI.scale(4), cx - pinW / 2.0f, top + UI.scale(3), cx, top + UI.scale(1));
	    pin.curveTo(cx + pinW / 2.0f, top + UI.scale(3), cx + pinW / 2.0f, top + pinH - UI.scale(4), cx, top + pinH);
	    g.draw(pin);
	    int eye = Math.max(UI.scale(3), pinW / 3);
	    g.drawOval(cx - eye / 2, top + UI.scale(4), eye, eye);
	    if(hidden) {
		g.setColor(new Color(255, 192, 192, 235));
		g.drawLine(UI.scale(2), h - UI.scale(2), w - UI.scale(2), UI.scale(2));
	    }
	}

	private void drawDockIcon(java.awt.Graphics2D g, int w, int h) {
	    int left = UI.scale(1);
	    int top = UI.scale(2);
	    int gap = UI.scale(2);
	    int panelW = Math.max(UI.scale(4), (w - gap - UI.scale(2)) / 2);
	    int panelH = Math.max(UI.scale(8), h - UI.scale(4));
	    g.drawRoundRect(left, top, panelW, panelH, UI.scale(3), UI.scale(3));
	    g.drawRoundRect(left + panelW + gap, top + UI.scale(2), panelW, panelH - UI.scale(4), UI.scale(3), UI.scale(3));
	    g.drawLine(left + panelW + (gap / 2), top + UI.scale(2), left + panelW + (gap / 2), top + panelH - UI.scale(2));
	}

	private void drawProvinceIcon(java.awt.Graphics2D g, int w, int h) {
	    int left = UI.scale(1);
	    int top = UI.scale(2);
	    int right = w - UI.scale(2);
	    int bottom = h - UI.scale(2);
	    java.awt.geom.Path2D.Float shape = new java.awt.geom.Path2D.Float();
	    shape.moveTo(left + UI.scale(4), top);
	    shape.lineTo(right - UI.scale(4), top);
	    shape.lineTo(right, top + (h / 2));
	    shape.lineTo(right - UI.scale(4), bottom);
	    shape.lineTo(left + UI.scale(4), bottom);
	    shape.lineTo(left, top + (h / 2));
	    shape.closePath();
	    g.draw(shape);
	    g.drawLine(w / 2, top + UI.scale(1), w / 2, bottom - UI.scale(1));
	    g.drawLine(left + UI.scale(3), h / 2, right - UI.scale(3), h / 2);
	    g.drawLine(left + UI.scale(4), top + UI.scale(3), right - UI.scale(4), bottom - UI.scale(3));
	}

	protected void draw(BufferedImage buf) {
	    MoonUiTheme.paintFantasyButton(buf, sz, down || active.get(), hover, false, icon);
	}

	public void tick(double dt) {
	    super.tick(dt);
	    boolean now = active.get();
	    if(now != lastActive) {
		lastActive = now;
		redraw();
	    }
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b != 1)
		return false;
	    down = true;
	    redraw();
	    return true;
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(ev.b != 1)
		return false;
	    boolean hit = ev.c.isect(Coord.z, sz);
	    down = false;
	    redraw();
	    if(hit && action != null)
		action.run();
	    return true;
	}

	public void mousemove(MouseMoveEvent ev) {
	    boolean nh = ev.c.isect(Coord.z, sz);
	    if(nh != hover) {
		hover = nh;
		redraw();
	    }
	}

	public boolean gkeytype(GlobKeyEvent ev) {
	    if(action != null)
		action.run();
	    return true;
	}
    }

    private class MapToolbar extends Widget {
	private final MapIconButton home;
	private final MapIconButton mark;
	private final MapIconButton hmark;
	private final MapIconButton dock;
	private final MapIconButton prov;

	MapToolbar() {
	    super(Coord.z);
	    home = add(new MapIconButton(ICON_FOLLOW, () -> false, MapWnd.this::recenter), Coord.z);
	    home.settip("Follow").setgkey(kb_home);
	    mark = add(new MapIconButton(ICON_MARK, () -> domark, () -> domark = !domark), Coord.z);
	    mark.settip("Add marker").setgkey(kb_mark);
	    hmark = add(new MapIconButton(ICON_HIDE_MARK, () -> Utils.eq(markcfg, MarkerConfig.hideall), () -> {
		if(Utils.eq(markcfg, MarkerConfig.hideall))
		    markcfg = MarkerConfig.showall;
		else if(Utils.eq(markcfg, MarkerConfig.showall) && (cmarkers != null))
		    markcfg = cmarkers;
		else
		    markcfg = MarkerConfig.hideall;
	    }), Coord.z);
	    hmark.settip("Hide markers").setgkey(kb_hmark);
	    dock = add(new MapIconButton(ICON_DOCK, MapWnd.this::compact, () -> {
		compact(!compact());
		Utils.setprefb("compact-map", compact());
	    }), Coord.z);
	    dock.settip("Compact mode").setgkey(kb_compact);
	    prov = add(new MapIconButton(ICON_PROVINCES, () -> overlays.contains("realm"), () -> toggleol("realm", !overlays.contains("realm"))), Coord.z);
	    prov.settip("Display provinces").setgkey(kb_prov);
	    pack();
	}

	@Override
	public void pack() {
	    int x = MAPBAR_PAD;
	    MapIconButton[] items = {home, mark, hmark, dock, prov};
	    for(MapIconButton item : items) {
		item.c = Coord.of(x, MAPBAR_PAD);
		x += item.sz.x + MAPBAR_GAP;
	    }
	    resize(Coord.of(x - MAPBAR_GAP + MAPBAR_PAD, MAPBAR_BTN + (MAPBAR_PAD * 2)));
	}

	@Override
	public void draw(GOut g) {
	    g.chcolor(12, 10, 22, 176);
	    g.frect(Coord.z, sz);
	    g.chcolor(176, 134, 235, 110);
	    g.rect(Coord.z, sz.sub(1, 1));
	    g.chcolor();
	    super.draw(g);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.propagate(this))
		return true;
	    return checkhit(ev.c);
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(ev.propagate(this))
		return true;
	    return checkhit(ev.c);
	}
    }

    private class View extends MiniMap implements CursorQuery.Handler {
	View(MapFile file) {
	    super(file);
	}

	public void drawgrid(GOut g, Coord ul, DisplayGrid disp) {
	    super.drawgrid(g, ul, disp);
	    for(String tag : overlays) {
		try {
		    Tex img = disp.olimg(tag);
		    if(img != null) {
			g.chcolor(255, 255, 255, olalpha);
			g.image(img, ul, UI.scale(img.sz()));
		    }
		} catch(Loading l) {
		}
	    }
	    g.chcolor();
	}

	public boolean filter(DisplayMarker mark) {
	    return(markcfg.filter(mark.m));
	}

	public boolean clickmarker(DisplayMarker mark, Location loc, int button, boolean press) {
	    GameUI gui = getparent(GameUI.class);
	    if(TeleportManager.handleArmedMapPick(gui, this, loc, button, press))
		return(true);
	    if(button == 1) {
		if(!compact() && !press && !domark) {
		    focus(mark.m);
		    return(true);
		}
	    } else if(mark.m instanceof SMarker) {
		Gob gob = MarkerID.find(ui.sess.glob.oc, mark.m);
		if(gob != null)
		    mvclick(mv, null, loc, gob, button);
	    }
	    return(false);
	}

	public boolean clickicon(DisplayIcon icon, Location loc, int button, boolean press) {
	    GameUI gui = getparent(GameUI.class);
	    if(TeleportManager.handleArmedMapPick(gui, this, loc, button, press))
		return(true);
	    if(!press && !domark) {
		mvclick(mv, null, loc, icon.gob, button);
		return(true);
	    }
	    return(false);
	}

	public boolean clickloc(Location loc, int button, boolean press) {
	    GameUI gui = getparent(GameUI.class);
	    if(TeleportManager.handleArmedMapPick(gui, this, loc, button, press))
		return(true);
	    if(domark && (button == 1) && !press) {
		Marker nm = new PMarker(loc.seg.id, loc.tc, "New marker", BuddyWnd.gc[new Random().nextInt(BuddyWnd.gc.length)]);
		file.add(nm);
		focus(nm);
		domark = false;
		return(true);
	    }
	    if(!press && (sessloc != null) && (loc.seg == sessloc.seg)) {
		mvclick(mv, null, loc, null, button);
		return(true);
	    }
	    return(false);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(domark && (ev.b == 3)) {
		domark = false;
		return(true);
	    }
	    super.mousedown(ev);
	    return(true);
	}

	public void draw(GOut g) {
	    g.chcolor(18, 12, 30, 255);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    super.draw(g);
	}

	public boolean getcurs(CursorQuery ev) {
	    if(domark)
		return(ev.set(markcurs));
	    return(false);
	}
    }

    public void tick(double dt) {
	layoutToolbar();
	super.tick(dt);
	synchronized(deferred) {
	    for(Iterator<Runnable> i = deferred.iterator(); i.hasNext();) {
		Runnable task = i.next();
		try {
		    task.run();
		} catch(Loading l) {
		    continue;
		}
		i.remove();
	    }
	}
	view.markobjs();
	if(visible && (markerseq != view.file.markerseq)) {
	    if(view.file.lock.readLock().tryLock()) {
		try {
		    Map<Marker, ListMarker> prev = new HashMap<>();
		    for(ListMarker pm : this.markers)
			prev.put(pm.mark, pm);
		    List<ListMarker> markers = new ArrayList<>();
		    for(Marker mark : view.file.markers) {
			if(!mflt.test(mark))
			    continue;
			ListMarker lm = prev.get(mark);
			if(lm == null)
			    lm = new ListMarker(mark);
			else
			    lm.type = MarkerType.of(lm.mark);
			markers.add(lm);
		    }
		    markers.sort(mcmp);
		    this.markers = markers;
		} finally {
		    view.file.lock.readLock().unlock();
		}
	    }
	}
    }

    public static abstract class MarkerType implements Comparable<MarkerType> {
	public static final int iconsz = UI.scale(20);
	private static final HashedSet<MarkerType> types = new HashedSet<>(Hash.eq);
	public abstract Tex icon();

	public static MarkerType of(Marker mark) {
	    if(mark instanceof PMarker) {
		return(types.intern(new PMarkerType(((PMarker)mark).color)));
	    } else if(mark instanceof SMarker) {
		return(types.intern(new SMarkerType(((SMarker)mark).res)));
	    } else {
		return(null);
	    }
	}

	public int compareTo(MarkerType that) {
	    return(this.getClass().getName().compareTo(that.getClass().getName()));
	}
    }

    public static class PMarkerType extends MarkerType {
	public final Color col;
	private Tex icon = null;

	public PMarkerType(Color col) {
	    this.col = col;
	}

	public Tex icon() {
	    if(icon == null) {
		Resource.Image fg = MiniMap.DisplayMarker.flagfg, bg = MiniMap.DisplayMarker.flagbg;
		Coord tsz = Coord.of(Math.max(fg.tsz.x, bg.tsz.x), Math.max(fg.tsz.y, bg.tsz.y));
		Coord bsz = Coord.of(Math.max(tsz.x, tsz.y));
		Coord o = bsz.sub(tsz);
		WritableRaster buf = PUtils.imgraster(bsz);
		PUtils.blit(buf, PUtils.coercergba(fg.img).getRaster(), fg.o.add(o));
		PUtils.colmul(buf, col);
		PUtils.alphablit(buf, PUtils.coercergba(bg.img).getRaster(), bg.o.add(o));
		icon = new TexI(PUtils.uiscale(PUtils.rasterimg(buf), new Coord(iconsz, iconsz)));
	    }
	    return(icon);
	}

	public boolean equals(PMarkerType that) {
	    return(Utils.eq(this.col, that.col));
	}
	public boolean equals(Object that) {
	    return((that instanceof PMarkerType) && equals((PMarkerType)that));
	}

	public int hashCode() {
	    return(col.hashCode());
	}

	public int compareTo(PMarkerType that) {
	    int a = Utils.index(BuddyWnd.gc, this.col), b = Utils.index(BuddyWnd.gc, that.col);
	    if((a >= 0) && (b >= 0))
		return(a - b);
	    if((a < 0) && (b >= 0))
		return(1);
	    if((a >= 0) && (b < 0))
		return(-1);
	    return(Utils.idcmp.compare(this.col, that.col));
	}
	public int compareTo(MarkerType that) {
	    if(that instanceof PMarkerType)
		return(compareTo((PMarkerType)that));
	    return(super.compareTo(that));
	}
    }

    public static class SMarkerType extends MarkerType {
	private Resource.Saved spec;
	private Tex icon = null;

	public SMarkerType(Resource.Saved spec) {
	    this.spec = spec;
	}

	public Tex icon() {
	    if(icon == null) {
		BufferedImage img = spec.get().flayer(Resource.imgc).img;
		icon = new TexI(PUtils.uiscale(img, new Coord((iconsz * img.getWidth())/ img.getHeight(), iconsz)));
	    }
	    return(icon);
	}

	public boolean equals(SMarkerType that) {
	    if(Utils.eq(this.spec.name, that.spec.name)) {
		if(that.spec.ver > this.spec.ver) {
		    this.spec = that.spec;
		    this.icon = null;
		}
		return(true);
	    }
	    return(false);
	}
	public boolean equals(Object that) {
	    return((that instanceof SMarkerType) && equals((SMarkerType)that));
	}

	public int hashCode() {
	    return(spec.name.hashCode());
	}

	public int compareTo(SMarkerType that) {
	    return(this.spec.name.compareTo(that.spec.name));
	}
	public int compareTo(MarkerType that) {
	    if(that instanceof SMarkerType)
		return(compareTo((SMarkerType)that));
	    return(super.compareTo(that));
	}
    }

    public static class MarkerConfig {
	public static final MarkerConfig showall = new MarkerConfig();
	public static final MarkerConfig hideall = new MarkerConfig().showsel(true);
	public Set<MarkerType> sel = Collections.emptySet();
	public boolean showsel = false;

	public MarkerConfig() {
	}

	public MarkerConfig(MarkerConfig from) {
	    this.sel = from.sel;
	    this.showsel = from.showsel;
	}

	public MarkerConfig showsel(boolean showsel) {
	    MarkerConfig ret = new MarkerConfig(this);
	    ret.showsel = showsel;
	    return(ret);
	}

	public MarkerConfig add(MarkerType type) {
	    MarkerConfig ret = new MarkerConfig(this);
	    ret.sel = new HashSet<>(ret.sel);
	    ret.sel.add(type);
	    return(ret);
	}

	public MarkerConfig remove(MarkerType type) {
	    MarkerConfig ret = new MarkerConfig(this);
	    ret.sel = new HashSet<>(ret.sel);
	    ret.sel.remove(type);
	    return(ret);
	}

	public MarkerConfig toggle(MarkerType type) {
	    if(sel.contains(type))
		return(remove(type));
	    else
		return(add(type));
	}

	public boolean filter(MarkerType type) {
	    return(sel.contains(type) != showsel);
	}

	public boolean filter(Marker mark) {
	    return(sel.isEmpty() ? showsel : filter(MarkerType.of(mark)));
	}

	public boolean equals(MarkerConfig that) {
	    return(Utils.eq(this.sel, that.sel) && (this.showsel == that.showsel));
	}
	public boolean equals(Object that) {
	    return((that instanceof MarkerConfig) && equals((MarkerConfig)that));
	}
    }

    public static class ListMarker {
	public final Marker mark;
	public MarkerType type;

	public ListMarker(Marker mark) {
	    this.mark = mark;
	    type = MarkerType.of(mark);
	}
    }

    public class MarkerList extends SSearchBox<ListMarker, Widget> {
	public MarkerList(Coord sz) {
	    super(sz, MarkerType.iconsz);
	}

	public List<ListMarker> allitems() {return(markers);}
	public boolean searchmatch(ListMarker lm, String txt) {return(lm.mark.nm.toLowerCase().indexOf(txt.toLowerCase()) >= 0);}

	public class Item extends IconText {
	    public final ListMarker lm;

	    public Item(Coord sz, ListMarker lm) {
		super(sz);
		this.lm = lm;
	    }

	    protected BufferedImage img() {throw(new RuntimeException());}
	    protected String text() {return(lm.mark.nm);}
	    protected boolean valid(String text) {return(Utils.eq(text, text()));}

	    protected void drawicon(GOut g) {
		try {
		    Tex icon = lm.type.icon();
		    if(markcfg.filter(lm.type))
			g.chcolor(255, 255, 255, 128);
		    g.aimage(icon, Coord.of(sz.y / 2), 0.5, 0.5);
		    g.chcolor();
		} catch(Loading l) {
		}
	    }

	    public boolean mousedown(MouseDownEvent ev) {
		if(ev.c.x < sz.y) {
		    toggletype(lm.type);
		    return(true);
		}
		return(super.mousedown(ev));
	    }
	}

	public Widget makeitem(ListMarker lm, int idx, Coord sz) {
	    Widget ret = new ItemWidget<ListMarker>(this, sz, lm);
	    ret.add(new Item(sz, lm), Coord.z);
	    return(ret);
	}

	private void toggletype(MarkerType type) {
	    MarkerConfig nc = markcfg.toggle(type);
	    markcfg = nc;
	    cmarkers = nc.sel.isEmpty() ? null : nc;
	}

	public void change(ListMarker lm) {
	    change2(lm);
	    if(lm != null)
		view.center(new SpecLocator(lm.mark.seg, lm.mark.tc));
	}

	public void change2(ListMarker lm) {
	    this.sel = lm;

	    if(tool.namesel != null) {
		ui.destroy(tool.namesel);
		tool.namesel = null;
		ui.destroy(mremove);
		mremove = null;
		if(colsel != null) {
		    ui.destroy(colsel);
		    colsel = null;
		}
	    }

	    if(lm != null) {
		Marker mark = lm.mark;
		if(tool.namesel == null) {
			tool.namesel = tool.add(new TextEntry(tool.innerw(), "") {
			    {dshow = true;}
			    public void activate(String text) {
				mark.nm = text;
				view.file.update(mark);
				commit();
				change2(null);
			    }
			});
		}
		tool.namesel.settext(mark.nm);
		tool.namesel.buf.point(mark.nm.length());
		tool.namesel.commit();
		if(mark instanceof PMarker) {
		    PMarker pm = (PMarker)mark;
		    colsel = tool.add(new GroupSelector(Math.max(0, Utils.index(BuddyWnd.gc, pm.color))) {
			    public void changed(int group) {
				pm.color = BuddyWnd.gc[group];
				view.file.update(mark);
			    }
			});
		}
		mremove = tool.add(new Button(tool.innerw(), "Remove", false) {
			public void click() {
			    view.file.remove(mark);
			    change2(null);
			}
		    });
		MapWnd.this.resize(csz());
	    }
	}
    }

    public void resize(Coord sz) {
	layoutToolbar();
	sz = sz.max(compact() ? UI.scale(220, 180) : UI.scale(540, 320));
	super.resize(sz);
	tool.resize(Math.max(UI.scale(240), sz.y - (MAP_PAD * 2)));
	if(!compact()) {
	    tool.c = Coord.of(sz.x - tool.sz.x - MAP_PAD, MAP_PAD);
	    viewf.c = Coord.of(MAP_PAD, MAP_PAD);
	    viewf.resize(Coord.of(Math.max(UI.scale(240), tool.c.x - (MAP_PAD * 2)), Math.max(UI.scale(180), sz.y - (MAP_PAD * 2))));
	} else {
	    viewf.c = Coord.of(MAP_PAD, MAP_PAD);
	    viewf.resize(sz.sub(MAP_PAD * 2, MAP_PAD * 2));
	    tool.c = viewf.pos("ur").adds(UI.scale(10), 0);
	}
	view.resize(viewf.inner());
	layoutToolbar();
    }

    private boolean compact() {
	return(compactmode);
    }

    public void compact(boolean a) {
	Coord keep = this.sz;
	compactmode = a;
	if(tool != null) {
	    tool.show(!a);
	    if(a)
		delfocusable(tool);
	    else
		newfocusable(tool);
	}
	resize(keep);
    }

    public void recenter() {
	view.follow(player);
    }

    public void focus(Marker m) {
	for(ListMarker lm : markers) {
	    if(lm.mark == m) {
		tool.list.change2(lm);
		tool.list.display(lm);
		break;
	    }
	}
    }

    public void markobj(long gobid, long oid, Indir<Resource> resid, String nm) {
	synchronized(deferred) {
	    deferred.add(new Runnable() {
		    double f = 0;
		    public void run() {
			Resource res = resid.get();
			String rnm = nm;
			if(rnm == null) {
			    Resource.Tooltip tt = res.layer(Resource.tooltip);
			    if(tt == null)
				return;
			    rnm = tt.t;
			}
			double now = Utils.rtime();
			if(f == 0)
			    f = now;
			Gob gob = ui.sess.glob.oc.getgob(gobid);
			if(gob == null) {
			    if(now - f < 1.0)
				throw(new Loading());
			    return;
			}
			Coord tc = gob.rc.floor(tilesz);
			MCache.Grid obg = ui.sess.glob.map.getgrid(tc.div(cmaps));
			SMarker mark;
			if(!view.file.lock.writeLock().tryLock())
			    throw(new Loading());
			try {
			    MapFile.GridInfo info = view.file.gridinfo.get(obg.id);
			    if(info == null)
				throw(new Loading());
			    Coord sc = tc.add(info.sc.sub(obg.gc).mul(cmaps));
			    SMarker prev = view.file.smarker(res.name, info.seg, sc);
			    if(prev == null) {
				mark = new SMarker(info.seg, sc, rnm, oid, new Resource.Saved(Resource.remote(), res.name, res.ver));
				view.file.add(mark);
			    } else {
				mark = prev;
				if((prev.seg != info.seg) || !eq(prev.tc, sc) || !eq(prev.nm, rnm)) {
				    prev.seg = info.seg;
				    prev.tc = sc;
				    prev.nm = rnm;
				    view.file.update(prev);
				}
			    }
			} finally {
			    view.file.lock.writeLock().unlock();
			}
			synchronized(gob) {
			    gob.setattr(new MarkerID(gob, mark));
			}
		    }
		});
	}
    }

    public static class ExportWindow extends Window implements MapFile.ExportStatus {
	private Thread th;
	private volatile String prog = "Exporting map...";

	public ExportWindow() {
	    super(UI.scale(new Coord(300, 65)), "Exporting map...", true);
	    adda(new Button(UI.scale(100), "Cancel", false, this::cancel), csz().x / 2, UI.scale(40), 0.5, 0.0);
	}

	public void run(Thread th) {
	    (this.th = th).start();
	}

	public void cdraw(GOut g) {
	    g.text(prog, UI.scale(new Coord(10, 10)));
	}

	public void cancel() {
	    th.interrupt();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(!th.isAlive())
		destroy();
	}

	public void grid(int cs, int ns, int cg, int ng) {
	    this.prog = String.format("Exporting map cut %,d/%,d in segment %,d/%,d", cg, ng, cs, ns);
	}

	public void mark(int cm, int nm) {
	    this.prog = String.format("Exporting marker %,d/%,d", cm, nm);
	}
    }

    public static class ImportWindow extends Window {
	private Thread th;
	private volatile String prog = "Initializing";
	private double sprog = -1;

	public ImportWindow() {
	    super(UI.scale(new Coord(300, 65)), "Importing map...", true);
	    adda(new Button(UI.scale(100), "Cancel", false, this::cancel), csz().x / 2, UI.scale(40), 0.5, 0.0);
	}

	public void run(Thread th) {
	    (this.th = th).start();
	}

	public void cdraw(GOut g) {
	    String prog = this.prog;
	    if(sprog >= 0)
		prog = String.format("%s: %d%%", prog, (int)Math.floor(sprog * 100));
	    else
		prog = prog + "...";
	    g.text(prog, UI.scale(new Coord(10, 10)));
	}

	public void cancel() {
	    th.interrupt();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(!th.isAlive())
		destroy();
	}

	public void prog(String prog) {
	    this.prog = prog;
	    this.sprog = -1;
	}

	public void sprog(double sprog) {
	    this.sprog = sprog;
	}
    }

    public void exportmap(Path path) {
	GameUI gui = getparent(GameUI.class);
	ExportWindow prog = new ExportWindow();
	Thread th = new HackThread(() -> {
		boolean complete = false;
		try {
		    try {
			try(OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
			    file.export(out, MapFile.ExportFilter.all, prog);
			}
			complete = true;
		    } finally {
			if(!complete)
			    Files.deleteIfExists(path);
		    }
		} catch(IOException e) {
		    e.printStackTrace(Debug.log);
		    gui.error("Unexpected error occurred when exporting map.");
		} catch(InterruptedException e) {
		}
	}, "Mapfile exporter");
	prog.run(th);
	gui.adda(prog, gui.sz.div(2), 0.5, 1.0);
    }

    public void importmap(Path path) {
	GameUI gui = getparent(GameUI.class);
	ImportWindow prog = new ImportWindow();
	Thread th = new HackThread(() -> {
		try {
		    try(SeekableByteChannel fp = Files.newByteChannel(path)) {
			long size = fp.size();
			class Updater extends CountingInputStream {
			    Updater(InputStream bk) {super(bk);}

			    protected void update(long val) {
				super.update(val);
				prog.sprog((double)pos / (double)size);
			    }
			}
			prog.prog("Validating map data");
			file.reimport(new Updater(new BufferedInputStream(Channels.newInputStream(fp))), MapFile.ImportFilter.readonly);
			prog.prog("Importing map data");
			fp.position(0);
			file.reimport(new Updater(new BufferedInputStream(Channels.newInputStream(fp))), MapFile.ImportFilter.all);
		    }
		} catch(InterruptedException e) {
		} catch(Exception e) {
		    e.printStackTrace(Debug.log);
		    gui.error("Could not import map: " + e.getMessage());
		}
	}, "Mapfile importer");
	prog.run(th);
	gui.adda(prog, gui.sz.div(2), 0.5, 1.0);
    }

    public void exportmap() {
	java.awt.EventQueue.invokeLater(() -> {
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("Exported Haven map data", "hmap"));
		if(fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
		    return;
		Path path = fc.getSelectedFile().toPath();
		if(path.getFileName().toString().indexOf('.') < 0)
		    path = path.resolveSibling(path.getFileName() + ".hmap");
		exportmap(path);
	    });
    }

    public void importmap() {
	java.awt.EventQueue.invokeLater(() -> {
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("Exported Haven map data", "hmap"));
		if(fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
		    return;
		importmap(fc.getSelectedFile().toPath());
	    });
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("exportmap", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length > 1)
			exportmap(Utils.path(args[1]));
		    else
			exportmap();
		}
	    });
	cmdmap.put("importmap", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length > 1)
			importmap(Utils.path(args[1]));
		    else
			importmap();
		}
	    });
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
