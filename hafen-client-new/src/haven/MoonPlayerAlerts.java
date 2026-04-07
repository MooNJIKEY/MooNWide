package haven;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MoonPlayerAlerts {
    private MoonPlayerAlerts() {}

    public enum Kind {
	FRIENDLY,
	UNKNOWN,
	HOSTILE
    }

    private static final class SeenPlayer {
	Kind kind;
	double lastSeen;
	double lastAlert;

	SeenPlayer(Kind kind, double now) {
	    this.kind = kind;
	    this.lastSeen = now;
	    this.lastAlert = 0;
	}
    }

    private static final class ClipCache {
	final long stamp;
	final byte[] data;

	ClipCache(long stamp, byte[] data) {
	    this.stamp = stamp;
	    this.data = data;
	}
    }

    private static final Map<Long, SeenPlayer> seen = new HashMap<>();
    private static final Map<String, ClipCache> clips = new HashMap<>();
    private static double lastFriendlyPlay;
    private static double lastUnknownPlay;
    private static double lastHostilePlay;
    private static double lastScan;
    private static final double STALE_SEC = 20.0;
    private static final double GLOBAL_GAP_SEC = 0.35;
    private static final double SCAN_INTERVAL = 0.20;
    private static final int SAMPLE_RATE = 22050;
    private static final byte[] DEFAULT_FRIENDLY = toneBytes(new int[] {740, 988}, new int[] {90, 135}, 30, 0.26);
    private static final byte[] DEFAULT_UNKNOWN = toneBytes(new int[] {660, 523, 660}, new int[] {85, 80, 125}, 24, 0.28);
    private static final byte[] DEFAULT_HOSTILE = toneBytes(new int[] {988, 784, 659, 784}, new int[] {70, 70, 90, 120}, 18, 0.32);

    public static void tick(GameUI gui, double dt) {
	if(gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null)
	    return;
	if(!active()) {
	    seen.clear();
	    lastScan = 0;
	    return;
	}
	double now = Utils.rtime();
	if((now - lastScan) < SCAN_INTERVAL)
	    return;
	lastScan = now;
	List<Gob> snapshot = new ArrayList<>();
	synchronized(gui.ui.sess.glob.oc) {
	    for(Gob gob : gui.ui.sess.glob.oc)
		snapshot.add(gob);
	}
	Set<Long> vis = new HashSet<>(snapshot.size());
	for(Gob gob : snapshot) {
	    if(gob == null || gob.removed || gob.virtual)
		continue;
	    if(MoonPacketHook.isPlayerGob(gob))
		continue;
	    if(MoonOverlay.classifyGob(gob) != MoonOverlay.GobType.PLAYER)
		continue;
	    vis.add(gob.id);
	    Kind kind = classify(gui, gob.id);
	    SeenPlayer st = seen.get(gob.id);
	    boolean fresh = (st == null) || ((now - st.lastSeen) > STALE_SEC);
	    boolean escalated = (st != null) && (priority(kind) > priority(st.kind));
	    if(st == null)
		st = new SeenPlayer(kind, now);
	    st.kind = kind;
	    st.lastSeen = now;
	    if((fresh || escalated) && enabled(kind) && ((now - st.lastAlert) >= MoonConfig.playerAlertCooldownSec)) {
		if(play(gui.ui, kind, false))
		    st.lastAlert = now;
	    }
	    seen.put(gob.id, st);
	}
	seen.entrySet().removeIf(e -> !vis.contains(e.getKey()) && ((now - e.getValue().lastSeen) > STALE_SEC));
    }

    private static boolean active() {
	return MoonConfig.playerAlertSounds &&
	    (MoonConfig.playerAlertFriendly || MoonConfig.playerAlertUnknown || MoonConfig.playerAlertHostile);
    }

    public static void test(UI ui, Kind kind) {
	play(ui, kind, true);
    }

    private static int priority(Kind kind) {
	switch(kind) {
	case HOSTILE: return 2;
	case UNKNOWN: return 1;
	default: return 0;
	}
    }

    private static Kind classify(GameUI gui, long gobid) {
	if(isHostile(gui, gobid))
	    return Kind.HOSTILE;
	if(isFriendly(gui, gobid))
	    return Kind.FRIENDLY;
	return Kind.UNKNOWN;
    }

    private static boolean isFriendly(GameUI gui, long gobid) {
	if(gui == null || gui.ui == null || gui.ui.sess == null)
	    return false;
	Party party = gui.ui.sess.glob.party;
	if(party == null || party.memb == null)
	    return false;
	for(Party.Member m : party.memb.values()) {
	    if(m != null && m.gobid == gobid)
		return true;
	}
	return false;
    }

    private static boolean isHostile(GameUI gui, long gobid) {
	Fightview fv = (gui != null) ? gui.fv : null;
	if(fv == null)
	    return false;
	if(fv.current != null && fv.current.gobid == gobid)
	    return true;
	for(Fightview.Relation rel : fv.lsrel) {
	    if(rel != null && !rel.invalid && rel.gobid == gobid)
		return true;
	}
	return false;
    }

    private static boolean enabled(Kind kind) {
	if(!MoonConfig.playerAlertSounds)
	    return false;
	switch(kind) {
	case FRIENDLY: return MoonConfig.playerAlertFriendly;
	case UNKNOWN: return MoonConfig.playerAlertUnknown;
	case HOSTILE: return MoonConfig.playerAlertHostile;
	default: return false;
	}
    }

    private static boolean play(UI ui, Kind kind, boolean force) {
	if(ui == null)
	    return false;
	double now = Utils.rtime();
	if(!force) {
	    double last;
	    switch(kind) {
	    case FRIENDLY: last = lastFriendlyPlay; break;
	    case UNKNOWN: last = lastUnknownPlay; break;
	    case HOSTILE: last = lastHostilePlay; break;
	    default: last = 0;
	    }
	    if((now - last) < GLOBAL_GAP_SEC)
		return false;
	}
	Audio.CS clip = clip(kind);
	if(clip == null)
	    return false;
	ui.sfx(new Audio.VolAdjust(clip, volume(kind)));
	switch(kind) {
	case FRIENDLY: lastFriendlyPlay = now; break;
	case UNKNOWN: lastUnknownPlay = now; break;
	case HOSTILE: lastHostilePlay = now; break;
	}
	return true;
    }

    private static double volume(Kind kind) {
	switch(kind) {
	case FRIENDLY: return MoonConfig.playerAlertFriendlyVolume / 1000.0;
	case UNKNOWN: return MoonConfig.playerAlertUnknownVolume / 1000.0;
	case HOSTILE: return MoonConfig.playerAlertHostileVolume / 1000.0;
	default: return 1.0;
	}
    }

    private static Audio.CS clip(Kind kind) {
	byte[] data = customBytes(path(kind));
	if(data == null)
	    data = defaults(kind);
	try {
	    return Audio.PCMClip.fromwav(new ByteArrayInputStream(data));
	} catch(IOException e) {
	    try {
		return Audio.PCMClip.fromwav(new ByteArrayInputStream(defaults(kind)));
	    } catch(IOException ignored) {
		return null;
	    }
	}
    }

    private static String path(Kind kind) {
	switch(kind) {
	case FRIENDLY: return MoonConfig.playerAlertFriendlyPath;
	case UNKNOWN: return MoonConfig.playerAlertUnknownPath;
	case HOSTILE: return MoonConfig.playerAlertHostilePath;
	default: return "";
	}
    }

    private static byte[] defaults(Kind kind) {
	switch(kind) {
	case FRIENDLY: return DEFAULT_FRIENDLY;
	case UNKNOWN: return DEFAULT_UNKNOWN;
	case HOSTILE: return DEFAULT_HOSTILE;
	default: return DEFAULT_UNKNOWN;
	}
    }

    private static byte[] customBytes(String raw) {
	String path = (raw == null) ? "" : raw.trim();
	if(path.isEmpty())
	    return null;
	try {
	    Path p = Paths.get(path);
	    if(!Files.isRegularFile(p))
		return null;
	    long stamp = Files.getLastModifiedTime(p).toMillis();
	    String key = p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
	    synchronized(clips) {
		ClipCache cur = clips.get(key);
		if(cur != null && cur.stamp == stamp)
		    return cur.data;
		byte[] data = Files.readAllBytes(p);
		clips.put(key, new ClipCache(stamp, data));
		return data;
	    }
	} catch(Exception ignored) {
	    return null;
	}
    }

    private static byte[] toneBytes(int[] freqs, int[] lengthsMs, int gapMs, double amp) {
	ByteArrayOutputStream pcm = new ByteArrayOutputStream();
	for(int seg = 0; seg < freqs.length && seg < lengthsMs.length; seg++) {
	    int samples = Math.max(1, (int)Math.round((lengthsMs[seg] / 1000.0) * SAMPLE_RATE));
	    double fade = Math.max(24.0, samples * 0.12);
	    for(int i = 0; i < samples; i++) {
		double envIn = Math.min(1.0, i / fade);
		double envOut = Math.min(1.0, (samples - 1 - i) / fade);
		double env = Math.min(envIn, envOut);
		double ang = (2.0 * Math.PI * freqs[seg] * i) / SAMPLE_RATE;
		short sv = (short)Math.round(Math.sin(ang) * env * amp * 32767.0);
		pcm.write(sv & 0xff);
		pcm.write((sv >> 8) & 0xff);
	    }
	    int gapSamples = Math.max(0, (int)Math.round((gapMs / 1000.0) * SAMPLE_RATE));
	    for(int i = 0; i < gapSamples; i++) {
		pcm.write(0);
		pcm.write(0);
	    }
	}
	byte[] pcmBytes = pcm.toByteArray();
	ByteArrayOutputStream wav = new ByteArrayOutputStream(44 + pcmBytes.length);
	int dataLen = pcmBytes.length;
	int byteRate = SAMPLE_RATE * 2;
	int riffLen = 36 + dataLen;
	writeAscii(wav, "RIFF");
	write32(wav, riffLen);
	writeAscii(wav, "WAVE");
	writeAscii(wav, "fmt ");
	write32(wav, 16);
	write16(wav, 1);
	write16(wav, 1);
	write32(wav, SAMPLE_RATE);
	write32(wav, byteRate);
	write16(wav, 2);
	write16(wav, 16);
	writeAscii(wav, "data");
	write32(wav, dataLen);
	wav.write(pcmBytes, 0, pcmBytes.length);
	return wav.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
	for(int i = 0; i < s.length(); i++)
	    out.write((byte)s.charAt(i));
    }

    private static void write16(ByteArrayOutputStream out, int v) {
	out.write(v & 0xff);
	out.write((v >> 8) & 0xff);
    }

    private static void write32(ByteArrayOutputStream out, int v) {
	out.write(v & 0xff);
	out.write((v >> 8) & 0xff);
	out.write((v >> 16) & 0xff);
	out.write((v >> 24) & 0xff);
    }
}
