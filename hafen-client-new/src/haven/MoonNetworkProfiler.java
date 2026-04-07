package haven;

import java.util.Arrays;
import java.util.Locale;

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
    private static final long DEFAULT_GAP_NS = 500_000L;
    private static final long DEFAULT_TIMEOUT_NS = 2_000_000_000L;
    private static final Object[] NO_ARGS = new Object[0];
    private static final Object LOCK = new Object();

    private static volatile boolean observerInstalled;
    private static volatile Trace active;

    private MoonNetworkProfiler() {}

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
