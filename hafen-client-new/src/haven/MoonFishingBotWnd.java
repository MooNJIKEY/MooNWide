package haven;

/**
 * Minimal safe-v1 fishing bot window: pick water tile, enable/disable, and tune retry policy.
 */
public class MoonFishingBotWnd extends MoonChatStylePanel {
    private final GameUI gui;
    private final Button runBtn;
    private final Label waterLbl;
    private final Label statusLbl;
    private double refreshAcc = 0.0;

    public MoonFishingBotWnd(GameUI gui) {
	super(outer(), "moon-fishingbot", text("Бот рыбалки", "Fishing bot"));
	this.gui = gui;
	setMinSize(outer());

	Coord b = contentOffset().add(UI.scale(10), UI.scale(10));
	runBtn = add(new Button(UI.scale(260), runBtnLabel(), this::toggleRun), b);
	waterLbl = add(new Label(waterLine(), UI.scale(320)), runBtn.pos("bl").adds(0, 10).x(b.x));
	Button pick = add(new Button(UI.scale(180), text("Выбрать точку воды", "Pick water tile"), this::pickWater),
	    waterLbl.pos("bl").adds(0, 6).x(b.x));
	add(new Button(UI.scale(120), text("Очистить", "Clear"), this::clearWater), pick.pos("ur").adds(8, 0));

	Widget prev = add(new CheckBox(text("Стоп при враждебных мобах", "Stop near hostiles"), true) {
	    { a = MoonConfig.fishingBotStopHostile; }
	    public void set(boolean val) {
		MoonConfig.setFishingBotStopHostile(val);
		a = val;
	    }
	}, pick.pos("bl").adds(0, 10).x(b.x));

	{
	    Label dpy = add(new Label(""), prev.pos("bl").adds(0, 10).x(b.x + UI.scale(210)));
	    prev = add(new HSlider(UI.scale(180), 6, 60, MoonConfig.fishingBotCastTimeoutSec) {
		    protected void added() {
			dpy.settext(text("Таймаут каста: ", "Cast timeout: ") + val + "s");
		    }
		    public void changed() {
			MoonConfig.setFishingBotCastTimeoutSec(val);
			dpy.settext(text("Таймаут каста: ", "Cast timeout: ") + val + "s");
		    }
		}, prev.pos("bl").adds(0, 4).x(b.x));
	}
	{
	    Label dpy = add(new Label(""), prev.pos("bl").adds(0, 10).x(b.x + UI.scale(210)));
	    prev = add(new HSlider(UI.scale(180), 1, 12, MoonConfig.fishingBotMaxFailures) {
		    protected void added() {
			dpy.settext(text("Макс. ошибок: ", "Max failures: ") + val);
		    }
		    public void changed() {
			MoonConfig.setFishingBotMaxFailures(val);
			dpy.settext(text("Макс. ошибок: ", "Max failures: ") + val);
		    }
		}, prev.pos("bl").adds(0, 4).x(b.x));
	}
	{
	    Label dpy = add(new Label(""), prev.pos("bl").adds(0, 10).x(b.x + UI.scale(210)));
	    prev = add(new HSlider(UI.scale(180), 0, 16, MoonConfig.fishingBotMinFreeSlots) {
		    protected void added() {
			dpy.settext(text("Свободных слотов минимум: ", "Min free slots: ") + val);
		    }
		    public void changed() {
			MoonConfig.setFishingBotMinFreeSlots(val);
			dpy.settext(text("Свободных слотов минимум: ", "Min free slots: ") + val);
		    }
		}, prev.pos("bl").adds(0, 4).x(b.x));
	}

	prev = add(new Button(UI.scale(260), text("Сбросить состояние", "Reset state"), () -> {
	    MoonFishingBot.reset();
	    if(gui != null && gui.ui != null)
		gui.ui.msg(text("Состояние рыбалки сброшено.", "Fishing state reset."), java.awt.Color.WHITE, null);
	    refreshUi();
	}), prev.pos("bl").adds(0, 12).x(b.x));

	statusLbl = add(new Label("", UI.scale(320)), prev.pos("bl").adds(0, 12).x(b.x));
	refreshUi();
    }

    private static Coord outer() {
	Coord csz = UI.scale(new Coord(390, 280));
	return new Coord(csz.x + MoonPanel.PAD * 2, csz.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2);
    }

    private static String text(String ru, String en) {
	return MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }

    private static String runBtnLabel() {
	return MoonConfig.fishingBotEnabled ? text("Остановить", "Stop") : text("Запустить рыбалку", "Start fishing");
    }

    private static String waterLine() {
	Coord tc = MoonTreeUtil.parseTileCoord(MoonConfig.fishingBotWaterTile);
	return (tc == null) ? text("Точка воды: не выбрана", "Water tile: not selected")
	    : String.format(text("Точка воды: %d %d", "Water tile: %d %d"), tc.x, tc.y);
    }

    private void toggleRun() {
	if(MoonConfig.fishingBotEnabled) {
	    MoonConfig.setFishingBotEnabled(false);
	    MoonFishingBot.reset();
	} else {
	    Coord tc = MoonTreeUtil.parseTileCoord(MoonConfig.fishingBotWaterTile);
	    if(tc == null) {
		if(gui != null && gui.ui != null)
		    gui.ui.msg(text("Сначала выбери тайл воды.", "Pick a water tile first."), java.awt.Color.WHITE, null);
		return;
	    }
	    MoonFishingBot.reset();
	    MoonConfig.setFishingBotEnabled(true);
	}
	refreshUi();
    }

    private void pickWater() {
	if(gui == null || gui.map == null)
	    return;
	gui.map.startClientTileSelect(new Coord(1, 1), a -> {
	    if(a != null && a.positive()) {
		Coord t = a.ul;
		MoonConfig.setFishingBotWaterTile(t.x + " " + t.y);
	    }
	    ui.loader.defer(this::refreshUi, null);
	}, false);
    }

    private void clearWater() {
	MoonConfig.setFishingBotWaterTile("");
	refreshUi();
    }

    private void refreshUi() {
	runBtn.change(runBtnLabel());
	waterLbl.settext(waterLine());
	statusLbl.settext(text("Статус: ", "Status: ") + MoonFishingBot.statusSummary(gui));
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	refreshAcc += dt;
	if(refreshAcc >= 0.25) {
	    refreshAcc = 0.0;
	    refreshUi();
	}
    }

    @Override
    protected void onCloseClicked() {
	reqdestroy();
    }

    @Override
    public void remove() {
	if(gui != null && gui.moonFishingBotWnd == this)
	    gui.moonFishingBotWnd = null;
	super.remove();
    }
}
