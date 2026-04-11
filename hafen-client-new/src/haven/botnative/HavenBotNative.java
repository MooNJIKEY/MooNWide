package haven.botnative;

import haven.GameUI;

/**
 * JNI bridge to the native bot core ({@code havenbot} shared library).
 * All methods that touch the game must run on the Haven UI thread (same as {@link GameUI#tick(double)}).
 * If the library is missing or {@link #nInit} fails, calls are no-ops / return defaults — the client still runs.
 */
public final class HavenBotNative {
    private static volatile boolean loaded;
    private static volatile boolean nativeReady;
    private static volatile String loadError = "";
    private static volatile String loadTarget = "";

    static {
	tryLoad();
    }

    private HavenBotNative() {}

    private static void tryLoad() {
	String explicit = System.getProperty("haven.botnative.lib", "").trim();
	try {
	    if(!explicit.isEmpty()) {
		loadTarget = explicit;
		System.load(explicit);
	    } else {
		loadTarget = "havenbot";
		System.loadLibrary("havenbot");
	    }
	    nativeReady = nInit();
	    loaded = true;
	    loadError = nativeReady ? "" : "nInit returned false";
	} catch(Throwable e) {
	    loaded = false;
	    nativeReady = false;
	    loadError = e.toString();
	}
    }

    /** True when {@code havenbot} loaded and C++ {@code nInit} succeeded. */
    public static boolean isAvailable() {
	return loaded && nativeReady;
    }

    /** Reason why the bridge is unavailable; empty on success. */
    public static String loadError() {
	return loadError;
    }

    /** Explicit DLL path or logical library name used for the last load attempt. */
    public static String loadTarget() {
	return loadTarget;
    }

    /** Short native build id (UTF-8); empty if unavailable. */
    public static String nativeVersion() {
	if(!isAvailable())
	    return("");
	return(nVersion());
    }

    /**
     * Called once per UI frame from {@link GameUI}. Safe to call when unavailable (returns immediately).
     * C++ may use {@code gui} later via JNI global refs — do not call from background threads.
     */
    public static void tick(GameUI gui, double dt) {
	if(!isAvailable())
	    return;
	nTick(gui, dt);
    }

    /** Release native resources (optional; idempotent). */
    public static void shutdown() {
	if(!isAvailable())
	    return;
	nShutdown();
	nativeReady = false;
    }

    private static native boolean nInit();
    private static native void nShutdown();
    private static native String nVersion();
    private static native void nTick(GameUI gui, double dt);
}
