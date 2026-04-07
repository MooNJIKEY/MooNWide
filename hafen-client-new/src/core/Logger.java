package core;

public class Logger {
    private static boolean debug = false;

    public static void setDebugEnabled(boolean v) { debug = v; }
    public static boolean isDebugEnabled() { return debug; }

    /** Tagged line for subsystem diagnostics (e.g. mine bot). */
    public static void log(String tag, String msg) {
        System.out.println("[" + tag + "] " + msg);
    }

    public static void info(String msg) { System.out.println("[MooNWide] " + msg); }
    public static void warn(String msg) { System.err.println("[MooNWide WARN] " + msg); }
    public static void error(String msg) { System.err.println("[MooNWide ERROR] " + msg); }
    public static void debug(String msg) { if (debug) System.out.println("[MooNWide DEBUG] " + msg); }
}
