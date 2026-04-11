package haven;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;

/**
 * Strict passive gate assist for small wooden / straw wickets only.
 * Explicitly excludes palisade / brick / iron-like gates and all big gates.
 */
public final class MoonPassiveGate {
    private static final double SEARCH_RADIUS = 40 * 11;
    private static final double PASS_LATERAL_CLAMP = 4;
    private static final double PASS_OFFSET = 18;
    private static final double CROSSING_HALF_WIDTH = 12;
    private static final double BIG_PASS_LATERAL_CLAMP = 12;
    private static final double BIG_PASS_OFFSET = 34;
    private static final double BIG_CROSSING_HALF_WIDTH = 32;
    private static final double FUZZY_SEGMENT_RADIUS = 34;
    private static final double SEGMENT_PAD = BIG_CROSSING_HALF_WIDTH + BIG_PASS_OFFSET + 8;
    private static final String[] EXCLUDED_TOKENS = {
	"palisade", "brick", "brickwall", "iron", "steel", "metal"
    };
    private static final Path DEBUG_LOG = Paths.get(System.getProperty("user.home", "."), ".haven", "moon-passivegate-debug.log");

    private static volatile String lastAction = "";
    private static volatile String blockedReason = "";

    private MoonPassiveGate() {}

    private static final class GateDecision {
	final String name;
	final boolean allowed;
	final String reason;
	final boolean big;

	GateDecision(String name, boolean allowed, String reason) {
	    this(name, allowed, reason, false);
	}

	GateDecision(String name, boolean allowed, String reason, boolean big) {
	    this.name = name;
	    this.allowed = allowed;
	    this.reason = reason;
	    this.big = big;
	}
    }

    private static final class GateCrossing {
	final Gob gate;
	final GateDecision gateDecision;
	final double crossY;
	final double gateDistance;

	GateCrossing(Gob gate, GateDecision gateDecision, double crossY, double gateDistance) {
	    this.gate = gate;
	    this.gateDecision = gateDecision;
	    this.crossY = crossY;
	    this.gateDistance = gateDistance;
	}
    }

    public static final class Assist {
	public final long gateId;
	public final Coord2d target;
	public final Coord2d finalTarget;
	public final String gateName;
	public final boolean bigGate;
	private final double targetSide;
	private final double approachRadius;
	private final double closeRadius;

	private Assist(Gob gate, String gateName, Coord2d target, Coord2d finalTarget, boolean bigGate) {
	    Coord2d localTarget = target.sub(gate.rc).rot(-gate.a);
	    this.gateId = gate.id;
	    this.gateName = gateName;
	    this.target = target;
	    this.finalTarget = finalTarget;
	    this.bigGate = bigGate;
	    this.targetSide = (localTarget.x >= 0) ? 1.0 : -1.0;
	    double pass = bigGate ? BIG_PASS_OFFSET : PASS_OFFSET;
	    this.approachRadius = pass + 10.0;
	    this.closeRadius = this.approachRadius + (bigGate ? 24.0 : 12.0);
	}

	public Gob gate(MapView map) {
	    if(map == null || map.glob == null || map.glob.oc == null)
		return null;
	    return map.glob.oc.getgob(gateId);
	}

	public boolean playerNearGate(MapView map, Gob player) {
	    Gob gate = gate(map);
	    return gate != null && player != null && player.rc.dist(gate.rc) <= approachRadius;
	}

	public boolean playerPassed(MapView map, Gob player) {
	    Gob gate = gate(map);
	    if(gate == null || player == null)
		return false;
	    try {
		Coord2d localPlayer = player.rc.sub(gate.rc).rot(-gate.a);
		if((localPlayer.x * targetSide) > 6.0)
		    return true;
	    } catch(Loading ignored) {
	    }
	    return player.rc.dist(target) <= 6.0;
	}

	public boolean playerCrossedPlane(MapView map, Gob player) {
	    Gob gate = gate(map);
	    if(gate == null || player == null)
		return false;
	    try {
		Coord2d localPlayer = player.rc.sub(gate.rc).rot(-gate.a);
		return (localPlayer.x * targetSide) > 1.0;
	    } catch(Loading ignored) {
		return false;
	    }
	}

	public boolean playerCanClose(MapView map, Gob player) {
	    Gob gate = gate(map);
	    return gate != null && player != null && player.rc.dist(gate.rc) <= closeRadius;
	}

	public boolean hasContinueTarget() {
	    return finalTarget != null && finalTarget.dist(target) > 8.0;
	}

	public Boolean gateLikelyOpen(MapView map) {
	    Gob gate = gate(map);
	    if(gate == null)
		return null;
	    ResDrawable rd = gate.getattr(ResDrawable.class);
	    if(rd == null || rd.sdt == null || rd.sdt.rbuf == null || rd.sdt.rbuf.length == 0)
		return null;
	    return (rd.sdt.rbuf[0] != 0);
	}
    }

    public static void resetStatus() {
	lastAction = "";
	blockedReason = "";
    }

    public static void noteAction(String action) {
	lastAction = (action == null) ? "" : action;
	blockedReason = "";
	MoonAutomationRegistry.noteAction("passivegate", lastAction);
	debug("action " + lastAction);
    }

    public static void noteBlocked(String reason) {
	blockedReason = (reason == null) ? "" : reason;
	MoonAutomationRegistry.noteBlocked("passivegate", blockedReason);
	debug("blocked " + blockedReason);
    }

    public static String statusSummary() {
	if(!MoonConfig.passiveSmallGate)
	    return "disabled";
	if(blockedReason != null && !blockedReason.isBlank())
	    return "blocked: " + blockedReason;
	if(lastAction != null && !lastAction.isBlank())
	    return lastAction;
	return "armed (small gate / polegate / wicket)";
    }

    public static Coord2d resolveTarget(MapView map, Coord2d requested, ClickData inf, int clickb, int mods) {
	Assist assist = resolveAssist(map, requested, inf, clickb, mods);
	return (assist == null) ? null : assist.target;
    }

    public static Assist resolveAssist(MapView map, Coord2d requested, ClickData inf, int clickb, int mods) {
	if(!MoonConfig.passiveSmallGate || map == null || requested == null)
	    return null;
	if(clickb != 1 || ((mods & UI.MOD_META) != 0))
	    return null;
	GameUI gui = map.getparent(GameUI.class);
	if(gui == null || gui.vhand != null)
	    return null;
	Gob player = map.player();
	if(player == null)
	    return null;

	Gob clicked = gobFromClickData(inf);
	if(clicked != null) {
	    GateDecision decision = inspectGate(clicked);
	    if(!decision.allowed) {
		if(decision.reason != null)
		    noteBlocked(decision.reason);
		return null;
	    }
	    double crossY;
	    try {
		crossY = requested.sub(clicked.rc).rot(-clicked.a).y;
	    } catch(Loading ignored) {
		crossY = 0.0;
	    }
	    double halfWidth = crossingHalfWidth(decision);
	    if(Math.abs(crossY) > halfWidth) {
		try {
		    /* Direct click on the wicket itself is still intentional even if the terrain probe lands off-center. */
		    crossY = player.rc.sub(clicked.rc).rot(-clicked.a).y;
		} catch(Loading ignored) {
		    crossY = 0.0;
		}
	    }
	    if(Math.abs(crossY) > halfWidth)
		crossY = 0.0;
	    Assist assist = createAssist(player.rc, clicked, decision, crossY, null);
	    noteAction("assist " + decision.name);
	    return assist;
	}
	GateCrossing crossing = findCrossingGate(map, player.rc, requested);
	if(crossing == null)
	    return null;
	Assist assist = createAssist(player.rc, crossing.gate, crossing.gateDecision, crossing.crossY, requested);
	noteAction("assist " + crossing.gateDecision.name);
	return assist;
    }

    private static GateCrossing findCrossingGate(MapView map, Coord2d playerRc, Coord2d requested) {
	GateCrossing result = null;
	double targetDistance = playerRc.dist(requested);
	if(targetDistance <= 0.01)
	    return null;
	double minx = Math.min(playerRc.x, requested.x) - SEGMENT_PAD;
	double miny = Math.min(playerRc.y, requested.y) - SEGMENT_PAD;
	double maxx = Math.max(playerRc.x, requested.x) + SEGMENT_PAD;
	double maxy = Math.max(playerRc.y, requested.y) + SEGMENT_PAD;
	for(Gob gob : map.glob.oc) {
	    try {
		Coord2d grc = gob.rc;
		if(grc.x < minx || grc.x > maxx || grc.y < miny || grc.y > maxy)
		    continue;
		double gateDistance = grc.dist(playerRc);
		if(gateDistance >= SEARCH_RADIUS || gateDistance >= targetDistance)
		    continue;
		GateDecision gate = inspectGate(gob);
		if(!gate.allowed)
		    continue;
		Coord2d playerLocal = playerRc.sub(gob.rc).rot(-gob.a);
		Coord2d targetLocal = requested.sub(gob.rc).rot(-gob.a);
		double denom = playerLocal.x - targetLocal.x;
		if(Math.abs(denom) < 0.001) {
		    result = fuzzyCrossing(result, gob, gate, playerRc, requested, gateDistance);
		    continue;
		}
		double t = playerLocal.x / denom;
		if(t <= 0 || t >= 1) {
		    result = fuzzyCrossing(result, gob, gate, playerRc, requested, gateDistance);
		    continue;
		}
		double crossY = playerLocal.y + ((targetLocal.y - playerLocal.y) * t);
		if(Math.abs(crossY) > crossingHalfWidth(gate)) {
		    result = fuzzyCrossing(result, gob, gate, playerRc, requested, gateDistance);
		    continue;
		}
		if(result == null || gateDistance < result.gateDistance)
		    result = new GateCrossing(gob, gate, crossY, gateDistance);
	    } catch(Loading ignored) {
	    }
	}
	return result;
    }

    private static GateCrossing fuzzyCrossing(GateCrossing current, Gob gob, GateDecision gate,
					      Coord2d playerRc, Coord2d requested, double gateDistance) {
	double targetDistance = playerRc.dist(requested);
	if(targetDistance <= 0.01 || gateDistance >= targetDistance)
	    return current;
	Coord2d ab = requested.sub(playerRc);
	double len2 = (ab.x * ab.x) + (ab.y * ab.y);
	if(len2 <= 0.0001)
	    return current;
	Coord2d ag = gob.rc.sub(playerRc);
	double t = ((ag.x * ab.x) + (ag.y * ab.y)) / len2;
	if(t <= 0.05 || t >= 0.98)
	    return current;
	Coord2d closest = playerRc.add(ab.mul(t));
	if(closest.dist(gob.rc) > FUZZY_SEGMENT_RADIUS)
	    return current;
	double crossY = 0.0;
	try {
	    Coord2d playerLocal = playerRc.sub(gob.rc).rot(-gob.a);
	    Coord2d targetLocal = requested.sub(gob.rc).rot(-gob.a);
	    double y = (Math.abs(playerLocal.y) <= Math.abs(targetLocal.y)) ? playerLocal.y : targetLocal.y;
	    double halfWidth = crossingHalfWidth(gate);
	    crossY = Math.max(-halfWidth, Math.min(halfWidth, y));
	} catch(Loading ignored) {
	}
	if(current == null || gateDistance < current.gateDistance)
	    return new GateCrossing(gob, gate, crossY, gateDistance);
	return current;
    }

    private static GateDecision inspectGate(Gob gob) {
	if(gob == null)
	    return new GateDecision(null, false, null);
	String full = MoonGobKind.resourceName(gob);
	if(full == null || full.isBlank())
	    return new GateDecision(null, false, null);
	full = full.toLowerCase(Locale.ROOT);
	String name = basename(full);
	for(String ex : EXCLUDED_TOKENS) {
	    if(name.contains(ex) || full.contains(ex))
		return new GateDecision(null, false, "excluded by allowlist (" + ex + ") " + full);
	}
	if("polegate".equals(name))
	    return new GateDecision(name, true, null);
	if("polebiggate".equals(name))
	    return new GateDecision(name, true, null, true);
	if("gate".equals(name) && full.contains("/arch/"))
	    return new GateDecision(name, true, null);
	if(name.contains("wicket") || full.contains("wicket"))
	    return new GateDecision(name.isEmpty() ? full : name, true, null);
	if(name.startsWith("pole") && name.contains("gate") && full.contains("/arch/"))
	    return new GateDecision(name, true, null, name.contains("big"));
	if(looksGateish(name, full))
	    return new GateDecision(null, false, "unsupported gate type " + full);
	return new GateDecision(null, false, null);
    }

    private static String basename(String full) {
	if(full == null)
	    return "";
	int p = Math.max(full.lastIndexOf('/'), full.lastIndexOf('\\'));
	return (p >= 0) ? full.substring(p + 1) : full;
    }

    private static boolean looksGateish(String name, String full) {
	String n = (name == null) ? "" : name;
	String f = (full == null) ? "" : full;
	return n.contains("gate") || f.contains("gate") || n.contains("wicket") || f.contains("wicket");
    }

    private static Gob gobFromClickData(ClickData inf) {
	if(inf == null || inf.ci == null)
	    return null;
	if(inf.ci instanceof Gob.GobClick)
	    return ((Gob.GobClick)inf.ci).gob;
	if(inf.ci instanceof Composited.CompositeClick)
	    return ((Composited.CompositeClick)inf.ci).gi.gob;
	return null;
    }

    private static double crossingHalfWidth(GateDecision gate) {
	return (gate != null && gate.big) ? BIG_CROSSING_HALF_WIDTH : CROSSING_HALF_WIDTH;
    }

    private static Coord2d destinationBeyondGate(Coord2d playerRc, Gob gate, GateDecision decision, double crossY) {
	Coord2d playerLocal = playerRc.sub(gate.rc).rot(-gate.a);
	double direction = (playerLocal.x <= 0) ? 1.0 : -1.0;
	double lateral = (decision != null && decision.big) ? BIG_PASS_LATERAL_CLAMP : PASS_LATERAL_CLAMP;
	double pass = (decision != null && decision.big) ? BIG_PASS_OFFSET : PASS_OFFSET;
	double y = Math.max(-lateral, Math.min(lateral, crossY));
	return gate.rc.add(new Coord2d(direction * pass, y).rot(gate.a));
    }

    private static Assist createAssist(Coord2d playerRc, Gob gate, GateDecision decision, double crossY, Coord2d finalTarget) {
	Coord2d target = destinationBeyondGate(playerRc, gate, decision, crossY);
	String gateName = (decision == null || decision.name == null) ? "gate" : decision.name;
	boolean big = decision != null && decision.big;
	return new Assist(gate, gateName, target, (finalTarget == null) ? target : finalTarget, big);
    }

    private static void debug(String line) {
	if(line == null || line.isBlank())
	    return;
	try {
	    Path dir = DEBUG_LOG.getParent();
	    if(dir != null)
		Files.createDirectories(dir);
	    Files.write(DEBUG_LOG, (Instant.now() + " " + line + "\n").getBytes(StandardCharsets.UTF_8),
		StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	} catch(Exception ignored) {
	}
    }
}
