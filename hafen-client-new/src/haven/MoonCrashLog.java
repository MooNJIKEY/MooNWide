package haven;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes uncaught Java exceptions to {@code ~/.haven/moonwide-crash.log} so crashes can be
 * diagnosed when the process exits without a console (native GL/JNI crashes are not logged here).
 */
public final class MoonCrashLog {
    private static final AtomicBoolean installed = new AtomicBoolean(false);

    public static void install() {
	if(!installed.compareAndSet(false, true))
	    return;
	Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
	Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
	    record(t, e);
	    if(prev != null)
		prev.uncaughtException(t, e);
	});
    }

    public static void record(Thread t, Throwable e) {
	if(shouldIgnore(t, e))
	    return;
	try {
	    Path p = crashPath();
	    Path dir = p.getParent();
	    if(dir != null)
		Files.createDirectories(dir);
	    try(PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8,
		    StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
		w.println("==== " + new Date() + " thread=" + t.getName());
		e.printStackTrace(w);
		w.println();
	    }
	} catch(Throwable ignored) {
	}
    }

    private static boolean shouldIgnore(Thread t, Throwable e) {
	if(e == null)
	    return true;
	if(isBenignShutdownInterrupt(e))
	    return true;
	Throwable cause = e.getCause();
	return (cause != null) && isBenignShutdownInterrupt(cause);
    }

    private static boolean isBenignShutdownInterrupt(Throwable e) {
	if(!(e instanceof InterruptedException) && !(e instanceof RuntimeException))
	    return false;
	Throwable root = e;
	while(root.getCause() != null)
	    root = root.getCause();
	if(!(root instanceof InterruptedException))
	    return false;
	for(StackTraceElement el : e.getStackTrace()) {
	    String cls = el.getClassName();
	    if("haven.GLPanel$Loop".equals(cls))
		return true;
	}
	for(StackTraceElement el : root.getStackTrace()) {
	    String cls = el.getClassName();
	    if("haven.GLPanel$Loop".equals(cls))
		return true;
	}
	return false;
    }

    private static Path crashPath() {
	String home = System.getProperty("user.home", ".");
	return(FileSystems.getDefault().getPath(home, ".haven", "moonwide-crash.log"));
    }

    private MoonCrashLog() {}
}
