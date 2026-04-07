package haven;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Отладка «невидимых» на клиенте {@link Gob} в {@link OCache} и тест «слепого» клика по gob id
 * (сервер может ответить {@code uimsg("err", …)} если статов не хватает — это не обход, а проверка).
 * <p>
 * Поток объектов: сервер шлёт UDP-дельты в {@link Glob}, {@link OCache} добавляет/обновляет {@link Gob}
 * (см. {@code OD_RES} и др.). Клиент не хранит отдельный флаг «Perception» — если gob есть в кэше,
 * у него есть позиция; видимость в 3D может отличаться от политики сервера на клик.
 */
public final class MoonBlindCollect {

    private static final ConcurrentLinkedQueue<String> incomingErrTexts = new ConcurrentLinkedQueue<>();
    private static volatile boolean errTapRegistered;

    private MoonBlindCollect() {}

    private static void ensureErrTap() {
	if(errTapRegistered)
	    return;
	synchronized(MoonBlindCollect.class) {
	    if(errTapRegistered)
		return;
	    MoonPacketHook.addIncomingObserver(MoonBlindCollect::onIncomingRelForErr);
	    errTapRegistered = true;
	}
    }

    private static void onIncomingRelForErr(int type, PMessage msg) {
	if(type != RMessage.RMSG_WDGMSG || msg == null)
	    return;
	PMessage m = msg.clone();
	try {
	    m.int32();
	    String name = m.string();
	    if(!"err".equals(name))
		return;
	    Object[] args = m.list();
	    if(args.length > 0 && args[0] instanceof String)
		incomingErrTexts.offer((String)args[0]);
	    else
		incomingErrTexts.offer(Arrays.deepToString(args));
	} catch(Throwable ignored) {
	}
    }

    private static void drainErrQueue() {
	while(incomingErrTexts.poll() != null) {
	}
    }

    private static String pollErrAfter(long waitMs) {
	long end = System.currentTimeMillis() + waitMs;
	while(System.currentTimeMillis() < end) {
	    String e = incomingErrTexts.poll();
	    if(e != null)
		return(e);
	    try {
		Thread.sleep(8);
	    } catch(InterruptedException ignored) {
		Thread.currentThread().interrupt();
		return(null);
	    }
	}
	return(null);
    }

    /**
     * Обходит {@link OCache#gobAction} и печатает в stderr gob, у которых подозрительное состояние
     * drawable (нет модели, загрузка, virtual).
     */
    public static void dumpSuspiciousGobs(GameUI gui) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null) {
	    System.err.println("[MoonBlind] no GameUI/map/session");
	    return;
	}
	OCache oc = gui.ui.sess.glob.oc;
	MapView mv = gui.map;
	ArrayList<Gob> snapshot = new ArrayList<>();
	synchronized(oc) {
	    oc.gobAction(snapshot::add);
	}
	int n = 0;
	for(Gob g : snapshot) {
	    if(g == null || g.removed)
		continue;
	    String res = MoonGobDump.resname(g);
	    String reasons = describeSuspicious(g);
	    if(reasons == null)
		continue;
	    boolean mapH = mv.isHidden(g);
	    System.err.println("[MoonBlind][suspicious] id=" + g.id + " res=" + res + " virtual=" + g.virtual
		+ " mapHidden=" + mapH + " " + reasons);
	    n++;
	}
	System.err.println("[MoonBlind][suspicious] total " + n + " / " + snapshot.size());
    }

    private static String describeSuspicious(Gob g) {
	Drawable d = g.getattr(Drawable.class);
	if(d == null)
	    return(g.virtual ? "noDrawable+virtual" : "noDrawable");
	try {
	    Resource r = d.getres();
	    if(r == null || r.name == null || r.name.isEmpty())
		return("emptyDrawableRes");
	} catch(Loading e) {
	    return("drawableLoading");
	} catch(Exception e) {
	    return("drawable:" + e.getClass().getSimpleName());
	}
	if(g.virtual)
	    return("virtual");
	return(null);
    }

    /**
     * Для каждого gob, чьё имя ресурса содержит {@code targetResName} (без учёта регистра), шлёт
     * синтетический ЛКМ-клик ({@code button=1}) и ждёт до ~450 ms входящего {@code err} с сервера.
     */
    public static void blindCollect(GameUI gui, String targetResName) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null) {
	    System.err.println("[MoonBlind] no GameUI/map/session");
	    return;
	}
	if(targetResName == null || targetResName.isEmpty()) {
	    System.err.println("[MoonBlind] empty target");
	    return;
	}
	ensureErrTap();
	String needle = targetResName.toLowerCase(Locale.ROOT);
	OCache oc = gui.ui.sess.glob.oc;
	ArrayList<Gob> snapshot = new ArrayList<>();
	synchronized(oc) {
	    oc.gobAction(snapshot::add);
	}
	int hits = 0;
	for(Gob g : snapshot) {
	    if(g == null || g.removed)
		continue;
	    String res = MoonGobDump.resname(g);
	    if(res == null || res.isEmpty())
		continue;
	    if(!res.toLowerCase(Locale.ROOT).contains(needle))
		continue;
	    hits++;
	    drainErrQueue();
	    boolean sent = MoonSmartInteract.sendRawGobClick(gui, g, 1);
	    if(!sent) {
		System.err.println("[MoonBlind][collect] id=" + g.id + " res=" + res + " send=FAIL");
		continue;
	    }
	    String err = pollErrAfter(450);
	    if(err != null)
		System.err.println("[MoonBlind][collect] id=" + g.id + " res=" + res + " err=" + err);
	    else
		System.err.println("[MoonBlind][collect] id=" + g.id + " res=" + res + " err=(none in 450ms)");
	    try {
		Thread.sleep(60);
	    } catch(InterruptedException ignored) {
		Thread.currentThread().interrupt();
		break;
	    }
	}
	System.err.println("[MoonBlind][collect] matched " + hits + " gob(s) for '" + targetResName + "'");
    }
}
