package haven;

import java.awt.event.KeyEvent;
import haven.Widget.GlobKeyEvent;

/**
 * Optional hotkeys to toggle MooNWide client features (assign in Options → Keybindings).
 * Defaults are unbound ({@link KeyMatch#nil}); user sets keys per profile.
 */
public final class MoonKeybinds {
    private MoonKeybinds() {}

    public static final KeyBinding kb_toggleEsp = KeyBinding.get("mw-toggle/esp", KeyMatch.nil);
    public static final KeyBinding kb_toggleXray = KeyBinding.get("mw-toggle/xray", KeyMatch.nil);
    public static final KeyBinding kb_toggleTraceHostile = KeyBinding.get("mw-toggle/trace-hostile", KeyMatch.nil);
    public static final KeyBinding kb_toggleTraceNeutral = KeyBinding.get("mw-toggle/trace-neutral", KeyMatch.nil);
    public static final KeyBinding kb_toggleTracePlayers = KeyBinding.get("mw-toggle/trace-players", KeyMatch.nil);
    public static final KeyBinding kb_toggleAutoDrink = KeyBinding.get("mw-toggle/autodrink", KeyMatch.nil);
    public static final KeyBinding kb_toggleCombatBot = KeyBinding.get("mw-toggle/combatbot", KeyMatch.nil);
    public static final KeyBinding kb_toggleAggroRadius = KeyBinding.get("mw-toggle/aggro-radius", KeyMatch.nil);
    public static final KeyBinding kb_toggleSpeedBoost = KeyBinding.get("mw-toggle/speed-boost", KeyMatch.nil);
    public static final KeyBinding kb_toggleFlatTerrain = KeyBinding.get("mw-toggle/flat-terrain", KeyMatch.nil);
    /** Default unbound: Shift+N is the same as action-menu «Next» ({@code scm-next}) and collides in {@link RootWidget}. */
    public static final KeyBinding kb_toggleAlwaysDaylight = KeyBinding.get("mw-toggle/always-daylight", KeyMatch.nil);
    public static final KeyBinding kb_toggleQualityObjects = KeyBinding.get("mw-toggle/quality-objects", KeyMatch.nil);
    public static final KeyBinding kb_toggleCropHide = KeyBinding.get("mw-toggle/crop-hide", KeyMatch.nil);
    /** Cycle live camera mode on the current map; default Shift+Z. */
    public static final KeyBinding kb_cycleCameraMode = KeyBinding.get("mw-toggle/camera-cycle",
	KeyMatch.forcode(KeyEvent.VK_Z, KeyMatch.S));
    /**
     * Default Shift+B: cycle global hitbox mode for all gobs — off / outlines / outlines only.
     * Uses physical {@code VK_B} so it works when {@link KeyEvent#getKeyChar()} is undefined under some layouts.
     */
    public static final KeyBinding kb_toggleTreeHitbox = KeyBinding.get("mw-toggle/tree-hitbox", KeyMatch.forcode(KeyEvent.VK_B, KeyMatch.S));
    /** Structures / terrain footprint overlay — unbound by default; set in Options → Keybindings → MooNWide. */
    public static final KeyBinding kb_cycleEntityHitboxViz = KeyBinding.get("mw-toggle/entity-hitbox-viz", KeyMatch.nil);
    /** Show / hide {@link MoonSpeedWirePanel} (Speedget wire multiplier HUD); default Shift+X. */
    public static final KeyBinding kb_toggleSpeedWirePanel = KeyBinding.get("mw-toggle/speed-wire-panel",
	KeyMatch.forcode(KeyEvent.VK_X, KeyMatch.S));
    /** Jump to Nth most recently used saved point ({@link TeleportManager}); unbound by default. */
    public static final KeyBinding kb_tp_quick1 = KeyBinding.get("mw-tp/quick-1", KeyMatch.nil);
    public static final KeyBinding kb_tp_quick2 = KeyBinding.get("mw-tp/quick-2", KeyMatch.nil);
    public static final KeyBinding kb_tp_quick3 = KeyBinding.get("mw-tp/quick-3", KeyMatch.nil);

    /**
     * Suppress moon keybinds when a text input, flower menu, or similar interactive overlay is active.
     */
    private static boolean shouldSuppressKeybinds(GameUI gui) {
	if(gui == null || gui.ui == null)
	    return(true);
	if(gui.chat != null && gui.chat.hasfocus)
	    return(true);
	try {
	    Widget root = gui.ui.root;
	    if(root != null) {
		for(Widget w = root.child; w != null; w = w.next) {
		    if(w instanceof FlowerMenu && w.visible)
			return(true);
		}
	    }
	} catch(Exception ignored) {}
	return(false);
    }

    /**
     * Keep the historical Shift+B behavior even if an older prefs profile saved this binding as nil.
     */
    private static boolean isHardwiredShiftB(GlobKeyEvent ev) {
	if(ev == null || ev.awt == null)
	    return(false);
	int mods = UI.modflags(ev.awt);
	return (ev.awt.getKeyCode() == KeyEvent.VK_B)
	    && ((mods & KeyMatch.S) != 0)
	    && ((mods & (KeyMatch.C | KeyMatch.M)) == 0);
    }

    /**
     * @return true if key was consumed.
     */
    public static boolean handleGlob(GameUI gui, GlobKeyEvent ev) {
	if(shouldSuppressKeybinds(gui))
	    return(false);
	if(kb_toggleEsp.key().match(ev)) {
	    MoonConfig.setEspEnabled(!MoonConfig.espEnabled);
	    msgToggle(gui, "keybind.mw.esp", MoonConfig.espEnabled);
	    return(true);
	}
	if(kb_toggleXray.key().match(ev)) {
	    MoonConfig.setXrayEnabled(!MoonConfig.xrayEnabled);
	    if(MoonConfig.xrayEnabled && !MoonConfig.gfxModXray)
		MoonConfig.setGfxModXray(true);
	    msgToggle(gui, "keybind.mw.xray", MoonConfig.xrayEnabled);
	    return(true);
	}
	if(kb_toggleTraceHostile.key().match(ev)) {
	    MoonConfig.setTraceHostile(!MoonConfig.traceHostile);
	    msgToggle(gui, "keybind.mw.trace-hostile", MoonConfig.traceHostile);
	    return(true);
	}
	if(kb_toggleTraceNeutral.key().match(ev)) {
	    MoonConfig.setTraceNeutralMobs(!MoonConfig.traceNeutralMobs);
	    msgToggle(gui, "keybind.mw.trace-neutral", MoonConfig.traceNeutralMobs);
	    return(true);
	}
	if(kb_toggleTracePlayers.key().match(ev)) {
	    MoonConfig.setTracePlayers(!MoonConfig.tracePlayers);
	    msgToggle(gui, "keybind.mw.trace-players", MoonConfig.tracePlayers);
	    return(true);
	}
	if(kb_toggleAutoDrink.key().match(ev)) {
	    MoonConfig.setAutoDrink(!MoonConfig.autoDrink);
	    msgToggle(gui, "keybind.mw.autodrink", MoonConfig.autoDrink);
	    return(true);
	}
	if(kb_toggleCombatBot.key().match(ev)) {
	    MoonConfig.setCombatBot(!MoonConfig.combatBot);
	    msgToggle(gui, "keybind.mw.combatbot", MoonConfig.combatBot);
	    return(true);
	}
	if(kb_toggleAggroRadius.key().match(ev)) {
	    MoonConfig.setAggroRadius(!MoonConfig.aggroRadius);
	    msgToggle(gui, "keybind.mw.aggro", MoonConfig.aggroRadius);
	    return(true);
	}
	if(kb_toggleSpeedBoost.key().match(ev)) {
	    MoonConfig.setSpeedBoost(!MoonConfig.speedBoost);
	    msgToggle(gui, "keybind.mw.speedboost", MoonConfig.speedBoost);
	    return(true);
	}
	if(kb_toggleFlatTerrain.key().match(ev)) {
	    MoonConfig.setFlatTerrain(!MoonConfig.flatTerrain);
	    msgToggle(gui, "keybind.mw.flatterrain", MoonConfig.flatTerrain);
	    return(true);
	}
	if(kb_toggleAlwaysDaylight.key().match(ev)) {
	    MoonConfig.setAlwaysDaylight(!MoonConfig.alwaysDaylight);
	    msgToggle(gui, "keybind.mw.daylight", MoonConfig.alwaysDaylight);
	    return(true);
	}
	if(kb_toggleQualityObjects.key().match(ev)) {
	    MoonConfig.setQualityObjects(!MoonConfig.qualityObjects);
	    msgToggle(gui, "keybind.mw.qual-objs", MoonConfig.qualityObjects);
	    return(true);
	}
	if(kb_toggleCropHide.key().match(ev)) {
	    MoonConfig.setCropHide(!MoonConfig.cropHide);
	    if(gui.map != null)
		MoonCropMode.refresh(gui.map);
	    msgToggle(gui, "keybind.mw.crop-hide", MoonConfig.cropHide);
	    return(true);
	}
	if(kb_cycleCameraMode.key().match(ev)) {
	    if(gui.map != null) {
		String next = gui.map.cycleCameraMode();
		gui.ui.msg(LocalizationManager.tr("msg.camera-cycle") + ": " + next, java.awt.Color.WHITE, null);
		return(true);
	    }
	    return(false);
	}
	if(kb_toggleTreeHitbox.key().match(ev) || isHardwiredShiftB(ev)) {
	    int mode = (MoonConfig.globalHitboxMode + 1) % 3;
	    MoonConfig.setGlobalHitboxMode(mode);
	    MoonConfig.setGfxModHitboxes(mode > 0);
	    if(mode > 0)
		MoonConfig.setEntityHitboxVizMode(0);
	    String state = LocalizationManager.tr("hitbox.mode." + mode);
	    gui.ui.msg(LocalizationManager.tr("keybind.mw.tree-hitbox") + ": " + state, java.awt.Color.WHITE, null);
	    return(true);
	}
	if(kb_cycleEntityHitboxViz.key().match(ev)) {
	    MoonConfig.cycleEntityHitboxViz();
	    int m = MoonConfig.entityHitboxVizMode;
	    if(m > 0) {
		MoonConfig.setGlobalHitboxMode(0);
		MoonConfig.setGfxModHitboxes(false);
	    }
	    String state = LocalizationManager.tr("entity.hitbox.mode." + m);
	    gui.ui.msg(LocalizationManager.tr("keybind.mw.entity-hitbox") + ": " + state, java.awt.Color.WHITE, null);
	    return(true);
	}
	if(kb_toggleSpeedWirePanel.key().match(ev)) {
	    gui.toggleMoonSpeedWirePanel();
	    return(true);
	}
	if(kb_tp_quick1.key().match(ev)) {
	    TeleportManager.goQuickSlot(gui, 0);
	    return(true);
	}
	if(kb_tp_quick2.key().match(ev)) {
	    TeleportManager.goQuickSlot(gui, 1);
	    return(true);
	}
	if(kb_tp_quick3.key().match(ev)) {
	    TeleportManager.goQuickSlot(gui, 2);
	    return(true);
	}
	return(false);
    }

    private static void msgToggle(GameUI gui, String nameKey, boolean on) {
	if(gui == null || gui.ui == null)
	    return;
	String name = LocalizationManager.tr(nameKey);
	String state = on ? LocalizationManager.tr("moon.kb.on") : LocalizationManager.tr("moon.kb.off");
	gui.ui.msg(name + ": " + state, java.awt.Color.WHITE, null);
    }
}
