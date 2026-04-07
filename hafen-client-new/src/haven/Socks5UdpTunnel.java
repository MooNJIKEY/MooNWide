package haven;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

/**
 * SOCKS5 UDP ASSOCIATE (RFC 1928). Wraps game datagrams for a relay; requires the TCP control
 * connection to stay open for the lifetime of the UDP session.
 */
public final class Socks5UdpTunnel implements Closeable {
    private final NetEndpoint proxy;
    private ByteChannel ctl;

    public Socks5UdpTunnel(NetEndpoint proxy) {
	if(proxy.kind != NetEndpoint.Kind.SOCKS5)
	    throw new IllegalArgumentException("SOCKS5 only");
	this.proxy = proxy;
    }

    /**
     * Negotiate UDP relay and {@link DatagramChannel#connect(SocketAddress) connect} UDP to the
     * SOCKS relay endpoint.
     */
    public void establish(DatagramChannel udp, InetSocketAddress gameServer) throws IOException {
	SocketChannel raw = SocketChannel.open();
	raw.socket().setSoTimeout(20000);
	raw.configureBlocking(true);
	raw.connect(new InetSocketAddress(proxy.host, proxy.port));
	ByteChannel ch = raw;
	boolean ok = false;
	try {
	    Socks5TcpTunnel.socks5Handshake(ch, proxy);

	    ByteBuffer req = ByteBuffer.allocate(16);
	    req.put((byte)5).put((byte)3).put((byte)0).put((byte)1);
	    req.putInt(0);
	    req.putShort((short)0);
	    req.flip();
	    while(req.hasRemaining())
		ch.write(req);

	    InetSocketAddress relay = normalizeSocksUdpRelay(ch, readUdpAssociateReply(ch));
	    udp.configureBlocking(false);
	    udp.connect(relay);
	    MoonConnectLog.log("socks5-udp: UDP relay (send SOCKS-framed packets here) = "
		+ relay.getAddress().getHostAddress() + ":" + relay.getPort()
		+ " | if login hangs, check: cloud inbound UDP to this port, 3proxy -e public IP, home router UDP");
	    this.ctl = ch;
	    ok = true;
	} finally {
	    if(!ok) {
		try {
		    ch.close();
		} catch(IOException ignored) {}
	    }
	}
    }

    /**
     * SOCKS servers often return BND.ADDR {@code 0.0.0.0} or {@code ::} for UDP ASSOCIATE, meaning
     * "send datagrams to the same host as the TCP control connection" (RFC 1928). Connecting a
     * {@link DatagramChannel} to ANY is wrong on many OSes; use the proxy's peer address instead.
     * <p>
     * If the proxy was reached over a tunnel (e.g. WireGuard to {@code 10.66.66.1}) but 3proxy
     * advertises {@code -e} as the VPS public IP, the relay address is a global IP while the TCP
     * peer is private — OS would send game UDP via the default route instead of the tunnel. Use
     * the TCP peer with the relay UDP port in that case.
     */
    private static InetSocketAddress normalizeSocksUdpRelay(ByteChannel ctl, InetSocketAddress relay)
	throws IOException {
	if(relay == null)
	    return relay;
	InetAddress relayAddr = relay.getAddress();
	if(relayAddr != null && relayAddr.isAnyLocalAddress()) {
	    SocketChannel sc = HavenDpiBypass.unwrapSocketChannel(ctl);
	    if(sc != null) {
		SocketAddress ra = sc.getRemoteAddress();
		if(ra instanceof InetSocketAddress) {
		    InetAddress peer = ((InetSocketAddress) ra).getAddress();
		    return new InetSocketAddress(peer, relay.getPort());
		}
	    }
	    return relay;
	}
	if(relayAddr != null && isPublicUnicast(relayAddr)) {
	    SocketChannel sc = HavenDpiBypass.unwrapSocketChannel(ctl);
	    if(sc != null) {
		SocketAddress ra = sc.getRemoteAddress();
		if(ra instanceof InetSocketAddress) {
		    InetAddress peer = ((InetSocketAddress) ra).getAddress();
		    if(peer != null && isTunnelSideAddress(peer))
			return new InetSocketAddress(peer, relay.getPort());
		}
	    }
	}
	return relay;
    }

    /** Site-local, link-local, loopback, CGNAT (100.64/10), or IPv6 ULA — typical VPN/tunnel path. */
    private static boolean isTunnelSideAddress(InetAddress a) {
	if(a.isLoopbackAddress() || a.isLinkLocalAddress())
	    return true;
	if(a.isSiteLocalAddress())
	    return true;
	if(a instanceof Inet4Address) {
	    byte[] x = a.getAddress();
	    if(x.length == 4) {
		int b0 = x[0] & 0xff, b1 = x[1] & 0xff;
		if(b0 == 100 && b1 >= 64 && b1 <= 127)
		    return true;
	    }
	}
	if(a instanceof Inet6Address) {
	    byte[] x = a.getAddress();
	    if(x.length >= 2 && (x[0] & 0xfe) == 0xfc)
		return true;
	}
	return false;
    }

    private static boolean isPublicUnicast(InetAddress a) {
	if(a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isMulticastAddress()
	    || a.isLinkLocalAddress())
	    return false;
	if(a.isSiteLocalAddress())
	    return false;
	if(a instanceof Inet6Address) {
	    byte[] x = a.getAddress();
	    if(x.length >= 2 && (x[0] & 0xfe) == 0xfc)
		return false;
	}
	return true;
    }

    private static InetSocketAddress readUdpAssociateReply(ByteChannel ch) throws IOException {
	ByteBuffer hdr = ByteBuffer.allocate(4);
	Socks5TcpTunnel.readFully(ch, hdr, 4);
	byte ver = hdr.get();
	byte rep = hdr.get();
	if(ver != 5)
	    throw new IOException("SOCKS5 bad reply version");
	if(rep != 0)
	    throw new IOException("SOCKS5 UDP ASSOCIATE failed, rep=" + (rep & 0xff) + " " + socksRepHint(rep));
	hdr.get();
	int atyp = hdr.get() & 0xff;
	InetSocketAddress r = readEndpoint(ch, atyp);
	if(r.isUnresolved())
	    r = new InetSocketAddress(InetAddress.getByName(r.getHostString()), r.getPort());
	return r;
    }

    private static String socksRepHint(byte rep) {
	switch(rep) {
	case 1:
	    return "(general failure)";
	case 2:
	    return "(not allowed)";
	case 3:
	    return "(network unreachable)";
	case 4:
	    return "(host unreachable)";
	case 5:
	    return "(connection refused)";
	case 7:
	    return "(command not supported — server may not implement UDP ASSOCIATE)";
	case 8:
	    return "(address type not supported)";
	default:
	    return "";
	}
    }

    private static InetSocketAddress readEndpoint(ByteChannel ch, int atyp) throws IOException {
	switch(atyp) {
	case 1: {
	    ByteBuffer b = ByteBuffer.allocate(6);
	    Socks5TcpTunnel.readFully(ch, b, 6);
	    byte[] ip = new byte[4];
	    b.get(ip);
	    int port = Short.toUnsignedInt(b.getShort());
	    return new InetSocketAddress(InetAddress.getByAddress(ip), port);
	}
	case 3: {
	    ByteBuffer l = ByteBuffer.allocate(1);
	    Socks5TcpTunnel.readFully(ch, l, 1);
	    int n = l.get(0) & 0xff;
	    ByteBuffer rest = ByteBuffer.allocate(n + 2);
	    Socks5TcpTunnel.readFully(ch, rest, n + 2);
	    byte[] host = new byte[n];
	    rest.get(host);
	    int port = Short.toUnsignedInt(rest.getShort());
	    return InetSocketAddress.createUnresolved(new String(host, StandardCharsets.UTF_8), port);
	}
	case 4: {
	    ByteBuffer b = ByteBuffer.allocate(18);
	    Socks5TcpTunnel.readFully(ch, b, 18);
	    byte[] ip = new byte[16];
	    b.get(ip);
	    int port = Short.toUnsignedInt(b.getShort());
	    return new InetSocketAddress(InetAddress.getByAddress(ip), port);
	}
	default:
	    throw new IOException("SOCKS5 bad ATYP in reply: " + atyp);
	}
    }

    /**
     * Append SOCKS UDP header + {@code app} (position→limit) into {@code out}.
     */
    public void wrapInto(InetSocketAddress dest, ByteBuffer app, ByteBuffer out) throws IOException {
	encodeUdpDatagram(dest, app, out);
    }

    /**
     * SOCKS5 UDP framing (RFC 1928) for relaying.
     */
    public static void encodeUdpDatagram(InetSocketAddress dest, ByteBuffer app, ByteBuffer out) throws IOException {
	out.clear();
	out.putShort((short)0).put((byte)0);
	InetAddress a = dest.getAddress();
	if(a != null) {
	    if(a instanceof Inet4Address) {
		out.put((byte)1).put(a.getAddress()).putShort((short)dest.getPort());
	    } else if(a instanceof Inet6Address) {
		out.put((byte)4).put(a.getAddress()).putShort((short)dest.getPort());
	    } else {
		throw new IOException("unsupported InetAddress type");
	    }
	} else {
	    String hn = dest.getHostString();
	    if(hn == null)
		hn = dest.getHostName();
	    byte[] raw = hn.getBytes(StandardCharsets.UTF_8);
	    if(raw.length > 255)
		throw new IOException("SOCKS5 hostname too long");
	    out.put((byte)3).put((byte)raw.length).put(raw).putShort((short)dest.getPort());
	}
	out.put(app.duplicate());
	if(out.position() > out.capacity())
	    throw new IOException("SOCKS5 UDP packet overflow");
    }

    /**
     * Parse SOCKS5 UDP header at {@code buf} position; on success advances {@code buf} to payload start
     * and returns destination address. Otherwise returns {@code null}.
     */
    public static InetSocketAddress parseUdpDestAndPositionPayload(ByteBuffer buf) {
	ByteBuffer d = buf.duplicate();
	if(d.remaining() < 10)
	    return(null);
	if(d.getShort() != 0)
	    return(null);
	if(d.get() != 0)
	    return(null);
	int atyp = d.get() & 0xff;
	InetSocketAddress dest;
	switch(atyp) {
	case 1: {
	    if(d.remaining() < 6)
		return(null);
	    byte[] ip = new byte[4];
	    d.get(ip);
	    int port = Short.toUnsignedInt(d.getShort());
	    try {
		dest = new InetSocketAddress(InetAddress.getByAddress(ip), port);
	    } catch(UnknownHostException e) {
		return(null);
	    }
	    break;
	}
	case 3: {
	    if(d.remaining() < 1)
		return(null);
	    int n = d.get() & 0xff;
	    if(d.remaining() < n + 2)
		return(null);
	    byte[] host = new byte[n];
	    d.get(host);
	    int port = Short.toUnsignedInt(d.getShort());
	    dest = InetSocketAddress.createUnresolved(new String(host, StandardCharsets.UTF_8), port);
	    break;
	}
	case 4: {
	    if(d.remaining() < 18)
		return(null);
	    byte[] ip = new byte[16];
	    d.get(ip);
	    int port = Short.toUnsignedInt(d.getShort());
	    try {
		dest = new InetSocketAddress(InetAddress.getByAddress(ip), port);
	    } catch(UnknownHostException e) {
		return(null);
	    }
	    break;
	}
	default:
	    return(null);
	}
	int pay = d.position();
	buf.position(pay);
	return(dest);
    }

    /**
     * @return byte offset from current position where game UDP payload starts, or {@code -1}.
     */
    public static int gameUdpPayloadStart(ByteBuffer buf) {
	ByteBuffer b = buf.duplicate();
	if(b.remaining() < 10)
	    return -1;
	if(b.getShort() != 0)
	    return -1;
	if(b.get() != 0)
	    return -1;
	int atyp = b.get() & 0xff;
	switch(atyp) {
	case 1:
	    if(b.remaining() < 6)
		return -1;
	    b.position(b.position() + 6);
	    break;
	case 3: {
	    if(b.remaining() < 1)
		return -1;
	    int n = b.get() & 0xff;
	    if(b.remaining() < n + 2)
		return -1;
	    b.position(b.position() + n + 2);
	    break;
	}
	case 4:
	    if(b.remaining() < 18)
		return -1;
	    b.position(b.position() + 18);
	    break;
	default:
	    return -1;
	}
	return b.position();
    }

    @Override
    public void close() throws IOException {
	if(ctl != null) {
	    try {
		ctl.close();
	    } finally {
		ctl = null;
	    }
	}
    }
}
