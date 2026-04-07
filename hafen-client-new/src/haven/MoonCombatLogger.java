package haven;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Append-only combat AI decision log for offline analysis / weight tuning.
 * Path: {@code ~/.haven/moon-combat-ai.log} (created on first write).
 * Each line includes {@code pid=} (JVM process id) so two clients (e.g. bot-vs-bot) stay distinguishable in one file.
 */
public final class MoonCombatLogger {
    private static final long LOG_PID = logPidOrZero();
    private static Path path;
    private static boolean initTried;

    private static long logPidOrZero() {
	try {
	    return ProcessHandle.current().pid();
	} catch(@SuppressWarnings("unused") Throwable t) {
	    return 0L;
	}
    }

    /** Same path the logger appends to; used by {@link MoonCombatLogAnalyzer}. */
    public static Path logFilePath() {
	return logPath();
    }

    private static Path logPath() {
	if(path == null) {
	    String home = System.getProperty("user.home", ".");
	    path = Paths.get(home, ".haven", "moon-combat-ai.log");
	}
	return path;
    }

    public static synchronized void logDecision(int mode, int slot, String resName, double score, int ipSelf, int ipOpp, String note) {
	if(!MoonConfig.combatBotLogAi)
	    return;
	try {
	    Path p = logPath();
	    if(!initTried) {
		initTried = true;
		Files.createDirectories(p.getParent());
	    }
	    String line = String.format(java.util.Locale.ROOT, "%s\tpid=%d\tmode=%d\tslot=%d\tres=%s\tscore=%.3f\tip=%d/%d\t%s%n",
		Instant.now().toString(), LOG_PID, mode, slot, String.valueOf(resName).replace('\t', ' '), score, ipSelf, ipOpp,
		note == null ? "" : note.replace('\t', ' '));
	    Files.writeString(p, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	} catch(Exception e) {
	    System.err.println("moon-combat-ai log: " + e.getMessage());
	}
    }
}
