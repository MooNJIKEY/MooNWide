package haven;

import haven.sloth.gui.MovableWidget;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** MooNWide equipment panel: same chrome as {@link MoonInvWnd}. */
public class MoonEquWnd extends MovableWidget {
    public final Equipory eq;
    private static final int headerh = UI.scale(16);
    private static final int pad = UI.scale(6);
    private static final int grip = UI.scale(14);
    private static final int LOCK_SZ = UI.scale(8);
    private static final int SIDE_MAX = UI.scale(220);
    private static final int SIDE_BTN_W = UI.scale(12);
    private static final int SIDE_BTN_H = UI.scale(30);
    private UI.Grab resizing = null;
    private Coord rsz;
    private Coord rdoff;
    private int bgAlpha;
    private final Scrollport buffScroll;
    private boolean sideTarget;
    private double sideOpen;
    private MoonDropdownMenu headerMenu = null;
    private double appear = 0.0;

    private static Coord minInnerFor(Equipory ep) {
	return ep.sz;
    }

    private static Coord initialInner(Equipory ep) {
	Coord saved = Utils.getprefc("wndsz-moon-equ", null);
	Coord base = minInnerFor(ep);
	if(saved == null)
	    return base;
	return Coord.of(Math.max(base.x, saved.x), Math.max(base.y, saved.y));
    }

    /** Outer window size for a given equipment inner size and optional left stats strip. */
    private static Coord wndszBase(Coord csz, int sideExtra) {
	Coord area = (csz == null) ? UI.scale(new Coord(200, 280)) : csz;
	return Coord.of(area.x + (pad * 2) + sideExtra, area.y + headerh + (pad * 2));
    }

    private int sideW() {
	return (int)Math.round(SIDE_MAX * sideOpen);
    }

    private Coord outerForInner(Coord inner) {
	return wndszBase(inner, sideW());
    }

    private Coord contentc() {
	return Coord.of(pad + sideW(), headerh + pad);
    }

    private Coord minInner() {
	return eq.sz;
    }

    public Coord csz() {
	return Coord.of(sz.x - (pad * 2) - sideW(), sz.y - headerh - (pad * 2));
    }

    private Coord cszFromOuter(Coord wsz) {
	Coord mi = minInner();
	int iw = wsz.x - (pad * 2) - sideW();
	int ih = wsz.y - headerh - (pad * 2);
	return Coord.of(Math.max(mi.x, iw), Math.max(mi.y, ih));
    }

    @Override
    protected void savePosition() {
	super.savePosition();
	Utils.setprefc("wndc-equ", c);
	Utils.setprefc("wndsz-moon-equ", csz());
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {}
    }

    public MoonEquWnd(Equipory ep) {
	boolean st = Utils.getprefb("moon-equ-side-open", false);
	double op = st ? 1.0 : 0.0;
	int sw0 = (int)Math.round(SIDE_MAX * op);
	super(wndszBase(initialInner(ep), sw0), "moon-equ");
	loadPosition = false;
	this.bgAlpha = Utils.getprefi("moon-equ-alpha", 210);
	this.sideTarget = st;
	this.sideOpen = op;
	this.buffScroll = add(new Scrollport(Coord.of(Math.max(sw0, UI.scale(8)), ep.sz.y)), new Coord(pad, headerh + pad));
	this.buffScroll.cont.add(new MoonEquBuffSummary.BuffBody(ep));
	this.eq = add(ep, contentc());
	layoutEq();
    }

    @Override
    protected void added() {
	Coord fromWndc = Utils.getprefc("wndc-equ", null);
	Coord fromWpos = Utils.getprefc("wpos-moon-equ", null);
	if(fromWndc != null) {
	    c = fromWndc;
	    Utils.setprefc("wpos-moon-equ", c);
	} else if(fromWpos != null) {
	    c = fromWpos;
	    Utils.setprefc("wndc-equ", c);
	}
	lock(Utils.getprefb("wlock-moon-equ", false));
	super.added();
	appear = 0.0;
    }

    @Override
    public void show() {
	appear = 0.0;
	super.show();
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	if(visible() && appear < 1.0)
	    appear = Math.min(1.0, appear + (dt * 6.5));
	int prev = sideW();
	double speed = 14;
	if(sideTarget)
	    sideOpen = Math.min(1, sideOpen + dt * speed);
	else
	    sideOpen = Math.max(0, sideOpen - dt * speed);
	int sw = sideW();
	if(sw != prev) {
	    Coord inner = Coord.of(sz.x - (pad * 2) - prev, sz.y - headerh - (pad * 2));
	    if(!locked())
		c = c.sub(sw - prev, 0);
	    super.resize(outerForInner(inner));
	    layoutEq();
	}
    }

    private boolean sideBtnHit(Coord c) {
	Coord p = sideBtnPos();
	return c.isect(p, Coord.of(SIDE_BTN_W, SIDE_BTN_H));
    }

    private Coord sideBtnPos() {
	int avatarLeft = eq.c.x + Equipory.bgc.x;
	int x = Math.max(UI.scale(2), avatarLeft + UI.scale(2));
	int y = headerh + pad + Math.max(UI.scale(8), (Math.max(eq.sz.y, minInner().y) / 2) - (SIDE_BTN_H / 2));
	return Coord.of(x, y);
    }

    private void drawSideToggle(GOut g) {
	Coord p = sideBtnPos();
	Coord s = Coord.of(SIDE_BTN_W, SIDE_BTN_H);
	Coord ctr = p.add(s.x / 2, s.y / 2);
	int glow = sideTarget ? 38 : 22;
	g.chcolor(MoonUiTheme.ACCENT_DARK.getRed(), MoonUiTheme.ACCENT_DARK.getGreen(), MoonUiTheme.ACCENT_DARK.getBlue(), glow);
	g.fellipse(ctr, Coord.of((s.x / 2) + UI.scale(4), (s.y / 2) + UI.scale(2)));
	g.chcolor(10, 10, 20, 196);
	g.frect(p, s);
	g.chcolor(MoonUiTheme.BORDER);
	g.rect(p, s.sub(1, 1));
	g.chcolor(MoonUiTheme.ACCENT);
	int cx = p.x + (s.x / 2);
	int cy = p.y + (s.y / 2);
	if(sideTarget) {
	    g.line(Coord.of(cx + UI.scale(2), cy - UI.scale(5)), Coord.of(cx - UI.scale(2), cy), 2);
	    g.line(Coord.of(cx - UI.scale(2), cy), Coord.of(cx + UI.scale(2), cy + UI.scale(5)), 2);
	} else {
	    g.line(Coord.of(cx - UI.scale(2), cy - UI.scale(5)), Coord.of(cx + UI.scale(2), cy), 2);
	    g.line(Coord.of(cx + UI.scale(2), cy), Coord.of(cx - UI.scale(2), cy + UI.scale(5)), 2);
	}
	g.chcolor();
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
	if(sideBtnHit(c))
	    return LocalizationManager.tr(sideTarget ? "ui.menu.side.hide" : "ui.menu.side.show");
	return super.tooltip(c, prev);
    }

    @Override
    public void cresize(Widget ch) {
	if(ch == eq)
	    layoutEq();
	super.cresize(ch);
    }

    public void setAlpha(int alpha) {
	bgAlpha = Math.max(60, Math.min(255, alpha));
	Utils.setprefi("moon-equ-alpha", bgAlpha);
    }

    private boolean griphit(Coord c) {
	return (c.x >= (sz.x - grip)) && (c.y >= (sz.y - grip));
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
	if(menuHit(mc) || lockHit(mc) || closeHit(mc) || sideBtnHit(mc))
	    return false;
	return ((mc.y < headerh) && (button == 1)) || super.moveHit(mc, button);
    }

    @Override
    protected boolean altMoveHit(Coord mc, int button) {
	return false;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	if((ev.b == 1) && sideBtnHit(ev.c)) {
	    sideTarget = !sideTarget;
	    Utils.setprefb("moon-equ-side-open", sideTarget);
	    try {
		Utils.prefs().flush();
	    } catch(Exception ignored) {}
	    return true;
	}
	if((ev.b == 1) && closeHit(ev.c)) {
	    hide();
	    return true;
	}
	if((ev.b == 1) && lockHit(ev.c)) {
	    toggleLock();
	    return true;
	}
	if((ev.b == 1) && menuHit(ev.c)) {
	    toggleHeaderMenu();
	    return true;
	}
	if((ev.b == 1) && headerMenu != null) {
	    closeHeaderMenu();
	}
	if((ev.b == 1) && griphit(ev.c)) {
	    resizing = ui.grabmouse(this);
	    rsz = sz;
	    rdoff = ev.c;
	    raise();
	    return true;
	}
	return super.mousedown(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
	if(resizing != null) {
	    resizing.remove();
	    resizing = null;
	    Utils.setprefc("wndsz-moon-equ", csz());
	    return true;
	}
	return super.mouseup(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	if((resizing != null) && (rsz != null) && (rdoff != null)) {
	    Coord mino = outerForInner(minInner());
	    resize(Coord.of(Math.max(mino.x, rsz.x + ev.c.x - rdoff.x),
		Math.max(mino.y, rsz.y + ev.c.y - rdoff.y)));
	    return;
	}
	super.mousemove(ev);
    }

    @Override
    public void resize(Coord nsz) {
	Coord inner = cszFromOuter(nsz);
	super.resize(outerForInner(inner));
	layoutEq();
	Utils.setprefc("wndsz-moon-equ", csz());
    }

    private void layoutEq() {
	Coord mi = minInner();
	Coord cur = csz();
	if(cur.x < mi.x || cur.y < mi.y)
	    super.resize(outerForInner(Coord.of(Math.max(cur.x, mi.x), Math.max(cur.y, mi.y))));
	int sw = sideW();
	eq.c = Coord.of(pad + sw, headerh + pad);
	int bh = Math.max(eq.sz.y, mi.y);
	buffScroll.resize(Coord.of(Math.max(sw, UI.scale(4)), bh));
	buffScroll.c = Coord.of(pad, headerh + pad);
	buffScroll.visible = sw >= UI.scale(3);
    }

    @Override
    public void draw(GOut g) {
	double anim = easeOut(appear);
	Color bg = new Color(18, 12, 30, bgAlpha);
	Color top = scaleAlpha(shade(bg, 1.08f, 18), anim);
	Color bottom = scaleAlpha(shade(bg, 0.78f, -12), anim);
	MoonUiTheme.drawPanelChrome(g, sz, headerh, MoonUiTheme.title(LocalizationManager.tr("shortcut.equ")),
	    true, headerMenu != null, true, locked(), true, true, true,
	    top, bottom, scaleAlpha(MoonUiTheme.HEADER_TOP, anim), scaleAlpha(MoonUiTheme.HEADER_BOTTOM, anim));
	int sw = sideW();
	if(sw > 0) {
	    Coord sideUl = Coord.of(pad, headerh + pad);
	    Coord sideSz = Coord.of(Math.max(UI.scale(4), sw - pad), Math.max(eq.sz.y, minInner().y));
	    g.chcolor(16, 14, 28, 146);
	    g.frect(sideUl, sideSz);
	    g.chcolor(MoonUiTheme.BORDER_SOFT);
	    g.line(Coord.of(sw + pad, headerh + pad), Coord.of(sw + pad, headerh + pad + sideSz.y), 1);
	    g.chcolor();
	}

	Coord gr = Coord.of(sz.x - grip, sz.y - grip);
	g.chcolor(MoonPanel.MOON_BORDER);
	g.line(gr.add(UI.scale(4), grip - UI.scale(2)), gr.add(grip - UI.scale(2), UI.scale(4)), 1);
	g.line(gr.add(UI.scale(7), grip - UI.scale(2)), gr.add(grip - UI.scale(2), UI.scale(7)), 1);
	g.chcolor();
	int contentAlpha = Math.max(96, (int)Math.round(255 * anim));
	g.chcolor(255, 255, 255, contentAlpha);
	super.draw(g);
	g.chcolor();
	drawSideToggle(g);
    }

    private boolean menuHit(Coord c) {
	return MoonUiTheme.menuButtonHit(c, sz, headerh, true, true);
    }

    private boolean lockHit(Coord c) {
	return MoonUiTheme.lockButtonHit(c, sz, headerh, true);
    }

    private boolean closeHit(Coord c) {
	return MoonUiTheme.closeButtonHit(c, sz, headerh);
    }

    private Coord menuAnchor() {
	return MoonDropdownMenu.toRoot(this, MoonUiTheme.menuButtonCenter(sz, headerh, true, true));
    }

    private void toggleHeaderMenu() {
	if(headerMenu != null) {
	    closeHeaderMenu();
	    return;
	}
	headerMenu = MoonDropdownMenu.popup(this, menuAnchor(), buildMenuEntries(), () -> headerMenu = null);
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
	    () -> LocalizationManager.tr("ui.menu.opacity") + ": " + bgAlpha,
	    () -> {
		int next = (bgAlpha <= 60) ? 210 : (bgAlpha - 50);
		if(next < 60)
		    next = 210;
		setAlpha(next);
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("shortcut.reset_pos"),
	    () -> {
		c = parent.sz.sub(sz).div(2);
		savePosition();
	    }));
	return entries;
    }

    private static Color shade(Color base, float mul, int add) {
	int r = Utils.clip(Math.round(base.getRed() * mul) + add, 0, 255);
	int g = Utils.clip(Math.round(base.getGreen() * mul) + add, 0, 255);
	int b = Utils.clip(Math.round(base.getBlue() * mul) + add, 0, 255);
	return new Color(r, g, b, base.getAlpha());
    }

    private static Color scaleAlpha(Color c, double scale) {
	int alpha = Utils.clip((int)Math.round(c.getAlpha() * Math.max(0.0, Math.min(1.0, 0.25 + (0.75 * scale)))), 0, 255);
	return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static double easeOut(double t) {
	double v = Math.max(0.0, Math.min(1.0, t));
	double inv = 1.0 - v;
	return 1.0 - (inv * inv * inv);
    }
}
