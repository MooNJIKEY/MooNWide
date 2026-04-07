package haven;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ForkJoinPool;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Minimal LibreTranslate HTTP client (POST JSON {@code /translate}).
 * Failures are surfaced as {@link IOException}; callers should fall back to the source string.
 */
public final class LibreTranslateService {
    private LibreTranslateService() {}

    /** Uses {@link ForkJoinPool#commonPool()} if no executor is supplied. */
    public static CompletableFuture<String> translateAsync(String text) {
	return(translateAsync(text, ForkJoinPool.commonPool()));
    }

    public static CompletableFuture<String> translateAsync(String text, Executor executor) {
	if(executor == null)
	    executor = ForkJoinPool.commonPool();
	String t = text;
	return CompletableFuture.supplyAsync(() -> {
	    try {
		return translateBlocking(t);
	    } catch(Exception e) {
		throw new RuntimeException(e);
	    }
	}, executor);
    }

    /**
     * Reads prefs each call so options apply without restart.
     */
    public static String translateBlocking(String text) throws IOException {
	if(text == null || text.isEmpty())
	    return(text);
	String urlStr = Utils.getpref("moon-translate-api-url", "https://libretranslate.com/translate").trim();
	if(urlStr.isEmpty())
	    throw new IOException("empty moon-translate-api-url");
	URL url;
	try {
	    url = URI.create(urlStr).toURL();
	} catch(IllegalArgumentException | MalformedURLException e) {
	    throw new IOException("bad moon-translate-api-url: " + urlStr, e);
	}
	String body = buildRequestBody(text);
	byte[] payload = body.getBytes(StandardCharsets.UTF_8);
	URLConnection conn = Http.open(url);
	if(!(conn instanceof java.net.HttpURLConnection))
	    throw new IOException("unexpected connection type");
	java.net.HttpURLConnection hc = (java.net.HttpURLConnection)conn;
	hc.setRequestMethod("POST");
	hc.setDoOutput(true);
	hc.setConnectTimeout(Math.max(1000, Utils.getprefi("moon-translate-connect-ms", 8000)));
	hc.setReadTimeout(Math.max(1000, Utils.getprefi("moon-translate-read-ms", 25000)));
	hc.setRequestProperty("Content-Type", "application/json");
	hc.setRequestProperty("Accept", "application/json");
	hc.setFixedLengthStreamingMode(payload.length);
	try(OutputStream os = hc.getOutputStream()) {
	    os.write(payload);
	}
	int code = hc.getResponseCode();
	InputStream in = (code >= 200 && code < 300) ? hc.getInputStream() : hc.getErrorStream();
	if(in == null)
	    throw new IOException("LibreTranslate HTTP " + code);
	String resp = readAll(in);
	if(code < 200 || code >= 300)
	    throw new IOException("LibreTranslate HTTP " + code + ": " + truncate(resp, 200));
	return parseTranslatedText(resp);
    }

    private static String truncate(String s, int max) {
	if(s == null)
	    return("");
	if(s.length() <= max)
	    return(s);
	return(s.substring(0, max) + "…");
    }

    static String buildRequestBody(String text) {
	StringBuilder sb = new StringBuilder(32 + text.length() * 2);
	sb.append('{');
	sb.append("\"q\":").append(jsonString(text));
	sb.append(",\"source\":\"en\",\"target\":\"ru\",\"format\":\"text\"");
	String key = Utils.getpref("moon-translate-api-key", "").trim();
	if(!key.isEmpty()) {
	    sb.append(",\"api_key\":").append(jsonString(key));
	}
	sb.append('}');
	return(sb.toString());
    }

    static String jsonString(String s) {
	StringBuilder sb = new StringBuilder(s.length() + 8);
	sb.append('"');
	for(int i = 0, n = s.length(); i < n; i++) {
	    char c = s.charAt(i);
	    switch(c) {
	    case '\\':
		sb.append("\\\\");
		break;
	    case '"':
		sb.append("\\\"");
		break;
	    case '\n':
		sb.append("\\n");
		break;
	    case '\r':
		sb.append("\\r");
		break;
	    case '\t':
		sb.append("\\t");
		break;
	    default:
		if(c < 0x20)
		    sb.append(String.format("\\u%04x", (int)c));
		else
		    sb.append(c);
	    }
	}
	sb.append('"');
	return(sb.toString());
    }

    private static String readAll(InputStream in) throws IOException {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	byte[] buf = new byte[8192];
	int r;
	while((r = in.read(buf)) >= 0)
	    bos.write(buf, 0, r);
	return(bos.toString(StandardCharsets.UTF_8));
    }

    static String parseTranslatedText(String json) throws IOException {
	try {
	    Object root = MoonJsonLite.parse(json);
	    if(!(root instanceof Map))
		throw new IOException("not a JSON object");
	    Object tr = ((Map<?, ?>)root).get("translatedText");
	    if(tr == null)
		throw new IOException("missing translatedText");
	    return(String.valueOf(tr));
	} catch(MoonJsonLite.MoonJsonParseException e) {
	    throw new IOException("bad JSON: " + e.getMessage(), e);
	}
    }
}
