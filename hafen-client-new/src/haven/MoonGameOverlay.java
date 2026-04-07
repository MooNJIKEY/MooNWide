package haven;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Extra EN→RU replacements for dynamic game text (item tooltips, paginae, ad-hoc lines from server).
 * Loaded from {@code haven/res/moon/moon-l10n-game.json}; extend the file to cover more strings.
 * Replace order: longest English phrase first to avoid partial clobbering.
 */
public final class MoonGameOverlay {
    private MoonGameOverlay() {}

    private static final List<String[]> phrases = new ArrayList<>();

    static {
	load();
    }

    public static void reload() {
	load();
    }

    private static synchronized void load() {
	phrases.clear();
	try(InputStream in = MoonGameOverlay.class.getResourceAsStream("res/moon/moon-l10n-game.json")) {
	    if(in == null)
		return;
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    byte[] buf = new byte[8192];
	    int n;
	    while((n = in.read(buf)) >= 0)
		bos.write(buf, 0, n);
	    String json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
	    Object root = MoonJsonLite.parse(json);
	    java.util.Map<String, Object> m = MoonJsonLite.asMap(root);
	    Object parr = m.get("pairs");
	    if(!(parr instanceof List))
		return;
	    for(Object row : (List<?>)parr) {
		if(!(row instanceof List))
		    continue;
		List<?> rowl = (List<?>)row;
		if(rowl.size() < 2)
		    continue;
		String en = MoonJsonLite.asString(rowl.get(0));
		String ru = MoonJsonLite.asString(rowl.get(1));
		if(en.isEmpty())
		    continue;
		phrases.add(new String[] {en, ru.isEmpty() ? en : ru});
	    }
	} catch(Exception ignored) {
	    phrases.clear();
	}
	Collections.sort(phrases, new Comparator<String[]>() {
	    public int compare(String[] a, String[] b) {
		return(Integer.compare(b[0].length(), a[0].length()));
	    }
	});
    }

    /**
     * Apply overlay when MooNWide language is Russian; otherwise return {@code s} unchanged.
     * Safe for RichText only if phrases do not inject broken {@code $} sequences.
     */
    public static String apply(String s) {
	if(s == null || s.isEmpty())
	    return(s);
	if(!MoonL10n.LANG_RU.equals(MoonL10n.lang()))
	    return(s);
	if(phrases.isEmpty())
	    return(s);
	String t = s;
	for(String[] pr : phrases) {
	    if(pr[0].isEmpty())
		continue;
	    if(t.indexOf(pr[0]) >= 0)
		t = t.replace(pr[0], pr[1]);
	}
	return(t);
    }
}
