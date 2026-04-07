package haven;

import config.ClientConfig;
import config.KeybindConfig;
import config.ModuleConfig;
import core.Logger;
import core.ModuleLoader;
import managers.automation.AutoRunner;
import managers.automation.TaskQueue;
import managers.combat.CombatAnalyzer;
import managers.combat.CombatManager;
import managers.resource.ResourceScanner;
import managers.ui.UIEnhancer;
import modules.Module;

import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MoonwideWnd extends Window {
    private static final Coord DEFSZ = UI.scale(new Coord(620, 500));
    private static final Coord TAB_C = UI.scale(new Coord(12, 76));
    private static final Coord TAB_SZ = UI.scale(new Coord(580, 385));
    private static final int TAB_BTN_W = UI.scale(108);
    private static final int ACTION_W = UI.scale(254);
    private static final int INPUT_W = UI.scale(90);
    private static final int INPUT_X = UI.scale(360);
    private static final int GAP = UI.scale(8);

    private static final Color ACCENT = new Color(210, 185, 120);
    private static final Color MUTED = new Color(180, 170, 190);
    private static final Color GOOD = new Color(140, 200, 160);
    private static final Color WARN = new Color(220, 195, 120);
    private static final Color BAD = new Color(200, 120, 130);

    private static final Text.Foundry TITLE = new Text.Foundry(Text.sans.deriveFont(Font.BOLD, UI.scale(16f))).aa(true);
    private static final Text.Foundry SECTION = new Text.Foundry(Text.sans.deriveFont(Font.BOLD, UI.scale(13f))).aa(true);

    private final Tabs tabs;
    private final Map<Tabs.Tab, NavButton> nav = new LinkedHashMap<>();

    private Label overviewState;
    private Label overviewModules;
    private Label overviewQueue;
    private Label overviewQueueHead;
    private Label botsState;
    private Label visualState;
    private Label combatState;
    private Label settingsState;
    private double refreshacc;

    private class NavButton extends Tabs.TabButton {
        private final String base;

        private NavButton(String base, Tabs.Tab tab) {
            tabs.super(TAB_BTN_W, base, tab);
            this.base = base;
        }

        private void sync() {
            change((tabs.curtab == tab) ? ("> " + base) : base);
        }
    }

    public MoonwideWnd() {
        super(DEFSZ, LocalizationManager.tr("mcc.title"), true);

        Label title = add(new Label(LocalizationManager.tr("mcc.title"), TITLE), 0, 0);
        title.setcolor(ACCENT);
        Label subtitle = add(new Label(LocalizationManager.tr("mcc.subtitle"), UI.scale(560)), 0, title.pos("bl").adds(0, 4).y);
        subtitle.setcolor(MUTED);

        tabs = new Tabs(TAB_C, TAB_SZ, this) {
            public void changed(Tab from, Tab to) {
                updateNav();
            }
        };

        buildOverview(addTab(LocalizationManager.tr("mcc.tab.overview"), 0));
        buildBots(addTab(LocalizationManager.tr("mcc.tab.bots"), 1));
        buildVisual(addTab(LocalizationManager.tr("mcc.tab.visual"), 2));
        buildCombat(addTab(LocalizationManager.tr("mcc.tab.combat"), 3));
        buildSettings(addTab(LocalizationManager.tr("mcc.tab.settings"), 4));

        updateNav();
        refreshStatus();
    }

    public void show() {
        refreshStatus();
        super.show();
        raise();
    }

    public void tick(double dt) {
        super.tick(dt);
        refreshacc += dt;
        if(refreshacc >= 0.25) {
            refreshacc = 0;
            refreshStatus();
        }
    }

    private Tabs.Tab addTab(String title, int index) {
        Tabs.Tab tab = tabs.add();
        NavButton button = add(new NavButton(title, tab), TAB_C.x + (index * (TAB_BTN_W + GAP)), UI.scale(42));
        nav.put(tab, button);
        return(tab);
    }

    private void updateNav() {
        for(NavButton button : nav.values())
            button.sync();
    }

    private Scrollport scrolltab(Tabs.Tab tab) {
        return(tab.add(new Scrollport(tab.sz), Coord.z));
    }

    private int addSection(Widget cont, String title, String text, int y) {
        Label head = cont.add(new Label(title, SECTION), 0, y);
        head.setcolor(ACCENT);
        y = head.pos("bl").adds(0, 3).y;
        if((text != null) && (text.length() > 0)) {
            Label desc = cont.add(new Label(text, UI.scale(520)), 0, y);
            desc.setcolor(MUTED);
            y = desc.pos("bl").adds(0, 8).y;
        } else {
            y += UI.scale(8);
        }
        return(y);
    }

    private int addModuleToggle(Widget cont, String moduleName, String label, String description, int y) {
        CheckBox box = new CheckBox(label);
        box.state(() -> moduleEnabled(moduleName));
        box.set(val -> setModuleEnabled(moduleName, val));
        box.settip(description, true);
        cont.add(box, 0, y);
        Label desc = cont.add(new Label(description, UI.scale(520)), UI.scale(24), y + UI.scale(2));
        desc.setcolor(MUTED);
        return(Math.max(box.c.y + box.sz.y, desc.c.y + desc.sz.y) + UI.scale(6));
    }

    private int addActionRow(Widget cont, int y, Button left, Button right) {
        Coord br = cont.addhlp(new Coord(0, y), GAP, left, right);
        return(br.y + UI.scale(8));
    }

    private Button action(String label, Runnable action) {
        return(new Button(ACTION_W, label, false).action(action));
    }

    private int addSetting(Widget cont, String label, String text, TextEntry entry, Runnable apply, int y) {
        Label head = cont.add(new Label(label), 0, y);
        Button applybtn = new Button(UI.scale(82), LocalizationManager.tr("mcc.apply"), false).action(apply);
        cont.add(entry, INPUT_X, y - UI.scale(2));
        cont.add(applybtn, INPUT_X + entry.sz.x + UI.scale(6), y - UI.scale(2));
        Label desc = cont.add(new Label(text, UI.scale(520)), 0, head.pos("bl").adds(0, 2).y);
        desc.setcolor(MUTED);
        return(Math.max(desc.c.y + desc.sz.y, applybtn.c.y + applybtn.sz.y) + UI.scale(6));
    }

    private void buildOverview(Tabs.Tab tab) {
        Scrollport port = scrolltab(tab);
        Widget cont = port.cont;
        int y = 0;

        y = addSection(cont, LocalizationManager.tr("mcc.ov.section"), LocalizationManager.tr("mcc.ov.desc"), y);
        overviewState = cont.add(new Label(""), 0, y);
        overviewState.setcolor(GOOD);
        y = overviewState.pos("bl").adds(0, 3).y;
        overviewModules = cont.add(new Label(""), 0, y);
        overviewModules.setcolor(WARN);
        y = overviewModules.pos("bl").adds(0, 3).y;
        overviewQueue = cont.add(new Label(""), 0, y);
        overviewQueue.setcolor(Color.WHITE);
        y = overviewQueue.pos("bl").adds(0, 3).y;
        overviewQueueHead = cont.add(new Label(""), 0, y);
        overviewQueueHead.setcolor(MUTED);
        y = overviewQueueHead.pos("bl").adds(0, 10).y;

        y = addSection(cont, LocalizationManager.tr("mcc.qa.section"), LocalizationManager.tr("mcc.qa.desc"), y);
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.timers"), this::openTimers), action(LocalizationManager.tr("mcc.btn.accounts"), this::openSavedAccounts));
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.highlight"), this::openHighlightManager), action(LocalizationManager.tr("mcc.btn.hidden"), this::openHiddenManager));
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.deleted"), this::openDeletedManager), action(LocalizationManager.tr("mcc.btn.gameopts"), this::openOptions));

        y = addSection(cont, LocalizationManager.tr("mcc.shortcuts.section"), LocalizationManager.tr("mcc.shortcuts.desc"), y);
        for(Map.Entry<String, String> bind : KeybindConfig.getAll().entrySet()) {
            Label hotkey = cont.add(new Label(KeybindConfig.getLabel(bind.getKey()) + ": " + bind.getValue()), 0, y);
            hotkey.setcolor(MUTED);
            y = hotkey.pos("bl").adds(0, 2).y;
        }
        y += UI.scale(6);

        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.savecfg"), ClientConfig::save), action(LocalizationManager.tr("mcc.btn.closepanel"), this::hide));
    }

    private void buildBots(Tabs.Tab tab) {
        Scrollport port = scrolltab(tab);
        Widget cont = port.cont;
        int y = 0;

        y = addSection(cont, LocalizationManager.tr("mcc.bots.section"), LocalizationManager.tr("mcc.bots.desc"), y);
        y = addModuleToggle(cont, "Auto Drink/Eat", LocalizationManager.tr("mcc.lbl.autodrink"), LocalizationManager.tr("mcc.desc.autodrink"), y);
        y = addModuleToggle(cont, "Task Queue", LocalizationManager.tr("mcc.lbl.taskq"), LocalizationManager.tr("mcc.desc.taskq"), y);
        botsState = cont.add(new Label(""), 0, y);
        botsState.setcolor(GOOD);
        y = botsState.pos("bl").adds(0, 10).y;

        final TextEntry drink = new TextEntry(INPUT_W, formatDouble(ModuleConfig.getOptionDouble("Auto Drink/Eat", "drinkThreshold", 0.30))) {
            public void activate(String text) {
                applyRatio(this, "Auto Drink/Eat", "drinkThreshold", "mcc.msg.drink_saved", "mcc.err.drink_range");
            }
        };
        y = addSetting(cont, LocalizationManager.tr("mcc.drink_thr"), LocalizationManager.tr("mcc.drink_thr.desc"), drink, () -> applyRatio(drink, "Auto Drink/Eat", "drinkThreshold", "mcc.msg.drink_saved", "mcc.err.drink_range"), y);

        final TextEntry eat = new TextEntry(INPUT_W, formatDouble(ModuleConfig.getOptionDouble("Auto Drink/Eat", "eatThreshold", 0.30))) {
            public void activate(String text) {
                applyRatio(this, "Auto Drink/Eat", "eatThreshold", "mcc.msg.eat_saved", "mcc.err.eat_range");
            }
        };
        y = addSetting(cont, LocalizationManager.tr("mcc.eat_thr"), LocalizationManager.tr("mcc.eat_thr.desc"), eat, () -> applyRatio(eat, "Auto Drink/Eat", "eatThreshold", "mcc.msg.eat_saved", "mcc.err.eat_range"), y);

        y = addSection(cont, LocalizationManager.tr("mcc.queue.section"), LocalizationManager.tr("mcc.queue.desc"), y);
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.clearq"), () -> {
            TaskQueue.clear();
            refreshStatus();
            info(LocalizationManager.tr("mcc.msg.queue_cleared"));
        }), action(LocalizationManager.tr("mcc.btn.opentimers"), this::openTimers));
    }

    private void buildVisual(Tabs.Tab tab) {
        Scrollport port = scrolltab(tab);
        Widget cont = port.cont;
        int y = 0;

        y = addSection(cont, LocalizationManager.tr("mcc.vis.section"), LocalizationManager.tr("mcc.vis.desc"), y);
        y = addModuleToggle(cont, "Path Highlighter", LocalizationManager.tr("mcc.lbl.pathhl"), LocalizationManager.tr("mcc.desc.pathhl"), y);
        y = addModuleToggle(cont, "Resource Radar", LocalizationManager.tr("mcc.lbl.radar"), LocalizationManager.tr("mcc.desc.radar"), y);
        y = addModuleToggle(cont, "UI Cleaner", LocalizationManager.tr("mcc.lbl.uiclean"), LocalizationManager.tr("mcc.desc.uiclean"), y);

        CheckBox ridges = new CheckBox(LocalizationManager.tr("mcc.cb.ridges"));
        ridges.state(() -> DefSettings.puruspfignoreridge);
        ridges.set(val -> {
            Utils.setprefb("puruspfignoreridge", DefSettings.puruspfignoreridge = val);
        });
        cont.add(ridges, 0, y);
        y = ridges.pos("bl").adds(0, 4).y;

        CheckBox hideClutter = new CheckBox(LocalizationManager.tr("mcc.cb.hideclutter"));
        hideClutter.state(UIEnhancer::isHideClutter);
        hideClutter.set(UIEnhancer::setHideClutter);
        cont.add(hideClutter, 0, y);
        y = hideClutter.pos("bl").adds(0, 4).y;

        CheckBox compactBars = new CheckBox(LocalizationManager.tr("mcc.cb.compact"));
        compactBars.state(UIEnhancer::isCompactBars);
        compactBars.set(UIEnhancer::setCompactBars);
        cont.add(compactBars, 0, y);
        y = compactBars.pos("bl").adds(0, 8).y;

        visualState = cont.add(new Label(""), 0, y);
        visualState.setcolor(GOOD);
        y = visualState.pos("bl").adds(0, 10).y;

        final TextEntry radarDistance = new TextEntry(INPUT_W, formatDouble(ResourceScanner.getMaxDistance())) {
            public void activate(String text) {
                applyDistance(this);
            }
        };
        y = addSetting(cont, LocalizationManager.tr("mcc.radar_dist"), LocalizationManager.tr("mcc.radar_dist.desc"), radarDistance, () -> applyDistance(radarDistance), y);

        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.highlight"), this::openHighlightManager), action(LocalizationManager.tr("mcc.btn.hidden"), this::openHiddenManager));
    }

    private void buildCombat(Tabs.Tab tab) {
        Scrollport port = scrolltab(tab);
        Widget cont = port.cont;
        int y = 0;

        y = addSection(cont, LocalizationManager.tr("mcc.cmb.section"), LocalizationManager.tr("mcc.cmb.desc"), y);
        y = addModuleToggle(cont, "Combat Overlay", LocalizationManager.tr("mcc.lbl.combatov"), LocalizationManager.tr("mcc.desc.combatov"), y);
        y = addModuleToggle(cont, "Smart Hotkeys", LocalizationManager.tr("mcc.lbl.smarthk"), LocalizationManager.tr("mcc.desc.smarthk"), y);
        combatState = cont.add(new Label(""), 0, y);
        combatState.setcolor(GOOD);
        y = combatState.pos("bl").adds(0, 10).y;

        final TextEntry cooldown = new TextEntry(INPUT_W, Long.toString(CombatManager.getAttackCooldownMs())) {
            public void activate(String text) {
                applyCooldown(this);
            }
        };
        y = addSetting(cont, LocalizationManager.tr("mcc.cmb.cooldown"), LocalizationManager.tr("mcc.cmb.cooldown.desc"), cooldown, () -> applyCooldown(cooldown), y);

        y = addSection(cont, LocalizationManager.tr("mcc.cmb.views.section"), LocalizationManager.tr("mcc.cmb.views.desc"), y);
        for(Map.Entry<String, String> bind : KeybindConfig.getAll().entrySet()) {
            if(bind.getKey().startsWith("moonwide.toggle_")) {
                Label hotkey = cont.add(new Label(KeybindConfig.getLabel(bind.getKey()) + ": " + bind.getValue()), 0, y);
                hotkey.setcolor(MUTED);
                y = hotkey.pos("bl").adds(0, 2).y;
            }
        }
        y += UI.scale(6);
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.openopts"), this::openOptions), action(LocalizationManager.tr("mcc.btn.savecfg"), ClientConfig::save));
    }

    private void buildSettings(Tabs.Tab tab) {
        Scrollport port = scrolltab(tab);
        Widget cont = port.cont;
        int y = 0;

        y = addSection(cont, LocalizationManager.tr("mcc.set.section"), LocalizationManager.tr("mcc.set.desc"), y);

        CheckBox debug = new CheckBox(LocalizationManager.tr("mcc.cb.debug"));
        debug.state(ClientConfig::isDebugLog);
        debug.set(val -> {
            ClientConfig.set("debug", val);
            Logger.setDebugEnabled(val);
            ClientConfig.save();
            refreshStatus();
        });
        cont.add(debug, 0, y);
        y = debug.pos("bl").adds(0, 4).y;

        CheckBox dark = new CheckBox(LocalizationManager.tr("mcc.cb.dark"));
        dark.state(ClientConfig::isDarkTheme);
        dark.set(val -> {
            ClientConfig.setDarkTheme(val);
            ClientConfig.save();
            refreshStatus();
        });
        cont.add(dark, 0, y);
        y = dark.pos("bl").adds(0, 8).y;

        settingsState = cont.add(new Label(""), 0, y);
        settingsState.setcolor(GOOD);
        y = settingsState.pos("bl").adds(0, 10).y;

        y = addSection(cont, LocalizationManager.tr("mcc.surface.section"), LocalizationManager.tr("mcc.surface.desc"), y);
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.savecfg2"), ClientConfig::save), action(LocalizationManager.tr("mcc.btn.havenopts"), this::openOptions));
        y = addActionRow(cont, y, action(LocalizationManager.tr("mcc.btn.deleted"), this::openDeletedManager), action(LocalizationManager.tr("mcc.btn.closepanel"), this::hide));
    }

    private void refreshStatus() {
        int total = 0;
        int enabled = 0;
        for(Module module : ModuleLoader.getModules()) {
            total++;
            if(module.isEnabled())
                enabled++;
        }
        TaskQueue.Task next = TaskQueue.peek();
        String nextTask = (next == null) ? LocalizationManager.tr("mcc.idle") : next.name();
        String combatHint = CombatAnalyzer.getSuggestedAction();
        if((combatHint == null) || combatHint.length() == 0)
            combatHint = LocalizationManager.tr("opt.mooncombat.idle");

        if(overviewState != null) {
            overviewState.settext(String.format(LocalizationManager.tr("mcc.status.hooks"), KeybindConfig.get("moonwide.menu")));
            overviewState.setcolor(GOOD);
        }
        if(overviewModules != null) {
            overviewModules.settext(String.format(LocalizationManager.tr("mcc.status.modules"), enabled, total));
            overviewModules.setcolor((enabled > 0) ? WARN : BAD);
        }
        if(overviewQueue != null) {
            overviewQueue.settext(String.format(LocalizationManager.tr("mcc.status.automation"), TaskQueue.size(),
                AutoRunner.isEnabled() ? LocalizationManager.tr("mcc.runner.active") : LocalizationManager.tr("mcc.runner.sleep")));
        }
        if(overviewQueueHead != null) {
            overviewQueueHead.settext(String.format(LocalizationManager.tr("mcc.status.queuehead"), nextTask));
            overviewQueueHead.setcolor(MUTED);
        }
        if(botsState != null) {
            botsState.settext(String.format(LocalizationManager.tr("mcc.status.bots"), TaskQueue.size(), nextTask));
            botsState.setcolor((TaskQueue.size() > 0) ? WARN : GOOD);
        }
        if(visualState != null) {
            visualState.settext(String.format(LocalizationManager.tr("mcc.status.radar"), ResourceScanner.getResources().size(), formatDouble(ResourceScanner.getMaxDistance())));
            visualState.setcolor(GOOD);
        }
        if(combatState != null) {
            combatState.settext(String.format(LocalizationManager.tr("mcc.status.combat"), combatHint, CombatManager.getAttackCooldownMs()));
            combatState.setcolor(GOOD);
        }
        if(settingsState != null) {
            settingsState.settext(String.format(LocalizationManager.tr("mcc.status.settings"), onoff(ClientConfig.isDebugLog()), onoff(ClientConfig.isDarkTheme())));
            settingsState.setcolor(MUTED);
        }
    }

    private void setModuleEnabled(String moduleName, boolean enabled) {
        if(enabled) {
            ModuleLoader.enable(moduleName);
        } else {
            ModuleLoader.disable(moduleName);
        }
        refreshStatus();
    }

    private boolean moduleEnabled(String moduleName) {
        Module module = ModuleLoader.getModule(moduleName);
        return((module != null) && module.isEnabled());
    }

    private void applyRatio(TextEntry entry, String module, String key, String savedKey, String errKey) {
        try {
            double value = Double.parseDouble(entry.text().trim());
            if((value < 0.0) || (value > 1.0))
                throw(new NumberFormatException());
            ModuleConfig.setOption(module, key, String.valueOf(value));
            entry.settext(formatDouble(value));
            entry.commit();
            info(LocalizationManager.tr(savedKey));
        } catch(NumberFormatException e) {
            error(LocalizationManager.tr(errKey));
        }
    }

    private void applyDistance(TextEntry entry) {
        try {
            double value = Double.parseDouble(entry.text().trim());
            if(value <= 0)
                throw(new NumberFormatException());
            ResourceScanner.setMaxDistance(value);
            entry.settext(formatDouble(value));
            entry.commit();
            refreshStatus();
            info(LocalizationManager.tr("mcc.msg.radar_saved"));
        } catch(NumberFormatException e) {
            error(LocalizationManager.tr("mcc.err.radar_pos"));
        }
    }

    private void applyCooldown(TextEntry entry) {
        try {
            long value = Long.parseLong(entry.text().trim());
            if(value <= 0)
                throw(new NumberFormatException());
            CombatManager.setAttackCooldownMs(value);
            entry.settext(Long.toString(value));
            entry.commit();
            refreshStatus();
            info(LocalizationManager.tr("mcc.msg.cooldown_saved"));
        } catch(NumberFormatException e) {
            error(LocalizationManager.tr("mcc.err.cooldown_pos"));
        }
    }

    private void openTimers() {
        GameUI gui = getparent(GameUI.class);
        if((gui != null) && (gui.timerswnd != null))
            gui.timerswnd.show();
    }

    private void openSavedAccounts() {
        GameUI gui = getparent(GameUI.class);
        if(gui == null)
            return;
        SavedAccountsWnd wnd = gui.getchild(SavedAccountsWnd.class);
        if(wnd == null)
            gui.add(wnd = new SavedAccountsWnd(), UI.scale(new Coord(200, 150)));
        wnd.show();
    }

    private void openHighlightManager() {
        GameUI gui = getparent(GameUI.class);
        if(gui == null)
            return;
        haven.sloth.gui.HighlightManager wnd = gui.getchild(haven.sloth.gui.HighlightManager.class);
        if(wnd == null)
            gui.add(wnd = new haven.sloth.gui.HighlightManager(), UI.scale(new Coord(220, 250)));
        wnd.show();
    }

    private void openDeletedManager() {
        GameUI gui = getparent(GameUI.class);
        if(gui == null)
            return;
        haven.sloth.gui.DeletedManager wnd = gui.getchild(haven.sloth.gui.DeletedManager.class);
        if(wnd == null)
            gui.add(wnd = new haven.sloth.gui.DeletedManager(), UI.scale(new Coord(220, 150)));
        wnd.show();
    }

    private void openHiddenManager() {
        GameUI gui = getparent(GameUI.class);
        if(gui == null)
            return;
        haven.sloth.gui.HiddenManager wnd = gui.getchild(haven.sloth.gui.HiddenManager.class);
        if(wnd == null)
            gui.add(wnd = new haven.sloth.gui.HiddenManager(), UI.scale(new Coord(220, 250)));
        wnd.show();
    }

    private void openOptions() {
        GameUI gui = getparent(GameUI.class);
        if((gui != null) && (gui.opts != null))
            gui.opts.show();
    }

    private void info(String msg) {
        if(ui != null)
            ui.msg(msg);
    }

    private void error(String msg) {
        if(ui != null)
            ui.error(msg);
    }

    private String formatDouble(double value) {
        String text = String.format(Locale.US, "%.2f", value);
        while(text.endsWith("0"))
            text = text.substring(0, text.length() - 1);
        if(text.endsWith("."))
            text = text + "0";
        return(text);
    }

    private String onoff(boolean value) {
        return(value ? LocalizationManager.tr("mcc.state.on") : LocalizationManager.tr("mcc.state.off"));
    }
}
