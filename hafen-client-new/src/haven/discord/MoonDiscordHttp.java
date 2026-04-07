package haven.discord;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class MoonDiscordHttp implements HttpHandler {
    private final MoonDiscordService svc;

    MoonDiscordHttp(MoonDiscordService svc) {
	this.svc = svc;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
	try {
	    if(!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
		ex.sendResponseHeaders(405, -1);
		return;
	    }
	    String auth = ex.getRequestHeaders().getFirst("Authorization");
	    String secret = svc.getSecret();
	    if(secret == null || secret.isEmpty() || auth == null || !auth.startsWith("Bearer ")) {
		ex.sendResponseHeaders(401, -1);
		return;
	    }
	    String tok = auth.substring(7);
	    if(!constantTimeEq(tok, secret)) {
		ex.sendResponseHeaders(401, -1);
		return;
	    }
	    byte[] raw = readAll(ex.getRequestBody());
	    String json = new String(raw, StandardCharsets.UTF_8);
	    svc.deliverNotify(json);
	    ex.sendResponseHeaders(204, -1);
	} catch(Exception e) {
	    ex.sendResponseHeaders(500, -1);
	} finally {
	    ex.close();
	}
    }

    private static boolean constantTimeEq(String a, String b) {
	if(a == null || b == null || a.length() != b.length())
	    return(false);
	int r = 0;
	for(int i = 0; i < a.length(); i++)
	    r |= a.charAt(i) ^ b.charAt(i);
	return(r == 0);
    }

    private static byte[] readAll(InputStream in) throws IOException {
	byte[] buf = new byte[65536];
	int n = in.read(buf);
	if(n < 0)
	    return(new byte[0]);
	if(n < buf.length && in.read() < 0)
	    return(Arrays.copyOf(buf, n));
	java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
	bos.write(buf, 0, n);
	byte[] chunk = new byte[8192];
	int k;
	while((k = in.read(chunk)) >= 0)
	    bos.write(chunk, 0, k);
	return(bos.toByteArray());
    }
}
