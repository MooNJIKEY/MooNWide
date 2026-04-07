package haven;

import haven.sloth.gui.MovableWidget;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ChatWnd extends MovableWidget {
    public final ChatUI chat;
    private static final Coord defcontent = UI.scale(new Coord(620, 220));
    private static final Coord mincontent = UI.scale(new Coord(420, 160));
    private static final int headerh = UI.scale(16);
    private static final int pad = UI.scale(6);
    private static final int grip = UI.scale(14);
    private static final int LOCK_SZ = UI.scale(8);
    private UI.Grab resizing = null;
    private Coord rsz;
    private Coord rdoff;
    private int bgAlpha;
    private MoonDropdownMenu headerMenu = null;

    @Override
    protected void savePosition() {
	super.savePosition();
	Utils.setprefc("wndc-chat", c);
	Utils.setprefc("wndsz-chat", csz());
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {}
    }

    public ChatWnd(ChatUI chat) {
        super(wndsz(Utils.getprefc("wndsz-chat", defcontent)), "chat");
        /* GameUI uses wndc-chat; MovableWidget uses wpos-chat — load explicitly in added() */
        this.loadPosition = false;
        this.chat = add(chat, contentc());
        this.bgAlpha = Utils.getprefi("moon-chat-alpha", 210);
        layoutchat();
    }

    @Override
    protected void added() {
	Coord fromWndc = Utils.getprefc("wndc-chat", null);
	Coord fromWpos = Utils.getprefc("wpos-chat", null);
	if(fromWndc != null) {
	    c = fromWndc;
	    Utils.setprefc("wpos-chat", c);
	} else if(fromWpos != null) {
	    c = fromWpos;
	    Utils.setprefc("wndc-chat", c);
	}
	lock(Utils.getprefb("wlock-chat", false));
	super.added();
    }

    private static Coord wndsz(Coord csz) {
        Coord area = (csz == null) ? defcontent : csz;
        area = Coord.of(Math.max(mincontent.x, area.x), Math.max(mincontent.y, area.y));
        return(Coord.of(area.x + (pad * 2), area.y + headerh + (pad * 2)));
    }

    private Coord contentc() {
        return(Coord.of(pad, headerh + pad));
    }

    public Coord csz() {
        return(Coord.of(Math.max(mincontent.x, sz.x - (pad * 2)),
                        Math.max(mincontent.y, sz.y - headerh - (pad * 2))));
    }

    public int alpha() {
        return(bgAlpha);
    }

    public void setAlpha(int alpha) {
        bgAlpha = Math.max(60, Math.min(255, alpha));
        Utils.setprefi("moon-chat-alpha", bgAlpha);
    }

    private boolean griphit(Coord c) {
        return((c.x >= (sz.x - grip)) && (c.y >= (sz.y - grip)));
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
        if(menuHit(mc) || lockHit(mc) || closeHit(mc)) return false;
        return(((mc.y < headerh) && (button == 1)) || super.moveHit(mc, button));
    }

    @Override
    protected boolean altMoveHit(Coord mc, int button) {
        return false;
    }

    public boolean mousedown(MouseDownEvent ev) {
        if((ev.b == 1) && closeHit(ev.c)) {
            reqclose();
            return(true);
        }
        if((ev.b == 1) && lockHit(ev.c)) {
            toggleLock();
            return(true);
        }
        if((ev.b == 1) && menuHit(ev.c)) {
            toggleHeaderMenu();
            return(true);
        }
        if((ev.b == 1) && headerMenu != null) {
            closeHeaderMenu();
        }
        if((ev.b == 1) && griphit(ev.c)) {
            resizing = ui.grabmouse(this);
            rsz = sz;
            rdoff = ev.c;
            raise();
            return(true);
        }
        return(super.mousedown(ev));
    }

    public boolean mouseup(MouseUpEvent ev) {
        if(resizing != null) {
            resizing.remove();
            resizing = null;
            Utils.setprefc("wndsz-chat", csz());
            return(true);
        }
        return(super.mouseup(ev));
    }

    public void mousemove(MouseMoveEvent ev) {
        if((resizing != null) && (rsz != null) && (rdoff != null)) {
            resize(Coord.of(Math.max(wndsz(mincontent).x, rsz.x + ev.c.x - rdoff.x),
                            Math.max(wndsz(mincontent).y, rsz.y + ev.c.y - rdoff.y)));
            return;
        }
        super.mousemove(ev);
    }

    public void resize(Coord nsz) {
        super.resize(wndsz(csz(nsz)));
        layoutchat();
        Utils.setprefc("wndsz-chat", csz());
    }

    private Coord csz(Coord wsz) {
        return(Coord.of(Math.max(mincontent.x, wsz.x - (pad * 2)),
                        Math.max(mincontent.y, wsz.y - headerh - (pad * 2))));
    }

    private void layoutchat() {
        if(chat != null) {
            chat.c = contentc();
            chat.resize(csz());
        }
    }

    public void draw(GOut g) {
        Color bg = new Color(18, 12, 30, bgAlpha);
        Color top = shade(bg, 1.08f, 18);
        Color bottom = shade(bg, 0.78f, -12);
        MoonUiTheme.drawPanelChrome(g, sz, headerh, MoonUiTheme.title(LocalizationManager.tr("chat.title")),
            true, headerMenu != null, true, locked(), true, true,
            top, bottom, MoonUiTheme.HEADER_TOP, MoonUiTheme.HEADER_BOTTOM);

        Coord gr = Coord.of(sz.x - grip, sz.y - grip);
        g.chcolor(MoonPanel.MOON_BORDER);
        g.line(gr.add(UI.scale(4), grip - UI.scale(2)), gr.add(grip - UI.scale(2), UI.scale(4)), 1);
        g.line(gr.add(UI.scale(7), grip - UI.scale(2)), gr.add(grip - UI.scale(2), UI.scale(7)), 1);
        g.chcolor();
        super.draw(g);
    }

    private boolean menuHit(Coord c) {
        return MoonUiTheme.menuButtonHit(c, sz, headerh, true, true);
    }

    private boolean lockHit(Coord c) {
        return MoonUiTheme.lockButtonHit(c, sz, headerh, true);
    }

    private boolean closeHit(Coord c) {
        return MoonUiTheme.closeButtonHit(c, sz, headerh);
    }

    private Coord menuAnchor() {
        return MoonDropdownMenu.toRoot(this, MoonUiTheme.menuButtonCenter(sz, headerh, true, true));
    }

    private void toggleHeaderMenu() {
        if(headerMenu != null) {
            closeHeaderMenu();
            return;
        }
        headerMenu = MoonDropdownMenu.popup(this, menuAnchor(), buildMenuEntries(), () -> headerMenu = null);
    }

    private void closeHeaderMenu() {
        if(headerMenu != null) {
            MoonDropdownMenu menu = headerMenu;
            headerMenu = null;
            menu.destroy();
        }
    }

    private List<MoonDropdownMenu.Entry> buildMenuEntries() {
        List<MoonDropdownMenu.Entry> entries = new ArrayList<>();
        entries.add(MoonDropdownMenu.Entry.action(
            () -> LocalizationManager.tr("ui.menu.opacity") + ": " + bgAlpha,
            () -> {
                int next = (bgAlpha <= 60) ? 210 : (bgAlpha - 50);
                if(next < 60)
                    next = 210;
                setAlpha(next);
            }));
        entries.add(MoonDropdownMenu.Entry.action(
            () -> LocalizationManager.tr("shortcut.reset_pos"),
            () -> {
                c = parent.sz.sub(sz).div(2);
                savePosition();
            }));
        return entries;
    }

    private static Color shade(Color base, float mul, int add) {
        int r = Utils.clip(Math.round(base.getRed() * mul) + add, 0, 255);
        int g = Utils.clip(Math.round(base.getGreen() * mul) + add, 0, 255);
        int b = Utils.clip(Math.round(base.getBlue() * mul) + add, 0, 255);
        return new Color(r, g, b, base.getAlpha());
    }

    public void reqclose() {
        hide();
        Utils.setprefb("chatvis", false);
    }
}
