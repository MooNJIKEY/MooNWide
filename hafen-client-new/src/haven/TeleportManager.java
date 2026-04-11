package haven;

import static haven.OCache.posres;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Short-range navigation assist: validated map clicks and saved points.
 * Outgoing {@code click} on {@link MapView} can be clamped when {@link MoonConfig#teleportClickClamp} is on.
 * Server movement rules still apply; this does not bypass authority.
 */
public final class TeleportManager {
    private TeleportManager() {}

    private static final CopyOnWriteArrayList<TeleportPoint> points = new CopyOnWriteArrayList<>();
    private static volatile long lastTeleportAtMs = 0L;
    private static volatile boolean mapPickArmed = false;

    static {
	loadFromPrefs();
	MoonPacketHook.addOutgoingFilter(TeleportManager::outgoingClickFilter);
    }

    private static void loadFromPrefs() {
	points.clear();
	String raw = Utils.getpref("moon-tp-points", "");
	if(raw == null || raw.isEmpty())
	    return;
	try {
	    Object root = MoonJsonLite.parse(raw.trim());
	    List<?> arr = MoonJsonLite.asList(root);
	    for(Object o : arr) {
		if(!(o instanceof java.util.Map))
		    continue;
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
		String name = MoonJsonLite.asString(m.get("name"));
		double x = MoonJsonLite.asDouble(m.get("x"), Double.NaN);
		double y = MoonJsonLite.asDouble(m.get("y"), Double.NaN);
		if(Double.isNaN(x) || Double.isNaN(y))
		    continue;
		TeleportPoint p = pointFromJson(name, x, y, m);
		if(p != null)
		    points.add(p);
	    }
	} catch(Exception e) {
	    System.err.println("[MoonTeleport] load: " + e);
	}
    }

    public static void saveToPrefs() {
	Utils.setpref("moon-tp-points", serializePoints());
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {
	}
    }

    private static String serializePoints() {
	StringBuilder sb = new StringBuilder();
	sb.append('[');
	boolean first = true;
	for(TeleportPoint p : points) {
	    if(!first)
		sb.append(',');
	    first = false;
	    sb.append("{\"name\":\"").append(TeleportPoint.jsonEscape(p.name)).append("\",");
	    sb.append("\"x\":").append(Double.toString(p.x)).append(',');
	    sb.append("\"y\":").append(Double.toString(p.y)).append(',');
	    sb.append("\"cat\":\"").append(TeleportPoint.jsonEscape(p.category)).append("\",");
	    sb.append("\"last\":").append(p.lastUsed);
	    if(p.hasMapLocation()) {
		sb.append(",\"seg\":").append(p.mapSeg);
		sb.append(",\"tx\":").append(p.mapTcX);
		sb.append(",\"ty\":").append(p.mapTcY);
	    }
	    sb.append('}');
	}
	sb.append(']');
	return sb.toString();
    }

    public static List<TeleportPoint> getPointsSnapshot() {
	return Collections.unmodifiableList(new ArrayList<>(points));
    }

    public static void addPoint(TeleportPoint p) {
	if(p == null)
	    return;
	points.add(p);
	saveToPrefs();
    }

    public static void removePoint(TeleportPoint p) {
	if(p == null)
	    return;
	points.remove(p);
	saveToPrefs();
    }

    public static String exportJson() {
	return serializePoints();
    }

    public static int importJsonReplace(String json) {
	if(json == null || json.trim().isEmpty())
	    return 0;
	points.clear();
	int n = 0;
	try {
	    Object root = MoonJsonLite.parse(json.trim());
	    List<?> arr = MoonJsonLite.asList(root);
	    for(Object o : arr) {
		if(!(o instanceof java.util.Map))
		    continue;
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
		String name = MoonJsonLite.asString(m.get("name"));
		double x = MoonJsonLite.asDouble(m.get("x"), Double.NaN);
		double y = MoonJsonLite.asDouble(m.get("y"), Double.NaN);
		if(Double.isNaN(x) || Double.isNaN(y))
		    continue;
		TeleportPoint p = pointFromJson(name, x, y, m);
		if(p != null) {
		    points.add(p);
		    n++;
		}
	    }
	} catch(Exception e) {
	    System.err.println("[MoonTeleport] import: " + e);
	    return -1;
	}
	saveToPrefs();
	return n;
    }

    public static void goQuickSlot(GameUI gui, int index) {
	if(gui == null || index < 0)
	    return;
	List<TeleportPoint> s = CoordinateValidator.sortedByLastUsed(new ArrayList<>(points));
	if(index >= s.size()) {
	    msg(gui, LocalizationManager.tr("tp.quick.empty"), Color.GRAY);
	    return;
	}
	TeleportPoint p = s.get(index);
	tryNavigateTo(gui, p, "quick#" + index);
    }

    public static boolean tryNavigateTo(GameUI gui, TeleportPoint point, String logLabel) {
	if(point == null)
	    return false;
	return tryNavigateTo(gui, point.pos(), logLabel, point);
    }

    public static boolean tryNavigateTo(GameUI gui, Coord2d target, String logLabel, TeleportPoint touchLastUsed) {
	if(gui == null || gui.map == null) {
	    msg(gui, LocalizationManager.tr("tp.fail.no_map"), Color.WHITE);
	    return false;
	}
	if(!MoonConfig.teleportNavEnabled) {
	    msg(gui, LocalizationManager.tr("tp.fail.disabled"), Color.WHITE);
	    return false;
	}
	Gob pl;
	try {
	    pl = gui.map.player();
	} catch(Loading e) {
	    msg(gui, LocalizationManager.tr("tp.fail.loading"), Color.WHITE);
	    return false;
	}
	if(pl == null) {
	    msg(gui, LocalizationManager.tr("tp.fail.no_player"), Color.WHITE);
	    return false;
	}
	long now = System.currentTimeMillis();
	if(now - lastTeleportAtMs < MoonConfig.teleportCooldownMs) {
	    msg(gui, LocalizationManager.tr("tp.fail.cooldown"), Color.ORANGE);
	    logLine("reject cooldown label=" + logLabel);
	    return false;
	}
	Coord2d cur = Coord2d.of(pl.rc.x, pl.rc.y);
	StringBuilder det = new StringBuilder();
	CoordinateValidator.Result r = CoordinateValidator.validate(gui, cur, target, det);
	if(r != CoordinateValidator.Result.OK) {
	    msg(gui, LocalizationManager.tr("tp.fail.reason") + ": " + r + " " + det, Color.RED);
	    logLine("reject " + r + " " + det + " label=" + logLabel);
	    return false;
	}
	List<Coord2d> rawPath = planPath(gui, cur, target);
	if(rawPath == null || rawPath.isEmpty()) {
	    msg(gui, LocalizationManager.tr("tp.fail.reason") + ": PATH", Color.RED);
	    logLine("reject no-path label=" + logLabel);
	    return false;
	}
	List<Coord2d> path = compressPath(rawPath);
	MoonPathWalker.start(gui.map, path);
	gui.map.moonSetRouteTrace(path);
	lastTeleportAtMs = now;
	MapView.moonNavTriggerFlash(target);
	if(touchLastUsed != null) {
	    touchLastUsed.lastUsed = now;
	    saveToPrefs();
	}
	logLine(String.format(Locale.ROOT, "ok tiles=%.1f label=%s raw=%d route=%d %s",
	    cur.dist(target) / MCache.tilesz.x, logLabel, rawPath.size(), path.size(), det));
	msg(gui, LocalizationManager.tr("tp.ok"), new Color(120, 200, 255));
	return true;
    }

    public static boolean handleArmedMapPick(GameUI gui, MiniMap map, MiniMap.Location loc, int button, boolean press) {
	if(!mapPickArmed)
	    return false;
	if(button == 3 && press) {
	    setMapPickArmed(gui, false);
	    return true;
	}
	if(button != 1)
	    return true;
	if(press)
	    return true;
	if(tryNavigateToMapLocation(gui, map, loc, "map-pick"))
	    setMapPickArmed(gui, false);
	return true;
    }

    public static boolean tryNavigateToMapLocation(GameUI gui, MiniMap map, MiniMap.Location loc, String label) {
	Coord2d target = mapLocationToWorld(gui, map, loc);
	if(target == null) {
	    msg(gui, LocalizationManager.tr("tp.fail.mapseg"), Color.RED);
	    return false;
	}
	return tryNavigateTo(gui, target, label, null);
    }

    public static boolean isMapPickArmed() {
	return mapPickArmed;
    }

    public static long cooldownRemainingMs() {
	long left = MoonConfig.teleportCooldownMs - (System.currentTimeMillis() - lastTeleportAtMs);
	return Math.max(0L, left);
    }

    public static String statusSummary() {
	if(!MoonConfig.teleportNavEnabled)
	    return "disabled";
	if(mapPickArmed)
	    return "map-pick armed";
	long left = cooldownRemainingMs();
	if(left > 0L)
	    return String.format(Locale.ROOT, "cooldown %.1fs", left / 1000.0);
	return "ready (" + points.size() + " points)";
    }

    public static void toggleMapPick(GameUI gui) {
	setMapPickArmed(gui, !mapPickArmed);
    }

    public static void setMapPickArmed(GameUI gui, boolean armed) {
	mapPickArmed = armed;
	msg(gui, LocalizationManager.tr(armed ? "tp.pick.on" : "tp.pick.off"), armed ? new Color(120, 200, 255) : Color.GRAY);
    }

    public static void enrichPointWithCurrentMap(GameUI gui, TeleportPoint point) {
	if(gui == null || point == null)
	    return;
	MiniMap.Location loc = currentMapLocation(gui);
	if(loc != null)
	    point.setMapLocation(loc.seg.id, loc.tc);
    }

    public static Coord inferPointMapTc(GameUI gui, TeleportPoint point, long segId) {
	if(gui == null || point == null || point.hasMapLocation() || gui.map == null)
	    return null;
	MiniMap.Location loc = currentMapLocation(gui);
	if(loc == null || loc.seg.id != segId)
	    return null;
	Gob pl;
	try {
	    pl = gui.map.player();
	} catch(Loading e) {
	    return null;
	}
	if(pl == null)
	    return null;
	Coord playerTc = pl.rc.floor(MCache.tilesz);
	Coord pointTc = point.pos().floor(MCache.tilesz);
	return loc.tc.add(pointTc.sub(playerTc));
    }

    public static Color pointColor(TeleportPoint point) {
	String cat = (point == null) ? TeleportPoint.CAT_OTHER : point.category;
	if(TeleportPoint.CAT_HOME.equals(cat))
	    return new Color(120, 255, 140, 255);
	if(TeleportPoint.CAT_RESOURCE.equals(cat))
	    return new Color(255, 218, 90, 255);
	if(TeleportPoint.CAT_FRIEND.equals(cat))
	    return new Color(90, 220, 255, 255);
	return new Color(220, 180, 255, 255);
    }

    private static TeleportPoint pointFromJson(String name, double x, double y, java.util.Map<String, Object> m) {
	String cat = TeleportPoint.normalizeCategory(MoonJsonLite.asString(m.get("cat")));
	long seg = (long) MoonJsonLite.asDouble(m.get("seg"), TeleportPoint.NO_SEG);
	int tx = (int) MoonJsonLite.asDouble(m.get("tx"), 0);
	int ty = (int) MoonJsonLite.asDouble(m.get("ty"), 0);
	TeleportPoint p = new TeleportPoint(name, x, y, cat, seg, tx, ty);
	p.lastUsed = (long) MoonJsonLite.asDouble(m.get("last"), 0);
	return p;
    }

    private static List<Coord2d> planPath(GameUI gui, Coord2d cur, Coord2d target) {
	if(gui == null || gui.map == null)
	    return null;
	MCache map = gui.map.glob.map;
	OCache oc = gui.map.glob.oc;
	long pg = MoonPacketHook.getPlayerGobId();
	return MoonPathfinder.findPath(map, oc, cur, target, pg, MoonConfig.teleportBlockHostileNearTarget, MoonConfig.teleportHostileClearTiles);
    }

    private static List<Coord2d> compressPath(List<Coord2d> src) {
	if(src == null || src.size() <= 2)
	    return (src == null) ? null : new ArrayList<>(src);
	List<Coord2d> out = new ArrayList<>();
	out.add(src.get(0));
	Coord prevTc = src.get(0).floor(MCache.tilesz);
	int lastDx = Integer.MIN_VALUE;
	int lastDy = Integer.MIN_VALUE;
	for(int i = 1; i < src.size(); i++) {
	    Coord tc = src.get(i).floor(MCache.tilesz);
	    int dx = Integer.compare(tc.x - prevTc.x, 0);
	    int dy = Integer.compare(tc.y - prevTc.y, 0);
	    if(i == 1) {
		lastDx = dx;
		lastDy = dy;
	    } else if(dx != lastDx || dy != lastDy) {
		out.add(src.get(i - 1));
		lastDx = dx;
		lastDy = dy;
	    }
	    prevTc = tc;
	}
	Coord2d end = src.get(src.size() - 1);
	if(out.isEmpty() || !samePoint(out.get(out.size() - 1), end))
	    out.add(end);
	return out;
    }

    private static boolean samePoint(Coord2d a, Coord2d b) {
	return (a != null) && (b != null) && (a.dist(b) < 0.001);
    }

    private static MiniMap currentMiniMap(GameUI gui) {
	if(gui == null)
	    return null;
	if(gui.mmap instanceof MiniMap)
	    return (MiniMap) gui.mmap;
	if(gui.mapfile != null && gui.mapfile.view instanceof MiniMap)
	    return (MiniMap) gui.mapfile.view;
	return null;
    }

    private static MiniMap.Location currentMapLocation(GameUI gui) {
	MiniMap mm = currentMiniMap(gui);
	if(mm == null || gui == null || gui.map == null)
	    return null;
	try {
	    return mm.resolve(new MiniMap.MapLocator(gui.map));
	} catch(Exception e) {
	    return mm.sessloc;
	}
    }

    private static Coord2d mapLocationToWorld(GameUI gui, MiniMap map, MiniMap.Location loc) {
	if(gui == null || gui.map == null || map == null || loc == null)
	    return null;
	MiniMap.Location sess = map.sessloc;
	if(sess == null || sess.seg != loc.seg)
	    return null;
	Gob pl;
	try {
	    pl = gui.map.player();
	} catch(Loading e) {
	    return null;
	}
	if(pl == null)
	    return null;
	Coord dt = loc.tc.sub(sess.tc);
	Coord2d base = pl.rc.floor(MCache.tilesz).mul(MCache.tilesz).add(MCache.tilesz.div(2));
	return base.add(dt.mul(MCache.tilesz));
    }

    private static void msg(GameUI gui, String text, Color col) {
	if(gui != null && gui.ui != null && text != null)
	    gui.ui.msg(text, col, null);
    }

    private static void logLine(String line) {
	if(!MoonConfig.teleportLogStderr)
	    return;
	System.err.println("[MoonTeleport] " + System.currentTimeMillis() + " " + line);
    }

    private static Object[] outgoingClickFilter(UI ui, int widgetId, String msg, Object[] args) {
	try {
	    if(!MoonConfig.teleportNavEnabled || !MoonConfig.teleportClickClamp)
		return args;
	    if(ui == null || args == null || !"click".equals(msg))
		return args;
	    Widget w = ui.getwidget(widgetId);
	    if(!(w instanceof MapView))
		return args;
	    if(args.length != 4)
		return args;
	    if(!(args[1] instanceof Coord))
		return args;
	    /* Diablo-move, path walker send screen coord {@link Coord#z}; do not clamp those. */
	    if(args[0] == Coord.z)
		return args;
	    MapView mv = (MapView) w;
	    Gob pl;
	    try {
		pl = mv.player();
	    } catch(Loading e) {
		return args;
	    }
	    if(pl == null)
		return args;
	    Coord2d dest = ((Coord) args[1]).mul(posres);
	    Coord2d cur = Coord2d.of(pl.rc.x, pl.rc.y);
	    double maxW = MoonConfig.teleportMaxTiles * MCache.tilesz.x;
	    if(cur.dist(dest) <= maxW)
		return args;
	    Coord2d delta = dest.sub(cur);
	    double len = delta.abs();
	    if(len < 1e-6)
		return args;
	    Coord2d clamped = cur.add(delta.norm(maxW));
	    Coord2d centred = clamped.floor(MCache.tilesz).mul(MCache.tilesz).add(MCache.tilesz.div(2));
	    MCache map = mv.glob.map;
	    Coord2d use = centred;
	    if(!MoonPathfinder.isTilePassableAtWorld(map, centred))
		use = clamped;
	    if(!MoonPathfinder.isTilePassableAtWorld(map, use))
		return args;
	    GameUI gui = mv.getparent(GameUI.class);
	    if(gui != null) {
		StringBuilder det = new StringBuilder();
		CoordinateValidator.Result r = CoordinateValidator.validate(gui, cur, use, det);
		if(r != CoordinateValidator.Result.OK)
		    return args;
	    }
	    Object[] out = Arrays.copyOf(args, 4);
	    out[1] = use.floor(posres);
	    logLine("clamp outgoing click -> tiles=" + (cur.dist(use) / MCache.tilesz.x));
	    return out;
	} catch(Exception e) {
	    System.err.println("[MoonTeleport] outgoing filter: " + e);
	    return args;
	}
    }
}
