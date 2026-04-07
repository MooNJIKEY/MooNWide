package haven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON-driven string overlays and optional filesystem {@code .res} overrides for localized clients.
 *
 * <p><b>Keys:</b> {@link #tr(String)} reads {@code lang_ru.json} / {@code lang_en.json} from the
 * classpath ({@code /haven/l10n/}), merges {@code ~/.haven/l10n/lang_*.json}, then falls back to
 * built-in {@link MoonL10n} tables.
 *
 * <p><b>Literals:</b> {@link #applyUiLiteral} translates exact English UI strings via
 * {@code lang_ru_literals.json} (+ user {@code ~/.haven/l10n/lang_ru_literals.json}). It is invoked
 * from {@link Text.Foundry#render} and plain {@link RichText.Foundry#render} (no {@code $} markup).
 * Pref {@code moon-l10n-debug} logs likely-untranslated literals once per string.
 *
 * <p><b>Auto-translate:</b> {@link #autoTranslate(String)} (after literals) uses LibreTranslate when
 * {@code moon-auto-translate} is on, caches results in {@code ~/.haven/translate_cache.json}, and may
 * append hot strings to {@code ~/.haven/l10n/lang_ru_literals.json}. See prefs {@code moon-translate-api-url},
 * {@code moon-translate-max-threads}, {@code moon-translate-api-key} (optional),
 * {@code moon-translate-connect-ms}, {@code moon-translate-read-ms} (HTTP timeouts),
 * {@code moon-translate-rps} (max API requests per second per client),
 * {@code moon-translate-log} (verbose fallback/API logging),
 * {@code moon-translate-dictionary-hint} (append EN↔RU pairs to {@code ~/.haven/l10n/translate_dictionary_suggestions.txt}),
 * {@code moon-translate-boot-cooldown-ms} (defer heavy work after launch),
 * {@code moon-translate-max-rich-chars}, {@code moon-translate-max-cache-entries}.
 *
 * <p><b>Resources:</b> when UI language is Russian at startup, if {@code ~/.haven/l10n/ru/}
 * contains a tree of {@code .res} files (paths like {@code gfx/hud/foo.res} mirroring the JAR),
 * that directory is {@link Resource.Pool#prepend prepended} to {@link Resource#local()} so those
 * assets win over the embedded JAR. Changing language in options does not rebuild the pool —
 * restart the client to apply {@code .res} overrides.
 */
public final class LocalizationManager {
    private LocalizationManager() {}

    public static final String LANG_EN = MoonL10n.LANG_EN;
    public static final String LANG_RU = MoonL10n.LANG_RU;

    private static final Map<String, String> overlayRu = new HashMap<>();
    private static final Map<String, String> overlayEn = new HashMap<>();
    /** English (or any source) UI string → Russian; used by {@link #applyUiLiteral}. */
    private static final Map<String, String> literalsRu = new HashMap<>();
    private static final Set<String> literalDebugOnce = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean resourceOverrideInstalled;
    private static volatile long sampledUiGeneration = -1;
    private static volatile double sampledUiGenerationAt = Double.NEGATIVE_INFINITY;
    private static final double UI_GENERATION_SAMPLE_INTERVAL = 0.18;

    /**
     * Call once early in startup (before heavy {@link Resource} use). Safe to call again to reload JSON.
     */
    public static void bootstrap() {
	reloadOverlays();
	AutoTranslate.init();
	installResourceOverrideIfNeeded();
    }

    public static void reloadOverlays() {
	synchronized(overlayRu) {
	    overlayRu.clear();
	    overlayEn.clear();
	    literalsRu.clear();
	    loadLangFile(LANG_RU, overlayRu);
	    loadLangFile(LANG_EN, overlayEn);
	    loadLiteralsFile(LANG_RU, literalsRu);
	    Path user = userLangFile(LANG_RU);
	    if(user != null)
		mergeJsonFile(user, overlayRu);
	    user = userLangFile(LANG_EN);
	    if(user != null)
		mergeJsonFile(user, overlayEn);
	    user = userLiteralsFile(LANG_RU);
	    if(user != null)
		mergeJsonFile(user, literalsRu);
	}
    }

    /**
     * Resolve a message key: JSON overlays ({@code lang_*.json}), then {@link MoonL10n} built-in tables.
     * This is the single entry point for keyed UI strings (replaces direct {@link MoonL10n#t} usage).
     */
    public static String tr(String key) {
	if(key == null)
	    return("");
	String k = key;
	String lang = MoonL10n.lang();
	synchronized(overlayRu) {
	    String v = lookupInMaps(lang, k);
	    if(v != null)
		return(v);
	}
	String builtin = MoonL10n.lookupStaticForManager(k);
	if(builtin != null)
	    return(builtin);
	return(k);
    }

    /** After {@link #tr(String)}, apply {@link String#format(Locale, String, Object...)} with {@link Locale#US}. */
    public static String trf(String key, Object... args) {
	return(String.format(Locale.US, tr(key), args));
    }

    /** If {@code key} is missing from all tables, return {@code fallback} instead of the key. */
    public static String trOr(String key, String fallback) {
	String v = tr(key);
	return(Objects.equals(v, key) ? fallback : v);
    }

    /**
     * Translate a full UI string for Russian locale using {@code lang_ru_literals.json} (exact key match).
     * Hooked from {@link Text.Foundry#render} and plain {@link RichText.Foundry#render} (no {@code $} markup).
     */
    public static String applyUiLiteral(String text) {
	if(text == null || text.isEmpty())
	    return(text);
	if(!LANG_RU.equals(MoonL10n.lang()))
	    return(text);
	synchronized(overlayRu) {
	    String v = literalsRu.get(text);
	    if(v != null && !v.isEmpty())
		return(v);
	}
	if(Utils.getprefb("moon-l10n-debug", false) && looksLikeUnlocalizedEnglish(text)
	    && literalDebugOnce.add(text)) {
	    String sn = text.length() > 120 ? text.substring(0, 117) + "..." : text;
	    new Warning(null, "l10n: untranslated literal → ~/.haven/l10n/lang_ru_literals.json: " + sn).issue();
	}
	return(text);
    }

    /**
     * Full auto-translate pipeline: whole-string literal, then either {@link RichText} segment-wise
     * translation (preserving {@code $…} markup) or plain {@link AutoTranslate#translateSegment}.
     */
    public static String autoTranslateProcessed(String text) {
	if(text == null || text.isEmpty())
	    return(text);
	String base = text;
	if(LANG_RU.equals(MoonL10n.lang())) {
	    String fl = applyUiLiteral(base);
	    if(!Objects.equals(fl, base))
		base = fl;
	    String ov = MoonGameOverlay.apply(base);
	    if(!Objects.equals(ov, base))
		base = ov;
	}
	if(!LANG_RU.equals(MoonL10n.lang()) || !Utils.getprefb("moon-auto-translate", false))
	    return(base);
	int maxRich = Math.max(1024, Utils.getprefi("moon-translate-max-rich-chars", 6144));
	if(base.length() > maxRich && base.indexOf('$') >= 0)
	    return(base);
	if(base.indexOf('$') < 0)
	    return(AutoTranslate.translateSegment(base));
	/* RichText segment walk is expensive during startup; plain path still uses overlay/literal/cache. */
	if(!AutoTranslate.allowHeavyPipeline())
	    return(base);
	try {
	    return(RichTextAutoTranslate.translateRich(base, seg -> {
		if(seg.isEmpty())
		    return(seg);
		String ls = applyUiLiteral(seg);
		if(!Objects.equals(ls, seg))
		    return(ls);
		return(AutoTranslate.translateSegment(seg));
	    }));
	} catch(Throwable t) {
	    AutoTranslate.logFallback("rich-walk", base, t);
	    return(base);
	}
    }

    /** Prefer {@link #autoTranslateProcessed(String)}; kept as alias for older call sites. */
    @Deprecated
    public static String autoTranslate(String text) {
	return(autoTranslateProcessed(text));
    }

    /** Same as {@link #autoTranslateProcessed(String)} (chat / RichText entry). */
    public static String autoTranslateRich(String text) {
	return(autoTranslateProcessed(text));
    }

    /** When true, chat lines re-render after async translations land. */
    public static boolean autoTranslateChatRefresh() {
	return(Utils.getprefb("moon-auto-translate", false) && LANG_RU.equals(MoonL10n.lang()));
    }

    /** Bump translation generation and hint widgets to redraw (chat uses {@link ChatUI.Channel.Message#valid}). */
    public static void refreshTranslateUI() {
	AutoTranslate.bumpUiGeneration();
    }

    /** Tooltip / pagina refresh generation; bumps when cache or literals change. */
    public static long autoTranslateUiGeneration() {
	return(AutoTranslate.uiGeneration());
    }

    public static long autoTranslateUiGenerationSampled() {
	long cur = AutoTranslate.uiGeneration();
	if(sampledUiGeneration < 0) {
	    sampledUiGeneration = cur;
	    sampledUiGenerationAt = Utils.rtime();
	    return(cur);
	}
	if(cur == sampledUiGeneration)
	    return(cur);
	double now = Utils.rtime();
	if((now - sampledUiGenerationAt) < UI_GENERATION_SAMPLE_INTERVAL)
	    return(sampledUiGeneration);
	sampledUiGeneration = cur;
	sampledUiGenerationAt = now;
	return(cur);
    }

    /** One-line metrics for logs or console. */
    public static String autoTranslateMetricsLine() {
	return(AutoTranslate.metricsLine());
    }

    static String lookupOverlayValueForAuto(String text) {
	if(text == null)
	    return(null);
	synchronized(overlayRu) {
	    return(lookupInMaps(MoonL10n.lang(), text));
	}
    }

    static String lookupLiteralForAuto(String text) {
	if(text == null)
	    return(null);
	synchronized(overlayRu) {
	    String v = literalsRu.get(text);
	    if(v != null && !v.isEmpty())
		return(v);
	    return(null);
	}
    }

    static void mergeLiteralEntry(String en, String ru) {
	if(en == null || ru == null)
	    return;
	synchronized(overlayRu) {
	    literalsRu.put(en, ru);
	}
    }

    private static boolean looksLikeUnlocalizedEnglish(String s) {
	if(s.indexOf('$') >= 0)
	    return(false);
	int lat = 0, cyr = 0;
	for(int i = 0, n = s.length(); i < n; i++) {
	    char c = s.charAt(i);
	    if((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
		lat++;
	    if(c >= '\u0400' && c <= '\u04FF')
		cyr++;
	}
	return(lat >= 4 && cyr == 0);
    }

    /**
     * @return overlay string if present, else {@code null} (do not use as final UI string).
     */
    public static String overlay(String key) {
	if(key == null)
	    return(null);
	synchronized(overlayRu) {
	    return(lookupInMaps(MoonL10n.lang(), key));
	}
    }

    private static String lookupInMaps(String lang, String key) {
	Map<String, String> primary = LANG_RU.equals(lang) ? overlayRu : overlayEn;
	return(primary.get(key));
    }

    private static void loadLangFile(String lang, Map<String, String> into) {
	String fn = "lang_" + lang + ".json";
	try(InputStream in = LocalizationManager.class.getResourceAsStream("/haven/l10n/" + fn)) {
	    if(in == null)
		return;
	    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
	    mergeJsonText(text, into);
	} catch(IOException e) {
	    new Warning(e, "l10n: classpath " + fn).issue();
	}
    }

    private static void loadLiteralsFile(String lang, Map<String, String> into) {
	if(!LANG_RU.equals(lang))
	    return;
	String fn = "lang_" + lang + "_literals.json";
	try(InputStream in = LocalizationManager.class.getResourceAsStream("/haven/l10n/" + fn)) {
	    if(in == null)
		return;
	    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
	    mergeJsonText(text, into);
	} catch(IOException e) {
	    new Warning(e, "l10n: classpath " + fn).issue();
	}
    }

    private static Path userLangFile(String lang) {
	try {
	    String home = System.getProperty("user.home", ".");
	    Path p = Paths.get(home, ".haven", "l10n", "lang_" + lang + ".json");
	    if(Files.isRegularFile(p))
		return(p);
	} catch(Exception ignored) {
	}
	String prop = System.getProperty("haven.l10n.json." + lang, null);
	if(prop != null) {
	    Path p = Paths.get(prop);
	    if(Files.isRegularFile(p))
		return(p);
	}
	return(null);
    }

    private static Path userLiteralsFile(String lang) {
	if(!LANG_RU.equals(lang))
	    return(null);
	try {
	    String home = System.getProperty("user.home", ".");
	    Path p = Paths.get(home, ".haven", "l10n", "lang_" + lang + "_literals.json");
	    if(Files.isRegularFile(p))
		return(p);
	} catch(Exception ignored) {
	}
	return(null);
    }

    private static void mergeJsonFile(Path file, Map<String, String> into) {
	try {
	    mergeJsonText(Files.readString(file, StandardCharsets.UTF_8), into);
	} catch(IOException e) {
	    new Warning(e, "l10n: " + file).issue();
	}
    }

    static void mergeJsonText(String text, Map<String, String> into) {
	Map<String, String> parsed = parseFlatJsonObject(text);
	into.putAll(parsed);
    }

    /**
     * Minimal flat JSON object parser: flat {@code "key": "value"} entries only.
     * Supports common JSON string escapes including unicode {@code \\u}XXXX.
     */
    static Map<String, String> parseFlatJsonObject(String raw) {
	Map<String, String> out = new HashMap<>();
	if(raw == null)
	    return(out);
	String text = stripBom(raw.trim());
	if(text.isEmpty() || text.charAt(0) != '{')
	    return(out);
	int i = 1, n = text.length();
	while(i < n) {
	    i = skipWs(text, i, n);
	    if(i >= n)
		break;
	    if(text.charAt(i) == '}')
		break;
	    ParseString ks = readJsonString(text, i, n);
	    if(ks == null)
		break;
	    i = skipWs(text, ks.next, n);
	    if(i >= n || text.charAt(i) != ':')
		break;
	    i = skipWs(text, i + 1, n);
	    ParseString vs = readJsonString(text, i, n);
	    if(vs == null)
		break;
	    out.put(ks.value, vs.value);
	    i = skipWs(text, vs.next, n);
	    if(i < n && text.charAt(i) == ',')
		i++;
	}
	return(out);
    }

    private static String stripBom(String s) {
	if(s.startsWith("\uFEFF"))
	    return(s.substring(1));
	return(s);
    }

    private static int skipWs(String s, int i, int n) {
	while(i < n && Character.isWhitespace(s.charAt(i)))
	    i++;
	return(i);
    }

    private static final class ParseString {
	final String value;
	final int next;

	ParseString(String value, int next) {
	    this.value = value;
	    this.next = next;
	}
    }

    private static ParseString readJsonString(String s, int i, int n) {
	if(i >= n || s.charAt(i) != '"')
	    return(null);
	StringBuilder sb = new StringBuilder();
	for(i = i + 1; i < n; ) {
	    char c = s.charAt(i);
	    if(c == '"')
		return(new ParseString(sb.toString(), i + 1));
	    if(c == '\\') {
		if(i + 1 >= n)
		    return(null);
		char e = s.charAt(i + 1);
		switch(e) {
		case 'n':
		    sb.append('\n');
		    break;
		case 'r':
		    sb.append('\r');
		    break;
		case 't':
		    sb.append('\t');
		    break;
		case '"':
		case '\\':
		    sb.append(e);
		    break;
		case 'u':
		    if(i + 6 > n)
			return(null);
		    try {
			int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
			sb.append((char)cp);
		    } catch(NumberFormatException ex) {
			return(null);
		    }
		    i += 4;
		    break;
		default:
		    sb.append(e);
		}
		i += 2;
		continue;
	    }
	    sb.append(c);
	    i++;
	}
	return(null);
    }

    public static Path defaultResOverrideDir(String lang) {
	String env = System.getenv("HAFEN_L10N_RES");
	if(env != null && !env.isEmpty())
	    return(Paths.get(env));
	String prop = System.getProperty("haven.l10n.res", null);
	if(prop != null && !prop.isEmpty())
	    return(Paths.get(prop, lang));
	String home = System.getProperty("user.home", ".");
	return(Paths.get(home, ".haven", "l10n", lang));
    }

    private static void installResourceOverrideIfNeeded() {
	if(resourceOverrideInstalled)
	    return;
	String lang = Utils.getpref("moon-lang", LANG_RU);
	if(!LANG_RU.equals(lang))
	    return;
	Path root = defaultResOverrideDir(LANG_RU);
	try {
	    if(Files.isDirectory(root)) {
		Resource.local().prepend(new Resource.FileSource(root));
		resourceOverrideInstalled = true;
	    }
	} catch(Exception e) {
	    new Warning(e, "l10n: resource override").issue();
	}
    }

    /** For tests / tooling: all keys currently loaded for Russian overlay. */
    public static Map<String, String> snapshotRu() {
	synchronized(overlayRu) {
	    return(Collections.unmodifiableMap(new HashMap<>(overlayRu)));
	}
    }

    /** Export every key name (one per line) for translators. */
    public static String dumpKeyList() {
	synchronized(overlayRu) {
	    java.util.TreeSet<String> keys = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	    keys.addAll(overlayRu.keySet());
	    keys.addAll(overlayEn.keySet());
	    StringBuilder sb = new StringBuilder();
	    for(String k : keys)
		sb.append(k).append('\n');
	    return(sb.toString());
	}
    }
}
