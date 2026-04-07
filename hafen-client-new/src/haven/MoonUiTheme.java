package haven;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

public final class MoonUiTheme {
    public static final int CHROME_STYLE_COMPACT = 0;
    public static final int CHROME_STYLE_ORBITAL = 1;
    public static final Color BODY_TOP = new Color(28, 24, 48, 206);
    public static final Color BODY_BOTTOM = new Color(16, 14, 30, 192);
    public static final Color BODY_SHADE = new Color(132, 78, 255, 12);
    public static final Color HEADER_TOP = new Color(64, 50, 116, 214);
    public static final Color HEADER_BOTTOM = new Color(30, 24, 62, 198);
    public static final Color HEADER_GLOW = new Color(194, 154, 255, 18);
    public static final Color BORDER = new Color(170, 126, 240, 168);
    public static final Color BORDER_SOFT = new Color(224, 202, 255, 22);
    public static final Color ACCENT = new Color(239, 230, 255);
    public static final Color ACCENT_DARK = new Color(172, 138, 228);
    public static final Color TEXT = new Color(245, 239, 255);
    public static final Color TEXT_MUTED = new Color(190, 174, 220);
    public static final Color MENU_TOP = new Color(36, 28, 68, 242);
    public static final Color MENU_BOTTOM = new Color(18, 14, 36, 236);
    public static final Color MENU_HOVER = new Color(202, 176, 255, 18);
    public static final Color MENU_SEPARATOR = new Color(150, 118, 214, 30);
    public static final Color LOCKED = new Color(208, 170, 255, 196);
    public static final Color UNLOCKED = new Color(154, 130, 196, 172);
    public static final Color BUTTON_TOP = new Color(132, 136, 148, 244);
    public static final Color BUTTON_BOTTOM = new Color(82, 86, 98, 240);
    public static final Color BUTTON_PRESS_TOP = new Color(154, 158, 172, 248);
    public static final Color BUTTON_PRESS_BOTTOM = new Color(96, 100, 114, 244);
    public static final Color BUTTON_BORDER = new Color(214, 220, 232, 112);
    public static final Color BUTTON_SHINE = new Color(255, 255, 255, 12);
    public static final Color BUTTON_TEXT = new Color(248, 244, 255);
    public static final Color HEADER_ACTION_GLOW = new Color(204, 170, 255, 24);
    public static final Color HEADER_ACTION_DANGER = new Color(214, 170, 255, 22);

    public static final int MENU_BTN_W = UI.scale(16);
    public static final int MENU_BTN_H = MENU_BTN_W;
    public static final int MENU_BTN_GAP = UI.scale(4);
    public static final int TITLE_PAD_X = UI.scale(12);
    public static final int MENU_PAD_RIGHT = UI.scale(10);
    public static final int ORNAMENT_MARGIN = UI.scale(86);

    private static final int HEADER_ACTION_MENU = 0;
    private static final int HEADER_ACTION_LOCK = 1;
    private static final int HEADER_ACTION_CLOSE = 2;

    private static final Map<String, Tex> labelCache = new ConcurrentHashMap<>();
    private static final Map<String, Tex> headerActionCache = new ConcurrentHashMap<>();
    private static final Map<String, Tex> panelChromeCache = new ConcurrentHashMap<>();
    private static final Map<String, Tex> capsuleCache = new ConcurrentHashMap<>();
    private static final Map<String, Text.Line> compactTitleCache = new ConcurrentHashMap<>();
    private static volatile int chromeStyle = Math.floorMod(Utils.getprefi("moon-chrome-style", CHROME_STYLE_COMPACT), 2);
    private static final Font TITLE_FONT = chooseFont(new String[] {"Palatino Linotype", "Book Antiqua", "Georgia", "Serif"}, Font.BOLD, 16f);
    private static final Font COMPACT_TITLE_FONT = chooseFont(new String[] {"Palatino Linotype", "Book Antiqua", "Georgia", "Serif"}, Font.BOLD, 13f);
    private static final Font LABEL_FONT = chooseFont(new String[] {"JetBrains Mono", "IBM Plex Mono", "Consolas", "Courier New", "Monospaced"}, Font.PLAIN, 12f);
    private static final Font BUTTON_FONT = chooseFont(new String[] {"JetBrains Mono", "IBM Plex Mono", "Consolas", "Courier New", "Monospaced"}, Font.BOLD, 12f);
    private static volatile BufferedImage moonCursorImg;
    private static volatile Tex moonCursorTex;
    private static volatile Coord moonCursorHotspot = Coord.z;
    private static volatile int moonCursorScale = Integer.MIN_VALUE;

    private static final Text.Foundry panelTitleFoundry = new Text.Foundry(TITLE_FONT, TEXT).aa(true);
    private static final Text.Foundry compactTitleFoundry = new Text.Foundry(COMPACT_TITLE_FONT, ACCENT).aa(true);
    private static final Text.Foundry menuFoundry = new Text.Foundry(LABEL_FONT, TEXT).aa(true);

    /** Character list row title (replaces vanilla fraktur). */
    public static final Text.Furnace CHARLIST_NAME_FURN = new PUtils.BlurFurn(
        new PUtils.TexFurn(new Text.Foundry(TITLE_FONT, ACCENT).aa(true), Window.ctex),
        UI.scale(1), UI.scale(1), new Color(0, 0, 0, 200));
    /** Character list subtitle (server / world line). */
    public static final Text.Furnace CHARLIST_META_FURN = new PUtils.TexFurn(
        new Text.Foundry(LABEL_FONT, TEXT_MUTED).aa(true), Window.ctex);

    private MoonUiTheme() {
    }

    private static Font chooseFont(String[] preferred, int style, float size) {
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT);
        for(String name : preferred) {
            if(Arrays.asList(fonts).contains(name))
                return new Font(name, style, UI.scale((int)Math.floor(size)));
        }
        return new Font("Serif", style, UI.scale((int)Math.floor(size)));
    }

    public static Text.Line title(String text) {
        return panelTitleFoundry.render(text, ACCENT);
    }

    public static Text.Line compactTitle(String text) {
        return compactTitleCache.computeIfAbsent(text, k -> compactTitleFoundry.render(k, ACCENT));
    }

    public static Text.Line menuText(String text, Color color) {
        return menuFoundry.render(text, color);
    }

    public static int chromeStyle() {
        return chromeStyle;
    }

    public static boolean compactChrome() {
        return chromeStyle == CHROME_STYLE_COMPACT;
    }

    public static void setChromeStyle(int style) {
        chromeStyle = Math.floorMod(style, 2);
        Utils.setprefi("moon-chrome-style", chromeStyle);
    }

    public static boolean useMoonCursor(Resource res) {
        return (res != null) && "gfx/hud/curs/arw".equals(res.name);
    }

    public static BufferedImage moonCursorImage() {
        ensureMoonCursor();
        return moonCursorImg;
    }

    public static Tex moonCursorTex() {
        ensureMoonCursor();
        return moonCursorTex;
    }

    public static Coord moonCursorHotspot() {
        ensureMoonCursor();
        return moonCursorHotspot;
    }

    private static synchronized void ensureMoonCursor() {
        int scale = UI.scale(1);
        if((moonCursorImg != null) && (moonCursorTex != null) && (moonCursorScale == scale))
            return;
        if(moonCursorTex != null)
            moonCursorTex.dispose();

        Coord sz = Coord.of(UI.scale(26), UI.scale(34));
        BufferedImage img = TexI.mkbuf(sz);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Path2D.Float arrow = new Path2D.Float();
        arrow.moveTo(UI.scale(3), UI.scale(2));
        arrow.lineTo(UI.scale(7), UI.scale(23));
        arrow.lineTo(UI.scale(11), UI.scale(18));
        arrow.lineTo(UI.scale(15), UI.scale(30));
        arrow.lineTo(UI.scale(19), UI.scale(28));
        arrow.lineTo(UI.scale(14), UI.scale(16));
        arrow.lineTo(UI.scale(23), UI.scale(15));
        arrow.closePath();

        Path2D.Float inner = new Path2D.Float();
        inner.moveTo(UI.scale(5), UI.scale(5));
        inner.lineTo(UI.scale(8), UI.scale(20));
        inner.lineTo(UI.scale(10), UI.scale(17));
        inner.lineTo(UI.scale(13), UI.scale(25));
        inner.lineTo(UI.scale(16), UI.scale(24));
        inner.lineTo(UI.scale(12), UI.scale(14));
        inner.lineTo(UI.scale(19), UI.scale(14));
        inner.closePath();

        Graphics2D shadow = (Graphics2D)g.create();
        shadow.translate(UI.scale(2), UI.scale(2));
        shadow.setColor(new Color(4, 3, 12, 132));
        shadow.fill(arrow);
        shadow.dispose();

        g.setPaint(new GradientPaint(0, 0, new Color(250, 244, 255, 248), 0, sz.y, new Color(136, 118, 210, 244)));
        g.fill(arrow);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 150), 0, sz.y, new Color(179, 161, 242, 60)));
        g.fill(inner);
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 1.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(28, 20, 58, 228));
        g.draw(arrow);
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 0.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 108));
        g.draw(inner);

        float orb = UI.scale(4);
        float orbX = UI.scale(13);
        float orbY = UI.scale(20);
        g.setColor(new Color(255, 234, 176, 224));
        g.fill(new Ellipse2D.Float(orbX, orbY, orb, orb));
        g.setColor(new Color(255, 255, 255, 164));
        g.fill(new Ellipse2D.Float(orbX + UI.scale(1), orbY + UI.scale(1), Math.max(1f, orb - UI.scale(2)), Math.max(1f, orb - UI.scale(2))));
        g.dispose();

        moonCursorImg = img;
        moonCursorTex = new TexI(img, false);
        moonCursorHotspot = Coord.of(UI.scale(3), UI.scale(2));
        moonCursorScale = scale;
    }

    public static void drawVerticalGradient(GOut g, Coord ul, Coord sz, Color top, Color bottom) {
        if(sz.x <= 0 || sz.y <= 0)
            return;
        int h = Math.max(1, sz.y - 1);
        for(int y = 0; y < sz.y; y++) {
            double t = (double)y / h;
            int r = blend(top.getRed(), bottom.getRed(), t);
            int gr = blend(top.getGreen(), bottom.getGreen(), t);
            int b = blend(top.getBlue(), bottom.getBlue(), t);
            int a = blend(top.getAlpha(), bottom.getAlpha(), t);
            g.chcolor(r, gr, b, a);
            g.line(ul.add(0, y), ul.add(sz.x, y), 1);
        }
        g.chcolor();
    }

    private static int blend(int a, int b, double t) {
        return (int)Math.round(a + ((b - a) * t));
    }

    private static int headerActionCount(boolean withMenu, boolean withLock, boolean withClose) {
        int count = 0;
        if(withMenu)
            count++;
        if(withLock)
            count++;
        if(withClose)
            count++;
        return count;
    }

    public static int headerActionsWidth(boolean withMenu, boolean withClose) {
        return headerActionsWidth(withMenu, false, withClose);
    }

    public static int headerActionsWidth(boolean withMenu, boolean withLock, boolean withClose) {
        int count = headerActionCount(withMenu, withLock, withClose);
        if(count <= 0)
            return 0;
        return (count * MENU_BTN_W) + ((count - 1) * MENU_BTN_GAP);
    }

    private static Coord headerActionPos(Coord sz, int headerH, int slotFromRight) {
        int x = sz.x - MENU_PAD_RIGHT - MENU_BTN_W - (slotFromRight * (MENU_BTN_W + MENU_BTN_GAP));
        int y = (headerH > 0) ? Math.max(UI.scale(1), (headerH - MENU_BTN_H) / 2) : UI.scale(3);
        return Coord.of(x, y);
    }

    public static int titleRightBound(Coord sz, int headerH, boolean withMenu) {
        return titleRightBound(sz, headerH, withMenu, false);
    }

    public static Coord menuButtonPos(Coord sz, int headerH) {
        return menuButtonPos(sz, headerH, false);
    }

    public static Coord menuButtonPos(Coord sz, int headerH, boolean withClose) {
        return menuButtonPos(sz, headerH, false, withClose);
    }

    public static Coord menuButtonPos(Coord sz, int headerH, boolean withLock, boolean withClose) {
        return headerActionPos(sz, headerH, (withClose ? 1 : 0) + (withLock ? 1 : 0));
    }

    public static Coord lockButtonPos(Coord sz, int headerH) {
        return lockButtonPos(sz, headerH, false);
    }

    public static Coord lockButtonPos(Coord sz, int headerH, boolean withClose) {
        return headerActionPos(sz, headerH, withClose ? 1 : 0);
    }

    public static Coord closeButtonPos(Coord sz, int headerH) {
        return headerActionPos(sz, headerH, 0);
    }

    public static boolean menuButtonHit(Coord c, Coord sz, int headerH) {
        return menuButtonHit(c, sz, headerH, false);
    }

    public static boolean menuButtonHit(Coord c, Coord sz, int headerH, boolean withClose) {
        return menuButtonHit(c, sz, headerH, false, withClose);
    }

    public static boolean menuButtonHit(Coord c, Coord sz, int headerH, boolean withLock, boolean withClose) {
        return c.isect(menuButtonPos(sz, headerH, withLock, withClose), Coord.of(MENU_BTN_W, MENU_BTN_H));
    }

    public static boolean lockButtonHit(Coord c, Coord sz, int headerH) {
        return lockButtonHit(c, sz, headerH, false);
    }

    public static boolean lockButtonHit(Coord c, Coord sz, int headerH, boolean withClose) {
        return c.isect(lockButtonPos(sz, headerH, withClose), Coord.of(MENU_BTN_W, MENU_BTN_H));
    }

    public static boolean closeButtonHit(Coord c, Coord sz, int headerH) {
        return c.isect(closeButtonPos(sz, headerH), Coord.of(MENU_BTN_W, MENU_BTN_H));
    }

    public static Coord menuButtonCenter(Coord sz, int headerH) {
        return menuButtonCenter(sz, headerH, false);
    }

    public static Coord menuButtonCenter(Coord sz, int headerH, boolean withClose) {
        return menuButtonCenter(sz, headerH, false, withClose);
    }

    public static Coord menuButtonCenter(Coord sz, int headerH, boolean withLock, boolean withClose) {
        return menuButtonPos(sz, headerH, withLock, withClose).add(MENU_BTN_W / 2, MENU_BTN_H / 2);
    }

    public static Coord lockButtonCenter(Coord sz, int headerH) {
        return lockButtonCenter(sz, headerH, false);
    }

    public static Coord lockButtonCenter(Coord sz, int headerH, boolean withClose) {
        return lockButtonPos(sz, headerH, withClose).add(MENU_BTN_W / 2, MENU_BTN_H / 2);
    }

    public static Coord closeButtonCenter(Coord sz, int headerH) {
        return closeButtonPos(sz, headerH).add(MENU_BTN_W / 2, MENU_BTN_H / 2);
    }

    private static double chromePulse() {
        return 0.5 + (0.5 * ((Math.sin(Utils.rtime() * 2.4) + 1.0) * 0.5));
    }

    public static int titleRightBound(Coord sz, int headerH, boolean withMenu, boolean withClose) {
        return titleRightBound(sz, headerH, withMenu, false, withClose);
    }

    public static int titleRightBound(Coord sz, int headerH, boolean withMenu, boolean withLock, boolean withClose) {
        int bound = sz.x - TITLE_PAD_X;
        if(withMenu || withLock || withClose)
            bound = Math.min(bound, sz.x - MENU_PAD_RIGHT - headerActionsWidth(withMenu, withLock, withClose) - UI.scale(10));
        return bound;
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen, boolean withBorder) {
        drawPanelChrome(g, sz, headerH, title, withMenu, menuOpen, false, false, false, withBorder, false,
            BODY_TOP, BODY_BOTTOM, HEADER_TOP, HEADER_BOTTOM);
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen,
                                       boolean withClose, boolean withBorder) {
        drawPanelChrome(g, sz, headerH, title, withMenu, menuOpen, false, false, withClose, withBorder, false,
            BODY_TOP, BODY_BOTTOM, HEADER_TOP, HEADER_BOTTOM);
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen,
                                       boolean withBorder, Color bodyTop, Color bodyBottom, Color headerTop, Color headerBottom) {
        drawPanelChrome(g, sz, headerH, title, withMenu, menuOpen, false, false, false, withBorder, false,
            bodyTop, bodyBottom, headerTop, headerBottom);
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen,
                                       boolean withClose, boolean withBorder, Color bodyTop, Color bodyBottom,
                                       Color headerTop, Color headerBottom) {
        drawPanelChrome(g, sz, headerH, title, withMenu, menuOpen, false, false, withClose, withBorder, false,
            bodyTop, bodyBottom, headerTop, headerBottom);
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen,
                                       boolean withLock, boolean locked, boolean withClose, boolean withBorder) {
        drawPanelChrome(g, sz, headerH, title, withMenu, menuOpen, withLock, locked, withClose, withBorder, false,
            BODY_TOP, BODY_BOTTOM, HEADER_TOP, HEADER_BOTTOM);
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen,
                                       boolean withLock, boolean locked, boolean withClose, boolean withBorder,
                                       boolean rounded,
                                       Color bodyTop, Color bodyBottom, Color headerTop, Color headerBottom) {
        if((sz.x <= 0) || (sz.y <= 0))
            return;
        g.image(panelChromeTex(sz, headerH, withBorder, rounded, bodyTop, bodyBottom, headerTop, headerBottom), Coord.z);
        int pulse = (int)Math.round(10 * chromePulse());
        g.chcolor(226, 202, 255, Math.max(10, pulse));
        g.line(Coord.of(UI.scale(10), UI.scale(3)), Coord.of(sz.x - UI.scale(10), UI.scale(3)), 1);
        g.chcolor();

        if(compactChrome()) {
            drawCompactTitleChip(g, sz, headerH, title, withMenu, withLock, withClose);
        } else {
            drawHeaderOrnament(g, sz, headerH, withMenu, withLock, withClose);
            if(title != null) {
                Coord tp = Coord.of(TITLE_PAD_X, Math.max(UI.scale(1), (headerH - title.sz().y) / 2 - UI.scale(1)));
                int avail = titleRightBound(sz, headerH, withMenu, withLock, withClose) - tp.x;
                if(avail > 0) {
                    if(title.sz().x <= avail) {
                        g.image(title.tex(), tp);
                    } else {
                        g.reclip(tp, Coord.of(avail, headerH)).image(title.tex(), Coord.z);
                    }
                }
            }
            g.chcolor(MENU_SEPARATOR);
            g.line(Coord.of(UI.scale(8), headerH), Coord.of(sz.x - UI.scale(8), headerH), 1);
            g.chcolor();
        }

        drawPanelActions(g, sz, headerH, withMenu, menuOpen, withLock, locked, withClose);
    }

    public static void drawPanelChrome(GOut g, Coord sz, int headerH, Text.Line title, boolean withMenu, boolean menuOpen,
                                       boolean withLock, boolean locked, boolean withClose, boolean withBorder,
                                       Color bodyTop, Color bodyBottom, Color headerTop, Color headerBottom) {
        drawPanelChrome(g, sz, headerH, title, withMenu, menuOpen, withLock, locked, withClose, withBorder, false,
            bodyTop, bodyBottom, headerTop, headerBottom);
    }

    public static void drawPanelActions(GOut g, Coord sz, int headerH, boolean withMenu, boolean menuOpen,
                                        boolean withLock, boolean locked, boolean withClose) {
        if(withClose)
            drawCloseButton(g, sz, headerH);
        if(withLock)
            drawLockButton(g, sz, headerH, locked, withClose);
        if(withMenu)
            drawMenuButton(g, sz, headerH, menuOpen, withLock, withClose);
    }

    private static void drawHeaderOrnament(GOut g, Coord sz, int headerH, boolean withMenu, boolean withLock, boolean withClose) {
        int w = sz.x;
        int cy = Math.max(UI.scale(3), headerH / 2);
        int cx = w / 2;
        int left = Math.max(TITLE_PAD_X + UI.scale(18), cx - UI.scale(78));
        int reserve = headerActionsWidth(withMenu, withLock, withClose);
        int rightEdge = w - MENU_PAD_RIGHT - Math.max(0, reserve);
        int right = Math.min(rightEdge - UI.scale(14), cx + UI.scale(78));
        if(right <= left)
            return;

        g.chcolor(148, 92, 244, 52);
        g.line(Coord.of(left, cy), Coord.of(cx - UI.scale(18), cy), 1);
        g.line(Coord.of(cx + UI.scale(18), cy), Coord.of(right, cy), 1);
        g.chcolor(196, 152, 255, 88);
        g.frect(Coord.of(cx - UI.scale(1), cy - UI.scale(1)), Coord.of(UI.scale(3), UI.scale(3)));
        g.chcolor();
    }

    private static void drawCompactTitleChip(GOut g, Coord sz, int headerH, Text.Line title,
                                             boolean withMenu, boolean withLock, boolean withClose) {
        if(title == null)
            return;
        Text.Line chipTitle = compactTitle(title.text);
        Coord chipUl = Coord.of(UI.scale(8), Math.max(UI.scale(1), (headerH - Math.max(UI.scale(12), chipTitle.sz().y + UI.scale(4))) / 2));
        int maxW = titleRightBound(sz, headerH, withMenu, withLock, withClose) - chipUl.x;
        if(maxW <= UI.scale(24))
            return;
        int chipH = Math.max(UI.scale(12), Math.min(headerH - UI.scale(4), chipTitle.sz().y + UI.scale(4)));
        Coord chipSz = Coord.of(Math.min(maxW, chipTitle.sz().x + UI.scale(16)), chipH);
        drawCapsule(g, chipUl, chipSz, new Color(24, 20, 42, 214), new Color(174, 138, 235, 116));
        Coord tp = chipUl.add(UI.scale(8), Math.max(0, (chipSz.y - chipTitle.sz().y) / 2) - UI.scale(1));
        int avail = chipSz.x - UI.scale(10);
        if(chipTitle.sz().x <= avail) {
            g.image(chipTitle.tex(), tp);
        } else {
            g.reclip(tp, Coord.of(avail, chipSz.y)).image(chipTitle.tex(), Coord.z);
        }
    }

    private static void drawCapsule(GOut g, Coord ul, Coord sz, Color fill, Color stroke) {
        g.image(capsuleTex(sz, fill, stroke), ul);
    }

    private static Tex capsuleTex(Coord sz, Color fill, Color stroke) {
        String key = sz.x + "x" + sz.y + "|" + fill.getRGB() + "|" + stroke.getRGB();
        Tex tex = capsuleCache.get(key);
        if(tex != null)
            return tex;
        Tex created = new TexI(renderCapsuleImage(sz, fill, stroke), false);
        Tex prev = capsuleCache.putIfAbsent(key, created);
        return (prev != null) ? prev : created;
    }

    private static BufferedImage renderCapsuleImage(Coord sz, Color fill, Color stroke) {
        BufferedImage img = TexI.mkbuf(sz);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float ox = UI.scale(1);
        float oy = UI.scale(1);
        float ow = Math.max(1, sz.x - UI.scale(2));
        float oh = Math.max(1, sz.y - UI.scale(2));
        float radius = Math.max(UI.scale(7), Math.min(UI.scale(10), oh - UI.scale(4)));
        RoundRectangle2D.Float rr = new RoundRectangle2D.Float(ox, oy, ow, oh, radius, radius);
        RoundRectangle2D.Float inner = new RoundRectangle2D.Float(ox + UI.scale(1), oy + UI.scale(1),
            Math.max(1, ow - UI.scale(2)), Math.max(1, oh - UI.scale(2)),
            Math.max(UI.scale(6), radius - UI.scale(3)), Math.max(UI.scale(6), radius - UI.scale(3)));

        g.setPaint(new GradientPaint(0, 0, tint(fill, 1.04f, 2), 0, sz.y, fill));
        g.fill(rr);
        g.setPaint(new GradientPaint(0, UI.scale(1), new Color(236, 220, 255, 22), 0, Math.max(UI.scale(8), sz.y / 2),
            new Color(226, 194, 255, 0)));
        g.fill(inner);
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 0.95f)));
        g.setColor(stroke);
        g.draw(rr);
        g.setColor(new Color(236, 226, 255, 28));
        g.draw(inner);
        g.dispose();
        return img;
    }

    /**
     * Compact chrome for login / char-select widgets (sessions strip, server status).
     * When {@code headerH} &lt;= 0 or {@code title} is null, only body + border are drawn.
     */
    public static void drawLoginPanelChrome(GOut g, Coord sz, int headerH, Text.Line title) {
        if((sz.x <= 0) || (sz.y <= 0))
            return;
        g.image(panelChromeTex(sz, headerH, true, true, BODY_TOP, BODY_BOTTOM, HEADER_TOP, HEADER_BOTTOM), Coord.z);
        if(headerH > 0 && title != null) {
            if(compactChrome()) {
                drawCompactTitleChip(g, sz, headerH, title, false, false, false);
            } else {
                drawHeaderOrnament(g, sz, headerH, false, false, false);
                Coord tp = Coord.of(TITLE_PAD_X, Math.max(UI.scale(1), (headerH - title.sz().y) / 2 - UI.scale(1)));
                g.image(title.tex(), tp);
                g.chcolor(MENU_SEPARATOR);
                g.line(Coord.of(UI.scale(8), headerH), Coord.of(sz.x - UI.scale(8), headerH), 1);
                g.chcolor();
            }
        }
    }

    private static Tex panelChromeTex(Coord sz, int headerH, boolean withBorder, boolean rounded, Color bodyTop, Color bodyBottom,
                                      Color headerTop, Color headerBottom) {
        Coord tsz = safesz(sz);
        String key = chromeStyle() + "|" + rounded + "|" + tsz.x + "x" + tsz.y + "|" + headerH + "|" + withBorder + "|"
            + bodyTop.getRGB() + "|" + bodyBottom.getRGB() + "|" + headerTop.getRGB() + "|" + headerBottom.getRGB();
        Tex tex = panelChromeCache.get(key);
        if(tex != null)
            return tex;
        Tex created = new TexI(renderPanelChrome(tsz, headerH, withBorder, rounded, bodyTop, bodyBottom, headerTop, headerBottom), false);
        Tex prev = panelChromeCache.putIfAbsent(key, created);
        return (prev != null) ? prev : created;
    }

    private static Coord safesz(Coord sz) {
        return Coord.of(Math.max(1, sz.x), Math.max(1, sz.y));
    }

    private static BufferedImage renderPanelChrome(Coord sz, int headerH, boolean withBorder, boolean rounded, Color bodyTop, Color bodyBottom,
                                                   Color headerTop, Color headerBottom) {
        if(!rounded)
            return renderCompactPanelChrome(sz, headerH, withBorder, bodyTop, bodyBottom, false);
        if(compactChrome())
            return renderCompactPanelChrome(sz, headerH, withBorder, bodyTop, bodyBottom, true);
        return renderOrbitalPanelChrome(sz, headerH, withBorder, bodyTop, bodyBottom, headerTop, headerBottom);
    }

    private static BufferedImage renderCompactPanelChrome(Coord sz, int headerH, boolean withBorder, Color bodyTop, Color bodyBottom, boolean rounded) {
        BufferedImage img = TexI.mkbuf(sz);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float ox = UI.scale(1);
        float oy = UI.scale(1);
        float ow = Math.max(1, sz.x - UI.scale(3));
        float oh = Math.max(1, sz.y - UI.scale(3));
        float radius = rounded ? UI.scale(12) : UI.scale(5);

        RoundRectangle2D.Float panel = new RoundRectangle2D.Float(ox, oy, ow, oh, radius, radius);
        g.setColor(new Color(6, 6, 14, 82));
        g.fill(new RoundRectangle2D.Float(ox + UI.scale(1), oy + UI.scale(1), ow, oh, radius, radius));
        g.setPaint(new GradientPaint(0, 0, bodyTop, 0, sz.y, bodyBottom));
        g.fill(panel);

        g.setColor(new Color(255, 255, 255, 11));
        g.drawLine(UI.scale(8), UI.scale(5), sz.x - UI.scale(8), UI.scale(5));
        g.setColor(new Color(180, 148, 240, 14));
        g.drawLine(UI.scale(8), Math.max(UI.scale(11), headerH), sz.x - UI.scale(8), Math.max(UI.scale(11), headerH));

        if(withBorder) {
            g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 1.05f)));
            g.setColor(BORDER);
            g.draw(panel);
            g.setColor(new Color(BORDER_SOFT.getRed(), BORDER_SOFT.getGreen(), BORDER_SOFT.getBlue(), 40));
            g.draw(new RoundRectangle2D.Float(ox + UI.scale(2), oy + UI.scale(2), Math.max(1, ow - UI.scale(4)),
                Math.max(1, oh - UI.scale(4)), Math.max(UI.scale(2), radius - UI.scale(3)), Math.max(UI.scale(2), radius - UI.scale(3))));
        }

        g.setColor(new Color(180, 148, 240, 14));
        g.drawLine(UI.scale(10), sz.y - UI.scale(8), UI.scale(22), sz.y - UI.scale(8));
        g.drawLine(sz.x - UI.scale(22), sz.y - UI.scale(8), sz.x - UI.scale(10), sz.y - UI.scale(8));
        g.dispose();
        return img;
    }

    private static BufferedImage renderOrbitalPanelChrome(Coord sz, int headerH, boolean withBorder, Color bodyTop, Color bodyBottom,
                                                          Color headerTop, Color headerBottom) {
        BufferedImage img = TexI.mkbuf(sz);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float ox = UI.scale(1);
        float oy = UI.scale(1);
        float ow = Math.max(1, sz.x - UI.scale(3));
        float oh = Math.max(1, sz.y - UI.scale(3));
        float radius = UI.scale(30);

        RoundRectangle2D.Float shadow = new RoundRectangle2D.Float(ox + UI.scale(2), oy + UI.scale(3), ow, oh, radius, radius);
        g.setColor(new Color(2, 2, 8, 108));
        g.fill(shadow);

        RoundRectangle2D.Float panel = new RoundRectangle2D.Float(ox, oy, ow, oh, radius, radius);
        g.setPaint(new GradientPaint(0, 0, bodyTop, 0, sz.y, bodyBottom));
        g.fill(panel);

        Graphics2D hg = (Graphics2D)g.create();
        Area headerArea = new Area(panel);
        headerArea.intersect(new Area(new Rectangle2D.Float(ox, oy, ow, Math.max(headerH + UI.scale(8), UI.scale(14)))));
        hg.setPaint(new GradientPaint(0, 0, headerTop, 0, Math.max(headerH, 1), headerBottom));
        hg.fill(headerArea);
        hg.dispose();

        g.setPaint(new GradientPaint(0, oy, new Color(187, 122, 255, 32), 0, oy + Math.max(1, headerH), new Color(187, 122, 255, 0)));
        g.fill(new RoundRectangle2D.Float(ox + UI.scale(3), oy + UI.scale(3), Math.max(1, ow - UI.scale(6)),
            Math.max(UI.scale(10), headerH / 2f), radius - UI.scale(10), radius - UI.scale(10)));

        int dividerY = Math.max(UI.scale(10), headerH);
        g.setColor(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue(), 92));
        g.drawLine(UI.scale(10), dividerY, sz.x - UI.scale(10), dividerY);

        int cy = sz.y / 2;
        g.setColor(new Color(112, 66, 214, 10));
        g.drawOval(UI.scale(18), cy - UI.scale(76), Math.max(UI.scale(48), sz.x - UI.scale(36)), UI.scale(152));

        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(196, 144, 255, 14));
        g.draw(new RoundRectangle2D.Float(ox + UI.scale(6), oy + UI.scale(6), Math.max(1, ow - UI.scale(12)),
            Math.max(1, oh - UI.scale(12)), radius - UI.scale(12), radius - UI.scale(12)));

        if(withBorder) {
            g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 1.05f)));
            g.setColor(BORDER);
            g.draw(panel);
            g.setColor(new Color(BORDER_SOFT.getRed(), BORDER_SOFT.getGreen(), BORDER_SOFT.getBlue(), 62));
            g.draw(new RoundRectangle2D.Float(ox + UI.scale(2), oy + UI.scale(2), Math.max(1, ow - UI.scale(4)),
                Math.max(1, oh - UI.scale(4)), radius - UI.scale(4), radius - UI.scale(4)));
        }

        g.setColor(new Color(142, 92, 242, 36));
        g.drawArc(UI.scale(4), sz.y - UI.scale(36), UI.scale(30), UI.scale(30), 0, 90);
        g.drawArc(sz.x - UI.scale(34), sz.y - UI.scale(36), UI.scale(30), UI.scale(30), 90, 90);
        g.dispose();
        return img;
    }

    public static void drawMenuButton(GOut g, Coord sz, int headerH, boolean open) {
        drawMenuButton(g, sz, headerH, open, false, false);
    }

    public static void drawMenuButton(GOut g, Coord sz, int headerH, boolean open, boolean withClose) {
        drawMenuButton(g, sz, headerH, open, false, withClose);
    }

    public static void drawMenuButton(GOut g, Coord sz, int headerH, boolean open, boolean withLock, boolean withClose) {
        Coord pos = menuButtonPos(sz, headerH, withLock, withClose);
        drawHeaderActionButton(g, pos, HEADER_ACTION_MENU, open);
    }

    public static void drawLockButton(GOut g, Coord sz, int headerH, boolean locked) {
        drawLockButton(g, sz, headerH, locked, false);
    }

    public static void drawLockButton(GOut g, Coord sz, int headerH, boolean locked, boolean withClose) {
        drawHeaderActionButton(g, lockButtonPos(sz, headerH, withClose), HEADER_ACTION_LOCK, locked);
    }

    public static void drawCloseButton(GOut g, Coord sz, int headerH) {
        drawHeaderActionButton(g, closeButtonPos(sz, headerH), HEADER_ACTION_CLOSE, false);
    }

    private static void drawHeaderActionButton(GOut g, Coord pos, int kind, boolean active) {
        Coord center = pos.add(MENU_BTN_W / 2, MENU_BTN_H / 2);
        Color tint;
        if(kind == HEADER_ACTION_CLOSE) {
            tint = HEADER_ACTION_DANGER;
        } else if(kind == HEADER_ACTION_LOCK) {
            tint = active ? LOCKED : UNLOCKED;
        } else {
            tint = HEADER_ACTION_GLOW;
        }
        int baseGlow = active ? 14 : 6;
        int pulse = active ? (4 + (int)Math.round(6 * ((Math.sin(Utils.rtime() * 5.0) + 1.0) * 0.5))) : 0;
        g.chcolor(tint.getRed(), tint.getGreen(), tint.getBlue(), Math.min(255, tint.getAlpha() + baseGlow + pulse));
        g.fellipse(center, Coord.of((MENU_BTN_W / 2) + UI.scale(1), (MENU_BTN_H / 2) + UI.scale(1)));
        g.chcolor();
        g.image(headerActionTex(kind, active), pos);
    }

    private static Tex headerActionTex(int kind, boolean active) {
        int scale = UI.scale(1);
        String key = scale + "|" + kind + "|" + active;
        Tex tex = headerActionCache.get(key);
        if(tex != null)
            return tex;
        Tex created = new TexI(renderHeaderActionImage(kind, active), false);
        Tex prev = headerActionCache.putIfAbsent(key, created);
        return (prev != null) ? prev : created;
    }

    private static BufferedImage renderHeaderActionImage(int kind, boolean active) {
        int w = MENU_BTN_W;
        int h = MENU_BTN_H;
        BufferedImage img = TexI.mkbuf(Coord.of(w, h));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.max(1f, (Math.min(w, h) / 2f) - UI.scale(1));
        Ellipse2D.Float outer = new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f);
        Ellipse2D.Float inner = new Ellipse2D.Float(cx - (r - UI.scale(1)), cy - (r - UI.scale(1)),
            Math.max(1f, (r - UI.scale(1)) * 2f), Math.max(1f, (r - UI.scale(1)) * 2f));

        g.setColor(new Color(8, 8, 18, active ? 92 : 72));
        g.fill(new Ellipse2D.Float(cx - r, cy - r + UI.scale(1), r * 2f, r * 2f));

        boolean danger = kind == HEADER_ACTION_CLOSE;
        Color baseTop;
        Color baseMid;
        Color baseBottom;
        if(kind == HEADER_ACTION_LOCK) {
            baseTop = active ? new Color(120, 64, 245, 244) : new Color(62, 36, 120, 236);
            baseMid = active ? new Color(62, 28, 148, 244) : new Color(28, 18, 74, 236);
            baseBottom = active ? new Color(12, 8, 38, 240) : new Color(7, 5, 22, 232);
        } else if(danger) {
            baseTop = active ? new Color(112, 54, 188, 244) : new Color(84, 40, 146, 236);
            baseMid = active ? new Color(52, 22, 120, 244) : new Color(38, 16, 92, 236);
            baseBottom = active ? new Color(10, 7, 28, 240) : new Color(9, 7, 22, 232);
        } else {
            baseTop = active ? new Color(65, 30, 158, 244) : new Color(32, 18, 92, 234);
            baseMid = active ? new Color(21, 12, 62, 242) : new Color(14, 9, 38, 232);
            baseBottom = active ? new Color(8, 6, 24, 238) : new Color(7, 5, 18, 230);
        }

        g.setPaint(new RadialGradientPaint(cx, cy - UI.scale(2), r * 1.25f,
            new float[] {0f, 0.62f, 1f},
            new Color[] {baseTop, baseMid, baseBottom}));
        g.fill(outer);

        g.setPaint(new GradientPaint(0, 0, new Color(240, 228, 255, active ? 40 : 24),
            0, h, new Color(210, 172, 255, 0)));
        g.fill(new Ellipse2D.Float(cx - (r - UI.scale(2)), cy - r + UI.scale(2), (r - UI.scale(2)) * 2f,
            Math.max(1f, r)));

        g.setColor(new Color(198, 174, 240, active ? 168 : 132));
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 0.95f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outer);
        g.setColor(new Color(244, 236, 255, active ? 78 : 54));
        g.draw(inner);

        g.setColor(new Color(226, 198, 255, danger ? 220 : (kind == HEADER_ACTION_LOCK ? 214 : 202)));
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 1.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if(kind == HEADER_ACTION_CLOSE) {
            int inset = UI.scale(4);
            g.drawLine(inset, inset, w - inset, h - inset);
            g.drawLine(w - inset, inset, inset, h - inset);
        } else if(kind == HEADER_ACTION_LOCK) {
            float bodyW = Math.max(UI.scale(5), w - UI.scale(10));
            float bodyH = Math.max(UI.scale(4), h - UI.scale(12));
            float bx = cx - (bodyW / 2f);
            float by = cy;
            g.draw(new RoundRectangle2D.Float(bx, by, bodyW, bodyH, UI.scale(1), UI.scale(1)));
            if(active) {
                g.draw(new Path2D.Float() {{
                    moveTo(bx + UI.scale(1), by);
                    curveTo(bx + UI.scale(1), by - UI.scale(4), bx + bodyW - UI.scale(1), by - UI.scale(4), bx + bodyW - UI.scale(1), by);
                }});
            } else {
                g.draw(new Path2D.Float() {{
                    moveTo(bx + UI.scale(1), by);
                    curveTo(bx + UI.scale(1), by - UI.scale(4), bx + bodyW - UI.scale(1), by - UI.scale(4), bx + bodyW - UI.scale(1), by - UI.scale(1));
                    lineTo(bx + bodyW + UI.scale(1), by + UI.scale(2));
                }});
            }
            g.drawLine((int)Math.round(cx), (int)Math.round(by + UI.scale(1)), (int)Math.round(cx), (int)Math.round(by + bodyH - UI.scale(1)));
        } else {
            int x1 = UI.scale(4);
            int x2 = w - UI.scale(4);
            int y1 = UI.scale(5);
            int step = UI.scale(3);
            g.drawLine(x1, y1, x2, y1);
            g.drawLine(x1, y1 + step, x2, y1 + step);
            g.drawLine(x1, y1 + (step * 2), x2, y1 + (step * 2));
        }
        g.dispose();
        return img;
    }

    public static void paintFantasyButton(BufferedImage img, Coord sz, boolean pressed, boolean disabled, BufferedImage cont) {
        paintFantasyButton(img, sz, pressed, false, disabled, cont);
    }

    public static void paintFantasyButton(BufferedImage img, Coord sz, boolean pressed, boolean hovered, boolean disabled, BufferedImage cont) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color top = pressed ? BUTTON_PRESS_TOP : (hovered ? tint(BUTTON_TOP, 1.08f, 6) : BUTTON_TOP);
        Color bottom = pressed ? BUTTON_PRESS_BOTTOM : (hovered ? tint(BUTTON_BOTTOM, 1.06f, 4) : BUTTON_BOTTOM);
        float radius = Math.max(UI.scale(8), Math.min(UI.scale(10), sz.y - UI.scale(14)));
        RoundRectangle2D.Float rr = new RoundRectangle2D.Float(1, 1, sz.x - 3, sz.y - 3, radius, radius);
        RoundRectangle2D.Float inner = new RoundRectangle2D.Float(UI.scale(3), UI.scale(3), sz.x - UI.scale(7), sz.y - UI.scale(7),
            Math.max(UI.scale(6), radius - UI.scale(3)), Math.max(UI.scale(6), radius - UI.scale(3)));

        g.setColor(new Color(8, 8, 16, pressed ? 110 : (hovered ? 100 : 92)));
        g.fill(new RoundRectangle2D.Float(UI.scale(1), UI.scale(2), sz.x - UI.scale(2), sz.y - UI.scale(3), radius, radius));
        g.setPaint(new GradientPaint(0, 0, top, 0, sz.y, bottom));
        g.fill(rr);
        g.setPaint(new GradientPaint(0, UI.scale(2), new Color(242, 244, 248, pressed ? 34 : (hovered ? 28 : 18)),
            0, Math.max(UI.scale(8), sz.y / 2), new Color(214, 220, 232, 0)));
        g.fill(inner);
        g.setStroke(new BasicStroke(Math.max(1f, UI.scale(1) * 1.05f)));
        g.setColor(hovered ? tint(BUTTON_BORDER, 1.06f, 10) : BUTTON_BORDER);
        g.draw(rr);
        g.setColor(new Color(236, 240, 246, pressed ? 48 : (hovered ? 38 : 28)));
        g.draw(inner);

        if(cont != null) {
            int tx = (sz.x - cont.getWidth()) / 2;
            int ty = (sz.y - cont.getHeight()) / 2;
            if(pressed) {
                tx += UI.scale(1);
                ty += UI.scale(1);
            }
            g.drawImage(cont, tx, ty, null);
        }

        if(disabled) {
            g.setColor(new Color(255, 255, 255, 44));
            g.fill(rr);
            g.setColor(new Color(16, 14, 28, 132));
            g.fill(rr);
        }
        g.dispose();
    }

    private static Color tint(Color base, float mul, int add) {
        int r = Utils.clip(Math.round(base.getRed() * mul) + add, 0, 255);
        int g = Utils.clip(Math.round(base.getGreen() * mul) + add, 0, 255);
        int b = Utils.clip(Math.round(base.getBlue() * mul) + add, 0, 255);
        return new Color(r, g, b, base.getAlpha());
    }

    public static BufferedImage renderThemedText(String text) {
        return renderThemedText(text, BUTTON_TEXT);
    }

    public static BufferedImage renderThemedText(String text, Color color) {
        Font font = BUTTON_FONT.deriveFont((float)UI.scale(12));
        BufferedImage probe = TexI.mkbuf(Coord.of(UI.scale(8), UI.scale(8)));
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        int w = Math.max(1, fm.stringWidth(text));
        int h = Math.max(1, fm.getHeight());
        pg.dispose();

        BufferedImage img = TexI.mkbuf(Coord.of(w + UI.scale(6), h + UI.scale(4)));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        int base = UI.scale(2) + fm.getAscent();
        g.setColor(new Color(18, 14, 38, 92));
        g.drawString(text, UI.scale(3), base + UI.scale(1));
        g.setColor(color);
        g.drawString(text, UI.scale(2), base);
        g.dispose();
        return img;
    }

    public static Tex labelTex(String assetKey, String fallbackText, int maxW, int maxH) {
        String lang = MoonL10n.lang();
        String ck = lang + "|" + assetKey + "|" + maxW + "|" + maxH + "|" + fallbackText;
        Tex tex = labelCache.get(ck);
        if(tex != null)
            return tex;

        BufferedImage img = null;
        if(img == null)
            img = loadLabel(assetKey, lang);
        if(img == null)
            img = loadLabel(assetKey, MoonL10n.LANG_EN);
        if(img == null)
            img = renderLabelFallback(fallbackText, maxW, maxH);
        img = trimTransparent(img);
        img = fit(img, maxW, maxH);
        tex = new TexI(img, false);
        labelCache.put(ck, tex);
        return tex;
    }

    private static BufferedImage fit(BufferedImage img, int maxW, int maxH) {
        double scale = 1.0;
        if((maxW > 0) && (maxH > 0)) {
            if(img.getWidth() > maxW || img.getHeight() > maxH)
                scale = Math.min((double)maxW / img.getWidth(), (double)maxH / img.getHeight());
        }
        if(Math.abs(scale - 1.0) < 0.01)
            return img;
        int nw = Math.max(1, (int)Math.round(img.getWidth() * scale));
        int nh = Math.max(1, (int)Math.round(img.getHeight() * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage centerCanvas(BufferedImage img, int maxW, int maxH) {
        int w = (maxW > 0) ? maxW : img.getWidth();
        int h = (maxH > 0) ? maxH : img.getHeight();
        if(img.getWidth() == w && img.getHeight() == h)
            return img;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int x = (w - img.getWidth()) / 2;
        int y = (h - img.getHeight()) / 2;
        g.drawImage(img, x, y, null);
        g.dispose();
        return out;
    }

    private static BufferedImage loadLabel(String assetKey, String lang) {
        String path = "res/moon/ui/labels/" + lang + "/" + assetKey + ".png";
        try(InputStream in = MoonUiTheme.class.getResourceAsStream(path)) {
            if(in == null)
                return null;
            return ImageIO.read(in);
        } catch(IOException e) {
            return null;
        }
    }

    private static BufferedImage renderLabelFallback(String text, int maxW, int maxH) {
        if(text == null)
            text = "";
        int w = Math.max(UI.scale(120), maxW);
        int h = Math.max(UI.scale(34), maxH);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int cy = h / 2;
        g.setColor(new Color(226, 221, 255, 90));
        g.drawLine(UI.scale(12), cy, UI.scale(82), cy);
        g.drawLine(w - UI.scale(82), cy, w - UI.scale(12), cy);
        g.setColor(new Color(232, 229, 255, 160));
        g.fillRect((w / 2) - 1, cy - 1, 3, 3);

        Font font = TITLE_FONT.deriveFont((float)UI.scale(17));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int tx = (w - tw) / 2;
        int ty = ((h - fm.getHeight()) / 2) + fm.getAscent();
        g.setColor(new Color(16, 14, 34, 168));
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(new Color(244, 242, 255, 238));
        g.drawString(text, tx, ty);
        g.dispose();
        return img;
    }

    private static BufferedImage trimTransparent(BufferedImage src) {
        int minX = src.getWidth();
        int minY = src.getHeight();
        int maxX = -1;
        int maxY = -1;
        for(int y = 0; y < src.getHeight(); y++) {
            for(int x = 0; x < src.getWidth(); x++) {
                if((src.getRGB(x, y) >>> 24) > 0) {
                    if(x < minX)
                        minX = x;
                    if(y < minY)
                        minY = y;
                    if(x > maxX)
                        maxX = x;
                    if(y > maxY)
                        maxY = y;
                }
            }
        }
        if(maxX < minX || maxY < minY)
            return src;
        BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), minX, minY, maxX + 1, maxY + 1, null);
        g.dispose();
        return out;
    }
}
