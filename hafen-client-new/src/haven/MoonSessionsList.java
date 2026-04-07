package haven;

import java.awt.Color;

/**
 * Saved sessions list: login screen uses a framed standalone list, while the in-game
 * switcher embeds the same rows into a Moon-style panel.
 */
public class MoonSessionsList extends Widget {
    public enum Mode { LOGIN, INGAME }

    private static final int ROW_H = UI.scale(22);
    private static final int PAD = UI.scale(5);
    private static final int MAX_ROWS = 12;
    private static final int W_LOGIN = UI.scale(272);
    private static final int W_INGAME = UI.scale(240);
    private static final int MIN_H_LOGIN = UI.scale(136);
    private static final int MIN_H_INGAME = UI.scale(92);
    private static final int LOGIN_HEADER_H = UI.scale(24);
    /** Vertical space reserved for {@link MoonFantasyButton} at bottom. */
    private static final int BTN_BLOCK = UI.scale(40);
    private static final int BTN_TOP_GAP = UI.scale(8);
    private static Tex xicon, upicon, downicon;
    static {
	xicon = LoginScreen.textfs.render("\u2716", Color.WHITE).tex();
	upicon = LoginScreen.textfs.render("\u25B2", Color.WHITE).tex();
	downicon = LoginScreen.textfs.render("\u25BC", Color.WHITE).tex();
    }

    private int hoverRow = -1;
    private int xhover = -1, uphover = -1, downhover = -1;
    private final MoonFantasyButton bottomBtn;
    private final Mode mode;
    private final LoginScreen loginParent;
    private final GameUI gameUi;

    public MoonSessionsList(LoginScreen parent) {
	super(calcSize(Mode.LOGIN));
	this.mode = Mode.LOGIN;
	this.loginParent = parent;
	this.gameUi = null;
	bottomBtn = add(new MoonFantasyButton(calcWidth(Mode.LOGIN),
	    LocalizationManager.tr("login.sessions.add"), parent::openCredbox), buttonPos());
	bottomBtn.c = new Coord(0, sz.y - bottomBtn.sz.y);
    }

    public MoonSessionsList(GameUI gui) {
	super(calcSize(Mode.INGAME));
	this.mode = Mode.INGAME;
	this.loginParent = null;
	this.gameUi = gui;
	bottomBtn = add(new MoonFantasyButton(calcWidth(Mode.INGAME),
	    LocalizationManager.tr("session.new"), this::spawnBlank), buttonPos());
	bottomBtn.c = new Coord(0, sz.y - bottomBtn.sz.y);
    }

    private boolean embedded() {
	return(mode == Mode.INGAME);
    }

    private static int calcWidth(Mode mode) {
	return(mode == Mode.INGAME) ? W_INGAME : W_LOGIN;
    }

    private static Coord calcSize(Mode mode) {
	int n = Math.min(Config.logins.size(), MAX_ROWS);
	int listH = Math.max(ROW_H, n * ROW_H);
	int top = (mode == Mode.LOGIN) ? (LOGIN_HEADER_H + UI.scale(8)) : 0;
	int minh = (mode == Mode.INGAME) ? MIN_H_INGAME : MIN_H_LOGIN;
	int h = Math.max(minh, top + listH + BTN_TOP_GAP + BTN_BLOCK);
	return(new Coord(calcWidth(mode), h));
    }

    private int listTop() {
	return embedded() ? 0 : (LOGIN_HEADER_H + UI.scale(8));
    }

    private Coord buttonPos() {
	return new Coord(0, sz.y - BTN_BLOCK);
    }

    private void spawnBlank() {
	if(!MoonParallelSessions.startBlank() && (gameUi != null) && (gameUi.ui != null))
	    gameUi.ui.error(LocalizationManager.tr("session.spawn.fail"));
    }

    private int rowAt(Coord c) {
	if(c.x < 0 || c.x >= sz.x)
	    return -1;
	if(c.y < listTop() || c.y >= listTop() + Math.min(Config.logins.size(), MAX_ROWS) * ROW_H)
	    return -1;
	int r = (c.y - listTop()) / ROW_H;
	return (r >= 0 && r < Config.logins.size() && r < MAX_ROWS) ? r : -1;
    }

    @Override
    public void draw(GOut g) {
	if(!embedded()) {
	    MoonUiTheme.drawLoginPanelChrome(g, sz, LOGIN_HEADER_H,
		MoonUiTheme.title(LocalizationManager.tr("session.list.title")));
	}
	int y = listTop();
	int xpos = sz.x - xicon.sz().x - PAD;
	int cpos = xpos - upicon.sz().x - PAD;
	synchronized(Config.logins) {
	    int i = 0;
	    for(LoginData ld : Config.logins) {
		if(i >= MAX_ROWS)
		    break;
		Color rowBorder = MoonUiTheme.MENU_SEPARATOR;
		if(hoverRow == i) {
		    g.chcolor(MoonUiTheme.MENU_HOVER);
		    g.frect(new Coord(0, y), new Coord(sz.x, ROW_H));
		}
		g.chcolor(rowBorder);
		g.line(new Coord(0, y + ROW_H - 1), new Coord(sz.x, y + ROW_H - 1), 1);
		g.chcolor();
		Tex nameTex = LoginScreen.textfs.render(ld.username, MoonUiTheme.TEXT).tex();
		g.image(nameTex, new Coord(PAD, y + (ROW_H - nameTex.sz().y) / 2));
		g.chcolor(xhover == i ? MoonUiTheme.ACCENT : MoonUiTheme.ACCENT_DARK);
		g.aimage(xicon, new Coord(sz.x - PAD, y + (ROW_H - xicon.sz().y) / 2), 1, 0);
		g.chcolor(uphover == i ? MoonUiTheme.ACCENT : MoonUiTheme.ACCENT_DARK);
		g.aimage(upicon, new Coord(cpos, y + UI.scale(2)), 1, 0);
		g.chcolor(downhover == i ? MoonUiTheme.ACCENT : MoonUiTheme.ACCENT_DARK);
		g.aimage(downicon, new Coord(cpos, y + ROW_H - UI.scale(2)), 1, 1);
		g.chcolor();
		y += ROW_H;
		i++;
	    }
	}
	super.draw(g);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	int row = rowAt(ev.c);
	hoverRow = row;
	int xpos = sz.x - xicon.sz().x - PAD;
	int cpos = xpos - upicon.sz().x - PAD;
	xhover = (row >= 0 && ev.c.x >= xpos && ev.c.x <= xpos + xicon.sz().x) ? row : -1;
	if((row >= 0) && (ev.c.x >= cpos) && (ev.c.x <= cpos + upicon.sz().x)) {
	    int ry = ev.c.y - (listTop() + row * ROW_H);
	    uphover = (ry < ROW_H / 2) ? row : -1;
	    downhover = (ry >= ROW_H / 2) ? row : -1;
	} else {
	    uphover = -1;
	    downhover = -1;
	}
	super.mousemove(ev);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	if(ev.b != 1)
	    return super.mousedown(ev);
	int row = rowAt(ev.c);
	int xpos = sz.x - xicon.sz().x - PAD;
	int cpos = xpos - upicon.sz().x - PAD;
	if(row >= 0) {
	    LoginData itm = Config.logins.get(row);
	    if(ev.c.x >= xpos && ev.c.x <= xpos + xicon.sz().x) {
		synchronized(Config.logins) {
		    Config.logins.remove(itm);
		    Config.saveLogins();
		}
		return true;
	    }
	    if(ev.c.x >= cpos && ev.c.x <= cpos + upicon.sz().x) {
		int ry = ev.c.y - (listTop() + row * ROW_H);
		moveData(itm, (ry < ROW_H / 2) ? -1 : 1);
		return true;
	    }
	    if(mode == Mode.LOGIN) {
		loginParent.wdgmsg("login", new AuthClient.NativeCred(itm.username, itm.password), false);
	    } else if(!MoonParallelSessions.activateSavedIfOpen(itm)
		&& !MoonParallelSessions.startWithSaved(itm) && gameUi != null && gameUi.ui != null) {
		gameUi.ui.error(LocalizationManager.tr("session.spawn.fail"));
	    }
	    return true;
	}
	return super.mousedown(ev);
    }

    private void moveData(LoginData itm, int amount) {
	synchronized(Config.logins) {
	    int idx = Config.logins.indexOf(itm);
	    if(idx >= 0) {
		int f = idx + amount;
		if(f >= 0 && f < Config.logins.size()) {
		    Config.logins.remove(itm);
		    Config.logins.add(f, itm);
		    Config.saveLogins();
		}
	    }
	}
    }

    @Override
    public void tick(double dt) {
	Coord nsz = calcSize(mode);
	if(!nsz.equals(sz)) {
	    resize(nsz);
	    bottomBtn.setMinWidth(sz.x);
	    bottomBtn.c = new Coord(0, sz.y - bottomBtn.sz.y);
	}
	super.tick(dt);
    }
}
