package haven;

import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Walks an A*-computed path by issuing sequential click commands.
 * Runs on a background thread; call {@link #cancel()} to stop.
 */
public class MoonPathWalker extends Thread {
    private final MapView mv;
    private final List<Coord2d> path;
    private final Runnable onCompleteUi;
    private volatile boolean cancelled = false;

    private static volatile MoonPathWalker active = null;

    public MoonPathWalker(MapView mv, List<Coord2d> path) {
	this(mv, path, null);
    }

    public MoonPathWalker(MapView mv, List<Coord2d> path, Runnable onCompleteUi) {
	super("MoonPathWalker");
	setDaemon(true);
	this.mv = mv;
	this.path = path;
	this.onCompleteUi = onCompleteUi;
    }

    public static void start(MapView mv, List<Coord2d> path) {
	start(mv, path, null);
    }

    /** @param onCompleteUi optional; runs on AWT/Swing EDT after the walk thread finishes (cancelled or done). */
    public static void start(MapView mv, List<Coord2d> path, Runnable onCompleteUi) {
	cancelActive();
	MoonPathWalker w = new MoonPathWalker(mv, path, onCompleteUi);
	active = w;
	w.start();
    }

    public static void cancelActive() {
	MoonPathWalker w = active;
	if(w != null) {
	    w.cancel();
	    active = null;
	}
    }

    public void cancel() {
	cancelled = true;
	if(mv != null)
	    mv.moonPathWalkerCancelPending();
	interrupt();
    }

    @Override
    public void run() {
	try {
	    if(mv != null)
		mv.moonSetRouteTrace(path);
	    for(int i = 0; i < path.size() && !cancelled; i++) {
		Coord2d wp = path.get(i);
		mv.moonPathWalkerAwaitClick(wp, 12000);
		if(!waitArrival(wp, 8000))
		    break;
	    }
	} catch(InterruptedException ignored) {
	} finally {
	    if(mv != null)
		mv.moonClearRouteTrace();
	    if(active == this)
		active = null;
	    if(onCompleteUi != null)
		SwingUtilities.invokeLater(onCompleteUi);
	}
    }

    private boolean waitArrival(Coord2d wp, long timeoutMs) throws InterruptedException {
	long deadline = System.currentTimeMillis() + timeoutMs;
	while(!cancelled) {
	    Gob pl = mv.player();
	    if(pl == null) return false;
	    Coord2d pc = new Coord2d(pl.getc());
	    if(pc.dist(wp) < 6.0) return true;
	    if(System.currentTimeMillis() > deadline) return false;
	    double v = pl.getv();
	    if(v == 0 && System.currentTimeMillis() > deadline - timeoutMs + 2000)
		return false;
	    Thread.sleep(50);
	}
	return false;
    }
}
