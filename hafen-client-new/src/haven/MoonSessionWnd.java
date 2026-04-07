package haven;

public class MoonSessionWnd extends MoonPanel {
    private final GameUI gui;
    private final MoonSessionsList list;
    private int lastListHeight = -1;

    public MoonSessionWnd(GameUI gui) {
	super(Coord.z, "moon-session-switcher", LocalizationManager.tr("session.switch.title"));
	this.gui = gui;
	setShowLock(true);
	setResizable(false);
	setMinSize(UI.scale(new Coord(220, 96)));
	Config.reloadLogins();
	list = add(new MoonSessionsList(gui), contentOffset());
	relayout();
    }

    private void relayout() {
	Coord co = contentOffset();
	list.c = co;
	Coord csz = Coord.of(list.sz.x, list.sz.y);
	resizeContent(csz);
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	if(list.sz.y != lastListHeight) {
	    lastListHeight = list.sz.y;
	    relayout();
	}
    }

    @Override
    public void remove() {
	if((gui != null) && (gui.sesswnd == this))
	    gui.sesswnd = null;
	super.remove();
    }
}
