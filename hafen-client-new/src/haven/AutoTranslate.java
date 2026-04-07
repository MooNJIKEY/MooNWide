package haven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background LibreTranslate queue, disk cache, and usage-based promotion to {@code lang_ru_literals.json}.
 * Invoked from {@link LocalizationManager#autoTranslate(String)}.
 */
final class AutoTranslate {
    private AutoTranslate() {}

    private static final long BOOT_START_NANO = System.nanoTime();
    /** Cap in-memory pairs (EN key variants + normalized); avoids huge maps from years of caching. */
    private static final int DEFAULT_MAX_CACHE_ENTRIES = 75_000;
    private static final Pattern WS_COLLAPSE = Pattern.compile("\\s+");

    private static final Map<String, String> translateCache = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> usageCounts = new ConcurrentHashMap<>();
    private static final java.util.Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private static volatile ExecutorService pool;
    private static volatile int poolThreads = -1;
    private static final AtomicLong apiSuccess = new AtomicLong();
    private static final AtomicLong uiGeneration = new AtomicLong();
    /** Limit queued + running jobs so a dead API cannot grow an unbounded task queue during map load. */
    private static final int MAX_BACKLOG = 512;
    private static final AtomicInteger backlog = new AtomicInteger();
    private static final AtomicBoolean cacheDirty = new AtomicBoolean();
    private static volatile ScheduledExecutorService flushScheduler;
    private static long lastRequestNano;

    static void init() {
	ensurePool();
	synchronized(AutoTranslate.class) {
	    if(flushScheduler == null) {
		flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		    Thread t = new Thread(r, "haven-translate-cache-flush");
		    t.setDaemon(true);
		    return(t);
		});
		flushScheduler.scheduleWithFixedDelay(() -> {
		    if(cacheDirty.getAndSet(false)) {
			evictIfNeeded();
			flushCacheFile();
		    }
		}, 2, 2, TimeUnit.SECONDS);
	    }
	}
	/* Disk read + JSON parse can be megabytes; never block the main / UI thread during startup. */
	Thread loader = new Thread(() -> {
	    try {
		loadCacheFileSync();
	    } catch(Throwable t) {
		new Warning(t, "l10n: translate_cache async load").issue();
	    }
	}, "haven-translate-cache-load");
	loader.setDaemon(true);
	loader.setPriority(Thread.NORM_PRIORITY - 1);
	loader.start();
    }

    /**
     * After boot cooldown, full pipeline runs (RichText walk, API queue). Before that: literals/overlay only
     * in {@link LocalizationManager#autoTranslateProcessed}.
     */
    static boolean allowHeavyPipeline() {
	if(!Utils.getprefb("moon-auto-translate", false))
	    return(false);
	int ms = Utils.getprefi("moon-translate-boot-cooldown-ms", 6000);
	if(ms <= 0)
	    return(true);
	long elapsedMs = (System.nanoTime() - BOOT_START_NANO) / 1_000_000L;
	return(elapsedMs >= (long)ms);
    }

    private static int maxCacheEntries() {
	return(Math.max(1024, Utils.getprefi("moon-translate-max-cache-entries", DEFAULT_MAX_CACHE_ENTRIES)));
    }

    /** Best-effort trim so flush and lookups stay bounded. */
    private static void evictIfNeeded() {
	int cap = maxCacheEntries();
	int sz = translateCache.size();
	if(sz <= cap)
	    return;
	int drop = sz - cap + Math.min(5000, cap / 10);
	java.util.Iterator<String> it = translateCache.keySet().iterator();
	for(int i = 0; i < drop && it.hasNext(); i++)
	    translateCache.remove(it.next());
    }

    static void shutdownPool() {
	ExecutorService p = pool;
	pool = null;
	if(p != null)
	    p.shutdown();
	poolThreads = -1;
    }

    private static void ensurePool() {
	int n = Math.max(1, Math.min(8, Utils.getprefi("moon-translate-max-threads", 3)));
	if(pool != null && poolThreads == n)
	    return;
	shutdownPool();
	poolThreads = n;
	pool = Executors.newFixedThreadPool(n, r -> {
	    Thread t = new Thread(r, "libretranslate-" + System.identityHashCode(r));
	    t.setDaemon(true);
	    return(t);
	});
    }

    static Path cachePath() {
	String home = System.getProperty("user.home", ".");
	return(Paths.get(home, ".haven", "translate_cache.json"));
    }

    private static void loadCacheFileSync() {
	Path p = cachePath();
	if(!Files.isRegularFile(p))
	    return;
	try {
	    long sz = Files.size(p);
	    if(sz > 12_000_000L) {
		new Warning(null, "l10n: translate_cache.json too large (" + sz + " B), skipping load (use smaller file or raise limit in code)").issue();
		return;
	    }
	    String raw = Files.readString(p, StandardCharsets.UTF_8);
	    Map<String, String> m = LocalizationManager.parseFlatJsonObject(raw);
	    translateCache.putAll(m);
	    evictIfNeeded();
	} catch(Exception e) {
	    new Warning(e, "l10n: translate_cache.json load").issue();
	}
    }

    private static synchronized void flushCacheFile() {
	Path p = cachePath();
	try {
	    Files.createDirectories(p.getParent());
	    String json = flatJsonString(translateCache);
	    Files.writeString(p, json, StandardCharsets.UTF_8);
	} catch(Exception e) {
	    new Warning(e, "l10n: translate_cache.json save").issue();
	}
    }

    static String flatJsonString(Map<String, String> m) {
	StringBuilder sb = new StringBuilder(m.size() * 32 + 4);
	sb.append('{');
	boolean first = true;
	for(Map.Entry<String, String> e : m.entrySet()) {
	    if(!first)
		sb.append(',');
	    first = false;
	    sb.append('\n').append("  ")
		.append(LibreTranslateService.jsonString(e.getKey()))
		.append(": ")
		.append(LibreTranslateService.jsonString(e.getValue()));
	}
	sb.append('\n').append('}');
	return(sb.toString());
    }

    static long uiGeneration() {
	return(uiGeneration.get());
    }

    static void bumpUiGeneration() {
	uiGeneration.incrementAndGet();
    }

    /** Plain-text segment only (no {@code $} markup); used by {@link LocalizationManager#autoTranslateProcessed}. */
    static String translateSegment(String text) {
	if(text == null || text.isEmpty())
	    return(text);
	if(!LocalizationManager.LANG_RU.equals(MoonL10n.lang()) || !Utils.getprefb("moon-auto-translate", false))
	    return(text);
	if(text.indexOf('$') >= 0)
	    return(text);
	String trim = text.trim();
	String o = LocalizationManager.lookupOverlayValueForAuto(trim);
	if(o != null)
	    return(postProcessRu(o));
	String lit = LocalizationManager.lookupLiteralForAuto(trim);
	if(lit != null)
	    return(postProcessRu(lit));
	if(!allowHeavyPipeline()) {
	    String cachedEarly = getCached(trim);
	    if(cachedEarly != null && !cachedEarly.isEmpty())
		return(postProcessRu(cachedEarly));
	    return(text);
	}
	String ruLocal = tryLocalizePatterns(trim);
	if(ruLocal != null) {
	    putCached(trim, ruLocal);
	    usageCounts.computeIfAbsent(normalizeKey(trim), k -> new AtomicInteger()).incrementAndGet();
	    maybePromoteToLiterals(trim, ruLocal);
	    return(postProcessRu(ruLocal));
	}
	String cached = getCached(trim);
	if(cached != null && !cached.isEmpty()) {
	    usageCounts.computeIfAbsent(normalizeKey(trim), k -> new AtomicInteger()).incrementAndGet();
	    maybePromoteToLiterals(trim, cached);
	    return(postProcessRu(cached));
	}
	if(containsCyrillic(trim))
	    return(text);
	if(!passesSmartFilter(trim))
	    return(text);
	usageCounts.computeIfAbsent(normalizeKey(trim), k -> new AtomicInteger()).incrementAndGet();
	schedule(trim);
	return(text);
    }

    static String normalizeKey(String s) {
	if(s == null)
	    return("");
	String t = WS_COLLAPSE.matcher(s.trim()).replaceAll(" ");
	return(t.toLowerCase(Locale.ROOT));
    }

    private static String getCached(String trimmed) {
	String v = translateCache.get(trimmed);
	if(v != null && !v.isEmpty())
	    return(v);
	String nk = normalizeKey(trimmed);
	return(translateCache.get(nk));
    }

    private static void putCached(String trimmedOriginal, String ru) {
	String nk = normalizeKey(trimmedOriginal);
	translateCache.put(nk, ru);
	if(!trimmedOriginal.equals(nk))
	    translateCache.put(trimmedOriginal, ru);
	evictIfNeeded();
    }

    private static void schedule(String text) {
	final String trim = text.trim();
	String nk = normalizeKey(trim);
	if(!inFlight.add(nk))
	    return;
	int b = backlog.incrementAndGet();
	if(b > MAX_BACKLOG) {
	    backlog.decrementAndGet();
	    inFlight.remove(nk);
	    logFallback("backlog", trim, null);
	    return;
	}
	if(translateDebug())
	    new Warning(null, "l10n: auto-translate queued: \"" + truncate(trim, 72) + "\"").issue();
	ensurePool();
	ExecutorService ex = pool;
	if(ex == null) {
	    backlog.decrementAndGet();
	    inFlight.remove(nk);
	    return;
	}
	ex.submit(() -> {
	    try {
		runJob(trim);
	    } finally {
		backlog.decrementAndGet();
	    }
	});
    }

    private static void acquireRateLimit() {
	int rps = Math.max(1, Utils.getprefi("moon-translate-rps", 4));
	long gapNs = 1_000_000_000L / rps;
	synchronized(AutoTranslate.class) {
	    long now = System.nanoTime();
	    long wait = gapNs - (now - lastRequestNano);
	    if(wait > 0) {
		try {
		    long ms = wait / 1_000_000;
		    int ns = (int)(wait % 1_000_000);
		    Thread.sleep(ms, ns);
		} catch(InterruptedException ignored) {
		    Thread.currentThread().interrupt();
		}
	    }
	    lastRequestNano = System.nanoTime();
	}
    }

    private static void runJob(String text) {
	String nk = normalizeKey(text);
	try {
	    String ruLocal = tryLocalizePatterns(text);
	    if(ruLocal != null) {
		String pr = postProcessRu(ruLocal);
		putCached(text, pr);
		apiSuccess.incrementAndGet();
		maybePromoteToLiterals(text, pr);
		bumpUiGeneration();
		cacheDirty.set(true);
		maybeDictionaryHint(text, pr);
		return;
	    }
	    acquireRateLimit();
	    String ru = LibreTranslateService.translateBlocking(text);
	    if(ru == null || ru.isEmpty()) {
		logFallback("empty-response", text, null);
		return;
	    }
	    ru = postProcessRu(ru);
	    if(Objects.equals(ru, text) || equalsIgnoreCaseAscii(ru, text)) {
		logBadTranslation(text, ru);
		return;
	    }
	    putCached(text, ru);
	    apiSuccess.incrementAndGet();
	    if(translateDebug() || Utils.getprefb("moon-translate-log", false))
		new Warning(null, "l10n: translated → " + truncate(ru, 80)).issue();
	    maybePromoteToLiterals(text, ru);
	    maybeDictionaryHint(text, ru);
	    bumpUiGeneration();
	    cacheDirty.set(true);
	} catch(Exception e) {
	    new Warning(e, "l10n: LibreTranslate failed for \"" + truncate(text, 40) + "\"").issue();
	    logFallback("api", text, e);
	} finally {
	    inFlight.remove(nk);
	}
    }

    static void logFallback(String kind, String src, Throwable t) {
	if(!(translateDebug() || Utils.getprefb("moon-translate-log", false)))
	    return;
	String msg = "l10n[at] fallback " + kind + ": \"" + truncate(src, 60) + "\"";
	if(t != null)
	    new Warning(t, msg).issue();
	else
	    new Warning(null, msg).issue();
    }

    private static void logBadTranslation(String en, String ru) {
	if(translateDebug() || Utils.getprefb("moon-translate-log", false))
	    new Warning(null, "l10n[at] suspicious translation (same as source): \"" + truncate(en, 48) + "\"").issue();
    }

    private static void maybeDictionaryHint(String en, String ru) {
	if(!Utils.getprefb("moon-translate-dictionary-hint", false))
	    return;
	try {
	    Path p = Paths.get(System.getProperty("user.home", "."), ".haven", "l10n", "translate_dictionary_suggestions.txt");
	    Files.createDirectories(p.getParent());
	    String line = en.replace('\n', ' ') + "\t" + ru.replace('\n', ' ') + "\n";
	    Files.writeString(p, line, StandardCharsets.UTF_8,
		java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
	} catch(IOException ignored) {
	}
    }

    private static boolean equalsIgnoreCaseAscii(String a, String b) {
	return(a != null && b != null && a.equalsIgnoreCase(b));
    }

    /** Rule-based RU snippets without API (quality prefix, LP). */
    private static String tryLocalizePatterns(String trim) {
	java.util.regex.Matcher qm = java.util.regex.Pattern.compile("(?i)^Q\\s*(\\d+)\\s+(.+)$").matcher(trim);
	if(qm.matches()) {
	    String q = qm.group(1);
	    String item = qm.group(2).trim();
	    String itemTr = translateSegmentIsolated(item);
	    return(itemTr + " (качество " + q + ")");
	}
	java.util.regex.Pattern lp = java.util.regex.Pattern.compile("(\\d+)\\s*LP\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
	java.util.regex.Matcher lm = lp.matcher(trim);
	if(lm.find()) {
	    String suf = LocalizationManager.trOr("auto.translate.lp", "очков обучения");
	    lm.reset();
	    StringBuffer sb = new StringBuffer();
	    while(lm.find())
		lm.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(lm.group(1) + " " + suf));
	    lm.appendTail(sb);
	    return(sb.toString());
	}
	return(null);
    }

    /** One translateSegment pass without scheduling (for nested Q-name resolution). */
    private static String translateSegmentIsolated(String seg) {
	if(seg == null || seg.isEmpty())
	    return(seg);
	String o = LocalizationManager.lookupOverlayValueForAuto(seg);
	if(o != null)
	    return(postProcessRu(o));
	String lit = LocalizationManager.lookupLiteralForAuto(seg);
	if(lit != null)
	    return(postProcessRu(lit));
	String c = getCached(seg.trim());
	if(c != null)
	    return(postProcessRu(c));
	return(seg);
    }

    static String postProcessRu(String ru) {
	if(ru == null)
	    return(null);
	return(ru.trim().replaceAll("\\s{2,}", " "));
    }

    private static boolean translateDebug() {
	return(Utils.getprefb("moon-translate-debug", false) || Utils.getprefb("moon-l10n-debug", false));
    }

    private static String truncate(String s, int max) {
	if(s == null)
	    return("");
	return(s.length() <= max ? s : s.substring(0, max) + "…");
    }

    private static void maybePromoteToLiterals(String en, String ru) {
	int th = Math.max(1, Utils.getprefi("moon-translate-literal-threshold", 5));
	AtomicInteger c = usageCounts.get(normalizeKey(en));
	if(c == null || c.get() < th)
	    return;
	Path userLit = userLiteralsPath();
	if(userLit == null)
	    return;
	try {
	    Map<String, String> cur = new HashMap<>();
	    if(Files.isRegularFile(userLit)) {
		cur.putAll(LocalizationManager.parseFlatJsonObject(Files.readString(userLit, StandardCharsets.UTF_8)));
	    }
	    if(Objects.equals(cur.get(en), ru))
		return;
	    cur.put(en, ru);
	    Files.createDirectories(userLit.getParent());
	    Files.writeString(userLit, flatJsonString(cur), StandardCharsets.UTF_8);
	    LocalizationManager.mergeLiteralEntry(en, ru);
	    bumpUiGeneration();
	    if(translateDebug())
		new Warning(null, "l10n: promoted literal (used ≥" + th + "×) → " + userLit).issue();
	} catch(IOException e) {
	    new Warning(e, "l10n: literals promote").issue();
	}
    }

    private static Path userLiteralsPath() {
	try {
	    String home = System.getProperty("user.home", ".");
	    Path p = Paths.get(home, ".haven", "l10n", "lang_ru_literals.json");
	    return(p);
	} catch(Exception e) {
	    return(null);
	}
    }

    static boolean containsCyrillic(String s) {
	for(int i = 0, n = s.length(); i < n; i++) {
	    char c = s.charAt(i);
	    if(c >= '\u0400' && c <= '\u04FF')
		return(true);
	}
	return(false);
    }

    static boolean passesSmartFilter(String s) {
	if(s.length() < 2)
	    return(false);
	String t = s.trim();
	if(t.isEmpty())
	    return(false);
	if(t.matches("-?\\d+(\\.\\d+)?"))
	    return(false);
	if(t.matches("0[xX][0-9a-fA-F]+"))
	    return(false);
	if(t.matches("[0-9a-fA-F]{8,}"))
	    return(false);
	if(t.indexOf(' ') < 0 && t.indexOf('/') >= 0 && t.matches("[a-z0-9_/\\\\.-]+"))
	    return(false);
	boolean hasLetter = false;
	for(int i = 0, n = t.length(); i < n; i++) {
	    char c = t.charAt(i);
	    if(Character.isLetter(c)) {
		hasLetter = true;
		break;
	    }
	}
	return(hasLetter);
    }

    static long apiTranslatedCount() {
	return(apiSuccess.get());
    }

    static int cacheSize() {
	return(translateCache.size());
    }

    static String metricsLine() {
	return(String.format(Locale.ROOT, "auto-translate: API ok=%d, cache entries=%d, unique queued=%d",
	    apiSuccess.get(), translateCache.size(), usageCounts.size()));
    }
}
