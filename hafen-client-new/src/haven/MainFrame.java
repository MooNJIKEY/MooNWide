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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.List;

public class MainFrame extends java.awt.Frame implements Console.Directory {
    /** Root {@link ThreadGroup} for Haven (set in {@link #main}); used for in-process parallel sessions. */
    static volatile ThreadGroup havenRootGroup;
    private volatile boolean quitWatchdogArmed = false;

    public static ThreadGroup havenThreadGroup() {
	ThreadGroup g = havenRootGroup;
	return((g != null) ? g : Thread.currentThread().getThreadGroup());
    }

    /** Window title: from pref client.name (e.g. "MooNWide" or your name). */
    public static String TITLE() {
	return Utils.getpref("client.name", "MooNWide");
    }
    public static final Config.Variable<Boolean> initfullscreen = Config.Variable.propb("haven.fullscreen", false);
    public static final Config.Variable<Boolean> initborderless = Config.Variable.propb("haven.borderlessfs", false);
    public static final Config.Variable<String> renderer = Config.Variable.prop("haven.renderer", "jogl");
    public static final Config.Variable<Boolean> status = Config.Variable.propb("haven.status", false);
    private static volatile MainFrame instance;
    final UIPanel p;
    private final ThreadGroup g;
    private Thread mt;
    boolean fullscreen;
    boolean borderless;
    DisplayMode fsmode = null, prefs = null;
    Coord prefssz = null;
    Rectangle windowedBounds = null;
    int windowedState = NORMAL;
    boolean windowedResizable = true;

    public static MainFrame getInstance() {
	return(instance);
    }

    public boolean isBorderlessFullscreen() {
	return(borderless);
    }

    public boolean isExclusiveFullscreen() {
	return(fullscreen);
    }

    public boolean isFullscreenLike() {
	return(fullscreen || borderless);
    }

    public static void initlocale() {
	try {
	    /* XXX? Localization is nice and all, but the game as a
	     * whole currently isn't internationalized, so using the
	     * local settings for things like number formatting just
	     * leads to inconsistency.
	     *
	     * The locale still seems to influence AWT default font
	     * selection, though. This should be investigated. */
	    Locale.setDefault(Locale.US);
	} catch(Exception e) {
	    new Warning(e, "locale initialization failed").issue();
	}
    }
	
    public static void initawt() {
	try {
	    System.setProperty("apple.awt.application.name", "Haven & Hearth");
	    javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
	    // Cache DNS forever to avoid sporadic UnknownHostException when fetching resources
	    java.security.Security.setProperty("networkaddress.cache.ttl", "-1");
	} catch(Exception e) {
	    new Warning(e, "AWT initialization failed").issue();
	}
    }

    static {
	initlocale();
	initawt();
    }
	
    DisplayMode findmode(int w, int h) {
	GraphicsDevice dev = getGraphicsConfiguration().getDevice();
	if(!dev.isFullScreenSupported())
	    return(null);
	DisplayMode b = null;
	for(DisplayMode m : dev.getDisplayModes()) {
	    int d = m.getBitDepth();
	    if((m.getWidth() == w) && (m.getHeight() == h) && ((d == 24) || (d == 32) || (d == DisplayMode.BIT_DEPTH_MULTI))) {
		if((b == null) || (d > b.getBitDepth()) || ((d == b.getBitDepth()) && (m.getRefreshRate() > b.getRefreshRate())))
		    b = m;
	    }
	}
	return(b);
    }

    private Rectangle currentScreenBounds() {
	try {
	    GraphicsConfiguration gc = getGraphicsConfiguration();
	    if(gc != null)
		return(new Rectangle(gc.getBounds()));
	} catch(Exception e) {
	}
	try {
	    PointerInfo pi = MouseInfo.getPointerInfo();
	    if((pi != null) && (pi.getDevice() != null))
		return(new Rectangle(pi.getDevice().getDefaultConfiguration().getBounds()));
	} catch(Exception e) {
	}
	Rectangle usable = maximumUsableBounds();
	return(new Rectangle(usable.x, usable.y, usable.width, usable.height));
    }

    private void savewindowedstate() {
	if(fullscreen || borderless)
	    return;
	windowedBounds = getBounds();
	windowedState = getExtendedState();
	windowedResizable = isResizable();
	prefssz = new Coord(getSize());
    }
	
    public void setfs() {
	GraphicsDevice dev = getGraphicsConfiguration().getDevice();
	if(fullscreen)
	    return;
	if(borderless)
	    setwnd();
	fullscreen = true;
	savewindowedstate();
	try {
	    setVisible(false);
	    dispose();
	    setUndecorated(true);
	    setVisible(true);
	    dev.setFullScreenWindow(this);
	    if(fsmode != null) {
		prefs = dev.getDisplayMode();
		dev.setDisplayMode(fsmode);
	    }
	    pack();
	} catch(Exception e) {
	    throw(new RuntimeException(e));
	}
    }

    public void setbfs() {
	if(borderless)
	    return;
	if(fullscreen)
	    setwnd();
	savewindowedstate();
	borderless = true;
	try {
	    Rectangle bounds = currentScreenBounds();
	    setVisible(false);
	    dispose();
	    setUndecorated(true);
	    setResizable(false);
	    setVisible(true);
	    setExtendedState(NORMAL);
	    setBounds(bounds);
	    p.setSize(bounds.width, bounds.height);
	    validate();
	    toFront();
	    ((Component)p).requestFocus();
	} catch(Exception e) {
	    borderless = false;
	    throw(new RuntimeException(e));
	}
    }
	
    public void setwnd() {
	GraphicsDevice dev = getGraphicsConfiguration().getDevice();
	if(!fullscreen && !borderless)
	    return;
	try {
	    if(fullscreen) {
		if(prefs != null)
		    dev.setDisplayMode(prefs);
		dev.setFullScreenWindow(null);
	    }
	    setVisible(false);
	    dispose();
	    setUndecorated(false);
	    setResizable(windowedResizable);
	    if(windowedBounds != null)
		setBounds(windowedBounds);
	    else if(prefssz != null)
		setSize(prefssz.x, prefssz.y);
	    setVisible(true);
	    if((windowedState & MAXIMIZED_BOTH) != 0)
		setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
	    ((Component)p).requestFocus();
	} catch(Exception e) {
	    throw(new RuntimeException(e));
	}
	fullscreen = false;
	borderless = false;
    }

    public void setborderless(boolean on) {
	Utils.setprefb("wndborderless", on);
	if(on)
	    setbfs();
	else
	    setwnd();
    }

    /** Trigger the same graceful shutdown path as closing the window frame. */
    public void requestQuit() {
	Runnable quit = () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
	if(EventQueue.isDispatchThread())
	    quit.run();
	else
	    EventQueue.invokeLater(quit);
    }

    private void armQuitWatchdog() {
	synchronized(this) {
	    if(quitWatchdogArmed)
		return;
	    quitWatchdogArmed = true;
	}
	Thread watchdog = new Thread(() -> {
	    try {
		Thread.sleep(6000);
	    } catch(InterruptedException e) {
		return;
	    }
	    if(isDisplayable()) {
		Warning.warn("forcing exit after stalled shutdown");
		System.exit(0);
	    }
	}, "MoonQuitWatchdog");
	watchdog.setDaemon(true);
	watchdog.start();
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("sz", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length == 3) {
			int w = Integer.parseInt(args[1]),
			    h = Integer.parseInt(args[2]);
			p.setSize(w, h);
			pack();
			Utils.setprefc("wndsz", new Coord(w, h));
		    } else if(args.length == 2) {
			if(args[1].equals("dyn")) {
			    setResizable(true);
			    Utils.setprefb("wndlock", false);
			} else if(args[1].equals("lock")) {
			    setResizable(false);
			    Utils.setprefb("wndlock", true);
			}
		    }
		}
	    });
	cmdmap.put("fsmode", new Console.Command() {
		public void run(Console cons, String[] args) throws Exception {
		    if((args.length < 2) || args[1].equals("none")) {
			fsmode = null;
			Utils.setprefc("fsmode", Coord.z);
		    } else if(args.length == 3) {
			DisplayMode mode = findmode(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
			if(mode == null)
			    throw(new Exception("No such mode is available"));
			fsmode = mode;
			Utils.setprefc("fsmode", new Coord(mode.getWidth(), mode.getHeight()));
		    }
		}
	    });
	cmdmap.put("fs", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length >= 2) {
			if(Utils.atoi(args[1]) != 0)
			    getToolkit().getSystemEventQueue().invokeLater(MainFrame.this::setfs);
			else
			    getToolkit().getSystemEventQueue().invokeLater(MainFrame.this::setwnd);
		    }
		}
	    });
	cmdmap.put("bfs", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length >= 2) {
			boolean on = Utils.atoi(args[1]) != 0;
			getToolkit().getSystemEventQueue().invokeLater(() -> MainFrame.this.setborderless(on));
		    }
		}
	    });
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }

    private void seticon() {
	Image icon;
	try {
	    InputStream data = MainFrame.class.getResourceAsStream("icon.png");
	    icon = javax.imageio.ImageIO.read(data);
	    data.close();
	} catch(IOException e) {
	    throw(new Error(e));
	}
	setIconImage(icon);
	try {
	    Class<?> ctb = Class.forName("java.awt.Taskbar");
	    Object tb = ctb.getMethod("getTaskbar").invoke(null);
	    ctb.getMethod("setIconImage", Image.class).invoke(tb, icon);
	} catch(Exception e) {
	}
    }

    private UIPanel renderer() {
	String id = renderer.get();
	switch(id) {
	case "jogl":
	    return(new JOGLPanel());
	case "lwjgl":
	    return(new LWJGLPanel());
	default:
	    throw(new RuntimeException("invalid renderer specified in haven.renderer: " + id));
	}
    }

    /** Usable screen area (taskbar excluded on most systems). */
    private static Rectangle maximumUsableBounds() {
	try {
	    return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
	} catch(Exception e) {
	    return new Rectangle(0, 0, 800, 600);
	}
    }

    public MainFrame(Coord isz) {
	super("Haven & Hearth");
	Rectangle maxR = maximumUsableBounds();
	Coord sz;
	boolean startupMaximized = false;
	if(isz == null) {
	    /*
	     * First launch (no wndsz): fill the monitor work area and maximize.
	     * After that, restore saved client size and optional maximized state.
	     */
	    Coord saved = Utils.getprefc("wndsz", null);
	    if(saved == null) {
		sz = new Coord(maxR.width, maxR.height);
		startupMaximized = true;
	    } else {
		sz = saved;
		if(Utils.getprefb("wndmax", false))
		    startupMaximized = true;
	    }
	} else {
	    sz = isz;
	}
	this.g = new ThreadGroup(HackThread.tg(), "Haven client");
	instance = this;
	Component pp = (Component)(this.p = renderer());
	if(fsmode == null) {
	    Coord pfm = Utils.getprefc("fsmode", null);
	    if((pfm != null) && !pfm.equals(Coord.z))
		fsmode = findmode(pfm.x, pfm.y);
	}
	setLayout(new BorderLayout());
	add(pp, BorderLayout.CENTER);
	pp.setSize(sz.x, sz.y);
	pack();
	setResizable(!Utils.getprefb("wndlock", false));
	pp.requestFocus();
	seticon();
	setLocation(maxR.x, maxR.y);
	setVisible(true);
	if(!initfullscreen.get() && startupMaximized) {
	    getToolkit().getSystemEventQueue().invokeLater(() -> {
		if(!fullscreen)
		    setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
	    });
	}
	addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) {
		    armQuitWatchdog();
		    mt.interrupt();
		}

		public void windowActivated(WindowEvent e) {
		    p.background(false);
		}

		public void windowDeactivated(WindowEvent e) {
		    p.background(true);
		}
	    });
    }
	
    private void savewndstate() {
	if(!fullscreen) {
	    if(getExtendedState() == NORMAL)
		/* Apparent, getSize attempts to return the "outer
		 * size" of the window, including WM decorations, even
		 * though setSize sets the "inner size" of the
		 * window. Therefore, use the Panel's size instead; it
		 * ought to correspond to the inner size at all
		 * times. */{
		Dimension dim = p.getSize();
		Utils.setprefc("wndsz", new Coord(dim.width, dim.height));
	    }
	    Utils.setprefb("wndmax", (getExtendedState() & MAXIMIZED_BOTH) != 0);
	}
    }

    public static class ConnectionError extends RuntimeException {
	public ConnectionError(String mesg) {
	    super(mesg);
	}
    }

    public static Session connect(Object[] args) {
	Session.User acct;
	byte[] cookie;
	NamedSocketAddress gameserv = (Bootstrap.gameserv.get() != null) ?
	    Bootstrap.gameserv.get() :
	    new NamedSocketAddress(Bootstrap.authserv.get().host, Bootstrap.gameport.get());
	if((Bootstrap.authuser.get() != null) && (Bootstrap.authck.get() != null)) {
	    acct = new Session.User(Bootstrap.authuser.get());
	    cookie = Bootstrap.authck.get();
	} else {
	    String username;
	    if(Bootstrap.authuser.get() != null) {
		username = Bootstrap.authuser.get();
	    } else {
		if((username = Utils.getpref("tokenname@" + Bootstrap.authserv.get().host, null)) == null)
		    throw(new ConnectionError("no explicit or saved username for host: " + Bootstrap.authserv.get().host));
	    }
	    String token = Utils.getpref("savedtoken-" + username + "@" + Bootstrap.authserv.get().host, null);
	    if(token == null)
		throw(new ConnectionError("no saved token for user: " + username));
	    try {
		AuthClient cl = new AuthClient(Bootstrap.authserv.get());
		try {
		    try {
			acct = new Session.User(new AuthClient.TokenCred(username, Utils.hex.dec(token)).tryauth(cl));
		    } catch(AuthClient.Credentials.AuthException e) {
			throw(new ConnectionError("authentication with saved token failed"));
		    }
		    cookie = cl.getcookie();
		    List<NamedSocketAddress> hosts = cl.gethosts(gameserv);
		    if(!hosts.isEmpty())
			gameserv = hosts.get(0);
		} finally {
		    cl.close();
		}
	    } catch(IOException e) {
		throw(new RuntimeException(e));
	    }
	}
	try {
	    return(new Session(new java.net.InetSocketAddress(Utils.hostaddr(gameserv.host), gameserv.port), acct, Connection.encrypt.get(), cookie, args));
	} catch(Connection.SessionError e) {
	    throw(new ConnectionError(e.getMessage()));
	} catch(InterruptedException exc) {
	    throw(new RuntimeException(exc));
	} catch(IOException e) {
	    throw(new RuntimeException(e));
	}
    }

    private void uiloop() throws InterruptedException {
	UI.Runner fun = null;
	while(true) {
	    if(fun == null)
		fun = new Bootstrap();
	    String t = fun.title();
	    if(t == null)
		setTitle(TITLE());
	    else
		setTitle(TITLE() + " \u2013 " + t);
	    fun = fun.run(p.newui(fun));
	}
    }

    private void run(UI.Runner task) {
	synchronized(this) {
	    if(this.mt != null)
		throw(new RuntimeException("MainFrame is already running"));
	    this.mt = Thread.currentThread();
	}
	try {
	    Thread ui = new HackThread(p, "Haven UI thread");
	    ui.start();
	    try {
		try {
		    if(task == null) {
			uiloop();
		    } else {
			while(task != null)
			    task = task.run(p.newui(task));
		    }
		} catch(InterruptedException e) {
		} finally {
		    p.newui(null);
		}
		savewndstate();
	    } finally {
		ui.interrupt();
		try {
		    ui.join(5000);
		} catch(InterruptedException e) {}
		if(ui.isAlive())
		    Warning.warn("ui thread failed to terminate");
		dispose();
	    }
	} finally {
	    synchronized(this) {
		this.mt = null;
	    }
	}
    }
    
    public static final Config.Variable<Boolean> nopreload = Config.Variable.propb("haven.nopreload", false);
    public static void setupres() {
	if(ResCache.global != null)
	    Resource.setcache(ResCache.global);
	if(Resource.resurl.get() != null)
	    Resource.addurl(Resource.resurl.get());
	if(ResCache.global != null) {
	    /*
	    try {
		Resource.loadlist(Resource.remote(), ResCache.global.fetch("tmp/allused"), -10);
	    } catch(IOException e) {}
	    */
	}
	if(!nopreload.get()) {
	    try {
		InputStream pls;
		pls = Resource.class.getResourceAsStream("res-preload");
		if(pls != null)
		    Resource.loadlist(Resource.remote(), pls, -5);
		pls = Resource.class.getResourceAsStream("res-bgload");
		if(pls != null)
		    Resource.loadlist(Resource.remote(), pls, -10);
	    } catch(IOException e) {
		throw(new Error(e));
	    }
	}
    }

    public static final Config.Variable<Path> loadwaited = Config.Variable.propp("haven.loadwaited", "");
    public static final Config.Variable<Path> allused = Config.Variable.propp("haven.allused", "");
    public static void resdump() {
	try {
	    dumplist(Resource.remote().loadwaited(), loadwaited.get());
	} catch(Throwable e) {
	    new Warning(e, "Skipping loadwaited resdump after shutdown failure").issue();
	}
	try {
	    dumplist(Resource.remote().cached(), allused.get());
	} catch(Throwable e) {
	    new Warning(e, "Skipping cached resdump after shutdown failure").issue();
	}
	if(ResCache.global != null) {
	    try {
		Writer w = new OutputStreamWriter(ResCache.global.store("tmp/allused"), "UTF-8");
		try {
		    Resource.dumplist(Resource.remote().used(), w);
		} finally {
		    w.close();
		}
	    } catch(Throwable e) {
		new Warning(e, "Skipping global resdump after shutdown failure").issue();
	    }
	}
    }

    static {
	WebBrowser.self = DesktopBrowser.create();
    }

    private static void javabughack() throws InterruptedException {
	/* Work around a stupid deadlock bug in AWT. */
	try {
	    javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
		    public void run() {
			PrintStream bitbucket = new PrintStream(new ByteArrayOutputStream());
			bitbucket.print(LoginScreen.textf);
			bitbucket.print(LoginScreen.textfs);
		    }
		});
	} catch(java.lang.reflect.InvocationTargetException e) {
	    /* Oh, how I love Swing! */
	    throw(new Error(e));
	}
	/* Work around another deadl bug in Sun's JNLP client. */
	javax.imageio.spi.IIORegistry.getDefaultInstance();
    }

    public static void status(String state) {
	if(status.get()) {
	    System.out.println("hafen:status:" + state);
	    System.out.flush();
	}
    }

    private static void main2(String[] args) {
	MoonCrashLog.install();
	Config.cmdline(args);
	try {
	    HavenNetProxy.migrateLegacyLocalProxyPrefsIfNeeded();
	    MoonConnectLog.log(HavenNetProxy.connectLogSummary());
	} catch(Throwable e) {
	    new Warning(e, "HavenNetProxy bootstrap").issue();
	}
	try {
	    LocalizationManager.bootstrap();
	} catch(Throwable e) {
	    new Warning(e, "LocalizationManager.bootstrap").issue();
	}
	try {
	    Class.forName("client.MooNWideClient").getMethod("init").invoke(null);
	} catch(Throwable e) { /* MooNWide not on classpath */ }
	status("start");
	try {
	    javabughack();
	} catch(InterruptedException e) {
	    return;
	}
	setupres();
	UI.Runner fun = null;
	if(Bootstrap.servargs.get() != null) {
	    try {
		fun = new RemoteUI(connect(Bootstrap.servargs.get()));
	    } catch(ConnectionError e) {
		System.err.println("hafen: " + e.getMessage());
		System.exit(1);
	    }
	}
	MainFrame f = new MainFrame(null);
	status("visible");
	if(initborderless.get() || Utils.getprefb("wndborderless", false))
	    f.setbfs();
	else if(initfullscreen.get())
	    f.setfs();
	f.run(fun);
	resdump();
	status("exit");
	System.exit(0);
    }
    
    public static void main(final String[] args) {
	/* Set up the error handler as early as humanly possible. */
	ThreadGroup g = new ThreadGroup("Haven main group");
	String ed = Utils.getprop("haven.errorurl", "");
	if(ed.equals("stderr")) {
	    g = new haven.error.SimpleHandler("Haven main group", true);
	} else if(!ed.equals("")) {
	    try {
		final haven.error.ErrorHandler hg = new haven.error.ErrorHandler(new java.net.URI(ed).toURL());
		hg.sethandler(new haven.error.ErrorGui(null) {
			public void errorsent() {
			    hg.interrupt();
			}
		    });
		g = hg;
		new DeadlockWatchdog(hg).start();
	    } catch(java.net.MalformedURLException | java.net.URISyntaxException e) {
	    }
	}
	havenRootGroup = g;
	Thread main = new HackThread(g, () -> main2(args), "Haven main thread");
	main.start();
    }
	
    private static void dumplist(Collection<Resource> list, Path fn) {
	try {
	    if(fn != null) {
		try(Writer w = Files.newBufferedWriter(fn, Utils.utf8)) {
		    Resource.dumplist(list, w);
		}
	    }
	} catch(IOException e) {
	    throw(new RuntimeException(e));
	}
    }
}
