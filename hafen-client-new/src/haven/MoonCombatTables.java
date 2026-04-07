package haven;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * External combat table: card breach/IP/risk, synergy matrix entries, buff stacking rules.
 * Loaded from classpath {@code haven/res/moon-combat-data.json}.
 */
public final class MoonCombatTables {

    public static final class CardRow {
	public final String id;
	public final double breach;
	public final double ipPressure;
	public final double risk;
	public final String typeKey;
	public final List<String> tags;

	public CardRow(String id, double breach, double ipPressure, double risk, String typeKey, List<String> tags) {
	    this.id = id;
	    this.breach = breach;
	    this.ipPressure = ipPressure;
	    this.risk = risk;
	    this.typeKey = typeKey;
	    this.tags = tags;
	}
    }

    public static final class SynergyPairRow {
	public final String fromType;
	public final String toType;
	public final double bonus;
	public final int maxPlaysApart;

	public SynergyPairRow(String fromType, String toType, double bonus, int maxPlaysApart) {
	    this.fromType = fromType;
	    this.toType = toType;
	    this.bonus = bonus;
	    this.maxPlaysApart = maxPlaysApart;
	}
    }

    public static final class SynergyChainRow {
	public final List<String> types;
	public final double bonus;
	public final int maxPlaysSpan;

	public SynergyChainRow(List<String> types, double bonus, int maxPlaysSpan) {
	    this.types = types;
	    this.bonus = bonus;
	    this.maxPlaysSpan = maxPlaysSpan;
	}
    }

    public static final class BuffRuleRow {
	public final String matchSubstr;
	public final boolean selfScope;
	public final double mitigationFlat;
	public final double addBreach;
	public final double multBreach;
	public final String stackGroup;
	public final String stackMode;
	public final double stackCap;

	public BuffRuleRow(String matchSubstr, boolean selfScope, double mitigationFlat, double addBreach,
	    double multBreach, String stackGroup, String stackMode, double stackCap) {
	    this.matchSubstr = matchSubstr.toLowerCase(Locale.ROOT);
	    this.selfScope = selfScope;
	    this.mitigationFlat = mitigationFlat;
	    this.addBreach = addBreach;
	    this.multBreach = multBreach;
	    this.stackGroup = stackGroup;
	    this.stackMode = stackMode;
	    this.stackCap = stackCap;
	}
    }

    private static volatile List<CardRow> cards = Collections.emptyList();
    private static volatile List<SynergyPairRow> synergyPairs = Collections.emptyList();
    private static volatile List<SynergyChainRow> synergyChains = Collections.emptyList();
    private static volatile List<BuffRuleRow> buffRules = Collections.emptyList();
    private static String loadError = "";

    static {
	reloadFromClasspath();
    }

    public static String lastLoadError() {
	return loadError;
    }

    public static void reloadFromClasspath() {
	loadError = "";
	try(InputStream in = MoonCombatTables.class.getResourceAsStream("/haven/res/moon-combat-data.json")) {
	    if(in == null) {
		loadError = "missing /haven/res/moon-combat-data.json";
		clear();
		return;
	    }
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    byte[] buf = new byte[8192];
	    int n;
	    while((n = in.read(buf)) >= 0)
		bos.write(buf, 0, n);
	    String json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
	    reloadFromJsonString(json);
	} catch(Exception e) {
	    loadError = String.valueOf(e.getMessage());
	    clear();
	}
    }

    public static void reloadFromJsonString(String json) {
	loadError = "";
	try {
	    Object root = MoonJsonLite.parse(json);
	    Map<String, Object> m = MoonJsonLite.asMap(root);
	    List<CardRow> cList = new ArrayList<>();
	    Object cArr = m.get("cards");
	    if(cArr instanceof List) {
		for(Object o : (List<?>)cArr) {
		    if(!(o instanceof Map))
			continue;
		    @SuppressWarnings("unchecked")
		    Map<String, Object> cm = (Map<String, Object>) o;
		    String id = MoonJsonLite.asString(cm.get("id"));
		    if(id.isEmpty())
			continue;
		    double breach = MoonJsonLite.asDouble(cm.get("breach"), 0);
		    double ipP = MoonJsonLite.asDouble(cm.get("ipPressure"), breach * 0.5);
		    double risk = MoonJsonLite.asDouble(cm.get("risk"), 0.1);
		    String type = MoonJsonLite.asString(cm.get("type"));
		    if(type.isEmpty())
			type = "attack";
		    List<String> tags = new ArrayList<>();
		    Object t = cm.get("tags");
		    if(t instanceof List) {
			for(Object x : (List<?>)t)
			    tags.add(MoonJsonLite.asString(x));
		    }
		    cList.add(new CardRow(id, breach, ipP, risk, type, Collections.unmodifiableList(tags)));
		}
	    }
	    List<SynergyPairRow> sp = new ArrayList<>();
	    Object sArr = m.get("synergyPairs");
	    if(sArr instanceof List) {
		for(Object o : (List<?>)sArr) {
		    if(!(o instanceof Map))
			continue;
		    @SuppressWarnings("unchecked")
		    Map<String, Object> sm = (Map<String, Object>) o;
		    sp.add(new SynergyPairRow(
			MoonJsonLite.asString(sm.get("fromType")).toLowerCase(Locale.ROOT),
			MoonJsonLite.asString(sm.get("toType")).toLowerCase(Locale.ROOT),
			MoonJsonLite.asDouble(sm.get("bonus"), 0),
			MoonJsonLite.asInt(sm.get("maxPlaysApart"), 1)));
		}
	    }
	    List<SynergyChainRow> sc = new ArrayList<>();
	    Object chArr = m.get("synergyChains");
	    if(chArr instanceof List) {
		for(Object o : (List<?>)chArr) {
		    if(!(o instanceof Map))
			continue;
		    @SuppressWarnings("unchecked")
		    Map<String, Object> chm = (Map<String, Object>) o;
		    List<String> types = new ArrayList<>();
		    Object ta = chm.get("types");
		    if(ta instanceof List<?> tl) {
			for(Object x : tl)
			    types.add(MoonJsonLite.asString(x).toLowerCase(Locale.ROOT));
		    }
		    if(types.size() >= 2)
			sc.add(new SynergyChainRow(types,
			    MoonJsonLite.asDouble(chm.get("bonus"), 0),
			    MoonJsonLite.asInt(chm.get("maxPlaysSpan"), types.size() + 1)));
		}
	    }
	    List<BuffRuleRow> br = new ArrayList<>();
	    Object bArr = m.get("buffRules");
	    if(bArr instanceof List<?> rawB) {
		for(Object o : rawB) {
		    if(!(o instanceof Map))
			continue;
		    @SuppressWarnings("unchecked")
		    Map<String, Object> bm = (Map<String, Object>) o;
		    String match = MoonJsonLite.asString(bm.get("matchSubstr"));
		    if(match.isEmpty())
			continue;
		    String scope = MoonJsonLite.asString(bm.get("scope")).toLowerCase(Locale.ROOT);
		    boolean self = scope.equals("self");
		    br.add(new BuffRuleRow(match, self,
			MoonJsonLite.asDouble(bm.get("mitigationFlat"), 0),
			MoonJsonLite.asDouble(bm.get("addBreach"), 0),
			MoonJsonLite.asDouble(bm.get("multBreach"), 0),
			MoonJsonLite.asString(bm.get("stackGroup")),
			MoonJsonLite.asString(bm.get("stackMode")),
			MoonJsonLite.asDouble(bm.get("stackCap"), 1)));
		}
	    }
	    cards = Collections.unmodifiableList(cList);
	    synergyPairs = Collections.unmodifiableList(sp);
	    synergyChains = Collections.unmodifiableList(sc);
	    buffRules = Collections.unmodifiableList(br);
	} catch(MoonJsonLite.MoonJsonParseException e) {
	    loadError = e.getMessage();
	    clear();
	}
    }

    private static void clear() {
	cards = List.of();
	synergyPairs = List.of();
	synergyChains = List.of();
	buffRules = List.of();
    }

    /**
     * Longest {@code card.id} that is a prefix of {@code resName} (case-insensitive), or contains match.
     */
    public static CardRow matchCard(String resName) {
	if(resName == null || resName.isEmpty())
	    return null;
	String rn = resName.toLowerCase(Locale.ROOT);
	CardRow best = null;
	int bestLen = -1;
	for(CardRow c : cards) {
	    String id = c.id.toLowerCase(Locale.ROOT);
	    if(rn.equals(id) || rn.startsWith(id) || rn.contains(id)) {
		if(id.length() > bestLen) {
		    bestLen = id.length();
		    best = c;
		}
	    }
	}
	return best;
    }

    public static List<SynergyPairRow> synergyPairs() {
	return synergyPairs;
    }

    public static List<SynergyChainRow> synergyChains() {
	return synergyChains;
    }

    public static List<BuffRuleRow> buffRules() {
	return buffRules;
    }
}
