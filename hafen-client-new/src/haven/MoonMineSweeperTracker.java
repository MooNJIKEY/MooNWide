package haven;

import java.util.Locale;

/**
 * Observes the player's mining animation and converts a successful dig with no cavewarn clue
 * into a persistent minesweeper zero. This makes safe cells appear for both manual mining and the bot.
 */
public final class MoonMineSweeperTracker {
    private MoonMineSweeperTracker() {}

    private static final double CLUE_SETTLE_SEC = 0.85;
    private static final double PENDING_TIMEOUT_SEC = 4.00;
    private static Coord activeTile = null;
    private static Coord pendingTile = null;
    private static double pendingSince = 0;

    public static void tick(GameUI gui, double dt) {
        if(gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null)
            return;
        Gob pl;
        try {
            pl = gui.map.player();
        } catch(Loading e) {
            return;
        }
        if(pl == null)
            return;
        double now = Utils.rtime();
        flushPending(gui.map.glob.map, now);

        Coord cur = currentMiningTile(pl, gui.map.glob.map);
        if(cur != null) {
            if(activeTile != null && !activeTile.equals(cur))
                beginPending(gui.map.glob.map, activeTile, now);
            activeTile = cur;
        } else if(activeTile != null) {
            beginPending(gui.map.glob.map, activeTile, now);
            activeTile = null;
        }
    }

    private static void flushPending(MCache map, double now) {
        if(pendingTile == null)
            return;
        if(MoonMineSweeperData.clue(pendingTile) != MoonMineSweeperData.NO_CLUE) {
            pendingTile = null;
            return;
        }
        if(now - pendingSince > PENDING_TIMEOUT_SEC) {
            pendingTile = null;
            return;
        }
        if(!MoonMiningOverlay.isCaveMineFloorTile(map, pendingTile))
            return;
        if(now - pendingSince < CLUE_SETTLE_SEC)
            return;
        MoonMineSweeperData.observeClue(pendingTile, 0);
        pendingTile = null;
    }

    private static void beginPending(MCache map, Coord tc, double now) {
        if(tc == null || (!MoonMiningOverlay.isCaveMineFloorTile(map, tc) && !MoonMiningOverlay.isCaveMineWallTile(map, tc)))
            return;
        pendingTile = tc;
        pendingSince = now;
    }

    private static Coord currentMiningTile(Gob gob, MCache map) {
        if(!playerIsMining(gob))
            return(null);
        Coord ptc = gob.rc.floor(MCache.tilesz);
        Coord2d forward = new Coord2d(
            gob.rc.x + (Math.cos(gob.a) * (MCache.tilesz.x * 1.05)),
            gob.rc.y + (Math.sin(gob.a) * (MCache.tilesz.y * 1.05)));
        Coord ftc = forward.floor(MCache.tilesz);
        Coord best = null;
        double bestScore = Double.MAX_VALUE;
        for(int dy = -1; dy <= 1; dy++) {
            for(int dx = -1; dx <= 1; dx++) {
                Coord tc = ftc.add(dx, dy);
                if(!MoonMiningOverlay.isCaveMineWallTile(map, tc))
                    continue;
                if(!hasAdjacentFloor(map, tc))
                    continue;
                Coord2d ctr = tileCenterWorld(tc);
                double dist = ctr.dist(forward);
                double pldist = ctr.dist(gob.rc);
                double score = dist + (pldist * 0.15);
                if(best == null || score < bestScore) {
                    best = tc;
                    bestScore = score;
                }
            }
        }
        if(best != null)
            return best;
        for(int dy = -1; dy <= 1; dy++) {
            for(int dx = -1; dx <= 1; dx++) {
                Coord tc = ptc.add(dx, dy);
                if(MoonMiningOverlay.isCaveMineWallTile(map, tc) && hasAdjacentFloor(map, tc))
                    return tc;
            }
        }
        return ftc;
    }

    private static Coord2d tileCenterWorld(Coord tc) {
        return new Coord2d((tc.x + 0.5) * MCache.tilesz.x, (tc.y + 0.5) * MCache.tilesz.y);
    }

    private static boolean hasAdjacentFloor(MCache map, Coord tc) {
        for(int dy = -1; dy <= 1; dy++) {
            for(int dx = -1; dx <= 1; dx++) {
                if(dx == 0 && dy == 0)
                    continue;
                if(MoonMiningOverlay.isCaveMineFloorTile(map, tc.add(dx, dy)))
                    return true;
            }
        }
        return false;
    }

    private static boolean playerIsMining(Gob gob) {
        try {
            Drawable d = gob.getattr(Drawable.class);
            if(!(d instanceof Composite))
                return(false);
            Composited.Poses poses = ((Composite)d).comp.poses;
            if(poses == null || poses.mods == null)
                return(false);
            for(Skeleton.PoseMod mod : poses.mods) {
                String s = mod.toString().toLowerCase(Locale.ROOT);
                if(s.contains("pickan") || s.contains("mine"))
                    return(true);
            }
        } catch(Exception ignored) {
        }
        return(false);
    }
}
