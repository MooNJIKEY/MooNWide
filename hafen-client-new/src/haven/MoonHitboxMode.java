package haven;

import haven.render.Location;
import haven.render.Pipe;
import haven.render.RenderTree;
import haven.render.Transform;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hitbox overlay system (Shift+B cycles: off → outlines → outlines-only (gobs hidden)).
 *
 * Data extraction follows {@code GobHitbox.getBBox}-style rules: {@code gfx/terobjs/consobj} via
 * {@code haven.res.lib.obst.Obstacle} (sdt), resource obstacle/neg layers, RenderLink traversal,
 * hardcoded animal sizes, with caching (consobj is never cached — depends on sdt).
 */
public final class MoonHitboxMode {
    private MoonHitboxMode() {}

    private static final Color COL_DEFAULT   = new Color(120, 245, 255, 220);
    private static final Map<String, Coord2d[][]> hitboxCache = new HashMap<>();
    private static final Map<MapView, OverlayState> overlayStates = new WeakHashMap<>();
    private static final double SYNC_INTERVAL = 0.12;
    private static double lastSync = 0;
    private static int lastModeKey = Integer.MIN_VALUE;

    /** NDC depth gate for {@link #screenxf} — drops vertices behind/near clip to avoid screen-spanning rays. */
    private static final float HITBOX_SCRZ_MIN = -0.05f;
    private static final float HITBOX_SCRZ_MAX = 1.05f;
    private static final int HITBOX_MAX_SCREEN_EDGE = 3000;
    private static final double HITBOX_MAX_WORLD_EDGE = MCache.tilesz.x * 60.0;

    /**
     * Shared with {@link MoonEntityHitboxViz}: same z-clip as footprint outline overlays.
     */
    static Coord3f hitboxScreenProject(MapView mv, Coord3f wz) {
	try {
	    Coord3f s = mv.screenxf(wz);
	    if(s == null || !Float.isFinite(s.x) || !Float.isFinite(s.y) || !Float.isFinite(s.z))
		return(null);
	    if(s.z < HITBOX_SCRZ_MIN || s.z > HITBOX_SCRZ_MAX)
		return(null);
	    return(s);
	} catch(Exception e) {
	    return(null);
	}
    }

    static boolean hitboxEdgeOk(Coord prevSc, Coord curSc, Coord2d prevW, Coord2d curW) {
	if(prevSc == null || curSc == null || prevW == null || curW == null)
	    return(false);
	if(prevW.dist(curW) > HITBOX_MAX_WORLD_EDGE)
	    return(false);
	double dx = curSc.x - prevSc.x, dy = curSc.y - prevSc.y;
	if((dx * dx + dy * dy) > (double)HITBOX_MAX_SCREEN_EDGE * HITBOX_MAX_SCREEN_EDGE)
	    return(false);
	return(true);
    }

    /* ─── Hardcoded animal / prop BBoxes (GobHitbox table) ─── */

    private static final Coord2d[][] BBOX_CALF   = rect(-9, -3, 9, 3);
    private static final Coord2d[][] BBOX_LAMB   = rect(-4, -2, 5, 2);
    private static final Coord2d[][] BBOX_GOAT   = rect(-3, -2, 4, 2);
    private static final Coord2d[][] BBOX_PIG    = rect(-6, -3, 6, 3);
    private static final Coord2d[][] BBOX_BOOST  = rect(-4, -4, 4, 4);
    private static final Coord2d[][] BBOX_HORSE  = rect(-8, -4, 8, 4);
    private static final Coord2d[][] BBOX_TDEER  = rect(-6, -2, 12, 2);

    private static Coord2d[][] rect(double x0, double y0, double x1, double y1) {
	return new Coord2d[][] { new Coord2d[] {
	    Coord2d.of(x0, y0), Coord2d.of(x1, y0), Coord2d.of(x1, y1), Coord2d.of(x0, y1)
	}};
    }

    private static final class WireEntry {
	final Object owner;
	final MoonHitboxNode node;
	final RenderTree.Slot slot;
	final int sig;

	WireEntry(Object owner, MoonHitboxNode node, RenderTree.Slot slot, int sig) {
	    this.owner = owner;
	    this.node = node;
	    this.slot = slot;
	    this.sig = sig;
	}

	void remove() {
	    try {
		slot.remove();
	    } catch(RuntimeException ignored) {
	    }
	    node.dispose();
	}
    }

    private static final class OverlayState {
	final Map<Long, WireEntry> world = new HashMap<>();
	final Map<Integer, WireEntry> carried = new HashMap<>();
    }

    private static OverlayState overlays(MapView mv) {
	synchronized(overlayStates) {
	    return overlayStates.computeIfAbsent(mv, key -> new OverlayState());
	}
    }

    /* ─── Tick: hide/show gobs for mode 2 ─── */

    public static void tick(MapView mv) {
	if(mv == null || mv.glob == null || mv.glob.oc == null)
	    return;
	int modeKey = (MoonConfig.globalHitboxMode & 0xff)
	    | ((MoonConfig.entityHitboxVizMode & 0xff) << 8)
	    | (MoonConfig.gfxModHitboxes ? (1 << 16) : 0);
	double now = Utils.rtime();
	boolean changed = (modeKey != lastModeKey);
	if(!changed && (modeKey == 0))
	    return;
	if(!changed && ((now - lastSync) < SYNC_INTERVAL))
	    return;
	lastModeKey = modeKey;
	lastSync = now;
	if(MoonConfig.entityHitboxVizMode > 0) {
	    restoreHidden(mv);
	} else if(MoonConfig.globalHitboxMode == 2) {
	    hideAllButPlayer(mv);
	} else {
	    restoreHidden(mv);
	}
	try {
	    syncWorldWireLayer(mv);
	} catch(Exception ignored) {
	}
	try {
	    syncCarriedWireLayer(mv);
	} catch(Exception ignored) {
	}
	try {
	    syncPlobWireOverlay(mv);
	} catch(Exception ignored) {
	}
    }

    static boolean wantsHidden(Gob gob, long plgob) {
	return (MoonConfig.entityHitboxVizMode <= 0)
	    && (MoonConfig.globalHitboxMode == 2)
	    && (gob != null)
	    && (gob.id != plgob);
    }

    private static int hitboxSdtSig(Gob g) {
	ResDrawable rd = g.getattr(ResDrawable.class);
	if(rd == null || rd.sdt == null || rd.sdt.rbuf == null)
	    return 0;
	return Arrays.hashCode(rd.sdt.rbuf);
    }

    private static void removePlobHitboxWire(MapView.Plob p) {
	Gob.Overlay o = p.findol(MoonGobHitboxOl.OLID);
	if(o == null)
	    return;
	try {
	    if(o.spr != null)
		o.spr.dispose();
	} catch(Exception ignored) {
	}
	try {
	    o.remove(false);
	} catch(Exception ignored) {
	}
    }

    /** {@link MapView.Plob} hitbox as a 3D line overlay in gob-local space. */
    private static void syncPlobWireOverlay(MapView mv) {
	if(mv == null)
	    return;
	Gob pg = mv.moonActivePlacementGob();
	if(!(pg instanceof MapView.Plob))
	    return;
	MapView.Plob p = (MapView.Plob)pg;
	if(!MoonConfig.hitboxPlacementCarriedEnabled()) {
	    removePlobHitboxWire(p);
	    return;
	}
	Coord2d[][] bbox;
	try {
	    bbox = getBBox(p);
	} catch(Loading e) {
	    return;
	}
	if(bbox == null) {
	    removePlobHitboxWire(p);
	    return;
	}
	int sig = hitboxSdtSig(p);
	int bboxSig = bboxSignature(bbox);
	Gob.Overlay ex = p.findol(MoonGobHitboxOl.OLID);
	if(ex != null) {
	    if(ex.spr instanceof MoonGobHitboxOl) {
		MoonGobHitboxOl cur = (MoonGobHitboxOl)ex.spr;
		if(cur.sdtSignature() == sig && cur.bboxSignature() == bboxSig)
		    return;
	    }
	    removePlobHitboxWire(p);
	}
	final Coord2d[][] bboxf = bbox;
	final int sigf = sig;
	final int bboxSigf = bboxSig;
	p.addol(new Gob.Overlay(p, MoonGobHitboxOl.OLID, new Sprite.Mill<MoonGobHitboxOl>() {
	    @Override
	    public MoonGobHitboxOl create(Sprite.Owner owner) {
		return new MoonGobHitboxOl(owner, bboxf, sigf, bboxSigf);
	    }
	}), false);
    }

    /* ─── Draw: screen-projected 2D outlines ─── */

    public static void draw(GOut g, MapView mv) {
	/* World hitboxes render through detached local-space nodes in {@link #tick(MapView)}. */
    }

    /**
     * Plob + carried equipment: not in {@link OCache} or needs bone transform. Called from
     * {@link MapView#draw} whenever any hitbox overlay is on so it still runs if
     * {@link MoonConfig#gfxModHitboxes} is off or entity mode replaced {@link #draw}.
     */
    public static void drawPlacementAndCarriedHitboxes(GOut g, MapView mv) {
	if(mv == null)
	    return;
	try {
	    Gob ghost = mv.moonActivePlacementGob();
	    if(ghost != null) {
		/* Plob uses a detached wire overlay; this fallback only covers the loading gap. */
		if(ghost instanceof MapView.Plob) {
		    if(ghost.findol(MoonGobHitboxOl.OLID) == null)
			drawGobHitboxFallback(g, mv, ghost);
		} else {
		    drawGobHitboxFallback(g, mv, ghost);
		}
	    }
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
	g.chcolor();
    }

    private static void syncWorldWireLayer(MapView mv) {
	OverlayState st = overlays(mv);
	if((MoonConfig.entityHitboxVizMode <= 0) && !MoonConfig.gfxOrGlobalHitboxes()) {
	    clearEntries(st.world);
	    return;
	}
	List<Gob> snapshot = new ArrayList<>();
	synchronized(mv.glob.oc) {
	    for(Gob gob : mv.glob.oc)
		snapshot.add(gob);
	}
	Set<Long> keep = new HashSet<>();
	boolean entityOnly = MoonConfig.entityHitboxVizMode > 0;
	for(Gob gob : snapshot) {
	    if(gob == null)
		continue;
	    if(entityOnly) {
		if(!MoonEntityHitboxViz.eligible(gob, mv.plgob))
		    continue;
	    } else if((gob.id == mv.plgob) && !plgobShowsConsobjHitbox(gob)) {
		continue;
	    }
	    Coord2d[][] bbox;
	    try {
		bbox = getBBox(gob);
	    } catch(Loading e) {
		continue;
	    }
	    if(bbox == null || bbox.length == 0)
		continue;
	    keep.add(gob.id);
	    ensureWorldEntry(mv, st.world, gob, bbox, entrySig(worldSig(gob), bbox));
	}
	pruneEntries(st.world, keep);
    }

    private static void syncCarriedWireLayer(MapView mv) {
	OverlayState st = overlays(mv);
	if(!MoonConfig.hitboxPlacementCarriedEnabled()) {
	    clearEntries(st.carried);
	    return;
	}
	Gob pl;
	try {
	    pl = mv.player();
	} catch(Exception e) {
	    clearEntries(st.carried);
	    return;
	}
	if(pl == null) {
	    clearEntries(st.carried);
	    return;
	}
	Composite cmp = pl.getattr(Composite.class);
	if(cmp == null || cmp.comp == null || cmp.comp.equ == null || cmp.comp.equ.isEmpty()) {
	    clearEntries(st.carried);
	    return;
	}
	Set<Integer> keep = new HashSet<>();
	int idx = 0;
	for(Composited.Equipped eq : cmp.comp.equ) {
	    idx++;
	    if(eq == null || eq.desc == null || eq.desc.res == null)
		continue;
	    Composited.ED ed = eq.desc;
	    Resource er;
	    try {
		er = ed.res.res.get();
	    } catch(Loading e) {
		continue;
	    }
	    if(er == null || er.name == null)
		continue;
	    String nm = er.name;
	    if(!nm.startsWith("gfx/terobjs/") || nm.startsWith("gfx/terobjs/items/"))
		continue;
	    Coord2d[][] bbox;
	    try {
		bbox = bboxForResourceAndSdt(er, ed.res.sdt);
	    } catch(Loading e) {
		continue;
	    }
	    if(bbox == null || bbox.length == 0)
		continue;
	    int key = carriedKey(idx, eq, nm);
	    keep.add(key);
	    ensureCarriedEntry(mv, st.carried, key, eq, pl, bbox, entrySig(carriedSig(eq, nm), bbox));
	}
	pruneEntries(st.carried, keep);
    }

    private static void ensureWorldEntry(MapView mv, Map<Long, WireEntry> entries, Gob gob, Coord2d[][] bbox, int sig) {
	WireEntry cur = entries.get(gob.id);
	if(cur != null && cur.sig == sig && cur.owner == gob)
	    return;
	if(cur != null)
	    cur.remove();
	MoonHitboxNode node = new MoonHitboxNode(bbox, () -> Pipe.Op.compose(gob.placed.curplace(), MoonGobHitboxOl.renderState()));
	if(!node.valid()) {
	    entries.remove(gob.id);
	    return;
	}
	try {
	    entries.put(gob.id, new WireEntry(gob, node, mv.basic.add(node), sig));
	} catch(RuntimeException e) {
	    node.dispose();
	    entries.remove(gob.id);
	}
    }

    private static void ensureCarriedEntry(MapView mv, Map<Integer, WireEntry> entries, int key, Composited.Equipped eq,
					   Gob pl, Coord2d[][] bbox, int sig) {
	WireEntry cur = entries.get(key);
	if(cur != null && cur.sig == sig && cur.owner == eq)
	    return;
	if(cur != null)
	    cur.remove();
	MoonHitboxNode node = new MoonHitboxNode(bbox, () -> carriedState(pl, eq));
	if(!node.valid()) {
	    entries.remove(key);
	    return;
	}
	try {
	    entries.put(key, new WireEntry(eq, node, mv.basic.add(node), sig));
	} catch(RuntimeException e) {
	    node.dispose();
	    entries.remove(key);
	}
    }

    private static <K> void pruneEntries(Map<K, WireEntry> entries, Set<K> keep) {
	for(Iterator<Map.Entry<K, WireEntry>> it = entries.entrySet().iterator(); it.hasNext();) {
	    Map.Entry<K, WireEntry> ent = it.next();
	    if(keep.contains(ent.getKey()))
		continue;
	    ent.getValue().remove();
	    it.remove();
	}
    }

    private static <K> void clearEntries(Map<K, WireEntry> entries) {
	for(Iterator<Map.Entry<K, WireEntry>> it = entries.entrySet().iterator(); it.hasNext();) {
	    it.next().getValue().remove();
	    it.remove();
	}
    }

    private static int worldSig(Gob gob) {
	return (hitboxSdtSig(gob) * 31) + Objects.hashCode(MoonGobKind.resourceName(gob));
    }

    private static int carriedKey(int idx, Composited.Equipped eq, String resName) {
	int id = eq.id;
	if(id >= 0)
	    return id;
	return (idx * 31) + Objects.hashCode(resName);
    }

    private static int carriedSig(Composited.Equipped eq, String resName) {
	int sig = Objects.hashCode(resName);
	if(eq != null && eq.desc != null && eq.desc.res != null && eq.desc.res.sdt != null && eq.desc.res.sdt.rbuf != null)
	    sig = (sig * 31) + Arrays.hashCode(eq.desc.res.sdt.rbuf);
	return sig;
    }

    private static int entrySig(int baseSig, Coord2d[][] bbox) {
	return (baseSig * 31) + bboxSignature(bbox);
    }

    static int bboxSignature(Coord2d[][] bbox) {
	if(bbox == null)
	    return 0;
	int sig = 1;
	for(Coord2d[] poly : bbox) {
	    sig = (sig * 31) + ((poly == null) ? 0 : poly.length);
	    if(poly == null)
		continue;
	    for(Coord2d p : poly) {
		long xb = (p == null) ? 0L : Double.doubleToLongBits(p.x);
		long yb = (p == null) ? 0L : Double.doubleToLongBits(p.y);
		sig = (sig * 31) + (int)(xb ^ (xb >>> 32));
		sig = (sig * 31) + (int)(yb ^ (yb >>> 32));
	    }
	}
	return sig;
    }

    private static Pipe.Op carriedState(Gob pl, Composited.Equipped eq) {
	Composite cmp = pl.getattr(Composite.class);
	if(cmp == null || cmp.comp == null || eq == null || eq.desc == null)
	    return Pipe.Op.compose(pl.placed.curplace(), MoonGobHitboxOl.renderState());
	Composited.ED ed = eq.desc;
	Pipe.Op bone = null;
	if(ed.at != null && !ed.at.isEmpty()) {
	    Skeleton.Bone boneRef = cmp.comp.skel.bones.get(ed.at);
	    if(boneRef != null) {
		Pipe.Op op = cmp.comp.pose.bonetrans(boneRef.idx).get();
		if(op instanceof Transform)
		    bone = op;
	    }
	}
	Pipe.Op off = ((ed.off == null) || ed.off.equals(Coord3f.o)) ? null : Location.xlate(ed.off);
	return Pipe.Op.compose(pl.placed.curplace(), bone, off, MoonGobHitboxOl.renderState());
    }

    /* ─── Data extraction (GobHitbox.getBBox-style) ─── */

    /**
     * Resource accessor: handles both regular Drawable and SprDrawable.
     */
    static Resource drawableResource(Drawable d) {
	if(d == null)
	    return(null);
	Resource r = d.getres();
	if(r != null)
	    return(r);
	if(d instanceof SprDrawable) {
	    SprDrawable sd = (SprDrawable)d;
	    if(sd.spr != null && sd.spr.res != null)
		return(sd.spr.res);
	}
	return(null);
    }

    /**
     * Get hitbox polygons for a gob. Returns cached result if available.
     * GobHitbox.getBBox-style pipeline: consobj sdt → hardcoded animals → cache →
     * Obstacle.p → Neg.bs/bc → mesh AABB → null.
     */
    public static Coord2d[][] getBBox(Gob gob) {
	Resource res = null;
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    res = drawableResource(d);
	} catch(Loading e) {
	    return null;
	}
	if(res == null)
	    return null;
	String name = res.name;
	if(name == null)
	    return null;

	/*
	 * consobj: hit geometry is almost always in sdt (lib/obst). Must NOT use the global
	 * hitboxCache keyed only by res.name — a failed/empty sdt was caching [] and broke
	 * every later Plob / placement preview (reference impl calls getBBox per gob, no such cache).
	 */
	if(name.endsWith("/consobj"))
	    return getBBoxConsobj(gob, res);

	Coord2d[][] hardcoded = getHardcoded(name);
	if(hardcoded != null)
	    return hardcoded;

	boolean plob = gob instanceof MapView.Plob;
	Coord2d[][] cached;
	synchronized(hitboxCache) {
	    cached = hitboxCache.get(name);
	    if(!plob && cached != null) {
		if(cached.length > 0)
		    return cached;
		return null;
	    }
	}

	Coord2d[][] overlay = plob ? bboxFromTerobjOverlays(gob) : null;
	Coord2d[][] extracted = null;
	boolean extractedFresh = false;
	Coord2d[][] result = overlay;
	if(result == null) {
	    if(cached != null && cached.length > 0) {
		extracted = cached;
	    } else {
		extracted = extractFromResource(res);
		extractedFresh = true;
	    }
	    result = extracted;
	}
	if(result == null && !plob)
	    result = bboxFromTerobjOverlays(gob);
	if(result == null && plob)
	    result = defaultTerobjPlacementFootprint();

	synchronized(hitboxCache) {
	    if(extractedFresh && extracted != null) {
		hitboxCache.put(name, extracted);
	    } else if(!plob && result != null && result != extracted) {
		hitboxCache.remove(name);
	    } else if(!plob && extractedFresh) {
		hitboxCache.put(name, new Coord2d[0][]);
	    }
	}
	return result;
    }

    /**
     * Placement preview (lift / carry): server often sets a minimal {@link ResDrawable} and puts the
     * visible terobj mesh in {@link Gob.Overlay}s — same idea as extra ols in {@link MapView} place msg.
     */
    private static Coord2d[][] bboxFromTerobjOverlays(Gob gob) {
	if(gob == null || gob.ols == null || gob.ols.isEmpty())
	    return null;
	List<Gob.Overlay> olsSnap;
	synchronized(gob) {
	    olsSnap = new ArrayList<>(gob.ols);
	}
	for(Gob.Overlay ol : olsSnap) {
	    if(ol == null || ol.sm == null || !(ol.sm instanceof Sprite.Mill.FromRes))
		continue;
	    Sprite.Mill.FromRes fr = (Sprite.Mill.FromRes)ol.sm;
	    try {
		Resource r = fr.res.get();
		if(r == null || r.name == null || !r.name.startsWith("gfx/terobjs/"))
		    continue;
		byte[] raw = fr.sdt;
		Message sdt = (raw == null || raw.length == 0) ? Message.nil : new MessageBuf(raw);
		Coord2d[][] b = bboxForResourceAndSdt(r, sdt);
		if(b != null)
		    return b;
		b = extractFromResource(r);
		if(b != null)
		    return b;
		if(r.name != null && MoonTreeUtil.isLooseLogOrWoodBlock(r.name)) {
		    double h = MCache.tilesz.x * 0.48;
		    return new Coord2d[][] { new Coord2d[] {
			Coord2d.of(-h, -h), Coord2d.of(h, -h), Coord2d.of(h, h), Coord2d.of(-h, h)
		    }};
		}
	    } catch(Loading ignored) {
	    } catch(Exception ignored) {
	    }
	}
	return null;
    }

    /** consobj only: sdt-driven, then static layers, then mesh / default quad — never name-cache. */
    private static Coord2d[][] getBBoxConsobj(Gob gob, Resource res) {
	Message sdt = null;
	ResDrawable rd = gob.getattr(ResDrawable.class);
	if(rd != null)
	    sdt = rd.sdt;
	if(gob instanceof MapView.Plob) {
	    Coord2d[][] built = consobjBuiltBBox(rd);
	    if(built != null)
		return built;
	}
	Coord2d[][] dyn = bboxForResourceAndSdt(res, sdt);
	if(dyn != null)
	    return dyn;
	Coord2d[][] built = consobjBuiltBBox(rd);
	if(built != null)
	    return built;
	try {
	    Coord2d[][] ex = extractFromResource(res);
	    if(ex != null)
		return ex;
	    Coord2d[] mq = meshAabbQuad(res);
	    if(mq != null)
		return new Coord2d[][] { mq };
	} catch(Loading ignored) {}
	return defaultTerobjPlacementFootprint();
    }

    /** Last-resort footprint for placement / Plob when obst and resource layers give nothing. */
    private static Coord2d[][] defaultTerobjPlacementFootprint() {
	double h = MCache.tilesz.x * 0.5;
	return new Coord2d[][] { new Coord2d[] {
	    Coord2d.of(-h, -h), Coord2d.of(h, -h), Coord2d.of(h, h), Coord2d.of(-h, h)
	}};
    }

    /** Player is usually skipped; draw when carrying/placing as {@code gfx/terobjs/consobj}. */
    private static boolean plgobShowsConsobjHitbox(Gob gob) {
	if(gob == null)
	    return false;
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    if(!(d instanceof ResDrawable))
		return false;
	    Resource r = d.getres();
	    return r != null && r.name != null && r.name.endsWith("/consobj");
	} catch(Loading ignored) {
	    return false;
	}
    }

    /**
     * Static resource + optional sdt (e.g. equipment {@link ResData} while carrying).
     * {@code consobj} uses {@code sdt}; other terobjs use resource layers / fallbacks.
     */
    static Coord2d[][] bboxForResourceAndSdt(Resource res, Message sdt) {
	if(res == null)
	    return null;
	String name = res.name;
	if(name != null && name.endsWith("/consobj")) {
	    if(sdt == null)
		return null;
	    MessageBuf mbuf = new MessageBuf(sdt);
	    if(mbuf.rem() < 1)
		return null;
	    try {
		haven.res.lib.obst.Obstacle obst = haven.res.lib.obst.Obstacle.parse(mbuf);
		if(obst == null || obst.p == null)
		    return null;
		for(Coord2d[] poly : obst.p) {
		    if(poly != null && poly.length >= 2)
			return obst.p;
		}
		return null;
	    } catch(Message.FormatError | Loading ignored) {
		return null;
	    }
	}
	return extractFromResource(res);
    }

    /**
     * Hitboxes for {@code gfx/terobjs} on player {@link Composite} (lift / carry).
     * Package-visible so {@link MoonEntityHitboxViz} can reuse the same outlines.
     */
    static void drawPlayerCarriedTerobjHitboxes(GOut g, MapView mv) {
	if(mv.plgob < 0)
	    return;
	Gob pl = mv.player();
	if(pl == null)
	    return;
	Composite cmp = pl.getattr(Composite.class);
	if(cmp == null)
	    return;
	Collection<Composited.Equipped> eqs;
	try {
	    eqs = cmp.comp.equ;
	    if(eqs == null || eqs.isEmpty())
		return;
	} catch(Loading e) {
	    return;
	}
	Gob.Placer placer = pl.placer();
	if(placer == null)
	    return;
	Matrix4f Mroot;
	try {
	    Coord3f tc = placer.getc(pl.rc, pl.a);
	    Matrix4f Mr = placer.getr(pl.rc, pl.a);
	    Mroot = Transform.makexlate(new Matrix4f(), tc);
	    Mroot.mul1(Mr);
	} catch(Loading e) {
	    return;
	}
	g.chcolor(COL_DEFAULT);
	for(Composited.Equipped eq : eqs) {
	    if(eq == null || eq.desc == null || eq.desc.res == null)
		continue;
	    Composited.ED ed = eq.desc;
	    Resource er;
	    try {
		er = ed.res.res.get();
	    } catch(Loading e) {
		continue;
	    }
	    if(er == null || er.name == null)
		continue;
	    String nm = er.name;
	    if(!nm.startsWith("gfx/terobjs/"))
		continue;
	    if(nm.startsWith("gfx/terobjs/items/"))
		continue;

	    Coord2d[][] bbox;
	    try {
		bbox = bboxForResourceAndSdt(er, ed.res.sdt);
	    } catch(Loading e) {
		continue;
	    }
	    if(bbox == null)
		continue;

	    Matrix4f Mb;
	    try {
		if(ed.at == null || ed.at.isEmpty()) {
		    Mb = Matrix4f.id;
		} else {
		    Skeleton.Bone bone = cmp.comp.skel.bones.get(ed.at);
		    if(bone == null)
			continue;
		    Pipe.Op op = cmp.comp.pose.bonetrans(bone.idx).get();
		    if(!(op instanceof Transform))
			continue;
		    Mb = ((Transform)op).fin(Matrix4f.id);
		}
	    } catch(Loading e) {
		continue;
	    } catch(RuntimeException e) {
		continue;
	    }
	    Matrix4f Mw = new Matrix4f(Mroot).mul1(Mb);
	    if(!ed.off.equals(Coord3f.o))
		Mw.mul1(Transform.makexlate(new Matrix4f(), ed.off));

	    for(Coord2d[] poly : bbox) {
		if(poly == null || poly.length < 2)
		    continue;
		drawWorldPolyFromMatrix(g, mv, poly, Mw);
	    }
	}
    }

    private static void drawWorldPolyFromMatrix(GOut g, MapView mv, Coord2d[] poly, Matrix4f Mw) {
	if(poly == null || poly.length < 2)
	    return;
	Coord prev = null, first = null;
	for(Coord2d p : poly) {
	    if(p == null)
		continue;
	    Coord3f wp = Mw.mul4(Coord3f.of((float)p.x, (float)p.y, 0f));
	    Coord2d rv = Coord2d.of(wp.x, wp.y);
	    Coord3f wz;
	    try {
		wz = mv.glob.map.getzp(rv);
	    } catch(Exception e) {
		continue;
	    }
	    Coord3f sc = mv.screenxf(wz);
	    if(sc == null || !Float.isFinite(sc.x) || !Float.isFinite(sc.y))
		continue;
	    Coord cur = Coord.of(Math.round(sc.x), Math.round(sc.y));
	    if(first == null)
		first = cur;
	    if(prev != null)
		g.line(prev, cur, 1.5);
	    prev = cur;
	}
	if(prev != null && first != null && !prev.equals(first))
	    g.line(prev, first, 1.5);
    }

    private static Coord2d[][] getHardcoded(String name) {
	if(name.equals("gfx/kritter/cattle/calf"))
	    return BBOX_CALF;
	if(name.equals("gfx/kritter/sheep/lamb"))
	    return BBOX_LAMB;
	if(name.startsWith("gfx/kritter/horse/"))
	    return BBOX_HORSE;
	if(name.startsWith("gfx/kritter/goat/"))
	    return BBOX_GOAT;
	if(name.startsWith("gfx/kritter/pig/"))
	    return BBOX_PIG;
	if(name.startsWith("gfx/terobjs/boostspeed"))
	    return BBOX_BOOST;
	if(name.matches("gfx/kritter/reindeer/teimdeer(bull|cow)"))
	    return BBOX_TDEER;
	return null;
    }

    /**
     * Extract hitbox polygons from a resource and its RenderLink chain.
     * Priority: Obstacle.p → Neg bounding box (bs/bc) → RenderLink → mesh AABB → log tile.
     */
    private static Coord2d[][] extractFromResource(Resource res) {
	List<Coord2d[]> hitlist = new ArrayList<>();
	collectHitData(res, Collections.newSetFromMap(new IdentityHashMap<>()), hitlist);

	if(!hitlist.isEmpty())
	    return hitlist.toArray(new Coord2d[0][]);

	Coord2d[] meshQuad = meshAabbQuad(res);
	if(meshQuad != null)
	    return new Coord2d[][] { meshQuad };

	if(res.name != null && MoonTreeUtil.isLooseLogOrWoodBlock(res.name)) {
	    double h = MCache.tilesz.x * 0.48;
	    return new Coord2d[][] { new Coord2d[] {
		Coord2d.of(-h, -h), Coord2d.of(h, -h), Coord2d.of(h, h), Coord2d.of(-h, h)
	    }};
	}

	return null;
    }

    private static Coord2d[][] consobjBuiltBBox(ResDrawable rd) {
	if(rd == null || rd.spr == null)
	    return null;
	try {
	    Field builtf = rd.spr.getClass().getField("built");
	    Object raw = builtf.get(rd.spr);
	    if(!(raw instanceof ResData))
		return null;
	    ResData built = (ResData) raw;
	    if(built == null || built.res == null)
		return null;
	    Resource br = built.res.get();
	    if(br == null)
		return null;
	    Coord2d[][] bbox = bboxForResourceAndSdt(br, built.sdt);
	    if(bbox != null)
		return bbox;
	    return extractFromResource(br);
	} catch(NoSuchFieldException ignored) {
	    return null;
	} catch(Loading ignored) {
	    return null;
	} catch(IllegalAccessException ignored) {
	    return null;
	}
    }

    private static void collectHitData(Resource res, Set<Resource> seen, List<Coord2d[]> hitlist) {
	if(res == null || !seen.add(res))
	    return;
	collectObstacles(res, hitlist);
	collectNegBounds(res, hitlist);
	try {
	    for(RenderLink.Res lr : res.layers(RenderLink.Res.class)) {
		if(lr == null || lr.l == null)
		    continue;
		collectFromRenderLink(lr.l, seen, hitlist);
	    }
	} catch(Loading ignored) {}
    }

    /**
     * Use Neg.bs and Neg.bc as bounding box corners (NOT Neg.ep which are entrance polygons).
     */
    private static void collectNegBounds(Resource res, List<Coord2d[]> hitlist) {
	try {
	    Collection<Resource.Neg> negs = res.layers(Resource.Neg.class);
	    if(negs == null)
		return;
	    for(Resource.Neg neg : negs) {
		if(neg == null || neg.bs == null || neg.bc == null)
		    continue;
		Coord bs = neg.bs, bc = neg.bc;
		if(bs.x == 0 && bs.y == 0 && bc.x == 0 && bc.y == 0)
		    continue;
		hitlist.add(new Coord2d[] {
		    Coord2d.of(bs.x, bs.y), Coord2d.of(bc.x, bs.y),
		    Coord2d.of(bc.x, bc.y), Coord2d.of(bs.x, bc.y)
		});
	    }
	} catch(Loading ignored) {}
    }

    /**
     * Union AABB of all mesh layers → ground-plane quad. Used as fallback
     * for objects without obstacle/neg data (racks, logs, etc.).
     */
    private static Coord2d[] meshAabbQuad(Resource res) {
	float[] bb = meshBounds(res);
	if(bb == null)
	    return null;
	float nx = bb[0], ny = bb[1], px = bb[2], py = bb[3];
	boolean any = true;
	if(!any || !Float.isFinite(nx) || nx >= px || ny >= py)
	    return null;
	float sx = px - nx, sy = py - ny;
	double minHalf = MCache.tilesz.x * 0.36;
	double hx = Math.max(sx * 0.5, minHalf);
	double hy = Math.max(sy * 0.5, minHalf);
	double cx = (nx + px) * 0.5, cy = (ny + py) * 0.5;
	return new Coord2d[] {
	    Coord2d.of(cx - hx, cy - hy), Coord2d.of(cx + hx, cy - hy),
	    Coord2d.of(cx + hx, cy + hy), Coord2d.of(cx - hx, cy + hy)
	};
    }

    private static float[] meshBounds(Resource res) {
	float[] bb = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };
	try {
	    if(!collectMeshBounds(res, Collections.newSetFromMap(new IdentityHashMap<>()), null, null, bb))
		return null;
	    return bb;
	} catch(Loading e) {
	    return null;
	}
    }

    private static boolean collectMeshBounds(Resource res, Set<Resource> seen, Integer meshId, RenderLink.Collect collect, float[] bb) {
	if(res == null || !seen.add(res))
	    return false;
	boolean any = false;
	for(FastMesh.MeshRes mr : res.layers(FastMesh.MeshRes.class)) {
	    if(mr == null)
		continue;
	    if(meshId != null && meshId >= 0 && mr.id != meshId)
		continue;
	    if(collect != null) {
		if(mr.id < 0 || ((mr.id & collect.meshmask) != collect.meshid))
		    continue;
	    }
	    FastMesh fm = mr.m;
	    if(fm == null)
		continue;
	    Coord3f nb = fm.nbounds(), pb = fm.pbounds();
	    bb[0] = Math.min(bb[0], Math.min(nb.x, pb.x));
	    bb[1] = Math.min(bb[1], Math.min(nb.y, pb.y));
	    bb[2] = Math.max(bb[2], Math.max(nb.x, pb.x));
	    bb[3] = Math.max(bb[3], Math.max(nb.y, pb.y));
	    any = true;
	}
	for(RenderLink.Res lr : res.layers(RenderLink.Res.class)) {
	    if(lr == null || lr.l == null)
		continue;
	    any = collectMeshBounds(lr.l, seen, bb) || any;
	}
	return any;
    }

    private static boolean collectMeshBounds(RenderLink link, Set<Resource> seen, float[] bb) {
	try {
	    if(link instanceof RenderLink.MeshMat) {
		RenderLink.MeshMat mm = (RenderLink.MeshMat)link;
		return collectMeshBounds(mm.mesh.get(), seen, mm.meshid, null, bb);
	    } else if(link instanceof RenderLink.Collect) {
		RenderLink.Collect cl = (RenderLink.Collect)link;
		return collectMeshBounds(cl.from.get(), seen, null, cl, bb);
	    } else if(link instanceof RenderLink.Parameters) {
		RenderLink.Parameters pl = (RenderLink.Parameters)link;
		return collectMeshBounds(pl.res.get(), seen, null, null, bb);
	    } else if(link instanceof RenderLink.ResSprite) {
		RenderLink.ResSprite rs = (RenderLink.ResSprite)link;
		return collectMeshBounds(rs.res.get(), seen, null, null, bb);
	    }
	} catch(Loading ignored) {
	}
	return false;
    }

    private static void collectObstacles(Resource res, List<Coord2d[]> hitlist) {
	try {
	    Collection<Resource.Obstacle> obs = res.layers(Resource.obst);
	    if(obs == null)
		return;
	    for(Resource.Obstacle ob : obs) {
		if(ob == null || ob.p == null)
		    continue;
		for(Coord2d[] poly : ob.p) {
		    if(poly != null && poly.length >= 2)
			hitlist.add(poly);
		}
	    }
	} catch(Loading ignored) {}
    }

    private static void collectFromRenderLink(RenderLink l, Set<Resource> seen, List<Coord2d[]> hitlist) {
	try {
	    if(l instanceof RenderLink.MeshMat) {
		RenderLink.MeshMat mm = (RenderLink.MeshMat)l;
		collectFromIndir(mm.mesh, seen, hitlist);
	    } else if(l instanceof RenderLink.AmbientLink) {
		RenderLink.AmbientLink al = (RenderLink.AmbientLink)l;
		collectFromIndir(al.res, seen, hitlist);
	    } else if(l instanceof RenderLink.Collect) {
		RenderLink.Collect cl = (RenderLink.Collect)l;
		collectFromIndir(cl.from, seen, hitlist);
	    } else if(l instanceof RenderLink.Parameters) {
		RenderLink.Parameters pl = (RenderLink.Parameters)l;
		collectFromIndir(pl.res, seen, hitlist);
	    } else if(l instanceof RenderLink.ResSprite) {
		RenderLink.ResSprite rs = (RenderLink.ResSprite)l;
		collectFromIndir(rs.res, seen, hitlist);
	    }
	} catch(Loading ignored) {}
    }

    private static void collectFromIndir(Indir<Resource> ires, Set<Resource> seen, List<Coord2d[]> hitlist) {
	if(ires == null)
	    return;
	try {
	    Resource r = ires.get();
	    if(r == null)
		return;
	    collectHitData(r, seen, hitlist);
	} catch(Loading ignored) {}
    }

    /* ─── Drawing ─── */

    /**
     * World-outline hitbox like global Shift+B mode ({@link #drawPoly} / {@code screenxf}).
     * Used for placement {@link MapView.Plob}: entity footprint mode uses a strict z-clip that often drops previews.
     */
    public static void drawOneGobHitboxWorldOutline(GOut g, MapView mv, Gob gob) {
	if(gob == null || mv == null)
	    return;
	try {
	    drawGobHitboxFallback(g, mv, gob);
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
    }

    private static void drawGobHitboxFallback(GOut g, MapView mv, Gob gob) {
	Coord2d[][] bbox = null;
	try {
	    bbox = getBBox(gob);
	} catch(Loading ignored) {
	    return;
	}
	if(bbox == null)
	    return;

	double a = gob.a;
	double sa = Math.sin(a), ca = Math.cos(a);
	g.chcolor(COL_DEFAULT);
	for(Coord2d[] poly : bbox) {
	    if(poly == null || poly.length < 2)
		continue;
	    drawPoly(g, mv, gob, poly, sa, ca);
	}
    }

    private static void drawPoly(GOut g, MapView mv, Gob gob, Coord2d[] poly,
				  double sa, double ca) {
	if(poly == null || poly.length < 2)
	    return;
	Coord3f anchor = null;
	if(gob instanceof MapView.Plob) {
	    try {
		anchor = gob.getc();
	    } catch(Loading e) {
		return;
	    }
	}
	Coord prev = null, first = null;
	Coord2d firstW = null, prevW = null;
	for(Coord2d p : poly) {
	    if(p == null)
		continue;
	    if(!Double.isFinite(p.x) || !Double.isFinite(p.y))
		continue;
	    Coord2d rv = Coord2d.of((p.x * ca) - (p.y * sa), (p.y * ca) + (p.x * sa)).add(gob.rc);
	    Coord3f wz;
	    if(anchor != null)
		wz = Coord3f.of((float)rv.x, (float)rv.y, anchor.z);
	    else {
		try {
		    wz = mv.glob.map.getzp(rv);
		} catch(Exception e) {
		    continue;
		}
	    }
	    Coord3f sc = hitboxScreenProject(mv, wz);
	    if(sc == null)
		continue;
	    Coord cur = Coord.of(Math.round(sc.x), Math.round(sc.y));
	    if(first == null) {
		first = cur;
		firstW = rv;
	    }
	    if(prev != null && hitboxEdgeOk(prev, cur, prevW, rv))
		g.line(prev, cur, 1.5);
	    prev = cur;
	    prevW = rv;
	}
	if(prev != null && first != null && !prev.equals(first)
	    && firstW != null && prevW != null && hitboxEdgeOk(prev, first, prevW, firstW))
	    g.line(prev, first, 1.5);
    }

    /** Quad from footprint — kept for {@link MoonEntityHitboxViz} fallback only. */
    static Coord2d[] meshFootprintQuad(Resource res) {
	return null;
    }

    /* ─── Hide/Show for mode 2 ─── */

    private static void hideAllButPlayer(MapView mv) {
	List<Gob> toHide = new ArrayList<>();
	try {
	    synchronized(mv.glob.oc) {
		for(Gob gob : mv.glob.oc) {
		    if(gob == null || !wantsHidden(gob, mv.plgob))
			continue;
		    if(mv.isHiddenBy(gob, MapView.HideReason.GLOBAL_HITBOX))
			continue;
		    toHide.add(gob);
		}
	    }
	} catch(Exception ignored) {
	    return;
	}
	for(Gob gob : toHide) {
	    try {
		mv.hideGob(gob, MapView.HideReason.GLOBAL_HITBOX);
	    } catch(Exception ignored) {
	    }
	}
    }

    private static void restoreHidden(MapView mv) {
	for(Gob gob : mv.hiddenGobs(MapView.HideReason.GLOBAL_HITBOX)) {
	    if(gob != null)
		try { mv.showGob(gob, MapView.HideReason.GLOBAL_HITBOX); } catch(Exception ignored) {}
	}
    }
}
