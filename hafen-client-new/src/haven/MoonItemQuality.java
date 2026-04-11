package haven;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared inventory-style quality readout (numeric Q from {@link ItemInfo}) and overlay drawing
 * for {@link WItem}, craft {@link Makewindow.Spec} slots, and similar.
 */
public final class MoonItemQuality {

    /** Per-concrete-class string fields for {@link #reflectStringQualityFields} (tooltip factories repeat across items). */
    private static final Map<Class<?>, Field[]> ITEMINFO_STRING_FIELDS = new ConcurrentHashMap<>();

    /** Fallback when structured quality infos use private fields or nonstandard class names. */
    private static final Pattern QUALITY_LINE = Pattern.compile(
	"(quality|качество|кач\\.)\\b[\\s:：\\-–—]{0,6}(-?\\d+(?:\\.\\d+)?)",
	Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Same as {@link #QUALITY_LINE} but without {@code \\b} — some JRE/locale combos mishandle Cyrillic word boundaries. */
    private static final Pattern QUALITY_LINE_LOOSE = Pattern.compile(
	"(?i)(quality|качество|кач\\.)(?:[^\\d\\n]{0,12})(-?\\d+(?:\\.\\d+)?)");
    /** Short "Q: 12" style lines in tips (EN and some custom servers). */
    private static final Pattern QUALITY_Q_SHORT = Pattern.compile(
	"\\bQ\\b[\\s:：\\-–—]{0,4}(-?\\d+(?:\\.\\d+)?)",
	Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "Q=12" or "Q = 12" (some tooltips omit a colon). */
    private static final Pattern QUALITY_Q_EQ = Pattern.compile(
	"\\bQ\\s*=\\s*(-?\\d+(?:\\.\\d+)?)",
	Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private MoonItemQuality() {}

    /**
     * Reads quality for a concrete inventory item. If the item is a stack/container widget with
     * child {@link GItem}s in {@link GItem#contents}, their qualities are averaged directly so the
     * overlay does not depend on tooltip propagation quirks.
     */
    public static double readQ(GItem item) {
	if(item == null)
	    return 0;
	double stacked = readQFromContents(item);
	if(stacked > 0)
	    return stacked;
	return readQ(item.info());
    }

    /**
     * Reads a positive quality from item infos (QBuff, {@code *quality*} types, nested
     * {@link ItemInfo.Contents}, declared fields {@code q}/{@code quality}, then tooltip text).
     */
    public static double readQ(List<ItemInfo> infos) {
	if(infos == null)
	    return 0;
	try {
	    double r = readQRecursive(infos);
	    if(r > 0)
		return r;
	    r = qualityFromTips(infos);
	    if(r > 0)
		return r;
	    r = qualityFromJoinedText(infos);
	    if(r > 0)
		return r;
	} catch(Loading ignored) {}
	return 0;
    }

    private static final class QualityAgg {
	double sum;
	int count;

	void add(double q) {
	    if(q > 0) {
		sum += q;
		count++;
	    }
	}

	double avg() {
	    return(count > 0 ? (sum / count) : 0);
	}
    }

    /** Last resort: one blob of all string fields so keywords and digits can span lines/entries. */
    private static double qualityFromJoinedText(List<ItemInfo> infos) {
	StringBuilder sb = new StringBuilder(256);
	appendAllItemInfoStrings(infos, sb);
	return parseQualityFromText(sb.toString());
    }

    private static void appendAllItemInfoStrings(List<ItemInfo> infos, StringBuilder sb) {
	for(ItemInfo ii : infos) {
	    if(ii == null)
		continue;
	    if(ii instanceof ItemInfo.Contents) {
		appendAllItemInfoStrings(((ItemInfo.Contents)ii).sub, sb);
	    } else {
		sb.append('\n');
		appendItemInfoStringsRecursive(ii, sb);
	    }
	}
    }

    private static void appendItemInfoStringsRecursive(ItemInfo inf, StringBuilder sb) {
	for(Class<?> cl = inf.getClass(); cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.getType() != String.class)
		    continue;
		try {
		    f.setAccessible(true);
		    String s = (String)f.get(inf);
		    if(s != null && !s.isEmpty()) {
			sb.append(s);
			sb.append('\n');
		    }
		} catch(Exception ignored) {
		}
	    }
	}
    }

    private static double readQRecursive(List<ItemInfo> infos) {
	if(infos == null)
	    return 0;
	/* Any ItemInfo with a numeric q/qual/quality field (covers QBuff and obfuscated tt classes). */
	for(ItemInfo inf : infos) {
	    if(inf == null || inf instanceof ItemInfo.Contents)
		continue;
	    double v = readQFromResourceQuality(inf);
	    if(v > 0)
		return v;
	    v = reflectNumericQuality(inf);
	    if(v > 0)
		return v;
	    v = reflectStringQualityFields(inf);
	    if(v > 0)
		return v;
	}
	QualityAgg nested = new QualityAgg();
	for(ItemInfo inf : infos) {
	    if(inf instanceof ItemInfo.Contents) {
		collectContentQuality(((ItemInfo.Contents)inf).sub, nested);
	    }
	}
	if(nested.count > 0)
	    return nested.avg();
	return 0;
    }

    private static void collectContentQuality(List<ItemInfo> infos, QualityAgg out) {
	if(infos == null || out == null)
	    return;
	double own = 0;
	for(ItemInfo inf : infos) {
	    if(inf == null || inf instanceof ItemInfo.Contents)
		continue;
	    own = readQFromResourceQuality(inf);
	    if(own <= 0)
		own = reflectNumericQuality(inf);
	    if(own <= 0)
		own = reflectStringQualityFields(inf);
	    if(own > 0)
		break;
	}
	if(own > 0)
	    out.add(own);
	for(ItemInfo inf : infos) {
	    if(inf instanceof ItemInfo.Contents)
		collectContentQuality(((ItemInfo.Contents)inf).sub, out);
	}
    }

    private static double readQFromContents(GItem item) {
	Widget contents = item.contents;
	if(contents == null)
	    return 0;
	QualityAgg nested = new QualityAgg();
	for(Widget ch : contents.children()) {
	    if(!(ch instanceof GItem))
		continue;
	    double q = readQ((GItem)ch);
	    if(q > 0)
		nested.add(q);
	}
	return nested.avg();
    }

    /**
     * Direct read of {@code public double q} from stock Haven {@code ui/tt/q/qbuff} and {@code ui/tt/q/quality}
     * (reflection by name can fail on some JRE/module setups; {@link Class#getField} works for public fields).
     */
    private static double readQFromResourceQuality(ItemInfo inf) {
	try {
	    java.lang.reflect.Field f = inf.getClass().getField("q");
	    if(f.getType() == double.class)
		return f.getDouble(inf);
	    if(f.getType() == float.class)
		return f.getFloat(inf);
	    Object o = f.get(inf);
	    if(o instanceof Number)
		return ((Number)o).doubleValue();
	} catch(NoSuchFieldException | IllegalAccessException ignored) {
	}
	return 0;
    }

    /** Obfuscated {@code tt} factories often hold the tooltip line in a {@code String} field not named {@code q}. */
    private static double reflectStringQualityFields(ItemInfo inf) {
	Field[] fields = ITEMINFO_STRING_FIELDS.computeIfAbsent(inf.getClass(), MoonItemQuality::collectItemInfoStringFields);
	for(Field f : fields) {
	    try {
		String s = (String)f.get(inf);
		double v = parseQualityFromText(s);
		if(v > 0)
		    return v;
	    } catch(Exception ignored) {
	    }
	}
	return 0;
    }

    private static Field[] collectItemInfoStringFields(Class<?> leaf) {
	ArrayList<Field> acc = new ArrayList<>();
	for(Class<?> cl = leaf; cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.getType() != String.class)
		    continue;
		try {
		    f.setAccessible(true);
		} catch(Exception ignored) {
		    continue;
		}
		acc.add(f);
	    }
	}
	return acc.toArray(new Field[0]);
    }

    /**
     * Reads {@code q}/{@code qual}/{@code quality} on this instance only (no nested {@link ItemInfo}).
     */
    private static double reflectNumericQualityDirect(ItemInfo inf) {
	for(Class<?> cl = inf.getClass(); cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(String nm : new String[] {"q", "qual", "quality"}) {
		try {
		    Field f = cl.getDeclaredField(nm);
		    if(Modifier.isStatic(f.getModifiers()))
			continue;
		    double v = readNumericField(f, inf);
		    if(v > 0)
			return v;
		} catch(NoSuchFieldException ignored) {
		} catch(Exception ignored) {
		}
	    }
	}
	try {
	    for(Class<?> cl = inf.getClass(); cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
		for(Field f : cl.getDeclaredFields()) {
		    if(Modifier.isStatic(f.getModifiers()))
			continue;
		    String fn = f.getName().toLowerCase(Locale.ROOT);
		    if(!fn.equals("q") && !fn.equals("qual") && !fn.equals("quality"))
			continue;
		    double v = readNumericField(f, inf);
		    if(v > 0)
			return v;
		}
	    }
	} catch(Exception ignored) {
	}
	return 0;
    }

    /**
     * Like {@link #reflectNumericQualityDirect}, then unwrap one level of nested {@link ItemInfo}
     * fields (e.g. {@code haven.res.ui.tt.q.qbuff.QBuff.Short} holds a {@code QBuff} in field {@code q}).
     */
    private static double reflectNumericQuality(ItemInfo inf) {
	double v = reflectNumericQualityDirect(inf);
	if(v > 0)
	    return v;
	for(Class<?> cl = inf.getClass(); cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()))
		    continue;
		if(!ItemInfo.class.isAssignableFrom(f.getType()))
		    continue;
		try {
		    f.setAccessible(true);
		    Object sub = f.get(inf);
		    if(sub instanceof ItemInfo) {
			v = reflectNumericQualityDirect((ItemInfo)sub);
			if(v > 0)
			    return v;
		    }
		} catch(Exception ignored) {
		}
	    }
	}
	return 0;
    }

    private static double readNumericField(Field f, Object o) throws IllegalAccessException {
	f.setAccessible(true);
	Class<?> t = f.getType();
	if(t == double.class)
	    return f.getDouble(o);
	if(t == float.class)
	    return f.getFloat(o);
	if(t == int.class)
	    return f.getInt(o);
	if(t == long.class)
	    return f.getLong(o);
	Object boxed = f.get(o);
	if(boxed instanceof Number)
	    return ((Number)boxed).doubleValue();
	return 0;
    }

    private static void collectTips(List<ItemInfo> list, List<ItemInfo.Tip> out) {
	for(ItemInfo ii : list) {
	    if(ii instanceof ItemInfo.Contents) {
		collectTips(((ItemInfo.Contents)ii).sub, out);
	    } else if(ii instanceof ItemInfo.Tip) {
		out.add((ItemInfo.Tip)ii);
	    }
	}
    }

    private static double parseQualityFromText(String s) {
	if(s == null)
	    return 0;
	s = stripRichMarkupForQuality(s);
	double v = parseQualityStrict(s);
	if(v > 0)
	    return v;
	return parseQualityLoose(s);
    }

    /** Unwrap one level of Haven RichText {@code $tag[…]{inner}} so digits inside {@code {…}} match. */
    private static String stripRichMarkupForQuality(String s) {
	if(s == null || s.indexOf('$') < 0)
	    return s;
	String t = s;
	for(int i = 0; i < 10; i++) {
	    String n = t.replaceAll("\\$[a-zA-Z][^\\{]*\\{([^}]*)\\}", "$1");
	    if(n.equals(t))
		break;
	    t = n;
	}
	return t;
    }

    private static double parseQualityStrict(String s) {
	Matcher m = QUALITY_LINE.matcher(s);
	if(m.find()) {
	    try {
		return Double.parseDouble(m.group(2));
	    } catch(NumberFormatException ignored) {
	    }
	}
	m = QUALITY_LINE_LOOSE.matcher(s);
	if(m.find()) {
	    try {
		return Double.parseDouble(m.group(2));
	    } catch(NumberFormatException ignored) {
	    }
	}
	m = QUALITY_Q_SHORT.matcher(s);
	if(m.find()) {
	    try {
		return Double.parseDouble(m.group(1));
	    } catch(NumberFormatException ignored) {
	    }
	}
	m = QUALITY_Q_EQ.matcher(s);
	if(m.find()) {
	    try {
		return Double.parseDouble(m.group(1));
	    } catch(NumberFormatException ignored) {
	    }
	}
	return 0;
    }

    /**
     * RichText and line breaks: the number may be on the next line or inside {@code $col[]{…}}.
     * Take the first plausible positive number in the window after the keyword (not only same line).
     */
    private static double parseQualityLoose(String s) {
	Matcher kw = Pattern.compile("(quality|качество|кач\\.)\\b",
	    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(s);
	while(kw.find()) {
	    int from = kw.end();
	    int to = Math.min(s.length(), from + 500);
	    String tail = s.substring(from, to);
	    Matcher num = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(tail);
	    while(num.find()) {
		try {
		    double q = Double.parseDouble(num.group(1));
		    if(q > 0 && q <= 1_000_000)
			return q;
		} catch(NumberFormatException ignored) {
		}
	    }
	}
	kw = Pattern.compile("(quality|качество|кач\\.)",
	    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(s);
	while(kw.find()) {
	    int from = kw.end();
	    int to = Math.min(s.length(), from + 500);
	    String tail = s.substring(from, to);
	    Matcher num = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(tail);
	    while(num.find()) {
		try {
		    double q = Double.parseDouble(num.group(1));
		    if(q > 0 && q <= 1_000_000)
			return q;
		} catch(NumberFormatException ignored) {
		}
	    }
	}
	return 0;
    }

    private static double qualityFromTips(List<ItemInfo> infos) {
	List<ItemInfo.Tip> tips = new ArrayList<>();
	collectTips(infos, tips);
	for(ItemInfo.Tip t : tips) {
	    double rq = readQFromResourceQuality(t);
	    if(rq > 0)
		return rq;
	    rq = reflectNumericQuality(t);
	    if(rq > 0)
		return rq;
	    if(t instanceof ItemInfo.AdHoc) {
		double v = parseQualityFromText(((ItemInfo.AdHoc)t).raw);
		if(v > 0)
		    return v;
	    } else if(t instanceof ItemInfo.Name) {
		double v = parseQualityFromText(((ItemInfo.Name)t).nameText());
		if(v > 0)
		    return v;
	    } else if(t instanceof ItemInfo.Pagina) {
		double v = parseQualityFromText(((ItemInfo.Pagina)t).str);
		if(v > 0)
		    return v;
	    } else {
		double v = reflectStringQualityFields(t);
		if(v > 0)
		    return v;
	    }
	}
	return 0;
    }

    /** Per-slot tex cache; safe to keep on a long-lived widget or spec. */
    public static final class SlotCache {
	public Tex tex;
	public String key;

	public void clear() {
	    if(tex != null) {
		tex.dispose();
		tex = null;
	    }
	    key = null;
	}

	public void draw(GOut g, Coord sz, double q) {
	    if(q <= 0)
		return;
	    String display = MoonConfig.formatInvQualityNumber(q);
	    Color col = MoonConfig.invQualityOverlayColor(q);
	    String k = MoonConfig.invQualitySettingsRev + "|" + display + "|" + col.getRGB() + "|"
		+ MoonConfig.invQualityFontPx;
	    if(tex == null || !k.equals(key)) {
		if(tex != null)
		    tex.dispose();
		Text.Foundry f = new Text.Foundry(Text.sans, Math.max(1, UI.scale(MoonConfig.invQualityFontPx))).aa(true);
		tex = f.render(display, col).tex();
		key = k;
	    }
	    drawInvQualityPlaced(g, sz, tex);
	}
    }

    /**
     * Places the quality label in the slot according to {@link MoonConfig#invQualityCorner}.
     * {@link GOut#aimage(Tex, Coord, double, double)} anchor: (0,0) top-left of tex at c, (1,1) bottom-right at c.
     */
    public static void drawInvQualityPlaced(GOut g, Coord sz, Tex tex) {
	int pad = UI.scale(1);
	Coord c;
	double ax, ay;
	switch(Math.floorMod(MoonConfig.invQualityCorner, 5)) {
	case 1: /* top-left */
	    c = new Coord(pad, pad);
	    ax = 0.0;
	    ay = 0.0;
	    break;
	case 2: /* bottom-right */
	    c = new Coord(sz.x - pad, sz.y - pad);
	    ax = 1.0;
	    ay = 1.0;
	    break;
	case 3: /* bottom-left */
	    c = new Coord(pad, sz.y - pad);
	    ax = 0.0;
	    ay = 1.0;
	    break;
	case 4: /* center */
	    c = sz.div(2);
	    ax = 0.5;
	    ay = 0.5;
	    break;
	case 0: /* top-right (default) */
	default:
	    c = new Coord(sz.x - pad, pad);
	    ax = 1.0;
	    ay = 0.0;
	    break;
	}
	g.aimage(tex, c, ax, ay);
    }
}
