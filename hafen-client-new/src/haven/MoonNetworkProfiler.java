package haven;

import java.awt.Color;
import java.awt.EventQueue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static haven.OCache.posres;

/**
 * Dev-only profiler for outgoing {@link RMessage#RMSG_WDGMSG} timing and inbound
 * correlation on the same widget id.
 * <p>
 * Safety boundary: active dual-send traces are limited to non-mutating diagnostic
 * actions. For state-mutating pairs (for example inventory actions), the profiler
 * arms a passive trace only and logs inbound RTT / err / dstwdg without injecting
 * the pair itself.
 */
public final class MoonNetworkProfiler {
    private static final String PROP_ENABLED = "haven.moon.netprof";
    /** When true, print {@code agnostic-sync} lines for silent outcomes (default off — avoids log spam). */
    private static final String PROP_AGNOSTIC_VERBOSE = "haven.moon.netprof.agnosticVerbose";
    private static final long DEFAULT_GAP_NS = 500_000L;
    private static final long DEFAULT_TIMEOUT_NS = 2_000_000_000L;
    private static final Object[] NO_ARGS = new Object[0];
    private static final Object LOCK = new Object();
    private static final Integer INT_NEG1 = Integer.valueOf(-1);
    private static final long AGNOSTIC_GAP_NS = 550_000L;
    private static final long AGNOSTIC_TICK_NS = 100_000_000L;
    private static final double FLUSH_REL_WAIT_SEC = 0.35;

    /** {@link #executeAgnosticSyncTest(UI, int, Coord)} sequence: transfer→take with optional dirty map inject. */
    public static final int AGNOSTIC_MODE_ORIGINAL = 0;
    /** transfer → {@link #queueWorldDirtyItemAct} (MapView {@code itemact} at feet) → take. */
    public static final int AGNOSTIC_MODE_DIRTY_CONTEXT = 1;
    /** take → busy → transfer (race while item path may still be in flight). */
    public static final int AGNOSTIC_MODE_DOUBLE_TAKE = 2;

    /** Default: dirty-context inject (see {@link #AGNOSTIC_MODE_DIRTY_CONTEXT}). */
    public static volatile int agnosticSyncMode = AGNOSTIC_MODE_DIRTY_CONTEXT;

    /**
     * Monotonic id per {@link #executeAgnosticSyncTest} — {@link #scheduleTripleSyncChecks} drops delayed lines
     * when a newer test started (avoids spam-K interleaving +1000ms from run A with +100ms from run B).
     */
    private static final AtomicInteger agnosticSyncRunId = new AtomicInteger();

    /** Human-readable label for {@link #agnosticSyncMode} (hotkey feedback). */
    public static String agnosticSyncModeLabel() {
	switch(agnosticSyncMode) {
	case AGNOSTIC_MODE_ORIGINAL:
	    return "ORIGINAL (transfer→take)";
	case AGNOSTIC_MODE_DIRTY_CONTEXT:
	    return "DIRTY (MapView itemact between transfer and take)";
	case AGNOSTIC_MODE_DOUBLE_TAKE:
	    return "DOUBLE (take→transfer)";
	default:
	    return "custom(" + agnosticSyncMode + ")";
	}
    }

    /** Clamp to {@link #AGNOSTIC_MODE_ORIGINAL}…{@link #AGNOSTIC_MODE_DOUBLE_TAKE} and log to stderr. */
    public static void setAgnosticSyncMode(int mode) {
	int m = Utils.clip(mode, AGNOSTIC_MODE_ORIGINAL, AGNOSTIC_MODE_DOUBLE_TAKE);
	agnosticSyncMode = m;
	System.err.println("[MoonNetProf] agnostic mode set: " + agnosticSyncModeLabel());
    }

    /** Cycle 0 → 1 → 2 → 0; see hotkeys in {@link GameUI#globtype}. */
    public static int cycleAgnosticSyncMode() {
	int cur = Utils.clip(agnosticSyncMode, AGNOSTIC_MODE_ORIGINAL, AGNOSTIC_MODE_DOUBLE_TAKE);
	setAgnosticSyncMode((cur + 1) % 3);
	return(agnosticSyncMode);
    }

    private static volatile boolean observerInstalled;
    private static volatile Trace active;
    private static volatile AgnosticSync agnosticSync;
    private static GhostLeakOverlay ghostLeakOverlay;

    private MoonNetworkProfiler() {}

    /**
     * Single-flight state for {@link #executeAgnosticSyncTest(UI, int, Coord)}; fields are written from the
     * connection thread and the test / timeout thread — all volatile.
     */
    private static final class AgnosticSync {
	final UI ui;
	final int wid;
	/** Time first race packet (transfer or take) was queued. */
	volatile long firstPacketQueuedNs;
	/** Time second race packet (take or transfer) was queued. */
	volatile long secondPacketQueuedNs;
	volatile boolean midFlightErr;
	/** Inbound {@code err} within {@link #AGNOSTIC_TICK_NS} after second packet. */
	volatile boolean postSecondPacketErr;
	/** Non-err WDGMSG or dstwdg for slot {@link #wid} after first packet. */
	volatile boolean firstPacketAck;

	AgnosticSync(UI ui, int wid) {
	    this.ui = ui;
	    this.wid = wid;
	}
    }

    /** Full-screen warning after {@link #agnosticVerdictAfterTick} reports a potential ghost state. */
    private static final class GhostLeakOverlay extends Widget {
	private final Text.Line line;

	GhostLeakOverlay() {
	    super(Coord.z);
	    line = Text.render("GHOST DETECTED - LOGOUT NOW", new Color(255, 60, 60));
	}

	@Override
	protected void added() {
	    resize(parent.sz);
	    raise();
	}

	@Override
	public void draw(GOut g) {
	    g.chcolor(0, 0, 0, 200);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    Coord tsz = line.sz();
	    g.image(line.img, sz.sub(tsz).div(2));
	}

	@Override
	public boolean mousedown(Widget.MouseDownEvent ev) {
	    if(MoonNetworkProfiler.ghostLeakOverlay == this)
		MoonNetworkProfiler.ghostLeakOverlay = null;
	    reqdestroy();
	    return(true);
	}
    }

    private static final class Trace {
	final int wid;
	final String action1;
	final String action2;
	final long armedNs;
	final boolean passiveOnly;
	final String note;
	volatile long action1QueuedNs;
	volatile long action2QueuedNs;
	volatile long markerQueuedNs;

	Trace(int wid, String action1, String action2, boolean passiveOnly, String note) {
	    this.wid = wid;
	    this.action1 = action1;
	    this.action2 = action2;
	    this.armedNs = System.nanoTime();
	    this.passiveOnly = passiveOnly;
	    this.note = note;
	}
    }

    public static boolean devtoolsEnabled() {
	return(Utils.parsebool(Utils.getprop(PROP_ENABLED, "false"), false));
    }

    public static boolean enabled() {
	return(devtoolsEnabled());
    }

    public static String enableHint() {
	return("-D" + PROP_ENABLED + "=true");
    }

    private static boolean agnosticVerbose() {
	return(Utils.parsebool(Utils.getprop(PROP_AGNOSTIC_VERBOSE, "false"), false));
    }

    private static void ensureObserver() {
	if(observerInstalled)
	    return;
	synchronized(LOCK) {
	    if(observerInstalled)
		return;
	    MoonPacketHook.addIncomingObserver(MoonNetworkProfiler::onIncoming);
	    observerInstalled = true;
	}
    }

    private static boolean isMutatingAction(String action) {
	if(action == null)
	    return(true);
	switch(action) {
	case "take":
	case "transfer":
	case "drop":
	case "itemact":
	case "iact":
	case "dropul":
	case "xfer":
	case "place":
	case "equip":
	case "unequip":
	    return(true);
	default:
	    return(false);
	}
    }

    private static long sinceMs(long nowNs, long thenNs) {
	return((nowNs - thenNs) / 1_000_000L);
    }

    public static void busyWaitNanos(long targetNs) {
	if(targetNs <= 0L)
	    return;
	long start = System.nanoTime();
	while(System.nanoTime() - start < targetNs) {
	    /* spin */
	}
    }

    public static void armPassiveTrace(UI ui, int wid, String action1, String action2) {
	if(!enabled()) {
	    System.err.println("[MoonNetProf] passive trace ignored: disabled (" + enableHint() + ")");
	    return;
	}
	if(ui == null || ui.sess == null)
	    throw(new IllegalArgumentException("ui/sess"));
	ensureObserver();
	synchronized(LOCK) {
	    active = new Trace(wid, action1, action2, true, "passive");
	}
	System.err.println("[MoonNetProf] armed passive trace wid=" + wid + " action1=" + action1 + " action2=" + action2);
    }

    /**
     * Dev-only timing helper for two-step diagnostic sequences on the same widget id.
     * <p>
     * For mutating pairs the profiler refuses active injection and arms passive tracing only.
     */
    public static void traceTransactionConsistency(UI ui, int wid, String action1, String action2) {
	if(!enabled()) {
	    System.err.println("[MoonNetProf] trace ignored: disabled (" + enableHint() + ")");
	    return;
	}
	if(ui == null || ui.sess == null)
	    throw(new IllegalArgumentException("ui/sess"));
	if(wid < 0)
	    throw(new IllegalArgumentException("wid"));
	ensureObserver();
	if(isMutatingAction(action1) || isMutatingAction(action2)) {
	    synchronized(LOCK) {
		active = new Trace(wid, action1, action2, true, "mutating-pair-refused");
	    }
	    System.err.println("[MoonNetProf] active dual-send refused for mutating pair wid=" + wid
		+ " action1=" + action1 + " action2=" + action2
		+ "; armed passive trace instead");
	    return;
	}
	Trace trace = new Trace(wid, action1, action2, false, "direct-safe");
	synchronized(LOCK) {
	    active = trace;
	}
	trace.action1QueuedNs = System.nanoTime();
	queueWdgmsg(ui.sess, wid, action1);
	trace.markerQueuedNs = System.nanoTime();
	queueUserAgentMarker(ui.sess, "moon.netprof.trace", Long.toString(trace.markerQueuedNs));
	busyWaitNanos(DEFAULT_GAP_NS);
	trace.action2QueuedNs = System.nanoTime();
	queueWdgmsg(ui.sess, wid, action2);
	System.err.println("[MoonNetProf] queued direct-safe trace wid=" + wid + " action1=" + action1
	    + " gapNs=" + DEFAULT_GAP_NS + " action2=" + action2);
    }

    /**
     * Direct single-action profiler path via {@link Session#queuemsg}, bypassing {@link UI} message dispatch.
     */
    public static void traceSingleAction(UI ui, int wid, String action, Object... args) {
	if(!enabled()) {
	    System.err.println("[MoonNetProf] single-action trace ignored: disabled (" + enableHint() + ")");
	    return;
	}
	if(ui == null || ui.sess == null)
	    throw(new IllegalArgumentException("ui/sess"));
	if(wid < 0)
	    throw(new IllegalArgumentException("wid"));
	ensureObserver();
	Trace trace = new Trace(wid, action, null, false, "single");
	trace.action1QueuedNs = System.nanoTime();
	synchronized(LOCK) {
	    active = trace;
	}
	queueWdgmsg(ui.sess, wid, action, args);
	System.err.println("[MoonNetProf] queued single-action trace wid=" + wid + " action=" + action
	    + " args=" + Arrays.deepToString((args == null) ? NO_ARGS : args));
    }

    private static void queueUserAgentMarker(Session sess, String key, String val) {
	PMessage msg = new PMessage(RMessage.RMSG_USERAGENT);
	msg.addstring(key).addstring(val);
	sess.queuemsg(msg);
    }

    private static void queueWdgmsg(Session sess, int wid, String action, Object... args) {
	Object[] a = (args == null) ? NO_ARGS : args;
	PMessage msg = new PMessage(RMessage.RMSG_WDGMSG);
	msg.addint32(wid);
	msg.addstring(action);
	msg.addlist(a);
	sess.queuemsg(msg);
    }

    /** {@code transfer(slot, n)} — {@link Message#addtto} only, no vararg array for args. */
    private static void queueTransferSlotN(Session sess, int wid, Coord slot, Integer n) {
	PMessage msg = new PMessage(RMessage.RMSG_WDGMSG);
	msg.addint32(wid);
	msg.addstring("transfer");
	msg.addtto(slot);
	msg.addtto(n);
	sess.queuemsg(msg);
    }

    /** {@code take(n)} — single-arg list without allocating {@code Object[]}. */
    private static void queueTakeN(Session sess, int wid, Integer n) {
	PMessage msg = new PMessage(RMessage.RMSG_WDGMSG);
	msg.addint32(wid);
	msg.addstring("take");
	msg.addtto(n);
	sess.queuemsg(msg);
    }

    /**
     * MapView {@code itemact} at player feet (screen center / world under gob) — server-side context switch away
     * from the inventory slot. Same wire shape as {@link MapView#moonSyntheticItemAct}.
     */
    private static void queueWorldDirtyItemAct(Session sess, UI ui) {
	GameUI gui = findGameUI(ui);
	if(gui == null || gui.map == null) {
	    System.err.println("[MoonNetProf] dirty-context: no MapView — skip");
	    return;
	}
	int mwid = ui.widgetid(gui.map);
	if(mwid < 0) {
	    System.err.println("[MoonNetProf] dirty-context: MapView not bound — skip");
	    return;
	}
	Gob pl = gui.map.player();
	if(pl == null || pl.getc() == null) {
	    System.err.println("[MoonNetProf] dirty-context: no player gob — skip");
	    return;
	}
	Coord3f gc = pl.getc();
	Coord2d wpt = new Coord2d(gc.x, gc.y);
	Coord pc = gui.map.sz.sub(2, 2).div(2);
	int mods = 0;
	queueWdgmsg(sess, mwid, "itemact", pc, wpt.floor(posres), Integer.valueOf(mods));
	sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	System.err.println("[MoonNetProf] dirty-context: MapView itemact wid=" + mwid + " wpt=" + wpt);
    }

    private static void printSyncCheckLine(UI ui, int delayMs, int runId) {
	if(agnosticSyncRunId.get() != runId)
	    return;
	GameUI gui = findGameUI(ui);
	boolean vhandItem = gui != null && gui.vhand != null && gui.vhand.item != null;
	boolean handNonEmpty = gui != null && !gui.hand.isEmpty();
	boolean inHand = vhandItem || handNonEmpty;
	System.err.println("[SYNC-CHECK] run=" + runId + " +" + delayMs + "ms Item in hand: " + inHand
	    + " (vhand.item!=null)=" + vhandItem + " hand.nonEmpty=" + handNonEmpty);
    }

    private static void scheduleTripleSyncChecks(final UI ui, final int runId) {
	if(ui == null)
	    return;
	for(int ms : new int[] { 100, 300, 1000 }) {
	    final int delayMs = ms;
	    Thread t = new Thread(() -> {
		try {
		    Thread.sleep(delayMs);
		} catch(InterruptedException e) {
		    Thread.currentThread().interrupt();
		    return;
		}
		if(agnosticSyncRunId.get() != runId)
		    return;
		EventQueue.invokeLater(() -> {
		    if(agnosticSyncRunId.get() != runId)
			return;
		    printSyncCheckLine(ui, delayMs, runId);
		});
	    }, "MoonNetProf-sync-" + delayMs + "-r" + runId);
	    t.setDaemon(true);
	    t.start();
	}
    }

    /**
     * Inventory race harness — mode {@link #agnosticSyncMode}:
     * <ul>
     * <li>{@link #AGNOSTIC_MODE_ORIGINAL}: transfer→BEATING→gap→take</li>
     * <li>{@link #AGNOSTIC_MODE_DIRTY_CONTEXT}: + MapView {@code itemact} at feet between transfer and take</li>
     * <li>{@link #AGNOSTIC_MODE_DOUBLE_TAKE}: take→BEATING→gap→transfer (conflict while take may be in flight)</li>
     * </ul>
     * After each REL, {@link Session#flushOutgoingRel}. {@link #scheduleTripleSyncChecks} runs from packet end.
     */
    public static void executeAgnosticSyncTest(UI ui, int wid, Coord slot) {
	if(!enabled()) {
	    System.err.println("[MoonNetProf] agnostic-sync ignored: disabled (" + enableHint() + ")");
	    return;
	}
	if(ui == null || ui.sess == null)
	    throw(new IllegalArgumentException("ui/sess"));
	if(wid < 0)
	    throw(new IllegalArgumentException("wid"));
	if(slot == null)
	    throw(new IllegalArgumentException("slot"));
	ensureObserver();
	synchronized(LOCK) {
	    active = null;
	}
	Session sess = ui.sess;
	AgnosticSync s = new AgnosticSync(ui, wid);
	agnosticSync = s;
	int mode = agnosticSyncMode;
	if(mode != AGNOSTIC_MODE_ORIGINAL && mode != AGNOSTIC_MODE_DIRTY_CONTEXT && mode != AGNOSTIC_MODE_DOUBLE_TAKE)
	    mode = AGNOSTIC_MODE_DIRTY_CONTEXT;

	if(mode == AGNOSTIC_MODE_DOUBLE_TAKE) {
	    s.firstPacketQueuedNs = System.nanoTime();
	    queueTakeN(sess, wid, INT_NEG1);
	    sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	    sess.queuemsg(new PMessage(RMessage.RMSG_BEATING));
	    sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	    busyWaitNanos(AGNOSTIC_GAP_NS);
	    s.secondPacketQueuedNs = System.nanoTime();
	    queueTransferSlotN(sess, wid, slot, INT_NEG1);
	    sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	} else {
	    s.firstPacketQueuedNs = System.nanoTime();
	    queueTransferSlotN(sess, wid, slot, INT_NEG1);
	    sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	    sess.queuemsg(new PMessage(RMessage.RMSG_BEATING));
	    sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	    if(mode == AGNOSTIC_MODE_DIRTY_CONTEXT)
		queueWorldDirtyItemAct(sess, ui);
	    busyWaitNanos(AGNOSTIC_GAP_NS);
	    s.secondPacketQueuedNs = System.nanoTime();
	    queueTakeN(sess, wid, INT_NEG1);
	sess.flushOutgoingRel(FLUSH_REL_WAIT_SEC);
	}
	int runId = agnosticSyncRunId.incrementAndGet();
	scheduleTripleSyncChecks(ui, runId);
	scheduleAgnosticVerdict(s);
    }

    /**
     * Hotkey helper: same targeting as {@link MoonInventoryStress#stressTransferHotkey(GameUI, int)} (hover or
     * optional main-inventory fallback).
     */
    public static void executeAgnosticSyncHotkey(GameUI gui) {
	if(!enabled()) {
	    System.err.println("[MoonNetProf] agnostic-sync ignored: disabled (" + enableHint() + ")");
	    return;
	}
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonNetProf] agnostic-sync: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem hover = MoonInventoryStress.gitemUnderCursor(ui);
	GItem it = hover;
	if(it == null && MoonInventoryStress.stressHotkeyAllowMaininvFallback && gui.maininv != null) {
	    for(Map.Entry<GItem, WItem> e : gui.maininv.wmap.entrySet()) {
		it = e.getKey();
		break;
	    }
	}
	if(it == null) {
	    System.err.println("[MoonNetProf] agnostic-sync: hover the slot (or enable stressHotkeyAllowMaininvFallback)");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonNetProf] agnostic-sync: GItem not bound to UI id");
	    return;
	}
	boolean fromHover = (hover != null);
	Coord slot = MoonInventoryStress.stressSlotCoord(it, ui, fromHover);
	executeAgnosticSyncTest(ui, id, slot);
    }

    private static void scheduleAgnosticVerdict(final AgnosticSync s) {
	Thread t = new Thread(() -> agnosticVerdictAfterTick(s), "MoonNetProf-agnostic-tick");
	t.setDaemon(true);
	t.start();
    }

    private static GameUI findGameUI(UI ui) {
	if(ui == null || ui.root == null)
	    return(null);
	return(findGameUI0(ui.root));
    }

    private static GameUI findGameUI0(Widget w) {
	if(w instanceof GameUI)
	    return((GameUI)w);
	for(Widget c = w.child; c != null; c = c.next) {
	    GameUI g = findGameUI0(c);
	    if(g != null)
		return(g);
	}
	return(null);
    }

    /**
     * UI-thread snapshot of hand state + full-screen warning. Must run on EDT — called from
     * {@link EventQueue#invokeLater(Runnable)}.
     */
    private static void ghostLeakUiActions(AgnosticSync s) {
	GameUI gui = findGameUI(s.ui);
	if(gui == null)
	    return;
	synchronized(s.ui) {
	    if(ghostLeakOverlay != null) {
		try {
		    ghostLeakOverlay.reqdestroy();
		} catch(Throwable ignored) {
		}
		ghostLeakOverlay = null;
	    }
	    ghostLeakOverlay = gui.add(new GhostLeakOverlay(), Coord.z);
	    ghostLeakOverlay.resize(gui.sz);
	}
    }

    private static void onPotentialGhostLeak(AgnosticSync s) {
	if(s == null || s.ui == null)
	    return;
	EventQueue.invokeLater(() -> ghostLeakUiActions(s));
    }

    private static void agnosticVerdictAfterTick(AgnosticSync s) {
	try {
	    Thread.sleep(100L);
	} catch(InterruptedException e) {
	    Thread.currentThread().interrupt();
	    return;
	}
	if(agnosticSync != s)
	    return;
	boolean silentNoErr = !s.midFlightErr && !s.postSecondPacketErr;
	if(silentNoErr && s.firstPacketAck) {
	    System.err.println("[MoonNetProf] Potential State Leak: ack after first race packet, no err after second within 100ms wid="
		+ s.wid + " mode=" + agnosticSyncMode);
	    onPotentialGhostLeak(s);
	} else if(silentNoErr && agnosticVerbose()) {
	    System.err.println("[MoonNetProf] agnostic-sync: wid=" + s.wid
		+ " outcome=silent_no_err (no WDGMSG/dstwdg ack on this wid in window; not scored as leak)");
	} else if(!silentNoErr) {
	    System.err.println("[MoonNetProf] agnostic-sync verdict wid=" + s.wid + " midFlightErr=" + s.midFlightErr
		+ " postSecondPacketErr=" + s.postSecondPacketErr + " firstPacketAck=" + s.firstPacketAck);
	}
	agnosticSync = null;
    }

    private static void observeAgnosticIncoming(int type, PMessage msg) {
	AgnosticSync s = agnosticSync;
	if(s == null || msg == null)
	    return;
	if(type == RMessage.RMSG_DSTWDG) {
	    PMessage m = msg.clone();
	    try {
		m.rewind();
		int dstId = m.int32();
		long now = System.nanoTime();
		if(dstId == s.wid && now >= s.firstPacketQueuedNs)
		    s.firstPacketAck = true;
	    } catch(Throwable ignored) {
	    }
	    return;
	}
	if(type != RMessage.RMSG_WDGMSG)
	    return;
	PMessage m = msg.clone();
	try {
	    m.rewind();
	    int recvWid = m.int32();
	    String name = m.string();
	    if(recvWid != s.wid)
		return;
	    long now = System.nanoTime();
	    if("err".equals(name)) {
		if(now < s.firstPacketQueuedNs)
		    return;
		if(s.secondPacketQueuedNs == 0L)
		    s.midFlightErr = true;
		else if(now >= s.secondPacketQueuedNs && (now - s.secondPacketQueuedNs) <= AGNOSTIC_TICK_NS)
		    s.postSecondPacketErr = true;
		return;
	    }
	    if(now >= s.firstPacketQueuedNs)
		s.firstPacketAck = true;
	} catch(Throwable ignored) {
	}
    }

    public static Object parseConsoleAtom(String token) {
	if(token == null)
	    return("");
	int comma = token.indexOf(',');
	if(comma > 0 && comma < token.length() - 1 && token.indexOf(',', comma + 1) < 0) {
	    try {
		int x = Integer.parseInt(token.substring(0, comma).trim());
		int y = Integer.parseInt(token.substring(comma + 1).trim());
		return(Coord.of(x, y));
	    } catch(NumberFormatException ignored) {
	    }
	}
	try {
	    return(Integer.valueOf(token));
	} catch(NumberFormatException ignored) {
	}
	return(token);
    }

    public static Object[] parseConsoleArgs(String[] argv, int from) {
	if(argv == null || from >= argv.length)
	    return(NO_ARGS);
	Object[] ret = new Object[argv.length - from];
	for(int i = from; i < argv.length; i++)
	    ret[i - from] = parseConsoleAtom(argv[i]);
	return(ret);
    }

    private static void finishTrace(String reason) {
	synchronized(LOCK) {
	    active = null;
	}
	if(reason != null && !reason.isEmpty())
	    System.err.println("[MoonNetProf] trace finished: " + reason);
    }

    private static void maybeTimeout(long nowNs, Trace trace) {
	if(trace == null)
	    return;
	if(nowNs - trace.armedNs <= DEFAULT_TIMEOUT_NS)
	    return;
	synchronized(LOCK) {
	    if(active == trace)
		active = null;
	}
	System.err.println("[MoonNetProf] timeout wid=" + trace.wid + " action1=" + trace.action1
	    + " action2=" + trace.action2 + " armedMs=" + sinceMs(nowNs, trace.armedNs));
    }

    private static void onIncoming(int type, PMessage msg) {
	if(agnosticSync != null && msg != null)
	    observeAgnosticIncoming(type, msg);
	Trace trace = active;
	if(trace == null || msg == null)
	    return;
	long nowNs = System.nanoTime();
	maybeTimeout(nowNs, trace);
	trace = active;
	if(trace == null)
	    return;
	try {
	    if(type == RMessage.RMSG_DSTWDG) {
		PMessage m = msg.clone();
		m.rewind();
		int dst = m.int32();
		if(dst != trace.wid)
		    return;
		long base = (trace.action2QueuedNs != 0L) ? trace.action2QueuedNs :
		    (trace.action1QueuedNs != 0L ? trace.action1QueuedNs : trace.armedNs);
		System.err.println(String.format(Locale.ROOT,
		    "[MoonNetProf] inbound dstwdg wid=%d dtMs=%d phase=%s note=%s",
		    dst, sinceMs(nowNs, base), (trace.action2QueuedNs != 0L ? "after-action2" : "after-arm"), trace.note));
		finishTrace("dstwdg");
		return;
	    }
	    if(type != RMessage.RMSG_WDGMSG)
		return;
	    PMessage m = msg.clone();
	    m.rewind();
	    int recvWid = m.int32();
	    String name = m.string();
	    Object[] args = m.list();
	    long base = (trace.action2QueuedNs != 0L) ? trace.action2QueuedNs :
		(trace.action1QueuedNs != 0L ? trace.action1QueuedNs : trace.armedNs);
	    if("err".equals(name)) {
		System.err.println(String.format(Locale.ROOT,
		    "[MoonNetProf] inbound err recvWid=%d dtMs=%d phase=%s args=%s note=%s",
		    recvWid, sinceMs(nowNs, base), (trace.action2QueuedNs != 0L ? "after-action2" : "after-arm"),
		    Arrays.deepToString(args), trace.note));
		finishTrace("err");
		return;
	    }
	    if(recvWid != trace.wid)
		return;
	    System.err.println(String.format(Locale.ROOT,
		"[MoonNetProf] inbound wdgmsg wid=%d name=%s dtMs=%d phase=%s args=%s note=%s",
		recvWid, name, sinceMs(nowNs, base), (trace.action2QueuedNs != 0L ? "after-action2" : "after-action1"),
		Arrays.deepToString(args), trace.note));
	    finishTrace("wdgmsg:" + name);
	} catch(Throwable t) {
	    System.err.println("[MoonNetProf] incoming parse failed: " + t);
	}
    }
}
