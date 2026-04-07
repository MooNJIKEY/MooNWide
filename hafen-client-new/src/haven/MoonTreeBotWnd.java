package haven;

/**
 * Tree-chop bot: chop/stock/split/water areas; styled like {@link ChatWnd}.
 */
public class MoonTreeBotWnd extends MoonChatStylePanel {
    public final GameUI gui;
    private Label chopLbl, stockLbl, waterLbl, waterStockLbl, splitLogLbl, splitOutLbl, spotsLbl;
    private Button runBtn;

    public MoonTreeBotWnd(GameUI gui) {
	super(treeOuterSz(), "moon-treebot", LocalizationManager.tr("treebot.title"));
	this.gui = gui;
	setMinSize(UI.scale(new Coord(200, 200)));
	int x = UI.scale(10);
	int y = UI.scale(10);
	Coord b = contentOffset().add(x, y);
	runBtn = add(new Button(UI.scale(260), runBtnLabel(), () -> toggleRun()), b);
	Widget prev = runBtn;
	prev = add(new CheckBox(LocalizationManager.tr("treebot.hitstack"), true) {
	    { a = MoonConfig.treeBotHitstack; }
	    public void set(boolean val) { MoonConfig.setTreeBotHitstack(val); a = val; }
	}, prev.pos("bl").adds(0, 8).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("treebot.mobs"), true) {
	    { a = MoonConfig.treeBotAvoidMobs; }
	    public void set(boolean val) { MoonConfig.setTreeBotAvoidMobs(val); a = val; }
	}, prev.pos("bl").adds(0, 6).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("treebot.water"), true) {
	    { a = MoonConfig.treeBotWater; }
	    public void set(boolean val) { MoonConfig.setTreeBotWater(val); a = val; }
	}, prev.pos("bl").adds(0, 6).x(b.x));

	prev = add(new CheckBox(LocalizationManager.tr("treebot.chop.first"), true) {
	    { a = MoonConfig.treeBotChopBeforeHaul; }
	    public void set(boolean val) { MoonConfig.setTreeBotChopBeforeHaul(val); a = val; }
	}, prev.pos("bl").adds(0, 8).x(b.x));

	prev = add(new CheckBox(LocalizationManager.tr("treebot.auto.pipeline"), true) {
	    { a = MoonConfig.treeBotAutoPipeline; }
	    public void set(boolean val) { MoonConfig.setTreeBotAutoPipeline(val); a = val; }
	}, prev.pos("bl").adds(0, 6).x(b.x));
	prev = add(new CheckBox(LocalizationManager.tr("treebot.hud.line"), true) {
	    { a = MoonConfig.treeBotHudLine; }
	    public void set(boolean val) { MoonConfig.setTreeBotHudLine(val); a = val; }
	}, prev.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Button(UI.scale(260), LocalizationManager.tr("treebot.reset"), () -> {
	    MoonTreeChopBot.resetState();
	    gui.ui.msg(LocalizationManager.tr("treebot.reset.done"), java.awt.Color.WHITE, null);
	}), prev.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.phase.row")), prev.pos("bl").adds(0, 10).x(b.x));
	int bpw = UI.scale(84);
	Button pb1 = add(new Button(bpw, LocalizationManager.tr("treebot.phase.btn1"), () -> MoonConfig.setTreeBotWorkPhase(1)),
	    prev.pos("bl").adds(0, 4).x(b.x));
	Button pb2 = add(new Button(bpw, LocalizationManager.tr("treebot.phase.btn2"), () -> MoonConfig.setTreeBotWorkPhase(2)),
	    pb1.pos("ur").adds(4, 0));
	add(new Button(bpw, LocalizationManager.tr("treebot.phase.btn3"), () -> MoonConfig.setTreeBotWorkPhase(3)),
	    pb2.pos("ur").adds(4, 0));
	spotsLbl = add(new Label(spotsLine()), pb1.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.chop.hdr")), spotsLbl.pos("bl").adds(0, 10).x(b.x));
	chopLbl = add(new Label(chopLine()), prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(220), LocalizationManager.tr("treebot.chop.pick"), () -> pickChop()),
	    chopLbl.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.stock.hdr")), prev.pos("bl").adds(0, 12).x(b.x));
	stockLbl = add(new Label(stockLine()), prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(220), LocalizationManager.tr("treebot.stock.pick"), () -> pickStock()),
	    stockLbl.pos("bl").adds(0, 6).x(b.x));
	prev = add(new Label(LocalizationManager.tr("treebot.stock.hint")), prev.pos("bl").adds(0, 4).x(b.x + UI.scale(6)));
	prev = add(new Label(LocalizationManager.tr("treebot.limit.piles")), prev.pos("bl").adds(0, 6).x(b.x));

	prev = add(new CheckBox(LocalizationManager.tr("treebot.split.enable"), true) {
	    { a = MoonConfig.treeBotSplitEnabled; }
	    public void set(boolean val) { MoonConfig.setTreeBotSplitEnabled(val); a = val; }
	}, prev.pos("bl").adds(0, 10).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.split.log.hdr")), prev.pos("bl").adds(0, 8).x(b.x));
	splitLogLbl = add(new Label(splitLogLine()), prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(220), LocalizationManager.tr("treebot.split.log.pick"), () -> pickSplitLog()),
	    splitLogLbl.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.split.out.hdr")), prev.pos("bl").adds(0, 10).x(b.x));
	splitOutLbl = add(new Label(splitOutLine()), prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(220), LocalizationManager.tr("treebot.split.out.pick"), () -> pickSplitOut()),
	    splitOutLbl.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.water.hdr")), prev.pos("bl").adds(0, 12).x(b.x));
	waterLbl = add(new Label(waterLine()), prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(220), LocalizationManager.tr("treebot.water.pick"), () -> pickWater()),
	    waterLbl.pos("bl").adds(0, 6).x(b.x));

	prev = add(new Label(LocalizationManager.tr("treebot.water.stock.hdr")), prev.pos("bl").adds(0, 10).x(b.x));
	waterStockLbl = add(new Label(waterStockLine()), prev.pos("bl").adds(0, 4).x(b.x));
	prev = add(new Button(UI.scale(220), LocalizationManager.tr("treebot.water.stock.pick"), () -> pickWaterStock()),
	    waterStockLbl.pos("bl").adds(0, 6).x(b.x));
	add(new Label(LocalizationManager.tr("treebot.water.hint")), prev.pos("bl").adds(0, 8).x(b.x + UI.scale(10)));
    }

    private static String runBtnLabel() {
	return(MoonConfig.treeBotEnabled ? LocalizationManager.tr("treebot.stop") : LocalizationManager.tr("treebot.start"));
    }

    private void toggleRun() {
	if(MoonConfig.treeBotEnabled) {
	    MoonConfig.setTreeBotEnabled(false);
	} else {
	    Area c = MoonTreeUtil.parseArea(MoonConfig.treeBotChopArea);
	    if(c == null || !c.positive()) {
		gui.ui.msg(LocalizationManager.tr("treebot.need.chop"), java.awt.Color.WHITE, null);
		return;
	    }
	    if(MoonConfig.treeBotSplitEnabled) {
		Area a = MoonTreeUtil.parseArea(MoonConfig.treeBotSplitLogArea);
		Area b = MoonTreeUtil.parseArea(MoonConfig.treeBotSplitOutArea);
		if(a == null || !a.positive() || b == null || !b.positive()) {
		    gui.ui.msg(LocalizationManager.tr("treebot.need.split"), java.awt.Color.WHITE, null);
		    return;
		}
	    }
	    MoonConfig.setTreeBotEnabled(true);
	    if(MoonConfig.treeBotWorkPhase == 0)
		MoonConfig.setTreeBotWorkPhase(1);
	}
	runBtn.change(runBtnLabel());
    }

    private static String spotsLine() {
	return(LocalizationManager.tr("treebot.spots.line") + " " + MoonConfig.treeBotSpotCount());
    }

    private static Coord treeOuterSz() {
	Coord csz = UI.scale(new Coord(380, 920));
	return(new Coord(csz.x + MoonPanel.PAD * 2, csz.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2));
    }

    @Override
    protected void added() {
	if(Utils.getprefc("wpos-moon-treebot", null) == null) {
	    Coord leg = Utils.getprefc("wndc-moon-treebot", null);
	    if(leg != null)
		Utils.setprefc("wpos-moon-treebot", leg);
	}
	super.added();
    }

    @Override
    protected void onCloseClicked() {
	reqdestroy();
    }

    private String chopLine() {
	Area a = MoonTreeUtil.parseArea(MoonConfig.treeBotChopArea);
	if(a == null || !a.positive())
	    return(LocalizationManager.tr("treebot.chop.none"));
	int n = (gui != null && gui.map != null)
	    ? MoonTreeUtil.countTreesInTileArea(gui.map.glob, a) : 0;
	return(String.format(LocalizationManager.tr("treebot.chop.line"), MoonTreeUtil.areaToString(a), n));
    }

    private String stockLine() {
	Area a = MoonTreeUtil.parseArea(MoonConfig.treeBotStockArea);
	if(a == null || !a.positive())
	    return(LocalizationManager.tr("treebot.stock.none"));
	return(String.format(LocalizationManager.tr("treebot.stock.line"), MoonTreeUtil.areaToString(a)));
    }

    private String splitLogLine() {
	Area a = MoonTreeUtil.parseArea(MoonConfig.treeBotSplitLogArea);
	if(a == null || !a.positive())
	    return(LocalizationManager.tr("treebot.split.log.none"));
	return(String.format(LocalizationManager.tr("treebot.split.log.line"), MoonTreeUtil.areaToString(a)));
    }

    private String splitOutLine() {
	Area a = MoonTreeUtil.parseArea(MoonConfig.treeBotSplitOutArea);
	if(a == null || !a.positive())
	    return(LocalizationManager.tr("treebot.split.out.none"));
	return(String.format(LocalizationManager.tr("treebot.split.out.line"), MoonTreeUtil.areaToString(a)));
    }

    private String waterLine() {
	Coord t = MoonTreeUtil.parseTileCoord(MoonConfig.treeBotWaterTile);
	if(t == null)
	    return(LocalizationManager.tr("treebot.water.none"));
	return(String.format(LocalizationManager.tr("treebot.water.line"), t.x, t.y));
    }

    private String waterStockLine() {
	Area a = MoonTreeUtil.parseArea(MoonConfig.treeBotWaterStockArea);
	if(a == null || !a.positive())
	    return(LocalizationManager.tr("treebot.water.stock.none"));
	return(String.format(LocalizationManager.tr("treebot.water.stock.line"), MoonTreeUtil.areaToString(a)));
    }

    /** Spot count line only (after Shift+Alt+click on map / 3D). */
    public void refreshSpotsUi() {
	if(spotsLbl != null)
	    spotsLbl.settext(spotsLine());
    }

    private void refreshLabels() {
	chopLbl.settext(chopLine());
	stockLbl.settext(stockLine());
	splitLogLbl.settext(splitLogLine());
	splitOutLbl.settext(splitOutLine());
	waterLbl.settext(waterLine());
	waterStockLbl.settext(waterStockLine());
	if(spotsLbl != null)
	    spotsLbl.settext(spotsLine());
    }

    private void pickChop() {
	if(gui.map == null)
	    return;
	gui.map.startClientTileSelect(null, a -> {
	    if(a != null && a.positive())
		MoonConfig.setTreeBotChopArea(MoonTreeUtil.areaToString(a));
	    ui.loader.defer(() -> refreshLabels(), null);
	}, true);
    }

    private void pickStock() {
	if(gui.map == null)
	    return;
	gui.map.startClientTileSelect(null, a -> {
	    if(a != null && a.positive())
		MoonConfig.setTreeBotStockArea(MoonTreeUtil.areaToString(a));
	    ui.loader.defer(() -> refreshLabels(), null);
	}, false);
    }

    private void pickSplitLog() {
	if(gui.map == null)
	    return;
	gui.map.startClientTileSelect(null, a -> {
	    if(a != null && a.positive())
		MoonConfig.setTreeBotSplitLogArea(MoonTreeUtil.areaToString(a));
	    ui.loader.defer(() -> refreshLabels(), null);
	}, true);
    }

    private void pickSplitOut() {
	if(gui.map == null)
	    return;
	gui.map.startClientTileSelect(null, a -> {
	    if(a != null && a.positive())
		MoonConfig.setTreeBotSplitOutArea(MoonTreeUtil.areaToString(a));
	    ui.loader.defer(() -> refreshLabels(), null);
	}, false);
    }

    private void pickWater() {
	if(gui.map == null)
	    return;
	gui.map.startClientTileSelect(new Coord(1, 1), a -> {
	    if(a != null && a.positive()) {
		Coord t = a.ul;
		MoonConfig.setTreeBotWaterTile(t.x + " " + t.y);
	    }
	    ui.loader.defer(() -> refreshLabels(), null);
	}, false);
    }

    private void pickWaterStock() {
	if(gui.map == null)
	    return;
	gui.map.startClientTileSelect(null, a -> {
	    if(a != null && a.positive())
		MoonConfig.setTreeBotWaterStockArea(MoonTreeUtil.areaToString(a));
	    ui.loader.defer(() -> refreshLabels(), null);
	}, false);
    }

    @Override
    public void remove() {
	if(gui != null && gui.moonTreeBotWnd == this)
	    gui.moonTreeBotWnd = null;
	Utils.setprefc("wndc-moon-treebot", c);
	super.remove();
    }
}
