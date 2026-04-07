package haven;

/**
 * Walks Haven {@link RichText} source and runs a translator only on plain-text runs, preserving
 * {@code $tag[...]{...}} structure (including nested tags).
 */
final class RichTextAutoTranslate {
    private RichTextAutoTranslate() {}

    static boolean namechar(char c) {
	return((c == ':') || (c == '_') || (c == '$') || (c == '.') || (c == '-')
	    || ((c >= '0') && (c <= '9')) || ((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z')));
    }

    static String translateRich(String input, java.util.function.Function<String, String> plainTr) {
	if(input == null || input.isEmpty())
	    return(input);
	StringBuilder out = new StringBuilder(input.length() + 16);
	int i = 0;
	final int n = input.length();
	while(i < n) {
	    int tag = nextTagStart(input, i);
	    if(tag < 0) {
		out.append(plainTr.apply(input.substring(i)));
		break;
	    }
	    if(tag > i)
		out.append(plainTr.apply(input.substring(i, tag)));
	    i = tag;
	    if(i + 1 < n) {
		char c1 = input.charAt(i + 1);
		if(c1 == '$' || c1 == '{' || c1 == '}') {
		    out.append('$').append(c1);
		    i += 2;
		    continue;
		}
	    }
	    TagSpan sp = parseTagSpan(input, i);
	    if(sp == null) {
		out.append('$');
		i++;
		continue;
	    }
	    out.append(sp.prefix);
	    if(sp.inner != null)
		out.append(translateRich(sp.inner, plainTr));
	    if(sp.closeBrace)
		out.append('}');
	    i = sp.end;
	}
	return(out.toString());
    }

    private static int nextTagStart(String s, int from) {
	for(int i = from; i < s.length(); i++) {
	    if(s.charAt(i) != '$')
		continue;
	    if(i + 1 < s.length()) {
		char n = s.charAt(i + 1);
		if(n == '$' || n == '{' || n == '}')
		    continue;
	    }
	    return(i);
	}
	return(-1);
    }

    private static final class TagSpan {
	final String prefix;
	final String inner;
	final boolean closeBrace;
	final int end;

	TagSpan(String prefix, String inner, boolean closeBrace, int end) {
	    this.prefix = prefix;
	    this.inner = inner;
	    this.closeBrace = closeBrace;
	    this.end = end;
	}
    }

    private static TagSpan parseTagSpan(String s, int dollarIdx) {
	final int n = s.length();
	int pos = dollarIdx + 1;
	if(pos >= n)
	    return(null);
	StringBuilder nb = new StringBuilder();
	while(pos < n && namechar(s.charAt(pos)))
	    nb.append(s.charAt(pos++));
	if(nb.length() == 0)
	    return(null);
	String name = nb.toString();
	int afterName = pos;
	if(pos < n && s.charAt(pos) == '[') {
	    pos++;
	    while(pos < n && s.charAt(pos) != ']') {
		if(s.charAt(pos) == '\\' && pos + 1 < n)
		    pos++;
		pos++;
	    }
	    if(pos >= n || s.charAt(pos) != ']')
		return(null);
	    pos++;
	}
	if("img".equals(name)) {
	    return(new TagSpan(s.substring(dollarIdx, pos), null, false, pos));
	}
	if(pos >= n || s.charAt(pos) != '{')
	    return(null);
	String inner;
	int close;
	try {
	    int[] ic = extractBalancedInner(s, pos + 1);
	    inner = s.substring(ic[0], ic[1]);
	    close = ic[2];
	} catch(RuntimeException ex) {
	    return(null);
	}
	String prefix = s.substring(dollarIdx, pos + 1);
	return(new TagSpan(prefix, inner, true, close + 1));
    }

    /**
     * @return int[]{innerStart, innerEndExclusive, indexOfClosingBrace}
     */
    private static int[] extractBalancedInner(String s, int start) {
	int depth = 0;
	int k = start;
	final int n = s.length();
	while(k < n) {
	    char c = s.charAt(k);
	    if(c == '$' && k + 1 < n) {
		char n1 = s.charAt(k + 1);
		if(n1 == '$' || n1 == '{' || n1 == '}') {
		    k += 2;
		    continue;
		}
		TagSpan innerTag = parseTagSpan(s, k);
		if(innerTag == null) {
		    k++;
		    continue;
		}
		k = innerTag.end;
		continue;
	    }
	    if(c == '{') {
		depth++;
		k++;
		continue;
	    }
	    if(c == '}') {
		if(depth == 0)
		    return(new int[] {start, k, k});
		depth--;
		k++;
		continue;
	    }
	    k++;
	}
	throw(new RichText.FormatException("unclosed '{' in rich text segment"));
    }
}
