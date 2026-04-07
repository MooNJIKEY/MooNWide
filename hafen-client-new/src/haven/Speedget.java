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

import haven.sloth.gui.MovableWidget;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Speedget extends MovableWidget {
    public static final Tex imgs[][];
    public static final String tips[];
    public static final Coord tsz;
    private static final int BORDER = UI.scale(2);
    private static final int MENU_BTN = UI.scale(12);
    private static final int MENU_GAP = UI.scale(4);
    public int cur, max;
    private boolean restorePending = false;
    private int restoreAttempts = 0;
    private double nextRestoreAt = 0;
    private MoonDropdownMenu headerMenu = null;

    static {
	String[] names = {"crawl", "walk", "run", "sprint"};
	String[] vars = {"dis", "off", "on"};
	imgs = new Tex[names.length][vars.length];
	int w = 0;
	for(int i = 0; i < names.length; i++) {
	    for(int o = 0; o < vars.length; o++)
		imgs[i][o] = Resource.loadtex("gfx/hud/meter/rmeter/" + names[i] + "-" + vars[o]);
	    w += imgs[i][0].sz().x;
	}
	tsz = new Coord(w + BORDER * 2 + MENU_BTN + MENU_GAP, Math.max(imgs[0][0].sz().y + BORDER * 2, MENU_BTN + BORDER * 2));
	tips = new String[names.length];
	for(int i = 0; i < names.length; i++) {
	    tips[i] = Resource.local().loadwait("gfx/hud/meter/rmeter/" + names[i] + "-on").flayer(Resource.tooltip).t;
	}
    }

    @RName("speedget")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    int cur = Utils.iv(args[0]);
	    int max = Utils.iv(args[1]);
	    return(new Speedget(cur, max));
	}
    }

    public Speedget(int cur, int max) {
	super(tsz, "speedget");
	this.cur = cur;
	this.max = max;
    }

    @Override
    protected void added() {
	super.added();
	if(MoonConfig.rememberSpeedMode) {
	    restorePending = true;
	    restoreAttempts = 0;
	    nextRestoreAt = 0;
	}
    }

    @Override
    protected boolean altMoveHit(Coord mc, int button) {
	return false;
    }

    @Override
    protected boolean moveHit(Coord mc, int button) {
	return (button == 1 && mc.y < UI.scale(10) && !menuHit(mc)) || super.moveHit(mc, button);
    }

    public void draw(GOut g) {
	int x = BORDER;
	int iy = (sz.y - imgs[0][0].sz().y) / 2;
	for(int i = 0; i < 4; i++) {
	    Tex t;
	    if(i == cur)
		t = imgs[i][2];
	    else if(i > max)
		t = imgs[i][0];
	    else
		t = imgs[i][1];
	    g.image(t, new Coord(x, iy));
	    x += t.sz().x;
	}

	drawMenuButton(g);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "cur") {
	    int prev = cur;
	    cur = Utils.iv(args[0]);
	    if(MoonConfig.debugSpeedWire && ui != null) {
		int wid = ui.widgetid(this);
		System.err.println("[MoonSpeedWire] IN cur wid=" + wid + " " + prev + " -> " + cur + " (max=" + max + ")");
	    }
	    if(MoonConfig.rememberSpeedMode) {
		int saved = Utils.getprefi("moon-last-speed-mode", -1);
		if(saved >= 0 && cur == Utils.clip(saved, 0, Math.max(0, max)))
		    restorePending = false;
	    }
	}
	else if(msg == "max") {
	    int prevMax = max;
	    max = Utils.iv(args[0]);
	    if(MoonConfig.debugSpeedWire && ui != null) {
		int wid = ui.widgetid(this);
		System.err.println("[MoonSpeedWire] IN max wid=" + wid + " " + prevMax + " -> " + max + " (cur=" + cur + ")");
	    }
	}
    }

    public void set(int s) {
	if(MoonConfig.rememberSpeedMode)
	    Utils.setprefi("moon-last-speed-mode", s);
	wdgmsg("set", s);
    }

    public boolean mousedown(MouseDownEvent ev) {
	if((ev.b == 1) && menuHit(ev.c)) {
	    toggleHeaderMenu();
	    return true;
	}
	if((ev.b == 1) && headerMenu != null)
	    closeHeaderMenu();
	if(super.mousedown(ev))
	    return(true);
	int x = BORDER;
	int iy = (sz.y - imgs[0][0].sz().y) / 2;
	for(int i = 0; i < 4; i++) {
	    x += imgs[i][0].sz().x;
	    if(ev.c.x < x && ev.c.y >= iy) {
		set(i);
		break;
	    }
	}
	return(true);
    }

    public boolean mousewheel(MouseWheelEvent ev) {
	if(max >= 0)
	    set(Utils.clip(cur + ev.a, 0, max));
	return(true);
    }

    public Object tooltip(Coord c, Widget prev) {
	if((cur >= 0) && (cur < tips.length))
	    return(String.format("Selected speed: " + tips[cur]));
	return(null);
    }

    public static final KeyBinding kb_speedup = KeyBinding.get("speed-up", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.C));
    public static final KeyBinding kb_speeddn = KeyBinding.get("speed-down", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S | KeyMatch.C));
    public static final KeyBinding[] kb_speeds = {
	KeyBinding.get("speed-set/0", KeyMatch.nil),
	KeyBinding.get("speed-set/1", KeyMatch.nil),
	KeyBinding.get("speed-set/2", KeyMatch.nil),
	KeyBinding.get("speed-set/3", KeyMatch.nil),
    };
    public boolean globtype(GlobKeyEvent ev) {
	int dir = 0;
	if(kb_speedup.key().match(ev))
	    dir = 1;
	else if(kb_speeddn.key().match(ev))
	    dir = -1;
	if(dir != 0) {
	    if(max >= 0) {
		set(Utils.clip(cur + dir, 0, max));
	    }
	    return(true);
	}
	for(int i = 0; i < kb_speeds.length; i++) {
	    if(kb_speeds[i].key().match(ev)) {
		set(i);
		return(true);
	    }
	}
	return(super.globtype(ev));
    }

    private boolean menuHit(Coord c) {
	return c.isect(menuBtnPos(), Coord.of(MENU_BTN, MENU_BTN));
    }

    private Coord menuAnchor() {
	Coord p = menuBtnPos().add(MENU_BTN / 2, MENU_BTN / 2);
	return MoonDropdownMenu.toRoot(this, p);
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
	    () -> LocalizationManager.tr("shortcut.reset_pos"),
	    () -> {
		if(parent != null)
		    c = parent.sz.sub(sz).div(2);
		savePosition();
	    }));
	entries.add(MoonDropdownMenu.Entry.separator());
	entries.add(MoonDropdownMenu.Entry.action(
	    () -> LocalizationManager.tr(locked() ? "ui.menu.unlock" : "ui.menu.lock"),
	    this::toggleLock));
	return entries;
    }

    private static Color shade(Color base, float mul, int add) {
	int r = Utils.clip(Math.round(base.getRed() * mul) + add, 0, 255);
	int g = Utils.clip(Math.round(base.getGreen() * mul) + add, 0, 255);
	int b = Utils.clip(Math.round(base.getBlue() * mul) + add, 0, 255);
	return new Color(r, g, b, base.getAlpha());
    }

    private Coord menuBtnPos() {
	return Coord.of(sz.x - MENU_BTN - BORDER, (sz.y - MENU_BTN) / 2);
    }

    private void drawMenuButton(GOut g) {
	Coord p = menuBtnPos();
	Coord center = p.add(MENU_BTN / 2, MENU_BTN / 2);
	if(headerMenu != null) {
	    g.chcolor(MoonUiTheme.HEADER_ACTION_GLOW);
	    g.fellipse(center, Coord.of(UI.scale(7), UI.scale(7)));
	}
	g.chcolor(MoonUiTheme.ACCENT);
	int cx = p.x + MENU_BTN / 2;
	int cy = p.y + MENU_BTN / 2;
	g.line(Coord.of(cx - UI.scale(3), cy - UI.scale(2)), Coord.of(cx + UI.scale(3), cy - UI.scale(2)), 1);
	g.line(Coord.of(cx - UI.scale(3), cy), Coord.of(cx + UI.scale(3), cy), 1);
	g.line(Coord.of(cx - UI.scale(3), cy + UI.scale(2)), Coord.of(cx + UI.scale(3), cy + UI.scale(2)), 1);
	g.chcolor();
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	if(!MoonConfig.rememberSpeedMode || !restorePending)
	    return;
	double now = Utils.rtime();
	if(now < nextRestoreAt)
	    return;
	int saved = Utils.getprefi("moon-last-speed-mode", -1);
	if(saved < 0) {
	    restorePending = false;
	    return;
	}
	int tgt = Utils.clip(saved, 0, Math.max(0, max));
	if(cur == tgt) {
	    restorePending = false;
	    return;
	}
	if(restoreAttempts >= 6) {
	    restorePending = false;
	    return;
	}
	set(tgt);
	restoreAttempts++;
	nextRestoreAt = now + 0.5;
    }
}
