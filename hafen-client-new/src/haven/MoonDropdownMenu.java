package haven;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class MoonDropdownMenu extends Widget {
    public static class Entry {
        public final Supplier<String> label;
        public final Runnable action;
        public final Color color;
        public final boolean separator;

        private Entry(Supplier<String> label, Runnable action, Color color, boolean separator) {
            this.label = label;
            this.action = action;
            this.color = color;
            this.separator = separator;
        }

        public static Entry action(String label, Runnable action) {
            return new Entry(() -> label, action, MoonUiTheme.TEXT, false);
        }

        public static Entry action(Supplier<String> label, Runnable action) {
            return new Entry(label, action, MoonUiTheme.TEXT, false);
        }

        public static Entry action(Supplier<String> label, Color color, Runnable action) {
            return new Entry(label, action, color, false);
        }

        public static Entry separator() {
            return new Entry(() -> "", null, MoonUiTheme.TEXT, true);
        }
    }

    private static final int PAD_X = UI.scale(16);
    private static final int PAD_Y = UI.scale(14);
    private static final int ROW_H = UI.scale(30);
    private static final int SEP_H = UI.scale(10);
    private static final int SHADOW = UI.scale(4);

    private final List<Entry> entries;
    private final Runnable onClose;
    private final double[] hoverAnim;
    private int hover = -1;
    private Tex chrome;
    private double reveal = 0.0;
    private double pulse = 0.0;

    public MoonDropdownMenu(List<Entry> entries, Runnable onClose) {
        super(Coord.z);
        this.entries = new ArrayList<>(entries);
        this.onClose = onClose;
        resize(computeSize());
        this.hoverAnim = new double[this.entries.size()];
        this.chrome = new TexI(renderChrome(sz), false);
    }

    private Coord computeSize() {
        int w = UI.scale(176);
        int h = PAD_Y * 2;
        for(Entry entry : entries) {
            if(entry.separator) {
                h += SEP_H;
                continue;
            }
            String text = Objects.toString(entry.label.get(), "");
            w = Math.max(w, MoonUiTheme.menuText(text, entry.color).sz().x + (PAD_X * 2) + UI.scale(28));
            h += ROW_H;
        }
        return Coord.of(w, h);
    }

    @Override
    public void destroy() {
        super.destroy();
        if(chrome != null) {
            chrome.dispose();
            chrome = null;
        }
        if(onClose != null)
            onClose.run();
    }

    @Override
    public void draw(GOut g) {
        double a = Utils.smoothstep(Math.max(0.0, Math.min(1.0, reveal)));
        int alpha = Math.max(0, Math.min(255, (int)Math.round(255 * a)));
        int w = Math.max(UI.scale(12), (int)Math.round(sz.x * (0.92 + (0.08 * a))));
        int h = Math.max(UI.scale(12), (int)Math.round(sz.y * (0.88 + (0.12 * a))));
        int rise = (int)Math.round((1.0 - a) * UI.scale(8));
        Coord drawPos = Coord.of((sz.x - w) / 2, (sz.y - h) / 2 + rise);
        if(chrome != null) {
            g.chcolor(255, 255, 255, alpha);
            g.image(chrome, drawPos, Coord.of(w, h));
            g.chcolor();
        }

        Coord orb = Coord.of(sz.x - PAD_X - UI.scale(12), PAD_Y - UI.scale(2));
        int orbAlpha = Math.max(0, Math.min(255, (int)Math.round((72 + (20 * Math.sin(pulse * 3.6))) * a)));
        g.chcolor(MoonUiTheme.ACCENT.getRed(), MoonUiTheme.ACCENT.getGreen(), MoonUiTheme.ACCENT.getBlue(), orbAlpha);
        g.fellipse(orb, Coord.of(UI.scale(4), UI.scale(4)));
        g.chcolor();

        int y = PAD_Y + (int)Math.round((1.0 - a) * UI.scale(6));
        for(int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if(entry.separator) {
                int cy = y + (SEP_H / 2);
                g.chcolor(MoonUiTheme.MENU_SEPARATOR);
                g.line(Coord.of(PAD_X, cy), Coord.of(sz.x - PAD_X, cy), 1);
                g.chcolor();
                y += SEP_H;
                continue;
            }
            double ha = hoverAnim[i];
            if(ha > 0.001) {
                int fillA = Math.max(0, Math.min(255, (int)Math.round((8 + (20 * ha)) * a)));
                int edgeA = Math.max(0, Math.min(255, (int)Math.round((24 + (46 * ha)) * a)));
                drawCapsule(g, Coord.of(PAD_X - UI.scale(6), y + UI.scale(1)),
                    Coord.of(sz.x - ((PAD_X - UI.scale(6)) * 2), ROW_H - UI.scale(2)),
                    new Color(98, 48, 188, fillA),
                    new Color(MoonUiTheme.ACCENT.getRed(), MoonUiTheme.ACCENT.getGreen(), MoonUiTheme.ACCENT.getBlue(), edgeA));
            }
            String text = Objects.toString(entry.label.get(), "");
            Text.Line line = MoonUiTheme.menuText(text, entry.color);
            g.chcolor(255, 255, 255, alpha);
            g.image(line.tex(), Coord.of(PAD_X, y + Math.max(0, (ROW_H - line.sz().y) / 2) - UI.scale(1)));
            g.chcolor();
            y += ROW_H;
        }
        super.draw(g);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        reveal = Math.min(1.0, reveal + (dt * 7.5));
        pulse += dt;
        for(int i = 0; i < hoverAnim.length; i++) {
            double target = (i == hover) ? 1.0 : 0.0;
            double speed = (i == hover) ? 12.0 : 9.0;
            hoverAnim[i] += (target - hoverAnim[i]) * Math.min(1.0, dt * speed);
        }
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        hover = entryAt(ev.c);
        super.mousemove(ev);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1)
            return true;
        int idx = entryAt(ev.c);
        if(idx >= 0 && idx < entries.size()) {
            Entry entry = entries.get(idx);
            if(!entry.separator && entry.action != null)
                entry.action.run();
        }
        destroy();
        return true;
    }

    private int entryAt(Coord c) {
        int y = PAD_Y;
        for(int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int h = entry.separator ? SEP_H : ROW_H;
            if(c.y >= y && c.y < y + h)
                return entry.separator ? -1 : i;
            y += h;
        }
        return -1;
    }

    @Override
    public boolean checkhit(Coord c) {
        return c.isect(Coord.z, sz);
    }

    private static void drawCapsule(GOut g, Coord ul, Coord sz, Color fill, Color border) {
        int r = Math.max(UI.scale(6), sz.y / 2);
        int bodyW = Math.max(0, sz.x - (r * 2));
        Coord left = ul.add(r, sz.y / 2);
        Coord right = ul.add(sz.x - r, sz.y / 2);

        g.chcolor(fill);
        if(bodyW > 0)
            g.frect(ul.add(r, 0), Coord.of(bodyW, sz.y));
        g.fellipse(left, Coord.of(r, r));
        g.fellipse(right, Coord.of(r, r));

        g.chcolor(border);
        g.line(ul.add(r, 0), ul.add(sz.x - r, 0), 1);
        g.line(ul.add(r, sz.y - 1), ul.add(sz.x - r, sz.y - 1), 1);
        g.line(ul.add(0, r), ul.add(0, sz.y - r), 1);
        g.line(ul.add(sz.x - 1, r), ul.add(sz.x - 1, sz.y - r), 1);
        g.chcolor();
    }

    private static BufferedImage renderChrome(Coord sz) {
        BufferedImage img = TexI.mkbuf(sz);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        RoundRectangle2D.Float shadow = new RoundRectangle2D.Float(SHADOW, SHADOW,
            sz.x - (SHADOW * 2) - UI.scale(1), sz.y - (SHADOW * 2) - UI.scale(1), UI.scale(26), UI.scale(26));
        g.setColor(new Color(2, 2, 8, 136));
        g.fill(shadow);

        RoundRectangle2D.Float body = new RoundRectangle2D.Float(UI.scale(1), UI.scale(1),
            sz.x - UI.scale(3), sz.y - UI.scale(3), UI.scale(26), UI.scale(26));
        g.setPaint(new GradientPaint(0, 0, MoonUiTheme.MENU_TOP, 0, sz.y, MoonUiTheme.MENU_BOTTOM));
        g.fill(body);

        RoundRectangle2D.Float shine = new RoundRectangle2D.Float(UI.scale(3), UI.scale(3),
            sz.x - UI.scale(7), Math.max(UI.scale(18), (int)Math.round(sz.y * 0.34)), UI.scale(20), UI.scale(20));
        g.setPaint(new GradientPaint(0, 0, new Color(194, 152, 255, 52), 0, shine.y + shine.height,
            new Color(194, 152, 255, 0)));
        g.fill(shine);

        g.setColor(MoonUiTheme.BORDER);
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 1.05f)));
        g.draw(body);
        g.setColor(new Color(MoonUiTheme.BORDER_SOFT.getRed(), MoonUiTheme.BORDER_SOFT.getGreen(), MoonUiTheme.BORDER_SOFT.getBlue(), 72));
        g.draw(new RoundRectangle2D.Float(UI.scale(3), UI.scale(3), sz.x - UI.scale(7), sz.y - UI.scale(7),
            UI.scale(22), UI.scale(22)));

        g.setColor(new Color(MoonUiTheme.ACCENT.getRed(), MoonUiTheme.ACCENT.getGreen(), MoonUiTheme.ACCENT.getBlue(), 108));
        g.fillOval(sz.x - PAD_X - UI.scale(12), PAD_Y - UI.scale(6), UI.scale(8), UI.scale(8));
        g.dispose();
        return img;
    }

    public static Coord toRoot(Widget widget, Coord c) {
        Coord ret = c;
        for(Widget p = widget; p != null && p != widget.ui.root; p = p.parent)
            ret = ret.add(p.c);
        return ret;
    }

    public static MoonDropdownMenu popup(Widget owner, Coord rootAnchor, List<Entry> entries, Runnable onClose) {
        MoonDropdownMenu menu = new MoonDropdownMenu(entries, onClose);
        Coord rootSz = owner.ui.root.sz;
        Coord pos = rootAnchor.add(-menu.sz.x + MoonUiTheme.MENU_BTN_W, UI.scale(6));
        if(pos.x + menu.sz.x > rootSz.x)
            pos.x = rootSz.x - menu.sz.x - UI.scale(4);
        if(pos.x < UI.scale(4))
            pos.x = UI.scale(4);
        if(pos.y + menu.sz.y > rootSz.y)
            pos.y = rootAnchor.y - menu.sz.y - UI.scale(6);
        if(pos.y < UI.scale(4))
            pos.y = UI.scale(4);
        owner.ui.root.add(menu, pos);
        return menu;
    }
}
