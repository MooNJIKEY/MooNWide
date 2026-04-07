package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class MoonCropMode {
    private MoonCropMode() {}

    private static final String CROP_PREFIX = "gfx/terobjs/plants/";
    private static final double SCAN_INTERVAL = 0.20;
    private static final int TEXT_CACHE_MAX = 128;

    private static double lastScan = 0;
    private static boolean wasActive = false;
    private static int stageFontPx = -1;
    private static Text.Foundry stageFoundry;
    private static final Map<String, Tex> stageTextCache = new LinkedHashMap<String, Tex>(48, 0.75f, true) {
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

    public static boolean isCrop(Gob gob) {
        return isCropRes(MoonGobKind.resourceName(gob));
    }

    public static boolean isCropRes(String name) {
        return (name != null) && name.startsWith(CROP_PREFIX);
    }

    static boolean wantsHidden(Gob gob) {
        return MoonConfig.cropHide && isCrop(gob);
    }

    public static void tick(MapView mv) {
        if(mv == null || mv.glob == null || mv.glob.oc == null)
            return;
        if(!MoonConfig.cropHide) {
            if(wasActive)
                restoreHidden(mv);
            wasActive = false;
            return;
        }
        wasActive = true;
        double now = Utils.rtime();
        if((now - lastScan) < SCAN_INTERVAL)
            return;
        lastScan = now;
        syncHidden(mv);
    }

    public static void refresh(MapView mv) {
        lastScan = 0;
        if(mv == null)
            return;
        if(MoonConfig.cropHide)
            syncHidden(mv);
        else
            restoreHidden(mv);
    }

    public static void drawStage(GOut g, MapView mv, Gob gob, Coord3f gc3) {
        if(!MoonConfig.cropShowStage || gob == null || gc3 == null || !isCrop(gob))
            return;
        if(mv != null && mv.isHidden(gob))
            return;
        int stage = cropStage(gob);
        if(stage == Integer.MIN_VALUE)
            return;
        try {
            float zoff = 8.0f * (MoonConfig.cropScalePct / 100.0f);
            Coord3f sc3 = mv.screenxf(new Coord3f(gc3.x, gc3.y, gc3.z + zoff));
            if(sc3 == null || !Float.isFinite(sc3.x) || !Float.isFinite(sc3.y) || !Float.isFinite(sc3.z))
                return;
            if(sc3.z < -0.05f || sc3.z > 1.05f)
                return;
            Coord sc = new Coord((int)sc3.x, (int)sc3.y);
            g.aimage(stageTextTex(Integer.toString(stage), MoonConfig.overlayTextColor()),
                sc.add(0, -UI.scale(6)), 0.5, 1.0);
        } catch(Loading ignored) {
        } catch(Exception ignored) {
        }
    }

    private static void syncHidden(MapView mv) {
        Set<Long> seen = new HashSet<>();
        List<Gob> toHide = new ArrayList<>();
        try {
            synchronized(mv.glob.oc) {
                for(Gob gob : mv.glob.oc) {
                    if(gob == null || !isCrop(gob))
                        continue;
                    seen.add(gob.id);
                    if(!mv.isHiddenBy(gob, MapView.HideReason.CROP_HIDE))
                        toHide.add(gob);
                }
            }
        } catch(Exception ignored) {
            return;
        }
        for(Gob gob : toHide) {
            try {
                mv.hideGob(gob, MapView.HideReason.CROP_HIDE);
            } catch(Exception ignored) {
            }
        }
        for(Gob gob : mv.hiddenGobs(MapView.HideReason.CROP_HIDE)) {
            if(gob == null)
                continue;
            if(seen.contains(gob.id))
                continue;
            try { mv.showGob(gob, MapView.HideReason.CROP_HIDE); } catch(Exception ignored) {}
        }
    }

    private static void restoreHidden(MapView mv) {
        for(Gob gob : mv.hiddenGobs(MapView.HideReason.CROP_HIDE)) {
            if(gob != null)
                try { mv.showGob(gob, MapView.HideReason.CROP_HIDE); } catch(Exception ignored) {}
        }
    }

    private static int cropStage(Gob gob) {
        ResDrawable rd = gob.getattr(ResDrawable.class);
        if(rd == null || rd.sdt == null || rd.sdt.rbuf == null || rd.sdt.rbuf.length == 0)
            return Integer.MIN_VALUE;
        return rd.sdt.rbuf[0] & 0xff;
    }

    private static Text.Foundry stageFoundry() {
        int px = MoonConfig.overlayTextSize;
        if((stageFoundry == null) || (stageFontPx != px)) {
            synchronized(stageTextCache) {
                for(Tex tex : stageTextCache.values())
                    tex.dispose();
                stageTextCache.clear();
            }
            stageFontPx = px;
            stageFoundry = new Text.Foundry(Text.sans, px).aa(true);
        }
        return stageFoundry;
    }

    private static Tex stageTextTex(String text, Color color) {
        stageFoundry();
        String key = stageFontPx + "|" + (color.getRGB() & 0xffffffffL) + "|" + text;
        synchronized(stageTextCache) {
            Tex tex = stageTextCache.get(key);
            if(tex == null) {
                tex = new TexI(Utils.outline2(stageFoundry().render(text, color).img, Color.BLACK));
                stageTextCache.put(key, tex);
            }
            return tex;
        }
    }
}
