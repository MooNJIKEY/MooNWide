package haven;

import static haven.OCache.posres;

import core.Logger;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Directional mine bot: digs along the compass direction (N/E/S/W). Corridor scoring uses a
 * virtual target far ahead of the player so candidates stay in a forward strip — no map stop tile.
 */
public final class MoonMineBot {
    private MoonMineBot() {}

    public static final int MODE_TUNNEL = 0;
    public static final int MODE_SAPPER = 1;

    public static final int DIR_N = 0;
    public static final int DIR_E = 1;
    public static final int DIR_S = 2;
    public static final int DIR_W = 3;

    private static final double ACTION_INTERVAL = 0.20;
    private static final double INTERACT_DIST = MCache.tilesz.x * 2.70;
    private static final double WALK_STEP = MCache.tilesz.x * 2.2;
    private static final double MOB_THREAT_DIST = MCache.tilesz.x * 10.0;
    private static final double STALL_GIVEUP_SEC = 8.5;
    private static final double RETRY_CLICK_SEC = 0.85;
    private static final double STAND_REACHED_DIST = MCache.tilesz.x * 0.96;
    private static final float WALL_GOB_Z_BOOST = 12f;
    private static final int SCAN_RADIUS = 12;
    /** Virtual endpoint (tiles) along {@link #dirVec()} for corridor math when no stop tile is set. */
    private static final int VIRTUAL_TARGET_TILES = 320;

    private static final int[][] ORTH = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static final class SupportType {
	public final int id;
	public final int radius;
	public final String ru;
	public final String en;

	private SupportType(int id, int radius, String ru, String en) {
	    this.id = id;
	    this.radius = radius;
	    this.ru = ru;
	    this.en = en;
	}

	public String label() {
	    return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
	}
    }

    private static final SupportType[] SUPPORT_TYPES = {
	new SupportType(0, 0, "Без подпорки", "No support"),
	new SupportType(1, 9, "Mine Support (r:9)", "Mine Support (r:9)"),
	new SupportType(2, 11, "Stone Column (r:11)", "Stone Column (r:11)"),
	new SupportType(3, 13, "Mine Beam (r:13)", "Mine Beam (r:13)")
    };

    private static final class Candidate {
	final Coord wallTile;
	final Coord standTile;
	final double score;

	Candidate(Coord wallTile, Coord standTile, double score) {
	    this.wallTile = wallTile;
	    this.standTile = standTile;
	    this.score = score;
	}
    }

    private static double lastAction = 0;
    private static double lastMobFlee = 0;
    private static double lastMineCursorAttempt = 0;
    private static Coord miningTargetTile = null;
    private static double miningSince = 0;
    private static double lastMineClick = 0;
    private static Coord pendingSupportTile = null;

    private static double mineDiagLastTs = 0;
    private static String mineDiagLastKey = "";
    private static boolean mineDiagPathLogged = false;

    /** Throttled line to stdout when {@link MoonConfig#mineBotDiagLog} is on. */
    private static void mineDiag(double minInterval, String key, String msg) {
	if(!MoonConfig.mineBotDiagLog)
	    return;
	double t = Utils.rtime();
	if(key != null && key.equals(mineDiagLastKey) && (t - mineDiagLastTs) < minInterval)
	    return;
	mineDiagLastKey = key != null ? key : "";
	mineDiagLastTs = t;
	mineDiagEcho(msg);
    }

    private static void mineDiagAlways(String msg) {
	if(MoonConfig.mineBotDiagLog)
	    mineDiagEcho(msg);
    }

    /** Console + {@code ~/minebot-diag.log} when diagnostics checkbox is on. */
    private static void mineDiagEcho(String msg) {
	Logger.log("MineBot", msg);
	if(!MoonConfig.mineBotDiagLog)
	    return;
	try {
	    Path path = Paths.get(System.getProperty("user.home", "."), "minebot-diag.log");
	    Files.write(path,
		(Instant.now() + " " + msg + "\n").getBytes(StandardCharsets.UTF_8),
		StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	} catch(Exception ignored) {
	}
    }

    public static void resetState() {
	lastAction = 0;
	lastMobFlee = 0;
	miningTargetTile = null;
	miningSince = 0;
	lastMineClick = 0;
	pendingSupportTile = null;
	mineDiagLastKey = "";
	if(MoonConfig.mineBotDiagLog)
	    mineDiagEcho("resetState()");
    }

    public static SupportType supportType() {
	return supportType(MoonConfig.mineBotSupportType);
    }

    public static SupportType supportType(int id) {
	id = Utils.clip(id, 0, SUPPORT_TYPES.length - 1);
	return SUPPORT_TYPES[id];
    }

    public static Coord dirVec() {
	return dirVec(MoonConfig.mineBotDirection);
    }

    public static Coord dirVec(int dir) {
	switch(Math.floorMod(dir, 4)) {
	case DIR_E: return Coord.of(1, 0);
	case DIR_S: return Coord.of(0, 1);
	case DIR_W: return Coord.of(-1, 0);
	default:    return Coord.of(0, -1);
	}
    }

    public static Coord perpVec(int dir) {
	Coord d = dirVec(dir);
	return Coord.of(-d.y, d.x);
    }

    public static String modeLabel() {
	return (MoonConfig.mineBotMode == MODE_SAPPER)
	    ? mineText("Сапёр", "Sapper")
	    : mineText("Туннель", "Tunnel");
    }

    public static String directionLabel() {
	switch(Math.floorMod(MoonConfig.mineBotDirection, 4)) {
	case DIR_E: return mineText("Восток", "East");
	case DIR_S: return mineText("Юг", "South");
	case DIR_W: return mineText("Запад", "West");
	default:    return mineText("Север", "North");
	}
    }

    public static void tick(GameUI gui, double dt) {
	if(gui == null || !MoonConfig.mineBotEnabled)
	    return;
	MapView map = gui.map;
	if(map == null || map.glob == null || map.glob.map == null)
	    return;
	Gob pl = map.player();
	if(pl == null)
	    return;

	double now = Utils.rtime();
	if(MoonConfig.mineBotDiagLog && !mineDiagPathLogged) {
	    mineDiagPathLogged = true;
	    Path p = Paths.get(System.getProperty("user.home", "."), "minebot-diag.log");
	    mineDiagEcho("diagnostics log (append): " + p.toAbsolutePath()
		+ " | tags: flee, walk-lock, walk-new, no-cand, stam-gate, clickMine, busy");
	}
	if(MoonConfig.mineBotAvoidMobs && fleeFromThreat(map, gui, pl, now)) {
	    mineDiag(0.8, "flee", "fleeFromThreat — retreat click (mining lock kept)");
	    return;
	}

	if(MoonAutoDrink.pendingFlower) {
	    mineDiag(1.0, "flower", "pendingFlower (drink UI) — wait");
	    return;
	}
	if(MoonAutoDrink.tryDrinkBelowPercent(gui, MoonConfig.mineBotStaminaMinPct, MoonConfig.mineBotWater)) {
	    lastAction = now;
	    mineDiag(1.2, "drink", "tryDrinkBelowPercent — tick");
	    return;
	}
	int st = MoonAutoDrink.getCurrentStamina(gui);
	if(st >= 0 && st < MoonConfig.mineBotStaminaMinPct) {
	    mineDiag(1.0, "stam-gate", "stamina " + st + "% < min " + MoonConfig.mineBotStaminaMinPct + "% — idle");
	    return;
	}

	Set<Coord> supportedTiles = MoonMiningOverlay.supportedTileSnapshot(map);
	if(supportedTiles.isEmpty())
	    supportedTiles = Collections.emptySet();

	try {
	    Coord dir = dirVec();
	    Coord ptc = pl.rc.floor(MCache.tilesz);
	    Coord corridorTarget = corridorTargetFromPlayer(ptc, dir);

	    Coord supportStop = null;
	    /* Corridor geometry must use the far virtual target; using supportStop here cuts off walls
	     * ahead of the next support (remain<0 in insideDirectionalCorridor). */
	    Coord pickTarget = corridorTarget;
	    if(MoonConfig.mineBotMode == MODE_TUNNEL) {
		supportStop = plannedSupportStop(map, pl, corridorTarget, dir);
		pendingSupportTile = supportStop;
	    } else {
		pendingSupportTile = null;
	    }

	    if(miningTargetTile != null) {
		if(isMinedOut(map.glob.map, miningTargetTile)) {
		    mineDiagAlways("wall tile " + miningTargetTile + " mined out (floor) — unlock");
		    miningTargetTile = null;
		    miningSince = 0;
		} else {
		    Candidate active = pickCandidate(map, pl, pickTarget, supportedTiles, miningTargetTile);
		    if(active == null) {
			mineDiagAlways("lost candidate for locked wall " + miningTargetTile + " (mode="
			    + modeLabel() + " pickTarget=" + pickTarget + " supportStop=" + supportStop + ") — unlock");
			miningTargetTile = null;
			miningSince = 0;
			if(supportStop != null && handlePendingSupport(gui, map, pl, supportStop, now))
			    return;
			return;
		    }
		    if(playerIsMining(pl) || progressBusy(gui)) {
			mineDiag(0.45, "busy-lock", "player mining or progress bar — hold lock wall=" + miningTargetTile);
			return;
		    }
		    Coord2d goal = tileCenterWorld(active.standTile);
		    double dist = pl.rc.dist(goal);
		    boolean canDig = dist <= STAND_REACHED_DIST
			|| closeEnoughToMineFace(pl, active.wallTile, active.standTile, dir);
		    if(!canDig) {
			if(now - lastAction < ACTION_INTERVAL)
			    return;
			boolean wok = walkTowardStandoff(map, gui, pl.rc, goal, dir);
			mineDiag(0.35, "walk-lock", String.format(
			    "walk to stand %s for wall %s dist=%.1f need<=%.1f clickGround=%s",
			    active.standTile, active.wallTile, dist, STAND_REACHED_DIST, wok ? "ok" : "FAIL"));
			lastAction = now;
			return;
		    }
		    if(now - miningSince > STALL_GIVEUP_SEC) {
			mineDiagAlways("stall give-up " + STALL_GIVEUP_SEC + "s on wall " + miningTargetTile + " — unlock");
			miningTargetTile = null;
			miningSince = 0;
		    } else if(now - lastMineClick >= RETRY_CLICK_SEC) {
			if(clickMineTile(map, gui, pl, active.wallTile, active.standTile))
			    lastMineClick = now;
			lastAction = now;
		    }
		    return;
		}
	    }

	    if(playerIsMining(pl) || progressBusy(gui)) {
		mineDiag(0.45, "busy", "player mining or progress — no new pick");
		return;
	    }
	    if(now - lastAction < ACTION_INTERVAL)
		return;

	    Candidate next = pickCandidate(map, pl, pickTarget, supportedTiles, null);
	    if(next == null) {
		mineDiag(0.55, "no-cand", String.format(
		    "no candidate playerTile=%s dir=%s mode=%s pickTarget=%s supportStop=%s prog=%s mining=%s",
		    ptc, dir, MoonConfig.mineBotMode, pickTarget, supportStop,
		    (gui.prog != null) ? String.format("%.3f", gui.prog.prog) : "null",
		    playerIsMining(pl)));
		if(supportStop != null && handlePendingSupport(gui, map, pl, supportStop, now))
		    return;
		return;
	    }
	    Coord2d goal = tileCenterWorld(next.standTile);
	    double d = pl.rc.dist(goal);
	    boolean canDig = d <= STAND_REACHED_DIST
		|| closeEnoughToMineFace(pl, next.wallTile, next.standTile, dir);
	    if(!canDig) {
		boolean wok = walkTowardStandoff(map, gui, pl.rc, goal, dir);
		mineDiag(0.35, "walk-new", String.format(
		    "walk to stand %s for wall %s dist=%.1f need<=%.1f clickGround=%s",
		    next.standTile, next.wallTile, d, STAND_REACHED_DIST, wok ? "ok" : "FAIL"));
		lastAction = now;
		return;
	    }
	    if(clickMineTile(map, gui, pl, next.wallTile, next.standTile)) {
		mineDiagAlways("locked target wall=" + next.wallTile + " stand=" + next.standTile);
		miningTargetTile = next.wallTile;
		miningSince = now;
		lastMineClick = now;
		lastAction = now;
	    } else {
		mineDiagAlways("first clickMine FAILED wall=" + next.wallTile + " stand=" + next.standTile + " — no lock");
	    }
	} catch(Loading ignored) {
	} catch(Exception e) {
	    new Warning(e, "MoonMineBot").issue();
	}
    }

    public static String statusHudLine(GameUI gui) {
	if(gui == null || !MoonConfig.mineBotEnabled || !MoonConfig.mineBotHudLine)
	    return null;
	int st = MoonAutoDrink.getCurrentStamina(gui);
	String stam = (st < 0) ? "?" : (st + "%");
	StringBuilder x = new StringBuilder(96);
	x.append(modeLabel()).append(" ").append(directionLabel());
	x.append(" | stam ").append(stam);
	x.append(" | ").append(mineText("компас", "compass"));
	if(miningTargetTile != null)
	    x.append(" | dig ").append(miningTargetTile.x).append(",").append(miningTargetTile.y);
	if(pendingSupportTile != null)
	    x.append(" | sup ").append(pendingSupportTile.x).append(",").append(pendingSupportTile.y);
	return x.toString();
    }

    private static Candidate pickCandidate(MapView map, Gob pl, Coord target, Set<Coord> supportedTiles, Coord forceWall) {
	MCache cache = map.glob.map;
	Coord ptc = pl.rc.floor(MCache.tilesz);
	Coord dir = dirVec();
	int width = (MoonConfig.mineBotMode == MODE_SAPPER) ? 1 : 0;
	Candidate best = null;
	double bestScore = Double.MAX_VALUE;
	int minx = ptc.x - SCAN_RADIUS;
	int maxx = ptc.x + SCAN_RADIUS;
	int miny = ptc.y - SCAN_RADIUS;
	int maxy = ptc.y + SCAN_RADIUS;
	for(int y = miny; y <= maxy; y++) {
	    for(int x = minx; x <= maxx; x++) {
		Coord tc = Coord.of(x, y);
		if(forceWall != null && !forceWall.equals(tc))
		    continue;
		if(!isDirectionalCandidateTile(cache, tc, target, ptc, dir, width, supportedTiles))
		    continue;
		Coord stand = preferredStandBehindWall(cache, tc, dir);
		if(stand == null)
		    stand = bestStandTile(cache, pl, tc, target, dir);
		if(stand == null)
		    continue;
		double score = candidateScore(pl, tc, stand, target, dir, supportedTiles);
		if(score < bestScore) {
		    best = new Candidate(tc, stand, score);
		    bestScore = score;
		}
	    }
	}
	return best;
    }

    private static boolean isDirectionalCandidateTile(MCache map, Coord tc, Coord target, Coord ptc, Coord dir, int width, Set<Coord> supportedTiles) {
	if(!looksLikeMineWallTile(map, tc))
	    return false;
	if(!hasExposedFloorNeighbor(map, tc, ptc))
	    return false;
	if(!insideDirectionalCorridor(tc, target, ptc, dir, width))
	    return false;
	byte state = MoonMineSweeperData.state(tc);
	boolean supported = supportedTiles.contains(tc);
	/* Tunnel and sapper use the same wall eligibility; only corridor half-width differs (0 vs 1). */
	if(state == MoonMineSweeperData.RISK && !supported)
	    return false;
	return true;
    }

    private static boolean insideDirectionalCorridor(Coord tc, Coord target, Coord ptc, Coord dir, int width) {
	int ahead = forwardDist(ptc, tc, dir);
	if(ahead < -1)
	    return false;
	int remain = forwardDist(tc, target, dir);
	if(remain < 0)
	    return false;
	int perp = perpDist(tc, target, dir);
	return perp <= width;
    }

    private static double candidateScore(Gob pl, Coord wall, Coord stand, Coord target, Coord dir, Set<Coord> supportedTiles) {
	int remain = Math.max(0, forwardDist(wall, target, dir));
	int ahead = Math.max(0, forwardDist(pl.rc.floor(MCache.tilesz), wall, dir));
	int perp = perpDist(wall, target, dir);
	int pri = candidatePriority(wall, supportedTiles);
	double walk = pl.rc.dist(tileCenterWorld(stand));
	return pri * 1000000.0 + remain * 140.0 + perp * 260.0 + ahead * 18.0 + walk * 1.2;
    }

    private static int candidatePriority(Coord tc, Set<Coord> supportedTiles) {
	if(supportedTiles.contains(tc))
	    return 0;
	byte st = MoonMineSweeperData.state(tc);
	if(st == MoonMineSweeperData.SAFE || st == MoonMineSweeperData.AUTO_SAFE)
	    return 1;
	if(st == MoonMineSweeperData.UNKNOWN)
	    return (MoonConfig.mineBotMode == MODE_SAPPER) ? 3 : 2;
	return 9;
    }

    /** Floor tile on the tunnel side of the wall (step opposite to dig direction). */
    private static Coord preferredStandBehindWall(MCache map, Coord wall, Coord dir) {
	Coord back = wall.sub(dir);
	if(MoonMiningOverlay.isCaveMineFloorTile(map, back))
	    return back;
	return null;
    }

    /**
     * Stand only on the tunnel side of the wall ({@code forwardDist(wall, stand, dir) < 0}), so we
     * never path into rock because a floor tile exists on the wrong face.
     */
    private static Coord bestStandTile(MCache map, Gob pl, Coord wall, Coord target, Coord dir) {
	Coord ptc = pl.rc.floor(MCache.tilesz);
	Coord best = null;
	double bestScore = Double.MAX_VALUE;
	for(int[] d : ORTH) {
	    Coord tc = wall.add(d[0], d[1]);
	    if(!MoonMiningOverlay.isCaveMineFloorTile(map, tc))
		continue;
	    if(forwardDist(wall, tc, dir) >= 0)
		continue;
	    Coord2d wc = tileCenterWorld(tc);
	    double toPlayer = pl.rc.dist(wc);
	    double toTarget = tileCenterWorld(target).dist(wc);
	    int ahead = Math.max(0, forwardDist(ptc, tc, dir));
	    double score = toPlayer * 1.4 + toTarget * 0.15 + ahead * 3.0;
	    if(best == null || score < bestScore) {
		best = tc;
		bestScore = score;
	    }
	}
	if(best != null)
	    return best;
	/* Rare: no “behind” floor — fall back to any orth floor (legacy). */
	for(int[] d : ORTH) {
	    Coord tc = wall.add(d[0], d[1]);
	    if(!MoonMiningOverlay.isCaveMineFloorTile(map, tc))
		continue;
	    Coord2d wc = tileCenterWorld(tc);
	    double toPlayer = pl.rc.dist(wc);
	    double toTarget = tileCenterWorld(target).dist(wc);
	    int ahead = Math.max(0, forwardDist(ptc, tc, dir));
	    double score = toPlayer * 1.4 + toTarget * 0.15 + ahead * 3.0;
	    if(best == null || score < bestScore) {
		best = tc;
		bestScore = score;
	    }
	}
	return best;
    }

    /**
     * True if we are close enough to the wall face to send a mine click without micro-shuffling.
     * Never use Euclidean distance alone: a loose radius allowed “can dig” while standing on the
     * wrong side of the tile or jammed at a map edge, so the bot stopped walking and spammed useless clicks.
     */
    private static boolean closeEnoughToMineFace(Gob pl, Coord wallTile, Coord standTile, Coord dir) {
	Coord ptc = pl.rc.floor(MCache.tilesz);
	if(standTile != null && ptc.equals(standTile))
	    return true;
	int af = forwardDist(ptc, wallTile, dir);
	int perp = perpDist(ptc, wallTile, dir);
	if(af < 0)
	    return false;
	if(perp > 1)
	    return false;
	double dWall = pl.rc.dist(tileCenterWorld(wallTile));
	if(af <= 3 && dWall <= MCache.tilesz.x * 3.45)
	    return true;
	/* Face-on, one–two tiles; still requires af >= 0 and perp <= 1 */
	return af <= 2 && dWall <= MCache.tilesz.x * 1.9;
    }

    private static Coord plannedSupportStop(MapView map, Gob pl, Coord target, Coord dir) {
	SupportType type = supportType();
	if(type.radius <= 0)
	    return null;
	Coord ptc = pl.rc.floor(MCache.tilesz);
	Coord next = nextPlannedSupportCenter(ptc, target, dir, type.radius);
	if(next == null)
	    return null;
	if(hasStandingSupportNear(map, next))
	    return null;
	int dist = Math.abs(forwardDist(ptc, next, dir));
	if(dist > type.radius + 2)
	    return null;
	return next;
    }

    private static Coord nextPlannedSupportCenter(Coord ptc, Coord target, Coord dir, int radius) {
	int spacing = Math.max(2, radius * 2 - 1);
	int line = lineCoord(target, dir);
	int pprog = axisCoord(ptc, dir);
	int tprog = axisCoord(target, dir);
	int sign = axisSign(dir);
	int start = tprog;
	int bestProg = Integer.MIN_VALUE;
	Coord best = null;
	for(int i = 0; i < 32; i++) {
	    int prog = start - sign * spacing * i;
	    Coord tc = coordOnLine(prog, line, dir);
	    if(sign * (pprog - prog) <= 0)
		continue;
	    if(sign * (tprog - prog) < 0)
		continue;
	    if(best == null || sign * (prog - bestProg) > 0) {
		best = tc;
		bestProg = prog;
	    }
	}
	return best;
    }

    private static boolean handlePendingSupport(GameUI gui, MapView map, Gob pl, Coord supportTile, double now) {
	Coord stand = bestSupportStandTile(map.glob.map, supportTile);
	if(stand == null)
	    return false;
	Coord2d goal = tileCenterWorld(stand);
	double dist = pl.rc.dist(goal);
	if(dist > STAND_REACHED_DIST) {
	    if(now - lastAction < ACTION_INTERVAL)
		return true;
	    walkTowardStandoff(map, gui, pl.rc, goal, dirVec());
	    lastAction = now;
	    return true;
	}
	MoonConfig.setMineBotEnabled(false);
	resetState();
	gui.ui.msg(supportPlaceText(supportTile), Color.WHITE, null);
	return true;
    }

    private static Coord bestSupportStandTile(MCache map, Coord supportTile) {
	Coord best = null;
	for(int[] d : ORTH) {
	    Coord tc = supportTile.add(d[0], d[1]);
	    if(MoonMiningOverlay.isCaveMineFloorTile(map, tc))
		return tc;
	    if(best == null && MoonMiningOverlay.isSupportDisplayTile(map, tc))
		best = tc;
	}
	return best;
    }

    private static boolean hasStandingSupportNear(MapView map, Coord supportTile) {
	try {
	    synchronized(map.glob.oc) {
		for(Gob gob : map.glob.oc) {
		    if(gob == null || !MoonMiningOverlay.isMineSupportGob(gob))
			continue;
		    if(!MoonMiningOverlay.supportIsStructurallySound(gob))
			continue;
		    Coord gt = gob.rc.floor(MCache.tilesz);
		    if(Math.abs(gt.x - supportTile.x) <= 1 && Math.abs(gt.y - supportTile.y) <= 1)
			return true;
		}
	    }
	} catch(Exception ignored) {
	}
	return false;
    }

    private static int axisSign(Coord dir) {
	return(dir.x != 0) ? dir.x : dir.y;
    }

    private static int axisCoord(Coord tc, Coord dir) {
	return(dir.x != 0) ? tc.x : tc.y;
    }

    private static int lineCoord(Coord tc, Coord dir) {
	return(dir.x != 0) ? tc.y : tc.x;
    }

    private static Coord coordOnLine(int prog, int line, Coord dir) {
	if(dir.x != 0)
	    return Coord.of(prog, line);
	return Coord.of(line, prog);
    }

    private static int forwardDist(Coord from, Coord to, Coord dir) {
	return (to.x - from.x) * dir.x + (to.y - from.y) * dir.y;
    }

    private static int perpDist(Coord a, Coord b, Coord dir) {
	return (dir.x != 0) ? Math.abs(a.y - b.y) : Math.abs(a.x - b.x);
    }

    private static String supportPlaceText(Coord tc) {
	String name = supportType().label();
	if(LocalizationManager.LANG_RU.equals(MoonL10n.lang()))
	    return "Поставьте " + name + " на клетку " + tc.x + "," + tc.y + " и запустите бота снова.";
	return "Place " + name + " on tile " + tc.x + "," + tc.y + " and start the bot again.";
    }

    private static boolean progressBusy(GameUI gui) {
	if(gui.prog == null)
	    return false;
	double p = gui.prog.prog;
	return p > 0.04 && p < 0.997;
    }

    private static boolean fleeFromThreat(MapView map, GameUI gui, Gob pl, double now) {
	if(now - lastMobFlee < 0.95)
	    return(false);
	Gob threat = null;
	double threatD = Double.MAX_VALUE;
	try {
	    synchronized(map.glob.oc) {
		for(Gob g : map.glob.oc) {
		    if(g == null || g == pl)
			continue;
		    if(!MoonOverlay.isThreatMob(g))
			continue;
		    double d = pl.rc.dist(g.rc);
		    if(d < threatD) {
			threatD = d;
			threat = g;
		    }
		}
	    }
	} catch(Exception e) {
	    return(false);
	}
	if(threat == null || threatD >= MOB_THREAT_DIST)
	    return(false);
	Coord ptc = pl.rc.floor(MCache.tilesz);
	Coord threatTile = threat.rc.floor(MCache.tilesz);
	Coord dir = dirVec();
	/* Mob strictly behind the tunnel axis is not blocking the dig face; fleeing can shove us into a side wall. */
	if(forwardDist(ptc, threatTile, dir) < 0)
	    return(false);
	try {
	    Coord2d away = pl.rc.sub(threat.rc);
	    double len = Math.hypot(away.x, away.y);
	    if(len < 1e-3)
		away = Coord2d.of(1, 0);
	    else
		away = away.mul(1.0 / len);
	    Coord2d tunnelBack = Coord2d.of(-dir.x, -dir.y).norm();
	    away = away.mul(0.52).add(tunnelBack.mul(0.48)).norm();
	    Coord2d goal = pl.rc.add(away.mul(MCache.tilesz.x * 6.0));
	    clickGround(map, gui, goal);
	    lastMobFlee = now;
	    lastAction = now;
	    return(true);
	} catch(Exception e) {
	    return(false);
	}
    }

    private static boolean looksLikeMineWallTile(MCache map, Coord tc) {
	return MoonMiningOverlay.isCaveMineWallTile(map, tc);
    }

    /**
     * Wall is “open” if an orth neighbor is cave floor, or the player stands on an orth neighbor
     * on a non-wall tile (border cases where floor heuristics lag).
     */
    private static boolean hasExposedFloorNeighbor(MCache map, Coord wallTile, Coord playerTile) {
	for(int[] d : ORTH) {
	    Coord n = wallTile.add(d[0], d[1]);
	    if(MoonMiningOverlay.isCaveMineFloorTile(map, n))
		return true;
	    if(playerTile != null && n.equals(playerTile)
		&& !MoonMiningOverlay.isCaveMineWallTile(map, playerTile))
		return true;
	}
	return false;
    }

    private static boolean playerIsMining(Gob gob) {
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    if(!(d instanceof Composite))
		return(false);
	    Composited.Poses poses = ((Composite)d).comp.poses;
	    if(poses == null || poses.mods == null)
		return(false);
	    for(Skeleton.PoseMod mod : poses.mods) {
		String s = mod.toString().toLowerCase(Locale.ROOT);
		if(s.contains("pickan") || s.contains("mine") || s.contains("choppan"))
		    return(true);
	    }
	} catch(Exception ignored) {
	}
	return(false);
    }

    private static boolean isMinedOut(MCache map, Coord tc) {
	return MoonMiningOverlay.isCaveMineFloorTile(map, tc);
    }

    private static Coord2d tileCenterWorld(Coord tc) {
	return new Coord2d((tc.x + 0.5) * MCache.tilesz.x, (tc.y + 0.5) * MCache.tilesz.y);
    }

    private static boolean walkTowardStandoff(MapView map, GameUI gui, Coord2d from, Coord2d goal, Coord digDir) {
	double len = from.dist(goal);
	if(len < 1e-3)
	    return false;
	if(len <= INTERACT_DIST)
	    return clickGround(map, gui, goal);
	Coord2d nd = goal.sub(from).norm();
	double step = Math.min(WALK_STEP, len - INTERACT_DIST * 0.5);
	if(step < MCache.tilesz.x * 0.3)
	    step = Math.min(WALK_STEP, len * 0.5);
	Coord2d axis = Coord2d.of(digDir.x, digDir.y).norm();
	double align = nd.x * axis.x + nd.y * axis.y;
	Coord2d blended = nd.mul(0.58).add(axis.mul(Math.signum(align == 0 ? 1 : align) * 0.42)).norm();
	Coord2d wpt = from.add(blended.mul(step));
	return clickGround(map, gui, wpt);
    }

    private static boolean clickGround(MapView map, GameUI gui, Coord2d wpt) {
	boolean ok = map.moonSyntheticMapClick(wpt, null, 1, gui.ui.modflags());
	if(!ok && MoonConfig.mineBotDiagLog)
	    mineDiagAlways(String.format("clickGround FAIL wpt=(%.2f,%.2f) map.sz=%s", wpt.x, wpt.y, map.sz));
	return ok;
    }

    private static Gob findWallGobOnTile(MapView map, Coord wallTile, Gob pl) {
	Gob best = null;
	double bestD = Double.MAX_VALUE;
	try {
	    synchronized(map.glob.oc) {
		for(Gob g : map.glob.oc) {
		    if(g == null || g == pl || g.virtual)
			continue;
		    if(MoonMiningOverlay.isMineSupportGob(g))
			continue;
		    if(!wallTile.equals(g.rc.floor(MCache.tilesz)))
			continue;
		    if(g.getattr(Drawable.class) == null)
			continue;
		    double d = pl.rc.dist(g.rc);
		    if(d < bestD) {
			bestD = d;
			best = g;
		    }
		}
	    }
	} catch(Exception ignored) {
	}
	return best;
    }

    private static Coord mineWallGobScreenPc(MapView map, Gob gob) {
	Coord3f wc = null;
	try {
	    wc = gob.getc();
	} catch(Loading ignored) {
	}
	if(wc == null) {
	    try {
		wc = map.glob.map.getzp(gob.rc);
	    } catch(Loading e) {
		return null;
	    }
	}
	wc = wc.add(0, 0, WALL_GOB_Z_BOOST);
	Coord3f sc = map.screenxf(wc);
	if(sc == null) {
	    try {
		sc = map.screenxf(map.glob.map.getzp(gob.rc));
	    } catch(Loading e) {
		return null;
	    }
	}
	if(sc == null)
	    sc = map.screenxf(gob.rc);
	if(sc == null)
	    return null;
	return new Coord(
	    Utils.clip((int)Math.round(sc.x), 2, map.sz.x - 3),
	    Utils.clip((int)Math.round(sc.y), 2, map.sz.y - 3));
    }

    /** Same wire format as {@link MoonTreeChopBot} gob click — many cave walls are gobs. */
    private static boolean clickMineWallGob(MapView map, GameUI gui, Gob gob) {
	Coord pc = mineWallGobScreenPc(map, gob);
	if(pc == null)
	    return false;
	int mf = gui.ui.modflags();
	map.wdgmsg("click", pc, gob.rc.floor(posres), 1, mf, 0, (int)gob.id, gob.rc.floor(posres), 0, -1);
	return true;
    }

    /** {@code itemact} with gob args (same tail as click) for mining / tool cursor. */
    private static boolean clickMineWallGobItemAct(MapView map, GameUI gui, Gob gob) {
	Coord pc = mineWallGobScreenPc(map, gob);
	if(pc == null)
	    return false;
	int mf = gui.ui.modflags() & ~UI.MOD_META;
	Object[] args = Utils.extend(
	    new Object[] { pc, gob.rc.floor(posres), mf },
	    new Object[] { 0, (int)gob.id, gob.rc.floor(posres), 0, -1 });
	map.wdgmsg("itemact", args);
	return true;
    }

    private static boolean miningCursorActive(GameUI gui) {
	if(gui == null || gui.ui == null)
	    return false;
	try {
	    for(Widget w : new Widget[] { gui, gui.ui.root }) {
		if(w == null)
		    continue;
		Indir<Resource> c = w.cursor;
		if(c == null)
		    continue;
		Resource r = c.get();
		if(r != null && r.name != null) {
		    String n = r.name.toLowerCase(Locale.ROOT);
		    if(n.contains("curs/mine") || n.contains("curs/chop") || n.contains("pick"))
			return true;
		}
	    }
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
	return false;
    }

    private static boolean looksLikeMinePagina(MenuGrid.Pagina p) {
	if(p == null || p instanceof MenuGrid.SpecialPagina)
	    return false;
	try {
	    Resource r = p.res();
	    if(r == null || r.name == null)
		return false;
	    String n = r.name.toLowerCase(Locale.ROOT);
	    if(n.contains("paginae/") && (n.contains("mine") || n.contains("/dig")))
		return true;
	    if(n.contains("pickaxe") || n.contains("/pick"))
		return true;
	    Resource.AButton act = r.layer(Resource.action);
	    if(act != null && act.name != null) {
		String an = act.name.toLowerCase(Locale.ROOT);
		if(an.contains("mine") || an.equals("dig"))
		    return true;
	    }
	} catch(Loading | Resource.NoSuchLayerException ignored) {
	}
	return false;
    }

    /**
     * Activates a leaf “Mine” / pick menu or belt action so the server sets the mining cursor.
     * Returns true only when {@link #miningCursorActive} is already true.
     */
    private static boolean ensureMiningCursor(GameUI gui, double now) {
	if(miningCursorActive(gui))
	    return true;
	if(now - lastMineCursorAttempt < 0.9)
	    return false;
	lastMineCursorAttempt = now;
	if(gui.menu != null) {
	    try {
		if(gui.belt != null) {
		    for(GameUI.BeltSlot bs : gui.belt) {
			if(bs instanceof GameUI.PagBeltSlot) {
			    MenuGrid.Pagina p = ((GameUI.PagBeltSlot)bs).pag;
			    if(gui.menu.isLeafAction(p) && looksLikeMinePagina(p)) {
				gui.menu.use(p.button(), new MenuGrid.Interaction(1, gui.ui.modflags()), false);
				mineDiag(1.0, "mine-act", "mine cursor: belt " + p.res().name);
				return false;
			    }
			}
		    }
		}
		synchronized(gui.menu.paginae) {
		    for(MenuGrid.Pagina p : gui.menu.paginae) {
			if(gui.menu.isLeafAction(p) && looksLikeMinePagina(p)) {
			    gui.menu.use(p.button(), new MenuGrid.Interaction(1, gui.ui.modflags()), false);
			    mineDiag(1.0, "mine-act", "mine cursor: menu " + p.res().name);
			    return false;
			}
		    }
		}
	    } catch(Loading ignored) {
	    } catch(Exception e) {
		new Warning(e, "MoonMineBot.ensureMiningCursor").issue();
	    }
	}
	try {
	    gui.act("paginae/act/mine");
	    mineDiag(1.0, "mine-act", "mine cursor: act paginae/act/mine (fallback)");
	} catch(Exception ignored) {
	}
	return false;
    }

    /**
     * Prefer wall gob click, then synthetic tile click (exact {@code posres}), then resolved screen
     * click without trace snap.
     */
    private static boolean clickMineTile(MapView map, GameUI gui, Gob pl, Coord tc, Coord standTile) {
	double now = Utils.rtime();
	if(!ensureMiningCursor(gui, now)) {
	    mineDiag(0.45, "mine-curs",
		"need mining cursor — put Mine on belt or bar; bot tried menu/belt/act");
	    return false;
	}
	Gob wg = findWallGobOnTile(map, tc, pl);
	if(wg != null) {
	    boolean ia = clickMineWallGobItemAct(map, gui, wg);
	    boolean ck = !ia && clickMineWallGob(map, gui, wg);
	    if(ia || ck) {
		if(MoonConfig.mineBotDiagLog)
		    mineDiagAlways(String.format("clickMine gob id=%d tile=%s %s", wg.id, tc,
			ia ? "itemact" : "click"));
		return true;
	    }
	}
	Coord2d wpt = mineClickPoint(tc, standTile);
	Coord3f anchor = null;
	try {
	    anchor = map.glob.map.getzp(wpt).add(0, 0, 12);
	} catch(Loading ignored) {
	}
	if(map.moonSyntheticItemAct(wpt, anchor, gui.ui.modflags())) {
	    if(MoonConfig.mineBotDiagLog)
		mineDiag(0.25, "itemact-syn", String.format(
		    "clickMine itemact wall=%s wpt=(%.2f,%.2f)", tc, wpt.x, wpt.y));
	    return true;
	}
	if(map.moonSyntheticMapClick(wpt, anchor, 1, gui.ui.modflags())) {
	    if(MoonConfig.mineBotDiagLog)
		mineDiag(0.25, "click-syn", String.format("clickMine synthetic wall=%s wpt=(%.2f,%.2f)", tc, wpt.x, wpt.y));
	    return true;
	}
	Coord3f scPt = null;
	if(anchor != null)
	    scPt = map.screenxf(anchor);
	if(scPt == null)
	    scPt = map.screenxf(wpt);
	if(scPt == null && map.glob != null && map.glob.map != null) {
	    try {
		scPt = map.screenxf(map.glob.map.getzp(wpt));
	    } catch(Loading ignored) {
	    }
	}
	if(scPt == null) {
	    if(MoonConfig.mineBotDiagLog)
		mineDiagAlways(String.format(
		    "clickMine no screen wall=%s stand=%s wpt=(%.2f,%.2f)", tc, standTile, wpt.x, wpt.y));
	    return false;
	}
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(scPt.x), 2, map.sz.x - 3),
	    Utils.clip((int)Math.round(scPt.y), 2, map.sz.y - 3));
	map.moonResolvedScreenClick(pc, 1, gui.ui.modflags());
	if(MoonConfig.mineBotDiagLog)
	    mineDiag(0.25, "click-wall", String.format(
		"clickMine resolved wall=%s wpt=(%.2f,%.2f) pc=%s", tc, wpt.x, wpt.y, pc));
	return true;
    }

    private static Coord2d mineClickPoint(Coord wallTile, Coord standTile) {
	Coord2d center = tileCenterWorld(wallTile);
	if(standTile == null)
	    return center;
	Coord dv = standTile.sub(wallTile);
	double ox = Utils.clip(-dv.x, -1, 1) * (MCache.tilesz.x * 0.28);
	double oy = Utils.clip(-dv.y, -1, 1) * (MCache.tilesz.y * 0.28);
	return center.add(ox, oy);
    }

    private static Coord corridorTargetFromPlayer(Coord ptc, Coord dir) {
	return ptc.add(dir.x * VIRTUAL_TARGET_TILES, dir.y * VIRTUAL_TARGET_TILES);
    }

    /**
     * Preview grid uses screen-like axes: +Y is up, +X is right. Maps compass to tunnel step on that grid
     * (North = up), independent of tile-axis sign in {@link #dirVec()}.
     */
    public static Coord previewForward() {
	switch(Math.floorMod(MoonConfig.mineBotDirection, 4)) {
	case DIR_E: return Coord.of(1, 0);
	case DIR_S: return Coord.of(0, -1);
	case DIR_W: return Coord.of(-1, 0);
	default:    return Coord.of(0, 1);
	}
    }

    /** Lateral “right” when facing {@link #previewForward()} (matches {@link #perpVec} handedness for N/E). */
    public static Coord previewRight() {
	Coord f = previewForward();
	return Coord.of(f.y, -f.x);
    }

    /**
     * Start cell for the first tunnel segment on the preview grid. Chosen so the whole tunnel + supports
     * stay inside the fixed 13×13 preview grid in the mine bot window.
     */
    public static Coord previewTunnelAnchor() {
	int len = previewTunnelLength();
	if(len <= 0)
	    return Coord.z;
	int half = (len - 1) / 2;
	switch(Math.floorMod(MoonConfig.mineBotDirection, 4)) {
	case DIR_N:
	    return Coord.z;
	case DIR_S:
	    return Coord.of(0, len - 1);
	case DIR_E:
	    return Coord.of(-half, 0);
	default: /* W */
	    return Coord.of(half, 0);
	}
    }

    public static List<Coord> previewTunnelCells() {
	List<Coord> out = new ArrayList<>();
	int len = previewTunnelLength();
	Coord f = previewForward();
	Coord off = previewTunnelAnchor();
	for(int k = 0; k < len; k++)
	    out.add(off.add(f.mul(k)));
	return out;
    }

    public static List<Coord> previewSupportCells() {
	List<Coord> out = new ArrayList<>();
	SupportType type = supportType();
	if(type.radius <= 0)
	    return out;
	int len = previewTunnelLength();
	Coord f = previewForward();
	Coord off = previewTunnelAnchor();
	Coord r = previewRight();
	out.add(off.add(r));
	if(len > 1)
	    out.add(off.add(f.mul(len - 1)).add(r));
	return out;
    }

    public static List<Coord> previewWingCells() {
	if(MoonConfig.mineBotMode != MODE_SAPPER)
	    return Collections.emptyList();
	List<Coord> out = new ArrayList<>();
	int len = previewTunnelLength();
	int midK = Math.max(2, len / 2);
	Coord f = previewForward();
	Coord off = previewTunnelAnchor();
	Coord lat = previewRight();
	Coord mid = off.add(f.mul(midK));
	out.add(mid.sub(lat));
	out.add(mid.add(lat));
	return out;
    }

    public static int previewTunnelLength() {
	SupportType type = supportType();
	if(type.radius <= 0)
	    return 8;
	int spacing = Math.max(2, type.radius * 2 - 1);
	int len = 4 + (spacing / 3);
	return Utils.clip(len, 6, 11);
    }

    private static String mineText(String ru, String en) {
	return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }
}
