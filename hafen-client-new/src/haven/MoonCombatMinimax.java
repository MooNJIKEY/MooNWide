package haven;

/**
 * Shallow expectiminimax: our card choice, then chance node over opponent archetypes from
 * {@link MoonCombatAI.MoonOpponentPredictor} + {@link MoonCombatBrainState.OpponentProfile}.
 * Depth 2 = one opponent reply; depth 3 adds a coarse second own move (beam = top-2 first moves).
 */
public final class MoonCombatMinimax {

    private MoonCombatMinimax() {}

    public static int bestSlot(MoonFightCombatContext ctx, MoonCombatBrainState brain, int depth) {
	if(ctx == null || brain == null || depth < 2)
	    return MoonCombatAI.MoonCombatDecisionMaker.pickSlot(ctx, brain);
	int ip0 = ctx.ipDelta;
	MoonFightCombatContext.HandCard best = null;
	double bestV = Double.NEGATIVE_INFINITY;
	for(MoonFightCombatContext.HandCard c : ctx.hand) {
	    if(!c.ready)
		continue;
	    double v = expectAfterOurCard(ctx, brain, c, ip0, depth);
	    if(v > bestV) {
		bestV = v;
		best = c;
	    }
	}
	return best == null ? -1 : best.slot;
    }

    private static double expectAfterOurCard(MoonFightCombatContext ctx, MoonCombatBrainState brain,
	MoonFightCombatContext.HandCard c, int ipDelta, int depth) {
	int ip1 = ipDelta + MoonCombatAI.ipDeltaEstimateOurCard(c, ctx);
	double[] mix = MoonCombatAI.MoonOpponentPredictor.archetypeMix(ctx, brain);
	double ev = 0;
	for(int a = 0; a < 3; a++) {
	    int ip2 = ip1 - MoonCombatAI.oppSwingEstimate(a);
	    double rest = depth >= 3 ? secondPlyBest(ctx, brain, ip2, c) : 0;
	    ev += mix[a] * (evaluatePosition(ip2, ctx) + rest);
	}
	double risk = MoonCombatAI.cardRisk(c);
	ev -= MoonConfig.combatBotRiskAversion * risk * 25;
	ev += MoonCombatAI.openingRedundancyPenalty(ctx, brain, c);
	return ev;
    }

    /** Top-2 continuation after hypothetical first card (same hand, reduced IP — no CD model). */
    private static double secondPlyBest(MoonFightCombatContext ctx, MoonCombatBrainState brain, int ipAfter, MoonFightCombatContext.HandCard first) {
	double best = Double.NEGATIVE_INFINITY;
	int n = 0;
	for(MoonFightCombatContext.HandCard c : ctx.hand) {
	    if(!c.ready || c.slot == first.slot)
		continue;
	    int ip1 = ipAfter + MoonCombatAI.ipDeltaEstimateOurCard(c, ctx);
	    double[] mix = MoonCombatAI.MoonOpponentPredictor.archetypeMix(ctx, brain);
	    double ev = 0;
	    for(int a = 0; a < 3; a++) {
		int ip2 = ip1 - MoonCombatAI.oppSwingEstimate(a);
		ev += mix[a] * evaluatePosition(ip2, ctx);
	    }
	    ev -= MoonConfig.combatBotRiskAversion * MoonCombatAI.cardRisk(c) * 15;
	    if(ev > best)
		best = ev;
	    if(++n >= 2)
		break;
	}
	return best > Double.NEGATIVE_INFINITY ? best * 0.55 : 0;
    }

    private static double evaluatePosition(int ipDelta, MoonFightCombatContext ctx) {
	double base = Math.tanh(ipDelta * 0.22) * 180;
	double tight = -Math.abs(ipDelta) * 0.8;
	return base + tight + ctx.ipSelf * 0.02;
    }
}
