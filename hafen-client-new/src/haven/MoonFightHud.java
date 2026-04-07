package haven;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks fight IP deltas per opponent and shows floating readouts + mob HP bars.
 * Red/yellow split is a client-side partition of each IP loss (not exact wound types).
 * Received damage totals persist in prefs ({@code moon-fight-taken-r/y/a}) until reset.
 */
public final class MoonFightHud {
    private MoonFightHud() {}
    private static final boolean DEBUG_FIGHT = false;
    private static final int DAMAGE_LINE_CACHE_MAX = 256;

    private static final float HEAD_Z = 20f;
    private static final int BAR_W = UI.scale(56);
    private static final int BAR_H = UI.scale(5);

    private static final Map<Long, MobTrack> byGob = new ConcurrentHashMap<>();
    /** Session memory: keeps dealt totals even if fight relations are recreated for the same gob. */
    private static final Map<Long, int[]> dealtMemo = new ConcurrentHashMap<>();
    private static final Map<Long, Coord2d> dealtLastPos = new ConcurrentHashMap<>();
    private static volatile int takenR = Utils.getprefi("moon-fight-taken-r", 0);
    private static volatile int takenY = Utils.getprefi("moon-fight-taken-y", 0);
    private static volatile int takenA = Utils.getprefi("moon-fight-taken-a", 0);
    private static volatile long playerGobId = -1;
    private static final Map<String, Tex> dmgLineCache = new ConcurrentHashMap<>();

    private static final class MobTrack {
	final long gobid;
	int lastIp, lastOip;
	int dealtR, dealtY, dealtA;

	MobTrack(long gobid, int ip, int oip) {
	    this.gobid = gobid;
	    this.lastIp = ip;
	    this.lastOip = oip;
	}
    }

    private static void persistTaken() {
	Utils.setprefi("moon-fight-taken-r", takenR);
	Utils.setprefi("moon-fight-taken-y", takenY);
	Utils.setprefi("moon-fight-taken-a", takenA);
	try { Utils.prefs().flush(); } catch(Exception ignored) {}
    }

    public static void onRelationBegin(long gobid, int ip, int oip) {
	MobTrack tr = byGob.get(gobid);
	if(tr == null) {
	    tr = new MobTrack(gobid, ip, oip);
	    int[] memo = dealtMemo.get(gobid);
	    if(memo != null && memo.length >= 2) {
		tr.dealtR = memo[0];
		tr.dealtY = memo[1];
		if(memo.length >= 3)
		    tr.dealtA = memo[2];
	    }
	} else {
	    tr.lastIp = ip;
	    tr.lastOip = oip;
	}
	byGob.put(gobid, tr);
    }

    public static void onRelationRemoved(long gobid) {
	MobTrack tr = byGob.get(gobid);
	if(tr != null)
	    dealtMemo.put(gobid, new int[] {tr.dealtR, tr.dealtY, tr.dealtA});
	byGob.remove(gobid);
    }

    public static void setPlayerGobId(long gobid) {
	playerGobId = gobid;
    }

    /** Damage float overlays (gfx/fx/floatimg) for accurate totals. */
    public static void onFloatDamage(long gobid, int dmg, int clr, Coord2d wpos) {
	if(dmg <= 0)
	    return;
	boolean red = (clr == 61455);   /* 0xF00F => 255,0,0 */
	boolean yellow = (clr == 64527);/* 0xFC0F => 255,204,0 */
	boolean armor = (clr == 36751); /* 0x8F8F => 136,255,136 */
	if(!red && !yellow && !armor)
	    return;
	if(gobid == playerGobId) {
	    if(armor)      takenA += dmg;
	    else if(yellow) takenY += dmg;
	    else            takenR += dmg;
	    persistTaken();
	    return;
	}
	if(MoonConfig.combatHudPersistScope == 0 && !byGob.containsKey(gobid) && !dealtMemo.containsKey(gobid))
	    return;
	MobTrack tr = byGob.get(gobid);
	int r = 0, y = 0, a = 0;
	if(tr != null) {
	    r = tr.dealtR;
	    y = tr.dealtY;
	    a = tr.dealtA;
	} else {
	    int[] old = dealtMemo.get(gobid);
	    if(old != null) {
		if(old.length > 0) r = old[0];
		if(old.length > 1) y = old[1];
		if(old.length > 2) a = old[2];
	    }
	}
	if(armor)      a += dmg;
	else if(yellow) y += dmg;
	else            r += dmg;
	if(tr != null) {
	    tr.dealtR = r;
	    tr.dealtY = y;
	    tr.dealtA = a;
	}
	dealtMemo.put(gobid, new int[] {r, y, a});
	if(wpos != null)
	    dealtLastPos.put(gobid, wpos);
    }

    /** Call before assigning new ip/oip on the relation; uses stored {@link Fightview.Relation#ip}/{@link Fightview.Relation#oip} as old values. */
    public static void onRelationIpUpdate(Fightview fv, Fightview.Relation rel, int newIp, int newOip) {
	if(rel == null)
	    return;
	MobTrack tr = byGob.get(rel.gobid);
	if(tr == null) {
	    onRelationBegin(rel.gobid, rel.ip, rel.oip);
	    tr = byGob.get(rel.gobid);
	    if(tr == null)
		return;
	}
	boolean swap = MoonConfig.combatHudSwapFightIp;
	int oldOppA = swap ? tr.lastIp : tr.lastOip;
	int nowOppA = swap ? newIp : newOip;
	int oldOppB = swap ? tr.lastOip : tr.lastIp;
	int nowOppB = swap ? newOip : newIp;
	int oldSelfA = swap ? tr.lastOip : tr.lastIp;
	int nowSelfA = swap ? newOip : newIp;
	int oldSelfB = swap ? tr.lastIp : tr.lastOip;
	int nowSelfB = swap ? newIp : newOip;

	int dOpp = (oldOppA > nowOppA) ? (oldOppA - nowOppA) : ((oldOppB > nowOppB) ? (oldOppB - nowOppB) : 0);
	if(dOpp > 0) {
	    tr.dealtR += (dOpp * 7) / 10;
	    tr.dealtY += dOpp - (dOpp * 7) / 10;
	    dealtMemo.put(rel.gobid, new int[] {tr.dealtR, tr.dealtY, tr.dealtA});
	}
	int dSelf = (oldSelfA > nowSelfA) ? (oldSelfA - nowSelfA) : ((oldSelfB > nowSelfB) ? (oldSelfB - nowSelfB) : 0);
	if(DEBUG_FIGHT) {
	    fightLog(String.format(
		"upd gob=%d old(ip=%d,oip=%d) new(ip=%d,oip=%d) dOpp=%d dSelf=%d cur=%s dealt=%d/%d/%d taken=%d/%d/%d swap=%s",
		rel.gobid, tr.lastIp, tr.lastOip, newIp, newOip, dOpp, dSelf,
		(fv.current != null ? Long.toString(fv.current.gobid) : "null"),
		tr.dealtR, tr.dealtY, tr.dealtA, takenR, takenY, takenA, Boolean.toString(swap)));
	}
	tr.lastIp = newIp;
	tr.lastOip = newOip;
    }

    public static void resetTakenDamage() {
	takenR = takenY = takenA = 0;
	Utils.setprefi("moon-fight-taken-r", 0);
	Utils.setprefi("moon-fight-taken-y", 0);
	Utils.setprefi("moon-fight-taken-a", 0);
	try { Utils.prefs().flush(); } catch(Exception ignored) {}
    }

    public static void draw(GOut g, MapView mv) {
	if(!MoonConfig.combatDamageHud)
	    return;
	GameUI gui = mv.getparent(GameUI.class);
	if(gui == null || gui.fv == null)
	    return;
	Fightview fv = gui.fv;
	boolean showTakenFooter = MoonConfig.combatHudShowTaken && (takenR > 0 || takenY > 0 || takenA > 0);
	boolean haveDealtMemo = !dealtMemo.isEmpty();
	if(fv.lsrel.isEmpty() && byGob.isEmpty() && !showTakenFooter && !haveDealtMemo)
	    return;

	Gob pl;
	try {
	    pl = mv.player();
	} catch(Loading e) {
	    return;
	}
	if(pl != null && MoonConfig.combatHudShowTaken && ((takenR > 0 || takenY > 0 || takenA > 0) || !fv.lsrel.isEmpty())) {
	    Coord3f pc;
	    try {
		pc = pl.getc();
	    } catch(Exception e) {
		pc = null;
	    }
	    if(pc != null) {
		Coord3f sc = combatAnchor(mv, pc);
		if(sc != null) {
		    int x = (int)sc.x;
		    int ty = (int)sc.y - UI.scale(36);
		    ty = drawDamageLine(g, takenR, takenY, takenA, x, ty);
		}
	    }
	}

	int hpMode = MoonConfig.combatHudEnemyHpMode;
	Set<Long> drawnDamage = new HashSet<>();
	for(Fightview.Relation rel : fv.lsrel) {
	    if(rel == null || rel.invalid)
		continue;
	    Gob gob;
	    try {
		gob = mv.glob.oc.getgob(rel.gobid);
	    } catch(Loading e) {
		continue;
	    }
	    if(gob == null)
		continue;
	    MobTrack tr = byGob.get(rel.gobid);
	    Coord3f gc;
	    try {
		gc = gob.getc();
	    } catch(Exception e) {
		continue;
	    }
	    if(gc == null)
		continue;
	    Coord3f scc = combatAnchor(mv, gc);
	    if(scc == null)
		continue;
	    int x = (int)scc.x;
	    int ty = (int)scc.y - UI.scale(8);
	    dealtLastPos.put(rel.gobid, new Coord2d(gc.x, gc.y));

	    int dealtR = (tr == null) ? 0 : tr.dealtR;
	    int dealtY = (tr == null) ? 0 : tr.dealtY;
	    int dealtA = (tr == null) ? 0 : tr.dealtA;
	    if(MoonConfig.combatHudShowDealt) {
		ty = drawDamageLine(g, dealtR, dealtY, dealtA, x, ty);
		drawnDamage.add(rel.gobid);
	    }

	    GobHealth gh = gob.getattr(GobHealth.class);
	    boolean showHp;
	    if(hpMode >= 2)
		showHp = true;
	    else if(hpMode == 1)
		showHp = gh != null;
	    else
		showHp = gh != null && gh.hp < 0.999f;

	    if(showHp) {
		float hp = (gh == null) ? 1f : Utils.clip(gh.hp, 0f, 1f);
		int bx = x - BAR_W / 2;
		ty -= BAR_H + UI.scale(4);
		g.chcolor(30, 10, 10, 200);
		g.frect(Coord.of(bx, ty - BAR_H), Coord.of(BAR_W, BAR_H));
		if(gh == null) {
		    g.chcolor(120, 120, 130, 200);
		    g.frect(Coord.of(bx, ty - BAR_H), Coord.of(BAR_W, BAR_H));
		} else {
		    g.chcolor(80, 200, 90, 220);
		    g.frect(Coord.of(bx, ty - BAR_H), Coord.of(Math.max(1, (int)Math.round(BAR_W * hp)), BAR_H));
		}
		g.chcolor(255, 255, 255, 160);
		g.rect(Coord.of(bx, ty - BAR_H), Coord.of(BAR_W, BAR_H));
		g.chcolor();
	    }
	}

	/* Keep totals visible on dead/KO gobs after relation disappears. */
	if(MoonConfig.combatHudShowDealt) {
	    for(Map.Entry<Long, int[]> e : dealtMemo.entrySet()) {
		long gobid = e.getKey();
		if(drawnDamage.contains(gobid))
		    continue;
		int[] v = e.getValue();
		if(v == null || v.length < 2)
		    continue;
		int r = v[0], y = v[1], a = (v.length >= 3) ? v[2] : 0;
		if(r <= 0 && y <= 0 && a <= 0)
		    continue;
		Gob gob;
		try {
		    gob = mv.glob.oc.getgob(gobid);
		} catch(Loading l) {
		    continue;
		}
		Coord3f scc = null;
		if(gob != null) {
		    Coord3f gc;
		    try {
			gc = gob.getc();
		    } catch(Exception ex) {
			gc = null;
		    }
		    if(gc != null) {
			dealtLastPos.put(gobid, new Coord2d(gc.x, gc.y));
			scc = combatAnchor(mv, gc);
		    }
		}
		if(scc == null) {
		    Coord2d wp = dealtLastPos.get(gobid);
		    if(wp == null)
			continue;
		    try {
			Coord3f wc = mv.glob.map.getzp(wp);
			scc = combatAnchor(mv, wc);
		    } catch(Exception ex) {
			continue;
		    }
		}
		if(scc == null)
		    continue;
		int x = (int)scc.x;
		int ty = (int)scc.y - UI.scale(8);
		drawDamageLine(g, r, y, a, x, ty);
	    }
	}
    }

    private static int drawDamageLine(GOut g, int r, int y, int a, int cx, int ty) {
	Tex tex = damageLineTex(r, y, a);
	int w = tex.sz().x, h = tex.sz().y;
	g.image(tex, Coord.of(cx - w / 2, ty - h));
	return ty - h - UI.scale(2);
    }

    private static Tex damageLineTex(int r, int y, int a) {
	String key = r + "|" + y + "|" + a;
	Tex tex = dmgLineCache.get(key);
	if(tex != null)
	    return(tex);
	String rt = String.format("$col[255,80,80]{R:%d}", r);
	String yt = String.format("$col[255,210,80]{Y:%d}", y);
	String at = String.format("$col[120,255,120]{A:%d}", a);
	tex = new TexI(RichText.render(
	    String.format("$size[15]{$bg[20,20,20,170]{%s  %s  %s}}", rt, yt, at), 0).img);
	if(dmgLineCache.size() >= DAMAGE_LINE_CACHE_MAX) {
	    for(Map.Entry<String, Tex> e : dmgLineCache.entrySet()) {
		Tex old = dmgLineCache.remove(e.getKey());
		if(old != null)
		    old.dispose();
		break;
	    }
	}
	Tex prev = dmgLineCache.put(key, tex);
	if(prev != null && prev != tex)
	    prev.dispose();
	return(tex);
    }

    private static Coord3f safeScreen(MapView mv, Coord3f wc) {
	try {
	    Coord3f s = mv.screenxf(wc);
	    if(s == null || !Float.isFinite(s.x) || !Float.isFinite(s.y))
		return(null);
	    return(s);
	} catch(Exception e) {
	    return(null);
	}
    }

    private static Coord3f combatAnchor(MapView mv, Coord3f wc) {
	Coord3f s = safeScreen(mv, new Coord3f(wc.x, wc.y, wc.z + HEAD_Z));
	if(s != null)
	    return(s);
	s = safeScreen(mv, new Coord3f(wc.x, wc.y, wc.z + 8f));
	if(s != null)
	    return(s);
	return(safeScreen(mv, wc));
    }

    private static void fightLog(String line) {
	try {
	    Path p = FileSystems.getDefault().getPath(System.getProperty("user.home", "."), ".haven", "moon-fight-debug.log");
	    Path dir = p.getParent();
	    if(dir != null)
		Files.createDirectories(dir);
	    Files.writeString(p, line + "\n", StandardCharsets.UTF_8,
		StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	} catch(Throwable ignored) {
	}
    }

}
