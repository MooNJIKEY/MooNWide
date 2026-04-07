package haven;

import java.net.URI;
import java.util.Objects;

/**
 * Describes one hop for outbound networking. Used for game UDP (SOCKS5 with UDP associate)
 * and auth TCP (HTTP CONNECT or direct).
 */
public final class NetEndpoint {
    public enum Kind {
	DIRECT,
	SOCKS5,
	HTTP_CONNECT
    }

    public final Kind kind;
    public final String host;
    public final int port;
    public final String user;
    public final String pass;

    public NetEndpoint(Kind kind, String host, int port, String user, String pass) {
	this.kind = Objects.requireNonNull(kind);
	this.host = host;
	this.port = port;
	this.user = user == null ? "" : user;
	this.pass = pass == null ? "" : pass;
    }

    public static NetEndpoint direct() {
	return new NetEndpoint(Kind.DIRECT, "", 0, "", "");
    }

    /**
     * URI-like forms:
     * {@code socks5://host:port}, {@code socks5://user:pass@host:port},
     * {@code http-connect://host:port} (plain HTTP CONNECT to proxy, then TLS to auth server).
     */
    public static NetEndpoint parse(String spec) {
	if(spec == null || (spec = spec.trim()).isEmpty() || spec.equalsIgnoreCase("direct"))
	    return direct();
	String s = spec;
	if(!s.contains("://")) {
	    if(s.regionMatches(true, 0, "socks5:", 0, 7))
		s = "socks5://" + s.substring(7);
	    else if(s.regionMatches(true, 0, "http-connect:", 0, 13))
		s = "http-connect://" + s.substring(13);
	    else if(s.regionMatches(true, 0, "http:", 0, 5))
		s = "http-connect://" + s.substring(5);
	}
	URI u;
	try {
	    u = URI.create(s);
	} catch(IllegalArgumentException e) {
	    throw new IllegalArgumentException("Bad proxy spec: " + spec, e);
	}
	String scheme = u.getScheme();
	if(scheme == null)
	    throw new IllegalArgumentException("Bad proxy spec (no scheme): " + spec);
	String h = u.getHost();
	int p = u.getPort();
	if(h == null || h.isEmpty() || p < 1 || p > 65535)
	    throw new IllegalArgumentException("Bad proxy host/port: " + spec);
	String ui = u.getRawUserInfo();
	String usr = "", pwd = "";
	if(ui != null && !ui.isEmpty()) {
	    int c = ui.indexOf(':');
	    if(c >= 0) {
		usr = uriDecode(ui.substring(0, c));
		pwd = uriDecode(ui.substring(c + 1));
	    } else {
		usr = uriDecode(ui);
	    }
	}
	switch(scheme.toLowerCase(java.util.Locale.ROOT)) {
	case "socks5":
	case "socks":
	    return new NetEndpoint(Kind.SOCKS5, h, p, usr, pwd);
	case "http-connect":
	case "http":
	    return new NetEndpoint(Kind.HTTP_CONNECT, h, p, usr, pwd);
	default:
	    throw new IllegalArgumentException("Unknown proxy scheme: " + scheme + " in " + spec);
	}
    }

    private static String uriDecode(String s) {
	try {
	    return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
	} catch(Exception e) {
	    return s;
	}
    }

    @Override
    public String toString() {
	if(kind == Kind.DIRECT)
	    return "direct";
	return kind + "://" + (user.isEmpty() ? "" : (user + "@")) + host + ":" + port;
    }
}
