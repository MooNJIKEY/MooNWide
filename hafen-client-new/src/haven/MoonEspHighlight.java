package haven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies color tint to mobs/players in the 3D scene for ESP highlighting.
 * Runs each tick to apply/remove GobAlpha attributes on relevant gobs.
 */
public class MoonEspHighlight {
    private static final Set<Long> tinted = new HashSet<>();
    private static boolean wasActive = false;
    private static final double SCAN_INTERVAL = 0.15;
    private static double lastScan = 0;

    public static void tick(MapView mv) {
        boolean active = MoonConfig.espEnabled;

        if (!active && !wasActive) return;

        if (!active && wasActive) {
            clearAll(mv);
            wasActive = false;
            lastScan = 0;
            return;
        }
        wasActive = true;
        double now = Utils.rtime();
        if((now - lastScan) < SCAN_INTERVAL)
            return;
        lastScan = now;

        if (mv.glob == null || mv.glob.oc == null) return;

        Set<Long> seen = new HashSet<>();
        List<Gob> snapshot = new ArrayList<>();
        try {
            synchronized (mv.glob.oc) {
                for (Gob gob : mv.glob.oc)
                    snapshot.add(gob);
            }
        } catch (Exception e) { return; }

        for (Gob gob : snapshot) {
            if (gob.id == mv.plgob) continue;
            try {
                MoonOverlay.GobType type = MoonOverlay.classifyGob(gob);
                if (type == MoonOverlay.GobType.UNKNOWN) continue;

                seen.add(gob.id);
                float r, g, b, a;
                switch (type) {
                    case HOSTILE_MOB:
                        r = 1.0f; g = 0.15f; b = 0.15f; a = 0.35f; break;
                    case PLAYER:
                        r = 0.2f; g = 0.8f; b = 1.0f; a = 0.3f; break;
                    case NEUTRAL_MOB:
                        r = 1.0f; g = 0.85f; b = 0.2f; a = 0.25f; break;
                    default: continue;
                }
                GobAlpha existing = gob.getattr(GobAlpha.class);
                if (existing == null) {
                    gob.setattr(new GobAlpha(gob, r, g, b, a));
                }
                tinted.add(gob.id);
            } catch (Loading ignored) {
            } catch (Exception ignored) {}
        }

        List<Long> toRemove = new ArrayList<>();
        for (Long id : tinted) {
            if (!seen.contains(id))
                toRemove.add(id);
        }
        for (Long id : toRemove) {
            tinted.remove(id);
            Gob gob = mv.glob.oc.getgob(id);
            if (gob != null) {
                try { gob.setattr(new GobAlpha(gob, 0, 0, 0, 0)); } catch (Exception ignored) {}
            }
        }
    }

    private static void clearAll(MapView mv) {
        if (mv.glob == null || mv.glob.oc == null) return;
        for (Long id : tinted) {
            Gob gob = mv.glob.oc.getgob(id);
            if (gob != null) {
                try { gob.setattr(new GobAlpha(gob, 0, 0, 0, 0)); } catch (Exception ignored) {}
            }
        }
        tinted.clear();
    }
}
