package haven;

import java.awt.Color;
import java.util.List;

public class MenuGridWnd extends MoonPanel {
    public final MenuGrid grid;

    /**
     * Opaque body + chrome: {@link GameUI#map} is {@code lower()}ed behind the HUD; translucent
     * Moon panels would show mmap/world bleed-through in the skill grid.
     */
    @Override
    protected Color panelBodyColor() {
	return new Color(18, 12, 30, 255);
    }

    @Override
    protected boolean opaquePanelChrome() {
	return true;
    }

    public MenuGridWnd(MenuGrid grid) {
	super(Coord.z, "moon-menugrid", null);
	setShowLock(true);
	setResizable(false);
	this.grid = add(grid, contentOffset());
	resizeToGrid();
    }

    private void resizeToGrid() {
	Coord csz = MenuGrid.bgsz.mul(grid.gridSize()).add(1, 1);
	super.resize(wrapSize(csz));
	if(grid != null)
	    grid.c = contentOffset();
    }

    @Override
    protected void added() {
	super.added();
	resizeToGrid();
    }

    @Override
    public void draw(GOut g) {
	super.draw(g);
    }

    @Override
    protected void drawContent(GOut g) {
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	GameUI gui = getparent(GameUI.class);
	if(gui != null)
	    gui.restoreMoonHudZOrder();
	return super.mousedown(ev);
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
	if(menuHit(mc))
	    return false;
	return(chrome && button == 1 && mc.y < dragBandHeight());
    }

    @Override
    protected boolean altMoveHit(Coord mc, int button) {
	return false;
    }

    @Override
    protected void addPanelMenuEntries(List<MoonDropdownMenu.Entry> entries) {
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.columns_more") + " (" + grid.gridSize().x + ")",
	    () -> {
		applyGrid(grid.gridSize().add(1, 0));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.columns_less") + " (" + grid.gridSize().x + ")",
	    () -> {
		applyGrid(grid.gridSize().sub(1, 0));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.rows_more") + " (" + grid.gridSize().y + ")",
	    () -> {
		applyGrid(grid.gridSize().add(0, 1));
		closeHeaderMenu();
	    }));
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr("ui.menu.rows_less") + " (" + grid.gridSize().y + ")",
	    () -> {
		applyGrid(grid.gridSize().sub(0, 1));
		closeHeaderMenu();
	    }));
	addResetPositionEntry(entries, () -> {
	    MenuGridWnd.this.c = MenuGridWnd.this.parent.sz.sub(MenuGridWnd.this.sz).sub(UI.scale(10), UI.scale(10));
	    savePos();
	});
    }

    private void applyGrid(Coord next) {
	int cols = Utils.clip(next.x, 2, 8);
	int rows = Utils.clip(next.y, 2, 8);
	grid.setGridSize(new Coord(cols, rows));
	resizeToGrid();
    }

    private void savePos() {
	if(key != null)
	    Utils.setprefc("wpos-" + key, c);
    }
}
