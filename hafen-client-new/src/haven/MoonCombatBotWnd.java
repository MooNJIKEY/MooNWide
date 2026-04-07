package haven;

import java.util.Locale;

/**
 * Combat bot settings; styled like {@link ChatWnd}. AI mode, aggression, search depth, logging.
 */
public class MoonCombatBotWnd extends MoonChatStylePanel {
    public final GameUI gui;

    public MoonCombatBotWnd(GameUI gui) {
	super(combatOuterSz(), "moon-combatbot", LocalizationManager.tr("combatbot.wnd.title"));
	this.gui = gui;
	setMinSize(UI.scale(new Coord(280, 200)));
	int x = UI.scale(10);
	int y = UI.scale(10);
	Coord b = contentOffset().add(x, y);
	add(new CheckBox(LocalizationManager.tr("combatbot.enable")) {
	    { a = MoonConfig.combatBot; }
	    public void set(boolean val) {
		MoonConfig.setCombatBot(val);
		a = val;
		if(!val)
		    MoonCombatBot.reset();
	    }
	}, b);
	int row = b.y + UI.scale(26);
	{
	    Label dpy = add(new Label(""), new Coord(b.x + UI.scale(200), row));
	    add(new HSlider(UI.scale(180), 0, 2, MoonConfig.combatBotBrainMode) {
		    protected void added() {
			dpy.settext(brainLabel(val));
		    }
		    public void changed() {
			MoonConfig.setCombatBotBrainMode(val);
			dpy.settext(brainLabel(val));
		    }
		}, new Coord(b.x, row));
	    row += UI.scale(28);
	}
	{
	    Label dpy = add(new Label(""), new Coord(b.x + UI.scale(200), row));
	    add(new HSlider(UI.scale(180), 0, 100, MoonConfig.combatBotAggression) {
		    protected void added() {
			dpy.settext(LocalizationManager.tr("combatbot.ai.agg") + ": " + val);
		    }
		    public void changed() {
			MoonConfig.setCombatBotAggression(val);
			dpy.settext(LocalizationManager.tr("combatbot.ai.agg") + ": " + val);
		    }
		}, new Coord(b.x, row));
	    row += UI.scale(28);
	}
	{
	    Label dpy = add(new Label(""), new Coord(b.x + UI.scale(200), row));
	    add(new HSlider(UI.scale(180), 2, 3, MoonConfig.combatBotSearchDepth) {
		    protected void added() {
			dpy.settext(LocalizationManager.tr("combatbot.ai.depth") + ": " + val);
		    }
		    public void changed() {
			MoonConfig.setCombatBotSearchDepth(val);
			dpy.settext(LocalizationManager.tr("combatbot.ai.depth") + ": " + val);
		    }
		}, new Coord(b.x, row));
	    row += UI.scale(28);
	}
	{
	    int r100 = (int)Math.round(MoonConfig.combatBotRiskAversion * 100);
	    Label dpy = add(new Label(""), new Coord(b.x + UI.scale(200), row));
	    add(new HSlider(UI.scale(180), 0, 300, r100) {
		    protected void added() {
			dpy.settext(LocalizationManager.tr("combatbot.ai.risk") + ": " + String.format(Locale.ROOT, "%.2f", val / 100.0));
		    }
		    public void changed() {
			MoonConfig.setCombatBotRiskAversion(val / 100.0);
			dpy.settext(LocalizationManager.tr("combatbot.ai.risk") + ": " + String.format(Locale.ROOT, "%.2f", val / 100.0));
		    }
		}, new Coord(b.x, row));
	    row += UI.scale(28);
	}
	add(new CheckBox(LocalizationManager.tr("combatbot.ai.hints")) {
	    { a = MoonConfig.combatBotTrustHints; }
	    public void set(boolean val) {
		MoonConfig.setCombatBotTrustHints(val);
		a = val;
	    }
	}, new Coord(b.x, row));
	row += UI.scale(22);
	add(new CheckBox(LocalizationManager.tr("combatbot.ai.log")) {
	    { a = MoonConfig.combatBotLogAi; }
	    public void set(boolean val) {
		MoonConfig.setCombatBotLogAi(val);
		a = val;
	    }
	}, new Coord(b.x, row));
	row += UI.scale(24);
	add(new Button(UI.scale(200), LocalizationManager.tr("combatbot.ai.analyze"), false).action(() -> {
	    MoonCombatLogAnalyzer.runFromClient(gui);
	}), new Coord(b.x, row));
	row += UI.scale(36);
	add(new Label(LocalizationManager.tr("combatbot.wnd.hint")), new Coord(b.x, row));
	row += UI.scale(18);
	add(new Label(LocalizationManager.tr("combatbot.wnd.data") + " " + shortErr(MoonCombatTables.lastLoadError())),
	    new Coord(b.x, row));
	row += UI.scale(18);
	add(new Label(LocalizationManager.tr("combatbot.wnd.keybind")), new Coord(b.x, row));
    }

    private static String shortErr(String e) {
	if(e == null || e.isEmpty())
	    return "OK";
	return e.length() > 48 ? e.substring(0, 45) + "…" : e;
    }

    private static String brainLabel(int v) {
	String key = switch(v) {
	case 0 -> "combatbot.ai.brain.0";
	case 1 -> "combatbot.ai.brain.1";
	default -> "combatbot.ai.brain.2";
	};
	return LocalizationManager.tr("combatbot.ai.brain") + ": " + LocalizationManager.tr(key);
    }

    private static Coord combatOuterSz() {
	Coord csz = UI.scale(new Coord(360, 360));
	return(new Coord(csz.x + MoonPanel.PAD * 2, csz.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2));
    }

    @Override
    protected void added() {
	if(Utils.getprefc("wpos-moon-combatbot", null) == null) {
	    Coord leg = Utils.getprefc("wndc-moon-combatbot", null);
	    if(leg != null)
		Utils.setprefc("wpos-moon-combatbot", leg);
	}
	super.added();
    }

    @Override
    protected void onCloseClicked() {
	reqdestroy();
    }

    @Override
    public void remove() {
	if(gui != null && gui.moonCombatBotWnd == this)
	    gui.moonCombatBotWnd = null;
	Utils.setprefc("wndc-moon-combatbot", c);
	super.remove();
    }
}
