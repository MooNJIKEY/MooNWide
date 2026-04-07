package haven;

import java.util.ArrayList;
import java.util.List;

public class MoonXRay {
    private static boolean wasActive = false;
    private static double lastScan = 0;
    private static final double SCAN_INTERVAL = 0.25;

    public static void tick(MapView mv) {
        boolean active = MoonConfig.xrayEnabled && MoonConfig.gfxModXray;

        if (!active && wasActive) {
            showAll(mv);
            wasActive = false;
            return;
        }

        if (!active) return;

        wasActive = true;

        double now = Utils.rtime();
        if (now - lastScan < SCAN_INTERVAL) return;
        lastScan = now;

        hideObstacles(mv);
        showNoLongerHidden(mv);
    }

    private static void hideObstacles(MapView mv) {
        if (mv.glob == null || mv.glob.oc == null) return;
        List<Gob> toHide = new ArrayList<>();
        try {
            synchronized (mv.glob.oc) {
                for (Gob gob : mv.glob.oc) {
                    if (shouldHide(gob) && !mv.isHiddenBy(gob, MapView.HideReason.XRAY))
                        toHide.add(gob);
                }
            }
        } catch (Exception ignored) { return; }
        for (Gob gob : toHide) {
            try { mv.hideGob(gob, MapView.HideReason.XRAY); } catch (Exception ignored) {}
        }
    }

    private static void showNoLongerHidden(MapView mv) {
        if (mv.glob == null || mv.glob.oc == null) return;
        List<Gob> toShow = new ArrayList<>();
        try {
            for (Gob gob : mv.hiddenGobs(MapView.HideReason.XRAY)) {
                if (gob == null)
                    continue;
                if (mv.glob.oc.getgob(gob.id) == null)
                    continue;
                if (shouldHide(gob))
                    continue;
                toShow.add(gob);
            }
        } catch (Exception ignored) {
            return;
        }
        for (Gob gob : toShow) {
            try { mv.showGob(gob, MapView.HideReason.XRAY); } catch (Exception ignored) {}
        }
    }

    private static void showAll(MapView mv) {
        List<Gob> toShow = mv.hiddenGobs(MapView.HideReason.XRAY);
        for (Gob gob : toShow) {
            try { mv.showGob(gob, MapView.HideReason.XRAY); } catch (Exception ignored) {}
        }
    }

    static boolean shouldHide(Gob gob) {
        return MoonGobKind.isXrayHideTarget(MoonGobKind.classify(gob), MoonConfig.xrayHideMode);
    }
}
