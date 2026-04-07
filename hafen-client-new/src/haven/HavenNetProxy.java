package haven;

import java.io.*;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Proxy chains for Haven & Hearth. <b>All</b> client traffic uses these prefs (or proxy file):
 * game UDP via {@link #gameChain()}, login/auth TCP via {@link #authChain()}. If the auth chain
 * is unset, the game chain is used for login too (one field covers everything).
 * <p>
 * Game traffic is <b>UDP</b> — only SOCKS5 with UDP ASSOCIATE can relay it; HTTP CONNECT is TCP-only.
 * <p>
 * Configuration (first non-empty wins per key): system properties, then prefs
 * {@code haven-net-proxyfile}, {@code haven-net-game-chain}, {@code haven-net-auth-chain}.
 * <p>
 * Chain format: {@code socks5://host:port|direct} (entries separated by {@code |}).
 * With {@link #strictProxy()} (default on), {@code direct} hops are dropped whenever the chain
 * also contains SOCKS5 or HTTP CONNECT — no silent fallback around a broken proxy.
 */
public final class HavenNetProxy {
    private HavenNetProxy() {}

    public static final String PROP_PROXY_FILE = "haven.net.proxyfile";
    public static final String PREF_PROXY_FILE = "haven-net-proxyfile";
    public static final String PREF_GAME_CHAIN = "haven-net-game-chain";
    public static final String PREF_AUTH_CHAIN = "haven-net-auth-chain";
    /** When true (default), strip {@code direct} from chains that include a proxy hop. */
    public static final String PREF_PROXY_STRICT = "haven-net-proxy-strict";
    public static final String KEY_GAME_CHAIN = "game.chain";
    public static final String KEY_AUTH_CHAIN = "auth.chain";

    private static volatile List<NetEndpoint> gameChain;
    private static volatile List<NetEndpoint> authChain;

    /** If the user only ever set {@code haven-local-proxy-upstream}, copy it once to the game chain. */
    public static void migrateLegacyLocalProxyPrefsIfNeeded() {
	String j = Utils.getpref(PREF_GAME_CHAIN, "");
	if(j != null && !j.trim().isEmpty())
	    return;
	String old = Utils.getpref("haven-local-proxy-upstream", "");
	if(old != null && !old.trim().isEmpty()) {
	    Utils.setpref(PREF_GAME_CHAIN, old.trim());
	    clearCache();
	}
    }

    public static List<NetEndpoint> gameChain() {
	List<NetEndpoint> c = gameChain;
	if(c != null)
	    return c;
	synchronized(HavenNetProxy.class) {
	if(gameChain == null)
		gameChain = Collections.unmodifiableList(buildGameChainList());
	    return gameChain;
	}
    }

    public static List<NetEndpoint> authChain() {
	List<NetEndpoint> c = authChain;
	if(c != null)
	    return c;
	synchronized(HavenNetProxy.class) {
	    if(authChain == null)
		authChain = Collections.unmodifiableList(buildAuthChainList());
	    return authChain;
	}
    }

    /** Default {@code true}: never use {@code direct} if the chain lists SOCKS5 or HTTP CONNECT. */
    public static boolean strictProxy() {
	return Utils.getprefb(PREF_PROXY_STRICT, true);
    }

    private static List<NetEndpoint> buildGameChainList() {
	return applyStrictFilter(parseChain(readGameChainRaw()));
    }

    private static List<NetEndpoint> buildAuthChainList() {
	return applyStrictFilter(parseChain(readAuthChainRawEffective()));
    }

    private static List<NetEndpoint> applyStrictFilter(List<NetEndpoint> in) {
	if(!strictProxy() || in == null || in.isEmpty())
	    return in;
	boolean hasRelay = false;
	for(NetEndpoint e : in) {
	    if(e.kind == NetEndpoint.Kind.SOCKS5 || e.kind == NetEndpoint.Kind.HTTP_CONNECT)
		hasRelay = true;
	}
	if(!hasRelay)
	    return in;
	List<NetEndpoint> out = new ArrayList<>();
	for(NetEndpoint e : in) {
	    if(e.kind != NetEndpoint.Kind.DIRECT)
		out.add(e);
	}
	if(out.isEmpty())
	    out.add(NetEndpoint.direct());
	return out;
    }

    /** Java pref only (proxy file still overrides at runtime if set). */
    public static String gameChainPrefForUi() {
	return Utils.getpref(PREF_GAME_CHAIN, "");
    }

    public static String authChainPrefForUi() {
	return Utils.getpref(PREF_AUTH_CHAIN, "");
    }

    /** One-line description of the game UDP chain. */
    public static String gameChainDescription() {
	return chainDescription(gameChain());
    }

    /** One-line description of the effective login TCP chain. */
    public static String authChainDescription() {
	return chainDescription(authChain());
    }

    private static String chainDescription(List<NetEndpoint> chain) {
	StringBuilder sb = new StringBuilder();
	for(NetEndpoint ep : chain) {
	    if(sb.length() > 0)
		sb.append(" -> ");
	    sb.append(ep.toString());
	}
	return sb.toString();
    }

    public static void clearCache() {
	synchronized(HavenNetProxy.class) {
	    gameChain = null;
	    authChain = null;
	}
    }

    /** Startup log line: effective paths and hint if everything is direct. */
    public static String connectLogSummary() {
	StringBuilder sb = new StringBuilder();
	sb.append("Haven net: game UDP = ").append(gameChainDescription());
	sb.append(" | login TCP = ").append(authChainDescription());
	if(gameChainIsTrivialDirect() && authChainIsTrivialDirect()) {
	    sb.append(" | NOTE: chains empty -> all traffic direct; set ")
		.append(PREF_GAME_CHAIN)
		.append(" e.g. socks5://host:1080 (SOCKS5 with UDP for the game)");
	} else if(gameUdpIsDirectOnlyButAuthUsesProxy()) {
	    sb.append(" | WARN: game UDP chain is direct but login uses a proxy — set ")
		.append(PREF_GAME_CHAIN)
		.append(" to the same SOCKS5 (e.g. with WireGuard use socks5://user:pass@10.66.66.1:1080) or game traffic bypasses the proxy");
	} else if(strictProxy()) {
	    sb.append(" | strict-proxy=ON: direct hops ignored when SOCKS/HTTP present (no UDP/TCP bypass)");
	} else if(gameChainHasDirectFailover()) {
	    sb.append(" | NOTE: strict-proxy=OFF and game chain has |direct — SOCKS UDP failure falls back to plain UDP");
	}
	return sb.toString();
    }

    /** After {@link #strictProxy()} filter: only direct, while login chain uses SOCKS/HTTP. */
    private static boolean gameUdpIsDirectOnlyButAuthUsesProxy() {
	List<NetEndpoint> g = gameChain();
	if(g.size() != 1 || g.get(0).kind != NetEndpoint.Kind.DIRECT)
	    return false;
	for(NetEndpoint e : authChain()) {
	    if(e.kind == NetEndpoint.Kind.SOCKS5 || e.kind == NetEndpoint.Kind.HTTP_CONNECT)
		return true;
	}
	return false;
    }

    public static boolean gameChainIsTrivialDirect() {
	List<NetEndpoint> p = parseChain(readGameChainRaw());
	return p.size() == 1 && p.get(0).kind == NetEndpoint.Kind.DIRECT;
    }

    public static boolean authChainIsTrivialDirect() {
	List<NetEndpoint> p = parseChain(readAuthChainRawEffective());
	return p.size() == 1 && p.get(0).kind == NetEndpoint.Kind.DIRECT;
    }

    /** True if game chain has a SOCKS5 hop followed later by {@code direct} (silent UDP fallback). */
    public static boolean gameChainHasDirectFailover() {
	List<NetEndpoint> p = parseChain(readGameChainRaw());
	boolean sawSocks = false;
	for(NetEndpoint e : p) {
	    if(e.kind == NetEndpoint.Kind.SOCKS5)
		sawSocks = true;
	    else if(sawSocks && e.kind == NetEndpoint.Kind.DIRECT)
		return true;
	}
	return false;
    }

    private static String readGameChainRaw() {
	String fromFile = loadFileProperty(KEY_GAME_CHAIN);
	if(fromFile != null && !fromFile.isEmpty())
	    return fromFile;
	String sys = System.getProperty("haven.net.game-chain");
	if(sys != null && !sys.isEmpty())
	    return sys;
	return Utils.getpref(PREF_GAME_CHAIN, "");
    }

    private static String readAuthChainRaw() {
	String fromFile = loadFileProperty(KEY_AUTH_CHAIN);
	if(fromFile != null && !fromFile.isEmpty())
	    return fromFile;
	String sys = System.getProperty("haven.net.auth-chain");
	if(sys != null && !sys.isEmpty())
	    return sys;
	return Utils.getpref(PREF_AUTH_CHAIN, "");
    }

    /** Auth chain if set; otherwise same string as game chain (single configuration). */
    private static String readAuthChainRawEffective() {
	String a = readAuthChainRaw();
	if(a != null && !a.trim().isEmpty())
	    return a;
	return readGameChainRaw();
    }

    private static String loadFileProperty(String key) {
	Path p = proxyFilePath();
	if(p == null || !Files.isRegularFile(p))
	    return null;
	try(InputStream in = Files.newInputStream(p)) {
	    Properties pr = new Properties();
	    pr.load(new InputStreamReader(in, StandardCharsets.UTF_8));
	    return pr.getProperty(key);
	} catch(Exception e) {
	    new Warning(e, "haven net-proxy file").issue();
	    return null;
	}
    }

    private static Path proxyFilePath() {
	String s = System.getProperty(PROP_PROXY_FILE);
	if(s == null || s.isEmpty())
	    s = Utils.getpref(PREF_PROXY_FILE, "");
	if(s == null || s.isEmpty())
	    return null;
	try {
	    return Utils.path(s);
	} catch(Exception e) {
	    return null;
	}
    }

    public static List<NetEndpoint> parseChainPublic(String raw) {
	return parseChain(raw);
    }

    private static List<NetEndpoint> parseChain(String raw) {
	if(raw == null || raw.trim().isEmpty())
	    return Collections.singletonList(NetEndpoint.direct());
	List<NetEndpoint> out = new ArrayList<>();
	for(String part : raw.split("\\|")) {
	    part = part.trim();
	    if(part.isEmpty())
		continue;
	    try {
		out.add(NetEndpoint.parse(part));
	    } catch(IllegalArgumentException e) {
		new Warning(e, "haven net-proxy chain entry: " + part).issue();
	    }
	}
	if(out.isEmpty())
	    out.add(NetEndpoint.direct());
	return out;
    }

    public static ByteChannel connectAuthTcp(NamedSocketAddress target) throws IOException {
	return dialTcpThrough(authChain(), target.host, target.port);
    }

    /**
     * Open TCP to {@code host}:{@code port} using the first working hop in {@code chain}.
     */
    public static ByteChannel dialTcpThrough(List<NetEndpoint> chain, String host, int port)
	throws IOException {
	IOException last = null;
	for(NetEndpoint ep : chain) {
	    try {
		switch(ep.kind) {
		case DIRECT:
		    return HavenDpiBypass.wrapTcpOutboundSplit(Utils.connect(new NamedSocketAddress(host, port)));
		case HTTP_CONNECT:
		    return HttpConnectTunnel.connectThrough(ep, host, port);
		case SOCKS5:
		    return Socks5TcpTunnel.connectThroughTcp(ep, host, port);
		default:
		    continue;
		}
	    } catch(IOException e) {
		last = e;
	    }
	}
	if(last != null)
	    throw last;
	throw new IOException("no usable proxy chain entries for TCP");
    }
}
