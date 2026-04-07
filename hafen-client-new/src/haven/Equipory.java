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

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static haven.Inventory.invsq;

public class Equipory extends Widget implements DTarget {
    private static final Pattern ARMOR_TEXT = Pattern.compile("(?i)(?:armor\\s*class|класс\\s*брони)[^\\d-]{0,16}(-?\\d+)\\s*[/+]\\s*(-?\\d+)");
    private static final Resource.Image bgi = Resource.loadrimg("gfx/hud/equip/bg");
    private static final int yo = Inventory.sqsz.y, sh = 11;
    private static final Tex bg = new TexI(PUtils.uiscale(bgi.img, Coord.of((sh * yo * bgi.sz.x) / bgi.sz.y, sh * yo)));
    private static final int rx = invsq.sz().x + bg.sz().x;
    private static final Text.Foundry metricf = new Text.Foundry(Text.sans, 11, new Color(236, 224, 255)).aa(true);
    private static final int metricstep = UI.scale(16);
    private static final int metricy = UI.scale(56);
    public static final Coord bgc = new Coord(invsq.sz().x, 0);
    public static final Coord ecoords[] = {
	new Coord( 0,  0 * yo),
	new Coord( 0,  1 * yo),
	new Coord( 0,  3 * yo),
	new Coord(rx,  3 * yo),
	new Coord( 0,  4 * yo),
	new Coord(rx,  4 * yo),
	new Coord( 0,  5 * yo),
	new Coord(rx,  5 * yo),
	new Coord( 0,  6 * yo),
	new Coord(rx,  6 * yo),
	new Coord( 0,  7 * yo),
	new Coord(rx,  7 * yo),
	new Coord( 0,  9 * yo),
	new Coord(rx,  9 * yo),
	new Coord( 0, 10 * yo),
	new Coord(rx, 10 * yo),
	new Coord(invsq.sz().x, 0 * yo),
	new Coord(rx,  1 * yo),
	new Coord(rx,  2 * yo),
	new Coord( 0,  8 * yo),
	new Coord(rx,  8 * yo),
	new Coord(rx,  0 * yo),
	new Coord( 0,  2 * yo),
    };
    public static final Tex[] ebgs = new Tex[ecoords.length];
    public static final Text[] etts = new Text[ecoords.length];
    static Coord isz;
    static {
	isz = new Coord();
	for(Coord ec : ecoords) {
	    if(ec.x + invsq.sz().x > isz.x)
		isz.x = ec.x + invsq.sz().x;
	    if(ec.y + invsq.sz().y > isz.y)
		isz.y = ec.y + invsq.sz().y;
	}
	for(int i = 0; i < ebgs.length; i++) {
	    Resource bgres = Resource.local().loadwait("gfx/hud/equip/ep" + i);
	    Resource.Image img = bgres.layer(Resource.imgc);
	    if(img != null) {
		ebgs[i] = img.tex();
		etts[i] = Text.render(LocalizationManager.autoTranslateProcessed(bgres.flayer(Resource.tooltip).t));
	    }
	}
    }
    Map<GItem, Collection<WItem>> wmap = new HashMap<>();
    private final Avaview ava;
    private Tex dettex = null;
    private Tex subtex = null;
    private Tex armortex = null;
    private int metricdigest = Integer.MIN_VALUE;

    @RName("epry")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    long gobid;
	    if(args.length < 1)
		gobid = -2;
	    else if(args[0] == null)
		gobid = -1;
	    else
		gobid = Utils.uiv(args[0]);
	    return(new Equipory(gobid));
	}
    }

    protected void added() {
	if(ava.avagob == -2)
	    ava.avagob = getparent(GameUI.class).plid;
	super.added();
    }

    public Equipory(long gobid) {
	super(isz);
	ava = add(new Avaview(bg.sz(), gobid, "equcam") {
		public boolean mousedown(MouseDownEvent ev) {
		    return(false);
		}

		public void draw(GOut g) {
		    g.image(bg, Coord.z);
		    super.draw(g);
		}

		final FColor cc = new FColor(0, 0, 0, 0);
		protected FColor clearcolor() {return(cc);}
	    }, bgc);
    }

    public static interface SlotInfo {
	public int slots();
    }

    public void addchild(Widget child, Object... args) {
	if(child instanceof GItem) {
	    add(child);
	    GItem g = (GItem)child;
	    ArrayList<WItem> v = new ArrayList<>();
	    for(int i = 0; i < args.length; i++) {
		int ep = Utils.iv(args[i]);
		if(ep < ecoords.length)
		    v.add(add(new WItem(g), ecoords[ep].add(1, 1)));
	    }
	    v.trimToSize();
	    wmap.put(g, v);
	} else {
	    super.addchild(child, args);
	}
    }

    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    for(WItem v : wmap.remove(i))
		ui.destroy(v);
	}
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "pop") {
	    ava.avadesc = Composited.Desc.decode(ui.sess, args);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public int epat(Coord c) {
	for(int i = 0; i < ecoords.length; i++) {
	    if(c.isect(ecoords[i], invsq.sz()))
		return(i);
	}
	return(-1);
    }

    /** Widget at equipment slot index {@code ep} (same layout as {@link #ecoords}). */
    public WItem witemAt(int ep) {
	if(ep < 0 || ep >= ecoords.length)
	    return(null);
	Coord want = ecoords[ep].add(1, 1);
	for(Widget w = child; w != null; w = w.next) {
	    if(w instanceof WItem && w.c.equals(want))
		return((WItem)w);
	}
	return(null);
    }

    public boolean drop(Coord cc, Coord ul) {
	wdgmsg("drop", epat(cc));
	return(true);
    }

    public void drawslots(GOut g) {
	int slots = 0;
	GameUI gui = getparent(GameUI.class);
	if((gui != null) && (gui.vhand != null)) {
	    try {
		SlotInfo si = ItemInfo.find(SlotInfo.class, gui.vhand.item.info());
		if(si != null)
		    slots = si.slots();
	    } catch(Loading l) {
	    }
	}
	for(int i = 0; i < ecoords.length; i++) {
	    if((slots & (1 << i)) != 0) {
		g.chcolor(255, 255, 0, 64);
		g.frect(ecoords[i].add(1, 1), invsq.sz().sub(2, 2));
		g.chcolor();
	    }
	    g.image(invsq, ecoords[i]);
	    if(ebgs[i] != null)
		g.image(ebgs[i], ecoords[i]);
	}
    }

    public Object tooltip(Coord c, Widget prev) {
	int sl = epat(c);
	if(sl >= 0)
	    return(etts[sl]);
	return(null);
    }

    public void draw(GOut g) {
	drawslots(g);
	super.draw(g);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }

    @Override
    public void dispose() {
	disposeMetricTex();
	super.dispose();
    }

    private void ensureMetricTex() {
	int dig = metricdigest();
	if(dig == metricdigest)
	    return;
	try {
	    rebuildMetricTex();
	    metricdigest = dig;
	} catch(Loading ignored) {
	}
    }

    private int metricdigest() {
	int dig = childseq;
	for(GItem item : wmap.keySet())
	    dig = (dig * 31) + item.infoseq;
	Glob glob = (ui == null || ui.sess == null) ? null : ui.sess.glob;
	dig = (dig * 31) + attrcomp(glob, "prc");
	dig = (dig * 31) + attrcomp(glob, "int");
	dig = (dig * 31) + attrcomp(glob, "explore");
	dig = (dig * 31) + attrcomp(glob, "stealth");
	dig = (dig * 31) + (int)(ava.avagob ^ (ava.avagob >>> 32));
	return(dig);
    }

    static int attrcomp(Glob glob, String name) {
	if(glob == null)
	    return(0);
	Glob.CAttr attr = glob.getcattr(name);
	return((attr == null) ? 0 : attr.comp);
    }

    static int[] summaryMetrics(Equipory eq) {
	int ahard = 0, asoft = 0;
	if(eq != null) {
	    for(GItem item : eq.wmap.keySet()) {
		try {
		    List<ItemInfo> info = item.info();
		    if(isBrokenWear(info))
			continue;
		    int[] armor = armorValues(info);
		    if(armor != null) {
			ahard += armor[0];
			asoft += armor[1];
		    }
		} catch(Loading ignored) {
		}
	    }
	}
	Glob glob = (eq == null || eq.ui == null || eq.ui.sess == null) ? null : eq.ui.sess.glob;
	int prc = attrcomp(glob, "prc");
	int intl = attrcomp(glob, "int");
	int exp = attrcomp(glob, "explore");
	int ste = attrcomp(glob, "stealth");
	return(new int[] {prc * exp, intl * ste, ahard + asoft, ahard, asoft});
    }

    private void rebuildMetricTex() {
	disposeMetricTex();
	int ahard = 0, asoft = 0;
	for(GItem item : wmap.keySet()) {
	    List<ItemInfo> info = item.info();
	    if(isBrokenWear(info))
		continue;
	    int[] armor = armorValues(info);
	    if(armor != null) {
		ahard += armor[0];
		asoft += armor[1];
	    }
	}
	armortex = metricTex(String.format(Locale.US, "Armor Class: %s (%s + %s)",
	    formatMetric(ahard + asoft), formatMetric(ahard), formatMetric(asoft)));

	GameUI gui = getparent(GameUI.class);
	if((gui != null) && (ava.avagob == gui.plid)) {
	    Glob glob = (ui == null || ui.sess == null) ? null : ui.sess.glob;
	    int prc = attrcomp(glob, "prc");
	    int intl = attrcomp(glob, "int");
	    int exp = attrcomp(glob, "explore");
	    int ste = attrcomp(glob, "stealth");
	    dettex = metricTex(String.format(Locale.US, "Detection (Prc*Exp): %s", formatMetric(prc * exp)));
	    subtex = metricTex(String.format(Locale.US, "Subtlety (Int*Ste): %s", formatMetric(intl * ste)));
	}
    }

    private static String formatMetric(int value) {
	return(String.format(Locale.US, "%,d", value).replace(',', '.'));
    }

    private static Tex metricTex(String line) {
	return(new TexI(Utils.outline2(metricf.render(line).img, Color.BLACK)));
    }

    static boolean isBrokenWear(List<ItemInfo> info) {
	if(info == null)
	    return(false);
	for(ItemInfo tip : info) {
	    if(!tip.getClass().getName().endsWith(".Wear"))
		continue;
	    Integer max = reflectInt(tip, "m");
	    Integer dmg = reflectInt(tip, "d");
	    if((max != null) && (dmg != null))
		return((max - dmg) <= 0);
	}
	return(false);
    }

    static int[] armorValues(List<ItemInfo> info) {
	if(info == null)
	    return(null);
	for(ItemInfo tip : info) {
	    int[] parsed = armorValuesDeep(tip, new IdentityHashMap<>(), 0);
	    if(parsed != null)
		return(parsed);
	}
	return(null);
    }

    private static int[] armorValuesDeep(Object obj, IdentityHashMap<Object, Boolean> seen, int depth) {
	if(obj == null || depth > 4)
	    return(null);
	if(seen.put(obj, Boolean.TRUE) != null)
	    return(null);
	if(obj instanceof CharSequence) {
	    return(parseArmorText(obj.toString()));
	} else if(obj instanceof ItemInfo.Contents) {
	    return(armorValues(((ItemInfo.Contents)obj).sub));
	} else if(obj instanceof Iterable<?>) {
	    for(Object sub : (Iterable<?>)obj) {
		int[] parsed = armorValuesDeep(sub, seen, depth + 1);
		if(parsed != null)
		    return(parsed);
	    }
	    return(null);
	} else if(obj.getClass().isArray()) {
	    int n = java.lang.reflect.Array.getLength(obj);
	    for(int i = 0; i < n; i++) {
		int[] parsed = armorValuesDeep(java.lang.reflect.Array.get(obj, i), seen, depth + 1);
		if(parsed != null)
		    return(parsed);
	    }
	    return(null);
	}
	String cn = obj.getClass().getName();
	if(!cn.startsWith("haven."))
	    return(null);
	if(cn.endsWith(".Armor") || cn.toLowerCase(Locale.ROOT).contains("armor")) {
	    Integer hard = findNamedInt(obj, "hard", "hardarmor", "hardArmor", "h");
	    Integer soft = findNamedInt(obj, "soft", "softarmor", "softArmor", "s");
	    if((hard != null) && (soft != null))
		return(new int[] {hard, soft});
	    int[] pair = firstTwoNumberFields(obj);
	    if(pair != null)
		return(pair);
	}
	for(Class<?> cl = obj.getClass(); cl != null; cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
		    continue;
		try {
		    if(!f.canAccess(obj))
			f.setAccessible(true);
		    int[] parsed = armorValuesDeep(f.get(obj), seen, depth + 1);
		    if(parsed != null)
			return(parsed);
		} catch(ReflectiveOperationException | RuntimeException ignored) {
		}
	    }
	}
	return(null);
    }

    private static int[] parseArmorText(String raw) {
	if(raw == null)
	    return(null);
	Matcher m = ARMOR_TEXT.matcher(raw);
	if(m.find()) {
	    try {
		return(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
	    } catch(NumberFormatException ignored) {
	    }
	}
	return(null);
    }

    private static Integer findNamedInt(Object obj, String... names) {
	for(String name : names) {
	    Integer val = reflectInt(obj, name);
	    if(val != null)
		return(val);
	}
	return(null);
    }

    private static int[] firstTwoNumberFields(Object obj) {
	ArrayList<Integer> vals = new ArrayList<>(2);
	for(Class<?> cl = obj.getClass(); cl != null; cl = cl.getSuperclass()) {
	    for(Field f : cl.getDeclaredFields()) {
		if(Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
		    continue;
		try {
		    if(!f.canAccess(obj))
			f.setAccessible(true);
		    Object val = f.get(obj);
		    if(val instanceof Number) {
			vals.add(((Number)val).intValue());
			if(vals.size() >= 2)
			    return(new int[] {vals.get(0), vals.get(1)});
		    }
		} catch(ReflectiveOperationException | SecurityException ignored) {
		}
	    }
	}
	return(null);
    }

    private static Integer reflectInt(Object obj, String name) {
	try {
	    Field f;
	    try {
		f = obj.getClass().getField(name);
	    } catch(NoSuchFieldException e) {
		f = obj.getClass().getDeclaredField(name);
		f.setAccessible(true);
	    }
	    Object val = f.get(obj);
	    if(val instanceof Number)
		return(((Number)val).intValue());
	} catch(ReflectiveOperationException | SecurityException ignored) {
	}
	return(null);
    }

    private void drawMetric(GOut g, Tex tex, int row) {
	if(tex == null)
	    return;
	int x = bgc.x + (bg.sz().x / 2);
	int y = bgc.y + bg.sz().y - metricy + (row * metricstep);
	Coord tsz = tex.sz();
	Coord ul = Coord.of(x - (tsz.x / 2) - UI.scale(5), y - UI.scale(1));
	g.chcolor(10, 8, 18, 154);
	g.frect(ul, Coord.of(tsz.x + UI.scale(10), tsz.y + UI.scale(3)));
	g.chcolor(178, 130, 236, 92);
	g.rect(ul, Coord.of(tsz.x + UI.scale(10), tsz.y + UI.scale(3)).sub(1, 1));
	g.chcolor();
	g.aimage(tex, Coord.of(x, y), 0.5, 0.0);
    }

    private void disposeMetricTex() {
	dettex = disposeMetricTex(dettex);
	subtex = disposeMetricTex(subtex);
	armortex = disposeMetricTex(armortex);
    }

    private static Tex disposeMetricTex(Tex tex) {
	if(tex != null)
	    tex.dispose();
	return(null);
    }
}
