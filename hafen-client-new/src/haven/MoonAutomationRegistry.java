package haven;

import haven.botnative.HavenBotNative;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import managers.automation.AutoRunner;
import managers.automation.TaskQueue;

/**
 * Unified runtime registry for MooNWide automation modules.
 * Keeps user-visible status, last error/action, and a small diagnostics log.
 */
public final class MoonAutomationRegistry {
    private MoonAutomationRegistry() {}

    @FunctionalInterface
    public interface TickCall {
	void run() throws Throwable;
    }

    private static final class ModuleDef {
	final String id;
	final String ru;
	final String en;
	final String iconKey;
	final String source;
	final String logPath;

	ModuleDef(String id, String ru, String en, String iconKey, String source, String logPath) {
	    this.id = id;
	    this.ru = ru;
	    this.en = en;
	    this.iconKey = iconKey;
	    this.source = source;
	    this.logPath = logPath;
	}
    }

    private static final class RuntimeState {
	volatile String lastAction = "";
	volatile String lastError = "";
	volatile String blockedReason = "";
	volatile double lastActionAt = 0.0;
	volatile double lastErrorAt = 0.0;
	volatile double lastUiAt = 0.0;
    }

    public static final class Snapshot {
	public final String id;
	public final String title;
	public final String iconKey;
	public final String source;
	public final boolean enabled;
	public final boolean active;
	public final boolean available;
	public final String status;
	public final String lastAction;
	public final String lastError;
	public final String logPath;

	public Snapshot(String id, String title, String iconKey, String source, boolean enabled, boolean active,
			boolean available, String status, String lastAction, String lastError, String logPath) {
	    this.id = id;
	    this.title = title;
	    this.iconKey = iconKey;
	    this.source = source;
	    this.enabled = enabled;
	    this.active = active;
	    this.available = available;
	    this.status = status;
	    this.lastAction = lastAction;
	    this.lastError = lastError;
	    this.logPath = logPath;
	}
    }

    private static final LinkedHashMap<String, ModuleDef> defs = new LinkedHashMap<>();
    private static final ConcurrentHashMap<String, RuntimeState> runtime = new ConcurrentHashMap<>();
    private static final Path AUTO_LOG = Paths.get(System.getProperty("user.home", "."), ".haven", "moonwide-automation.log");

    static {
	register("combat", "Автобой", "Combat bot", "bot.combat", "moon", AUTO_LOG.toString());
	register("tree", "Рубка деревьев", "Tree bot", "bot.tree", "moon", AUTO_LOG.toString());
	register("mine", "Копка / сапёр", "Mine / sapper", "bot.mine", "moon", Paths.get(System.getProperty("user.home", "."), "minebot-diag.log").toString());
	register("fishing", "Рыбалка", "Fishing bot", "bot.fish", "moon", AUTO_LOG.toString());
	register("autodrink", "Автопитьё", "Auto drink", "bot.more", "moon", AUTO_LOG.toString());
	register("autodrop", "Автодроп", "Auto drop", "bot.more", "moon", AUTO_LOG.toString());
	register("passivegate", "Пассивная калитка", "Passive gate", "moon.passivegate", "moon", AUTO_LOG.toString());
	register("bulk", "Bulk station", "Bulk station", "bot.more", "moon", AUTO_LOG.toString());
	register("tpnav", "TP / Nav", "TP / Nav", "bot.nav", "moon", AUTO_LOG.toString());
	register("legacy", "Legacy queue", "Legacy queue", "bot.more", "legacy", AUTO_LOG.toString());
	register("native", "havenbot JNI", "havenbot JNI", "bot.more", "native", AUTO_LOG.toString());
    }

    private static void register(String id, String ru, String en, String iconKey, String source, String logPath) {
	defs.put(id, new ModuleDef(id, ru, en, iconKey, source, logPath));
    }

    private static RuntimeState state(String id) {
	return(runtime.computeIfAbsent(id, k -> new RuntimeState()));
    }

    private static String text(ModuleDef def) {
	return MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? def.ru : def.en;
    }

    private static String shortErr(Throwable t) {
	if(t == null)
	    return "";
	String n = t.getClass().getSimpleName();
	String m = t.getMessage();
	if(m == null || m.isBlank())
	    return n;
	m = m.replace('\n', ' ').replace('\r', ' ').trim();
	return (n + ": " + m).trim();
    }

    private static String shortText(String s) {
	if(s == null)
	    return "";
	s = s.replace('\n', ' ').replace('\r', ' ').trim();
	return(s.length() > 112) ? (s.substring(0, 109) + "...") : s;
    }

    private static void writeDiag(String line) {
	try {
	    Path dir = AUTO_LOG.getParent();
	    if(dir != null)
		Files.createDirectories(dir);
	    Files.write(AUTO_LOG, (Instant.now() + " " + line + "\n").getBytes(StandardCharsets.UTF_8),
		StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	} catch(Exception ignored) {
	}
    }

    public static void noteAction(String id, String action) {
	if(id == null || action == null || action.isBlank())
	    return;
	RuntimeState st = state(id);
	st.lastAction = shortText(action);
	st.blockedReason = "";
	st.lastActionAt = Utils.rtime();
    }

    public static void noteBlocked(String id, String reason) {
	if(id == null || reason == null || reason.isBlank())
	    return;
	RuntimeState st = state(id);
	st.blockedReason = shortText(reason);
	st.lastActionAt = Utils.rtime();
    }

    public static void clearBlocked(String id) {
	if(id == null)
	    return;
	state(id).blockedReason = "";
    }

    public static void clearLastError(String id) {
	if(id == null)
	    return;
	state(id).lastError = "";
    }

    public static void resetRuntime(String id) {
	if(id == null)
	    return;
	runtime.remove(id);
    }

    public static void resetAllRuntime() {
	runtime.clear();
    }

    public static void noteError(String id, Throwable t, GameUI gui) {
	if(id == null || t == null)
	    return;
	RuntimeState st = state(id);
	String err = shortErr(t);
	double now = Utils.rtime();
	if(err.equals(st.lastError) && (now - st.lastErrorAt) < 1.25)
	    return;
	st.lastError = err;
	st.lastErrorAt = now;
	writeDiag("[" + id + "] " + err);
	new Warning(t, "Moon automation [" + id + "]").issue();
	if(gui != null && gui.ui != null && (now - st.lastUiAt) >= 2.0) {
	    st.lastUiAt = now;
	    ModuleDef def = defs.get(id);
	    String title = (def != null) ? text(def) : id;
	    gui.ui.msg(title + ": " + shortText(err), new Color(255, 210, 120), null);
	}
    }

    public static void tickModule(String id, GameUI gui, TickCall call) {
	if(call == null)
	    return;
	try {
	    call.run();
	} catch(Throwable t) {
	    noteError(id, t, gui);
	}
    }

    public static List<Snapshot> snapshots(GameUI gui) {
	ArrayList<Snapshot> out = new ArrayList<>();
	for(ModuleDef def : defs.values())
	    out.add(snapshot(gui, def));
	return out;
    }

    private static Snapshot snapshot(GameUI gui, ModuleDef def) {
	RuntimeState st = state(def.id);
	boolean enabled = false;
	boolean active = false;
	boolean available = true;
	String status = "idle";
	String lastError = st.lastError;
	String lastAction = st.lastAction;
	if(st.blockedReason != null && !st.blockedReason.isBlank())
	    status = "blocked: " + st.blockedReason;
	switch(def.id) {
	case "combat" -> {
	    enabled = MoonConfig.combatBot;
	    String load = MoonCombatTables.lastLoadError();
	    available = (load == null || load.isBlank());
	    active = enabled && gui != null && gui.fv != null && gui.fv.current != null;
	    String engine = MoonCombatBot.statusSummary();
	    status = enabled ? ((gui != null && gui.fv != null) ? (active ? "active fight session" : "armed, waiting fight") : "waiting UI") : "disabled";
	    if(engine != null && !engine.isBlank() && enabled)
		status = status + " | " + shortText(engine);
	    if(!available)
		status = "tables: " + shortText(load);
	}
	case "tree" -> {
	    enabled = MoonConfig.treeBotEnabled;
	    active = enabled;
	    status = enabled ? ((gui != null && MoonTreeChopBot.statusHudLine(gui) != null) ? MoonTreeChopBot.statusHudLine(gui) :
		("phase " + MoonConfig.treeBotWorkPhase)) : "disabled";
	}
	case "mine" -> {
	    enabled = MoonConfig.mineBotEnabled;
	    active = enabled;
	    status = enabled ? ((gui != null && MoonMineBot.statusHudLine(gui) != null) ? MoonMineBot.statusHudLine(gui) :
		(MoonMineBot.modeLabel() + " / " + MoonMineBot.directionLabel())) : "disabled";
	}
	case "fishing" -> {
	    enabled = MoonConfig.fishingBotEnabled;
	    active = enabled;
	    available = MoonTreeUtil.parseTileCoord(MoonConfig.fishingBotWaterTile) != null;
	    status = MoonFishingBot.statusSummary(gui);
	}
	case "autodrink" -> {
	    enabled = MoonConfig.autoDrink;
	    active = MoonAutoDrink.isBusy();
	    status = MoonAutoDrink.statusSummary(gui);
	}
	case "autodrop" -> {
	    enabled = MoonConfig.mineAutoDropEnabled;
	    active = MoonAutoDrop.isBusy();
	    status = MoonAutoDrop.statusSummary();
	}
	case "passivegate" -> {
	    enabled = MoonConfig.passiveSmallGate;
	    active = enabled;
	    status = MoonPassiveGate.statusSummary();
	}
	case "bulk" -> {
	    enabled = MoonConfig.bulkStationFill || MoonConfig.bulkStationTakeAll;
	    active = MoonBulkStation.isActive();
	    status = MoonBulkStation.statusSummary();
	}
	case "tpnav" -> {
	    enabled = MoonConfig.teleportNavEnabled;
	    active = TeleportManager.isMapPickArmed();
	    status = TeleportManager.statusSummary();
	}
	case "legacy" -> {
	    enabled = AutoRunner.isEnabled() || TaskQueue.size() > 0;
	    active = TaskQueue.size() > 0;
	    TaskQueue.Task head = TaskQueue.peek();
	    status = String.format(Locale.ROOT, "runner=%s | queue=%d%s",
		AutoRunner.isEnabled() ? "on" : "off",
		TaskQueue.size(),
		(head != null) ? (" | next=" + shortText(head.name())) : "");
	}
	case "native" -> {
	    enabled = HavenBotNative.isAvailable();
	    active = enabled;
	    available = enabled;
	    status = enabled ? ("available " + shortText(HavenBotNative.nativeVersion())) :
		("unavailable: " + shortText(HavenBotNative.loadError()));
	}
	}
	if(lastError != null && !lastError.isBlank())
	    status = status + " | err: " + shortText(lastError);
	return new Snapshot(def.id, text(def), def.iconKey, def.source, enabled, active, available,
	    status, lastAction, lastError, def.logPath);
    }
}
