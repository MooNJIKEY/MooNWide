package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.function.Consumer;

/**
 * Floating onboarding guide for MooNWide UI: short sections plus live buttons
 * that open the real panels so players can learn the layout by trying it.
 */
public class MoonUiGuideWnd extends MoonChatStylePanel {
    public static final String GUIDE_SEEN_PREF = "moonwide-guide-v3-seen";
    private static final int PAD = UI.scale(8);
    private static final int CLOSE_H = UI.scale(32);
    private static final Text.Foundry SECTF = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(15f))).aa(true);
    private static final Text.Foundry BODYF = new Text.Foundry(Text.sans, UI.scale(12)).aa(true);

    public final GameUI gui;
    private final Scrollport scroll;

    public MoonUiGuideWnd(GameUI gui) {
	super(guideOuterSz(), "moon-ui-guide", LocalizationManager.tr("guide.title"));
	this.gui = gui;
	setMinSize(UI.scale(new Coord(320, 260)));
	setResizable(false);
	Coord co = contentOffset();
	Coord inner = contentSize();
	scroll = add(new Scrollport(Coord.of(inner.x, inner.y - CLOSE_H - PAD)), co);
	scroll.cont.add(new GuideBody(scroll.cont.sz.x), Coord.z);
	add(new Button(inner.x, LocalizationManager.tr("guide.ok"), false).action(this::closeGuide),
	    co.add(0, inner.y - CLOSE_H));
    }

    private static Coord guideOuterSz() {
	Coord csz = UI.scale(new Coord(560, 640));
	return(new Coord(csz.x + MoonPanel.PAD * 2, csz.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2));
    }

    private static String loc(String en, String ru) {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? ru : en);
    }

    private void openOpt(Consumer<OptWnd> open) {
	if(gui == null || gui.opts == null || open == null)
	    return;
	gui.opts.show();
	gui.opts.raise();
	open.accept(gui.opts);
	gui.fitwdg(gui.opts);
	gui.setfocus(gui.opts);
    }

    public void closeGuide() {
	Utils.setprefb(GUIDE_SEEN_PREF, true);
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {}
	if(gui != null && gui.moonUiGuideWnd == this)
	    gui.moonUiGuideWnd = null;
	reqdestroy();
    }

    @Override
    protected void onCloseClicked() {
	closeGuide();
    }

    @Override
    public void remove() {
	if(gui != null && gui.moonUiGuideWnd == this)
	    gui.moonUiGuideWnd = null;
	super.remove();
    }

    private final class GuideBody extends Widget {
	private final int width;
	private int y = 0;

	private final class Action {
	    final String label;
	    final Runnable action;

	    Action(String label, Runnable action) {
		this.label = label;
		this.action = action;
	    }
	}

	GuideBody(int width) {
	    super(Coord.of(width, 1));
	    this.width = Math.max(UI.scale(260), width - UI.scale(6));
	    build();
	    resize(Coord.of(this.width, y));
	}

	private void build() {
	    addSection(
		loc("Start here", "С чего начать"),
		loc(
		    "If you are not sure where a feature lives, start with Settings. The buttons below open the exact client panels so you can learn the interface by using it.",
		    "Если не понимаешь, где искать функцию, начинай с Настроек. Кнопки ниже открывают реальные панели клиента, чтобы интерфейс можно было понять через действие, а не через длинный текст."));
	    y = addButtons(y,
		new Action(LocalizationManager.tr("shortcut.opts"), () -> openOpt(OptWnd::showMainPanel)),
		new Action(LocalizationManager.tr("opt.moonwide"), () -> openOpt(opt -> opt.chpanel(opt.moonfunctions))),
		new Action(LocalizationManager.tr("opt.overlay"), () -> openOpt(OptWnd::showOverlayPanel)),
		new Action(LocalizationManager.tr("opt.combat"), () -> openOpt(OptWnd::showCombatPanel)),
		new Action(LocalizationManager.tr("opt.performance"), () -> openOpt(OptWnd::showPerformancePanel)),
		new Action(LocalizationManager.tr("shortcut.bots"), () -> {
		    if(gui != null)
			gui.toggleMoonBotsWindow();
		}));

	    addSection(
		loc("Panel menu and unlock", "Меню панели и разблокировка"),
		loc(
		    "Every floating MooNWide window has a small menu button in the top-right of its header. Use it to lock or unlock the panel, and to close it cleanly.",
		    "У каждого плавающего окна MooNWide есть маленькая кнопка меню в правом верхнем углу шапки. Через неё можно заблокировать или разблокировать панель, а также аккуратно закрыть её."));
	    addBullet(loc("Lock a panel if it should stay in place while you click the world.", "Блокируй панель, если она должна оставаться на месте во время кликов по миру."));
	    addBullet(loc("Unlock it when you want to move it again.", "Разблокируй её, когда захочешь снова двигать окно."));
	    addBullet(loc("Try it on this guide window: open the menu below, then choose Lock or Unlock.", "Попробуй прямо на этом гайде: открой меню ниже, потом выбери Lock или Unlock."));
	    y = addButtons(y,
		new Action(loc("Show panel menu", "Показать меню панели"), () -> MoonUiGuideWnd.this.toggleHeaderMenu()),
		new Action(LocalizationManager.tr("guide.ok"), MoonUiGuideWnd.this::closeGuide));

	    addSection(
		loc("Common windows", "Частые окна"),
		loc(
		    "These are the windows most players look for first during normal play.",
		    "Это окна, которые игроки чаще всего ищут в обычной игре."));
	    y = addButtons(y,
		new Action(LocalizationManager.tr("shortcut.inv"), () -> {
		    if(gui != null)
			gui.toggleInventoryWindow();
		}),
		new Action(LocalizationManager.tr("shortcut.equ"), () -> {
		    if(gui != null)
			gui.toggleEquipmentWindow();
		}),
		new Action(LocalizationManager.tr("shortcut.map"), () -> {
		    if(gui != null)
			gui.toggleMinimapWindow();
		}),
		new Action(loc("World map", "Карта мира"), () -> {
		    if(gui != null)
			gui.toggleMapWindow();
		}),
		new Action(LocalizationManager.tr("shortcut.chat"), () -> {
		    if(gui != null)
			gui.toggleChatWindow();
		}),
		new Action(loc("Sessions", "Сессии"), () -> {
		    if(gui != null)
			gui.toggleSessionSwitcher();
		}));

	    addSection(
		loc("How to read the interface", "Как читать интерфейс"),
		loc(
		    "Left quick buttons are mostly character and map windows. Right quick buttons are systems: chat, overlay, combat, bots, settings.",
		    "Левые быстрые кнопки в основном про окна персонажа и карты. Правые быстрые кнопки — это системные вещи: чат, оверлей, бой, боты, настройки."));
	    addBullet(loc("World visuals and labels live in World Overlay.", "Все, что рисуется поверх мира, лежит в разделе Оверлей мира."));
	    addBullet(loc("Fight helpers and combat HUD live in Combat.", "Боевые помощники и боевой HUD лежат в разделе Бой."));
	    addBullet(loc("FPS, loading smoothness, and heavy modules live in Performance.", "FPS, плавность загрузки и тяжелые модули лежат в разделе Производительность."));
	    addBullet(loc("Automation lives in Bots and MooNWide Functions.", "Автоматизация живет в Ботах и Функциях MooNWide."));
	    addBullet(loc("Floating windows: drag by the header, lock them if needed, resize from the corner.", "Плавающие окна: таскай за шапку, при необходимости ставь на замок, размер меняй из угла."));

	    addSection(
		loc("Small update", "Небольшое изменение"),
		loc(
		    "Craft is no longer on the lower quick bar. Open crafting from the main action menu. Bots and guide tiles use themed icons, and empty belt slots have a special mark.",
		    "Кнопки крафта больше нет на нижней быстрой панели. Крафт теперь открывается из главного меню действий. Плитки ботов и гайда используют тематические иконки, а пустые слоты пояса имеют отдельную отметку."));

	    y += UI.scale(4);
	    RichText.Foundry noteFnd = new RichText.Foundry(
		TextAttribute.FAMILY, "SansSerif",
		TextAttribute.SIZE, UI.scale(12f),
		TextAttribute.FOREGROUND, new Color(225, 218, 235));
	    noteFnd.aa = true;
	    RichTextBox note = add(new RichTextBox(Coord.of(width, UI.scale(84)), LocalizationManager.tr("guide.quicknote"), noteFnd), Coord.of(0, y));
	    note.bg = null;
	    y += note.sz.y + PAD;
	}

	private void addSection(String title, String body) {
	    Label hdr = add(new Label(title, SECTF), Coord.of(0, y));
	    hdr.setcolor(MoonUiTheme.ACCENT);
	    y += hdr.sz.y + UI.scale(4);
	    Label txt = add(new Label(body, width, BODYF), Coord.of(0, y));
	    txt.setcolor(MoonUiTheme.TEXT_MUTED);
	    y += txt.sz.y + UI.scale(10);
	}

	private void addBullet(String text) {
	    Label txt = add(new Label("• " + text, width - UI.scale(6), BODYF), Coord.of(UI.scale(6), y));
	    txt.setcolor(MoonUiTheme.TEXT);
	    y += txt.sz.y + UI.scale(4);
	}

	private int addButtons(int y0, Action... actions) {
	    int gap = UI.scale(6);
	    int cols = 3;
	    int bw = Math.max(UI.scale(132), (width - (gap * (cols - 1))) / cols);
	    int x = 0;
	    int rowh = 0;
	    int cy = y0;
	    for(Action act : actions) {
		Button btn = add(Button.wrapped(bw, act.label).action(act.action), Coord.of(x, cy));
		rowh = Math.max(rowh, btn.sz.y);
		x += bw + gap;
		if(x + bw > width + UI.scale(2)) {
		    x = 0;
		    cy += rowh + gap;
		    rowh = 0;
		}
	    }
	    return cy + rowh + UI.scale(12);
	}
    }
}
