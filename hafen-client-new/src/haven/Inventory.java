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

import java.util.*;
import java.awt.Color;
import java.awt.image.WritableRaster;

public class Inventory extends Widget implements DTarget {
    public static final Coord sqsz = UI.scale(new Coord(32, 32)).add(1, 1);
    public static final Tex invsq;
    public boolean dropul = true;
    public Coord isz;
    public boolean[] sqmask = null;
    Map<GItem, WItem> wmap = new HashMap<GItem, WItem>();

    private List<SortEntry> sortQueue = null;
    private static int slotNumPx = -1;
    private static int slotNumRgb = -1;
    private static Text.Foundry slotNumFoundry;
    private static final Map<Integer, Tex> slotNumCache = new HashMap<>();

    private static Tex slotNumberTex(int idx) {
	int px = MoonConfig.inventorySlotNumberSize;
	int rgb = MoonConfig.inventorySlotNumberRgb;
	if((slotNumFoundry == null) || (slotNumPx != px) || (slotNumRgb != rgb)) {
	    slotNumPx = px;
	    slotNumRgb = rgb;
	    slotNumFoundry = new Text.Foundry(Text.sans, px).aa(true);
	    slotNumCache.clear();
	}
	return(slotNumCache.computeIfAbsent(idx, n ->
	    new TexI(Utils.outline2(slotNumFoundry.render(Integer.toString(n), MoonConfig.inventorySlotNumberColor()).img, Color.BLACK))));
    }
    private int sortIdx = 0;
    private double sortDelay = 0;
    private enum SortPhase { TAKE, DROP }
    private SortPhase sortPhase = SortPhase.TAKE;

    private static class SortEntry {
	final GItem item;
	final Coord targetSlot;
	final String name;
	SortEntry(GItem item, Coord target, String name) {
	    this.item = item;
	    this.targetSlot = target;
	    this.name = name;
	}
    }

    public void sortByName(boolean descending) {
	if(sortQueue != null) return;
	List<Map.Entry<GItem, WItem>> items = new ArrayList<>(wmap.entrySet());
	if(items.isEmpty()) return;

	items.sort((a, b) -> {
	    String na = itemName(a.getKey());
	    String nb = itemName(b.getKey());
	    return descending ? nb.compareToIgnoreCase(na) : na.compareToIgnoreCase(nb);
	});

	sortQueue = new ArrayList<>();
	int col = 0, row = 0;
	for(Map.Entry<GItem, WItem> e : items) {
	    Coord slot = new Coord(col, row);
	    sortQueue.add(new SortEntry(e.getKey(), slot, itemName(e.getKey())));
	    col++;
	    if(col >= isz.x) { col = 0; row++; }
	}
	sortIdx = 0;
	sortPhase = SortPhase.TAKE;
	sortDelay = 0;
    }

    private static String itemName(GItem item) {
	try {
	    return item.getres().basename();
	} catch(Exception e) {
	    return "zzz";
	}
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	if(sortQueue == null || sortIdx >= sortQueue.size()) {
	    sortQueue = null;
	    return;
	}
	sortDelay -= dt;
	if(sortDelay > 0) return;

	GameUI gui = getparent(GameUI.class);
	if(gui == null) { sortQueue = null; return; }
	if(!gui.hand.isEmpty() && sortPhase == SortPhase.TAKE) {
	    sortDelay = 0.05;
	    return;
	}

	SortEntry se = sortQueue.get(sortIdx);
	if(sortPhase == SortPhase.TAKE) {
	    if(wmap.containsKey(se.item)) {
		WItem wi = wmap.get(se.item);
		se.item.wdgmsg("take", wi.c.div(sqsz));
		sortPhase = SortPhase.DROP;
		sortDelay = 0.08;
	    } else {
		sortIdx++;
	    }
	} else {
	    if(!gui.hand.isEmpty()) {
		wdgmsg("drop", se.targetSlot);
		sortPhase = SortPhase.TAKE;
		sortDelay = 0.08;
		sortIdx++;
	    } else {
		/* Hand empty before drop (lag / user took item): wait, retry same step */
		sortPhase = SortPhase.DROP;
		sortDelay = 0.12;
	    }
	}
    }

    public boolean isSorting() { return sortQueue != null; }

    static {
	Coord sz = sqsz.add(1, 1);
	WritableRaster buf = PUtils.imgraster(sz);
	for(int i = 1, y = sz.y - 1; i < sz.x - 1; i++) {
	    buf.setSample(i, 0, 0, 20); buf.setSample(i, 0, 1, 28); buf.setSample(i, 0, 2, 21); buf.setSample(i, 0, 3, 167);
	    buf.setSample(i, y, 0, 20); buf.setSample(i, y, 1, 28); buf.setSample(i, y, 2, 21); buf.setSample(i, y, 3, 167);
	}
	for(int i = 1, x = sz.x - 1; i < sz.y - 1; i++) {
	    buf.setSample(0, i, 0, 20); buf.setSample(0, i, 1, 28); buf.setSample(0, i, 2, 21); buf.setSample(0, i, 3, 167);
	    buf.setSample(x, i, 0, 20); buf.setSample(x, i, 1, 28); buf.setSample(x, i, 2, 21); buf.setSample(x, i, 3, 167);
	}
	for(int y = 1; y < sz.y - 1; y++) {
	    for(int x = 1; x < sz.x - 1; x++) {
		buf.setSample(x, y, 0, 36); buf.setSample(x, y, 1, 52); buf.setSample(x, y, 2, 38); buf.setSample(x, y, 3, 125);
	    }
	}
	invsq = new TexI(PUtils.rasterimg(buf));
    }

    @RName("inv")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Inventory((Coord)args[0]));
	}
    }

    public void draw(GOut g) {
	Coord c = new Coord();
	int mo = 0;
	for(c.y = 0; c.y < isz.y; c.y++) {
	    for(c.x = 0; c.x < isz.x; c.x++) {
		if((sqmask != null) && sqmask[mo++]) {
		    g.chcolor(64, 64, 64, 255);
		    g.image(invsq, c.mul(sqsz));
		    g.chcolor();
		} else {
		    g.image(invsq, c.mul(sqsz));
		}
	    }
	}
	super.draw(g);
	if(MoonConfig.inventorySlotNumbers) {
	    boolean[] occupied = new boolean[isz.x * isz.y];
	    for(WItem wi : wmap.values()) {
		if(wi == null)
		    continue;
		Coord base = wi.c.sub(1, 1).div(sqsz);
		int sw = Math.max(1, (wi.sz.x + sqsz.x - 1) / sqsz.x);
		int sh = Math.max(1, (wi.sz.y + sqsz.y - 1) / sqsz.y);
		for(int y = 0; y < sh; y++) {
		    for(int x = 0; x < sw; x++) {
			int sx = base.x + x;
			int sy = base.y + y;
			if(sx < 0 || sy < 0 || sx >= isz.x || sy >= isz.y)
			    continue;
			occupied[(sy * isz.x) + sx] = true;
		    }
		}
	    }
	    int idx = 1;
	    Coord p = new Coord();
	    for(p.y = 0; p.y < isz.y; p.y++) {
		for(p.x = 0; p.x < isz.x; p.x++, idx++) {
		    int si = (p.y * isz.x) + p.x;
		    if(occupied[si])
			continue;
		    if((sqmask != null) && sqmask[si])
			continue;
		    Tex tex = slotNumberTex(idx);
		    g.aimage(tex, p.mul(sqsz).add(UI.scale(4), sqsz.y - UI.scale(3)), 0, 1);
		}
	    }
	}
	MoonStorage.drawInventoryHighlights(this, g);
    }
	
    public Inventory(Coord sz) {
	super(sqsz.mul(sz).add(1, 1));
	isz = sz;
    }
    
    public boolean mousewheel(MouseWheelEvent ev) {
	if(ui.modshift) {
	    Inventory minv = getparent(GameUI.class).maininv;
	    if(minv != this) {
		if(ev.a < 0)
		    wdgmsg("invxf", minv.wdgid(), 1);
		else if(ev.a > 0)
		    minv.wdgmsg("invxf", this.wdgid(), 1);
	    }
	}
	return(true);
    }
    
    public void addchild(Widget child, Object... args) {
	add(child);
	Coord c = (Coord)args[0];
	if(child instanceof GItem) {
	    GItem i = (GItem)child;
	    wmap.put(i, add(new WItem(i), c.mul(sqsz).add(1, 1)));
	}
	MoonStorage.onInventoryChanged(this);
    }
    
    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    ui.destroy(wmap.remove(i));
	}
	MoonStorage.onInventoryChanged(this);
    }
    
    public boolean drop(Coord cc, Coord ul) {
	Coord dc;
	if(dropul)
	    dc = ul.add(sqsz.div(2)).div(sqsz);
	else
	    dc = cc.div(sqsz);
	wdgmsg("drop", dc);
	return(true);
    }
	
    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }
	
    public void uimsg(String msg, Object... args) {
	if(msg == "sz") {
	    isz = (Coord)args[0];
	    resize(invsq.sz().add(UI.scale(new Coord(-1, -1))).mul(isz).add(UI.scale(new Coord(1, 1))));
	    sqmask = null;
	} else if(msg == "mask") {
	    boolean[] nmask;
	    if(args[0] == null) {
		nmask = null;
	    } else {
		nmask = new boolean[isz.x * isz.y];
		byte[] raw = (byte[])args[0];
		for(int i = 0; i < isz.x * isz.y; i++)
		    nmask[i] = (raw[i >> 3] & (1 << (i & 7))) != 0;
	    }
	    this.sqmask = nmask;
	} else if(msg == "mode") {
	    dropul = !Utils.bv(args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }
}
