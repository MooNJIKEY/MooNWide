package haven;

import static haven.Inventory.invsq;

/**
 * Hub for MooNWide bots: same cell size as {@link MenuGrid}; styled like {@link ChatWnd}.
 */
public class MoonBotsWnd extends MoonChatStylePanel {
    public final GameUI gui;

    private static final int COLS = 3, ROWS = 2;
    private static final int GAP = 4;

    public MoonBotsWnd(GameUI gui) {
	super(outerSz(), "moon-bots", LocalizationManager.tr("bots.title"));
	this.gui = gui;
	setMinSize(outerSz());
	Coord co = contentOffset();
	Coord cell = invsq.sz();
	int gap = UI.scale(GAP);
	int pad = UI.scale(10);
	for(int i = 0; i < COLS * ROWS; i++) {
	    int gx = i % COLS, gy = i / COLS;
	    Coord p = co.add(pad + gx * (cell.x + gap), pad + gy * (cell.y + gap));
	    switch(i) {
	    case 0:
		add(new BotCell("bot.combat", () -> openCombatSettings(), LocalizationManager.tr("bots.hub.combat.tip")), p);
		break;
	    case 1:
		add(new BotCell("bot.tree", () -> openTreeSettings(), LocalizationManager.tr("bots.hub.tree.tip")), p);
		break;
	    case 2:
		add(new BotCell("bot.nav", () -> openTeleport(), LocalizationManager.tr("bots.hub.nav.tip")), p);
		break;
	    case 3:
		add(new BotCell("bot.mine", () -> openMineBot(), LocalizationManager.tr("bots.hub.mine.tip")), p);
		break;
	    case 4:
		add(new BotCell("bot.fish", null, LocalizationManager.tr("bots.placeholder.fish")), p);
		break;
	    default:
		add(new BotCell("bot.more", null, LocalizationManager.tr("bots.placeholder.more")), p);
		break;
	    }
	}
    }

    private static Coord contentInnerSz() {
	Coord cell = invsq.sz();
	int gap = UI.scale(GAP);
	int pad = UI.scale(10);
	int w = pad * 2 + COLS * cell.x + (COLS - 1) * gap;
	int h = pad * 2 + ROWS * cell.y + (ROWS - 1) * gap;
	return(new Coord(w, h));
    }

    private static Coord outerSz() {
	Coord in = contentInnerSz();
	return(new Coord(in.x + MoonPanel.PAD * 2, in.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2));
    }

    @Override
    protected void added() {
	if(Utils.getprefc("wpos-moon-bots", null) == null) {
	    Coord leg = Utils.getprefc("wndc-moon-bots", null);
	    if(leg != null)
		Utils.setprefc("wpos-moon-bots", leg);
	}
	super.added();
    }

    @Override
    protected void onCloseClicked() {
	reqdestroy();
    }

    private void openCombatSettings() {
	gui.openMoonCombatBotWindow();
    }

    private void openTeleport() {
	gui.toggleMoonTeleportWindow();
    }

    private void openTreeSettings() {
	if(gui.moonTreeBotWnd != null) {
	    gui.moonTreeBotWnd.raise();
	    gui.setfocus(gui.moonTreeBotWnd);
	    return;
	}
	MoonTreeBotWnd w = new MoonTreeBotWnd(gui);
	Coord c = gui.fitwdg(w, new Coord(gui.sz.x / 2 - w.sz.x / 2, UI.scale(90)));
	gui.moonTreeBotWnd = gui.add(w, c);
	gui.fitwdg(gui.moonTreeBotWnd);
	gui.moonTreeBotWnd.raise();
	gui.setfocus(gui.moonTreeBotWnd);
    }

    private void openMineBot() {
	gui.openMoonMineBotWindow();
    }

    private static class BotCell extends Widget {
	final Runnable onActivate;
	final String iconKey;
	final Tex icon;

	BotCell(String iconKey, Runnable onActivate, String tip) {
	    super(invsq.sz());
	    this.iconKey = iconKey;
	    this.onActivate = onActivate;
	    this.icon = new TexI(PUtils.uiscale(MoonUiIcons.image(iconKey, invsq.sz().sub(6, 6)), invsq.sz().sub(6, 6)), false);
	    settip(tip, false);
	}

	@Override
	public void draw(GOut g) {
	    g.image(invsq, Coord.z);
	    if(icon != null)
		g.image(icon, invsq.sz().sub(icon.sz()).div(2));
	}

	@Override
	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b == 1 && onActivate != null) {
		onActivate.run();
		return(true);
	    }
	    return(super.mousedown(ev));
	}
    }

    @Override
    public void remove() {
	if(gui != null && gui.moonBotsWnd == this)
	    gui.moonBotsWnd = null;
	Utils.setprefc("wndc-moon-bots", c);
	super.remove();
    }
}
