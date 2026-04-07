package haven;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Per-opponent fight memory: recent self card types (for synergy chains), Markov-style move counts.
 * Not persisted across sessions unless extended.
 */
public final class MoonCombatBrainState {

    public static final int MAX_SELF_HISTORY = 8;
    public static final int MAX_PROFILES = 48;

    private long activeOppGob = -1;
    private final Deque<String> selfTypeHistory = new ArrayDeque<>();
    /** Newest-first slots we actually played (for stuck server highlight detection). */
    private final Deque<Integer> selfSlotHistory = new ArrayDeque<>();
    private final Map<Long, OpponentProfile> profiles = new HashMap<>();
    /** Max ready opening-type cards seen in one hand snapshot this fight — ≥2 suggests a multi-opening deck. */
    private int maxReadyOpeningSeen = 0;

    public void beginOpponent(long gobid) {
	if(gobid != activeOppGob) {
	    activeOppGob = gobid;
	    selfTypeHistory.clear();
	    selfSlotHistory.clear();
	    maxReadyOpeningSeen = 0;
	}
    }

    public void endFight() {
	activeOppGob = -1;
	selfTypeHistory.clear();
	selfSlotHistory.clear();
	maxReadyOpeningSeen = 0;
    }

    public long activeGob() {
	return activeOppGob;
    }

    public OpponentProfile profile(long gobid) {
	return profiles.computeIfAbsent(gobid, g -> new OpponentProfile());
    }

    public void observeOpponentPlay(MoonCombatAI.CardArchetype a) {
	if(activeOppGob < 0)
	    return;
	OpponentProfile p = profile(activeOppGob);
	p.recordOppArchetype(a);
    }

    /** Call after we commit a card (resource name + table/heuristic type key). */
    public void recordSelfPlay(String typeKeyLower) {
	if(typeKeyLower == null)
	    return;
	String k = typeKeyLower.toLowerCase(Locale.ROOT);
	selfTypeHistory.addFirst(k);
	while(selfTypeHistory.size() > MAX_SELF_HISTORY)
	    selfTypeHistory.removeLast();
    }

    public Deque<String> selfTypeHistoryView() {
	return selfTypeHistory;
    }

    /** Call each tick from combat AI with count of ready opening-archetype cards in hand. */
    public void observeReadyOpeningInHand(int readyOpeningCards) {
	if(readyOpeningCards > maxReadyOpeningSeen)
	    maxReadyOpeningSeen = readyOpeningCards;
    }

    public boolean likelySingleOpeningDeck() {
	return maxReadyOpeningSeen < 2;
    }

    public int selfOpeningPlaysThisFight() {
	int n = 0;
	for(String h : selfTypeHistory) {
	    if("opening".equals(h))
		n++;
	}
	return n;
    }

    public void recordSelfSlot(int slot) {
	if(slot < 0)
	    return;
	selfSlotHistory.addFirst(slot);
	while(selfSlotHistory.size() > MAX_SELF_HISTORY)
	    selfSlotHistory.removeLast();
    }

    /**
     * True if our last two committed plays used {@code hintSlot} — UI often keeps {@link Fightsess#use}
     * on the same index while the bar reorders; trusting it spams one card in logs.
     */
    public boolean serverHintLikelyStuckOn(int hintSlot) {
	if(hintSlot < 0 || selfSlotHistory.size() < 2)
	    return false;
	Iterator<Integer> it = selfSlotHistory.iterator();
	return hintSlot == it.next() && hintSlot == it.next();
    }

    public static final class OpponentProfile {
	private static final int N = 5;
	private final int[][] markov = new int[N][N];
	private int lastArchetypeIdx = -1;
	public int oppAggressivePlays;
	public int oppDefensivePlays;

	private static int idx(MoonCombatAI.CardArchetype a) {
	    return switch(a) {
	    case DEFENSE -> 0;
	    case AGGRESSION -> 1;
	    case OPENING -> 2;
	    case UTILITY -> 3;
	    default -> 4;
	    };
	}

	public void recordOppArchetype(MoonCombatAI.CardArchetype a) {
	    int j = idx(a);
	    if(a == MoonCombatAI.CardArchetype.AGGRESSION)
		oppAggressivePlays++;
	    else if(a == MoonCombatAI.CardArchetype.DEFENSE)
		oppDefensivePlays++;
	    if(lastArchetypeIdx >= 0)
		markov[lastArchetypeIdx][j]++;
	    lastArchetypeIdx = j;
	}

	/** P(next archetype | last observed) from counts + Laplace smoothing. */
	public double[] nextArchetypeDistribution(MoonCombatAI.CardArchetype last) {
	    int i = idx(last);
	    double[] p = new double[4];
	    double sum = 0;
	    for(int j = 0; j < 4; j++) {
		p[j] = markov[i][j] + 1.0;
		sum += p[j];
	    }
	    if(sum <= 0)
		sum = 1;
	    for(int j = 0; j < 4; j++)
		p[j] /= sum;
	    return p;
	}
    }
}
