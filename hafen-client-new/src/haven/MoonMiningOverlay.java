package haven;

import haven.render.Model;
import haven.render.BaseColor;
import haven.render.States;
import haven.render.MixColor;
import haven.render.Pipe;
import haven.resutil.CaveTile;
import haven.resutil.CrackTex;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlights map tiles covered by mine supports using the same geometric idea as in-game rules:
 * a disc in the ground plane centered on the <b>map tile</b> that holds the prop (not the mesh's
 * offset {@link Gob#rc}), radius from runtime overlay / resource props / trusted known tiers. A tile is
 * supported only if the exact center of the tile lies inside the support definition.
 * Props with {@link GobHealth} at zero are skipped (collapsed); lightly damaged supports still contribute.
 */
public final class MoonMiningOverlay {
    private MoonMiningOverlay() {}

    /** Hard cap after distance sort (nearest first). Raised: large cave grids used to skip random far supports at 64. */
    private static final int MAX_SUPPORTS = 384;
    /** Guard against projection glitches producing giant quads on screen. */
    private static final int SCREEN_MARGIN = 4096;
    private static final int VIEW_RADIUS = 34;
    private static final int CACHE_MOVE_EPS = 2;
    private static final double CACHE_TTL_SEC = 0.30;
    private static final int FILL_TILE_CAP = Integer.MAX_VALUE;

    private static final int[][] ORTH = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Coord2d[] CORNER_OFFSETS = {
	Coord2d.of(0, 0), Coord2d.of(1, 0), Coord2d.of(1, 1), Coord2d.of(0, 1),
    };

    private static Cache cache = null;
    private static MCache overlayMap = null;
    private static long overlaySig = Long.MIN_VALUE;
    private static List<MCache.Overlay> liveSafeOverlays = Collections.emptyList();
    private static List<MCache.Overlay> liveBrokenOverlays = Collections.emptyList();
    private static final Map<String, Tex> supportTextCache = new HashMap<>();
    private static int supportTextCacheSize = -1;
    private static final double SUPPORT_HP_CACHE_TTL_SEC = 0.25;
    private static Glob supportHpGlob = null;
    private static Coord supportHpPlayerTile = null;
    private static double supportHpBuiltAt = -1;
    private static List<SupportHpMark> supportHpMarks = Collections.emptyList();

    private static final class Segment {
	final Coord a;
	final Coord b;

	Segment(Coord a, Coord b) {
	    this.a = a;
	    this.b = b;
	}
    }

    private static final class FillStrip {
	final int y;
	final int x0;
	final int x1;

	FillStrip(int y, int x0, int x1) {
	    this.y = y;
	    this.x0 = x0;
	    this.x1 = x1;
	}
    }

    private static final class FillRect {
	final int x0;
	final int y0;
	final int x1;
	final int y1;

	FillRect(int x0, int y0, int x1, int y1) {
	    this.x0 = x0;
	    this.y0 = y0;
	    this.x1 = x1;
	    this.y1 = y1;
	}
    }

    private static final class SupportHpMark {
	final Coord2d rc;
	final int pct;

	SupportHpMark(Coord2d rc, int pct) {
	    this.rc = rc;
	    this.pct = pct;
	}
    }

    private static final class SupportSpec {
	final Coord tile;
	final Coord2d center;
	final double radiusTiles;

	SupportSpec(Coord tile, Coord2d center, double radiusTiles) {
	    this.tile = tile;
	    this.center = center;
	    this.radiusTiles = radiusTiles;
	}
    }

    private static final class ContactLine {
	final Coord2d a0;
	final Coord2d a1;
	final Coord2d b0;
	final Coord2d b1;

	ContactLine(Coord2d a0, Coord2d a1, Coord2d b0, Coord2d b1) {
	    this.a0 = a0;
	    this.a1 = a1;
	    this.b0 = b0;
	    this.b1 = b1;
	}
    }

    private static final class Cache {
	final Glob glob;
	final Coord playerTile;
	final Area view;
	final Set<Coord> covered;
	final Set<Coord> broken;
	final List<FillStrip> fillStrips;
	final List<FillRect> fillRects;
	final List<FillRect> brokenFillRects;
	final List<Segment> edges;
	final List<ContactLine> contacts;
	final long supportSig;
	final double builtAt;

	Cache(Glob glob, Coord playerTile, Area view, Set<Coord> covered, Set<Coord> broken, List<FillStrip> fillStrips, List<FillRect> fillRects, List<FillRect> brokenFillRects, List<Segment> edges, List<ContactLine> contacts, long supportSig, double builtAt) {
	    this.glob = glob;
	    this.playerTile = playerTile;
	    this.view = view;
	    this.covered = covered;
	    this.broken = broken;
	    this.fillStrips = fillStrips;
	    this.fillRects = fillRects;
	    this.brokenFillRects = brokenFillRects;
	    this.edges = edges;
	    this.contacts = contacts;
	    this.supportSig = supportSig;
	    this.builtAt = builtAt;
	}
    }

    private static final MCache.OverlayInfo SAFE_OVERLAY = new MCache.OverlayInfo() {
	public Collection<String> tags() {
	    return(Arrays.asList("show"));
	}

	public Material mat() {
	    Color c = MoonConfig.mineSupportFillColor();
	    return(new Material(new BaseColor(c), States.maskdepth, new States.DepthBias(-1, -1), new MapMesh.OLOrder(this)));
	}

	public Material omat() {
	    Color c = MoonConfig.mineSupportOutlineColor();
	    return(new Material(new BaseColor(c), States.maskdepth, new States.DepthBias(-1, -1), new MapMesh.OLOrder(this)));
	}
    };

    private static final MCache.OverlayInfo BROKEN_OVERLAY = new MCache.OverlayInfo() {
	private final Pipe.Op crack = Pipe.Op.compose(
	    new CrackTex(CrackTex.imgs[2], new Color(20, 10, 10, 255), Coord3f.of(0.41f, 0.77f, 0.49f).norm(), 1.17f),
	    new MixColor(180, 60, 40, 28));

	public Collection<String> tags() {
	    return(Arrays.asList("show"));
	}

	public Material mat() {
	    return(new Material(new BaseColor(new Color(158, 96, 82, 52)), crack, States.maskdepth,
		new States.DepthBias(-1, -1), new MapMesh.OLOrder(this)));
	}

	public Material omat() {
	    return(new Material(new BaseColor(new Color(190, 92, 70, 130)), States.maskdepth,
		new States.DepthBias(-1, -1), new MapMesh.OLOrder(this)));
	}
    };

    /** Longest match wins. Radius in <i>tile</i> units (same as server “tiles from center”). */
    private static final List<String[]> RADIUS_RULES = new ArrayList<>();
    static {
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/monumentalcolumn", "30"});
	RADIUS_RULES.add(new String[] {"monumentalcolumn", "30"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/minebeam", "13"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/minebeam", "13"});
	RADIUS_RULES.add(new String[] {"minesupportbeam", "13"});
	RADIUS_RULES.add(new String[] {"minesupbeam", "13"});
	RADIUS_RULES.add(new String[] {"minebeam", "13"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/stoneminesupport", "11"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/ironminesupport", "11"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/steelminesupport", "11"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/stonecolumn", "11"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/minecolumn", "11"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/column", "11"});
	RADIUS_RULES.add(new String[] {"stonecolumn", "11"});
	RADIUS_RULES.add(new String[] {"minecolumn", "11"});
	RADIUS_RULES.add(new String[] {"cavecolumn", "11"});
	RADIUS_RULES.add(new String[] {"tunnelcolumn", "11"});
	RADIUS_RULES.add(new String[] {"stonepillar", "11"});
	RADIUS_RULES.add(new String[] {"minepillar", "11"});
	RADIUS_RULES.add(new String[] {"supportpillar", "11"});
	RADIUS_RULES.add(new String[] {"marblecolumn", "11"});
	RADIUS_RULES.add(new String[] {"granitecolumn", "11"});
	RADIUS_RULES.add(new String[] {"stoneminesupport", "11"});
	RADIUS_RULES.add(new String[] {"ironminesupport", "11"});
	RADIUS_RULES.add(new String[] {"steelminesupport", "11"});
	RADIUS_RULES.add(new String[] {"brassminesupport", "11"});
	RADIUS_RULES.add(new String[] {"cupronminesupport", "11"});
	RADIUS_RULES.add(new String[] {"wroughtminesupport", "11"});
	RADIUS_RULES.add(new String[] {"metalminesupport", "11"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/minesuptower", "9"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/minebrace", "9"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/arch/minesupport", "9"});
	RADIUS_RULES.add(new String[] {"gfx/terobjs/minesupport", "9"});
	RADIUS_RULES.add(new String[] {"minesupport", "9"});
	RADIUS_RULES.add(new String[] {"minesup", "9"});
	RADIUS_RULES.add(new String[] {"minebrace", "9"});
	RADIUS_RULES.add(new String[] {"mine-sup", "9"});
	RADIUS_RULES.add(new String[] {"mineshoring", "9"});
	RADIUS_RULES.add(new String[] {"mine-shoring", "9"});
	RADIUS_RULES.add(new String[] {"roofsupport", "9"});
	RADIUS_RULES.add(new String[] {"timbersupport", "9"});
	RADIUS_RULES.add(new String[] {"tunnecolumn", "11"});
	RADIUS_RULES.add(new String[] {"tunnpillar", "11"});
	Collections.sort(RADIUS_RULES, Comparator.comparing(a -> -a[0].length()));
    }

    /** Declarative radius in resource props (any positive numeric value). */
    private static final String[] SUPPORT_RADIUS_PROP_KEYS = {
	"suprad", "msrad", "covrad", "minesuprad", "supportrad", "supportradius", "msupportrad",
    };

    private static final Pattern TIP_RADIUS = Pattern.compile(
        "(?i)(radius|range|\\br\\b)\\s*[:=]?\\s*([0-9]+(?:\\.[0-9]+)?)");

    /**
     * {@link ResDrawable} exposes {@link Resource} via {@link Drawable#getres()}; {@link SprDrawable} does not
     * (getres is null) even though {@link Sprite#res} holds the path — stone/metal supports often use
     * {@link SprDrawable}, which previously made the overlay skip them.
     */
    private static Resource gobStructureResource(Gob gob) {
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    if(d == null)
		return(null);
	    if(d instanceof ResDrawable)
		return(((ResDrawable)d).getres());
	    if(d instanceof SprDrawable) {
		Sprite spr = ((SprDrawable)d).spr;
		if(spr != null && spr.res != null)
		    return(spr.res);
	    }
	    return(d.getres());
	} catch(Loading ignored) {
	    return(null);
	}
    }

    private static String gobStructureResName(Gob gob) {
	Resource r = gobStructureResource(gob);
	return(r == null) ? null : r.name;
    }

    /** World position of the supported tile’s center (server grid); avoids false rings when the model sits off-center. */
    private static Coord2d supportDiscCenterWorld(Gob gob, double tw, double th) {
	Coord tc = gob.rc.floor(MCache.tilesz);
	return new Coord2d((tc.x + 0.5) * tw, (tc.y + 0.5) * th);
    }

    /**
     * Fully collapsed supports (hp == 0) do not provide coverage. Slightly damaged props still draw so
     * the overlay matches “standing” supports; only {@link GobHealth} at zero is treated as gone.
     */
    static boolean supportIsStructurallySound(Gob gob) {
	GobHealth gh = gob.getattr(GobHealth.class);
	if(gh == null)
	    return true;
	return gh.hp > 1e-3f;
    }

    /** Official rule: a tile is supported only if the exact center of the tile lies inside the support. */
    private static boolean tileCenterInsideDisc(Coord2d discCenter, double rWorld, Coord tile, double tw, double th) {
	double px = (tile.x + 0.5) * tw;
	double py = (tile.y + 0.5) * th;
	return(discCenter.dist(new Coord2d(px, py)) <= rWorld + 1e-6);
    }

    /**
     * Map tiles whose centers fall inside a standing mine-support coverage disc (same rules as the overlay).
     * Callers may cache one set per tick; safe for {@link MapView} with non-null {@code glob.map}.
     */
    public static Set<Coord> computeSupportedTileSet(MapView mv, Gob pl) {
	return(computeSupportedTileSet(mv, pl, collectSupportSpecs(mv, pl, true)));
    }

    private static Set<Coord> computeSupportedTileSet(MapView mv, Gob pl, List<SupportSpec> supports) {
	Set<Coord> covered = new HashSet<>();
	if(pl == null || mv == null || mv.glob == null || mv.glob.map == null || supports == null)
	    return(covered);
	double tw = MCache.tilesz.x, th = MCache.tilesz.y;
	try {
	    for(SupportSpec support : supports) {
		double rWorld = support.radiusTiles * (Math.min(tw, th));
		int span = (int)Math.ceil(support.radiusTiles) + 2;
		Coord bc = support.tile;
		for(int dy = -span; dy <= span; dy++) {
		    for(int dx = -span; dx <= span; dx++) {
			Coord tc = bc.add(dx, dy);
			if(!isSupportDisplayTile(mv.glob.map, tc))
			    continue;
			if(tileCenterInsideDisc(support.center, rWorld, tc, tw, th))
			    covered.add(tc);
		    }
		}
	    }
	} catch(Exception ignored) {
	}
	return(covered);
    }

    /** Cave / mine floor tile heuristic for “collapse risk” tint and mine-bot targeting. */
    public static boolean isCaveMineFloorTile(MCache map, Coord tc) {
	try {
	    int tid = map.gettile(tc);
	    Resource res = map.tilesetr(tid);
	    if(res == null || res.name == null)
		return(false);
	    String n = res.name.toLowerCase(Locale.ROOT);
	    return n.contains("cave") || n.contains("mine") || n.contains("cellar")
		|| n.contains("granite") || n.contains("gneiss") || n.contains("schist")
		|| n.contains("dolomite") || n.contains("basalt");
	} catch(Loading e) {
	    return(false);
	}
    }

    /** Diggable cave wall / mine wall tile used for sapper marks on unmined cells. */
    public static boolean isCaveMineWallTile(MCache map, Coord tc) {
	try {
	    int tid = map.gettile(tc);
	    if(tid < 0)
		return(false);
	    Tiler tiler = map.tiler(tid);
	    Resource res = map.tilesetr(tid);
	    if(res == null || res.name == null)
		return(false);
	    String n = res.name.toLowerCase(Locale.ROOT);
	    if(n.contains("/nil") || n.contains("water") || n.contains("deep"))
		return(false);
	    if(tiler instanceof CaveTile)
		return(true);
	    if((n.contains("cave") || n.contains("mine") || n.contains("cellar"))
		&& (n.contains("wall") || n.contains("rock") || n.contains("face")))
		return(true);
	    return(false);
	} catch(Loading e) {
	    return(false);
	}
    }

    static boolean isSupportDisplayTile(MCache map, Coord tc) {
	try {
	    int tid = map.gettile(tc);
	    if(tid < 0)
		return(false);
	    Tiler tiler = map.tiler(tid);
	    Resource res = map.tilesetr(tid);
	    if(res == null || res.name == null)
		return(false);
	    String n = res.name.toLowerCase(Locale.ROOT);
	    if(tiler instanceof CaveTile)
		return(true);
	    if(n.contains("/nil") || n.contains("deep") || n.contains("water"))
		return(false);
	    if(n.contains("cave") || n.contains("mine") || n.contains("cellar"))
		return(true);
	    return(!n.contains("/nil"));
	} catch(Loading e) {
	    return(false);
	}
    }

    public static void draw(GOut g, MapView mv) {
	boolean showSafe = MoonConfig.mineSupportSafeTiles;
	boolean showRisk = false;
	if(!showSafe && !showRisk) {
	    clearWorldOverlay();
	    return;
	}
	Gob pl;
	try {
	    pl = mv.player();
	} catch(Loading e) {
	    return;
	}
	if(pl == null || mv.glob == null || mv.glob.map == null)
	    return;
	double tw = MCache.tilesz.x, th = MCache.tilesz.y;
	Coord ptc = pl.rc.floor(MCache.tilesz);
	Cache snap = snapshot(mv, pl);
	if(snap == null) {
	    clearWorldOverlay();
	    return;
	}

	syncWorldOverlay(mv.glob.map, snap.fillRects, snap.brokenFillRects, snap.supportSig);

	Color edgeCol = MoonConfig.mineSupportOutlineColor();
	float edgeW = MoonConfig.mineSupportOutlineWidth;
	boolean edgeOn = edgeCol.getAlpha() > 0 && edgeW > 0;

	if(showSafe) {
	    if(edgeOn) {
		Color contactCol = new Color(edgeCol.getRed(), edgeCol.getGreen(), edgeCol.getBlue(),
		    Math.max(22, edgeCol.getAlpha() / 4));
		g.chcolor(contactCol);
		for(ContactLine cl : snap.contacts) {
		    Coord[] ptsA = projectWorldLine(mv, cl.a0, cl.a1);
		    Coord[] ptsB = projectWorldLine(mv, cl.b0, cl.b1);
		    if(ptsA != null)
			g.line(ptsA[0], ptsA[1], Math.max(1f, edgeW * 0.6f));
		    if(ptsB != null)
			g.line(ptsB[0], ptsB[1], Math.max(1f, edgeW * 0.6f));
		    if(ptsA == null && ptsB == null)
			continue;
		}
		g.chcolor();
	    }
	}

	if(MoonConfig.mineSupportShowHp)
	    drawSupportHp(g, mv, pl);

	if(showRisk) {
	}
    }

    private static void drawSupportHp(GOut g, MapView mv, Gob pl) {
	if(mv == null || mv.glob == null || pl == null)
	    return;
	try {
	    for(SupportHpMark mark : supportHpSnapshot(mv, pl)) {
		Coord3f sc = mv.screenxf(mv.glob.map.getzp(mark.rc).add(0, 0, 14));
		if(sc == null || !Float.isFinite(sc.x) || !Float.isFinite(sc.y))
		    continue;
		Coord c = Coord.of(Math.round(sc.x), Math.round(sc.y));
		if(c.x < -32 || c.y < -32 || c.x > mv.sz.x + 32 || c.y > mv.sz.y + 32)
		    continue;
		g.aimage(supportText(mark.pct), c, 0.5, 1.0);
	    }
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
    }

    public static void tickSupportHpCache(MapView mv) {
	if(mv == null || mv.glob == null || !MoonConfig.gfxModMiningOverlay || !MoonConfig.mineSupportShowHp) {
	    supportHpMarks = Collections.emptyList();
	    supportHpGlob = null;
	    supportHpPlayerTile = null;
	    supportHpBuiltAt = -1;
	    return;
	}
	try {
	    Gob pl = mv.player();
	    if(pl == null)
		return;
	    Coord ptc = pl.rc.floor(MCache.tilesz);
	    double now = Utils.rtime();
	    if((supportHpGlob == mv.glob) && (supportHpPlayerTile != null) &&
	       (Math.abs(supportHpPlayerTile.x - ptc.x) <= CACHE_MOVE_EPS) &&
	       (Math.abs(supportHpPlayerTile.y - ptc.y) <= CACHE_MOVE_EPS) &&
	       ((now - supportHpBuiltAt) <= SUPPORT_HP_CACHE_TTL_SEC))
		return;
	    List<SupportHpMark> marks = new ArrayList<>();
	    synchronized(mv.glob.oc) {
		for(Gob gob : mv.glob.oc) {
		    if(gob == null || !isMineSupportGob(gob))
			continue;
		    if(pl.rc.dist(gob.rc) > (MCache.tilesz.x * 80))
			continue;
		    GobHealth gh = gob.getattr(GobHealth.class);
		    if(gh == null)
			continue;
		    marks.add(new SupportHpMark(gob.rc, Utils.clip(Math.round(gh.hp * 100f), 0, 100)));
		}
	    }
	    supportHpGlob = mv.glob;
	    supportHpPlayerTile = ptc;
	    supportHpBuiltAt = now;
	    supportHpMarks = marks;
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
    }

    private static List<SupportHpMark> supportHpSnapshot(MapView mv, Gob pl) {
	if(mv == null || pl == null)
	    return(Collections.emptyList());
	tickSupportHpCache(mv);
	if((supportHpGlob != mv.glob) || (supportHpMarks == null))
	    return(Collections.emptyList());
	return(supportHpMarks);
    }

    private static Tex supportText(int pct) {
	int size = MoonConfig.mineSupportTextSize;
	synchronized(supportTextCache) {
	    if(supportTextCacheSize != size) {
		for(Tex tex : supportTextCache.values())
		    tex.dispose();
		supportTextCache.clear();
		supportTextCacheSize = size;
	    }
	    Color base = MoonConfig.mineSupportTextColor();
	    Color col = tintSupportText(base, pct);
	    String key = pct + "|" + size + "|" + (col.getRGB() & 0xffffffffL);
	    Tex tex = supportTextCache.get(key);
	    if(tex == null) {
		Text.Foundry f = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale((float)size)), col).aa(true);
		tex = new TexI(Utils.outline2(f.render(pct + "%", col).img, new Color(10, 12, 18)));
		supportTextCache.put(key, tex);
	    }
	    return(tex);
	}
    }

    private static Color tintSupportText(Color base, int pct) {
	if(pct <= 25)
	    return(blend(base, new Color(255, 118, 118), 0.72));
	if(pct <= 60)
	    return(blend(base, new Color(255, 215, 120), 0.42));
	return(base);
    }

    private static Color blend(Color a, Color b, double t) {
	double u = 1.0 - t;
	return(new Color(
	    Utils.clip((int)Math.round(a.getRed() * u + b.getRed() * t), 0, 255),
	    Utils.clip((int)Math.round(a.getGreen() * u + b.getGreen() * t), 0, 255),
	    Utils.clip((int)Math.round(a.getBlue() * u + b.getBlue() * t), 0, 255),
	    Utils.clip((int)Math.round(a.getAlpha() * u + b.getAlpha() * t), 0, 255)));
    }

    public static void cleanupIfDisabled() {
	if(MoonConfig.gfxModMiningOverlay && (MoonConfig.mineSupportSafeTiles || MoonConfig.mineSweeperRiskTiles))
	    return;
	clearWorldOverlay();
    }

    public static boolean isTileSupported(MapView mv, Coord tc) {
	if(mv == null || tc == null)
	    return(false);
	Gob pl;
	try {
	    pl = mv.player();
	} catch(Loading e) {
	    return(false);
	}
	if(pl == null)
	    return(false);
	Cache snap = snapshot(mv, pl);
	return(snap != null) && snap.covered.contains(tc);
    }

    public static Set<Coord> supportedTileSnapshot(MapView mv) {
	if(mv == null)
	    return(Collections.emptySet());
	Gob pl;
	try {
	    pl = mv.player();
	} catch(Loading e) {
	    return(Collections.emptySet());
	}
	if(pl == null)
	    return(Collections.emptySet());
	Cache snap = snapshot(mv, pl);
	if(snap == null || snap.covered.isEmpty())
	    return(Collections.emptySet());
	return(snap.covered);
    }

    private static Cache snapshot(MapView mv, Gob pl) {
	if(mv == null || pl == null || mv.glob == null || mv.glob.map == null)
	    return(null);
	Coord ptc = pl.rc.floor(MCache.tilesz);
	double now = Utils.rtime();
	Cache cur = cache;
	if(cur != null && cur.glob == mv.glob && Math.abs(cur.playerTile.x - ptc.x) <= CACHE_MOVE_EPS
	    && Math.abs(cur.playerTile.y - ptc.y) <= CACHE_MOVE_EPS && now - cur.builtAt <= CACHE_TTL_SEC) {
	    long sig = supportSignature(mv, ptc);
	    if(sig == cur.supportSig)
		return(cur);
	}
	long sig = supportSignature(mv, ptc);
	Area view = Area.sized(ptc.sub(VIEW_RADIUS, VIEW_RADIUS), Coord.of(VIEW_RADIUS * 2 + 1, VIEW_RADIUS * 2 + 1));
	List<SupportSpec> supports = collectSupportSpecs(mv, pl, true);
	List<SupportSpec> brokenSupports = collectSupportSpecs(mv, pl, false);
	Set<Coord> covered = computeSupportedTileSet(mv, pl, supports);
	Set<Coord> broken = computeSupportedTileSet(mv, pl, brokenSupports);
	Set<Coord> visible = new LinkedHashSet<>();
	Set<Coord> visibleBroken = new LinkedHashSet<>();
	for(Coord tc : covered) {
	    if(view.contains(tc))
		visible.add(tc);
	}
	for(Coord tc : broken) {
	    if(view.contains(tc))
		visibleBroken.add(tc);
	}
	List<FillStrip> fillStrips = (visible.size() <= FILL_TILE_CAP) ? computeFillStrips(visible) : null;
	List<FillRect> fillRects = (fillStrips == null) ? Collections.emptyList() : computeFillRects(fillStrips);
	List<FillRect> brokenFillRects = computeFillRects(computeFillStrips(visibleBroken));
	List<Segment> edges = Collections.emptyList();
	List<ContactLine> contacts = computeContactLines(supports, view);
	return(cache = new Cache(mv.glob, ptc, view, visible, visibleBroken, fillStrips, fillRects, brokenFillRects, edges, contacts, sig, now));
    }

    private static List<SupportSpec> collectSupportSpecs(MapView mv, Gob pl, boolean standing) {
	List<SupportSpec> out = new ArrayList<>();
	if(pl == null || mv == null || mv.glob == null || mv.glob.map == null)
	    return(out);
	Coord ptc = pl.rc.floor(MCache.tilesz);
	double tw = MCache.tilesz.x, th = MCache.tilesz.y;
	try {
	    final int collectR = 80;
	    List<Gob> candidates = new ArrayList<>();
	    synchronized(mv.glob.oc) {
		for(Gob gob : mv.glob.oc) {
		    if(gob == null)
			continue;
		    if(!isMineSupportGob(gob))
			continue;
		    if(standing && !supportIsStructurallySound(gob))
			continue;
		    if(!standing && !supportIsBroken(gob))
			continue;
		    Coord bc = gob.rc.floor(MCache.tilesz);
		    if(Math.max(Math.abs(bc.x - ptc.x), Math.abs(bc.y - ptc.y)) > collectR)
			continue;
		    candidates.add(gob);
		}
	    }
	    candidates.sort(Comparator.comparingDouble(gb -> gb.rc.dist(pl.rc)));
	    int n = 0;
	    for(Gob gob : candidates) {
		if(n >= MAX_SUPPORTS)
		    break;
		double rTiles = supportRadiusTiles(gob);
		if(rTiles <= 0)
		    continue;
		n++;
		Coord bc = gob.rc.floor(MCache.tilesz);
		out.add(new SupportSpec(bc, supportDiscCenterWorld(gob, tw, th), rTiles));
	    }
	} catch(Exception ignored) {
	}
	return(out);
    }

    private static long supportSignature(MapView mv, Coord ptc) {
	long sig = 1469598103934665603L;
	if(mv == null || mv.glob == null)
	    return(sig);
	try {
	    final int collectR = 80;
	    synchronized(mv.glob.oc) {
		for(Gob gob : mv.glob.oc) {
		    if(gob == null)
			continue;
		    if(!isMineSupportGob(gob))
			continue;
		    if(!supportIsStructurallySound(gob))
			continue;
		    Coord bc = gob.rc.floor(MCache.tilesz);
		    if(Math.max(Math.abs(bc.x - ptc.x), Math.abs(bc.y - ptc.y)) > collectR)
			continue;
		    long part = gob.id;
		    part = (part * 1099511628211L) ^ (((long)bc.x) << 32 ^ (bc.y & 0xffffffffL));
		    part = (part * 1099511628211L) ^ Math.round(supportRadiusTiles(gob) * 4.0);
		    GobHealth gh = gob.getattr(GobHealth.class);
		    int hpq = (gh == null) ? 4 : Utils.clip(Math.round(gh.hp * 4f), 0, 4);
		    part = (part * 1099511628211L) ^ hpq;
		    sig ^= part;
		    sig *= 1099511628211L;
		}
	    }
	} catch(Exception ignored) {
	}
	return(sig);
    }

    private static boolean supportIsBroken(Gob gob) {
	GobHealth gh = gob.getattr(GobHealth.class);
	return(gh != null) && (gh.hp <= 1e-3f);
    }

    private static List<Segment> computeEdges(Set<Coord> covered) {
	List<Segment> edges = new ArrayList<>();
	if(covered == null || covered.isEmpty())
	    return(edges);
	Map<Integer, List<Integer>> horiz = new HashMap<>();
	Map<Integer, List<Integer>> vert = new HashMap<>();
	for(Coord tc : covered) {
	    if(!covered.contains(tc.add(1, 0)))
		vert.computeIfAbsent(tc.x + 1, k -> new ArrayList<>()).add(tc.y);
	    if(!covered.contains(tc.add(-1, 0)))
		vert.computeIfAbsent(tc.x, k -> new ArrayList<>()).add(tc.y);
	    if(!covered.contains(tc.add(0, 1)))
		horiz.computeIfAbsent(tc.y + 1, k -> new ArrayList<>()).add(tc.x);
	    if(!covered.contains(tc.add(0, -1)))
		horiz.computeIfAbsent(tc.y, k -> new ArrayList<>()).add(tc.x);
	}
	for(Map.Entry<Integer, List<Integer>> e : horiz.entrySet())
	    mergeUnitRuns(edges, e.getValue(), true, e.getKey());
	for(Map.Entry<Integer, List<Integer>> e : vert.entrySet())
	    mergeUnitRuns(edges, e.getValue(), false, e.getKey());
	return(edges);
    }

    private static List<FillStrip> computeFillStrips(Set<Coord> covered) {
	List<FillStrip> strips = new ArrayList<>();
	if(covered == null || covered.isEmpty())
	    return(strips);
	Map<Integer, List<Integer>> rows = new HashMap<>();
	for(Coord tc : covered)
	    rows.computeIfAbsent(tc.y, k -> new ArrayList<>()).add(tc.x);
	for(Map.Entry<Integer, List<Integer>> e : rows.entrySet()) {
	    List<Integer> xs = e.getValue();
	    Collections.sort(xs);
	    int runStart = xs.get(0);
	    int prev = runStart;
	    for(int i = 1; i < xs.size(); i++) {
		int cur = xs.get(i);
		if(cur == prev + 1) {
		    prev = cur;
		    continue;
		}
		strips.add(new FillStrip(e.getKey(), runStart, prev + 1));
		runStart = prev = cur;
	    }
	    strips.add(new FillStrip(e.getKey(), runStart, prev + 1));
	}
	return(strips);
    }

    private static List<FillRect> computeFillRects(List<FillStrip> strips) {
	List<FillRect> rects = new ArrayList<>();
	if(strips == null || strips.isEmpty())
	    return(rects);
	strips.sort(Comparator.<FillStrip>comparingInt(s -> s.y).thenComparingInt(s -> s.x0).thenComparingInt(s -> s.x1));
	int i = 0;
	while(i < strips.size()) {
	    FillStrip s = strips.get(i);
	    int x0 = s.x0, x1 = s.x1, y0 = s.y, y1 = s.y + 1;
	    i++;
	    while(i < strips.size()) {
		FillStrip n = strips.get(i);
		if(n.x0 == x0 && n.x1 == x1 && n.y == y1) {
		    y1++;
		    i++;
		} else {
		    break;
		}
	    }
	    rects.add(new FillRect(x0, y0, x1, y1));
	}
	return(rects);
    }

    private static List<ContactLine> computeContactLines(List<SupportSpec> supports, Area view) {
	List<ContactLine> out = new ArrayList<>();
	if(supports == null || supports.size() < 2)
	    return(out);
	double tw = MCache.tilesz.x;
	double th = MCache.tilesz.y;
	double tileScale = Math.min(tw, th);
	for(int i = 0; i < supports.size(); i++) {
	    SupportSpec a = supports.get(i);
	    if(view != null && !view.contains(a.tile))
		continue;
	    for(int j = i + 1; j < supports.size(); j++) {
		SupportSpec b = supports.get(j);
		if(view != null && !view.contains(b.tile))
		    continue;
		double d = a.center.dist(b.center);
		double ra = a.radiusTiles * tileScale;
		double rb = b.radiusTiles * tileScale;
		if(d <= 1e-6 || d > (ra + rb))
		    continue;
		Coord2d dir = Coord2d.of((b.center.x - a.center.x) / d, (b.center.y - a.center.y) / d);
		Coord2d amid = Coord2d.of((a.center.x + b.center.x) * 0.5, (a.center.y + b.center.y) * 0.5);
		Coord2d a0 = a.center.add(dir.mul(Math.min(ra, d * 0.48)));
		Coord2d b0 = b.center.sub(dir.mul(Math.min(rb, d * 0.48)));
		out.add(new ContactLine(a0, amid, b0, amid));
	    }
	}
	return(out);
    }

    private static void mergeUnitRuns(List<Segment> out, List<Integer> starts, boolean horizontal, int axis) {
	if(starts == null || starts.isEmpty())
	    return;
	Collections.sort(starts);
	int runStart = starts.get(0);
	int prev = runStart;
	for(int i = 1; i < starts.size(); i++) {
	    int cur = starts.get(i);
	    if(cur == prev + 1) {
		prev = cur;
		continue;
	    }
	    addSegment(out, horizontal, axis, runStart, prev + 1);
	    runStart = prev = cur;
	}
	addSegment(out, horizontal, axis, runStart, prev + 1);
    }

    private static void addSegment(List<Segment> out, boolean horizontal, int axis, int from, int toExclusive) {
	if(toExclusive <= from)
	    return;
	if(horizontal)
	    out.add(new Segment(Coord.of(from, axis), Coord.of(toExclusive, axis)));
	else
	    out.add(new Segment(Coord.of(axis, from), Coord.of(axis, toExclusive)));
    }

    private static Coord[] projectTileCorners(MapView mv, Coord tc, double tw, double th) {
	return(projectRect(mv, tc.x, tc.y, tc.x + 1, tc.y + 1, tw, th));
    }

    private static Coord[] projectRect(MapView mv, int x0, int y0, int x1, int y1, double tw, double th) {
	Coord2d ul = new Coord2d(x0 * tw, y0 * th);
	Coord2d ur = new Coord2d(x1 * tw, y0 * th);
	Coord2d br = new Coord2d(x1 * tw, y1 * th);
	Coord2d bl = new Coord2d(x0 * tw, y1 * th);
	Coord[] sc = new Coord[4];
	int ok = 0;
	for(int i = 0; i < 4; i++) {
	    Coord2d c = (i == 0) ? ul : (i == 1) ? ur : (i == 2) ? br : bl;
	    try {
		Coord3f wc = mv.glob.map.getzp(c);
		Coord3f s = mv.screenxf(wc);
		if(s == null || !Float.isFinite(s.x) || !Float.isFinite(s.y))
		    continue;
		sc[i] = new Coord((int)Math.round(s.x), (int)Math.round(s.y));
		ok++;
	    } catch(Exception ex) {
		break;
	    }
	}
	if(ok < 4)
	    return(null);
	if(!quadSane(sc, mv.sz))
	    return(null);
	return(sc);
    }

    private static Coord[] projectWorldLine(MapView mv, Coord2d a, Coord2d b) {
	if(a == null || b == null)
	    return(null);
	Coord[] pts = new Coord[2];
	for(int n = 0; n < 2; n++) {
	    Coord2d c = (n == 0) ? a : b;
	    try {
		Coord3f wc = mv.glob.map.getzp(c);
		Coord3f s = mv.screenxf(wc);
		if(s == null || !Float.isFinite(s.x) || !Float.isFinite(s.y))
		    return(null);
		pts[n] = Coord.of(Math.round(s.x), Math.round(s.y));
	    } catch(Exception e) {
		return(null);
	    }
	}
	return(pts);
    }

    /** Radius in tiles; prefers runtime overlay, then resource props, then trusted known tiers, then cautious fallback. */
    static double supportRadiusTiles(Gob gob) {
	Double fromOverlay = radiusFromOverlaySdt(gob);
	if(fromOverlay != null && fromOverlay > 0)
	    return fromOverlay;
	Double fromProp = radiusFromResourceProps(gob);
	if(fromProp != null && fromProp > 0)
	    return fromProp;
	try {
	    Resource r = gobStructureResource(gob);
	    if(r != null) {
		Double tip = radiusFromTooltip(r);
		if(tip != null && tip > 0)
		    return tip;
		String nm = r.name;
		if(nm != null) {
		    String ln = nm.toLowerCase(Locale.ROOT);
		    for(String[] rule : RADIUS_RULES) {
			if(ln.contains(rule[0]))
			    return Double.parseDouble(rule[1]);
		    }
		}
	    }
	} catch(Exception ignored) {}
	return Math.max(1, MoonConfig.mineSupportRadiusTiles);
    }

    private static Double radiusFromResourceProps(Gob gob) {
	try {
	    Resource r = gobStructureResource(gob);
	    if(r == null)
		return(null);
	    Resource.Props pr = r.layer(Resource.Props.class);
	    if(pr == null)
		return(null);
	    for(String k : SUPPORT_RADIUS_PROP_KEYS) {
		Object o = pr.get(k);
		if(o == null)
		    continue;
		if(o instanceof Number)
		    return(((Number)o).doubleValue());
		if(o instanceof String) {
		    try {
			return(Double.parseDouble(((String)o).trim()));
		    } catch(NumberFormatException ignored) {}
		}
	    }
	} catch(Exception ignored) {}
	return(null);
    }

    /**
     * Vanilla mine-support overlays encode radius in {@code gfx/fx/msrad} sprite data as a half-float in tile units.
     * When present, this is the most exact runtime radius we can read client-side.
     */
    private static Double radiusFromOverlaySdt(Gob gob) {
	List<Gob.Overlay> snap;
	synchronized(gob) {
	    snap = new ArrayList<>(gob.ols);
	}
	for(Gob.Overlay ol : snap) {
	    if(ol == null)
		continue;
	    String nm = overlayResName(ol);
	    if(nm == null || !nm.toLowerCase(Locale.ROOT).contains("gfx/fx/msrad"))
		continue;
	    if(!(ol.sm instanceof Sprite.Mill.FromRes))
		continue;
	    byte[] sdt = ((Sprite.Mill.FromRes)ol.sm).sdt;
	    if(sdt == null || sdt.length < 2)
		continue;
	    try {
		return((double)Utils.hfdec((short)new MessageBuf(sdt).int16()) * 11.0);
	    } catch(Exception ignored) {
	    }
	}
	return(null);
    }

    private static String overlayResName(Gob.Overlay ol) {
	try {
	    if(ol.spr != null && ol.spr.res != null)
		return(ol.spr.res.name);
	    if(ol.sm instanceof Sprite.Mill.FromRes)
		return(((Sprite.Mill.FromRes)ol.sm).res.get().name);
	} catch(Loading ignored) {
	}
	return(null);
    }

    private static Double radiusFromTooltip(Resource r) {
	try {
	    Resource.Tooltip tt = r.layer(Resource.Tooltip.class);
	    if(tt == null || tt.t == null)
		return(null);
	    Matcher m = TIP_RADIUS.matcher(tt.t);
	    if(m.find())
		return(Double.parseDouble(m.group(2)));
	} catch(Exception ignored) {}
	return(null);
    }

    private static void fillQuad(GOut g, Color col, Coord p0, Coord p1, Coord p2, Coord p3) {
	g.chcolor(col);
	Coord t = g.tx;
	float[] data = {
	    p0.x + t.x, p0.y + t.y, p1.x + t.x, p1.y + t.y, p2.x + t.x, p2.y + t.y,
	    p0.x + t.x, p0.y + t.y, p2.x + t.x, p2.y + t.y, p3.x + t.x, p3.y + t.y
	};
	g.drawp(Model.Mode.TRIANGLES, data, 6);
	g.chcolor();
    }

    private static boolean quadSane(Coord[] sc, Coord screensz) {
	if(sc == null || sc.length < 4 || screensz == null)
	    return(false);
	int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE;
	int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE;
	for(Coord p : sc) {
	    if(p == null)
		return(false);
	    minx = Math.min(minx, p.x);
	    miny = Math.min(miny, p.y);
	    maxx = Math.max(maxx, p.x);
	    maxy = Math.max(maxy, p.y);
	}
	if(maxx < -SCREEN_MARGIN || maxy < -SCREEN_MARGIN)
	    return(false);
	if(minx > screensz.x + SCREEN_MARGIN || miny > screensz.y + SCREEN_MARGIN)
	    return(false);
	long w = (long)maxx - minx;
	long h = (long)maxy - miny;
	if(w <= 0 || h <= 0)
	    return(false);
	/* If one tile expands to near full-screen dimensions, projection is broken for this quad. */
	if(w > (long)screensz.x * 2 || h > (long)screensz.y * 2)
	    return(false);
	long area2 = 0;
	for(int i = 0; i < 4; i++) {
	    Coord a = sc[i];
	    Coord b = sc[(i + 1) & 3];
	    area2 += (long)a.x * b.y - (long)b.x * a.y;
	}
	area2 = Math.abs(area2);
	long screenArea2 = 2L * Math.max(1, screensz.x) * Math.max(1, screensz.y);
	return(area2 <= screenArea2);
    }

    /** Column/pillar cave supports whose resource basename omits “minesup” (e.g. stone tunnel columns). */
    private static boolean nameLooksLikeArchMineColumn(String l) {
	if(!l.contains("/terobjs/arch/") && !l.contains("gfx/terobjs/arch/"))
	    return(false);
	boolean colish = l.contains("column") || l.contains("pillar") || l.contains("colonn");
	if(!colish)
	    return(false);
	if(l.contains("mine") || l.contains("cave") || l.contains("tunnel") || l.contains("tunne")
	    || l.contains("brace") || l.contains("sup") || l.contains("shore"))
	    return(true);
	/* Plain “stone column” / similar tunneling props (no “mine” in the path). */
	if(l.contains("stone") || l.contains("marble") || l.contains("granite"))
	    return(true);
	return(false);
    }

    static boolean isMineSupportGob(Gob gob) {
	try {
	    Double ov = radiusFromOverlaySdt(gob);
	    if(ov != null && ov > 0)
		return(true);
	} catch(Exception ignored) {}
	String n = gobStructureResName(gob);
	if(n == null)
	    return(false);
	String l = n.toLowerCase(Locale.ROOT);
	if(l.contains("monumentalcolumn"))
	    return(true);
	if(l.contains("minesup") || l.contains("minesupport") || l.contains("mine-sup")
	    || l.contains("gob/minesupport") || l.contains("minebrace")
	    || l.contains("minesuptower")
	    || l.contains("minesupbeam") || l.contains("minesupportbeam")
	    || l.contains("mineshoring") || l.contains("mine-shoring")
	    || l.contains("roofsupport") || l.contains("timbersupport")
	    || l.contains("minebeam") || l.contains("minecolumn")
	    || l.contains("stonecolumn") || l.contains("cavecolumn") || l.contains("tunnelcolumn")
	    || l.contains("stoneminesupport") || l.contains("ironminesupport")
	    || l.contains("steelminesupport") || l.contains("brassminesupport")
	    || l.contains("cupronminesupport") || l.contains("wroughtminesupport")
	    || l.contains("metalminesupport"))
	    return(true);
	if(l.contains("stonepillar") || l.contains("minepillar") || l.contains("supportpillar"))
	    return(true);
	if(l.equals("gfx/terobjs/column"))
	    return(true);
	try {
	    Double pr = radiusFromResourceProps(gob);
	    if(pr != null && pr > 0)
		return(true);
	} catch(Exception ignored) {}
	return(nameLooksLikeArchMineColumn(l));
    }

    private static void syncWorldOverlay(MCache map, List<FillRect> safeRects, List<FillRect> brokenRects, long sig) {
	if(map == null || safeRects == null || brokenRects == null)
	    return;
	if((overlayMap == map) && (overlaySig == sig))
	    return;
	clearWorldOverlay();
	List<MCache.Overlay> safe = new ArrayList<>(safeRects.size());
	for(FillRect rect : safeRects) {
	    if(rect.x1 <= rect.x0 || rect.y1 <= rect.y0)
		continue;
	    safe.add(map.new Overlay(Area.sized(Coord.of(rect.x0, rect.y0), Coord.of(rect.x1 - rect.x0, rect.y1 - rect.y0)), SAFE_OVERLAY));
	}
	List<MCache.Overlay> broken = new ArrayList<>(brokenRects.size());
	for(FillRect rect : brokenRects) {
	    if(rect.x1 <= rect.x0 || rect.y1 <= rect.y0)
		continue;
	    broken.add(map.new Overlay(Area.sized(Coord.of(rect.x0, rect.y0), Coord.of(rect.x1 - rect.x0, rect.y1 - rect.y0)), BROKEN_OVERLAY));
	}
	overlayMap = map;
	overlaySig = sig;
	liveSafeOverlays = safe;
	liveBrokenOverlays = broken;
    }

    private static void clearWorldOverlay() {
	for(MCache.Overlay ol : liveSafeOverlays) {
	    try {
		ol.destroy();
	    } catch(Exception ignored) {
	    }
	}
	for(MCache.Overlay ol : liveBrokenOverlays) {
	    try {
		ol.destroy();
	    } catch(Exception ignored) {
	    }
	}
	liveSafeOverlays = Collections.emptyList();
	liveBrokenOverlays = Collections.emptyList();
	overlayMap = null;
	overlaySig = Long.MIN_VALUE;
    }
}
