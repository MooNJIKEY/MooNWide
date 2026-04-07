package haven;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Experimental helpers for inventory wire stress tests (double {@code transfer}, inbound observation).
 * <p>
 * Vanilla mouse mapping ({@link InventoryItemClickPolicy}): <strong>Shift+LMB</strong> →
 * {@code transfer} with {@code n=1}; <strong>Ctrl+Shift+LMB</strong> → {@code transfer} with {@code n=-1}
 * (full stack). {@link RemoteUI} is not on the click path — it only encodes {@link UI#wdgmsg} via
 * {@link RemoteUI#rcvmsg}.
 * <p>
 * Race tactics (dev hotkeys in {@link GameUI#globtype}): <strong>U</strong> {@code transfer(-1)} → USERAGENT →
 * {@link #busyWaitNanos(long)}({@link #tacticsUPauseBetweenTransferAndTakeNs}) → {@code take(-1)}; plain <strong>U</strong>
 * = 500 000 ns, <strong>Shift+U</strong> = 1 000 000 ns; <strong>K</strong> два {@code transfer} с busy-wait между ними;
 * <strong>M</strong> {@link #stressBurstShiftHotkey} — серия transfer + focus + нарастающие паузы;
 * <strong>I</strong> dual {@code transfer(containerWid, -1)}; <strong>Shift+O</strong> split/full stack
 * (plain <strong>O</strong> остаётся для опций).
 */
public final class MoonInventoryStress {

    private MoonInventoryStress() {}

    /**
     * Сервер шлёт ошибки как {@link RMessage#RMSG_WDGMSG} с {@code name=err} (отдельного {@code RMSG_ERR} в протоколе нет).
     * Счётчик {@link #stressTransferOrdinalInBurst} сбрасывается в начале тактик с двумя {@code transfer} подряд.
     */
    private static volatile int stressTransferOrdinalInBurst;
    private static volatile int lastStressTransferOrdinalInBurst;
    private static volatile boolean stressErrSeenInBurst;
    private static volatile boolean stressErrObserverRegistered;

    private static final String ANSI_RED = "\u001b[31m";
    private static final String ANSI_RESET = "\u001b[0m";

    private static void resetStressTransferBurstForErr() {
	stressTransferOrdinalInBurst = 0;
	lastStressTransferOrdinalInBurst = 0;
	stressErrSeenInBurst = false;
    }

    private static void ensureIncomingErrObserver() {
	synchronized(MoonInventoryStress.class) {
	    if(stressErrObserverRegistered)
		return;
	    MoonPacketHook.addIncomingObserver(MoonInventoryStress::onIncomingErrWdgmsg);
	    stressErrObserverRegistered = true;
	}
    }

    private static void onIncomingErrWdgmsg(int type, PMessage msg) {
	if(type != RMessage.RMSG_WDGMSG || msg == null)
	    return;
	PMessage m = msg.clone();
	try {
	    m.rewind();
	    int recvWid = m.int32();
	    String nm = m.string();
	    if(!"err".equals(nm))
		return;
	    Object[] args = m.list();
	    String errText;
	    if(args.length > 0 && args[0] instanceof String)
		errText = (String)args[0];
	    else
		errText = Arrays.deepToString(args);
	    stressErrSeenInBurst = true;
	    boolean itemNotFound = errText.toLowerCase(Locale.ROOT).contains("item not found");
	    boolean secondTransferPacket = (lastStressTransferOrdinalInBurst == 2);
	    String line = "[RMSG_ERR] RMSG_WDGMSG name=err recvWid=" + recvWid + " text=" + errText;
	    if(itemNotFound && secondTransferPacket)
		System.err.println(ANSI_RED + line + ANSI_RESET);
	    else
		System.err.println(line);
	} catch(Throwable t) {
	    System.err.println("[RMSG_ERR] parse inbound err failed: " + t);
	}
    }

    /**
     * Если после пары {@code transfer} не пришло {@code err}, а стек визуально один — сервер мог «проглотить» дубликат;
     * подсказка: Split-Join ({@link GameUI} Shift+O).
     */
    private static void maybeHintSplitJoinIfSilentSwallow(UI ui, int gitemWidgetId) {
	if(ui == null || stressErrSeenInBurst)
	    return;
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    return;
	GItem g = (GItem)w;
	if(g.num == 1) {
	    System.err.println("[MoonStress] hint: no inbound err after stress pair; stack count=1 (server may have dropped a duplicate). Try Shift+O Split-Join.");
	}
    }

    /** Даёт времени прийти inbound {@code err} до проверки «молчаливого проглатывания». */
    private static void scheduleHintSplitJoinIfSilentSwallow(UI ui, int gitemWidgetId) {
	if(ui == null)
	    return;
	Thread t = new Thread(() -> {
	    try {
		Thread.sleep(150);
	    } catch(InterruptedException e) {
		Thread.currentThread().interrupt();
		return;
	    }
	    maybeHintSplitJoinIfSilentSwallow(ui, gitemWidgetId);
	}, "MoonStress-splitjoin-hint");
	t.setDaemon(true);
	t.start();
    }

    static {
	ensureIncomingErrObserver();
    }

    /**
     * Активное ожидание целевой длительности (нагрузка на CPU, точность ~микросекунды).
     */
    public static void busyWaitNanos(long targetNs) {
	if(targetNs <= 0)
	    return;
	long start = System.nanoTime();
	while(System.nanoTime() - start < targetNs) {
	    /* spin */
	}
    }

    /** Пауза между двумя пакетами в тактиках K/U (наносекунды), по умолчанию 0.5 ms — через {@link #busyWaitNanos(long)}. */
    public static volatile long stressKJitterNs = 500_000L;

    /**
     * Пауза (busy-wait) между {@code transfer(-1)} и {@code take(-1)} в тактике U, после {@link #queueUseragentSpacer}.
     * По умолчанию 500 000 ns; для 1 ms используйте {@code 1_000_000L} или горячую клавишу Shift+U.
     */
    public static volatile long tacticsUPauseBetweenTransferAndTakeNs = 500_000L;

    /**
     * Нарастающие паузы (нс) между шагами Burst-Shifter: после каждого {@code transfer} кроме последнего идёт
     * {@code focus} и затем busy-wait. Длина массива + 1 = число {@code transfer} (по умолчанию 5+1=6).
     */
    public static volatile long[] burstShiftPausesNs = new long[] {
	0L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L
    };

    /** Между двумя подряд идентичными {@link #queueWdgmsg} вставляется REL-спейсер (см. {@link #queueRelSpacer}). */
    public static volatile boolean stressInsertDupSpacer = true;

    private static final Object dupLock = new Object();
    private static int lastDupWid = -1;
    private static String lastDupName = null;
    private static Object[] lastDupArgs = null;

    /** Сброс детектора дубликатов (например между экспериментами). */
    public static void resetDuplicateSpacerGate() {
	synchronized(dupLock) {
	    lastDupWid = -1;
	    lastDupName = null;
	    lastDupArgs = null;
	}
    }

    private static Object[] copyArgs(Object[] args) {
	if(args == null || args.length == 0)
	    return(args);
	return(Arrays.copyOf(args, args.length));
    }

    /**
     * Дополнительный REL между двумя идентичными {@code WDGMSG} — {@link RMessage#RMSG_USERAGENT} с уникальным значением,
     * чтобы не совпадать с фильтром дубликатов на сервере.
     */
    private static void queueRelSpacer(UI ui) {
	long nano = System.nanoTime();
	System.err.println("[MoonStress] REL-SPACER nano=" + nano + " kind=USERAGENT");
	PMessage sp = new PMessage(RMessage.RMSG_USERAGENT);
	sp.addstring("moon.stress-spacer").addstring(Long.toString(nano));
	ui.sess.queuemsg(sp);
    }

    /**
     * Публичная вставка того же USERAGENT REL, что {@link #queueRelSpacer} — для тактики U между {@code transfer} и
     * {@code take}.
     */
    public static void queueUseragentSpacer(UI ui) {
	if(ui == null || ui.sess == null)
	    throw new IllegalArgumentException("ui/sess");
	queueRelSpacer(ui);
    }

    /**
     * Ставит в очередь исходящий {@link RMessage#RMSG_WDGMSG} через {@link Session#queuemsg} — тот же путь, что
     * {@link RemoteUI#rcvmsg}. Перед вторым подряд <em>полностью идентичным</em> сообщением (тот же wid/name/args)
     * может вставляться {@link #queueRelSpacer}. В лог пишется {@link System#nanoTime()} на момент постановки в очередь.
     */
    public static void queueWdgmsg(UI ui, int widgetId, String name, Object... args) {
	queueWdgmsgWithParkNanos(ui, widgetId, name, 0L, args);
    }

    /**
     * То же, но перед постановкой в очередь — {@link #busyWaitNanos(long)} (можно 0).
     * Отдельное имя, чтобы не было неоднозначности с {@code Integer} (например {@code -1}) как с {@code long}.
     */
    public static void queueWdgmsgWithParkNanos(UI ui, int widgetId, String name, long parkNanosBefore,
	Object... args) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	if(ui.sess == null)
	    throw new IllegalArgumentException("no Session");
	if(parkNanosBefore > 0)
	    busyWaitNanos(parkNanosBefore);
	synchronized(dupLock) {
	    if("transfer".equals(name)) {
		stressTransferOrdinalInBurst++;
		lastStressTransferOrdinalInBurst = stressTransferOrdinalInBurst;
	    }
	    long nano = System.nanoTime();
	    if(stressInsertDupSpacer && lastDupName != null && lastDupWid == widgetId && lastDupName.equals(name)
		&& Arrays.deepEquals(lastDupArgs, args))
		queueRelSpacer(ui);
	    System.err.println("[MoonStress] OUT nano=" + nano + " wid=" + widgetId + " name=" + name + " args="
		+ Arrays.deepToString(args));
	    PMessage msg = new PMessage(RMessage.RMSG_WDGMSG);
	    msg.addint32(widgetId);
	    msg.addstring(name);
	    msg.addlist(args);
	    MoonInventoryWireDebug.maybeLogItem(name, args);
	    ui.sess.queuemsg(msg);
	    lastDupWid = widgetId;
	    lastDupName = name;
	    lastDupArgs = copyArgs(args);
	}
    }

    /**
     * «Мусорный» {@code wdgmsg("focus")} на родителе {@link GItem} (или {@link UI#root}) — для Burst-Shifter и др.
     */
    private static void queueJunkFocusParentOfGItem(UI ui, GItem g) {
	if(ui == null || ui.sess == null || g == null)
	    return;
	Widget p = g.parent;
	while(p != null && ui.widgetid(p) < 0)
	    p = p.parent;
	int wid = (p != null) ? ui.widgetid(p) : ui.widgetid(ui.root);
	if(wid < 0)
	    return;
	queueWdgmsg(ui, wid, "focus");
    }

    /**
     * Transfer-Hand-Swap: {@code transfer(-1)} (в сундук) → {@link #queueUseragentSpacer} →
     * {@link #busyWaitNanos(long)} на {@link #tacticsUPauseBetweenTransferAndTakeNs} (или {@code pauseNsOverride}) →
     * {@code take(-1)} (в руку). Гипотеза: предмет «уезжает» в хранилище, пауза + USERAGENT, затем take в руку.
     *
     * @param pauseNsOverride если {@code >= 0}, подставляется вместо {@link #tacticsUPauseBetweenTransferAndTakeNs}
     *            (например {@code 1_000_000L} для Shift+U).
     */
    public static void tacticsTransferHandSwap(UI ui, int gitemWidgetId, long pauseNsOverride) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    throw new IllegalArgumentException("widget " + gitemWidgetId + " is not a GItem: " + w);
	long pauseNs = Math.max(0L, pauseNsOverride >= 0 ? pauseNsOverride : tacticsUPauseBetweenTransferAndTakeNs);
	System.err.println("[RACE-CONDITION] Executing Tactic [U] for wid=" + gitemWidgetId + " pauseNs=" + pauseNs
	    + " (transfer→USERAGENT→busy-wait→take).");
	resetStressTransferBurstForErr();
	long prev = System.nanoTime();
	queueWdgmsg(ui, gitemWidgetId, "transfer", Integer.valueOf(-1));
	long now = System.nanoTime();
	System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (enqueue transfer)");
	prev = now;
	queueUseragentSpacer(ui);
	now = System.nanoTime();
	System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (enqueue USERAGENT)");
	prev = now;
	if(pauseNs > 0) {
	    busyWaitNanos(pauseNs);
	    now = System.nanoTime();
	    System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (busy-wait U; targetNs=" + pauseNs + ")");
	    prev = now;
	}
	queueWdgmsg(ui, gitemWidgetId, "take", Integer.valueOf(-1));
	now = System.nanoTime();
	System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (enqueue take)");
    }

    /** То же, пауза из {@link #tacticsUPauseBetweenTransferAndTakeNs} (по умолчанию 500 000 ns). */
    public static void tacticsTransferHandSwap(UI ui, int gitemWidgetId) {
	tacticsTransferHandSwap(ui, gitemWidgetId, -1L);
    }

    /**
     * Cross-Window Transfer: два {@code transfer(targetWid, -1)} подряд с разными {@code wid} открытых контейнеров.
     */
    public static void tacticsCrossWindowTransfer(UI ui, int gitemWidgetId, int target1Wid, int target2Wid) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    throw new IllegalArgumentException("widget " + gitemWidgetId + " is not a GItem: " + w);
	if(target1Wid < 0 || target2Wid < 0)
	    throw new IllegalArgumentException("target wids must be >= 0");
	System.err.println("[RACE-CONDITION] Executing Tactic [I] for wid=" + gitemWidgetId + ".");
	resetStressTransferBurstForErr();
	queueWdgmsg(ui, gitemWidgetId, "transfer", Integer.valueOf(target1Wid), Integer.valueOf(-1));
	queueWdgmsg(ui, gitemWidgetId, "transfer", Integer.valueOf(target2Wid), Integer.valueOf(-1));
	scheduleHintSplitJoinIfSilentSwallow(ui, gitemWidgetId);
    }

    /**
     * Split-Join Race: {@code transfer(slot, 1)} и сразу {@code transfer(slot2, -1)} с разными координатами в
     * пределах {@link WItem}.
     */
    public static void tacticsSplitJoinRace(UI ui, int gitemWidgetId, Coord slotOneUnit, Coord slotFullStack) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    throw new IllegalArgumentException("widget " + gitemWidgetId + " is not a GItem: " + w);
	System.err.println("[RACE-CONDITION] Executing Tactic [O] for wid=" + gitemWidgetId + ".");
	resetStressTransferBurstForErr();
	queueWdgmsg(ui, gitemWidgetId, "transfer", slotOneUnit, Integer.valueOf(1));
	queueWdgmsg(ui, gitemWidgetId, "transfer", slotFullStack, Integer.valueOf(-1));
	scheduleHintSplitJoinIfSilentSwallow(ui, gitemWidgetId);
    }

    private static Coord alternateSlotInWItem(WItem wi, Coord base) {
	if(wi == null)
	    return(base);
	int mx = Math.max(0, wi.sz.x - 1);
	int my = Math.max(0, wi.sz.y - 1);
	if(base.x < mx)
	    return(Coord.of(base.x + 1, base.y));
	if(base.y < my)
	    return(Coord.of(base.x, base.y + 1));
	return(Coord.of(0, 0));
    }

    /**
     * Пауза между первым {@code transfer} и вторым {@code drop} в {@link #tacticsL} (мс). Для
     * {@link RemoteUI} второй пакет уходит из отдельного потока после {@link Thread#sleep(long)}.
     */
    public static volatile int tacticsLGapMs = 2;

    /**
     * Два раза подряд одинаковый {@code transfer(slot, n)} через {@link #queueWdgmsg} (если есть
     * {@link Session} — между ними при {@link #stressInsertDupSpacer} вставляется REL-спейсер (см. тактику K).
     * Иначе локальный {@link GItem#wdgmsg}.
     * <p>
     * REL: каждое сообщение в очереди получает seq; при спейсере между дубликатами — три REL на два transfer.
     * Между первым и вторым {@code transfer} — {@link #busyWaitNanos(long)} на {@link #stressKJitterNs}.
     * Перед парой вызывается {@link #resetDuplicateSpacerGate()}, чтобы лишний спейсер не вставлялся из-за
     * предыдущего нажатия K по тому же слоту.
     *
     * @param gitemWidgetId {@link UI#widgetid} for the {@link GItem} (not {@link WItem} wrapper id)
     * @param slot          click coordinate in {@link GItem} space (e.g. from {@link WItem#c} or mouse)
     */
    public static void stressTransfer(UI ui, int gitemWidgetId, Coord slot, int n) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    throw new IllegalArgumentException("widget " + gitemWidgetId + " is not a GItem: " + w);
	Integer nn = Integer.valueOf(n);
	if(ui.sess != null) {
	    resetDuplicateSpacerGate();
	    resetStressTransferBurstForErr();
	    long prev = System.nanoTime();
	    queueWdgmsg(ui, gitemWidgetId, "transfer", slot, nn);
	    long now = System.nanoTime();
	    System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (time to enqueue transfer #1 only)");
	    prev = now;
	    long jitter = Math.max(0L, stressKJitterNs);
	    if(jitter > 0) {
		busyWaitNanos(jitter);
		now = System.nanoTime();
		System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (busy-wait K; targetNs=" + jitter + ")");
		prev = now;
	    }
	    queueWdgmsg(ui, gitemWidgetId, "transfer", slot, nn);
	    now = System.nanoTime();
	    System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns (time to enqueue transfer #2 incl. dup USERAGENT)");
	    scheduleHintSplitJoinIfSilentSwallow(ui, gitemWidgetId);
	} else {
	    ((GItem) w).wdgmsg("transfer", slot, n);
	    ((GItem) w).wdgmsg("transfer", slot, n);
	}
    }

    /**
     * Tactic L — только сначала {@code transfer(slot, -1)}, затем {@link Session#wakeConnection},
     * пауза {@link #tacticsLGapMs}, затем {@code drop(slot, -1)}. REL второй сообщения не ставится в очередь
     * сразу: даём I/O-потоку время уйти в сеть с первым пакетом; при {@link RemoteUI} {@code drop} шлётся из
     * фонового потока после sleep (очередь {@link Session#queuemsg} потокобезопасна).
     */
    public static void tacticsL(UI ui, int gitemWidgetId, Coord slot) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    throw new IllegalArgumentException("widget " + gitemWidgetId + " is not a GItem: " + w);
	System.err.println("[STRESS] Out: transfer(" + slot + ", -1) for wid=" + gitemWidgetId + ".");
	if(ui.rcvr instanceof RemoteUI) {
	    RemoteUI r = (RemoteUI) ui.rcvr;
	    r.rcvmsg(gitemWidgetId, "transfer", slot, Integer.valueOf(-1));
	    if(ui.sess != null) {
		ui.sess.wakeConnection();
		System.err.println("[STRESS] Gap: wakeConnection() after transfer (nudge I/O)");
	    }
	    final int gap = Math.max(0, tacticsLGapMs);
	    final RemoteUI rr = r;
	    final int gid = gitemWidgetId;
	    final Coord sl = slot;
	    System.err.println("[STRESS] Gap: async thread sleep " + gap + "ms then drop");
	    Thread t = new Thread(() -> {
		try {
		    if(gap > 0)
			Thread.sleep(gap);
		} catch(InterruptedException e) {
		    Thread.currentThread().interrupt();
		    return;
		}
		System.err.println("[STRESS] Out: drop(" + sl + ", -1) for wid=" + gid + ".");
		rr.rcvmsg(gid, "drop", sl, Integer.valueOf(-1));
	    }, "MoonInventoryStress-tacticsL");
	    t.setDaemon(true);
	    t.start();
	} else {
	    ((GItem) w).wdgmsg("transfer", slot, -1);
	    if(ui.sess != null) {
		ui.sess.wakeConnection();
		System.err.println("[STRESS] Gap: wakeConnection() after transfer");
	    }
	    int gap = Math.max(0, tacticsLGapMs);
	    System.err.println("[STRESS] Gap: sleep " + gap + "ms on caller thread before drop");
	    try {
		if(gap > 0)
		    Thread.sleep(gap);
	    } catch(InterruptedException e) {
		Thread.currentThread().interrupt();
		return;
	    }
	    System.err.println("[STRESS] Out: drop(" + slot + ", -1) for wid=" + gitemWidgetId + ".");
	    ((GItem) w).wdgmsg("drop", slot, -1);
	}
    }

    /**
     * Tactic P — {@code transfer(slot, 1)} затем {@code transfer(slot, -1)} на том же {@code slot}.
     */
    public static void tacticsP(UI ui, int gitemWidgetId, Coord slot) {
	if(ui == null)
	    throw new IllegalArgumentException("ui");
	Widget w = ui.getwidget(gitemWidgetId);
	if(!(w instanceof GItem))
	    throw new IllegalArgumentException("widget " + gitemWidgetId + " is not a GItem: " + w);
	System.err.println("[STRESS] Out: transfer(" + slot + ", 1) for wid=" + gitemWidgetId + ".");
	if(ui.sess != null) {
	    resetStressTransferBurstForErr();
	    queueWdgmsg(ui, gitemWidgetId, "transfer", slot, Integer.valueOf(1));
	} else {
	    ((GItem) w).wdgmsg("transfer", slot, 1);
	}
	System.err.println("[STRESS] Out: transfer(" + slot + ", -1) for wid=" + gitemWidgetId + ".");
	if(ui.sess != null) {
	    queueWdgmsg(ui, gitemWidgetId, "transfer", slot, Integer.valueOf(-1));
	    scheduleHintSplitJoinIfSilentSwallow(ui, gitemWidgetId);
	} else {
	    ((GItem) w).wdgmsg("transfer", slot, -1);
	}
    }

    /**
     * @return {@code after - before} mod 65536 — expect {@code 3} when {@link #stressInsertDupSpacer} inserts
     * USERAGENT between two identical {@link #stressTransfer} transfers, else {@code 2}.
     */
    public static int relSeqCursorDeltaAfterStressTransfer(Session sess, UI ui, int gitemWidgetId, Coord slot, int n) {
	if(sess == null || sess.conn == null)
	    throw new IllegalArgumentException("sess");
	int before = sess.conn.debugRelSeqCursor();
	stressTransfer(ui, gitemWidgetId, slot, n);
	int after = sess.conn.debugRelSeqCursor();
	return((after - before) & 0xffff);
    }

    /**
     * Deepest visible widget under {@code c} (root coordinates), same traversal as
     * {@link Widget.PointerEvent} propagation ({@code lchild}→{@code prev} z-order).
     */
    private static Widget deepestWidgetAt(Widget from, Coord c) {
	if(from == null)
	    return(null);
	for(Widget wdg = from.lchild; wdg != null; wdg = wdg.prev) {
	    if(!wdg.visible())
		continue;
	    Coord cc = from.xlate(wdg.c, true);
	    if(!c.isect(cc, wdg.sz))
		continue;
	    Widget inner = deepestWidgetAt(wdg, c.sub(cc));
	    if(inner != null)
		return(inner);
	    return(wdg);
	}
	return(null);
    }

    /**
     * Resolves the {@link GItem} under the pointer using {@link UI#mc} (hit-test; {@link UI#mouseon} is not
     * maintained in this client). Walks parents for {@link WItem} / {@link GItem}.
     *
     * @return {@code null} if nothing inventory-like is under the cursor
     */
    public static GItem gitemUnderCursor(UI ui) {
	if(ui == null || ui.root == null)
	    return(null);
	Widget w = deepestWidgetAt(ui.root, ui.mc);
	for(; w != null; w = w.parent) {
	    if(w instanceof GItem)
		return((GItem) w);
	    if(w instanceof WItem)
		return(((WItem) w).item);
	}
	return(null);
    }

    /**
     * {@link WItem} для {@code g} по позиции курсора: цепочка hit-test, иначе {@link Inventory#wmap} /
     * {@link Equipory#wmap} (как ванильный {@code ev.c} для {@link MoonInventoryWireDebug}).
     */
    private static WItem witemForStressHover(GItem g, UI ui) {
	if(ui == null || g == null)
	    return(null);
	Widget hit = deepestWidgetAt(ui.root, ui.mc);
	for(Widget w = hit; w != null; w = w.parent) {
	    if(w instanceof WItem) {
		WItem wi = (WItem) w;
		if(wi.item == g)
		    return(wi);
	    }
	}
	Widget p = g.parent;
	if(p instanceof Inventory)
	    return(((Inventory)p).wmap.get(g));
	if(p instanceof Equipory) {
	    Collection<WItem> c = ((Equipory)p).wmap.get(g);
	    if(c == null || c.isEmpty())
		return(null);
	    WItem any = null;
	    for(WItem wi : c) {
		if(ui.mc.isect(wi.rootpos(), wi.sz))
		    return(wi);
		any = wi;
	    }
	    return(any);
	}
	return(null);
    }

    /**
     * Координаты в пространстве {@link WItem} (как {@link InventoryItemClickPolicy}). Без реального hover —
     * {@link Coord#z}.
     */
    public static Coord stressSlotCoord(GItem g, UI ui, boolean fromHover) {
	if(!fromHover || g == null || ui == null)
	    return(Coord.z);
	WItem wi = witemForStressHover(g, ui);
	if(wi == null)
	    return(Coord.z);
	Coord c = wi.rootxlate(ui.mc);
	int mx = Math.max(0, wi.sz.x - 1);
	int my = Math.max(0, wi.sz.y - 1);
	return(Coord.of(Utils.clip(c.x, 0, mx), Utils.clip(c.y, 0, my)));
    }

    /**
     * {@link #stressTransfer(UI, int, Coord, int)} для предмета под курсором; слот — {@link #stressSlotCoord}.
     */
    public static void stressTransferHovered(UI ui, int n) {
	GItem it = gitemUnderCursor(ui);
	if(it == null)
	    throw new IllegalStateException("no GItem under cursor");
	int id = ui.widgetid(it);
	if(id < 0)
	    throw new IllegalStateException("GItem not bound to UI id");
	Coord slot = stressSlotCoord(it, ui, true);
	stressTransfer(ui, id, slot, n);
    }

    /**
     * When true, {@link #stressTransferHotkey} may pick the first {@link GItem} in {@link GameUI#maininv}
     * if nothing is hovered (arbitrary order — unsafe). Default false: hover required.
     */
    public static boolean stressHotkeyAllowMaininvFallback = false;

    /** Same targeting as stress hotkeys: hover, optional {@link #stressHotkeyAllowMaininvFallback}. */
    private static GItem stressHotkeyTargetItem(GameUI gui) {
	if(gui == null || gui.ui == null)
	    return(null);
	UI ui = gui.ui;
	GItem it = gitemUnderCursor(ui);
	if(it == null && stressHotkeyAllowMaininvFallback && gui.maininv != null) {
	    for(Map.Entry<GItem, WItem> e : gui.maininv.wmap.entrySet()) {
		it = e.getKey();
		break;
	    }
	}
	return(it);
    }

    /**
     * Hotkey: prefers {@link #gitemUnderCursor}; optional main-inv fallback only if
     * {@link #stressHotkeyAllowMaininvFallback} is true. Double {@code transfer(-1)} is aggressive — target
     * the slot you mean; random fallback can move the wrong stacks and confuse the server.
     */
    public static void stressTransferHotkey(GameUI gui, int n) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] stressTransferHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem hover = gitemUnderCursor(ui);
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot to test (maininv fallback off). "
		+ "Set stressHotkeyAllowMaininvFallback=true to restore old behavior.");
	    return;
	}
	boolean fromHover = (hover != null);
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	Coord slot = stressSlotCoord(it, ui, fromHover);
	System.out.println("[MoonInventoryStress] transfer x2 -> wid=" + id + " slot=" + slot + " src="
	    + (fromHover ? "hover" : "maininv-fallback"));
	stressTransfer(ui, id, slot, n);
    }

    /** Plain {@code L}: {@link #tacticsL} на слоте под курсором ({@link #stressSlotCoord}). */
    public static void stressTransferDropHotkey(GameUI gui) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] stressTransferDropHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot (transfer+drop). "
		+ "Set stressHotkeyAllowMaininvFallback=true for maininv fallback.");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	boolean fromHover = (gitemUnderCursor(ui) != null);
	tacticsL(ui, id, stressSlotCoord(it, ui, fromHover));
    }

    /** Plain {@code P}: {@link #tacticsP} на слоте под курсором ({@link #stressSlotCoord}). */
    public static void stressSplitTransferHotkey(GameUI gui) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] stressSplitTransferHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot (split-transfer). "
		+ "Set stressHotkeyAllowMaininvFallback=true for maininv fallback.");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	boolean fromHover = (gitemUnderCursor(ui) != null);
	tacticsP(ui, id, stressSlotCoord(it, ui, fromHover));
    }

    /**
     * Burst-Shifter (hotkey {@code M}): {@code burstShiftPausesNs.length + 1} раза {@code transfer(slot, n)}
     * с {@code focus} и busy-wait между шагами — подбор тайминга «моргания» предмета.
     */
    public static void stressBurstShiftHotkey(GameUI gui, int n) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] stressBurstShiftHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot (burst-shift). "
		+ "Set stressHotkeyAllowMaininvFallback=true for maininv fallback.");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	if(ui.sess == null) {
	    System.err.println("[MoonInventoryStress] no Session");
	    return;
	}
	boolean fromHover = (gitemUnderCursor(ui) != null);
	Coord slot = stressSlotCoord(it, ui, fromHover);
	Integer nn = Integer.valueOf(n);
	long[] pauses = burstShiftPausesNs;
	if(pauses == null || pauses.length == 0) {
	    System.err.println("[MoonInventoryStress] burstShiftPausesNs empty");
	    return;
	}
	int nXfer = pauses.length + 1;
	System.err.println("[BURST-SHIFT] transfers=" + nXfer + " pauses(ns)=" + Arrays.toString(pauses));
	resetDuplicateSpacerGate();
	resetStressTransferBurstForErr();
	long prev = System.nanoTime();
	for(int i = 0; i < nXfer; i++) {
	    if(i > 0) {
		queueJunkFocusParentOfGItem(ui, it);
		long now = System.nanoTime();
		System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns between packets (enqueue focus before transfer #"
		    + (i + 1) + ")");
		prev = now;
		long pauseNs = Math.max(0L, pauses[i - 1]);
		if(pauseNs > 0)
		    busyWaitNanos(pauseNs);
		now = System.nanoTime();
		System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns between packets (busy-wait burst #" + i + ")");
		prev = now;
	    }
	    queueWdgmsg(ui, id, "transfer", slot, nn);
	    long now = System.nanoTime();
	    System.err.println("[NANO-TEST] Delta: " + (now - prev) + " ns between packets (transfer #" + (i + 1) + ")");
	    prev = now;
	}
    }

    /**
     * Hotkey U — {@link #tacticsTransferHandSwap(UI, int, long)}; {@code pauseNsOverride} {@code < 0} →
     * {@link #tacticsUPauseBetweenTransferAndTakeNs}.
     */
    public static void tacticsTransferHandSwapHotkey(GameUI gui, long pauseNsOverride) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] tacticsTransferHandSwapHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot (transfer-hand-swap). "
		+ "Set stressHotkeyAllowMaininvFallback=true for maininv fallback.");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	if(ui.sess == null) {
	    System.err.println("[MoonInventoryStress] no Session");
	    return;
	}
	try {
	    tacticsTransferHandSwap(ui, id, pauseNsOverride);
	} catch(Throwable t) {
	    System.err.println("[MoonInventoryStress] tacticsTransferHandSwap: " + t);
	}
    }

    /** Hotkey U — пауза из {@link #tacticsUPauseBetweenTransferAndTakeNs} (500 000 ns по умолчанию). */
    public static void tacticsTransferHandSwapHotkey(GameUI gui) {
	tacticsTransferHandSwapHotkey(gui, -1L);
    }

    /** Hotkey I — {@link #tacticsCrossWindowTransfer(UI, int, int, int)}; нужны два открытых контейнера. */
    public static void tacticsCrossWindowTransferHotkey(GameUI gui) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] tacticsCrossWindowTransferHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot (cross-window transfer). "
		+ "Open two containers (e.g. chest + cupboard).");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	if(ui.sess == null) {
	    System.err.println("[MoonInventoryStress] no Session");
	    return;
	}
	int[] tw = findTwoContainerWindowIds(gui);
	if(tw[0] < 0 || tw[1] < 0) {
	    System.err.println("[MoonInventoryStress] need two open storage windows (chest, cupboard, …). "
		+ "found=" + Arrays.toString(tw));
	    return;
	}
	try {
	    tacticsCrossWindowTransfer(ui, id, tw[0], tw[1]);
	} catch(Throwable t) {
	    System.err.println("[MoonInventoryStress] tacticsCrossWindowTransfer: " + t);
	}
    }

    /**
     * Hotkey O — {@link #tacticsSplitJoinRace(UI, int, Coord, Coord)}; разные координаты слота в пределах
     * {@link WItem}.
     */
    public static void tacticsSplitJoinRaceHotkey(GameUI gui) {
	if(gui == null || gui.ui == null) {
	    System.err.println("[MoonInventoryStress] tacticsSplitJoinRaceHotkey: no GameUI/ui");
	    return;
	}
	UI ui = gui.ui;
	GItem it = stressHotkeyTargetItem(gui);
	if(it == null) {
	    System.err.println("[MoonInventoryStress] hover the slot (split-join race). "
		+ "Set stressHotkeyAllowMaininvFallback=true for maininv fallback.");
	    return;
	}
	int id = ui.widgetid(it);
	if(id < 0) {
	    System.err.println("[MoonInventoryStress] GItem not bound to UI id");
	    return;
	}
	if(ui.sess == null) {
	    System.err.println("[MoonInventoryStress] no Session");
	    return;
	}
	boolean fromHover = (gitemUnderCursor(ui) != null);
	Coord base = stressSlotCoord(it, ui, fromHover);
	WItem wi = witemForStressHover(it, ui);
	Coord alt = alternateSlotInWItem(wi, base);
	try {
	    tacticsSplitJoinRace(ui, id, base, alt);
	} catch(Throwable t) {
	    System.err.println("[MoonInventoryStress] tacticsSplitJoinRace: " + t);
	}
    }

    /**
     * Два разных {@link Window} id с {@link Inventory} (не {@link GameUI#maininv}), сначала с эвристикой заголовка,
     * иначе без неё — как {@link #findContainerId(GameUI)}.
     */
    public static int[] findTwoContainerWindowIds(GameUI gui) {
	int[] out = new int[]{-1, -1};
	if(gui == null || gui.ui == null)
	    return(out);
	List<Integer> ids = new ArrayList<>();
	for(Widget w = gui.child; w != null; w = w.next)
	    collectStorageWindowIds(gui, w, ids, true);
	if(ids.size() < 2) {
	    ids.clear();
	    for(Widget w = gui.child; w != null; w = w.next)
		collectStorageWindowIds(gui, w, ids, false);
	}
	if(ids.size() >= 2) {
	    out[0] = ids.get(0);
	    out[1] = ids.get(1);
	}
	return(out);
    }

    private static void collectStorageWindowIds(GameUI gui, Widget w, List<Integer> out, boolean titleHeuristic) {
	if(w instanceof Window) {
	    Window win = (Window) w;
	    if(win.visible()) {
		Inventory inv = firstInventoryDesc(win);
		if(inv != null && inv != gui.maininv
		    && (!titleHeuristic || isLikelyStorageWindow(win)))
		    out.add(gui.ui.widgetid(win));
	    }
	}
	for(Widget c = w.child; c != null; c = c.next)
	    collectStorageWindowIds(gui, c, out, titleHeuristic);
    }

    /**
     * Logs every inbound {@link RMessage#RMSG_WDGMSG} (and optionally {@link RMessage#RMSG_NEWWDG}) on the
     * connection thread. Use to correlate double {@code transfer} with server-driven {@code uimsg} replies
     * (there is no separate application-level "ACK" type — look for widget updates, {@code err}, etc.).
     */
    public static MoonPacketHook.IncomingObserver registerWdgmsgTrace(Session sess) {
	if(sess == null)
	    throw new IllegalArgumentException("sess");
	MoonPacketHook.IncomingObserver o = (type, msg) -> {
	    if(type != RMessage.RMSG_WDGMSG && type != RMessage.RMSG_NEWWDG)
		return;
	    PMessage c = msg.clone();
	    c.rewind();
	    try {
		if(type == RMessage.RMSG_WDGMSG) {
		    int wid = c.int32();
		    String nm = c.string();
		    Object[] args = c.list(sess.resmapper);
		    System.err.println("[MoonInventoryStress IN] RMSG_WDGMSG id=" + wid + " name=" + nm
			+ " args=" + Arrays.deepToString(args));
		} else {
		    int wid = c.int32();
		    String wtype = c.string();
		    System.err.println("[MoonInventoryStress IN] RMSG_NEWWDG id=" + wid + " type=" + wtype + " …");
		}
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress IN] parse error: " + t);
	    }
	};
	MoonPacketHook.addIncomingObserver(o);
	return o;
    }

    public static void unregisterWdgmsgTrace(MoonPacketHook.IncomingObserver o) {
	if(o != null)
	    MoonPacketHook.removeIncomingObserver(o);
    }

    /**
     * {@link UI#widgetid} of a visible {@link Window} that contains an {@link Inventory} other than
     * {@link GameUI#maininv} (typical “misc” storage: chest, cupboard, etc.).
     * <p>
     * This is the <em>window</em> id, not a {@link GItem} id — use for logging or pairing with UI; for
     * {@link #stressTransfer} you still need the {@link GItem} id of the slot being transferred.
     *
     * @return {@code -1} if none found
     */
    public static int findContainerId(GameUI gui) {
	if(gui == null || gui.ui == null)
	    return(-1);
	int id = findContainerId0(gui, true);
	if(id >= 0)
	    return(id);
	return(findContainerId0(gui, false));
    }

    private static int findContainerId0(GameUI gui, boolean titleHeuristic) {
	for(Widget w = gui.child; w != null; w = w.next) {
	    int id = findStorageWindowId(gui, w, titleHeuristic);
	    if(id >= 0)
		return(id);
	}
	return(-1);
    }

    private static int findStorageWindowId(GameUI gui, Widget w, boolean titleHeuristic) {
	if(w instanceof Window) {
	    Window win = (Window) w;
	    if(win.visible()) {
		Inventory inv = firstInventoryDesc(win);
		if(inv != null && inv != gui.maininv
		    && (!titleHeuristic || isLikelyStorageWindow(win)))
		    return(gui.ui.widgetid(win));
	    }
	}
	for(Widget c = w.child; c != null; c = c.next) {
	    int id = findStorageWindowId(gui, c, titleHeuristic);
	    if(id >= 0)
		return(id);
	}
	return(-1);
    }

    private static Inventory firstInventoryDesc(Widget w) {
	if(w instanceof Inventory)
	    return((Inventory) w);
	for(Widget c = w.child; c != null; c = c.next) {
	    Inventory inv = firstInventoryDesc(c);
	    if(inv != null)
		return(inv);
	}
	return(null);
    }

    /** Reduces false positives (e.g. crafting) by title heuristics; extend if needed. */
    private static boolean isLikelyStorageWindow(Window win) {
	if(win.cap == null)
	    return(true);
	String s = win.cap.toLowerCase(Locale.ROOT);
	return(s.contains("chest") || s.contains("cupboard") || s.contains("cabinet")
	    || s.contains("locker") || s.contains("cistern") || s.contains("bin")
	    || s.contains("crate") || s.contains("barrel") || s.contains("shed")
	    || s.contains("rack") || s.contains("stockpile") || s.contains("wagon")
	    || s.contains("шкаф") || s.contains("сундук"));
    }
}
