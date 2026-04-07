package haven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced card combat reasoning: data tables ({@link MoonCombatTables}), buff rules, opponent profiling,
 * synergy chains, optional shallow expectiminimax ({@link MoonCombatMinimax}).
 * <p>
 * Wiki-shaped buckets (Ring of Brodgar / H&amp;H): maneuvers (stances) → opening; restorations → defense;
 * attacks → aggression; initiative/mobility moves + specials → utility. Order in {@link #classify} matters.
 */
public final class MoonCombatAI {

    private MoonCombatAI() {}

    /** Maneuvers / stances — checked before generic defense so e.g. Parry stance is not misread as defense. */
    private static final String[] MANEUVER_KEYS = {
	"bloodlust", "chin up", "combat meditation", "death or glory", "oak stance",
	"shield up", "to arms", "parry",
	"манёвр", "маневр", "стойка"
    };
    /** Restoration moves (clear / reduce openings) — before generic defense keys. */
    private static final String[] RESTORATION_KEYS = {
	"artful evasion", "feigned dodge", "quick dodge", "regain composure", "sidestep",
	"watch its moves", "yield ground", "zig-zag ruse", "zig zag ruse", "zig-zag", "zig zag",
	"jump", "qdodge"
    };
    /** Initiative, mobility, control — engine cards (not stances). */
    private static final String[] MOVE_ENGINE_KEYS = {
	"dash", "take aim", "think", "call down the thunder", "opportunity knocks",
	"feign flight", "float like a butterfly", "push the advantage", "seize the day",
	"slide", "throw sand", "low blow",
	"прицел", "осмотр", "песок", "уворот"
    };
    private static final String[] SPECIAL_MOVE_KEYS = {
	"battle cry", "consume the flames", "evil eye",
	"боевой клич", "злой глаз"
    };
    /** Named attacks from wiki (checked before short generic agg tokens like "blood"). */
    private static final String[] NAMED_ATTACK_KEYS = {
	"chop", "cleave", "full circle", "go for the jugular", "haymaker", "kick",
	"knock its teeth out", "left hook", "punch", "punch 'em both",
	"punch em both", "quick barrage", "raven's bite", "ravens bite", "rip apart",
	"sideswipe", "steal thunder", "sting", "storm of swords", "takedown", "uppercut",
	"go for the eyes", "circle strike"
    };
    private static final String[] DEF_KEYS = {
	"block", "dodge", "shield", "ward", "guard", "slip", "weave",
	"recover", "feint", "brace", "cover", "deflect", "riposte", "bind",
	"уклон", "блок", "парир", "защит", "защита", "уклонен"
    };
    private static final String[] AGG_KEYS = {
	"bash", "charge", "blood", "cleave", "thrust", "strike", "punch", "kick",
	"slash", "swing", "beat", "fury", "pain", "hack", "stab", "pommel",
	"flurry", "lunge", "drive", "break", "crush", "wild",
	"удар", "руб", "тыч", "пин", "бей", "рубит"
    };
    private static final String[] OPEN_KEYS = {
	"opening", "oath", "rally", "arms", "war", "battle", "duel",
	"stance", "ready", "engage", "вызов", "призыв"
    };
    private static final String[] UTIL_KEYS = {
	"heal", "focus", "medit", "buff", "drink", "restore", "recover sta",
	"исцел", "лечен", "фокус", "энерг"
    };
    /** Self buff lines: active maneuver / stance (re-playing wastes a slot). */
    private static final String[] STANCE_BUFF_KEYS = {
	"stance", "stances", "combat stance", "battle stance", "war stance", "guard stance",
	"readiness", "to arms", "opening buff", "maneuver",
	"bloodlust", "chin up", "combat meditation", "death or glory", "oak stance",
	"shield up", "parry",
	"стойк", "боевой", "боевая стойка", "манёвр", "маневр"
    };

    public enum CardArchetype {
	DEFENSE, AGGRESSION, OPENING, UTILITY, UNKNOWN
    }

    private static boolean anyKey(String text, String[] keys) {
	if(text == null || text.isEmpty())
	    return false;
	for(String k : keys) {
	    if(text.contains(k))
		return true;
	}
	return false;
    }

    public static CardArchetype classify(String text) {
	if(text == null)
	    text = "";
	if(anyKey(text, MANEUVER_KEYS))
	    return CardArchetype.OPENING;
	if(anyKey(text, RESTORATION_KEYS))
	    return CardArchetype.DEFENSE;
	if(anyKey(text, SPECIAL_MOVE_KEYS))
	    return CardArchetype.UTILITY;
	if(anyKey(text, MOVE_ENGINE_KEYS))
	    return CardArchetype.UTILITY;
	if(anyKey(text, NAMED_ATTACK_KEYS))
	    return CardArchetype.AGGRESSION;
	if(anyKey(text, DEF_KEYS))
	    return CardArchetype.DEFENSE;
	if(anyKey(text, AGG_KEYS))
	    return CardArchetype.AGGRESSION;
	if(anyKey(text, OPEN_KEYS))
	    return CardArchetype.OPENING;
	if(anyKey(text, UTIL_KEYS))
	    return CardArchetype.UTILITY;
	return CardArchetype.UNKNOWN;
    }

    /** Table {@code typeKey} if present, else keyword {@link #classify} on card text. */
    static CardArchetype resolvedArchetype(MoonFightCombatContext.HandCard c) {
	MoonCombatTables.CardRow row = MoonCombatTables.matchCard(c.resName);
	if(row != null && row.typeKey != null && !row.typeKey.isEmpty()) {
	    String t = row.typeKey.toLowerCase(Locale.ROOT);
	    return switch(t) {
	    case "attack", "aggro", "aggression" -> CardArchetype.AGGRESSION;
	    case "defense", "defence", "restoration" -> CardArchetype.DEFENSE;
	    case "opening", "open", "maneuver" -> CardArchetype.OPENING;
	    case "utility", "util", "move" -> CardArchetype.UTILITY;
	    default -> classify(c.text);
	    };
	}
	return classify(c.text);
    }

    static int countReadyOpeningArchetypeInHand(MoonFightCombatContext ctx) {
	if(ctx == null || ctx.hand == null)
	    return 0;
	int n = 0;
	for(MoonFightCombatContext.HandCard c : ctx.hand) {
	    if(c.ready && resolvedArchetype(c) == CardArchetype.OPENING)
		n++;
	}
	return n;
    }

    static int countReadyCardsInHand(MoonFightCombatContext ctx) {
	if(ctx == null || ctx.hand == null)
	    return 0;
	int n = 0;
	for(MoonFightCombatContext.HandCard c : ctx.hand) {
	    if(c.ready)
		n++;
	}
	return n;
    }

    private static boolean stanceLikeBuffActive(MoonFightCombatContext ctx) {
	if(ctx == null)
	    return false;
	for(MoonFightCombatContext.BuffSnapshot bs : ctx.selfBuffSnapshots) {
	    if(bs != null && bs.line != null && anyKey(bs.line.toLowerCase(Locale.ROOT), STANCE_BUFF_KEYS))
		return true;
	}
	for(String line : ctx.selfBuffSummaries) {
	    if(line != null && anyKey(line.toLowerCase(Locale.ROOT), STANCE_BUFF_KEYS))
		return true;
	}
	return false;
    }

    /**
     * Negative adjustment when playing an opening/stance card would not help (stance already up,
     * or single-opening deck already used one this fight). Used by scorer, minimax, and legacy picker.
     */
    static double openingRedundancyPenalty(MoonFightCombatContext ctx, MoonCombatBrainState brain,
	MoonFightCombatContext.HandCard card) {
	if(card == null || resolvedArchetype(card) != CardArchetype.OPENING)
	    return 0;
	boolean multiOpening = brain != null && !brain.likelySingleOpeningDeck();
	double p = 0;
	if(stanceLikeBuffActive(ctx))
	    p -= multiOpening ? 48 : 170;
	if(brain != null) {
	    if(!brain.selfTypeHistoryView().isEmpty() && "opening".equals(brain.selfTypeHistoryView().peekFirst()))
		p -= multiOpening ? 40 : 105;
	    if(brain.likelySingleOpeningDeck() && brain.selfOpeningPlaysThisFight() >= 1)
		p -= 150;
	}
	return p;
    }

    /**
     * If {@link MoonConfig#combatBotTrustHints} points at a redundant maneuver/stance, ignore the hint and
     * let the scorer / minimax pick (fixes log spam e.g. {@code paginae/atk/bloodlust} every tick).
     */
    static boolean trustHintOverriddenForSlot(Fightsess fs, int slot, MoonFightCombatContext ctx,
	MoonCombatBrainState brain, double now) {
	if(fs == null || ctx == null || brain == null || slot < 0)
	    return false;
	MoonFightCombatContext.HandCard hc = null;
	for(MoonFightCombatContext.HandCard c : ctx.hand) {
	    if(c.slot == slot && c.ready)
		hc = c;
	}
	if(hc == null)
	    return false;
	if(openingRedundancyPenalty(ctx, brain, hc) < -70)
	    return true;
	if(countReadyCardsInHand(ctx) >= 2 && brain.serverHintLikelyStuckOn(slot))
	    return true;
	return false;
    }

    /** Maps table JSON type string + archetype to synergy keys. */
    public static String cardTypeKey(MoonFightCombatContext.HandCard c) {
	MoonCombatTables.CardRow r = MoonCombatTables.matchCard(c.resName);
	if(r != null && r.typeKey != null && !r.typeKey.isEmpty())
	    return r.typeKey.toLowerCase(Locale.ROOT);
	return archetypeToTableKey(classify(c.text));
    }

    public static String archetypeToTableKey(CardArchetype a) {
	return switch(a) {
	case AGGRESSION -> "attack";
	case DEFENSE -> "defense";
	case OPENING -> "opening";
	case UTILITY -> "utility";
	default -> "utility";
	};
    }

    public static double aggressionTilt() {
	return (MoonConfig.combatBotAggression - 50) / 50.0;
    }

    public static double cardRisk(MoonFightCombatContext.HandCard c) {
	MoonCombatTables.CardRow r = MoonCombatTables.matchCard(c.resName);
	if(r != null)
	    return r.risk;
	return switch(classify(c.text)) {
	case AGGRESSION -> 0.14;
	case OPENING -> 0.1;
	case DEFENSE -> 0.05;
	default -> 0.08;
	};
    }

    /** Expected change to {@code ipSelf - ipOpp} after playing this card (scaled heuristic). */
    public static int ipDeltaEstimateOurCard(MoonFightCombatContext.HandCard c, MoonFightCombatContext ctx) {
	MoonCombatTables.CardRow r = MoonCombatTables.matchCard(c.resName);
	double p = r != null ? r.ipPressure : ipPressureFallback(classify(c.text));
	p *= MoonBuffManager.selfBreachMultiplier(ctx);
	p *= (1.0 - MoonBuffManager.oppMitigationFromRules(ctx));
	p *= 1.0 + 0.12 * aggressionTilt();
	return (int)Math.round(Utils.clip(p * 4.0, -2, 6));
    }

    private static double ipPressureFallback(CardArchetype a) {
	return switch(a) {
	case AGGRESSION -> 0.55;
	case OPENING -> 0.4;
	case DEFENSE -> 0.15;
	case UTILITY -> 0.36;
	default -> 0.25;
	};
    }

    /** Opponent archetype index: 0 def, 1 agg, 2 open — estimated IP swing against us. */
    public static int oppSwingEstimate(int oppArchetypeIdx) {
	return switch(oppArchetypeIdx) {
	case 1 -> 2;
	case 2 -> 1;
	default -> 0;
	};
    }

    public static final class MoonCardEvaluator {
	public static double alphaRole = 1.0;
	public static double alphaBreach = 1.0;

	public static double score(MoonFightCombatContext.HandCard card, MoonFightCombatContext ctx,
	    MoonCombatBrainState brain) {
	    if(!card.ready)
		return Double.NEGATIVE_INFINITY;
	    CardArchetype a = resolvedArchetype(card);

	    int d = ctx.ipDelta;
	    double tilt = aggressionTilt();
	    double needDef = d < -1 ? 1.0 : (d > 1 ? 0.25 - 0.1 * tilt : 0.55 - 0.15 * tilt);
	    double needAgg = d > 1 ? 1.0 : (d < -1 ? 0.2 + 0.1 * tilt : 0.5 + 0.2 * tilt);
	    double needOpen = 0.35 + 0.15 * Math.tanh(d * 0.15) + 0.08 * tilt;
	    double needUtil = 0.28 + (d < -2 ? 0.15 : 0);

	    double r = switch(a) {
	    case DEFENSE -> needDef;
	    case AGGRESSION -> needAgg;
	    case OPENING -> needOpen;
	    case UTILITY -> needUtil;
	    default -> 0.3;
	    };

	    double breach = MoonBreachCalculator.effectiveBreach(card, ctx);
	    double base = alphaRole * r * 100 + alphaBreach * breach - card.slot * 0.02;
	    return base + openingRedundancyPenalty(ctx, brain, card);
	}

	public static double score(MoonFightCombatContext.HandCard card, MoonFightCombatContext ctx) {
	    return score(card, ctx, null);
	}
    }

    public static final class MoonSynergyAnalyzer {
	public static double pairFallbackBonus = 1.0;
	public static double chainFallbackBonus = 1.0;

	public static double bonusFromHistory(MoonCombatBrainState brain, MoonFightCombatContext.HandCard candidate) {
	    if(brain == null)
		return 0;
	    String cand = cardTypeKey(candidate);
	    List<String> hist = new ArrayList<>(brain.selfTypeHistoryView());
	    double bonus = 0;
	    for(MoonCombatTables.SynergyPairRow pr : MoonCombatTables.synergyPairs()) {
		if(!pr.toType.equals(cand))
		    continue;
		if(!hist.isEmpty() && pr.fromType.equals(hist.get(0))) {
		    bonus += pr.bonus * pairFallbackBonus;
		} else if(pr.maxPlaysApart >= 2 && hist.size() >= 2 && pr.fromType.equals(hist.get(1))) {
		    bonus += pr.bonus * 0.82 * pairFallbackBonus;
		}
	    }
	    for(MoonCombatTables.SynergyChainRow ch : MoonCombatTables.synergyChains()) {
		if(ch.types.size() < 2)
		    continue;
		if(!ch.types.get(ch.types.size() - 1).equals(cand))
		    continue;
		if(!chainSuffixMatches(hist, ch.types))
		    continue;
		if(hist.size() + 1 > ch.maxPlaysSpan)
		    continue;
		bonus += ch.bonus * chainFallbackBonus;
	    }
	    /* Keyword fallback when table has no pair rows */
	    if(bonus < 1e-6 && MoonCombatTables.synergyPairs().isEmpty() && !hist.isEmpty()) {
		String last = hist.get(0);
		String cur = cand;
		if("opening".equals(last) && "attack".equals(cur))
		    bonus += 35;
		else if("attack".equals(last) && "attack".equals(cur))
		    bonus += 12;
	    }
	    return bonus;
	}

	/**
	 * {@code chain} is play order [oldest ... newest candidate]. {@code hist} is newest-first self types.
	 */
	private static boolean chainSuffixMatches(List<String> hist, List<String> chain) {
	    int L = chain.size();
	    if(hist.size() < L - 1)
		return false;
	    for(int k = 0; k < L - 1; k++) {
		String expect = chain.get(L - 2 - k);
		if(!expect.equals(hist.get(k)))
		    return false;
	    }
	    return true;
	}
    }

    public static final class MoonBreachCalculator {
	public static double W_AGG = 1.0, W_OPEN = 0.75, W_DEF = 0.35, W_UNK = 0.5, W_UTIL = 0.4;

	public static double effectiveBreach(MoonFightCombatContext.HandCard card, MoonFightCombatContext ctx) {
	    MoonCombatTables.CardRow r = MoonCombatTables.matchCard(card.resName);
	    double base;
	    if(r != null)
		base = r.breach;
	    else {
		CardArchetype a = classify(card.text);
		base = switch(a) {
		case AGGRESSION -> W_AGG;
		case OPENING -> W_OPEN;
		case DEFENSE -> W_DEF;
		case UTILITY -> W_UTIL;
		default -> W_UNK;
		};
	    }
	    base += MoonBuffManager.additiveSelfBreach(ctx);
	    double mult = MoonBuffManager.selfBreachMultiplier(ctx);
	    double mit = MoonBuffManager.oppMitigationFromRules(ctx);
	    return base * mult * (1.0 - mit);
	}

	public static double expectedPressure(MoonFightCombatContext.HandCard card, MoonFightCombatContext ctx) {
	    return effectiveBreach(card, ctx) * (1.0 + 0.08 * Math.max(0, ctx.ipDelta));
	}
    }

    public static final class MoonBuffManager {
	private static final Pattern PCT = Pattern.compile("([+-]?\\d+)\\s*%");

	public static double parseAdditiveSum(Iterable<String> lines) {
	    double sum = 0;
	    for(String line : lines) {
		if(line == null)
		    continue;
		Matcher m = PCT.matcher(line.toLowerCase(Locale.ROOT));
		while(m.find()) {
		    try {
			sum += Integer.parseInt(m.group(1)) * 0.01;
		    } catch(NumberFormatException ignored) {}
		}
	    }
	    return sum;
	}

	public static double additiveSelfBreach(MoonFightCombatContext ctx) {
	    double add = 0;
	    Map<String, Double> groups = new HashMap<>();
	    applyRulesToSnapshots(ctx.selfBuffSnapshots, true, groups, null);
	    for(double v : groups.values())
		add += v;
	    return add;
	}

	public static double selfBreachMultiplier(MoonFightCombatContext ctx) {
	    double m = 1.0;
	    Map<String, Double> multGroups = new HashMap<>();
	    for(MoonFightCombatContext.BuffSnapshot bs : ctx.selfBuffSnapshots) {
		String ln = bs.line.toLowerCase(Locale.ROOT);
		for(MoonCombatTables.BuffRuleRow rule : MoonCombatTables.buffRules()) {
		    if(!rule.selfScope || rule.multBreach <= 0)
			continue;
		    if(!ln.contains(rule.matchSubstr))
			continue;
		    String g = rule.stackGroup.isEmpty() ? rule.matchSubstr : rule.stackGroup;
		    if("refresh_max".equals(rule.stackMode))
			multGroups.merge(g, rule.multBreach, Math::max);
		    else
			multGroups.merge(g, rule.multBreach, (a, b) -> a * b);
		}
	    }
	    for(double x : multGroups.values())
		m *= x;
	    double pct = 0;
	    for(MoonFightCombatContext.BuffSnapshot bs : ctx.selfBuffSnapshots) {
		String s = bs.line.toLowerCase(Locale.ROOT);
		if(s.contains("damage") || s.contains("attack") || s.contains("урон") || s.contains("атак"))
		    pct += parseAdditiveSum(List.of(bs.line));
	    }
	    if(pct <= 0)
		pct = parseAdditiveSum(ctx.selfBuffSummaries) * 0.4;
	    return Utils.clip(m * (1.0 + pct), 0.45, 2.2);
	}

	public static double oppMitigationFromRules(MoonFightCombatContext ctx) {
	    Map<String, Double> mit = new HashMap<>();
	    applyRulesToSnapshots(ctx.oppBuffSnapshots, false, null, mit);
	    double sum = 0;
	    for(double v : mit.values())
		sum += v;
	    return Utils.clip(sum, 0, 0.65);
	}

	private static void applyRulesToSnapshots(List<MoonFightCombatContext.BuffSnapshot> snaps, boolean selfScope,
	    Map<String, Double> selfAdd, Map<String, Double> oppMit) {
	    for(MoonFightCombatContext.BuffSnapshot bs : snaps) {
		String ln = bs.line.toLowerCase(Locale.ROOT);
		for(MoonCombatTables.BuffRuleRow rule : MoonCombatTables.buffRules()) {
		    if(rule.selfScope != selfScope)
			continue;
		    if(!ln.contains(rule.matchSubstr))
			continue;
		    String g = rule.stackGroup.isEmpty() ? rule.matchSubstr : rule.stackGroup;
		    if(selfScope && selfAdd != null && rule.addBreach != 0) {
			if("add_cap".equals(rule.stackMode))
			    selfAdd.merge(g, rule.addBreach, (a, b) -> Math.min(rule.stackCap, a + b));
			else
			    selfAdd.merge(g, rule.addBreach, Double::sum);
		    }
		    if(!selfScope && oppMit != null && rule.mitigationFlat > 0) {
			if("add_cap".equals(rule.stackMode))
			    oppMit.merge(g, rule.mitigationFlat, (a, b) -> Math.min(rule.stackCap, a + b));
			else if("refresh_max".equals(rule.stackMode))
			    oppMit.merge(g, rule.mitigationFlat, Math::max);
			else
			    oppMit.merge(g, rule.mitigationFlat, Double::sum);
		    }
		}
	    }
	}

	/** Remaining fraction 0..1 from UI meters; rough ETA = fraction * nominal duration sec. */
	public static double predictBuffRemainingScore(MoonFightCombatContext.BuffSnapshot bs, double nominalDurationSec) {
	    if(bs.ameter != null)
		return Utils.clip(bs.ameter, 0, 1);
	    if(bs.cmeter != null)
		return Utils.clip(bs.cmeter, 0, 1);
	    return 0.5;
	}
    }

    public static final class MoonOpponentPredictor {
	public static double priorAggressive(MoonFightCombatContext ctx) {
	    double p = 0.45 + 0.12 * aggressionTilt();
	    if(ctx.ipDelta < -2)
		p += 0.18;
	    else if(ctx.ipDelta > 2)
		p -= 0.12;
	    String ot = !ctx.lastOppCardText.isEmpty() ? ctx.lastOppCardText : ctx.lastOppResName.toLowerCase(Locale.ROOT);
	    if(anyKey(ot, DEF_KEYS))
		p -= 0.16;
	    if(anyKey(ot, AGG_KEYS))
		p += 0.14;
	    return Utils.clip(p, 0.08, 0.92);
	}

	/** Mixture for def / agg / opening (utility rolled into opening). */
	public static double[] archetypeMix(MoonFightCombatContext ctx, MoonCombatBrainState brain) {
	    double pAgg = priorAggressive(ctx);
	    double pDef = (1 - pAgg) * 0.48;
	    double pOpen = (1 - pAgg) * 0.52;
	    long gob = brain != null ? brain.activeGob() : -1;
	    if(gob >= 0 && brain != null) {
		MoonCombatBrainState.OpponentProfile pf = brain.profile(gob);
		CardArchetype last = classify(!ctx.lastOppCardText.isEmpty() ? ctx.lastOppCardText : ctx.lastOppResName);
		double[] mk = pf.nextArchetypeDistribution(last == CardArchetype.UNKNOWN ? CardArchetype.AGGRESSION : last);
		double blend = 0.55;
		pAgg = blend * pAgg + (1 - blend) * (mk[1]);
		pDef = blend * pDef + (1 - blend) * (mk[0]);
		pOpen = blend * pOpen + (1 - blend) * (mk[2] + mk[3]);
	    }
	    double s = pAgg + pDef + pOpen;
	    if(s > 1e-6) {
		pAgg /= s;
		pDef /= s;
		pOpen /= s;
	    }
	    return new double[] {pDef, pAgg, pOpen};
	}

	public static double counterDefenseBonus(MoonFightCombatContext.HandCard card, MoonFightCombatContext ctx,
	    MoonCombatBrainState brain) {
	    if(classify(card.text) != CardArchetype.DEFENSE)
		return 0;
	    double[] mix = archetypeMix(ctx, brain);
	    return mix[1] * 42;
	}
    }

    public static final class MoonCombatDecisionMaker {
	public static double wSynergy = 1.0;
	public static double wCounter = 1.0;
	public static double wPressure = 18.0;

	public static int pickSlot(MoonFightCombatContext ctx, MoonCombatBrainState brain) {
	    MoonFightCombatContext.HandCard best = null;
	    double bestScore = Double.NEGATIVE_INFINITY;
	    for(MoonFightCombatContext.HandCard c : ctx.hand) {
		if(!c.ready)
		    continue;
		double s = MoonCardEvaluator.score(c, ctx, brain);
		s += wSynergy * MoonSynergyAnalyzer.bonusFromHistory(brain, c);
		s += wPressure * MoonBreachCalculator.expectedPressure(c, ctx);
		s += wCounter * MoonOpponentPredictor.counterDefenseBonus(c, ctx, brain);
		if(s > bestScore) {
		    bestScore = s;
		    best = c;
		}
	    }
	    return best == null ? -1 : best.slot;
	}

	public static int pickSlotRespectingHints(Fightsess fs, MoonFightCombatContext ctx, MoonCombatBrainState brain,
	    double now) {
	    int n = fs.actions.length;
	    if(MoonConfig.combatBotTrustHints && fs.use >= 0 && fs.use < n) {
		Fightsess.Action a = fs.actions[fs.use];
		if(a != null && !(a.ct > 0 && now < a.ct)
		    && !trustHintOverriddenForSlot(fs, fs.use, ctx, brain, now))
		    return fs.use;
	    }
	    if(MoonConfig.combatBotTrustHints && fs.useb >= 0 && fs.useb < n) {
		Fightsess.Action b = fs.actions[fs.useb];
		if(b != null && !(b.ct > 0 && now < b.ct)
		    && !trustHintOverriddenForSlot(fs, fs.useb, ctx, brain, now))
		    return fs.useb;
	    }
	    int mode = MoonConfig.combatBotBrainMode;
	    if(mode >= 2) {
		int d = Math.max(2, Math.min(3, MoonConfig.combatBotSearchDepth));
		return MoonCombatMinimax.bestSlot(ctx, brain, d);
	    }
	    return pickSlot(ctx, brain);
	}
    }
}
