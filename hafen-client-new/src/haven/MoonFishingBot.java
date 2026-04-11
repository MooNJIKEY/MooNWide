package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Safe-v1 fishing bot: arms a fishing cursor, casts at a saved water tile, waits for the current
 * fishing action to end, and repeats with diagnostics instead of silent failure.
 */
public final class MoonFishingBot {
    private MoonFishingBot() {}

    private enum State {
	IDLE, ARMING, WAITING, COOLDOWN
    }

    private static final double ACTION_INTERVAL = 0.30;
    private static final double CURSOR_RETRY_SEC = 0.90;
    private static final double CAST_RETRY_SEC = 1.00;
    private static final double HOSTILE_RADIUS = MCache.tilesz.x * 10.0;
    private static final String[] FISH_ACTIONS = {"paginae/act/fish", "paginae/act/angling", "paginae/act/fishing"};

    private static State state = State.IDLE;
    private static double stateSince = 0.0;
    private static double lastActionAt = 0.0;
    private static double lastCursorAttempt = 0.0;
    private static double castStartedAt = 0.0;
    private static boolean progressSeen = false;
    private static int failures = 0;
    private static String lastStatus = "disabled";

    public static void reset() {
	state = State.IDLE;
	stateSince = 0.0;
	lastActionAt = 0.0;
	lastCursorAttempt = 0.0;
	castStartedAt = 0.0;
	progressSeen = false;
	failures = 0;
	lastStatus = "idle";
	MoonAutomationRegistry.noteAction("fishing", "reset");
	MoonAutomationRegistry.clearBlocked("fishing");
	MoonAutomationRegistry.clearLastError("fishing");
    }

    public static String statusSummary(GameUI gui) {
	if(!MoonConfig.fishingBotEnabled)
	    return "disabled";
	Coord tc = MoonTreeUtil.parseTileCoord(MoonConfig.fishingBotWaterTile);
	if(tc == null)
	    return "no water tile";
	String pos = String.format(Locale.ROOT, "@ %d,%d", tc.x, tc.y);
	if(lastStatus == null || lastStatus.isBlank())
	    return "armed " + pos;
	return lastStatus + " " + pos;
    }

    public static void tick(GameUI gui, double dt) {
	if(gui == null || !MoonConfig.fishingBotEnabled) {
	    if(!MoonConfig.fishingBotEnabled)
		lastStatus = "disabled";
	    return;
	}
	MapView map = gui.map;
	Gob pl = (map != null) ? map.player() : null;
	Coord water = MoonTreeUtil.parseTileCoord(MoonConfig.fishingBotWaterTile);
	if(map == null || pl == null) {
	    lastStatus = "waiting map";
	    MoonAutomationRegistry.noteBlocked("fishing", "no map/player");
	    return;
	}
	if(water == null) {
	    lastStatus = "no water tile";
	    MoonAutomationRegistry.noteBlocked("fishing", "pick water tile first");
	    return;
	}
	if(gui.maininv == null) {
	    lastStatus = "waiting inventory";
	    MoonAutomationRegistry.noteBlocked("fishing", "no main inventory");
	    return;
	}
	if(MoonConfig.fishingBotStopHostile && hostileNearby(map, pl)) {
	    stop(gui, text("Рыбалка остановлена: рядом враждебный моб.", "Fishing stopped: hostile nearby."),
		"hostile nearby");
	    return;
	}
	int free = countFreeSlots(gui.maininv);
	if(free <= MoonConfig.fishingBotMinFreeSlots) {
	    stop(gui, text("Рыбалка остановлена: инвентарь заполнен.", "Fishing stopped: inventory is full."),
		"inventory full");
	    return;
	}
	if(gui.vhand != null || (gui.hand != null && !gui.hand.isEmpty())) {
	    lastStatus = "hand busy";
	    MoonAutomationRegistry.noteBlocked("fishing", "hand busy");
	    return;
	}
	if(MoonAutoDrink.isBusy()) {
	    lastStatus = "waiting autodrink";
	    MoonAutomationRegistry.noteBlocked("fishing", "waiting autodrink");
	    return;
	}

	double now = Utils.rtime();
	boolean busy = progressBusy(gui);
	if(busy)
	    progressSeen = true;
	switch(state) {
	case IDLE -> tickIdle(gui, map, water, now);
	case ARMING -> tickArming(gui, map, water, now);
	case WAITING -> tickWaiting(gui, map, water, now, busy);
	case COOLDOWN -> tickCooldown(gui, now);
	}
    }

    private static void tickIdle(GameUI gui, MapView map, Coord water, double now) {
	if(now - lastActionAt < ACTION_INTERVAL)
	    return;
	if(!fishingCursorActive(gui)) {
	    if(!ensureFishingCursor(gui, now)) {
		lastStatus = "need fishing cursor";
		MoonAutomationRegistry.noteBlocked("fishing", "put Fish on belt/menu");
		return;
	    }
	    state = State.ARMING;
	    stateSince = now;
	    lastStatus = "arming cursor";
	    MoonAutomationRegistry.noteAction("fishing", "arming fishing cursor");
	    return;
	}
	cast(gui, map, water, now);
    }

    private static void tickArming(GameUI gui, MapView map, Coord water, double now) {
	if(fishingCursorActive(gui)) {
	    cast(gui, map, water, now);
	    return;
	}
	if(now - stateSince >= CURSOR_RETRY_SEC) {
	    state = State.IDLE;
	    stateSince = now;
	    lastStatus = "retry arm";
	}
    }

    private static void tickWaiting(GameUI gui, MapView map, Coord water, double now, boolean busy) {
	if(busy) {
	    lastStatus = "casting / waiting";
	    return;
	}
	if(progressSeen && (now - castStartedAt) >= 0.35) {
	    failures = 0;
	    state = State.COOLDOWN;
	    stateSince = now;
	    lastActionAt = now;
	    lastStatus = "attempt finished";
	    MoonAutomationRegistry.noteAction("fishing", "cast cycle finished");
	    return;
	}
	if((now - castStartedAt) >= MoonConfig.fishingBotCastTimeoutSec) {
	    failures++;
	    MoonAutomationRegistry.noteBlocked("fishing", "timeout #" + failures);
	    if(failures >= MoonConfig.fishingBotMaxFailures) {
		stop(gui, text("Рыбалка остановлена: слишком много таймаутов.", "Fishing stopped: too many timeouts."),
		    "repeated timeout");
		return;
	    }
	    state = State.COOLDOWN;
	    stateSince = now;
	    lastActionAt = now;
	    lastStatus = "timeout, retry";
	}
    }

    private static void tickCooldown(GameUI gui, double now) {
	if(now - stateSince >= CAST_RETRY_SEC) {
	    state = State.IDLE;
	    stateSince = now;
	    lastStatus = "ready";
	    MoonAutomationRegistry.clearBlocked("fishing");
	}
    }

    private static void cast(GameUI gui, MapView map, Coord water, double now) {
	Coord2d wpt = tileCenterWorld(water);
	if(!map.moonSyntheticItemAct(wpt, null, 0)) {
	    lastStatus = "cast failed";
	    MoonAutomationRegistry.noteBlocked("fishing", "cast failed");
	    state = State.COOLDOWN;
	    stateSince = now;
	    lastActionAt = now;
	    return;
	}
	state = State.WAITING;
	stateSince = now;
	lastActionAt = now;
	castStartedAt = now;
	progressSeen = progressBusy(gui);
	lastStatus = "cast sent";
	MoonAutomationRegistry.noteAction("fishing", "cast @" + water.x + "," + water.y);
	MoonAutomationRegistry.clearBlocked("fishing");
    }

    private static void stop(GameUI gui, String msg, String reason) {
	MoonConfig.setFishingBotEnabled(false);
	reset();
	lastStatus = reason;
	MoonAutomationRegistry.noteBlocked("fishing", reason);
	if(gui != null && gui.ui != null)
	    gui.ui.msg(msg, Color.WHITE, null);
    }

    private static boolean progressBusy(GameUI gui) {
	if(gui == null || gui.prog == null)
	    return false;
	double p = gui.prog.prog;
	return p > 0.04 && p < 0.997;
    }

    private static boolean fishingCursorActive(GameUI gui) {
	if(gui == null || gui.ui == null)
	    return false;
	try {
	    for(Widget w : new Widget[] {gui, gui.ui.root}) {
		if(w == null || w.cursor == null)
		    continue;
		Resource r = w.cursor.get();
		if(r == null || r.name == null)
		    continue;
		String n = r.name.toLowerCase(Locale.ROOT);
		if(n.contains("fish") || n.contains("angl") || n.contains("hook") || n.contains("rod"))
		    return true;
	    }
	} catch(Loading ignored) {
	}
	return false;
    }

    private static boolean looksLikeFishingPagina(MenuGrid.Pagina p) {
	if(p == null || p instanceof MenuGrid.SpecialPagina)
	    return false;
	try {
	    Resource r = p.res();
	    if(r == null || r.name == null)
		return false;
	    String n = r.name.toLowerCase(Locale.ROOT);
	    if(n.contains("paginae/") && (n.contains("fish") || n.contains("angl")))
		return true;
	    Resource.AButton act = r.layer(Resource.action);
	    if(act != null) {
		if(act.name != null) {
		    String an = act.name.toLowerCase(Locale.ROOT);
		    if(an.contains("fish") || an.contains("angl"))
			return true;
		}
		for(String ad : act.ad) {
		    if(ad == null)
			continue;
		    String dn = ad.toLowerCase(Locale.ROOT);
		    if(dn.contains("fish") || dn.contains("angl"))
			return true;
		}
	    }
	} catch(Loading ignored) {
	}
	return false;
    }

    private static boolean ensureFishingCursor(GameUI gui, double now) {
	if(fishingCursorActive(gui))
	    return true;
	if(now - lastCursorAttempt < CURSOR_RETRY_SEC)
	    return false;
	lastCursorAttempt = now;
	if(gui.menu != null) {
	    try {
		if(gui.belt != null) {
		    for(GameUI.BeltSlot bs : gui.belt) {
			if(bs instanceof GameUI.PagBeltSlot) {
			    MenuGrid.Pagina p = ((GameUI.PagBeltSlot)bs).pag;
			    if(gui.menu.isLeafAction(p) && looksLikeFishingPagina(p)) {
				gui.menu.use(p.button(), new MenuGrid.Interaction(1, gui.ui.modflags()), false);
				return false;
			    }
			}
		    }
		}
		synchronized(gui.menu.paginae) {
		    for(MenuGrid.Pagina p : gui.menu.paginae) {
			if(gui.menu.isLeafAction(p) && looksLikeFishingPagina(p)) {
			    gui.menu.use(p.button(), new MenuGrid.Interaction(1, gui.ui.modflags()), false);
			    return false;
			}
		    }
		}
	    } catch(Loading ignored) {
	    }
	}
	for(String act : FISH_ACTIONS) {
	    try {
		gui.act(act);
		return false;
	    } catch(Exception ignored) {
	    }
	}
	return false;
    }

    private static boolean hostileNearby(MapView map, Gob pl) {
	if(map == null || pl == null)
	    return false;
	try {
	    synchronized(map.glob.oc) {
		for(Gob g : map.glob.oc) {
		    if(g == null || g == pl)
			continue;
		    if(!MoonOverlay.isThreatMob(g))
			continue;
		    if(pl.rc.dist(g.rc) < HOSTILE_RADIUS)
			return true;
		}
	    }
	} catch(Exception ignored) {
	}
	return false;
    }

    private static int countFreeSlots(Inventory inv) {
	if(inv == null || inv.isz == null)
	    return 0;
	boolean[] occ = new boolean[Math.max(1, inv.isz.x * inv.isz.y)];
	try {
	    for(WItem wi : new ArrayList<>(inv.wmap.values())) {
		if(wi == null)
		    continue;
		Coord base = wi.c.sub(1, 1).div(Inventory.sqsz);
		int sw = Math.max(1, (wi.sz.x + Inventory.sqsz.x - 1) / Inventory.sqsz.x);
		int sh = Math.max(1, (wi.sz.y + Inventory.sqsz.y - 1) / Inventory.sqsz.y);
		for(int y = 0; y < sh; y++) {
		    for(int x = 0; x < sw; x++) {
			int sx = base.x + x;
			int sy = base.y + y;
			if(sx < 0 || sy < 0 || sx >= inv.isz.x || sy >= inv.isz.y)
			    continue;
			occ[(sy * inv.isz.x) + sx] = true;
		    }
		}
	    }
	} catch(Exception ignored) {
	}
	int free = 0;
	for(int y = 0; y < inv.isz.y; y++) {
	    for(int x = 0; x < inv.isz.x; x++) {
		int i = (y * inv.isz.x) + x;
		if(inv.sqmask != null && i < inv.sqmask.length && inv.sqmask[i])
		    continue;
		if(!occ[i])
		    free++;
	    }
	}
	return free;
    }

    private static Coord2d tileCenterWorld(Coord tc) {
	return Coord2d.of((tc.x + 0.5) * MCache.tilesz.x, (tc.y + 0.5) * MCache.tilesz.y);
    }

    private static String text(String ru, String en) {
	return MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }
}
