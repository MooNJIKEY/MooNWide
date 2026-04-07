package haven;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-client packet interception. Hooks into the message pipeline at these points:
 *
 *   1. Outgoing wdgmsg  — filter/mutate before serialisation  ({@link UI#wdgmsg});
 *      optional {@link #addOutgoingFilter}; built-in {@link Speedget} assist when
 *      {@link MoonConfig#experimentalSpeedWireAssist} (rewritten {@code set} args → server; Java only, not JNI).
 *      Optional stdout trace when {@link MoonConfig#debugOutgoingWdgmsg} ({@link #logOutgoingWdgmsg}).
 *      When {@link MoonConfig#debugErrLatencyLog}, последние исходящие сообщения копятся для корреляции с {@code err}.
 *   2. Incoming rel msgs — read-only observe after decryption ({@link #addIncomingObserver}); clone only.
 *      Серверные ошибки приходят как {@link RMessage#RMSG_WDGMSG} с {@code name=err} (отдельного {@code RMSG_ERR} нет).
 *      При {@link MoonConfig#debugErrLatencyLog} пишется {@code [MoonErrChain]} с задержкой от последнего исходящего.
 *   3. Incoming rel msgs — <strong>live</strong> transform before dispatch ({@link #addIncomingTransformer}):
 *      mutate or replace {@link PMessage} so {@link Session#handlerel} and thus UI / {@link Glob} see edited data;
 *      return {@code null} to drop the message. Does not cover {@link OCache} object deltas or map stream bytes
 *      (those use other {@link Connection} callbacks).
 *   4. Linear move display — {@link LinMove#getc} uses server line {@code s+v*t}, then {@link #applyLinLineWorld}
 *      (optional {@link LinLineAdjuster}s).
 *
 * Incoming transformers run on the connection thread; keep them fast. The game server remains authoritative;
 * editing client-side messages can desync visuals/state from reality and may violate server rules.
 */
public final class MoonPacketHook {

    private MoonPacketHook() {}

    /* Lists must appear before the static block: Java runs static initializers in source order. */
    private static final CopyOnWriteArrayList<OutgoingFilter> outFilters = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<IncomingObserver> inObservers = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<IncomingTransformer> inTransformers = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<LinLineAdjuster> linLineAdjusters = new CopyOnWriteArrayList<>();

    private static final Object ERR_CHAIN_LOCK = new Object();
    private static final Deque<OutgoingErrRecord> errOutgoingRing = new ArrayDeque<>();

    private static final class OutgoingErrRecord {
	final double t;
	final int widgetId;
	final String msg;
	final String argsSummary;
	final String widgetClass;

	OutgoingErrRecord(double t, int widgetId, String msg, String argsSummary, String widgetClass) {
	    this.t = t;
	    this.widgetId = widgetId;
	    this.msg = msg;
	    this.argsSummary = argsSummary;
	    this.widgetClass = widgetClass;
	}
    }

    static {
        addOutgoingFilter(MoonPacketHook::builtinOutgoing);
        addOutgoingFilter(MoonPacketHook::logOutgoingWdgmsg);
    }

    /** Extra Speedget indices merged into outgoing {@code set} when {@link MoonConfig#speedMultiplier} {@code > 1}. */
    private static int speedMultExtraTiers(double mult, int sgmax) {
        if (mult <= 1.0 + 1e-6 || sgmax <= 0)
            return 0;
        int t = (int) Math.floor((mult - 1.0) / 0.55 + 1e-9);
        return Utils.clip(t, 0, sgmax);
    }

    /* ─── Outgoing wdgmsg filter ─── */

    /**
     * When {@link MoonConfig#debugOutgoingWdgmsg} is true, prints each outgoing wdgmsg after prior filters
     * (e.g. Speedget rewrite): {@code [widgetId] [msg] [args]} to {@link System#out}.
     */
    private static Object[] logOutgoingWdgmsg(UI ui, int widgetId, String msg, Object[] args) {
	if(!MoonConfig.debugOutgoingWdgmsg)
	    return args;
	try {
	    System.out.println("[" + widgetId + "] [" + msg + "] " + Arrays.deepToString(args));
	} catch(Throwable ignored) {}
	return args;
    }

    public interface OutgoingFilter {
        /**
         * Intercept an outgoing widget message before it is encoded and queued for the server.
         *
         * @param ui         Active {@link UI} (for {@link UI#getwidget}); may be {@code null}.
         * @return {@code null} to drop the message (nothing is sent). Otherwise return the
         *         argument list that will be serialised — usually {@code args} after in-place
         *         edits, or a new {@code Object[]} if the arity or types must change.
         */
        Object[] filter(UI ui, int widgetId, String msg, Object[] args);
    }

    public static void addOutgoingFilter(OutgoingFilter f)    { outFilters.add(f); }
    public static void removeOutgoingFilter(OutgoingFilter f) { outFilters.remove(f); }

    /**
     * Called from {@link UI#wdgmsg} before {@link UI.Receiver#rcvmsg} and
     * {@link Session#queuemsg}. The returned array is exactly what goes to the server
     * for this wdgmsg (subject to the session wire encryption).
     *
     * @return {@code null} if a filter dropped the message; otherwise the args to serialise.
     */
    public static Object[] fireOutgoing(UI ui, int widgetId, String msg, Object[] args) {
        for (OutgoingFilter f : outFilters) {
            try {
                args = f.filter(ui, widgetId, msg, args);
                if (args == null)
                    return null;
            } catch (Exception e) {
                System.err.println("[MoonPacketHook] outgoing filter error: " + e);
            }
        }
	if(args != null)
	    recordOutgoingForErrChain(ui, widgetId, msg, args);
        return args;
    }

    private static void recordOutgoingForErrChain(UI ui, int widgetId, String msg, Object[] args) {
	if(!MoonConfig.debugErrLatencyLog)
	    return;
	double t = Utils.rtime();
	String argsSummary;
	try {
	    argsSummary = Arrays.deepToString(args);
	} catch(Throwable e) {
	    argsSummary = "<deepToString: " + e + ">";
	}
	if(argsSummary.length() > 520)
	    argsSummary = argsSummary.substring(0, 520) + "…";
	String wcls = "";
	try {
	    if(ui != null) {
		Widget w = ui.getwidget(widgetId);
		if(w != null)
		    wcls = w.getClass().getSimpleName();
	    }
	} catch(Throwable ignored) {
	}
	synchronized(ERR_CHAIN_LOCK) {
	    int cap = Utils.clip(MoonConfig.errLatencyRingSize, 8, 256);
	    while(errOutgoingRing.size() >= cap)
		errOutgoingRing.removeFirst();
	    errOutgoingRing.addLast(new OutgoingErrRecord(t, widgetId, msg, argsSummary, wcls));
	}
    }

    private static void maybeLogIncomingErr(PMessage msg) {
	PMessage m = msg.clone();
	try {
	    int recvWid = m.int32();
	    String name = m.string();
	    if(!"err".equals(name))
		return;
	    Object[] eargs;
	    try {
		eargs = m.list();
	    } catch(Throwable e) {
		System.err.println("[MoonErrChain] parse err args failed: " + e);
		return;
	    }
	    String errText;
	    try {
		errText = Arrays.deepToString(eargs);
	    } catch(Throwable e) {
		errText = "<deepToString: " + e + ">";
	    }
	    if(errText.length() > 480)
		errText = errText.substring(0, 480) + "…";
	    double now = Utils.rtime();
	    OutgoingErrRecord last = null;
	    OutgoingErrRecord lastSameRecv = null;
	    OutgoingErrRecord[] snap;
	    synchronized(ERR_CHAIN_LOCK) {
		if(!errOutgoingRing.isEmpty())
		    last = errOutgoingRing.peekLast();
		snap = errOutgoingRing.toArray(new OutgoingErrRecord[0]);
	    }
	    for(int i = snap.length - 1; i >= 0; i--) {
		if(snap[i].widgetId == recvWid) {
		    lastSameRecv = snap[i];
		    break;
		}
	    }
	    StringBuilder sb = new StringBuilder(900);
	    sb.append("[MoonErrChain] RMSG_WDGMSG name=err recvWid=").append(recvWid);
	    sb.append(" text=").append(errText);
	    if(last != null) {
		double dtMs = (now - last.t) * 1000.0;
		sb.append(String.format(Locale.ROOT, " dtLastOutMs=%.2f", dtMs));
		sb.append(" lastOut=wid=").append(last.widgetId).append(" msg=").append(last.msg);
		if(!last.widgetClass.isEmpty())
		    sb.append(" class=").append(last.widgetClass);
		sb.append(" args=").append(last.argsSummary);
	    } else {
		sb.append(" dtLastOutMs=n/a (no outgoing in ring)");
	    }
	    if(lastSameRecv != null && lastSameRecv != last) {
		double dtMs = (now - lastSameRecv.t) * 1000.0;
		sb.append(String.format(Locale.ROOT, " dtLastOutSameRecvWidMs=%.2f", dtMs));
		sb.append(" sameRecvOut=msg=").append(lastSameRecv.msg).append(" args=").append(lastSameRecv.argsSummary);
	    }
	    if(snap.length > 0) {
		sb.append(" recentOut=");
		int from = Math.max(0, snap.length - 3);
		for(int i = from; i < snap.length; i++) {
		    OutgoingErrRecord r = snap[i];
		    if(i > from)
			sb.append(" | ");
		    sb.append(String.format(Locale.ROOT, "w%d:%s~%.0fms", r.widgetId, r.msg, (now - r.t) * 1000.0));
		}
	    }
	    sb.append(" note=err is inbound only; causal wdgmsg is inferred from lastOut/recentOut, not from server.");
	    System.err.println(sb.toString());
	} catch(Throwable e) {
	    System.err.println("[MoonErrChain] decode incoming WDGMSG failed: " + e);
	}
    }

    /**
     * Some layouts bind a container id (e.g. {@link ProxyFrame}) while the real meter is a child
     * {@link Speedget}; outgoing messages use the bound id, so we search under that widget.
     */
    private static Speedget speedgetUnderBound(Widget w, int depth) {
        if (w == null || depth < 0)
            return null;
        if (w instanceof Speedget)
            return (Speedget) w;
        for (Widget c = w.child; c != null; c = c.next) {
            Speedget sg = speedgetUnderBound(c, depth - 1);
            if (sg != null)
                return sg;
        }
        return null;
    }

    /** Resolve {@link Speedget} for a bound widget id (direct or under a wrapper). */
    static Speedget speedgetForBoundWidget(UI ui, int widgetId) {
        if (ui == null)
            return null;
        Widget w = ui.getwidget(widgetId);
        if (w instanceof Speedget)
            return (Speedget) w;
        return speedgetUnderBound(w, 12);
    }

    /** Rewrites outgoing {@link Speedget} {@code set} when assist / lift prefs apply. */
    private static Object[] builtinOutgoing(UI ui, int widgetId, String msg, Object[] args) {
        try {
            if (ui == null || args == null)
                return args;
            if (!MoonConfig.experimentalSpeedWireAssist)
                return args;
            if (!"set".equals(msg) || args.length < 1)
                return args;
            double mult = MoonConfig.speedMultiplier;
            boolean multActive = mult > 1.0 + 1e-6;
            /* Speed boost skips sprint/lift; wire mult still applies when {@code > 1}. */
            if (MoonConfig.speedBoost && !multActive)
                return args;
            if (!MoonConfig.clientSpeedScale && MoonConfig.serverSpeedLift <= 0 && !multActive)
                return args;
            Widget w = ui.getwidget(widgetId);
            Speedget sg = (w instanceof Speedget) ? (Speedget) w : speedgetUnderBound(w, 12);
            if (sg == null) {
                if (MoonConfig.debugSpeedWire)
                    System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId
                        + " skip=no Speedget (bound=" + (w == null ? "null" : w.getClass().getSimpleName()) + ")");
                return args;
            }
            int s = Utils.iv(args[0]);
            if (s < 0 || s > sg.max) {
                if (MoonConfig.debugSpeedWire)
                    System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId + " skip=badUiReq req=" + s + " max=" + sg.max);
                return args;
            }

            if (s >= sg.max) {
                if (MoonConfig.debugSpeedWire)
                    System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId + " uiReq=" + s + " wire=" + s
                        + " max=" + sg.max + " note=alreadyAtWidgetMax");
                return args;
            }
            /* Slowing: always send chosen tier, never assist/mult. */
            if (s < sg.cur) {
                if (MoonConfig.debugSpeedWire)
                    System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId + " uiReq=" + s + " wire=" + s
                        + " cur=" + sg.cur + " max=" + sg.max + " note=slowdownPassthrough");
                return args;
            }

            boolean raising = (s > sg.cur);
            boolean sameTier = (s == sg.cur);
            if (sameTier && !multActive) {
                if (MoonConfig.debugSpeedWire)
                    System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId + " uiReq=" + s + " wire=" + s
                        + " cur=" + sg.cur + " max=" + sg.max + " note=sameTierNoMult");
                return args;
            }

            int target = s;
            String note = "passthrough";
            if (raising && !MoonConfig.speedBoost) {
                if (MoonConfig.clientSpeedScale && MoonConfig.clientScaleServerSprint) {
                    target = sg.max;
                    note = "sprintAssist";
                } else if (MoonConfig.serverSpeedLift > 0) {
                    target = Utils.clip(s + MoonConfig.serverSpeedLift, 0, sg.max);
                    note = "lift+" + MoonConfig.serverSpeedLift;
                }
            }

            if (multActive) {
                int extra = speedMultExtraTiers(mult, sg.max);
                if (extra > 0) {
                    int bumped = Utils.clip(target + extra, 0, sg.max);
                    if (bumped != target) {
                        target = bumped;
                        note = note + "+pktMult" + String.format(java.util.Locale.ROOT, "%.2f", mult);
                    }
                }
            }

            if (target == s) {
                if (MoonConfig.debugSpeedWire)
                    System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId + " uiReq=" + s + " wire=" + s
                        + " max=" + sg.max + " note=" + note + "(noChange)");
                return args;
            }
            Object[] out = Arrays.copyOf(args, args.length);
            out[0] = target;
            if (MoonConfig.debugSpeedWire)
                System.err.println("[MoonSpeedWire] OUT set wid=" + widgetId + " uiReq=" + s + " wire=" + target
                    + " max=" + sg.max + " note=" + note);
            return out;
        } catch (Throwable t) {
            System.err.println("[MoonPacketHook] builtinOutgoing: " + t);
            return args;
        }
    }

    /* ─── Incoming reliable-message observer ─── */

    public interface IncomingObserver {
        void observe(int type, PMessage msg);
    }

    public static void addIncomingObserver(IncomingObserver o)    { inObservers.add(o); }
    public static void removeIncomingObserver(IncomingObserver o) { inObservers.remove(o); }

    /**
     * Called from {@link Session#handlerel} for every incoming reliable message.
     * The message is a clone — observers may read it freely without affecting the
     * original dispatch.
     */
    public static void fireIncoming(int type, PMessage msg) {
	if(MoonConfig.debugErrLatencyLog && type == RMessage.RMSG_WDGMSG && msg != null)
	    maybeLogIncomingErr(msg);
        if (inObservers.isEmpty())
            return;
        for (IncomingObserver o : inObservers) {
            try {
                o.observe(type, msg);
            } catch (Exception e) {
                System.err.println("[MoonPacketHook] incoming observer error: " + e);
            }
        }
    }

    /* ─── Incoming REL transform (before UI / glob / res cache) ─── */

    /**
     * Mutate or replace a reliable message before {@link Session#handlerel} dispatches it.
     * Runs after {@link IncomingObserver} (which still sees an unmodified clone).
     */
    public interface IncomingTransformer {
        /**
         * @param sess Session receiving the message ({@link Glob}, {@link Session#resmapper}, etc.).
         * @param msg  Incoming REL payload; read cursor is at start of type-specific body. If you read from
         *             {@code msg}, call {@link PMessage#rewind()} before returning so downstream code reads
         *             from the beginning.
         * @return {@code null} to discard this message (no UI update, no {@link Glob#blob}, etc.), or the
         *         message to process — usually {@code msg} after edits, or a new {@link PMessage} with the
         *         same {@link PMessage#type} if you rebuild the buffer.
         */
        PMessage transform(Session sess, PMessage msg);
    }

    public static void addIncomingTransformer(IncomingTransformer t)    { inTransformers.add(t); }
    public static void removeIncomingTransformer(IncomingTransformer t) { inTransformers.remove(t); }

    /**
     * Apply all registered transformers in order. Used from {@link Session#handlerel}.
     *
     * @return {@code null} if any transformer dropped the message.
     */
    public static PMessage applyIncomingTransformers(Session sess, PMessage msg) {
        if (msg == null)
            return null;
        for (IncomingTransformer t : inTransformers) {
            try {
                msg = t.transform(sess, msg);
                if (msg == null)
                    return null;
            } catch (Exception e) {
                System.err.println("[MoonPacketHook] incoming transformer error: " + e);
            }
        }
        return msg;
    }

    /**
     * Rebuild {@link RMessage#RMSG_WDGMSG} for the incoming UI queue: same layout {@link RemoteUI#run} expects
     * ({@code int32} widget id, NUL-terminated name, typed argument list). Use when replacing a message after
     * {@link Message#list(java.util.function.Function)} — writing with {@code addint32}/{@code addtto} only fills
     * the write buffer; this seals it into a new {@link PMessage} with a readable {@code rbuf}.
     */
    public static PMessage buildIncomingWdgmsg(int widgetId, String name, Object[] args) {
	PMessage w = new PMessage(RMessage.RMSG_WDGMSG);
	w.addint32(widgetId);
	w.addstring(name);
	if(args != null) {
	    for(Object o : args)
		w.addtto(o);
	}
	return new PMessage(RMessage.RMSG_WDGMSG, w.fin());
    }

    /* ─── LinMove world position (after s + v*t) ─── */

    /**
     * Optional tweak of the linear-move world point {@link LinMove} would render.
     * Deviating from the server line {@code s + v*t} will usually look like rubber-banding
     * to other clients; keep {@code lineWorld} unless you know the trade-off.
     */
    public interface LinLineAdjuster {
        Coord2d adjust(Gob gob, boolean isPlayer, Coord2d lineWorld);
    }

    public static void addLinLineAdjuster(LinLineAdjuster a)    { linLineAdjusters.add(a); }
    public static void removeLinLineAdjuster(LinLineAdjuster a) { linLineAdjusters.remove(a); }

    /** Apply registered {@link LinLineAdjuster}s in order (default: identity). */
    public static Coord2d applyLinLineWorld(Gob gob, Coord2d lineWorld) {
        if (gob == null || lineWorld == null)
            return lineWorld;
        boolean pl = isPlayerGob(gob);
        Coord2d p = lineWorld;
        for (LinLineAdjuster a : linLineAdjusters) {
            try {
                p = a.adjust(gob, pl, p);
            } catch (Exception e) {
                System.err.println("[MoonPacketHook] LinLineAdjuster error: " + e);
            }
        }
        return p;
    }

    /* ─── Player gob tracking ─── */

    private static volatile long playerGobId = -1;

    public static void setPlayerGobId(long id) { playerGobId = id; }
    public static long  getPlayerGobId()       { return playerGobId; }

    public static boolean isPlayerGob(Gob g) {
        if(g == null)
            return false;
        long gid = g.glob.moonPlayerGobId;
        if(gid >= 0)
            return g.id == gid;
        return playerGobId >= 0 && g.id == playerGobId;
    }

    /**
     * Incoming gob deltas ({@link OCache} / UDP) are <em>not</em> visible to {@link MoonJniPacketHook}.
     * Scale linear-segment velocity when the OD_LINBEG delta is applied (see {@link LinMove}).
     */
    public static Coord2d scalePlayerLinSegVelocity(Gob g, Coord2d v) {
        if(g == null || v == null)
            return v;
        if(!isPlayerGob(g))
            return v;
        double m = MoonConfig.linMoveVisualSpeedMult;
        if(m <= 1.0 + 1e-9)
            return v;
        return v.mul(m);
    }

    /** Scale homing speed scalar when the OD_HOMING delta is applied (see {@link Homing}). */
    public static double scalePlayerHomingSpeed(Gob g, double v) {
        if(g == null)
            return v;
        if(!isPlayerGob(g))
            return v;
        double m = MoonConfig.linMoveVisualSpeedMult;
        if(m <= 1.0 + 1e-9)
            return v;
        return v * m;
    }

    /**
     * When {@link MoonConfig#linMoveVisualSpeedMult} changes mid-segment, rescale stored {@link LinMove#v} /
     * {@link Homing#v} so the slider has immediate effect without waiting for the next OD_LINBEG.
     */
    public static void rescalePlayerMoveVelocityForMultChange(Glob glob, double prevM, double newM) {
        if(glob == null)
            return;
        if(prevM < 1e-9)
            prevM = 1.0;
        double r = newM / prevM;
        if(Math.abs(r - 1.0) < 1e-12)
            return;
        long id = glob.moonPlayerGobId;
        if(id < 0)
            id = playerGobId;
        if(id < 0)
            return;
        Gob g = glob.oc.getgob(id);
        if(g == null || !isPlayerGob(g))
            return;
        synchronized(g) {
            LinMove lm = g.getattr(LinMove.class);
            if(lm != null)
                lm.v = lm.v.mul(r);
            Homing h = g.getattr(Homing.class);
            if(h != null)
                h.v *= r;
        }
    }
}
