package haven;

import haven.render.Model;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MoonOverlay {
    private static final Color COL_HOSTILE    = new Color(255, 50, 50, 220);
    private static final Color COL_PLAYER     = new Color(50, 200, 255, 220);
    private static final Color COL_NEUTRAL    = new Color(255, 200, 50, 200);
    private static final Color COL_ESP_PLAYER = new Color(60, 220, 255, 160);
    private static final Color COL_ESP_HOSTILE= new Color(255, 80, 80, 160);
    private static final Color COL_ESP_BOX    = new Color(180, 120, 255, 140);

    private static final Color COL_VEHICLE      = new Color(100, 180, 255, 160);
    private static final Color COL_BUILDING     = new Color(180, 180, 180, 120);
    private static final Color COL_RESOURCE     = new Color(255, 200, 50, 180);
    private static final Color COL_STOCKPILE    = new Color(200, 160, 80, 140);
    private static final Color COL_CONTAINER    = new Color(180, 140, 60, 140);
    private static final Color COL_HERB         = new Color(80, 230, 80, 160);
    private static final Color COL_DUNGEON      = new Color(200, 60, 200, 160);
    private static final Color COL_WORKSTATION  = new Color(200, 200, 150, 120);

    private static final Color COL_AGGRO_STRONG = new Color(255, 30, 30, 180);
    private static final Color COL_AGGRO_MEDIUM = new Color(255, 160, 30, 140);
    private static final Color COL_AGGRO_WEAK   = new Color(255, 255, 80, 100);

    private static final double AGGRO_STRONG = 55.0;
    private static final double AGGRO_MEDIUM = 110.0;
    private static final double AGGRO_WEAK   = 176.0;
    private static final int CIRCLE_SEGMENTS = 48;

    private static final float HEAD_Z = 15.0f;
    private static final float BOX_HW = 4.0f;
    private static final float BOX_HH = 18.0f;

    private static final float TREE_HW = 5.0f;
    private static final float TREE_HH = 30.0f;
    private static int overlayFontPx = -1;
    private static Text.Foundry overlayFoundry;
    private static final int TEXT_CACHE_MAX = 512;
    private static final Map<String, Tex> overlayTextCache = new LinkedHashMap<String, Tex>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
            if(size() > TEXT_CACHE_MAX) {
                if(eldest.getValue() != null)
                    eldest.getValue().dispose();
                return true;
            }
            return false;
        }
    };
    private static final int ITEM_TEXT_CACHE_MAX = 256;
    private static final Map<String, Tex> itemTextCache = new LinkedHashMap<String, Tex>(96, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
            if(size() > ITEM_TEXT_CACHE_MAX) {
                if(eldest.getValue() != null)
                    eldest.getValue().dispose();
                return true;
            }
            return false;
        }
    };
    private static int itemFontPx = -1;
    private static Text.Foundry itemFoundry;
    private static Glob cachedSnapshotGlob = null;
    private static double cachedSnapshotAt = -1;
    private static List<Gob> cachedSnapshot = Collections.emptyList();
    private static final double SNAPSHOT_TTL = 0.12;

    private static Text.Foundry overlayFoundry() {
	int px = MoonConfig.overlayTextSize;
	if((overlayFoundry == null) || (overlayFontPx != px)) {
	    synchronized(overlayTextCache) {
		for(Tex tex : overlayTextCache.values())
		    tex.dispose();
		overlayTextCache.clear();
	    }
	    overlayFontPx = px;
	    overlayFoundry = new Text.Foundry(Text.sans, px).aa(true);
	}
	return(overlayFoundry);
    }

    private static void drawOverlayText(GOut g, String text, Coord c, double ax, double ay, Color color) {
	if((text == null) || text.isEmpty())
	    return;
	Color col = (color != null) ? color : MoonConfig.overlayTextColor();
	Tex tex = overlayTextTex(text, col);
	g.aimage(tex, c, ax, ay);
    }

    private static Tex overlayTextTex(String text, Color color) {
	overlayFoundry();
	String key = overlayFontPx + "|" + (color.getRGB() & 0xffffffffL) + "|" + text;
	synchronized(overlayTextCache) {
	    Tex tex = overlayTextCache.get(key);
	    if(tex == null) {
		tex = new TexI(Utils.outline2(overlayFoundry().render(text, color).img, Color.BLACK));
		overlayTextCache.put(key, tex);
	    }
	    return tex;
	}
    }

    private static Tex itemTextTex(String text, int px, Color color) {
	if((text == null) || text.isEmpty())
	    return null;
	if((itemFoundry == null) || (itemFontPx != px)) {
	    synchronized(itemTextCache) {
		if((itemFoundry == null) || (itemFontPx != px)) {
		    for(Tex tex : itemTextCache.values())
			tex.dispose();
		    itemTextCache.clear();
		    itemFontPx = px;
		    itemFoundry = new Text.Foundry(Text.sans.deriveFont(Font.PLAIN, (float)px)).aa(true);
		}
	    }
	}
	String key = px + "|" + (color.getRGB() & 0xffffffffL) + "|" + text;
	synchronized(itemTextCache) {
	    Tex tex = itemTextCache.get(key);
	    if(tex == null) {
		tex = new TexI(Utils.outline2(itemFoundry.render(text, color).img, Color.BLACK));
		itemTextCache.put(key, tex);
	    }
	    return tex;
	}
    }

    private static List<Gob> snapshotGobs(MapView mv) {
	double now = Utils.rtime();
	if((cachedSnapshotGlob == mv.glob) && ((now - cachedSnapshotAt) <= SNAPSHOT_TTL))
	    return cachedSnapshot;
	List<Gob> snap = new ArrayList<>();
	try {
	    synchronized(mv.glob.oc) {
		for(Gob gb : mv.glob.oc)
		    snap.add(gb);
	    }
	} catch(Exception e) {
	    return Collections.emptyList();
	}
	cachedSnapshotGlob = mv.glob;
	cachedSnapshotAt = now;
	cachedSnapshot = snap;
	return snap;
    }
    /**
     * Fallback footprint half-size / height when {@link MoonHitboxMode#getBBox} is unavailable
     * (same as previous X-Ray heuristic).
     */
    private static final float STUMP_HW = 2.0f;
    private static final float STUMP_HH = 4.5f;
    private static final float BUSH_HW = 4.0f;
    private static final float BUSH_HH = 10.0f;
    private static final float ROCK_HW = 5.0f;
    private static final float ROCK_HH = 8.0f;

    public static void draw(GOut g, MapView mv) {
        if (!MoonConfig.anyOverlayActive())
            return;
        Gob player = null;
        try { player = mv.player(); } catch (Exception e) { return; }
        if (player == null) return;

        Coord3f pc3;
        try { pc3 = player.getc(); } catch (Exception e) { return; }
        if (pc3 == null) return;

        Coord3f playerHead = new Coord3f(pc3.x, pc3.y, pc3.z + HEAD_Z);
        Coord3f phsc = safeScreenXF3(mv, playerHead);
        if (phsc == null) return;
        Coord psc = new Coord((int)phsc.x, (int)phsc.y);

        List<Gob> snapshot = snapshotGobs(mv);
        if(snapshot.isEmpty())
            return;

        for (Gob gob : snapshot) {
            if (gob == null || gob.id == mv.plgob) continue;
            try {
                Coord3f gc3 = gob.getc();
                if (gc3 == null) continue;

                Coord3f gobHead = new Coord3f(gc3.x, gc3.y, gc3.z + HEAD_Z);
                Coord3f ghsc = safeScreenXF3(mv, gobHead);
                if (ghsc == null || ghsc.z < 0 || ghsc.z > 1) continue;

                Coord gsc = new Coord((int)ghsc.x, (int)ghsc.y);
                if (gsc.x < -400 || gsc.y < -400 || gsc.x > mv.sz.x + 400 || gsc.y > mv.sz.y + 400)
                    continue;

                MoonCropMode.drawStage(g, mv, gob, gc3);

                GobType type = classifyGob(gob);
                if (type == GobType.UNKNOWN) continue;

                double dist = player.rc.dist(gob.rc) / 11.0;

                MoonEspProfile prof = MoonEspProfile.forType(type);

                if (MoonConfig.anyTraceActive())
                    drawTrace(g, psc, gsc, type, dist);

                if (prof != null && prof.enabled)
                    drawEsp3D(g, mv, gc3, gob, type, dist, prof);

                if (MoonConfig.aggroRadius && type == GobType.HOSTILE_MOB
                    && !mobDeadOrNoAggroDisplay(gob))
                    drawAggroRadius(g, mv, gob.rc);

            } catch (Loading ignored) {
            } catch (Exception ignored) {}
        }

        if (MoonConfig.xrayEnabled) {
            if(MoonConfig.xrayStyle == 1)
                drawXRayGroundTiles(g, mv);
            else
                drawXRayGhosts(g, mv);
        }

        g.chcolor();
    }

    /** Flat footprint on terrain (style 1): uses {@link MoonHitboxMode#getBBox} when possible. */
    private static void drawXRayGroundTiles(GOut g, MapView mv) {
        Gob player = null;
        try { player = mv.player(); } catch (Exception e) { return; }
        if(player == null || mv.glob == null || mv.glob.map == null) return;
        Coord2d prc = player.rc;
        double maxRaw = MoonConfig.xrayGhostMaxDist * 11.0;
        int cap = Math.max(32, Math.min(600, MoonConfig.xrayGhostMax));
        int drawn = 0;

        Color fill = MoonConfig.xrayColor();
        Color edge = new Color(255, 220, 220, 255);

        List<Gob> hidden;
        try {
            hidden = mv.hiddenGobs(MapView.HideReason.XRAY);
        } catch (Exception e) { return; }

        for(Gob gob : hidden) {
            if(gob == null) continue;
            if(drawn >= cap) break;
            try {
                double rawd = prc.dist(gob.rc);
                if(rawd > maxRaw) continue;

                Coord2d[][] bbox = null;
                try {
                    bbox = MoonHitboxMode.getBBox(gob);
                } catch(Loading ignored) {}
                boolean drew = false;
                if(bbox != null && bbox.length > 0) {
                    for(Coord2d[] poly : bbox) {
                        if(xrayFillGroundFootprint(g, mv, gob, poly, fill, edge))
                            drew = true;
                    }
                }
                if(drew) {
                    drawn++;
                    continue;
                }
                xrayDrawGroundDiamondFallback(g, mv, gob, fill, edge);
                drawn++;
            } catch(Loading ignored) {
            } catch(Exception ignored) {}
        }
    }

    /**
     * Local footprint vertex → world XY (same rotation as {@link MoonEntityHitboxViz#drawPolyGround}),
     * then terrain Z and screen.
     */
    private static boolean xrayFillGroundFootprint(GOut g, MapView mv, Gob gob, Coord2d[] poly,
	    Color fill, Color edge) {
	if(poly == null || poly.length < 3)
	    return(false);
	double sa = Math.sin(gob.a), ca = Math.cos(gob.a);
	int nIn = poly.length;
	Coord[] sc = new Coord[nIn];
	Coord2d[] wv = new Coord2d[nIn];
	int m = 0;
	for(int i = 0; i < nIn; i++) {
	    Coord2d p = poly[i];
	    if(p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y))
		return(false);
	    Coord2d rv = Coord2d.of((p.x * ca) - (p.y * sa), (p.y * ca) + (p.x * sa)).add(gob.rc);
	    Coord3f wz;
	    try {
		wz = mv.glob.map.getzp(rv);
	    } catch(Exception ex) {
		return(false);
	    }
	    if(wz == null)
		continue;
	    Coord3f s = MoonHitboxMode.hitboxScreenProject(mv, wz);
	    if(s == null)
		continue;
	    wv[m] = rv;
	    sc[m] = Coord.of(Math.round(s.x), Math.round(s.y));
	    m++;
	}
	if(m < 3)
	    return(false);
	xrayFillConvexPolygon(g, fill, sc, m);
	g.chcolor(edge);
	for(int i = 0; i < m; i++) {
	    int j = (i + 1) % m;
	    if(MoonHitboxMode.hitboxEdgeOk(sc[i], sc[j], wv[i], wv[j]))
		g.line(sc[i], sc[j], 1.25);
	}
	g.chcolor();
	return(true);
    }

    private static void xrayFillConvexPolygon(GOut g, Color fill, Coord[] sc, int m) {
	if(m < 3)
	    return;
	g.chcolor(fill);
	Coord t = g.tx;
	for(int i = 1; i < m - 1; i++) {
	    float[] data = {
		sc[0].x + t.x, sc[0].y + t.y,
		sc[i].x + t.x, sc[i].y + t.y,
		sc[i + 1].x + t.x, sc[i + 1].y + t.y
	    };
	    g.drawp(Model.Mode.TRIANGLES, data, 3);
	}
	g.chcolor();
    }

    private static void xrayDrawGroundDiamondFallback(GOut g, MapView mv, Gob gob, Color fill, Color edge) {
	String name = gobResName(gob);
	float[] dim = new float[2];
	xrayGhostDims(name, dim);
	double hw = dim[0];
	Coord2d c = gob.rc;
	Coord2d[] ring = {
	    c.add(0, -hw), c.add(hw, 0), c.add(0, hw), c.sub(hw, 0)
	};
	Coord[] sc = new Coord[4];
	int ok = 0;
	for(int i = 0; i < 4; i++) {
	    Coord3f wc;
	    try {
		wc = mv.glob.map.getzp(ring[i]);
	    } catch(Exception ex) {
		return;
	    }
	    Coord3f s = xrayCornerScreen(mv, wc);
	    if(s == null)
		continue;
	    sc[ok++] = new Coord((int)s.x, (int)s.y);
	}
	if(ok < 4)
	    return;
	fillQuad(g, fill, sc[0], sc[1], sc[2], sc[3]);
	g.chcolor(edge);
	for(int i = 0; i < 4; i++) {
	    int j = (i + 1) % 4;
	    g.line(sc[i], sc[j], 1.25);
	}
	g.chcolor();
    }

    private static void fillQuad(GOut g, Color col, Coord p0, Coord p1, Coord p2, Coord p3) {
        g.chcolor(col);
        Coord t = g.tx;
        float[] data = {
            p0.x + t.x, p0.y + t.y, p1.x + t.x, p1.y + t.y, p2.x + t.x, p2.y + t.y,
            p0.x + t.x, p0.y + t.y, p2.x + t.x, p2.y + t.y, p3.x + t.x, p3.y + t.y
        };
        g.drawp(Model.Mode.TRIANGLES, data, 6);
        g.chcolor();
    }

    private static Coord3f xrayWorldPos(Gob gob, MapView mv) {
        try {
            Coord3f wc = gob.getc();
            if (wc != null) return wc;
        } catch (Loading ignored) {}
        try {
            if (mv.glob != null && mv.glob.map != null)
                return mv.glob.map.getzp(new Coord2d(gob.rc.x, gob.rc.y));
        } catch (Exception ignored) {}
        return null;
    }

    private static void xrayGhostDims(String name, float[] out) {
        MoonGobKind.Kind kind = MoonGobKind.classify(name);
        if(kind == MoonGobKind.Kind.TREE || kind == MoonGobKind.Kind.STUMP) {
            out[0] = STUMP_HW; out[1] = STUMP_HH;
        } else if(kind == MoonGobKind.Kind.BUSH) {
            out[0] = BUSH_HW; out[1] = BUSH_HH;
        } else if(kind == MoonGobKind.Kind.BOULDER) {
            out[0] = ROCK_HW; out[1] = ROCK_HH;
        } else {
            if(name == null) {
                out[0] = TREE_HW; out[1] = TREE_HH;
            } else {
                out[0] = 4.0f; out[1] = 8.0f;
            }
        }
    }

    /**
     * Screen-space ghost rect (style 0): horizontal extent from real {@link MoonHitboxMode#getBBox} footprint;
     * vertical extent from per-kind {@link #xrayGhostDims} height on each vertex.
     */
    private static void drawXRayGhosts(GOut g, MapView mv) {
        Gob player = null;
        try { player = mv.player(); } catch (Exception e) { return; }
        if (player == null) return;
        Coord2d prc = player.rc;
        double maxRaw = MoonConfig.xrayGhostMaxDist * 11.0;
        int cap = Math.max(32, Math.min(600, MoonConfig.xrayGhostMax));
        int drawn = 0;

        Color base = MoonConfig.xrayColor();

        List<Gob> hidden;
        try {
            hidden = mv.hiddenGobs(MapView.HideReason.XRAY);
        } catch (Exception e) { return; }

        for (Gob gob : hidden) {
            if (gob == null) continue;
            if (drawn >= cap) break;
            try {
                double rawd = prc.dist(gob.rc);
                if (rawd > maxRaw) continue;

                String name = gobResName(gob);
                float[] dim = new float[2];
                xrayGhostDims(name, dim);
                float bot = -2.0f;
                float top = dim[1];

                Coord2d[][] bbox = null;
                try {
                    bbox = MoonHitboxMode.getBBox(gob);
                } catch (Loading ignored) {}

                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                int valid = 0;

                if (bbox != null && bbox.length > 0) {
                    double sa = Math.sin(gob.a), ca = Math.cos(gob.a);
                    for (Coord2d[] poly : bbox) {
                        if (poly == null) continue;
                        for (Coord2d p : poly) {
                            if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y)) continue;
                            Coord2d rv = Coord2d.of((p.x * ca) - (p.y * sa), (p.y * ca) + (p.x * sa)).add(gob.rc);
                            Coord3f wz;
                            try {
                                wz = mv.glob.map.getzp(rv);
                            } catch (Exception ex) {
                                continue;
                            }
                            if (wz == null) continue;
                            Coord3f b = new Coord3f(wz.x, wz.y, wz.z + bot);
                            Coord3f t = new Coord3f(wz.x, wz.y, wz.z + top);
                            for (Coord3f corner : new Coord3f[] { b, t }) {
                                Coord3f s = MoonHitboxMode.hitboxScreenProject(mv, corner);
                                if (s == null) continue;
                                valid++;
                                int sx = (int) s.x, sy = (int) s.y;
                                if (sx < minX) minX = sx;
                                if (sx > maxX) maxX = sx;
                                if (sy < minY) minY = sy;
                                if (sy > maxY) maxY = sy;
                            }
                        }
                    }
                }

                if (valid < 4) {
                    minX = Integer.MAX_VALUE;
                    minY = Integer.MAX_VALUE;
                    maxX = Integer.MIN_VALUE;
                    maxY = Integer.MIN_VALUE;
                    valid = 0;
                    Coord3f gc3 = xrayWorldPos(gob, mv);
                    if (gc3 == null) continue;
                    float hw = dim[0];
                    float cx = gc3.x, cy = gc3.y, cz = gc3.z;
                    Coord3f[] corners = new Coord3f[8];
                    corners[0] = new Coord3f(cx - hw, cy - hw, cz + bot);
                    corners[1] = new Coord3f(cx + hw, cy - hw, cz + bot);
                    corners[2] = new Coord3f(cx + hw, cy + hw, cz + bot);
                    corners[3] = new Coord3f(cx - hw, cy + hw, cz + bot);
                    corners[4] = new Coord3f(cx - hw, cy - hw, cz + top);
                    corners[5] = new Coord3f(cx + hw, cy - hw, cz + top);
                    corners[6] = new Coord3f(cx + hw, cy + hw, cz + top);
                    corners[7] = new Coord3f(cx - hw, cy + hw, cz + top);
                    for (int i = 0; i < 8; i++) {
                        Coord3f s = xrayCornerScreen(mv, corners[i]);
                        if (s == null) continue;
                        valid++;
                        int sx = (int) s.x, sy = (int) s.y;
                        if (sx < minX) minX = sx;
                        if (sx > maxX) maxX = sx;
                        if (sy < minY) minY = sy;
                        if (sy > maxY) maxY = sy;
                    }
                }

                if (valid < 4) continue;

                int mx = Math.max(1, mv.sz.x), my = Math.max(1, mv.sz.y);
                if ((maxX < 0) || (maxY < 0) || (minX >= mx) || (minY >= my))
                    continue;
                minX = Math.max(minX, 0);
                minY = Math.max(minY, 0);
                maxX = Math.min(maxX, mx - 1);
                maxY = Math.min(maxY, my - 1);
                int rw = maxX - minX + 1;
                int rh = maxY - minY + 1;
                if (rw < 2 || rh < 2) continue;

                Coord ul = Coord.of(minX, minY);
                Coord sz = Coord.of(rw, rh);
                g.chcolor(base);
                g.frect(ul, sz);
                g.chcolor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, base.getAlpha() + 60)));
                g.rect(ul, sz);
                drawn++;
            } catch (Loading ignored) {
            } catch (Exception ignored) {}
        }
        g.chcolor();
    }

    private static Coord3f safeScreenXF3(MapView mv, Coord3f wc) {
        try {
            Coord3f s = mv.screenxf(wc);
            if (s == null)
                return null;
            if (!Float.isFinite(s.x) || !Float.isFinite(s.y))
                return null;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** Like {@link #safeScreenXF3} but rejects off-frustum / behind-camera garbage (X-Ray ghost corners). */
    private static Coord3f xrayCornerScreen(MapView mv, Coord3f wc) {
        Coord3f s = safeScreenXF3(mv, wc);
        if (s == null)
            return null;
        if (s.z < -0.05f || s.z > 1.05f)
            return null;
        return s;
    }

    private static void drawTrace(GOut g, Coord from, Coord to, GobType type, double dist) {
        Color col = null;
        switch(type) {
            case HOSTILE_MOB:
                if (MoonConfig.traceHostile) col = COL_HOSTILE;
                break;
            case PLAYER:
                if (MoonConfig.tracePlayers) col = COL_PLAYER;
                break;
            case NEUTRAL_MOB:
                if (MoonConfig.traceNeutralMobs) col = COL_NEUTRAL;
                break;
            default: break;
        }
        if (col == null) return;
        g.chcolor(col);
        g.line(from, to, 1.5);
        Coord mid = Coord.of((from.x + to.x) / 2, (from.y + to.y) / 2);
        drawOverlayText(g, String.format("%.0f", dist), mid, 0.5, 0.5, new Color(col.getRed(), col.getGreen(), col.getBlue(), 255));
        g.chcolor();
    }

    private static void drawEsp3D(GOut g, MapView mv, Coord3f gc, Gob gob, GobType type, double dist, MoonEspProfile prof) {
        Color bc = prof.color;

        float hw, top, bot;
        switch(type) {
            case VEHICLE:      hw = 8.0f; top = 14.0f; bot = -2.0f; break;
            case BUILDING:     hw = 7.0f; top = 20.0f; bot = -2.0f; break;
            case RESOURCE_NODE:hw = 5.0f; top = 10.0f; bot = -1.0f; break;
            case STOCKPILE:    hw = 5.0f; top = 6.0f;  bot = -1.0f; break;
            case CONTAINER:    hw = 4.0f; top = 8.0f;  bot = -1.0f; break;
            case HERB:         hw = 2.5f; top = 5.0f;  bot = -0.5f; break;
            case DUNGEON:      hw = 6.0f; top = 15.0f; bot = -2.0f; break;
            case WORKSTATION:  hw = 5.0f; top = 10.0f; bot = -1.0f; break;
            case ITEM:         hw = (float) prof.itemBoxHalfW(); top = 5.0f; bot = -0.5f; break;
            default:           hw = BOX_HW; top = BOX_HH; bot = -2.0f; break;
        }

        float cx = gc.x, cy = gc.y, cz = gc.z;
        Coord3f[] corners = new Coord3f[8];
        corners[0] = new Coord3f(cx - hw, cy - hw, cz + bot);
        corners[1] = new Coord3f(cx + hw, cy - hw, cz + bot);
        corners[2] = new Coord3f(cx + hw, cy + hw, cz + bot);
        corners[3] = new Coord3f(cx - hw, cy + hw, cz + bot);
        corners[4] = new Coord3f(cx - hw, cy - hw, cz + top);
        corners[5] = new Coord3f(cx + hw, cy - hw, cz + top);
        corners[6] = new Coord3f(cx + hw, cy + hw, cz + top);
        corners[7] = new Coord3f(cx - hw, cy + hw, cz + top);

        Coord[] sc = new Coord[8];
        for (int i = 0; i < 8; i++) {
            Coord3f s = safeScreenXF3(mv, corners[i]);
            if (s == null) return;
            sc[i] = new Coord((int)s.x, (int)s.y);
        }

        g.chcolor(bc);
        for (int i = 0; i < 4; i++) {
            g.line(sc[i], sc[(i + 1) % 4], 1.0);
            g.line(sc[i + 4], sc[((i + 1) % 4) + 4], 1.0);
            g.line(sc[i], sc[i + 4], 1.0);
        }

        Coord topCenter = Coord.of(
            (sc[4].x + sc[5].x + sc[6].x + sc[7].x) / 4,
            (sc[4].y + sc[5].y + sc[6].y + sc[7].y) / 4
        );

        int ty = topCenter.y - UI.scale(2);
                if (prof.showName) {
            String name = (type == GobType.ITEM) ? gobItemDisplayName(gob) : gobDisplayName(gob);
            if (name != null && !name.isEmpty()) {
                g.chcolor(Color.WHITE);
                if (type == GobType.ITEM) {
                    Tex tex = itemTextTex(name, prof.itemLabelPx(), Color.WHITE);
                    if(tex != null) {
                        g.aimage(tex, Coord.of(topCenter.x, ty), 0.5, 1.0);
                        ty -= tex.sz().y + UI.scale(2);
                    }
                } else {
                    drawOverlayText(g, name, Coord.of(topCenter.x, ty), 0.5, 1.0, MoonConfig.overlayTextColor());
                    ty -= UI.scale(MoonConfig.overlayTextSize + 2);
                }
            }
        }
        if (prof.showDist) {
            drawOverlayText(g, String.format("%.0fm", dist), Coord.of(topCenter.x, ty), 0.5, 1.0,
                new Color(220, 220, 220, 220));
            ty -= UI.scale(MoonConfig.overlayTextSize + 2);
        }
        if (prof.showSpeed) {
            double spd = 0;
            try { spd = gob.getv(); } catch (Exception ignored) {}
            if (spd > 0.01) {
                drawOverlayText(g, String.format("%.1f", spd), Coord.of(topCenter.x, ty), 0.5, 1.0,
                    new Color(180, 255, 180, 230));
            }
        }
        g.chcolor();
    }

    /** Hostile critter that should count for aggro / safe-mode (not dead/KO). */
    public static boolean isThreatMob(Gob gob) {
	return classifyGob(gob) == GobType.HOSTILE_MOB && !mobDeadOrNoAggroDisplay(gob);
    }

    private static boolean mobDeadOrNoAggroDisplay(Gob gob) {
	GobHealth gh = gob.getattr(GobHealth.class);
	if(gh != null && gh.hp <= 0f)
	    return(true);
	Drawable d = gob.getattr(Drawable.class);
	if(!(d instanceof Composite))
	    return(false);
	Composite cmp = (Composite) d;
	Composited.Poses poses = cmp.comp.poses;
	if(poses == null || poses.mods == null)
	    return(false);
	for(Skeleton.PoseMod m : poses.mods) {
	    String s = m.toString().toLowerCase(Locale.ROOT);
	    if(s.contains("/dead") || s.contains("/knock") || s.contains("dying"))
		return(true);
	}
	return(false);
    }

    private static void drawAggroRadius(GOut g, MapView mv, Coord2d center) {
        drawWorldCircle(g, mv, center, AGGRO_STRONG, COL_AGGRO_STRONG, 2.0);
        drawWorldCircle(g, mv, center, AGGRO_MEDIUM, COL_AGGRO_MEDIUM, 1.5);
        drawWorldCircle(g, mv, center, AGGRO_WEAK,   COL_AGGRO_WEAK,   1.0);
    }

    private static void drawWorldCircle(GOut g, MapView mv, Coord2d center, double radius, Color col, double lineW) {
        Coord[] pts = new Coord[CIRCLE_SEGMENTS + 1];
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            double angle = (2.0 * Math.PI * i) / CIRCLE_SEGMENTS;
            double wx = center.x + Math.cos(angle) * radius;
            double wy = center.y + Math.sin(angle) * radius;
            Coord3f wc;
            try {
                wc = mv.glob.map.getzp(new Coord2d(wx, wy));
            } catch (Exception e) {
                return;
            }
            Coord3f sc = safeScreenXF3(mv, wc);
            if (sc == null) return;
            pts[i] = new Coord((int)sc.x, (int)sc.y);
        }
        g.chcolor(col);
        for (int i = 0; i < CIRCLE_SEGMENTS; i++)
            g.line(pts[i], pts[i + 1], lineW);
    }

    /**
     * Primary drawable resource name; matches {@link MoonHitboxMode#drawableResource} so X-Ray / hitbox
     * logic sees the mesh resource (e.g. {@link SprDrawable} has {@code getres() == null} but {@code spr.res} set).
     */
    static String gobResName(Gob gob) {
        return MoonGobKind.resourceName(gob);
    }

    private static String gobDisplayName(Gob gob) {
        String full = gobResName(gob);
        if (full == null) return null;
        int p = full.lastIndexOf('/');
        return (p >= 0) ? full.substring(p + 1) : full;
    }

    /** Tooltip or resource basename for items on the ground. */
    private static String gobItemDisplayName(Gob gob) {
        try {
            Drawable d = gob.getattr(Drawable.class);
            if (d instanceof ResDrawable) {
                Resource r = d.getres();
                if (r != null) {
                    Resource.Tooltip tt = r.layer(Resource.tooltip);
                    if (tt != null && !tt.t.isEmpty())
                        return tt.t;
                }
            }
        } catch (Loading ignored) {}
        return gobDisplayName(gob);
    }

    public static GobType classifyGob(Gob gob) {
        String name = gobResName(gob);
        if (name == null) return GobType.UNKNOWN;
        MoonGobKind.Kind kind = MoonGobKind.classify(name);
        if (name.contains("gfx/borka"))
            return GobType.PLAYER;
        if (name.contains("gfx/kritter/")) {
            String sub = name.substring(name.indexOf("gfx/kritter/") + 12);
            /* Hostile id may be a later segment (e.g. …/herd/wildgoat/male), not only the first folder. */
            if (kritterSubpathHasHostile(sub))
                return GobType.HOSTILE_MOB;
            return GobType.NEUTRAL_MOB;
        }
        if (name.contains("gfx/terobjs/vehicle/"))
            return GobType.VEHICLE;
        if (name.contains("gfx/terobjs/arch/"))
            return GobType.BUILDING;
        if (name.contains("gfx/terobjs/geyser") || name.contains("gfx/terobjs/claypit")
            || name.contains("gfx/terobjs/saltbasin") || name.contains("gfx/terobjs/abyssalchasm")
            || name.contains("gfx/terobjs/windthrow") || name.contains("gfx/terobjs/icespire")
            || name.contains("gfx/terobjs/woodheart") || name.contains("gfx/terobjs/crystalpatch")
            || name.contains("gfx/terobjs/guanopile") || name.contains("gfx/terobjs/caveorgan")
            || name.contains("gfx/terobjs/fairystone") || name.contains("gfx/terobjs/jotunmussel")
            || name.contains("gfx/terobjs/lilypadlotus"))
            return GobType.RESOURCE_NODE;
        if (name.contains("gfx/terobjs/stockpile"))
            return GobType.STOCKPILE;
        if (name.contains("gfx/terobjs/crate") || name.contains("gfx/terobjs/chest")
            || name.contains("gfx/terobjs/largechest") || name.contains("gfx/terobjs/cupboard")
            || name.contains("gfx/terobjs/woodbox") || name.contains("gfx/terobjs/wbasket")
            || name.contains("gfx/terobjs/barrel") || name.contains("gfx/terobjs/matalcabinet"))
            return GobType.CONTAINER;
        if (name.contains("gfx/terobjs/herbs/"))
            return GobType.HERB;
        if (name.contains("gfx/terobjs/dng/") || name.contains("gfx/terobjs/wonders/"))
            return GobType.DUNGEON;
        if (MoonGobKind.isWorkstation(kind))
            return GobType.WORKSTATION;
        if (name.contains("/items/") || name.contains("/item/") || name.contains("gfx/terobjs/pickup")
            || name.contains("gfx/terobjs/heap"))
            return GobType.ITEM;
        return GobType.UNKNOWN;
    }

    private static boolean kritterSubpathHasHostile(String sub) {
        if (sub == null || sub.isEmpty()) return false;
        for (int start = 0; start < sub.length(); ) {
            int slash = sub.indexOf('/', start);
            String part = (slash < 0) ? sub.substring(start) : sub.substring(start, slash);
            if (!part.isEmpty() && isHostileAnimal(part))
                return true;
            if (slash < 0) break;
            start = slash + 1;
        }
        return false;
    }

    private static boolean isHostileAnimal(String n) {
        switch(n) {
            case "bear": case "lynx": case "wolf": case "boar":
            case "badger": case "walrus": case "mammoth": case "troll":
            case "bat": case "adder": case "orca": case "moose":
            case "caverat": case "goldeneagle": case "wolverine":
            case "greyseal": case "wildhorse":
            case "spermwhale": case "nidbane":
            case "wildgoat": case "goat": case "scorpion": case "woodscorpion":
                return true;
        }
        return false;
    }

    public enum GobType {
        PLAYER, HOSTILE_MOB, NEUTRAL_MOB,
        VEHICLE, BUILDING, RESOURCE_NODE, STOCKPILE,
        CONTAINER, HERB, DUNGEON, WORKSTATION, ITEM,
        UNKNOWN
    }
}
