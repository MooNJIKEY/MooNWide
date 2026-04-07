package haven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for combat data tables (objects, arrays, strings, numbers, booleans, null).
 * No scientific notation; escapes: {@code \\}, {@code \"}, {@code \n}, {@code \r}, {@code \t}.
 */
public final class MoonJsonLite {
    private final String s;
    private int i;

    private MoonJsonLite(String s) {
	this.s = s;
	this.i = 0;
    }

    public static Object parse(String json) throws MoonJsonParseException {
	MoonJsonLite p = new MoonJsonLite(json);
	Object v = p.parseValue();
	p.skipWs();
	if(p.i < p.s.length())
	    throw new MoonJsonParseException("Trailing data at " + p.i);
	return v;
    }

    private void skipWs() {
	while(i < s.length()) {
	    char c = s.charAt(i);
	    if(c == ' ' || c == '\n' || c == '\r' || c == '\t')
		i++;
	    else
		break;
	}
    }

    private Object parseValue() throws MoonJsonParseException {
	skipWs();
	if(i >= s.length())
	    throw new MoonJsonParseException("Unexpected EOF");
	char c = s.charAt(i);
	if(c == '{')
	    return parseObject();
	if(c == '[')
	    return parseArray();
	if(c == '"')
	    return parseString();
	if(c == '-' || (c >= '0' && c <= '9'))
	    return parseNumber();
	if(c == 't' && s.regionMatches(i, "true", 0, 4)) {
	    i += 4;
	    return Boolean.TRUE;
	}
	if(c == 'f' && s.regionMatches(i, "false", 0, 5)) {
	    i += 5;
	    return Boolean.FALSE;
	}
	if(c == 'n' && s.regionMatches(i, "null", 0, 4)) {
	    i += 4;
	    return null;
	}
	throw new MoonJsonParseException("Bad value at " + i);
    }

    private Map<String, Object> parseObject() throws MoonJsonParseException {
	expect('{');
	Map<String, Object> m = new LinkedHashMap<>();
	skipWs();
	if(peek() == '}') {
	    i++;
	    return m;
	}
	while(true) {
	    skipWs();
	    String key = parseString();
	    skipWs();
	    expect(':');
	    Object val = parseValue();
	    m.put(key, val);
	    skipWs();
	    char d = peek();
	    if(d == '}') {
		i++;
		break;
	    }
	    if(d == ',')
		i++;
	    else
		throw new MoonJsonParseException("Expected , or } at " + i);
	}
	return m;
    }

    private List<Object> parseArray() throws MoonJsonParseException {
	expect('[');
	List<Object> list = new ArrayList<>();
	skipWs();
	if(peek() == ']') {
	    i++;
	    return list;
	}
	while(true) {
	    list.add(parseValue());
	    skipWs();
	    char d = peek();
	    if(d == ']') {
		i++;
		break;
	    }
	    if(d == ',')
		i++;
	    else
		throw new MoonJsonParseException("Expected , or ] at " + i);
	}
	return list;
    }

    private String parseString() throws MoonJsonParseException {
	expect('"');
	StringBuilder sb = new StringBuilder();
	while(i < s.length()) {
	    char c = s.charAt(i++);
	    if(c == '"')
		return sb.toString();
	    if(c == '\\') {
		if(i >= s.length())
		    throw new MoonJsonParseException("Bad escape");
		char e = s.charAt(i++);
		switch(e) {
		case '"':
		case '\\':
		case '/':
		    sb.append(e);
		    break;
		case 'n':
		    sb.append('\n');
		    break;
		case 'r':
		    sb.append('\r');
		    break;
		case 't':
		    sb.append('\t');
		    break;
		default:
		    throw new MoonJsonParseException("Unknown escape \\" + e);
		}
	    } else {
		sb.append(c);
	    }
	}
	throw new MoonJsonParseException("Unclosed string");
    }

    private Number parseNumber() throws MoonJsonParseException {
	int start = i;
	if(peek() == '-')
	    i++;
	boolean dot = false;
	while(i < s.length()) {
	    char c = s.charAt(i);
	    if(c >= '0' && c <= '9') {
		i++;
	    } else if(c == '.' && !dot) {
		dot = true;
		i++;
	    } else {
		break;
	    }
	}
	String sub = s.substring(start, i);
	try {
	    if(dot)
		return Double.parseDouble(sub);
	    return Long.parseLong(sub);
	} catch(NumberFormatException e) {
	    throw new MoonJsonParseException("Bad number: " + sub);
	}
    }

    private char peek() {
	return i < s.length() ? s.charAt(i) : '\0';
    }

    private void expect(char c) throws MoonJsonParseException {
	skipWs();
	if(i >= s.length() || s.charAt(i) != c)
	    throw new MoonJsonParseException("Expected '" + c + "' at " + i);
	i++;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) throws MoonJsonParseException {
	if(!(o instanceof Map))
	    throw new MoonJsonParseException("Not an object");
	return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) throws MoonJsonParseException {
	if(!(o instanceof List))
	    throw new MoonJsonParseException("Not an array");
	return (List<Object>) o;
    }

    public static String asString(Object o) {
	return o == null ? "" : String.valueOf(o);
    }

    public static double asDouble(Object o, double def) {
	if(o instanceof Number)
	    return ((Number)o).doubleValue();
	if(o instanceof String) {
	    try {
		return Double.parseDouble(((String)o).trim());
	    } catch(NumberFormatException ignored) {}
	}
	return def;
    }

    public static int asInt(Object o, int def) {
	if(o instanceof Number)
	    return ((Number)o).intValue();
	if(o instanceof String) {
	    try {
		return Integer.parseInt(((String)o).trim());
	    } catch(NumberFormatException ignored) {}
	}
	return def;
    }

    public static final class MoonJsonParseException extends Exception {
	public MoonJsonParseException(String m) {
	    super(m);
	}
    }
}
