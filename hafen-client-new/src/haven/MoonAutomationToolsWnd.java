package haven;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import managers.automation.AutoRunner;
import managers.automation.TaskQueue;

/**
 * Utility / diagnostics hub for assist automations, legacy queue, and native automation status.
 */
public class MoonAutomationToolsWnd extends MoonChatStylePanel {
    private final GameUI gui;
    private final LinkedHashMap<String, Label> rows = new LinkedHashMap<>();
    private final Button autoDrinkBtn;
    private final Button autoDropBtn;
    private final Button passiveGateBtn;
    private final Button bulkFillBtn;
    private final Button bulkTakeBtn;
    private final Button legacyRunnerBtn;
    private double refreshAcc = 0.0;

    public MoonAutomationToolsWnd(GameUI gui) {
	super(outer(), "moon-automation-tools", text("Automation Tools", "Automation Tools"));
	this.gui = gui;
	setMinSize(outer());

	Coord b = contentOffset().add(UI.scale(10), UI.scale(10));
	add(new Label(text("Быстрые переключатели и диагностика automation-слоя.", "Quick toggles and diagnostics for the automation stack."),
	    UI.scale(500)), b);

	autoDrinkBtn = add(new Button(UI.scale(156), "", this::toggleAutoDrink), b.add(0, UI.scale(30)));
	autoDropBtn = add(new Button(UI.scale(156), "", this::toggleAutoDrop), autoDrinkBtn.pos("ur").adds(8, 0));
	passiveGateBtn = add(new Button(UI.scale(156), "", this::togglePassiveGate), autoDropBtn.pos("ur").adds(8, 0));
	bulkFillBtn = add(new Button(UI.scale(156), "", this::toggleBulkFill), autoDrinkBtn.pos("bl").adds(0, 8));
	bulkTakeBtn = add(new Button(UI.scale(156), "", this::toggleBulkTake), bulkFillBtn.pos("ur").adds(8, 0));
	legacyRunnerBtn = add(new Button(UI.scale(156), "", this::toggleLegacyRunner), bulkTakeBtn.pos("ur").adds(8, 0));

	Button openCombat = add(new Button(UI.scale(156), text("Автобой...", "Combat..."), gui::openMoonCombatBotWindow),
	    bulkFillBtn.pos("bl").adds(0, 12));
	Button openTree = add(new Button(UI.scale(156), text("Рубка...", "Tree..."), gui::openMoonTreeBotWindow),
	    openCombat.pos("ur").adds(8, 0));
	Button openMine = add(new Button(UI.scale(156), text("Копка...", "Mine..."), gui::openMoonMineBotWindow),
	    openTree.pos("ur").adds(8, 0));
	Button openFish = add(new Button(UI.scale(156), text("Рыбалка...", "Fishing..."), gui::openMoonFishingBotWindow),
	    openCombat.pos("bl").adds(0, 8));
	Button openTp = add(new Button(UI.scale(156), text("TP / Nav...", "TP / Nav..."), gui::toggleMoonTeleportWindow),
	    openFish.pos("ur").adds(8, 0));
	add(new Button(UI.scale(156), text("Автодроп...", "Auto-drop..."), gui::openMoonAutoDropWindow),
	    openTp.pos("ur").adds(8, 0));

	Button clearQueue = add(new Button(UI.scale(238), text("Очистить legacy-очередь", "Clear legacy queue"), this::clearLegacyQueue),
	    openFish.pos("bl").adds(0, 12));
	add(new Button(UI.scale(238), text("Сбросить все состояния", "Reset all states"), this::resetAll),
	    clearQueue.pos("ur").adds(10, 0));

	int y = clearQueue.pos("bl").adds(0, 14).y;
	for(String id : new String[] {"combat", "tree", "mine", "fishing", "autodrink", "autodrop", "passivegate", "bulk", "tpnav", "legacy", "native"}) {
	    Label row = add(new Label("", UI.scale(500)), Coord.of(b.x, y));
	    rows.put(id, row);
	    y = row.pos("bl").adds(0, 4).y;
	}
	refreshUi();
    }

    private static Coord outer() {
	Coord csz = UI.scale(new Coord(560, 470));
	return new Coord(csz.x + MoonPanel.PAD * 2, csz.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2);
    }

    private static String text(String ru, String en) {
	return MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }

    private void toggleAutoDrink() {
	MoonConfig.setAutoDrink(!MoonConfig.autoDrink);
	MoonAutomationRegistry.noteAction("autodrink", MoonConfig.autoDrink ? "enabled" : "disabled");
	refreshUi();
    }

    private void toggleAutoDrop() {
	MoonConfig.setMineAutoDropEnabled(!MoonConfig.mineAutoDropEnabled);
	MoonAutomationRegistry.noteAction("autodrop", MoonConfig.mineAutoDropEnabled ? "enabled" : "disabled");
	refreshUi();
    }

    private void togglePassiveGate() {
	MoonConfig.setPassiveSmallGate(!MoonConfig.passiveSmallGate);
	MoonPassiveGate.resetStatus();
	MoonAutomationRegistry.noteAction("passivegate", MoonConfig.passiveSmallGate ? "enabled" : "disabled");
	refreshUi();
    }

    private void toggleBulkFill() {
	MoonConfig.setBulkStationFill(!MoonConfig.bulkStationFill);
	if(!MoonConfig.bulkStationFill)
	    MoonBulkStation.cancelBulkAutomation();
	MoonAutomationRegistry.noteAction("bulk", MoonConfig.bulkStationFill ? "fill on" : "fill off");
	refreshUi();
    }

    private void toggleBulkTake() {
	MoonConfig.setBulkStationTakeAll(!MoonConfig.bulkStationTakeAll);
	if(!MoonConfig.bulkStationTakeAll)
	    MoonBulkStation.cancelBulkAutomation();
	MoonAutomationRegistry.noteAction("bulk", MoonConfig.bulkStationTakeAll ? "take-all on" : "take-all off");
	refreshUi();
    }

    private void toggleLegacyRunner() {
	AutoRunner.setEnabled(!AutoRunner.isEnabled());
	MoonAutomationRegistry.noteAction("legacy", AutoRunner.isEnabled() ? "runner on" : "runner off");
	refreshUi();
    }

    private void clearLegacyQueue() {
	TaskQueue.clear();
	MoonAutomationRegistry.noteAction("legacy", "queue cleared");
	if(gui != null && gui.ui != null)
	    gui.ui.msg(text("Legacy-очередь очищена.", "Legacy queue cleared."), Color.WHITE, null);
	refreshUi();
    }

    private void resetAll() {
	MoonCombatBot.reset();
	MoonTreeChopBot.resetState();
	MoonMineBot.resetState();
	MoonFishingBot.reset();
	MoonPassiveGate.resetStatus();
	MoonBulkStation.cancelBulkAutomation();
	MoonAutoDrink.clearPendingFlower();
	TaskQueue.clear();
	MoonAutomationRegistry.resetAllRuntime();
	if(gui != null && gui.ui != null)
	    gui.ui.msg(text("Состояния automation сброшены.", "Automation states reset."), Color.WHITE, null);
	refreshUi();
    }

    private void refreshButtons() {
	autoDrinkBtn.change(text("Автопитьё: ", "AutoDrink: ") + (MoonConfig.autoDrink ? text("вкл", "on") : text("выкл", "off")));
	autoDropBtn.change(text("Автодроп: ", "AutoDrop: ") + (MoonConfig.mineAutoDropEnabled ? text("вкл", "on") : text("выкл", "off")));
	passiveGateBtn.change(text("Калитка: ", "Gate: ") + (MoonConfig.passiveSmallGate ? text("вкл", "on") : text("выкл", "off")));
	bulkFillBtn.change(text("Bulk fill: ", "Bulk fill: ") + (MoonConfig.bulkStationFill ? text("вкл", "on") : text("выкл", "off")));
	bulkTakeBtn.change(text("Take-all: ", "Take-all: ") + (MoonConfig.bulkStationTakeAll ? text("вкл", "on") : text("выкл", "off")));
	legacyRunnerBtn.change(text("Legacy runner: ", "Legacy runner: ") + (AutoRunner.isEnabled() ? text("вкл", "on") : text("выкл", "off")));
    }

    private void refreshUi() {
	refreshButtons();
	List<MoonAutomationRegistry.Snapshot> snaps = MoonAutomationRegistry.snapshots(gui);
	for(MoonAutomationRegistry.Snapshot snap : snaps) {
	    Label row = rows.get(snap.id);
	    if(row == null)
		continue;
	    String text = snap.title + " [" + snap.source + "] " + snap.status;
	    if(snap.lastAction != null && !snap.lastAction.isBlank())
		text += " | last: " + snap.lastAction;
	    row.settext(text);
	    if(!snap.available)
		row.setcolor(new Color(235, 120, 120));
	    else if(snap.active)
		row.setcolor(new Color(140, 220, 160));
	    else if(snap.enabled)
		row.setcolor(new Color(225, 210, 140));
	    else
		row.setcolor(new Color(175, 175, 190));
	}
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
	if(gui != null && gui.moonAutomationToolsWnd == this)
	    gui.moonAutomationToolsWnd = null;
	super.remove();
    }
}
