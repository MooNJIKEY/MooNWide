/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.render.*;
import managers.combat.CombatManager;
import java.util.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;

public class Fightsess extends Widget {
    private static final Coord off = new Coord(UI.scale(32), UI.scale(32));
    public static final Tex cdframe = Resource.loadtex("gfx/hud/combat/cool");
    public static final Tex actframe = Buff.frame;
    public static final Coord actframeo = Buff.imgoff;
    public static final Tex indframe = Resource.loadtex("gfx/hud/combat/indframe");
    public static final Coord indframeo = (indframe.sz().sub(off)).div(2);
    public static final Tex indbframe = Resource.loadtex("gfx/hud/combat/indbframe");
    public static final Coord indbframeo = (indframe.sz().sub(off)).div(2);
    public static final Tex useframe = Resource.loadtex("gfx/hud/combat/lastframe");
    public static final Coord useframeo = (useframe.sz().sub(off)).div(2);
    public static final int actpitch = UI.scale(50);
    private static final int HUD_TEXT_CACHE_MAX = 256;
    private static final Map<String, Tex> hudTextCache = new LinkedHashMap<String, Tex>(HUD_TEXT_CACHE_MAX, 0.75f, true) {
	protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
	    if(size() > HUD_TEXT_CACHE_MAX) {
		Tex tex = eldest.getValue();
		if(tex != null)
		    tex.dispose();
		return(true);
	    }
	    return(false);
	}
    };
    private static final Color HUD_PANEL_TOP = new Color(72, 48, 24, 214);
    private static final Color HUD_PANEL_BOTTOM = new Color(22, 14, 10, 208);
    private static final Color HUD_PANEL_EDGE = new Color(214, 179, 112, 210);
    private static final Color HUD_PANEL_SOFT = new Color(255, 228, 168, 38);
    private static final Color HUD_PANEL_GLOW = new Color(255, 232, 188, 24);
    private static final Color HUD_TEXT = new Color(248, 239, 216);
    private static final Color HUD_TEXT_SOFT = new Color(214, 198, 170);
    private static final Color HUD_HP = new Color(56, 206, 74, 230);
    private static final Color HUD_HP_DARK = new Color(18, 82, 28, 216);
    private static final Color HUD_IP = new Color(82, 120, 244, 228);
    private static final Color HUD_IP_DARK = new Color(18, 36, 102, 214);
    private static final Color HUD_SLOT = new Color(12, 8, 18, 184);
    private static final Color HUD_SLOT_EDGE = new Color(228, 204, 142, 168);
    private static final Color HUD_SELF_EDGE = new Color(118, 224, 142, 210);
    private static final Color HUD_TARGET_EDGE = new Color(236, 124, 108, 214);
    public final Action[] actions;
    public int use = -1, useb = -1;
    public Coord pcc;
    public int pho;
    private Fightview fv;
    /** When {@link MoonConfig#fightOverlayFixed}: drag combat UI with Alt+left mouse. */
    private UI.Grab overlayDrag;
    private Coord overlayDragOff;
    private SplitSection overlaySection;
    private Coord infoC, lastC, actsC;
    private double infoScale = 1.0, lastScale = 1.0, actsScale = 1.0;

    private enum SplitSection {
	INFO("info"), LAST("last"), ACTS("acts");
	final String key;
	SplitSection(String key) {this.key = key;}
    }

    public static class Action {
	public final Indir<Resource> res;
	public double cs, ct;

	public Action(Indir<Resource> res) {
	    this.res = res;
	}
    }

    @RName("fsess")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    int nact = Utils.iv(args[0]);
	    return(new Fightsess(nact));
	}
    }

    @SuppressWarnings("unchecked")
    public Fightsess(int nact) {
	pho = -UI.scale(40);
	this.actions = new Action[nact];
    }

    protected void added() {
	fv = parent.getparent(GameUI.class).fv;
	presize();
    }

    private Coord defaultSectionPos(SplitSection sec) {
	if(moonCombatHudMode()) {
	    switch(sec) {
	    case INFO: return Coord.of(sz.x / 2, UI.scale(78));
	    case LAST: return Coord.of(sz.x / 2, UI.scale(78));
	    default:   return Coord.of(sz.x / 2, sz.y - UI.scale(96));
	    }
	}
	switch(sec) {
	case INFO: return Coord.of(sz.x / 2, UI.scale(62));
	case LAST: return Coord.of(sz.x / 2, UI.scale(122));
	default:   return Coord.of(sz.x / 2, UI.scale(248));
	}
    }

    private String secKey(SplitSection sec, String suffix) {
	return "moon-fight-overlay-" + sec.key + "-" + suffix;
    }

    private Coord loadSectionPos(SplitSection sec) {
	Coord def = defaultSectionPos(sec);
	Coord c = Utils.getprefc(secKey(sec, "c"), def);
	return new Coord(Utils.clip(c.x, 0, Math.max(0, sz.x - 1)), Utils.clip(c.y, 0, Math.max(0, sz.y - 1)));
    }

    private double loadSectionScale(SplitSection sec) {
	double v = Utils.getprefd(secKey(sec, "scale"), 1.0);
	if(Double.isNaN(v))
	    v = 1.0;
	return Utils.clip(v, 0.7, 2.2);
    }

    private void saveSection(SplitSection sec) {
	Coord c = sectionCoord(sec);
	Utils.setprefc(secKey(sec, "c"), c);
	Utils.setprefd(secKey(sec, "scale"), sectionScale(sec));
	try { Utils.prefs().flush(); } catch(Exception ignored) {}
    }

    private Coord sectionCoord(SplitSection sec) {
	switch(sec) {
	case INFO: return infoC;
	case LAST: return lastC;
	default:   return actsC;
	}
    }

    private void setSectionCoord(SplitSection sec, Coord c) {
	Coord clamped = new Coord(Utils.clip(c.x, 0, Math.max(0, sz.x - 1)), Utils.clip(c.y, 0, Math.max(0, sz.y - 1)));
	switch(sec) {
	case INFO: infoC = clamped; break;
	case LAST: lastC = clamped; break;
	default:   actsC = clamped; break;
	}
    }

    private double sectionScale(SplitSection sec) {
	switch(sec) {
	case INFO: return infoScale;
	case LAST: return lastScale;
	default:   return actsScale;
	}
    }

    private void setSectionScale(SplitSection sec, double v) {
	double clip = Utils.clip(v, 0.7, 2.2);
	switch(sec) {
	case INFO: infoScale = clip; break;
	case LAST: lastScale = clip; break;
	default:   actsScale = clip; break;
	}
    }

    private boolean moonCombatHudMode() {
	return(MoonConfig.gfxModFightHud);
    }

    private Coord combatAnchor(SplitSection sec) {
	if(MoonConfig.fightOverlayFixed) {
	    Coord c = sectionCoord(sec);
	    if(c != null)
		return(c);
	}
	if(moonCombatHudMode()) {
	    switch(sec) {
	    case INFO: return Coord.of(sz.x / 2, UI.scale(78));
	    case LAST: return Coord.of(sz.x / 2, UI.scale(78));
	    default:   return Coord.of(sz.x / 2, sz.y - UI.scale(96));
	    }
	}
	return(pcc);
    }

    private double combatScale(SplitSection sec) {
	return(MoonConfig.fightOverlayFixed ? sectionScale(sec) : 1.0);
    }

    public void presize() {
	resize(parent.sz);
	if(MoonConfig.fightOverlayFixed) {
	    Coord s = Utils.getprefc("moon-fight-overlay-pcc", null);
	    if(s != null)
		pcc = new Coord(Utils.clip(s.x, 0, Math.max(0, sz.x - 1)), Utils.clip(s.y, 0, Math.max(0, sz.y - 1)));
	    else
		pcc = sz.div(2);
	    pho = Utils.getprefi("moon-fight-overlay-pho", -UI.scale(40));
	    infoC = loadSectionPos(SplitSection.INFO);
	    lastC = loadSectionPos(SplitSection.LAST);
	    actsC = loadSectionPos(SplitSection.ACTS);
	    infoScale = loadSectionScale(SplitSection.INFO);
	    lastScale = loadSectionScale(SplitSection.LAST);
	    actsScale = loadSectionScale(SplitSection.ACTS);
	} else {
	    pcc = sz.div(2);
	}
    }

    private void updatepos() {
	if(MoonConfig.fightOverlayFixed) {
	    Coord s = Utils.getprefc("moon-fight-overlay-pcc", null);
	    if(s != null)
		pcc = new Coord(Utils.clip(s.x, 0, Math.max(0, sz.x - 1)), Utils.clip(s.y, 0, Math.max(0, sz.y - 1)));
	    pho = Utils.getprefi("moon-fight-overlay-pho", -UI.scale(40));
	    return;
	}
	MapView map;
	Gob pl;
	if(((map = getparent(GameUI.class).map) == null) || ((pl = map.player()) == null))
	    return;
	Coord3f raw = pl.placed.getc();
	if(raw == null)
	    return;
	pcc = map.screenxf(raw).round2();
	pho = (int)(map.screenxf(raw.add(0, 0, UI.scale(20))).round2().sub(pcc).y) - UI.scale(20);
    }

    private static class Effect implements RenderTree.Node {
	Sprite spr;
	RenderTree.Slot slot;
	boolean used = true;

	Effect(Sprite spr) {this.spr = spr;}

	public void added(RenderTree.Slot slot) {
	    slot.add(spr);
	}
    }

    private static final Resource tgtfx = Resource.local().loadwait("gfx/hud/combat/trgtarw");
    private final Collection<Effect> curfx = new ArrayList<>();

    private Effect fxon(long gobid, Resource fx, Effect cur) {
	MapView map = getparent(GameUI.class).map;
	Gob gob = ui.sess.glob.oc.getgob(gobid);
	if((map == null) || (gob == null))
	    return(null);
	Pipe.Op place;
	try {
	    place = gob.placed.curplace();
	} catch(Loading l) {
	    return(null);
	}
	if((cur == null) || (cur.slot == null)) {
	    try {
		cur = new Effect(Sprite.create(new Sprite.UIOwner(this), fx, Message.nil));
		cur.slot = map.basic.add(cur.spr, place);
	    } catch(Loading l) {
		return(null);
	    }
	    curfx.add(cur);
	} else {
	    cur.slot.cstate(place);
	}
	cur.used = true;
	return(cur);
    }

    public void tick(double dt) {
	for(Iterator<Effect> i = curfx.iterator(); i.hasNext();) {
	    Effect fx = i.next();
	    if(!fx.used) {
		if(fx.slot != null) {
		    fx.slot.remove();
		    fx.slot = null;
		}
		i.remove();
	    } else {
		fx.used = false;
		fx.spr.tick(dt);
	    }
	}
    }

    public void destroy() {
	for(Effect fx : curfx) {
	    if(fx.slot != null)
		fx.slot.remove();
	}
	curfx.clear();
	super.destroy();
    }

    private static Tex hudText(String text, float px, Color color, boolean bold) {
	String msg = (text == null) ? "" : text;
	String key = (bold ? "b" : "n") + "|" + Math.round(px * 10f) + "|" + color.getRGB() + "|" + msg;
	synchronized(hudTextCache) {
	    Tex tex = hudTextCache.get(key);
	    if(tex != null)
		return(tex);
	    Font font = Text.serif.deriveFont(bold ? Font.BOLD : Font.PLAIN, UI.scale(px));
	    Text.Furnace furn = new PUtils.BlurFurn(new Text.Foundry(font, color).aa(true), 1, 1, new Color(0, 0, 0, 200));
	    tex = furn.render(msg).tex();
	    hudTextCache.put(key, tex);
	    return(tex);
	}
    }

    private static void drawCenteredTex(GOut g, Tex tex, Coord center, double scale) {
	if(tex == null || center == null)
	    return;
	if(scale == 1.0) {
	    g.aimage(tex, center, 0.5, 0.5);
	} else {
	    Coord tsz = tex.sz().mul(scale);
	    g.image(tex, center.sub(tsz.div(2)), tsz);
	}
    }

    private static void drawHudText(GOut g, String text, Coord center, double scale, float px, Color color, boolean bold) {
	if(text == null || text.isEmpty())
	    return;
	drawCenteredTex(g, hudText(text, px, color, bold), center, scale);
    }

    private static void drawHudTextLeft(GOut g, String text, Coord ul, double scale, float px, Color color, boolean bold) {
	if(text == null || text.isEmpty() || ul == null)
	    return;
	Tex tex = hudText(text, px, color, bold);
	if(scale == 1.0) {
	    g.image(tex, ul);
	} else {
	    g.image(tex, ul, tex.sz().mul(scale));
	}
    }

    private Coord drawMoonPanel(GOut g, Coord center, Coord baseSz, double scale) {
	Coord psz = baseSz.mul(scale);
	Coord ul = center.sub(psz.div(2));
	return(ul);
    }

    private void drawMoonBar(GOut g, Coord ul, Coord sz, double frac, Color top, Color bottom, String text, double textScale) {
	g.chcolor(0, 0, 0, 96);
	g.frect(ul.add(0, 1), sz);
	g.chcolor(18, 12, 10, 96);
	g.frect(ul, sz);
	g.chcolor();
	int iw = Math.max(0, sz.x - UI.scale(4));
	int ih = Math.max(1, sz.y - UI.scale(4));
	if(frac >= 0) {
	    int fw = Utils.clip((int)Math.round(iw * Utils.clip(frac, 0.0, 1.0)), 0, iw);
	    if(fw > 0)
		MoonUiTheme.drawVerticalGradient(g, ul.add(UI.scale(2), UI.scale(2)), Coord.of(fw, ih), top, bottom);
	}
	g.chcolor(236, 224, 196, 170);
	g.rect(ul, sz);
	g.chcolor(255, 248, 232, 48);
	g.rect(ul.add(1, 1), sz.sub(2, 2));
	g.chcolor();
	if(text != null && !text.isEmpty())
	    drawHudText(g, text, ul.add(sz.div(2)), textScale, 11f, HUD_TEXT, true);
    }

    private static double meterFrac(GameUI gui, String name) {
	if(gui == null || name == null)
	    return(-1);
	try {
	    List<IMeter.Meter> ml = gui.getmeters(name);
	    if(ml != null && !ml.isEmpty())
		return(Utils.clip(ml.get(0).a / 100.0, 0.0, 1.0));
	} catch(Exception ignored) {
	}
	return(-1);
    }

    private Gob playerGob() {
	GameUI gui = getparent(GameUI.class);
	if(gui == null || gui.map == null)
	    return(null);
	try {
	    return(gui.map.player());
	} catch(Exception e) {
	    return(null);
	}
    }

    private Gob currentGob() {
	if(fv == null || fv.current == null || ui == null || ui.sess == null || ui.sess.glob == null)
	    return(null);
	try {
	    return(ui.sess.glob.oc.getgob(fv.current.gobid));
	} catch(Exception e) {
	    return(null);
	}
    }

    private static double gobHealthFrac(Gob gob) {
	if(gob == null)
	    return(-1);
	try {
	    GobHealth gh = gob.getattr(GobHealth.class);
	    return((gh == null) ? -1 : Utils.clip(gh.hp, 0.0, 1.0));
	} catch(Exception e) {
	    return(-1);
	}
    }

    private static Tex portraitTex(Gob gob) {
	if(gob == null)
	    return(null);
	try {
	    Avatar ava = gob.getattr(Avatar.class);
	    if(ava != null) {
		List<Resource.Image> imgs = ava.images();
		if(imgs != null && !imgs.isEmpty())
		    return(imgs.get(0).tex());
	    }
	} catch(Exception ignored) {
	}
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    Resource res = (d == null) ? null : d.getres();
	    Resource.Image img = (res == null) ? null : res.layer(Resource.imgc);
	    return((img == null) ? null : img.tex());
	} catch(Exception ignored) {
	}
	return(null);
    }

    private static String gobLabel(Gob gob) {
	if(gob == null)
	    return("No target");
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    Resource res = (d == null) ? null : d.getres();
	    if(res != null) {
		String base = res.basename();
		if(base != null && !base.isEmpty()) {
		    String clean = base.replace('_', ' ').replace('-', ' ').trim();
		    return(clean.isEmpty() ? "Target" : Character.toUpperCase(clean.charAt(0)) + clean.substring(1));
		}
	    }
	} catch(Exception ignored) {
	}
	return(String.format(Locale.ROOT, "Target #%d", gob.id));
    }

    private static String pctText(double frac) {
	return((frac < 0) ? "?" : String.format(Locale.ROOT, "%.0f%%", frac * 100.0));
    }

    private void drawMoonPortrait(GOut g, Gob gob, Coord center, int size, Color edge) {
	Coord psz = Coord.of(size, size);
	Coord ul = center.sub(psz.div(2));
	g.chcolor(HUD_SLOT);
	g.frect(ul, psz);
	g.chcolor();
	Tex tex = portraitTex(gob);
	if(tex != null) {
	    Coord img = psz.sub(UI.scale(6), UI.scale(6));
	    g.image(tex, ul.add(UI.scale(3), UI.scale(3)), img);
	} else {
	    g.chcolor(255, 240, 214, 28);
	    g.frect(ul.add(UI.scale(4), UI.scale(4)), psz.sub(UI.scale(8), UI.scale(8)));
	    g.chcolor();
	    drawHudText(g, "?", center, 1.0, 18f, HUD_TEXT, true);
	}
	g.chcolor(edge);
	g.rect(ul, psz);
	g.chcolor(HUD_PANEL_SOFT);
	g.rect(ul.add(1, 1), psz.sub(2, 2));
	g.chcolor();
    }

    private Coord actionCenter(int i, Coord actsAnchor, double actsSc) {
	if(moonCombatHudMode()) {
	    int pitch = Math.max(UI.scale(40), (int)Math.round(UI.scale(52) * actsSc));
	    int x = ((i % 5) - 2) * pitch;
	    int y = ((i / 5) == 0) ? -(int)Math.round(UI.scale(24) * actsSc) : (int)Math.round(UI.scale(34) * actsSc);
	    return(actsAnchor.add(x, y));
	}
	return(actsAnchor.add(MoonConfig.fightOverlayFixed ? actc(i, actsSc) : actc(i)));
    }

    private Coord lastActionCenter(boolean opponent, Coord lastAnchor, double lastSc) {
	if(moonCombatHudMode()) {
	    int dx = (int)Math.round(UI.scale(90) * lastSc);
	    int dy = (int)Math.round(-UI.scale(6) * lastSc);
	    return(lastAnchor.add(opponent ? dx : -dx, dy));
	}
	return(lastAnchor.add(MoonConfig.fightOverlayFixed ? scaled(opponent ? usec2 : usec1, lastSc) : (opponent ? usec2 : usec1)));
    }

    private Coord selfBuffCoord(Coord lastAnchor, int idx, double scale) {
	if(moonCombatHudMode()) {
	    int step = Buff.cframe.sz().x + UI.scale(2);
	    int base = (int)Math.round(UI.scale(176) * scale);
	    return(lastAnchor.add(-(base + (idx * step)), -Buff.cframe.sz().y / 2));
	}
	int step = Buff.cframe.sz().x + UI.scale(4);
	return(lastAnchor.add((int)Math.round((-UI.scale(40) - ((idx + 1) * step)) * scale), -Buff.cframe.sz().y / 2));
    }

    private Coord oppBuffCoord(Coord lastAnchor, int idx, double scale) {
	if(moonCombatHudMode()) {
	    int step = Buff.cframe.sz().x + UI.scale(2);
	    int base = (int)Math.round(UI.scale(176) * scale);
	    return(lastAnchor.add(base + (idx * step), -Buff.cframe.sz().y / 2));
	}
	int step = Buff.cframe.sz().x + UI.scale(4);
	return(lastAnchor.add((int)Math.round((UI.scale(40) + (idx * step)) * scale), -Buff.cframe.sz().y / 2));
    }

    private void drawMoonInfoModule(GOut g, double now, Coord infoAnchor, double infoSc) {
	GameUI gui = getparent(GameUI.class);
	Gob self = playerGob();
	Gob tgt = currentGob();
	Fightview.Relation rel = fv.current;
	drawMoonPanel(g, infoAnchor, Coord.of(UI.scale(374), UI.scale(86)), infoSc);
	int portraitSz = Math.max(UI.scale(34), (int)Math.round(UI.scale(42) * infoSc));
	Coord selfC = infoAnchor.add((int)Math.round(-UI.scale(142) * infoSc), (int)Math.round(-UI.scale(6) * infoSc));
	Coord tgtC = infoAnchor.add((int)Math.round(UI.scale(142) * infoSc), (int)Math.round(-UI.scale(6) * infoSc));
	drawMoonPortrait(g, self, selfC, portraitSz, HUD_SELF_EDGE);
	drawMoonPortrait(g, tgt, tgtC, portraitSz, HUD_TARGET_EDGE);

	Coord cdc = infoAnchor.add(0, (int)Math.round(-UI.scale(4) * infoSc));
	double atkSpan = fv.atkct - fv.atkcs;
	if(atkSpan > 1e-6 && now < fv.atkct) {
	    double a = (now - fv.atkcs) / atkSpan;
	    g.chcolor(255, 86, 124, 224);
	    g.fellipse(cdc, UI.scale(new Coord(26, 26)).mul(infoSc), Math.PI / 2 - (Math.PI * 2 * Math.min(1.0 - a, 1.0)), Math.PI / 2);
	    g.chcolor();
	}
	drawCenteredTex(g, cdframe, cdc, infoSc * 1.08);
	String delta = (rel == null) ? "0" : String.format(Locale.ROOT, "%+d", rel.ip - rel.oip);
	drawHudText(g, delta, cdc, infoSc, 12f, HUD_TEXT, true);

	String label = (tgt == null) ? "Combat ready" : gobLabel(tgt);
	drawHudText(g, label, infoAnchor.add(0, (int)Math.round(UI.scale(24) * infoSc)), infoSc, 13f, HUD_TEXT, true);

	double targetHp = gobHealthFrac(tgt);
	double selfHp = meterFrac(gui, "hp");
	double hpFill = (targetHp >= 0) ? targetHp : selfHp;
	String hpLabel = (targetHp >= 0)
	    ? ("Target HP " + pctText(targetHp))
	    : ((selfHp >= 0) ? ("Self HP " + pctText(selfHp)) : "HP unknown");
	Coord hpUl = infoAnchor.add((int)Math.round(-UI.scale(116) * infoSc), (int)Math.round(UI.scale(38) * infoSc));
	Coord hpSz = Coord.of((int)Math.round(UI.scale(222) * infoSc), (int)Math.round(UI.scale(18) * infoSc));
	drawMoonBar(g, hpUl, hpSz, hpFill, HUD_HP, HUD_HP_DARK, hpLabel, 1.0);

	double stam = meterFrac(gui, "stam");
	if(stam < 0)
	    stam = Utils.clip(CombatManager.getLastStamina(), 0.0, 1.0);
	double nrj = meterFrac(gui, "nrj");
	if(nrj < 0)
	    nrj = Utils.clip(CombatManager.getLastEnergy(), 0.0, 1.0);
	double ipFill = (rel == null || (rel.ip + rel.oip) <= 0) ? -1 : (rel.ip / (double)Math.max(1, rel.ip + rel.oip));
	String ipLabel = (rel == null)
	    ? String.format(Locale.ROOT, "ST %s | EN %s", pctText(stam), pctText(nrj))
	    : String.format(Locale.ROOT, "IP %d:%d | ST %s | EN %s", rel.ip, rel.oip, pctText(stam), pctText(nrj));
	Coord ipUl = hpUl.add(0, (int)Math.round(UI.scale(22) * infoSc));
	Coord ipSz = Coord.of(hpSz.x, (int)Math.round(UI.scale(16) * infoSc));
	drawMoonBar(g, ipUl, ipSz, ipFill, HUD_IP, HUD_IP_DARK, ipLabel, 1.0);

	try {
	    Indir<Resource> lastact = fv.lastact;
	    if(lastact != this.lastact1) {
		this.lastact1 = lastact;
		this.lastacttip1 = null;
	    }
	    if(lastact != null) {
		Tex ut = lastact.get().flayer(Resource.imgc).tex();
		Coord lc = lastActionCenter(false, infoAnchor, infoSc);
		Coord utsz = Coord.of((int)Math.round(UI.scale(34) * infoSc), (int)Math.round(UI.scale(34) * infoSc));
		g.image(ut, lc.sub(utsz.div(2)), utsz);
		drawCenteredTex(g, useframe, lc, infoSc * 0.86);
	    }
	} catch(Loading l) {
	}
	if(fv.current != null) {
	    try {
		Indir<Resource> lastact = fv.current.lastact;
		if(lastact != this.lastact2) {
		    this.lastact2 = lastact;
		    this.lastacttip2 = null;
		}
		if(lastact != null) {
		    Tex ut = lastact.get().flayer(Resource.imgc).tex();
		    Coord lc = lastActionCenter(true, infoAnchor, infoSc);
		    Coord utsz = Coord.of((int)Math.round(UI.scale(34) * infoSc), (int)Math.round(UI.scale(34) * infoSc));
		    g.image(ut, lc.sub(utsz.div(2)), utsz);
		    drawCenteredTex(g, useframe, lc, infoSc * 0.86);
		}
	    } catch(Loading l) {
	    }
	} else if(this.lastact2 != null) {
	    this.lastact2 = null;
	    this.lastacttip2 = null;
	}

	int idx = 0;
	for(Buff buff : fv.buffs.children(Buff.class)) {
	    Coord dc = selfBuffCoord(infoAnchor, idx++, infoSc);
	    buff.draw(g.reclip(dc, buff.sz));
	}
	if(fv.current != null) {
	    idx = 0;
	    for(Buff buff : fv.current.buffs.children(Buff.class)) {
		Coord dc = oppBuffCoord(infoAnchor, idx++, infoSc);
		buff.draw(g.reclip(dc, buff.sz));
	    }
	}
    }

    private void drawMoonLastModule(GOut g, double now, Coord lastAnchor, double lastSc) {
    }

    private String keyLabel(int i) {
	if(i >= 0 && i < kb_acts.length && kb_acts[i].key() != KeyMatch.nil) {
	    try {
		return(kb_acts[i].key().name());
	    } catch(Exception ignored) {
	    }
	}
	return((i >= 0 && i < keytips.length) ? keytips[i] : "");
    }

    private void drawMoonActsModule(GOut g, double now, Coord actsAnchor, double actsSc) {
	for(int i = 0; i < actions.length; i++) {
	    Coord ca = actionCenter(i, actsAnchor, actsSc);
	    Action act = actions[i];
	    try {
		Coord slotBox = Coord.of((int)Math.round(UI.scale(42) * actsSc), (int)Math.round(UI.scale(42) * actsSc));
		Coord slotUl = ca.sub(slotBox.div(2));
		g.chcolor(8, 6, 10, 82);
		g.frect(slotUl, slotBox);
		g.chcolor(255, 244, 216, 52);
		g.rect(slotUl, slotBox);
		g.chcolor();
		if(act != null) {
		    Resource res = act.res.get();
		    Tex img = res.flayer(Resource.imgc).tex();
		    Coord isz = Coord.of((int)Math.round(UI.scale(32) * actsSc), (int)Math.round(UI.scale(32) * actsSc));
		    Coord ic = ca.sub(isz.div(2));
		    g.image(img, ic, isz);
		    if(now < act.ct) {
			double a = (now - act.cs) / (act.ct - act.cs);
			g.chcolor(0, 0, 0, 144);
			g.prect(ca, ic.sub(ca), ic.add(isz).sub(ca), (1.0 - a) * Math.PI * 2);
			g.chcolor();
		    }
		    if(i == use) {
			drawCenteredTex(g, indframe, ca, actsSc * 0.92);
		    } else if(i == useb) {
			drawCenteredTex(g, indbframe, ca, actsSc * 0.92);
		    } else {
			drawCenteredTex(g, actframe, ca, actsSc * 0.92);
		    }
		}
		drawHudText(g, keyLabel(i), ca.add(0, (int)Math.round(UI.scale(31) * actsSc)), actsSc, 9f, HUD_TEXT_SOFT, true);
	    } catch(Loading l) {
	    }
	}
    }

    private static final Text.Furnace ipf = new PUtils.BlurFurn(new Text.Foundry(Text.serif, 18, new Color(128, 128, 255)).aa(true), 1, 1, new Color(48, 48, 96));
    private final Indir<Text> ip =  Utils.transform(() -> fv.current.ip , v -> ipf.render("IP: " + v));
    private final Indir<Text> oip = Utils.transform(() -> fv.current.oip, v -> ipf.render("IP: " + v));

    private static Coord actc(int i) {
	int rl = 5;
	return(new Coord((actpitch * (i % rl)) - (((rl - 1) * actpitch) / 2), UI.scale(125) + ((i / rl) * actpitch)));
    }

    private static Coord actc(int i, double scale) {
	int rl = 5;
	int pitch = Math.max(UI.scale(28), (int)Math.round(actpitch * scale));
	int y0 = (int)Math.round(UI.scale(125) * scale);
	return(new Coord((pitch * (i % rl)) - (((rl - 1) * pitch) / 2), y0 + ((i / rl) * pitch)));
    }

    private static Coord scaled(Coord c, double scale) {
	return c.mul(scale);
    }

    private static Coord scaledTex(Tex tex, double scale) {
	return tex.sz().mul(scale);
    }

    private Text ipText(int val, double scale) {
	int px = Math.max(UI.scale(12), (int)Math.round(18 * scale));
	Text.Furnace f = new PUtils.BlurFurn(new Text.Foundry(Text.serif, px, new Color(128, 128, 255)).aa(true), 1, 1, new Color(48, 48, 96));
	return f.render("IP: " + val);
    }

    private Coord sectionHalfSize(SplitSection sec) {
	if(moonCombatHudMode()) {
	    switch(sec) {
	    case INFO: return Coord.of(UI.scale(260), UI.scale(74)).mul(sectionScale(sec));
	    case LAST: return Coord.z;
	    default:   return Coord.of(UI.scale(160), UI.scale(82)).mul(sectionScale(sec));
	    }
	}
	switch(sec) {
	case INFO: return Coord.of(UI.scale(220), UI.scale(72)).mul(sectionScale(sec));
	case LAST: return Coord.of(UI.scale(150), UI.scale(60)).mul(sectionScale(sec));
	default:   return Coord.of(UI.scale(175), UI.scale(150)).mul(sectionScale(sec));
	}
    }

    private SplitSection sectionAt(Coord c) {
	if(moonCombatHudMode()) {
	    SplitSection[] active = {SplitSection.INFO, SplitSection.ACTS};
	    for(SplitSection sec : active) {
		Coord hc = sectionCoord(sec);
		Coord hs = sectionHalfSize(sec);
		if(hc != null && c.isect(hc.sub(hs), hs.mul(2)))
		    return(sec);
	    }
	    return(null);
	}
	for(SplitSection sec : SplitSection.values()) {
	    Coord hc = sectionCoord(sec);
	    Coord hs = sectionHalfSize(sec);
	    if(hc != null && c.isect(hc.sub(hs), hs.mul(2)))
		return sec;
	}
	return null;
    }

    private static final Coord cmc = UI.scale(new Coord(0, 67));
    private static final Coord usec1 = UI.scale(new Coord(-65, 67));
    private static final Coord usec2 = UI.scale(new Coord(65, 67));
    private Indir<Resource> lastact1 = null, lastact2 = null;
    private Text lastacttip1 = null, lastacttip2 = null;
    private Effect curtgtfx;
    public void draw(GOut g) {
	updatepos();
	double now = Utils.rtime();
	Coord infoAnchor = combatAnchor(SplitSection.INFO);
	Coord lastAnchor = moonCombatHudMode() ? infoAnchor : combatAnchor(SplitSection.LAST);
	Coord actsAnchor = combatAnchor(SplitSection.ACTS);
	double infoSc = combatScale(SplitSection.INFO);
	double lastSc = moonCombatHudMode() ? infoSc : combatScale(SplitSection.LAST);
	double actsSc = combatScale(SplitSection.ACTS);

	if(moonCombatHudMode()) {
	    drawMoonInfoModule(g, now, infoAnchor, infoSc);
	    drawMoonActsModule(g, now, actsAnchor, actsSc);
	    if(fv.current != null && fv.lsrel.size() > 1)
		curtgtfx = fxon(fv.current.gobid, tgtfx, curtgtfx);
	    return;
	}

	for(Buff buff : fv.buffs.children(Buff.class))
	    buff.draw(g.reclip(infoAnchor.add(-buff.c.x - Buff.cframe.sz().x - UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y), buff.sz));
	if(fv.current != null) {
	    for(Buff buff : fv.current.buffs.children(Buff.class))
		buff.draw(g.reclip(infoAnchor.add(buff.c.x + UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y), buff.sz));

	    if(MoonConfig.fightOverlayFixed) {
		g.aimage(ipText(fv.current.ip, infoSc).tex(), infoAnchor.add(scaled(Coord.of(-UI.scale(75), 0), infoSc)), 1, 0.5);
		g.aimage(ipText(fv.current.oip, infoSc).tex(), infoAnchor.add(scaled(Coord.of(UI.scale(75), 0), infoSc)), 0, 0.5);
	    } else {
		g.aimage(ip.get().tex(), infoAnchor.add(-UI.scale(75), 0), 1, 0.5);
		g.aimage(oip.get().tex(), infoAnchor.add(UI.scale(75), 0), 0, 0.5);
	    }

	    if(fv.lsrel.size() > 1)
		curtgtfx = fxon(fv.current.gobid, tgtfx, curtgtfx);
	}

	{
	    Coord cdc = actsAnchor.add(scaled(cmc, actsSc));
	    double atkSpan = fv.atkct - fv.atkcs;
	    if(atkSpan > 1e-6 && now < fv.atkct) {
		double a = (now - fv.atkcs) / atkSpan;
		g.chcolor(255, 0, 128, 224);
		g.fellipse(cdc, UI.scale(new Coord(24, 24)).mul(actsSc), Math.PI / 2 - (Math.PI * 2 * Math.min(1.0 - a, 1.0)), Math.PI / 2);
		g.chcolor();
	    }
	    Coord cdsz = MoonConfig.fightOverlayFixed ? scaledTex(cdframe, actsSc) : cdframe.sz();
	    g.image(cdframe, cdc.sub(cdsz.div(2)), cdsz);
	}
	try {
	    Indir<Resource> lastact = fv.lastact;
	    if(lastact != this.lastact1) {
		this.lastact1 = lastact;
		this.lastacttip1 = null;
	    }
	    double lastuse = fv.lastuse;
	    if(lastact != null) {
		Tex ut = lastact.get().flayer(Resource.imgc).tex();
		Coord utsz = MoonConfig.fightOverlayFixed ? scaledTex(ut, lastSc) : ut.sz();
		Coord useul = lastAnchor.add(scaled(usec1, lastSc)).sub(utsz.div(2));
		g.image(ut, useul, utsz);
		Coord fsz = MoonConfig.fightOverlayFixed ? scaledTex(useframe, lastSc) : useframe.sz();
		g.image(useframe, useul.sub(MoonConfig.fightOverlayFixed ? useframeo.mul(lastSc) : useframeo), fsz);
		double a = now - lastuse;
		if(a < 1) {
		    Coord off = new Coord((int)(a * utsz.x / 2), (int)(a * utsz.y / 2));
		    g.chcolor(255, 255, 255, (int)(255 * (1 - a)));
		    g.image(ut, useul.sub(off), utsz.add(off.mul(2)));
		    g.chcolor();
		}
	    }
	} catch(Loading l) {
	}
	if(fv.current != null) {
	    try {
		Indir<Resource> lastact = fv.current.lastact;
		if(lastact != this.lastact2) {
		    this.lastact2 = lastact;
		    this.lastacttip2 = null;
		}
		double lastuse = fv.current.lastuse;
		if(lastact != null) {
		    Tex ut = lastact.get().flayer(Resource.imgc).tex();
		    Coord utsz = MoonConfig.fightOverlayFixed ? scaledTex(ut, lastSc) : ut.sz();
		    Coord useul = lastAnchor.add(scaled(usec2, lastSc)).sub(utsz.div(2));
		    g.image(ut, useul, utsz);
		    Coord fsz = MoonConfig.fightOverlayFixed ? scaledTex(useframe, lastSc) : useframe.sz();
		    g.image(useframe, useul.sub(MoonConfig.fightOverlayFixed ? useframeo.mul(lastSc) : useframeo), fsz);
		    double a = now - lastuse;
		    if(a < 1) {
			Coord off = new Coord((int)(a * utsz.x / 2), (int)(a * utsz.y / 2));
			g.chcolor(255, 255, 255, (int)(255 * (1 - a)));
			g.image(ut, useul.sub(off), utsz.add(off.mul(2)));
			g.chcolor();
		    }
		}
	    } catch(Loading l) {
	    }
	}
	for(int i = 0; i < actions.length; i++) {
	    Coord ca = actsAnchor.add(MoonConfig.fightOverlayFixed ? actc(i, actsSc) : actc(i));
	    Action act = actions[i];
	    try {
		if(act != null) {
		    Resource res = act.res.get();
		    Tex img = res.flayer(Resource.imgc).tex();
		    Coord isz = MoonConfig.fightOverlayFixed ? scaledTex(img, actsSc) : img.sz();
		    Coord ic = ca.sub(isz.div(2));
		    g.image(img, ic, isz);
		    if(now < act.ct) {
			double a = (now - act.cs) / (act.ct - act.cs);
			g.chcolor(0, 0, 0, 128);
			g.prect(ca, ic.sub(ca), ic.add(isz).sub(ca), (1.0 - a) * Math.PI * 2);
			g.chcolor();
		    }
		    if(i == use) {
			Coord fsz = MoonConfig.fightOverlayFixed ? scaledTex(indframe, actsSc) : indframe.sz();
			g.image(indframe, ic.sub(MoonConfig.fightOverlayFixed ? indframeo.mul(actsSc) : indframeo), fsz);
		    } else if(i == useb) {
			Coord fsz = MoonConfig.fightOverlayFixed ? scaledTex(indbframe, actsSc) : indbframe.sz();
			g.image(indbframe, ic.sub(MoonConfig.fightOverlayFixed ? indbframeo.mul(actsSc) : indbframeo), fsz);
		    } else {
			Coord fsz = MoonConfig.fightOverlayFixed ? scaledTex(actframe, actsSc) : actframe.sz();
			g.image(actframe, ic.sub(MoonConfig.fightOverlayFixed ? actframeo.mul(actsSc) : actframeo), fsz);
		    }
		}
	    } catch(Loading l) {}
	}
    }

    private Widget prevtt = null;
    private Text acttip = null;
    public static final String[] keytips = {"1", "2", "3", "4", "5", "Shift+1", "Shift+2", "Shift+3", "Shift+4", "Shift+5"};
    public Object tooltip(Coord c, Widget prev) {
	Coord infoAnchor = combatAnchor(SplitSection.INFO);
	Coord lastAnchor = moonCombatHudMode() ? infoAnchor : combatAnchor(SplitSection.LAST);
	Coord actsAnchor = combatAnchor(SplitSection.ACTS);
	double lastSc = moonCombatHudMode() ? combatScale(SplitSection.INFO) : combatScale(SplitSection.LAST);
	double actsSc = combatScale(SplitSection.ACTS);
	if(moonCombatHudMode()) {
	    int idx = 0;
	    for(Buff buff : fv.buffs.children(Buff.class)) {
		Coord dc = selfBuffCoord(lastAnchor, idx++, lastSc);
		if(c.isect(dc, buff.sz)) {
		    Object ret = buff.tooltip(c.sub(dc), prevtt);
		    if(ret != null) {
			prevtt = buff;
			return(ret);
		    }
		}
	    }
	    if(fv.current != null) {
		idx = 0;
		for(Buff buff : fv.current.buffs.children(Buff.class)) {
		    Coord dc = oppBuffCoord(lastAnchor, idx++, lastSc);
		    if(c.isect(dc, buff.sz)) {
			Object ret = buff.tooltip(c.sub(dc), prevtt);
			if(ret != null) {
			    prevtt = buff;
			    return(ret);
			}
		    }
		}
	    }
	} else {
	    for(Buff buff : fv.buffs.children(Buff.class)) {
		Coord dc = infoAnchor.add(-buff.c.x - Buff.cframe.sz().x - UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y);
		if(c.isect(dc, buff.sz)) {
		    Object ret = buff.tooltip(c.sub(dc), prevtt);
		    if(ret != null) {
			prevtt = buff;
			return(ret);
		    }
		}
	    }
	    if(fv.current != null) {
		for(Buff buff : fv.current.buffs.children(Buff.class)) {
		    Coord dc = infoAnchor.add(buff.c.x + UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y);
		    if(c.isect(dc, buff.sz)) {
			Object ret = buff.tooltip(c.sub(dc), prevtt);
			if(ret != null) {
			    prevtt = buff;
			    return(ret);
			}
		    }
		}
	    }
	}
	for(int i = 0; i < actions.length; i++) {
	    Coord ca = actionCenter(i, actsAnchor, actsSc);
	    Indir<Resource> act = (actions[i] == null) ? null : actions[i].res;
	    if(act != null) {
		Tex img = act.get().flayer(Resource.imgc).tex();
		Coord isz = moonCombatHudMode() ? Coord.of((int)Math.round(UI.scale(32) * actsSc), (int)Math.round(UI.scale(32) * actsSc)) :
		    (MoonConfig.fightOverlayFixed ? scaledTex(img, actsSc) : img.sz());
		ca = ca.sub(isz.div(2));
		if(c.isect(ca, isz)) {
		    String tip = LocalizationManager.autoTranslateProcessed(act.get().flayer(Resource.tooltip).t);
		    if(kb_acts[i].key() != KeyMatch.nil)
			tip += " ($b{$col[255,128,0]{" + kb_acts[i].key().name() + "}})";
		    if((acttip == null) || !acttip.text.equals(tip))
			acttip = RichText.render(tip, -1);
		    return(acttip);
		}
	    }
	}
	{
	    Indir<Resource> lastact = this.lastact1;
	    if(lastact != null) {
		Coord usesz = moonCombatHudMode() ? Coord.of((int)Math.round(UI.scale(36) * lastSc), (int)Math.round(UI.scale(36) * lastSc)) :
		    (MoonConfig.fightOverlayFixed ? scaled(lastact.get().flayer(Resource.imgc).sz, lastSc) : lastact.get().flayer(Resource.imgc).sz);
		Coord lac = lastActionCenter(false, lastAnchor, lastSc);
		if(c.isect(lac.sub(usesz.div(2)), usesz)) {
		    if(lastacttip1 == null)
			lastacttip1 = Text.render(LocalizationManager.autoTranslateProcessed(lastact.get().flayer(Resource.tooltip).t));
		    return(lastacttip1);
		}
	    }
	}
	{
	    Indir<Resource> lastact = this.lastact2;
	    if(lastact != null) {
		Coord usesz = moonCombatHudMode() ? Coord.of((int)Math.round(UI.scale(36) * lastSc), (int)Math.round(UI.scale(36) * lastSc)) :
		    (MoonConfig.fightOverlayFixed ? scaled(lastact.get().flayer(Resource.imgc).sz, lastSc) : lastact.get().flayer(Resource.imgc).sz);
		Coord lac = lastActionCenter(true, lastAnchor, lastSc);
		if(c.isect(lac.sub(usesz.div(2)), usesz)) {
		    if(lastacttip2 == null)
			lastacttip2 = Text.render(LocalizationManager.autoTranslateProcessed(lastact.get().flayer(Resource.tooltip).t));
		    return(lastacttip2);
		}
	    }
	}
	return(null);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "act") {
	    int n = Utils.iv(args[0]);
	    if(args.length > 1) {
		Indir<Resource> res = ui.sess.getresv(args[1]);
		actions[n] = new Action(res);
	    } else {
		actions[n] = null;
	    }
	} else if(msg == "acool") {
	    int n = Utils.iv(args[0]);
	    double now = Utils.rtime();
	    actions[n].cs = now;
	    actions[n].ct = now + (Utils.dv(args[1]) * 0.06);
	} else if(msg == "use") {
	    this.use = Utils.iv(args[0]);
	    this.useb = (args.length > 1) ? Utils.iv(args[1]) : -1;
	} else if(msg == "used") {
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final KeyBinding[] kb_acts = {
	KeyBinding.get("fgt/0", KeyMatch.forcode(KeyEvent.VK_1, 0)),
	KeyBinding.get("fgt/1", KeyMatch.forcode(KeyEvent.VK_2, 0)),
	KeyBinding.get("fgt/2", KeyMatch.forcode(KeyEvent.VK_3, 0)),
	KeyBinding.get("fgt/3", KeyMatch.forcode(KeyEvent.VK_4, 0)),
	KeyBinding.get("fgt/4", KeyMatch.forcode(KeyEvent.VK_5, 0)),
	KeyBinding.get("fgt/5", KeyMatch.forcode(KeyEvent.VK_1, KeyMatch.S)),
	KeyBinding.get("fgt/6", KeyMatch.forcode(KeyEvent.VK_2, KeyMatch.S)),
	KeyBinding.get("fgt/7", KeyMatch.forcode(KeyEvent.VK_3, KeyMatch.S)),
	KeyBinding.get("fgt/8", KeyMatch.forcode(KeyEvent.VK_4, KeyMatch.S)),
	KeyBinding.get("fgt/9", KeyMatch.forcode(KeyEvent.VK_5, KeyMatch.S)),
    };
    public static final KeyBinding kb_relcycle =  KeyBinding.get("fgt-cycle", KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C), KeyMatch.S);

    /* XXX: This is a bit ugly, but release message do need to be
     * properly sequenced with use messages in some way. */
    private class Release implements Runnable {
	final int n;

	Release(int n) {
	    this.n = n;
	    Environment env = ui.getenv();
	    Render out = env.render();
	    out.fence(this);
	    env.submit(out);
	}


	public void run() {
	    wdgmsg("rel", n);
	}
    }

    private UI.Grab holdgrab = null;
    private int held = -1;
    @Override
    public boolean mousedown(MouseDownEvent ev) {
	if(MoonConfig.fightOverlayFixed && ev.b == 1 && (ui.modflags() & UI.MOD_META) != 0) {
	    SplitSection sec = sectionAt(ev.c);
	    if(sec != null) {
		overlaySection = sec;
		overlayDrag = ui.grabmouse(this);
		overlayDragOff = ev.c.sub(sectionCoord(sec));
		return(true);
	    }
	}
	return(super.mousedown(ev));
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	if(overlayDrag != null) {
	    setSectionCoord(overlaySection, ev.c.sub(overlayDragOff));
	    return;
	}
	super.mousemove(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
	if(overlayDrag != null) {
	    overlayDrag.remove();
	    overlayDrag = null;
	    if(overlaySection != null)
		saveSection(overlaySection);
	    overlaySection = null;
	    try { Utils.prefs().flush(); } catch(Exception ignored) {}
	    return(true);
	}
	return(super.mouseup(ev));
    }

    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
	if(MoonConfig.fightOverlayFixed && (ui.modflags() & UI.MOD_META) != 0) {
	    SplitSection sec = sectionAt(ev.c);
	    if(sec != null) {
		setSectionScale(sec, sectionScale(sec) - (ev.a * 0.1));
		saveSection(sec);
		return(true);
	    }
	}
	return(super.mousewheel(ev));
    }

    public boolean globtype(GlobKeyEvent ev) {
	// ev = new KeyEvent((java.awt.Component)ev.getSource(), ev.getID(), ev.getWhen(), ev.getModifiersEx(), ev.getKeyCode(), ev.getKeyChar(), ev.getKeyLocation());
	{
	    int n = -1;
	    for(int i = 0; i < kb_acts.length; i++) {
		if(kb_acts[i].key().match(ev)) {
		    n = i;
		    break;
		}
	    }
	    int fn = n;
	    if((n >= 0) && (n < actions.length)) {
		MapView map = getparent(GameUI.class).map;
		Coord mvc = map.rootxlate(ui.mc);
		if(held >= 0) {
		    new Release(held);
		    held = -1;
		}
		if(mvc.isect(Coord.z, map.sz)) {
		    map.new Maptest(mvc) {
			    protected void hit(Coord pc, Coord2d mc) {
				wdgmsg("use", fn, 1, ui.modflags(), mc.floor(OCache.posres));
			    }

			    protected void nohit(Coord pc) {
				wdgmsg("use", fn, 1, ui.modflags());
			    }
			}.run();
		}
		if(holdgrab == null)
		    holdgrab = ui.grabkeys(this);
		held = n;
		return(true);
	    }
	}
	if(kb_relcycle.key().match(ev.awt, KeyMatch.S)) {
	    if((ev.mods & KeyMatch.S) == 0) {
		Fightview.Relation cur = fv.current;
		if(cur != null) {
		    fv.lsrel.remove(cur);
		    fv.lsrel.addLast(cur);
		}
	    } else {
		Fightview.Relation last = fv.lsrel.getLast();
		if(last != null) {
		    fv.lsrel.remove(last);
		    fv.lsrel.addFirst(last);
		}
	    }
	    fv.wdgmsg("bump", (int)fv.lsrel.get(0).gobid);
	    return(true);
	}
	return(super.globtype(ev));
    }

    public boolean keydown(KeyDownEvent ev) {
	return(false);
    }

    public boolean keyup(KeyUpEvent ev) {
	if(ev.grabbed && (held >= 0) && (held < kb_acts.length) && (kb_acts[held].key().match(ev.awt, KeyMatch.MODS))) {
	    MapView map = getparent(GameUI.class).map;
	    new Release(held);
	    holdgrab.remove();
	    holdgrab = null;
	    held = -1;
	    return(true);
	}
	return(false);
    }
}
