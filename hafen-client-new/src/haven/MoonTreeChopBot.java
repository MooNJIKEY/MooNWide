package haven;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static haven.OCache.posres;

/**
 * Tree logging: chop standing trees first (optional), then tree logs → split output (optional),
 * then haul to stock tile area. Does not automate building new stockpiles (server/UI flow).
 * Uses {@link MoonOverlay} for mob avoidance, optional row-major tree order (hitstack),
 * stall watchdog, and phase 3 to haul split pieces from the output area to main stock.
 * Settings in {@link MoonConfig}; {@link #resetState()} clears chop/haul progress.
 */
public final class MoonTreeChopBot {
    private MoonTreeChopBot() {}

    private enum CarryTarget {
	NONE, MAIN_STOCK, SPLIT_STOCK
    }

    private static double lastAction = 0;
    private static CarryTarget carryTarget = CarryTarget.NONE;

    /**
     * Server expects left-click then right-click on the tree, then “Chop” on the flower menu.
     * After that the character keeps chopping the same tree by itself — no further clicks until
     * this gob is gone or a different tree is targeted.
     */
    private static int treeChopStep = 0;
    private static double treeChopWait = 0;
    private static long treeChopGob = -1;
    private static int treeChopFlowerMiss = 0;
    /** We already chose “Chop” for this gob; do not open the menu again until target changes. */
    private static long treeChopCommittedGob = -1;

    /** Throttle only successful actions; failed projection retries next tick. */
    private static final double ACTION_INTERVAL = 0.42;
    private static final double TREE_CHOP_LEFT_RIGHT_DELAY = 0.12;
    private static final double TREE_CHOP_RIGHT_FLOWER_DELAY = 0.22;
    private static final double TREE_CHOP_FLOWER_RETRY = 0.08;
    private static final int TREE_CHOP_FLOWER_MAX = 40;
    /**
     * Max world distance at which we send a gob click (harvest / pickup). Larger values made the
     * server treat the action as “walk here” instead of interact.
     */
    private static final double INTERACT_DIST = MCache.tilesz.x * 2.75;
    /** Shorter segments → more path updates (helps a bit around obstacles; not full pathfinding). */
    private static final double WALK_STEP = MCache.tilesz.x * 2.0;
    /** Screen ray: aim at trunk, not ground under tree (otherwise hit-test misses → walk into tree). */
    private static final float GOB_CLICK_Z_BOOST = 12f;

    private static double phase1EmptySince = -1;
    private static double phase2EmptySince = -1;
    private static double phase3EmptySince = -1;

    private static final double MOB_THREAT_DIST = MCache.tilesz.x * 10.0;
    private static double lastMobFlee = 0;

    private static final double WATCHDOG_STALL_SEC = 14.0;
    private static final double WATCH_MOVE_EPS = MCache.tilesz.x * 0.42;
    private static Coord2d watchLastRc = null;
    private static double watchLastMove = 0;

    /** Current logging stand from {@link MoonConfig#treeBotSpots} (multi-line world XY). */
    private static int treeSpotIdx = 0;
    private static final double SPOT_REACH = MCache.tilesz.x * 2.5;

    public static void tick(GameUI gui, double dt) {
	if(gui == null || !MoonConfig.treeBotEnabled)
	    return;
	MapView map = gui.map;
	if(map == null)
	    return;
	Area chop = MoonTreeUtil.parseArea(MoonConfig.treeBotChopArea);
	if(chop == null || !chop.positive())
	    return;
	Gob pl = map.player();
	if(pl == null)
	    return;

	if(!hasCarriedItem(gui))
	    carryTarget = CarryTarget.NONE;

	int work = MoonConfig.treeBotWorkPhase;
	if(work == 0)
	    return;

	double now = Utils.rtime();
	if(MoonConfig.treeBotAvoidMobs && fleeFromThreat(map, gui, pl, now))
	    return;
	if(treeChopStep == 0 && treeChopCommittedGob == -1 && now - lastAction < ACTION_INTERVAL)
	    return;

	Area stock = MoonTreeUtil.parseArea(MoonConfig.treeBotStockArea);
	Area splitIn = MoonTreeUtil.parseArea(MoonConfig.treeBotSplitLogArea);
	Area splitOut = MoonTreeUtil.parseArea(MoonConfig.treeBotSplitOutArea);
	boolean splitOk = MoonConfig.treeBotSplitEnabled && splitIn != null && splitIn.positive()
	    && splitOut != null && splitOut.positive();

	try {
	    if(hasCarriedItem(gui)) {
		tickCarry(map, gui, pl, stock, splitOut, splitOk);
		lastAction = now;
		return;
	    }

	    if(work == 1) {
		tickPhase1Fell(gui, map, pl, now, chop);
		return;
	    }
	    if(work == 2) {
		tickPhase2Stock(gui, map, pl, now, chop, stock, splitIn, splitOut, splitOk);
		return;
	    }
	    if(work == 3) {
		tickPhase3HaulFirewoodToStock(gui, map, pl, now, stock, splitOut, splitOk);
		return;
	    }
	} catch(Loading l) {
	} catch(Exception e) {
	    new Warning(e, "TreeChopBot").issue();
	} finally {
	    tickWatchdog(gui, pl, now, work);
	}
    }

    /**
     * One-line status for HUD: phase, stamina, flags. {@code null} if the bot is off or HUD disabled.
     */
    public static String statusHudLine(GameUI gui) {
	if(gui == null || !MoonConfig.treeBotEnabled || !MoonConfig.treeBotHudLine)
	    return(null);
	int w = MoonConfig.treeBotWorkPhase;
	String ph = (w == 0) ? "pause" : (w == 1) ? "fell" : (w == 2) ? "haul" : (w == 3) ? "fw" : "?";
	int st = MoonAutoDrink.getCurrentStamina(gui);
	String stam = (st < 0) ? "?" : (st + "%");
	StringBuilder x = new StringBuilder(64);
	x.append("Tree ").append(w).append(" ").append(ph).append(" | stam ").append(stam);
	if(treeChopCommittedGob != -1)
	    x.append(" | chop");
	if(MoonConfig.treeBotAvoidMobs)
	    x.append(" | mobs");
	if(MoonConfig.treeBotAutoPipeline)
	    x.append(" | auto");
	List<Coord2d> spots = MoonTreeUtil.parseWorldSpotList(MoonConfig.treeBotSpots);
	if(!spots.isEmpty())
	    x.append(" | spot ").append(Math.floorMod(treeSpotIdx, spots.size()) + 1).append("/").append(spots.size());
	return(x.toString());
    }

    private static void finishPhase1(GameUI gui) {
	MoonNotify.treeBotPhase(gui, "treebot.phase1.done");
	treeSpotIdx = 0;
	if(MoonConfig.treeBotAutoPipeline)
	    MoonConfig.setTreeBotWorkPhase(2);
	else
	    MoonConfig.setTreeBotWorkPhase(0);
    }

    private static void finishPhase2(GameUI gui, boolean splitOk) {
	MoonNotify.treeBotPhase(gui, "treebot.phase2.done");
	if(MoonConfig.treeBotAutoPipeline && splitOk)
	    MoonConfig.setTreeBotWorkPhase(3);
	else
	    MoonConfig.setTreeBotWorkPhase(0);
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
	try {
	    Coord2d away = pl.rc.sub(threat.rc);
	    double len = Math.hypot(away.x, away.y);
	    if(len < 1e-3)
		away = Coord2d.of(1, 0);
	    else
		away = away.mul(1.0 / len);
	    double step = Math.min(MCache.tilesz.x * 5.0, threatD * 0.6 + MCache.tilesz.x * 2.0);
	    Coord2d wpt = pl.rc.add(away.mul(step));
	    Coord3f sc = map.screenxf(wpt);
	    if(sc == null)
		return(false);
	    Coord pc = new Coord(
		Utils.clip((int)Math.round(sc.x), 2, map.sz.x - 3),
		Utils.clip((int)Math.round(sc.y), 2, map.sz.y - 3));
	    map.wdgmsg("click", pc, wpt.floor(posres), 1, gui.ui.modflags());
	    lastMobFlee = now;
	    clearTreeChopSeq();
	    treeChopCommittedGob = -1;
	    gui.ui.msg(LocalizationManager.tr("treebot.mob.flee"), Color.WHITE, null);
	    return(true);
	} catch(Exception e) {
	    return(false);
	}
    }

    private static void tickWatchdog(GameUI gui, Gob pl, double now, int work) {
	if(work <= 0 || pl == null)
	    return;
	if(treeChopCommittedGob != -1) {
	    watchLastRc = pl.rc;
	    watchLastMove = now;
	    return;
	}
	if(MoonAutoDrink.pendingFlower) {
	    watchLastRc = pl.rc;
	    watchLastMove = now;
	    return;
	}
	if(work == 1) {
	    int st = MoonAutoDrink.getCurrentStamina(gui);
	    if(st >= 0 && st < MoonConfig.treeBotStaminaMinPct) {
		watchLastRc = pl.rc;
		watchLastMove = now;
		return;
	    }
	}
	if(work == 2 && MoonConfig.treeBotChopBeforeHaul && gui.map != null && gui.map.glob != null) {
	    Area chopA = MoonTreeUtil.parseArea(MoonConfig.treeBotChopArea);
	    if(chopA != null && chopA.positive()
		&& MoonTreeUtil.countTreesInTileArea(gui.map.glob, chopA) > 0) {
		watchLastRc = pl.rc;
		watchLastMove = now;
		return;
	    }
	}
	if(watchLastRc == null) {
	    watchLastRc = pl.rc;
	    watchLastMove = now;
	    return;
	}
	if(pl.rc.dist(watchLastRc) > WATCH_MOVE_EPS) {
	    watchLastRc = pl.rc;
	    watchLastMove = now;
	    return;
	}
	if(now - watchLastMove >= WATCHDOG_STALL_SEC) {
	    clearTreeChopSeq();
	    treeChopCommittedGob = -1;
	    carryTarget = CarryTarget.NONE;
	    gui.ui.msg(LocalizationManager.tr("treebot.watchdog"), Color.WHITE, null);
	    watchLastRc = pl.rc;
	    watchLastMove = now;
	}
    }

    private static void tickCarry(MapView map, GameUI gui, Gob pl, Area stock, Area splitOut, boolean splitOk) {
	clearTreeChopSeq();
	treeChopCommittedGob = -1;
	if(carryTarget == CarryTarget.NONE) {
	    if(stock != null && stock.positive())
		carryTarget = CarryTarget.MAIN_STOCK;
	    else if(splitOk)
		carryTarget = CarryTarget.SPLIT_STOCK;
	}
	if(carryTarget == CarryTarget.SPLIT_STOCK && splitOk)
	    goOrDrop(map, gui, pl, splitOut, true);
	else if(carryTarget == CarryTarget.MAIN_STOCK && stock != null && stock.positive())
	    goOrDrop(map, gui, pl, stock, true);
	else
	    clickDropAt(map, gui, pl.rc);
    }

    /** Phase 1: stamina + drink, then fell trees only. */
    private static void tickPhase1Fell(GameUI gui, MapView map, Gob pl, double now, Area chop) {
	if(MoonAutoDrink.tryDrinkBelowPercent(gui, MoonConfig.treeBotStaminaMinPct)) {
	    phase1EmptySince = -1;
	    return;
	}
	int st = MoonAutoDrink.getCurrentStamina(gui);
	if(st >= 0 && st < MoonConfig.treeBotStaminaMinPct) {
	    phase1EmptySince = -1;
	    return;
	}
	if(tryWalkToLoggingSpot(map, gui, pl, now))
	    return;

	Gob tree = pickChoppableTree(gui, chop, pl);
	if(tree == null) {
	    clearTreeChopSeq();
	    treeChopCommittedGob = -1;
	    if(phase1EmptySince < 0)
		phase1EmptySince = now;
	    else if(now - phase1EmptySince >= 1.6) {
		List<Coord2d> spots = MoonTreeUtil.parseWorldSpotList(MoonConfig.treeBotSpots);
		if(!spots.isEmpty() && treeSpotIdx < spots.size() - 1) {
		    treeSpotIdx++;
		    phase1EmptySince = -1;
		    return;
		}
		finishPhase1(gui);
		phase1EmptySince = -1;
	    }
	    return;
	}
	phase1EmptySince = -1;

	if(treeChopCommittedGob != -1 && tree.id != treeChopCommittedGob)
	    treeChopCommittedGob = -1;
	if(tree.id == treeChopCommittedGob && pl.rc.dist(tree.rc) <= INTERACT_DIST)
	    return;
	if(moveToInteractGob(map, gui, pl, tree))
	    lastAction = now;
    }

    /** Phase 2: haul logs / split output to stock (no felling). */
    private static void tickPhase2Stock(GameUI gui, MapView map, Gob pl, double now, Area chop, Area stock,
	    Area splitIn, Area splitOut, boolean splitOk) {
	if(MoonConfig.treeBotChopBeforeHaul) {
	    int standing = MoonTreeUtil.countTreesInTileArea(gui.map.glob, chop);
	    if(standing > 0)
		return;
	}
	boolean canHaulToMain = stock != null && stock.positive();
	if(splitOk) {
	    Gob logSplit = pickNearestGroundRes(gui, pl, MoonTreeUtil::isLooseTreeLog, chop, splitIn);
	    if(logSplit != null) {
		clearTreeChopSeq();
		treeChopCommittedGob = -1;
		carryTarget = CarryTarget.SPLIT_STOCK;
		interactGob(map, gui, pl, logSplit);
		lastAction = now;
		phase2EmptySince = -1;
		return;
	    }
	}
	if(canHaulToMain) {
	    Gob forStock;
	    if(splitOk)
		forStock = pickNearestGroundRes(gui, pl, MoonTreeUtil::isLooseSplitOrFirewoodChunk,
		    chop, splitIn, splitOut);
	    else
		forStock = pickNearestGroundRes(gui, pl, MoonTreeUtil::isLooseTreeLog, chop);
	    if(forStock != null) {
		clearTreeChopSeq();
		treeChopCommittedGob = -1;
		carryTarget = CarryTarget.MAIN_STOCK;
		interactGob(map, gui, pl, forStock);
		lastAction = now;
		phase2EmptySince = -1;
		return;
	    }
	}
	/* Nothing left to haul */
	if(phase2EmptySince < 0)
	    phase2EmptySince = now;
	else if(now - phase2EmptySince >= 2.0) {
	    finishPhase2(gui, splitOk);
	    phase2EmptySince = -1;
	}
    }

    private static boolean tryWalkToLoggingSpot(MapView map, GameUI gui, Gob pl, double now) {
	List<Coord2d> spots = MoonTreeUtil.parseWorldSpotList(MoonConfig.treeBotSpots);
	if(spots.isEmpty())
	    return(false);
	Coord2d spot = spots.get(Math.floorMod(treeSpotIdx, spots.size()));
	if(pl.rc.dist(spot) <= SPOT_REACH)
	    return(false);
	if(walkTowardStandoff(map, gui, pl.rc, spot)) {
	    lastAction = now;
	    return(true);
	}
	return(false);
    }

    /**
     * Phase 3: haul split pieces (firewood / boards from split output area) to main stock.
     * Assumes logs were already split elsewhere; does not open the crafting UI.
     */
    private static void tickPhase3HaulFirewoodToStock(GameUI gui, MapView map, Gob pl, double now,
	    Area stock, Area splitOut, boolean splitOk) {
	if(!splitOk || splitOut == null || !splitOut.positive()) {
	    MoonNotify.treeBotPhase(gui, "treebot.phase3.skip");
	    MoonConfig.setTreeBotWorkPhase(0);
	    phase3EmptySince = -1;
	    return;
	}
	if(stock == null || !stock.positive()) {
	    MoonNotify.treeBotPhase(gui, "treebot.phase3.needstock");
	    MoonConfig.setTreeBotWorkPhase(0);
	    phase3EmptySince = -1;
	    return;
	}
	Gob piece = pickNearestGroundRes(gui, pl, MoonTreeUtil::isLooseSplitOrFirewoodChunk, splitOut);
	if(piece != null) {
	    clearTreeChopSeq();
	    treeChopCommittedGob = -1;
	    carryTarget = CarryTarget.MAIN_STOCK;
	    interactGob(map, gui, pl, piece);
	    lastAction = now;
	    phase3EmptySince = -1;
	    return;
	}
	if(phase3EmptySince < 0)
	    phase3EmptySince = now;
	else if(now - phase3EmptySince >= 2.0) {
	    MoonNotify.treeBotPhase(gui, "treebot.phase3.done");
	    MoonConfig.setTreeBotWorkPhase(0);
	    phase3EmptySince = -1;
	}
    }

    private static boolean hasCarriedItem(GameUI gui) {
	return gui.vhand != null || (gui.hand != null && !gui.hand.isEmpty());
    }

    private static boolean goOrDrop(MapView map, GameUI gui, Gob pl, Area tileArea, boolean drop) {
	Coord2d wpt = areaDropPoint(tileArea);
	double dist = pl.rc.dist(wpt);
	if(dist > INTERACT_DIST)
	    return walkTowardStandoff(map, gui, pl.rc, wpt);
	if(drop)
	    return clickDropAt(map, gui, wpt);
	return false;
    }

    private static Coord2d areaDropPoint(Area a) {
	Coord mid = new Coord((a.ul.x + a.br.x - 1) / 2, (a.ul.y + a.br.y - 1) / 2);
	return(tileCenterWorld(mid));
    }

    private static Coord2d tileCenterWorld(Coord tc) {
	double tw = MCache.tilesz.x, th = MCache.tilesz.y;
	return(new Coord2d((tc.x + 0.5) * tw, (tc.y + 0.5) * th));
    }

    private static boolean clickDropAt(MapView map, GameUI gui, Coord2d wpt) {
	Coord3f sc = screenForGround(map, wpt);
	if(sc == null)
	    return(false);
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(sc.x), 2, map.sz.x - 3),
	    Utils.clip((int)Math.round(sc.y), 2, map.sz.y - 3));
	map.wdgmsg("drop", pc, wpt.floor(posres), gui.ui.modflags());
	return(true);
    }

    private static boolean interactGob(MapView map, GameUI gui, Gob pl, Gob gob) {
	return moveToInteractGob(map, gui, pl, gob);
    }

    private static boolean tileInAnyArea(Coord t, Area... areas) {
	for(Area a : areas) {
	    if(a != null && a.positive() && a.contains(t))
		return(true);
	}
	return(false);
    }

    /** Nearest gob whose resource name matches {@code pred} and lies in the union of {@code areas}. */
    private static Gob pickNearestGroundRes(GameUI gui, Gob pl, Predicate<String> pred, Area... areas) {
	if(areas == null || areas.length == 0)
	    return(null);
	Gob best = null;
	double bestd = Double.MAX_VALUE;
	synchronized(gui.map.glob.oc) {
	    for(Gob g : gui.map.glob.oc) {
		if(g == null || g == pl)
		    continue;
		try {
		    Drawable d = g.getattr(Drawable.class);
		    if(d == null)
			continue;
		    Resource r = d.getres();
		    if(r == null || !pred.test(r.name))
			continue;
		    Coord t = g.rc.floor(MCache.tilesz);
		    if(!tileInAnyArea(t, areas))
			continue;
		    double dist = pl.rc.dist(g.rc);
		    if(dist < bestd) {
			bestd = dist;
			best = g;
		    }
		} catch(Exception ignored) {}
	    }
	}
	return(best);
    }

    private static Gob pickNearestChoppableTree(GameUI gui, Area tileArea, Gob pl) {
	Gob best = null;
	double bestd = Double.MAX_VALUE;
	synchronized(gui.map.glob.oc) {
	    for(Gob g : gui.map.glob.oc) {
		if(g == null || g == pl)
		    continue;
		try {
		    Drawable d = g.getattr(Drawable.class);
		    if(d == null)
			continue;
		    Resource r = d.getres();
		    if(r == null || !MoonTreeUtil.isChoppableTree(r.name))
			continue;
		    Coord t = g.rc.floor(MCache.tilesz);
		    if(!tileArea.contains(t))
			continue;
		    double dist = pl.rc.dist(g.rc);
		    if(dist < bestd) {
			bestd = dist;
			best = g;
		    }
		} catch(Exception ignored) {}
	    }
	}
	return(best);
    }

    private static Gob pickChoppableTree(GameUI gui, Area tileArea, Gob pl) {
	if(!MoonConfig.treeBotHitstack)
	    return(pickNearestChoppableTree(gui, tileArea, pl));
	return(pickHitstackChoppableTree(gui, tileArea, pl));
    }

    /**
     * Lexicographic order on tile (y, x): clears the chop area in a predictable strip instead of
     * jumping between nearest trees.
     */
    private static Gob pickHitstackChoppableTree(GameUI gui, Area tileArea, Gob pl) {
	Gob best = null;
	Coord bestTile = null;
	synchronized(gui.map.glob.oc) {
	    for(Gob g : gui.map.glob.oc) {
		if(g == null || g == pl)
		    continue;
		try {
		    Drawable d = g.getattr(Drawable.class);
		    if(d == null)
			continue;
		    Resource r = d.getres();
		    if(r == null || !MoonTreeUtil.isChoppableTree(r.name))
			continue;
		    Coord t = g.rc.floor(MCache.tilesz);
		    if(!tileArea.contains(t))
			continue;
		    if(best == null || t.y < bestTile.y || (t.y == bestTile.y && t.x < bestTile.x)) {
			best = g;
			bestTile = t;
		    }
		} catch(Exception ignored) {}
	    }
	}
	return(best);
    }

    private static void clearTreeChopSeq() {
	treeChopStep = 0;
	treeChopWait = 0;
	treeChopGob = -1;
	treeChopFlowerMiss = 0;
    }

    /** Clears chop menu progress, committed tree, and carry routing (e.g. after a stuck state). */
    public static void resetState() {
	clearTreeChopSeq();
	treeChopCommittedGob = -1;
	carryTarget = CarryTarget.NONE;
	lastAction = 0;
	phase1EmptySince = -1;
	phase2EmptySince = -1;
	phase3EmptySince = -1;
	lastMobFlee = 0;
	watchLastRc = null;
	watchLastMove = 0;
	treeSpotIdx = 0;
    }

    /** After editing the stand list (e.g. Shift+Alt+click), walk from the first point again. */
    public static void onSpotsEdited() {
	treeSpotIdx = 0;
    }

    /** Walk until within {@link #INTERACT_DIST}, then interact (trees: left → right → flower “Chop”). */
    private static boolean moveToInteractGob(MapView map, GameUI gui, Gob pl, Gob gob) {
	double len = pl.rc.dist(gob.rc);
	if(len > INTERACT_DIST) {
	    if(gob.id == treeChopCommittedGob)
		treeChopCommittedGob = -1;
	    clearTreeChopSeq();
	    double dlen = gob.rc.sub(pl.rc).abs();
	    if(dlen < 1e-4)
		return clickGob(map, gui, gob, 1);
	    Coord2d nd = gob.rc.sub(pl.rc).norm();
	    Coord2d stopAt = gob.rc.sub(nd.mul(INTERACT_DIST));
	    double toGo = pl.rc.dist(stopAt);
	    if(toGo <= MCache.tilesz.x * 0.45)
		return clickGob(map, gui, gob, 1);
	    double step = Math.min(WALK_STEP, toGo);
	    Coord2d wpt = pl.rc.add(nd.mul(step));
	    return clickGround(map, gui, wpt);
	}
	if(isChoppableTreeGob(gob))
	    return runTreeChopSequence(map, gui, pl, gob);
	return clickGob(map, gui, gob, 1);
    }

    private static boolean isChoppableTreeGob(Gob g) {
	try {
	    Drawable d = g.getattr(Drawable.class);
	    if(d == null)
		return(false);
	    Resource r = d.getres();
	    return(r != null && MoonTreeUtil.isChoppableTree(r.name));
	} catch(Loading e) {
	    return(false);
	} catch(Exception e) {
	    return(false);
	}
    }

    /**
     * @return true when {@code lastAction} should advance (walk click, log pickup, or chop step done).
     */
    private static boolean runTreeChopSequence(MapView map, GameUI gui, Gob pl, Gob gob) {
	double now = Utils.rtime();
	if(treeChopGob != gob.id) {
	    treeChopGob = gob.id;
	    treeChopStep = 0;
	    treeChopWait = 0;
	    treeChopFlowerMiss = 0;
	}
	if(now < treeChopWait)
	    return(false);
	if(treeChopStep == 0) {
	    if(!clickGob(map, gui, gob, 1))
		return(false);
	    treeChopStep = 1;
	    treeChopWait = now + TREE_CHOP_LEFT_RIGHT_DELAY;
	    return(false);
	}
	if(treeChopStep == 1) {
	    if(!clickGob(map, gui, gob, 3))
		return(false);
	    treeChopStep = 2;
	    treeChopWait = now + TREE_CHOP_RIGHT_FLOWER_DELAY;
	    treeChopFlowerMiss = 0;
	    return(false);
	}
	FlowerMenu fm = findFlowerMenuWithChop(gui);
	if(fm == null) {
	    treeChopWait = now + TREE_CHOP_FLOWER_RETRY;
	    if(++treeChopFlowerMiss > TREE_CHOP_FLOWER_MAX) {
		clearTreeChopSeq();
		return(true);
	    }
	    return(false);
	}
	int idx = findChopPetalIndex(fm);
	if(idx < 0) {
	    treeChopWait = now + TREE_CHOP_FLOWER_RETRY;
	    if(++treeChopFlowerMiss > TREE_CHOP_FLOWER_MAX) {
		clearTreeChopSeq();
		return(true);
	    }
	    return(false);
	}
	fm.wdgmsg("cl", idx, gui.ui.modflags());
	treeChopCommittedGob = gob.id;
	clearTreeChopSeq();
	return(true);
    }

    /** Prefer a flower menu that actually offers chop (avoids picking Buddy menus, etc.). */
    private static FlowerMenu findFlowerMenuWithChop(GameUI gui) {
	if(gui == null || gui.ui == null || gui.ui.root == null)
	    return(null);
	return(findFlowerMenuWithChop(gui.ui.root));
    }

    private static FlowerMenu findFlowerMenuWithChop(Widget w) {
	if(w instanceof FlowerMenu) {
	    FlowerMenu fm = (FlowerMenu)w;
	    if(findChopPetalIndex(fm) >= 0)
		return(fm);
	}
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    FlowerMenu r = findFlowerMenuWithChop(ch);
	    if(r != null)
		return(r);
	}
	return(null);
    }

    private static int findChopPetalIndex(FlowerMenu fm) {
	if(fm == null || fm.opts == null)
	    return(-1);
	for(int i = 0; i < fm.opts.length; i++) {
	    String n = fm.opts[i].name;
	    if(n == null)
		continue;
	    String lc = n.toLowerCase(Locale.ROOT);
	    if(lc.contains("chop") || lc.contains("руб") || lc.contains("сруб") || lc.contains("fell"))
		return(i);
	}
	return(-1);
    }

    private static boolean walkTowardStandoff(MapView map, GameUI gui, Coord2d from, Coord2d goal) {
	double len = from.dist(goal);
	if(len < 1e-3)
	    return false;
	if(len <= INTERACT_DIST)
	    return clickGround(map, gui, goal);
	Coord2d nd = goal.sub(from).norm();
	double step = Math.min(WALK_STEP, len - INTERACT_DIST * 0.5);
	if(step < MCache.tilesz.x * 0.3)
	    step = Math.min(WALK_STEP, len * 0.5);
	Coord2d wpt = from.add(nd.mul(step));
	return clickGround(map, gui, wpt);
    }

    private static Coord3f screenForGround(MapView map, Coord2d wpt) {
	Coord3f sc = map.screenxf(wpt);
	if(sc != null)
	    return(sc);
	try {
	    Coord3f zp = map.glob.map.getzp(wpt);
	    sc = map.screenxf(zp);
	    if(sc != null)
		return(sc);
	} catch(Loading ignored) {}
	return(null);
    }

    private static boolean clickGround(MapView map, GameUI gui, Coord2d wpt) {
	Coord3f sc = screenForGround(map, wpt);
	if(sc == null)
	    return(false);
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(sc.x), 2, map.sz.x - 3),
	    Utils.clip((int)Math.round(sc.y), 2, map.sz.y - 3));
	map.wdgmsg("click", pc, wpt.floor(posres), 1, gui.ui.modflags());
	return(true);
    }

    /**
     * Synthetic map click on a gob. {@code button} is the same as mouse events ({@code 1} = left,
     * {@code 3} = right); the server uses this for walk vs context menu.
     */
    private static boolean clickGob(MapView map, GameUI gui, Gob gob, int button) {
	Coord3f wc = null;
	try {
	    wc = gob.getc();
	} catch(Loading ignored) {}
	if(wc == null) {
	    try {
		wc = map.glob.map.getzp(gob.rc);
	    } catch(Loading e) {
		return(false);
	    }
	}
	wc = wc.add(0, 0, GOB_CLICK_Z_BOOST);
	Coord3f sc = map.screenxf(wc);
	if(sc == null) {
	    try {
		sc = map.screenxf(map.glob.map.getzp(gob.rc));
	    } catch(Loading e) {
		return(false);
	    }
	}
	if(sc == null)
	    sc = map.screenxf(gob.rc);
	if(sc == null)
	    return(false);
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(sc.x), 2, map.sz.x - 3),
	    Utils.clip((int)Math.round(sc.y), 2, map.sz.y - 3));
	Coord2d mc = gob.rc;
	map.wdgmsg("click", pc, mc.floor(posres), button, gui.ui.modflags(),
	    0, (int)gob.id, gob.rc.floor(posres), 0, -1);
	return(true);
    }
}
