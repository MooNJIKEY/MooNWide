package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Cyclable entity hitbox overlay: mode 1 = footprint outlines on terrain + visible meshes;
 * mode 2 = same outlines, structural meshes hidden (X-Ray–style ground traces). Excludes players and mobs.
 *
 * Uses the same {@link MoonHitboxMode#getBBox} data extraction for consistency.
 */
public final class MoonEntityHitboxViz {
    private MoonEntityHitboxViz() {}

    private static final Color COL_OBST = new Color(100, 235, 255, 230);
    private static final double MAX_DIST = 95 * 11.0;
    private static final int MAX_DRAW = 500;
    private static final float LINE_W = 1.35f;
    private static final double SYNC_INTERVAL = 0.12;
    private static double lastSync = 0;
    private static int lastMode = Integer.MIN_VALUE;

    public static void tick(MapView mv) {
	if (mv == null || mv.glob == null || mv.glob.oc == null)
	    return;
	int mode = MoonConfig.entityHitboxVizMode;
	double now = Utils.rtime();
	boolean changed = (mode != lastMode);
	if(!changed && (mode == 0))
	    return;
	if(!changed && ((now - lastSync) < SYNC_INTERVAL))
	    return;
	lastMode = mode;
	lastSync = now;
	if (mode == 2) {
	    hideStructural(mv);
	} else {
	    restoreHidden(mv);
	}
    }

    private static void hideStructural(MapView mv) {
	List<Gob> toHide = new ArrayList<>();
	try {
	    synchronized (mv.glob.oc) {
		for (Gob gob : mv.glob.oc) {
		    if (gob == null || gob.id == mv.plgob)
			continue;
		    if (!eligible(gob, mv.plgob))
			continue;
		    if (mv.isHiddenBy(gob, MapView.HideReason.ENTITY_HITBOX))
			continue;
		    toHide.add(gob);
		}
	    }
	} catch (Exception ignored) {
	    return;
	}
	for (Gob gob : toHide) {
	    try {
		mv.hideGob(gob, MapView.HideReason.ENTITY_HITBOX);
	    } catch (Exception ignored) {}
	}
    }

    private static void restoreHidden(MapView mv) {
	for (Gob gob : mv.hiddenGobs(MapView.HideReason.ENTITY_HITBOX)) {
	    if (gob != null)
		try { mv.showGob(gob, MapView.HideReason.ENTITY_HITBOX); } catch (Exception ignored) {}
	}
    }

    public static void draw(GOut g, MapView mv) {
	/* Entity hitboxes now use the same detached local-space wire pipeline as global hitboxes. */
    }

    static boolean wantsHidden(Gob gob, long plgob) {
	return MoonConfig.entityHitboxVizMode == 2 && eligible(gob, plgob);
    }

    static boolean eligible(Gob gob, long plgob) {
	if (gob == null || gob.id == plgob)
	    return false;
	Coord2d[][] bbox = null;
	try {
	    bbox = MoonHitboxMode.getBBox(gob);
	} catch (Loading ignored) {}
	if (bbox == null || bbox.length == 0)
	    return false;
	MoonOverlay.GobType t = MoonOverlay.classifyGob(gob);
	return t != MoonOverlay.GobType.PLAYER
	    && t != MoonOverlay.GobType.HOSTILE_MOB
	    && t != MoonOverlay.GobType.NEUTRAL_MOB;
    }

    private static boolean drawGobFootprint(GOut g, MapView mv, Gob gob) {
	Coord2d[][] bbox = MoonHitboxMode.getBBox(gob);
	if (bbox == null || bbox.length == 0)
	    return false;

	double a = gob.a;
	double sa = Math.sin(a), ca = Math.cos(a);
	boolean drewAny = false;
	g.chcolor(COL_OBST);
	for (Coord2d[] poly : bbox) {
	    if (poly == null || poly.length < 2)
		continue;
	    if (drawPolyGround(g, mv, gob, poly, sa, ca))
		drewAny = true;
	}
	return drewAny;
    }

    private static boolean drawPolyGround(GOut g, MapView mv, Gob gob, Coord2d[] poly,
					double sa, double ca) {
	if (poly == null || poly.length < 2)
	    return false;
	Coord prev = null, first = null;
	Coord2d firstW = null, prevW = null;
	boolean drewSeg = false;
	for (Coord2d p : poly) {
	    if (p == null)
		continue;
	    if (!Double.isFinite(p.x) || !Double.isFinite(p.y))
		continue;
	    Coord2d rv = Coord2d.of((p.x * ca) - (p.y * sa), (p.y * ca) + (p.x * sa)).add(gob.rc);
	    Coord3f wz;
	    try {
		wz = mv.glob.map.getzp(rv);
	    } catch (Exception e) {
		continue;
	    }
	    Coord3f sc = cornerScreen(mv, wz);
	    if (sc == null)
		continue;
	    Coord cur = Coord.of(Math.round(sc.x), Math.round(sc.y));
	    if (first == null) {
		first = cur;
		firstW = rv;
	    }
	    if (prev != null && MoonHitboxMode.hitboxEdgeOk(prev, cur, prevW, rv)) {
		g.line(prev, cur, LINE_W);
		drewSeg = true;
	    }
	    prev = cur;
	    prevW = rv;
	}
	if (prev != null && first != null && !prev.equals(first)
	    && firstW != null && prevW != null && MoonHitboxMode.hitboxEdgeOk(prev, first, prevW, firstW)) {
	    g.line(prev, first, LINE_W);
	    drewSeg = true;
	}
	return drewSeg;
    }

    private static Coord3f cornerScreen(MapView mv, Coord3f wc) {
	return MoonHitboxMode.hitboxScreenProject(mv, wc);
    }
}
