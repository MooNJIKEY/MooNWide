package haven;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bundled wiki snapshot for recipes that may not yet be discovered in the current character menu.
 * Generated from the Haven & Hearth Fandom API and loaded locally at runtime.
 */
public final class MoonCraftWiki {
    private MoonCraftWiki() {}

    public static final class Entry {
	public final String title;
	public final String skill;
	public final String materials;
	public final String reference;
	public final String how;
	public final String description;

	public Entry(String title, String skill, String materials, String reference, String how, String description) {
	    this.title = title;
	    this.skill = skill;
	    this.materials = materials;
	    this.reference = reference;
	    this.how = how;
	    this.description = description;
	}
    }

    private static volatile List<Entry> entries = Collections.emptyList();
    private static volatile Map<String, Entry> byName = Collections.emptyMap();

    static {
	load();
    }

    public static List<Entry> all() {
	return(entries);
    }

    public static Entry find(String name) {
	if(name == null)
	    return(null);
	return(byName.get(norm(name)));
    }

    public static synchronized void load() {
	ArrayList<Entry> list = new ArrayList<>();
	LinkedHashMap<String, Entry> map = new LinkedHashMap<>();
	try(InputStream in = MoonCraftWiki.class.getResourceAsStream("res/moon-craft-wiki.json")) {
	    if(in == null) {
		entries = Collections.emptyList();
		byName = Collections.emptyMap();
		return;
	    }
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    byte[] buf = new byte[8192];
	    int n;
	    while((n = in.read(buf)) >= 0)
		bos.write(buf, 0, n);
	    Object root = MoonJsonLite.parse(new String(bos.toByteArray(), StandardCharsets.UTF_8));
	    Map<String, Object> obj = MoonJsonLite.asMap(root);
	    Object arr = obj.get("recipes");
	    if(arr instanceof List) {
		for(Object row : (List<?>)arr) {
		    if(!(row instanceof Map))
			continue;
		    Map<String, Object> m = MoonJsonLite.asMap(row);
		    Entry e = new Entry(
			MoonJsonLite.asString(m.get("title")),
			MoonJsonLite.asString(m.get("skill")),
			MoonJsonLite.asString(m.get("materials")),
			MoonJsonLite.asString(m.get("reference")),
			MoonJsonLite.asString(m.get("how")),
			MoonJsonLite.asString(m.get("description")));
		    if(e.title.isEmpty())
			continue;
		    list.add(e);
		    map.putIfAbsent(norm(e.title), e);
		}
	    }
	} catch(Exception ignored) {
	    list.clear();
	    map.clear();
	}
	entries = Collections.unmodifiableList(list);
	byName = Collections.unmodifiableMap(map);
    }

    private static String norm(String s) {
	return((s == null) ? "" : s.trim().toLowerCase(Locale.ROOT));
    }
}
