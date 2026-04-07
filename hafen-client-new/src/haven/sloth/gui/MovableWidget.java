package haven.sloth.gui;

import haven.Coord;
import haven.GameUI;
import haven.GOut;
import haven.UI;
import haven.Utils;
import haven.Widget;

/**
 * MooNWide movable widget: drag to move, lock to fix position.
 * Uses prefs (Utils.getprefc/setprefc, getprefb/setprefb) instead of SQLite.
 */
public abstract class MovableWidget extends Widget {
    public static final double VISIBLE_PER = 0.9;

    protected final String key;
    private boolean lock;
    protected UI.Grab dm = null;
    protected Coord doff;
    protected boolean movableBg;
    protected boolean loadPosition = true;

    public MovableWidget(Coord sz, String name) {
        super(sz);
        this.key = name;
    }

    public MovableWidget(String name) {
        super();
        this.key = name;
    }

    public MovableWidget() {
        super();
        this.key = null;
    }

    public boolean isLock() { return lock; }
    public void lock(boolean l) { lock = l; }
    public boolean locked() { return lock; }

    public void toggleLock() {
        lock(!lock);
        savePosition();
    }

    public boolean moving() { return dm != null; }

    private String posKey() { return key == null ? null : "wpos-" + key; }
    private String lockKey() { return key == null ? null : "wlock-" + key; }

    private void loadPosition() {
        if (key != null) {
            Coord saved = Utils.getprefc(posKey(), null);
            if (saved != null)
                c = saved;
            lock = Utils.getprefb(lockKey(), false);
        }
    }

    protected void savePosition() {
        if (key != null) {
            Utils.setprefc(posKey(), c);
            Utils.setprefb(lockKey(), lock);
            try { Utils.prefs().flush(); } catch(Exception ignored) {}
        }
    }

    @Override
    protected void added() {
        if (loadPosition)
            loadPosition();
        super.added();
    }

    /** Ctrl+right click to start drag. */
    protected boolean moveHit(Coord mc, int button) {
        return ui.modflags() == UI.MOD_CTRL && button == 3;
    }

    /** Left click to start drag when simpledrag is enabled. */
    protected boolean altMoveHit(Coord mc, int button) {
        return ui.modflags() == 0 && button == 1 && haven.DefSettings.simpledraging;
    }

    @Override
    public boolean mousedown(Widget.MouseDownEvent ev) {
        if (super.mousedown(ev))
            return true;
        if ((ui.modflags() == UI.MOD_CTRL) && (ev.b == 2)) {
            toggleLock();
            return true;
        }
        if ((moveHit(ev.c, ev.b) || altMoveHit(ev.c, ev.b)) && !isLock()) {
            movableBg = true;
            dm = ui.grabmouse(this);
            doff = ev.c;
            parent.setfocus(this);
            raise();
            return true;
        }
        return false;
    }

    /** Clamp position so the widget stays fully inside its parent. */
    protected void keepInParent() {
        if (parent == null || parent.sz == null) return;
        c.x = Math.max(0, Math.min(c.x, Math.max(0, parent.sz.x - sz.x)));
        c.y = Math.max(0, Math.min(c.y, Math.max(0, parent.sz.y - sz.y)));
    }

    @Override
    public boolean mouseup(Widget.MouseUpEvent ev) {
        if (dm != null) {
            movableBg = false;
            dm.remove();
            dm = null;
            keepInParent();
            savePosition();
            GameUI gui = getparent(GameUI.class);
            if(gui != null)
                gui.restoreMoonHudZOrder();
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void mousemove(Widget.MouseMoveEvent ev) {
        if (dm != null && doff != null) {
            move(this.c.add(ev.c.sub(doff)));
            keepInParent();
        } else {
            super.mousemove(ev);
        }
    }

    @Override
    public void draw(GOut g) {
        super.draw(g);
        if (movableBg) {
            g.chcolor(60, 60, 60, 120);
            g.frect(Coord.z, sz);
            g.chcolor();
        }
    }
}
