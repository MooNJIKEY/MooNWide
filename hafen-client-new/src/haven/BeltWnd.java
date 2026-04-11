package haven;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.List;

public class BeltWnd extends MoonPanel implements DTarget, DropTarget {
    public static final Tex invsq = Inventory.invsq;
    private static Tex emptySlotIcon;
    private static Coord emptySlotIconSz;
    private final int[] keys;
    private final String[] keyLabels;
    /** Short tag for the page footer so multiple belts never read as one glued "Page 1/5Page 1/4…". */
    private final String pageAbbrev;
    /** Vertical stagger for the footer when several belts share the same screen rect. */
    private final int pageFooterDy;
    private final int slotsPerPage;
    private final int startSlot;
    private final GameUI gui;
    private int curPage = 0;
    private int maxPages;
    private Coord gridSz;
    private boolean horizontal;

    public BeltWnd(GameUI gui, String prefKey, String title, String pageAbbrev,
		   int[] keys, String[] keyLabels,
		   int startSlot, int maxPages, int pageFooterDy) {
	super(Coord.z, prefKey, title);
	this.gui = gui;
	this.keys = keys;
	this.keyLabels = keyLabels;
	this.pageAbbrev = pageAbbrev;
	this.pageFooterDy = pageFooterDy;
	this.slotsPerPage = keys.length;
	this.startSlot = startSlot;
	this.maxPages = maxPages;
	this.horizontal = Utils.getprefb(prefKey + "-horiz", true);
	Coord saved = Utils.getprefc(prefKey + "-gridsz", null);
	if(saved != null && saved.x > 0 && saved.y > 0)
	    this.gridSz = saved;
	else
	    this.gridSz = horizontal ? new Coord(slotsPerPage, 1) : new Coord(1, slotsPerPage);
	this.curPage = Utils.getprefi(prefKey + "-page", 0) % maxPages;
	setResizable(false);
	setShowLock(true);
	relayout();
    }

    private Coord cellSz() { return invsq.sz(); }
    private int cellGap() { return UI.scale(2); }

    @Override
    protected boolean roundedChrome() {
	return true;
    }

    private void relayout() {
	Coord cs = cellSz();
	int gap = cellGap();
	Coord content = Coord.of(
	    gridSz.x * (cs.x + gap) - gap,
	    gridSz.y * (cs.y + gap) - gap + UI.scale(16)
	);
	super.resize(wrapSize(content));
    }

    private Coord slotCoord(int i) {
	Coord cs = cellSz();
	int gap = cellGap();
	int gx = i % gridSz.x;
	int gy = i / gridSz.x;
	return contentOffset().add(gx * (cs.x + gap), gy * (cs.y + gap));
    }

    private Tex emptySlotIcon(Coord cs) {
	Coord want = cs.sub(UI.scale(10), UI.scale(10));
	if(emptySlotIcon == null || !want.equals(emptySlotIconSz)) {
	    emptySlotIconSz = want;
	    java.awt.image.BufferedImage src = MoonUiIcons.image("slot.empty", want);
	    if(src != null)
		emptySlotIcon = new TexI(PUtils.uiscale(src, want), false);
	    else
		emptySlotIcon = null;
	}
	return emptySlotIcon;
    }

    private int slotAt(Coord c) {
	Coord cs = cellSz();
	for(int i = 0; i < slotsPerPage && i < gridSz.x * gridSz.y; i++) {
	    Coord sc = slotCoord(i);
	    if(c.isect(sc, cs))
		return i;
	}
	return -1;
    }

    private int beltIdx(int slot) {
	return startSlot + slot + (curPage * slotsPerPage);
    }

    @Override
    protected void drawContent(GOut g) {
	Coord cs = cellSz();
	int visible = Math.min(slotsPerPage, gridSz.x * gridSz.y);
	for(int i = 0; i < visible; i++) {
	    Coord sc = slotCoord(i);
	    g.image(invsq, sc);
	    int bidx = beltIdx(i);
	    try {
		if(bidx < gui.belt.length && gui.belt[bidx] != null) {
		    gui.belt[bidx].draw(g.reclip(sc.add(UI.scale(1), UI.scale(1)),
						 cs.sub(UI.scale(2), UI.scale(2))));
		} else {
		    Tex empty = emptySlotIcon(cs);
		    if(empty != null)
			g.image(empty, sc.add(cs.sub(empty.sz()).div(2)));
		}
	    } catch(Loading e) {}
	    if(i < keyLabels.length) {
		g.chcolor(160, 140, 200, 255);
		FastText.aprintf(g, sc.add(cs.sub(UI.scale(2), 0)), 1, 1, "%s", keyLabels[i]);
		g.chcolor();
	    }
	}

	Coord co = contentOffset();
	int pgy = gridSz.y * (cs.y + cellGap());
	if(maxPages > 1) {
	    g.chcolor(MOON_ACCENT);
	    g.atext(String.format("%s · %d/%d", pageAbbrev, curPage + 1, maxPages),
		    Coord.of(co.x + (contentSize().x / 2), co.y + pgy + UI.scale(2) + pageFooterDy), 0.5, 0.0);
	    g.chcolor();
	}

    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	int si = slotAt(ev.c);
	if(si >= 0) {
	    int bidx = beltIdx(si);
	    if(ev.b == 1) {
		activateBeltSlot(bidx);
	    } else if(ev.b == 3) {
		if(gui.clearSpecialBeltSlot(bidx))
		    return true;
		gui.wdgmsg("setbelt", bidx, null);
	    }
	    return true;
	}

	Coord co = contentOffset();
	Coord cs = cellSz();
	int pgy = co.y + gridSz.y * (cs.y + cellGap());
	if(maxPages > 1 && ev.c.y >= pgy && ev.c.y < pgy + UI.scale(16)) {
	    if(ev.c.x < sz.x / 2) {
		curPage = (curPage - 1 + maxPages) % maxPages;
	    } else {
		curPage = (curPage + 1) % maxPages;
	    }
	    Utils.setprefi(key + "-page", curPage);
	    return true;
	}
	/* Header: drag / lock. Below header: eat all clicks so they never pass through to the map. */
	if(ev.c.y < MoonPanel.HEADER_H)
	    return(super.mousedown(ev));
	return(true);
    }

    private static final int SNAP_DIST = UI.scale(15);

    private void snapToEdge() {
	if(parent == null || parent.sz == null) return;
	Coord ps = parent.sz;
	if(c.x < SNAP_DIST)
	    c.x = 0;
	else if(c.x + sz.x > ps.x - SNAP_DIST)
	    c.x = Math.max(0, ps.x - sz.x);
	if(c.y < SNAP_DIST)
	    c.y = 0;
	else if(c.y + sz.y > ps.y - SNAP_DIST)
	    c.y = Math.max(0, ps.y - sz.y);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	super.mousemove(ev);
	if(moving())
	    snapToEdge();
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
	boolean wasDragging = moving();
	boolean ret = super.mouseup(ev);
	if(wasDragging) {
	    snapToEdge();
	    savePos();
	}
	return ret;
    }

    @Override
    public boolean globtype(GlobKeyEvent ev) {
	for(int i = 0; i < keys.length; i++) {
	    if(ev.code == keys[i]) {
		activateBeltSlot(beltIdx(i));
		return true;
	    }
	}
	return super.globtype(ev);
    }

    private void activateBeltSlot(int bidx) {
	if(bidx < 0 || bidx >= gui.belt.length || gui.belt[bidx] == null)
	    return;
	if(gui.map != null) {
	    Coord mvc = gui.map.rootxlate(ui.mc);
	    if(mvc.isect(Coord.z, gui.map.sz)) {
		gui.beltKeyAct(bidx);
		return;
	    }
	}
	gui.belt[bidx].use(new MenuGrid.Interaction(1, ui.modflags()));
    }

    @Override
    public boolean drop(Coord c, Coord ul) {
	int si = slotAt(c);
	if(si >= 0) {
	    gui.clearSpecialBeltSlot(beltIdx(si));
	    gui.wdgmsg("setbelt", beltIdx(si), 0);
	    return true;
	}
	return false;
    }

    @Override
    public boolean iteminteract(Coord c, Coord ul) { return false; }

    @Override
    public boolean dropthing(Coord c, Object thing) {
	int si = slotAt(c);
	if(si >= 0) {
	    int bidx = beltIdx(si);
	    if(thing instanceof MenuGrid.Pagina) {
		MenuGrid.Pagina pag = (MenuGrid.Pagina)thing;
		if(gui.setSpecialBeltSlot(bidx, pag))
		    return true;
		gui.clearSpecialBeltSlot(bidx);
		try {
		    if(pag.id instanceof Indir)
			gui.wdgmsg("setbelt", bidx, "res", pag.res().name);
		    else
			gui.wdgmsg("setbelt", bidx, "pag", pag.id);
		} catch(Loading l) {}
		return true;
	    }
	}
	return false;
    }

    @Override
    protected void addPanelMenuEntries(List<MoonDropdownMenu.Entry> entries) {
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr(horizontal ? "ui.menu.layout.vertical" : "ui.menu.layout.horizontal"),
	    () -> {
		setLayout(!horizontal);
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.columns_more") + " (" + gridSz.x + ")",
	    () -> {
		setGridSize(new Coord(Math.min(slotsPerPage, gridSz.x + 1), gridSz.y));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.columns_less") + " (" + gridSz.x + ")",
	    () -> {
		setGridSize(new Coord(Math.max(1, gridSz.x - 1), gridSz.y));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.rows_more") + " (" + gridSz.y + ")",
	    () -> {
		setGridSize(new Coord(gridSz.x, Math.min(slotsPerPage, gridSz.y + 1)));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.rows_less") + " (" + gridSz.y + ")",
	    () -> {
		setGridSize(new Coord(gridSz.x, Math.max(1, gridSz.y - 1)));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.grid.5x2"),
	    () -> {
		setGridSize(new Coord(5, 2));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.grid.2x5"),
	    () -> {
		setGridSize(new Coord(2, 5));
		closeHeaderMenu();
	    }));
	addResetPositionEntry(entries, () -> {
	    BeltWnd.this.c = BeltWnd.this.parent.sz.sub(BeltWnd.this.sz).div(2);
	    savePos();
	});
    }

    public void setLayout(boolean horiz) {
	this.horizontal = horiz;
	if(horiz)
	    this.gridSz = new Coord(slotsPerPage, 1);
	else
	    this.gridSz = new Coord(1, slotsPerPage);
	Utils.setprefb(key + "-horiz", horiz);
	Utils.setprefc(key + "-gridsz", gridSz);
	relayout();
    }

    public void setGridSize(Coord gsz) {
	this.gridSz = gsz;
	Utils.setprefc(key + "-gridsz", gsz);
	relayout();
    }

    private void savePos() {
	if(key != null)
	    Utils.setprefc("wpos-" + key, c);
    }

    public static BeltWnd createNKeyBelt(GameUI gui) {
	int[] keys = new int[]{
	    KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5,
	    KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9, KeyEvent.VK_0
	};
	String[] labels = {"1","2","3","4","5","6","7","8","9","0"};
	return new BeltWnd(gui, "moon-belt-n", LocalizationManager.tr("belt.panel.num"), LocalizationManager.tr("belt.pageabbr.num"), keys, labels, 0, 5, 0);
    }

    public static BeltWnd createFKeyBelt(GameUI gui) {
	int[] keys = new int[]{
	    KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
	    KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
	    KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12
	};
	String[] labels = {"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"};
	return new BeltWnd(gui, "moon-belt-f", LocalizationManager.tr("belt.panel.f"), LocalizationManager.tr("belt.pageabbr.f"), keys, labels, 50, 5, UI.scale(14));
    }

    public static BeltWnd createNumBelt(GameUI gui) {
	int[] keys = new int[]{
	    KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD3,
	    KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD6,
	    KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8, KeyEvent.VK_NUMPAD9,
	    KeyEvent.VK_NUMPAD0
	};
	String[] labels = {"N1","N2","N3","N4","N5","N6","N7","N8","N9","N0"};
	return new BeltWnd(gui, "moon-belt-num", LocalizationManager.tr("belt.panel.numpad"), LocalizationManager.tr("belt.pageabbr.numpad"), keys, labels, 100, 4, UI.scale(28));
    }
}
