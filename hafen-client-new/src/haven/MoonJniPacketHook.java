package haven;

/**
 * <b>What is substituted (JNI):</b> only the <em>HCrypt session</em> plaintext blob — byte 0 = inner
 * message type ({@link Session#MSG_REL}, {@link Session#MSG_MAPDATA}, …), bytes 1… = payload exactly as
 * {@link Connection.Crypto#encrypt}/{@link Connection.Crypto#decrypt} see it. Raw UDP, object deltas
 * ({@link OCache}), and map bulk stream are <em>not</em> passed here.
 * <p>
 * <b>Not JNI:</b> movement tweaks are Java-only: {@link MoonPacketHook} rewrites outgoing {@link Speedget}
 * {@code set} ({@link MoonConfig#experimentalSpeedWireAssist}), and scales incoming segment velocity at
 * {@link OCache} delta apply ({@link MoonConfig#linMoveVisualSpeedMult}) — gob UDP stream is not hooked here.
 * <p>
 * Native {@code moonpackethook} must load; hook is off unless {@link #syncWireEnabledFromPrefs()} leaves
 * {@link #isWireHookEnabled()} true (prefs and/or {@code -Dhaven.moonjni.wire=true}).
 */
public final class MoonJniPacketHook {
    /** 0 = server → client (after decrypt), 1 = client → server (before encrypt). */
    public static final int DIR_INCOMING = 0;
    public static final int DIR_OUTGOING = 1;

    private static final int DIAG_NATIVE_STDERR = 1;

    private static volatile boolean loaded;
    private static volatile boolean wireEnabled;
    private static volatile int nativeDiagFlags;

    static {
	tryLoad();
	syncWireEnabledFromPrefs();
	applyNativeDiagFlags();
    }

    private MoonJniPacketHook() {}

    private static void tryLoad() {
	String explicit = System.getProperty("haven.moonjni.lib", "").trim();
	try {
	    if(!explicit.isEmpty())
		System.load(explicit);
	    else
		System.loadLibrary("moonpackethook");
	    loaded = true;
	} catch(UnsatisfiedLinkError e) {
	    loaded = false;
	}
    }

    /** Native DLL/SO loaded successfully. */
    public static boolean isNativeLoaded() {
	return loaded;
    }

    /** When false, {@link #applyPlain} skips native transform (still can log if {@link MoonConfig#debugJniWireLog}). */
    public static boolean isWireHookEnabled() {
	return wireEnabled;
    }

    public static void setWireHookEnabled(boolean v) {
	wireEnabled = v;
    }

    public static void syncWireEnabledFromPrefs() {
	wireEnabled = Boolean.parseBoolean(System.getProperty("haven.moonjni.wire", "false"))
	    || Utils.getprefb("moon-exp-jni-wire", false);
    }

    /** Push {@link MoonConfig#debugJniWireNativeStderr} into native (stderr one-liner in {@code analyzeRawPacket}). */
    public static void applyNativeDiagFlags() {
	if(!loaded)
	    return;
	int f = MoonConfig.debugJniWireNativeStderr ? DIAG_NATIVE_STDERR : 0;
	if(f == nativeDiagFlags)
	    return;
	try {
	    nSetDiag(f);
	    nativeDiagFlags = f;
	} catch(Throwable ignored) {
	}
    }

    public static byte[] transform(int direction, byte[] plain) {
	return applyPlain(direction, plain);
    }

    /** Used from {@link Connection.Crypto}. */
    /**
     * Cleartext session (no {@link Connection.Crypto}): log the same logical frame as HCrypt inner plaintext
     * — byte 0 = {@link Session} message type, rest = payload. Does not call native transform.
     */
    public static void traceCleartextSend(PMessage msg) {
	if(!MoonConfig.debugJniWireLog || msg == null)
	    return;
	try {
	    int pay = msg.size();
	    byte[] pl = new byte[1 + pay];
	    pl[0] = (byte)msg.type;
	    if(pay > 0)
		msg.fin(pl, 1);
	    logFrame(DIR_OUTGOING, "clear", pl, false);
	} catch(Throwable ignored) {
	}
    }

    /** @see #traceCleartextSend */
    public static void traceCleartextRecv(PMessage msg) {
	if(!MoonConfig.debugJniWireLog || msg == null)
	    return;
	try {
	    int pay = msg.rt - msg.rh;
	    if(pay < 0)
		pay = 0;
	    byte[] pl = new byte[1 + pay];
	    pl[0] = (byte)msg.type;
	    if(pay > 0 && msg.rbuf != null)
		System.arraycopy(msg.rbuf, msg.rh, pl, 1, pay);
	    logFrame(DIR_INCOMING, "clear", pl, false);
	} catch(Throwable ignored) {
	}
    }

    static byte[] applyPlain(int direction, byte[] plain) {
	if(plain == null || plain.length < 1)
	    return plain;
	final boolean log = MoonConfig.debugJniWireLog;
	if(log)
	    logFrame(direction, "pre", plain, false);
	if(!loaded || !wireEnabled) {
	    return plain;
	}
	try {
	    byte[] rep = nTransform(direction, plain);
	    boolean replaced = rep != null && rep.length >= 1;
	    byte[] out = replaced ? rep : plain;
	    if(log)
		logFrame(direction, "post", out, replaced);
	    return out;
	} catch(Throwable t) {
	    if(log)
		System.err.println("[MoonJniWire] native error: " + t);
	    return plain;
	}
    }

    private static void logFrame(int direction, String phase, byte[] buf, boolean nativeNewArray) {
	try {
	    int t = buf[0] & 0xff;
	    int n = Math.min(buf.length, 72);
	    byte[] slice = new byte[n];
	    System.arraycopy(buf, 0, slice, 0, n);
	    String hex = Utils.bprint.enc(slice);
	    String dir = (direction == DIR_INCOMING) ? "IN " : "OUT";
	    String typ = sessionTypeName(t);
	    String line = String.format("[MoonJniWire] %s %s type=%s(%d) len=%d nativeNewBuf=%s hex[%d]=%s",
		dir, phase, typ, t, buf.length, nativeNewArray, n, hex);
	    System.err.println(line);
	    if(MoonConfig.debugJniWireMirrorStdout)
		System.out.println(line);
	} catch(Throwable ignored) {
	}
    }

    private static String sessionTypeName(int t) {
	switch(t) {
	case Session.MSG_SESS: return "SESS";
	case Session.MSG_REL: return "REL";
	case Session.MSG_ACK: return "ACK";
	case Session.MSG_BEAT: return "BEAT";
	case Session.MSG_MAPREQ: return "MAPREQ";
	case Session.MSG_MAPDATA: return "MAPDATA";
	case Session.MSG_OBJDATA: return "OBJDATA";
	case Session.MSG_OBJACK: return "OBJACK";
	case Session.MSG_CLOSE: return "CLOSE";
	case Session.MSG_CRYPT: return "CRYPT";
	default: return "UNK";
	}
    }

    private static native byte[] nTransform(int direction, byte[] plain);

    /** Bit {@link #DIAG_NATIVE_STDERR}: print {@code [MoonJniNative]} line from C++. */
    private static native void nSetDiag(int flags);
}
