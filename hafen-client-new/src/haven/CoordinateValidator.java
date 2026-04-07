package haven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side checks before issuing a map movement click ({@link TeleportManager}).
 * The server remains authoritative; these gates limit obviously bad / abusive targets.
 */
public final class CoordinateValidator {
    private CoordinateValidator() {}

    public enum Result {
	OK,
	NO_PLAYER,
	NO_MAP,
	LOADING,
	DISTANCE,
	TILE,
	HEIGHT,
	HOSTILE_ZONE,
	PATH,
	ATTR
    }

    private static final Comparator<TeleportPoint> BY_LAST_USED =
	Comparator.comparingLong((TeleportPoint p) -> p.lastUsed).reversed();

    public static Result validate(GameUI gui, Coord2d current, Coord2d target, StringBuilder detail) {
	if(gui == null || gui.map == null) {
	    if(detail != null) detail.append("no map");
	    return Result.NO_MAP;
	}
	if(current == null || target == null) {
	    if(detail != null) detail.append("null position");
	    return Result.NO_PLAYER;
	}
	MCache map = gui.map.glob.map;
	OCache oc = gui.map.glob.oc;
	double maxWorld = MoonConfig.teleportMaxTiles * MCache.tilesz.x;
	double dist = current.dist(target);
	if(dist > maxWorld + 1e-3) {
	    if(detail != null)
		detail.append(String.format(java.util.Locale.ROOT, "distance %.1f > %.1f tiles",
		    dist / MCache.tilesz.x, MoonConfig.teleportMaxTiles));
	    return Result.DISTANCE;
	}
	try {
	    if(!MoonPathfinder.isTilePassableAtWorld(map, target)) {
		if(detail != null) detail.append("tile blocked");
		return Result.TILE;
	    }
	    Coord ptc = current.floor(MCache.tilesz);
	    Coord ttc = target.floor(MCache.tilesz);
	    double zp = map.getcz(tileCentre(ptc));
	    double zt = map.getcz(tileCentre(ttc));
	    if(Math.abs(zt - zp) > MoonConfig.teleportMaxHeightDelta) {
		if(detail != null) detail.append("height delta");
		return Result.HEIGHT;
	    }
	} catch(Loading e) {
	    if(detail != null) detail.append("map loading");
	    return Result.LOADING;
	} catch(Exception e) {
	    if(detail != null) detail.append(e.getMessage());
	    return Result.NO_MAP;
	}
	if(MoonConfig.teleportBlockHostileNearTarget) {
	    double r = MoonConfig.teleportHostileClearTiles * MCache.tilesz.x;
	    try {
		synchronized(oc) {
		    for(Gob g : oc) {
			if(g == null) continue;
			if(!MoonOverlay.isThreatMob(g)) continue;
			if(g.rc.dist(target) < r) {
			    if(detail != null) detail.append("hostile near target");
			    return Result.HOSTILE_ZONE;
			}
		    }
		}
	    } catch(Exception ignored) {
	    }
	}
	if(MoonConfig.teleportRequirePath) {
	    long pg = MoonPacketHook.getPlayerGobId();
	    List<Coord2d> path = MoonPathfinder.findPath(map, oc, current, target, pg,
		MoonConfig.teleportBlockHostileNearTarget, MoonConfig.teleportHostileClearTiles);
	    if(path == null || path.isEmpty()) {
		if(detail != null) detail.append("no client path");
		return Result.PATH;
	    }
	}
	String attrKey = MoonConfig.teleportMinAttrKey;
	if(attrKey != null && !attrKey.isEmpty() && MoonConfig.teleportMinAttrSum > 0) {
	    try {
		Glob.CAttr a = gui.ui.sess.glob.getcattr(attrKey.intern());
		int sum = a.base + a.comp;
		if(sum < MoonConfig.teleportMinAttrSum) {
		    if(detail != null) detail.append("attribute gate");
		    return Result.ATTR;
		}
	    } catch(Exception e) {
		if(detail != null) detail.append("attr read failed");
		return Result.ATTR;
	    }
	}
	return Result.OK;
    }

    private static Coord2d tileCentre(Coord tc) {
	double w = MCache.tilesz.x, h = MCache.tilesz.y;
	return new Coord2d((tc.x + 0.5) * w, (tc.y + 0.5) * h);
    }

    public static List<TeleportPoint> sortedByLastUsed(List<TeleportPoint> src) {
	if(src == null || src.isEmpty())
	    return Collections.emptyList();
	List<TeleportPoint> c = new ArrayList<>(src);
	c.sort(BY_LAST_USED);
	return c;
    }
}
