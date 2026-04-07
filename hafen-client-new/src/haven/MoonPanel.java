package haven;

import haven.sloth.gui.MovableWidget;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MoonPanel extends MovableWidget {
    public static final Color MOON_BG       = MoonUiTheme.BODY_BOTTOM;
    public static final Color MOON_BORDER   = MoonUiTheme.BORDER;
    public static final Color MOON_ACCENT   = MoonUiTheme.ACCENT;
    public static final Color MOON_HEADER   = MoonUiTheme.HEADER_TOP;
    public static final Color MOON_HEADER2  = MoonUiTheme.HEADER_BOTTOM;
    public static final Color MOON_LOCK_ON  = MoonUiTheme.LOCKED;
    public static final Color MOON_LOCK_OFF = MoonUiTheme.UNLOCKED;
    public static final Color MOON_HOVER    = MoonUiTheme.MENU_HOVER;
    public static final int HEADER_H = UI.scale(16);
    public static final int PAD      = UI.scale(6);
    public static final int GRIP     = UI.scale(14);

    private String title;
    private Text.Line titleTex;
    private boolean resizable;
    private Coord minsz;
    private UI.Grab resizing = null;
    private Coord rsz, rdoff;
    protected boolean showLock = true;
    protected float panelScale = 1.0f;
    protected boolean chrome = true;
    private MoonDropdownMenu headerMenu = null;
    private double appear = 0.0;

    public MoonPanel(Coord sz, String prefKey, String title) {
	super(sz, prefKey);
	this.title = title;
	this.titleTex = (title != null) ? MoonUiTheme.title(title) : null;
	this.resizable = false;
	this.minsz = UI.scale(new Coord(80, 60));
	this.panelScale = Utils.getpreff("moon-scale-" + prefKey, 1.0f);
    }

    public MoonPanel(String prefKey, String title) {
	this(Coord.z, prefKey, title);
    }

    public void setTitle(String t) {
	this.title = t;
	this.titleTex = (t != null) ? MoonUiTheme.title(t) : null;
    }

    public void setResizable(boolean r) { this.resizable = r; }
    public void setMinSize(Coord min) { this.minsz = min; }
    public void setShowLock(boolean s) { this.showLock = s; }
    public void setChrome(boolean c) { this.chrome = c; }

    /** Body fill under the header; subclasses may tie opacity to {@link ChatWnd}-style prefs. */
    protected Color panelBodyColor() {
	return(MOON_BG);
    }

    /** When true, header stripes use fully opaque alpha so nothing behind the HUD bleeds through. */
    protected boolean opaquePanelChrome() {
	return(false);
    }

    /** When set, header shows a close control (left of the lock). */
    private boolean closable = false;

    public void setClosable(boolean v) {
	this.closable = v;
    }

    protected void onCloseClicked() {
    }

    protected boolean hasTitle() {
	return (titleTex != null) && (titleTex.text != null) && !titleTex.text.isEmpty();
    }

    protected boolean roundedChrome() {
	return false;
    }

    protected boolean transparentChromeMode() {
	return false;
    }

    protected boolean externalActionButtons() {
	return true;
    }

    private int actionStripHeight(boolean withMenu, boolean withLock, boolean withClose) {
	if(!chrome || !externalActionButtons())
	    return 0;
	int aw = MoonUiTheme.headerActionsWidth(withMenu, withLock, withClose);
	return (aw > 0) ? (MoonUiTheme.MENU_BTN_H + UI.scale(6)) : 0;
    }

    protected int actionStripHeight() {
	return actionStripHeight(hasHeaderMenu(), hasLockButton(), hasCloseButton());
    }

    protected Coord chromeSize() {
	return Coord.of(sz.x, Math.max(1, sz.y - actionStripHeight()));
    }

    protected Coord chromeOffset() {
	return Coord.of(0, actionStripHeight());
    }

    protected int actionHeaderHeight() {
	return externalActionButtons() ? actionStripHeight() : headerHeight();
    }

    protected int headerHeight() {
	return (!chrome || !hasTitle()) ? 0 : HEADER_H;
    }

    protected int dragBandHeight() {
	return hasTitle() ? headerHeight() : UI.scale(8);
    }

    public Coord contentOffset() {
	if(!chrome)
	    return(Coord.z);
	return Coord.of(PAD, actionStripHeight() + headerHeight() + PAD);
    }

    public Coord contentSize() {
	if(!chrome)
	    return(sz);
	return Coord.of(
	    Math.max(1, sz.x - (PAD * 2)),
	    Math.max(1, sz.y - actionStripHeight() - headerHeight() - (PAD * 2))
	);
    }

    public Coord wrapSize(Coord csz) {
	if(!chrome)
	    return(csz);
	return Coord.of(csz.x + (PAD * 2), csz.y + actionStripHeight() + headerHeight() + (PAD * 2));
    }

    public void resizeContent(Coord csz) {
	Coord ws = wrapSize(csz);
	ws = Coord.of(Math.max(minsz.x, ws.x), Math.max(minsz.y, ws.y));
	super.resize(ws);
	onContentResize(contentSize());
	if(ui != null)
	    saveSize();
    }

    protected void onContentResize(Coord csz) {}

    private String sizeKey() { return key == null ? null : "wndsz-" + key; }

    protected void saveSize() {
	String sk = sizeKey();
	if(sk != null)
	    Utils.setprefc(sk, sz);
    }

    protected void loadSize() {
	String sk = sizeKey();
	if(sk != null) {
	    Coord saved = Utils.getprefc(sk, null);
	    if(saved != null && saved.x >= minsz.x && saved.y >= minsz.y)
		super.resize(saved);
	}
    }

    @Override
    protected void added() {
	loadSize();
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
    }

    private boolean griphit(Coord c) {
	return resizable && (c.x >= (sz.x - GRIP)) && (c.y >= (sz.y - GRIP));
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
	if(!chrome)
	    return super.moveHit(mc, button);
	boolean hasMenu = hasHeaderMenu();
	boolean hasLock = hasLockButton();
	boolean hasClose = hasCloseButton();
	int actionStripH = actionStripHeight(hasMenu, hasLock, hasClose);
	int actionHeaderH = externalActionButtons() ? actionStripH : headerHeight();
	if(hasMenu && mc.isect(MoonUiTheme.menuButtonPos(sz, actionHeaderH, hasLock, hasClose), Coord.of(MoonUiTheme.MENU_BTN_W, MoonUiTheme.MENU_BTN_H))) return false;
	if(hasLock && mc.isect(MoonUiTheme.lockButtonPos(sz, actionHeaderH, hasClose), Coord.of(MoonUiTheme.MENU_BTN_W, MoonUiTheme.MENU_BTN_H))) return false;
	if(hasClose && mc.isect(MoonUiTheme.closeButtonPos(sz, actionHeaderH), Coord.of(MoonUiTheme.MENU_BTN_W, MoonUiTheme.MENU_BTN_H))) return false;
	return ((mc.y < (actionStripH + dragBandHeight())) && (mc.x < sz.x) && (button == 1))
	    || super.moveHit(mc, button);
    }

    @Override
    protected boolean altMoveHit(Coord mc, int button) {
	return false;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	if((ev.b == 1) && closeHit(ev.c)) {
	    onCloseClicked();
	    return(true);
	}
	if((ev.b == 1) && lockHit(ev.c)) {
	    toggleLock();
	    return(true);
	}
	if((ev.b == 1) && menuHit(ev.c)) {
	    toggleHeaderMenu();
	    return(true);
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
	if(ev.propagate(this))
	    return true;
	if(super.mousedown(ev))
	    return true;
	return checkhit(ev.c);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
	if(resizing != null) {
	    resizing.remove();
	    resizing = null;
	    saveSize();
	    GameUI gui = getparent(GameUI.class);
	    if(gui != null)
		gui.restoreMoonHudZOrder();
	    return true;
	}
	/*
	 * Release panel dragging before propagating mouse-up into children.
	 * Otherwise widgets like MenuGrid can consume the event and leave the
	 * parent panel's mouse grab active, after which the panel starts
	 * responding to clicks outside its visible bounds.
	 */
	if(moving())
	    return super.mouseup(ev);
	if(ev.propagate(this))
	    return true;
	if(super.mouseup(ev))
	    return true;
	return checkhit(ev.c);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	if((resizing != null) && (rsz != null) && (rdoff != null)) {
	    Coord nsz = Coord.of(
		Math.max(minsz.x, rsz.x + ev.c.x - rdoff.x),
		Math.max(minsz.y, rsz.y + ev.c.y - rdoff.y)
	    );
	    super.resize(nsz);
	    onContentResize(contentSize());
	    return;
	}
	super.mousemove(ev);
    }

    public float getPanelScale() { return panelScale; }

    protected void onScaleChanged(float newScale) {}

    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
	if(ui != null && ui.modflags() == (UI.MOD_CTRL | UI.MOD_META)) {
	    float ns = Math.max(0.5f, Math.min(2.0f, panelScale - 0.1f * ev.a));
	    if(ns != panelScale) {
		panelScale = ns;
		if(key != null)
		    Utils.setpreff("moon-scale-" + key, panelScale);
		Coord center = c.add(sz.div(2));
		onScaleChanged(panelScale);
		move(center.sub(sz.div(2)));
	    }
	    return true;
	}
	return super.mousewheel(ev);
    }

    @Override
    public void draw(GOut g) {
	if(!chrome) {
	    drawContent(g);
	    super.draw(g);
	    return;
	}
	boolean hasMenu = hasHeaderMenu();
	boolean hasLock = hasLockButton();
	boolean hasClose = hasCloseButton();
	int actionStripH = actionStripHeight(hasMenu, hasLock, hasClose);
	int actionHeaderH = externalActionButtons() ? actionStripH : headerHeight();
	double anim = easeOut(appear);
	Color body = panelBodyColor();
	Color bodyTop = scaleAlpha(shade(body, 1.08f, 18), anim);
	Color bodyBottom = scaleAlpha(shade(body, 0.78f, -12), anim);
	Color headerTop = scaleAlpha(opaquePanelChrome() ? forceAlpha(MOON_HEADER, 255) : MOON_HEADER, anim);
	Color headerBottom = scaleAlpha(opaquePanelChrome() ? forceAlpha(MOON_HEADER2, 255) : MOON_HEADER2, anim);
	int headerH = headerHeight();
	boolean transparent = transparentChromeMode();
	if(!transparent) {
	    Coord chromeSz = Coord.of(sz.x, Math.max(1, sz.y - actionStripH));
	    GOut cg = g.reclip(Coord.of(0, actionStripH), chromeSz);
	    MoonUiTheme.drawPanelChrome(cg, chromeSz, headerH, hasTitle() ? titleTex : null,
		externalActionButtons() ? false : hasMenu, externalActionButtons() ? false : headerMenu != null,
		externalActionButtons() ? false : hasLock, locked(), externalActionButtons() ? false : hasClose, true, roundedChrome(),
		bodyTop, bodyBottom, headerTop, headerBottom);
	}

	if(resizable && !transparent) {
	    Coord gr = Coord.of(sz.x - GRIP, sz.y - GRIP);
	    g.chcolor(MOON_BORDER);
	    g.line(gr.add(UI.scale(4), GRIP - UI.scale(2)),
		   gr.add(GRIP - UI.scale(2), UI.scale(4)), 1);
	    g.line(gr.add(UI.scale(7), GRIP - UI.scale(2)),
		   gr.add(GRIP - UI.scale(2), UI.scale(7)), 1);
	    g.chcolor();
	}

	int contentAlpha = Math.max(96, (int)Math.round(255 * anim));
	g.chcolor(255, 255, 255, contentAlpha);
	drawContent(g);
	super.draw(g);
	g.chcolor();
	if(externalActionButtons())
	    MoonUiTheme.drawPanelActions(g, sz, actionHeaderH, hasMenu, headerMenu != null, hasLock, locked(), hasClose);
	else if(headerH <= 0)
	    MoonUiTheme.drawPanelActions(g, sz, headerH, hasMenu, headerMenu != null, hasLock, locked(), hasClose);
    }

    protected void drawContent(GOut g) {}

    private static Color shade(Color base, float mul, int add) {
	int r = Utils.clip(Math.round(base.getRed() * mul) + add, 0, 255);
	int g = Utils.clip(Math.round(base.getGreen() * mul) + add, 0, 255);
	int b = Utils.clip(Math.round(base.getBlue() * mul) + add, 0, 255);
	return new Color(r, g, b, base.getAlpha());
    }

    private static Color forceAlpha(Color c, int alpha) {
	return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
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

    protected void addPanelMenuEntries(List<MoonDropdownMenu.Entry> entries) {
    }

    protected final void addResetPositionEntry(List<MoonDropdownMenu.Entry> entries, Runnable action) {
	entries.add(MoonDropdownMenu.Entry.action(() -> LocalizationManager.tr("shortcut.reset_pos"), () -> {
	    action.run();
	    closeHeaderMenu();
	}));
    }

    protected void closeHeaderMenu() {
	if(headerMenu != null) {
	    MoonDropdownMenu menu = headerMenu;
	    headerMenu = null;
	    menu.destroy();
	}
    }

    protected Coord headerMenuAnchor() {
	return MoonDropdownMenu.toRoot(this, MoonUiTheme.menuButtonCenter(sz, actionHeaderHeight(), hasLockButton(), hasCloseButton()));
    }

    protected List<MoonDropdownMenu.Entry> buildHeaderMenuEntries() {
	List<MoonDropdownMenu.Entry> entries = new ArrayList<>();
	addPanelMenuEntries(entries);
	return entries;
    }

    protected boolean hasHeaderMenu() {
	return chrome && (!buildHeaderMenuEntries().isEmpty());
    }

    protected boolean hasCloseButton() {
	return chrome && closable;
    }

    protected boolean hasLockButton() {
	return chrome && showLock;
    }

    protected boolean menuHit(Coord c) {
	return chrome && hasHeaderMenu() && MoonUiTheme.menuButtonHit(c, sz, actionHeaderHeight(), hasLockButton(), hasCloseButton());
    }

    protected boolean lockHit(Coord c) {
	return hasLockButton() && MoonUiTheme.lockButtonHit(c, sz, actionHeaderHeight(), hasCloseButton());
    }

    protected boolean closeHit(Coord c) {
	return hasCloseButton() && MoonUiTheme.closeButtonHit(c, sz, actionHeaderHeight());
    }

    protected void toggleHeaderMenu() {
	if(headerMenu != null) {
	    closeHeaderMenu();
	    return;
	}
	List<MoonDropdownMenu.Entry> entries = buildHeaderMenuEntries();
	if(entries.isEmpty() || ui == null)
	    return;
	headerMenu = MoonDropdownMenu.popup(this, headerMenuAnchor(), entries, () -> headerMenu = null);
    }
}
