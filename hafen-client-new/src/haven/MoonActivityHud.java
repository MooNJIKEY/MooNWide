package haven;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Floating text above the player: activity progress (craft/gather), rough ETA, and
 * durability of the nearest damaged structure or tile (walls, mine tiles) via {@link GobHealth}.
 */
public final class MoonActivityHud {
    private MoonActivityHud() {}

    private static final float HEAD_Z = 22f;
    private static final double STRUCT_SCAN_INTERVAL = 0.25;
    private static final double STRUCT_RESCAN_MOVE = MCache.tilesz.x * 0.75;
    private static double lastEtaP = -1;
    private static double lastEtaT;
    private static double smoothedRate;
    private static double lastStructScanAt = -1;
    private static Coord2d lastStructPlayerRc = null;
    private static String cachedStructLine = null;
    private static final int LINE_CACHE_MAX = 32;
    private static final Map<String, Tex> lineCache = new LinkedHashMap<String, Tex>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
            if(size() > LINE_CACHE_MAX) {
                if(eldest.getValue() != null)
                    eldest.getValue().dispose();
                return true;
            }
            return false;
        }
    };

    public static void tick(GameUI gui) {
	if(gui == null || gui.prog == null) {
	    lastEtaP = -1;
	    smoothedRate = 0;
	} else {
	    double p = gui.prog.prog;
	    double now = Utils.rtime();
	    if(lastEtaP < 0) {
		lastEtaP = p;
		lastEtaT = now;
	    } else {
		double dt = now - lastEtaT;
		if(dt >= 0.08) {
		    double dp = p - lastEtaP;
		    if(dp > 1e-7 && dt > 0) {
			double inst = dp / dt;
			smoothedRate = (smoothedRate <= 1e-9) ? inst : smoothedRate * 0.82 + inst * 0.18;
		    }
		    lastEtaP = p;
		    lastEtaT = now;
		}
	    }
	}
	updateStructureCache(gui);
    }

    public static void draw(GOut g, MapView mv) {
	GameUI gui = mv.getparent(GameUI.class);
	if(gui == null)
	    return;
	boolean showTree = MoonConfig.treeBotEnabled && MoonConfig.treeBotHudLine;
	boolean showMine = MoonConfig.mineBotEnabled && MoonConfig.mineBotHudLine;
	if(!MoonConfig.activityHud && !showTree && !showMine)
	    return;
	Gob pl = null;
	int x = -1, ty = -1;

	/* Progress line should live near the hourglass HUD, not above character. */
	if(MoonConfig.activityHud && gui.prog != null) {
	    x = gui.prog.c.x + gui.prog.sz.x / 2;
	    ty = gui.prog.c.y - UI.scale(6);
	} else {
	    try {
		pl = mv.player();
	    } catch(Loading e) {
		return;
	    }
	    if(pl == null)
		return;
	    Coord3f pc3;
	    try {
		pc3 = pl.getc();
	    } catch(Exception e) {
		return;
	    }
	    if(pc3 == null)
		return;
	    Coord3f head = new Coord3f(pc3.x, pc3.y, pc3.z + HEAD_Z);
	    Coord3f sc = safeScreen(mv, head);
	    if(sc == null)
		return;
	    x = (int)sc.x;
	    ty = (int)sc.y - UI.scale(18);
	}

	if(showTree) {
	    String tline = MoonTreeChopBot.statusHudLine(gui);
	    if(tline != null)
		ty = drawLine(g, tline, new Color(120, 255, 160, 240), x, ty);
	}
	if(showMine) {
	    String mline = MoonMineBot.statusHudLine(gui);
	    if(mline != null)
		ty = drawLine(g, mline, new Color(255, 200, 120, 240), x, ty);
	}

	if(MoonConfig.activityHud && gui.prog != null) {
	    double p = Utils.clip(gui.prog.prog, 0, 1);
	    String line = String.format(LocalizationManager.tr("activity.hud.action"), p * 100.0);
	    if(smoothedRate > 1e-5 && p > 0.02 && p < 0.999) {
		double eta = (1.0 - p) / smoothedRate;
		if(eta > 0.5 && eta < 3600)
		    line += " " + String.format(LocalizationManager.tr("activity.hud.eta"), eta);
	    }
	    ty = drawLine(g, line, new Color(255, 220, 120, 240), x, ty);
	}

	if(!MoonConfig.activityHud)
	    return;
	if(pl == null) {
	    try {
		pl = mv.player();
	    } catch(Loading e) {
		return;
	    }
	    if(pl == null)
		return;
	}
	String struct = cachedStructLine;
	if(struct != null)
	    drawLine(g, struct, new Color(180, 220, 255, 240), x, ty);
    }

    private static int drawLine(GOut g, String text, Color col, int cx, int ty) {
	Tex tex = lineTex(text, col);
	int w = tex.sz().x, h = tex.sz().y;
	g.image(tex, Coord.of(cx - w / 2, ty - h));
	return ty - h - UI.scale(3);
    }

    private static Tex lineTex(String text, Color col) {
	String key = (col.getRGB() & 0xffffffffL) + "|" + text;
	synchronized(lineCache) {
	    Tex tex = lineCache.get(key);
	    if(tex == null) {
		tex = new TexI(Text.render(text, col).img);
		lineCache.put(key, tex);
	    }
	    return(tex);
	}
    }

    private static void updateStructureCache(GameUI gui) {
	if(gui == null || gui.map == null || !MoonConfig.activityHud) {
	    cachedStructLine = null;
	    lastStructPlayerRc = null;
	    lastStructScanAt = -1;
	    return;
	}
	Gob pl;
	try {
	    pl = gui.map.player();
	} catch(Loading e) {
	    return;
	} catch(Exception e) {
	    cachedStructLine = null;
	    return;
	}
	if(pl == null || pl.rc == null) {
	    cachedStructLine = null;
	    return;
	}
	double now = Utils.rtime();
	boolean needScan = (lastStructScanAt < 0) || ((now - lastStructScanAt) >= STRUCT_SCAN_INTERVAL);
	if(!needScan && lastStructPlayerRc != null && pl.rc.dist(lastStructPlayerRc) >= STRUCT_RESCAN_MOVE)
	    needScan = true;
	if(!needScan)
	    return;
	lastStructPlayerRc = pl.rc;
	lastStructScanAt = now;
	cachedStructLine = pickStructureHpLine(pl, gui.map);
    }

    /** Closest damaged {@link GobHealth} object (wall, cave tile, etc.). */
    private static String pickStructureHpLine(Gob pl, MapView mv) {
	Gob best = null;
	double bestd = Double.MAX_VALUE;
	double maxd = MoonConfig.activityHudStructRange * MCache.tilesz.x;
	try {
	    synchronized(mv.glob.oc) {
		for(Gob g : mv.glob.oc) {
		    if(g == null || g == pl)
			continue;
		    GobHealth gh = g.getattr(GobHealth.class);
		    if(gh == null || gh.hp >= 0.999f)
			continue;
		    String rn = MoonOverlay.gobResName(g);
		    if(rn != null && (rn.contains("gfx/borka") || rn.contains("gfx/kritter/")))
			continue;
		    double d = pl.rc.dist(g.rc);
		    if(d > maxd)
			continue;
		    if(d < bestd) {
			bestd = d;
			best = g;
		    }
		}
	    }
	} catch(Exception ignored) {
	    return(null);
	}
	if(best == null)
	    return(null);
	GobHealth gh = best.getattr(GobHealth.class);
	if(gh == null)
	    return(null);
	return(String.format(LocalizationManager.tr("activity.hud.structure"), gh.hp * 100.0));
    }

    private static Coord3f safeScreen(MapView mv, Coord3f wc) {
	try {
	    Coord3f s = mv.screenxf(wc);
	    if(s == null || !Float.isFinite(s.x) || !Float.isFinite(s.y))
		return(null);
	    return(s);
	} catch(Exception e) {
	    return(null);
	}
    }
}
