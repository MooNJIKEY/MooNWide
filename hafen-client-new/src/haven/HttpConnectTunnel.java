package haven;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

/**
 * HTTP CONNECT through a plain-TCP proxy (RFC 7231). Used for auth server TCP only.
 */
public final class HttpConnectTunnel {
    private HttpConnectTunnel() {}

    public static ByteChannel connectThrough(NetEndpoint proxy, String targetHost, int targetPort) throws IOException {
	if(proxy.kind != NetEndpoint.Kind.HTTP_CONNECT)
	    throw new IllegalArgumentException("not HTTP_CONNECT");
	InetSocketAddress pa = new InetSocketAddress(proxy.host, proxy.port);
	SocketChannel raw = SocketChannel.open();
	try {
	    raw.socket().setSoTimeout(20000);
	    raw.connect(pa);
	    ByteChannel ch = raw;
	    if(!proxy.user.isEmpty()) {
		String tok = java.util.Base64.getEncoder().encodeToString((proxy.user + ":" + proxy.pass).getBytes(StandardCharsets.UTF_8));
		writeUtf8(ch, String.format("CONNECT %s:%d HTTP/1.1\r\nHost: %s:%d\r\nProxy-Authorization: Basic %s\r\n\r\n",
		    targetHost, targetPort, targetHost, targetPort, tok));
	    } else {
		writeUtf8(ch, String.format("CONNECT %s:%d HTTP/1.1\r\nHost: %s:%d\r\n\r\n",
		    targetHost, targetPort, targetHost, targetPort));
	    }
	    readHttpOk(ch);
	    SocketChannel u = HavenDpiBypass.unwrapSocketChannel(ch);
	    if(u != null)
		u.configureBlocking(true);
	    return ch;
	} catch(Throwable t) {
	    try {
		raw.close();
	    } catch(IOException ignored) {}
	    if(t instanceof IOException)
		throw(IOException)t;
	    throw new IOException(t);
	}
    }

    private static void writeUtf8(ByteChannel ch, String s) throws IOException {
	ByteBuffer b = ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
	while(b.hasRemaining())
	    ch.write(b);
    }

    private static void readHttpOk(ByteChannel ch) throws IOException {
	ByteBuffer buf = ByteBuffer.allocate(4096);
	int total = 0;
	StringBuilder head = new StringBuilder();
	while(total < 65536) {
	    int n = ch.read(buf);
	    if(n < 0)
		throw new IOException("HTTP proxy closed before response");
	    buf.flip();
	    head.append(StandardCharsets.UTF_8.decode(buf));
	    buf.clear();
	    total += n;
	    String h = head.toString();
	    int end = h.indexOf("\r\n\r\n");
	    if(end >= 0) {
		String statusLine = h.substring(0, h.indexOf('\r'));
		if(!statusLine.contains("200"))
		    throw new IOException("HTTP CONNECT failed: " + statusLine.trim());
		return;
	    }
	}
	throw new IOException("HTTP proxy response too large or incomplete");
    }
}
