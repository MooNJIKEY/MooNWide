package haven.discord;

import com.sun.net.httpserver.HttpServer;
import haven.GameUI;
import haven.HackThread;
import haven.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP listener for Discord bot {@code POST /notify} and heartbeat {@code POST /status} to the bot API.
 * Prefs: {@code moon-discord-enabled}, {@code moon-discord-port} (default 3001),
 * {@code moon-discord-bot-url} (default http://127.0.0.1:3000), {@code moon-discord-secret},
 * {@code moon-discord-heartbeat-sec} (default 20). userId = hex hash of chrid+plid (no raw names on wire).
 * Heartbeat sends {@code notifyUrl} so the bot can fan out /announce to every online client.
 * Prefs: {@code moon-discord-notify-url} (full base e.g. http://192.168.1.10:3001) overrides host/port;
 * else {@code moon-discord-listen-host} (default 127.0.0.1) + {@code moon-discord-port}.
 */
public final class MoonDiscordService {
    private static MoonDiscordService instance;
    private static volatile long lastStatusErrLogMs;

    private HttpServer server;
    private HackThread heartbeat;
    private volatile boolean hbRun;
    private volatile GameUI gui;
    private String secret = "";

    public static synchronized MoonDiscordService get() {
	if(instance == null)
	    instance = new MoonDiscordService();
	return(instance);
    }

    public static boolean enabled() {
	return(Utils.getprefb("moon-discord-enabled", false));
    }

    public static void startFor(GameUI g) {
	if(!enabled())
	    return;
	get().bindAndStart(g);
    }

    public static void stopAll() {
	if(instance != null)
	    instance.stop();
    }

    /** Stop listener/heartbeat, then start again from current prefs if enabled (call after changing prefs from UI). */
    public static synchronized void reload(GameUI g) {
	if(instance != null) {
	    instance.stop();
	    instance = null;
	}
	if(g != null)
	    startFor(g);
    }

    String getSecret() {
	return(secret);
    }

    void deliverNotify(String json) {
	GameUI g = gui;
	if(g == null) {
	    logThrottled("moon-discord: notify dropped (no GameUI session)");
	    return;
	}
	String type = MoonDiscordJson.str(json, "type");
	String title = MoonDiscordJson.str(json, "title");
	String message = MoonDiscordJson.str(json, "message");
	try {
	    g.ui.loader.defer(() -> MoonDiscordNotifyWnd.showPopup(g, type, title, message), null);
	} catch(Exception e) {
	    logThrottled("moon-discord: notify defer failed: " + e.getMessage());
	}
    }

    private static void logThrottled(String msg) {
	long n = System.currentTimeMillis();
	if(n - lastStatusErrLogMs < 10_000L)
	    return;
	lastStatusErrLogMs = n;
	System.err.println(msg);
    }

    private void bindAndStart(GameUI g) {
	this.secret = Utils.getpref("moon-discord-secret", "");
	if(this.secret == null || this.secret.isEmpty()) {
	    System.err.println("moon-discord: moon-discord-secret empty; HTTP server not started.");
	    return;
	}
	synchronized(this) {
	    if(server != null) {
		this.gui = g;
		postStatus(g);
		return;
	    }
	    this.gui = g;
	    try {
		int port = Utils.getprefi("moon-discord-port", 3001);
		/* 0.0.0.0 — приём POST /notify с других машин в LAN (брандмауэр должен пускать порт). */
		server = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port), 0);
		server.createContext("/notify", new MoonDiscordHttp(this));
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
		System.err.println("moon-discord: listening POST /notify on 0.0.0.0:" + port);
	    } catch(IOException e) {
		System.err.println("moon-discord: failed to bind: " + e.getMessage());
		server = null;
		return;
	    }
	    hbRun = true;
	    heartbeat = new HackThread(this::heartbeatLoop, "moon-discord-heartbeat");
	    heartbeat.setDaemon(true);
	    heartbeat.start();
	    postStatus(g);
	}
    }

    private void heartbeatLoop() {
	while(hbRun) {
	    try {
		Thread.sleep(Math.max(5, Utils.getprefi("moon-discord-heartbeat-sec", 20)) * 1000L);
	    } catch(InterruptedException e) {
		break;
	    }
	    if(!hbRun)
		break;
	    GameUI g = gui;
	    if(g == null)
		continue;
	    postStatus(g);
	}
    }

    private void postStatus(GameUI g) {
	String base = stripTrailingSlash(Utils.getpref("moon-discord-bot-url", "http://127.0.0.1:3000"));
	String url = base + "/status";
	String uid = userId(g);
	String notifyBase = notifyUrlBase();
	String body = "{\"userId\":\"" + escapeJson(uid) + "\",\"online\":true,\"notifyUrl\":\"" + escapeJson(notifyBase) + "\"}";
	HttpURLConnection c = null;
	try {
	    c = (HttpURLConnection)new URL(url).openConnection();
	    c.setRequestMethod("POST");
	    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
	    c.setRequestProperty("Authorization", "Bearer " + secret);
	    c.setDoOutput(true);
	    c.setConnectTimeout(8000);
	    c.setReadTimeout(8000);
	    byte[] b = body.getBytes(StandardCharsets.UTF_8);
	    c.setFixedLengthStreamingMode(b.length);
	    try(OutputStream os = c.getOutputStream()) {
		os.write(b);
	    }
	    int code = c.getResponseCode();
	    drainHttpBody(c, code);
	    if(code < 200 || code >= 300)
		logThrottled("moon-discord: POST /status HTTP " + code + " (проверьте API_SECRET и moon-discord-bot-url)");
	} catch(Exception e) {
	    logThrottled("moon-discord: POST /status failed: " + e.getMessage());
	} finally {
	    if(c != null)
		c.disconnect();
	}
    }

    private static void drainHttpBody(HttpURLConnection c, int code) throws IOException {
	InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
	if(in == null)
	    return;
	byte[] buf = new byte[512];
	while(in.read(buf) >= 0) {
	}
	in.close();
    }

    private static String stripTrailingSlash(String s) {
	if(s == null)
	    return("");
	String t = s.trim();
	while(t.endsWith("/"))
	    t = t.substring(0, t.length() - 1);
	return(t);
    }

    /** Base URL this game client exposes for POST /notify (must be reachable from the Discord bot machine). */
    private static String notifyUrlBase() {
	String custom = Utils.getpref("moon-discord-notify-url", "");
	if(custom != null && !custom.isEmpty()) {
	    return(stripTrailingSlash(custom));
	}
	int port = Utils.getprefi("moon-discord-port", 3001);
	String host = Utils.getpref("moon-discord-listen-host", "127.0.0.1").trim();
	if(host.isEmpty())
	    host = "127.0.0.1";
	return("http://" + host + ":" + port);
    }

    private static String userId(GameUI g) {
	String raw = String.valueOf(g.chrid) + ":" + g.plid;
	int h = raw.hashCode();
	return(Integer.toHexString(h) + Long.toHexString(g.plid));
    }

    private static String escapeJson(String s) {
	if(s == null)
	    return("");
	StringBuilder sb = new StringBuilder();
	for(int i = 0; i < s.length(); i++) {
	    char c = s.charAt(i);
	    if(c == '"' || c == '\\')
		sb.append('\\');
	    sb.append(c);
	}
	return(sb.toString());
    }

    private synchronized void stop() {
	hbRun = false;
	if(heartbeat != null)
	    heartbeat.interrupt();
	heartbeat = null;
	if(server != null) {
	    server.stop(1);
	    server = null;
	}
	gui = null;
    }
}
