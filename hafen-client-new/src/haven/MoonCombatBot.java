package haven;

import static haven.OCache.posres;

import java.util.Locale;

/**
 * Client-side combat automation. Modes: legacy keywords ({@link MoonConfig#combatBotBrainMode} 0),
 * {@link MoonCombatAI} one-ply (1), expectiminimax (2). Respects server slot hints when
 * {@link MoonConfig#combatBotTrustHints} is on.
 */
public class MoonCombatBot {
    private static double lastActionTime = 0;
    private static double prevOppLastUse = -1e9;
    private static long lastOppGob = Long.MIN_VALUE;
    private static final MoonCombatBrainState combatBrain = new MoonCombatBrainState();

    public static void tick(GameUI gui, double dt) {
	if(!MoonConfig.combatBot)
	    return;
	if(gui == null || gui.fv == null)
	    return;

	Fightview fv = gui.fv;
	if(fv.lsrel == null || fv.lsrel.isEmpty())
	    return;

	double now = Utils.rtime();

	if(fv.atkct > 0 && now < fv.atkct)
	    return;
	if(now - lastActionTime < 0.36)
	    return;

	Fightview.Relation target = fv.current;
	if(target == null && !fv.lsrel.isEmpty())
	    target = fv.lsrel.getFirst();
	if(target == null)
	    return;

	Fightsess fs = findFightsess(gui);
	if(fs == null)
	    return;

	combatBrain.beginOpponent(target.gobid);
	if(target.gobid != lastOppGob) {
	    lastOppGob = target.gobid;
	    prevOppLastUse = -1e9;
	}
	observeOpponentPlay(target);

	int slot = pickActionSlot(fs, fv, target, now);
	if(slot < 0)
	    return;

	try {
	    logDecisionIfNeeded(fs, fv, target, slot, now);
	    recordSelfCard(fs, slot);
	    sendUse(fs, gui, fv, slot);
	    lastActionTime = now;
	} catch(Exception e) {
	    new Warning(e, "CombatBot use error").issue();
	}
    }

    private static void observeOpponentPlay(Fightview.Relation rel) {
	if(rel == null || rel.lastact == null)
	    return;
	if(rel.lastuse <= prevOppLastUse + 1e-3)
	    return;
	prevOppLastUse = rel.lastuse;
	String tx = MoonFightCombatContext.fightCardText(rel.lastact);
	if(tx != null && !tx.isEmpty())
	    combatBrain.observeOpponentPlay(MoonCombatAI.classify(tx));
    }

    private static void recordSelfCard(Fightsess fs, int slot) {
	if(slot < 0 || slot >= fs.actions.length)
	    return;
	Fightsess.Action act = fs.actions[slot];
	if(act == null)
	    return;
	String rn = "";
	try {
	    Resource r = act.res.get();
	    if(r != null && r.name != null)
		rn = r.name;
	} catch(Loading ignored) {}
	String text = cardText(act);
	MoonFightCombatContext.HandCard hc = new MoonFightCombatContext.HandCard(slot, rn, text, true, 0, false, false);
	combatBrain.recordSelfPlay(MoonCombatAI.cardTypeKey(hc));
	combatBrain.recordSelfSlot(slot);
    }

    private static void logDecisionIfNeeded(Fightsess fs, Fightview fv, Fightview.Relation rel, int slot, double now) {
	if(!MoonConfig.combatBotLogAi || rel == null)
	    return;
	String rn = "";
	try {
	    if(slot >= 0 && slot < fs.actions.length && fs.actions[slot] != null && fs.actions[slot].res != null) {
		Resource r = fs.actions[slot].res.get();
		if(r != null && r.name != null)
		    rn = r.name;
	    }
	} catch(Loading ignored) {}
	MoonFightCombatContext ctx = MoonFightCombatContext.capture(fs, fv, rel, now);
	double score = 0;
	if(slot >= 0) {
	    for(MoonFightCombatContext.HandCard c : ctx.hand) {
		if(c.slot != slot || !c.ready)
		    continue;
		score = MoonCombatAI.MoonCardEvaluator.score(c, ctx, combatBrain)
		    + MoonCombatAI.MoonCombatDecisionMaker.wSynergy * MoonCombatAI.MoonSynergyAnalyzer.bonusFromHistory(combatBrain, c)
		    + MoonCombatAI.MoonCombatDecisionMaker.wPressure * MoonCombatAI.MoonBreachCalculator.expectedPressure(c, ctx)
		    + MoonCombatAI.MoonCombatDecisionMaker.wCounter * MoonCombatAI.MoonOpponentPredictor.counterDefenseBonus(c, ctx, combatBrain);
		break;
	    }
	}
	MoonCombatLogger.logDecision(MoonConfig.combatBotBrainMode, slot, rn, score, rel.ip, rel.oip,
	    MoonCombatTables.lastLoadError());
    }

    private static void sendUse(Fightsess fs, GameUI gui, Fightview fv, int slot) {
	int mods = gui.ui.modflags();
	Fightview.Relation rel = fv.current;
	if(rel != null && gui.map != null) {
	    Gob og = gui.map.glob.oc.getgob(rel.gobid);
	    if(og != null) {
		fs.wdgmsg("use", slot, 1, mods, og.rc.floor(posres));
		return;
	    }
	}
	fs.wdgmsg("use", slot, 1, mods);
    }

    private static Fightsess findFightsess(Widget w) {
	if(w instanceof Fightsess)
	    return (Fightsess) w;
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    Fightsess fs = findFightsess(ch);
	    if(fs != null)
		return fs;
	}
	return null;
    }

    private static boolean isReady(Fightsess fs, int i, double now) {
	if(i < 0 || i >= fs.actions.length)
	    return false;
	Fightsess.Action act = fs.actions[i];
	if(act == null)
	    return false;
	return !(act.ct > 0 && now < act.ct);
    }

    private static String cardText(Fightsess.Action act) {
	if(act == null || act.res == null)
	    return "";
	try {
	    Resource r = act.res.get();
	    if(r == null)
		return "";
	    StringBuilder sb = new StringBuilder();
	    if(r.name != null)
		sb.append(r.name).append(' ');
	    Resource.Tooltip tt = r.layer(Resource.tooltip);
	    if(tt != null && tt.t != null)
		sb.append(tt.t);
	    return sb.toString().toLowerCase(Locale.ROOT);
	} catch(Loading ignored) {
	    return "";
	}
    }

    private static int pickActionSlot(Fightsess fs, Fightview fv, Fightview.Relation rel, double now) {
	MoonFightCombatContext ctx = MoonFightCombatContext.capture(fs, fv, rel, now);
	combatBrain.observeReadyOpeningInHand(MoonCombatAI.countReadyOpeningArchetypeInHand(ctx));
	if(MoonConfig.combatBotBrainMode == 0)
	    return pickLegacySlot(fs, rel, now, ctx);

	int slot = MoonCombatAI.MoonCombatDecisionMaker.pickSlotRespectingHints(fs, ctx, combatBrain, now);
	if(slot >= 0)
	    return slot;
	return pickLegacySlot(fs, rel, now, ctx);
    }

    /**
     * Original IP + keyword scoring when AI returns nothing or mode 0.
     */
    private static int pickLegacySlot(Fightsess fs, Fightview.Relation rel, double now, MoonFightCombatContext ctx) {
	int n = fs.actions.length;

	if(MoonConfig.combatBotTrustHints && fs.use >= 0 && fs.use < n && isReady(fs, fs.use, now)
	    && !MoonCombatAI.trustHintOverriddenForSlot(fs, fs.use, ctx, combatBrain, now))
	    return fs.use;
	if(MoonConfig.combatBotTrustHints && fs.useb >= 0 && fs.useb < n && isReady(fs, fs.useb, now)
	    && !MoonCombatAI.trustHintOverriddenForSlot(fs, fs.useb, ctx, combatBrain, now))
	    return fs.useb;

	int best = -1;
	double bestScore = Double.NEGATIVE_INFINITY;
	int ipd = rel != null ? rel.ip - rel.oip : 0;

	for(int i = 0; i < n; i++) {
	    if(!isReady(fs, i, now))
		continue;
	    Fightsess.Action act = fs.actions[i];
	    String text = cardText(act);
	    String rn = "";
	    try {
		Resource r = act.res.get();
		if(r != null && r.name != null)
		    rn = r.name;
	    } catch(Loading ignored) {}
	    double s = 0;

	    MoonCombatAI.CardArchetype arch = MoonCombatAI.classify(text);
	    boolean defw = arch == MoonCombatAI.CardArchetype.DEFENSE;
	    boolean aggw = arch == MoonCombatAI.CardArchetype.AGGRESSION;
	    boolean opnw = arch == MoonCombatAI.CardArchetype.OPENING;

	    if(ipd < -1) {
		s += defw ? 120 : 0;
		s += aggw ? 20 : 0;
		s += opnw ? 10 : 0;
	    } else if(ipd > 1) {
		s += aggw ? 100 : 0;
		s += opnw ? 60 : 0;
		s += defw ? 25 : 0;
	    } else {
		s += aggw ? 50 : 0;
		s += defw ? 50 : 0;
		s += opnw ? 40 : 0;
	    }

	    if(!defw && !aggw && !opnw)
		s += 15;

	    s -= i * 0.02;
	    if(ctx != null)
		s += MoonCombatAI.openingRedundancyPenalty(ctx, combatBrain,
		    new MoonFightCombatContext.HandCard(i, rn, text, true, 0, false, false));

	    if(s > bestScore) {
		bestScore = s;
		best = i;
	    }
	}
	return best;
    }

    public static void reset() {
	lastActionTime = 0;
	prevOppLastUse = -1e9;
	lastOppGob = Long.MIN_VALUE;
    }
}
