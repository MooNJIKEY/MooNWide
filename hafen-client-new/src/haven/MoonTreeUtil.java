package haven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tree-bot helpers: persist tile {@link Area}, estimate tree count in that area on the client.
 */
public final class MoonTreeUtil {
    private MoonTreeUtil() {}

    public static String areaToString(Area a) {
	if(a == null || !a.positive())
	    return("");
	return(a.ul.x + " " + a.ul.y + " " + a.br.x + " " + a.br.y);
    }

    /**
     * One world XY per non-empty line ({@code x y} or {@code x y z}; z ignored).
     * Used as logging stand waypoints before chopping in {@link MoonTreeChopBot}.
     */
    public static List<Coord2d> parseWorldSpotList(String s) {
	if(s == null || s.trim().isEmpty())
	    return(Collections.emptyList());
	ArrayList<Coord2d> out = new ArrayList<>();
	for(String line : s.trim().split("\\r?\\n")) {
	    line = line.trim();
	    if(line.isEmpty())
		continue;
	    String[] p = line.split("\\s+");
	    if(p.length < 2)
		continue;
	    try {
		double x = Double.parseDouble(p[0]);
		double y = Double.parseDouble(p[1]);
		out.add(new Coord2d(x, y));
	    } catch(NumberFormatException ignored) {}
	}
	return(out);
    }

    public static Area parseArea(String s) {
	if(s == null || s.trim().isEmpty())
	    return(null);
	String[] p = s.trim().split("\\s+");
	if(p.length != 4)
	    return(null);
	try {
	    int x0 = Integer.parseInt(p[0]), y0 = Integer.parseInt(p[1]);
	    int x1 = Integer.parseInt(p[2]), y1 = Integer.parseInt(p[3]);
	    return(new Area(new Coord(x0, y0), new Coord(x1, y1)));
	} catch(NumberFormatException e) {
	    return(null);
	}
    }

    /** Single tile {@code "tx ty"} for water source / barrel tile. */
    public static Coord parseTileCoord(String s) {
	if(s == null || s.trim().isEmpty())
	    return(null);
	String[] p = s.trim().split("\\s+");
	if(p.length != 2)
	    return(null);
	try {
	    return(new Coord(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
	} catch(NumberFormatException e) {
	    return(null);
	}
    }

    /** Count choppable trees (excludes stumps) in the given tile area. */
    public static int countTreesInTileArea(Glob glob, Area tileArea) {
	if(glob == null || tileArea == null || !tileArea.positive())
	    return(0);
	int n = 0;
	synchronized(glob.oc) {
	    for(Gob g : glob.oc) {
		if(g == null)
		    continue;
		try {
		    Drawable d = g.getattr(Drawable.class);
		    if(d == null)
			continue;
		    Resource r = d.getres();
		    if(r == null)
			continue;
		    Coord t = g.rc.floor(MCache.tilesz);
		    if(!tileArea.contains(t))
			continue;
		    if(isChoppableTree(r.name))
			n++;
		} catch(Exception ignored) {}
	    }
	}
	return(n);
    }

    /** Standing tree — not a stump / leftover. */
    public static boolean isChoppableTree(String resName) {
	if(resName == null)
	    return(false);
	String n = resName.toLowerCase(Locale.ROOT);
	if(!n.contains("/trees/"))
	    return(false);
	if(n.contains("stump"))
	    return(false);
	if(n.contains("oldstump") || n.contains("treestump"))
	    return(false);
	return(true);
    }

    /**
     * Felled trunk / tree log on the ground (haul when split mode is off). Excludes boards,
     * wood blocks, branches — those are treated as split output / firewood.
     */
    public static boolean isLooseTreeLog(String resName) {
	if(!isLooseLogOrWoodBlock(resName))
	    return(false);
	String n = resName.toLowerCase(Locale.ROOT);
	if(n.contains("board"))
	    return(false);
	if(n.contains("wblock"))
	    return(false);
	if(n.contains("branch"))
	    return(false);
	if(n.contains("block") && n.contains("wood") && !n.contains("log"))
	    return(false);
	return(n.contains("log") || n.contains("oldtrunk") || n.contains("trunk"));
    }

    /** Split / firewood pieces (not a full tree log) — for main stock when split mode is on. */
    public static boolean isLooseSplitOrFirewoodChunk(String resName) {
	return(isLooseLogOrWoodBlock(resName) && !isLooseTreeLog(resName));
    }

    /**
     * Loose log / wood block on the ground (pickup / heap), not a living tree and not a stockpile.
     */
    public static boolean isLooseLogOrWoodBlock(String resName) {
	if(resName == null)
	    return(false);
	String n = resName.toLowerCase(Locale.ROOT);
	if(n.contains("gfx/terobjs/stockpile"))
	    return(false);
	if(n.contains("gfx/terobjs/items/")) {
	    if(n.contains("log") || n.contains("oldtrunk") || n.contains("wblock") || n.contains("board"))
		return(true);
	    if(n.contains("block") && (n.contains("wood") || n.contains("tree")))
		return(true);
	}
	if(n.contains("gfx/terobjs/heap")) {
	    if(n.contains("log") || n.contains("wood") || n.contains("block"))
		return(true);
	}
	if(n.contains("gfx/terobjs/pickup") && n.contains("log"))
	    return(true);
	/* Felled trunks often stay under trees/ (not items/heap) — same cues as {@link #isXRayExcludeLog}. */
	if(n.contains("/trees/")) {
	    if(n.contains("stump") || n.contains("oldstump") || n.contains("treestump"))
		return(false);
	    if(n.contains("oldtrunk") || n.contains("felltreetrunk") || n.contains("fallentree")
		|| n.contains("felltree") || n.contains("deadtree") || n.contains("layingtree")
		|| n.contains("layinglog"))
		return(true);
	    if(n.contains("-log") || n.contains("_log"))
		return(true);
	    if(n.contains("/log/") || n.endsWith("/log"))
		return(true);
	    /* Felled / cut timber: many installs use …/trees/fell*, timber*, etc. (not only *log*). */
	    if(n.contains("fell") || n.contains("timber") || n.contains("debark") || n.contains("billet")
		|| n.contains("roundwood") || n.contains("logchunk") || n.contains("woodchunk")
		|| n.contains("cuttree") || n.contains("choptree"))
		return(true);
	}
	return(false);
    }

    /**
     * Ground timber / felled pieces {@link MoonXRay} must not hide (under {@code gfx/terobjs/trees/} the mesh
     * often shares the folder with standing trees — use {@link #isLooseLogOrWoodBlock} as single source).
     */
    public static boolean isXRayExcludeLog(String resName) {
	return resName != null && isLooseLogOrWoodBlock(resName);
    }
}
