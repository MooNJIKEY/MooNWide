package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import haven.render.Texture;

public class MoonQuickPanel extends MoonPanel {
    private final List<QBtn> buttons = new ArrayList<>();
    private static final Coord BTN_SZ = Inventory.sqsz;
    private boolean vertical = false;
    private int pressedIdx = -1;
    private Coord pressStart;
    private static final int DRAG_THRESHOLD = UI.scale(5);

    public MoonQuickPanel(String key) {
        super(Coord.z, key, null);
        setShowLock(true);
        setResizable(false);
        vertical = Utils.getprefb(key + "-vert", false);
    }

    public void addButton(String tooltip, String resPath, Consumer<MenuGrid.Interaction> action) {
        buttons.add(new QBtn(tooltip, resPath, action));
        relayout();
    }

    public void toggleOrientation() {
        vertical = !vertical;
        Utils.setprefb(key + "-vert", vertical);
        try { Utils.prefs().flush(); } catch (Exception ignored) {}
        relayout();
    }

    private void relayout() {
        int n = buttons.size();
        if (n == 0) return;
        Coord csz;
        if (vertical)
            csz = Coord.of(BTN_SZ.x + 2, (BTN_SZ.y + 1) * n + 1);
        else
            csz = Coord.of((BTN_SZ.x + 1) * n + 1, BTN_SZ.y + 2);
        super.resize(wrapSize(csz));
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
        return button == 1;
    }

    private Coord btnLocalPos(int i) {
        if (vertical)
            return Coord.of(1, (BTN_SZ.y + 1) * i + 1);
        else
            return Coord.of((BTN_SZ.x + 1) * i + 1, 1);
    }

    @Override
    protected void drawContent(GOut g) {
        Coord off = contentOffset();
        int n = buttons.size();
        for (int i = 0; i < n; i++) {
            Coord p = off.add(btnLocalPos(i));
            g.image(Inventory.invsq, p);
            QBtn btn = buttons.get(i);
            try {
                Tex tex = btn.icon();
                if (tex != null)
                    g.image(tex, p.add(1, 1));
            } catch (Loading ignored) {}
            if (i == pressedIdx) {
                g.chcolor(255, 255, 255, 40);
                g.frect(p.add(1, 1), BTN_SZ.sub(2, 2));
                g.chcolor();
            }
        }
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        super.mousemove(ev);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (ev.b == 1) {
            int idx = hitButton(ev.c);
            if (idx >= 0 && idx < buttons.size()) {
                pressedIdx = idx;
                pressStart = ev.c;
            } else {
                pressedIdx = -1;
                pressStart = null;
            }
        }
        return super.mousedown(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if (ev.b == 1 && pressedIdx >= 0 && dm == null) {
            int idx = pressedIdx;
            pressedIdx = -1;
            if (pressStart != null && ev.c.dist(pressStart) < DRAG_THRESHOLD) {
                if (idx < buttons.size()) {
                    buttons.get(idx).action.accept(new MenuGrid.Interaction(1, ui.modflags()));
                    return true;
                }
            }
        }
        pressedIdx = -1;
        pressStart = null;
        return super.mouseup(ev);
    }

    private int hitButton(Coord wc) {
        Coord lc = wc.sub(contentOffset());
        if (vertical) {
            if (lc.x < 1 || lc.x >= BTN_SZ.x + 1) return -1;
            if (lc.y < 1) return -1;
            int idx = (lc.y - 1) / (BTN_SZ.y + 1);
            if (idx < 0 || idx >= buttons.size()) return -1;
            int ry = (lc.y - 1) % (BTN_SZ.y + 1);
            if (ry >= BTN_SZ.y) return -1;
            return idx;
        } else {
            if (lc.y < 1 || lc.y >= BTN_SZ.y + 1) return -1;
            if (lc.x < 1) return -1;
            int idx = (lc.x - 1) / (BTN_SZ.x + 1);
            if (idx < 0 || idx >= buttons.size()) return -1;
            int rx = (lc.x - 1) % (BTN_SZ.x + 1);
            if (rx >= BTN_SZ.x) return -1;
            return idx;
        }
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int idx = hitButton(c);
        if (idx >= 0 && idx < buttons.size())
            return buttons.get(idx).tooltip;
        return super.tooltip(c, prev);
    }

    @Override
    protected void addPanelMenuEntries(List<MoonDropdownMenu.Entry> entries) {
        entries.add(MoonDropdownMenu.Entry.action(
            () -> LocalizationManager.tr("shortcut.orientation") + ": "
                + LocalizationManager.tr(vertical ? "shortcut.vertical" : "shortcut.horizontal"),
            () -> {
                toggleOrientation();
                closeHeaderMenu();
            }));
        addResetPositionEntry(entries, () -> {
            MoonQuickPanel.this.c = MoonQuickPanel.this.parent.sz.sub(MoonQuickPanel.this.sz).sub(UI.scale(10), UI.scale(10));
            savePosition();
        });
    }

    private static class QBtn {
        final String tooltip;
        final String resPath;
        final Consumer<MenuGrid.Interaction> action;
        private Tex cachedIcon;
        /** After one full pass, stop calling {@code loadwait} from {@link #icon()} (avoids blocking every frame). */
        private boolean iconLoadDone;

        QBtn(String tooltip, String resPath, Consumer<MenuGrid.Interaction> action) {
            this.tooltip = tooltip;
            this.resPath = resPath;
            this.action = action;
        }

        /**
         * Custom shortcuts use {@code custom/paginae/...} art packed in the JAR; when those files are
         * absent, fall back to stock HUD resources so the two Moon toolbars always show real icons.
         */
        private static List<String> iconTryPaths(String primary) {
            List<String> paths = new ArrayList<>();
            if (primary != null && !primary.isEmpty())
                paths.add(primary);
            if (primary != null && primary.startsWith("custom/paginae/")) {
                if (primary.endsWith("/inv"))
                    paths.add("gfx/hud/csearch-bg");
                else if (primary.endsWith("/equ"))
                    paths.add("gfx/hud/equip/ep0");
                else if (primary.endsWith("/char"))
                    paths.add("gfx/hud/chr/foodm");
                else if (primary.endsWith("/kithnkin"))
                    paths.add("gfx/hud/buttons/kin");
                else if (primary.endsWith("/smap"))
                    paths.add("gfx/hud/mmap/flag");
                else if (primary.endsWith("/lmap"))
                    paths.add("gfx/hud/lbtn-map");
                else if (primary.endsWith("/chat"))
                    paths.add("gfx/hud/hb-btn-chat");
                else if (primary.endsWith("/crafting") || primary.endsWith("/search"))
                    paths.add("gfx/hud/csearch-bg");
                else if (primary.endsWith("/builderwindow"))
                    paths.add("gfx/hud/curs/wrench");
                else if (primary.endsWith("/fakegrid"))
                    paths.add("gfx/hud/bosq");
                else if (primary.endsWith("/opts"))
                    paths.add("gfx/hud/curs/wrench");
            }
            paths.add("gfx/hud/sc-next");
            paths.add("gfx/hud/sc-back");
            return paths;
        }

        private static Tex loadRasterIcon(String path) {
            try (InputStream in = MoonQuickPanel.class.getClassLoader().getResourceAsStream("res/" + path)) {
                if (in == null)
                    return null;
                BufferedImage img = ImageIO.read(in);
                if (img == null)
                    return null;
                return new TexI(PUtils.uiscale(img, BTN_SZ.sub(2, 2)), false).filter(Texture.Filter.NEAREST);
            } catch (Exception e) {
                return null;
            }
        }

        Tex icon() {
            if (cachedIcon != null || iconLoadDone)
                return cachedIcon;
            if (resPath.endsWith(".png")) {
                cachedIcon = loadRasterIcon(resPath);
                iconLoadDone = true;
                return cachedIcon;
            }
            for (String p : iconTryPaths(resPath)) {
                try {
                    Resource res = Resource.local().loadwait(p);
                    Resource.Image img = res.layer(Resource.imgc);
                    if (img != null) {
                        cachedIcon = img.tex();
                        iconLoadDone = true;
                        return cachedIcon;
                    }
                } catch (Loading l) {
                    /* Let {@link MoonQuickPanel#drawContent} skip this frame; retry when data is ready. */
                    throw l;
                } catch (Exception ignored) {
                    /* Missing or invalid resource — try next fallback path. */
                }
            }
            iconLoadDone = true;
            return null;
        }
    }
}
