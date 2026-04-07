package haven;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.SwingUtilities;

/**
 * «Умное» взаимодействие с {@link Gob} по id из {@link MoonGobDump}.
 * <p>
 * <strong>Сквозь стену:</strong> клиент может отправить тот же вид {@link MapView}{@code wdgmsg}, что и при клике
 * по миникарте с привязкой к gob ({@link MiniMap#mvclick}). На сервере обычно проверяются дистанция, LOS и
 * доступ к объекту — если персонаж стоит у внешней стены, а сундук «виден» только на радаре внутри здания,
 * сервер с высокой вероятностью <em>отклонит</em> действие или не применит его, даже если пакет ушёл.
 * Это не обход физики на клиенте, а лишь досылка сообщения без реального hit-test по сцене.
 * <p>
 * Если цель дальше ~2 тайлов, один ПКМ на сервере часто <em>не открывает</em> контейнер — сначала
 * строится {@link MoonPathfinder A*}-маршрут и {@link MoonPathWalker}, после прихода шлётся ПКМ.
 */
public final class MoonSmartInteract {

    /** Как в {@link MoonTreeChopBot}: чуть приподнять точку для {@link MapView#screenxf} у низких гобов. */
    private static final float CLICK_Z_BOOST = 12f;

    /**
     * В пределах этого расстояния (wu) обычно хватает одного ПКМ; дальше — подход по пути, затем ПКМ.
     * ~2 тайла.
     */
    private static final double DIRECT_RMB_MAX_DIST = MCache.tilesz.x * 2.0;

    /**
     * Макс. расстояние в <strong>мировых</strong> координатах ({@link Gob#rc} / {@link Coord2d#dist}).
     * Один тайл ≈ {@link MCache#tilesz}.x (11) — старое значение {@code 5.0} было меньше половины тайла.
     * По умолчанию ~6 тайлов.
     */
    public static double defaultInteractRadius = MCache.tilesz.x * 6.0;

    private MoonSmartInteract() {}

    /**
     * Находит gob по id (включая случай {@link HashMultiMap}, когда {@link OCache#getgob(long)} даёт
     * {@code null} при нескольких значениях), проверяет расстояние и тип ({@link MoonOverlay.GobType#PLAYER}
     * или {@link MoonOverlay.GobType#CONTAINER}), шлёт {@code MapView.wdgmsg("click", …)} в формате
     * {@link MiniMap#mvclick} — с id gob и мировой точкой.
     *
     * @return {@code true}, если сообщение поставлено в очередь UI
     */
    public static boolean interactById(GameUI gui, long id) {
	return interactById(gui, id, defaultInteractRadius);
    }

    /**
     * @param maxDist макс. {@code gob.rc.dist(player.rc)}
     */
    public static boolean interactById(GameUI gui, long id, double maxDist) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null) {
	    System.err.println("[MoonInteract] no GameUI/map/session");
	    return(false);
	}
	OCache oc = gui.ui.sess.glob.oc;
	Gob g = findGob(oc, id);
	if(g == null || g.removed) {
	    System.err.println("[MoonInteract] no gob id=" + id);
	    return(false);
	}
	Gob pl = gui.map.player();
	if(pl == null) {
	    System.err.println("[MoonInteract] no player gob");
	    return(false);
	}
	double dist = g.rc.dist(pl.rc);
	if(dist > maxDist + 1e-6) {
	    System.err.println("[MoonInteract] too far: " + String.format(Locale.ROOT, "%.1f", dist) + " > "
		+ String.format(Locale.ROOT, "%.1f", maxDist) + " wu (~" + String.format(Locale.ROOT, "%.1f", maxDist / MCache.tilesz.x)
		+ " tiles) id=" + id);
	    return(false);
	}
	MoonOverlay.GobType t = MoonOverlay.classifyGob(g);
	if(t != MoonOverlay.GobType.PLAYER && t != MoonOverlay.GobType.CONTAINER) {
	    System.err.println("[MoonInteract] type not PLAYER/CONTAINER: " + t + " id=" + id);
	    return(false);
	}
	return(sendClickOnGob(gui, g, t, dist));
    }

    /**
     * Ближайший {@link MoonOverlay.GobType#PLAYER} или {@link MoonOverlay.GobType#CONTAINER} в пределах
     * {@code maxDist} (не свой персонаж) — для горячей клавиши {@code H}.
     */
    public static boolean interactNearestSmart(GameUI gui) {
	return interactNearestSmart(gui, defaultInteractRadius);
    }

    /** @param maxDist макс. расстояние в мире до цели */
    public static boolean interactNearestSmart(GameUI gui, double maxDist) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null) {
	    System.err.println("[MoonInteract] no GameUI/map/session");
	    return(false);
	}
	Gob pl = gui.map.player();
	if(pl == null) {
	    System.err.println("[MoonInteract] no player gob");
	    return(false);
	}
	OCache oc = gui.ui.sess.glob.oc;
	ArrayList<Gob> snapshot = new ArrayList<>();
	synchronized(oc) {
	    oc.gobAction(snapshot::add);
	}
	Gob best = null;
	double bestD = Double.MAX_VALUE;
	for(Gob g : snapshot) {
	    if(g == null || g.removed || g.virtual)
		continue;
	    if(MoonPacketHook.isPlayerGob(g))
		continue;
	    MoonOverlay.GobType t = MoonOverlay.classifyGob(g);
	    if(t != MoonOverlay.GobType.PLAYER && t != MoonOverlay.GobType.CONTAINER)
		continue;
	    double d = g.rc.dist(pl.rc);
	    if(d > maxDist + 1e-6)
		continue;
	    if(best == null || d < bestD - 1e-9 || (Math.abs(d - bestD) < 1e-9 && g.id < best.id)) {
		best = g;
		bestD = d;
	    }
	}
	if(best == null) {
	    System.err.println("[MoonInteract] no PLAYER/CONTAINER within " + String.format(Locale.ROOT, "%.1f", maxDist)
		+ " world units (~" + String.format(Locale.ROOT, "%.1f", maxDist / MCache.tilesz.x) + " tiles). "
		+ "Raise radius or use :interact <id> <radius>");
	    return(false);
	}
	MoonOverlay.GobType tk = MoonOverlay.classifyGob(best);
	System.out.println("[MoonInteract] nearest id=" + best.id + " kind=" + tk + " dist="
	    + String.format(Locale.ROOT, "%.2f", bestD));
	return(sendClickOnGob(gui, best, tk, bestD));
    }

    private static boolean sendClickOnGob(GameUI gui, Gob g, MoonOverlay.GobType t, double dist) {
	MapView mv = gui.map;
	MoonPathWalker.cancelActive();
	mv.moonClearRouteTrace();
	boolean needWalk = (t == MoonOverlay.GobType.CONTAINER || t == MoonOverlay.GobType.PLAYER)
	    && dist > DIRECT_RMB_MAX_DIST + 1e-6;
	if(needWalk) {
	    Gob pl = mv.player();
	    if(pl == null)
		return(false);
	    MCache map = mv.glob != null ? mv.glob.map : null;
	    OCache oc = mv.glob != null ? mv.glob.oc : null;
	    if(map != null && oc != null) {
		List<Coord2d> path = MoonPathfinder.findPath(map, oc, pl.rc, g.rc, pl.id,
		    MoonConfig.teleportBlockHostileNearTarget, MoonConfig.teleportHostileClearTiles);
		if(path != null && !path.isEmpty()) {
		    final long gid = g.id;
		    MoonPathWalker.start(mv, path, () -> SwingUtilities.invokeLater(() -> sendRmbAfterWalk(gui, gid)));
		    System.out.println("[MoonInteract] walk then RMB id=" + g.id + " kind=" + t + " dist="
			+ String.format(Locale.ROOT, "%.2f", dist) + " pathPts=" + path.size() + " res="
			+ MoonGobDump.resname(g));
		    return(true);
		}
	    }
	    System.err.println("[MoonInteract] no A* path, LMB walk id=" + g.id + " (press H again when close)");
	    return(sendGobButtonClick(gui, g, t, dist, 1));
	}
	return(sendGobButtonClick(gui, g, t, dist, 3));
    }

    /** После {@link MoonPathWalker} — повторно найти gob и открыть ПКМ. */
    private static void sendRmbAfterWalk(GameUI gui, long gobid) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null)
	    return;
	Gob g = findGob(gui.ui.sess.glob.oc, gobid);
	if(g == null || g.removed) {
	    System.err.println("[MoonInteract] gob gone after walk id=" + gobid);
	    return;
	}
	Gob pl = gui.map.player();
	if(pl == null)
	    return;
	MoonOverlay.GobType t = MoonOverlay.classifyGob(g);
	double dist = g.rc.dist(pl.rc);
	/*
	 * {@link MoonPathWalker} вызывает onComplete и при отмене/обрыве — тогда ещё далеко, ПКМ бессмыслен.
	 */
	if(dist > DIRECT_RMB_MAX_DIST * 1.25 + 1e-6) {
	    System.err.println("[MoonInteract] walk ended still far dist=" + String.format(Locale.ROOT, "%.1f", dist)
		+ " id=" + g.id + " — retry H or walk closer");
	    return;
	}
	System.out.println("[MoonInteract] after walk RMB id=" + g.id + " kind=" + t + " dist="
	    + String.format(Locale.ROOT, "%.2f", dist));
	sendGobButtonClick(gui, g, t, dist, 3);
    }

    /**
     * Один синтетический клик по gob (как {@link MiniMap#mvclick}) без проверок дистанции/типа.
     * Для отладки «слепого» взаимодействия с объектами в {@link OCache}.
     *
     * @return {@code false} если не удалось спроецировать на экран
     */
    public static boolean sendRawGobClick(GameUI gui, Gob g, int button) {
	MapView mv = gui.map;
	Coord3f wc = rawGobClickWorldPoint(mv, g);
	if(wc == null)
	    return(false);
	wc = wc.add(0, 0, CLICK_Z_BOOST);
	Coord3f sc = mv.screenxf(wc);
	if(sc == null) {
	    try {
		sc = mv.screenxf(mv.glob.map.getzp(g.rc));
	    } catch(Loading ignored) {
	    }
	}
	if(sc == null)
	    sc = mv.screenxf(g.rc);
	if(sc == null) {
	    System.err.println("[MoonInteract] raw click screen null id=" + g.id);
	    return(false);
	}
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(sc.x), 2, mv.sz.x - 3),
	    Utils.clip((int)Math.round(sc.y), 2, mv.sz.y - 3));
	int sendMf = gui.ui.modflags() & ~UI.MOD_META;
	mv.wdgmsg("click", pc, g.rc.floor(OCache.posres), button, sendMf, 0,
	    (int)g.id, g.rc.floor(OCache.posres), 0, -1);
	return(true);
    }

    private static Coord3f rawGobClickWorldPoint(MapView mv, Gob g) {
	try {
	    return(g.getc());
	} catch(Loading e) {
	    try {
		if(mv.glob != null && mv.glob.map != null)
		    return(mv.glob.map.getzp(g.rc));
	    } catch(Loading ignored) {
	    }
	    return(Coord3f.of((float)g.rc.x, (float)g.rc.y, 0f));
	}
    }

    /**
     * ЛКМ (1) — подход; ПКМ (3) — меню / открытие (ср. {@link MoonTreeChopBot}).
     */
    private static boolean sendGobButtonClick(GameUI gui, Gob g, MoonOverlay.GobType t, double dist, int button) {
	MapView mv = gui.map;
	Coord3f wc;
	try {
	    wc = g.getc();
	} catch(Loading e) {
	    System.err.println("[MoonInteract] Loading (gob position) id=" + g.id);
	    return(false);
	}
	wc = wc.add(0, 0, CLICK_Z_BOOST);
	Coord3f sc = mv.screenxf(wc);
	if(sc == null) {
	    try {
		sc = mv.screenxf(mv.glob.map.getzp(g.rc));
	    } catch(Loading ignored) {
	    }
	}
	if(sc == null)
	    sc = mv.screenxf(g.rc);
	if(sc == null) {
	    System.err.println("[MoonInteract] screen projection null id=" + g.id);
	    return(false);
	}
	Coord pc = new Coord(
	    Utils.clip((int)Math.round(sc.x), 2, mv.sz.x - 3),
	    Utils.clip((int)Math.round(sc.y), 2, mv.sz.y - 3));
	int sendMf = gui.ui.modflags() & ~UI.MOD_META;
	mv.wdgmsg("click", pc, g.rc.floor(OCache.posres), button, sendMf, 0,
	    (int)g.id, g.rc.floor(OCache.posres), 0, -1);
	System.out.println("[MoonInteract] click btn=" + button + " id=" + g.id + " kind=" + t + " dist="
	    + String.format(Locale.ROOT, "%.2f", dist) + " res=" + MoonGobDump.resname(g));
	return(true);
    }

    /**
     * То же, но без проверки типа (только расстояние и наличие gob). Осторожно: для отладки.
     */
    public static boolean interactByIdAnyType(GameUI gui, long id, double maxDist) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null) {
	    System.err.println("[MoonInteract] no GameUI/map/session");
	    return(false);
	}
	Gob g = findGob(gui.ui.sess.glob.oc, id);
	if(g == null || g.removed) {
	    System.err.println("[MoonInteract] no gob id=" + id);
	    return(false);
	}
	Gob pl = gui.map.player();
	if(pl == null)
	    return(false);
	if(g.rc.dist(pl.rc) > maxDist + 1e-6)
	    return(false);
	double d = g.rc.dist(pl.rc);
	return(sendClickOnGob(gui, g, MoonOverlay.classifyGob(g), d));
    }

    private static Gob findGob(OCache oc, long id) {
	Gob g = oc.getgob(id);
	if(g != null)
	    return(g);
	ArrayList<Gob> hit = new ArrayList<>(1);
	oc.gobAction(gob -> {
	    if(gob.id == id)
		hit.add(gob);
	});
	return(hit.isEmpty() ? null : hit.get(0));
    }
}
