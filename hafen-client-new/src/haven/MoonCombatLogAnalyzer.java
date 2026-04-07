package haven;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the bundled {@code moon_combat_ai_log.py} via Python on {@link MoonCombatLogger}'s log file.
 */
public final class MoonCombatLogAnalyzer {
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final String SCRIPT_RESOURCE = "/haven/res/scripts/moon_combat_ai_log.py";
    private static final String SCRIPT_FILENAME = "moon_combat_ai_log.py";

    private MoonCombatLogAnalyzer() {}

    public static void runFromClient(GameUI gui) {
	if(gui == null || gui.ui == null)
	    return;
	if(!running.compareAndSet(false, true)) {
	    gui.ui.msg(LocalizationManager.tr("combatbot.ai.analyze.busy"));
	    return;
	}
	gui.ui.msg(LocalizationManager.tr("combatbot.ai.analyze.start"));
	new HackThread(() -> {
	    try {
		Result r = runBlocking();
		gui.ui.loader.defer(() -> showResult(gui, r), null);
	    } finally {
		running.set(false);
	    }
	}, "moon-combat-log-analyzer").start();
    }

    private static void showResult(GameUI gui, Result r) {
	if(r.ok) {
	    String head = r.stdout;
	    if(head.length() > 900)
		head = head.substring(0, 897) + "...";
	    String p1 = r.reportTxt != null ? r.reportTxt.toString() : "";
	    gui.ui.msg(LocalizationManager.tr("combatbot.ai.analyze.done1") + " " + p1);
	    if(r.reportJson != null)
		gui.ui.msg(LocalizationManager.tr("combatbot.ai.analyze.done2") + " " + r.reportJson);
	    if(!head.isEmpty())
		gui.ui.msg(head);
	} else {
	    gui.ui.error(r.message != null && !r.message.isEmpty() ? r.message : LocalizationManager.tr("combatbot.ai.analyze.fail"));
	}
    }

    private static final class Result {
	final boolean ok;
	final String message;
	final String stdout;
	final Path reportTxt;
	final Path reportJson;

	Result(boolean ok, String message, String stdout, Path reportTxt, Path reportJson) {
	    this.ok = ok;
	    this.message = message;
	    this.stdout = stdout != null ? stdout : "";
	    this.reportTxt = reportTxt;
	    this.reportJson = reportJson;
	}
    }

    private static Result runBlocking() {
	try {
	    Path script = extractScript();
	    Path log = MoonCombatLogger.logFilePath();
	    Path homeHaven = Paths.get(System.getProperty("user.home", "."), ".haven");
	    Files.createDirectories(homeHaven);
	    Path reportTxt = homeHaven.resolve("moon-combat-ai-report.txt");
	    Path reportJson = homeHaven.resolve("moon-combat-ai-report.json");

	    String[] launcher = findPythonLauncher();
	    if(launcher == null)
		return new Result(false, LocalizationManager.tr("combatbot.ai.analyze.nopy"), "", null, null);

	    List<String> cmd = new ArrayList<>();
	    cmd.addAll(Arrays.asList(launcher));
	    cmd.add(script.toAbsolutePath().toString());
	    cmd.add(log.toAbsolutePath().toString());
	    cmd.add("--json");
	    cmd.add(reportJson.toAbsolutePath().toString());

	    ProcessBuilder pb = new ProcessBuilder(cmd);
	    pb.redirectErrorStream(true);
	    Process p = pb.start();
	    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	    if(!p.waitFor(45, TimeUnit.SECONDS)) {
		p.destroyForcibly();
		return new Result(false, LocalizationManager.tr("combatbot.ai.analyze.timeout"), out, null, null);
	    }
	    Files.writeString(reportTxt, out, StandardCharsets.UTF_8);
	    if(p.exitValue() != 0)
		return new Result(false, out.trim().isEmpty() ? LocalizationManager.tr("combatbot.ai.analyze.fail") : out, out, reportTxt, null);
	    return new Result(true, null, out, reportTxt, Files.isRegularFile(reportJson) ? reportJson : null);
	} catch(Exception e) {
	    return new Result(false, String.valueOf(e.getMessage()), "", null, null);
	}
    }

    private static String[] findPythonLauncher() {
	String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	if(os.contains("win")) {
	    if(pythonWorks("py", "-3"))
		return new String[] {"py", "-3"};
	}
	if(pythonWorks("python3"))
	    return new String[] {"python3"};
	if(pythonWorks("python"))
	    return new String[] {"python"};
	return null;
    }

    private static boolean pythonWorks(String... prefix) {
	try {
	    List<String> cmd = new ArrayList<>(Arrays.asList(prefix));
	    cmd.add("-c");
	    cmd.add("print(1)");
	    ProcessBuilder pb = new ProcessBuilder(cmd);
	    pb.redirectErrorStream(true);
	    Process p = pb.start();
	    if(!p.waitFor(8, TimeUnit.SECONDS))
		return false;
	    return p.exitValue() == 0;
	} catch(Exception e) {
	    return false;
	}
    }

    private static Path extractScript() throws Exception {
	String home = System.getProperty("user.home", ".");
	Path dir = Paths.get(home, ".haven");
	Files.createDirectories(dir);
	Path out = dir.resolve(SCRIPT_FILENAME);
	try(InputStream in = MoonCombatLogAnalyzer.class.getResourceAsStream(SCRIPT_RESOURCE)) {
	    if(in == null)
		throw new Exception("Missing " + SCRIPT_RESOURCE + " in classpath");
	    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}
	return out;
    }
}
