/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Bjorn Johannessen <johannessen.bjorn@gmail.com>
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Charlist extends Widget {
    private static final Text.Foundry titlef = new Text.Foundry(Text.serif.deriveFont(java.awt.Font.BOLD, UI.scale(40f))).aa(true);
    private static final Text.Foundry subf = new Text.Foundry(Text.serif.deriveFont(java.awt.Font.PLAIN, UI.scale(18f))).aa(true);
    private static final Text.Foundry bodyf = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.PLAIN, UI.scale(16f))).aa(true);
    private static final Text.Foundry chipf = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.PLAIN, UI.scale(13f))).aa(true);

    public final int height;
    public final List<Char> chars = new ArrayList<Char>();
    public Avaview avalink;

    public Boxlist list;
    public MoonFantasyButton sau, sad, playbtn;
    public final Avaview preview;
    private Widget createSource;
    private MoonFantasyButton createProxy;

    private boolean dirty;
    private boolean showdisc;
    private Coord lastlayout = Coord.z;
    private int lastcharcount = -1;
    private double introa = 0.0;
    private double breathe = 0.0;

    private Coord panelc = Coord.z;
    private Coord panelsz = UI.scale(560, 700);
    private Coord previewc = UI.scale(980, 220);
    private Coord previewsz = UI.scale(360, 500);
    private int panelpad = UI.scale(16);
    private int rowh = UI.scale(118);
    private int rowmarg = UI.scale(10);
    private int topbtnh = UI.scale(34);
    private int headerY = UI.scale(74);

    private Text.Line selname;
    private Text.Line seldisc;
    private Text.Line heroTitle = titlef.render(MainFrame.TITLE(), MoonUiTheme.ACCENT);
    private Text.Line heroSubtitle = subf.render(heroSubtitleText(), MoonUiTheme.TEXT_MUTED);

    @RName("charlist")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Charlist(Utils.iv(args[0])));
	}
    }

    public static class Char {
	public final String name;
	public String disc;
	public Composited.Desc avadesc;
	public Resource.Resolver avamap;
	public Collection<ResData> avaposes;

	public Char(String name) {
	    this.name = name;
	}

	public void ava(Composited.Desc desc, Resource.Resolver resmap, Collection<ResData> poses) {
	    this.avadesc = desc;
	    this.avamap = resmap;
	    this.avaposes = poses;
	}
    }

    public Charlist(int height) {
	super(Coord.z);
	this.height = height;
	setcanfocus(true);
	this.preview = add(new Avaview(UI.scale(320, 448), -1, "avacam"), Coord.z);
	this.playbtn = add(new MoonFantasyButton(UI.scale(220), enterText(), this::playSelected), Coord.z);
	this.createProxy = add(new MoonFantasyButton(UI.scale(200), newCharacterText(), this::activateCreateCharacter), Coord.z);
	this.createProxy.setDisabled(true);
	relayout(UI.scale(1600, 900));
    }

    /** Match the immediate parent (e.g. {@link LoginScreen}); clipping follows parent bounds, not {@link UI#root} alone. */
    private Coord viewportSize() {
	if(parent != null && parent.sz.x > 0 && parent.sz.y > 0)
	    return parent.sz;
	if(ui != null && ui.root != null)
	    return ui.root.sz;
	return sz;
    }

    private void pinToViewport() {
	Coord vsz = viewportSize();
	if((ui != null) && (ui.root != null) && (parent != null) && (parent != ui.root)) {
	    unlink();
	    parent = ui.root;
	    link();
	    raise();
	}
	if(vsz.x > 0 && vsz.y > 0 && !vsz.equals(sz))
	    resize(vsz);
	c = Coord.z;
    }

    private static String heroSubtitleText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u0412\u044B\u0431\u043E\u0440 \u043F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430 \u0438 \u0432\u0445\u043E\u0434 \u0432 \u043C\u0438\u0440" : "Character selection and world entry");
    }

    private static String helperChipText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430 \u0438 \u043F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u0435 \u043F\u0443\u0442\u044C" : "Choose your character and continue your journey");
    }

    private static String previewTitleText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u041F\u0440\u0435\u0434\u043F\u0440\u043E\u0441\u043C\u043E\u0442\u0440" : "Preview");
    }

    private static String previewHintText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ?
	    "\u0411\u043E\u043B\u044C\u0448\u043E\u0439 \u043F\u043E\u0440\u0442\u0440\u0435\u0442 \u0441\u043F\u0440\u0430\u0432\u0430 \u0437\u0430\u043C\u0435\u043D\u044F\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0435 \u043E\u043A\u043D\u043E \u043F\u0440\u0435\u0434\u043F\u0440\u043E\u0441\u043C\u043E\u0442\u0440\u0430." :
	    "The large portrait on the right replaces the old preview frame.");
    }

    private static String upText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u0412\u044B\u0448\u0435" : "Up");
    }

    private static String downText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u041D\u0438\u0436\u0435" : "Down");
    }

    private static String enterText() {
	return(LocalizationManager.tr("charlist.play"));
    }

    private static String newCharacterText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u041D\u043E\u0432\u044B\u0439 \u043F\u0435\u0440\u0441\u043E\u043D\u0430\u0436" : "New character");
    }

    private static String verifiedText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u0412\u0435\u0440\u0438\u0444\u0438\u0446\u0438\u0440\u043E\u0432\u0430\u043D" : "Verified");
    }

    private static String steamText() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "Steam" : "Steam");
    }

    private String createButtonLabel() {
	if((createSource instanceof Button) && (((Button)createSource).text != null) && (((Button)createSource).text.text != null) && !((Button)createSource).text.text.isEmpty())
	    return(((Button)createSource).text.text);
	return(newCharacterText());
    }

    private void activateCreateCharacter() {
	if(createSource instanceof Button)
	    ((Button)createSource).click();
	else if(createSource instanceof IButton)
	    ((IButton)createSource).click();
	else if(createSource != null)
	    createSource.wdgmsg("activate");
    }

    private static boolean isCreateHotkey(KeyMatch key) {
	return((key != null) && (key != KeyMatch.nil) && "N".equalsIgnoreCase(key.keyname) && ((key.modmatch & KeyMatch.M) != 0));
    }

    private static boolean isCreateSource(Widget w) {
	if(w == null)
	    return(false);
	KeyMatch key = (w.kb_gkey != null) ? w.kb_gkey.key() : w.gkey;
	if(isCreateHotkey(key))
	    return(true);
	if(w instanceof Button) {
	    Button btn = (Button)w;
	    return((btn.text != null) && (btn.text.text != null) && btn.text.text.toLowerCase().contains("new"));
	}
	return(false);
    }

    private Widget findCreateSource(Widget w) {
	if((w == null) || (w == this) || (w == createProxy))
	    return(null);
	if(isCreateSource(w))
	    return(w);
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    Widget found = findCreateSource(ch);
	    if(found != null)
		return(found);
	}
	return(null);
    }

    public void setCreateSource(Widget source) {
	if(source == null)
	    return;
	if(source == createProxy)
	    return;
	if(!isCreateSource(source))
	    return;
	this.createSource = source;
	KeyMatch key = (source.kb_gkey != null) ? source.kb_gkey.key() : source.gkey;
	if(key != null)
	    createProxy.setgkey(key);
	source.kb_gkey = null;
	source.gkey = null;
	source.tooltip = null;
	this.createSource.move(Coord.of(-UI.scale(10000), -UI.scale(10000)));
	this.createSource.hide();
	ensureCreateProxy();
	relayout(viewportSize());
    }

    private void ensureCreateProxy() {
	String label = createButtonLabel();
	createProxy.setLabel(label);
	createProxy.setDisabled(createSource == null);
    }

    private String accountName() {
	String acct = Bootstrap.authuser.get();
	if((acct == null) || acct.isEmpty())
	    acct = Utils.getpref("tokenname@" + Bootstrap.authserv.get().host, null);
	if(((acct == null) || acct.isEmpty()) && (Bootstrap.authserv.get() != null))
	    acct = Utils.getpref("savedtoken-user@" + Bootstrap.authserv.get().host, null);
	return(acct);
    }

    private boolean hasVerifiedAccount() {
	String acct = accountName();
	if("steam".equals(LoginScreen.authmech.get()))
	    return(true);
	boolean verified = (Bootstrap.authck.get() != null) || (Bootstrap.authtoken.get() != null);
	if(!verified && (acct != null) && !acct.isEmpty() && (Bootstrap.authserv.get() != null))
	    verified = Utils.getpref("savedtoken-" + acct + "@" + Bootstrap.authserv.get().host, null) != null;
	return(verified);
    }

    private String accountBadgeText() {
	String acct = accountName();
	if("steam".equals(LoginScreen.authmech.get())) {
	    if((acct != null) && !acct.isEmpty())
		return(acct + "  Steam");
	    return(steamText());
	}
	if(!hasVerifiedAccount())
	    return(null);
	if((acct != null) && !acct.isEmpty())
	    return(acct + "  " + verifiedText());
	return(verifiedText());
    }

    private static double smooth(double a) {
	return(Utils.smoothstep(Math.max(0.0, Math.min(1.0, a))));
    }

    private void relayout(Coord psz) {
	if((psz == null) || (psz.x < 1) || (psz.y < 1))
	    return;
	int charcount = chars.size();
	if(psz.equals(lastlayout) && (list != null) && (charcount == lastcharcount))
	    return;
	lastlayout = psz;
	lastcharcount = charcount;
	resize(psz);

	Char oldsel = (list != null) ? list.sel : null;
	int oldscroll = (list != null) ? list.scrollval() : 0;

	double scale = Math.max(1.0, Math.min(1.9, Math.min((double)psz.x / UI.scale(1600), (double)psz.y / UI.scale(900))));
	int sidePad = Math.max(UI.scale(36), (int)Math.round(UI.scale(52) * scale));
	int topPad = Math.max(UI.scale(96), (int)Math.round(UI.scale(128) * scale));
	this.topbtnh = Math.max(UI.scale(30), (int)Math.round(UI.scale(36) * scale));
	int baseRowH = Math.max(UI.scale(102), (int)Math.round(UI.scale(118) * scale));
	this.rowh = baseRowH;
	this.rowmarg = Math.max(UI.scale(8), (int)Math.round(UI.scale(10) * scale));

	int framePad = Math.max(UI.scale(12), (int)Math.round(UI.scale(16) * scale));
	this.panelpad = framePad;
	int controlsGap = Math.max(UI.scale(10), (int)Math.round(UI.scale(14) * scale));
	int contentW = psz.x - (sidePad * 2);
	int colGap = UI.scale(36);
	int panelW = Utils.clip((int)Math.round(contentW * 0.44), UI.scale(620), UI.scale(900));
	int previewW = Utils.clip((int)Math.round(contentW * 0.29), UI.scale(360), UI.scale(540));
	if((panelW + colGap + previewW) > contentW)
	    previewW = Math.max(UI.scale(300), contentW - panelW - colGap);

	int desiredBottom = psz.y - Math.max(UI.scale(68), (int)Math.round(UI.scale(84) * scale));
	int maxPanelH = Math.max(UI.scale(720), desiredBottom - topPad);
	int listW = panelW - (framePad * 2);
	this.panelc = Coord.of(sidePad, topPad);

	int previewH = Utils.clip((int)Math.round(previewW * 1.18), UI.scale(400), Math.min(UI.scale(620), psz.y - topPad - UI.scale(220)));
	this.previewsz = Coord.of(previewW, previewH);
	this.previewc = Coord.of(psz.x - sidePad - previewW, topPad + UI.scale(36));

	if(list != null)
	    list.destroy();
	if(sau != null)
	    sau.destroy();
	if(sad != null)
	    sad.destroy();
	list = null;
	sau = null;
	sad = null;

	int rows = Math.max(1, charcount);
	int rowstep = rowh + rowmarg;
	int rawAvailListH = Math.max(UI.scale(96), maxPanelH - (framePad * 2));
	int maxRowsFit = Math.max(1, (rawAvailListH + rowmarg) / rowstep);
	int shownRows = Math.max(1, Math.min(rows, Math.min(height, maxRowsFit)));
	boolean needsScroll = rows > shownRows;
	MoonFantasyButton upbtn = needsScroll ? new MoonFantasyButton(listW, upText(), () -> scroll(-1)) : null;
	MoonFantasyButton downbtn = needsScroll ? new MoonFantasyButton(listW, downText(), () -> scroll(1)) : null;
	int controlsH = needsScroll ? (upbtn.sz.y + downbtn.sz.y + (controlsGap * 2)) : 0;
	int availListH = Math.max(UI.scale(96), maxPanelH - (framePad * 2) - controlsH);
	if(needsScroll)
	    shownRows = Math.max(1, Math.min(shownRows, Math.max(1, (availListH + rowmarg) / rowstep)));
	int listH = (shownRows * rowh) + (Math.max(0, shownRows - 1) * rowmarg);
	int topY = panelc.y + framePad;
	if(needsScroll) {
	    sau = add(upbtn, Coord.of(panelc.x + framePad, topY));
	    topY += sau.sz.y + controlsGap;
	}
	list = add(new Boxlist(listH, listW, rowh, rowmarg), Coord.of(panelc.x + framePad, topY));
	int usedListH = Math.max(rowh, Math.min(list.totalh(), list.sz.y));
	if(usedListH != list.sz.y)
	    list.resize(Coord.of(list.sz.x, usedListH));
	int usedPanelH = (list.c.y - panelc.y) + list.sz.y + framePad;
	if(needsScroll) {
	    sad = add(downbtn, Coord.of(panelc.x + framePad, list.c.y + list.sz.y + controlsGap));
	    usedPanelH = (sad.c.y - panelc.y) + sad.sz.y + framePad;
	}
	this.panelsz = Coord.of(panelW, usedPanelH);

	preview.resize(previewsz);
	preview.c = previewc;
	playbtn.setMinWidth(Math.max(UI.scale(220), previewW));
	playbtn.c = Coord.of(previewc.x + (previewW - playbtn.sz.x) / 2, previewc.y + previewH + UI.scale(22));
	createProxy.setMinWidth(Math.max(UI.scale(184), Math.min(UI.scale(260), panelW / 3)));
	createProxy.c = Coord.of(panelc.x + panelsz.x - createProxy.sz.x, Math.max(UI.scale(12), panelc.y - createProxy.sz.y - UI.scale(8)));

	if(oldsel != null) {
	    list.change(oldsel);
	    list.scrollval(Math.max(0, oldscroll));
	    list.display(oldsel);
	} else if(!chars.isEmpty()) {
	    list.change(chars.get(0));
	}
	updateSelection(list.sel);
	updateScrollButtons();
    }

    private void updateScrollButtons() {
	boolean many = (list != null) && (list.scrollmax() > 0);
	if(sau != null) {
	    if(many) sau.show(); else sau.hide();
	}
	if(sad != null) {
	    if(many) sad.show(); else sad.hide();
	}
    }

    private void playSelected() {
	if((list != null) && (list.sel != null))
	    wdgmsg("play", list.sel.name);
    }

    private void updateSelection(Char chr) {
	if(chr == null) {
	    selname = null;
	    seldisc = null;
	    preview.avagob = -1;
	    preview.avadesc = null;
	    return;
	}
	selname = titlef.render(chr.name, MoonUiTheme.ACCENT);
	String d = (chr.disc == null || chr.disc.isEmpty()) ? previewHintText() : chr.disc;
	seldisc = bodyf.render(d, MoonUiTheme.TEXT);
	if(chr.avadesc != null) {
	    preview.pop(chr.avadesc.clone(), chr.avamap);
	    preview.chposes(chr.avaposes, false);
	}
	if((avalink != null) && (chr.avadesc != null)) {
	    avalink.pop(chr.avadesc.clone(), chr.avamap);
	    avalink.chposes(chr.avaposes, false);
	}
    }

    private void drawBackdrop(GOut g) {
	Tex bg = LoginScreen.loginBackgroundTex();
	Coord bsz = bg.sz();
	double cover = Math.max((double)sz.x / Math.max(1, bsz.x), (double)sz.y / Math.max(1, bsz.y));
	double zoom = cover * (1.02 + (0.01 * Math.sin(breathe * 0.32)));
	Coord dsz = Coord.of((int)Math.round(bsz.x * zoom), (int)Math.round(bsz.y * zoom));
	Coord ul = Coord.of((sz.x - dsz.x) / 2, (sz.y - dsz.y) / 2);
	g.image(bg, ul, dsz);
	MoonUiTheme.drawVerticalGradient(g, Coord.z, sz, new Color(10, 10, 24, 96), new Color(8, 8, 18, 198));
	g.chcolor(255, 255, 255, 8);
	g.line(Coord.of(UI.scale(52), sz.y - UI.scale(118)), Coord.of(sz.x - UI.scale(52), sz.y - UI.scale(118)), 1);
	g.chcolor();
    }

    private int livePanelHeight() {
	int bottom = panelc.y + panelpad;
	if(list != null)
	    bottom = Math.max(bottom, list.c.y + list.sz.y + panelpad);
	if((sau != null) && sau.visible())
	    bottom = Math.max(bottom, sau.c.y + sau.sz.y + panelpad);
	if((sad != null) && sad.visible())
	    bottom = Math.max(bottom, sad.c.y + sad.sz.y + panelpad);
	return(Math.max(UI.scale(96), bottom - panelc.y));
    }

    private void drawHero(GOut g) {
	double a = smooth(introa);
	int titleY = headerY + (int)Math.round((1.0 - a) * UI.scale(18));
	g.chcolor(255, 255, 255, Math.min(255, (int)Math.round(255 * a)));
	g.aimage(heroTitle.tex(), Coord.of(sz.x / 2, titleY), 0.5, 0.0);
	g.chcolor(255, 255, 255, Math.min(255, (int)Math.round(220 * a)));
	g.aimage(heroSubtitle.tex(), Coord.of(sz.x / 2, titleY + heroTitle.sz().y + UI.scale(12)), 0.5, 0.0);

	Text.Line chip = chipf.render(helperChipText(), new Color(232, 226, 255));
	int chipW = Math.min(sz.x - UI.scale(96), Math.max(UI.scale(560), chip.sz().x + UI.scale(52)));
	Coord chipSz = Coord.of(chipW, UI.scale(32));
	Coord chipUl = Coord.of((sz.x - chipW) / 2, titleY + heroTitle.sz().y + heroSubtitle.sz().y + UI.scale(30));
	g.chcolor(26, 22, 52, Math.min(255, (int)Math.round(126 * a)));
	g.frect(chipUl, chipSz);
	g.chcolor(MoonUiTheme.BORDER.getRed(), MoonUiTheme.BORDER.getGreen(), MoonUiTheme.BORDER.getBlue(),
	    Math.min(255, (int)Math.round(144 * a)));
	g.rect(chipUl, chipSz);
	g.chcolor(214, 208, 255, Math.min(255, (int)Math.round(44 * a)));
	g.line(Coord.of(chipUl.x + UI.scale(16), chipUl.y + chipSz.y / 2), Coord.of(chipUl.x + UI.scale(96), chipUl.y + chipSz.y / 2), 1);
	g.line(Coord.of(chipUl.x + chipSz.x - UI.scale(96), chipUl.y + chipSz.y / 2),
	    Coord.of(chipUl.x + chipSz.x - UI.scale(16), chipUl.y + chipSz.y / 2), 1);
	g.chcolor(255, 255, 255, Math.min(255, (int)Math.round(230 * a)));
	g.aimage(chip.tex(), chipUl.add(chipSz.div(2)), 0.5, 0.5);
	g.chcolor();
    }

    private void drawListStage(GOut g) {
        Text.Line hdr = subf.render(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "\u041F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0438" : "Characters", MoonUiTheme.ACCENT);
        Coord hdrc = panelc.add(0, -hdr.sz().y - UI.scale(10));
        g.image(hdr.tex(), hdrc);
        g.chcolor(214, 208, 255, 48);
        int lineY = hdrc.y + (hdr.sz().y / 2);
        int lineStart = hdrc.x + hdr.sz().x + UI.scale(12);
        int lineEnd = panelc.x + panelsz.x;
        if((createProxy != null) && createProxy.visible())
            lineEnd = Math.min(lineEnd, createProxy.c.x - UI.scale(18));
        if(lineEnd > lineStart)
            g.line(Coord.of(lineStart, lineY), Coord.of(lineEnd, lineY), 1);
        g.chcolor();
        String badge = accountBadgeText();
        if(badge != null) {
            Text.Line chip = chipf.render(badge, new Color(255, 245, 208));
            Coord chipsz = chip.sz().add(UI.scale(34), UI.scale(12));
            int right = panelc.x + panelsz.x;
            if((createProxy != null) && createProxy.visible())
                right = createProxy.c.x - UI.scale(12);
            Coord chipul = Coord.of(right - chipsz.x, panelc.y - chipsz.y - UI.scale(10));
            g.chcolor(74, 56, 18, 214);
            g.frect(chipul, chipsz);
            g.chcolor(230, 199, 96, 230);
            g.rect(chipul, chipsz);
            Coord iconc = chipul.add(UI.scale(13), chipsz.y / 2);
            g.chcolor(240, 206, 108, 255);
            g.frect(iconc.sub(UI.scale(4), UI.scale(4)), Coord.of(UI.scale(8), UI.scale(8)));
            g.chcolor(255, 247, 220, 255);
            g.line(iconc.add(UI.scale(8), UI.scale(-1)), iconc.add(UI.scale(11), UI.scale(3)), 1);
            g.line(iconc.add(UI.scale(11), UI.scale(3)), iconc.add(UI.scale(16), UI.scale(-5)), 1);
            g.aimage(chip.tex(), chipul.add(UI.scale(28) + (chip.sz().x / 2), chipsz.y / 2), 0.5, 0.5);
            g.chcolor();
        }
    }

    private void drawPreviewStage(GOut g) {
        Coord ul = previewc.sub(UI.scale(10), UI.scale(10));
        Coord box = previewsz.add(UI.scale(20), UI.scale(20));
        g.chcolor(16, 14, 34, 116);
        g.frect(ul, box);
        g.chcolor(MoonUiTheme.BORDER);
        g.rect(ul, box);
        g.chcolor(MoonUiTheme.BORDER_SOFT);
        g.rect(ul.add(UI.scale(2), UI.scale(2)), box.sub(UI.scale(4), UI.scale(4)));
        g.chcolor();
        Text.Line hdr = subf.render(previewTitleText(), MoonUiTheme.ACCENT);
        Coord titlec = Coord.of(previewc.x, ul.y - hdr.sz().y - UI.scale(18));
        g.image(hdr.tex(), titlec);
        g.chcolor(214, 208, 255, 48);
        g.line(Coord.of(titlec.x + hdr.sz().x + UI.scale(12), titlec.y + (hdr.sz().y / 2)),
            Coord.of(previewc.x + previewsz.x, titlec.y + (hdr.sz().y / 2)), 1);
        g.chcolor();
        if(selname != null)
            g.image(selname.tex(), Coord.of(previewc.x, titlec.y + hdr.sz().y + UI.scale(10)));
    }

    public class Charbox extends Widget {
	public final Char chr;
	public final Avaview ava;
	public final ILabel name, disc;
	private final MoonFantasyButton rowPlay;

	public Charbox(Char chr, Coord rowsz) {
	    super(rowsz);
	    this.chr = chr;
	    int avaSide = Math.max(UI.scale(84), sz.y - UI.scale(16));
	    Widget avaf = add(Frame.with(this.ava = new Avaview(Coord.of(avaSide, avaSide), -1, "avacam"), false), Coord.of(UI.scale(8), (sz.y - avaSide) / 2));
	    name = add(new ILabel(chr.name, MoonUiTheme.CHARLIST_NAME_FURN), avaf.pos("ur").adds(UI.scale(10), UI.scale(4)));
	    disc = add(new ILabel("", MoonUiTheme.CHARLIST_META_FURN), name.pos("bl").adds(0, UI.scale(6)));
	    rowPlay = adda(new MoonFantasyButton(Math.max(UI.scale(128), UI.scale((int)(sz.x * 0.2))), enterText(),
		    () -> Charlist.this.wdgmsg("play", chr.name)), pos("cbr").subs(UI.scale(12), UI.scale(10)), 1.0, 1.0);
	}

	public void tick(double dt) {
	    if(chr.avadesc != ava.avadesc)
		ava.pop(chr.avadesc, chr.avamap);
	    String sdisc = showdisc ? ((chr.disc == null) ? "" : chr.disc) : "";
	    if(!Utils.eq(sdisc, disc.text()))
		disc.settext(sdisc);
	}

	public void draw(GOut g) {
	    if((list != null) && (list.sel == chr))
		MoonUiTheme.drawVerticalGradient(g, Coord.z, sz, MoonUiTheme.HEADER_BOTTOM, MoonUiTheme.BODY_BOTTOM);
	    else
		MoonUiTheme.drawVerticalGradient(g, Coord.z, sz, new Color(38, 33, 70, 222), new Color(22, 19, 44, 214));
	    g.chcolor(MoonUiTheme.BORDER);
	    g.rect(Coord.z, sz);
	    g.chcolor(MoonUiTheme.BORDER_SOFT);
	    g.rect(Coord.of(UI.scale(2), UI.scale(2)), sz.sub(UI.scale(4), UI.scale(4)));
	    g.chcolor();
	    super.draw(g);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.propagate(this) || super.mousedown(ev))
		return(true);
	    if(list != null)
		list.change(chr);
	    return(true);
	}
    }

    public class Boxlist extends SListBox<Char, Charbox> {
	public Boxlist(int h, int w, int itemh, int marg) {
	    super(Coord.of(w, h), itemh, marg);
	}

	protected List<Char> items() {return(chars);}
	protected Charbox makeitem(Char chr, int idx, Coord sz) {return(new Charbox(chr, sz));}
	protected void drawslot(GOut g, Char item, int idx, Area area) {}
	public boolean mousewheel(MouseWheelEvent ev) {return(false);}
	protected boolean unselect(int button) {return(false);}
	protected boolean autoscroll() {return(false);}

	public void change(Char chr) {
	    super.change(chr);
	    display(chr);
	    updateSelection(chr);
	}
    }

    protected void added() {
	parent.setfocus(this);
	pinToViewport();
	relayout(viewportSize());
	raise();
    }

    public void presize() {
	pinToViewport();
	relayout(viewportSize());
    }

    private void checkdisc() {
	String one = null;
	boolean first = true;
	showdisc = false;
	synchronized(chars) {
	    for(Char c : chars) {
		if(first) {
		    one = c.disc;
		    first = false;
		} else if(!Utils.eq(one, c.disc)) {
		    showdisc = true;
		    break;
		}
	    }
	}
    }

    private int scrolltgt = -1;
    private double scrollval = -1;
    public void tick(double dt) {
	pinToViewport();
	if(!viewportSize().equals(lastlayout) || (chars.size() != lastcharcount))
	    relayout(viewportSize());
	if((createSource == null) && (ui != null) && (ui.root != null)) {
	    Widget found = findCreateSource(ui.root);
	    if(found != null)
		setCreateSource(found);
	}
	if(scrolltgt >= 0 && list != null) {
	    if(scrollval < 0)
		scrollval = list.scrollval();
	    double d = scrollval - scrolltgt;
	    double nv = scrolltgt + (d * Math.pow(0.5, dt * 50));
	    if(Math.abs(nv - scrolltgt) < 1) {
		nv = scrolltgt;
		scrolltgt = -1;
		scrollval = -1;
	    }
	    list.scrollval((int)Math.round(scrollval = nv));
	}
	if(dirty) {
	    checkdisc();
	    dirty = false;
	    updateSelection((list != null) ? list.sel : null);
	}
	introa = Math.min(1.0, introa + (dt / 0.75));
	breathe += dt;
	updateScrollButtons();
	super.tick(dt);
    }

    public void scroll(int amount) {
	if(list == null)
	    return;
	scrolltgt = Utils.clip(((scrolltgt < 0) ? list.scrollval() : scrolltgt) + ((rowh + rowmarg) * amount), list.scrollmin(), list.scrollmax());
    }

    public boolean mousewheel(MouseWheelEvent ev) {
	scroll(ev.a);
	return(true);
    }

    public void draw(GOut g) {
	drawBackdrop(g);
	drawHero(g);
	drawListStage(g);
	drawPreviewStage(g);
	super.draw(g);
    }

    public void addchild(Widget child, Object... args) {
	/* Suppress vanilla character-entry widgets (large preview, old create button, etc.).
	 * This screen owns its own fullscreen layout. Keep server widgets alive but hidden/off-screen
	 * so widget ids remain valid for server messages. */
	add(child, Coord.of(-UI.scale(10000), -UI.scale(10000)));
	child.hide();
	if(isCreateSource(child))
	    setCreateSource(child);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "add") {
	    Char c = new Char((String)args[0]);
	    if(args.length > 1) {
		Object[] rawdesc = (Object[])args[1];
		Collection<ResData> poses = new ArrayList<ResData>();
		Composited.Desc desc = Composited.Desc.decode(ui.sess, rawdesc);
		Resource.Resolver map = new Resource.Resolver.ResourceMap(ui.sess, (Object[])args[2]);
		if(rawdesc.length > 3) {
		    Object[] rawposes = (Object[])rawdesc[3];
		    for(int i = 0; i < rawposes.length; i += 2)
			poses.add(new ResData(ui.sess.getresv(rawposes[i]), new MessageBuf((byte[])rawposes[i + 1])));
		}
		c.ava(desc, map, poses);
	    }
	    synchronized(chars) {
		chars.add(c);
		if((list != null) && (list.sel == null))
		    list.change(c);
	    }
	    dirty = true;
	} else if(msg == "ava") {
	    String cnm = (String)args[0];
	    Object[] rawdesc = (Object[])args[1];
	    Collection<ResData> poses = new ArrayList<ResData>();
	    Composited.Desc ava = Composited.Desc.decode(ui.sess, rawdesc);
	    Resource.Resolver map = new Resource.Resolver.ResourceMap(ui.sess, (Object[])args[2]);
	    if(rawdesc.length > 3) {
		Object[] rawposes = (Object[])rawdesc[3];
		for(int i = 0; i < rawposes.length; i += 2)
		    poses.add(new ResData(ui.sess.getresv(rawposes[i]), new MessageBuf((byte[])rawposes[i + 1])));
	    }
	    synchronized(chars) {
		for(Char c : chars) {
		    if(c.name.equals(cnm)) {
			c.ava(ava, map, poses);
			dirty = true;
			break;
		    }
		}
	    }
	} else if(msg == "srv") {
	    String cnm = (String)args[0];
	    String disc = (String)args[1];
	    synchronized(chars) {
		for(Char c : chars) {
		    if(c.name.equals(cnm)) {
			c.disc = disc;
			dirty = true;
			break;
		    }
		}
	    }
	} else if(msg == "biggu") {
	    int id = Utils.iv(args[0]);
	    if(id < 0) {
		avalink = null;
	    } else {
		Widget tgt = ui.getwidget(id);
		if(tgt instanceof ProxyFrame) {
		    ProxyFrame<?> pf = (ProxyFrame<?>)tgt;
		    if(pf.ch instanceof Avaview)
			avalink = (Avaview)pf.ch;
		    pf.hide();
		} else if(tgt instanceof Avaview) {
		    avalink = (Avaview)tgt;
		    tgt.hide();
		    if(tgt.parent != null)
			tgt.parent.hide();
		}
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    public boolean keydown(KeyDownEvent ev) {
	if(ev.code == ev.awt.VK_UP) {
	    if(list != null && !chars.isEmpty())
		list.change(chars.get(Math.max(chars.indexOf(list.sel) - 1, 0)));
	    return(true);
	} else if(ev.code == ev.awt.VK_DOWN) {
	    if(list != null && !chars.isEmpty())
		list.change(chars.get(Math.min(chars.indexOf(list.sel) + 1, chars.size() - 1)));
	    return(true);
	} else if(ev.code == ev.awt.VK_ENTER) {
	    playSelected();
	    return(true);
	}
	return(super.keydown(ev));
    }
}

