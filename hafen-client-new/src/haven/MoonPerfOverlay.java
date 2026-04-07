package haven;

import java.awt.Color;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class MoonPerfOverlay {
    private MoonPerfOverlay() {}

    private static volatile int fps = -1;
    private static volatile int frameLag;
    private static volatile double avgFrameMs;
    private static volatile double p95FrameMs;
    private static volatile int jankCount;
    private static int fontPx = -1;
    private static Text.Foundry foundry;
    private static final int HUD_LINE_CAP = 5;
    private static final String[] cachedKeys = new String[HUD_LINE_CAP];
    private static final Tex[] cachedTex = new Tex[HUD_LINE_CAP];
    private static final String[] cachedLines = new String[HUD_LINE_CAP];
    private static double nextHudRefresh = Double.NEGATIVE_INFINITY;
    private static final double HUD_REFRESH_INTERVAL = 0.22;
    private static volatile boolean debugDetails = Utils.getprefb("moon-perf-debug", false);
    private static volatile double dispatchMs;
    private static volatile double worldMs;
    private static volatile double uiTickMs;
    private static volatile double worldBuildMs;
    private static volatile double drawMs;
    private static volatile double swapMs;
    private static volatile double waitMs;
    private static volatile int gobPending;
    private static volatile int gobReady;
    private static volatile int gobAddRate;
    private static volatile int terrainPending;
    private static volatile int optionalPending;
    private static final AtomicInteger textRenderCount = new AtomicInteger();
    private static final AtomicInteger tooltipRenderCount = new AtomicInteger();
    private static final AtomicInteger translateRefreshCount = new AtomicInteger();

    private static Text.Foundry foundry() {
	int px = MoonConfig.overlayTextSize;
	if((foundry == null) || (fontPx != px)) {
	    clearCachedTex();
	    fontPx = px;
	    foundry = new Text.Foundry(Text.sans, px).aa(true);
	}
	return(foundry);
    }

    private static void clearCachedTex() {
	for(int i = 0; i < cachedTex.length; i++) {
	    if(cachedTex[i] != null) {
		cachedTex[i].dispose();
		cachedTex[i] = null;
	    }
	    cachedKeys[i] = null;
	    cachedLines[i] = null;
	}
    }

    public static void updateRenderStats(int framesPerSec, int swapLagFrames, double avgMs, double p95Ms, int jankFrames) {
	fps = framesPerSec;
	frameLag = swapLagFrames;
	avgFrameMs = avgMs;
	p95FrameMs = p95Ms;
	jankCount = jankFrames;
    }

    private static double smooth(double prev, double next) {
	if(prev <= 0.0)
	    return(next);
	return(prev * 0.82) + (next * 0.18);
    }

    public static void updatePhaseStats(double dispatch, double world, double ui, double build, double draw, double swap, double wait) {
	dispatchMs = smooth(dispatchMs, dispatch);
	worldMs = smooth(worldMs, world);
	uiTickMs = smooth(uiTickMs, ui);
	worldBuildMs = smooth(worldBuildMs, build);
	drawMs = smooth(drawMs, draw);
	swapMs = smooth(swapMs, swap);
	waitMs = smooth(waitMs, wait);
    }

    public static void updateWorldBuildMs(double build) {
	worldBuildMs = smooth(worldBuildMs, build);
    }

    public static double worldBuildMs() {
	return(worldBuildMs);
    }

    public static void updateSceneStats(int ready, int pending, int addsPerSec, int terrain, int optional) {
	gobReady = ready;
	gobPending = pending;
	gobAddRate = addsPerSec;
	terrainPending = terrain;
	optionalPending = optional;
    }

    public static void countTextRender() {
	textRenderCount.incrementAndGet();
    }

    public static void countTooltipRender() {
	textRenderCount.incrementAndGet();
	tooltipRenderCount.incrementAndGet();
    }

    public static void countTranslateRefresh() {
	translateRefreshCount.incrementAndGet();
    }

    public static void setDebugDetails(boolean val) {
	debugDetails = val;
	Utils.setprefb("moon-perf-debug", val);
	clearCachedTex();
    }

    public static boolean debugDetails() {
	return(debugDetails);
    }

    private static String phaseLine() {
	return(String.format(Locale.ROOT, "ph dsp %.2f | wrd %.2f | ui %.2f | bld %.2f", dispatchMs, worldMs, uiTickMs, worldBuildMs));
    }

    private static String phaseLine2() {
	return(String.format(Locale.ROOT, "ph drw %.2f | swp %.2f | w %.2f", drawMs, swapMs, waitMs));
    }

    private static String sceneLine() {
	return(String.format(Locale.ROOT, "gob %d+%d | +%d/s | map %d+%d", gobReady, gobPending, gobAddRate, terrainPending, optionalPending));
    }

    private static String churnLine(int textRenders, int tooltipRenders, int translateRefreshes) {
	return(String.format(Locale.ROOT, "txt %d | tip %d | tr %d", textRenders, tooltipRenders, translateRefreshes));
    }

    public static String dump() {
	int tr = textRenderCount.get();
	int tip = tooltipRenderCount.get();
	int tref = translateRefreshCount.get();
	return(String.format(Locale.ROOT,
	    "MoonPerf fps=%d lag=%d avg=%.1f p95=%.1f jank=%d || %s || %s || %s || %s",
	    fps, frameLag, avgFrameMs, p95FrameMs, jankCount,
	    phaseLine(), phaseLine2(), sceneLine(), churnLine(tr, tip, tref)));
    }

    public static boolean inStressWindow() {
	return(frameLag > 2) || (p95FrameMs > 24.0) || (avgFrameMs > 20.0);
    }

    private static String pingText(int ping) {
	return((ping >= 0) ? (ping + " ms") : "--");
    }

    private static String appendStat(String base, String extra) {
	if(extra == null || extra.isEmpty())
	    return(base);
	if(base == null || base.isEmpty())
	    return(extra);
	return(base + " | " + extra);
    }

    private static void refreshHudLines(GameUI gui, double now) {
	int ping = -1;
	String loader = "";
	try {
	    if(gui.ui != null) {
		loader = gui.ui.loader.stats();
		if(gui.ui.sess != null)
		    ping = gui.ui.sess.conn.smoothedPingMs();
	    }
	} catch(Exception ignored) {
	}
	MapView.MoonLoadProgress load = (gui.map != null) ? gui.map.moonLoadProgress() : null;
	int resq = 0;
	try {
	    resq = Resource.local().qdepth() + Resource.remote().qdepth();
	} catch(Exception ignored) {
	}
	int txt = textRenderCount.getAndSet(0);
	int tip = tooltipRenderCount.getAndSet(0);
	int tr = translateRefreshCount.getAndSet(0);
	for(int i = 0; i < cachedLines.length; i++)
	    cachedLines[i] = null;
	if(!MoonConfig.perfHudCompact && !debugDetails) {
	    String line = String.format(Locale.ROOT, "%d FPS | %s", fps, pingText(ping));
	    if(frameLag > 2)
		line = appendStat(line, "q:" + frameLag);
	    cachedLines[0] = line;
	} else {
	    String line1 = String.format(Locale.ROOT, "%d FPS | %s | %.1f/%.1f ms", fps, pingText(ping), avgFrameMs, p95FrameMs);
	    String line2 = "";
	    if(frameLag > 0 || inStressWindow())
		line2 = appendStat(line2, "q:" + frameLag);
	    if(jankCount > 0)
		line2 = appendStat(line2, "j:" + jankCount);
	    if(loader != null && !loader.isEmpty())
		line2 = appendStat(line2, "L " + loader);
	    if((resq > 0) || ((load != null) && load.loading))
		line2 = appendStat(line2, "R " + resq);
	    if(load != null && load.loading)
		line2 = appendStat(line2, load.hudSummary());
	    cachedLines[0] = line1;
	    cachedLines[1] = line2.isEmpty() ? null : line2;
	    if(debugDetails) {
		cachedLines[2] = phaseLine();
		cachedLines[3] = phaseLine2() + " | " + sceneLine();
		cachedLines[4] = churnLine(txt, tip, tr);
	    }
	}
	nextHudRefresh = now + HUD_REFRESH_INTERVAL;
    }

    public static void draw(GameUI gui, GOut g) {
	if(!MoonConfig.showFpsPingHud || gui == null)
	    return;
	int f = fps;
	if(f < 0)
	    return;
	double now = Utils.rtime();
	if((cachedLines[0] == null) || (now >= nextHudRefresh))
	    refreshHudLines(gui, now);
	int x = g.sz().x - UI.scale(8);
	int y = UI.scale(8);
	Color col = MoonConfig.overlayTextColor();
	for(int i = 0; i < cachedLines.length; i++) {
	    String line = cachedLines[i];
	    if(line == null || line.isEmpty())
		continue;
	    Tex tex = lineTex(i, line, col);
	    g.aimage(tex, new Coord(x, y), 1.0, 0.0);
	    y += tex.sz().y + UI.scale(2);
	}
    }

    private static Tex lineTex(int idx, String line, Color col) {
	foundry();
	String key = fontPx + "|" + (col.getRGB() & 0xffffffffL) + "|" + line;
	if(key.equals(cachedKeys[idx]) && cachedTex[idx] != null)
	    return(cachedTex[idx]);
	if(cachedTex[idx] != null)
	    cachedTex[idx].dispose();
	cachedKeys[idx] = key;
	cachedTex[idx] = new TexI(Utils.outline2(foundry.render(line, col).img, Color.BLACK));
	return(cachedTex[idx]);
    }
}
