package haven;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Vector-aware A* pathfinder for Haven's 11x11 world-unit tile grid.
 *
 * The planner works in three stages:
 * 1. Build a coarse tile path with A*.
 * 2. Collapse the tile chain into longer vector segments with line-of-sight checks.
 * 3. Sample a smooth Catmull-Rom spline over those anchors for preview / streaming movement.
 *
 * The server remains authoritative. Movement still goes through {@link MapView#wdgmsg(String, Object...)}
 * and the UI thread, but {@link #sendSmoothMove(MapView, PathPlan)} streams short movement clicks ahead
 * of the player so turns do not stall as hard as with one-click-per-corner walking.
 */
public final class MoonPathfinder {
    private MoonPathfinder() {}

    private static final int[][] N8 = {
	{1, 0}, {-1, 0}, {0, 1}, {0, -1},
	{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final int BASE_MAX_VISIT = 32000;
    private static final int BASE_GOAL_SEARCH_TILES = 12;

    private static final double TILE = MCache.tilesz.x;
    private static final double MAX_DZ = 48.0;
    private static final double EPS = 1e-6;
    private static final double OBSTACLE_SCAN_MARGIN = TILE * 20.0;
    private static final double OBSTACLE_SCAN_REL_MARGIN = 0.35;
    private static final double OBSTACLE_HARD_PADDING = TILE * 0.16;
    private static final double OBSTACLE_SOFT_PADDING = TILE * 0.62;
    private static final double OBSTACLE_SOFT_COST = 0.85;
    private static final double OBSTACLE_SOFT_COST_CAP = 2.40;
    private static final double LOS_SAMPLE_STEP = TILE * 0.45;
    private static final double SPLINE_SAMPLE_STEP = TILE * 0.55;
    private static final double SAMPLE_MIN_SEPARATION = TILE * 0.18;
    private static final double PATH_REACHED_EPS = TILE * 0.32;
    private static final double SMOOTH_LOOKAHEAD_MIN = TILE * 1.20;
    private static final double SMOOTH_LOOKAHEAD_SEC = 0.34;
    private static final double SMOOTH_MIN_SEND_DT = 0.06;
    private static final double SMOOTH_STALL_SEC = 0.32;
    private static final double SMOOTH_PROGRESS_EPS = 0.75;
    private static final long SMOOTH_LOOP_SLEEP_MS = 18L;
    private static final long UI_CLICK_TIMEOUT_MS = 1200L;

    private static final Set<String> BLOCKED_TILES = new HashSet<>(Arrays.asList(
	"gfx/tiles/nil", "gfx/tiles/cave"
    ));
    private static final Set<String> DEEP_WATER = new HashSet<>(Arrays.asList(
	"gfx/tiles/deep", "gfx/tiles/odeep", "gfx/tiles/odeeper"
    ));
    private static final Set<String> BOG_TILES = new HashSet<>(Arrays.asList(
	"gfx/tiles/bog", "gfx/tiles/bogwater",
	"gfx/tiles/fen", "gfx/tiles/fenwater",
	"gfx/tiles/swamp", "gfx/tiles/swampwater"
    ));

    private static volatile SmoothMoveSession activeSmoothMove = null;

    public static final class PathPlan {
	public final Coord2d start;
	public final Coord2d requestedGoal;
	public final Coord2d resolvedGoal;
	public final boolean adjustedGoal;
	public final List<Coord2d> rawPath;
	public final List<Coord2d> waypoints;
	public final List<Coord2d> spline;

	private PathPlan(Coord2d start, Coord2d requestedGoal, Coord2d resolvedGoal, boolean adjustedGoal,
			 List<Coord2d> rawPath, List<Coord2d> waypoints, List<Coord2d> spline) {
	    this.start = start;
	    this.requestedGoal = requestedGoal;
	    this.resolvedGoal = resolvedGoal;
	    this.adjustedGoal = adjustedGoal;
	    this.rawPath = freeze(rawPath);
	    this.waypoints = freeze(waypoints);
	    this.spline = freeze(spline);
	}

	public List<Coord2d> movePath() {
	    return(!spline.isEmpty() ? spline : waypoints);
	}
    }

    private static final class ObstacleFootprint {
	final long gobId;
	final Coord2d center;
	final double baseRadius;
	final double hardPadding;
	final double softPadding;
	final double broadRadius;
	final double angle;
	final double sa;
	final double ca;
	final Coord2d[][] localPolys;

	ObstacleFootprint(long gobId, Coord2d center, double baseRadius, double hardPadding,
			  double softPadding, double angle, Coord2d[][] localPolys) {
	    this.gobId = gobId;
	    this.center = center;
	    this.baseRadius = Math.max(0.0, baseRadius);
	    this.hardPadding = Math.max(0.0, hardPadding);
	    this.softPadding = Math.max(this.hardPadding, softPadding);
	    this.broadRadius = this.baseRadius + this.softPadding;
	    this.angle = angle;
	    this.sa = Math.sin(angle);
	    this.ca = Math.cos(angle);
	    this.localPolys = localPolys;
	}

	boolean hasPolys() {
	    return(localPolys != null && localPolys.length > 0);
	}
    }

    private static final class SearchContext {
	final MCache map;
	final List<ObstacleFootprint> obstacles;
	final Set<Coord> blockedTiles;
	final Coord startTile;
	final Coord goalTile;

	SearchContext(MCache map, List<ObstacleFootprint> obstacles, Set<Coord> blockedTiles, Coord startTile, Coord goalTile) {
	    this.map = map;
	    this.obstacles = obstacles;
	    this.blockedTiles = blockedTiles;
	    this.startTile = startTile;
	    this.goalTile = goalTile;
	}
    }

    private static final class GoalCandidate {
	final Coord tile;
	final double score;

	GoalCandidate(Coord tile, double score) {
	    this.tile = tile;
	    this.score = score;
	}
    }

    private static final class Node implements Comparable<Node> {
	final Coord pos;
	final Node parent;
	final double g;
	final double f;

	Node(Coord pos, Node parent, double g, double f) {
	    this.pos = pos;
	    this.parent = parent;
	    this.g = g;
	    this.f = f;
	}

	public int compareTo(Node o) {
	    return(Double.compare(this.f, o.f));
	}
    }

    private static final class SmoothMoveSession extends Thread {
	private final MapView mv;
	private final PathPlan plan;
	private volatile boolean cancelled = false;
	private int nextWaypoint = 0;
	private int lastSentIndex = -1;
	private double lastSendAt = -1e9;
	private double lastProgressAt = Utils.rtime();
	private Coord2d lastPlayerPos = null;

	SmoothMoveSession(MapView mv, PathPlan plan) {
	    super("MoonSmoothMove");
	    setDaemon(true);
	    this.mv = mv;
	    this.plan = plan;
	}

	void cancel() {
	    cancelled = true;
	    if(mv != null)
		mv.moonPathWalkerCancelPending();
	    interrupt();
	}

	@Override
	public void run() {
	    List<Coord2d> path = plan.movePath();
	    try {
		if(mv != null)
		    mv.moonSetRouteTrace(path);
		while(!cancelled) {
		    Gob pl = (mv == null) ? null : mv.player();
		    if(pl == null)
			break;
		    Coord2d pc = playerWorld(pl);
		    if(pc == null)
			break;
		    if(reachedGoal(pc, plan.resolvedGoal))
			break;
		    updateProgress(pc);
		    advanceConsumed(pc, path);
		    if(nextWaypoint >= path.size())
			break;
		    double speed = Math.max(0.0, pl.getv());
		    double lookahead = Math.max(SMOOTH_LOOKAHEAD_MIN, speed * SMOOTH_LOOKAHEAD_SEC);
		    int want = chooseLookaheadIndex(pc, path, nextWaypoint, lookahead);
		    double now = Utils.rtime();
		    boolean needKick = (lastSentIndex < 0);
		    if(!needKick && lastSentIndex < path.size())
			needKick = pc.dist(path.get(lastSentIndex)) <= lookahead * 0.60;
		    if(!needKick && speed <= 0.05 && (now - lastProgressAt) >= SMOOTH_STALL_SEC)
			needKick = true;
		    if(needKick && (now - lastSendAt) >= SMOOTH_MIN_SEND_DT) {
			mv.moonPathWalkerAwaitClick(path.get(want), UI_CLICK_TIMEOUT_MS);
			lastSentIndex = want;
			lastSendAt = Utils.rtime();
		    }
		    Thread.sleep(SMOOTH_LOOP_SLEEP_MS);
		}
	    } catch(InterruptedException ignored) {
	    } finally {
		if(mv != null)
		    mv.moonClearRouteTrace();
		if(activeSmoothMove == this)
		    activeSmoothMove = null;
	    }
	}

	private void updateProgress(Coord2d pc) {
	    if(lastPlayerPos == null || pc.dist(lastPlayerPos) >= SMOOTH_PROGRESS_EPS) {
		lastPlayerPos = pc;
		lastProgressAt = Utils.rtime();
	    }
	}

	private void advanceConsumed(Coord2d pc, List<Coord2d> path) {
	    while(nextWaypoint < path.size() && reachedGoal(pc, path.get(nextWaypoint)))
		nextWaypoint++;
	}

	private int chooseLookaheadIndex(Coord2d pc, List<Coord2d> path, int startIdx, double lookahead) {
	    int best = startIdx;
	    Coord2d prev = pc;
	    double walked = 0.0;
	    for(int i = startIdx; i < path.size(); i++) {
		Coord2d cur = path.get(i);
		walked += prev.dist(cur);
		best = i;
		if(walked >= lookahead)
		    break;
		prev = cur;
	    }
	    return(best);
	}
    }

    public static List<Coord2d> findPath(MCache map, OCache oc, Coord2d from, Coord2d to, long playerGobId) {
	return(findPath(map, oc, from, to, playerGobId, false, 0));
    }

    public static List<Coord2d> findPath(MCache map, OCache oc, Coord2d from, Coord2d to, long playerGobId,
					  boolean avoidHostiles, int hostileClearTiles) {
	PathPlan plan = planPath(map, oc, from, to, playerGobId, avoidHostiles, hostileClearTiles);
	return((plan == null) ? null : new ArrayList<>(plan.waypoints));
    }

    public static PathPlan planSmoothPath(MapView mv, Coord2d to) {
	if(mv == null || to == null || mv.glob == null)
	    return(null);
	Gob pl = mv.player();
	if(pl == null)
	    return(null);
	Coord2d from = playerWorld(pl);
	if(from == null)
	    return(null);
	return(planPath(mv.glob.map, mv.glob.oc, from, to, mv.plgob,
	    MoonConfig.teleportBlockHostileNearTarget, MoonConfig.teleportHostileClearTiles));
    }

    /**
     * Passive LMB mode: returns {@code true} only when the direct segment to the click would hit blocked
     * terrain / obstacle, so the caller can keep normal clicks cheap in open space.
     */
    public static boolean needsDetour(MapView mv, Coord2d to) {
	if(mv == null || to == null || mv.glob == null)
	    return(false);
	Gob pl = mv.player();
	if(pl == null)
	    return(false);
	Coord2d from = playerWorld(pl);
	if(from == null)
	    return(false);
	MCache map = mv.glob.map;
	List<ObstacleFootprint> obstacles = collectObstacles(mv.glob.oc, from, to, mv.plgob,
	    MoonConfig.teleportBlockHostileNearTarget, MoonConfig.teleportHostileClearTiles);
	if(!pointPassable(map, to, obstacles))
	    return(true);
	SearchContext ctx = new SearchContext(map, obstacles, Collections.emptySet(),
	    from.floor(MCache.tilesz), to.floor(MCache.tilesz));
	return(!segmentPassable(ctx, from, to));
    }

    public static PathPlan planPath(MCache map, OCache oc, Coord2d from, Coord2d to, long playerGobId,
				     boolean avoidHostiles, int hostileClearTiles) {
	if(map == null || from == null || to == null)
	    return(null);
	List<ObstacleFootprint> obstacles = collectObstacles(oc, from, to, playerGobId, avoidHostiles, hostileClearTiles);
	Coord2d resolvedGoal = resolveGoal(map, obstacles, from, to);
	if(resolvedGoal == null)
	    return(null);
	Coord startTc = from.floor(MCache.tilesz);
	Coord goalTc = resolvedGoal.floor(MCache.tilesz);
	Set<Coord> blocked = rasterizeObstacles(obstacles, startTc, goalTc);
	blocked.remove(startTc);
	blocked.remove(goalTc);
	SearchContext ctx = new SearchContext(map, obstacles, blocked, startTc, goalTc);
	List<Coord2d> raw = runAStar(ctx, from, resolvedGoal);
	if(raw == null)
	    return(null);
	List<Coord2d> waypoints = simplifyPath(ctx, from, raw);
	if(waypoints == null || waypoints.isEmpty())
	    waypoints = new ArrayList<>(raw);
	List<Coord2d> spline = buildSpline(ctx, from, waypoints);
	return(new PathPlan(from, to, resolvedGoal, to.dist(resolvedGoal) > PATH_REACHED_EPS, raw, waypoints, spline));
    }

    public static boolean sendSmoothMove(MapView mv, PathPlan plan) {
	if(mv == null || plan == null || plan.movePath().isEmpty())
	    return(false);
	cancelActiveSmoothMove();
	SmoothMoveSession sess = new SmoothMoveSession(mv, plan);
	activeSmoothMove = sess;
	sess.start();
	return(true);
    }

    public static boolean sendSmoothMove(MapView mv, Coord2d to) {
	return(sendSmoothMove(mv, planSmoothPath(mv, to)));
    }

    public static void cancelActiveSmoothMove() {
	SmoothMoveSession sess = activeSmoothMove;
	if(sess != null) {
	    sess.cancel();
	    activeSmoothMove = null;
	}
    }

    public static boolean isTilePassableAtWorld(MCache map, Coord2d world) {
	if(map == null || world == null)
	    return(false);
	try {
	    return(tilePassable(map, world.floor(MCache.tilesz)));
	} catch(Exception e) {
	    return(false);
	}
    }

    private static List<Coord2d> runAStar(SearchContext ctx, Coord2d from, Coord2d exactGoal) {
	Coord sc = ctx.startTile;
	Coord gc = ctx.goalTile;
	if(sc.equals(gc)) {
	    List<Coord2d> out = new ArrayList<>(1);
	    out.add(exactGoal);
	    return(out);
	}
	PriorityQueue<Node> open = new PriorityQueue<>();
	Map<Coord, Node> best = new HashMap<>();
	Node start = new Node(sc, null, 0.0, heuristic(sc, gc));
	open.add(start);
	best.put(sc, start);
	int visited = 0;
	int limit = visitLimit(sc, gc);
	while(!open.isEmpty() && visited < limit) {
	    Node cur = open.poll();
	    visited++;
	    Node bestCur = best.get(cur.pos);
	    if(bestCur != cur && bestCur != null && bestCur.g < cur.g - EPS)
		continue;
	    if(cur.pos.equals(gc))
		return(reconstruct(cur, exactGoal));
	    for(int[] d : N8) {
		Coord np = cur.pos.add(d[0], d[1]);
		if(ctx.blockedTiles.contains(np))
		    continue;
		if(!tilePassable(ctx.map, np))
		    continue;
		if(!heightOk(ctx.map, cur.pos, np))
		    continue;
		Coord2d stepCenter = np.equals(gc) ? exactGoal : tileCentre(np);
		if(!pointPassable(ctx.map, stepCenter, ctx.obstacles))
		    continue;
		boolean diag = (d[0] != 0) && (d[1] != 0);
		if(diag) {
		    Coord ax = cur.pos.add(d[0], 0);
		    Coord ay = cur.pos.add(0, d[1]);
		    if(ctx.blockedTiles.contains(ax) || ctx.blockedTiles.contains(ay))
			continue;
		    if(!tilePassable(ctx.map, ax) || !tilePassable(ctx.map, ay))
			continue;
		    if(!heightOk(ctx.map, cur.pos, ax) || !heightOk(ctx.map, cur.pos, ay))
			continue;
		}
		double step = diag ? 1.41421356237 : 1.0;
		double ng = cur.g + step + obstaclePenalty(stepCenter, ctx.obstacles);
		Node prev = best.get(np);
		if(prev != null && prev.g <= ng + EPS)
		    continue;
		Node nn = new Node(np, cur, ng, ng + heuristic(np, gc));
		best.put(np, nn);
		open.add(nn);
	    }
	}
	return(null);
    }

    private static int visitLimit(Coord start, Coord goal) {
	int span = Math.max(Math.abs(start.x - goal.x), Math.abs(start.y - goal.y));
	long side = (long)span + 64L;
	long dynamic = Math.max(BASE_MAX_VISIT, side * side);
	return((dynamic >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)dynamic);
    }

    private static Coord2d resolveGoal(MCache map, List<ObstacleFootprint> obstacles, Coord2d from, Coord2d requested) {
	if(pointPassable(map, requested, obstacles))
	    return(requested);
	Coord startTc = from.floor(MCache.tilesz);
	Coord goalTc = requested.floor(MCache.tilesz);
	List<GoalCandidate> candidates = new ArrayList<>();
	int goalSearch = goalSearchLimit(from, requested);
	for(int r = 0; r <= goalSearch; r++) {
	    ringCandidates(map, obstacles, startTc, goalTc, requested, r, candidates);
	    if(!candidates.isEmpty())
		break;
	}
	if(candidates.isEmpty())
	    return(null);
	candidates.sort(Comparator.comparingDouble((GoalCandidate c) -> c.score)
	    .thenComparingInt(c -> c.tile.y).thenComparingInt(c -> c.tile.x));
	return(tileCentre(candidates.get(0).tile));
    }

    private static void ringCandidates(MCache map, List<ObstacleFootprint> obstacles, Coord startTc, Coord goalTc,
				       Coord2d requested, int radius, List<GoalCandidate> out) {
	int minx = goalTc.x - radius;
	int maxx = goalTc.x + radius;
	int miny = goalTc.y - radius;
	int maxy = goalTc.y + radius;
	for(int tx = minx; tx <= maxx; tx++) {
	    for(int ty = miny; ty <= maxy; ty++) {
		if(radius > 0 && tx > minx && tx < maxx && ty > miny && ty < maxy)
		    continue;
		Coord tc = Coord.of(tx, ty);
		if(!tilePassable(map, tc))
		    continue;
		if(!heightOk(map, startTc, tc))
		    continue;
		Coord2d center = tileCentre(tc);
		if(!pointPassable(map, center, obstacles))
		    continue;
		double score = center.dist(requested) + center.dist(tileCentre(startTc)) * 0.05;
		out.add(new GoalCandidate(tc, score));
	    }
	}
    }

    private static int goalSearchLimit(Coord2d from, Coord2d requested) {
	double distTiles = from.dist(requested) / TILE;
	int dynamic = (int)Math.ceil(Math.max(BASE_GOAL_SEARCH_TILES, distTiles * 0.12));
	return(Math.min(256, dynamic));
    }

    private static List<Coord2d> simplifyPath(SearchContext ctx, Coord2d start, List<Coord2d> raw) {
	if(raw == null || raw.isEmpty())
	    return(Collections.emptyList());
	List<Coord2d> pts = new ArrayList<>(raw.size() + 1);
	pts.add(start);
	pts.addAll(raw);
	List<Coord2d> out = new ArrayList<>();
	int anchor = 0;
	while(anchor < pts.size() - 1) {
	    int best = anchor + 1;
	    for(int i = anchor + 2; i < pts.size(); i++) {
		if(segmentPassable(ctx, pts.get(anchor), pts.get(i)))
		    best = i;
	    }
	    out.add(pts.get(best));
	    anchor = best;
	}
	return(dedupeClose(out));
    }

    private static List<Coord2d> buildSpline(SearchContext ctx, Coord2d start, List<Coord2d> anchors) {
	if(anchors == null || anchors.isEmpty())
	    return(Collections.emptyList());
	if(anchors.size() == 1)
	    return(new ArrayList<>(anchors));
	List<Coord2d> ctrl = new ArrayList<>(anchors.size() + 1);
	ctrl.add(start);
	ctrl.addAll(anchors);
	List<Coord2d> out = new ArrayList<>();
	Coord2d prevAccepted = start;
	for(int i = 0; i < ctrl.size() - 1; i++) {
	    Coord2d p0 = (i > 0) ? ctrl.get(i - 1) : ctrl.get(i);
	    Coord2d p1 = ctrl.get(i);
	    Coord2d p2 = ctrl.get(i + 1);
	    Coord2d p3 = (i + 2 < ctrl.size()) ? ctrl.get(i + 2) : ctrl.get(i + 1);
	    int steps = Math.max(1, (int)Math.ceil(p1.dist(p2) / SPLINE_SAMPLE_STEP));
	    ArrayList<Coord2d> seg = new ArrayList<>(steps);
	    Coord2d localPrev = prevAccepted;
	    boolean ok = true;
	    for(int s = 1; s <= steps; s++) {
		double t = s / (double)steps;
		Coord2d pt = ((i == 0) || (i == ctrl.size() - 2)) ? lerp(p1, p2, t) : catmullRom(p0, p1, p2, p3, t);
		if(!finite(pt) || !pointPassable(ctx.map, pt, ctx.obstacles) || !segmentPassable(ctx, localPrev, pt)) {
		    ok = false;
		    break;
		}
		if(seg.isEmpty() || seg.get(seg.size() - 1).dist(pt) >= SAMPLE_MIN_SEPARATION)
		    seg.add(pt);
		localPrev = pt;
	    }
	    if(!ok) {
		appendLinearSamples(ctx, out, prevAccepted, p2);
	    } else {
		for(Coord2d pt : seg)
		    appendDedupe(out, pt);
	    }
	    if(!out.isEmpty())
		prevAccepted = out.get(out.size() - 1);
	}
	if(out.isEmpty() || out.get(out.size() - 1).dist(anchors.get(anchors.size() - 1)) > SAMPLE_MIN_SEPARATION)
	    appendLinearSamples(ctx, out, prevAccepted, anchors.get(anchors.size() - 1));
	return(dedupeClose(out));
    }

    private static void appendLinearSamples(SearchContext ctx, List<Coord2d> out, Coord2d from, Coord2d to) {
	double len = from.dist(to);
	int steps = Math.max(1, (int)Math.ceil(len / SPLINE_SAMPLE_STEP));
	Coord2d prev = from;
	for(int i = 1; i <= steps; i++) {
	    Coord2d pt = lerp(from, to, i / (double)steps);
	    if(!finite(pt))
		continue;
	    if(pointPassable(ctx.map, pt, ctx.obstacles) && segmentPassable(ctx, prev, pt)) {
		appendDedupe(out, pt);
		prev = pt;
	    }
	}
	appendDedupe(out, to);
    }

    private static void appendDedupe(List<Coord2d> out, Coord2d pt) {
	if(pt == null)
	    return;
	if(out.isEmpty() || out.get(out.size() - 1).dist(pt) > SAMPLE_MIN_SEPARATION)
	    out.add(pt);
	else
	    out.set(out.size() - 1, pt);
    }

    private static List<Coord2d> reconstruct(Node goal, Coord2d exactGoal) {
	List<Node> rev = new ArrayList<>();
	for(Node n = goal; n != null; n = n.parent)
	    rev.add(n);
	Collections.reverse(rev);
	List<Coord2d> path = new ArrayList<>(Math.max(1, rev.size()));
	for(int i = 1; i < rev.size(); i++)
	    path.add(tileCentre(rev.get(i).pos));
	if(path.isEmpty())
	    path.add(exactGoal);
	else
	    path.set(path.size() - 1, exactGoal);
	return(path);
    }

    private static List<ObstacleFootprint> collectObstacles(OCache oc, Coord2d from, Coord2d to, long playerGobId,
							    boolean avoidHostiles, int hostileClearTiles) {
	if(oc == null)
	    return(Collections.emptyList());
	ArrayList<Gob> gobs = new ArrayList<>();
	synchronized(oc) {
	    for(Gob g : oc)
		gobs.add(g);
	}
	double scanMargin = Math.max(OBSTACLE_SCAN_MARGIN, from.dist(to) * OBSTACLE_SCAN_REL_MARGIN);
	double minx = Math.min(from.x, to.x) - scanMargin;
	double maxx = Math.max(from.x, to.x) + scanMargin;
	double miny = Math.min(from.y, to.y) - scanMargin;
	double maxy = Math.max(from.y, to.y) + scanMargin;
	ArrayList<ObstacleFootprint> out = new ArrayList<>();
	for(Gob gob : gobs) {
	    if(gob == null || gob.removed || gob.virtual || gob.id == playerGobId)
		continue;
	    Coord2d center = gob.rc;
	    if(center == null)
		continue;
	    if(avoidHostiles && MoonOverlay.isThreatMob(gob)) {
		double rr = Math.max(TILE, hostileClearTiles * TILE);
		if(intersectsBounds(center, rr, minx, maxx, miny, maxy))
		    out.add(new ObstacleFootprint(gob.id, center, rr, 0.0, 0.0, 0.0, null));
		continue;
	    }
	    CollisionRadius cr = CollisionRadius.get(gob);
	    if(cr == null)
		continue;
	    if(!intersectsBounds(center, cr.radius + OBSTACLE_SOFT_PADDING, minx, maxx, miny, maxy))
		continue;
	    out.add(new ObstacleFootprint(gob.id, center, cr.radius,
		OBSTACLE_HARD_PADDING, OBSTACLE_SOFT_PADDING, gob.a, cr.footprint));
	}
	return(out);
    }

    private static boolean intersectsBounds(Coord2d center, double radius, double minx, double maxx, double miny, double maxy) {
	return !(center.x + radius < minx || center.x - radius > maxx || center.y + radius < miny || center.y - radius > maxy);
    }

    private static Set<Coord> rasterizeObstacles(List<ObstacleFootprint> obstacles, Coord startTc, Coord goalTc) {
	Set<Coord> blocked = new HashSet<>();
	for(ObstacleFootprint ob : obstacles) {
	    if(ob.hasPolys()) {
		for(Coord2d[] poly : ob.localPolys)
		    rasterizePoly(poly, ob, blocked, startTc, goalTc);
	    } else {
		rasterizeCircle(ob.center, ob.baseRadius + ob.hardPadding, blocked, startTc, goalTc);
	    }
	}
	return(blocked);
    }

    private static void rasterizeCircle(Coord2d center, double radius, Set<Coord> blocked, Coord startTc, Coord goalTc) {
	if(center == null || radius <= EPS)
	    return;
	int minTx = (int)Math.floor((center.x - radius) / TILE) - 1;
	int maxTx = (int)Math.ceil((center.x + radius) / TILE) + 1;
	int minTy = (int)Math.floor((center.y - radius) / TILE) - 1;
	int maxTy = (int)Math.ceil((center.y + radius) / TILE) + 1;
	double r2 = radius * radius;
	for(int tx = minTx; tx <= maxTx; tx++) {
	    for(int ty = minTy; ty <= maxTy; ty++) {
		Coord tc = Coord.of(tx, ty);
		if(tc.equals(startTc) || tc.equals(goalTc))
		    continue;
		double minx = tx * TILE;
		double miny = ty * TILE;
		double maxx = minx + TILE;
		double maxy = miny + TILE;
		double dx = center.x - Utils.clip(center.x, minx, maxx);
		double dy = center.y - Utils.clip(center.y, miny, maxy);
		if((dx * dx) + (dy * dy) <= r2)
		    blocked.add(tc);
	    }
	}
    }

    private static void rasterizePoly(Coord2d[] poly, ObstacleFootprint ob, Set<Coord> blocked, Coord startTc, Coord goalTc) {
	if(poly == null || poly.length < 2)
	    return;
	Coord2d[] world = new Coord2d[poly.length];
	double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
	double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
	for(int i = 0; i < poly.length; i++) {
	    Coord2d p = poly[i];
	    double wx = (p.x * ob.ca) - (p.y * ob.sa) + ob.center.x;
	    double wy = (p.y * ob.ca) + (p.x * ob.sa) + ob.center.y;
	    world[i] = Coord2d.of(wx, wy);
	    minX = Math.min(minX, wx);
	    maxX = Math.max(maxX, wx);
	    minY = Math.min(minY, wy);
	    maxY = Math.max(maxY, wy);
	}
	int tMinX = (int)Math.floor(minX / TILE) - 1;
	int tMaxX = (int)Math.ceil(maxX / TILE) + 1;
	int tMinY = (int)Math.floor(minY / TILE) - 1;
	int tMaxY = (int)Math.ceil(maxY / TILE) + 1;
	for(int tx = tMinX; tx <= tMaxX; tx++) {
	    for(int ty = tMinY; ty <= tMaxY; ty++) {
		Coord tc = Coord.of(tx, ty);
		if(tc.equals(startTc) || tc.equals(goalTc))
		    continue;
		if(polyIntersectsTile(world, tx, ty))
		    blocked.add(tc);
	    }
	}
    }

    private static boolean pointPassable(MCache map, Coord2d world, List<ObstacleFootprint> obstacles) {
	if(map == null || world == null)
	    return(false);
	Coord tc = world.floor(MCache.tilesz);
	if(!tilePassable(map, tc))
	    return(false);
	for(ObstacleFootprint ob : obstacles) {
	    if(pointBlockedByObstacle(world, ob))
		return(false);
	}
	return(true);
    }

    private static boolean pointBlockedByObstacle(Coord2d world, ObstacleFootprint ob) {
	return(ob != null && world != null && obstacleBoundaryDistance(world, ob) <= ob.hardPadding + EPS);
    }

    private static double obstaclePenalty(Coord2d world, List<ObstacleFootprint> obstacles) {
	if(world == null || obstacles == null || obstacles.isEmpty())
	    return(0.0);
	double penalty = 0.0;
	for(ObstacleFootprint ob : obstacles) {
	    if(ob == null || ob.softPadding <= ob.hardPadding + EPS)
		continue;
	    if(world.dist(ob.center) > ob.broadRadius + TILE)
		continue;
	    double clearance = obstacleBoundaryDistance(world, ob);
	    if(clearance >= ob.softPadding)
		continue;
	    double band = Math.max(EPS, ob.softPadding - ob.hardPadding);
	    double k = 1.0 - Utils.clip((clearance - ob.hardPadding) / band, 0.0, 1.0);
	    penalty += (k * k) * OBSTACLE_SOFT_COST;
	    if(penalty >= OBSTACLE_SOFT_COST_CAP)
		return(OBSTACLE_SOFT_COST_CAP);
	}
	return(penalty);
    }

    private static double obstacleBoundaryDistance(Coord2d world, ObstacleFootprint ob) {
	if(ob == null || world == null)
	    return(Double.MAX_VALUE);
	if(!ob.hasPolys())
	    return(Math.max(0.0, world.dist(ob.center) - ob.baseRadius));
	Coord2d local = worldToLocal(world, ob);
	double best = Double.MAX_VALUE;
	for(Coord2d[] poly : ob.localPolys) {
	    if(poly == null || poly.length < 2)
		continue;
	    if(pointInPoly(local.x, local.y, poly))
		return(0.0);
	    best = Math.min(best, Math.sqrt(distanceToPolySq(local, poly)));
	}
	if(best < Double.MAX_VALUE)
	    return(best);
	return(Math.max(0.0, world.dist(ob.center) - ob.baseRadius));
    }

    private static boolean segmentPassable(SearchContext ctx, Coord2d a, Coord2d b) {
	if(a == null || b == null)
	    return(false);
	double len = a.dist(b);
	if(len <= EPS)
	    return(pointPassable(ctx.map, b, ctx.obstacles));
	int steps = Math.max(1, (int)Math.ceil(len / LOS_SAMPLE_STEP));
	Coord prevTc = a.floor(MCache.tilesz);
	for(int i = 1; i <= steps; i++) {
	    Coord2d cur = lerp(a, b, i / (double)steps);
	    Coord curTc = cur.floor(MCache.tilesz);
	    if(!tilePassable(ctx.map, curTc))
		return(false);
	    if(ctx.blockedTiles.contains(curTc) && !curTc.equals(ctx.goalTile))
		return(false);
	    if(!pointPassable(ctx.map, cur, ctx.obstacles))
		return(false);
	    if(!heightOk(ctx.map, prevTc, curTc))
		return(false);
	    prevTc = curTc;
	}
	return(true);
    }

    private static Coord2d worldToLocal(Coord2d world, ObstacleFootprint ob) {
	double dx = world.x - ob.center.x;
	double dy = world.y - ob.center.y;
	return Coord2d.of((dx * ob.ca) + (dy * ob.sa), (-dx * ob.sa) + (dy * ob.ca));
    }

    private static Coord2d tileCentre(Coord tc) {
	return(Coord2d.of((tc.x + 0.5) * TILE, (tc.y + 0.5) * TILE));
    }

    private static boolean tilePassable(MCache map, Coord tc) {
	try {
	    int tid = map.gettile(tc);
	    Resource res = map.tilesetr(tid);
	    if(res == null)
		return(false);
	    String name = res.name;
	    if(name == null)
		return(false);
	    if(BLOCKED_TILES.contains(name))
		return(false);
	    if(DEEP_WATER.contains(name))
		return(false);
	    if(BOG_TILES.contains(name))
		return(false);
	    if(name.startsWith("gfx/tiles/rocks/"))
		return(false);
	    return(true);
	} catch(Loading e) {
	    return(false);
	}
    }

    private static boolean heightOk(MCache map, Coord a, Coord b) {
	if(Objects.equals(a, b))
	    return(true);
	try {
	    double za = map.getcz(tileCentre(a));
	    double zb = map.getcz(tileCentre(b));
	    return(Math.abs(za - zb) <= MAX_DZ);
	} catch(Loading e) {
	    return(false);
	}
    }

    private static double heuristic(Coord a, Coord b) {
	int dx = Math.abs(a.x - b.x);
	int dy = Math.abs(a.y - b.y);
	return(Math.max(dx, dy) + 0.41421356237 * Math.min(dx, dy));
    }

    private static Coord2d playerWorld(Gob pl) {
	if(pl == null)
	    return(null);
	try {
	    return(Coord2d.of(pl.getc()));
	} catch(Loading e) {
	    return(pl.rc);
	}
    }

    private static boolean reachedGoal(Coord2d a, Coord2d b) {
	return(a != null && b != null && a.dist(b) <= PATH_REACHED_EPS);
    }

    private static Coord2d lerp(Coord2d a, Coord2d b, double t) {
	return(Coord2d.of(a.x + ((b.x - a.x) * t), a.y + ((b.y - a.y) * t)));
    }

    private static Coord2d catmullRom(Coord2d p0, Coord2d p1, Coord2d p2, Coord2d p3, double t) {
	double t2 = t * t;
	double t3 = t2 * t;
	double x = 0.5 * ((2.0 * p1.x) + ((-p0.x + p2.x) * t)
	    + ((2.0 * p0.x) - (5.0 * p1.x) + (4.0 * p2.x) - p3.x) * t2
	    + ((-p0.x) + (3.0 * p1.x) - (3.0 * p2.x) + p3.x) * t3);
	double y = 0.5 * ((2.0 * p1.y) + ((-p0.y + p2.y) * t)
	    + ((2.0 * p0.y) - (5.0 * p1.y) + (4.0 * p2.y) - p3.y) * t2
	    + ((-p0.y) + (3.0 * p1.y) - (3.0 * p2.y) + p3.y) * t3);
	return(Coord2d.of(x, y));
    }

    private static List<Coord2d> dedupeClose(List<Coord2d> src) {
	if(src == null || src.isEmpty())
	    return(Collections.emptyList());
	ArrayList<Coord2d> out = new ArrayList<>(src.size());
	for(Coord2d pt : src) {
	    if(pt == null)
		continue;
	    if(out.isEmpty() || out.get(out.size() - 1).dist(pt) > SAMPLE_MIN_SEPARATION)
		out.add(pt);
	    else
		out.set(out.size() - 1, pt);
	}
	return(out);
    }

    private static List<Coord2d> freeze(List<Coord2d> src) {
	if(src == null || src.isEmpty())
	    return(Collections.emptyList());
	return(Collections.unmodifiableList(new ArrayList<>(src)));
    }

    private static boolean finite(Coord2d c) {
	return(c != null && Double.isFinite(c.x) && Double.isFinite(c.y));
    }

    private static boolean pointInPoly(double px, double py, Coord2d[] poly) {
	boolean inside = false;
	for(int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
	    double yi = poly[i].y, yj = poly[j].y;
	    double xi = poly[i].x, xj = poly[j].x;
	    if(((yi > py) != (yj > py)) && (px < ((xj - xi) * (py - yi) / (yj - yi)) + xi))
		inside = !inside;
	}
	return(inside);
    }

    private static double distanceToPolySq(Coord2d p, Coord2d[] poly) {
	double best = Double.MAX_VALUE;
	for(int i = 0; i < poly.length; i++) {
	    Coord2d a = poly[i];
	    Coord2d b = poly[(i + 1) % poly.length];
	    best = Math.min(best, pointSegmentDistSq(p, a, b));
	}
	return(best);
    }

    private static double pointSegmentDistSq(Coord2d p, Coord2d a, Coord2d b) {
	double vx = b.x - a.x;
	double vy = b.y - a.y;
	double wx = p.x - a.x;
	double wy = p.y - a.y;
	double denom = (vx * vx) + (vy * vy);
	if(denom <= EPS)
	    return((wx * wx) + (wy * wy));
	double t = Utils.clip(((wx * vx) + (wy * vy)) / denom, 0.0, 1.0);
	double dx = p.x - (a.x + (vx * t));
	double dy = p.y - (a.y + (vy * t));
	return((dx * dx) + (dy * dy));
    }

    private static boolean polyIntersectsTile(Coord2d[] poly, int tx, int ty) {
	double minX = tx * TILE;
	double minY = ty * TILE;
	double maxX = minX + TILE;
	double maxY = minY + TILE;
	for(Coord2d p : poly) {
	    if(pointInRect(p.x, p.y, minX, minY, maxX, maxY))
		return(true);
	}
	Coord2d[] rect = {
	    Coord2d.of(minX, minY),
	    Coord2d.of(maxX, minY),
	    Coord2d.of(maxX, maxY),
	    Coord2d.of(minX, maxY)
	};
	for(Coord2d p : rect) {
	    if(pointInPoly(p.x, p.y, poly))
		return(true);
	}
	for(int i = 0; i < poly.length; i++) {
	    Coord2d a = poly[i];
	    Coord2d b = poly[(i + 1) % poly.length];
	    for(int j = 0; j < rect.length; j++) {
		Coord2d c = rect[j];
		Coord2d d = rect[(j + 1) % rect.length];
		if(segmentsIntersect(a, b, c, d))
		    return(true);
	    }
	}
	return(false);
    }

    private static boolean pointInRect(double px, double py, double minX, double minY, double maxX, double maxY) {
	return(px >= minX - EPS && px <= maxX + EPS && py >= minY - EPS && py <= maxY + EPS);
    }

    private static boolean segmentsIntersect(Coord2d a, Coord2d b, Coord2d c, Coord2d d) {
	double o1 = orient(a, b, c);
	double o2 = orient(a, b, d);
	double o3 = orient(c, d, a);
	double o4 = orient(c, d, b);
	if(((o1 > EPS && o2 < -EPS) || (o1 < -EPS && o2 > EPS)) &&
	   ((o3 > EPS && o4 < -EPS) || (o3 < -EPS && o4 > EPS)))
	    return(true);
	if(Math.abs(o1) <= EPS && onSegment(a, c, b))
	    return(true);
	if(Math.abs(o2) <= EPS && onSegment(a, d, b))
	    return(true);
	if(Math.abs(o3) <= EPS && onSegment(c, a, d))
	    return(true);
	if(Math.abs(o4) <= EPS && onSegment(c, b, d))
	    return(true);
	return(false);
    }

    private static double orient(Coord2d a, Coord2d b, Coord2d c) {
	return(((b.x - a.x) * (c.y - a.y)) - ((b.y - a.y) * (c.x - a.x)));
    }

    private static boolean onSegment(Coord2d a, Coord2d p, Coord2d b) {
	return(p.x >= Math.min(a.x, b.x) - EPS && p.x <= Math.max(a.x, b.x) + EPS &&
	       p.y >= Math.min(a.y, b.y) - EPS && p.y <= Math.max(a.y, b.y) + EPS);
    }
}
