/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;
import static haven.OCache.posres;
import core.ModuleLoader;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import haven.render.*;
import haven.MCache.OverlayInfo;
import haven.render.sl.Uniform;
import haven.render.sl.Type;
import modules.Module;

public class MapView extends PView implements DTarget, Console.Directory {
    public static boolean clickdb = false;
    /** MooNWide: brief screen flash at world point after {@link TeleportManager} navigation. */
    private static volatile double moonNavFlashUntil;
    private static volatile Coord2d moonNavFlashAt;

    public static void moonNavTriggerFlash(Coord2d mc) {
	moonNavFlashAt = mc;
	moonNavFlashUntil = Utils.rtime() + 0.45;
    }
    public long plgob = -1;
    public Coord2d cc;
    public final Glob glob;
    private boolean diabloHeld = false;
    private Coord diabloLastC = null;
    private Coord2d diabloLastMC = null;
    private boolean diabloPendingHit = false;
    private int view = 2;
    private Collection<Delayed> delayed = new LinkedList<Delayed>();
    private Collection<Delayed> delayed2 = new LinkedList<Delayed>();
    public Camera camera = restorecam();
    private Loader.Future<Plob> placing = null;
    /**
     * Set on loader thread right after {@link Plob#place()} so UI can draw hitboxes without relying
     * only on {@link Loader.Future#done()} (ordering vs frame tick).
     */
    private volatile Gob placementPreviewGob = null;

    /**
     * MooNWide: placement preview is a {@link Plob} on this widget's scene graph, not in {@link OCache}.
     * Hitbox overlays must draw it explicitly.
     */
    public Gob moonActivePlacementGob() {
	Gob ref = placementPreviewGob;
	if(ref != null)
	    return(ref);
	Loader.Future<Plob> f = placing;
	if(f == null || !f.done())
	    return(null);
	try {
	    return(f.get());
	} catch(RuntimeException e) {
	    return(null);
	}
    }
    private Grabber grab;
    private Selector selection;
    /** MooNWide: client-only tile rectangle; does not send <code>sel</code> to the server. */
    private ClientTileSelector moonClientSel;
    private Coord moonStorageHoverAt = null;
    private Object moonStorageHoverTip = null;
    private boolean moonStorageHoverPending = false;
    private int moonStorageHoverSeq = 0;
    private double moonStorageHoverReqAt = 0.0;
    private Coord3f camoff = new Coord3f(Coord3f.o);
    private Coord2d moveTraceTarget = null;
    private double moveTraceSetAt = 0;
    /** Manual route waypoints — Alt+click to add, character walks sequentially. */
    private final ArrayDeque<Coord2d> moveTraceQueue = new ArrayDeque<>();
    /** Rate limit for diablo-move auto clicks (reduces redundant traffic). */
    private static final double MOON_AUTOMOVE_CLICK_MIN_DT = 0.06;
    private static final double MOVE_TRACE_MIN_VISIBLE_DT = 0.35;
    private static final double MOON_GATE_ASSIST_PRECLICK_TIMEOUT = 0.65;
    private static final double MOON_GATE_ASSIST_RETRY_DT = 0.22;
    private static final double MOON_GATE_ASSIST_CLOSE_DT = 0.01;
    private static final double MOON_GATE_ASSIST_EXPIRE_DT = 3.0;
    private static final double MOON_GATE_ASSIST_SUPPRESS_DT = 0.90;
    private static final int MOON_GATE_STAGE_OPEN = 0;
    private static final int MOON_GATE_STAGE_PASS = 1;
    private static final int MOON_GATE_STAGE_CLOSE = 2;
    private double moonLastAutomoveClickTs = -1e9;
    /** Separate throttle so Diablo-move spam does not block manual-route waypoint clicks. */
    private double moonLastPathClickTs = -1e9;
    private static final double MOON_COMBAT_GUIDE_SCAN_INTERVAL = 0.12;
    private double moonCombatGuideScanAt = -1;
    private List<MoonCombatGuideSeg> moonCombatGuideSegs = Collections.emptyList();
    /** Apply saved {@link MoonConfig#mapGridMode} once after the map exists. */
    private boolean moonMapGridBoot;
    /** {@link MoonPathWalker} queues clicks here; {@link #tick} sends on the UI thread. */
    private final Object moonPathLock = new Object();
    private Coord2d moonPathClickTodo = null;
    /** Client-side route preview for navigation/pathfinder movement. */
    private Coord2d moonRouteTraceTarget = null;
    private final ArrayDeque<Coord2d> moonRouteTraceQueue = new ArrayDeque<>();
    private MoonPassiveGate.Assist moonGateAssist = null;
    private int moonGateAssistMods = 0;
    private int moonGateAssistStage = 0;
    private int moonGateAssistAttempts = 0;
    private double moonGateAssistStartedAt = 0.0;
    private double moonGateAssistNextAt = 0.0;
    private boolean moonGateAssistCrossed = false;
    private boolean moonGateAssistCloseSent = false;
    private long moonGateSuppressId = 0L;
    private double moonGateSuppressUntil = 0.0;
    private Text moveSpeedText = null;
    private double moveSpeedValue = Double.NaN;
    public double shake = 0.0;
    public static double plobpgran = Utils.getprefd("plobpgran", 8);
    public static double plobagran = Utils.getprefd("plobagran", 12);
    private static final Map<String, Class<? extends Camera>> camtypes = new HashMap<String, Class<? extends Camera>>();
    private static final Text.Furnace movespeedf = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 12, Color.WHITE).aa(true), 1, 1, Color.BLACK);
    private static final float movetraceheadz = 12.0f;
    private static final float movespeedz = 22.0f;

    private static final class MoonCombatGuideSeg {
	final Coord3f from;
	final Coord3f to;
	final long moverId;

	MoonCombatGuideSeg(Coord3f from, Coord3f to, long moverId) {
	    this.from = from;
	    this.to = to;
	    this.moverId = moverId;
	}
    }

    private enum MoonBuildStage {
	TERRAIN_CRITICAL,
	CLICKMAP,
	GOBS,
	TERRAIN_SECONDARY,
	OPTIONAL
    }

    public static final class MoonLoadProgress {
	public static final MoonLoadProgress EMPTY = new MoonLoadProgress(false, false, false, 0, 0, 0, 0, 0, 0, 0, 0);
	public final boolean revealReady;
	public final boolean secondaryDone;
	public final boolean loading;
	public final int criticalReady;
	public final int criticalTotal;
	public final int sceneReady;
	public final int sceneTotal;
	public final int gobReady;
	public final int gobPending;
	public final int optionalReady;
	public final int optionalTotal;

	public MoonLoadProgress(boolean revealReady, boolean secondaryDone, boolean loading,
			       int criticalReady, int criticalTotal,
			       int sceneReady, int sceneTotal,
			       int gobReady, int gobPending,
			       int optionalReady, int optionalTotal) {
	    this.revealReady = revealReady;
	    this.secondaryDone = secondaryDone;
	    this.loading = loading;
	    this.criticalReady = criticalReady;
	    this.criticalTotal = criticalTotal;
	    this.sceneReady = sceneReady;
	    this.sceneTotal = sceneTotal;
	    this.gobReady = gobReady;
	    this.gobPending = gobPending;
	    this.optionalReady = optionalReady;
	    this.optionalTotal = optionalTotal;
	}

	public double revealProgress() {
	    if(criticalTotal <= 0)
		return(revealReady ? 1.0 : 0.0);
	    return(Math.max(0.0, Math.min(1.0, (double)criticalReady / (double)criticalTotal)));
	}

	public double streamProgress() {
	    int total = sceneTotal + gobReady + gobPending + optionalTotal;
	    if(total <= 0)
		return(secondaryDone ? 1.0 : revealProgress());
	    int ready = sceneReady + gobReady + optionalReady;
	    return(Math.max(0.0, Math.min(1.0, (double)ready / (double)total)));
	}

	public String hudSummary() {
	    if(!loading)
		return("map ready");
	    return(String.format(Locale.ROOT, "map %d/%d", sceneReady + optionalReady, Math.max(1, sceneTotal + optionalTotal)));
	}
    }

    private volatile MoonLoadProgress moonLoadProgress = MoonLoadProgress.EMPTY;
    private boolean moonReducedBudgets = false;
    private int moonStableBudgetFrames = 0;
    private int moonTerrainCriticalBudget = Integer.MAX_VALUE;
    private int moonTerrainSecondaryBudget = Integer.MAX_VALUE;
    private int moonGobBuildBudget = Integer.MAX_VALUE;
    private long moonTerrainCriticalDeadline = Long.MAX_VALUE;
    private long moonTerrainSecondaryDeadline = Long.MAX_VALUE;
    private long moonGobBuildDeadline = Long.MAX_VALUE;
    private double moonLastSecondaryWorldWorkAt = Double.NEGATIVE_INFINITY;
    private double moonLastOverlaySyncAt = Double.NEGATIVE_INFINITY;
    private Area moonOverlaySyncArea = null;
    private int moonOverlaySyncMapSeq = Integer.MIN_VALUE;
    private int moonOverlaySyncTagSeq = Integer.MIN_VALUE;
    private volatile int moonOverlayTagSeq = 0;

    private boolean moonPathHighlightEnabled() {
	boolean enabled = MoonConfig.combatDrawClickPath;
	try {
	    Module m = ModuleLoader.getModule("Path Highlighter");
	    if(m != null)
		enabled = enabled || m.isEnabled();
	} catch(Exception ignored) {
	}
	return enabled;
    }

    private boolean moonTryReserveAutomoveClick() {
	double now = Utils.rtime();
	if(now - moonLastAutomoveClickTs < MOON_AUTOMOVE_CLICK_MIN_DT)
	    return(false);
	moonLastAutomoveClickTs = now;
	return(true);
    }

    private boolean moonTryPathClick() {
	double now = Utils.rtime();
	if(now - moonLastPathClickTs < MOON_AUTOMOVE_CLICK_MIN_DT)
	    return(false);
	moonLastPathClickTs = now;
	return(true);
    }

    private void setMoveTraceTarget(Coord2d mc, int modflags) {
	boolean isAlt = (modflags & UI.MOD_META) != 0;
	Coord2d wp = mc;
	if(MoonConfig.moveTraceTileCenter) {
	    Coord tc = wp.floor(MCache.tilesz);
	    wp = tc.mul(MCache.tilesz).add(MCache.tilesz.div(2));
	}
	if(isAlt) {
	    if(moveTraceTarget == null)
		moveTraceTarget = wp;
	    else
		moveTraceQueue.addLast(wp);
	} else {
	    moveTraceQueue.clear();
	    moveTraceTarget = wp;
	}
	moveTraceSetAt = Utils.rtime();
    }

    private void clearMoveTraceTarget() {
	moveTraceTarget = null;
	moveTraceSetAt = 0;
	moveTraceQueue.clear();
    }

    /** 3D hits, minimap, etc.: update client move trace (LMB/RMB). */
    public void updateMoveTrace(Coord2d mc, int button, int modflags) {
	if(mc == null)
	    return;
	if(MoonTreeSpotClick.tryConsume(ui, mc, button, modflags))
	    return;
	if(button == 1 || button == 3)
	    setMoveTraceTarget(mc, modflags);
    }

    private void moonClearGateAssist() {
	moonGateAssist = null;
	moonGateAssistMods = 0;
	moonGateAssistStage = 0;
	moonGateAssistAttempts = 0;
	moonGateAssistStartedAt = 0.0;
	moonGateAssistNextAt = 0.0;
	moonGateAssistCrossed = false;
	moonGateAssistCloseSent = false;
    }

    private boolean moonGateAssistSuppressed(MoonPassiveGate.Assist assist, double now) {
	return assist != null && assist.gateId == moonGateSuppressId && now < moonGateSuppressUntil;
    }

    private void moonSuppressGateAssist(MoonPassiveGate.Assist assist, double now, String reason) {
	if(assist != null) {
	    moonGateSuppressId = assist.gateId;
	    moonGateSuppressUntil = now + MOON_GATE_ASSIST_SUPPRESS_DT;
	}
	if(reason != null && !reason.isBlank())
	    MoonPassiveGate.noteBlocked(reason);
    }

    private boolean moonSendGateUseClick(MoonPassiveGate.Assist assist) {
	if(assist == null)
	    return(false);
	GameUI gui = getparent(GameUI.class);
	Gob gate = assist.gate(this);
	if(gui == null || gate == null || gate.removed)
	    return(false);
	return(MoonSmartInteract.sendRawGobClick(gui, gate, 3));
    }

    private boolean moonStartGateAssist(MoonPassiveGate.Assist assist, int mods) {
	if(assist == null)
	    return(false);
	double now = Utils.rtime();
	if(moonGateAssistSuppressed(assist, now)) {
	    MoonPassiveGate.noteBlocked("close suppressed/already sent");
	    return(false);
	}
	int sendMods = mods & ~UI.MOD_META;
	Boolean open = assist.gateLikelyOpen(this);
	boolean needOpen = (open == null) || !open.booleanValue();
	if(needOpen && !moonSendGateUseClick(assist)) {
	    MoonPassiveGate.noteBlocked("open failed");
	    return(false);
	}
	if(!moonSyntheticMapClick(assist.target, null, 1, sendMods)) {
	    MoonPassiveGate.noteBlocked("pass click failed");
	    return(false);
	}
	moonGateAssist = assist;
	moonGateAssistMods = sendMods;
	moonGateAssistStage = MOON_GATE_STAGE_PASS;
	moonGateAssistAttempts = 1;
	moonGateAssistStartedAt = now;
	moonGateAssistNextAt = moonGateAssistStartedAt + MOON_GATE_ASSIST_RETRY_DT;
	moonGateAssistCrossed = false;
	moonGateAssistCloseSent = false;
	MoonPassiveGate.noteAction((needOpen ? "open+pass " : "pass open ") + assist.gateName);
	return(true);
    }

    private void moonContinueAfterGate(MoonPassiveGate.Assist assist) {
	if(assist == null || !assist.hasContinueTarget())
	    return;
	if(moonSyntheticMapClick(assist.finalTarget, null, 1, moonGateAssistMods)) {
	    updateMoveTrace(assist.finalTarget, 1, moonGateAssistMods);
	    MoonPassiveGate.noteAction("continue " + assist.gateName);
	}
    }

    private void moonTickGateAssist(Gob player) {
	MoonPassiveGate.Assist assist = moonGateAssist;
	if(assist == null)
	    return;
	double now = Utils.rtime();
	if(player == null || (now - moonGateAssistStartedAt) >= MOON_GATE_ASSIST_EXPIRE_DT) {
	    moonClearGateAssist();
	    return;
	}
	Gob gate = assist.gate(this);
	if(gate == null || gate.removed) {
	    moonClearGateAssist();
	    return;
	}
	if(!moonGateAssistCrossed && assist.playerCrossedPlane(this, player)) {
	    moonGateAssistCrossed = true;
	    MoonPassiveGate.noteAction("crossed " + assist.gateName);
	}
	if(moonGateAssistStage == MOON_GATE_STAGE_CLOSE) {
	    if(moonGateAssistCloseSent) {
		moonClearGateAssist();
		return;
	    }
	    if(now < moonGateAssistNextAt)
		return;
	    if(!moonGateAssistCrossed) {
		moonSuppressGateAssist(assist, now, "close suppressed/already sent");
		moonClearGateAssist();
		return;
	    }
	    if(assist.playerCanClose(this, player)) {
		moonGateAssistCloseSent = true;
		moonSendGateUseClick(assist);
		MoonPassiveGate.noteAction("close once " + assist.gateName);
		moonSuppressGateAssist(assist, now, "close suppressed/already sent");
		moonContinueAfterGate(assist);
	    } else {
		moonSuppressGateAssist(assist, now, "close suppressed/already sent");
		moonContinueAfterGate(assist);
	    }
	    moonClearGateAssist();
	    return;
	}
	if(assist.playerPassed(this, player)) {
	    moonGateAssistStage = MOON_GATE_STAGE_CLOSE;
	    moonGateAssistNextAt = now + MOON_GATE_ASSIST_CLOSE_DT;
	    MoonPassiveGate.noteAction("close pending " + assist.gateName);
	    return;
	}
	if(now < moonGateAssistNextAt)
	    return;
	if(moonGateAssistAttempts >= 4) {
	    MoonPassiveGate.noteBlocked("pass failed");
	    moonClearGateAssist();
	    return;
	}
	if(moonSyntheticMapClick(assist.target, null, 1, moonGateAssistMods)) {
	    moonGateAssistAttempts++;
	    moonGateAssistStage = MOON_GATE_STAGE_PASS;
	    moonGateAssistNextAt = now + MOON_GATE_ASSIST_RETRY_DT;
	    MoonPassiveGate.noteAction("retry pass " + assist.gateName);
	} else {
	    moonGateAssistNextAt = now + 0.15;
	}
    }

    /**
     * Schedule a map click from a worker thread; blocks until {@link #tick} sends it or times out.
     * Used by {@link MoonPathWalker} so {@link #wdgmsg} runs only on the UI thread.
     */
    public void moonPathWalkerAwaitClick(Coord2d wp, long timeoutMs) throws InterruptedException {
	synchronized(moonPathLock) {
	    moonPathClickTodo = wp;
	    long deadline = System.currentTimeMillis() + timeoutMs;
	    while(moonPathClickTodo != null && !Thread.currentThread().isInterrupted()) {
		long w = deadline - System.currentTimeMillis();
		if(w <= 0) {
		    if(moonPathClickTodo == wp)
			moonPathClickTodo = null;
		    break;
		}
		moonPathLock.wait(Math.min(w, 200L));
	    }
	}
    }

    /** Wake {@link MoonPathWalker} blocked in {@link #moonPathWalkerAwaitClick} (e.g. on cancel). */
    public void moonPathWalkerCancelPending() {
	synchronized(moonPathLock) {
	    moonPathClickTodo = null;
	    moonPathLock.notifyAll();
	}
    }

    public void moonSetRouteTrace(List<Coord2d> path) {
	synchronized(moonPathLock) {
	    moonRouteTraceTarget = null;
	    moonRouteTraceQueue.clear();
	    if(path != null && !path.isEmpty()) {
		moonRouteTraceTarget = path.get(0);
		for(int i = 1; i < path.size(); i++)
		    moonRouteTraceQueue.addLast(path.get(i));
	    }
	}
    }

    public void moonClearRouteTrace() {
	synchronized(moonPathLock) {
	    moonRouteTraceTarget = null;
	    moonRouteTraceQueue.clear();
	}
    }

    private int currentview() {
	int ret = Math.max(2, view);
	if(!(camera instanceof OrthoCam))
	    return(ret + MoonConfig.mapLoadExtraCuts);
	OrthoCam oc = (OrthoCam)camera;
	float aspect = (sz.x <= 0) ? 1.0f : ((float)sz.y / (float)sz.x);
	double maxspan = Math.max(oc.field, oc.field * aspect);
	double cutworld = MCache.cutsz.x * tilesz.x;
	int dyn = (int)Math.ceil(maxspan / cutworld) + 2;
	return(Math.max(ret, dyn) + MoonConfig.mapLoadExtraCuts);
    }

    public MoonLoadProgress moonLoadProgress() {
	return(moonLoadProgress);
    }

    public boolean moonShouldShedOptional() {
	return(MoonConfig.perfAutoShed && (MoonPerfOverlay.inStressWindow() || moonLoadProgress.loading));
    }

    private void moonRefreshBuildBudgets() {
	boolean enabled = MoonConfig.progressiveWorldLoad;
	boolean distress = MoonConfig.perfAutoShed && MoonPerfOverlay.inStressWindow();
	if(!enabled) {
	    moonReducedBudgets = false;
	    moonStableBudgetFrames = 0;
	    moonTerrainCriticalBudget = Integer.MAX_VALUE;
	    moonTerrainSecondaryBudget = Integer.MAX_VALUE;
	    moonGobBuildBudget = Integer.MAX_VALUE;
	    return;
	}
	if(distress) {
	    moonReducedBudgets = true;
	    moonStableBudgetFrames = 0;
	} else if(moonReducedBudgets) {
	    if(++moonStableBudgetFrames >= 20)
		moonReducedBudgets = false;
	}
	int crit = MoonConfig.perfTerrainCriticalBudget;
	int sec = MoonConfig.perfTerrainSecondaryBudget;
	int gob = MoonConfig.perfGobBuildBudget;
	if(moonReducedBudgets) {
	    crit = Math.max(1, (crit + 1) / 2);
	    sec = Math.max(1, (sec + 1) / 2);
	    gob = Math.max(2, (gob + 1) / 2);
	}
	moonTerrainCriticalBudget = crit;
	moonTerrainSecondaryBudget = sec;
	moonGobBuildBudget = gob;
    }

    private boolean moonTryConsumeBuildBudget(MoonBuildStage stage) {
	if(moonBuildDeadlineReached(stage))
	    return(false);
	switch(stage) {
	case TERRAIN_CRITICAL:
	case CLICKMAP:
	    if(moonTerrainCriticalBudget <= 0)
		return(false);
	    moonTerrainCriticalBudget--;
	    return(true);
	case GOBS:
	    if(moonGobBuildBudget <= 0)
		return(false);
	    moonGobBuildBudget--;
	    return(true);
	case TERRAIN_SECONDARY:
	case OPTIONAL:
	default:
	    if(moonTerrainSecondaryBudget <= 0)
		return(false);
	    moonTerrainSecondaryBudget--;
	    return(true);
	}
    }

    private long moonStageDeadline(MoonBuildStage stage) {
	switch(stage) {
	case TERRAIN_CRITICAL:
	case CLICKMAP:
	    return(moonTerrainCriticalDeadline);
	case GOBS:
	    return(moonGobBuildDeadline);
	case TERRAIN_SECONDARY:
	case OPTIONAL:
	default:
	    return(moonTerrainSecondaryDeadline);
	}
    }

    private boolean moonBuildDeadlineReached(MoonBuildStage stage) {
	long deadline = moonStageDeadline(stage);
	return((deadline != Long.MAX_VALUE) && (System.nanoTime() >= deadline));
    }

    private long moonStageBudgetNanos(MoonBuildStage stage) {
	boolean loading = moonLoadProgress.loading;
	boolean stress = MoonConfig.perfAutoShed && MoonPerfOverlay.inStressWindow();
	if(!MoonConfig.progressiveWorldLoad && !stress)
	    return(Long.MAX_VALUE);
	double ms;
	switch(stage) {
	case TERRAIN_CRITICAL:
	case CLICKMAP:
	    ms = stress ? 1.35 : (loading ? 2.20 : 3.20);
	    break;
	case GOBS:
	    ms = stress ? 1.10 : (loading ? 1.80 : 2.60);
	    break;
	case TERRAIN_SECONDARY:
	case OPTIONAL:
	default:
	    ms = stress ? 0.55 : (loading ? 0.90 : 1.50);
	    break;
	}
	return((long)(ms * 1_000_000.0));
    }

    private void moonArmBuildWindow(MoonBuildStage stage) {
	long budget = moonStageBudgetNanos(stage);
	long deadline = (budget == Long.MAX_VALUE) ? Long.MAX_VALUE : (System.nanoTime() + budget);
	switch(stage) {
	case TERRAIN_CRITICAL:
	case CLICKMAP:
	    moonTerrainCriticalDeadline = deadline;
	    break;
	case GOBS:
	    moonGobBuildDeadline = deadline;
	    break;
	case TERRAIN_SECONDARY:
	case OPTIONAL:
	default:
	    moonTerrainSecondaryDeadline = deadline;
	    break;
	}
    }

    private double moonPlayerSpeed() {
	try {
	    Gob pl = player();
	    if(pl == null)
		return(0.0);
	    Moving mv = pl.getattr(Moving.class);
	    return((mv == null) ? 0.0 : pl.getv());
	} catch(Loading ignored) {
	    return(0.0);
	} catch(Exception ignored) {
	    return(0.0);
	}
    }

    private double moonSecondaryCadence() {
	boolean loading = moonLoadProgress.loading;
	boolean stress = MoonConfig.perfAutoShed && MoonPerfOverlay.inStressWindow();
	double speed = moonPlayerSpeed();
	if((loading || stress) && (speed >= 2.0))
	    return(stress ? 0.12 : 0.09);
	if(loading)
	    return(0.06);
	if(stress)
	    return(0.05);
	return(0.0);
    }

    private boolean moonShouldRunSecondaryWorldWork(double now) {
	double interval = moonSecondaryCadence();
	return((interval <= 0.0) || ((now - moonLastSecondaryWorldWorkAt) >= interval));
    }

    private double moonOverlayCadence() {
	double secondary = moonSecondaryCadence();
	if(secondary >= 0.09)
	    return(0.12);
	if(secondary > 0.0)
	    return(0.07);
	return(0.04);
    }

    private boolean moonShouldSyncOverlays(double now) {
	Area area = terrain.area;
	if(area == null)
	    return(!ols.isEmpty());
	if((moonOverlaySyncArea == null) || !moonOverlaySyncArea.equals(area))
	    return(true);
	if(moonOverlaySyncMapSeq != glob.map.olseq)
	    return(true);
	if(moonOverlaySyncTagSeq != moonOverlayTagSeq)
	    return(true);
	return((now - moonLastOverlaySyncAt) >= moonOverlayCadence());
    }

    private void moonMarkOverlaySync(double now) {
	Area area = terrain.area;
	moonLastOverlaySyncAt = now;
	moonOverlaySyncArea = (area == null) ? null : Area.corn(area.ul, area.br);
	moonOverlaySyncMapSeq = glob.map.olseq;
	moonOverlaySyncTagSeq = moonOverlayTagSeq;
    }
    
    public interface Delayed {
	public void run(GOut g);
    }

    public interface Grabber {
	boolean mmousedown(Coord mc, int button);
	boolean mmouseup(Coord mc, int button);
	boolean mmousewheel(Coord mc, int amount);
	void mmousemove(Coord mc);
    }

    public abstract class Camera implements Pipe.Op {
	protected haven.render.Camera view = new haven.render.Camera(Matrix4f.identity());
	protected Projection proj = new Projection(Matrix4f.identity());
	
	public Camera() {
	    resized();
	}

	public boolean keydown(KeyDownEvent ev) {
	    return(false);
	}

	public boolean click(Coord sc) {
	    return(false);
	}
	public void drag(Coord sc) {}
	public void release() {}
	public boolean wheel(Coord sc, int amount) {
	    return(false);
	}
	
	public void resized() {
	    float field = 0.5f;
	    float aspect = ((float)sz.y) / ((float)sz.x);
	    proj = Projection.frustum(-field, field, -aspect * field, aspect * field, 1, 2000);
	}

	public void apply(Pipe p) {
	    proj.apply(p);
	    view.apply(p);
	}
	
	public abstract float angle();
	public abstract void tick(double dt);

	public String stats() {return("N/A");}
    }
    
    public class FollowCam extends Camera {
	private final float fr = 0.0f, h = 10.0f;
	private float ca, cd;
	private Coord3f curc = null;
	private float elev, telev;
	private float angl, tangl;
	private Coord dragorig = null;
	private float anglorig;
	
	public FollowCam() {
	    elev = telev = (float)Math.PI / 6.0f;
	    angl = tangl = 0.0f;
	}
	
	public void resized() {
	    ca = (float)sz.y / (float)sz.x;
	    cd = 400.0f * ca;
	}
	
	public boolean click(Coord c) {
	    anglorig = tangl;
	    dragorig = c;
	    return(true);
	}
	
	public void drag(Coord c) {
	    tangl = anglorig + ((float)(c.x - dragorig.x) / 100.0f);
	    tangl = tangl % ((float)Math.PI * 2.0f);
	}

	private double f0 = 0.2, f1 = 0.5, f2 = 0.9;
	private double fl = Math.sqrt(2);
	private double fa = ((fl * (f1 - f0)) - (f2 - f0)) / (fl - 2);
	private double fb = ((f2 - f0) - (2 * (f1 - f0))) / (fl - 2);
	private float field(float elev) {
	    double a = elev / (Math.PI / 4);
	    return((float)(f0 + (fa * a) + (fb * Math.sqrt(a))));
	}

	private float dist(float elev) {
	    float da = (float)Math.atan(ca * field(elev));
	    return((float)(((cd - (h / Math.tan(elev))) * Math.sin(elev - da) / Math.sin(da)) - (h / Math.sin(elev))));
	}

	public void tick(double dt) {
	    elev += (telev - elev) * (float)(1.0 - Math.pow(500, -dt));
	    if(Math.abs(telev - elev) < 0.0001)
		elev = telev;
	    
	    float dangl = tangl - angl;
	    while(dangl >  Math.PI) dangl -= (float)(2 * Math.PI);
	    while(dangl < -Math.PI) dangl += (float)(2 * Math.PI);
	    angl += dangl * (float)(1.0 - Math.pow(500, -dt));
	    if(Math.abs(tangl - angl) < 0.0001)
		angl = tangl;
	    
	    Coord3f cc = getcc().invy();
	    if(curc == null)
		curc = cc;
	    float dx = cc.x - curc.x, dy = cc.y - curc.y;
	    float dist = (float)Math.sqrt((dx * dx) + (dy * dy));
	    if(dist > 250) {
		curc = cc;
	    } else if(dist > fr) {
		Coord3f oc = curc;
		float pd = (float)Math.cos(elev) * dist(elev);
		Coord3f cambase = new Coord3f(curc.x + ((float)Math.cos(tangl) * pd), curc.y + ((float)Math.sin(tangl) * pd), 0.0f);
		float a = cc.xyangle(curc);
		float nx = cc.x + ((float)Math.cos(a) * fr), ny = cc.y + ((float)Math.sin(a) * fr);
		Coord3f tgtc = new Coord3f(nx, ny, cc.z);
		curc = curc.add(tgtc.sub(curc).mul((float)(1.0 - Math.pow(500, -dt))));
		if(curc.dist(tgtc) < 0.01)
		    curc = tgtc;
		tangl = curc.xyangle(cambase);
	    }
	    
	    float field = field(elev);
	    view = haven.render.Camera.pointed(curc.add(camoff).add(0.0f, 0.0f, h), dist(elev), elev, angl);
	    proj = Projection.frustum(-field, field, -ca * field, ca * field, 1, 2000);
	}

	public float angle() {
	    return(angl);
	}
	
	private static final float maxang = (float)(Math.PI / 2 - 0.1);
	private static final float mindist = 50.0f;
	public boolean wheel(Coord c, int amount) {
	    float fe = telev;
	    telev += amount * telev * 0.02f;
	    if(telev > maxang)
		telev = maxang;
	    if(dist(telev) < mindist)
		telev = fe;
	    return(true);
	}

	public String stats() {
	    return(String.format("%f %f %f", elev, dist(elev), field(elev)));
	}
    }
    static {camtypes.put("follow", FollowCam.class);}

    public class SimpleCam extends Camera {
	private float dist = 50.0f;
	private float elev = (float)Math.PI / 4.0f;
	private float angl = 0.0f;
	private Coord dragorig = null;
	private float elevorig, anglorig;

	public void tick(double dt) {
	    Coord3f cc = getcc().invy();
	    view = haven.render.Camera.pointed(cc.add(camoff).add(0.0f, 0.0f, 15f), dist, elev, angl);
	}
	
	public float angle() {
	    return(angl);
	}
	
	public boolean click(Coord c) {
	    elevorig = elev;
	    anglorig = angl;
	    dragorig = c;
	    return(true);
	}
	
	public void drag(Coord c) {
	    elev = elevorig - ((float)(c.y - dragorig.y) / 100.0f);
	    if(elev < 0.0f) elev = 0.0f;
	    if(elev > (Math.PI / 2.0)) elev = (float)Math.PI / 2.0f;
	    angl = anglorig + ((float)(c.x - dragorig.x) / 100.0f);
	    angl = angl % ((float)Math.PI * 2.0f);
	}

	public boolean wheel(Coord c, int amount) {
	    float d = dist + (amount * 25);
	    if(d < 5)
		d = 5;
	    dist = d;
	    return(true);
	}
    }
    static {camtypes.put("worse", SimpleCam.class);}

    public class FreeCam extends Camera {
	private float dist = 50.0f, tdist = dist;
	private float elev = (float)Math.PI / 4.0f, telev = elev;
	private float angl = 0.0f, tangl = angl;
	private Coord dragorig = null;
	private float elevorig, anglorig;
	private final float pi2 = (float)(Math.PI * 2);
	private Coord3f cc = null;

	public void tick(double dt) {
	    float cf = (1f - (float)Math.pow(500, -dt * 3));
	    angl = angl + ((tangl - angl) * cf);
	    while(angl > pi2) {angl -= pi2; tangl -= pi2; anglorig -= pi2;}
	    while(angl < 0)   {angl += pi2; tangl += pi2; anglorig += pi2;}
	    if(Math.abs(tangl - angl) < 0.0001) angl = tangl;

	    elev = elev + ((telev - elev) * cf);
	    if(Math.abs(telev - elev) < 0.0001) elev = telev;

	    dist = dist + ((tdist - dist) * cf);
	    if(Math.abs(tdist - dist) < 0.0001) dist = tdist;

	    Coord3f mc = getcc().invy();
	    if((cc == null) || (Math.hypot(mc.x - cc.x, mc.y - cc.y) > 250))
		cc = mc;
	    else
		cc = cc.add(mc.sub(cc).mul(cf));
	    view = haven.render.Camera.pointed(cc.add(0.0f, 0.0f, 15f), dist, elev, angl);
	}

	public float angle() {
	    return(angl);
	}

	public boolean click(Coord c) {
	    elevorig = elev;
	    anglorig = angl;
	    dragorig = c;
	    return(true);
	}

	public void drag(Coord c) {
	    telev = elevorig - ((float)(c.y - dragorig.y) / 100.0f);
	    if(telev < 0.0f) telev = 0.0f;
	    if(telev > (Math.PI / 2.0)) telev = (float)Math.PI / 2.0f;
	    tangl = anglorig + ((float)(c.x - dragorig.x) / 100.0f);
	}

	public boolean wheel(Coord c, int amount) {
	    float d = tdist + (amount * 25);
	    if(d < 5)
		d = 5;
	    tdist = d;
	    return(true);
	}
    }
    static {camtypes.put("bad", FreeCam.class);}
    
    public class OrthoCam extends Camera {
	public boolean exact = true;
	protected float dfield = (float)(100 * Math.sqrt(2));
	protected float dist = 500.0f;
	protected float elev = (float)Math.PI / 6.0f;
	protected float angl = -(float)Math.PI / 4.0f;
	protected float field = dfield;
	private Coord dragorig = null;
	private float anglorig;
	protected Coord3f cc, jc;

	public void tick2(double dt) {
	    this.cc = getcc().invy();
	}

	public void tick(double dt) {
	    tick2(dt);
	    float aspect = ((float)sz.y) / ((float)sz.x);
	    Matrix4f vm = haven.render.Camera.makepointed(new Matrix4f(), cc.add(camoff).add(0.0f, 0.0f, 15f), dist, elev, angl);
	    if(exact) {
		if(jc == null)
		    jc = cc;
		float pfac = rsz.x / (field * 2);
		Coord3f vjc = vm.mul4(jc).mul(pfac);
		Coord3f corr = new Coord3f(Math.round(vjc.x) - vjc.x, Math.round(vjc.y) - vjc.y, 0).div(pfac);
		if((Math.abs(vjc.x) > 500) || (Math.abs(vjc.y) > 500))
		    jc = null;
		vm = Location.makexlate(new Matrix4f(), corr).mul1(vm);
	    }
	    view = new haven.render.Camera(vm);
	    proj = Projection.ortho(-field, field, -field * aspect, field * aspect, 1, 5000);
	}

	public float angle() {
	    return(angl);
	}

	public boolean click(Coord c) {
	    anglorig = angl;
	    dragorig = c;
	    return(true);
	}

	public void drag(Coord c) {
	    angl = anglorig + ((float)(c.x - dragorig.x) / 100.0f);
	    angl = angl % ((float)Math.PI * 2.0f);
	}

	public String stats() {
	    return(String.format("%.1f %.2f %.2f %.1f", dist, elev / Math.PI, angl / Math.PI, field));
	}
    }

    public static KeyBinding kb_camleft  = KeyBinding.get("cam-left",  KeyMatch.forcode(KeyEvent.VK_LEFT, 0));
    public static KeyBinding kb_camright = KeyBinding.get("cam-right", KeyMatch.forcode(KeyEvent.VK_RIGHT, 0));
    public static KeyBinding kb_camin    = KeyBinding.get("cam-in",    KeyMatch.forcode(KeyEvent.VK_UP, 0));
    public static KeyBinding kb_camout   = KeyBinding.get("cam-out",   KeyMatch.forcode(KeyEvent.VK_DOWN, 0));
    public static KeyBinding kb_camreset = KeyBinding.get("cam-reset", KeyMatch.forcode(KeyEvent.VK_HOME, 0));
    public class SOrthoCam extends OrthoCam {
	private Coord dragorig = null;
	private float anglorig;
	private float tangl = angl;
	private float tfield = field;
	private boolean isometric = true;
	private final float pi2 = (float)(Math.PI * 2);
	private double tf = 1.0;

	public SOrthoCam(String... args) {
	    PosixArgs opt = PosixArgs.getopt(args, "enift:Z:");
	    for(char c : opt.parsed()) {
		switch(c) {
		case 'e':
		    exact = true;
		    break;
		case 'n':
		    exact = false;
		    break;
		case 'i':
		    isometric = true;
		    break;
		case 'f':
		    isometric = false;
		    break;
		case 't':
		    tf = Double.parseDouble(opt.arg);
		    break;
		case 'Z':
		    field = tfield = dfield = Float.parseFloat(opt.arg);
		    break;
		}
	    }
	}

	public void tick2(double dt) {
	    dt *= tf;
	    float cf = 1f - (float)Math.pow(500, -dt);
	    Coord3f mc = getcc().invy();
	    if((cc == null) || (Math.hypot(mc.x - cc.x, mc.y - cc.y) > 250))
		cc = mc;
	    else if(!exact || (mc.dist(cc) > 2))
		cc = cc.add(mc.sub(cc).mul(cf));

	    angl = angl + ((tangl - angl) * cf);
	    while(angl > pi2) {angl -= pi2; tangl -= pi2; anglorig -= pi2;}
	    while(angl < 0)   {angl += pi2; tangl += pi2; anglorig += pi2;}
	    if(Math.abs(tangl - angl) < 0.001)
		angl = tangl;
	    else
		jc = cc;

	    field = field + ((tfield - field) * cf);
	    if(Math.abs(tfield - field) < 0.1)
		field = tfield;
	    else
		jc = cc;
	}

	public boolean click(Coord c) {
	    anglorig = angl;
	    dragorig = c;
	    return(true);
	}

	public void drag(Coord c) {
	    tangl = anglorig + ((float)(c.x - dragorig.x) / 100.0f);
	}

	public void release() {
	}

	private void chfield(float nf) {
	    tfield = nf;
	    tfield = Math.max(Math.min(tfield, sz.x * (float)Math.sqrt(2) / 4f), 50);
	}

	public boolean wheel(Coord c, int amount) {
	    chfield(tfield + amount * 10);
	    return(true);
	}

	public boolean keydown(KeyDownEvent ev) {
	    if(kb_camleft.key().match(ev)) {
		tangl = (float)(Math.PI * 0.5 * (Math.floor((tangl / (Math.PI * 0.5)) - 0.51) + 0.5));
		return(true);
	    } else if(kb_camright.key().match(ev)) {
		tangl = (float)(Math.PI * 0.5 * (Math.floor((tangl / (Math.PI * 0.5)) + 0.51) + 0.5));
		return(true);
	    } else if(kb_camin.key().match(ev)) {
		chfield(tfield - 50);
		return(true);
	    } else if(kb_camout.key().match(ev)) {
		chfield(tfield + 50);
		return(true);
	    } else if(kb_camreset.key().match(ev)) {
		tangl = angl + (float)Utils.cangle(-(float)Math.PI * 0.25f - angl);
		chfield(dfield);
		return(true);
	    }
	    return(false);
	}
    }
    static {camtypes.put("ortho", SOrthoCam.class);}

    @RName("mapview")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Coord sz = UI.scale((Coord)args[0]);
	    Coord2d mc = ((Coord)args[1]).mul(posres);
	    long pgob = -1;
	    if(args.length > 2)
		pgob = Utils.uiv(args[2]);
	    return(new MapView(sz, ui.sess.glob, mc, pgob));
	}
    }
    
    public MapView(Coord sz, Glob glob, Coord2d cc, long plgob) {
	super(sz);
	this.glob = glob;
	this.cc = cc;
	this.plgob = plgob;
	this.glob.moonPlayerGobId = plgob;
	MoonPacketHook.setPlayerGobId(plgob);
	basic.add(this.gobs = new Gobs());
	basic.add(this.terrain = new Terrain());
	this.clickmap = new ClickMap();
	clmaptree.add(clickmap);
	setcanfocus(true);
    }
    
    protected void envdispose() {
	if(smap != null) {
	    smap.dispose(); smap = null;
	    slist.dispose(); slist = null;
	}
	super.envdispose();
    }

    public void dispose() {
	gobs.slot.remove();
	clmaplist.dispose();
	clobjlist.dispose();
	super.dispose();
    }

    public boolean visol(String tag) {
	synchronized(oltags) {
	    return(oltags.containsKey(tag));
	}
    }

    public void enol(String tag) {
	synchronized(oltags) {
	    oltags.put(tag, oltags.getOrDefault(tag, 0) + 1);
	    moonOverlayTagSeq++;
	}
    }

    public void disol(String tag) {
	synchronized(oltags) {
	    Integer rc = oltags.get(tag);
	    if((rc != null) && (--rc > 0))
		oltags.put(tag, rc);
	    else
		oltags.remove(tag);;
	    moonOverlayTagSeq++;
	}
    }

    private final Gobs gobs;
    public enum HideReason {
	XRAY,
	GLOBAL_HITBOX,
	ENTITY_HITBOX,
	CROP_HIDE
    }
    /** Gobs hidden for X-Ray overlay (must not use Map with null values — {@link #showGob} relies on removal success). */
    /** X-Ray-only hidden subset used by overlay rendering. */
    private final Set<Gob> xrayHidden = new HashSet<>();
    /** All active hide owners per gob; slot is removed once while any reason remains. */
    private final Map<Gob, EnumSet<HideReason>> hiddenReasons = new HashMap<>();

    private EnumSet<HideReason> hiddenReasonSet(Gob gob, boolean create) {
	EnumSet<HideReason> rs = hiddenReasons.get(gob);
	if((rs == null) && create) {
	    rs = EnumSet.noneOf(HideReason.class);
	    hiddenReasons.put(gob, rs);
	}
	return(rs);
    }

    private void clearHiddenGob(Gob gob) {
	if(gob == null)
	    return;
	hiddenReasons.remove(gob);
	xrayHidden.remove(gob);
    }

    public boolean isHidden(Gob gob) {
	synchronized(gobs) {
	    EnumSet<HideReason> rs = hiddenReasonSet(gob, false);
	    return((rs != null) && !rs.isEmpty());
	}
    }

    public boolean isHiddenBy(Gob gob, HideReason reason) {
	synchronized(gobs) {
	    EnumSet<HideReason> rs = hiddenReasonSet(gob, false);
	    return((rs != null) && rs.contains(reason));
	}
    }

    List<Gob> hiddenGobs(HideReason reason) {
	synchronized(gobs) {
	    if(reason == HideReason.XRAY)
		return(new ArrayList<>(xrayHidden));
	    List<Gob> out = new ArrayList<>();
	    for(Map.Entry<Gob, EnumSet<HideReason>> ent : hiddenReasons.entrySet()) {
		EnumSet<HideReason> rs = ent.getValue();
		if((rs != null) && rs.contains(reason))
		    out.add(ent.getKey());
	    }
	    return(out);
	}
    }

    void dropHiddenReason(Gob gob, HideReason reason) {
	synchronized(gobs) {
	    EnumSet<HideReason> rs = hiddenReasonSet(gob, false);
	    if((rs == null) || !rs.remove(reason))
		return;
	    if(reason == HideReason.XRAY)
		xrayHidden.remove(gob);
	    if(rs.isEmpty())
		hiddenReasons.remove(gob);
	}
    }

    public void hideGob(Gob gob) {
	hideGob(gob, HideReason.XRAY);
    }

    public void hideGob(Gob gob, HideReason reason) {
	if((gob == null) || (reason == null))
	    return;
	synchronized(gobs) {
	    EnumSet<HideReason> rs = hiddenReasonSet(gob, true);
	    boolean first = rs.isEmpty();
	    if(!rs.add(reason))
		return;
	    if(reason == HideReason.XRAY)
		xrayHidden.add(gob);
	    if(!first)
		return;
	    RenderTree.Slot slot = gobs.current.remove(gob);
	    if(slot != null) {
		try { slot.remove(); } catch(Exception ignored) {}
	    }
	}
    }

    public void showGob(Gob gob) {
	showGob(gob, HideReason.XRAY);
    }

    public void showGob(Gob gob, HideReason reason) {
	if((gob == null) || (reason == null))
	    return;
	synchronized(gobs) {
	    EnumSet<HideReason> rs = hiddenReasonSet(gob, false);
	    if((rs == null) || !rs.remove(reason))
		return;
	    if(reason == HideReason.XRAY)
		xrayHidden.remove(gob);
	    if(!rs.isEmpty())
		return;
	    hiddenReasons.remove(gob);
	    if((gobs.slot != null) && !gobs.current.containsKey(gob))
		gobs.pending.add(gob);
	}
    }

    private class Gobs implements RenderTree.Node, OCache.ChangeCallback {
	final OCache oc = glob.oc;
	final LinkedHashSet<Gob> pending = new LinkedHashSet<>();
	final Map<Gob, RenderTree.Slot> current = new HashMap<>();
	RenderTree.Slot slot;
	private int addsPerSec = 0;
	private int addWindow = 0;
	private double addWindowStarted = Utils.rtime();

	private final class RankedGob {
	    final Gob gob;
	    final double dist;

	    RankedGob(Gob gob, double dist) {
		this.gob = gob;
		this.dist = dist;
	    }
	}

	private Coord2d priorityCenter() {
	    try {
		Gob pl = player();
		if(pl != null)
		    return(pl.rc);
	    } catch(Loading ignored) {
	    } catch(Exception ignored) {
	    }
	    return(cc);
	}

	private void addgobNow(Gob ob) {
	    RenderTree.Slot slot = this.slot;
	    if(slot == null || ob == null)
		return;
	    if(MoonConfig.xrayEnabled && MoonConfig.gfxModXray && MoonXRay.shouldHide(ob))
		hideGob(ob, HideReason.XRAY);
	    if(MoonHitboxMode.wantsHidden(ob, plgob))
		hideGob(ob, HideReason.GLOBAL_HITBOX);
	    if(MoonEntityHitboxViz.wantsHidden(ob, plgob))
		hideGob(ob, HideReason.ENTITY_HITBOX);
	    if(MoonCropMode.wantsHidden(ob))
		hideGob(ob, HideReason.CROP_HIDE);
	    synchronized(this) {
		if(current.containsKey(ob)) {
		    pending.remove(ob);
		    return;
		}
		if(!pending.contains(ob))
		    return;
		if(isHidden(ob)) {
		    pending.remove(ob);
		    return;
		}
	    }
	    synchronized(ob) {
		synchronized(this) {
		    if(!pending.contains(ob))
			return;
		    if(isHidden(ob)) {
			pending.remove(ob);
			return;
		    }
		}
		RenderTree.Slot nslot;
		try {
		    nslot = slot.add(ob.placed);
		} catch(Loading l) {
		    /* Placement can legitimately wait for terrain on world entry.
		     * Keep the gob pending and retry on a later frame instead of
		     * crashing the UI thread during progressive streaming. */
		    return;
		} catch(RenderTree.SlotRemoved e) {
		    return;
		}
		synchronized(this) {
		    if(pending.remove(ob)) {
			current.put(ob, nslot);
			noteAdded();
		    } else
			nslot.remove();
		}
	    }
	}

	private void noteAdded() {
	    double now = Utils.rtime();
	    addWindow++;
	    if((now - addWindowStarted) >= 1.0) {
		addsPerSec = (int)Math.round(addWindow / Math.max(0.25, now - addWindowStarted));
		addWindow = 0;
		addWindowStarted = now;
	    }
	}

	private double distanceTo(Gob gob, Coord2d ref) {
	    try {
		return((ref == null) ? 0.0 : gob.rc.dist(ref));
	    } catch(Exception ignored) {
		return(Double.MAX_VALUE);
	    }
	}

	private List<Gob> prioritize(List<Gob> todo, Coord2d ref, int limit) {
	    if((ref == null) || (todo.size() <= 1))
		return(todo);
	    if((limit <= 0) || (todo.size() <= Math.max(12, limit * 2))) {
		todo.sort(Comparator.comparingDouble(gob -> distanceTo(gob, ref)));
		return(todo);
	    }
	    PriorityQueue<RankedGob> best = new PriorityQueue<>(limit, Comparator.comparingDouble((RankedGob rg) -> rg.dist).reversed());
	    for(Gob gob : todo) {
		double dist = distanceTo(gob, ref);
		if(best.size() < limit) {
		    best.add(new RankedGob(gob, dist));
		} else if(dist < best.peek().dist) {
		    best.poll();
		    best.add(new RankedGob(gob, dist));
		}
	    }
	    ArrayList<RankedGob> ordered = new ArrayList<>(best);
	    ordered.sort(Comparator.comparingDouble(rg -> rg.dist));
	    ArrayList<Gob> ret = new ArrayList<>(ordered.size());
	    for(RankedGob rg : ordered)
		ret.add(rg.gob);
	    return(ret);
	}

	void tick() {
	    RenderTree.Slot slot = this.slot;
	    if(slot == null)
		return;
	    List<Gob> todo;
	    synchronized(this) {
		if(pending.isEmpty())
		    return;
		todo = new ArrayList<>(pending);
	    }
	    Coord2d ref = priorityCenter();
	    todo = prioritize(todo, ref, Math.max(1, moonGobBuildBudget));
	    for(Gob ob : todo) {
		if(!moonTryConsumeBuildBudget(MoonBuildStage.GOBS))
		    break;
		addgobNow(ob);
	    }
	}

	int pendingCount() {
	    synchronized(this) {
		return(pending.size());
	    }
	}

	int readyCount() {
	    synchronized(this) {
		return(current.size());
	    }
	}

	int addRate() {
	    synchronized(this) {
		double now = Utils.rtime();
		if((now - addWindowStarted) >= 1.0) {
		    addsPerSec = 0;
		    addWindow = 0;
		    addWindowStarted = now;
		}
		return(addsPerSec);
	    }
	}

	public void added(RenderTree.Slot slot) {
	    synchronized(this) {
		if(this.slot != null)
		    throw(new RuntimeException());
		this.slot = slot;
		synchronized(oc) {
		    for(Gob ob : oc)
			pending.add(ob);
		    oc.callback(this);
		}
	    }
	}

	public void removed(RenderTree.Slot slot) {
	    synchronized(this) {
		if(this.slot != slot)
		    throw(new RuntimeException());
		this.slot = null;
		oc.uncallback(this);
		pending.clear();
		current.clear();
	    }
	}

	public void added(Gob ob) {
	    synchronized(this) {
		if(!current.containsKey(ob))
		    pending.add(ob);
	    }
	}

	public void removed(Gob ob) {
	    RenderTree.Slot slot;
	    synchronized(this) {
		slot = current.remove(ob);
		pending.remove(ob);
		clearHiddenGob(ob);
	    }
	    if(slot != null) {
		try {
		    slot.remove();
		} catch(RenderTree.SlotRemoved e) {
		    /* Ignore here as there is a harmless remove-race
		     * on disposal. */
		}
	    }
	}

	public Loading loading() {
	    synchronized(this) {
		if(pending.isEmpty())
		    return(null);
	    }
	    return(new Loading("Loading objects..."));
	}
    }

    private class MapRaster extends RenderTree.Node.Track1 {
	final MCache map = glob.map;
	Area area;
	Loading lastload = new Loading("Initializing map...");

	abstract class Grid<T> extends RenderTree.Node.Track1 {
	    final Map<Coord, Pair<T, RenderTree.Slot>> cuts = new HashMap<>();
	    final boolean position;
	    final MoonBuildStage stage;
	    Loading lastload = new Loading("Initializing map...");
	    private Area orderedArea = null;
	    private Area prunedArea = null;
	    private List<Coord> orderedCoords = Collections.emptyList();

	    Grid(MoonBuildStage stage, boolean position) {
		this.stage = stage;
		this.position = position;
	    }

	    Grid(MoonBuildStage stage) {this(stage, true);}

	    abstract T getcut(Coord cc);
	    RenderTree.Node produce(T cut) {return((RenderTree.Node)cut);}

	    private List<Coord> orderedCoords() {
		if(area == null)
		    return(Collections.emptyList());
		if((orderedArea != null) && orderedArea.equals(area))
		    return(orderedCoords);
		Area snap = Area.corn(area.ul, area.br);
		List<Coord> order = new ArrayList<>(snap.area());
		final Coord center = snap.ul.add(snap.sz().div(2));
		for(Coord cc : snap)
		    order.add(cc);
		order.sort((a, b) -> {
		    int da = Math.abs(a.x - center.x) + Math.abs(a.y - center.y);
		    int db = Math.abs(b.x - center.x) + Math.abs(b.y - center.y);
		    if(da != db)
			return(Integer.compare(da, db));
		    if(a.y != b.y)
			return(Integer.compare(a.y, b.y));
		    return(Integer.compare(a.x, b.x));
		});
		orderedArea = snap;
		orderedCoords = order;
		return(order);
	    }

	    int readyCount() {
		return(readyCount(area));
	    }

	    int targetCount() {
		return((area == null) ? 0 : area.area());
	    }

	    int readyCount(Area subset) {
		if(area == null || subset == null)
		    return(0);
		Area ov = area.overlap(subset);
		if(ov == null)
		    return(0);
		int ready = 0;
		for(Coord cc : ov) {
		    if(cuts.containsKey(cc))
			ready++;
		}
		return(ready);
	    }

	    int targetCount(Area subset) {
		if(area == null || subset == null)
		    return(0);
		Area ov = area.overlap(subset);
		return((ov == null) ? 0 : ov.area());
	    }

	    void tick() {
		if(slot == null)
		    return;
		Loading curload = null;
		for(Coord cc : orderedCoords()) {
		    if(moonBuildDeadlineReached(stage))
			break;
		    try {
			T cut = getcut(cc);
			Pair<T, RenderTree.Slot> cur = cuts.get(cc);
			if((cur == null) || (cur.a != cut)) {
			    if(!moonTryConsumeBuildBudget(stage))
				break;
			    Coord2d pc = cc.mul(MCache.cutsz).mul(tilesz);
			    RenderTree.Node draw = produce(cut);
			    Pipe.Op cs = null;
			    if(position)
				cs = Location.xlate(new Coord3f((float)pc.x, -(float)pc.y, 0));
			    cuts.put(cc, new Pair<>(cut, slot.add(draw, cs)));
			    if(cur != null)
				cur.b.remove();
			}
		    } catch(Loading l) {
			l.boostprio(5);
			curload = l;
		    }
		}
		if((area != null) && ((prunedArea == null) || !prunedArea.equals(area))) {
		    for(Iterator<Map.Entry<Coord, Pair<T, RenderTree.Slot>>> i = cuts.entrySet().iterator(); i.hasNext();) {
			Map.Entry<Coord, Pair<T, RenderTree.Slot>> ent = i.next();
			if(!area.contains(ent.getKey())) {
			    ent.getValue().b.remove();
			    i.remove();
			}
		    }
		    prunedArea = Area.corn(area.ul, area.br);
		}
		if(curload != null)
		    this.lastload = curload;
		else if(readyCount() < targetCount())
		    this.lastload = new Loading("Streaming map...");
		else
		    this.lastload = null;
	    }

	    public void removed(RenderTree.Slot slot) {
		super.removed(slot);
		cuts.clear();
		orderedArea = null;
		prunedArea = null;
		orderedCoords = Collections.emptyList();
	    }
	}

	void tick() {
	    /* XXX: Should be taken out of the main rendering
	     * loop. Probably not a big deal, but still. */
	    try {
		Coord cc = new Coord2d(getcc()).floor(tilesz).div(MCache.cutsz);
		int cview = currentview();
		area = new Area(cc.sub(cview, cview), cc.add(cview, cview).add(1, 1));
		lastload = null;
	    } catch(Loading l) {
		l.boostprio(5);
		lastload = l;
	    }
	}

	public Loading loading() {
	    if(this.lastload != null)
		return(this.lastload);
	    return(null);
	}
    }

    public final Terrain terrain;
    public class Terrain extends MapRaster {
	final Grid main = new Grid<MapMesh>(MoonBuildStage.TERRAIN_CRITICAL) {
		MapMesh getcut(Coord cc) {
		    return(map.getcut(cc));
		}
	    };
	final Grid flavobjs = new Grid<RenderTree.Node>(MoonBuildStage.TERRAIN_SECONDARY, false) {
		RenderTree.Node getcut(Coord cc) {
		    return(map.getfo(cc));
		}
	    };

	private Terrain() {
	}

	void tick() {
	    super.tick();
	    if(area != null)
		main.tick();
	}

	void tickSecondary() {
	    if(area != null) {
		flavobjs.tick();
	    }
	}

	public void added(RenderTree.Slot slot) {
	    slot.add(main);
	    slot.add(flavobjs);
	    super.added(slot);
	}

	public Loading loading() {
	    Loading ret = super.loading();
	    if(ret != null)
		return(ret);
	    if((ret = main.lastload) != null)
		return(ret);
	    if((ret = flavobjs.lastload) != null)
		return(ret);
	    return(null);
	}
    }

    public class Overlay extends MapRaster {
	final OverlayInfo id;
	int rc = 0;
	boolean used;

	final Grid base = new Grid<RenderTree.Node>(MoonBuildStage.OPTIONAL) {
		RenderTree.Node getcut(Coord cc) {
		    return(map.getolcut(id, cc));
		}
	    };
	final Grid outl = new Grid<RenderTree.Node>(MoonBuildStage.OPTIONAL) {
		RenderTree.Node getcut(Coord cc) {
		    return(map.getololcut(id, cc));
		}
	    };

	private Overlay(OverlayInfo id) {
	    this.id = id;
	}

	void tick() {
	    super.tick();
	    if(area != null) {
		base.tick();
		outl.tick();
	    }
	}

	public void added(RenderTree.Slot slot) {
	    slot.add(base, id.mat());
	    Material omat = id.omat();
	    if(omat != null)
		slot.add(outl, omat);
	    super.added(slot);
	}

	public Loading loading() {
	    Loading ret = super.loading();
	    if(ret != null)
		return(ret);
	    if((ret = base.lastload) != null)
		return(ret);
	    if((ret = outl.lastload) != null)
		return(ret);
	    return(null);
	}

	public void remove() {
	    slot.remove();
	}
    }

    private final Map<String, Integer> oltags = new HashMap<>();
    private final Map<OverlayInfo, Overlay> ols = new HashMap<>();
    {oltags.put("show", 1);}
    private void olsync() {
	try {
	    for(Overlay ol : ols.values())
		ol.used = false;
	    if(terrain.area != null) {
		for(OverlayInfo id : glob.map.getols(terrain.area.mul(MCache.cutsz))) {
		    boolean vis = false;
		    synchronized(oltags) {
			for(String tag : id.tags()) {
			    if(oltags.containsKey(tag)) {
				vis = true;
				break;
			    }
			}
		    }
		    if(vis) {
			Overlay ol = ols.get(id);
			if(ol == null) {
			    try {
				basic.add(ol = new Overlay(id));
				ols.put(id, ol);
			    } catch(Loading l) {
				l.boostprio(2);
				continue;
			    }
			}
			ol.used = true;
		    }
		}
	    }
	    for(Iterator<Overlay> i = ols.values().iterator(); i.hasNext();) {
		Overlay ol = i.next();
		if(!ol.used) {
		    ol.remove();
		    i.remove();
		}
	    }
	} catch(Loading l) {
	    l.boostprio(2);
	} catch(Exception e) {
	    new Warning(e, "MapView oltick").issue();
	}
    }

    private void oltick() {
	for(Overlay ol : ols.values())
	    ol.tick();
    }

    private static final Material gridmat = new Material(new BaseColor(255, 255, 255, 48), States.maskdepth, new MapMesh.OLOrder(null),
							 Location.xlate(new Coord3f(0, 0, 0.5f))   /* Apparently, there is no depth bias for lines. :P */
							 );
    private static final float MOON_GRID_LINE_WIDE = 5f;

    private static Pipe.Op moonGridPipe(boolean thick) {
	if(thick)
	    return(Pipe.Op.compose(gridmat, new States.LineWidth(MOON_GRID_LINE_WIDE)));
	return(gridmat);
    }

    private class GridLines extends MapRaster {
	final Grid grid = new Grid<RenderTree.Node>(MoonBuildStage.OPTIONAL) {
		RenderTree.Node getcut(Coord cc) {
		    return(map.getcut(cc).grid());
		}
	    };

	private RenderTree.Slot gslot;
	private boolean thickMode;

	GridLines(boolean thick) {
	    this.thickMode = thick;
	}

	void setThick(boolean thick) {
	    if(thickMode == thick)
		return;
	    thickMode = thick;
	    if(gslot != null)
		gslot.ostate(moonGridPipe(thick));
	}

	void tick() {
	    super.tick();
	    if(area != null)
		grid.tick();
	}

	public void added(RenderTree.Slot slot) {
	    this.gslot = slot;
	    slot.ostate(moonGridPipe(thickMode));
	    slot.add(grid);
	    super.added(slot);
	}

	public void remove() {
	    gslot = null;
	    slot.remove();
	}
    }

    GridLines gridlines = null;

    private void moonApplyMapGridState() {
	int m = MoonConfig.mapGridMode;
	if(m == 0) {
	    if(gridlines != null) {
		gridlines.remove();
		gridlines = null;
	    }
	    return;
	}
	if(gridlines == null)
	    basic.add(gridlines = new GridLines(m == 2));
	else
	    gridlines.setThick(m == 2);
    }

    static class MapClick extends Clickable {
	final MapMesh cut;

	MapClick(MapMesh cut) {
	    this.cut = cut;
	}

	public String toString() {
	    return(String.format("#<mapclick %s>", cut));
	}
    }

    private final ClickMap clickmap;
    private class ClickMap extends MapRaster {
	final Grid grid = new Grid<MapMesh>(MoonBuildStage.CLICKMAP) {
		MapMesh getcut(Coord cc) {
		    return(map.getcut(cc));
		}
		RenderTree.Node produce(MapMesh cut) {
		    return(new MapClick(cut).apply(cut.flat));
		}
	    };

	void tick() {
	    super.tick();
	    if(area != null) {
		grid.tick();
	    }
	}

	public void added(RenderTree.Slot slot) {
	    slot.add(grid);
	    super.added(slot);
	}

	public Loading loading() {
	    Loading ret = super.loading();
	    if(ret != null)
		return(ret);
	    if((ret = grid.lastload) != null)
		return(ret);
	    return(null);
	}
    }

    private Area moonCriticalArea() {
	try {
	    Coord cc = new Coord2d(getcc()).floor(tilesz).div(MCache.cutsz);
	    return(Area.sized(cc.sub(1, 1), Coord.of(3, 3)));
	} catch(Loading l) {
	    return(null);
	}
    }

    private void moonUpdateLoadProgress() {
	Area crit = moonCriticalArea();
	int critReady = terrain.main.readyCount(crit) + clickmap.grid.readyCount(crit);
	int critTotal = terrain.main.targetCount(crit) + clickmap.grid.targetCount(crit);
	int sceneReady = terrain.main.readyCount() + terrain.flavobjs.readyCount() + clickmap.grid.readyCount();
	int sceneTotal = terrain.main.targetCount() + terrain.flavobjs.targetCount() + clickmap.grid.targetCount();
	int optionalReady = 0;
	int optionalTotal = 0;
	if(gridlines != null) {
	    optionalReady += gridlines.grid.readyCount();
	    optionalTotal += gridlines.grid.targetCount();
	}
	for(Overlay ol : ols.values()) {
	    optionalReady += ol.base.readyCount() + ol.outl.readyCount();
	    optionalTotal += ol.base.targetCount() + ol.outl.targetCount();
	}
	boolean playerReady = false;
	try {
	    Gob pl = player();
	    playerReady = (pl != null) && (pl.getc() != null);
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
	int gobReady = gobs.readyCount();
	int gobPending = gobs.pendingCount();
	boolean revealReady = playerReady && (critTotal <= 0 || critReady >= critTotal);
	boolean secondaryDone = revealReady && (sceneReady >= sceneTotal) && (optionalReady >= optionalTotal) && (gobPending <= 0);
	boolean loading = !secondaryDone;
	moonLoadProgress = new MoonLoadProgress(revealReady, secondaryDone, loading,
	    critReady, critTotal, sceneReady, sceneTotal, gobReady, gobPending, optionalReady, optionalTotal);
	MoonPerfOverlay.updateSceneStats(gobReady, gobPending, gobs.addRate(),
	    Math.max(0, sceneTotal - sceneReady), Math.max(0, optionalTotal - optionalReady));
    }

    public String camstats() {
	String cc;
	try {
	    Coord3f c = getcc();
	    cc = String.format("(%.1f %.1f %.1f)", c.x / tilesz.x, c.y / tilesz.y, c.z / tilesz.x);
	} catch(Loading l) {
	    cc = "<nil>";
	}
	return(String.format("C: %s, Cam: %s", cc, camera.stats()));
    }

    public String stats() {
	String ret = String.format("Tree %s", tree.stats());
	if(back != null)
	    ret = String.format("%s, Inst %s, Draw %s", ret, instancer.stats(), back.stats());
	MoonLoadProgress load = moonLoadProgress;
	if(load.loading)
	    ret = String.format("%s, Stream %d/%d, Gob %d+%d", ret,
		load.sceneReady + load.optionalReady, Math.max(1, load.sceneTotal + load.optionalTotal),
		load.gobReady, load.gobPending);
	return(ret);
    }

    private Coord3f smapcc = null;
    private ShadowMap.ShadowList slist = null;
    private ShadowMap smap = null;
    private double lsmch = 0;
    private void updsmap(DirLight light) {
	boolean usesdw = ui.gprefs.lshadow.val;
	int sdwres = ui.gprefs.shadowres.val;
	sdwres = (sdwres < 0) ? (2048 >> -sdwres) : (2048 << sdwres);
	if(usesdw) {
	    Coord3f dir, cc;
	    try {
		dir = new Coord3f(-light.dir[0], -light.dir[1], -light.dir[2]);
		cc = getcc().invy();
	    } catch(Loading l) {
		return;
	    }
	    if(smap == null) {
		if(instancer == null)
		    return;
		slist = new ShadowMap.ShadowList(instancer);
		smap = new ShadowMap(new Coord(sdwres, sdwres), 750, 5000, 1);
	    } else if(smap.lbuf.w != sdwres) {
		smap.dispose();
		smap = new ShadowMap(new Coord(sdwres, sdwres), 750, 5000, 1);
		smapcc = null;
		basic(ShadowMap.class, null);
	    }
	    smap = smap.light(light);
	    boolean ch = false;
	    double now = Utils.rtime();
	    if((smapcc == null) || (smapcc.dist(cc) > 50)) {
		smapcc = cc;
		ch = true;
	    } else {
		if(now - lsmch > 0.1)
		    ch = true;
	    }
	    if(ch || !smap.haspos()) {
		smap = smap.setpos(smapcc.add(dir.neg().mul(1000f)), dir);
		lsmch = now;
	    }
	    basic(ShadowMap.class, smap);
	} else {
	    if(smap != null) {
		instancer.remove(slist);
		smap.dispose(); smap = null;
		slist.dispose(); slist = null;
		basic(ShadowMap.class, null);
	    }
	    smapcc = null;
	}
    }

    private void drawsmap(Render out) {
	if(smap != null)
	    smap.update(out, slist);
    }

    public DirLight amblight = null;
    private RenderTree.Slot s_amblight = null;
    private void amblight() {
	synchronized(glob) {
	    boolean forceDay = MoonConfig.alwaysDaylight;
	    Color amb, dif, spc;
	    double elev, ang;
	    if(forceDay) {
		amb = MoonConfig.DAYLIGHT_FALLBACK_AMB;
		dif = MoonConfig.DAYLIGHT_FALLBACK_DIF;
		spc = MoonConfig.DAYLIGHT_FALLBACK_SPC;
		elev = MoonConfig.DAYLIGHT_FALLBACK_ELEV;
		ang = MoonConfig.DAYLIGHT_FALLBACK_ANG;
		double nb = MoonConfig.nightVisionBlend;
		if(nb > 0) {
		    amb = Utils.blendcol(amb, Color.WHITE, nb);
		    dif = Utils.blendcol(dif, Color.WHITE, nb);
		    spc = Utils.blendcol(spc, Color.WHITE, nb);
		}
	    } else {
		amb = glob.blightamb != null ? glob.blightamb : glob.lightamb;
		dif = glob.blightdif != null ? glob.blightdif : glob.lightdif;
		spc = glob.blightspc != null ? glob.blightspc : glob.lightspc;
		elev = glob.lightelev;
		ang = glob.lightang;
	    }
	    if(amb != null) {
		amblight = new DirLight(amb, dif, spc, Coord3f.o.sadd((float)elev, (float)ang, 1f));
		amblight.prio(100);
	    } else {
		amblight = null;
	    }
	}
	if(s_amblight != null) {
	    s_amblight.remove();
	    s_amblight = null;
	}
	if(amblight != null)
	    s_amblight = basic.add(amblight);
    }

    public static class LightCompiler {
	public final GSettings gprefs;
	private final Lighting.LightGrid zgrid;
	private final int maxlights;

	public LightCompiler(GSettings gprefs) {
	    this.gprefs = gprefs;
	    if(gprefs == null) {
		zgrid = null;
		maxlights = 0;
	    } else {
		maxlights = gprefs.maxlights.val;
		if(gprefs.lightmode.val == GSettings.LightMode.ZONED) {
		    zgrid = new Lighting.LightGrid(64, 64, 64);
		    if(maxlights != 0)
			zgrid.maxlights = maxlights;
		} else {
		    zgrid = null;
		}
	    }
	}

	public boolean valid(GSettings prefs) {
	    return((prefs == gprefs) ||
		   (((prefs == null) == (gprefs == null)) &&
		    (prefs.lightmode.val == gprefs.lightmode.val) &&
		    (prefs.maxlights.val == gprefs.maxlights.val)));
	}

	public Pipe.Op compile(Object[][] params, Projection proj) {
	    if(zgrid == null) {
		Lighting.SimpleLights ret = new Lighting.SimpleLights(params);
		if(maxlights != 0)
		    ret.maxlights = maxlights;
		return(ret);
	    } else {
		return(zgrid.compile(params, proj));
	    }
	}
    }

    private LightCompiler lighting;
    protected void lights() {
	GSettings gprefs = basic.state().get(GSettings.slot);
	if((lighting == null) || !lighting.valid(gprefs)) {
	    basic(Light.class, null);
	    lighting = new LightCompiler(gprefs);
	}
	Projection proj = (camera == null) ? new Projection(Matrix4f.id) : camera.proj;
	basic(Light.class, Pipe.Op.compose(lights, lighting.compile(lights.params(), proj)));
    }

    public static final Uniform amblight_idx = new Uniform(Type.INT, p -> {
	    DirLight light = ((MapView)((WidgetContext)p.get(RenderContext.slot)).widget()).amblight;
	    Light.LightList lights = p.get(Light.lights);
	    int idx = -1;
	    if(light != null)
		idx = lights.index(light);
	    return(idx);
	}, RenderContext.slot, Light.lights);

    private final Map<RenderTree.Node, RenderTree.Slot> rweather = new HashMap<>();
    private void updweather() {
	Glob.Weather[] wls = glob.weather().toArray(new Glob.Weather[0]);
	Pipe.Op[] wst = new Pipe.Op[wls.length];
	for(int i = 0; i < wls.length; i++)
	    wst[i] = wls[i].state();
	try {
	    basic(Glob.Weather.class, Pipe.Op.compose(wst));
	} catch(Loading l) {
	}
	Collection<RenderTree.Node> old =new ArrayList<>(rweather.keySet());
	for(Glob.Weather w : wls) {
	    if(w instanceof RenderTree.Node) {
		RenderTree.Node n = (RenderTree.Node)w;
		old.remove(n);
		if(rweather.get(n) == null) {
		    try {
			rweather.put(n, basic.add(n));
		    } catch(Loading l) {
		    }
		}
	    }
	}
	for(RenderTree.Node rem : old)
	    rweather.remove(rem).remove();
    }

    public RenderTree.Slot drawadd(RenderTree.Node extra) {
	return(basic.add(extra));
    }

    public Gob player() {
	return((plgob < 0) ? null : glob.oc.getgob(plgob));
    }

    private Coord3f safescreenxf(Coord3f wc) {
	try {
	    Coord3f sc = screenxf(wc);
	    if(sc == null)
		return(null);
	    if(!Float.isFinite(sc.x) || !Float.isFinite(sc.y) || !Float.isFinite(sc.z))
		return(null);
	    return(sc);
	} catch(Exception e) {
	    return(null);
	}
    }
    
    public Coord3f getcc() {
	Gob pl = player();
	Coord3f ret;
	if(pl != null)
	    ret = pl.getc();
	else
	    ret = glob.map.getzp(cc);
	if(MoonConfig.flatTerrain)
	    ret = Coord3f.of(ret.x, ret.y, 0);
	return(ret);
    }

    public static class Clicklist implements RenderList<Rendered>, RenderList.Adapter {
	public static final Pipe.Op clickbasic = Pipe.Op.compose(new States.Depthtest(States.Depthtest.Test.LE),
								 new States.Facecull(),
								 Homo3D.state);
	private static final int MAXID = 0xffffff;
	private final RenderList.Adapter master;
	private final boolean doinst;
	private final ProxyPipe basic = new ProxyPipe();
	private final Map<Slot<? extends Rendered>, Clickslot> slots = new HashMap<>();
	private final Map<Integer, Clickslot> idmap = new HashMap<>();
	private DefPipe curbasic = null;
	private RenderList<Rendered> back;
	private DrawList draw;
	private InstanceList instancer;
	private int nextid = 1;

	public class Clickslot implements Slot<Rendered> {
	    public final Slot<? extends Rendered> bk;
	    public final int id;
	    final Pipe idp;
	    private GroupPipe state;

	    public Clickslot(Slot<? extends Rendered> bk, int id) {
		this.bk = bk;
		this.id = id;
		this.idp = new SinglePipe<>(FragID.id, new FragID.ID(id));
	    }

	    public Rendered obj() {
		return(bk.obj());
	    }

	    public GroupPipe state() {
		if(state == null)
		    state = new IDState(bk.state());
		return(state);
	    }

	    private class IDState implements GroupPipe {
		static final int idx_bas = 0, idx_idp = 1, idx_back = 2;
		final GroupPipe back;

		IDState(GroupPipe back) {
		    this.back = back;
		}

		public Pipe group(int idx) {
		    switch(idx) {
		    case idx_bas: return(basic);
		    case idx_idp: return(idp);
		    default: return(back.group(idx - idx_back));
		    }
		}

		public int gstate(int id) {
		    if(id == FragID.id.id)
			return(idx_idp);
		    if(State.Slot.byid(id).type == State.Slot.Type.GEOM) {
			int ret = back.gstate(id);
			if(ret >= 0)
			    return(ret + idx_back);
		    }
		    if((id < curbasic.mask.length) && curbasic.mask[id])
			return(idx_bas);
		    return(-1);
		}

		public int nstates() {
		    return(Math.max(Math.max(back.nstates(), curbasic.mask.length), FragID.id.id + 1));
		}
	    }
	}

	public Clicklist(RenderList.Adapter master, boolean doinst) {
	    this.master = master;
	    this.doinst = doinst;
	    asyncadd(this.master, Rendered.class);
	}

	public void add(Slot<? extends Rendered> slot) {
	    if(slot.state().get(Clickable.slot) == null)
		return;
	    int id;
	    while(idmap.get(id = nextid) != null) {
		if(++nextid > MAXID)
		    nextid = 1;
	    }
	    Clickslot ns = new Clickslot(slot, id);
	    if(back != null)
		back.add(ns);
	    if(((slots.put(slot, ns)) != null) || (idmap.put(id, ns) != null))
		throw(new AssertionError());
	}

	public void remove(Slot<? extends Rendered> slot) {
	    Clickslot cs = slots.remove(slot);
	    if(cs != null) {
		if(idmap.remove(cs.id) != cs)
		    throw(new AssertionError());
		if(back != null)
		    back.remove(cs);
	    }
	}

	public void update(Slot<? extends Rendered> slot) {
	    if(back != null) {
		Clickslot cs = slots.get(slot);
		if(cs != null) {
		    cs.state = null;
		    back.update(cs);
		}
	    }
	}

	public void update(Pipe group, int[] statemask) {
	    if(back != null)
		back.update(group, statemask);
	}

	public Locked lock() {
	    return(master.lock());
	}

	public Iterable<? extends Slot<?>> slots() {
	    return(slots.values());
	}

	/* Shouldn't have to care. */
	public <R> void add(RenderList<R> list, Class<? extends R> type) {}
	public void remove(RenderList<?> list) {}

	public void basic(Pipe.Op st) {
	    try(Locked lk = lock()) {
		DefPipe buf = new DefPipe();
		buf.prep(st);
		if(curbasic != null) {
		    if(curbasic.maskdiff(buf).length != 0)
			throw(new RuntimeException("changing clickbasic definition mask is not supported"));
		}
		int[] mask = basic.dupdate(buf);
		curbasic = buf;
		if(back != null)
		    back.update(basic, mask);
	    }
	}

	public Coord sz() {
	    return(basic.get(States.viewport).area.sz());
	}

	public void draw(Render out) {
	    if((draw == null) || !out.env().compatible(draw)) {
		if(draw != null)
		    dispose();
		draw = out.env().drawlist().desc("click-list: " + this);
		if(doinst) {
		    instancer = new InstanceList(this);
		    instancer.add(draw, Rendered.class);
		    instancer.asyncadd(this, Rendered.class);
		    back = instancer;
		} else {
		    draw.asyncadd(this, Rendered.class);
		    back = draw;
		}
	    }
	    try(Locked lk = lock()) {
		if(instancer != null)
		    instancer.commit(out);
		draw.draw(out);
	    }
	}

	public void get(Render out, Coord c, Consumer<ClickData> cb) {
	    out.pget(basic, FragID.fragid, Area.sized(Coord.of(c.x, sz().y - c.y), new Coord(1, 1)), new VectorFormat(1, NumberFormat.SINT32), data -> {
		    int id = data.getInt(0);
		    if(id == 0) {
			cb.accept(null);
			return;
		    }
		    Clickslot cs = idmap.get(id);
		    if(cs == null) {
			cb.accept(null);
			return;
		    }
		    cb.accept(new ClickData(cs.bk.state().get(Clickable.slot), (RenderTree.Slot)cs.bk.cast(RenderTree.Node.class)));
		});
	}

	public void fuzzyget(Render out, Coord c, int rad, Consumer<ClickData> cb) {
	    Coord gc = Coord.of(c.x, sz().y - 1 - c.y);
	    Area area = new Area(gc.sub(rad, rad), gc.add(rad + 1, rad + 1)).overlap(Area.sized(Coord.z, this.sz()));
	    if(area == null) {
		cb.accept(null);
		return;
	    }
	    out.pget(basic, FragID.fragid, area, new VectorFormat(1, NumberFormat.SINT32), data -> {
		    Clickslot cs;
		    {
			int id = data.getInt(area.ridx(gc) * 4);
			if((id != 0) && ((cs = idmap.get(id)) != null)) {
			    cb.accept(new ClickData(cs.bk.state().get(Clickable.slot), (RenderTree.Slot)cs.bk.cast(RenderTree.Node.class)));
			    return;
			}
		    }
		    int maxr = Integer.MAX_VALUE;
		    Map<Clickslot, Integer> score = new HashMap<>();
		    for(Coord fc : area) {
			int id = data.getInt(area.ridx(fc) * 4);
			if((id == 0) || ((cs = idmap.get(id)) == null))
			    continue;
			int r = (int)Math.round(fc.dist(gc) * 10);
			if(r < maxr) {
			    score.clear();
			    maxr = r;
			} else if(r > maxr) {
			    continue;
			}
			score.put(cs, score.getOrDefault(cs, 0) + 1);
		    }
		    int maxscore = 0;
		    cs = null;
		    for(Map.Entry<Clickslot, Integer> ent : score.entrySet()) {
			if((cs == null) || (ent.getValue() > maxscore)) {
			    maxscore = ent.getValue();
			    cs = ent.getKey();
			}
		    }
		    if(cs == null) {
			cb.accept(null);
			return;
		    }
		    cb.accept(new ClickData(cs.bk.state().get(Clickable.slot), (RenderTree.Slot)cs.bk.cast(RenderTree.Node.class)));
		});
	}

	public void dispose() {
	    if(instancer != null) {
		instancer.dispose();
		instancer = null;
	    }
	    if(draw != null) {
		draw.dispose();
		draw = null;
	    }
	    back = null;
	}

	public String stats() {
	    if(back == null)
		return("");
	    return(String.format("Tree %s, Inst %s, Draw %s, Map %d", master.stats(), (instancer == null) ? null : instancer.stats(), draw.stats(), idmap.size()));
	}
    }

    private final RenderTree clmaptree = new RenderTree();
    private final Clicklist clmaplist = new Clicklist(clmaptree, false);
    private final Clicklist clobjlist = new Clicklist(tree, true);
    private FragID<Texture.Image<Texture2D>> clickid;
    private ClickLocation<Texture.Image<Texture2D>> clickloc;
    private DepthBuffer<Texture.Image<Texture2D>> clickdepth;
    private Pipe.Op curclickbasic;
    private Pipe.Op clickbasic(Coord sz) {
	if((curclickbasic == null) || !clickid.image.tex.sz().equals(sz)) {
	    if(clickid != null) {
		clickid.image.tex.dispose();
		clickloc.image.tex.dispose();
		clickdepth.image.tex.dispose();
	    }
	    clickid = new FragID<>(new Texture2D(sz, DataBuffer.Usage.STATIC, new VectorFormat(1, NumberFormat.SINT32), null).image(0));
	    clickloc = new ClickLocation<>(new Texture2D(sz, DataBuffer.Usage.STATIC, new VectorFormat(2, NumberFormat.UNORM16), null).image(0));
	    clickdepth = new DepthBuffer<>(new Texture2D(sz, DataBuffer.Usage.STATIC, Texture.DEPTH, new VectorFormat(1, NumberFormat.FLOAT32), null).image(0));
	    curclickbasic = Pipe.Op.compose(Clicklist.clickbasic, clickid, clickdepth, new States.Viewport(Area.sized(Coord.z, sz)));
	}
	/* XXX: FrameInfo shouldn't be treated specially. Is a new
	 * Slot.Type in order, perhaps? */
	return(Pipe.Op.compose(curclickbasic, camera, conf.state().get(FrameInfo.slot)));
    }

    private void checkmapclick(Render out, Pipe.Op basic, Coord c, Consumer<Coord2d> cb) {
	new Object() {
	    MapMesh cut;
	    Coord2d pos;

	    {
		clmaplist.basic(Pipe.Op.compose(basic, clickloc));
		clmaplist.draw(out);
		if(clickdb) {
		    GOut.debugimage(out, clmaplist.basic, FragID.fragid, Area.sized(Coord.z, clmaplist.sz()), new VectorFormat(1, NumberFormat.SINT32),
				    img -> Debug.dumpimage(img, Debug.somedir("click1.png")));
		    GOut.debugimage(out, clmaplist.basic, ClickLocation.fragloc, Area.sized(Coord.z, clmaplist.sz()), new VectorFormat(3, NumberFormat.UNORM16),
				    img -> Debug.dumpimage(img, Debug.somedir("click2.png")));
		}
		clmaplist.get(out, c, cd -> {
			if(clickdb)
			    Debug.log.printf("map-id: %s\n", cd);
			if(cd != null)
			    this.cut = ((MapClick)cd.ci).cut;
			ckdone(1);
		    });
		out.pget(clmaplist.basic, ClickLocation.fragloc, Area.sized(Coord.of(c.x, clmaplist.sz().y - c.y), new Coord(1, 1)), new VectorFormat(2, NumberFormat.FLOAT32), data -> {
			pos = new Coord2d(data.getFloat(0), data.getFloat(4));
			if(clickdb)
			    Debug.log.printf("map-pos: %s\n", pos);
			ckdone(2);
		    });
	    }

	    int dfl = 0;
	    void ckdone(int fl) {
		synchronized(this) {
		    if((dfl |= fl) == 3) {
			if(cut == null)
			    cb.accept(null);
			else
			    cb.accept(new Coord2d(cut.ul).add(pos.mul(new Coord2d(cut.sz))).mul(tilesz));
		    }
		}
	    }
	};
    }
    
    private static int gobclfuzz = 3;
    private void checkgobclick(Render out, Pipe.Op basic, Coord c, Consumer<ClickData> cb) {
	clobjlist.basic(basic);
	clobjlist.draw(out);
	if(clickdb) {
	    GOut.debugimage(out, clobjlist.basic, FragID.fragid, Area.sized(Coord.z, clobjlist.sz()), new VectorFormat(1, NumberFormat.SINT32),
			  img -> Debug.dumpimage(img, Debug.somedir("click3.png")));
	    Consumer<ClickData> ocb = cb;
	    cb = cl -> {
		Debug.log.printf("obj-id: %s\n", cl);
		ocb.accept(cl);
	    };
	}
	clobjlist.fuzzyget(out, c, gobclfuzz, cb);
    }
    
    public void delay(Delayed d) {
	synchronized(delayed) {
	    delayed.add(d);
	}
    }

    public void delay2(Delayed d) {
	synchronized(delayed2) {
	    delayed2.add(d);
	}
    }

    protected void undelay(Collection<Delayed> list, GOut g) {
	synchronized(list) {
	    for(Delayed d : list)
		d.run(g);
	    list.clear();
	}
    }

    static class PolText {
	Text text; double tm;
	PolText(Text text, double tm) {this.text = text; this.tm = tm;}
    }

    private static final Text.Furnace polownertf = new PUtils.BlurFurn(new Text.Foundry(Text.serif, 30).aa(true), 3, 1, Color.BLACK);
    private final Map<Integer, PolText> polowners = new HashMap<Integer, PolText>();

    public void setpoltext(int id, String text) {
	synchronized(polowners) {
	    polowners.put(id, new PolText(polownertf.render(text), Utils.rtime()));
	}
    }

    private void poldraw(GOut g) {
	if(polowners.isEmpty())
	    return;
	double now = Utils.rtime();
	synchronized(polowners) {
	    int y = (sz.y / 3) - (polowners.values().stream().map(t -> t.text.sz().y).reduce(0, (a, b) -> a + b + 10) / 2);
	    for(Iterator<PolText> i = polowners.values().iterator(); i.hasNext();) {
		PolText t = i.next();
		double poldt = now - t.tm;
		if(poldt < 6.0) {
		    int a;
		    if(poldt < 1.0)
			a = (int)(255 * poldt);
		    else if(poldt < 4.0)
			a = 255;
		    else
			a = (int)((255 * (2.0 - (poldt - 4.0))) / 2.0);
		    g.chcolor(255, 255, 255, a);
		    g.aimage(t.text.tex(), new Coord((sz.x - t.text.sz().x) / 2, y), 0.0, 0.0);
		    y += t.text.sz().y + 10;
		    g.chcolor();
		} else {
		    i.remove();
		}
	    }
	}
    }
    
    private void drawarrow(GOut g, double a) {
	Coord hsz = sz.div(2);
	double ca = -Coord.z.angle(hsz);
	Coord ac;
	if((a > ca) && (a < -ca)) {
	    ac = new Coord(sz.x, hsz.y - (int)(Math.tan(a) * hsz.x));
	} else if((a > -ca) && (a < Math.PI + ca)) {
	    ac = new Coord(hsz.x - (int)(Math.tan(a - Math.PI / 2) * hsz.y), 0);
	} else if((a > -Math.PI - ca) && (a < ca)) {
	    ac = new Coord(hsz.x + (int)(Math.tan(a + Math.PI / 2) * hsz.y), sz.y);
	} else {
	    ac = new Coord(0, hsz.y + (int)(Math.tan(a) * hsz.x));
	}
	Coord bc = ac.add(Coord.sc(a, -10));
	g.line(bc, bc.add(Coord.sc(a, -40)), 2);
	g.line(bc, bc.add(Coord.sc(a + Math.PI / 4, -10)), 2);
	g.line(bc, bc.add(Coord.sc(a - Math.PI / 4, -10)), 2);
    }

    public HomoCoord4f clipxf(Coord3f mc, boolean doclip) {
	HomoCoord4f ret = Homo3D.obj2clip(new Coord3f(mc.x, -mc.y, mc.z), basic.state());
	if(doclip && ret.clipped(HomoCoord4f.AX | HomoCoord4f.AY | HomoCoord4f.PZ)) {
	    Projection s_prj = basic.state().get(Homo3D.prj);
	    Matrix4f prj = (s_prj == null) ? Matrix4f.id : s_prj.fin(Matrix4f.id);
	    ret = HomoCoord4f.lineclip(HomoCoord4f.fromclip(prj, Coord3f.o), ret, HomoCoord4f.AX | HomoCoord4f.AY | HomoCoord4f.PZ);
	}
	return(ret);
    }

    public Coord3f screenxf(Coord3f mc) {
	return(clipxf(mc, false).toview(Area.sized(this.sz)));
    }

    public Coord3f screenxf(Coord2d mc) {
	Coord3f cc;
	try {
	    cc = getcc();
	} catch(Loading e) {
	    return(null);
	}
	return(screenxf(new Coord3f((float)mc.x, (float)mc.y, cc.z)));
    }

    public double screenangle(Coord2d mc, boolean clip) {
	Coord3f cc;
	try {
	    cc = getcc();
	} catch(Loading e) {
	    return(Double.NaN);
	}
	Coord3f mloc = new Coord3f((float)mc.x, -(float)mc.y, cc.z);
	float[] sloc = camera.proj.toclip(camera.view.fin(Matrix4f.id).mul4(mloc));
	if(clip) {
	    float w = sloc[3];
	    if((sloc[0] > -w) && (sloc[0] < w) && (sloc[1] > -w) && (sloc[1] < w))
		return(Double.NaN);
	}
	float a = ((float)sz.y) / ((float)sz.x);
	return(Math.atan2(sloc[1] * a, sloc[0]));
    }

    private void partydraw(GOut g) {
	for(Party.Member m : ui.sess.glob.party.memb.values()) {
	    if(m.gobid == this.plgob)
		continue;
	    Coord2d mc = m.getc();
	    if(mc == null)
		continue;
	    double a = screenangle(mc, true);
	    if(Double.isNaN(a))
		continue;
	    g.chcolor(m.col);
	    drawarrow(g, a);
	}
	g.chcolor();
    }

    private static final Color ROUTE_COL = new Color(60, 220, 120, 220);
    private static final Color ROUTE_QUEUE_COL = new Color(80, 200, 255, 200);
    private static final int ROUTE_DOT = 3;

    private void drawMoveTrace(GOut g) {
	Gob pl = player();
	Coord2d target = moveTraceTarget;
	if(pl == null || target == null)
	    return;
	try {
	    drawTracePath(g, pl, target, moveTraceQueue, ROUTE_COL, ROUTE_QUEUE_COL);
	} catch(Loading ignored) {
	} catch(Exception ignored) {}
    }

    private void drawRouteTrace(GOut g) {
	if(!moonPathHighlightEnabled())
	    return;
	Gob pl = player();
	if(pl == null)
	    return;
	Coord2d target;
	ArrayDeque<Coord2d> queue = new ArrayDeque<>();
	synchronized(moonPathLock) {
	    target = moonRouteTraceTarget;
	    queue.addAll(moonRouteTraceQueue);
	}
	if(target == null)
	    return;
	try {
	    drawTracePath(g, pl, target, queue, new Color(120, 220, 255, 230), new Color(80, 200, 255, 180));
	} catch(Loading ignored) {
	} catch(Exception ignored) {}
    }

    private void drawTracePath(GOut g, Gob pl, Coord2d target, Collection<Coord2d> queue, Color headCol, Color queueCol) {
	Coord3f pc = pl.getc();
	if(pc == null)
	    return;
	Coord2d origin = new Coord2d(pc.x, pc.y);
	Coord fromSc = worldToScreen(pc.x, pc.y, pc.z + movetraceheadz);
	Coord toSc = worldToScreen(target);
	if(fromSc == null || toSc == null)
	    return;

	g.chcolor(headCol);
	g.line(fromSc, toSc, 2.5);
	drawDot(g, toSc);

	Coord lastSc = toSc;
	if(queue != null && !queue.isEmpty()) {
	    g.chcolor(queueCol);
	    for(Coord2d qwp : queue) {
		Coord qsc = worldToScreen(qwp);
		if(qsc == null)
		    continue;
		g.line(lastSc, qsc, 2.5);
		drawDot(g, qsc);
		lastSc = qsc;
	    }
	}

	double dist = origin.dist(target);
	Coord2d prev = target;
	if(queue != null) {
	    for(Coord2d qwp : queue) {
		dist += prev.dist(qwp);
		prev = qwp;
	    }
	}
	g.chcolor(Color.WHITE);
	g.atext(String.format("%.0f tiles", dist / MCache.tilesz.x),
		Coord.of((fromSc.x + lastSc.x) / 2, (fromSc.y + lastSc.y) / 2), 0.5, 0.5);
	g.chcolor();
    }

    private Color moonCombatChaseColor(long moverId) {
	if(plgob >= 0 && moverId == plgob)
	    return MoonConfig.colorFromArgb(MoonConfig.combatChaseArgbSelf);
	if(glob.party.memb.containsKey(moverId))
	    return MoonConfig.colorFromArgb(MoonConfig.combatChaseArgbFriend);
	return MoonConfig.colorFromArgb(MoonConfig.combatChaseArgbEnemy);
    }

    private void refreshMoonCombatGuideSegs() {
	double now = Utils.rtime();
	if((now - moonCombatGuideScanAt) < MOON_COMBAT_GUIDE_SCAN_INTERVAL)
	    return;
	moonCombatGuideScanAt = now;
	if(!MoonConfig.combatDrawChaseVectors) {
	    moonCombatGuideSegs = Collections.emptyList();
	    return;
	}
	List<MoonCombatGuideSeg> segs = new ArrayList<>();
	try {
	    synchronized(glob.oc) {
		for(Gob gob : glob.oc) {
		    if(gob == null)
			continue;
		    Homing h = gob.getattr(Homing.class);
		    if(h == null)
			continue;
		    Gob tgt = h.targetGob();
		    if(tgt == null)
			continue;
		    Coord3f a = gob.getc();
		    Coord3f b = tgt.getc();
		    if(a == null || b == null)
			continue;
		    segs.add(new MoonCombatGuideSeg(Coord3f.of(a.x, a.y, a.z), Coord3f.of(b.x, b.y, b.z), gob.id));
		}
	    }
	} catch(Loading ignored) {
	    return;
	} catch(Exception ignored) {
	    return;
	}
	moonCombatGuideSegs = segs;
    }

    private void drawMoonCombatGuides(GOut g) {
	boolean wantChase = MoonConfig.combatDrawChaseVectors;
	if(!wantChase)
	    return;
	try {
	    refreshMoonCombatGuideSegs();
	    for(MoonCombatGuideSeg seg : moonCombatGuideSegs) {
		if(seg == null)
		    continue;
		Coord sa = worldToScreen(seg.from.x, seg.from.y, seg.from.z);
		Coord sb = worldToScreen(seg.to.x, seg.to.y, seg.to.z);
		if(sa == null || sb == null)
		    continue;
		g.chcolor(moonCombatChaseColor(seg.moverId));
		g.line(sa, sb, 2);
	    }
	    g.chcolor();
	} catch(Loading ignored) {
	} catch(Exception ignored) {}
    }

    private void drawMoonNavFlash(GOut g) {
	double now = Utils.rtime();
	Coord2d mc = moonNavFlashAt;
	if(mc == null || now > moonNavFlashUntil)
	    return;
	try {
	    Coord sc = worldToScreen(mc);
	    if(sc == null)
		return;
	    g.chcolor(new Color(80, 200, 255, 150));
	    g.fellipse(sc, UI.scale(new Coord(18, 18)));
	    g.chcolor();
	} catch(Loading ignored) {
	} catch(Exception ignored) {}
    }

    private Coord worldToScreen(double wx, double wy, double wz) {
	Coord3f sc = safescreenxf(Coord3f.of((float)wx, (float)wy, (float)wz));
	if(sc == null) return null;
	return Coord.of(Math.round(sc.x), Math.round(sc.y));
    }
    private Coord worldToScreen(Coord2d wc) {
	try { return worldToScreen(wc.x, wc.y, glob.map.getcz(wc)); }
	catch(Loading e) { return null; }
    }
    private void drawDot(GOut g, Coord sc) {
	int s = UI.scale(ROUTE_DOT);
	g.frect(sc.sub(s, s), Coord.of(s * 2, s * 2));
    }

    private void drawPlayerSpeed(GOut g) {
	Gob pl = player();
	if(pl == null)
	    return;
	try {
	    Moving mv = pl.getattr(Moving.class);
	    double spd = (mv == null) ? 0.0 : pl.getv();
	    if(spd <= 0.01) {
		moveSpeedText = null;
		moveSpeedValue = Double.NaN;
		return;
	    }
	    if((moveSpeedText == null) || !Double.isFinite(moveSpeedValue) || (Math.abs(spd - moveSpeedValue) >= 0.01)) {
		moveSpeedText = movespeedf.render(String.format("%.2f", spd));
		moveSpeedValue = spd;
	    }
	    Coord3f pc = pl.getc();
	    if(pc == null)
		return;
	    Coord3f sc = safescreenxf(Coord3f.of(pc.x, pc.y, pc.z + movespeedz));
	    if(sc == null)
		return;
	    g.chcolor();
	    g.aimage(moveSpeedText.tex(), Coord.of(Math.round(sc.x), Math.round(sc.y)), 0.5, 1.0);
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
    }

    protected void maindraw(Render out) {
	drawsmap(out);
	super.maindraw(out);
    }

    private Loading camload = null, lastload = null;
    public void draw(GOut g) {
	Loader.Future<Plob> placing = this.placing;
	if((placing != null) && placing.done())
	    placing.get().gtick(g.out);
	glob.map.sendreqs();
	if((olftimer != 0) && (olftimer < Utils.rtime()))
	    unflashol();
	try {
	    if(camload != null)
		throw(new Loading(camload));
	    undelay(delayed, g);
	    super.draw(g);
	    undelay(delayed2, g);
	    poldraw(g);
	    partydraw(g);
	    MoonFightHud.setPlayerGobId(plgob);
	    drawMoveTrace(g);
	    drawRouteTrace(g);
	    drawMoonCombatGuides(g);
	    drawMoonNavFlash(g);
	    drawPlayerSpeed(g);
	    if(MoonConfig.gfxModEspOverlay && MoonConfig.anyOverlayActive())
		try { MoonOverlay.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonOverlay draw error").issue(); }
	    try { MoonStorage.drawTrackingOverlay(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonStorage track draw error").issue(); }
	    if(MoonConfig.entityHitboxVizMode > 0)
		try { MoonEntityHitboxViz.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonEntityHitboxViz draw error").issue(); }
	    else if(MoonConfig.gfxOrGlobalHitboxes())
		try { MoonHitboxMode.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonHitboxMode draw error").issue(); }
	    if(MoonConfig.hitboxPlacementCarriedEnabled())
		try { MoonHitboxMode.drawPlacementAndCarriedHitboxes(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonHitboxMode placement hitbox draw error").issue(); }
	    if(!MoonConfig.gfxModMiningOverlay || (!MoonConfig.mineSupportSafeTiles && !MoonConfig.mineSweeperRiskTiles))
		try { MoonMiningOverlay.cleanupIfDisabled(); } catch(Exception e) { new Warning(e, "MoonMiningOverlay cleanup error").issue(); }
	    if(MoonConfig.gfxModMiningOverlay && (MoonConfig.mineSupportSafeTiles || MoonConfig.mineSweeperRiskTiles))
		try { MoonMiningOverlay.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonMiningOverlay draw error").issue(); }
	    if(MoonConfig.gfxModMiningOverlay && MoonConfig.mineSweeperShowLabels)
		try { MoonMineSweeperOverlay.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonMineSweeperOverlay draw error").issue(); }
	    if(MoonConfig.combatDamageHud || MoonConfig.gfxModFightHud)
		try { MoonFightHud.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonFightHud draw error").issue(); }
	    if(MoonConfig.gfxModActivityHud)
		try { MoonActivityHud.draw(g, this); } catch(Loading ignored) {} catch(Exception e) { new Warning(e, "MoonActivityHud draw error").issue(); }
	    int cview = currentview();
	    glob.map.reqarea(cc.floor(tilesz).sub(MCache.cutsz.mul(cview + 1)),
			     cc.floor(tilesz).add(MCache.cutsz.mul(cview + 1)));
	} catch(Loading e) {
	    e.boostprio(6);
	    lastload = e;
	    String text = e.getMessage();
	    if(text == null)
		text = "Loading...";
	    g.chcolor(Color.BLACK);
	    g.frect(Coord.z, sz);
	    g.chcolor(Color.WHITE);
	    g.atext(text, sz.div(2), 0.5, 0.5);
	    g.chcolor();
	}
    }
    
    private double initload = -2;
    private boolean initdraw = false;
    private void checkload() {
	if(initload == -1)
	    return;
	double now = Utils.rtime();
	if(initload == -2) {
	    delay2(g -> initdraw = true);
	    initload = now;
	}
	if((terrain.loading() == null) && (gobs.loading() == null) && initdraw) {
	    wdgmsg("initload", now - initload);
	    initload = -1;
	}
    }

    public void tick(double dt) {
	super.tick(dt);
	checkload();
	camload = null;
	try {
	    if((shake = shake * Math.pow(100, -dt)) < 0.01)
		shake = 0;
	    camoff.x = (float)((Math.random() - 0.5) * shake);
	    camoff.y = (float)((Math.random() - 0.5) * shake);
	    camoff.z = (float)((Math.random() - 0.5) * shake);
	    camera.tick(dt);
	} catch(Loading e) {
	    e.boostprio(5);
	    camload = e;
	}
	basic(Camera.class, camera);
	amblight();
	updsmap(amblight);
	updweather();
	moonRefreshBuildBudgets();
	long moonBuildStart = System.nanoTime();
	synchronized(glob.map) {
	    if(!moonMapGridBoot) {
		moonMapGridBoot = true;
		moonApplyMapGridState();
	    }
	    moonArmBuildWindow(MoonBuildStage.TERRAIN_CRITICAL);
	    terrain.tick();
	    clickmap.tick();
	}
	moonArmBuildWindow(MoonBuildStage.GOBS);
	gobs.tick();
	double now = Utils.rtime();
	boolean runSecondary = moonShouldRunSecondaryWorldWork(now);
	synchronized(glob.map) {
	    if(runSecondary) {
		moonArmBuildWindow(MoonBuildStage.TERRAIN_SECONDARY);
		terrain.tickSecondary();
		if(moonShouldSyncOverlays(now)) {
		    olsync();
		    moonMarkOverlaySync(now);
		}
		if(gridlines != null)
		    gridlines.tick();
		oltick();
		moonLastSecondaryWorldWorkAt = now;
	    }
	}
	MoonPerfOverlay.updateWorldBuildMs((System.nanoTime() - moonBuildStart) / 1_000_000.0);
	moonUpdateLoadProgress();
	synchronized(moonPathLock) {
	    if(moonPathClickTodo != null) {
		Coord2d w = moonPathClickTodo;
		moonPathClickTodo = null;
		wdgmsg("click", Coord.z, w.floor(posres), 1, 0);
		moonPathLock.notifyAll();
	    }
	}
	Loader.Future<Plob> placing = this.placing;
	if((placing != null) && placing.done()) {
	    Plob ob = placing.get();
	    synchronized(ob) {
		ob.ctick(dt);
	    }
	}
	if(diabloHeld && MoonConfig.diabloMove && diabloLastC != null) {
	    if(diabloLastMC != null && moonTryReserveAutomoveClick())
		wdgmsg("click", Coord.z, diabloLastMC.floor(posres), 1, 0);
	    if(!diabloPendingHit) {
		diabloPendingHit = true;
		new DiabloProbe(diabloLastC).run();
	    }
	}
	Gob pl = player();
	if(pl == null) {
	    moonClearGateAssist();
	    clearMoveTraceTarget();
	    moonClearRouteTrace();
	    moveSpeedText = null;
	    moveSpeedValue = Double.NaN;
	}
	moonTickGateAssist(pl);
	if(pl != null) {
	    try {
		Coord2d pc = new Coord2d(pl.getc());
		if(moveTraceTarget != null && !diabloHeld) {
		    double dist = pc.dist(moveTraceTarget);
		    double age = Utils.rtime() - moveTraceSetAt;
		    if(dist <= 5.0 && age >= MOVE_TRACE_MIN_VISIBLE_DT) {
			Coord2d next = moveTraceQueue.pollFirst();
			if(next != null) {
			    if(moonTryPathClick()) {
				moveTraceTarget = next;
				moveTraceSetAt = Utils.rtime();
				wdgmsg("click", Coord.z, next.floor(posres), 1, 0);
			    } else {
				moveTraceQueue.addFirst(next);
			    }
			} else {
			    clearMoveTraceTarget();
			}
		    }
		}
		synchronized(moonPathLock) {
		    if(moonRouteTraceTarget != null && pc.dist(moonRouteTraceTarget) <= 5.0) {
			Coord2d next = moonRouteTraceQueue.pollFirst();
			moonRouteTraceTarget = next;
		    }
		}
	    } catch(Loading ignored) {
	    }
	}
    }
    
    public void resize(Coord sz) {
	super.resize(sz);
	camera.resized();
    }

    public static interface PlobAdjust {
	public void adjust(Plob plob, Coord pc, Coord2d mc, int modflags);
	public boolean rotate(Plob plob, int amount, int modflags);
    }

    public static class StdPlace implements PlobAdjust {
	boolean freerot = false;

	public void adjust(Plob plob, Coord pc, Coord2d mc, int modflags) {
	    Coord2d nc;
	    if((modflags & UI.MOD_SHIFT) == 0)
		nc = mc.floor(tilesz).mul(tilesz).add(tilesz.div(2));
	    else if(plobpgran > 0)
		nc = mc.div(tilesz).mul(plobpgran).roundf().div(plobpgran).mul(tilesz);
	    else
		nc = mc;
	    Gob pl = plob.mv().player();
	    if((pl != null) && !freerot)
		plob.move(nc, Math.round(plob.rc.angle(pl.rc) / (Math.PI / 2)) * (Math.PI / 2));
	    else
		plob.move(nc);
	}

	public boolean rotate(Plob plob, int amount, int modflags) {
	    if((modflags & (UI.MOD_CTRL | UI.MOD_SHIFT)) == 0)
		return(false);
	    freerot = true;
	    double na;
	    if((modflags & UI.MOD_SHIFT) == 0)
		na = (Math.PI / 4) * Math.round((plob.a + (amount * Math.PI / 4)) / (Math.PI / 4));
	    else
		na = plob.a + amount * Math.PI / plobagran;
	    na = Utils.cangle(na);
	    plob.move(na);
	    return(true);
	}
    }

	public class Plob extends Gob {
	public PlobAdjust adjust = new StdPlace();
	Coord lastmc = null;
	RenderTree.Slot slot;

	private Plob(Indir<Resource> res, Message sdt) {
	    super(MapView.this.glob, Coord2d.of(getcc()));
	    setattr(new ResDrawable(this, res, sdt));
	}

	@Override
	protected void obstate(Pipe buf) {
	    /* Ground-level preview shares depth with fallen trunks / props and often loses the depth
	     * test; bias slightly toward the camera so the ghost stays visible on the same tile. */
	    buf.prep(new States.DepthBias(-4f, -4f));
	}

	public MapView mv() {return(MapView.this);}

	public void move(Coord2d c, double a) {
	    super.move(c, a);
	    updated();
	}

	public void move(Coord2d c) {
	    move(c, this.a);
	}

	public void move(double a) {
	    move(this.rc, a);
	}

	void place() {
	    if(ui.mc.isect(rootpos(), sz))
		new Adjust(ui.mc.sub(rootpos()), 0).run();
	    this.slot = basic.add(this.placed);
	}

	private class Adjust extends Maptest {
	    int modflags;
	    
	    Adjust(Coord c, int modflags) {
		super(c);
		this.modflags = modflags;
	    }
	    
	    public void hit(Coord pc, Coord2d mc) {
		adjust.adjust(Plob.this, pc, mc, modflags);
		lastmc = pc;
	    }
	}

	public String toString() {
	    return("#<plob>");
	}
    }

    private Collection<String> olflash = null;
    private double olftimer;

    private void unflashol() {
	if(olflash != null) {
	    olflash.forEach(this::disol);
	}
	olflash = null;
	olftimer = 0;
    }

    private void flashol(Collection<String> ols, double tm) {
	unflashol();
	ols.forEach(this::enol);
	olflash = ols;
	olftimer = Utils.rtime() + tm;
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "place") {
	    placementPreviewGob = null;
	    Loader.Future<Plob> placing = this.placing;
	    if(placing != null) {
		if(!placing.cancel()) {
		    Plob ob = placing.get();
		    synchronized(ob) {
			ob.slot.remove();
		    }
		}
		this.placing = null;
	    }
	    int a = 0;
	    Indir<Resource> res = ui.sess.getresv(args[a++]);
	    Message sdt;
	    if((args.length > a) && (args[a] instanceof byte[]))
		sdt = new MessageBuf((byte[])args[a++]);
	    else
		sdt = Message.nil;
	    int oa = a;
	    this.placing = glob.loader.defer(new Supplier<Plob>() {
		    int a = oa;
		    Plob ret = null;
		    public Plob get() {
			if(ret == null)
			    ret = new Plob(res, new MessageBuf(sdt));
			while(a < args.length) {
			    int a2 = a;
			    Indir<Resource> ores = ui.sess.getresv(args[a2++]);
			    Message odt;
			    if((args.length > a2) && (args[a2] instanceof byte[]))
				odt = new MessageBuf((byte[])args[a2++]);
			    else
				odt = Message.nil;
			    ret.addol(ores, odt);
			    a = a2;
			}
			ret.place();
			placementPreviewGob = ret;
			return(ret);
		    }
		});
	} else if(msg == "unplace") {
	    placementPreviewGob = null;
	    Loader.Future<Plob> placing = this.placing;
	    if(placing != null) {
		if(!placing.cancel()) {
		    Plob ob = placing.get();
		    synchronized(ob) {
			ob.slot.remove();
		    }
		}
		this.placing = null;
	    }
	} else if(msg == "move") {
	    cc = ((Coord)args[0]).mul(posres);
	} else if(msg == "plob") {
	    if(args[0] == null)
		plgob = -1;
	    else
		plgob = Utils.uiv(args[0]);
	    glob.moonPlayerGobId = plgob;
	    MoonPacketHook.setPlayerGobId(plgob);
	} else if(msg == "flashol2") {
	    Collection<String> ols = new LinkedList<>();
	    double tm = Utils.dv(args[0]) / 100.0;
	    for(int a = 1; a < args.length; a++)
		ols.add((String)args[a]);
	    flashol(ols, tm);
	} else if(msg == "sel") {
	    boolean sel = Utils.bv(args[0]);
	    synchronized(this) {
		if(selection != null) {
		    selection.destroy();
		    selection = null;
		}
		if(sel) {
		    Coord max = (args.length > 1) ? (Coord)args[1] : null;
		    selection = new Selector(max);
		}
	    }
	} else if(msg == "shake") {
	    shake += Utils.dv(args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public abstract class Maptest {
	private final Coord pc;

	public Maptest(Coord c) {
	    this.pc = c;
	}

	public void run() {
	    Environment env = ui.env;
	    Render out = env.render();
	    Pipe.Op basic = clickbasic(MapView.this.sz);
	    Pipe bstate = new BufPipe().prep(basic);
	    out.clear(bstate, FragID.fragid, FColor.BLACK);
	    out.clear(bstate, 1.0);
	    checkmapclick(out, basic, pc, mc -> {
		    synchronized(ui) {
			if(mc != null)
			    hit(pc, mc);
			else
			    nohit(pc);
		    }
		});
	    env.submit(out);
	}

	protected abstract void hit(Coord pc, Coord2d mc);
	protected void nohit(Coord pc) {}
    }

    public abstract class Hittest {
	private final Coord pc;
	private Coord2d mapcl;
	private ClickData objcl;
	private int dfl = 0;
	
	public Hittest(Coord c) {
	    pc = c;
	}
	
	public void run() {
	    Environment env = ui.env;
	    Render out = env.render();
	    Pipe.Op basic = clickbasic(MapView.this.sz);
	    Pipe bstate = new BufPipe().prep(basic);
	    out.clear(bstate, FragID.fragid, FColor.BLACK);
	    out.clear(bstate, 1.0);
	    checkmapclick(out, basic, pc, mc -> {mapcl = mc; ckdone(1);});
	    out.clear(bstate, FragID.fragid, FColor.BLACK);
	    checkgobclick(out, basic, pc, cl -> {objcl = cl; ckdone(2);});
	    env.submit(out);
	}

	private void ckdone(int fl) {
	    boolean done = false;
	    synchronized(this) {
		    if((dfl |= fl) == 3)
			done = true;
	    }
	    if(done) {
		synchronized(ui) {
		    if(mapcl != null) {
			if(objcl == null)
			    hit(pc, mapcl, null);
			else
			    hit(pc, mapcl, objcl);
		    } else {
			nohit(pc);
		    }
		}
	    }
	}
	
	protected abstract void hit(Coord pc, Coord2d mc, ClickData inf);
	protected void nohit(Coord pc) {}
    }

    private class Click extends Hittest {
	int clickb;
	final int clickmods;

	private Click(Coord c, int b, int mods) {
	    super(c);
	    clickb = b;
	    clickmods = mods;
	}
	
	protected void hit(Coord pc, Coord2d mc, ClickData inf) {
	    if(moonSendResolvedMapClick(pc, mc, inf, clickb, clickmods)) {
		try {
		    MoonBulkStation.maybeStartTakeAllAfterMapClick(MapView.this, inf, clickmods & ~UI.MOD_META, clickb);
		} catch(Exception ignored) {
		}
	    }
	}
    }

    public boolean moonResolvedScreenClick(Coord pc, int button, int mods) {
	if(pc == null)
	    return(false);
	new Click(pc, button, mods).run();
	return(true);
    }

    /**
     * MooNWide automation: synthetic map click at a fixed world point (same {@code wdgmsg} shape as
     * {@link MoonTreeChopBot} / {@link MoonSafeMode}). Skips GPU hit-testing.
     *
     * @param screenProj optional point in world space to project (e.g. {@code getzp(wpt).add(0,0,12)}
     *                   for a cave wall); if null, uses {@code wpt} then terrain {@code getzp(wpt)}.
     */
    public boolean moonSyntheticMapClick(Coord2d wpt, Coord3f screenProj, int button, int mods) {
	if(wpt == null)
	    return(false);
	MoonPathfinder.cancelActiveSmoothMove();
	MoonPathWalker.cancelActive();
	moonClearRouteTrace();
	Coord3f scPt = null;
	if(screenProj != null)
	    scPt = screenxf(screenProj);
	if(scPt == null)
	    scPt = screenxf(wpt);
	if(scPt == null && glob != null && glob.map != null) {
	    try {
		scPt = screenxf(glob.map.getzp(wpt));
	    } catch(Loading ignored) {}
	}
	if(scPt == null)
	    return(false);
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(scPt.x), 2, sz.x - 3),
	    Utils.clip((int)Math.round(scPt.y), 2, sz.y - 3));
	wdgmsg("click", pc, wpt.floor(posres), button, mods);
	return(true);
    }

    /**
     * Synthetic {@code itemact} at a world point (same screen projection as {@link #moonSyntheticMapClick}).
     * Used when the server expects tool use on terrain (mining cursor, etc.) instead of a plain {@code click}.
     */
    public boolean moonSyntheticItemAct(Coord2d wpt, Coord3f screenProj, int mods) {
	if(wpt == null)
	    return(false);
	MoonPathfinder.cancelActiveSmoothMove();
	MoonPathWalker.cancelActive();
	moonClearRouteTrace();
	Coord3f scPt = null;
	if(screenProj != null)
	    scPt = screenxf(screenProj);
	if(scPt == null)
	    scPt = screenxf(wpt);
	if(scPt == null && glob != null && glob.map != null) {
	    try {
		scPt = screenxf(glob.map.getzp(wpt));
	    } catch(Loading ignored) {}
	}
	if(scPt == null)
	    return(false);
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(scPt.x), 2, sz.x - 3),
	    Utils.clip((int)Math.round(scPt.y), 2, sz.y - 3));
	int sendMf = mods & ~UI.MOD_META;
	wdgmsg("itemact", pc, wpt.floor(posres), sendMf);
	return(true);
    }

    private boolean moonStartSmoothMove(Coord2d mc) {
	MoonPathfinder.cancelActiveSmoothMove();
	MoonPathWalker.cancelActive();
	clearMoveTraceTarget();
	moonClearRouteTrace();
	MoonPathfinder.PathPlan plan = MoonPathfinder.planSmoothPath(this, mc);
	if(plan == null || plan.movePath().isEmpty()) {
	    if(ui != null)
		ui.msg("Moon path: no free route.", new Color(255, 210, 120), null);
	    return(true);
	}
	List<Coord2d> move = plan.movePath();
	Coord2d kick = move.get(move.size() - 1);
	double minKick = MCache.tilesz.x * 1.20;
	for(Coord2d wp : move) {
	    if(plan.start != null && plan.start.dist(wp) >= minKick) {
		kick = wp;
		break;
	    }
	}
	wdgmsg("click", Coord.z, kick.floor(posres), 1, 0);
	if(!MoonPathfinder.sendSmoothMove(this, plan)) {
	    if(ui != null)
		ui.msg("Moon path: failed to start smooth move.", new Color(255, 210, 120), null);
	    return(true);
	}
	if(plan.adjustedGoal && ui != null)
	    ui.msg("Moon path: target blocked, rerouted to nearest free tile.", Color.WHITE, null);
	return(true);
    }

    private boolean moonTryPassiveLmbPathfinding(Coord2d mc, ClickData inf, int clickb, int mods) {
	if(mc == null || inf != null || clickb != 1)
	    return(false);
	if((mods & UI.MOD_META) != 0)
	    return(false);
	if(diabloHeld)
	    return(false);
	if(!MoonConfig.teleportPassiveLmbPathfinding)
	    return(false);
	if(!MoonPathfinder.needsDetour(this, mc))
	    return(false);
	return(moonStartSmoothMove(mc));
    }

    private boolean moonSendResolvedMapClick(Coord pc, Coord2d mc, ClickData inf, int clickb, int mods) {
	if(mc == null)
	    return(false);
	moonClearGateAssist();
	boolean isAlt = (mods & UI.MOD_META) != 0;
	if(isAlt && clickb == 3)
	    return(moonStartSmoothMove(mc));
	boolean gateAssist = false;
	MoonPassiveGate.Assist gate = MoonPassiveGate.resolveAssist(this, mc, inf, clickb, mods);
	if(gate != null) {
	    if(moonStartGateAssist(gate, mods)) {
		updateMoveTrace(gate.target, clickb, mods);
		MoonPathfinder.cancelActiveSmoothMove();
		MoonPathWalker.cancelActive();
		moonClearRouteTrace();
		if(inf == null && diabloHeld)
		    diabloLastMC = gate.target;
		return(true);
	    }
	    mc = gate.target;
	    inf = null;
	    gateAssist = true;
	}
	if(!gateAssist && moonTryPassiveLmbPathfinding(mc, inf, clickb, mods))
	    return(true);
	boolean queueOnly = false;
	if(isAlt && inf == null && clickb == 1) {
	    boolean hadTarget = (moveTraceTarget != null);
	    updateMoveTrace(mc, clickb, mods);
	    queueOnly = hadTarget;
	} else {
	    updateMoveTrace(mc, clickb, mods);
	}
	MoonPathfinder.cancelActiveSmoothMove();
	MoonPathWalker.cancelActive();
	moonClearRouteTrace();
	if(inf == null && diabloHeld)
	    diabloLastMC = mc;
	if(queueOnly)
	    return(false);

	int sendMf = mods & ~UI.MOD_META;
	Coord2d send = mc;
	if(inf == null && MoonConfig.moveTraceTileCenter) {
	    Coord tc = send.floor(MCache.tilesz);
	    send = tc.mul(MCache.tilesz).add(MCache.tilesz.div(2));
	}
	Object[] args = {pc, send.floor(posres), clickb, sendMf};
	if(inf != null)
	    args = Utils.extend(args, inf.clickargs());
	wdgmsg("click", args);
	return(true);
    }
    
    private class DiabloProbe extends Hittest {
	DiabloProbe(Coord c) { super(c); }
	protected void hit(Coord pc, Coord2d mc, ClickData inf) {
	    diabloPendingHit = false;
	    if(mc != null && inf == null)
		diabloLastMC = mc;
	}
	protected void nohit(Coord pc) { diabloPendingHit = false; }
    }

    public void grab(Grabber grab) {
	this.grab = grab;
    }
    
    public void release(Grabber grab) {
	if(this.grab == grab)
	    this.grab = null;
    }
    
    private UI.Grab camdrag = null;

    public boolean mousedown(MouseDownEvent ev) {
	parent.setfocus(this);
	if(MoonConfig.diabloMove && ev.b == 1 && grab == null && placing == null
	    && (ui.modflags() & UI.MOD_META) == 0) {
	    diabloHeld = true;
	    diabloLastC = ev.c;
	}
	Loader.Future<Plob> placing_l = this.placing;
	if(ev.b == 2) {
	    if((camdrag == null) && camera.click(ev.c)) {
		camdrag = ui.grabmouse(this);
	    }
	} else if((placing_l != null) && placing_l.done()) {
	    Plob placing = placing_l.get();
	    if(placing.lastmc != null) {
		boolean altPathWhilePlacing = (ev.b == 1) && ((ui.modflags() & UI.MOD_META) != 0);
		if(altPathWhilePlacing)
		    new Click(ev.c, ev.b, ui.modflags()).run();
		else
		    wdgmsg("place", placing.rc.floor(posres), (int)Math.round(placing.a * 32768 / Math.PI), ev.b, ui.modflags());
	    }
	} else if((grab != null) && grab.mmousedown(ev.c, ev.b)) {
	} else {
	    new Click(ev.c, ev.b, ui.modflags()).run();
	}
	return(true);
    }
    
    public void mousemove(MouseMoveEvent ev) {
	if(diabloHeld)
	    diabloLastC = ev.c;
	if(grab != null)
	    grab.mmousemove(ev.c);
	Loader.Future<Plob> placing_l = this.placing;
	if(camdrag != null) {
	    camera.drag(ev.c);
	} else if((placing_l != null) && placing_l.done()) {
	    Plob placing = placing_l.get();
	    if((placing.lastmc == null) || !placing.lastmc.equals(ev.c)) {
		placing.new Adjust(ev.c, ui.modflags()).run();
	    }
	}
    }
    
    public boolean mouseup(MouseUpEvent ev) {
	if(ev.b == 1) {
	    diabloHeld = false;
	    diabloLastMC = null;
	    diabloPendingHit = false;
	}
	if(ev.b == 2) {
	    if(camdrag != null) {
		camera.release();
		camdrag.remove();
		camdrag = null;
	    }
	} else if(grab != null) {
	    grab.mmouseup(ev.c, ev.b);
	}
	return(true);
    }

    public boolean mousewheel(MouseWheelEvent ev) {
	Loader.Future<Plob> placing_l = this.placing;
	if((grab != null) && grab.mmousewheel(ev.c, ev.a))
	    return(true);
	if((placing_l != null) && placing_l.done()) {
	    Plob placing = placing_l.get();
	    if(placing.adjust.rotate(placing, ev.a, ui.modflags()))
		return(true);
	}
	return(camera.wheel(ev.c, ev.a));
    }
    
    public boolean drop(final Coord cc, Coord ul) {
	new Hittest(cc) {
	    public void hit(Coord pc, Coord2d mc, ClickData inf) {
		int mf = ui.modflags();
		boolean stationFill = MoonBulkStation.wantsFillAutomation(mf, inf);
		int sendmf = stationFill ? MoonBulkStation.sanitizeActionMods(mf) : mf;
		MoonInventoryWireDebug.maybeLogBulkTrace("map-drop", "mods=", Integer.valueOf(mf), "send=", Integer.valueOf(sendmf), "inf=", Boolean.valueOf(inf != null), "station=", Boolean.valueOf(stationFill));
		/* Alt+LMB-style remap only without Shift, so bulk (e.g. Shift+Alt)+drop still sends drop/itemact paths. */
		if(((mf & UI.MOD_META) != 0) && !stationFill && ((mf & UI.MOD_SHIFT) == 0)) {
		    moonSendResolvedMapClick(pc, mc, null, 1, mf);
		    return;
		}
		/* Start bulk before wdgmsg: outgoing path can apply uimsg synchronously and clear vhand before maybeStart. */
		try {
		    MoonBulkStation.maybeStartFillAfterDrop(MapView.this, pc, mc, mf, inf);
		} catch(Exception ignored) {
		}
		wdgmsg("drop", pc, mc.floor(posres), sendmf);
	    }
	}.run();
	return(true);
    }
    
    public boolean iteminteract(Coord cc, Coord ul) {
	new Hittest(cc) {
	    public void hit(Coord pc, Coord2d mc, ClickData inf) {
		int mf = ui.modflags();
		boolean bulkItemact = MoonBulkStation.wantsItemactReplay(mf, inf);
		/* Match ARD wire (mod = shift bit only) for bulk itemact replay. */
		int sendmf = bulkItemact ? MoonBulkStation.itemactWireModsForStationFill() : mf;
		MoonInventoryWireDebug.maybeLogBulkTrace("map-itemact", "mods=", Integer.valueOf(mf), "send=", Integer.valueOf(sendmf), "inf=", Boolean.valueOf(inf != null), "bulk=", Boolean.valueOf(bulkItemact));
		if(((mf & UI.MOD_META) != 0) && !bulkItemact && ((mf & UI.MOD_SHIFT) == 0)) {
		    moonSendResolvedMapClick(pc, mc, null, 1, mf);
		    return;
		}
		Object[] args = {pc, mc.floor(posres), sendmf};
		if(inf != null)
		    args = Utils.extend(args, inf.clickargs());
		try {
		    if(MoonBulkStation.maybeStartTubRun(MapView.this, args, mf, inf))
			return;
		    MoonBulkStation.maybeStartFillAfterItemact(MapView.this, args, mf, inf);
		} catch(Exception ignored) {
		}
		wdgmsg("itemact", args);
	    }
	}.run();
	return(true);
    }

    public boolean keydown(KeyDownEvent ev) {
	Loader.Future<Plob> placing_l = this.placing;
	if((placing_l != null) && placing_l.done()) {
	    Plob placing = placing_l.get();
	    if((ev.code == KeyEvent.VK_LEFT) && placing.adjust.rotate(placing, -1, ui.modflags()))
		return(true);
	    if((ev.code == KeyEvent.VK_RIGHT) && placing.adjust.rotate(placing, 1, ui.modflags()))
		return(true);
	}
	if(camera.keydown(ev))
	    return(true);
	return(super.keydown(ev));
    }

    public static final KeyBinding kb_grid = KeyBinding.get("grid", KeyMatch.forchar('G', KeyMatch.C));
    public boolean globtype(GlobKeyEvent ev) {
	if(kb_grid.key().match(ev)) {
	    MoonConfig.setMapGridMode(MoonConfig.mapGridMode + 1);
	    moonApplyMapGridState();
	    if(ui != null)
		ui.msg(LocalizationManager.tr("map.grid.mode." + MoonConfig.mapGridMode), Color.WHITE, null);
	    return(true);
	}
	return(super.globtype(ev));
    }

    public Object tooltip(Coord c, Widget prev) {
	if((ui != null) && ui.modmeta) {
	    moonStorageMaybeProbe(c);
	    if(moonStorageHoverTip != null)
		return(moonStorageHoverTip);
	} else {
	    moonStorageClearHover();
	}
	if(moonClientSel != null && moonClientSel.tt != null)
	    return(moonClientSel.tt);
	if(selection != null) {
	    if(selection.tt != null)
		return(selection.tt);
	}
	return(super.tooltip(c, prev));
    }

    private void moonStorageClearHover() {
	moonStorageHoverSeq++;
	moonStorageHoverAt = null;
	moonStorageHoverTip = null;
	moonStorageHoverPending = false;
    }

    private void moonStorageMaybeProbe(Coord c) {
	if(c == null || ui == null || !ui.modmeta) {
	    moonStorageClearHover();
	    return;
	}
	double now = Utils.rtime();
	if(moonStorageHoverPending) {
	    if(Utils.eq(c, moonStorageHoverAt))
		return;
	    if((now - moonStorageHoverReqAt) < 0.04)
		return;
	} else if(Utils.eq(c, moonStorageHoverAt) && ((now - moonStorageHoverReqAt) < 0.20)) {
	    return;
	}
	final int seq = ++moonStorageHoverSeq;
	moonStorageHoverPending = true;
	moonStorageHoverAt = c;
	moonStorageHoverReqAt = now;
	new Hittest(c) {
	    protected void hit(Coord pc, Coord2d mc, ClickData inf) {
		moonStorageResolveHover(seq, inf);
	    }

	    protected void nohit(Coord pc) {
		moonStorageResolveHover(seq, null);
	    }
	}.run();
    }

    private void moonStorageResolveHover(int seq, ClickData inf) {
	if(seq != moonStorageHoverSeq)
	    return;
	moonStorageHoverPending = false;
	Gob gob = MoonStorage.gobFromClickData(inf);
	moonStorageHoverTip = (gob == null) ? null : MoonStorage.tooltipForGob(this, gob);
    }

    public class GrabXL implements Grabber {
	private final Grabber bk;
	public boolean mv = false;

	public GrabXL(Grabber bk) {
	    this.bk = bk;
	}

	public boolean mmousedown(Coord cc, final int button) {
	    new Maptest(cc) {
		public void hit(Coord pc, Coord2d mc) {
		    bk.mmousedown(mc.round(), button);
		}
	    }.run();
	    return(true);
	}

	public boolean mmouseup(Coord cc, final int button) {
	    new Maptest(cc) {
		public void hit(Coord pc, Coord2d mc) {
		    bk.mmouseup(mc.round(), button);
		}
	    }.run();
	    return(true);
	}

	public boolean mmousewheel(Coord cc, final int amount) {
	    new Maptest(cc) {
		public void hit(Coord pc, Coord2d mc) {
		    bk.mmousewheel(mc.round(), amount);
		}
	    }.run();
	    return(true);
	}

	public void mmousemove(Coord cc) {
	    if(mv) {
		new Maptest(cc) {
		    public void hit(Coord pc, Coord2d mc) {
			bk.mmousemove(mc.round());
		    }
		}.run();
	    }
	}
    }

    public static final OverlayInfo selol = new OverlayInfo() {
	    final Material mat = new Material(new BaseColor(255, 255, 0, 32), States.maskdepth);

	    public Collection<String> tags() {
		return(Arrays.asList("show"));
	    }

	    public Material mat() {return(mat);}
	};
    public class Selector implements Grabber {
	public final Coord max;
	public Coord sc;
	public int modflags;
	private MCache.Overlay ol;
	private UI.Grab mgrab;
	private Text tt;
	final GrabXL xl = new GrabXL(this) {
		public boolean mmousedown(Coord cc, int button) {
		    if(button != 1)
			return(false);
		    return(super.mmousedown(cc, button));
		}
		public boolean mmousewheel(Coord cc, int amount) {
		    return(false);
		}
	    };

	{
	    grab(xl);
	}

	public Selector(Coord max) {
	    this.max = max;
	}

	public boolean mmousedown(Coord mc, int button) {
	    synchronized(MapView.this) {
		if(selection != this)
		    return(false);
		if(sc != null) {
		    ol.destroy();
		    mgrab.remove();
		}
		sc = mc.div(MCache.tilesz2);
		modflags = ui.modflags();
		xl.mv = true;
		mgrab = ui.grabmouse(MapView.this);
		ol = glob.map.new Overlay(Area.sized(sc, new Coord(1, 1)), selol);
		return(true);
	    }
	}

	public Coord getec(Coord mc) {
	    Coord tc = mc.div(MCache.tilesz2);
	    if(max != null) {
		Coord dc = tc.sub(sc);
		tc = sc.add(Utils.clip(dc.x, -(max.x - 1), (max.x - 1)),
			    Utils.clip(dc.y, -(max.y - 1), (max.y - 1)));
	    }
	    return(tc);
	}

	public boolean mmouseup(Coord mc, int button) {
	    synchronized(MapView.this) {
		if(sc != null) {
		    Coord ec = getec(mc);
		    xl.mv = false;
		    tt = null;
		    ol.destroy();
		    mgrab.remove();
		    wdgmsg("sel", sc, ec, modflags);
		    sc = null;
		}
		return(true);
	    }
	}

	public boolean mmousewheel(Coord mc, int amount) {
	    return(false);
	}

	public void mmousemove(Coord mc) {
	    synchronized(MapView.this) {
		if(sc != null) {
		    Coord tc = getec(mc);
		    Coord c1 = new Coord(Math.min(tc.x, sc.x), Math.min(tc.y, sc.y));
		    Coord c2 = new Coord(Math.max(tc.x, sc.x), Math.max(tc.y, sc.y));
		    ol.update(new Area(c1, c2.add(1, 1)));
		    tt = Text.render(String.format("%d\u00d7%d", c2.x - c1.x + 1, c2.y - c1.y + 1));
		}
	    }
	}

	public void destroy() {
	    synchronized(MapView.this) {
		if(sc != null) {
		    ol.destroy();
		    mgrab.remove();
		}
		release(xl);
	    }
	}
    }

    /**
     * Drag a rectangle on the map (same interaction as server-driven mining/cultivation).
     * Tile coordinates are in the same space as {@link Selector}; {@link Area} is
     * {@code ul} inclusive, {@code br} exclusive.
     */
    public class ClientTileSelector implements Grabber {
	public final Coord max;
	public final Consumer<Area> done;
	/** When true, tooltip shows estimated tree count in the dragged tile rectangle. */
	public final boolean treeCountPreview;
	public Coord sc;
	public int modflags;
	private MCache.Overlay ol;
	private UI.Grab mgrab;
	public Text tt;
	final GrabXL xl = new GrabXL(this) {
		public boolean mmousedown(Coord cc, int button) {
		    if(button != 1)
			return(false);
		    return(super.mmousedown(cc, button));
		}
		public boolean mmousewheel(Coord cc, int amount) {
		    return(false);
		}
	    };

	{
	    grab(xl);
	}

	public ClientTileSelector(Coord max, Consumer<Area> onDone, boolean treeCountPreview) {
	    this.max = max;
	    this.done = onDone;
	    this.treeCountPreview = treeCountPreview;
	}

	public boolean mmousedown(Coord mc, int button) {
	    synchronized(MapView.this) {
		if(sc != null) {
		    ol.destroy();
		    mgrab.remove();
		}
		sc = mc.div(MCache.tilesz2);
		modflags = ui.modflags();
		xl.mv = true;
		mgrab = ui.grabmouse(MapView.this);
		ol = glob.map.new Overlay(Area.sized(sc, new Coord(1, 1)), selol);
		return(true);
	    }
	}

	public Coord getec(Coord mc) {
	    Coord tc = mc.div(MCache.tilesz2);
	    if(max != null) {
		Coord dc = tc.sub(sc);
		tc = sc.add(Utils.clip(dc.x, -(max.x - 1), (max.x - 1)),
			    Utils.clip(dc.y, -(max.y - 1), (max.y - 1)));
	    }
	    return(tc);
	}

	public boolean mmouseup(Coord mc, int button) {
	    synchronized(MapView.this) {
		if(sc != null) {
		    Coord tc = getec(mc);
		    xl.mv = false;
		    tt = null;
		    ol.destroy();
		    mgrab.remove();
		    Coord c1 = new Coord(Math.min(tc.x, sc.x), Math.min(tc.y, sc.y));
		    Coord c2 = new Coord(Math.max(tc.x, sc.x), Math.max(tc.y, sc.y));
		    Area a = new Area(c1, c2.add(1, 1));
		    Consumer<Area> cb = done;
		    sc = null;
		    destroy();
		    if(cb != null)
			cb.accept(a);
		}
		return(true);
	    }
	}

	public boolean mmousewheel(Coord mc, int amount) {
	    return(false);
	}

	public void mmousemove(Coord mc) {
	    synchronized(MapView.this) {
		if(sc != null) {
		    Coord tc = getec(mc);
		    Coord c1 = new Coord(Math.min(tc.x, sc.x), Math.min(tc.y, sc.y));
		    Coord c2 = new Coord(Math.max(tc.x, sc.x), Math.max(tc.y, sc.y));
		    Area ta = new Area(c1, c2.add(1, 1));
		    ol.update(ta);
		    int w = c2.x - c1.x + 1, h = c2.y - c1.y + 1;
		    if(treeCountPreview) {
			int n = MoonTreeUtil.countTreesInTileArea(MapView.this.glob, ta);
			tt = Text.render(String.format("%d\u00d7%d — %d", w, h, n));
		    } else {
			tt = Text.render(String.format("%d\u00d7%d", w, h));
		    }
		}
	    }
	}

	public void destroy() {
	    synchronized(MapView.this) {
		if(sc != null) {
		    ol.destroy();
		    mgrab.remove();
		}
		release(xl);
		if(MapView.this.moonClientSel == this)
		    MapView.this.moonClientSel = null;
	    }
	}
    }

    public void startClientTileSelect(Coord max, Consumer<Area> onDone) {
	startClientTileSelect(max, onDone, false);
    }

    public void startClientTileSelect(Coord max, Consumer<Area> onDone, boolean treeCountPreview) {
	synchronized(this) {
	    if(moonClientSel != null) {
		moonClientSel.destroy();
		moonClientSel = null;
	    }
	    if(selection != null) {
		selection.destroy();
		selection = null;
	    }
	    moonClientSel = new ClientTileSelector(max, onDone, treeCountPreview);
	}
    }

    private Camera makecam(Class<? extends Camera> ct, String... args) {
	try {
	    try {
		Constructor<? extends Camera> cons = ct.getConstructor(MapView.class, String[].class);
		return(cons.newInstance(new Object[] {this, args}));
	    } catch(IllegalAccessException e) {
	    } catch(NoSuchMethodException e) {
	    }
	    try {
		Constructor<? extends Camera> cons = ct.getConstructor(MapView.class);
		return(cons.newInstance(new Object[] {this}));
	    } catch(IllegalAccessException e) {
	    } catch(NoSuchMethodException e) {
	    }
	} catch(InstantiationException e) {
	    throw(new Error(e));
	} catch(InvocationTargetException e) {
	    if(e.getCause() instanceof RuntimeException)
		throw((RuntimeException)e.getCause());
	    throw(new RuntimeException(e));
	}
	throw(new RuntimeException("No valid constructor found for camera " + ct.getName()));
    }

    private Camera restorecam() {
	Class<? extends Camera> ct = camtypes.get(Utils.getpref("defcam", null));
	if(ct == null)
	    return(new SOrthoCam());
	byte[] raw = Utils.getprefb("camargs", null);
	String[] args;
	if(raw != null) {
	    try {
		String[] strict = Utils.deserializeStringArrayForPrefs(raw);
		if(strict != null) {
		    args = strict;
		} else {
		    Object ob = Utils.deserialize(raw);
		    args = (ob instanceof String[]) ? (String[])ob : new String[0];
		}
	    } catch(Exception e) {
		args = new String[0];
	    }
	} else {
	    args = new String[0];
	}
	try {
	    return(makecam(ct, args));
	} catch(Exception e) {
	    return(new SOrthoCam());
	}
    }

    private static final String[] moonCameraCycle = {"ortho", "follow", "bad", "worse"};

    private static String moonCameraLabel(String id) {
	if("follow".equals(id))
	    return(LocalizationManager.tr("opt.vanilla.cam.follow"));
	if("bad".equals(id))
	    return(LocalizationManager.tr("opt.vanilla.cam.free"));
	if("worse".equals(id))
	    return(LocalizationManager.tr("opt.vanilla.cam.legacy"));
	return(LocalizationManager.tr("opt.vanilla.cam.ortho"));
    }

    public String cycleCameraMode() {
	String cur = Utils.getpref("defcam", "ortho");
	int idx = -1;
	for(int i = 0; i < moonCameraCycle.length; i++) {
	    if(moonCameraCycle[i].equals(cur)) {
		idx = i;
		break;
	    }
	}
	String next = moonCameraCycle[(idx + 1 + moonCameraCycle.length) % moonCameraCycle.length];
	Class<? extends Camera> ct = camtypes.get(next);
	if(ct == null)
	    return(moonCameraLabel(cur));
	camera = makecam(ct, new String[0]);
	Utils.setpref("defcam", next);
	Utils.setprefb("camargs", Utils.serializeStringArrayForPrefs(new String[0]));
	return(moonCameraLabel(next));
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("cam", new Console.Command() {
		public void run(Console cons, String[] args) throws Exception {
		    if(args.length >= 2) {
			Class<? extends Camera> ct = camtypes.get(args[1]);
			String[] cargs = Utils.splice(args, 2);
			if(ct != null) {
				camera = makecam(ct, cargs);
				Utils.setpref("defcam", args[1]);
				Utils.setprefb("camargs", Utils.serializeStringArrayForPrefs(cargs));
			} else {
			    throw(new Exception("no such camera: " + args[1]));
			}
		    }
		}
	    });
	cmdmap.put("whyload", new Console.Command() {
		public void run(Console cons, String[] args) throws Exception {
		    Loading l = lastload;
		    if(l == null)
			throw(new Exception("Not loading"));
		    l.printStackTrace(cons.out);
		}
	    });
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }

    static {
	Console.setscmd("placegrid", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if((plobpgran = Double.parseDouble(args[1])) < 0)
			plobpgran = 0;
		    Utils.setprefd("plobpgran", plobpgran);
		}
	    });
	Console.setscmd("placeangle", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if((plobagran = Double.parseDouble(args[1])) < 2)
			plobagran = 2;
		    Utils.setprefd("plobagran", plobagran);
		}
	    });
	Console.setscmd("clickfuzz", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if((gobclfuzz = Integer.parseInt(args[1])) < 0)
			gobclfuzz = 0;
		}
	    });
	Console.setscmd("clickdb", new Console.Command() {
		public void run(Console cons, String[] args) {
		    clickdb = Utils.parsebool(args[1], false);
		}
	    });
    }
}
