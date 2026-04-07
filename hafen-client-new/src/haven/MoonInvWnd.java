package haven;

import haven.sloth.gui.MovableWidget;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** MooNWide inventory panel: same chrome as chat (drag, lock, resize, alpha). */
public class MoonInvWnd extends MovableWidget {
    public final Inventory inv;
    private final Button sortBtn;
    private static final int headerh = UI.scale(16);
    private static final int pad = UI.scale(6);
    private static final int grip = UI.scale(14);
    private static final int LOCK_SZ = UI.scale(8);
    private static final int gap = UI.scale(4);
    private UI.Grab resizing = null;
    private Coord rsz;
    private Coord rdoff;
    private int bgAlpha;
    private MoonDropdownMenu headerMenu = null;

    private static Coord minInnerFor(Inventory inv) {
	return Coord.of(
	    Math.max(inv.sz.x, UI.scale(72)),
	    inv.sz.y + gap + Button.hs);
    }

    private static Coord initialInner(Inventory inv) {
	Coord saved = Utils.getprefc("wndsz-moon-inv", null);
	Coord base = minInnerFor(inv);
	if(saved == null)
	    return base;
	return Coord.of(Math.max(base.x, saved.x), Math.max(base.y, saved.y));
    }

    private static Coord wndsz(Coord csz) {
	Coord area = (csz == null) ? UI.scale(new Coord(160, 120)) : csz;
	return Coord.of(area.x + (pad * 2), area.y + headerh + (pad * 2));
    }

    private Coord contentc() {
	return Coord.of(pad, headerh + pad);
    }

    private Coord minInner() {
	return Coord.of(
	    Math.max(inv.sz.x, sortBtn.sz.x),
	    inv.sz.y + gap + sortBtn.sz.y);
    }

    public Coord csz() {
	return Coord.of(sz.x - (pad * 2), sz.y - headerh - (pad * 2));
    }

    private Coord cszFromOuter(Coord wsz) {
	Coord mi = minInner();
	int iw = wsz.x - (pad * 2);
	int ih = wsz.y - headerh - (pad * 2);
	return Coord.of(Math.max(mi.x, iw), Math.max(mi.y, ih));
    }

    @Override
    protected void savePosition() {
	super.savePosition();
	Utils.setprefc("wndc-inv", c);
	Utils.setprefc("wndsz-moon-inv", csz());
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {}
    }

    public MoonInvWnd(Inventory inv) {
	super(wndsz(initialInner(inv)), "moon-inv");
	loadPosition = false;
	this.bgAlpha = Utils.getprefi("moon-inv-alpha", 210);
	this.inv = add(inv, contentc());
	this.sortBtn = add(new Button(UI.scale(72), LocalizationManager.tr("inv.sortAz")) {
	    @Override
	    public void click() {
		if(!MoonInvWnd.this.inv.isSorting())
		    MoonInvWnd.this.inv.sortByName(false);
	    }
	});
	layoutInv();
    }

    @Override
    protected void added() {
	Coord fromWndc = Utils.getprefc("wndc-inv", null);
	Coord fromWpos = Utils.getprefc("wpos-moon-inv", null);
	if(fromWndc != null) {
	    c = fromWndc;
	    Utils.setprefc("wpos-moon-inv", c);
	} else if(fromWpos != null) {
	    c = fromWpos;
	    Utils.setprefc("wndc-inv", c);
	}
	lock(Utils.getprefb("wlock-moon-inv", false));
	super.added();
    }

    @Override
    public void cresize(Widget ch) {
	if(ch == inv)
	    layoutInv();
	super.cresize(ch);
    }

    public int alpha() {
	return bgAlpha;
    }

    public void setAlpha(int alpha) {
	bgAlpha = Math.max(60, Math.min(255, alpha));
	Utils.setprefi("moon-inv-alpha", bgAlpha);
    }

    private boolean griphit(Coord c) {
	return (c.x >= (sz.x - grip)) && (c.y >= (sz.y - grip));
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
	if(menuHit(mc) || lockHit(mc) || closeHit(mc)) return false;
	return ((mc.y < headerh) && (button == 1)) || super.moveHit(mc, button);
    }

    @Override
    protected boolean altMoveHit(Coord mc, int button) {
	return false;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
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
	    Utils.setprefc("wndsz-moon-inv", csz());
	    return true;
	}
	return super.mouseup(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	if((resizing != null) && (rsz != null) && (rdoff != null)) {
	    Coord mino = wndsz(minInner());
	    resize(Coord.of(Math.max(mino.x, rsz.x + ev.c.x - rdoff.x),
		Math.max(mino.y, rsz.y + ev.c.y - rdoff.y)));
	    return;
	}
	super.mousemove(ev);
    }

    @Override
    public void resize(Coord nsz) {
	super.resize(wndsz(cszFromOuter(nsz)));
	layoutInv();
	Utils.setprefc("wndsz-moon-inv", csz());
    }

    private void layoutInv() {
	Coord mi = minInner();
	Coord cur = csz();
	if(cur.x < mi.x || cur.y < mi.y) {
	    super.resize(wndsz(Coord.of(Math.max(cur.x, mi.x), Math.max(cur.y, mi.y))));
	}
	inv.c = contentc();
	sortBtn.c = Coord.of(contentc().x, contentc().y + inv.sz.y + gap);
    }

    @Override
    public void draw(GOut g) {
	Color bg = new Color(18, 12, 30, bgAlpha);
	Color top = shade(bg, 1.08f, 18);
	Color bottom = shade(bg, 0.78f, -12);
	MoonUiTheme.drawPanelChrome(g, sz, headerh, MoonUiTheme.title(LocalizationManager.tr("shortcut.inv")),
	    true, headerMenu != null, true, locked(), true, true, true,
	    top, bottom, MoonUiTheme.HEADER_TOP, MoonUiTheme.HEADER_BOTTOM);

	Coord gr = Coord.of(sz.x - grip, sz.y - grip);
	g.chcolor(MoonPanel.MOON_BORDER);
	g.line(gr.add(UI.scale(4), grip - UI.scale(2)), gr.add(grip - UI.scale(2), UI.scale(4)), 1);
	g.line(gr.add(UI.scale(7), grip - UI.scale(2)), gr.add(grip - UI.scale(2), UI.scale(7)), 1);
	g.chcolor();
	super.draw(g);
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
	    () -> LocalizationManager.tr("inv.sortAz"),
	    () -> {
		if(!inv.isSorting())
		    inv.sortByName(false);
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
}
