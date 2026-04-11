package haven;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import managers.combat.CombatManager;

/**
 * Deck-agnostic combat engine built around a compact combat-state snapshot and
 * per-card effect vectors inferred from the currently equipped deck.
 *
 * <p>The goal is not exact game-theory simulation; it is fast, low-latency,
 * deck-independent move selection with lightweight online adaptation.
 */
public final class MoonCombatEngine {
    private MoonCombatEngine() {}

    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern IP_COST_PATTERN = Pattern.compile("(?:cost|costs|initiative(?:\\s+points?)?|ip)\\D{0,10}(\\d+)");
    private static final int MAX_REPLAY = 256;
    private static final double REPLAY_OBSERVE_DELAY = 0.45;
    private static final double ENGINE_TICK_SEC = 0.10;

    private static final String[] DEFENSE_CARDS = {
	"artful evasion", "feigned dodge", "quick dodge", "regain composure", "sidestep",
	"watch its moves", "yield ground", "zig-zag ruse", "zig zag ruse", "jump", "qdodge"
    };
    private static final String[] TACTICAL_CARDS = {
	"dash", "take aim", "think", "call down the thunder", "opportunity knocks",
	"float like a butterfly", "push the advantage", "seize the day", "slide",
	"throw sand", "low blow", "battle cry", "evil eye"
    };
    private static final String[] ESCAPE_CARDS = {
	"yield ground", "jump", "dash", "slide", "feign flight", "zig-zag ruse", "zig zag ruse"
    };
    private static final String[] FINISHER_CARDS = {
	"cleave", "full circle", "go for the jugular", "haymaker", "knock its teeth out",
	"quick barrage", "rip apart", "storm of swords", "takedown", "uppercut",
	"go for the eyes", "circle strike", "steal thunder"
    };
    private static final String[] OFFENSIVE_STANCES = {
	"bloodlust", "death or glory", "combat meditation", "to arms", "chin up"
    };
    private static final String[] DEFENSIVE_STANCES = {
	"oak stance", "shield up", "parry"
    };
    private static final String[] STANCE_NAMES = {
	"bloodlust", "chin up", "combat meditation", "death or glory",
	"oak stance", "shield up", "to arms", "parry"
    };

    private static double lastDecisionAt = -1e9;
    private static String lastStatus = "idle";
    private static PendingExperience pending;
    private static final ExperienceReplay replay = new ExperienceReplay(MAX_REPLAY);
    private static String lastPlayedActionSig = "";
    private static String lastPlayedStateSig = "";
    private static String lastObservedStateSig = "";
    private static int repeatedSameActionOnState = 0;
    private static int staleStateTicks = 0;
    private static double lastMeaningfulStateAt = -1e9;

    public enum OpeningType {
	STRIKING("striking", "strike"),
	BLUNT("blunt"),
	PIERCING("piercing", "pierce"),
	SLASHING("slashing", "slash"),
	REELING("reeling", "reel");

	private final String[] tokens;

	OpeningType(String... tokens) {
	    this.tokens = tokens;
	}

	boolean matches(String text) {
	    if(text == null || text.isEmpty())
		return false;
	    for(String token : tokens) {
		if(text.contains(token))
		    return true;
	    }
	    return false;
	}
    }

    public static final class ActiveStance {
	public final String resName;
	public final String label;
	public final double offenseBias;
	public final double defenseBias;
	public final double mobilityBias;

	public ActiveStance(String resName, String label, double offenseBias, double defenseBias, double mobilityBias) {
	    this.resName = resName != null ? resName : "";
	    this.label = label != null ? label : "";
	    this.offenseBias = offenseBias;
	    this.defenseBias = defenseBias;
	    this.mobilityBias = mobilityBias;
	}
    }

    public static final class CombatantState {
	public final double weight;
	public final EnumMap<OpeningType, Double> openings;
	public final double maxOpening;
	public final double totalOpening;
	public final double advantage;
	public final int ip;
	public final double stamina;
	public final double health;
	public final ActiveStance stance;

	public CombatantState(double weight, EnumMap<OpeningType, Double> openings, double advantage, int ip,
	    double stamina, double health, ActiveStance stance) {
	    this.weight = weight;
	    this.openings = immutableOpenings(openings);
	    this.maxOpening = maxOpening(this.openings);
	    this.totalOpening = totalOpening(this.openings);
	    this.advantage = advantage;
	    this.ip = ip;
	    this.stamina = stamina;
	    this.health = health;
	    this.stance = stance;
	}

	public double opening(OpeningType type) {
	    return openings.getOrDefault(type, 0.0);
	}

	public OpeningType highestOpeningType() {
	    OpeningType best = OpeningType.STRIKING;
	    double bestV = -1;
	    for(OpeningType type : OpeningType.values()) {
		double v = openings.getOrDefault(type, 0.0);
		if(v > bestV) {
		    bestV = v;
		    best = type;
		}
	    }
	    return best;
	}
    }

    public static final class CombatState {
	public final CombatantState self;
	public final CombatantState opponent;
	public final double now;
	public final long targetGobId;

	public CombatState(CombatantState self, CombatantState opponent, double now, long targetGobId) {
	    this.self = self;
	    this.opponent = opponent;
	    this.now = now;
	    this.targetGobId = targetGobId;
	}
    }

    public static final class ActionEffectVector {
	public final EnumMap<OpeningType, Double> oppOpeningDelta = zeroOpenings();
	public final EnumMap<OpeningType, Double> selfOpeningDelta = zeroOpenings();
	public double advantageDelta;
	public int ipDeltaSelf;
	public int ipDeltaOpp;
	public double staminaDeltaSelf;
	public double damagePotential;
	public double mobilityGain;
	public double escapeValue;
	public double chaseBreakValue;
	public double stanceValue;
	public double confidence = 0.35;

	public double defensiveValue() {
	    double score = 0.0;
	    for(double v : selfOpeningDelta.values()) {
		if(v < 0)
		    score += -v;
	    }
	    return score;
	}

	public double tacticalValue() {
	    return (advantageDelta * 10.0) + (ipDeltaSelf * 5.0) - (ipDeltaOpp * 3.0)
		+ (mobilityGain * 8.0) + (escapeValue * 6.0) + (chaseBreakValue * 4.0);
	}

	public double closingValue() {
	    return (damagePotential * 100.0) + sumPositive(oppOpeningDelta) * 0.35;
	}
    }

    public static final class ActionProfile {
	public final int slot;
	public final String resName;
	public final String label;
	public final MoonCombatAI.CardArchetype archetype;
	public final int ipCost;
	public final double cooldown;
	public final ActionEffectVector effect;

	public ActionProfile(int slot, String resName, String label, MoonCombatAI.CardArchetype archetype, int ipCost,
	    double cooldown, ActionEffectVector effect) {
	    this.slot = slot;
	    this.resName = resName != null ? resName : "";
	    this.label = label != null ? label : "";
	    this.archetype = archetype;
	    this.ipCost = ipCost;
	    this.cooldown = cooldown;
	    this.effect = effect;
	}

	public String signature() {
	    return !resName.isEmpty() ? resName : label;
	}

	public boolean isDefensive() {
	    return effect.defensiveValue() > 0.0;
	}

	public boolean isTactical() {
	    return effect.tacticalValue() > 0.0;
	}

	public boolean isClosing() {
	    return effect.damagePotential >= 0.22;
	}
    }

    public static final class Decision {
	public final ActionProfile action;
	public final double totalScore;
	public final String reason;

	public Decision(ActionProfile action, double totalScore, String reason) {
	    this.action = action;
	    this.totalScore = totalScore;
	    this.reason = reason != null ? reason : "";
	}
    }

    private static final class PendingExperience {
	final CombatState before;
	final ActionProfile action;
	final double resolveAt;

	PendingExperience(CombatState before, ActionProfile action, double resolveAt) {
	    this.before = before;
	    this.action = action;
	    this.resolveAt = resolveAt;
	}
    }

    public static final class Experience {
	public final String actionSignature;
	public final double reward;
	public final double at;

	public Experience(String actionSignature, double reward, double at) {
	    this.actionSignature = actionSignature;
	    this.reward = reward;
	    this.at = at;
	}
    }

    public static final class ExperienceReplay {
	private final int capacity;
	private final Deque<Experience> buffer = new ArrayDeque<>();
	private final Map<String, RewardStat> stats = new HashMap<>();

	ExperienceReplay(int capacity) {
	    this.capacity = Math.max(8, capacity);
	}

	void add(Experience exp) {
	    if(exp == null || exp.actionSignature == null || exp.actionSignature.isEmpty())
		return;
	    if(buffer.size() >= capacity) {
		Experience old = buffer.removeFirst();
		RewardStat oldStat = stats.get(old.actionSignature);
		if(oldStat != null)
		    oldStat.remove(old.reward);
	    }
	    buffer.addLast(exp);
	    stats.computeIfAbsent(exp.actionSignature, k -> new RewardStat()).add(exp.reward);
	}

	double bias(String signature) {
	    RewardStat stat = stats.get(signature);
	    if(stat == null || stat.count < 2)
		return 0.0;
	    return Utils.clip(stat.mean() * 12.0, -18.0, 18.0);
	}

	void clear() {
	    buffer.clear();
	    stats.clear();
	}
    }

    private static final class RewardStat {
	double sum;
	int count;

	void add(double reward) {
	    sum += reward;
	    count++;
	}

	void remove(double reward) {
	    sum -= reward;
	    count = Math.max(0, count - 1);
	}

	double mean() {
	    return (count <= 0) ? 0.0 : (sum / count);
	}
    }

    public static CombatState captureState(GameUI gui, Fightview fv, Fightview.Relation rel, MoonFightCombatContext ctx) {
	double now = (ctx != null) ? ctx.now : Utils.rtime();
	EnumMap<OpeningType, Double> selfOpen = openingsFromBuffs(ctx != null ? ctx.selfBuffSnapshots : List.of());
	EnumMap<OpeningType, Double> oppOpen = openingsFromBuffs(ctx != null ? ctx.oppBuffSnapshots : List.of());
	ActiveStance selfStance = activeStance(ctx != null ? ctx.selfBuffSnapshots : List.of());
	ActiveStance oppStance = activeStance(ctx != null ? ctx.oppBuffSnapshots : List.of());
	int selfIp = (ctx != null) ? ctx.ipSelf : ((rel != null) ? rel.ip : 0);
	int oppIp = (ctx != null) ? ctx.ipOpp : ((rel != null) ? rel.oip : 0);
	double selfStamina = meterFrac(gui, "stam");
	if(selfStamina < 0)
	    selfStamina = Utils.clip(CombatManager.getLastStamina(), 0.0, 1.0);
	double oppStamina = 1.0;
	double selfHealth = meterFrac(gui, "hp");
	double oppHealth = gobHealthFrac(currentGob(gui, rel));
	double selfWeight = estimateSelfWeight(gui, selfStance);
	double oppWeight = estimateOppWeight(selfWeight, rel, oppStance, oppHealth);
	double selfAdv = estimateAdvantage(selfIp, oppIp, selfStance, selfOpen, true);
	double oppAdv = estimateAdvantage(oppIp, selfIp, oppStance, oppOpen, false);
	CombatantState self = new CombatantState(selfWeight, selfOpen, selfAdv, selfIp, selfStamina, selfHealth, selfStance);
	CombatantState opponent = new CombatantState(oppWeight, oppOpen, oppAdv, oppIp, oppStamina, oppHealth, oppStance);
	return new CombatState(self, opponent, now, rel != null ? rel.gobid : -1L);
    }

    public static void observeState(CombatState state) {
	if(state == null)
	    return;
	String sig = stateSignature(state);
	if(sig.equals(lastObservedStateSig)) {
	    staleStateTicks++;
	} else {
	    lastObservedStateSig = sig;
	    staleStateTicks = 0;
	    lastMeaningfulStateAt = state.now;
	}
	if(pending != null && state.now >= pending.resolveAt) {
	    double reward = computeReward(pending.before, state);
	    replay.add(new Experience(pending.action.signature(), reward, state.now));
	    pending = null;
	}
    }

    public static boolean shouldEvaluate(double now) {
	if((now - lastDecisionAt) < ENGINE_TICK_SEC)
	    return false;
	lastDecisionAt = now;
	return true;
    }

    public static List<ActionProfile> scanDeck(CombatState state, MoonFightCombatContext ctx) {
	if(ctx == null || ctx.hand == null || ctx.hand.isEmpty())
	    return List.of();
	List<ActionProfile> out = new ArrayList<>();
	for(MoonFightCombatContext.HandCard card : ctx.hand) {
	    out.add(profileFromCard(card, state));
	}
	return Collections.unmodifiableList(out);
    }

    public static Decision evaluateBestMove(CombatState state, List<ActionProfile> availableMoves) {
	if(state == null || availableMoves == null || availableMoves.isEmpty())
	    return null;
	double ownOpening = state.self.maxOpening;
	double enemyOpening = state.opponent.maxOpening;
	double advantageGap = state.self.advantage - state.opponent.advantage;
	double ipGap = state.self.ip - state.opponent.ip;
	String stateSig = stateSignature(state);
	ActionProfile best = null;
	double bestScore = Double.NEGATIVE_INFINITY;
	String bestReason = "";
	for(ActionProfile move : availableMoves) {
	    if(move == null || move.cooldown > 1e-6)
		continue;
	    ScoreBreakdown breakdown = scoreMove(state, stateSig, move, ownOpening, enemyOpening, advantageGap, ipGap);
	    if(breakdown.total > bestScore) {
		bestScore = breakdown.total;
		best = move;
		bestReason = breakdown.reason;
	    }
	}
	if(best == null)
	    return null;
	boolean bestRepeatsOnSameState = stateSig.equals(lastPlayedStateSig) && best.signature().equals(lastPlayedActionSig);
	if(bestRepeatsOnSameState && repeatedSameActionOnState >= 3 && !best.isClosing() && !best.isDefensive() && enemyOpening < 0.50) {
	    lastStatus = "hold " + humanLabel(best) + " | anti-stall wait";
	    return null;
	}
	lastStatus = "pick " + humanLabel(best) + " | " + bestReason;
	return new Decision(best, bestScore, bestReason);
    }

    public static void notePlayed(CombatState state, ActionProfile action, double now) {
	if(state == null || action == null)
	    return;
	String sig = stateSignature(state);
	if(sig.equals(lastPlayedStateSig) && action.signature().equals(lastPlayedActionSig)) {
	    repeatedSameActionOnState++;
	} else {
	    repeatedSameActionOnState = 0;
	}
	lastPlayedStateSig = sig;
	lastPlayedActionSig = action.signature();
	pending = new PendingExperience(state, action, now + REPLAY_OBSERVE_DELAY);
    }

    public static ActionProfile findProfile(List<ActionProfile> profiles, int slot) {
	if(profiles == null)
	    return null;
	for(ActionProfile profile : profiles) {
	    if(profile != null && profile.slot == slot)
		return profile;
	}
	return null;
    }

    public static String statusSummary() {
	return lastStatus;
    }

    public static void reset() {
	lastDecisionAt = -1e9;
	lastStatus = "idle";
	pending = null;
	replay.clear();
	lastPlayedActionSig = "";
	lastPlayedStateSig = "";
	lastObservedStateSig = "";
	repeatedSameActionOnState = 0;
	staleStateTicks = 0;
	lastMeaningfulStateAt = -1e9;
    }

    public static void endFight() {
	lastDecisionAt = -1e9;
	lastStatus = "idle";
	pending = null;
	lastPlayedActionSig = "";
	lastPlayedStateSig = "";
	lastObservedStateSig = "";
	repeatedSameActionOnState = 0;
	staleStateTicks = 0;
	lastMeaningfulStateAt = -1e9;
    }

    public static EnumMap<OpeningType, Double> openingsFromSnapshots(List<MoonFightCombatContext.BuffSnapshot> buffs) {
	return openingsFromBuffs(buffs);
    }

    public static ActiveStance stanceFromSnapshots(List<MoonFightCombatContext.BuffSnapshot> buffs) {
	return activeStance(buffs);
    }

    private static ScoreBreakdown scoreMove(CombatState state, String stateSig, ActionProfile move, double ownOpening,
	double enemyOpening, double advantageGap, double ipGap) {
	ActionEffectVector fx = move.effect;
	double defensiveValue = fx.defensiveValue();
	double tacticalValue = fx.tacticalValue();
	double closingValue = fx.closingValue();
	double setupValue = sumPositive(fx.oppOpeningDelta);
	double stanceBias = (state.self.stance != null) ? state.self.stance.offenseBias - state.self.stance.defenseBias : 0.0;
	boolean neutralBoard = ownOpening < 0.18 && enemyOpening < 0.18 && Math.abs(ipGap) <= 1 && Math.abs(advantageGap) < 1.8;
	boolean unchangedState = stateSig.equals(lastPlayedStateSig);
	boolean stalled = unchangedState && (repeatedSameActionOnState >= 1 || staleStateTicks >= 6);
	double score = 0.0;

	if(ownOpening > 0.70) {
	    score += defensiveValue * 5.6;
	    if(!move.isDefensive())
		score -= 22.0 + (ownOpening * 28.0);
	    if(fx.escapeValue > 0.0)
		score += fx.escapeValue * 18.0;
	} else {
	    score += defensiveValue * (1.4 + ownOpening * 1.8);
	}

	if(advantageGap < 0.0 || ipGap < 0) {
	    score += tacticalValue * 3.8;
	    if(!move.isTactical())
		score -= 10.0;
	} else {
	    score += tacticalValue * 1.35;
	}

	if(enemyOpening > 0.50) {
	    score += closingValue * 1.2;
	    if(move.isClosing())
		score += 55.0 + (enemyOpening * 42.0);
	} else {
	    score += closingValue * 0.45;
	    score += setupValue * 1.65;
	}

	if(neutralBoard) {
	    if(move.archetype == MoonCombatAI.CardArchetype.OPENING || fx.stanceValue > 0.0 || setupValue >= 6.0)
		score += 24.0 + (setupValue * 0.9) + (fx.stanceValue * 10.0);
	    if(move.archetype == MoonCombatAI.CardArchetype.AGGRESSION && setupValue >= 6.0)
		score += 8.0;
	    if(move.archetype == MoonCombatAI.CardArchetype.UTILITY && fx.damagePotential < 0.12 && setupValue < 6.0)
		score -= 26.0;
	    if(move.archetype == MoonCombatAI.CardArchetype.DEFENSE && defensiveValue < 8.0)
		score -= 18.0;
	}

	if(ownOpening > 0.84) {
	    score += fx.escapeValue * 26.0;
	    score += fx.chaseBreakValue * 16.0;
	    score += fx.mobilityGain * 10.0;
	}

	if(stalled) {
	    if(move.signature().equals(lastPlayedActionSig))
		score -= 42.0 + (repeatedSameActionOnState * 18.0);
	    if(move.archetype == MoonCombatAI.CardArchetype.OPENING || fx.stanceValue > 0.0 || setupValue >= 6.0)
		score += 18.0;
	    if(move.isDefensive())
		score += ownOpening > 0.25 ? 16.0 : 6.0;
	    if(fx.escapeValue > 0.0 || fx.chaseBreakValue > 0.0)
		score += 10.0;
	    if(move.archetype == MoonCombatAI.CardArchetype.UTILITY && !move.signature().equals(lastPlayedActionSig)
		&& (setupValue > 6.0 || fx.escapeValue > 0.0))
		score += 6.0;
	}

	score += fx.stanceValue * (8.0 + (stanceBias < 0 ? 2.0 : 0.0));
	score += replay.bias(move.signature());
	score -= move.ipCost * 6.0;
	score += fx.advantageDelta * 4.0;
	score -= Math.max(0.0, -fx.staminaDeltaSelf) * 12.0;
	score += fx.confidence * 9.0;

	if(state.self.stamina >= 0.0 && state.self.stamina < 0.22 && move.ipCost > 0)
	    score -= 12.0;
	if(state.self.stamina >= 0.0 && state.self.stamina < 0.16 && fx.mobilityGain <= 0.0)
	    score -= 8.0;

	String reason;
	if(stalled && !move.signature().equals(lastPlayedActionSig)) {
	    reason = "anti-stall pivot";
	} else if(neutralBoard && (move.archetype == MoonCombatAI.CardArchetype.OPENING || fx.stanceValue > 0.0 || setupValue >= 6.0)) {
	    reason = "neutral setup";
	} else if(ownOpening > 0.70 && move.isDefensive()) {
	    reason = "defensive reset";
	} else if((advantageGap < 0.0 || ipGap < 0) && move.isTactical()) {
	    reason = "recover advantage";
	} else if(enemyOpening > 0.50 && move.isClosing()) {
	    reason = "close on opening";
	} else if(fx.escapeValue > 0.0 && ownOpening > 0.84) {
	    reason = "escape pressure";
	} else if(setupValue > defensiveValue && setupValue > closingValue * 0.3) {
	    reason = "build pressure";
	} else {
	    reason = "best composite";
	}
	return new ScoreBreakdown(score, reason);
    }

    private static ActionProfile profileFromCard(MoonFightCombatContext.HandCard card, CombatState state) {
	String text = lowered(card.text);
	String resName = lowered(card.resName);
	String label = !resName.isEmpty() ? resName : text;
	MoonCombatAI.CardArchetype archetype = MoonCombatAI.classify(text);
	MoonCombatTables.CardRow row = MoonCombatTables.matchCard(card.resName);
	ActionEffectVector effect = new ActionEffectVector();
	double breach = (row != null) ? row.breach : fallbackBreach(archetype);
	double ipPressure = (row != null) ? row.ipPressure : fallbackIpPressure(archetype);
	OpeningType enemyFocus = preferredEnemyFocus(text, state.opponent);
	OpeningType selfFocus = state.self.highestOpeningType();
	boolean finisher = containsAny(text, FINISHER_CARDS) || breach >= 0.78;
	boolean tactical = containsAny(text, TACTICAL_CARDS);
	boolean escape = containsAny(text, ESCAPE_CARDS);
	boolean stance = containsAny(text, STANCE_NAMES);

	switch(archetype) {
	case DEFENSE -> {
	    double reset = 8.0 + (state.self.maxOpening * 28.0) + breach * 10.0;
	    applyDefense(effect.selfOpeningDelta, state.self, reset, 0.68);
	    effect.advantageDelta += 0.35 + Math.max(0.0, ipPressure) * 1.4;
	    effect.mobilityGain += escape ? 0.75 : 0.18;
	    effect.escapeValue += escape ? 1.35 : 0.15;
	    effect.chaseBreakValue += escape ? 0.55 : 0.05;
	    effect.staminaDeltaSelf -= 0.02;
	    effect.confidence = 0.78;
	}
	case AGGRESSION -> {
	    double pressure = 7.0 + breach * 18.0;
	    effect.oppOpeningDelta.put(enemyFocus, pressure);
	    if(!finisher) {
		OpeningType secondary = secondaryEnemyFocus(enemyFocus);
		effect.oppOpeningDelta.put(secondary, effect.oppOpeningDelta.get(secondary) + pressure * 0.28);
	    }
	    effect.damagePotential = (0.14 + breach * 0.78)
		+ (state.opponent.opening(enemyFocus) * (finisher ? 1.15 : 0.62));
	    effect.advantageDelta += Math.max(0.0, ipPressure) * 1.2;
	    effect.mobilityGain += tactical ? 0.25 : 0.0;
	    effect.chaseBreakValue += escape ? 0.35 : 0.0;
	    effect.confidence = finisher ? 0.86 : 0.68;
	}
	case OPENING -> {
	    if(stance) {
		if(containsAny(text, OFFENSIVE_STANCES)) {
		    effect.stanceValue += 1.2;
		    effect.damagePotential += 0.12;
		    effect.advantageDelta += 0.8;
		} else if(containsAny(text, DEFENSIVE_STANCES)) {
		    effect.stanceValue += 1.1;
		    applyDefense(effect.selfOpeningDelta, state.self, 6.5 + breach * 7.0, 0.58);
		    effect.advantageDelta += 0.45;
		} else {
		    effect.stanceValue += 0.8;
		    effect.advantageDelta += 0.55;
		}
	    } else {
		double pressure = 8.0 + breach * 16.0;
		effect.oppOpeningDelta.put(enemyFocus, pressure);
		effect.advantageDelta += 0.5 + Math.max(0.0, ipPressure);
	    }
	    effect.confidence = 0.64;
	}
	case UTILITY -> {
	    effect.advantageDelta += 1.0 + Math.max(0.0, ipPressure) * 1.65;
	    effect.ipDeltaSelf += Math.max(1, (int)Math.round(Math.max(0.0, ipPressure) * 3.5));
	    effect.mobilityGain += tactical ? 0.95 : 0.35;
	    effect.escapeValue += escape ? 1.25 : 0.20;
	    effect.chaseBreakValue += escape ? 0.95 : 0.20;
	    if(containsAny(text, "take aim", "opportunity knocks", "push the advantage", "seize the day"))
		effect.damagePotential += 0.08;
	    effect.confidence = 0.72;
	}
	default -> {
	    effect.advantageDelta += Math.max(0.0, ipPressure) * 0.75;
	    effect.damagePotential += breach * 0.25;
	    effect.confidence = 0.32;
	}
	}

	if(finisher) {
	    effect.damagePotential += 0.10 + state.opponent.maxOpening * 0.40;
	    effect.advantageDelta += 0.10;
	}
	if(tactical && archetype != MoonCombatAI.CardArchetype.UTILITY) {
	    effect.advantageDelta += 0.6;
	    effect.mobilityGain += 0.35;
	}
	if(escape && archetype != MoonCombatAI.CardArchetype.DEFENSE) {
	    effect.escapeValue += 0.75;
	    effect.chaseBreakValue += 0.65;
	}
	if(state.self.maxOpening > 0.78 && archetype == MoonCombatAI.CardArchetype.DEFENSE)
	    effect.confidence += 0.12;

	int ipCost = parseIpCost(text, row, archetype);
	return new ActionProfile(card.slot, card.resName, label, archetype, ipCost, Math.max(0.0, card.cdRemaining), effect);
    }

    private static int parseIpCost(String text, MoonCombatTables.CardRow row, MoonCombatAI.CardArchetype archetype) {
	Matcher matcher = IP_COST_PATTERN.matcher(text);
	if(matcher.find()) {
	    try {
		return Integer.parseInt(matcher.group(1));
	    } catch(NumberFormatException ignored) {
	    }
	}
	if(row != null && row.ipPressure < -0.1)
	    return (int)Math.round(Math.abs(row.ipPressure) * 2.0);
	return switch(archetype) {
	case AGGRESSION -> containsAny(text, FINISHER_CARDS) ? 2 : 1;
	case UTILITY -> containsAny(text, "take aim", "seize the day") ? 1 : 0;
	default -> 0;
	};
    }

    private static void applyDefense(EnumMap<OpeningType, Double> delta, CombatantState self, double total, double primaryShare) {
	OpeningType primary = self.highestOpeningType();
	OpeningType secondary = secondaryEnemyFocus(primary);
	double main = total * Utils.clip(primaryShare, 0.45, 0.85);
	double rest = Math.max(0.0, total - main);
	delta.put(primary, delta.get(primary) - main);
	delta.put(secondary, delta.get(secondary) - rest);
    }

    private static double computeReward(CombatState before, CombatState after) {
	double damageDealt = hpDrop(before.opponent.health, after.opponent.health);
	double damageTaken = hpDrop(before.self.health, after.self.health);
	if(damageDealt <= 0.0)
	    damageDealt = Math.max(0.0, before.opponent.totalOpening - after.opponent.totalOpening) * 0.20;
	double advantageGained = Math.max(0.0, after.self.advantage - before.self.advantage);
	double ownOpeningIncrease = Math.max(0.0, after.self.totalOpening - before.self.totalOpening);
	return (damageDealt * 1.5) + (advantageGained * 0.5) - (damageTaken * 2.0) - (ownOpeningIncrease * 1.0);
    }

    private static double hpDrop(double before, double after) {
	if(before < 0.0 || after < 0.0)
	    return 0.0;
	return Math.max(0.0, before - after);
    }

    private static ActiveStance activeStance(List<MoonFightCombatContext.BuffSnapshot> buffs) {
	for(MoonFightCombatContext.BuffSnapshot buff : buffs) {
	    String line = lowered(buff.line + " " + buff.resName);
	    if(!containsAny(line, STANCE_NAMES))
		continue;
	    double offense = containsAny(line, OFFENSIVE_STANCES) ? 0.65 : 0.15;
	    double defense = containsAny(line, DEFENSIVE_STANCES) ? 0.75 : 0.15;
	    double mobility = containsAny(line, "to arms", "combat meditation") ? 0.35 : 0.10;
	    return new ActiveStance(buff.resName, buff.line, offense, defense, mobility);
	}
	return null;
    }

    private static EnumMap<OpeningType, Double> openingsFromBuffs(List<MoonFightCombatContext.BuffSnapshot> buffs) {
	EnumMap<OpeningType, Double> out = zeroOpenings();
	for(MoonFightCombatContext.BuffSnapshot buff : buffs) {
	    if(buff == null)
		continue;
	    String line = lowered(buff.line + " " + buff.resName);
	    OpeningType type = openingType(line);
	    if(type == null)
		continue;
	    out.put(type, Math.max(out.get(type), extractPercent(buff)));
	}
	return out;
    }

    private static double extractPercent(MoonFightCombatContext.BuffSnapshot buff) {
	if(buff.ameter != null)
	    return Utils.clip(buff.ameter, 0.0, 1.0);
	if(buff.cmeter != null)
	    return Utils.clip(buff.cmeter, 0.0, 1.0);
	Matcher matcher = PERCENT_PATTERN.matcher(lowered(buff.line));
	if(matcher.find()) {
	    try {
		return Utils.clip(Integer.parseInt(matcher.group(1)) / 100.0, 0.0, 1.0);
	    } catch(NumberFormatException ignored) {
	    }
	}
	return 0.0;
    }

    private static OpeningType openingType(String line) {
	for(OpeningType type : OpeningType.values()) {
	    if(type.matches(line))
		return type;
	}
	return null;
    }

    private static EnumMap<OpeningType, Double> zeroOpenings() {
	EnumMap<OpeningType, Double> out = new EnumMap<>(OpeningType.class);
	for(OpeningType type : OpeningType.values())
	    out.put(type, 0.0);
	return out;
    }

    private static EnumMap<OpeningType, Double> immutableOpenings(EnumMap<OpeningType, Double> openings) {
	EnumMap<OpeningType, Double> out = zeroOpenings();
	if(openings == null)
	    return out;
	for(Map.Entry<OpeningType, Double> entry : openings.entrySet())
	    out.put(entry.getKey(), Utils.clip(entry.getValue(), 0.0, 1.0));
	return out;
    }

    private static double maxOpening(EnumMap<OpeningType, Double> openings) {
	double best = 0.0;
	for(double value : openings.values())
	    best = Math.max(best, value);
	return best;
    }

    private static double totalOpening(EnumMap<OpeningType, Double> openings) {
	double sum = 0.0;
	for(double value : openings.values())
	    sum += value;
	return sum;
    }

    private static double sumPositive(EnumMap<OpeningType, Double> delta) {
	double sum = 0.0;
	for(double value : delta.values()) {
	    if(value > 0.0)
		sum += value;
	}
	return sum;
    }

    private static double estimateSelfWeight(GameUI gui, ActiveStance stance) {
	if(gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null)
	    return 1.0;
	Glob glob = gui.ui.sess.glob;
	double strength = attr(glob, "str");
	double ua = attr(glob, "unarmed");
	double mc = attr(glob, "melee");
	double base = Math.sqrt(Math.max(1.0, strength) * Math.max(1.0, Math.max(ua, mc)));
	double weight = Math.max(1.0, base / 10.0);
	if(stance != null)
	    weight *= 1.0 + stance.offenseBias * 0.08 + stance.defenseBias * 0.04;
	return weight;
    }

    private static double estimateOppWeight(double selfWeight, Fightview.Relation rel, ActiveStance stance, double health) {
	double weight = Math.max(1.0, selfWeight * 0.95);
	if(rel != null)
	    weight += Math.max(0, rel.oip - rel.ip) * 0.12;
	if(health >= 0.0)
	    weight *= 1.0 + ((1.0 - health) * 0.10);
	if(stance != null)
	    weight *= 1.0 + stance.offenseBias * 0.06 + stance.defenseBias * 0.03;
	return weight;
    }

    private static double estimateAdvantage(int ip, int oppIp, ActiveStance stance, EnumMap<OpeningType, Double> openings, boolean self) {
	double adv = ip;
	adv += (ip - oppIp) * 0.5;
	if(stance != null)
	    adv += stance.offenseBias * 2.0 + stance.defenseBias * 1.2;
	if(self)
	    adv -= maxOpening(openings) * 2.5;
	return adv;
    }

    private static double attr(Glob glob, String name) {
	try {
	    Glob.CAttr attr = glob.getcattr(name);
	    return(attr == null) ? 0.0 : Math.max(attr.comp, attr.base);
	} catch(Exception ignored) {
	    return 0.0;
	}
    }

    private static double meterFrac(GameUI gui, String name) {
	if(gui == null || name == null)
	    return -1.0;
	try {
	    List<IMeter.Meter> meters = gui.getmeters(name);
	    if(meters != null && !meters.isEmpty())
		return Utils.clip(meters.get(0).a / 100.0, 0.0, 1.0);
	} catch(Exception ignored) {
	}
	return -1.0;
    }

    private static Gob currentGob(GameUI gui, Fightview.Relation rel) {
	if(gui == null || gui.map == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null || rel == null)
	    return null;
	try {
	    return gui.ui.sess.glob.oc.getgob(rel.gobid);
	} catch(Exception ignored) {
	    return null;
	}
    }

    private static double gobHealthFrac(Gob gob) {
	if(gob == null)
	    return -1.0;
	try {
	    GobHealth health = gob.getattr(GobHealth.class);
	    return (health == null) ? -1.0 : Utils.clip(health.hp, 0.0, 1.0);
	} catch(Exception ignored) {
	    return -1.0;
	}
    }

    private static OpeningType preferredEnemyFocus(String text, CombatantState opponent) {
	OpeningType explicit = openingType(text);
	if(explicit != null)
	    return explicit;
	if(opponent != null)
	    return opponent.highestOpeningType();
	return OpeningType.STRIKING;
    }

    private static OpeningType secondaryEnemyFocus(OpeningType primary) {
	return switch(primary) {
	case STRIKING -> OpeningType.SLASHING;
	case BLUNT -> OpeningType.PIERCING;
	case PIERCING -> OpeningType.BLUNT;
	case SLASHING -> OpeningType.STRIKING;
	case REELING -> OpeningType.STRIKING;
	};
    }

    private static double fallbackBreach(MoonCombatAI.CardArchetype archetype) {
	return switch(archetype) {
	case AGGRESSION -> 0.72;
	case OPENING -> 0.46;
	case DEFENSE -> 0.18;
	case UTILITY -> 0.34;
	default -> 0.24;
	};
    }

    private static double fallbackIpPressure(MoonCombatAI.CardArchetype archetype) {
	return switch(archetype) {
	case UTILITY -> 0.68;
	case OPENING -> 0.38;
	case DEFENSE -> 0.20;
	case AGGRESSION -> 0.26;
	default -> 0.15;
	};
    }

    private static String humanLabel(ActionProfile action) {
	if(action == null)
	    return "";
	if(action.resName != null && !action.resName.isEmpty())
	    return action.resName;
	return action.label != null ? action.label : "";
    }

    private static String stateSignature(CombatState state) {
	if(state == null)
	    return "";
	String selfStance = (state.self.stance != null && state.self.stance.resName != null) ? state.self.stance.resName : "";
	String oppStance = (state.opponent.stance != null && state.opponent.stance.resName != null) ? state.opponent.stance.resName : "";
	return String.format(Locale.ROOT, "%d|%d|%d|%d|%d|%d|%d|%d|%s|%s",
	    state.targetGobId,
	    state.self.ip,
	    state.opponent.ip,
	    roundPct(state.self.maxOpening),
	    roundPct(state.opponent.maxOpening),
	    roundPct(state.self.totalOpening),
	    roundPct(state.opponent.totalOpening),
	    roundPct(state.opponent.health),
	    selfStance,
	    oppStance);
    }

    private static int roundPct(double v) {
	if(v < 0.0)
	    return -1;
	return (int)Math.round(v * 100.0);
    }

    private static boolean containsAny(String text, String... keys) {
	if(text == null || text.isEmpty())
	    return false;
	for(String key : keys) {
	    if(text.contains(key))
		return true;
	}
	return false;
    }

    private static String lowered(String text) {
	return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static final class ScoreBreakdown {
	final double total;
	final String reason;

	ScoreBreakdown(double total, String reason) {
	    this.total = total;
	    this.reason = reason;
	}
    }
}
