package haven;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MoonConfig {
    public static boolean traceHostile = Utils.getprefb("moon-trace-hostile", false);
    public static boolean traceNeutralMobs = Utils.getprefb("moon-trace-neutral", false);
    public static boolean tracePlayers = Utils.getprefb("moon-trace-players", false);

    public static boolean espEnabled = Utils.getprefb("moon-esp", false);
    public static boolean espDistance = Utils.getprefb("moon-esp-dist", true);
    public static boolean espNames = Utils.getprefb("moon-esp-names", true);
    public static boolean espVehicles = Utils.getprefb("moon-esp-vehicles", false);
    public static boolean espBuildings = Utils.getprefb("moon-esp-buildings", false);
    public static boolean espResources = Utils.getprefb("moon-esp-resources", false);
    public static boolean espContainers = Utils.getprefb("moon-esp-containers", false);

    public static boolean xrayEnabled = Utils.getprefb("moon-xray", false);
    /** Index into {@link MoonEspProfile} presets; used for X-Ray 2D hitbox squares. */
    public static int xrayColorIdx = Math.floorMod(Utils.getprefi("moon-xray-col", 8), MoonEspProfile.presetCount());
    public static int xrayGhostMax = Utils.getprefi("moon-xray-max", 220);
    /** Same units as ESP distance (÷11 from raw world dist) */
    public static double xrayGhostMaxDist = Utils.getprefd("moon-xray-maxdist", 140.0);
    /**
     * 0 = screen-space rect from projected 8 corners (tall “vertical” 2D box that changes with camera).
     * 1 = flat quad on terrain at footprint (red diamond at stump) — default.
     */
    public static int xrayStyle = Math.floorMod(Utils.getprefi("moon-xray-style", 1), 2);
    /** 0 = hide only trees/bushes/trellis, 1 = also hide rocks/bumlings. */
    public static int xrayHideMode = Math.floorMod(Utils.getprefi("moon-xray-hide-mode", 0), 2);

    /** When true, combat session UI ({@link Fightsess}) uses a saved screen position instead of following the player. */
    public static boolean fightOverlayFixed = Utils.getprefb("moon-fight-overlay-fixed", false);

    /** Default on so inventory Q is visible without opening Functions; existing {@code moon-quality-objs} pref still wins. */
    public static boolean qualityObjects = Utils.getprefb("moon-quality-objs", true);

    /** Bumped when inventory quality overlay prefs change (invalidates per-slot WItem tex cache). */
    public static volatile int invQualitySettingsRev = 0;

    private static void bumpInvQualitySettingsRev() {
	invQualitySettingsRev++;
    }

    /** Rounded integer vs one decimal place for the overlay number. */
    public static boolean invQualityRounded = Utils.getprefb("moon-inv-quality-rounded", true);
    /** Use per-tier colors from {@link #invQualityColorArgb}; otherwise legacy green tint. */
    public static boolean invQualityCustomColors = Utils.getprefb("moon-inv-quality-custom", false);
    /**
     * 0 = top-right, 1 = top-left, 2 = bottom-right, 3 = bottom-left, 4 = center (within slot).
     */
    public static int invQualityCorner = Math.floorMod(Utils.getprefi("moon-inv-qual-corner", 0), 5);
    /** Base font size in px before {@link UI#scale}; clamped 6–32. */
    public static int invQualityFontPx = Utils.clip(Utils.getprefi("moon-inv-qual-font-px", 8), 6, 32);

    public static final int INV_QUAL_TIER_COUNT = 7;
    private static final int[] INV_QUAL_TH_DEFAULT = {400, 300, 200, 100, 50, 10, 1};
    private static final int[] INV_QUAL_COL_DEFAULT = {
	0xFFFF0000, 0xFFFF7200, 0xFFA500FF, 0xFF0083FF, 0xFF00D60A, 0xFFFFFFFF, 0xFFB4B4B4
    };
    public static final int[] invQualityThreshold = new int[INV_QUAL_TIER_COUNT];
    public static final int[] invQualityColorArgb = new int[INV_QUAL_TIER_COUNT];
    static {
	for(int i = 0; i < INV_QUAL_TIER_COUNT; i++) {
	    invQualityThreshold[i] = Utils.getprefi("moon-inv-qual-th-" + i, INV_QUAL_TH_DEFAULT[i]);
	    invQualityColorArgb[i] = Utils.getprefi("moon-inv-qual-col-" + i, INV_QUAL_COL_DEFAULT[i]);
	}
    }

    public static boolean autoDrink = Utils.getprefb("moon-autodrink",
	Utils.getprefb("autodrink", false));
    public static int autoDrinkThreshold = Utils.getprefi("moon-autodrink-pct",
	Utils.getprefi("autodrinkthreshold", 30));
    /** Autodrink behavior toggles. */
    public static int autoDrinkIntervalSec = Utils.getprefi("moon-autodrink-interval-sec",
	Utils.getprefi("autodrinktime", 5));
    public static String autoDrinkLiquid = Utils.getpref("moon-autodrink-liquid",
	Utils.getpref("autoDrinkLiquid", "Water"));
    public static boolean autoDrinkWhatever = Utils.getprefb("moon-autodrink-whatever",
	Utils.getprefb("autoDrinkWhatever", false));
    public static boolean autoDrinkUseSip = Utils.getprefb("moon-autodrink-use-sip",
	Utils.getprefb("drinkorsip", false));
    public static boolean autoDrinkSmartSip = Utils.getprefb("moon-autodrink-smart-sip",
	Utils.getprefb("autodrinkosip", false));
    public static int autoSipThreshold = Utils.getprefi("moon-autosip-threshold",
	Utils.getprefi("autosipthreshold", 100));
    public static boolean autoSipOnce = Utils.getprefb("moon-autosip-once",
	Utils.getprefb("siponce", false));
    public static int autoSipWaitMs = Utils.getprefi("moon-autosip-wait-ms",
	Utils.getprefi("sipwaiting", 2000));
    public static boolean autoDrinkMessage = Utils.getprefb("moon-autodrink-message",
	Utils.getprefb("drinkmessage", false));
    /** Experimental: try direct sip path with aggressive timing. */
    public static boolean autoDrinkDirectSip = Utils.getprefb("moon-autodrink-direct-sip", false);
    public static int autoDrinkDirectSipIntervalMs = Utils.getprefi("moon-autodrink-direct-sip-interval-ms", 350);
    /**
     * Experimental: react on incoming server stamina meter updates ({@code im} {@code set}), same UI tick as the packet
     * is applied — lower latency than polling in {@code tick}. Prefers Sip (combat-aware). Uses
     * {@link #autoDrinkDirectSipIntervalMs} as minimum spacing when this or {@link #autoDrinkDirectSip} is on.
     */
    public static boolean autoDrinkServerHook = Utils.getprefb("moon-autodrink-server-hook", false);

    public static boolean combatBot = Utils.getprefb("moon-combatbot", false);
    /**
     * 0 = legacy keyword scoring only, 1 = {@link MoonCombatAI} one-ply, 2 = expectiminimax
     * ({@link MoonCombatMinimax}, depth {@link #combatBotSearchDepth}).
     */
    public static int combatBotBrainMode = Utils.getprefi("moon-combatbot-brain", 1);
    /** 0 = very defensive tilt, 50 = neutral, 100 = aggressive tilt for AI weights. */
    public static int combatBotAggression = Utils.getprefi("moon-combatbot-agg", 50);
    public static boolean combatBotLogAi = Utils.getprefb("moon-combatbot-log", false);
    public static boolean combatBotTrustHints = Utils.getprefb("moon-combatbot-trust-hints", true);
    /** Search depth for mode 2 (clamped 2–3 in decision maker). */
    public static int combatBotSearchDepth = Utils.getprefi("moon-combatbot-depth", 2);
    public static double combatBotRiskAversion = Utils.getprefd("moon-combatbot-risk", 1.0);

    /** Tree-chop bot: dense stacking by hitboxes (client-side planning). */
    public static boolean treeBotHitstack = Utils.getprefb("moon-treebot-hitstack", false);
    /** Avoid / react to hostile mobs. */
    public static boolean treeBotAvoidMobs = Utils.getprefb("moon-treebot-mobs", true);
    /** Auto drink / refill water while bot runs. */
    public static boolean treeBotWater = Utils.getprefb("moon-treebot-water", true);
    /** Master switch for tree bot runner (logic in {@link MoonTreeChopBot}). */
    public static boolean treeBotEnabled = Utils.getprefb("moon-treebot-on", false);
    /** After phase 1/2 completes, advance to the next phase instead of pausing at 0. */
    public static boolean treeBotAutoPipeline = Utils.getprefb("moon-treebot-auto-pipeline", true);
    /** Show floating status above the player (phase, stamina) while the bot is on. */
    public static boolean treeBotHudLine = Utils.getprefb("moon-treebot-hud-line", true);
    /**
     * 0 = paused between phases; 1 = felling only; 2 = haul to stock; 3 = split/piles (partial).
     * When enabling the bot, set to 1.
     */
    public static int treeBotWorkPhase = Utils.getprefi("moon-treebot-phase", 0);
    /** Do not start chopping until stamina is at least this (0–100), after drinking if possible. */
    public static int treeBotStaminaMinPct = Utils.getprefi("moon-treebot-stam-min", 30);
    /** Send phase messages to Discord webhook ({@code moon-treebot-webhook} or legacy {@code moon-discord-url}). */
    public static boolean treeBotDiscordNotify = Utils.getprefb("moon-treebot-discord", true);
    /** Draw hitboxes for all gobs when global hitbox mode &gt; 0 (hotkey in Keybindings → MooNWide). */
    public static boolean treeBotHitboxOverlay = Utils.getprefb("moon-treebot-hitbox-viz", false);
    /** 0 = off, 1 = show hitboxes, 2 = only hitboxes (hide objects). */
    public static int globalHitboxMode = Utils.getprefi("moon-global-hitbox-mode", 0);
    /**
     * Entity hitbox overlay (separate keybind): 0 off, 1 terrain footprint outlines + visible meshes,
     * 2 same outlines + structural meshes hidden (houses, crates, piles, fences, etc.; not mobs/players).
     */
    public static int entityHitboxVizMode = Utils.getprefi("moon-entity-hitbox-viz-mode", 0);

    /** World gob outlines: Graphics → Hitbox mod and/or Shift+B ({@link #globalHitboxMode} &gt; 0). */
    public static boolean gfxOrGlobalHitboxes() {
	return gfxModHitboxes || globalHitboxMode > 0;
    }

    /**
     * {@link MapView.Plob} preview + carried terobj wire: must match Graphics checkbox, not only Shift+B
     * or entity mode (otherwise RMB-place from hands shows no hitbox).
     */
    public static boolean hitboxPlacementCarriedEnabled() {
	return gfxModHitboxes || globalHitboxMode > 0 || entityHitboxVizMode > 0;
    }

    /**
     * Shift+Alt + RMB item-use (itemact) or shift+Alt+drop on map toward workstations / fires: repeat until hand empty or stall.
     */
    public static boolean bulkStationFill = Utils.getprefb("moon-bulk-station-fill", true);
    /**
     * Ctrl + RMB on map toward same targets (empty hand): after click, send {@code xfer} on {@link ISBox} under
     * {@link Window}s in batches.
     */
    public static boolean bulkStationTakeAll = Utils.getprefb("moon-bulk-station-takeall", true);
    public static int bulkStationRepeatIntervalMs = Utils.getprefi("moon-bulk-station-interval-ms", 90);
    public static int bulkStationMaxRepeats = Utils.getprefi("moon-bulk-station-max-repeats", 64);
    /**
     * Stop bulk after this many replays with no hand progress ({@link GameUI} hand num/infoseq/meter change);
     * {@code 0} = off. Separate from {@link #bulkStationMaxRepeats} (total budget).
     */
    public static int bulkStationStallRepeats = Utils.getprefi("moon-bulk-station-stall-repeats", 24);
    /**
     * When true, empty-hand ttub click swallows the first {@code itemact} (no wire). When false (default),
     * the first {@code itemact} is sent — many servers need that message before automation continues.
     */
    public static boolean bulkTubSendFirstItemact = Utils.getprefb("moon-bulk-tub-send-first-itemact", false);
    /**
     * Per “take” burst for automated tanning tub ({@code ttub} run): caps bulk replays so one failed deposit
     * does not spam 64 identical {@code itemact}s. Does not apply to manual Shift+Alt bulk on the tub.
     */
    public static int bulkTubMaxRepeatsPerDeposit = Utils.getprefi("moon-bulk-tub-max-repeats-deposit", 6);
    /**
     * Multiplier on {@link #bulkStationRepeatIntervalMs} between tub-run replays only (slows hammering the server).
     */
    public static double bulkTubReplayIntervalMult = initBulkTubReplayIntervalMult();

    private static double initBulkTubReplayIntervalMult() {
	double v = Utils.getprefd("moon-bulk-tub-replay-interval-mult", 2.0);
	if(Double.isNaN(v) || v < 1.0)
	    v = 1.0;
	return(Math.min(v, 5.0));
    }
    public static String bulkStationBlacklist = Utils.getpref("moon-bulk-station-blacklist", "");

    /**
     * Modifier bits that must all be held to start bulk station fill (default: Shift+Alt).
     * Only {@link UI#MOD_SHIFT}, {@link UI#MOD_CTRL}, {@link UI#MOD_META} are stored.
     */
    public static int bulkStationFillModMask = initBulkStationFillModMask();

    /** Log inventory item {@code wdgmsg} and bulk replay wire to stderr ({@code [MoonInvWire]}). */
    public static boolean debugInventoryWire = Utils.getprefb("moon-debug-inventory-wire", false);

    /**
     * Log every outgoing widget message to stdout before wire encode: {@code [id] [name] args...}
     * ({@link MoonPacketHook#logOutgoingWdgmsg}). Pref {@code moon-debug-outgoing-wdgmsg}.
     */
    public static boolean debugOutgoingWdgmsg = Utils.getprefb("moon-debug-outgoing-wdgmsg", false);

    /**
     * Корреляция входящих {@code WDGMSG name=err} с последними исходящими {@code wdgmsg}: stderr
     * {@code [MoonErrChain]} + задержка от последнего исходящего. Pref {@code moon-debug-err-latency}.
     */
    public static boolean debugErrLatencyLog = Utils.getprefb("moon-debug-err-latency", false);

    /** Размер кольца исходящих сообщений для {@link MoonPacketHook} (8–256). */
    public static int errLatencyRingSize = Utils.getprefi("moon-err-latency-ring", 64);

    private static int initBulkStationFillModMask() {
	int v = Utils.getprefi("moon-bulk-fill-mod-mask", UI.MOD_SHIFT | UI.MOD_META);
	return(normalizeBulkStationFillModMask(v));
    }

    /** At least one of Shift/Ctrl/Alt; default Shift+Alt if {@code v} strips to zero. */
    public static int normalizeBulkStationFillModMask(int v) {
	v &= UI.MOD_SHIFT | UI.MOD_CTRL | UI.MOD_META;
	if(v == 0)
	    v = UI.MOD_SHIFT | UI.MOD_META;
	return(v);
    }

    /**
     * When false, {@link MenuGrid} ignores root/back/next and letter shortcuts while the menu is open.
     */
    public static boolean menuGridKeyboard = Utils.getprefb("moon-menugrid-keyboard", false);

    /** Move trace target mode: false = exact click position, true = tile center. */
    public static boolean moveTraceTileCenter = Utils.getprefb("moon-movetrace-tile-center", false);
    /** Newline-separated world coords of logging spots (x,y,z per line). */
    public static String treeBotSpots = Utils.getpref("moon-treebot-spots", "");

    public static boolean aggroRadius = Utils.getprefb("moon-aggro-radius", false);

    public static boolean speedBoost = Utils.getprefb("moon-speed-boost", false);
    /**
     * When true and {@link #clientScaleServerSprint} is on, outgoing {@link Speedget} can be rewritten to sprint.
     * Linear move display follows server {@code s+v*t} (no client velocity scaling — avoids rubber-banding).
     */
    public static boolean clientSpeedScale = Utils.getprefb("moon-client-speed-scale", false);
    /**
     * When {@link #clientSpeedScale} is on, upgrade non-max {@link Speedget} {@code set} toward max
     * when the player is not slowing down ({@code s >= current cur}); lowering speed is sent unchanged.
     */
    public static boolean clientScaleServerSprint = Utils.getprefb("moon-client-scale-sprint", true);
    /**
     * Outgoing {@link Speedget} {@code set}: add this many speed tiers before clamping to widget max
     * (0 = off). Works even when {@link #clientSpeedScale} is off; ignored while sprint assist is on.
     * Crawl/walk/run all shift up on the wire so the server moves you faster, not only at sprint.
     */
    public static int serverSpeedLift = Utils.getprefi("moon-server-speed-lift", 0);
    /**
     * Log outgoing {@code Speedget} {@code set} and incoming {@code cur}/{@code max} to stderr
     * ({@code [MoonSpeedWire]}). Pref {@code moon-debug-speed-wire}.
     */
    public static boolean debugSpeedWire = Utils.getprefb("moon-debug-speed-wire", false);
    /**
     * Outgoing {@link Speedget} {@code set}: bumps the discrete <em>speed tier index</em> on the wire
     * (crawl/walk/run/sprint steps), not a continuous velocity factor — see {@link MoonPacketHook}.
     */
    public static double speedMultiplier = initSpeedMultiplierPref();
    /**
     * Client-only: when each move segment arrives, incoming velocity from the server is multiplied for
     * the local player only — at {@link OCache#OD_LINBEG} / {@link OCache#OD_HOMING} apply in Java (not JNI).
     * {@link LinMove} then uses normal {@code s + v·t}; {@link Homing} uses the scaled scalar. Independent of
     * {@link Speedget}; others still see normal physics (possible rubber-band on correction).
     */
    public static double linMoveVisualSpeedMult = initLinMoveVisualMultPref();

    private static double initSpeedMultiplierPref() {
	double v = Utils.getprefd("moon-speed-multiplier", 1.0);
	if(Double.isNaN(v) || v < 1.0)
	    v = 1.0;
	return(Math.min(v, 5.0));
    }

    private static double initLinMoveVisualMultPref() {
	double v = Utils.getprefd("moon-linmove-visual-mult", 1.0);
	if(Double.isNaN(v) || v < 1.0)
	    v = 1.0;
	return(Math.min(v, 3.0));
    }
    /**
     * When {@code > 0} and {@link #speedMultiplier} {@code > 1}, periodically re-sends {@link Speedget}{@code set(cur)}
     * so the server keeps seeing the multiplied tier (see {@link MoonSpeedBoost#tickSpeedMultResend}).
     * {@code 0} disables periodic resend (clicks / hotkeys still go through {@link MoonPacketHook}).
     */
    /** {@code > 0} periodically re-sends {@link Speedget} so multiplied tier stays on server; default 5s when unset. */
    public static double speedMultResendIntervalSec = Utils.getprefd("moon-speed-mult-resend-sec", 5.0);
    /**
     * Master switch for outgoing {@link Speedget} wire substitution in {@link MoonPacketHook} (Java, not JNI).
     * When false, lift / mult / sprint-assist never alter {@code set} on the wire.
     */
    public static boolean experimentalSpeedWireAssist = Utils.getprefb("moon-exp-speed-wire", false);
    /**
     * Allow {@link MoonJniPacketHook} on HCrypt plaintext (also {@code -Dhaven.moonjni.wire=true} forces on).
     */
    public static boolean experimentalJniWireHook = Utils.getprefb("moon-exp-jni-wire", false);
    /**
     * Log each HCrypt plaintext frame to stderr as {@code [MoonJniWire]} (type, len, hex prefix). Works
     * without enabling JNI substitution; can be very noisy.
     */
    public static boolean debugJniWireLog = Utils.getprefb("moon-debug-jni-wire", false);
    /** When native DLL is loaded, mirror a one-line summary from C++ to stderr ({@code [MoonJniNative]}). */
    public static boolean debugJniWireNativeStderr = Utils.getprefb("moon-debug-jni-native-stderr", false);
    /** Duplicate {@code [MoonJniWire]} lines to stdout (some Windows launchers hide stderr). */
    public static boolean debugJniWireMirrorStdout = Utils.getprefb("moon-debug-jni-mirror-stdout", false);
    /**
     * With autodrink: repeat real drink actions ({@code iact} / flower) while stamina &lt; 100 so the server
     * updates stamina. Ignores {@link #autoDrinkThreshold}; tune {@link #autoDrinkIntervalSec} or Direct Sip.
     */
    public static boolean autoDrinkMaintainFull = Utils.getprefb("moon-autodrink-maintain-full", false);
    /** Diablo-style movement: hold LMB to continuously move toward cursor. */
    public static boolean diabloMove = Utils.getprefb("moon-diablo-move", false);
    /** Map tile grid (Ctrl+G cycles): 0 off, 1 standard lines, 2 thick “super” lines. */
    public static int mapGridMode = Utils.getprefi("moon-map-grid-mode", 0);
    /** Extra live map-cut request radius on top of the camera-driven default. */
    public static int mapLoadExtraCuts = Utils.getprefi("moon-map-load-extra-cuts", 0);
    public enum PerfPreset {
	BALANCED(0),
	MAX_FPS(1),
	VISUAL_FIRST(2);

	public final int id;

	PerfPreset(int id) {
	    this.id = id;
	}

	public static PerfPreset fromId(int id) {
	    for(PerfPreset preset : values()) {
		if(preset.id == id)
		    return(preset);
	    }
	    return(BALANCED);
	}
    }
    public enum RuntimeCpuProfile {
	AUTO(0),
	AMD_RYZEN5_6C(1),
	AMD_RYZEN7_8C(2),
	AMD_RYZEN_X3D_8C(3),
	INTEL_I5_6C(4),
	INTEL_HYBRID_MID(5),
	INTEL_HYBRID_HIGH(6),
	CUSTOM(7);

	public final int id;

	RuntimeCpuProfile(int id) {
	    this.id = id;
	}

	public static RuntimeCpuProfile fromId(int id) {
	    for(RuntimeCpuProfile profile : values()) {
		if(profile.id == id)
		    return(profile);
	    }
	    return(AUTO);
	}
    }
    public static class RuntimeTune {
	public final int activeProcessors;
	public final int loaderThreads;
	public final int deferThreads;
	public final int heapMinGb;
	public final int heapMaxGb;

	public RuntimeTune(int activeProcessors, int loaderThreads, int deferThreads, int heapMinGb, int heapMaxGb) {
	    this.activeProcessors = activeProcessors;
	    this.loaderThreads = loaderThreads;
	    this.deferThreads = deferThreads;
	    this.heapMinGb = heapMinGb;
	    this.heapMaxGb = heapMaxGb;
	}
    }
    public static PerfPreset perfPreset = PerfPreset.fromId(Utils.getprefi("moon-perf-preset", PerfPreset.BALANCED.id));
    public static RuntimeCpuProfile runtimeCpuProfile = RuntimeCpuProfile.fromId(Utils.getprefi("moon-runtime-cpu-profile", RuntimeCpuProfile.AUTO.id));
    public static int runtimeActiveProcessors = Utils.clip(Utils.getprefi("moon-runtime-active-processors", 6), 2, 16);
    public static int runtimeLoaderThreads = Utils.clip(Utils.getprefi("moon-runtime-loader-threads", 3), 1, 8);
    public static int runtimeDeferThreads = Utils.clip(Utils.getprefi("moon-runtime-defer-threads", 4), 1, 8);
    public static int runtimeHeapMinGb = Utils.clip(Utils.getprefi("moon-runtime-heap-min-gb", 2), 1, 8);
    public static int runtimeHeapMaxGb = Utils.clip(Utils.getprefi("moon-runtime-heap-max-gb", 6), 2, 16);
    public static boolean perfHudCompact = Utils.getprefb("moon-perf-hud-compact", false);
    public static boolean progressiveWorldLoad = Utils.getprefb("moon-progressive-world-load", true);
    public static boolean perfAutoShed = Utils.getprefb("moon-perf-autoshed", true);
    public static int perfTerrainCriticalBudget = Utils.clip(Utils.getprefi("moon-perf-terrain-critical", 6), 1, 24);
    public static int perfTerrainSecondaryBudget = Utils.clip(Utils.getprefi("moon-perf-terrain-secondary", 4), 1, 24);
    public static int perfGobBuildBudget = Utils.clip(Utils.getprefi("moon-perf-gob-budget", 24), 4, 96);
    /** Remember selected movement speed (crawl/walk/run/sprint) across relogs. */
    public static boolean rememberSpeedMode = Utils.getprefb("moon-remember-speed-mode", false);
    /** Encounter alerts for newly-seen player gobs. */
    public static boolean playerAlertSounds = Utils.getprefb("moon-player-alerts", true);
    public static int playerAlertCooldownSec = Utils.getprefi("moon-player-alert-cooldown-sec", 12);
    public static boolean playerAlertFriendly = Utils.getprefb("moon-player-alert-friendly", true);
    public static boolean playerAlertUnknown = Utils.getprefb("moon-player-alert-unknown", true);
    public static boolean playerAlertHostile = Utils.getprefb("moon-player-alert-hostile", true);
    public static int playerAlertFriendlyVolume = Utils.getprefi("moon-player-alert-friendly-vol", 650);
    public static int playerAlertUnknownVolume = Utils.getprefi("moon-player-alert-unknown-vol", 850);
    public static int playerAlertHostileVolume = Utils.getprefi("moon-player-alert-hostile-vol", 1000);
    public static String playerAlertFriendlyPath = Utils.getpref("moon-player-alert-friendly-path", "");
    public static String playerAlertUnknownPath = Utils.getpref("moon-player-alert-unknown-path", "");
    public static String playerAlertHostilePath = Utils.getpref("moon-player-alert-hostile-path", "");
    public static boolean flatTerrain = Utils.getprefb("moon-flat-terrain", false);
    /**
     * When {@link #flatTerrain} is on, real height is multiplied by this (0 = fully flat, 1 = full height).
     * Default 0.5 keeps relief visible so mountains/hills stay readable.
     */
    public static double flatTerrainRelief = Utils.getprefd("moon-flat-terrain-relief", 0.5);

    /** Short cave walls (flattened vertical extent). Legacy {@code moon-hide-cave-walls} is treated as the same pref. */
    public static boolean flatCaves = Utils.getprefb("moon-flat-caves",
	Utils.getprefb("moon-hide-cave-walls", false));
    /** Legacy alias kept so existing menu actions and older prefs keep working. */
    public static boolean hideCaveWalls = flatCaves;
    /** Straight cave walls: keep height, remove random wall jitter. */
    public static boolean straightCaveWalls = Utils.getprefb("moon-straight-cave-walls", false);
    /** Lower palisade / brick / drystone / pole wall segments (flattened). */
    public static boolean flatWalls = Utils.getprefb("moon-flat-walls", false);
    public static boolean flatCupboards = Utils.getprefb("moon-flat-cupboards", true);

    /**
     * Smart storage subsystem ({@link haven.MoonStorage}: cache, search, tracking, map/inventory overlays).
     * When {@code false}, all {@code MoonStorage} hooks no-op. Pref {@code moon-smart-storage}.
     */
    public static boolean smartStorageEnabled = Utils.getprefb("moon-smart-storage", false);

    /** Workstation / storage status highlights. */
    public static boolean showDframeStatus = Utils.getprefb("moon-status-dframe", true);
    public static boolean showCoopStatus = Utils.getprefb("moon-status-coop", true);
    public static boolean showHutchStatus = Utils.getprefb("moon-status-hutch", true);
    public static boolean showRackStatus = Utils.getprefb("moon-status-rack", true);
    public static boolean showCupboardStatus = Utils.getprefb("moon-status-storage", true);
    public static boolean showPartialStorageStatus = Utils.getprefb("moon-status-storage-partial", false);
    public static boolean showShedStatus = Utils.getprefb("moon-status-shed", true);
    public static boolean showTroughStatus = Utils.getprefb("moon-status-trough", true);
    public static boolean showBeehiveStatus = Utils.getprefb("moon-status-beehive", true);
    public static boolean highlightPots = Utils.getprefb("moon-status-pots", false);
    /**
     * Highlight tiles actually supported by non-broken props ({@link haven.MoonMiningOverlay}: circle in
     * tile-plane from the <b>tile</b> the prop sits on; &gt;50% of a tile’s area must lie inside the disc;
     * damaged props with reduced {@link GobHealth} are ignored).
     */
    public static boolean mineSupportSafeTiles = Utils.getprefb("moon-mine-support-tiles", false);
    /** Fallback radius (tiles) when the support resource has no props / unknown name. */
    public static int mineSupportRadiusTiles = Utils.getprefi("moon-mine-support-radius", 9);
    /** Fill alpha for safe tiles (0 = outline only). */
    public static int mineSupportFillAlpha = Utils.getprefi("moon-mine-safe-fill-a", 72);
    /** Outline alpha along the outer edge of the safe region. */
    public static int mineSupportOutlineAlpha = Utils.getprefi("moon-mine-safe-edge-a", 255);
    /** Pixel width for the perimeter outline (see {@link haven.MoonMiningOverlay}). */
    public static int mineSupportOutlineWidth = Utils.getprefi("moon-mine-safe-edge-w", 3);
    /** Outline RGB (alpha from {@link #mineSupportOutlineAlpha}). Default vivid orange. */
    public static int mineSupportOutlineRgb = Utils.getprefi("moon-mine-safe-outline-rgb", 0x00FFB028);
    /**
     * Red tint on cave/mine floor tiles that lie <b>outside</b> any mine-support disc (collapse-risk hint).
     * Works together with or without {@link #mineSupportSafeTiles}; both are drawn from {@link MoonMiningOverlay}.
     */
    public static boolean mineSweeperRiskTiles = false;
    /** Fill alpha for {@link #mineSweeperRiskTiles} (0 = off). */
    public static int mineRiskFillAlpha = Utils.getprefi("moon-mine-risk-fill-a", 56);
    /** RGB for risk fill (alpha from {@link #mineRiskFillAlpha}). Default dark red. */
    public static int mineRiskFillRgb = Utils.getprefi("moon-mine-risk-fill-rgb", 0x00C03030);

    /** Floating IP/damage readout + mob HP bars while fighting ({@link MoonFightHud}). */
    public static boolean combatDamageHud = Utils.getprefb("moon-combat-damage-hud", true);
    public static boolean combatHudShowDealt = Utils.getprefb("moon-combat-hud-dealt", true);
    public static boolean combatHudShowTaken = Utils.getprefb("moon-combat-hud-taken", true);
    /**
     * 0 = HP bar only when not full; 1 = whenever {@link GobHealth} exists (default);
     * 2 = always show a bar in fight (even without GobHealth — neutral fill).
     */
    public static int combatHudEnemyHpMode = Utils.getprefi("moon-combat-hud-hp", 1);
    /**
     * Swap fightview ip/oip interpretation for dealt vs taken if your build/server order differs.
     */
    public static boolean combatHudSwapFightIp = Utils.getprefb("moon-combat-hud-swap-ip", false);
    /** 0 = persist dealt only for current/seen opponents; 1 = persist dealt for all observed targets. */
    public static int combatHudPersistScope = Utils.getprefi("moon-combat-hud-persist-scope", 0);

    /**
     * Force bright daylight lighting and calendar graphics locally while the server reports night.
     * World time / mechanics are unchanged; only client visuals.
     */
    public static boolean alwaysDaylight = Utils.getprefb("moon-always-daylight", false);
    /** Live modular graphics toggles (in-game, no restart). */
    public static boolean gfxModEspOverlay = Utils.getprefb("moon-gfxmod-esp-overlay", false);
    public static boolean gfxModXray = Utils.getprefb("moon-gfxmod-xray", false);
    public static boolean gfxModHitboxes = Utils.getprefb("moon-gfxmod-hitboxes", false);
    public static boolean gfxModMiningOverlay = Utils.getprefb("moon-gfxmod-mining-overlay", true);
    public static boolean gfxModFightHud = Utils.getprefb("moon-gfxmod-fight-hud", true);
    public static boolean gfxModActivityHud = Utils.getprefb("moon-gfxmod-activity-hud", true);
    public static boolean showFpsPingHud = Utils.getprefb("moon-fps-ping-hud", false);

    /**
     * Blend factor toward white for world lighting (brighter nights). 0 = server light only, max 0.65.
     * Reads legacy {@code nightVisionSetting} when {@code moon-night-vision-blend} is unset.
     */
    private static double initNightVisionBlend() {
	double v = (Utils.prefs().get("moon-night-vision-blend", null) != null)
	    ? Utils.getprefd("moon-night-vision-blend", 0)
	    : Utils.getprefd("nightVisionSetting", 0);
	return(Utils.clip(v, 0.0, 0.65));
    }
    public static double nightVisionBlend = initNightVisionBlend();

    /** Uniform scale for trees and bushes (30–100%). Legacy {@code treeAndBushScale} if moon pref unset. */
    private static int initTreeBushScalePct() {
	int v = (Utils.prefs().get("moon-treebush-scale-pct", null) != null)
	    ? Utils.getprefi("moon-treebush-scale-pct", 100)
	    : Utils.getprefi("treeAndBushScale", 100);
	return(Utils.clip(v, 30, 100));
    }
    /** Vertical scale for palisade / brick wall segments (40–100%). Legacy {@code palisadesAndBrickWallsScale} if unset. */
    private static int initPalisadeWallScalePct() {
	int v = (Utils.prefs().get("moon-palisade-z-pct", null) != null)
	    ? Utils.getprefi("moon-palisade-z-pct", 100)
	    : Utils.getprefi("palisadesAndBrickWallsScale", 100);
	return(Utils.clip(v, 40, 100));
    }
    public static int treeBushScalePct = initTreeBushScalePct();
    public static int palisadeWallScalePct = initPalisadeWallScalePct();
    public static boolean cropHide = Utils.getprefb("moon-crop-hide", false);
    public static boolean cropShowStage = Utils.getprefb("moon-crop-stage", false);
    public static int cropScalePct = Utils.clip(Utils.getprefi("moon-crop-scale-pct", 100), 30, 200);

    /** Draw 2D lines from {@link Homing} movers to their target gob. */
    public static boolean combatDrawChaseVectors = Utils.getprefb("moon-combat-chase-vec", false);
    /** Line from player to last plain ground click while {@link LinMove} is active. */
    public static boolean combatDrawClickPath = Utils.getprefb("moon-combat-click-path", true);
    /** ARGB line color (alpha in high byte). */
    public static int combatChaseArgbSelf = Utils.getprefi("moon-combat-chase-self-argb", 0xff4ecdc4);
    public static int combatChaseArgbFriend = Utils.getprefi("moon-combat-chase-friend-argb", 0xff6bcb77);
    public static int combatChaseArgbEnemy = Utils.getprefi("moon-combat-chase-enemy-argb", 0xffff6b6b);
    public static int combatPathClickArgb = Utils.getprefi("moon-combat-path-argb", 0xffffff88);

    /** Auto-walk away when hostiles enter radius (see {@link MoonSafeMode}). */
    public static boolean safeMode = Utils.getprefb("moon-safe-mode", false);
    /** Only apply safe mode while the activity progress indicator is shown. */
    public static boolean safeModeBusyOnly = Utils.getprefb("moon-safe-busyonly", true);
    /** Do not retreat while a fight target is active. */
    public static boolean safeModeRespectCombat = Utils.getprefb("moon-safe-nofight", true);
    /** Minimum distance to keep from the nearest hostile (world tiles). */
    public static double safeModeMinTiles = Utils.getprefd("moon-safe-mintiles", 4.0);

    private static final double DEFAULT_NAV_MAX_TILES = 2000.0;
    private static final double MAX_NAV_MAX_TILES = 4096.0;

    /**
     * Navigation assist ({@link TeleportManager}): saved points + optional outgoing click clamp.
     * Server still validates movement.
     */
    public static boolean teleportNavEnabled = Utils.getprefb("moon-tp-on", false);
    /** When on, ground clicks on {@link MapView} beyond {@link #teleportMaxTiles} are clamped to the max radius if the new tile passes checks. */
    public static boolean teleportClickClamp = Utils.getprefb("moon-tp-clamp", false);
    /** Require a client {@link MoonPathfinder} path to the target before sending a click. */
    public static boolean teleportRequirePath = Utils.getprefb("moon-tp-path", false);
    /** Passive LMB move assist: when the direct segment is blocked, auto-start a shortest detour path. */
    public static boolean teleportPassiveLmbPathfinding = Utils.getprefb("moon-tp-passive-lmb-path", false);
    /** Reject targets with a hostile mob within {@link #teleportHostileClearTiles} world tiles. */
    public static boolean teleportBlockHostileNearTarget = Utils.getprefb("moon-tp-nohostile", true);
    public static int teleportHostileClearTiles = Math.max(1, Utils.getprefi("moon-tp-hostile-tiles", 4));
    public static double teleportMaxTiles = initTeleportMaxTilesPref();
    public static int teleportCooldownMs = Math.max(0, Utils.getprefi("moon-tp-cooldown-ms", 5000));
    /** Max |Δz| between player tile centre and target tile centre (world units). */
    public static double teleportMaxHeightDelta = Utils.getprefd("moon-tp-max-dz", 120.0);
    /** Optional {@link Glob#getcattr} key; gate off when empty or {@link #teleportMinAttrSum} is 0. */
    public static String teleportMinAttrKey = Utils.getpref("moon-tp-attr-key", "");
    /** Minimum {@code base+comp} for {@link #teleportMinAttrKey} when set. */
    public static int teleportMinAttrSum = Utils.getprefi("moon-tp-attr-min", 0);
    public static boolean teleportLogStderr = Utils.getprefb("moon-tp-log-stderr", false);

    /** Text above the player: craft/gather %, ETA, structure HP. */
    public static boolean activityHud = Utils.getprefb("moon-activity-hud", true);
    /** Search radius (tiles) for damaged {@link GobHealth} targets. */
    public static double activityHudStructRange = Utils.getprefd("moon-activity-hud-range", 10.0);

    /** Used when {@link #alwaysDaylight} is on but no daytime sample has been cached yet (e.g. login at night). */
    public static final Color DAYLIGHT_FALLBACK_AMB = new Color(176, 184, 208);
    public static final Color DAYLIGHT_FALLBACK_DIF = new Color(255, 252, 235);
    public static final Color DAYLIGHT_FALLBACK_SPC = new Color(255, 255, 255);
    public static final double DAYLIGHT_FALLBACK_ANG = 0.0;
    public static final double DAYLIGHT_FALLBACK_ELEV = 1.47;

    public static void save(String key, boolean val) {
        Utils.setprefb(key, val);
        try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void save(String key, int val) {
        Utils.setprefi(key, val);
        try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void save(String key, double val) {
        Utils.setprefd(key, val);
        try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void save(String key, String val) {
	Utils.setpref(key, val == null ? "" : val);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    private static String normalizeBulkStationKey(String key) {
	if(key == null)
	    return null;
	key = key.trim().toLowerCase(Locale.ROOT);
	return key.isEmpty() ? null : key;
    }

    public static Set<String> bulkStationBlacklistSet() {
	LinkedHashSet<String> out = new LinkedHashSet<>();
	String raw = bulkStationBlacklist;
	if(raw == null || raw.trim().isEmpty())
	    return out;
	for(String part : raw.split("[,;\\s]+")) {
	    String key = normalizeBulkStationKey(part);
	    if(key != null)
		out.add(key);
	}
	return out;
    }

    public static boolean bulkStationBlacklistContains(String stationKey) {
	String key = normalizeBulkStationKey(stationKey);
	return key != null && bulkStationBlacklistSet().contains(key);
    }

    public static void setBulkStationBlacklistKeys(Set<String> keys) {
	List<String> norm = new ArrayList<>();
	if(keys != null) {
	    for(String key : keys) {
		String nk = normalizeBulkStationKey(key);
		if(nk != null)
		    norm.add(nk);
	    }
	}
	bulkStationBlacklist = String.join(",", norm);
	save("moon-bulk-station-blacklist", bulkStationBlacklist);
    }

    public static void setBulkStationBlacklisted(String stationKey, boolean disabled) {
	LinkedHashSet<String> keys = new LinkedHashSet<>(bulkStationBlacklistSet());
	String nk = normalizeBulkStationKey(stationKey);
	if(nk == null)
	    return;
	if(disabled)
	    keys.add(nk);
	else
	    keys.remove(nk);
	setBulkStationBlacklistKeys(keys);
    }

    public static void setMapGridMode(int v) {
	mapGridMode = Math.floorMod(v, 3);
	save("moon-map-grid-mode", mapGridMode);
    }

    public static void setTraceHostile(boolean v) { traceHostile = v; save("moon-trace-hostile", v); if(v) ensureOverlayRendererEnabled(); }
    public static void setTraceNeutralMobs(boolean v) { traceNeutralMobs = v; save("moon-trace-neutral", v); if(v) ensureOverlayRendererEnabled(); }
    public static void setTracePlayers(boolean v) { tracePlayers = v; save("moon-trace-players", v); if(v) ensureOverlayRendererEnabled(); }

    public static void setEspEnabled(boolean v) { espEnabled = v; save("moon-esp", v); if(v) ensureOverlayRendererEnabled(); }
    public static void setEspDistance(boolean v) { espDistance = v; save("moon-esp-dist", v); }
    public static void setEspNames(boolean v) { espNames = v; save("moon-esp-names", v); }
    public static void setEspVehicles(boolean v) { espVehicles = v; save("moon-esp-vehicles", v); }
    public static void setEspBuildings(boolean v) { espBuildings = v; save("moon-esp-buildings", v); }
    public static void setEspResources(boolean v) { espResources = v; save("moon-esp-resources", v); }
    public static void setEspContainers(boolean v) { espContainers = v; save("moon-esp-containers", v); }

    public static void setXrayEnabled(boolean v) { xrayEnabled = v; save("moon-xray", v); if(v) ensureOverlayRendererEnabled(); }
    public static void setXrayColorIdx(int v) {
        int n = MoonEspProfile.presetCount();
        xrayColorIdx = (v % n + n) % n;
        save("moon-xray-col", xrayColorIdx);
    }
    public static Color xrayColor() {
        return MoonEspProfile.preset(xrayColorIdx);
    }
    public static void setXrayGhostMax(int v) { xrayGhostMax = v; save("moon-xray-max", v); }
    public static void setXrayGhostMaxDist(double v) { xrayGhostMaxDist = v; save("moon-xray-maxdist", v); }

    public static void setXrayStyle(int v) {
	xrayStyle = Math.floorMod(v, 2);
	save("moon-xray-style", xrayStyle);
    }

    public static void setXrayHideMode(int v) {
	xrayHideMode = Math.floorMod(v, 2);
	save("moon-xray-hide-mode", xrayHideMode);
    }

    public static void setFightOverlayFixed(boolean v) {
	fightOverlayFixed = v;
	save("moon-fight-overlay-fixed", v);
    }

    public static void setQualityObjects(boolean v) {
	qualityObjects = v;
	save("moon-quality-objs", v);
	bumpInvQualitySettingsRev();
    }

    public static void setInvQualityRounded(boolean v) {
	invQualityRounded = v;
	save("moon-inv-quality-rounded", v);
	bumpInvQualitySettingsRev();
    }

    public static void setInvQualityCustomColors(boolean v) {
	invQualityCustomColors = v;
	save("moon-inv-quality-custom", v);
	bumpInvQualitySettingsRev();
    }

    public static void setInvQualityCorner(int v) {
	invQualityCorner = Math.floorMod(v, 5);
	save("moon-inv-qual-corner", invQualityCorner);
	/* No bumpInvQualitySettingsRev: label bitmap unchanged; only placement changes in draw. */
    }

    public static void setInvQualityFontPx(int v) {
	invQualityFontPx = Utils.clip(v, 6, 32);
	save("moon-inv-qual-font-px", invQualityFontPx);
	bumpInvQualitySettingsRev();
    }

    public static void setInvQualityThreshold(int tier, int v) {
	if(tier < 0 || tier >= INV_QUAL_TIER_COUNT)
	    return;
	int c = Utils.clip(v, 0, 1_000_000);
	invQualityThreshold[tier] = c;
	save("moon-inv-qual-th-" + tier, c);
	bumpInvQualitySettingsRev();
    }

    public static void setInvQualityColorArgb(int tier, int argb) {
	if(tier < 0 || tier >= INV_QUAL_TIER_COUNT)
	    return;
	invQualityColorArgb[tier] = argb;
	save("moon-inv-qual-col-" + tier, argb);
	bumpInvQualitySettingsRev();
    }

    public static void resetInvQualityTierDefaults(int tier) {
	if(tier < 0 || tier >= INV_QUAL_TIER_COUNT)
	    return;
	invQualityThreshold[tier] = INV_QUAL_TH_DEFAULT[tier];
	invQualityColorArgb[tier] = INV_QUAL_COL_DEFAULT[tier];
	save("moon-inv-qual-th-" + tier, invQualityThreshold[tier]);
	save("moon-inv-qual-col-" + tier, invQualityColorArgb[tier]);
	bumpInvQualitySettingsRev();
    }

    /**
     * If present in shared Java preferences, reads legacy keys ({@code qtoggle}, {@code roundedQuality}, threshold and
     * color entries) into this client's inventory-quality prefs. Returns false when none of those keys exist.
     */
    public static boolean importLegacyInvQualityPrefs() {
	try {
	    boolean any = prefsKeyExists("qtoggle")
		|| prefsKeyExists("roundedQuality")
		|| prefsKeyExists("enableCustomQualityColors")
		|| prefsKeyExists("q7ColorTextEntry");
	    if(!any)
		return(false);

	    if(prefsKeyExists("qtoggle"))
		setQualityObjects(Utils.getprefb("qtoggle", qualityObjects));
	    setInvQualityRounded(Utils.getprefb("roundedQuality", invQualityRounded));
	    setInvQualityCustomColors(Utils.getprefb("enableCustomQualityColors", invQualityCustomColors));

	    String[] thHur = {"q7ColorTextEntry", "q6ColorTextEntry", "q5ColorTextEntry", "q4ColorTextEntry",
		"q3ColorTextEntry", "q2ColorTextEntry", "q1ColorTextEntry"};
	    for(int i = 0; i < INV_QUAL_TIER_COUNT; i++) {
		String raw = Utils.getpref(thHur[i], null);
		if(raw != null && !raw.trim().isEmpty()) {
		    try {
			int parsed = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
			setInvQualityThreshold(i, parsed);
		    } catch(NumberFormatException ignored) {}
		}
	    }

	    String[] hurCol = {
		"q7ColorSetting_colorSetting", "q6ColorSetting_colorSetting", "q5ColorSetting_colorSetting",
		"q4ColorSetting_colorSetting", "q3ColorSetting_colorSetting", "q2ColorSetting_colorSetting",
		"q1ColorSetting_colorSetting"
	    };
	    String[][] defs = {
		{"255", "0", "0", "255"}, {"255", "114", "0", "255"}, {"165", "0", "255", "255"},
		{"0", "131", "255", "255"}, {"0", "214", "10", "255"}, {"255", "255", "255", "255"},
		{"180", "180", "180", "255"}
	    };
	    for(int i = 0; i < INV_QUAL_TIER_COUNT; i++) {
		List<String> ls = Utils.getprefsl(hurCol[i], defs[i]);
		if(ls != null && ls.size() >= 4) {
		    try {
			int r = Integer.parseInt(ls.get(0).trim());
			int g = Integer.parseInt(ls.get(1).trim());
			int b = Integer.parseInt(ls.get(2).trim());
			int a = Integer.parseInt(ls.get(3).trim());
			int argb = ((a & 0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
			setInvQualityColorArgb(i, argb);
		    } catch(Exception ignored) {}
		}
	    }
	    return(true);
	} catch(Exception e) {
	    return(false);
	}
    }

    private static boolean prefsKeyExists(String key) {
	try {
	    return(Utils.prefs().get(key, null) != null);
	} catch(SecurityException e) {
	    return(false);
	}
    }

    public static String formatInvQualityNumber(double q) {
	if(invQualityRounded) {
	    long r = Math.round(q);
	    /* Integer rounding would show 0 for 0 < q < 0.5 and hide low quality. */
	    if(r == 0 && q > 0)
		return(String.format(Locale.US, "%.1f", q));
	    return(String.format(Locale.US, "%.0f", (double)r));
	}
	return(String.format(Locale.US, "%.1f", q));
    }

    public static Color invQualityOverlayColor(double q) {
	if(!invQualityCustomColors)
	    return(new Color(220, 255, 180));
	for(int i = 0; i < INV_QUAL_TIER_COUNT; i++) {
	    if(q >= invQualityThreshold[i]) {
		int c = invQualityColorArgb[i];
		return(new Color((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff, (c >>> 24) & 0xff));
	    }
	}
	int c = invQualityColorArgb[INV_QUAL_TIER_COUNT - 1];
	return(new Color((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff, (c >>> 24) & 0xff));
    }

    public static void setAutoDrink(boolean v) { autoDrink = v; save("moon-autodrink", v); }
    public static void setAutoDrinkThreshold(int v) { autoDrinkThreshold = v; save("moon-autodrink-pct", v); }
    public static void setAutoDrinkIntervalSec(int v) { autoDrinkIntervalSec = Math.max(1, v); save("moon-autodrink-interval-sec", autoDrinkIntervalSec); }
    public static void setAutoDrinkLiquid(String v) { autoDrinkLiquid = (v == null || v.isEmpty()) ? "Water" : v; Utils.setpref("moon-autodrink-liquid", autoDrinkLiquid); try { Utils.prefs().flush(); } catch(Exception ignored) {} }
    public static void setAutoDrinkWhatever(boolean v) { autoDrinkWhatever = v; save("moon-autodrink-whatever", v); }
    public static void setAutoDrinkUseSip(boolean v) { autoDrinkUseSip = v; save("moon-autodrink-use-sip", v); }
    public static void setAutoDrinkSmartSip(boolean v) { autoDrinkSmartSip = v; save("moon-autodrink-smart-sip", v); }
    public static void setAutoSipThreshold(int v) { autoSipThreshold = Utils.clip(v, 10, 100); save("moon-autosip-threshold", autoSipThreshold); }
    public static void setAutoSipOnce(boolean v) { autoSipOnce = v; save("moon-autosip-once", v); }
    public static void setAutoSipWaitMs(int v) { autoSipWaitMs = Math.max(200, v); save("moon-autosip-wait-ms", autoSipWaitMs); }
    public static void setAutoDrinkMessage(boolean v) { autoDrinkMessage = v; save("moon-autodrink-message", v); }
    public static void setAutoDrinkDirectSip(boolean v) { autoDrinkDirectSip = v; save("moon-autodrink-direct-sip", v); }
    public static void setAutoDrinkServerHook(boolean v) { autoDrinkServerHook = v; save("moon-autodrink-server-hook", v); }
    public static void setAutoDrinkDirectSipIntervalMs(int v) {
	autoDrinkDirectSipIntervalMs = Utils.clip(v, 150, 2000);
	save("moon-autodrink-direct-sip-interval-ms", autoDrinkDirectSipIntervalMs);
    }

    public static void setCombatBot(boolean v) { combatBot = v; save("moon-combatbot", v); }

    public static void setCombatBotBrainMode(int v) {
	combatBotBrainMode = Utils.clip(v, 0, 2);
	save("moon-combatbot-brain", combatBotBrainMode);
    }

    public static void setCombatBotAggression(int v) {
	combatBotAggression = Utils.clip(v, 0, 100);
	save("moon-combatbot-agg", combatBotAggression);
    }

    public static void setCombatBotLogAi(boolean v) { combatBotLogAi = v; save("moon-combatbot-log", v); }

    public static void setCombatBotTrustHints(boolean v) { combatBotTrustHints = v; save("moon-combatbot-trust-hints", v); }

    public static void setCombatBotSearchDepth(int v) {
	combatBotSearchDepth = Utils.clip(v, 2, 3);
	save("moon-combatbot-depth", combatBotSearchDepth);
    }

    public static void setCombatBotRiskAversion(double v) {
	combatBotRiskAversion = Utils.clip(v, 0, 3.0);
	Utils.setprefd("moon-combatbot-risk", combatBotRiskAversion);
	try { Utils.prefs().flush(); } catch(Exception ignored) {}
    }

    public static void setTreeBotHitstack(boolean v) { treeBotHitstack = v; save("moon-treebot-hitstack", v); }
    public static void setTreeBotAvoidMobs(boolean v) { treeBotAvoidMobs = v; save("moon-treebot-mobs", v); }
    public static void setTreeBotWater(boolean v) { treeBotWater = v; save("moon-treebot-water", v); }
    public static void setTreeBotEnabled(boolean v) { treeBotEnabled = v; save("moon-treebot-on", v); }

    public static void setTreeBotAutoPipeline(boolean v) {
	treeBotAutoPipeline = v;
	save("moon-treebot-auto-pipeline", v);
    }

    public static void setTreeBotHudLine(boolean v) {
	treeBotHudLine = v;
	save("moon-treebot-hud-line", v);
    }

    public static void setTreeBotWorkPhase(int v) {
	treeBotWorkPhase = Utils.clip(v, 0, 3);
	save("moon-treebot-phase", treeBotWorkPhase);
    }

    public static void setTreeBotStaminaMinPct(int v) {
	treeBotStaminaMinPct = Utils.clip(v, 0, 100);
	save("moon-treebot-stam-min", treeBotStaminaMinPct);
    }

    public static void setTreeBotDiscordNotify(boolean v) {
	treeBotDiscordNotify = v;
	save("moon-treebot-discord", v);
    }

    public static void setTreeBotHitboxOverlay(boolean v) {
	treeBotHitboxOverlay = v;
	save("moon-treebot-hitbox-viz", v);
    }

    public static void setMineBotEnabled(boolean v) { mineBotEnabled = v; save("moon-minebot-on", v); }
    public static void setMineBotAvoidMobs(boolean v) { mineBotAvoidMobs = v; save("moon-minebot-mobs", v); }
    public static void setMineBotWater(boolean v) { mineBotWater = v; save("moon-minebot-water", v); }
    public static void setMineBotHudLine(boolean v) { mineBotHudLine = v; save("moon-minebot-hud", v); }
    public static void setMineBotDiagLog(boolean v) { mineBotDiagLog = v; save("moon-minebot-diag", v); }
    public static void setMineBotAutoHighlight(boolean v) { mineBotAutoHighlight = v; save("moon-minebot-autoviz", v); }
    public static void setMineBotMode(int v) {
	mineBotMode = Utils.clip(v, 0, 1);
	save("moon-minebot-ui-mode", mineBotMode);
    }
    public static void setMineBotDirection(int v) {
	mineBotDirection = Math.floorMod(v, 4);
	save("moon-minebot-dir", mineBotDirection);
    }
    public static void setMineBotSupportType(int v) {
	mineBotSupportType = Utils.clip(v, 0, 3);
	save("moon-minebot-support-type", mineBotSupportType);
    }
    public static void setMineAutoDropEnabled(boolean v) {
	mineAutoDropEnabled = v;
	save("moon-mine-autodrop-enabled", v);
    }
    public static void setMineAutoDropCategories(String v) {
	mineAutoDropCategories = (v != null) ? v.trim() : "";
	Utils.setpref("moon-mine-autodrop-categories", mineAutoDropCategories);
	try { Utils.prefs().flush(); } catch(Exception ignored) {}
    }
    public static void setMineSweeperShowLabels(boolean v) { mineSweeperShowLabels = v; save("moon-mine-sweeper-show-labels", v); }
    public static void setOverlayTextSize(int v) {
	overlayTextSize = Utils.clip(v, 8, 32);
	save("moon-overlay-text-size", overlayTextSize);
    }
    public static void setOverlayTextRgb(int v) {
	overlayTextRgb = v & 0xffffff;
	save("moon-overlay-text-rgb", overlayTextRgb);
    }
    public static void setInventorySlotNumbers(boolean v) { inventorySlotNumbers = v; save("moon-inv-slot-numbers", v); }
    public static void setInventorySlotNumberSize(int v) {
	inventorySlotNumberSize = Utils.clip(v, 8, 24);
	save("moon-inv-slot-number-size", inventorySlotNumberSize);
    }
    public static void setInventorySlotNumberRgb(int v) {
	inventorySlotNumberRgb = v & 0xffffff;
	save("moon-inv-slot-number-rgb", inventorySlotNumberRgb);
    }

    public static void setMineBotTargetMode(int v) {
	mineBotTargetMode = Utils.clip(v, 0, 2);
	save("moon-minebot-mode", mineBotTargetMode);
    }

    public static void setMineBotLooseMatch(boolean v) { mineBotLooseMatch = v; save("moon-minebot-loose", v); }

    public static void setMineBotStaminaMinPct(int v) {
	mineBotStaminaMinPct = Utils.clip(v, 0, 100);
	save("moon-minebot-stam-min", mineBotStaminaMinPct);
    }

    public static void setMineSupportShowHp(boolean v) {
	mineSupportShowHp = v;
	save("moon-mine-support-hp", v);
    }

    public static void setMineSupportTextSize(int v) {
	mineSupportTextSize = Utils.clip(v, 8, 28);
	save("moon-mine-support-text-size", mineSupportTextSize);
    }

    public static void setMineSweeperTextSize(int v) {
	mineSweeperTextSize = Utils.clip(v, 8, 28);
	save("moon-mine-sweeper-text-size", mineSweeperTextSize);
    }

    public static void setMineSupportTextRgb(int v) {
	mineSupportTextRgb = v & 0xffffff;
	save("moon-mine-support-text-rgb", mineSupportTextRgb);
    }

    public static void setMineSweeperSafeRgb(int v) {
	mineSweeperSafeRgb = v & 0xffffff;
	save("moon-mine-sweeper-safe-rgb", mineSweeperSafeRgb);
    }

    public static void setMineSweeperAutoSafeRgb(int v) {
	mineSweeperAutoSafeRgb = v & 0xffffff;
	save("moon-mine-sweeper-auto-rgb", mineSweeperAutoSafeRgb);
    }

    public static void setMineSweeperRiskRgb(int v) {
	mineSweeperRiskRgb = v & 0xffffff;
	save("moon-mine-sweeper-risk-rgb", mineSweeperRiskRgb);
    }

    public static void setGlobalHitboxMode(int v) {
	globalHitboxMode = Math.floorMod(v, 3);
	save("moon-global-hitbox-mode", globalHitboxMode);
    }

    public static void setEntityHitboxVizMode(int v) {
	entityHitboxVizMode = Math.floorMod(v, 3);
	save("moon-entity-hitbox-viz-mode", entityHitboxVizMode);
    }

    public static void cycleEntityHitboxViz() {
	setEntityHitboxVizMode(entityHitboxVizMode + 1);
    }

    public static void setBulkStationFill(boolean v) {
	bulkStationFill = v;
	save("moon-bulk-station-fill", v);
    }

    public static void setBulkStationTakeAll(boolean v) {
	bulkStationTakeAll = v;
	save("moon-bulk-station-takeall", v);
    }

    public static void setBulkStationRepeatIntervalMs(int v) {
	bulkStationRepeatIntervalMs = Utils.clip(v, 50, 1000);
	save("moon-bulk-station-interval-ms", bulkStationRepeatIntervalMs);
    }

    public static void setBulkStationMaxRepeats(int v) {
	bulkStationMaxRepeats = Utils.clip(v, 1, 256);
	save("moon-bulk-station-max-repeats", bulkStationMaxRepeats);
    }

    public static void setBulkStationStallRepeats(int v) {
	bulkStationStallRepeats = Utils.clip(v, 0, 256);
	save("moon-bulk-station-stall-repeats", bulkStationStallRepeats);
    }

    public static void setBulkTubSendFirstItemact(boolean v) {
	bulkTubSendFirstItemact = v;
	save("moon-bulk-tub-send-first-itemact", v);
    }

    public static void setBulkTubMaxRepeatsPerDeposit(int v) {
	bulkTubMaxRepeatsPerDeposit = Utils.clip(v, 1, 64);
	save("moon-bulk-tub-max-repeats-deposit", bulkTubMaxRepeatsPerDeposit);
    }

    public static void setBulkTubReplayIntervalMult(double v) {
	if(Double.isNaN(v) || v < 1.0)
	    v = 1.0;
	bulkTubReplayIntervalMult = Math.min(v, 5.0);
	save("moon-bulk-tub-replay-interval-mult", bulkTubReplayIntervalMult);
    }

    public static void setBulkStationFillModMask(int mask) {
	bulkStationFillModMask = normalizeBulkStationFillModMask(mask);
	save("moon-bulk-fill-mod-mask", bulkStationFillModMask);
    }

    public static void setDebugInventoryWire(boolean v) {
	debugInventoryWire = v;
	save("moon-debug-inventory-wire", v);
    }

    public static void setDebugOutgoingWdgmsg(boolean v) {
	debugOutgoingWdgmsg = v;
	save("moon-debug-outgoing-wdgmsg", v);
    }

    public static void setDebugErrLatencyLog(boolean v) {
	debugErrLatencyLog = v;
	save("moon-debug-err-latency", v);
    }

    public static void setErrLatencyRingSize(int v) {
	errLatencyRingSize = Utils.clip(v, 8, 256);
	save("moon-err-latency-ring", errLatencyRingSize);
    }

    public static void setMenuGridKeyboard(boolean v) {
	menuGridKeyboard = v;
	save("moon-menugrid-keyboard", v);
    }

    public static void setDiabloMove(boolean v) { diabloMove = v; save("moon-diablo-move", v); }
    public static void setMapLoadExtraCuts(int v) {
	mapLoadExtraCuts = Utils.clip(v, 0, 12);
	save("moon-map-load-extra-cuts", mapLoadExtraCuts);
    }
    public static void setPerfPreset(PerfPreset v) {
	perfPreset = (v != null) ? v : PerfPreset.BALANCED;
	save("moon-perf-preset", perfPreset.id);
    }
    private static RuntimeTune presetRuntimeTune(RuntimeCpuProfile profile) {
	RuntimeCpuProfile mode = (profile != null) ? profile : RuntimeCpuProfile.AUTO;
	switch(mode) {
	case AMD_RYZEN7_8C:
	    return(new RuntimeTune(8, 3, 4, 2, 6));
	case AMD_RYZEN_X3D_8C:
	    return(new RuntimeTune(8, 3, 4, 2, 6));
	case INTEL_I5_6C:
	    return(new RuntimeTune(6, 2, 3, 2, 6));
	case INTEL_HYBRID_MID:
	    return(new RuntimeTune(8, 3, 4, 2, 6));
	case INTEL_HYBRID_HIGH:
	    return(new RuntimeTune(10, 4, 4, 2, 8));
	case CUSTOM:
	    return(runtimeCustomTune());
	case AMD_RYZEN5_6C:
	case AUTO:
	default:
	    return(new RuntimeTune(6, 3, 4, 2, 6));
	}
    }

    private static RuntimeTune runtimeCustomTune() {
	int min = Utils.clip(runtimeHeapMinGb, 1, 8);
	int max = Utils.clip(runtimeHeapMaxGb, min + 1, 16);
	return(new RuntimeTune(
	    Utils.clip(runtimeActiveProcessors, 2, 16),
	    Utils.clip(runtimeLoaderThreads, 1, 8),
	    Utils.clip(runtimeDeferThreads, 1, 8),
	    min,
	    max));
    }

    public static RuntimeTune runtimeTune() {
	return((runtimeCpuProfile == RuntimeCpuProfile.CUSTOM) ? runtimeCustomTune() : presetRuntimeTune(runtimeCpuProfile));
    }

    private static void syncRuntimeCustoms(RuntimeTune tune) {
	if(tune == null)
	    return;
	runtimeActiveProcessors = Utils.clip(tune.activeProcessors, 2, 16);
	runtimeLoaderThreads = Utils.clip(tune.loaderThreads, 1, 8);
	runtimeDeferThreads = Utils.clip(tune.deferThreads, 1, 8);
	runtimeHeapMinGb = Utils.clip(tune.heapMinGb, 1, 8);
	runtimeHeapMaxGb = Utils.clip(tune.heapMaxGb, runtimeHeapMinGb + 1, 16);
	save("moon-runtime-active-processors", runtimeActiveProcessors);
	save("moon-runtime-loader-threads", runtimeLoaderThreads);
	save("moon-runtime-defer-threads", runtimeDeferThreads);
	save("moon-runtime-heap-min-gb", runtimeHeapMinGb);
	save("moon-runtime-heap-max-gb", runtimeHeapMaxGb);
    }

    private static Path runtimeEnvPath() {
	String explicit = System.getProperty("haven.runtime.env.path", "").trim();
	if(!explicit.isEmpty())
	    return(Paths.get(explicit).toAbsolutePath().normalize());
	Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
	Path name = cwd.getFileName();
	if((name != null) && name.toString().equalsIgnoreCase("bin")) {
	    Path clientRoot = cwd.getParent();
	    if((clientRoot != null) && (clientRoot.getFileName() != null) &&
	       clientRoot.getFileName().toString().equalsIgnoreCase("hafen-client-new")) {
		Path root = clientRoot.getParent();
		if(root != null)
		    return(root.resolve("bin").resolve("moon-runtime.env.bat"));
	    }
	}
	return(cwd.resolve("moon-runtime.env.bat"));
    }

    private static void persistRuntimeEnv() {
	RuntimeTune tune = runtimeTune();
	Path path = runtimeEnvPath();
	List<String> lines = new ArrayList<>();
	lines.add("@echo off");
	lines.add(String.format(Locale.ROOT, "set \"MOON_CPU_PROFILE=%s\"", runtimeCpuProfile.name()));
	lines.add(String.format(Locale.ROOT, "set \"MOON_HEAP_MIN=%dG\"", tune.heapMinGb));
	lines.add(String.format(Locale.ROOT, "set \"MOON_HEAP_MAX=%dG\"", tune.heapMaxGb));
	lines.add(String.format(Locale.ROOT, "set \"MOON_ACTIVE_PROCESSOR_COUNT=%d\"", tune.activeProcessors));
	lines.add(String.format(Locale.ROOT, "set \"MOON_LOADER_THREADS=%d\"", tune.loaderThreads));
	lines.add(String.format(Locale.ROOT, "set \"MOON_DEFER_THREADS=%d\"", tune.deferThreads));
	try {
	    Path parent = path.getParent();
	    if(parent != null)
		Files.createDirectories(parent);
	    Files.write(path, lines, StandardCharsets.UTF_8);
	} catch(IOException e) {
	    System.err.println("[MoonRuntime] Failed to persist runtime profile: " + path + " (" + e + ")");
	}
    }

    public static void setRuntimeCpuProfile(RuntimeCpuProfile v) {
	runtimeCpuProfile = (v != null) ? v : RuntimeCpuProfile.AUTO;
	save("moon-runtime-cpu-profile", runtimeCpuProfile.id);
	if(runtimeCpuProfile != RuntimeCpuProfile.CUSTOM)
	    syncRuntimeCustoms(presetRuntimeTune(runtimeCpuProfile));
	persistRuntimeEnv();
    }

    public static void setRuntimeActiveProcessors(int v) {
	runtimeCpuProfile = RuntimeCpuProfile.CUSTOM;
	save("moon-runtime-cpu-profile", runtimeCpuProfile.id);
	runtimeActiveProcessors = Utils.clip(v, 2, 16);
	save("moon-runtime-active-processors", runtimeActiveProcessors);
	if(runtimeHeapMaxGb <= runtimeHeapMinGb)
	    setRuntimeHeapMaxGb(runtimeHeapMinGb + 1);
	persistRuntimeEnv();
    }

    public static void setRuntimeLoaderThreads(int v) {
	runtimeCpuProfile = RuntimeCpuProfile.CUSTOM;
	save("moon-runtime-cpu-profile", runtimeCpuProfile.id);
	runtimeLoaderThreads = Utils.clip(v, 1, 8);
	save("moon-runtime-loader-threads", runtimeLoaderThreads);
	persistRuntimeEnv();
    }

    public static void setRuntimeDeferThreads(int v) {
	runtimeCpuProfile = RuntimeCpuProfile.CUSTOM;
	save("moon-runtime-cpu-profile", runtimeCpuProfile.id);
	runtimeDeferThreads = Utils.clip(v, 1, 8);
	save("moon-runtime-defer-threads", runtimeDeferThreads);
	persistRuntimeEnv();
    }

    public static void setRuntimeHeapMinGb(int v) {
	runtimeCpuProfile = RuntimeCpuProfile.CUSTOM;
	save("moon-runtime-cpu-profile", runtimeCpuProfile.id);
	runtimeHeapMinGb = Utils.clip(v, 1, 8);
	if(runtimeHeapMaxGb <= runtimeHeapMinGb)
	    runtimeHeapMaxGb = Math.min(16, runtimeHeapMinGb + 1);
	save("moon-runtime-heap-min-gb", runtimeHeapMinGb);
	save("moon-runtime-heap-max-gb", runtimeHeapMaxGb);
	persistRuntimeEnv();
    }

    public static void setRuntimeHeapMaxGb(int v) {
	runtimeCpuProfile = RuntimeCpuProfile.CUSTOM;
	save("moon-runtime-cpu-profile", runtimeCpuProfile.id);
	runtimeHeapMaxGb = Utils.clip(v, Math.max(2, runtimeHeapMinGb + 1), 16);
	save("moon-runtime-heap-max-gb", runtimeHeapMaxGb);
	persistRuntimeEnv();
    }
    public static void setPerfHudCompact(boolean v) {
	perfHudCompact = v;
	save("moon-perf-hud-compact", v);
    }
    public static void setProgressiveWorldLoad(boolean v) {
	progressiveWorldLoad = v;
	save("moon-progressive-world-load", v);
    }
    public static void setPerfAutoShed(boolean v) {
	perfAutoShed = v;
	save("moon-perf-autoshed", v);
    }
    public static void setPerfTerrainCriticalBudget(int v) {
	perfTerrainCriticalBudget = Utils.clip(v, 1, 24);
	save("moon-perf-terrain-critical", perfTerrainCriticalBudget);
    }
    public static void setPerfTerrainSecondaryBudget(int v) {
	perfTerrainSecondaryBudget = Utils.clip(v, 1, 24);
	save("moon-perf-terrain-secondary", perfTerrainSecondaryBudget);
    }
    public static void setPerfGobBuildBudget(int v) {
	perfGobBuildBudget = Utils.clip(v, 4, 96);
	save("moon-perf-gob-budget", perfGobBuildBudget);
    }

    public static GSettings applyPerfPreset(GSettings base, PerfPreset preset) {
	GSettings ret = (base != null) ? base : GSettings.defaults();
	PerfPreset mode = (preset != null) ? preset : PerfPreset.BALANCED;
	switch(mode) {
	case MAX_FPS:
	    ret = ret.update(null, ret.vsync, false);
	    ret = ret.update(null, ret.hz, 240f);
	    ret = ret.update(null, ret.bghz, 30f);
	    ret = ret.update(null, ret.syncmode, JOGLPanel.SyncMode.TICK);
	    ret = ret.update(null, ret.lshadow, false);
	    ret = ret.update(null, ret.rscale, 0.75f);
	    break;
	case VISUAL_FIRST:
	    ret = ret.update(null, ret.vsync, true);
	    ret = ret.update(null, ret.hz, Float.POSITIVE_INFINITY);
	    ret = ret.update(null, ret.bghz, 30f);
	    ret = ret.update(null, ret.syncmode, JOGLPanel.SyncMode.FRAME);
	    ret = ret.update(null, ret.lshadow, true);
	    ret = ret.update(null, ret.rscale, 1.0f);
	    break;
	default:
	    ret = ret.update(null, ret.vsync, true);
	    ret = ret.update(null, ret.hz, 120f);
	    ret = ret.update(null, ret.bghz, 30f);
	    ret = ret.update(null, ret.syncmode, JOGLPanel.SyncMode.FRAME);
	    ret = ret.update(null, ret.lshadow, false);
	    ret = ret.update(null, ret.rscale, 1.0f);
	    break;
	}
	return(ret);
    }

    public static void applyMoonPerfPreset(PerfPreset preset) {
	PerfPreset mode = (preset != null) ? preset : PerfPreset.BALANCED;
	setPerfPreset(mode);
	setProgressiveWorldLoad(true);
	setGfxModEspOverlay(false);
	setGfxModXray(false);
	setGfxModHitboxes(false);
	setCombatDrawChaseVectors(false);
	switch(mode) {
	case MAX_FPS:
	    setPerfAutoShed(true);
	    setPerfTerrainCriticalBudget(4);
	    setPerfTerrainSecondaryBudget(2);
	    setPerfGobBuildBudget(16);
	    setGfxModMiningOverlay(false);
	    setGfxModFightHud(false);
	    setGfxModActivityHud(false);
	    break;
	case VISUAL_FIRST:
	    setPerfAutoShed(false);
	    setPerfTerrainCriticalBudget(8);
	    setPerfTerrainSecondaryBudget(6);
	    setPerfGobBuildBudget(32);
	    setGfxModMiningOverlay(true);
	    setGfxModFightHud(true);
	    setGfxModActivityHud(true);
	    break;
	default:
	    setPerfAutoShed(true);
	    setPerfTerrainCriticalBudget(6);
	    setPerfTerrainSecondaryBudget(4);
	    setPerfGobBuildBudget(24);
	    setGfxModMiningOverlay(true);
	    setGfxModFightHud(true);
	    setGfxModActivityHud(true);
	    break;
	}
    }

    public static void setRememberSpeedMode(boolean v) {
	rememberSpeedMode = v;
	save("moon-remember-speed-mode", v);
    }
    public static void setPlayerAlertSounds(boolean v) { playerAlertSounds = v; save("moon-player-alerts", v); }
    public static void setPlayerAlertCooldownSec(int v) {
	playerAlertCooldownSec = Utils.clip(v, 1, 60);
	save("moon-player-alert-cooldown-sec", playerAlertCooldownSec);
    }
    public static void setPlayerAlertFriendly(boolean v) { playerAlertFriendly = v; save("moon-player-alert-friendly", v); }
    public static void setPlayerAlertUnknown(boolean v) { playerAlertUnknown = v; save("moon-player-alert-unknown", v); }
    public static void setPlayerAlertHostile(boolean v) { playerAlertHostile = v; save("moon-player-alert-hostile", v); }
    public static void setPlayerAlertFriendlyVolume(int v) {
	playerAlertFriendlyVolume = Utils.clip(v, 0, 1000);
	save("moon-player-alert-friendly-vol", playerAlertFriendlyVolume);
    }
    public static void setPlayerAlertUnknownVolume(int v) {
	playerAlertUnknownVolume = Utils.clip(v, 0, 1000);
	save("moon-player-alert-unknown-vol", playerAlertUnknownVolume);
    }
    public static void setPlayerAlertHostileVolume(int v) {
	playerAlertHostileVolume = Utils.clip(v, 0, 1000);
	save("moon-player-alert-hostile-vol", playerAlertHostileVolume);
    }
    public static void setPlayerAlertFriendlyPath(String v) {
	playerAlertFriendlyPath = (v != null) ? v.trim() : "";
	save("moon-player-alert-friendly-path", playerAlertFriendlyPath);
    }
    public static void setPlayerAlertUnknownPath(String v) {
	playerAlertUnknownPath = (v != null) ? v.trim() : "";
	save("moon-player-alert-unknown-path", playerAlertUnknownPath);
    }
    public static void setPlayerAlertHostilePath(String v) {
	playerAlertHostilePath = (v != null) ? v.trim() : "";
	save("moon-player-alert-hostile-path", playerAlertHostilePath);
    }

    /** Clears chop, stock, water, split areas (saved prefs). */
    public static void clearTreeBotAreas() {
	setTreeBotChopArea("");
	setTreeBotStockArea("");
	setTreeBotWaterTile("");
	setTreeBotWaterStockArea("");
	setTreeBotSplitLogArea("");
	setTreeBotSplitOutArea("");
    }
    public static void setTreeBotSpots(String s) {
	treeBotSpots = s != null ? s : "";
	Utils.setpref("moon-treebot-spots", treeBotSpots);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void appendTreeBotSpot(String line) {
	if(line == null || line.isEmpty())
	    return;
	String cur = treeBotSpots.trim();
	if(cur.isEmpty())
	    setTreeBotSpots(line);
	else
	    setTreeBotSpots(cur + "\n" + line);
    }

    public static void clearTreeBotSpots() { setTreeBotSpots(""); }

    public static int treeBotSpotCount() {
	String s = treeBotSpots.trim();
	if(s.isEmpty())
	    return(0);
	return(s.split("\n").length);
    }

    /** Tile {@link Area} as "ulx uly brx bry" ({@code br} exclusive), or empty. */
    public static String treeBotChopArea = Utils.getpref("moon-treebot-chop-area", "");
    public static String treeBotStockArea = Utils.getpref("moon-treebot-stock-area", "");
    /** Single water tile "tx ty" (fill from barrel / pond). */
    public static String treeBotWaterTile = Utils.getpref("moon-treebot-water-tile", "");
    /** Tiles where filled water / jugs are stacked (optional; for future routing). */
    public static String treeBotWaterStockArea = Utils.getpref("moon-treebot-water-stock-area", "");

    /**
     * While any standing tree remains in the chop area, do not pick up loose logs (finish felling first,
     * then haul to stockpiles). Disable only if you want to clear logs while trees are still up.
     */
    public static boolean treeBotChopBeforeHaul = Utils.getprefb("moon-treebot-chop-first", true);

    /** Split logs into firewood: logs on ground here → haul to {@link #treeBotSplitOutArea}. */
    public static boolean treeBotSplitEnabled = Utils.getprefb("moon-treebot-split-on", false);
    public static String treeBotSplitLogArea = Utils.getpref("moon-treebot-split-log-area", "");
    public static String treeBotSplitOutArea = Utils.getpref("moon-treebot-split-out-area", "");

    /** Mine / cave “sweeper”: left-clicks mineable wall gobs in a tile area; use mining cursor on the action bar. */
    public static boolean mineBotEnabled = Utils.getprefb("moon-minebot-on", false);
    public static boolean mineBotAvoidMobs = Utils.getprefb("moon-minebot-mobs", true);
    public static boolean mineBotWater = Utils.getprefb("moon-minebot-water", true);
    public static boolean mineBotHudLine = Utils.getprefb("moon-minebot-hud", true);
    /** Mine bot: print decisions via {@code core.Logger.log("MineBot", ...)}. */
    public static boolean mineBotDiagLog = Utils.getprefb("moon-minebot-diag", false);
    /** On start, turn on safe + risk overlays; does not restore previous prefs on stop. */
    public static boolean mineBotAutoHighlight = Utils.getprefb("moon-minebot-autoviz", true);
    /** 0 = tunneling bot, 1 = sapper bot. */
    public static int mineBotMode = Utils.getprefi("moon-minebot-ui-mode", 0);
    /** 0 = north, 1 = east, 2 = south, 3 = west. */
    public static int mineBotDirection = Utils.getprefi("moon-minebot-dir", 0);
    /** 0 = none, 1 = mine support, 2 = stone column, 3 = mine beam. */
    public static int mineBotSupportType = Utils.getprefi("moon-minebot-support-type", 0);
    /** Auto-drop matching items from the main inventory whenever this flag is on (independent of mine bot). */
    public static boolean mineAutoDropEnabled = Utils.getprefb("moon-mine-autodrop-enabled", false);
    /** Comma-separated category ids, see {@link haven.MoonAutoDrop}. */
    public static String mineAutoDropCategories = Utils.getpref("moon-mine-autodrop-categories", "stone_any,ore_any,coal_any");
    /** Draw persistent small digits for known cave cells (memory is kept even when the labels are hidden). */
    public static boolean mineSweeperShowLabels = Utils.getprefb("moon-mine-sweeper-show-labels", true);
    /** Generic world/overlay labels (ESP, traces, FPS/Ping HUD and similar). */
    public static int overlayTextSize = Utils.getprefi("moon-overlay-text-size", 12);
    public static int overlayTextRgb = Utils.getprefi("moon-overlay-text-rgb", 0x00F2F1FF);
    /** Inventory slot numbering overlay. */
    public static boolean inventorySlotNumbers = Utils.getprefb("moon-inv-slot-numbers", false);
    public static int inventorySlotNumberSize = Utils.getprefi("moon-inv-slot-number-size", 10);
    public static int inventorySlotNumberRgb = Utils.getprefi("moon-inv-slot-number-rgb", 0x00E8E4FF);
    /**
     * 0 = walls in area (name filter); 1 = only walls whose tile is inside a support disc;
     * 2 = only walls on unsupported (risk) floor tiles.
     */
    public static int mineBotTargetMode = Utils.getprefi("moon-minebot-mode", 0);
    /** If true, any {@link GobHealth} wall-like gob in the area (minus supports/mobs) is a candidate. */
    public static boolean mineBotLooseMatch = Utils.getprefb("moon-minebot-loose", false);
    public static int mineBotStaminaMinPct = Utils.getprefi("moon-minebot-stam-min", 25);
    public static boolean mineSupportShowHp = Utils.getprefb("moon-mine-support-hp", false);
    public static int mineSupportTextSize = Utils.getprefi("moon-mine-support-text-size", 12);
    public static int mineSweeperTextSize = Utils.getprefi("moon-mine-sweeper-text-size", 12);
    public static int mineSupportTextRgb = Utils.getprefi("moon-mine-support-text-rgb", 0x00E8FFF0);
    public static int mineSweeperSafeRgb = Utils.getprefi("moon-mine-sweeper-safe-rgb", 0x00A4F8A2);
    public static int mineSweeperAutoSafeRgb = Utils.getprefi("moon-mine-sweeper-auto-rgb", 0x00FFE484);
    public static int mineSweeperRiskRgb = Utils.getprefi("moon-mine-sweeper-risk-rgb", 0x00FF7676);

    public static void setTreeBotChopArea(String s) {
	treeBotChopArea = s != null ? s : "";
	Utils.setpref("moon-treebot-chop-area", treeBotChopArea);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void setTreeBotStockArea(String s) {
	treeBotStockArea = s != null ? s : "";
	Utils.setpref("moon-treebot-stock-area", treeBotStockArea);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void setTreeBotWaterTile(String s) {
	treeBotWaterTile = s != null ? s : "";
	Utils.setpref("moon-treebot-water-tile", treeBotWaterTile);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void setTreeBotWaterStockArea(String s) {
	treeBotWaterStockArea = s != null ? s : "";
	Utils.setpref("moon-treebot-water-stock-area", treeBotWaterStockArea);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void setTreeBotChopBeforeHaul(boolean v) {
	treeBotChopBeforeHaul = v;
	save("moon-treebot-chop-first", v);
    }

    public static void setTreeBotSplitEnabled(boolean v) {
	treeBotSplitEnabled = v;
	save("moon-treebot-split-on", v);
    }

    public static void setTreeBotSplitLogArea(String s) {
	treeBotSplitLogArea = s != null ? s : "";
	Utils.setpref("moon-treebot-split-log-area", treeBotSplitLogArea);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void setTreeBotSplitOutArea(String s) {
	treeBotSplitOutArea = s != null ? s : "";
	Utils.setpref("moon-treebot-split-out-area", treeBotSplitOutArea);
	try { Utils.prefs().flush(); } catch (Exception ignored) {}
    }

    public static void setAggroRadius(boolean v) { aggroRadius = v; save("moon-aggro-radius", v); if(v) ensureOverlayRendererEnabled(); }

    public static void setSpeedBoost(boolean v) { speedBoost = v; save("moon-speed-boost", v); }
    public static void setClientSpeedScale(boolean v) {
	clientSpeedScale = v;
	save("moon-client-speed-scale", v);
    }
    public static void setClientScaleServerSprint(boolean v) {
	clientScaleServerSprint = v;
	save("moon-client-scale-sprint", v);
    }
    public static void setServerSpeedLift(int v) {
	serverSpeedLift = Utils.clip(v, 0, 3);
	save("moon-server-speed-lift", serverSpeedLift);
    }
    public static void setDebugSpeedWire(boolean v) {
	debugSpeedWire = v;
	save("moon-debug-speed-wire", v);
    }
    public static void setSpeedMultiplier(double v) {
	speedMultiplier = Math.max(1.0, Math.min(v, 5.0));
	save("moon-speed-multiplier", speedMultiplier);
    }

    public static void setMoveTraceTileCenter(boolean v) {
	moveTraceTileCenter = v;
	save("moon-movetrace-tile-center", v);
    }

    public static void setLinMoveVisualSpeedMult(double v) {
	setLinMoveVisualSpeedMult(v, null);
    }

    /** @param glob if non-null, active {@link LinMove}/{@link Homing} on the player gob are rescaled immediately. */
    public static void setLinMoveVisualSpeedMult(double v, Glob glob) {
	double prev = linMoveVisualSpeedMult;
	linMoveVisualSpeedMult = Math.max(1.0, Math.min(v, 3.0));
	save("moon-linmove-visual-mult", linMoveVisualSpeedMult);
	if(glob != null)
	    MoonPacketHook.rescalePlayerMoveVelocityForMultChange(glob, prev, linMoveVisualSpeedMult);
    }

    public static void setSpeedMultResendIntervalSec(double v) {
	speedMultResendIntervalSec = Utils.clip(v, 0.0, 120.0);
	save("moon-speed-mult-resend-sec", speedMultResendIntervalSec);
    }

    public static void setExperimentalSpeedWireAssist(boolean v) {
	experimentalSpeedWireAssist = v;
	save("moon-exp-speed-wire", v);
    }

    public static void setExperimentalJniWireHook(boolean v) {
	experimentalJniWireHook = v;
	save("moon-exp-jni-wire", v);
	MoonJniPacketHook.syncWireEnabledFromPrefs();
    }

    public static void setDebugJniWireLog(boolean v) {
	debugJniWireLog = v;
	save("moon-debug-jni-wire", v);
	MoonJniPacketHook.applyNativeDiagFlags();
    }

    public static void setDebugJniWireNativeStderr(boolean v) {
	debugJniWireNativeStderr = v;
	save("moon-debug-jni-native-stderr", v);
	MoonJniPacketHook.applyNativeDiagFlags();
    }

    public static void setDebugJniWireMirrorStdout(boolean v) {
	debugJniWireMirrorStdout = v;
	save("moon-debug-jni-mirror-stdout", v);
    }
    public static void setAutoDrinkMaintainFull(boolean v) {
	autoDrinkMaintainFull = v;
	save("moon-autodrink-maintain-full", v);
    }
    public static void setFlatTerrain(boolean v) { flatTerrain = v; save("moon-flat-terrain", v); }

    public static void setFlatCaves(boolean v) {
	flatCaves = v;
	hideCaveWalls = v;
	save("moon-flat-caves", v);
	save("moon-hide-cave-walls", v);
    }

    public static void setHideCaveWalls(boolean v) {
	setFlatCaves(v);
    }

    public static void setStraightCaveWalls(boolean v) {
	straightCaveWalls = v;
	save("moon-straight-cave-walls", v);
    }

    public static void setFlatWalls(boolean v) {
	flatWalls = v;
	save("moon-flat-walls", v);
    }

    public static void setFlatCupboards(boolean v) {
	flatCupboards = v;
	save("moon-flat-cupboards", v);
    }

    public static void setShowDframeStatus(boolean v) { showDframeStatus = v; save("moon-status-dframe", v); }
    public static void setShowCoopStatus(boolean v) { showCoopStatus = v; save("moon-status-coop", v); }
    public static void setShowHutchStatus(boolean v) { showHutchStatus = v; save("moon-status-hutch", v); }
    public static void setShowRackStatus(boolean v) { showRackStatus = v; save("moon-status-rack", v); }
    public static void setShowCupboardStatus(boolean v) { showCupboardStatus = v; save("moon-status-storage", v); }

    public static void setSmartStorageEnabled(boolean v) {
	smartStorageEnabled = v;
	save("moon-smart-storage", v);
    }
    public static void setShowPartialStorageStatus(boolean v) { showPartialStorageStatus = v; save("moon-status-storage-partial", v); }
    public static void setShowShedStatus(boolean v) { showShedStatus = v; save("moon-status-shed", v); }
    public static void setShowTroughStatus(boolean v) { showTroughStatus = v; save("moon-status-trough", v); }
    public static void setShowBeehiveStatus(boolean v) { showBeehiveStatus = v; save("moon-status-beehive", v); }
    public static void setHighlightPots(boolean v) { highlightPots = v; save("moon-status-pots", v); }

    public static void setFlatTerrainRelief(double v) {
	v = Utils.clip(v, 0, 1);
	flatTerrainRelief = v;
	save("moon-flat-terrain-relief", v);
    }

    public static void setMineSupportSafeTiles(boolean v) {
	mineSupportSafeTiles = v;
	save("moon-mine-support-tiles", v);
    }

    public static void setMineSupportRadiusTiles(int v) {
	mineSupportRadiusTiles = Utils.clip(v, 1, 32);
	save("moon-mine-support-radius", mineSupportRadiusTiles);
    }

    public static void setMineSupportFillAlpha(int v) {
	mineSupportFillAlpha = Utils.clip(v, 0, 160);
	save("moon-mine-safe-fill-a", mineSupportFillAlpha);
    }

    public static void setMineSupportOutlineAlpha(int v) {
	mineSupportOutlineAlpha = Utils.clip(v, 0, 255);
	save("moon-mine-safe-edge-a", mineSupportOutlineAlpha);
    }

    public static void setMineSupportOutlineWidth(int v) {
	mineSupportOutlineWidth = Utils.clip(v, 1, 8);
	save("moon-mine-safe-edge-w", mineSupportOutlineWidth);
    }

    public static void setMineSupportOutlineRgb(int v) {
	mineSupportOutlineRgb = v & 0xffffff;
	save("moon-mine-safe-outline-rgb", mineSupportOutlineRgb);
    }

    public static Color mineSupportOutlineColor() {
	int a = mineSupportOutlineAlpha;
	int r = (mineSupportOutlineRgb >> 16) & 0xff;
	int g = (mineSupportOutlineRgb >> 8) & 0xff;
	int b = mineSupportOutlineRgb & 0xff;
	return new Color(r, g, b, a);
    }

    public static Color mineSupportFillColor() {
	int a = mineSupportFillAlpha;
	return new Color(70, 210, 115, a);
    }

    public static Color mineSupportTextColor() {
	int r = (mineSupportTextRgb >> 16) & 0xff;
	int g = (mineSupportTextRgb >> 8) & 0xff;
	int b = mineSupportTextRgb & 0xff;
	return new Color(r, g, b);
    }

    public static Color overlayTextColor() {
	int r = (overlayTextRgb >> 16) & 0xff;
	int g = (overlayTextRgb >> 8) & 0xff;
	int b = overlayTextRgb & 0xff;
	return new Color(r, g, b);
    }

    public static Color inventorySlotNumberColor() {
	int r = (inventorySlotNumberRgb >> 16) & 0xff;
	int g = (inventorySlotNumberRgb >> 8) & 0xff;
	int b = inventorySlotNumberRgb & 0xff;
	return new Color(r, g, b);
    }

    public static Color mineSweeperSafeColor() {
	int r = (mineSweeperSafeRgb >> 16) & 0xff;
	int g = (mineSweeperSafeRgb >> 8) & 0xff;
	int b = mineSweeperSafeRgb & 0xff;
	return new Color(r, g, b);
    }

    public static Color mineSweeperAutoSafeColor() {
	int r = (mineSweeperAutoSafeRgb >> 16) & 0xff;
	int g = (mineSweeperAutoSafeRgb >> 8) & 0xff;
	int b = mineSweeperAutoSafeRgb & 0xff;
	return new Color(r, g, b);
    }

    public static Color mineSweeperRiskColor() {
	int r = (mineSweeperRiskRgb >> 16) & 0xff;
	int g = (mineSweeperRiskRgb >> 8) & 0xff;
	int b = mineSweeperRiskRgb & 0xff;
	return new Color(r, g, b);
    }

    public static void setMineSweeperRiskTiles(boolean v) {
	mineSweeperRiskTiles = v;
	save("moon-mine-risk-tiles", v);
    }

    public static void setMineRiskFillAlpha(int v) {
	mineRiskFillAlpha = Utils.clip(v, 0, 140);
	save("moon-mine-risk-fill-a", mineRiskFillAlpha);
    }

    public static void setMineRiskFillRgb(int v) {
	mineRiskFillRgb = v & 0xffffff;
	save("moon-mine-risk-fill-rgb", mineRiskFillRgb);
    }

    public static Color mineRiskFillColor() {
	int a = mineRiskFillAlpha;
	int r = (mineRiskFillRgb >> 16) & 0xff;
	int g = (mineRiskFillRgb >> 8) & 0xff;
	int b = mineRiskFillRgb & 0xff;
	return new Color(r, g, b, a);
    }

    public static void setCombatDamageHud(boolean v) {
	combatDamageHud = v;
	save("moon-combat-damage-hud", v);
    }

    public static void setCombatHudShowDealt(boolean v) {
	combatHudShowDealt = v;
	save("moon-combat-hud-dealt", v);
    }

    public static void setCombatHudShowTaken(boolean v) {
	combatHudShowTaken = v;
	save("moon-combat-hud-taken", v);
    }

    public static void setCombatHudEnemyHpMode(int v) {
	combatHudEnemyHpMode = Utils.clip(v, 0, 2);
	save("moon-combat-hud-hp", combatHudEnemyHpMode);
    }

    public static void setCombatHudSwapFightIp(boolean v) {
	combatHudSwapFightIp = v;
	save("moon-combat-hud-swap-ip", v);
    }

    public static void setCombatHudPersistScope(int v) {
	combatHudPersistScope = Utils.clip(v, 0, 1);
	save("moon-combat-hud-persist-scope", combatHudPersistScope);
    }

    public static void setAlwaysDaylight(boolean v) { alwaysDaylight = v; save("moon-always-daylight", v); }

    public static void setNightVisionBlend(double v) {
	nightVisionBlend = Utils.clip(v, 0.0, 0.65);
	save("moon-night-vision-blend", nightVisionBlend);
    }

    public static void setTreeBushScalePct(int v) {
	treeBushScalePct = Utils.clip(v, 30, 100);
	save("moon-treebush-scale-pct", treeBushScalePct);
    }

    public static void setPalisadeWallScalePct(int v) {
	palisadeWallScalePct = Utils.clip(v, 40, 100);
	save("moon-palisade-z-pct", palisadeWallScalePct);
    }

    public static void setCropHide(boolean v) {
	cropHide = v;
	save("moon-crop-hide", v);
    }

    public static void setCropShowStage(boolean v) {
	cropShowStage = v;
	save("moon-crop-stage", v);
	if(v)
	    ensureOverlayRendererEnabled();
    }

    public static void setCropScalePct(int v) {
	cropScalePct = Utils.clip(v, 30, 200);
	save("moon-crop-scale-pct", cropScalePct);
    }

    /** Invalidate gob render state after scale sliders change (see {@link MoonGobScale}). */
    public static void refreshGobScaleRendering(GameUI gui) {
	if(gui == null || gui.map == null || gui.map.glob == null)
	    return;
	try {
	    gui.map.glob.oc.gobAction(g -> {
		if(g != null) {
		    synchronized(g) {
			g.updated();
		    }
		}
	    });
	} catch(Exception ignored) {
	}
    }

    public static Color colorFromArgb(int argb) {
	return new Color(argb, true);
    }

    public static void setCombatDrawChaseVectors(boolean v) {
	combatDrawChaseVectors = v;
	save("moon-combat-chase-vec", v);
    }

    public static void setCombatDrawClickPath(boolean v) {
	combatDrawClickPath = v;
	save("moon-combat-click-path", v);
    }

    public static void setCombatPathClickArgb(int argb) {
	combatPathClickArgb = argb;
	save("moon-combat-path-argb", combatPathClickArgb);
    }

    public static void setCombatChaseArgbSelf(int argb) {
	combatChaseArgbSelf = argb | 0xff000000;
	save("moon-combat-chase-self-argb", combatChaseArgbSelf);
    }

    public static void setCombatChaseArgbFriend(int argb) {
	combatChaseArgbFriend = argb | 0xff000000;
	save("moon-combat-chase-friend-argb", combatChaseArgbFriend);
    }

    public static void setCombatChaseArgbEnemy(int argb) {
	combatChaseArgbEnemy = argb | 0xff000000;
	save("moon-combat-chase-enemy-argb", combatChaseArgbEnemy);
    }
    public static void setGfxModEspOverlay(boolean v) { gfxModEspOverlay = v; save("moon-gfxmod-esp-overlay", v); }
    public static void setGfxModXray(boolean v) { gfxModXray = v; save("moon-gfxmod-xray", v); }
    public static void setGfxModHitboxes(boolean v) { gfxModHitboxes = v; save("moon-gfxmod-hitboxes", v); }
    public static void setGfxModMiningOverlay(boolean v) { gfxModMiningOverlay = v; save("moon-gfxmod-mining-overlay", v); }
    public static void setGfxModFightHud(boolean v) { gfxModFightHud = v; save("moon-gfxmod-fight-hud", v); }
    public static void setGfxModActivityHud(boolean v) { gfxModActivityHud = v; save("moon-gfxmod-activity-hud", v); }
    public static void setShowFpsPingHud(boolean v) { showFpsPingHud = v; save("moon-fps-ping-hud", v); }

    public static void setTeleportNavEnabled(boolean v) { teleportNavEnabled = v; save("moon-tp-on", v); }
    public static void setTeleportClickClamp(boolean v) { teleportClickClamp = v; save("moon-tp-clamp", v); }
    public static void setTeleportRequirePath(boolean v) { teleportRequirePath = v; save("moon-tp-path", v); }
    public static void setTeleportPassiveLmbPathfinding(boolean v) {
	teleportPassiveLmbPathfinding = v;
	save("moon-tp-passive-lmb-path", v);
    }
    public static void setTeleportBlockHostileNearTarget(boolean v) {
	teleportBlockHostileNearTarget = v;
	save("moon-tp-nohostile", v);
    }
    public static void setTeleportHostileClearTiles(int v) {
	teleportHostileClearTiles = Math.max(1, v);
	save("moon-tp-hostile-tiles", teleportHostileClearTiles);
    }
    public static void setTeleportMaxTiles(double v) {
	teleportMaxTiles = Math.max(1.0, Math.min(MAX_NAV_MAX_TILES, v));
	save("moon-tp-max-tiles", teleportMaxTiles);
    }
    public static void setTeleportCooldownMs(int v) {
	teleportCooldownMs = Math.max(0, Math.min(120000, v));
	save("moon-tp-cooldown-ms", teleportCooldownMs);
    }
    public static void setTeleportMaxHeightDelta(double v) {
	teleportMaxHeightDelta = Math.max(12.0, Math.min(500.0, v));
	save("moon-tp-max-dz", teleportMaxHeightDelta);
    }
    public static void setTeleportMinAttrKey(String k) {
	teleportMinAttrKey = (k == null) ? "" : k.trim();
	Utils.setpref("moon-tp-attr-key", teleportMinAttrKey);
	try { Utils.prefs().flush(); } catch(Exception ignored) {}
    }
    public static void setTeleportMinAttrSum(int v) {
	teleportMinAttrSum = Math.max(0, v);
	save("moon-tp-attr-min", teleportMinAttrSum);
    }
    public static void setTeleportLogStderr(boolean v) { teleportLogStderr = v; save("moon-tp-log-stderr", v); }

    public static void setSafeMode(boolean v) { safeMode = v; save("moon-safe-mode", v); }
    public static void setSafeModeBusyOnly(boolean v) { safeModeBusyOnly = v; save("moon-safe-busyonly", v); }
    public static void setSafeModeRespectCombat(boolean v) { safeModeRespectCombat = v; save("moon-safe-nofight", v); }
    public static void setSafeModeMinTiles(double v) {
	safeModeMinTiles = Math.max(0.5, v);
	save("moon-safe-mintiles", safeModeMinTiles);
    }
    public static void setActivityHud(boolean v) { activityHud = v; save("moon-activity-hud", v); }
    public static void setActivityHudStructRange(double v) {
	activityHudStructRange = Math.max(1.0, v);
	save("moon-activity-hud-range", activityHudStructRange);
    }

    public static boolean anyTraceActive() {
        return traceHostile || traceNeutralMobs || tracePlayers;
    }

    public static boolean anyOverlayActive() {
        return anyTraceActive() || MoonEspProfile.anyEnabled()
            || aggroRadius || xrayEnabled || cropShowStage;
    }

    public static void ensureOverlayRendererEnabled() {
	if(!gfxModEspOverlay)
	    setGfxModEspOverlay(true);
    }

    private static double initTeleportMaxTilesPref() {
	double val = Utils.getprefd("moon-tp-max-tiles", DEFAULT_NAV_MAX_TILES);
	if(Double.isNaN(val) || val < 1.0)
	    val = DEFAULT_NAV_MAX_TILES;
	if(!Utils.getprefb("moon-tp-max-tiles-migrated", false)) {
	    String raw = Utils.getpref("moon-tp-max-tiles", null);
	    if((raw == null) || (Math.abs(val - 50.0) < 0.001)) {
		val = DEFAULT_NAV_MAX_TILES;
		Utils.setprefd("moon-tp-max-tiles", val);
	    }
	    Utils.setprefb("moon-tp-max-tiles-migrated", true);
	    try { Utils.prefs().flush(); } catch(Exception ignored) {}
	}
	return Math.max(1.0, Math.min(MAX_NAV_MAX_TILES, val));
    }
}
