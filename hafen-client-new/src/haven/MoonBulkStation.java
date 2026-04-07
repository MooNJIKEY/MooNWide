package haven;

import static haven.OCache.posres;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bulk fill: hold all modifiers in {@link MoonConfig#bulkStationFillModMask} (default Shift+Alt), then
 * RMB itemact or drop on a station; repeats until hand stall or budget. Ctrl+RMB take-all via ISBox.xfer.
 * <p>
 * Tanning tub ({@code ttub}): same modifiers + empty hand on the tub starts a full run — take hide/leather from
 * main inventory, walk there if needed, then bulk itemact; repeats until nothing left to deposit.
 * <p>
 * <b>Diagnostics:</b> distinguish “no {@code [MoonInvWire]} bulk replay lines” (bulk not sending) from
 * “replays logged but nothing deposits” (server or target mismatch). If the official server rejects the action,
 * look for {@code WDGMSG name=err} in net logs when clicking. Strict wire parity with an external ARD build
 * requires comparing captures from that client; it is not derivable from other trees in this repository alone.
 */
public final class MoonBulkStation {
    private MoonBulkStation() {}

    private static final double FILL_HAND_STALL_TIMEOUT = 8.0;
    private static final double FILL_EMPTY_HAND_GRACE = 1.0;

    private static boolean fillActive;
    private static double fillNextAt;
    /** Last time hand stack meaningfully changed ({@code num} or item ref) — stall detection only. */
    private static double fillLastHandProgressAt;
    /** Time of last bulk replay (diagnostics). */
    private static double fillLastWireAt;
    private static MapView fillMap;
    private static String fillKind;
    private static Object[] fillArgs;
    private static String fillTargetKey;
    private static int fillRepeatBudget;
    private static GItem fillTracked;
    private static String fillTrackedRes;
    private static int fillLastNum = -2;
    private static int fillLastInfoseq = -1;
    private static int fillLastMeter = -1;
    private static int fillLastHandCount = -1;
    private static int fillSteps;
    /** One replay is queued after real hand progress (ARD-style), not every UI tick. */
    private static boolean fillPendingReplay;

    private static boolean takeActive;
    private static double takeNextAt;
    private static double takeGateUntil;
    private static int takeRound;

    private static final double TUB_TAKE_RATE_SEC = 0.22;
    /** World units — if farther than this from the tub, client pathfinds and walks. */
    private static final double TUB_NEED_WALK_DIST = 52.0;

    private enum TubPhase { IDLE, NEED_TAKE, WALKING }
    private static boolean tubSessionActive;
    private static TubPhase tubPhase = TubPhase.IDLE;
    private static long tubSessionGobId;
    private static MapView tubSessionMv;
    private static int tubSessionModflags;
    /** Exact initial itemact args from the user click on the tub (ARD-style replay source). */
    private static Object[] tubSessionItemactArgs;
    private static volatile boolean tubWalkArrived;
    private static boolean tubWalkStarted;
    private static double tubNextTakeAt;
    /** Last bulk fill was started from {@link #tubSessionActive} automation. */
    private static boolean fillFromTubRun;

    public static void tick(GameUI gui, double now) {
	if(gui == null || gui.ui == null)
	    return;
	tickTubRun(gui, now);
	tickFill(gui, now);
	tickTake(gui, now);
    }

    public static void onHandUpdated(GameUI gui) {
	onHandUpdated(gui, Utils.rtime());
    }

    /**
     * Tracks hand stack changes while bulk fill is active.
     * When the hand widget is effectively recreated ({@code fillTracked != it}), replay immediately like ARD.
     * Same-item stack updates are still throttled a little to avoid hammering the server on noisy item refreshes.
     */
    public static void onHandUpdated(GameUI gui, double now) {
	if(!fillActive || gui == null)
	    return;
	GItem it = currentHandItem(gui);
	int handCount = (gui.hand != null) ? gui.hand.size() : ((it != null) ? 1 : 0);
	if(it == null) {
	    fillLastHandCount = handCount;
	    return;
	}
	String curRes = handResName(it);
	if(fillTrackedRes != null && curRes != null && !fillTrackedRes.equals(curRes)) {
	    clearFill();
	    return;
	}
	boolean hardSwap = (fillTracked != it);
	/* Server may refresh the hand item via infoseq/meter without num changing the same tick. */
	boolean stateChg = hardSwap || (it.num != fillLastNum)
	    || (it.infoseq != fillLastInfoseq) || (it.meter != fillLastMeter)
	    || (handCount != fillLastHandCount);
	if(stateChg) {
	    fillTracked = it;
	    fillLastNum = it.num;
	    fillLastInfoseq = it.infoseq;
	    fillLastMeter = it.meter;
	    fillLastHandCount = handCount;
	    fillLastHandProgressAt = now;
	    if(fillSteps < fillRepeatBudget) {
		double readyAt = hardSwap ? now : Math.max(now, fillLastWireAt + replayIntervalSec());
		fillPendingReplay = true;
		fillNextAt = readyAt;
		if(now >= readyAt)
		    replayStoredFill(now);
	    } else {
		fillPendingReplay = false;
	    }
	}
    }

    private static void clearFill() {
	boolean wasTubFill = fillFromTubRun;
	MapView mvSnap = fillMap;
	fillActive = false;
	fillFromTubRun = false;
	fillMap = null;
	fillKind = null;
	fillArgs = null;
	fillTargetKey = null;
	fillRepeatBudget = 0;
	fillTracked = null;
	fillTrackedRes = null;
	fillLastNum = -2;
	fillLastInfoseq = -1;
	fillLastMeter = -1;
	fillLastHandCount = -1;
	fillLastHandProgressAt = 0;
	fillLastWireAt = 0;
	fillNextAt = 0;
	fillSteps = 0;
	fillPendingReplay = false;
	if(tubSessionActive && wasTubFill && mvSnap != null) {
	    GameUI g = mvSnap.getparent(GameUI.class);
	    tubAfterFillEnded(g);
	}
    }

    private static void clearTake() {
	takeActive = false;
	takeRound = 0;
	takeGateUntil = 0;
	takeNextAt = 0;
    }

    public static void cancelBulkAutomation() {
	clearTubSession();
	clearFill();
	clearTake();
    }

    private static void clearTubSession() {
	tubSessionActive = false;
	tubPhase = TubPhase.IDLE;
	tubSessionGobId = 0;
	tubSessionMv = null;
	tubSessionModflags = 0;
	tubSessionItemactArgs = null;
	tubWalkArrived = false;
	tubWalkStarted = false;
	fillFromTubRun = false;
	MoonPathWalker.cancelActive();
    }

    private static Gob gobFromClickData(ClickData inf) {
	if(inf == null || inf.ci == null)
	    return(null);
	if(inf.ci instanceof Gob.GobClick)
	    return(((Gob.GobClick)inf.ci).gob);
	if(inf.ci instanceof Composited.CompositeClick)
	    return(((Composited.CompositeClick)inf.ci).gi.gob);
	return(null);
    }

    static boolean isBulkTargetGob(Gob gob) {
	return bulkTargetKey(gob) != null;
    }

    /**
     * True when all bits in {@link MoonConfig#bulkStationFillModMask} are set (default: Shift+Alt).
     */
    public static boolean wantsBulkFillModifiers(int mf) {
	int mask = MoonConfig.bulkStationFillModMask;
	return mask != 0 && (mf & mask) == mask;
    }

    public static boolean wantsFillAutomation(int modflags, ClickData inf) {
	if(!MoonConfig.bulkStationFill || !wantsBulkFillModifiers(modflags))
	    return false;
	Gob gob = gobFromClickData(inf);
	return bulkTargetKey(gob) != null;
    }

    /**
     * ARD-style itemact replay arm: exact click args are enough, target classification is optional.
     * This keeps basic bulk alive even if local station detection misses a valid gob click.
     */
    public static boolean wantsItemactReplay(int modflags, ClickData inf) {
	return MoonConfig.bulkStationFill && wantsBulkFillModifiers(modflags) && (inf != null);
    }

    public static int sanitizeActionMods(int modflags) {
	return modflags & ~MoonConfig.bulkStationFillModMask;
    }

    /**
     * Modifiers on the wire for shift+alt bulk {@code itemact} on a station. ARD assigns
     * {@code lastItemactClickArgs[2] = 1} before sending — i.e. {@link UI#MOD_SHIFT} only. Using
     * {@link #sanitizeActionMods} instead strips shift as well → {@code 0}, which the server can treat as a
     * different default (e.g. pile / stockpile flow for hides) instead of normal tool use on the workstation.
     */
    public static int itemactWireModsForStationFill() {
	return UI.MOD_SHIFT;
    }

    static Set<String> knownStationKeys() {
	return MoonGobKind.knownBulkStationKeys();
    }

    static String stationLabelKey(String stationKey) {
	return "bulk.station.key." + stationKey;
    }

    static boolean stationBlacklisted(String stationKey) {
	return stationKey != null && MoonConfig.bulkStationBlacklistContains(stationKey);
    }

    static String bulkTargetKey(Gob gob) {
	String key = MoonGobKind.stationKey(gob);
	if(key == null || stationBlacklisted(key))
	    return null;
	return key;
    }

    private static String handResName(GItem it) {
	if(it == null)
	    return null;
	try {
	    Resource res = it.resource();
	    return (res == null || res.name == null) ? null : res.name.toLowerCase();
	} catch(Loading ignored) {
	    return null;
	}
    }

    private static GItem currentHandItem(GameUI gui) {
	if(gui == null)
	    return null;
	if(gui.vhand != null && gui.vhand.item != null)
	    return gui.vhand.item;
	if(gui.hand != null && !gui.hand.isEmpty()) {
	    GameUI.DraggedItem di = gui.hand.iterator().next();
	    if(di != null)
		return di.item;
	}
	return null;
    }

    /**
     * Deep-copy mutable point values for bulk replay. {@link #maybeStartFillAfterItemact} stores
     * {@code args.clone()} (shallow); {@link Coord} / {@link Coord2d} are mutable — the same references
     * can track live cursor or world updates, so replays would drift and the server ignores them.
     */
    private static Object[] snapshotFillArgs(Object[] args) {
	if(args == null)
	    return null;
	Object[] out = new Object[args.length];
	for(int i = 0; i < args.length; i++) {
	    Object o = args[i];
	    if(o instanceof Coord)
		out[i] = new Coord((Coord)o);
	    else if(o instanceof Coord2d)
		out[i] = new Coord2d(((Coord2d)o).x, ((Coord2d)o).y);
	    else if(o instanceof Coord3f)
		out[i] = new Coord3f((Coord3f)o);
	    else
		out[i] = o;
	}
	return out;
    }

    private static double intervalSec() {
	return(Math.max(0.05, MoonConfig.bulkStationRepeatIntervalMs / 1000.0));
    }

    private static double replayIntervalSec() {
	double sec = intervalSec();
	if(fillFromTubRun && "ttub".equals(fillTargetKey))
	    sec *= Math.max(1.0, MoonConfig.bulkTubReplayIntervalMult);
	return sec;
    }

    private static boolean isCoalFuel(String resName) {
	if(resName == null)
	    return false;
	String n = resName.toLowerCase();
	return n.contains("charcoal") || n.contains("blackcoal") || n.endsWith("/coal") || n.contains("/coal/");
    }

    private static boolean isBranchFuel(String resName) {
	return resName != null && resName.toLowerCase().contains("branch");
    }

    private static boolean isWoodBlockFuel(String resName) {
	if(resName == null)
	    return false;
	String n = resName.toLowerCase();
	return n.contains("wblock") || (n.contains("wood") && n.contains("block")) || n.contains("board");
    }

    private static int plannedFuelLoad(String stationKey, String fuelResName, int modflags) {
	if(stationKey == null || fuelResName == null)
	    return -1;
	/* Alt is reserved for enabling bulk (with Shift); use Ctrl for "full" batch sizes. */
	boolean full = (modflags & UI.MOD_CTRL) != 0;
	if(stationKey.equals("smelter") && isCoalFuel(fuelResName))
	    return 12;
	if(stationKey.equals("primsmelter")) {
	    if(isBranchFuel(fuelResName))
		return full ? 30 : 15;
	    if(isWoodBlockFuel(fuelResName))
		return full ? 6 : 3;
	}
	return -1;
    }

    private static void startFill(MapView mv, GameUI gui, String kind, Object[] args, int modflags, String targetKey,
	boolean fromTubRun, long targetGobId) {
	fillFromTubRun = fromTubRun;
	clearTake();
	fillActive = true;
	fillMap = mv;
	fillKind = kind;
	fillArgs = snapshotFillArgs(args);
	fillTargetKey = targetKey;
	fillTracked = currentHandItem(gui);
	fillTrackedRes = handResName(fillTracked);
	fillLastNum = fillTracked != null ? fillTracked.num : -1;
	fillLastInfoseq = fillTracked != null ? fillTracked.infoseq : -1;
	fillLastMeter = fillTracked != null ? fillTracked.meter : -1;
	fillLastHandCount = (gui.hand != null) ? gui.hand.size() : ((fillTracked != null) ? 1 : 0);
	int plannedLoad = plannedFuelLoad(targetKey, fillTrackedRes, modflags);
	int maxRep = Math.max(1, MoonConfig.bulkStationMaxRepeats);
	fillRepeatBudget = (plannedLoad > 0)
	    ? Math.max(1, Math.min(maxRep, plannedLoad - 1))
	    : maxRep;
	if(fromTubRun && "ttub".equals(targetKey))
	    fillRepeatBudget = Math.min(fillRepeatBudget, Math.max(1, MoonConfig.bulkTubMaxRepeatsPerDeposit));
	fillSteps = 0;
	fillPendingReplay = false;
	double t = Utils.rtime();
	fillLastHandProgressAt = t;
	fillLastWireAt = t;
	fillNextAt = t + replayIntervalSec();
    }

    public static boolean maybeStartFillAfterItemact(MapView mv, Object[] args, int modflags, ClickData inf) {
	if(fillActive)
	    return false;
	boolean wants = wantsItemactReplay(modflags, inf);
	if(!MoonConfig.bulkStationFill || mv == null || args == null || !wants) {
	    MoonInventoryWireDebug.maybeLogBulkTrace("start-itemact-skip", "enabled=", Boolean.valueOf(MoonConfig.bulkStationFill),
		"mv=", Boolean.valueOf(mv != null), "args=", Boolean.valueOf(args != null), "wants=", Boolean.valueOf(wants),
		"mods=", Integer.valueOf(modflags), "inf=", Boolean.valueOf(inf != null));
	    return false;
	}
	Gob gob = gobFromClickData(inf);
	String targetKey = bulkTargetKey(gob);
	GameUI gui = mv.getparent(GameUI.class);
	GItem hand = (gui == null) ? null : currentHandItem(gui);
	if(gui == null || hand == null) {
	    MoonInventoryWireDebug.maybeLogBulkTrace("start-itemact-nohand", "gui=", Boolean.valueOf(gui != null),
		"hand=", Boolean.valueOf(hand != null), "mods=", Integer.valueOf(modflags), "target=", targetKey);
	    return false;
	}
	Object[] fill = snapshotFillArgs(args);
	if(fill.length > 2)
	    fill[2] = Integer.valueOf(itemactWireModsForStationFill());
	MoonInventoryWireDebug.maybeLogBulkTrace("start-itemact-ok", "mods=", Integer.valueOf(modflags),
	    "target=", targetKey, "gob=", Integer.valueOf((gob == null) ? 0 : (int)gob.id), "hand=", handResName(hand));
	startFill(mv, gui, "itemact", fill, modflags, targetKey, false, (gob == null) ? 0 : gob.id);
	return true;
    }

    public static boolean maybeStartFillAfterDrop(MapView mv, Coord pc, Coord2d mc, int modflags, ClickData inf) {
	if(fillActive)
	    return false;
	if(!MoonConfig.bulkStationFill || mv == null || !wantsBulkFillModifiers(modflags))
	    return false;
	Gob gob = gobFromClickData(inf);
	String targetKey = bulkTargetKey(gob);
	if(gob == null || targetKey == null)
	    return false;
	GameUI gui = mv.getparent(GameUI.class);
	GItem hand = (gui == null) ? null : currentHandItem(gui);
	if(gui == null || hand == null) {
	    MoonInventoryWireDebug.maybeLogBulkTrace("start-drop-nohand", "gui=", Boolean.valueOf(gui != null),
		"hand=", Boolean.valueOf(hand != null), "mods=", Integer.valueOf(modflags), "target=", targetKey);
	    return false;
	}
	Object[] fill = snapshotFillArgs(new Object[] { pc, mc.floor(posres), Integer.valueOf(sanitizeActionMods(modflags)) });
	MoonInventoryWireDebug.maybeLogBulkTrace("start-drop-ok", "mods=", Integer.valueOf(modflags),
	    "target=", targetKey, "gob=", Integer.valueOf((int)gob.id), "hand=", handResName(hand));
	startFill(mv, gui, "drop", fill, modflags, targetKey, false, gob.id);
	return true;
    }

    private static boolean replayStoredFill(double now) {
	if(!fillActive || fillMap == null || fillArgs == null)
	    return false;
	if(fillSteps >= fillRepeatBudget) {
	    clearFill();
	    return false;
	}
	Object[] wire = snapshotFillArgs(fillArgs);
	if("itemact".equals(fillKind) && wire != null && wire.length > 2)
	    wire[2] = Integer.valueOf(itemactWireModsForStationFill());
	if(wire == null) {
	    MoonInventoryWireDebug.maybeLogBulkTrace("replay-skip-wire-null");
	    clearFill();
	    return false;
	}
	MoonInventoryWireDebug.maybeLogBulkTrace("replay-send", fillKind, "step=", Integer.valueOf(fillSteps + 1),
	    "budget=", Integer.valueOf(fillRepeatBudget));
	fillMap.wdgmsg(fillKind, wire);
	fillSteps++;
	fillPendingReplay = false;
	fillLastWireAt = now;
	fillNextAt = now + replayIntervalSec();
	MoonInventoryWireDebug.maybeLogBulkReplay(fillKind, wire);
	return true;
    }

    private static void tickFill(GameUI gui, double now) {
	if(!fillActive || fillMap == null)
	    return;
	onHandUpdated(gui, now);
	if(!fillActive || fillMap == null)
	    return;
	if(fillSteps >= fillRepeatBudget) {
	    clearFill();
	    return;
	}
	GItem it = currentHandItem(gui);
	if(it == null) {
	    if((fillLastWireAt > 0) && ((now - Math.max(fillLastHandProgressAt, fillLastWireAt)) > FILL_EMPTY_HAND_GRACE)) {
		MoonInventoryWireDebug.maybeLogBulkTrace("tick-empty-clear", "dt=", Double.valueOf(now - Math.max(fillLastHandProgressAt, fillLastWireAt)));
		clearFill();
	    }
	    return;
	}
	String curRes = handResName(it);
	if(fillTrackedRes == null && curRes != null)
	    fillTrackedRes = curRes;
	if(fillTrackedRes != null && curRes != null && !fillTrackedRes.equals(curRes)) {
	    clearFill();
	    return;
	}
	if((fillLastWireAt > fillLastHandProgressAt) && ((now - fillLastWireAt) > FILL_HAND_STALL_TIMEOUT)) {
	    if(MoonConfig.debugInventoryWire)
		MoonInventoryWireDebug.maybeLogBulkTrace("stall-clear", "dt=", Double.valueOf(now - fillLastWireAt));
	    clearFill();
	    return;
	}
	if(fillPendingReplay && now >= fillNextAt)
	    replayStoredFill(now);
    }

    public static void maybeStartTakeAllAfterMapClick(MapView mv, ClickData inf, int sendMods, int button) {
	if(!MoonConfig.bulkStationTakeAll || mv == null || button != 3)
	    return;
	if((sendMods & UI.MOD_CTRL) == 0)
	    return;
	GameUI gui = mv.getparent(GameUI.class);
	if(gui == null || gui.vhand != null)
	    return;
	Gob gob = gobFromClickData(inf);
	if(gob == null || bulkTargetKey(gob) == null)
	    return;
	clearFill();
	takeActive = true;
	takeRound = 0;
	takeGateUntil = Utils.rtime() + 0.22;
	takeNextAt = takeGateUntil;
    }

    private static void collectISBoxesUnderWindows(Widget w, List<ISBox> out) {
	if(w instanceof ISBox) {
	    boolean under = false;
	    for(Widget p = w.parent; p != null; p = p.parent) {
		if(p instanceof Window) {
		    under = true;
		    break;
		}
	    }
	    if(under)
		out.add((ISBox)w);
	}
	for(Widget ch = w.child; ch != null; ch = ch.next)
	    collectISBoxesUnderWindows(ch, out);
    }

    private static List<ISBox> snapshotTakeTargets(UI ui) {
	List<ISBox> list = new ArrayList<>();
	if(ui != null && ui.root != null)
	    collectISBoxesUnderWindows(ui.root, list);
	return(list);
    }

    private static void tickTake(GameUI gui, double now) {
	if(!takeActive)
	    return;
	if(now < takeNextAt)
	    return;
	if(takeRound >= MoonConfig.bulkStationMaxRepeats) {
	    clearTake();
	    return;
	}
	if(now < takeGateUntil) {
	    takeNextAt = takeGateUntil;
	    return;
	}
	takeNextAt = now + intervalSec();
	takeRound++;
	List<ISBox> boxes = snapshotTakeTargets(gui.ui);
	if(boxes.isEmpty()) {
	    clearTake();
	    return;
	}
	for(ISBox b : boxes) {
	    try {
		b.wdgmsg("xfer");
	    } catch(Exception ignored) {
	    }
	}
    }

    /* --- Tanning tub full run (ttub): take → walk → bulk itemact → repeat --- */

    /**
     * Shift+Alt (or configured mask) + item-use on tanning tub with <em>empty hand</em>: take hide/leather from
     * main inventory, walk into range if needed, then run bulk fill. When {@link MoonConfig#bulkTubSendFirstItemact}
     * is true, swallows the initial itemact (no wire); when false (default), returns false so the first
     * itemact is sent.
     */
    public static boolean maybeStartTubRun(MapView mv, Object[] args, int modflags, ClickData inf) {
	if(fillActive || tubSessionActive)
	    return(false);
	if(!MoonConfig.bulkStationFill || mv == null || !wantsBulkFillModifiers(modflags))
	    return(false);
	Gob gob = gobFromClickData(inf);
	if(gob == null || !"ttub".equals(bulkTargetKey(gob)))
	    return(false);
	GameUI gui = mv.getparent(GameUI.class);
	if(gui == null || gui.vhand != null)
	    return(false);
	clearTake();
	tubSessionActive = true;
	tubPhase = TubPhase.NEED_TAKE;
	tubSessionGobId = gob.id;
	tubSessionMv = mv;
	tubSessionModflags = modflags;
	tubSessionItemactArgs = snapshotFillArgs(args);
	if(tubSessionItemactArgs != null && tubSessionItemactArgs.length > 2)
	    tubSessionItemactArgs[2] = Integer.valueOf(itemactWireModsForStationFill());
	tubWalkArrived = false;
	tubWalkStarted = false;
	tubNextTakeAt = 0;
	MoonInventoryWireDebug.maybeLogBulkTrace("tub-start", "mods=", Integer.valueOf(modflags), "gob=", Integer.valueOf((int)gob.id));
	return(MoonConfig.bulkTubSendFirstItemact);
    }

    private static boolean matchesTubInvRes(String resName) {
	if(resName == null)
	    return(false);
	String n = resName.toLowerCase(Locale.ROOT);
	return n.contains("hide") || n.contains("leather");
    }

    private static GItem findTubItemInInventory(Inventory inv) {
	if(inv == null || inv.wmap == null)
	    return(null);
	try {
	    for(GItem item : new ArrayList<>(inv.wmap.keySet())) {
		if(item == null)
		    continue;
		String nm = handResName(item);
		if(matchesTubInvRes(nm))
		    return(item);
	    }
	} catch(Exception ignored) {
	}
	return(null);
    }

    private static List<Coord2d> compressTubPath(List<Coord2d> src) {
	if(src == null || src.size() <= 2)
	    return(src == null) ? null : new ArrayList<>(src);
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
	if(out.isEmpty() || !tubSamePoint(out.get(out.size() - 1), end))
	    out.add(end);
	return(out);
    }

    private static boolean tubSamePoint(Coord2d a, Coord2d b) {
	return(a != null) && (b != null) && (a.dist(b) < 0.001);
    }

    private static void startTubWalk(GameUI gui, Gob tub) {
	if(gui == null || gui.map == null || tub == null || tubSessionMv == null)
	    return;
	try {
	    Gob pl = gui.map.player();
	    if(pl == null) {
		clearTubSession();
		return;
	    }
	    Coord2d cur = new Coord2d(pl.getc());
	    Coord2d goal = tub.rc;
	    MCache map = gui.map.glob.map;
	    OCache oc = gui.map.glob.oc;
	    long pg = MoonPacketHook.getPlayerGobId();
	    List<Coord2d> raw = MoonPathfinder.findPath(map, oc, cur, goal, pg,
		MoonConfig.teleportBlockHostileNearTarget, MoonConfig.teleportHostileClearTiles);
	    List<Coord2d> path = compressTubPath(raw);
	    if(path == null || path.isEmpty()) {
		clearTubSession();
		return;
	    }
	    tubPhase = TubPhase.WALKING;
	    tubWalkStarted = true;
	    tubWalkArrived = false;
	    MoonPathWalker.start(gui.map, path, () -> tubWalkArrived = true);
	} catch(Exception e) {
	    clearTubSession();
	}
    }

    /** After bulk fill stops, next tick will take another stack or re-deposit remainder. */
    private static void tubAfterFillEnded(GameUI gui) {
	if(!tubSessionActive || gui == null)
	    return;
	tubPhase = TubPhase.NEED_TAKE;
	tubNextTakeAt = 0;
    }

    /** Advance tub session: take if needed, walk, or start bulk fill. */
    private static void tubTryContinueSession(GameUI gui, double now) {
	if(!tubSessionActive || tubSessionMv == null)
	    return;
	MapView mv = tubSessionMv;
	Gob tub = null;
	try {
	    tub = mv.glob.oc.getgob(tubSessionGobId);
	} catch(Exception ignored) {
	}
	if(tub == null) {
	    clearTubSession();
	    return;
	}
	Gob pl = mv.player();
	if(pl == null) {
	    clearTubSession();
	    return;
	}
	double d = pl.rc.dist(tub.rc);
	GItem hand = currentHandItem(gui);
	if(hand != null) {
	    String hn = handResName(hand);
	    if(!matchesTubInvRes(hn)) {
		clearTubSession();
		return;
	    }
	} else {
	    if(findTubItemInInventory(gui.maininv) == null) {
		clearTubSession();
		return;
	    }
	}
	if(hand == null) {
	    tubPhase = TubPhase.NEED_TAKE;
	    return;
	}
	if(d > TUB_NEED_WALK_DIST) {
	    if(tubPhase != TubPhase.WALKING && !tubWalkStarted)
		startTubWalk(gui, tub);
	    return;
	}
	Object[] args = snapshotFillArgs(tubSessionItemactArgs);
	if(args == null) {
	    clearTubSession();
	    return;
	}
	startFill(mv, gui, "itemact", args, tubSessionModflags, "ttub", true, tubSessionGobId);
	fillPendingReplay = true;
	fillNextAt = now;
	replayStoredFill(now);
    }

    private static void tickTubRun(GameUI gui, double now) {
	if(!tubSessionActive || tubSessionMv == null)
	    return;
	if(tubWalkArrived) {
	    tubWalkArrived = false;
	    tubWalkStarted = false;
	    tubPhase = TubPhase.NEED_TAKE;
	    tubTryContinueSession(gui, now);
	}
	if(fillActive)
	    return;
	if(tubPhase == TubPhase.WALKING && tubWalkStarted)
	    return;
	MapView mv = tubSessionMv;
	Gob tub = null;
	try {
	    tub = mv.glob.oc.getgob(tubSessionGobId);
	} catch(Exception ignored) {
	}
	if(tub == null) {
	    clearTubSession();
	    return;
	}
	if(now < tubNextTakeAt)
	    return;
	if(currentHandItem(gui) == null) {
	    GItem it = findTubItemInInventory(gui.maininv);
	    if(it == null) {
		clearTubSession();
		return;
	    }
	    it.wdgmsg("take", Coord.z);
	    tubNextTakeAt = now + TUB_TAKE_RATE_SEC;
	    return;
	}
	tubTryContinueSession(gui, now);
    }
}
