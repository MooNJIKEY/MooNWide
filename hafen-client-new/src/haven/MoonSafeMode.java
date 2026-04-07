package haven;

import static haven.OCache.posres;

/**
 * When enabled, issues occasional walk clicks away from the nearest hostile mob
 * if it enters {@link MoonConfig#safeModeMinTiles}.
 */
public final class MoonSafeMode {
    private MoonSafeMode() {}

    private static double lastFlee;

    public static void tick(GameUI gui, double dt) {
	if(gui == null || !MoonConfig.safeMode)
	    return;
	MapView map = gui.map;
	if(map == null)
	    return;
	if(MoonConfig.safeModeBusyOnly && gui.prog == null)
	    return;
	if(MoonConfig.safeModeRespectCombat && gui.fv != null && gui.fv.current != null)
	    return;
	Gob pl;
	try {
	    pl = map.player();
	} catch(Loading e) {
	    return;
	}
	if(pl == null)
	    return;
	double now = Utils.rtime();
	if(now - lastFlee < 1.1)
	    return;
	double minD = MoonConfig.safeModeMinTiles * MCache.tilesz.x;
	Gob threat = null;
	double threatD = Double.MAX_VALUE;
	try {
	    synchronized(map.glob.oc) {
		for(Gob g : map.glob.oc) {
		    if(g == null || g == pl)
			continue;
		    if(!MoonOverlay.isThreatMob(g))
			continue;
		    double d = pl.rc.dist(g.rc);
		    if(d < threatD) {
			threatD = d;
			threat = g;
		    }
		}
	    }
	} catch(Exception ignored) {
	    return;
	}
	if(threat == null || threatD >= minD)
	    return;
	try {
	    Coord2d away = pl.rc.sub(threat.rc);
	    double len = Math.hypot(away.x, away.y);
	    if(len < 1e-3)
		away = Coord2d.of(1, 0);
	    else
		away = away.mul(1.0 / len);
	    double step = Math.min(MCache.tilesz.x * 5.0, minD * 0.5 + MCache.tilesz.x * 2.0);
	    Coord2d wpt = pl.rc.add(away.mul(step));
	    Coord3f sc = map.screenxf(wpt);
	    if(sc == null)
		return;
	    Coord pc = new Coord(
		Utils.clip((int)Math.round(sc.x), 2, map.sz.x - 3),
		Utils.clip((int)Math.round(sc.y), 2, map.sz.y - 3));
	    map.wdgmsg("click", pc, wpt.floor(posres), 1, gui.ui.modflags());
	    lastFlee = now;
	} catch(Exception e) {
	    new Warning(e, "MoonSafeMode").issue();
	}
    }
}
