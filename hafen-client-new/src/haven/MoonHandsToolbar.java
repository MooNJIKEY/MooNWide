package haven;

import static haven.Inventory.invsq;
import static haven.Inventory.sqsz;

import java.util.List;

/**
 * Two hand slots (main / off) at double inventory cell size — mirrors {@link Equipory} indices 6 and 7.
 * Clicks, drag-drop from the cursor, and tooltips are wired to the real {@link WItem} widgets like the equipment window.
 */
public class MoonHandsToolbar extends MoonPanel implements DTarget {
    /** Left column / primary hand slot in default {@link Equipory} layout. */
    public static final int SLOT_MAIN = 6;
    /** Right column / off hand. */
    public static final int SLOT_OFF = 7;

    private static final int GAP = UI.scale(4);

    /** Slot interior ~2× normal inventory cell (same proportions as {@link #sqsz}). */
    private static final Coord CELL = Coord.of(sqsz.x * 2, sqsz.y * 2);

    private final GameUI gui;

    /** Tooltip hover state (mirrors {@link WItem#tooltip} timing; we are not a WItem so we track here). */
    private WItem ttWi;
    private double ttHoverStart;
    private WItem.ShortTip ttShort;
    private WItem.LongTip ttLong;
    private List<ItemInfo> ttInfo;

    public MoonHandsToolbar(GameUI gui) {
	super(Coord.z, "moon-hands", LocalizationManager.tr("hands.toolbar"));
	this.gui = gui;
	setShowLock(true);
	setResizable(false);
	int w = CELL.x * 2 + GAP;
	int h = CELL.y;
	resizeContent(Coord.of(w, h));
    }

    @Override
    protected boolean roundedChrome() {
	return true;
    }

    @Override
    protected boolean externalActionButtons() {
	return false;
    }

    private Coord slotOrigin(int slot) {
	Coord off = contentOffset();
	if(slot == SLOT_MAIN)
	    return off;
	if(slot == SLOT_OFF)
	    return off.add(CELL.x + GAP, 0);
	return null;
    }

    /** Which equipment slot (6 / 7) the coordinate hits, or -1. */
    private int slotAt(Coord c) {
	Coord off = contentOffset();
	Coord p0 = off;
	Coord p1 = off.add(CELL.x + GAP, 0);
	if(c.isect(p0, CELL))
	    return SLOT_MAIN;
	if(c.isect(p1, CELL))
	    return SLOT_OFF;
	return -1;
    }

    /** Map a point in this widget to coordinates inside {@link WItem} (same as real equip slot). */
    private Coord mapToWItem(Coord c, int slot, WItem wi) {
	Coord p0 = slotOrigin(slot);
	if(p0 == null)
	    return wi.sz.div(2);
	Coord ul = p0.add(1, 1);
	Coord inner = CELL.sub(2, 2);
	Coord margin = inner.sub(sqsz.sub(2, 2)).div(2);
	Coord rel = c.sub(ul).sub(margin);
	int dw = Math.max(1, sqsz.x - 2), dh = Math.max(1, sqsz.y - 2);
	int sx = (int)((rel.x + 0.5f) * wi.sz.x / dw);
	int sy = (int)((rel.y + 0.5f) * wi.sz.y / dh);
	sx = Utils.clip(sx, 0, Math.max(0, wi.sz.x - 1));
	sy = Utils.clip(sy, 0, Math.max(0, wi.sz.y - 1));
	return new Coord(sx, sy);
    }

    private boolean inSlotArea(Coord c) {
	Coord co = contentOffset();
	return c.isect(co, contentSize()) && c.y >= co.y;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	if(inSlotArea(ev.c)) {
	    int slot = slotAt(ev.c);
	    if(slot >= 0) {
		Equipory eq = gui.findEquipory();
		if(eq != null) {
		    WItem wi = eq.witemAt(slot);
		    if(wi != null && (ev.b == 1 || ev.b == 3)) {
			Coord wic = mapToWItem(ev.c, slot, wi);
			return wi.mousedown(ev.derive(wic));
		    }
		}
	    }
	}
	return super.mousedown(ev);
    }

    @Override
    public boolean drop(Coord cc, Coord ul) {
	int slot = slotAt(cc);
	if(slot < 0)
	    return false;
	Equipory eq = gui.findEquipory();
	if(eq == null)
	    return false;
	eq.wdgmsg("drop", slot);
	return true;
    }

    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
	int slot = slotAt(cc);
	if(slot < 0)
	    return false;
	Equipory eq = gui.findEquipory();
	if(eq == null)
	    return false;
	WItem wi = eq.witemAt(slot);
	if(wi == null)
	    return false;
	Coord wic = mapToWItem(cc, slot, wi);
	return wi.iteminteract(wic, ul);
    }

    @Override
    public boolean mousehover(MouseHoverEvent ev, boolean on) {
	if(on) {
	    int slot = slotAt(ev.c);
	    Equipory eq = gui.findEquipory();
	    if(eq != null && slot >= 0) {
		WItem wi = eq.witemAt(slot);
		if(wi != null) {
		    Coord wic = mapToWItem(ev.c, slot, wi);
		    return wi.mousehover(ev.derive(wic), on);
		}
	    }
	}
	return super.mousehover(ev, on);
    }

    @Override
    protected void drawContent(GOut g) {
	Coord off = contentOffset();
	Coord p0 = off;
	Coord p1 = off.add(CELL.x + GAP, 0);
	g.image(invsq, p0, CELL);
	g.image(invsq, p1, CELL);
	Equipory eq = gui.findEquipory();
	if(eq == null)
	    return;
	drawMirrored(g, p0.add(1, 1), eq.witemAt(SLOT_MAIN));
	drawMirrored(g, p1.add(1, 1), eq.witemAt(SLOT_OFF));
    }

    private static void drawMirrored(GOut g, Coord ul, WItem wi) {
	if(wi == null)
	    return;
	try {
	    GSprite spr = wi.item.spr();
	    if(spr == null)
		return;
	    Coord inner = CELL.sub(2, 2);
	    /* Center 1× slot sprite in 2× cell (readable like main inventory). */
	    Coord margin = inner.sub(sqsz.sub(2, 2)).div(2);
	    Coord innerSz = sqsz.sub(2, 2);
	    GOut g2 = g.reclip(ul.add(margin), innerSz);
	    wi.drawmain(g2, spr);
	    wi.moonDrawInventoryQualityMirror(g2, innerSz);
	} catch(Loading ignored) {}
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
	int slot = slotAt(c);
	Equipory eq = gui.findEquipory();
	WItem wi = (eq != null && slot >= 0) ? eq.witemAt(slot) : null;
	if(wi != null) {
	    double now = Utils.rtime();
	    if(!(prev == this && ttWi == wi))
		ttHoverStart = now;
	    ttWi = wi;
	    try {
		List<ItemInfo> info = wi.item.info();
		if(info.size() < 1) {
		    ttWi = null;
		    return super.tooltip(c, prev);
		}
		if(info != ttInfo) {
		    ttShort = null;
		    ttLong = null;
		    ttInfo = info;
		}
		if(now - ttHoverStart < 1.0) {
		    if(ttShort == null)
			ttShort = wi.new ShortTip(info);
		    return ttShort;
		} else {
		    if(ttLong == null)
			ttLong = wi.new LongTip(info);
		    return ttLong;
		}
	    } catch(Loading l) {
		return null;
	    }
	}
	ttWi = null;
	ttInfo = null;
	ttShort = null;
	ttLong = null;
	if(c.isect(contentOffset().add(CELL.x + GAP, 0), CELL))
	    return LocalizationManager.tr("hands.off");
	if(c.isect(contentOffset(), CELL))
	    return LocalizationManager.tr("hands.main");
	return super.tooltip(c, prev);
    }
}
