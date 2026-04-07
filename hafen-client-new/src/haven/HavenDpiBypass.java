package haven;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 * Master DPI workarounds for Haven client traffic we can affect without changing game protocol:
 * login TLS (HOBF + split), and outbound TCP to proxies / direct auth (write splitting).
 * Game payload UDP is unchanged; use SOCKS5 UDP relay to hide it inside proxy framing.
 */
public final class HavenDpiBypass {
    public static final String PREF_MASTER = "haven-dpi-bypass";
    /** Bytes per outbound TCP connection to send as small segmented writes (handshakes, TLS). */
    public static final String PREF_TCP_SPLIT_BUDGET = "haven-dpi-tcp-split-budget";

    private HavenDpiBypass() {}

    public static boolean master() {
	return Utils.getprefb(PREF_MASTER, false);
    }

    public static boolean authObfFirst() {
	return master() || Utils.getprefb("haven-auth-dpi-obf-first", false);
    }

    /** When master is on, TCP split is already applied in {@link HavenNetProxy#dialTcpThrough}. */
    public static boolean authExtraSplitLayer() {
	return Utils.getprefb("haven-auth-dpi-split", false) && !master();
    }

    public static int tcpSplitBudget() {
	int b = Utils.getprefi(PREF_TCP_SPLIT_BUDGET, 8192);
	return Math.max(0, b);
    }

    public static ByteChannel wrapTcpOutboundSplit(SocketChannel raw) throws IOException {
	if(raw == null || !master() || tcpSplitBudget() <= 0)
	    return raw;
	/* Splitting writes to loopback can break or stall SOCKS/HTTP handshakes to the embedded proxy. */
	try {
	    SocketAddress ra = raw.getRemoteAddress();
	    if(ra instanceof InetSocketAddress) {
		InetAddress a = ((InetSocketAddress) ra).getAddress();
		if(a != null && a.isLoopbackAddress())
		    return raw;
	    }
	} catch(IOException ignored) {}
	return new DpiSplitWriteChannel(raw, tcpSplitBudget());
    }

    /**
     * Remote address for TLS / SOCKS control paths when the transport may be {@link DpiSplitWriteChannel}.
     */
    public static SocketAddress remoteAddressOrNull(ByteChannel ch) throws IOException {
	SocketChannel sc = unwrapSocketChannel(ch);
	return sc != null ? sc.getRemoteAddress() : null;
    }

    public static SocketChannel unwrapSocketChannel(ByteChannel ch) {
	for(int guard = 0; guard < 8 && ch != null; guard++) {
	    if(ch instanceof SocketChannel)
		return(SocketChannel) ch;
	    if(ch instanceof DpiSplitWriteChannel)
		ch = ((DpiSplitWriteChannel) ch).wrapped();
	    else
		return null;
	}
	return null;
    }
}
