package haven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable snapshot of fight UI state for card AI. The vanilla client does not expose
 * numeric "breach" percentages — only {@link Fightview.Relation#ip}/{@code oip}, card
 * resources, cooldowns, server {@link Fightsess#use} hints, and {@link Buff} widgets.
 */
public class MoonFightCombatContext {

    public static final class BuffSnapshot {
	public final String resName;
	public final String line;
	public final Double ameter;
	public final Double cmeter;

	public BuffSnapshot(String resName, String line, Double ameter, Double cmeter) {
	    this.resName = resName != null ? resName : "";
	    this.line = line != null ? line : "";
	    this.ameter = ameter;
	    this.cmeter = cmeter;
	}
    }

    public static final class HandCard {
	public final int slot;
	public final String resName;
	/** Lowercased path name + tooltip for keyword heuristics. */
	public final String text;
	public final boolean ready;
	/** Seconds until ready; 0 if {@link #ready}. */
	public final double cdRemaining;
	public final boolean serverPrimaryHint;
	public final boolean serverAltHint;

	public HandCard(int slot, String resName, String text, boolean ready, double cdRemaining,
	    boolean serverPrimaryHint, boolean serverAltHint) {
	    this.slot = slot;
	    this.resName = resName;
	    this.text = text;
	    this.ready = ready;
	    this.cdRemaining = cdRemaining;
	    this.serverPrimaryHint = serverPrimaryHint;
	    this.serverAltHint = serverAltHint;
	}
    }

    public final List<HandCard> hand;
    public final int ipSelf;
    public final int ipOpp;
    /** {@code ipSelf - ipOpp}; positive means you are ahead on IP. */
    public final int ipDelta;
    public final List<String> selfBuffSummaries;
    public final List<String> oppBuffSummaries;
    public final List<BuffSnapshot> selfBuffSnapshots;
    public final List<BuffSnapshot> oppBuffSnapshots;
    public final String lastSelfResName;
    public final String lastOppResName;
    /** Lowercased name+tooltip of last played cards (for synergy / prediction). */
    public final String lastSelfCardText;
    public final String lastOppCardText;
    public final double now;

    public MoonFightCombatContext(List<HandCard> hand, int ipSelf, int ipOpp, List<String> selfBuffSummaries,
	List<String> oppBuffSummaries, List<BuffSnapshot> selfBuffSnapshots, List<BuffSnapshot> oppBuffSnapshots,
	String lastSelfResName, String lastOppResName,
	String lastSelfCardText, String lastOppCardText, double now) {
	this.hand = Collections.unmodifiableList(new ArrayList<>(hand));
	this.ipSelf = ipSelf;
	this.ipOpp = ipOpp;
	this.ipDelta = ipSelf - ipOpp;
	this.selfBuffSummaries = Collections.unmodifiableList(new ArrayList<>(selfBuffSummaries));
	this.oppBuffSummaries = Collections.unmodifiableList(new ArrayList<>(oppBuffSummaries));
	this.selfBuffSnapshots = Collections.unmodifiableList(new ArrayList<>(selfBuffSnapshots));
	this.oppBuffSnapshots = Collections.unmodifiableList(new ArrayList<>(oppBuffSnapshots));
	this.lastSelfResName = lastSelfResName != null ? lastSelfResName : "";
	this.lastOppResName = lastOppResName != null ? lastOppResName : "";
	this.lastSelfCardText = lastSelfCardText != null ? lastSelfCardText : "";
	this.lastOppCardText = lastOppCardText != null ? lastOppCardText : "";
	this.now = now;
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

    private static String resNameOnly(Indir<Resource> ir) {
	if(ir == null)
	    return "";
	try {
	    Resource r = ir.get();
	    return r != null && r.name != null ? r.name : "";
	} catch(Loading ignored) {
	    return "";
	}
    }

    /** Public for opponent observation outside {@link #capture}. */
    public static String fightCardText(Indir<Resource> ir) {
	return resourceCardText(ir);
    }

    private static String resourceCardText(Indir<Resource> ir) {
	if(ir == null)
	    return "";
	try {
	    Resource r = ir.get();
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

    private static void collectBuffSnapshots(Bufflist bl, List<BuffSnapshot> snaps, List<String> lines) {
	if(bl == null)
	    return;
	for(Buff b : bl.children(Buff.class)) {
	    if(b == null || b.res == null)
		continue;
	    try {
		Resource r = b.res.get();
		if(r == null)
		    continue;
		String rn = r.name != null ? r.name : "";
		StringBuilder sb = new StringBuilder();
		if(r.name != null)
		    sb.append(r.name);
		Resource.Tooltip tt = r.layer(Resource.tooltip);
		if(tt != null && tt.t != null) {
		    if(sb.length() > 0)
			sb.append(' ');
		    sb.append(tt.t);
		}
		String line = sb.toString().trim();
		if(!line.isEmpty())
		    lines.add(line);
		Double am = null, cm = null;
		try {
		    am = b.buffameter();
		} catch(Loading ignored) {}
		try {
		    cm = b.buffcmeter();
		} catch(Loading ignored) {}
		snaps.add(new BuffSnapshot(rn, line, am, cm));
	    } catch(Loading ignored) {}
	}
    }

    /**
     * Build context; safe to call every tick (no heavy {@link ItemInfo} on buffs — only resource tooltips).
     */
    public static MoonFightCombatContext capture(Fightsess fs, Fightview fv, Fightview.Relation rel, double now) {
	List<HandCard> hand = new ArrayList<>();
	int n = fs != null && fs.actions != null ? fs.actions.length : 0;
	int use = fs != null ? fs.use : -1;
	int useb = fs != null ? fs.useb : -1;
	for(int i = 0; i < n; i++) {
	    Fightsess.Action act = fs.actions[i];
	    if(act == null)
		continue;
	    String text = cardText(act);
	    String rn = "";
	    try {
		Resource r = act.res.get();
		if(r != null && r.name != null)
		    rn = r.name;
	    } catch(Loading ignored) {}
	    boolean ready = !(act.ct > 0 && now < act.ct);
	    double cd = (act.ct > 0 && now < act.ct) ? (act.ct - now) : 0;
	    hand.add(new HandCard(i, rn, text, ready, cd, i == use, i == useb));
	}

	int ipS = 0, ipO = 0;
	if(rel != null) {
	    ipS = rel.ip;
	    ipO = rel.oip;
	}

	List<String> selfB = new ArrayList<>();
	List<String> oppB = new ArrayList<>();
	List<BuffSnapshot> selfSn = new ArrayList<>();
	List<BuffSnapshot> oppSn = new ArrayList<>();
	if(fv != null) {
	    collectBuffSnapshots(fv.buffs, selfSn, selfB);
	    if(rel != null)
		collectBuffSnapshots(rel.buffs, oppSn, oppB);
	}

	String lastSelf = fv != null ? resNameOnly(fv.lastact) : "";
	String lastOpp = rel != null ? resNameOnly(rel.lastact) : "";
	String lastSelfTx = fv != null ? resourceCardText(fv.lastact) : "";
	String lastOppTx = rel != null ? resourceCardText(rel.lastact) : "";

	return new MoonFightCombatContext(hand, ipS, ipO, selfB, oppB, selfSn, oppSn, lastSelf, lastOpp, lastSelfTx, lastOppTx, now);
    }
}
