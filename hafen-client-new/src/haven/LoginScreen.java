/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.imageio.ImageIO;

public class LoginScreen extends Widget {
    public static final Config.Variable<String> authmech = Config.Variable.prop("haven.authmech", "native");
    public static final Text.Foundry
	textf = new Text.Foundry(Text.sans, UI.scale(18)).aa(true),
	textfs = new Text.Foundry(Text.sans, UI.scale(15)).aa(true);
    private static final Text.Foundry herof = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(42f))).aa(true);
    private static final Text.Foundry heroSubf = new Text.Foundry(Text.serif.deriveFont(Font.PLAIN, UI.scale(18f))).aa(true);
    private static final Text.Foundry heroChipf = new Text.Foundry(Text.sans.deriveFont(Font.PLAIN, UI.scale(13f))).aa(true);
    private static final int HERO_W = UI.scale(520);
    private static final int LOGIN_MARGIN_X = UI.scale(54);
    private static final int LOGIN_MARGIN_Y = UI.scale(54);
    private static final int LOGIN_STATUS_TOP = UI.scale(84);
    private static final int LOGIN_HERO_Y = UI.scale(86);

    private static Tex loginBgCache;

    /** Background for login / launcher: custom image if present, else vanilla {@code gfx/loginscr}. */
    public static Tex loginBackgroundTex() {
	synchronized(LoginScreen.class) {
	    if(loginBgCache != null)
		return(loginBgCache);
	    Tex t = resolveLoginSplash();
	    if(t == null)
		t = Resource.loadtex("gfx/loginscr");
	    loginBgCache = t;
	    return(loginBgCache);
	}
    }

    private static Path codeSourceDir() {
	try {
	    java.security.CodeSource cs = LoginScreen.class.getProtectionDomain().getCodeSource();
	    if(cs == null)
		return(null);
	    Path p = Paths.get(cs.getLocation().toURI());
	    if(Files.isRegularFile(p))
		return(p.getParent());
	    if(Files.isDirectory(p))
		return(p);
	} catch(Exception e) {
	}
	return(null);
    }

    private static Tex tryLoadSplashFile(Path path) {
	if(path == null || !Files.isRegularFile(path))
	    return(null);
	try {
	    BufferedImage bi = ImageIO.read(path.toFile());
	    if(bi == null)
		return(null);
	    Coord tsz = Coord.of(UI.scale(bi.getWidth()), UI.scale(bi.getHeight()));
	    if(tsz.x < 1 || tsz.y < 1)
		return(null);
	    return(new TexI(PUtils.uiscale(bi, tsz)));
	} catch(IOException e) {
	    return(null);
	}
    }

    /** Embedded in {@code hafen.jar} via {@code custom-res/} → {@code res/} at build time. */
    private static Tex tryLoadSplashClasspath() {
	ClassLoader cl = LoginScreen.class.getClassLoader();
	for(String p : new String[] {"res/login-splash.png", "res/login-splash.jpg"}) {
	    try(InputStream in = cl.getResourceAsStream(p)) {
		if(in == null)
		    continue;
		BufferedImage bi = ImageIO.read(in);
		if(bi == null)
		    continue;
		Coord tsz = Coord.of(UI.scale(bi.getWidth()), UI.scale(bi.getHeight()));
		if(tsz.x < 1 || tsz.y < 1)
		    continue;
		return(new TexI(PUtils.uiscale(bi, tsz)));
	    } catch(IOException e) {
	    }
	}
	return(null);
    }

    private static Tex resolveLoginSplash() {
	String prop = System.getProperty("haven.loginsplash");
	if(prop != null && !prop.isEmpty()) {
	    Tex t = tryLoadSplashFile(Utils.path(prop));
	    if(t != null)
		return(t);
	}
	try {
	    String pref = Utils.getpref("moon-login-splash", null);
	    if(pref != null && !pref.isEmpty()) {
		Tex t = tryLoadSplashFile(Utils.path(pref));
		if(t != null)
		    return(t);
	    }
	} catch(Exception e) {
	}
	Path dir = codeSourceDir();
	if(dir != null) {
	    for(String name : new String[] {"login-splash.png", "login-splash.jpg", "splash.png"}) {
		Tex t = tryLoadSplashFile(dir.resolve(name));
		if(t != null)
		    return(t);
	    }
	    Tex t = tryLoadSplashFile(dir.resolve("splash").resolve("login-splash.png"));
	    if(t != null)
		return(t);
	}
	{
	    Tex t = tryLoadSplashClasspath();
	    if(t != null)
		return(t);
	}
	try {
	    Path home = Paths.get(System.getProperty("user.home", ".")).resolve(".haven").resolve("login-splash.png");
	    Tex t = tryLoadSplashFile(home);
	    if(t != null)
		return(t);
	} catch(Exception e) {
	}
	return(null);
    }

    /** @deprecated use {@link #loginBackgroundTex()} */
    @Deprecated
    public static Tex bg() {
	return(loginBackgroundTex());
    }

    public static final Position bgc = new Position(UI.scale(420, 280));
    public final Widget login;
    public final String confname;
    private Text error, progress;
    private Widget optbtn;
    private OptWnd opts;
    /** Account selector panel (top-right). */
    private Widget accountPanel;
    private Widget statusWdg;
    private Charlist charStage;
    private final Text.Line heroTitle;
    private final Text.Line heroSubtitle;
    private final Text.Line buildTag;
    private double introa = 0.0;
    private double breathe = 0.0;

    private String getpref(String name, String def) {
	return(Utils.getpref(name + "@" + confname, def));
    }

    public LoginScreen(String confname) {
	super(loginBackgroundTex().sz());
	this.confname = confname;
	setfocustab(true);
	this.heroTitle = herof.render(MainFrame.TITLE(), MoonUiTheme.ACCENT);
	this.heroSubtitle = heroSubf.render(loginMoodLine(), MoonUiTheme.TEXT_MUTED);
	this.buildTag = heroChipf.render(buildLine(), new Color(232, 226, 255));
	Config.reloadLogins();
	final MoonFantasyButton[] optRef = new MoonFantasyButton[1];
	optRef[0] = new MoonFantasyButton(UI.scale(132), LocalizationManager.tr("login.opt"),
	    () -> optRef[0].wdgmsg("activate"));
	optbtn = adda(optRef[0], sz.x - UI.scale(10), UI.scale(34), 1, 1);
	optRef[0].setgkey(GameUI.kb_opt);
	if (HttpStatus.mond.get() != null) {
	    statusWdg = adda(new StatusLabel(HttpStatus.mond.get(), 1.0), Coord.of(sz.x - UI.scale(10), UI.scale(70)), 1, 1);
	} else {
	    statusWdg = null;
	}
	if ("native".equals(authmech.get())) {
	    accountPanel = adda(new MoonSessionsList(this), UI.scale(10), UI.scale(10), 0, 0);
	} else {
	    accountPanel = null;
	}
	switch(authmech.get()) {
	case "native":
	    login = new Credbox();
	    break;
	case "steam":
	    login = new Steambox();
	    break;
	default:
	    throw(new RuntimeException("Unknown authmech: " + authmech.get()));
	}
	add(login, Coord.z);
	layoutLoginScene();
	login.show();
    }

    private static String loginMoodLine() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ?
	    "Лунный вход в мир Haven & Hearth" :
	    "A moonlit gateway into Haven & Hearth");
    }

    private static String loginHintLine() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ?
	    "Сессии слева, вход и запуск мира справа" :
	    "Sessions on the left, login and world entry on the right");
    }

    private static String worldWakeLine() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "Подготовка входа" : "Preparing your journey");
    }

    private static String buildLine() {
	String build = readBuildVersion();
	if(build == null || build.isEmpty())
	    build = "dev-local";
	return("Build " + build);
    }

    private static String readBuildVersion() {
	Path dir = codeSourceDir();
	if(dir != null) {
	    Path file = dir.resolve("moonwide-version.txt");
	    try {
		if(Files.isRegularFile(file))
		    return(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).trim());
	    } catch(IOException ignored) {
	    }
	}
	ClassLoader cl = LoginScreen.class.getClassLoader();
	try(InputStream in = cl.getResourceAsStream("res/moonwide-version.txt")) {
	    if(in != null)
		return(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim());
	} catch(IOException ignored) {
	}
	return(null);
    }

    private static double smooth(double a) {
	return(Utils.smoothstep(Math.max(0.0, Math.min(1.0, a))));
    }

    private void layoutLoginScene() {
	double a = smooth(introa);
	int mx = Math.max(LOGIN_MARGIN_X, sz.x / 24);
	int my = Math.max(LOGIN_MARGIN_Y, sz.y / 18);
	int leftX = mx;
	int baseY = sz.y - my;
	if(accountPanel != null) {
	    int ax = (int)Math.round((1.0 - a) * UI.scale(48));
	    int ay = (int)Math.round((1.0 - a) * UI.scale(26));
	    accountPanel.c = Coord.of(leftX - ax, baseY - accountPanel.sz.y + ay);
	}
	if(login != null) {
	    int loginX = sz.x - login.sz.x - mx;
	    int loginY = sz.y - login.sz.y - UI.scale(120);
	    int ly = (int)Math.round((1.0 - a) * UI.scale(42));
	    login.c = Coord.of(loginX, loginY + ly);
	}
	if(statusWdg != null) {
	    int sy = (int)Math.round((1.0 - a) * UI.scale(20));
	    statusWdg.c = Coord.of(sz.x - statusWdg.sz.x - mx, LOGIN_STATUS_TOP - sy);
	}
	if(optbtn != null)
	    optbtn.c = Coord.of(sz.x - optbtn.sz.x - mx, UI.scale(28));
    }

    private void drawBackdrop(GOut g) {
	Tex bg = loginBackgroundTex();
	double zoom = 1.04 + (0.01 * Math.sin(breathe * 0.45));
	Coord bsz = bg.sz();
	double cover = Math.max((double)sz.x / Math.max(1, bsz.x), (double)sz.y / Math.max(1, bsz.y));
	Coord dsz = Coord.of((int)Math.round(bsz.x * cover * zoom), (int)Math.round(bsz.y * cover * zoom));
	Coord ul = Coord.of((sz.x - dsz.x) / 2, (sz.y - dsz.y) / 2);
	g.image(bg, ul, dsz);
	MoonUiTheme.drawVerticalGradient(g, Coord.z, sz, new Color(7, 9, 22, 124), new Color(6, 6, 16, 206));
	g.chcolor(255, 255, 255, 8);
	g.line(Coord.of(UI.scale(46), sz.y - UI.scale(112)), Coord.of(sz.x - UI.scale(46), sz.y - UI.scale(112)), 1);
	g.chcolor();
    }

    private void drawHero(GOut g) {
	double a = smooth(introa);
	int alpha = (int)Math.round(255 * a);
	int titleY = LOGIN_HERO_Y + (int)Math.round((1.0 - a) * UI.scale(24));
	g.chcolor(255, 255, 255, Math.max(0, Math.min(255, alpha)));
	g.aimage(heroTitle.tex(), Coord.of(sz.x / 2, titleY), 0.5, 0.0);
	g.chcolor(255, 255, 255, Math.max(0, Math.min(255, (int)(alpha * 0.9))));
	g.aimage(heroSubtitle.tex(), Coord.of(sz.x / 2, titleY + heroTitle.sz().y + UI.scale(10)), 0.5, 0.0);
	Text.Line mood = heroChipf.render(loginHintLine(), new Color(232, 226, 255));
	int chipY = titleY + heroTitle.sz().y + heroSubtitle.sz().y + UI.scale(28);
	int chipW = Math.min(sz.x - (UI.scale(96)), Math.max(HERO_W, mood.sz().x + UI.scale(52)));
	Coord chipSz = Coord.of(chipW, UI.scale(30));
	Coord chipUl = Coord.of((sz.x - chipSz.x) / 2, chipY);
	g.chcolor(26, 22, 52, Math.max(0, Math.min(255, (int)(126 * a))));
	g.frect(chipUl, chipSz);
	g.chcolor(MoonUiTheme.BORDER.getRed(), MoonUiTheme.BORDER.getGreen(), MoonUiTheme.BORDER.getBlue(),
	    Math.max(0, Math.min(255, (int)(144 * a))));
	g.rect(chipUl, chipSz);
	g.chcolor(214, 208, 255, Math.max(0, Math.min(255, (int)(44 * a))));
	g.line(Coord.of(chipUl.x + UI.scale(16), chipUl.y + chipSz.y / 2),
	    Coord.of(chipUl.x + UI.scale(96), chipUl.y + chipSz.y / 2), 1);
	g.line(Coord.of(chipUl.x + chipSz.x - UI.scale(96), chipUl.y + chipSz.y / 2),
	    Coord.of(chipUl.x + chipSz.x - UI.scale(16), chipUl.y + chipSz.y / 2), 1);
	g.chcolor(255, 255, 255, Math.max(0, Math.min(255, (int)(224 * a))));
	g.aimage(mood.tex(), chipUl.add(chipSz.div(2)), 0.5, 0.5);
	g.chcolor();
    }

    private void drawStatusChip(GOut g, Text text, int y, Color tint, int baseAlpha) {
	if(text == null)
	    return;
	Coord t = text.sz();
	Coord box = Coord.of(t.x + UI.scale(32), t.y + UI.scale(18));
	Coord ul = Coord.of((sz.x - box.x) / 2, y);
	g.chcolor(tint.getRed(), tint.getGreen(), tint.getBlue(), baseAlpha);
	g.frect(ul, box);
	g.chcolor(MoonUiTheme.BORDER.getRed(), MoonUiTheme.BORDER.getGreen(), MoonUiTheme.BORDER.getBlue(), baseAlpha + 30);
	g.rect(ul, box);
	g.chcolor();
	g.aimage(text.tex(), ul.add(box.div(2)), 0.5, 0.5);
    }

    private void drawBuildChip(GOut g) {
	if(buildTag == null)
	    return;
	Coord t = buildTag.sz();
	Coord box = Coord.of(t.x + UI.scale(20), t.y + UI.scale(12));
	Coord ul = Coord.of(UI.scale(18), sz.y - box.y - UI.scale(18));
	g.chcolor(22, 18, 44, 170);
	g.frect(ul, box);
	g.chcolor(MoonUiTheme.BORDER.getRed(), MoonUiTheme.BORDER.getGreen(), MoonUiTheme.BORDER.getBlue(), 196);
	g.rect(ul, box);
	g.chcolor();
	g.aimage(buildTag.tex(), ul.add(box.div(2)), 0.5, 0.5);
    }

    /** Opens the username/password box (used by {@link MoonSessionsList}). */
    public void openCredbox() {
	mklogin();
    }

    public static final KeyBinding kb_savtoken = KeyBinding.get("login/savtoken", KeyMatch.forchar('R', KeyMatch.M));
    public static final KeyBinding kb_deltoken = KeyBinding.get("login/deltoken", KeyMatch.forchar('F', KeyMatch.M));
    public class Credbox extends Widget {
	public final UserEntry user;
	private final TextEntry pass;
	private final CheckBox savetoken;
	private final Widget pwbox, tkbox;
	private byte[] token = null;
	private boolean inited = false;

	public class UserEntry extends TextEntry {
	    private final List<String> history = new ArrayList<>();
	    private int hpos = -1;
	    private String hcurrent;

	    private UserEntry(int w) {
		super(w, "");
		history.addAll(Utils.getprefsl("saved-tokens@" + confname, new String[] {}));
	    }

	    protected void changed() {
		checktoken();
		savetoken.set(token != null);
	    }

	    public void settext2(String text) {
		rsettext(text);
		changed();
	    }

	    public boolean keydown(KeyDownEvent ev) {
		if(ConsoleHost.kb_histprev.key().match(ev)) {
		    if(hpos < history.size() - 1) {
			if(hpos < 0)
			    hcurrent = text();
			settext2(history.get(++hpos));
		    }
		} else if(ConsoleHost.kb_histnext.key().match(ev)) {
		    if(hpos >= 0) {
			if(--hpos < 0)
			    settext2(hcurrent);
			else
			    settext2(history.get(hpos));
		    }
		} else {
		    return(super.keydown(ev));
		}
		return(true);
	    }

	    public void init(String name) {
		history.remove(name);
		settext2(name);
	    }
	}

	private Credbox() {
	    super(UI.scale(272, 252));
	    setfocustab(true);
	    Label lu = add(new Label(LocalizationManager.tr("login.user"), textf), UI.scale(12), UI.scale(12));
	    lu.setcolor(MoonUiTheme.TEXT);
	    add(user = new UserEntry(this.sz.x - UI.scale(24)), lu.pos("bl").adds(0, UI.scale(3)));
	    setfocus(user);

	    add(pwbox = new Widget(Coord.z), user.pos("bl").adds(0, UI.scale(10)));
	    Label lp = pwbox.add(new Label(LocalizationManager.tr("login.pass"), textf), Coord.z);
	    lp.setcolor(MoonUiTheme.TEXT);
	    pwbox.add(pass = new TextEntry(this.sz.x - UI.scale(24), ""), lp.pos("bl").adds(0, UI.scale(3))).pw = true;
	    pwbox.add(savetoken = new CheckBox(LocalizationManager.tr("login.remember"), true), pass.pos("bl").adds(0, UI.scale(10)));
	    savetoken.setgkey(kb_savtoken);
	    savetoken.settip(LocalizationManager.tr("login.remember.tip"), true);
	    pwbox.pack();
	    pwbox.hide();

	    add(tkbox = new Widget(new Coord(this.sz.x, 0)), user.pos("bl").adds(0, UI.scale(12)));
	    Label ls = tkbox.add(new Label(LocalizationManager.tr("login.saved"), textfs), UI.scale(0, UI.scale(28)));
	    ls.setcolor(MoonUiTheme.TEXT_MUTED);
	    MoonFantasyButton forgetBtn = new MoonFantasyButton(UI.scale(118), LocalizationManager.tr("login.forget"), this::forget);
	    tkbox.adda(forgetBtn, ls.pos("mid").x(this.sz.x), 1.0, 0.5);
	    forgetBtn.setgkey(kb_deltoken);
	    tkbox.pack();
	    tkbox.hide();

	    MoonFantasyButton execBtn = new MoonFantasyButton(this.sz.x - UI.scale(24),
		LocalizationManager.tr("login.submit"), this::enter);
	    adda(execBtn,
		pos("cmid").y(Math.max(pwbox.pos("bl").y, tkbox.pos("bl").y)).adds(0, UI.scale(38)), 0.5, 0.0);
	    execBtn.setgkey(Widget.key_act);
	    pack();
	}

	@Override
	public void draw(GOut g) {
	    MoonUiTheme.drawVerticalGradient(g, Coord.z, sz, MoonUiTheme.BODY_TOP, MoonUiTheme.BODY_BOTTOM);
	    g.chcolor(MoonUiTheme.BORDER);
	    g.rect(Coord.z, sz);
	    g.chcolor(MoonUiTheme.BORDER_SOFT);
	    g.rect(Coord.of(UI.scale(2), UI.scale(2)), sz.sub(UI.scale(4), UI.scale(4)));
	    g.chcolor();
	    super.draw(g);
	}

	private void init() {
	    if(inited)
		return;
	    inited = true;
	    user.init(getpref("loginname", ""));
	}

	private void checktoken() {
	    if(this.token != null) {
		Arrays.fill(this.token, (byte)0);
		this.token = null;
	    }
	    byte[] token = Bootstrap.gettoken(user.text(), confname);
	    if(token == null) {
		tkbox.hide();
		pwbox.show();
	    } else {
		tkbox.show();
		pwbox.hide();
		this.token = token;
	    }
	}

	private void forget() {
	    String nm = user.text();
	    Bootstrap.settoken(nm, confname, null);
	    savetoken.set(false);
	    checktoken();
	}

	private void enter() {
	    if(user.text().equals("")) {
		setfocus(user);
	    } else if(pwbox.visible && pass.text().equals("")) {
		setfocus(pass);
	    } else {
		boolean save = pwbox.visible && savetoken.state();
		if (save) {
		    String un = user.text(), pw = pass.text();
		    synchronized (Config.logins) {
			Config.logins.removeIf(ld -> ld.username.equals(un));
			Config.logins.add(0, new LoginData(un, pw));
			Config.saveLogins();
		    }
		}
		LoginScreen.this.wdgmsg("login", creds(), save);
	    }
	}

	private AuthClient.Credentials creds() {
	    byte[] token = this.token;
	    AuthClient.Credentials ret;
	    if(token != null) {
		ret = new AuthClient.TokenCred(user.text(), Arrays.copyOf(token, token.length));
	    } else {
		String pw = pass.text();
		ret = null;
		parse: if(pw.length() == 64) {
		    byte[] ptok;
		    try {
			ptok = Utils.hex.dec(pw);
		    } catch(IllegalArgumentException e) {
			break parse;
		    }
		    ret = new AuthClient.TokenCred(user.text(), ptok);
		}
		if(ret == null)
		    ret = new AuthClient.NativeCred(user.text(), pw);
		pass.rsettext("");
	    }
	    return(ret);
	}

	public boolean keydown(KeyDownEvent ev) {
	    if(key_act.match(ev)) {
		enter();
		return(true);
	    }
	    return(super.keydown(ev));
	}

	public void show() {
	    if(!inited)
		init();
	    super.show();
	    checktoken();
	    if(pwbox.visible && !user.text().equals(""))
		setfocus(pass);
	}
    }

    /*
     * Steam login must stay on the screen until the player confirms it,
     * otherwise there is no time to reach settings before the session starts.
     */
    private static boolean steam_autologin = false;
    public class Steambox extends Widget {

	private Steambox() {
	    super(UI.scale(220, 148));
	    Label st = adda(new Label(LocalizationManager.tr("login.steam.title"), textf), sz.x / 2, 0, 0.5, 0);
	    st.setcolor(MoonUiTheme.TEXT);
	    MoonFantasyButton go;
	    go = new MoonFantasyButton(UI.scale(200), LocalizationManager.tr("login.steam.go"), this::enter);
	    adda(go, st.pos("bl").adds(0, UI.scale(14)).x(sz.x / 2), 0.5, 0.0);
	    go.setgkey(Widget.key_act);
	    pack();
	}

	private AuthClient.Credentials creds() throws java.io.IOException {
	    return(new SteamCreds());
	}

	private void enter() {
	    try {
		LoginScreen.this.wdgmsg("login", creds(), false);
	    } catch(java.io.IOException e) {
		error(e.getMessage());
	    }
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(steam_autologin) {
		enter();
		steam_autologin = false;
	    }
	}
    }

    public static class StatusLabel extends Widget {
	public final HttpStatus stat;
	public final double ax;

	public StatusLabel(URI svc, double ax) {
	super(new Coord(UI.scale(182), Text.std.height() * 3));
	    this.stat = new HttpStatus(svc);
	    this.ax = ax;
	}

	private Text[] lines = new Text[2];
	public void draw(GOut g) {
	    MoonUiTheme.drawLoginPanelChrome(g, sz, 0, null);
	    String[] buf = {null, null};
	    synchronized(stat) {
		if(!stat.syn || (stat.status == "")) {
		    buf[0] = LocalizationManager.tr("login.status.check");
		} else if(stat.status == "up") {
		    buf[0] = LocalizationManager.tr("login.status.up");
		    buf[1] = String.format(LocalizationManager.tr("login.status.players"), stat.users);
		} else if(stat.status == "shutdown") {
		    buf[0] = LocalizationManager.tr("login.status.down");
		} else if(stat.status == "terminating") {
		    buf[0] = LocalizationManager.tr("login.status.shutting");
		} else if(stat.status == "crashed") {
		    buf[0] = LocalizationManager.tr("login.status.crashed");
		}
	    }
	    for(int i = 0, y = UI.scale(8); i < 2; i++) {
		if((lines[i] != null) && !Utils.eq(buf[i], lines[i].text)) {
		    lines[i].dispose();
		    lines[i] = null;
		}
		if((buf[i] != null) && (lines[i] == null))
		    lines[i] = Text.render(buf[i], MoonUiTheme.TEXT);
		if(lines[i] != null) {
		    g.image(lines[i].tex(), Coord.of((int)((sz.x - lines[i].sz().x) * ax), y));
		    y += lines[i].sz().y;
		}
	    }
	}

	protected void added() {
	    stat.start();
	}

	public void dispose() {
	    stat.quit();
	}
    }

    private void mklogin() {
	login.show();
	progress(null);
	login.raise();  // окно ввода логина поверх панели аккаунтов
    }

    private void error(String error) {
	if(this.error != null)
	    this.error = null;
	if(error != null) {
	    this.error = textf.render(error, java.awt.Color.RED);
	    login.show();
	    login.raise();
	}
    }

    private void progress(String p) {
	if(progress != null)
	    progress = null;
	if(p != null)
	    progress = textf.render(p, java.awt.Color.WHITE);
    }

    private void clear() {
	login.hide();
	progress(null);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == optbtn) {
	    if(opts == null) {
		opts = adda(new OptWnd(false) {
			public void hide() {
			    /* XXX */
			    reqdestroy();
			}
		    }, sz.div(2), 0.5, 0.5);
	    } else {
		opts.reqdestroy();
		opts = null;
	    }
	    return;
	} else if(sender == opts) {
	    opts.reqdestroy();
	    opts = null;
	}
	super.wdgmsg(sender, msg, args);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "login") {
	    mklogin();
	} else if(msg == "error") {
	    error((String)args[0]);
	} else if(msg == "prg") {
	    error(null);
	    clear();
	    progress((String)args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public void presize() {
	if(parent != null) {
	    resize(parent.sz);
	    c = Coord.z;
	    layoutLoginScene();
	}
    }

    protected void added() {
	presize();
	layoutLoginScene();
	parent.setfocus(this);
    }

    /**
     * Server may wrap {@link Charlist} in {@link Window} (caption e.g. «Окно»). Children are drawn
     * clipped to the parent ({@link Widget#draw}), so a small window clips the fullscreen Moon layout.
     * Unlink the list to this screen and hide the wrapper.
     */
    private static Charlist findCharlistInTree(Widget w) {
	if(w instanceof Charlist)
	    return((Charlist)w);
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    Charlist cl = findCharlistInTree(ch);
	    if(cl != null)
		return(cl);
	}
	return(null);
    }

    private static Button findButtonInTree(Widget w) {
	if(w instanceof Button)
	    return((Button)w);
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    Button btn = findButtonInTree(ch);
	    if(btn != null)
		return(btn);
	}
	return(null);
    }

    public void addchild(Widget child, Object... args) {
	Charlist cl = findCharlistInTree(child);
	if(cl != null) {
	    charStage = cl;
	    Button create = findButtonInTree(child);
	    if(create != null)
		cl.setCreateSource(create);
	    if(child instanceof Charlist) {
		add(child, Coord.z);
		child.resize(sz);
		child.c = Coord.z;
	    } else {
		super.addchild(child, args);
		if(cl.parent != this) {
		    cl.unlink();
		    add(cl, Coord.z);
		    cl.resize(sz);
		    cl.c = Coord.z;
		    child.hide();
		}
	    }
	    cl.raise();
	    if(accountPanel != null) accountPanel.hide();
	    if(login != null) login.hide();
	    if(statusWdg != null) statusWdg.hide();
	    if(optbtn != null) optbtn.hide();
	    return;
	}
	if((charStage != null) && (child instanceof Button)) {
	    super.addchild(child, args);
	    charStage.setCreateSource((Button)child);
	    child.hide();
	    child.move(Coord.of(-UI.scale(10000), -UI.scale(10000)));
	    return;
	}
	super.addchild(child, args);
    }

    public void cdestroy(Widget ch) {
	if(ch == opts) {
	    opts = null;
	}
	if(ch == charStage) {
	    charStage = null;
	    if(accountPanel != null) accountPanel.show();
	    if(login != null) login.show();
	    if(statusWdg != null) statusWdg.show();
	    if(optbtn != null) optbtn.show();
	    layoutLoginScene();
	}
    }

    public void tick(double dt) {
	/* Keep fullscreen login aligned with the root when the window is resized or Charlist is shown. */
	if(parent != null && !sz.equals(parent.sz)) {
	    resize(parent.sz);
	    c = Coord.z;
	    layoutLoginScene();
	}
	super.tick(dt);
	if(introa < 1.0)
	    introa = Math.min(1.0, introa + (dt / 0.8));
	breathe += dt;
	layoutLoginScene();
    }

    public void draw(GOut g) {
	drawBackdrop(g);
	if(charStage == null)
	    drawHero(g);
	super.draw(g);
	drawBuildChip(g);
	if((charStage == null) && (error != null))
	    drawStatusChip(g, error, sz.y - UI.scale(110), new Color(82, 18, 30, 178), 148);
	if((charStage == null) && (progress != null))
	    drawStatusChip(g, progress, sz.y - UI.scale(154), new Color(28, 24, 58, 174), 136);
	else if((charStage == null) && (login != null) && !login.visible())
	    drawStatusChip(g, heroChipf.render(worldWakeLine(), MoonUiTheme.TEXT), sz.y - UI.scale(154),
		new Color(28, 24, 58, 174), 136);
    }
}
