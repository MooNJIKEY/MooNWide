package haven;

import java.awt.Color;
import java.awt.Font;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight mine-sweeper overlay: draws small persistent digits for manually-known cave cells.
 * Walls show 0/1 (safe / collapse), dug floor cells show the persisted cavewarn clue.
 */
public final class MoonMineSweeperOverlay {
    private MoonMineSweeperOverlay() {}

    private static final int VIEW_RADIUS = 30;
    private static final float LABEL_Z = 8f;
    private static final Color[] CLUE_COLORS = {
        new Color(232, 236, 248),
        new Color(164, 210, 255),
        new Color(150, 228, 170),
        new Color(255, 224, 156),
        new Color(255, 190, 150),
        new Color(255, 160, 160),
        new Color(220, 190, 255),
        new Color(210, 210, 220),
        new Color(245, 245, 245),
    };
    private static final Map<String, Tex> labelCache = new HashMap<>();
    private static Text.Foundry digitFurn = null;
    private static int digitFurnSize = -1;

    public static void draw(GOut g, MapView mv) {
        if(!MoonConfig.mineSweeperShowLabels)
            return;
        Gob pl;
        try {
            pl = mv.player();
        } catch(Loading e) {
            return;
        }
        if(pl == null || mv.glob == null || mv.glob.map == null)
            return;
        Coord ptc = pl.rc.floor(MCache.tilesz);
        Area view = Area.sized(ptc.sub(VIEW_RADIUS, VIEW_RADIUS), Coord.of(VIEW_RADIUS * 2 + 1, VIEW_RADIUS * 2 + 1));
        Map<Coord, Byte> marks = MoonMineSweeperData.snapshot(view);
        Map<Coord, Integer> clues = MoonMineSweeperData.clueSnapshot(view);
        if(marks.isEmpty() && clues.isEmpty())
            return;
        Set<Coord> supported = MoonMiningOverlay.supportedTileSnapshot(mv);

        MCache map = mv.glob.map;
        for(int y = view.ul.y; y < view.br.y; y++) {
            for(int x = view.ul.x; x < view.br.x; x++) {
                Coord tc = Coord.of(x, y);
                byte state = marks.getOrDefault(tc, MoonMineSweeperData.UNKNOWN);
                int clue = clues.getOrDefault(tc, MoonMineSweeperData.NO_CLUE);
                boolean floor = MoonMiningOverlay.isCaveMineFloorTile(map, tc);
                if(floor) {
                    if(clue == MoonMineSweeperData.NO_CLUE) {
                        if(state == MoonMineSweeperData.SAFE)
                            drawDot(g, mv, tc, safeColor());
                        else if(state == MoonMineSweeperData.AUTO_SAFE)
                            drawLabel(g, mv, tc, "+", autoSafeColor());
                        else if(state == MoonMineSweeperData.RISK)
                            drawLabel(g, mv, tc, "x", riskColor());
                        continue;
                    }
                    if(clue == 0) {
                        drawDot(g, mv, tc, safeColor());
                        if(supported.contains(tc))
                            drawDot(g, mv, tc, new Color(242, 255, 244), AUTO_DOT_R);
                    } else {
                        drawLabel(g, mv, tc, Integer.toString(clue), clueColor(clue));
                    }
                } else {
                    if(!MoonMiningOverlay.isCaveMineWallTile(map, tc))
                        continue;
                    if(state != MoonMineSweeperData.SAFE && state != MoonMineSweeperData.RISK && state != MoonMineSweeperData.AUTO_SAFE)
                        continue;
                    drawWallMark(g, mv, tc, state);
                }
            }
        }
    }

    private static void drawLabel(GOut g, MapView mv, Coord tc, String text, Color color) {
        Coord sc = screenForTile(mv, tc);
        if(sc == null)
            return;
        Tex tex = label(text, color);
        g.aimage(tex, sc, 0.5, 0.5);
    }

    private static void drawDot(GOut g, MapView mv, Coord tc, Color color) {
        drawDot(g, mv, tc, color, dotRadius());
    }

    private static void drawDot(GOut g, MapView mv, Coord tc, Color color, int r) {
        Coord sc = screenForTile(mv, tc);
        if(sc == null)
            return;
        g.chcolor(color);
        g.fellipse(sc, Coord.of(r, r));
        g.chcolor();
    }

    private static void drawWallMark(GOut g, MapView mv, Coord tc, byte state) {
        if(state == MoonMineSweeperData.RISK) {
            drawLabel(g, mv, tc, "x", riskColor());
        } else if(state == MoonMineSweeperData.AUTO_SAFE) {
            drawLabel(g, mv, tc, "+", autoSafeColor());
        } else {
            drawDot(g, mv, tc, safeColor(), dotRadius());
        }
    }

    private static Coord screenForTile(MapView mv, Coord tc) {
        try {
            Coord2d mc = Coord2d.of((tc.x + 0.5) * MCache.tilesz.x, (tc.y + 0.5) * MCache.tilesz.y);
            Coord3f sc = mv.screenxf(mv.glob.map.getzp(mc).add(0, 0, LABEL_Z));
            if(sc == null || !Float.isFinite(sc.x) || !Float.isFinite(sc.y))
                return null;
            Coord c = Coord.of(Math.round(sc.x), Math.round(sc.y));
            if(c.x < -64 || c.y < -64 || c.x > mv.sz.x + 64 || c.y > mv.sz.y + 64)
                return null;
            return c;
        } catch(Loading e) {
            return null;
        } catch(Exception e) {
            return null;
        }
    }

    private static Tex label(String text, Color color) {
        ensureFoundry();
        String key = text + "|" + (color.getRGB() & 0xffffffffL);
        synchronized(labelCache) {
            Tex tex = labelCache.get(key);
            if(tex == null) {
                tex = new TexI(Utils.outline2(digitFurn.render(text, color).img, new Color(10, 12, 18)));
                labelCache.put(key, tex);
            }
            return tex;
        }
    }

    private static void ensureFoundry() {
        int size = MoonConfig.mineSweeperTextSize;
        if(digitFurn != null && digitFurnSize == size)
            return;
        synchronized(labelCache) {
            if(digitFurn != null && digitFurnSize == size)
                return;
            for(Tex tex : labelCache.values())
                tex.dispose();
            labelCache.clear();
            digitFurn = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale((float)size)), Color.WHITE).aa(true);
            digitFurnSize = size;
        }
    }

    private static int dotRadius() {
        return Math.max(UI.scale(3), UI.scale(Math.max(2, MoonConfig.mineSweeperTextSize / 4)));
    }

    private static final int AUTO_DOT_R = UI.scale(2);

    private static Color safeColor() {
        return MoonConfig.mineSweeperSafeColor();
    }

    private static Color autoSafeColor() {
        return MoonConfig.mineSweeperAutoSafeColor();
    }

    private static Color riskColor() {
        return MoonConfig.mineSweeperRiskColor();
    }

    private static Color clueColor(int clue) {
        if(clue == 0)
            return safeColor();
        if(clue < 0 || clue >= CLUE_COLORS.length)
            return safeColor();
        return CLUE_COLORS[clue];
    }

}
