package haven;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * In-game messages + optional Discord webhook for tree-bot phase completion.
 * Webhook URL format: {@code https://discord.com/api/webhooks/...} (same as Discord channel integration).
 */
public final class MoonNotify {
    private MoonNotify() {}

    public static void treeBotPhase(GameUI gui, String l10nKey) {
	String msg = LocalizationManager.tr(l10nKey);
	if(gui != null && gui.ui != null)
	    gui.ui.msg(msg, java.awt.Color.WHITE, null);
	if(MoonConfig.treeBotDiscordNotify)
	    discordAsync(msg);
    }

    static void discordAsync(String plainText) {
	String wh0 = Utils.getpref("moon-treebot-webhook", "").trim();
	if(wh0.isEmpty())
	    wh0 = Utils.getpref("moon-discord-url", "").trim();
	if(wh0.isEmpty())
	    return;
	final String wh = wh0;
	final String body = "{\"content\":\"" + jsonEscape(plainText) + "\"}";
	new HackThread(() -> postWebhook(wh, body), "moon-discord-webhook").start();
    }

    private static String jsonEscape(String s) {
	if(s == null)
	    return("");
	StringBuilder b = new StringBuilder(s.length() + 8);
	for(int i = 0; i < s.length(); i++) {
	    char c = s.charAt(i);
	    switch(c) {
	    case '\\': b.append("\\\\"); break;
	    case '"': b.append("\\\""); break;
	    case '\n': b.append("\\n"); break;
	    case '\r': b.append("\\r"); break;
	    case '\t': b.append("\\t"); break;
	    default: b.append(c);
	    }
	}
	return(b.toString());
    }

    private static void postWebhook(String urlStr, String json) {
	HttpURLConnection c = null;
	try {
	    URL url = new URL(urlStr);
	    c = (HttpURLConnection)url.openConnection();
	    c.setConnectTimeout(8000);
	    c.setReadTimeout(8000);
	    c.setRequestMethod("POST");
	    c.setDoOutput(true);
	    c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
	    byte[] data = json.getBytes(StandardCharsets.UTF_8);
	    c.setFixedLengthStreamingMode(data.length);
	    try(OutputStream os = c.getOutputStream()) {
		os.write(data);
	    }
	    c.getInputStream().close();
	} catch(Exception ignored) {
	} finally {
	    if(c != null)
		c.disconnect();
	}
    }
}
