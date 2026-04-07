package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class MoonMineBotWnd extends MoonChatStylePanel {
    private final GameUI gui;
    private Button runBtn;
    private CheckBox labelsBox;
    private MoonDropdownMenu sessionMenu;
    private Tabs tabs;
    private Tabs.Tab tunnelTab;
    private Tabs.Tab sapperTab;
    private PreviewGrid tunnelPreview;
    private PreviewGrid sapperPreview;
    private Label supportLbl;
    private Label dirLblTunnel;
    private Label dirLblSapper;
    private Button sessBtn;

    public MoonMineBotWnd(GameUI gui) {
        super(mineOuterSz(), "moon-minebot", mineText("Копка / сапёр", "Mining / sapper"));
        this.gui = gui;
        setMinSize(UI.scale(new Coord(460, 580)));

        int x = UI.scale(10);
        int y = UI.scale(10);
        Coord base = contentOffset().add(x, y);

        Button tunnelBtn = add(new Button(UI.scale(180), mineText("Туннелирование", "Tunneling")), base);
        Button sapperBtn = add(new Button(UI.scale(180), mineText("Сапёр", "Sapper")), tunnelBtn.pos("ur").adds(10, 0));

        tabs = new Tabs(base.add(0, UI.scale(38)), UI.scale(new Coord(420, 480)), this);
        tunnelTab = tabs.add();
        sapperTab = tabs.add();
        tunnelBtn.action(() -> switchTab(MoonMineBot.MODE_TUNNEL));
        sapperBtn.action(() -> switchTab(MoonMineBot.MODE_SAPPER));

        buildTunnelTab();
        buildSapperTab();

        Coord common = tabs.c.add(0, tabs.sz.y + UI.scale(8));
        runBtn = add(new Button(UI.scale(420), runBtnLabel(), this::toggleRun), common);
        Widget prev = runBtn;

        prev = add(new CheckBox(mineText("При старте: показывать метки сапёра", "On start: show sapper labels"), true) {
            { a = MoonConfig.mineBotAutoHighlight; }
            public void set(boolean val) { MoonConfig.setMineBotAutoHighlight(val); a = val; }
        }, prev.pos("bl").adds(0, 8).x(common.x));
        labelsBox = add(new CheckBox(mineText("Показывать метки сапёра", "Show sapper labels"), true) {
            { a = MoonConfig.mineSweeperShowLabels; }
            public void set(boolean val) { MoonConfig.setMineSweeperShowLabels(val); a = val; }
        }, prev.pos("bl").adds(0, 6).x(common.x));
        prev = labelsBox;
        prev = add(new CheckBox(mineText("Избегать мобов", "Avoid mobs"), true) {
            { a = MoonConfig.mineBotAvoidMobs; }
            public void set(boolean val) { MoonConfig.setMineBotAvoidMobs(val); a = val; }
        }, prev.pos("bl").adds(0, 6).x(common.x));
        prev = add(new CheckBox(mineText("Автопитьё (стамина)", "Auto drink (stamina)"), true) {
            { a = MoonConfig.mineBotWater; }
            public void set(boolean val) { MoonConfig.setMineBotWater(val); a = val; }
        }, prev.pos("bl").adds(0, 6).x(common.x));
        prev = add(new CheckBox(mineText("Статус над персонажем", "Status above player"), true) {
            { a = MoonConfig.mineBotHudLine; }
            public void set(boolean val) { MoonConfig.setMineBotHudLine(val); a = val; }
        }, prev.pos("bl").adds(0, 6).x(common.x));
        prev = add(new CheckBox(mineText("Лог в консоль (диагностика копки)", "Console log (mine bot diagnostics)"), true) {
            { a = MoonConfig.mineBotDiagLog; }
            public void set(boolean val) { MoonConfig.setMineBotDiagLog(val); a = val; }
        }, prev.pos("bl").adds(0, 6).x(common.x));
        prev = add(new Button(UI.scale(420), mineText("Настройки оверлея и текста...", "Overlay and text settings..."),
            gui::openOverlayPanel), prev.pos("bl").adds(0, 6).x(common.x));
        prev = add(new Button(UI.scale(420), mineText("Автодроп по категориям...", "Auto-drop categories..."),
            gui::openMoonAutoDropWindow), prev.pos("bl").adds(0, 6).x(common.x));
        add(new Button(UI.scale(420), mineText("Сбросить текущую цель", "Reset current target"), () -> {
            MoonMineBot.resetState();
            gui.ui.msg(mineText("Состояние копки сброшено.", "Mining state cleared."), Color.WHITE, null);
        }), prev.pos("bl").adds(0, 6).x(common.x));

        switchTab(MoonConfig.mineBotMode);
        refreshUi();
    }

    private void buildTunnelTab() {
        Coord p = UI.scale(new Coord(0, 0));
        tunnelTab.add(new Label(mineText("Тип подпорки", "Support type")), p);
        supportLbl = tunnelTab.add(new Label(MoonMineBot.supportType().label()), p.add(0, UI.scale(20)));
        tunnelTab.add(new Button(UI.scale(30), "<", () -> cycleSupport(-1)), p.add(UI.scale(248), UI.scale(14)));
        tunnelTab.add(new Button(UI.scale(30), ">", () -> cycleSupport(1)), p.add(UI.scale(284), UI.scale(14)));

        tunnelTab.add(new Label(mineText("Направление", "Direction")), p.add(0, UI.scale(56)));
        dirLblTunnel = tunnelTab.add(new Label(MoonMineBot.directionLabel()), p.add(0, UI.scale(76)));
        addDirectionPad(tunnelTab, p.add(UI.scale(250), UI.scale(50)), this::setDirection);

        tunnelTab.add(new Label("Preview"), p.add(0, UI.scale(108)));
        tunnelPreview = tunnelTab.add(new PreviewGrid(false), p.add(0, UI.scale(128)));
        tunnelTab.add(new Label(mineText("Красное: подпорка", "Red: support")), p.add(UI.scale(236), UI.scale(138)));
        tunnelTab.add(new Label(mineText("Синее: тоннель", "Blue: tunnel")), p.add(UI.scale(236), UI.scale(158)));
        tunnelTab.add(new Label(mineText("Длина превью зависит от выбранного типа подпорки.", "Preview length changes with support type.")),
            p.add(UI.scale(236), UI.scale(188)));
        tunnelTab.add(new Label(mineText("Копка по компасу; пауза у следующей подпорки (туннель).", "Digs by compass; pauses at next support (tunnel).")),
            p.add(UI.scale(236), UI.scale(214)));
    }

    private void buildSapperTab() {
        Coord p = UI.scale(new Coord(0, 0));
        sapperTab.add(new Label(mineText("Направление сапёра", "Sapper direction")), p);
        dirLblSapper = sapperTab.add(new Label(MoonMineBot.directionLabel()), p.add(0, UI.scale(20)));
        addDirectionPad(sapperTab, p.add(UI.scale(250), UI.scale(2)), this::setDirection);

        sapperTab.add(new Label("Preview"), p.add(0, UI.scale(54)));
        Coord sapPreviewAt = p.add(0, UI.scale(74));
        sapperPreview = sapperTab.add(new PreviewGrid(true), sapPreviewAt);
        int legY = UI.scale(104);
        sapperTab.add(new Label(mineText("Синее: ход туннеля", "Blue: tunnel path")), p.add(UI.scale(236), legY));
        sapperTab.add(new Label(mineText("Зелёное: крылья сапёра", "Green: sapper wings")), p.add(UI.scale(236), legY + UI.scale(20)));
        sapperTab.add(new Label(mineText("Копка только по направлению компаса.", "Digs only along compass direction.")),
            p.add(UI.scale(236), legY + UI.scale(40)));

        Coord manualTop = sapPreviewAt.add(0, sapperPreview.sz.y + UI.scale(10));
        Widget prev = sapperTab.add(new Label(mineText("Ручная память сапёра", "Manual sapper memory")), manualTop);
        Button safeBtn = sapperTab.add(new Button(UI.scale(205), mineText("Отметить безопасные...", "Mark safe..."),
            () -> pickMark(MoonMineSweeperData.SAFE, "minebot.mark.safe.done")), prev.pos("bl").adds(0, 4));
        sapperTab.add(new Button(UI.scale(205), mineText("Отметить обвальные...", "Mark collapse..."),
            () -> pickMark(MoonMineSweeperData.RISK, "minebot.mark.risk.done")), safeBtn.pos("ur").adds(10, 0));
        Button clearRiskBtn = sapperTab.add(new Button(UI.scale(420), mineText("Сбросить обвальные метки", "Reset collapse marks"), this::resetRiskMarks),
            safeBtn.pos("bl").adds(0, 8));
        Button clearAllBtn = sapperTab.add(new Button(UI.scale(420), mineText("Сбросить все метки", "Reset all marks"), this::resetAllMarks),
            clearRiskBtn.pos("bl").adds(0, 6));
        sessBtn = sapperTab.add(new Button(UI.scale(420), mineText("Сессия...", "Session..."), this::toggleSessionMenu),
            clearAllBtn.pos("bl").adds(0, 6));
        sapperTab.add(new Button(UI.scale(420), mineText("Скрыть только отображение", "Hide labels only"), this::resetDisplayOnly),
            sessBtn.pos("bl").adds(0, 6));
    }

    private void addDirectionPad(Widget parent, Coord base, IntConsumer setter) {
        parent.add(new Button(UI.scale(34), "N", () -> setter.accept(MoonMineBot.DIR_N)), base.add(UI.scale(38), 0));
        parent.add(new Button(UI.scale(34), "W", () -> setter.accept(MoonMineBot.DIR_W)), base.add(0, UI.scale(38)));
        parent.add(new Button(UI.scale(34), "E", () -> setter.accept(MoonMineBot.DIR_E)), base.add(UI.scale(76), UI.scale(38)));
        parent.add(new Button(UI.scale(34), "S", () -> setter.accept(MoonMineBot.DIR_S)), base.add(UI.scale(38), UI.scale(76)));
    }

    private void switchTab(int mode) {
        MoonConfig.setMineBotMode(mode);
        if(tabs != null)
            tabs.showtab((mode == MoonMineBot.MODE_SAPPER) ? sapperTab : tunnelTab);
        refreshUi();
    }

    private void cycleSupport(int delta) {
        int cur = MoonConfig.mineBotSupportType + delta;
        if(cur < 0)
            cur = 3;
        if(cur > 3)
            cur = 0;
        MoonConfig.setMineBotSupportType(cur);
        refreshUi();
    }

    private void setDirection(int dir) {
        MoonConfig.setMineBotDirection(dir);
        refreshUi();
    }

    private void refreshUi() {
        if(runBtn != null)
            runBtn.change(runBtnLabel());
        if(supportLbl != null)
            supportLbl.settext(MoonMineBot.supportType().label());
        if(dirLblTunnel != null)
            dirLblTunnel.settext(MoonMineBot.directionLabel());
        if(dirLblSapper != null)
            dirLblSapper.settext(MoonMineBot.directionLabel());
        if(tunnelPreview != null)
            tunnelPreview.redraw();
        if(sapperPreview != null)
            sapperPreview.redraw();
    }

    private static String runBtnLabel() {
        return MoonConfig.mineBotEnabled
            ? mineText("Остановить", "Stop")
            : ((MoonConfig.mineBotMode == MoonMineBot.MODE_SAPPER)
                ? mineText("Запустить сапёра", "Start sapper")
                : mineText("Запустить туннелирование", "Start tunneling"));
    }

    private void toggleRun() {
        if(MoonConfig.mineBotEnabled) {
            MoonConfig.setMineBotEnabled(false);
            refreshUi();
            return;
        }
        if(MoonConfig.mineBotAutoHighlight)
            MoonConfig.setMineSweeperShowLabels(true);
        MoonMineBot.resetState();
        MoonConfig.setMineBotEnabled(true);
        if(labelsBox != null)
            labelsBox.set(MoonConfig.mineSweeperShowLabels);
        refreshUi();
    }

    private void pickMark(byte state, String doneKey) {
        if(gui.map == null)
            return;
        gui.map.startClientTileSelect(null, ar -> {
            if(ar == null || !ar.positive())
                return;
            MoonMineSweeperData.markArea(ar, state);
            MoonConfig.setMineSweeperShowLabels(true);
            if(labelsBox != null)
                labelsBox.set(true);
            gui.ui.msg(String.format(LocalizationManager.tr(doneKey), ar.ul.x, ar.ul.y, ar.br.x - 1, ar.br.y - 1), Color.WHITE, null);
        }, false);
    }

    private void resetDisplayOnly() {
        MoonConfig.setMineSweeperShowLabels(false);
        if(labelsBox != null)
            labelsBox.set(false);
        gui.ui.msg(mineText("Отображение скрыто, память сохранена.", "Labels hidden, memory kept."), Color.WHITE, null);
    }

    private void resetRiskMarks() {
        MoonMineSweeperData.resetRiskMarks();
        gui.ui.msg(mineText("Обвальные метки сброшены.", "Collapse marks cleared."), Color.WHITE, null);
    }

    private void resetAllMarks() {
        MoonMineSweeperData.clearAll();
        gui.ui.msg(mineText("Все метки сессии сброшены.", "All session marks cleared."), Color.WHITE, null);
    }

    private void toggleSessionMenu() {
        if(sessionMenu != null) {
            sessionMenu.destroy();
            sessionMenu = null;
            return;
        }
        Coord anchor = (sessBtn != null)
            ? MoonDropdownMenu.toRoot(sessBtn, new Coord(0, sessBtn.sz.y))
            : MoonDropdownMenu.toRoot(this, Coord.of(contentOffset().x + UI.scale(428), contentOffset().y + UI.scale(520)));
        sessionMenu = MoonDropdownMenu.popup(this, anchor, buildSessionEntries(), () -> sessionMenu = null);
    }

    private List<MoonDropdownMenu.Entry> buildSessionEntries() {
        List<MoonDropdownMenu.Entry> entries = new ArrayList<>();
        entries.add(MoonDropdownMenu.Entry.action(() -> mineText("Сохранить эту сессию", "Save this session"), () -> {
            int n = MoonMineSweeperData.saveSessionSnapshot();
            gui.ui.msg((n >= 0) ? mineText("Сессия сапёра сохранена.", "Mine session saved.")
                : mineText("Операция сессии не удалась.", "Session operation failed."), Color.WHITE, null);
        }));
        entries.add(MoonDropdownMenu.Entry.action(() -> mineText("Загрузить сохранённую сессию", "Load saved session"), () -> {
            int n = MoonMineSweeperData.loadSessionSnapshot();
            gui.ui.msg((n >= 0) ? mineText("Сессия сапёра загружена.", "Mine session loaded.")
                : mineText("Операция сессии не удалась.", "Session operation failed."), Color.WHITE, null);
            if(n >= 0)
                ensureLabelsShown();
        }));
        entries.add(MoonDropdownMenu.Entry.action(() -> mineText("Экспорт в буфер", "Export to clipboard"), () -> {
            boolean ok = MoonMineSweeperData.exportSnapshotToClipboard();
            gui.ui.msg(ok ? mineText("Сессия скопирована в буфер.", "Session copied to clipboard.")
                : mineText("Операция сессии не удалась.", "Session operation failed."), Color.WHITE, null);
        }));
        entries.add(MoonDropdownMenu.Entry.action(() -> mineText("Импорт из буфера", "Import from clipboard"), () -> {
            int n = MoonMineSweeperData.importSnapshotFromClipboard();
            gui.ui.msg((n >= 0) ? mineText("Сессия импортирована из буфера.", "Session imported from clipboard.")
                : mineText("Операция сессии не удалась.", "Session operation failed."), Color.WHITE, null);
            if(n >= 0)
                ensureLabelsShown();
        }));
        entries.add(MoonDropdownMenu.Entry.separator());
        entries.add(MoonDropdownMenu.Entry.action(
            () -> MoonConfig.mineSweeperShowLabels ? mineText("Скрыть метки", "Hide marks") : mineText("Показать метки", "Show marks"),
            () -> {
                MoonConfig.setMineSweeperShowLabels(!MoonConfig.mineSweeperShowLabels);
                if(labelsBox != null)
                    labelsBox.set(MoonConfig.mineSweeperShowLabels);
            }));
        return entries;
    }

    private void ensureLabelsShown() {
        MoonConfig.setMineSweeperShowLabels(true);
        if(labelsBox != null)
            labelsBox.set(true);
    }

    private static Coord mineOuterSz() {
        Coord csz = UI.scale(new Coord(460, 800));
        return Coord.of(csz.x + MoonPanel.PAD * 2, csz.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2);
    }

    @Override
    protected void added() {
        if(Utils.getprefc("wpos-moon-minebot", null) == null) {
            Coord leg = Utils.getprefc("wndc-moon-minebot", null);
            if(leg != null)
                Utils.setprefc("wpos-moon-minebot", leg);
        }
        super.added();
    }

    @Override
    protected void onCloseClicked() {
        reqdestroy();
    }

    @Override
    public void remove() {
        if(gui != null && gui.moonMineBotWnd == this)
            gui.moonMineBotWnd = null;
        Utils.setprefc("wndc-moon-minebot", c);
        super.remove();
    }

    private static String mineText(String ru, String en) {
        return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }

    private static final class PreviewGrid extends Widget {
        private static final int GRID = 13;
        private static final Coord CELL = UI.scale(new Coord(16, 16));
        private static final Color BG = new Color(22, 24, 30, 230);
        private static final Color GRID_C = new Color(92, 96, 110, 100);
        private static final Color TUNNEL_C = new Color(92, 168, 255, 220);
        private static final Color SUPPORT_C = new Color(224, 110, 110, 230);
        private static final Color WING_C = new Color(114, 214, 150, 220);
        private static final Color ARROW_C = new Color(255, 224, 112);
        private final boolean sapper;

        private PreviewGrid(boolean sapper) {
            super(Coord.of(CELL.x * GRID + 2, CELL.y * GRID * 13 / 13 + 2));
            this.sapper = sapper;
        }

        public void redraw() {
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(BG);
            g.frect(Coord.z, sz);
            g.chcolor(new Color(214, 172, 82));
            g.rect(Coord.z, sz.sub(1, 1));
            g.chcolor();
            for(int yy = 0; yy < GRID; yy++) {
                for(int xx = 0; xx < GRID; xx++) {
                    Coord p = Coord.of(1 + xx * CELL.x, 1 + yy * CELL.y);
                    g.chcolor(GRID_C);
                    g.rect(p, CELL);
                }
            }
            for(Coord c : MoonMineBot.previewTunnelCells())
                fillLogical(g, c, TUNNEL_C);
            for(Coord c : MoonMineBot.previewSupportCells())
                fillLogical(g, c, SUPPORT_C);
            if(sapper) {
                for(Coord c : MoonMineBot.previewWingCells())
                    fillLogical(g, c, WING_C);
            }
            drawArrow(g);
            g.chcolor();
        }

        /** Compass direction: tail one cell behind tunnel start, tip two steps forward (matches preview tunnel). */
        private void drawArrow(GOut g) {
            Coord off = MoonMineBot.previewTunnelAnchor();
            Coord f = MoonMineBot.previewForward();
            Coord tail = cellPos(off.sub(f)).add(CELL.div(2));
            Coord tip = cellPos(off.add(f.mul(2))).add(CELL.div(2));
            g.chcolor(ARROW_C);
            int aw = UI.scale(2);
            g.line(tail, tip, aw);
            int h = UI.scale(5);
            int dx = tip.x - tail.x, dy = tip.y - tail.y;
            if(dx != 0 || dy != 0) {
                if(Math.abs(dx) >= Math.abs(dy)) {
                    int s = Integer.signum(dx);
                    g.line(tip, tip.add(Coord.of(-s * h, -h)), aw);
                    g.line(tip, tip.add(Coord.of(-s * h, h)), aw);
                } else {
                    int s = Integer.signum(dy);
                    g.line(tip, tip.add(Coord.of(-h, -s * h)), aw);
                    g.line(tip, tip.add(Coord.of(h, -s * h)), aw);
                }
            }
            g.chcolor();
        }

        private void fillLogical(GOut g, Coord logical, Color col) {
            Coord p = cellPos(logical);
            g.chcolor(col);
            g.frect(p.add(1, 1), CELL.sub(2, 2));
            g.chcolor();
        }

        private Coord cellPos(Coord logical) {
            int cx = GRID / 2;
            int by = GRID - 2;
            int gx = cx + logical.x;
            int gy = by - logical.y;
            return Coord.of(1 + gx * CELL.x, 1 + gy * CELL.y);
        }
    }
}
