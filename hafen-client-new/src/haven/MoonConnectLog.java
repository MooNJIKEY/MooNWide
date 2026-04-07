package haven;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Append-only log for game UDP / session connection steps. Screen toasts clear after a few
 * seconds; this file stays under {@code ~/.haven/moonwide-connect.log} for diagnosis.
 */
public final class MoonConnectLog {
    private static final Object LOCK = new Object();
    /** Verbose inbound UI wire logging is very expensive; off by default. Enable with -Dmoon.connect.verbose=true. */
    private static final boolean VERBOSE_INBOUND_UI = Boolean.getBoolean("moon.connect.verbose");
    /** Cap REL NEWWDG logging so world UI does not flood the file; enough for login + one transition. */
    private static final AtomicInteger inboundNewwdgBudget = new AtomicInteger(120);
    /** Inbound WDGMSG (any name) — server may answer Play with a non-err uimsg. */
    private static final AtomicInteger inboundWdgmsgBudget = new AtomicInteger(200);

    /**
     * Short explanations for reading {@link #path()}; also mirrored to stderr.
     */
    public static void hint(String en, String ru) {
	log("--- HINT (EN) " + en);
	log("--- ПОДСКАЗКА (RU) " + ru);
    }

    public static void log(String line) {
	String ts = new java.util.Date().toString();
	String full = ts + " [" + Thread.currentThread().getName() + "] " + line;
	System.err.println("[moonwide-connect] " + full);
	synchronized(LOCK) {
	    try {
		Path p = path();
		Path dir = p.getParent();
		if(dir != null)
		    Files.createDirectories(dir);
		Files.write(p, (full + "\n").getBytes(StandardCharsets.UTF_8),
		    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	    } catch(Throwable ignored) {
	    }
	}
    }

    private static Path path() {
	String home = System.getProperty("user.home", ".");
	return(FileSystems.getDefault().getPath(home, ".haven", "moonwide-connect.log"));
    }

    /**
     * After decryption/reassembly: log notable inbound UI REL. Helps distinguish
     * "server never sent sess" vs "sess arrived". NEWWDG is budget-limited per run.
     */
    public static void logInboundRelUi(PMessage msg) {
	if(!VERBOSE_INBOUND_UI)
	    return;
	if(msg == null)
	    return;
	if(msg.type == RMessage.RMSG_NEWWDG) {
	    int remaining = inboundNewwdgBudget.getAndDecrement();
	    if(remaining <= 0) {
		if(remaining == 0)
		    log("net: inbound NEWWDG log budget exhausted (further NEWWDG lines suppressed until restart)");
		return;
	    }
	    try {
		PMessage c = msg.clone();
		c.rewind();
		int nid = c.int32();
		String wt = c.string();
		log("net: inbound NEWWDG id=" + nid + " type=" + wt);
	    } catch(Throwable t) {
		log("net: inbound NEWWDG (parse error: " + t.getMessage() + ")");
	    }
	} else if(msg.type == RMessage.RMSG_WDGMSG) {
	    int wleft = inboundWdgmsgBudget.getAndDecrement();
	    if(wleft <= 0) {
		if(wleft == 0)
		    log("net: inbound WDGMSG log budget exhausted (further WDGMSG lines suppressed until restart)");
		return;
	    }
	    try {
		PMessage c = msg.clone();
		c.rewind();
		int nid = c.int32();
		String nm = c.string();
		log("net: inbound WDGMSG id=" + nid + " name=" + nm);
	    } catch(Throwable t) {
		log("net: inbound WDGMSG (parse error: " + t.getMessage() + ")");
	    }
	}
    }

    private MoonConnectLog() {}
}
