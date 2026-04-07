package haven;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

/**
 * SOCKS5 CONNECT for TCP (auth server). UDP for the game uses {@link Socks5UdpTunnel}.
 */
public final class Socks5TcpTunnel {
    private Socks5TcpTunnel() {}

    public static ByteChannel connectThroughTcp(NetEndpoint proxy, String targetHost, int targetPort) throws IOException {
	if(proxy.kind != NetEndpoint.Kind.SOCKS5)
	    throw new IllegalArgumentException("not SOCKS5");
	InetSocketAddress pa = new InetSocketAddress(proxy.host, proxy.port);
	SocketChannel raw = SocketChannel.open();
	raw.socket().setSoTimeout(20000);
	raw.connect(pa);
	/* Raw socket: DPI write-splitting breaks SOCKS5 framing / server timeouts. */
	ByteChannel ch = raw;
	try {
	    socks5Handshake(ch, proxy);
	    byte[] hostBytes = targetHost.getBytes(StandardCharsets.UTF_8);
	    if(hostBytes.length > 255)
		throw new IOException("SOCKS5 host name too long");
	    ByteBuffer req = ByteBuffer.allocate(512);
	    req.put((byte)5).put((byte)1).put((byte)0).put((byte)3).put((byte)hostBytes.length).put(hostBytes)
		.putShort((short)targetPort);
	    req.flip();
	    while(req.hasRemaining())
		ch.write(req);
	    ByteBuffer hdr = ByteBuffer.allocate(512);
	    readFully(ch, hdr, 4);
	    if(hdr.get() != 5 || hdr.get() != 0)
		throw new IOException("SOCKS5 CONNECT failed");
	    hdr.get(); /* RSV */
	    int atyp = hdr.get() & 0xff;
	    skipAddrPort(ch, atyp);
	    if(ch instanceof SocketChannel)
		((SocketChannel) ch).configureBlocking(true);
	    else {
		SocketChannel u = HavenDpiBypass.unwrapSocketChannel(ch);
		if(u != null)
		    u.configureBlocking(true);
	    }
	    return ch;
	} catch(Throwable t) {
	    try {
		ch.close();
	    } catch(IOException ignored) {}
	    if(t instanceof IOException)
		throw(IOException)t;
	    throw new IOException(t);
	}
    }

    /** SOCKS5 method negotiation (+ optional username/password). */
    public static void socks5Handshake(ByteChannel ch, NetEndpoint proxy) throws IOException {
	boolean auth = !proxy.user.isEmpty();
	ByteBuffer greet = ByteBuffer.allocate(3);
	greet.put((byte)5).put((byte)1).put((byte)(auth ? 2 : 0));
	greet.flip();
	while(greet.hasRemaining())
	    ch.write(greet);
	ByteBuffer rep = ByteBuffer.allocate(2);
	readFully(ch, rep, 2);
	if(rep.get() != 5)
	    throw new IOException("SOCKS5 bad version");
	int method = rep.get() & 0xff;
	if(method == 0xff)
	    throw new IOException("SOCKS5 no acceptable method");
	if(method == 0)
	    return;
	if(method == 2) {
	    byte[] u = proxy.user.getBytes(StandardCharsets.UTF_8);
	    byte[] p = proxy.pass.getBytes(StandardCharsets.UTF_8);
	    if(u.length > 255 || p.length > 255)
		throw new IOException("SOCKS5 credentials too long");
	    ByteBuffer a = ByteBuffer.allocate(3 + u.length + p.length);
	    a.put((byte)1).put((byte)u.length).put(u).put((byte)p.length).put(p);
	    a.flip();
	    while(a.hasRemaining())
		ch.write(a);
	    ByteBuffer ar = ByteBuffer.allocate(2);
	    readFully(ch, ar, 2);
	    if(ar.get() != 1 || ar.get() != 0)
		throw new IOException("SOCKS5 username/password rejected");
	    return;
	}
	throw new IOException("SOCKS5 unsupported method: " + method);
    }

    private static void skipAddrPort(ByteChannel ch, int atyp) throws IOException {
	switch(atyp) {
	case 1:
	    readFully(ch, ByteBuffer.allocate(4 + 2), 6);
	    break;
	case 3: {
	    ByteBuffer l = ByteBuffer.allocate(1);
	    readFully(ch, l, 1);
	    int n = l.get() & 0xff;
	    readFully(ch, ByteBuffer.allocate(n + 2), n + 2);
	    break;
	}
	case 4:
	    readFully(ch, ByteBuffer.allocate(16 + 2), 18);
	    break;
	default:
	    throw new IOException("SOCKS5 bad ATYP " + atyp);
	}
    }

    public static void readFully(ByteChannel ch, ByteBuffer dst, int n) throws IOException {
	dst.clear();
	dst.limit(n);
	while(dst.hasRemaining()) {
	    int r = ch.read(dst);
	    if(r < 0)
		throw new IOException("SOCKS5 unexpected EOF");
	}
	dst.flip();
    }
}
