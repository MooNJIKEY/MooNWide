package haven.discord;

/** Minimal JSON string extraction for notify payloads (no external JSON lib). */
public final class MoonDiscordJson {
    private MoonDiscordJson() {}

    public static String str(String json, String key) {
	if(json == null || key == null)
	    return("");
	String k = "\"" + key + "\"";
	int i = json.indexOf(k);
	if(i < 0)
	    return("");
	i = json.indexOf(':', i);
	if(i < 0)
	    return("");
	i++;
	while(i < json.length() && Character.isWhitespace(json.charAt(i)))
	    i++;
	if(i >= json.length() || json.charAt(i) != '"')
	    return("");
	i++;
	StringBuilder sb = new StringBuilder();
	while(i < json.length()) {
	    char c = json.charAt(i++);
	    if(c == '"')
		break;
	    if(c == '\\' && i < json.length()) {
		char e = json.charAt(i++);
		if(e == 'n')
		    sb.append('\n');
		else if(e == 't')
		    sb.append('\t');
		else if(e == 'r')
		    sb.append('\r');
		else if(e == 'u' && i + 4 <= json.length()) {
		    try {
			int cp = Integer.parseInt(json.substring(i, i + 4), 16);
			sb.append((char)cp);
			i += 4;
		    } catch(NumberFormatException ex) {
			sb.append('u');
		    }
		} else
		    sb.append(e);
	    } else {
		sb.append(c);
	    }
	}
	return(sb.toString());
    }
}
