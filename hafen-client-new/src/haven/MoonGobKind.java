package haven;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Resource classifier for trees, bushes, workstations, etc. (used by x-ray, hitboxes, bulk binds).
 *
 * <p>Tree/bush basenames are a fixed allowlist; runtime does not use external databases.
 * Prefix heuristics remain only as a fallback for unknown resources or MooNWide-specific additions.
 */
public final class MoonGobKind {
    public enum Kind {
	TREE,
	LOG,
	STUMP,
	BUSH,
	BOULDER,
	DFRAME,
	TANTUB,
	CHEESERACK,
	WORKSTATION,
	UNKNOWN
    }

    private static final Set<String> KNOWN_TREE_BASENAMES = new HashSet<>(Arrays.asList(
	"acacia", "alder", "almondtree", "appletree", "ash", "aspen", "baywillow", "beech",
	"birch", "birdcherrytree", "blackpine", "blackpoplar", "buckthorn", "carobtree",
	"cedar", "chastetree", "checkertree", "cherry", "chestnuttree", "conkertree",
	"corkoak", "crabappletree", "cypress", "dogwood", "dwarfpine", "elm", "figtree",
	"fir", "gloomcap", "gnomeshat", "goldenchain", "grayalder", "hazel", "hornbeam",
	"juniper", "kingsoak", "larch", "laurel", "lemontree", "linden", "lotetree",
	"maple", "mayflower", "medlartree", "moundtree", "mulberry", "oak", "olivetree",
	"orangetree", "osier", "peartree", "persimmontree", "pine", "planetree", "plumtree",
	"poplar", "quincetree", "rowan", "sallow", "silverfir", "sorbtree", "spruce",
	"stonepine", "strawberrytree", "sweetgum", "sycamore", "tamarisk", "terebinth",
	"towercap", "treeheath", "trombonechantrelle", "walnuttree", "wartybirch",
	"whitebeam", "willow", "wychelm", "yew", "zelkova"
    ));

    private static final Set<String> KNOWN_BUSH_BASENAMES = new HashSet<>(Arrays.asList(
	"arrowwood", "blackberrybush", "blackcurrant", "blackthorn", "bogmyrtle", "boxwood",
	"bsnightshade", "caprifole", "cavefern", "crampbark", "dogrose", "elderberrybush",
	"ghostpipe", "gooseberrybush", "gorse", "hawthorn", "hoarwithy", "holly", "mastic",
	"poppycaps", "raspberrybush", "redcurrant", "sandthorn", "spindlebush", "teabush",
	"tibast", "tundrarose", "witherstand", "woodbine"
    ));

    private static final Set<String> GENERIC_WORKSTATION_BASENAMES = new HashSet<>(Arrays.asList(
	"cauldron", "cheeserack", "cupboard", "dframe", "fineryforge", "htable", "kiln",
	"largechest", "matalcabinet", "metalcabinet", "oven", "primsmelter", "smelter",
	"steelcrucible", "ttub"
    ));

    private MoonGobKind() {}

    public static Kind classify(Gob gob) {
	return classify(resourceName(gob));
    }

    public static Kind classify(String resName) {
	String name = normalize(resName);
	if(name == null)
	    return Kind.UNKNOWN;
	if(isLooseLog(name))
	    return Kind.LOG;
	if(isStump(name))
	    return Kind.STUMP;
	if(name.equals("gfx/terobjs/dframe"))
	    return Kind.DFRAME;
	if(name.equals("gfx/terobjs/ttub"))
	    return Kind.TANTUB;
	if(name.equals("gfx/terobjs/cheeserack"))
	    return Kind.CHEESERACK;
	if(isBoulder(name))
	    return Kind.BOULDER;
	if(isKnownTree(name))
	    return Kind.TREE;
	if(isKnownBush(name))
	    return Kind.BUSH;
	if(isGenericWorkstation(name))
	    return Kind.WORKSTATION;
	if(isTreeFallback(name))
	    return Kind.TREE;
	if(isBushFallback(name))
	    return Kind.BUSH;
	return Kind.UNKNOWN;
    }

    public static String resourceName(Gob gob) {
	if(gob == null)
	    return null;
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    if(d == null)
		return null;
	    Resource res = d.getres();
	    if(res != null)
		return normalize(res.name);
	    if(d instanceof SprDrawable) {
		SprDrawable sd = (SprDrawable)d;
		if(sd.spr != null && sd.spr.res != null)
		    return normalize(sd.spr.res.name);
	    }
	} catch(Loading ignored) {
	}
	return null;
    }

    public static boolean isXrayHideTarget(Kind kind, int hideMode) {
	return(kind == Kind.TREE)
	    || (kind == Kind.BUSH)
	    || ((hideMode == 1) && (kind == Kind.BOULDER));
    }

    public static boolean isWorkstation(Kind kind) {
	switch(kind) {
	case DFRAME:
	case TANTUB:
	case CHEESERACK:
	case WORKSTATION:
	    return true;
	default:
	    return false;
	}
    }

    public static boolean isFlatWallTarget(Gob gob) {
	return isFlatWallTarget(resourceName(gob));
    }

    public static boolean isFlatWallTarget(String resName) {
	String name = normalize(resName);
	if(name == null || !name.startsWith("gfx/terobjs/arch/"))
	    return false;
	String base = basename(name);
	if(base == null)
	    return false;
	return base.equals("brickwallseg") || base.equals("brickwallcp")
	    || base.equals("palisadeseg") || base.equals("palisadecp")
	    || base.equals("poleseg") || base.equals("polecp")
	    || base.equals("drystonewallseg") || base.equals("drystonewallcp")
	    || base.equals("hwall");
    }

    public static boolean isFlatCupboardTarget(Gob gob) {
	return isFlatCupboardTarget(resourceName(gob));
    }

    public static boolean isFlatCupboardTarget(String resName) {
	String name = normalize(resName);
	if(name == null || !name.startsWith("gfx/terobjs/"))
	    return false;
	String base = basename(name);
	if(base == null)
	    return false;
	return base.equals("cupboard") || base.equals("largechest")
	    || base.equals("metalcabinet") || base.equals("matalcabinet");
    }

    public static String stationKey(Gob gob) {
	return stationKey(classify(gob), resourceName(gob));
    }

    public static String stationKey(Kind kind, String resName) {
	switch(kind) {
	case DFRAME:
	    return "dframe";
	case TANTUB:
	    return "ttub";
	case CHEESERACK:
	    return "cheeserack";
	case WORKSTATION:
	    return basename(resName);
	default:
	    break;
	}
	String name = normalize(resName);
	if(name == null)
	    return null;
	if(name.contains("campfire") || name.contains("fgcamp"))
	    return "campfire";
	if(name.contains("bonfire"))
	    return "bonfire";
	if(name.contains("/fire") || name.contains("fireplace"))
	    return "fireplace";
	return null;
    }

    public static Set<String> knownBulkStationKeys() {
	return KNOWN_BULK_STATION_KEYS;
    }

    private static final Set<String> KNOWN_BULK_STATION_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
	"bonfire", "campfire", "cauldron", "cheeserack", "cupboard", "dframe",
	"fineryforge", "fireplace", "htable", "kiln", "largechest", "matalcabinet",
	"metalcabinet", "oven", "primsmelter", "smelter", "steelcrucible", "ttub"
    )));

    private static boolean isKnownTree(String name) {
	if(!name.startsWith("gfx/terobjs/trees/"))
	    return false;
	return KNOWN_TREE_BASENAMES.contains(basename(name));
    }

    private static boolean isKnownBush(String name) {
	if(!name.startsWith("gfx/terobjs/bushes/"))
	    return false;
	return KNOWN_BUSH_BASENAMES.contains(basename(name));
    }

    private static boolean isBoulder(String name) {
	return name.startsWith("gfx/terobjs/bumlings/")
	    || name.startsWith("gfx/tiles/rocks/");
    }

    private static boolean isLooseLog(String name) {
	return name.equals("gfx/terobjs/trees/oldtrunk")
	    || MoonTreeUtil.isLooseLogOrWoodBlock(name);
    }

    /**
     * Fallback for tree resources missing from the known basename list.
     * Keep this conservative: anything that looks like felled timber must not be hidden by xray.
     */
    private static boolean isTreeFallback(String name) {
	if(!name.startsWith("gfx/terobjs/trees/"))
	    return false;
	if(isLooseLog(name) || isStump(name))
	    return false;
	String base = basename(name);
	if(base == null)
	    return false;
	return !looksLikeFelledTimber(base);
    }

    private static boolean isBushFallback(String name) {
	return name.startsWith("gfx/terobjs/bushes/");
    }

    private static boolean looksLikeFelledTimber(String base) {
	return base.contains("trunk")
	    || base.contains("log")
	    || base.contains("timber")
	    || base.contains("fell")
	    || base.contains("fallen")
	    || base.contains("laying")
	    || base.contains("deadtree")
	    || base.contains("debark")
	    || base.contains("billet")
	    || base.contains("roundwood")
	    || base.contains("chunk")
	    || base.contains("cuttree")
	    || base.contains("choptree");
    }

    private static boolean isStump(String name) {
	if(!name.startsWith("gfx/terobjs/trees/"))
	    return false;
	return name.contains("stump") || name.contains("oldstump") || name.contains("treestump");
    }

    private static boolean isGenericWorkstation(String name) {
	if(!name.startsWith("gfx/terobjs/"))
	    return false;
	return GENERIC_WORKSTATION_BASENAMES.contains(basename(name));
    }

    private static String basename(String name) {
	if(name == null)
	    return null;
	int p = name.lastIndexOf('/');
	return (p >= 0) ? name.substring(p + 1) : name;
    }

    private static String normalize(String resName) {
	if(resName == null || resName.isEmpty())
	    return null;
	return resName.toLowerCase(Locale.ROOT);
    }
}
