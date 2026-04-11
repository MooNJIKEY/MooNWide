package haven;

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
 * Lightweight armor/wear extraction for UI overlays. Keep this strictly scoped to
 * {@link ItemInfo} trees so item rendering cannot wander into {@link GItem}/{@link UI} graphs.
 */
public final class MoonItemStats {
    private static final String INT_TOKEN = "-?\\d(?:[\\d\\s,._]*\\d)?";
    private static final Pattern ARMOR_TEXT = Pattern.compile(
	"(?i)(?:armor\\s*class|armor|класс\\s*брони|броня)[^\\d-]{0,24}(" + INT_TOKEN + ")\\s*[/+]\\s*(" + INT_TOKEN + ")");
    private static final Pattern WEAR_TEXT = Pattern.compile(
	"(?i)(?:wear|durability|прочность|износ)[^\\d-]{0,24}(" + INT_TOKEN + ")\\s*/\\s*(" + INT_TOKEN + ")");
    private static final Map<Class<?>, Field[]> ITEMINFO_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> ITEMINFO_STRING_FIELDS = new ConcurrentHashMap<>();

    private MoonItemStats() {}

    public static final class ArmorInfo {
	public final int hard;
	public final int soft;

	public ArmorInfo(int hard, int soft) {
	    this.hard = hard;
	    this.soft = soft;
	}

	public int total() {
	    return(hard + soft);
	}
    }

    public static final class WearInfo {
	public final int damage;
	public final int max;

	public WearInfo(int damage, int max) {
	    this.damage = damage;
	    this.max = max;
	}

	public int remaining() {
	    return(max - damage);
	}

	public boolean broken() {
	    return(remaining() <= 0);
	}
    }

    public static ArmorInfo armor(List<ItemInfo> info) {
	if(info == null)
	    return(null);
	for(ItemInfo tip : info) {
	    ArmorInfo found = armorInfo(tip, 0);
	    if(found != null)
		return(found);
	}
	return(null);
    }

    public static WearInfo wear(List<ItemInfo> info) {
	if(info == null)
	    return(null);
	for(ItemInfo tip : info) {
	    WearInfo found = wearInfo(tip, 0);
	    if(found != null)
		return(found);
	}
	return(null);
    }

    public static boolean isBroken(List<ItemInfo> info) {
	WearInfo wear = wear(info);
	return((wear != null) && wear.broken());
    }

    private static ArmorInfo armorInfo(ItemInfo info, int depth) {
	if(info == null || depth > 2)
	    return(null);
	if(info instanceof ItemInfo.Contents)
	    return(armor(((ItemInfo.Contents)info).sub));
	ArmorInfo parsed = parseArmor(stringsOf(info));
	if(parsed != null)
	    return(parsed);
	String cn = info.getClass().getName().toLowerCase(Locale.ROOT);
	if(cn.endsWith(".armor") || cn.contains("armor")) {
	    Integer hard = findNamedInt(info, "hard", "hardarmor", "hardArmor", "h");
	    Integer soft = findNamedInt(info, "soft", "softarmor", "softArmor", "s");
	    if((hard != null) && (soft != null))
		return(new ArmorInfo(hard, soft));
	    int[] pair = firstTwoNumberFields(info);
	    if(pair != null)
		return(new ArmorInfo(pair[0], pair[1]));
	}
	return(armorNested(info, depth));
    }

    private static WearInfo wearInfo(ItemInfo info, int depth) {
	if(info == null || depth > 2)
	    return(null);
	if(info instanceof ItemInfo.Contents)
	    return(wear(((ItemInfo.Contents)info).sub));
	WearInfo parsed = parseWear(stringsOf(info));
	if(parsed != null)
	    return(parsed);
	String cn = info.getClass().getName().toLowerCase(Locale.ROOT);
	if(cn.endsWith(".wear") || cn.contains("wear") || cn.contains("durability")) {
	    Integer max = findNamedInt(info, "m", "max", "durability", "maxdurability");
	    Integer dmg = findNamedInt(info, "d", "damage", "wear");
	    if((max != null) && (dmg != null))
		return(new WearInfo(dmg, max));
	    int[] pair = firstTwoNumberFields(info);
	    if(pair != null)
		return(new WearInfo(pair[0], pair[1]));
	}
	return(wearNested(info, depth));
    }

    private static ArmorInfo armorNested(ItemInfo info, int depth) {
	for(Field f : itemInfoFields(info.getClass())) {
	    try {
		Object val = f.get(info);
		if(val instanceof ItemInfo) {
		    ArmorInfo nested = armorInfo((ItemInfo)val, depth + 1);
		    if(nested != null)
			return(nested);
		}
	    } catch(ReflectiveOperationException | RuntimeException ignored) {
	    }
	}
	return(null);
    }

    private static WearInfo wearNested(ItemInfo info, int depth) {
	for(Field f : itemInfoFields(info.getClass())) {
	    try {
		Object val = f.get(info);
		if(val instanceof ItemInfo) {
		    WearInfo nested = wearInfo((ItemInfo)val, depth + 1);
		    if(nested != null)
			return(nested);
		}
	    } catch(ReflectiveOperationException | RuntimeException ignored) {
	    }
	}
	return(null);
    }

    private static String stringsOf(ItemInfo info) {
	StringBuilder buf = new StringBuilder(64);
	for(Field f : itemInfoStringFields(info.getClass())) {
	    try {
		String s = (String)f.get(info);
		if((s != null) && !s.isEmpty()) {
		    if(buf.length() > 0)
			buf.append('\n');
		    buf.append(s);
		}
	    } catch(ReflectiveOperationException | RuntimeException ignored) {
	    }
	}
	return(buf.toString());
    }

    private static Field[] itemInfoFields(Class<?> type) {
	return(ITEMINFO_FIELDS.computeIfAbsent(type, MoonItemStats::collectItemInfoFields));
    }

    private static Field[] itemInfoStringFields(Class<?> type) {
	return(ITEMINFO_STRING_FIELDS.computeIfAbsent(type, MoonItemStats::collectItemInfoStringFields));
    }

    private static Field[] collectItemInfoFields(Class<?> leaf) {
	ArrayList<Field> acc = new ArrayList<>();
	for(Class<?> cl = leaf; cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
		    continue;
		if(!ItemInfo.class.isAssignableFrom(f.getType()))
		    continue;
		try {
		    f.setAccessible(true);
		    acc.add(f);
		} catch(RuntimeException ignored) {
		}
	    }
	}
	return(acc.toArray(new Field[0]));
    }

    private static Field[] collectItemInfoStringFields(Class<?> leaf) {
	ArrayList<Field> acc = new ArrayList<>();
	for(Class<?> cl = leaf; cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || f.getType() != String.class)
		    continue;
		try {
		    f.setAccessible(true);
		    acc.add(f);
		} catch(RuntimeException ignored) {
		}
	    }
	}
	return(acc.toArray(new Field[0]));
    }

    private static ArmorInfo parseArmor(String raw) {
	if(raw == null)
	    return(null);
	Matcher m = ARMOR_TEXT.matcher(raw);
	if(m.find()) {
	    try {
		return(new ArmorInfo(parsePackedInt(m.group(1)), parsePackedInt(m.group(2))));
	    } catch(NumberFormatException ignored) {
	    }
	}
	return(null);
    }

    private static WearInfo parseWear(String raw) {
	if(raw == null)
	    return(null);
	Matcher m = WEAR_TEXT.matcher(raw);
	if(m.find()) {
	    try {
		return(new WearInfo(parsePackedInt(m.group(1)), parsePackedInt(m.group(2))));
	    } catch(NumberFormatException ignored) {
	    }
	}
	return(null);
    }

    private static int parsePackedInt(String raw) {
	if(raw == null)
	    throw(new NumberFormatException("null"));
	String norm = raw.replaceAll("[^\\d-]", "");
	if(norm.isEmpty() || "-".equals(norm))
	    throw(new NumberFormatException(raw));
	return(Integer.parseInt(norm));
    }

    private static Integer findNamedInt(ItemInfo info, String... names) {
	for(String name : names) {
	    Integer val = reflectInt(info, name);
	    if(val != null)
		return(val);
	}
	return(null);
    }

    private static Integer reflectInt(ItemInfo info, String name) {
	for(Class<?> cl = info.getClass(); cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    try {
		Field f = cl.getDeclaredField(name);
		if(!f.canAccess(info))
		    f.setAccessible(true);
		Object val = f.get(info);
		if(val instanceof Number)
		    return(((Number)val).intValue());
	    } catch(ReflectiveOperationException | RuntimeException ignored) {
	    }
	}
	return(null);
    }

    private static int[] firstTwoNumberFields(ItemInfo info) {
	int[] vals = new int[2];
	int found = 0;
	for(Class<?> cl = info.getClass(); cl != null && ItemInfo.class.isAssignableFrom(cl); cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
		    continue;
		try {
		    if(!f.canAccess(info))
			f.setAccessible(true);
		    Object val = f.get(info);
		    if(val instanceof Number) {
			vals[found++] = ((Number)val).intValue();
			if(found >= 2)
			    return(vals);
		    }
		} catch(ReflectiveOperationException | RuntimeException ignored) {
		}
	    }
	}
	return(null);
    }
}
