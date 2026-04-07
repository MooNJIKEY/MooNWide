package haven;

import java.awt.Color;

/**
 * Per-type ESP settings: enabled, color, show distance, show names, show speed.
 */
public class MoonEspProfile {
    public final String id;
    public boolean enabled;
    public Color color;
    public boolean showDist;
    public boolean showName;
    public boolean showSpeed;

    private static final Color[] PRESETS = {
        new Color(255, 80, 80, 160),     // 0 red
        new Color(60, 220, 255, 160),    // 1 cyan
        new Color(180, 120, 255, 140),   // 2 purple
        new Color(100, 180, 255, 160),   // 3 blue
        new Color(180, 180, 180, 120),   // 4 gray
        new Color(255, 200, 50, 180),    // 5 yellow
        new Color(200, 160, 80, 140),    // 6 brown
        new Color(180, 140, 60, 140),    // 7 dark brown
        new Color(80, 230, 80, 160),     // 8 green
        new Color(200, 60, 200, 160),    // 9 magenta
        new Color(200, 200, 150, 120),   // 10 beige
        new Color(255, 130, 50, 180),    // 11 orange
    };

    public static final String[] COLOR_NAMES_EN = {
        "Red", "Cyan", "Purple", "Blue", "Gray", "Yellow",
        "Brown", "Dark brown", "Green", "Magenta", "Beige", "Orange"
    };
    public static final String[] COLOR_NAMES_RU = {
        "\u041a\u0440\u0430\u0441\u043d\u044b\u0439", "\u0413\u043e\u043b\u0443\u0431\u043e\u0439", "\u0424\u0438\u043e\u043b\u0435\u0442\u043e\u0432\u044b\u0439", "\u0421\u0438\u043d\u0438\u0439", "\u0421\u0435\u0440\u044b\u0439", "\u0416\u0451\u043b\u0442\u044b\u0439",
        "\u041a\u043e\u0440\u0438\u0447\u043d\u0435\u0432\u044b\u0439", "\u0422\u0451\u043c\u043d\u043e-\u043a\u043e\u0440\u0438\u0447\u043d\u0435\u0432\u044b\u0439", "\u0417\u0435\u043b\u0451\u043d\u044b\u0439", "\u041c\u0430\u0434\u0436\u0435\u043d\u0442\u0430", "\u0411\u0435\u0436\u0435\u0432\u044b\u0439", "\u041e\u0440\u0430\u043d\u0436\u0435\u0432\u044b\u0439"
    };

    public MoonEspProfile(String id, boolean defEnabled, int defColorIdx, boolean defDist, boolean defName, boolean defSpeed) {
        this.id = id;
        this.enabled = Utils.getprefb("mesp-" + id + "-on", defEnabled);
        int ci = Utils.getprefi("mesp-" + id + "-col", defColorIdx);
        this.color = (ci >= 0 && ci < PRESETS.length) ? PRESETS[ci] : PRESETS[0];
        this.showDist = Utils.getprefb("mesp-" + id + "-dist", defDist);
        this.showName = Utils.getprefb("mesp-" + id + "-name", defName);
        this.showSpeed = Utils.getprefb("mesp-" + id + "-spd", defSpeed);
    }

    public void setEnabled(boolean v) {
        enabled = v;
        Utils.setprefb("mesp-" + id + "-on", v);
        if(v)
            MoonConfig.ensureOverlayRendererEnabled();
        flush();
    }
    public void setColorIdx(int ci) {
        if (ci >= 0 && ci < PRESETS.length) color = PRESETS[ci];
        Utils.setprefi("mesp-" + id + "-col", ci);
        flush();
    }
    public void setShowDist(boolean v) { showDist = v; Utils.setprefb("mesp-" + id + "-dist", v); flush(); }
    public void setShowName(boolean v) { showName = v; Utils.setprefb("mesp-" + id + "-name", v); flush(); }
    public void setShowSpeed(boolean v) { showSpeed = v; Utils.setprefb("mesp-" + id + "-spd", v); flush(); }

    public int colorIdx() {
        for (int i = 0; i < PRESETS.length; i++)
            if (PRESETS[i].equals(color)) return i;
        return 0;
    }

    public static Color preset(int i) {
        return (i >= 0 && i < PRESETS.length) ? PRESETS[i] : PRESETS[0];
    }

    public static int presetCount() { return PRESETS.length; }

    public static String colorName(int i) {
        String[] names = MoonL10n.lang().equals("ru") ? COLOR_NAMES_RU : COLOR_NAMES_EN;
        return (i >= 0 && i < names.length) ? names[i] : "?";
    }

    private static void flush() {
        try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    // Global profiles
    public static final MoonEspProfile HOSTILE_MOB = new MoonEspProfile("hostile", true, 0, true, true, true);
    public static final MoonEspProfile PLAYER      = new MoonEspProfile("player",  true, 1, true, true, true);
    public static final MoonEspProfile NEUTRAL_MOB = new MoonEspProfile("neutral", true, 2, true, true, false);
    public static final MoonEspProfile VEHICLE     = new MoonEspProfile("vehicle", false, 3, true, true, true);
    public static final MoonEspProfile BUILDING    = new MoonEspProfile("building", false, 4, false, true, false);
    public static final MoonEspProfile RESOURCE    = new MoonEspProfile("resource", false, 5, true, true, false);
    public static final MoonEspProfile CONTAINER   = new MoonEspProfile("container", false, 7, true, true, false);
    public static final MoonEspProfile HERB        = new MoonEspProfile("herb",    false, 8, false, true, false);
    public static final MoonEspProfile DUNGEON     = new MoonEspProfile("dungeon", false, 9, true, true, false);
    public static final MoonEspProfile WORKSTATION = new MoonEspProfile("workstation", false, 10, false, true, false);
    /** Ground items (no trace lines; separate hitbox / label prefs). */
    public static final MoonEspProfile ITEM = new MoonEspProfile("item", false, 11, true, true, false);

    public double itemBoxHalfW() {
        return Utils.getprefd("mesp-item-hw", 2.5);
    }

    public int itemLabelPx() {
        return Utils.getprefi("mesp-item-fnt", 12);
    }

    public void setItemBoxHalfW(double v) {
        Utils.setprefd("mesp-item-hw", v);
        flush();
    }

    public void setItemFontPx(int v) {
        Utils.setprefi("mesp-item-fnt", v);
        flush();
    }

    public static MoonEspProfile forType(MoonOverlay.GobType type) {
        switch (type) {
            case HOSTILE_MOB:  return HOSTILE_MOB;
            case PLAYER:       return PLAYER;
            case NEUTRAL_MOB:  return NEUTRAL_MOB;
            case VEHICLE:      return VEHICLE;
            case BUILDING:     return BUILDING;
            case RESOURCE_NODE:return RESOURCE;
            case STOCKPILE:    return RESOURCE;
            case CONTAINER:    return CONTAINER;
            case HERB:         return HERB;
            case DUNGEON:      return DUNGEON;
            case WORKSTATION:  return WORKSTATION;
            case ITEM:         return ITEM;
            default:           return null;
        }
    }

    public static boolean anyEnabled() {
        return HOSTILE_MOB.enabled || PLAYER.enabled || NEUTRAL_MOB.enabled
            || VEHICLE.enabled || BUILDING.enabled || RESOURCE.enabled
            || CONTAINER.enabled || HERB.enabled || DUNGEON.enabled
            || WORKSTATION.enabled || ITEM.enabled;
    }
}
