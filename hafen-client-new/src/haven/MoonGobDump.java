package haven;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Отладочный дамп объектов из {@link OCache}: сервер уже прислал gob, но часть может быть скрыта
 * рендером ({@link MapView#isHidden(Gob)} — клиентские причины: X-Ray, hitbox crop и т.д.), это
 * <strong>не</strong> отдельный флаг в {@link Gob}.
 * <p>
 * <strong>Про «невидимость» на сервере:</strong> у {@link Gob} в этом клиенте <em>нет</em> полей
 * {@code flags} или публичного {@code c} как битовой маски видимости. Поле {@code c} встречается
 * только во вложенных классах Placer (координата размещения). Состояние объекта с сервера приходит
 * потоком дельт {@link OCache} ({@code OD_MOVE}, {@code OD_RES}, …); отдельного «я скрыт от игрока»
 * в Java-слое {@link Gob} не хранится — если сервер не присылает объект, его просто нет в кэше.
 */
public final class MoonGobDump {

    private MoonGobDump() {}

    /**
     * Имя ресурса drawable (путь вроде {@code gfx/terobjs/...}), аналог запрошенного {@code g.resname()}.
     */
    public static String resname(Gob g) {
	return(MoonGobKind.resourceName(g));
    }

    /**
     * Обходит все {@link Gob} в {@link Glob#oc} (включая локальные коллекции через полный обход —
     * см. {@link OCache#gobAction}); печатает в stdout только «интересные» объекты.
     * <p>
     * Строка: {@code id}, {@code res}, классификация {@link MoonOverlay.GobType}, расстояние до игрока
     * {@code g.rc.dist(player.rc)}, флаг {@link MapView#isHidden(Gob)}.
     */
    public static void dumpNearbyGobs(GameUI gui) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null) {
	    System.err.println("[MoonGobDump] no GameUI/map/session");
	    return;
	}
	Gob pl = gui.map.player();
	if(pl == null) {
	    System.err.println("[MoonGobDump] player gob not found");
	    return;
	}
	Coord2d prc = pl.rc;
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
	    String res = resname(g);
	    MoonOverlay.GobType t = MoonOverlay.classifyGob(g);
	    if(!interesting(g, res, t))
		continue;
	    double dist = g.rc.dist(prc);
	    boolean hidden = mv.isHidden(g);
	    System.out.println("[MoonGobDump] id=" + g.id
		+ " res=" + res
		+ " kind=" + t
		+ " dist=" + String.format(Locale.ROOT, "%.2f", dist)
		+ " mapHidden=" + hidden);
	    n++;
	}
	System.out.println("[MoonGobDump] listed " + n + " / " + snapshot.size() + " gobs (after filter)");
    }

    private static boolean interesting(Gob g, String res, MoonOverlay.GobType t) {
	if(MoonPacketHook.isPlayerGob(g))
	    return(false);
	if(t == MoonOverlay.GobType.PLAYER)
	    return(true);
	if(t == MoonOverlay.GobType.HOSTILE_MOB)
	    return(true);
	if(res != null) {
	    String ln = res.toLowerCase(Locale.ROOT);
	    /* Редкие ноды: старые стволы, глина, кремни (подстрочное совпадение по пути ресурса). */
	    if(ln.contains("oldtrunk") || ln.contains("claypit") || ln.contains("flint"))
		return(true);
	}
	return(false);
    }
}
