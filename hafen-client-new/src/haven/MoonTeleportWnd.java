package haven;

import java.util.ArrayList;
import java.util.List;

/**
 * UI for {@link TeleportManager}: saved points, import/export, toggles.
 */
public class MoonTeleportWnd extends MoonChatStylePanel {
    public final GameUI gui;

    private static final int LIST_VIS = 9;
    private static final int LIST_W = 380;
    private static final int ITEM_H = 26;

    private final List<TeleportPoint> rows = new ArrayList<>();
    private final TPList list;
    private final TextEntry nameEnt;
    private final TextEntry importEnt;
    private final Label filterLbl;
    private final Button pickBtn;
    private int filterIx;
    private int newCatIx;
    private int lastPointCount = -1;
    private boolean lastPickArmed = false;
    private final String[] cats = {
	TeleportPoint.CAT_HOME, TeleportPoint.CAT_RESOURCE, TeleportPoint.CAT_FRIEND, TeleportPoint.CAT_OTHER
    };
    private final String[] filtKeys = { "all", TeleportPoint.CAT_HOME, TeleportPoint.CAT_RESOURCE, TeleportPoint.CAT_FRIEND, TeleportPoint.CAT_OTHER };

    public MoonTeleportWnd(GameUI gui) {
	super(outerSz(), "moon-tpnav", LocalizationManager.tr("tp.title"));
	this.gui = gui;
	setMinSize(outerSz());
	Coord b = contentOffset().add(UI.scale(10), UI.scale(8));

	Widget prev = add(new CheckBox(LocalizationManager.tr("tp.opt.enable"), true) {
	    { a = MoonConfig.teleportNavEnabled; }
	    public void set(boolean val) {
		MoonConfig.setTeleportNavEnabled(val);
		a = val;
	    }
	}, b);
	prev = add(new CheckBox(LocalizationManager.tr("tp.opt.clamp"), true) {
	    { a = MoonConfig.teleportClickClamp; }
	    public void set(boolean val) {
		MoonConfig.setTeleportClickClamp(val);
		a = val;
	    }
	}, prev.pos("bl").adds(0, 6).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("tp.opt.path"), true) {
	    { a = MoonConfig.teleportRequirePath; }
	    public void set(boolean val) {
		MoonConfig.setTeleportRequirePath(val);
		a = val;
	    }
	}, prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("tp.opt.passivepath"), true) {
	    { a = MoonConfig.teleportPassiveLmbPathfinding; }
	    public void set(boolean val) {
		MoonConfig.setTeleportPassiveLmbPathfinding(val);
		a = val;
	    }
	}, prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("tp.opt.nohostile"), true) {
	    { a = MoonConfig.teleportBlockHostileNearTarget; }
	    public void set(boolean val) {
		MoonConfig.setTeleportBlockHostileNearTarget(val);
		a = val;
	    }
	}, prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("tp.opt.log"), true) {
	    { a = MoonConfig.teleportLogStderr; }
	    public void set(boolean val) {
		MoonConfig.setTeleportLogStderr(val);
		a = val;
	    }
	}, prev.pos("bl").adds(0, 4).x(b.x));

	filterLbl = add(new Label(filterLine()), prev.pos("bl").adds(0, 10).x(b.x));
	prev = add(new Button(UI.scale(160), LocalizationManager.tr("tp.btn.filter"), () -> {
	    filterIx = (filterIx + 1) % filtKeys.length;
	    filterLbl.settext(filterLine());
	    refreshRows();
	}), filterLbl.pos("bl").adds(0, 4).x(b.x));

	list = add(new TPList(UI.scale(LIST_W), UI.scale(ITEM_H), LIST_VIS), prev.pos("bl").adds(0, 6).x(b.x));
	prev = list;

	Button goBtn = new Button(UI.scale(120), LocalizationManager.tr("tp.btn.go"), this::doGo);
	Button delBtn = new Button(UI.scale(120), LocalizationManager.tr("tp.btn.del"), this::doDelete);
	Coord goRow = addhlp(prev.pos("bl").adds(0, 8).x(b.x), UI.scale(6), goBtn, delBtn);
	pickBtn = add(new Button(UI.scale(246), pickBtnText(), () -> {
	    TeleportManager.toggleMapPick(gui);
	    updatePickButton();
	}), Coord.of(b.x, goRow.y + Math.max(goBtn.sz.y, delBtn.sz.y) + UI.scale(6)));
	prev = pickBtn;

	prev = add(new Label(LocalizationManager.tr("tp.save.hdr")), prev.pos("bl").adds(0, 10).x(b.x));
	nameEnt = add(new TextEntry(UI.scale(240), ""), prev.pos("bl").adds(0, 4).x(b.x));
	final Label newCat = add(new Label(newCatLine()), nameEnt.pos("bl").adds(0, 6).x(b.x));
	add(new Button(UI.scale(200), LocalizationManager.tr("tp.btn.newcat"), () -> {
	    newCatIx = (newCatIx + 1) % cats.length;
	    newCat.settext(newCatLine());
	}), newCat.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(260), LocalizationManager.tr("tp.btn.savehere"), this::doSaveHere),
	    newCat.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("tp.io.hdr")), prev.pos("bl").adds(0, 12).x(b.x));
	importEnt = add(new TextEntry(UI.scale(360), ""), prev.pos("bl").adds(0, 4).x(b.x));
	Button exBtn = new Button(UI.scale(120), LocalizationManager.tr("tp.btn.export"), () -> {
	    importEnt.settext(TeleportManager.exportJson());
	    gui.ui.msg(LocalizationManager.tr("tp.io.exported"), java.awt.Color.WHITE, null);
	});
	Button imBtn = new Button(UI.scale(120), LocalizationManager.tr("tp.btn.import"), () -> {
	    int n = TeleportManager.importJsonReplace(importEnt.text());
	    if(n < 0)
		gui.ui.msg(LocalizationManager.tr("tp.io.bad"), java.awt.Color.RED, null);
	    else {
		gui.ui.msg(LocalizationManager.tr("tp.io.imported") + " " + n, java.awt.Color.WHITE, null);
		refreshRows();
	    }
	});
	addhlp(importEnt.pos("bl").adds(0, 6).x(b.x), UI.scale(6), exBtn, imBtn);

	refreshRows();
    }

    private String filterLine() {
	return LocalizationManager.tr("tp.filter") + " " + LocalizationManager.tr("tp.cat." + filtKeys[filterIx]);
    }

    private String newCatLine() {
	return LocalizationManager.tr("tp.newcat") + " " + LocalizationManager.tr("tp.cat." + cats[newCatIx]);
    }

    private void refreshRows() {
	rows.clear();
	String fk = filtKeys[filterIx];
	for(TeleportPoint p : TeleportManager.getPointsSnapshot()) {
	    if(!"all".equals(fk) && !fk.equals(p.category))
		continue;
	    rows.add(p);
	}
	rows.sort((a, b) -> {
	    int c = a.category.compareTo(b.category);
	    if(c != 0)
		return c;
	    return a.name.compareToIgnoreCase(b.name);
	});
	list.syncFrom(rows);
	lastPointCount = TeleportManager.getPointsSnapshot().size();
    }

    private void doGo() {
	TeleportPoint p = list.sel;
	if(p == null) {
	    gui.ui.msg(LocalizationManager.tr("tp.needsel"), java.awt.Color.GRAY, null);
	    return;
	}
	TeleportManager.tryNavigateTo(gui, p, "ui:" + p.name);
	refreshRows();
    }

    private void doDelete() {
	TeleportPoint p = list.sel;
	if(p == null)
	    return;
	TeleportManager.removePoint(p);
	refreshRows();
    }

    private void doSaveHere() {
	if(gui.map == null)
	    return;
	Gob pl;
	try {
	    pl = gui.map.player();
	} catch(Loading e) {
	    gui.ui.msg(LocalizationManager.tr("tp.fail.loading"), java.awt.Color.WHITE, null);
	    return;
	}
	if(pl == null)
	    return;
	String nm = nameEnt.text().trim();
	if(nm.isEmpty())
	    nm = LocalizationManager.tr("tp.noname");
	Coord2d rc = Coord2d.of(pl.rc.x, pl.rc.y);
	TeleportPoint p = new TeleportPoint(nm, rc.x, rc.y, cats[newCatIx]);
	TeleportManager.enrichPointWithCurrentMap(gui, p);
	TeleportManager.addPoint(p);
	nameEnt.settext("");
	refreshRows();
	gui.ui.msg(LocalizationManager.tr("tp.saved"), java.awt.Color.WHITE, null);
    }

    private String pickBtnText() {
	return LocalizationManager.tr(TeleportManager.isMapPickArmed() ? "tp.btn.pick.cancel" : "tp.btn.pick.map");
    }

    private void updatePickButton() {
	boolean armed = TeleportManager.isMapPickArmed();
	if(pickBtn != null)
	    pickBtn.change(pickBtnText());
	lastPickArmed = armed;
    }

    private static Coord outerSz() {
	return new Coord(UI.scale(420) + MoonPanel.PAD * 2, UI.scale(640) + MoonPanel.HEADER_H + MoonPanel.PAD * 2);
    }

    @Override
    protected void added() {
	if(Utils.getprefc("wpos-moon-tpnav", null) == null) {
	    Coord leg = Utils.getprefc("wndc-moon-tpnav", null);
	    if(leg != null)
		Utils.setprefc("wpos-moon-tpnav", leg);
	}
	super.added();
	refreshRows();
	updatePickButton();
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	int curCount = TeleportManager.getPointsSnapshot().size();
	if(curCount != lastPointCount)
	    refreshRows();
	boolean armed = TeleportManager.isMapPickArmed();
	if(armed != lastPickArmed)
	    updatePickButton();
    }

    @Override
    protected void onCloseClicked() {
	reqdestroy();
    }

    @Override
    public void remove() {
	if(gui != null && gui.moonTeleportWnd == this)
	    gui.moonTeleportWnd = null;
	Utils.setprefc("wndc-moon-tpnav", c);
	super.remove();
    }

    private class TPList extends Listbox<TeleportPoint> {
	private final List<TeleportPoint> backing = new ArrayList<>();

	TPList(int w, int ih, int nvis) {
	    super(new Coord(w, ih * nvis), ih);
	    bgcolor = java.awt.Color.BLACK;
	}

	void syncFrom(List<TeleportPoint> src) {
	    backing.clear();
	    backing.addAll(src);
	    sel = null;
	    selindex = -1;
	}

	@Override
	protected TeleportPoint listitem(int i) {
	    return backing.get(i);
	}

	@Override
	protected int listitems() {
	    return backing.size();
	}

	@Override
	protected void drawitem(GOut g, TeleportPoint p, int i) {
	    String name = (p.name != null) ? p.name : "";
	    String cat = LocalizationManager.tr("tp.cat." + p.category);
	    String line = String.format(java.util.Locale.ROOT, "%s  (%.0f, %.0f)  %s", name, p.x, p.y, cat);
	    g.atext(line, new Coord(UI.scale(4), itemh / 2), 0, 0.5);
	}

	@Override
	public void draw(GOut g) {
	    super.draw(g);
	    if(backing.isEmpty()) {
		g.chcolor(java.awt.Color.GRAY);
		g.atext(LocalizationManager.tr("tp.empty"), sz.div(2), 0.5, 0.5);
		g.chcolor();
	    }
	}
    }
}
