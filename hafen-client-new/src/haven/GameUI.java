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
import java.util.*;
import java.util.function.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import haven.render.Location;
import static haven.Inventory.invsq;

public class GameUI extends ConsoleHost implements Console.Directory, UI.Notice.Handler {
    private static final Text.Foundry worldEntryFoundry = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(34f))).aa(true);
    private static final Text.Foundry worldEntrySubFoundry = new Text.Foundry(Text.serif.deriveFont(Font.PLAIN, UI.scale(16f))).aa(true);
    public final String chrid, genus;
    public final long plid;
    private final Hidepanel ulpanel, umpanel, urpanel, blpanel, mapmenupanel, brpanel, menupanel;
    public Widget portrait;
    public MenuGrid menu;
    public MenuGridWnd menugridwnd;
    public MoonQuickPanel gameShortcuts;
    public MoonQuickPanel moonTools;
    /** Two hand slots (2× cell size) mirroring equipment window. */
    public MoonHandsToolbar moonHandsToolbar;
    /** HUD: {@link Speedget} wire multiplier + assist ({@link MoonSpeedWirePanel}). */
    public MoonSpeedWirePanel moonSpeedWirePanel;
    public MapView map;
    public GobIcon.Settings iconconf;
    public MiniMap mmap;
    public Fightview fv;
    private List<Widget> meters = new LinkedList<Widget>();
    private Text lastmsg;
    private double msgtime;
    private String lastmsgRaw;
    private Color lastmsgCol;
    private long lastmsgTrGen = -1;
    private MoonInvWnd invwnd;
    private MoonEquWnd equwnd;
    private Window makewnd, srchwnd, iconwnd;
    private Widget makecontent;
    private Coord makewndc = Utils.getprefc("makewndc", new Coord(400, 200));
    public Inventory maininv;
    public CharWnd chrwdg;
    /** HUD FEP meter (wired when {@link CharWnd} receives {@link BAttrWnd}). */
    public FepMeter fepmeter;
    public MapWnd mapfile;
    private Widget qqview;
    public BuddyWnd buddies;
    private final Zergwnd zerg;
    public final Collection<Polity> polities = new ArrayList<Polity>();
    public HelpWnd help;
    public OptWnd opts;
    /** MooNWide: saved-account session switcher (native auth). */
    public MoonSessionWnd sesswnd;
    public haven.timers.TimersWnd timerswnd;
    public Collection<DraggedItem> hand = new LinkedList<DraggedItem>();
    public WItem vhand;
    /** MooNWide: chat in a movable window. */
    public ChatWnd chatwnd;
    /** MooNWide: bot menus from the action grid. */
    public MoonBotsWnd moonBotsWnd;
    /** MooNWide: short-range navigation / saved map points ({@link TeleportManager}). */
    public MoonTeleportWnd moonTeleportWnd;
    /** Local cache search over last-known storage snapshots. */
    public MoonStorageSearchWnd moonStorageSearchWnd;
    /** MooNWide: first-run / help panel for the new UI. */
    public MoonUiGuideWnd moonUiGuideWnd;
    public MoonCombatBotWnd moonCombatBotWnd;
    public MoonTreeBotWnd moonTreeBotWnd;
    public MoonMineBotWnd moonMineBotWnd;
    public MoonAutoDropWnd moonAutoDropWnd;
    public MoonFishingBotWnd moonFishingBotWnd;
    public MoonAutomationToolsWnd moonAutomationToolsWnd;
    public ChatUI chat;
    public ChatUI.Channel syslog;
    public Progress prog = null;
    private boolean afk = false;
    public BeltSlot[] belt = new BeltSlot[144];
    private final BeltSlot[] basebelt = new BeltSlot[144];
    public Belt beltwdg;
    public BeltWnd nkeyBelt, fkeyBelt, numBelt;
    public MapMenuWnd mapmenuwnd;
    public final Map<Integer, String> polowners = new HashMap<Integer, String>();
    public Bufflist buffs;
    private MiniMapWidget mmapwnd;
    private CharacterPanel charpanel;
    private Partyview partywnd;
    private double worldEntryFade = 1.0;
    private double worldEntryPulse = 0.0;
    private boolean worldEntryGate = true;
    private boolean worldEntrySawProg = false;
    private double worldEntryReleaseAt = -1.0;
    private boolean worldEntryOverlayArmed = true;
    private boolean worldEntryRevealed = false;
    private final Text.Line worldEntryTitle = worldEntryFoundry.render(MainFrame.TITLE(), MoonUiTheme.ACCENT);
    private final Text.Line worldEntrySub = worldEntrySubFoundry.render(worldEntrySubtitle(), MoonUiTheme.TEXT_MUTED);
    private String worldEntryLoadKey = null;
    private Text.Line worldEntryLoad = null;
    private double worldEntryBarProgress = 0.0;
    private double worldEntryBarTarget = 0.0;
    private double moonPerfSceneScanAt = 0.0;
    private double moonPerfHudRefreshAt = 0.0;

    public static abstract class BeltSlot {
	public final int idx;

	public BeltSlot(int idx) {
	    this.idx = idx;
	}

	public abstract void draw(GOut g);
	public abstract void use(MenuGrid.Interaction iact);
    }

    private static final OwnerContext.ClassResolver<ResBeltSlot> beltctxr = new OwnerContext.ClassResolver<ResBeltSlot>()
	.add(GameUI.class, slot -> slot.wdg())
	.add(Glob.class, slot -> slot.wdg().ui.sess.glob)
	.add(Session.class, slot -> slot.wdg().ui.sess);
    public class ResBeltSlot extends BeltSlot implements GSprite.Owner, RandomSource {
	public final ResData rdt;

	public ResBeltSlot(int idx, ResData rdt) {
	    super(idx);
	    this.rdt = rdt;
	}

	private GSprite spr = null;
	public GSprite spr() {
	    GSprite ret = this.spr;
	    if(ret == null)
		ret = this.spr = GSprite.create(this, rdt.res.get(), new MessageBuf(rdt.sdt));
	    return(ret);
	}

	public void draw(GOut g) {
	    try {
		spr().draw(g);
	    } catch(Loading l) {}
	}

	public void use(MenuGrid.Interaction iact) {
	    Object[] args = {idx, iact.btn, iact.modflags};
	    if(iact.mc != null) {
		args = Utils.extend(args, iact.mc.floor(OCache.posres));
		if(iact.click != null)
		    args = Utils.extend(args, iact.click.clickargs());
	    }
	    GameUI.this.wdgmsg("belt", args);
	}

	public Resource getres() {return(rdt.res.get());}
	public Random mkrandoom() {return(new Random(System.identityHashCode(this)));}
	public <T> T context(Class<T> cl) {return(beltctxr.context(cl, this));}
	private GameUI wdg() {return(GameUI.this);}
    }

    public static class PagBeltSlot extends BeltSlot {
	public final MenuGrid.Pagina pag;

	public PagBeltSlot(int idx, MenuGrid.Pagina pag) {
	    super(idx);
	    this.pag = pag;
	}

	public void draw(GOut g) {
	    try {
		MenuGrid.PagButton btn = pag.button();
		btn.draw(g, btn.spr());
	    } catch(Loading l) {
	    }
	}

	public void use(MenuGrid.Interaction iact) {
	    try {
		pag.scm.use(pag.button(), iact, false);
	    } catch(Loading l) {
	    }
	}

	public static MenuGrid.Pagina resolve(MenuGrid scm, Indir<Resource> resid) {
	    Resource res = resid.get();
	    Resource.AButton act = res.layer(Resource.action);
	    /* XXX: This is quite a hack. Is there a better way? */
	    if((act != null) && (act.ad.length == 0))
		return(scm.paginafor(res.indir()));
	    return(scm.paginafor(resid));
	}
    }

    /* XXX: Remove me */
    public BeltSlot mkbeltslot(int idx, ResData rdt) {
	Resource res = rdt.res.get();
	Resource.AButton act = res.layer(Resource.action);
	if(act != null) {
	    if(act.ad.length == 0)
		return(new PagBeltSlot(idx, menu.paginafor(res.indir())));
	    return(new PagBeltSlot(idx, menu.paginafor(rdt.res)));
	}
	return(new ResBeltSlot(idx, rdt));
    }

    private static String specialBeltPref(int slot) {
	return("moon-special-belt-" + slot);
    }

    private MenuGrid.SpecialPagina specialBeltPagina(int slot) {
	if(menu == null || slot < 0 || slot >= belt.length)
	    return(null);
	String key = Utils.getpref(specialBeltPref(slot), null);
	if(key == null)
	    return(null);
	return(menu.specialpag.get(key));
    }

    private void refreshBeltSlot(int slot) {
	if(slot < 0 || slot >= belt.length)
	    return;
	MenuGrid.SpecialPagina sp = specialBeltPagina(slot);
	if(sp != null)
	    belt[slot] = new PagBeltSlot(slot, sp);
	else
	    belt[slot] = basebelt[slot];
    }

    private void refreshAllBeltSlots() {
	for(int i = 0; i < belt.length; i++)
	    refreshBeltSlot(i);
    }

    public boolean setSpecialBeltSlot(int slot, MenuGrid.Pagina pag) {
	if(!(pag instanceof MenuGrid.SpecialPagina) || slot < 0 || slot >= belt.length)
	    return(false);
	Utils.setpref(specialBeltPref(slot), ((MenuGrid.SpecialPagina)pag).key);
	refreshBeltSlot(slot);
	return(true);
    }

    public boolean clearSpecialBeltSlot(int slot) {
	if(slot < 0 || slot >= belt.length)
	    return(false);
	if(Utils.getpref(specialBeltPref(slot), null) == null)
	    return(false);
	Utils.setpref(specialBeltPref(slot), null);
	refreshBeltSlot(slot);
	return(true);
    }

    private void setBaseBeltSlot(int slot, BeltSlot val) {
	if(slot < 0 || slot >= belt.length)
	    return;
	basebelt[slot] = val;
	refreshBeltSlot(slot);
    }

    public abstract class Belt extends Widget implements DTarget, DropTarget {
	public Belt(Coord sz) {
	    super(sz);
	}

	public void act(int idx, MenuGrid.Interaction iact) {
	    if(belt[idx] != null)
		belt[idx].use(iact);
	}

	public void keyact(int slot) {
	    if(map != null) {
		BeltSlot si = belt[slot];
		Coord mvc = map.rootxlate(ui.mc);
		if(mvc.isect(Coord.z, map.sz)) {
		    map.new Hittest(mvc) {
			    protected void hit(Coord pc, Coord2d mc, ClickData inf) {
				act(slot, new MenuGrid.Interaction(1, ui.modflags(), mc, inf));
			    }
			    
			    protected void nohit(Coord pc) {
				act(slot, new MenuGrid.Interaction(1, ui.modflags()));
			    }
			}.run();
		}
	    }
	}

	public abstract int beltslot(Coord c);

	public boolean mousedown(MouseDownEvent ev) {
	    int slot = beltslot(ev.c);
	    if(slot != -1) {
		if(ev.b == 1)
		    act(slot, new MenuGrid.Interaction(1, ui.modflags()));
		if(ev.b == 3) {
		    if(GameUI.this.clearSpecialBeltSlot(slot))
			return(true);
		    GameUI.this.wdgmsg("setbelt", slot, null);
		}
		return(true);
	    }
	    if(super.mousedown(ev))
		return(true);
	    return(checkhit(ev.c));
	}

	public boolean drop(Coord c, Coord ul) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		GameUI.this.clearSpecialBeltSlot(slot);
		GameUI.this.wdgmsg("setbelt", slot, 0);
		return(true);
	    }
	    return(false);
	}

	public boolean iteminteract(Coord c, Coord ul) {return(false);}

	public boolean dropthing(Coord c, Object thing) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		if(thing instanceof MenuGrid.Pagina) {
		    MenuGrid.Pagina pag = (MenuGrid.Pagina)thing;
		    if(GameUI.this.setSpecialBeltSlot(slot, pag))
			return(true);
		    GameUI.this.clearSpecialBeltSlot(slot);
		    try {
			if(pag.id instanceof Indir)
			    GameUI.this.wdgmsg("setbelt", slot, "res", pag.res().name);
			else
			    GameUI.this.wdgmsg("setbelt", slot, "pag", pag.id);
		    } catch(Loading l) {
		    }
		    return(true);
		}
	    }
	    return(false);
	}
    }
    
    public void beltKeyAct(int slot) {
	if(map != null) {
	    Coord mvc = map.rootxlate(ui.mc);
	    if(mvc.isect(Coord.z, map.sz)) {
		map.new Hittest(mvc) {
			protected void hit(Coord pc, Coord2d mc, ClickData inf) {
			    if(belt[slot] != null)
				belt[slot].use(new MenuGrid.Interaction(1, ui.modflags(), mc, inf));
			}
			protected void nohit(Coord pc) {
			    if(belt[slot] != null)
				belt[slot].use(new MenuGrid.Interaction(1, ui.modflags()));
			}
		    }.run();
	    }
	}
    }

    private boolean combatKeysReserved(GlobKeyEvent ev) {
	if(ev == null || fv == null || fv.lsrel == null || fv.lsrel.isEmpty())
	    return(false);
	for(KeyBinding kb : Fightsess.kb_acts) {
	    if((kb != null) && kb.key().match(ev))
		return(true);
	}
	return(false);
    }

    @RName("gameui")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    String chrid = (String)args[0];
	    long plid = Utils.uiv(args[1]);
	    String genus = "";
	    if(args.length > 2)
		genus = (String)args[2];
	    return(new GameUI(chrid, plid, genus));
	}
    }
    
    private final Coord minimapc;
    private final Coord menugridc;
    public GameUI(String chrid, long plid, String genus) {
	this.chrid = chrid;
	this.plid = plid;
	this.genus = genus;
	setcanfocus(true);
	setfocusctl(true);
	chat = new ChatUI(UI.scale(620), UI.scale(220), true);
	chatwnd = add(new ChatWnd(chat), Utils.getprefc("wndc-chat", UI.scale(new Coord(20, 180))));
	setchatvisible(Utils.getprefb("chatvis", true), false);
	blpanel = add(new Hidepanel("gui-bl", null, new Coord(-1,  1)) {
		public void move(double a) {
		    super.move(a);
		    mapmenupanel.move();
		}
	    });
	/* Left-edge fold strip (prefs id "mapmenu"). Vanilla used gfx/hud/lbtn-* on fold buttons;
	 * that is the ornate "gold frame" on old builds. Minimap window is MiniMapWidget ("moon-minimap"). */
	mapmenupanel = add(new Hidepanel("mapmenu", new Indir<Coord>() {
		public Coord get() {
		    return(new Coord(0, Math.min(blpanel.c.y - mapmenupanel.sz.y + UI.scale(33), GameUI.this.sz.y - mapmenupanel.sz.y)));
		}
	    }, new Coord(-1, 0)));
	brpanel = add(new Hidepanel("gui-br", null, new Coord( 1,  1)) {
		public void move(double a) {
		    super.move(a);
		    menupanel.move();
		}
	    });
	menupanel = add(new Hidepanel("menu", new Indir<Coord>() {
		public Coord get() {
		    return(new Coord(GameUI.this.sz.x, Math.min(brpanel.c.y - UI.scale(79), GameUI.this.sz.y - menupanel.sz.y)));
		}
	    }, new Coord(1, 0)));
	ulpanel = add(new Hidepanel("gui-ul", null, new Coord(-1, -1)));
	umpanel = add(new Hidepanel("gui-um", null, new Coord( 0, -1)));
	urpanel = add(new Hidepanel("gui-ur", null, new Coord( 1, -1)));
	mapmenuwnd = null;
	minimapc = new Coord(UI.scale(4), UI.scale(34));
	menugridc = Coord.z;
	/* Vanilla brframe / rbtn / csearch-bg removed — MooNWide uses MenuGridWnd (MoonPanel). */
	foldbuttons();
	/* Force-hide vanilla bottom panels so no ornate art appears. */
	brpanel.cshow(false);
	menupanel.cshow(false);
	blpanel.cshow(false);
	mapmenupanel.cshow(false);
	charpanel = add(new CharacterPanel(), UI.scale(new Coord(10, 10)));
	portrait = charpanel.add(Frame.with(new Avaview(Avaview.dasz, plid, "avacam"), false), Coord.z);
	charpanel.pack();
	buffs = add(new Bufflist(), UI.scale(new Coord(148, 67)));
	umpanel.add(new Cal(), Coord.z);
	syslog = chat.add(new ChatUI.Log("System"));
	opts = add(new OptWnd(), Utils.getprefc("wndc-opts", UI.scale(new Coord(200, 100))));
	opts.hide();
	MooNWideBridge.setMenuToggle(() -> toggleMoonBotsWindow());
	timerswnd = add(new haven.timers.TimersWnd(this), Utils.getprefc("wndc-timers", UI.scale(new Coord(200, 150))));
	timerswnd.hide();
	zerg = add(new Zergwnd(), Utils.getprefc("wndc-zerg", UI.scale(new Coord(187, 50))));
	zerg.hide();
	nkeyBelt = add(BeltWnd.createNKeyBelt(this));
	fkeyBelt = add(BeltWnd.createFKeyBelt(this));
	numBelt = add(BeltWnd.createNumBelt(this));
    }

    private static String worldEntrySubtitle() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "Мир пробуждается" : "The world awakens");
    }

    private static String worldEntryLoadingLine() {
	return(MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "Подготовка мира" : "Preparing the world");
    }

    private boolean moonShouldShedOptionalWork() {
	return(MoonConfig.perfAutoShed && (MoonPerfOverlay.inStressWindow() || ((map != null) && map.moonShouldShedOptional())));
    }

    private boolean moonSceneScanDue(double now) {
	double iv = moonShouldShedOptionalWork() ? 0.20 : 0.12;
	if(now >= moonPerfSceneScanAt) {
	    moonPerfSceneScanAt = now + iv;
	    return(true);
	}
	return(false);
    }

    private boolean moonHudRefreshDue(double now) {
	double iv = moonShouldShedOptionalWork() ? 0.40 : 0.25;
	if(now >= moonPerfHudRefreshAt) {
	    moonPerfHudRefreshAt = now + iv;
	    return(true);
	}
	return(false);
    }

    private String worldEntryLoadLabel(MapView.MoonLoadProgress load) {
	String base = worldEntryLoadingLine();
	if(prog != null)
	    return(String.format(Locale.ROOT, "%s %d%%", base, (int)Math.round(prog.prog * 100.0)));
	if(load == null || !load.loading)
	    return(base);
	if(worldEntryGate)
	    return(String.format(Locale.ROOT, "%s %d/%d", base, load.criticalReady, Math.max(1, load.criticalTotal)));
	return(String.format(Locale.ROOT, "%s %d/%d", base,
	    load.sceneReady + load.optionalReady, Math.max(1, load.sceneTotal + load.optionalTotal)));
    }

    private Text.Line worldEntryLoadLine(MapView.MoonLoadProgress load) {
	String label = worldEntryLoadLabel(load);
	if((worldEntryLoad == null) || !label.equals(worldEntryLoadKey)) {
	    worldEntryLoadKey = label;
	    worldEntryLoad = Text.std.render(label, MoonUiTheme.TEXT_MUTED);
	}
	return(worldEntryLoad);
    }

    private void updateWorldEntryState(double dt, double now) {
	worldEntryPulse += dt;
	if(!MoonConfig.progressiveWorldLoad) {
	    if(worldEntryGate) {
		boolean baseReady = (map != null) && (menu != null) && (prog == null);
		if(baseReady) {
		    if(worldEntryReleaseAt < 0.0)
			worldEntryReleaseAt = now + (worldEntrySawProg ? 0.22 : 0.55);
		    if(now >= worldEntryReleaseAt)
			worldEntryGate = false;
		} else {
		    worldEntryReleaseAt = -1.0;
		}
	    }
	    if(!worldEntryGate && worldEntryFade > 0.0)
		worldEntryFade = Math.max(0.0, worldEntryFade - (dt / 1.45));
	    if(!worldEntryGate && worldEntryFade <= 0.0)
		worldEntryOverlayArmed = false;
	    worldEntryBarTarget = (prog != null) ? prog.prog : (worldEntryGate ? 0.2 : 1.0);
	    worldEntryBarProgress += (worldEntryBarTarget - worldEntryBarProgress) * Math.min(1.0, dt * 4.0);
	    return;
	}
	MapView.MoonLoadProgress load = (map != null) ? map.moonLoadProgress() : MapView.MoonLoadProgress.EMPTY;
	if(worldEntryGate) {
	    boolean baseReady = (map != null) && (menu != null);
	    boolean revealReady = baseReady && (prog == null) && load.revealReady;
	    if(revealReady) {
		if(worldEntryReleaseAt < 0.0)
		    worldEntryReleaseAt = now + (worldEntrySawProg ? 0.10 : 0.18);
		if(now >= worldEntryReleaseAt) {
		    worldEntryGate = false;
		    worldEntryRevealed = true;
		}
	    } else {
		worldEntryReleaseAt = -1.0;
	    }
	}
	double targetFade = worldEntryGate ? 1.0 : 0.0;
	double lerp = Math.min(1.0, dt * ((targetFade < worldEntryFade) ? 3.0 : 5.0));
	worldEntryFade += (targetFade - worldEntryFade) * lerp;
	worldEntryBarTarget = worldEntryGate ? ((prog != null) ? prog.prog : load.revealProgress()) : load.streamProgress();
	worldEntryBarProgress += (worldEntryBarTarget - worldEntryBarProgress) * Math.min(1.0, dt * 6.0);
	if(!worldEntryGate && (worldEntryFade <= 0.02)) {
	    worldEntryFade = 0.0;
	    worldEntryOverlayArmed = false;
	}
    }

    private void drawWorldEntryOverlay(GOut g) {
	boolean hold = worldEntryGate;
	if(!hold && worldEntryFade <= 0.0)
	    return;
	MapView.MoonLoadProgress load = (map != null) ? map.moonLoadProgress() : MapView.MoonLoadProgress.EMPTY;
	double a = hold ? 1.0 : Utils.smoothstep(Math.max(0.0, Math.min(1.0, worldEntryFade)));
	int veilTop = Math.min(255, (int)Math.round((hold ? 246 : 212) * a));
	int veilBottom = Math.min(255, (int)Math.round((hold ? 252 : 232) * a));
	MoonUiTheme.drawVerticalGradient(g, Coord.z, sz, new Color(7, 7, 18, veilTop), new Color(12, 10, 30, veilBottom));
	int titleAlpha = Math.min(255, (int)Math.round((208 + (32 * Math.sin(worldEntryPulse * 2.2))) * a));
	g.chcolor(255, 255, 255, titleAlpha);
	int titleY = sz.y / 2 - worldEntryTitle.sz().y - UI.scale(12);
	g.aimage(worldEntryTitle.tex(), Coord.of(sz.x / 2, titleY), 0.5, 0.0);
	g.chcolor(255, 255, 255, Math.min(255, (int)Math.round(220 * a)));
	g.aimage(worldEntrySub.tex(), Coord.of(sz.x / 2, titleY + worldEntryTitle.sz().y + UI.scale(14)), 0.5, 0.0);
	g.chcolor(214, 208, 255, Math.min(255, (int)Math.round(88 * a)));
	int cy = titleY + worldEntryTitle.sz().y + worldEntrySub.sz().y + UI.scale(34);
	g.line(Coord.of(sz.x / 2 - UI.scale(94), cy), Coord.of(sz.x / 2 - UI.scale(18), cy), 1);
	g.line(Coord.of(sz.x / 2 + UI.scale(18), cy), Coord.of(sz.x / 2 + UI.scale(94), cy), 1);
	Text.Line loadtx = worldEntryLoadLine(load);
	g.chcolor(255, 255, 255, Math.min(255, (int)Math.round((hold ? 220 : 182) * a)));
	g.aimage(loadtx.tex(), Coord.of(sz.x / 2, cy + UI.scale(18)), 0.5, 0.0);
	int barW = UI.scale(220);
	int barH = UI.scale(8);
	Coord bar = Coord.of((sz.x - barW) / 2, cy + UI.scale(48));
	g.chcolor(44, 40, 72, Math.min(255, (int)Math.round((hold ? 220 : 176) * a)));
	g.frect(bar, Coord.of(barW, barH));
	g.chcolor(MoonUiTheme.BORDER);
	g.rect(bar, Coord.of(barW, barH));
	double prog = Math.max(0.0, Math.min(1.0, worldEntryBarProgress));
	int fill = (int)Math.round((barW - UI.scale(4)) * prog);
	if(hold)
	    fill = Math.max(UI.scale(26), fill);
	if(fill > 0) {
	    g.chcolor(214, 208, 255, Math.min(255, (int)Math.round((hold ? 204 : 164) * a)));
	    g.frect(bar.add(UI.scale(2), UI.scale(2)), Coord.of(fill, Math.max(UI.scale(2), barH - UI.scale(4))));
	}
	g.chcolor();
    }

    protected void attached() {
	iconconf = loadiconconf();
	super.attached();
    }

    public static final KeyBinding kb_srch = KeyBinding.get("scm-srch", KeyMatch.forchar('Z', KeyMatch.C));
    private void togglesearch() {
	if(menu == null)
	    return;
	if(srchwnd == null) {
	    srchwnd = new MenuSearch(menu);
	    fitwdg(GameUI.this.add(srchwnd, Utils.getprefc("wndc-srch", new Coord(200, 200))));
	} else {
	    if(!srchwnd.hasfocus) {
		setfocus(srchwnd);
	    } else {
		ui.destroy(srchwnd);
		srchwnd = null;
	    }
	}
    }

    /* MooNWide: vanilla fold panels (brpanel / menupanel / blpanel / mapmenupanel)
     * are permanently hidden — all HUD lives in MoonPanel widgets. updfold is a no-op. */
    private void updfold(boolean reset) {
	if(reset)
	    resetui();
    }

    /* Vanilla foldbuttons() removed — all fold panels permanently hidden in MooNWide. */
    private void foldbuttons() {}

    protected void added() {
	resize(parent.sz);
	ensureMoonHudPanels();
	ui.cons.out = new java.io.PrintWriter(new java.io.Writer() {
		StringBuilder buf = new StringBuilder();
		
		public void write(char[] src, int off, int len) {
		    List<String> lines = new ArrayList<String>();
		    synchronized(this) {
			buf.append(src, off, len);
			int p;
			while((p = buf.indexOf("\n")) >= 0) {
			    String ln = buf.substring(0, p).replace("\t", "        ");
			    lines.add(ln);
			    buf.delete(0, p + 1);
			}
		    }
		    for(String ln : lines) {
			syslog.append(ln, Color.WHITE);
		    }
		}
		
		public void close() {}
		public void flush() {}
	    });
	Debug.log = ui.cons.out;
	if((ui != null) && (ui.sess != null) && (GLPanelHolder.instance != null))
	    GLPanelHolder.instance.registerSessionUi(ui);
	haven.discord.MoonDiscordService.startFor(this);
    }

    public void dispose() {
	haven.discord.MoonDiscordService.stopAll();
	savewndpos();
	Debug.log = new java.io.PrintWriter(System.err);
	ui.cons.clearout();
	super.dispose();
    }
    
    public class Hidepanel extends Widget {
	public final String id;
	public final Coord g;
	public final Indir<Coord> base;
	public boolean tvis;
	private double cur;

	public Hidepanel(String id, Indir<Coord> base, Coord g) {
	    this.id = id;
	    this.base = base;
	    this.g = g;
	    cur = show(tvis = Utils.getprefb(id + "-visible", true))?0:1;
	}

	public <T extends Widget> T add(T child) {
	    super.add(child);
	    pack();
	    if(parent != null)
		move();
	    return(child);
	}

	public Coord base() {
	    if(base != null) return(base.get());
	    return(new Coord((g.x > 0)?parent.sz.x:(g.x < 0)?0:((parent.sz.x - this.sz.x) / 2),
			     (g.y > 0)?parent.sz.y:(g.y < 0)?0:((parent.sz.y - this.sz.y) / 2)));
	}

	public void move(double a) {
	    cur = a;
	    Coord c = new Coord(base());
	    if(g.x < 0)
		c.x -= (int)(sz.x * a);
	    else if(g.x > 0)
		c.x -= (int)(sz.x * (1 - a));
	    if(g.y < 0)
		c.y -= (int)(sz.y * a);
	    else if(g.y > 0)
		c.y -= (int)(sz.y * (1 - a));
	    this.c = c;
	}

	public void move() {
	    move(cur);
	}

	public void presize() {
	    move();
	}

	public void cresize(Widget ch) {
	    sz = contentsz();
	}

	public boolean mshow(final boolean vis) {
	    clearanims(Anim.class);
	    if(vis)
		show();
	    new NormAnim(0.25) {
		final double st = cur, f = vis?0:1;

		public void ntick(double a) {
		    if((a == 1.0) && !vis)
			hide();
		    move(st + (Utils.smoothstep(a) * (f - st)));
		}
	    };
	    tvis = vis;
	    updfold(false);
	    return(vis);
	}

	public boolean mshow() {
	    return(mshow(Utils.getprefb(id + "-visible", true)));
	}

	public boolean cshow(boolean vis) {
	    Utils.setprefb(id + "-visible", vis);
	    if(vis != tvis)
		mshow(vis);
	    return(vis);
	}

	public void cdestroy(Widget w) {
	    parent.cdestroy(w);
	}
    }

    public static class Hidewnd extends Window {
	Hidewnd(Coord sz, String cap, boolean lg) {
	    super(sz, cap, lg);
	}

	Hidewnd(Coord sz, String cap) {
	    super(sz, cap);
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
	    if((sender == this) && msg.equals("close")) {
		this.hide();
		return;
	    }
	    super.wdgmsg(sender, msg, args);
	}
    }

    static class Zergwnd extends Hidewnd {
	Tabs tabs = new Tabs(Coord.z, Coord.z, this);
	final TButton kin, pol, pol2;

	class TButton extends IButton {
	    Tabs.Tab tab = null;
	    final Tex inv;

	    TButton(String nm, boolean g) {
		super("gfx/hud/buttons/" + nm, "u", "d", null);
		if(g)
		    inv = Resource.loadtex("gfx/hud/buttons/" + nm + "g");
		else
		    inv = null;
	    }

	    public void draw(GOut g) {
		if((tab == null) && (inv != null))
		    g.image(inv, Coord.z);
		else
		    super.draw(g);
	    }

	    public void click() {
		if(tab != null) {
		    tabs.showtab(tab);
		    repack();
		}
	    }
	}

	Zergwnd() {
	    super(Coord.z, LocalizationManager.tr("shortcut.kin"), true);
	    kin = add(new TButton("kin", false));
	    kin.tooltip = Text.render(LocalizationManager.tr("shortcut.kin"));
	    pol = add(new TButton("pol", true));
	    pol2 = add(new TButton("rlm", true));
	}

	private void repack() {
	    tabs.indpack();
	    kin.c = new Coord(0, tabs.curtab.contentsz().y + UI.scale(20));
	    pol.c = new Coord(kin.c.x + kin.sz.x + UI.scale(10), kin.c.y);
	    pol2.c = new Coord(pol.c.x + pol.sz.x + UI.scale(10), pol.c.y);
	    this.pack();
	}

	Tabs.Tab ntab(Widget ch, TButton btn) {
	    Tabs.Tab tab = add(tabs.new Tab() {
		    public void cresize(Widget ch) {
			repack();
		    }
		}, tabs.c);
	    tab.add(ch, Coord.z);
	    btn.tab = tab;
	    repack();
	    return(tab);
	}

	void dtab(TButton btn) {
	    btn.tab.destroy();
	    btn.tab = null;
	    repack();
	}

	void addpol(Polity p) {
	    /* This isn't very nice. :( */
	    TButton btn = p.cap.equals("Village")?pol:pol2;
	    ntab(p, btn);
	    btn.tooltip = Text.render(p.cap);
	}
    }

    static class DraggedItem {
	final GItem item;
	final Coord dc;

	DraggedItem(GItem item, Coord dc) {
	    this.item = item; this.dc = dc;
	}
    }

    private void updhand() {
	if((hand.isEmpty() && (vhand != null)) || ((vhand != null) && !hand.contains(vhand.item))) {
	    ui.destroy(vhand);
	    vhand = null;
	}
	if(!hand.isEmpty() && (vhand == null)) {
	    DraggedItem fi = hand.iterator().next();
	    vhand = add(new ItemDrag(fi.dc, fi.item));
	}
	try {
	    MoonBulkStation.onHandUpdated(this);
	} catch(Exception ignored) {
	}
    }

    private String mapfilename() {
	StringBuilder buf = new StringBuilder();
	buf.append(genus);
	String chrid = Utils.getpref("mapfile/" + this.chrid, "");
	if(!chrid.equals("")) {
	    if(buf.length() > 0) buf.append('/');
	    buf.append(chrid);
	}
	return(buf.toString());
    }

    public Coord optplacement(Widget child, Coord org) {
	if(org == null)
	    org = Coord.z;
	/* Windows can arrive with sz (0,0) before layout; overlap math becomes NaN and opt stays null. */
	if(child == null || child.sz.x < 1 || child.sz.y < 1)
	    return(org);
	Set<Window> closed = new HashSet<>();
	Set<Coord> open = new HashSet<>();
	open.add(org);
	Coord opt = null;
	double optscore = Double.NEGATIVE_INFINITY;
	Coord plc = null;
	{
	    Gob pl = map.player();
	    if(pl != null) {
		Coord3f raw = pl.placed.getc();
		if(raw != null)
		    plc = map.screenxf(raw).round2();
	    }
	}
	Area parea = Area.sized(Coord.z, sz);
	while(!open.isEmpty()) {
	    Coord cur = Utils.take(open);
	    double score = 0;
	    Area tarea = Area.sized(cur, child.sz);
	    if(parea.isects(tarea)) {
		double outside = 1.0 - (((double)parea.overlap(tarea).area()) / ((double)tarea.area()));
		if((outside > 0.75) && !cur.equals(org))
		    continue;
		score -= Math.pow(outside, 2) * 100;
	    } else {
		if(!cur.equals(org))
		    continue;
		score -= 100;
	    }
	    {
		boolean any = false;
		for(Widget wdg = this.child; wdg != null; wdg = wdg.next) {
		    if(!(wdg instanceof Window))
			continue;
		    Window wnd = (Window)wdg;
		    if(!wnd.visible())
			continue;
		    Area warea = wnd.parentarea(this);
		    if(warea.isects(tarea)) {
			any = true;
			score -= ((double)warea.overlap(tarea).area()) / ((double)tarea.area());
			if(!closed.contains(wnd)) {
			    open.add(new Coord(wnd.c.x - child.sz.x, cur.y));
			    open.add(new Coord(cur.x, wnd.c.y - child.sz.y));
			    open.add(new Coord(wnd.c.x + wnd.sz.x, cur.y));
			    open.add(new Coord(cur.x, wnd.c.y + wnd.sz.y));
			    closed.add(wnd);
			}
		    }
		}
		if(!any)
		    score += 10;
	    }
	    if(plc != null) {
		if(tarea.contains(plc))
		    score -= 100;
		else
		    score -= (1 - Math.pow(tarea.closest(plc).dist(plc) / sz.dist(Coord.z), 0.5)) * 1.5;
	    }
	    score -= (cur.dist(org) / sz.dist(Coord.z)) * 0.75;
	    if(score > optscore) {
		optscore = score;
		opt = cur;
	    }
	}
	return((opt != null) ? opt : org);
    }

    /**
     * Write chat + minimap layout keys (wndc/wpos/wndsz/wlock). Does not flush.
     * {@link #keepinside} moves widgets without calling {@link haven.sloth.gui.MovableWidget#savePosition};
     * call this after any clamp so prefs match on-disk position.
     */
    private void syncChatMinimapLayoutPrefs() {
	if(chatwnd != null) {
	    Utils.setprefc("wndc-chat", chatwnd.c);
	    Utils.setprefc("wpos-chat", chatwnd.c);
	    Utils.setprefc("wndsz-chat", chatwnd.csz());
	    Utils.setprefb("wlock-chat", chatwnd.locked());
	}
	if(mmapwnd != null) {
	    Utils.setprefc("wpos-moon-minimap", mmapwnd.c);
	    Utils.setprefc("wndsz-moon-minimap", mmapwnd.sz);
	    Utils.setprefb("wlock-moon-minimap", mmapwnd.locked());
	}
	if(moonHandsToolbar != null) {
	    Utils.setprefc("wpos-moon-hands", moonHandsToolbar.c);
	    Utils.setprefc("wndsz-moon-hands", moonHandsToolbar.sz);
	    Utils.setprefb("wlock-moon-hands", moonHandsToolbar.locked());
	}
	if(moonSpeedWirePanel != null) {
	    Utils.setprefc("wpos-moon-speed-wire", moonSpeedWirePanel.c);
	    Utils.setprefc("wndsz-moon-speed-wire", moonSpeedWirePanel.sz);
	    Utils.setprefb("wlock-moon-speed-wire", moonSpeedWirePanel.locked());
	}
	if(nkeyBelt != null) {
	    Utils.setprefc("wpos-moon-belt-n", nkeyBelt.c);
	    Utils.setprefb("wlock-moon-belt-n", nkeyBelt.locked());
	}
	if(fkeyBelt != null) {
	    Utils.setprefc("wpos-moon-belt-f", fkeyBelt.c);
	    Utils.setprefb("wlock-moon-belt-f", fkeyBelt.locked());
	}
	if(numBelt != null) {
	    Utils.setprefc("wpos-moon-belt-num", numBelt.c);
	    Utils.setprefb("wlock-moon-belt-num", numBelt.locked());
	}
    }

    /** Persist chat + minimap layout and flush prefs (logout, periodic save). */
    private void persistChatAndMinimapLayout() {
	syncChatMinimapLayoutPrefs();
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {}
    }

    public void savewndpos() {
	if(invwnd != null)
	    Utils.setprefc("wndc-inv", invwnd.c);
	if(equwnd != null)
	    Utils.setprefc("wndc-equ", equwnd.c);
	if(chrwdg != null)
	    Utils.setprefc("wndc-chr", chrwdg.c);
	if(zerg != null)
	    Utils.setprefc("wndc-zerg", zerg.c);
	if(opts != null)
	    Utils.setprefc("wndc-opts", opts.c);
	if(sesswnd != null)
	    Utils.setprefc("wndc-sessions", sesswnd.c);
	if(mapfile != null) {
	    Utils.setprefc("wndc-map", mapfile.c);
	    Utils.setprefc("wndsz-map", mapfile.csz());
	}
	for(int i = 0; i < meters.size(); i++) {
	    Widget meter = meters.get(i);
	    if(meter != null) {
		Utils.setprefc(String.format("wpos-meter-%d", i), meter.c);
	    }
	}
	persistChatAndMinimapLayout();
    }

    public int meterIndex(Widget meter) {
	return(meters.indexOf(meter));
    }

    /**
     * Add a client-side {@link IMeter} (e.g. FEP) like server {@code place == "meter"} widgets:
     * stacked in the meter grid and listed in {@link #savewndpos}.
     */
    public void addClientMeter(IMeter meter) {
	int idx = meters.size();
	Coord pos = Utils.getprefc(String.format("wpos-meter-%d", idx), null);
	if(pos == null)
	    pos = defaultmeterc(idx);
	meter.setPersistIndex(idx);
	add(meter, pos);
	meters.add(meter);
    }

    private final BMap<String, Window> wndids = new HashBMap<String, Window>();

    /** Server id for misc windows (belt, keyring, …), used by {@link MoonMisc}. */
    public String miscWndIdFor(Window w) {
	return(wndids.reverse().get(w));
    }

    public void addchild(Widget child, Object... args) {
	String place = ((String)args[0]).intern();
	if(place == "mapview") {
	    child.resize(sz);
	    map = add((MapView)child, Coord.z);
	    map.lower();
	    if(mmapwnd != null) {
		ui.destroy(mmapwnd);
		mmapwnd = null;
		mmap = null;
	    } else if(mmap != null) {
		ui.destroy(mmap);
		mmap = null;
	    }
	    if(mapfile != null) {
		ui.destroy(mapfile);
		mapfile = null;
	    }
	    ResCache mapstore = ResCache.global;
	    if(MapFile.mapbase.get() != null)
		mapstore = HashDirCache.get(MapFile.mapbase.get());
	    if(mapstore != null) {
		MapFile file;
		try {
		    file = MapFile.load(mapstore, mapfilename());
		} catch(java.io.IOException e) {
		    new Warning(e, "failed to load mapfile; using empty map cache for this session").issue();
		    file = new MapFile(mapstore, mapfilename());
		}
		mmap = new CornerMap(UI.scale(new Coord(176, 176)), file);
		mmapwnd = add(new MiniMapWidget((CornerMap)mmap), Coord.z);
		mmapwnd.attachMapMenu(new MapMenu());
		if(Utils.getprefc("wpos-moon-minimap", null) == null) {
		    mmapwnd.move(defaultmmapc());
		    Utils.setprefc("wpos-moon-minimap", mmapwnd.c);
		}
		setminimapvisible(Utils.getprefb("moon-minimap-visible", true));
		mapfile = new MapWnd(file, map, Utils.getprefc("wndsz-map", UI.scale(new Coord(900, 620))), LocalizationManager.tr("map.title"));
		mapfile.show(Utils.getprefb("wndvis-map", false));
		add(mapfile, Utils.getprefc("wndc-map", new Coord(50, 50)));
	    }
	    raiseHudOverlay();
	} else if(place == "menu") {
	    menu = (MenuGrid)child;
	    /* Action search stays on Z / moon-tools bar; crafting itself is routed into the advanced browser. */
	    /* Icon: shared MenuGrid placeholder tile — res paths are ignored. */
	    MenuGrid.SpecialPagina macros = menu.registerSpecial("moon.macros", LocalizationManager.tr("menu.macros"), null, null);
	    if(macros != null)
		macros.forceRoot();
	    menu.registerSpecial("moon.bots", LocalizationManager.tr("menu.bots"), null, iact -> toggleMoonBotsWindow()).forceParent(macros);
	    menu.registerSpecial("moon.tpnav", LocalizationManager.tr("menu.tpnav"), null, iact -> toggleMoonTeleportWindow()).forceParent(macros);
	    menu.registerSpecial("moon.guide", LocalizationManager.tr("menu.guide"), null, iact -> toggleMoonUiGuideWindow()).forceParent(macros);
	    menu.registerSpecial("moon.storage", "Storage Search", null, iact -> toggleMoonStorageSearchWindow()).forceParent(macros);
	    menu.registerSpecial("moon.caveflat", LocalizationManager.tr("menu.caveflat"), null, iact -> {
		    MoonConfig.setHideCaveWalls(!MoonConfig.hideCaveWalls);
		    if(map != null && map.glob != null && map.glob.map != null)
			map.glob.map.invalidateAll();
		    ui.msg(LocalizationManager.tr(MoonConfig.hideCaveWalls ? "msg.caveflat.on" : "msg.caveflat.off"));
		}).forceParent(macros);
	    menu.registerSpecial("moon.safedig", LocalizationManager.tr("menu.safedig"), null, iact -> {
		    MoonConfig.setMineSupportSafeTiles(!MoonConfig.mineSupportSafeTiles);
		    ui.msg(LocalizationManager.tr(MoonConfig.mineSupportSafeTiles ? "msg.safedig.on" : "msg.safedig.off"));
		}).forceParent(macros);
	    menu.registerSpecial("moon.combathud", LocalizationManager.tr("menu.combathud"), null, iact -> {
		    MoonConfig.setCombatDamageHud(!MoonConfig.combatDamageHud);
		    ui.msg(LocalizationManager.tr(MoonConfig.combatDamageHud ? "msg.combathud.on" : "msg.combathud.off"));
		}).forceParent(macros);
	    menu.registerSpecial("moon.resettaken", LocalizationManager.tr("menu.resettaken"), null, iact -> {
		    MoonFightHud.resetTakenDamage();
		    ui.msg(LocalizationManager.tr("msg.dmg.reset"));
		}).forceParent(macros);
	    menu.registerSpecial("moon.passivegate", LocalizationManager.tr("menu.passivegate"), null, iact -> {
		    MoonConfig.setPassiveSmallGate(!MoonConfig.passiveSmallGate);
		    ui.msg(LocalizationManager.tr(MoonConfig.passiveSmallGate ? "msg.passivegate.on" : "msg.passivegate.off"));
		}).forceParent(macros);
	    menugridwnd = add(new MenuGridWnd(menu),
		Utils.getprefc("wpos-moon-menugrid",
		    sz.sub(UI.scale(200), UI.scale(200))));
	    initQuickPanels();
	    refreshAllBeltSlots();
	    clampMoonHudPanelsNow();
	    raiseHudOverlay();
	} else if(place == "fight") {
	    fv = add((Fightview)child, Utils.getprefc("wpos-fightview", defaultfightc((Fightview)child)));
	} else if(place == "fsess") {
	    add(child, Coord.z);
	} else if(place == "inv") {
	    maininv = (Inventory)child;
	    invwnd = new MoonInvWnd(maininv);
	    invwnd.hide();
	    add(invwnd, Utils.getprefc("wndc-inv", new Coord(100, 100)));
	} else if(place == "equ") {
	    equwnd = new MoonEquWnd((Equipory)child);
	    equwnd.hide();
	    add(equwnd, Utils.getprefc("wndc-equ", new Coord(400, 10)));
	} else if(place == "hand") {
	    GItem g = add((GItem)child);
	    Coord lc = (Coord)args[1];
	    hand.add(new DraggedItem(g, lc));
	    updhand();
	} else if(place == "chr") {
	    chrwdg = add((CharWnd)child, Utils.getprefc("wndc-chr", new Coord(300, 50)));
	    chrwdg.hide();
	} else if(place == "craft") {
	    Widget mkwdg = child;
	    String cap = LocalizationManager.tr("shortcut.crafting");
	    if(makewnd == null) {
		makewnd = new Window(Coord.z, cap, true) {
		    public void wdgmsg(Widget sender, String msg, Object... args) {
			if((sender == this) && msg.equals("close")) {
			    if(makecontent != null)
				makecontent.wdgmsg("close");
			    return;
			}
			super.wdgmsg(sender, msg, args);
		    }
		    public void cdestroy(Widget w) {
			if(w == makecontent) {
			    makecontent = null;
			    ui.destroy(this);
			    makewnd = null;
			}
		    }
		    public void destroy() {
			Utils.setprefc("makewndc", makewndc = this.c);
			super.destroy();
		    }
		};
		add(makewnd, makewndc);
	    }
	    Widget old = makecontent;
	    makecontent = null;
	    if(old != null)
		ui.destroy(old);
	    makecontent = mkwdg;
	    makewnd.add(mkwdg, Coord.z);
	    Coord craftAnchor = makewnd.c;
	    makewnd.pack();
	    makewnd.c = craftAnchor;
	    keepCraftWndInside();
	    makewndc = makewnd.c;
	    if(!makewnd.visible())
		makewnd.show();
	    setfocus(makewnd);
	    makewnd.raise();
	} else if(place == "buddy") {
	    zerg.ntab(buddies = (BuddyWnd)child, zerg.kin);
	} else if(place == "pol") {
	    Polity p = (Polity)child;
	    polities.add(p);
	    zerg.addpol(p);
	} else if(place == "chat") {
	    chat.addchild(child);
	} else if(place == "party") {
	    partywnd = (Partyview)child;
	    add(partywnd, Utils.getprefc("wpos-partyview", defaultpartyc(partywnd)));
	} else if(place == "meter") {
	    int idx = meters.size();
	    Coord pos = Utils.getprefc(String.format("wpos-meter-%d", idx), null);
	    if(pos == null)
		pos = defaultmeterc(idx);
	    if(child instanceof IMeter) {
		((IMeter)child).setPersistIndex(idx);
	    }
	    add(child, pos);
	    meters.add(child);
	} else if(place == "buff") {
	    buffs.addchild(child);
    } else if(place == "qq") {
	    if(qqview != null)
		qqview.reqdestroy();
	    final Widget cref = qqview = child;
	    MoonPanel qp = new MoonPanel(Coord.z, "moon-quest", LocalizationManager.tr("hud.quest.title")) {
		    { add(cref, contentOffset()); }

		    public void cresize(Widget ch) {
			if(ch == cref) {
			    resizeContent(cref.sz);
			}
		    }

		    public void cdestroy(Widget ch) {
			if(ch == cref) {
			    qqview = null;
			    destroy();
			}
		    }
		};
	    qp.setShowLock(true);
	    qp.setResizable(false);
	    Coord qdef = new Coord(UI.scale(10), GameUI.this.sz.y - UI.scale(200));
	    add(qp, Utils.getprefc("wpos-moon-quest", qdef));
	} else if(place == "misc") {
	    Coord c;
	    String miscMoonWndId = null;
	    long miscMoonGobId = -1L;
	    int a = 1;
	    if(args[a] instanceof Coord) {
		c = (Coord)args[a++];
	    } else if(args[a] instanceof Coord2d) {
		c = ((Coord2d)args[a++]).mul(new Coord2d(this.sz.sub(child.sz))).round();
		c = optplacement(child, c);
	    } else if(args[a] instanceof String) {
		c = relpos((String)args[a++], child, (args.length > a) ? ((Object[])args[a++]) : new Object[] {}, 0);
	    } else {
		throw(new UI.UIException("Illegal gameui child", place, args));
	    }
	    while(a < args.length) {
		Object opt = args[a++];
		if(opt instanceof Object[]) {
		    Object[] opta = (Object[])opt;
		    switch((String)opta[0]) {
		    case "id":
			String wndid = (String)opta[1];
			miscMoonWndId = wndid;
			if(child instanceof Window) {
			    c = Utils.getprefc(String.format("wndc-misc/%s", (String)opta[1]), c);
			    if(!wndids.containsKey(wndid)) {
				c = fitwdg(child, c);
				wndids.put(wndid, (Window)child);
			    } else {
				c = optplacement(child, c);
			    }
			}
			break;
		    case "obj":
			if(child instanceof Window) {
			    miscMoonGobId = Utils.uiv(opta[1]);
			    ((Window)child).settrans(new GobTrans(map, miscMoonGobId));
			}
			break;
		    }
		}
	    }
	    if(c == null)
		c = fitwdg(child, new Coord(Math.max(0, sz.x / 2 - child.sz.x / 2), UI.scale(80)));
	    add(child, c);
	    if(child instanceof Window) {
		Window mw = (Window)child;
		MoonStorage.registerWindow(this, mw, miscMoonWndId, miscMoonGobId);
		MoonMisc.applyIfEligible(mw, miscMoonWndId);
		/* Keep chests/cabinets above menu/minimap so grids stay visible. */
		mw.raise();
	    }
	    /* Inventory/title may attach after the window — rescan so belt/keyring pick up MooNWide deco. */
	    MoonMisc.refreshGameUIMiscWindows(this);
	} else if(place == "abt") {
	    add(child, Coord.z);
	} else {
	    throw(new UI.UIException("Illegal gameui child", place, args));
	}
    }

    public static class GobTrans implements Window.Transition<GobTrans.Anim, GobTrans.Anim> {
	public static final double time = 0.1;
	public final MapView map;
	public final long gobid;

	public GobTrans(MapView map, long gobid) {
	    this.map = map;
	    this.gobid = gobid;
	}

	private Coord oc() {
	    Gob gob = map.ui.sess.glob.oc.getgob(gobid);
	    if(gob == null)
		return(null);
	    haven.render.Location.Chain loc = Utils.el(gob.getloc());
	    if(loc == null)
		return(null);
	    return(map.screenxf(loc.fin(Matrix4f.id).mul4(Coord3f.o).invy()).round2());
	}

	public class Anim extends Window.NormAnim {
	    public final Window wnd;
	    private Coord oc;

	    public Anim(Window wnd, boolean hide, Anim from) {
		super(time, from, hide);
		this.wnd = wnd;
		Coord wc = (wnd.c != null) ? wnd.c : Coord.z;
		this.oc = wc.add(wnd.sz.div(2));
	    }

	    public void draw(GOut g, Tex tex) {
		Coord wp = (wnd.c != null) ? wnd.c : Coord.z;
		GOut pg = g.reclipl(wp.inv(), wnd.parent.sz);
		Coord cur = oc();
		if(cur != null)
		    this.oc = cur;
		Coord sz = tex.sz();
		double na = Utils.smoothstep(this.na);
		pg.chcolor(255, 255, 255, (int)(na * 255));
		double fac = 1.0 - na;
		Coord c = this.oc.sub(sz.div(2)).mul(1.0 - na).add(wp.mul(na));
		pg.image(tex, c.add((int)(sz.x * fac * 0.5), (int)(sz.y * fac * 0.5)),
			 Coord.of((int)(sz.x * (1.0 - fac)), (int)(sz.y * (1.0 - fac))));
	    }
	}

	public Anim show(Window wnd, Anim hide) {return(new Anim(wnd, false, hide));}
	public Anim hide(Window wnd, Anim show) {return(new Anim(wnd, true,  show));}
    }

    public void cdestroy(Widget w) {
	if(w instanceof Window) {
	    MoonStorage.onWindowClosed((Window)w);
	    String wndid = wndids.reverse().get((Window)w);
	    if(wndid != null) {
		wndids.remove(wndid);
		Utils.setprefc(String.format("wndc-misc/%s", wndid), w.c);
	    }
	}
	if(w instanceof GItem) {
	    for(Iterator<DraggedItem> i = hand.iterator(); i.hasNext();) {
		DraggedItem di = i.next();
		if(di.item == w) {
		    i.remove();
		    updhand();
		}
	    }
	} else if(polities.contains(w)) {
	    polities.remove(w);
	    zerg.dtab(zerg.pol);
	} else if(w == fepmeter) {
	    fepmeter = null;
	} else if(w == chrwdg) {
	    chrwdg = null;
	    if(fepmeter != null) {
		meters.remove(fepmeter);
		ui.destroy(fepmeter);
		fepmeter = null;
	    }
	} else if(w == moonStorageSearchWnd) {
	    Utils.setprefc("wndc-moon-storage-search", w.c);
	    moonStorageSearchWnd = null;
	} else if(w == partywnd) {
	    partywnd = null;
	}
	meters.remove(w);
    }

    /** Get list of meters by name (e.g. "stam", "hp", "nrj"). */
    public List<IMeter.Meter> getmeters(String name) {
	for (Widget meter : meters) {
	    if (meter instanceof IMeter) {
		IMeter im = (IMeter) meter;
		try {
		    Resource res = im.bg.get();
		    if (res != null && res.basename().equals(name))
			return im.meters;
		} catch (Loading e) {
		}
	    }
	}
	return null;
    }

    /** Get single meter by name and index. */
    public IMeter.Meter getmeter(String name, int midx) {
	List<IMeter.Meter> list = getmeters(name);
	if (list != null && midx < list.size())
	    return list.get(midx);
	return null;
    }

    public static class Progress extends Widget {
	private static final Resource.Anim progt = Resource.local().loadwait("gfx/hud/prog").layer(Resource.animc);
	public double prog;
	private TexI curi;

	public Progress(double prog) {
	    super(progt.f[0][0].ssz);
	    set(prog);
	}

	public void set(double prog) {
	    int fr = Utils.clip((int)Math.floor(prog * progt.f.length), 0, progt.f.length - 2);
	    int bf = Utils.clip((int)(((prog * progt.f.length) - fr) * 255), 0, 255);
	    WritableRaster buf = PUtils.imgraster(progt.f[fr][0].ssz);
	    PUtils.blit(buf, progt.f[fr][0].scaled().getRaster(), Coord.z);
	    PUtils.blendblit(buf, progt.f[fr + 1][0].scaled().getRaster(), Coord.z, bf);
	    if(this.curi != null)
		this.curi.dispose();
	    this.curi = new TexI(PUtils.rasterimg(buf));

	    double d = Math.abs(prog - this.prog);
	    int dec = Math.max(0, (int)Math.round(-Math.log10(d)) - 2);
	    this.tooltip = String.format("%." + dec + "f%%", prog * 100);
	    this.prog = prog;
	}

	public void draw(GOut g) {
	    g.image(curi, Coord.z);
	}

	public boolean checkhit(Coord c) {
	    return(Utils.checkhit(curi.back, c, 10));
	}
    }

    public void draw(GOut g) {
	super.draw(g);
	int by = sz.y;
	boolean chatanchored = (chatwnd != null) && chatwnd.visible();
	if(chatwnd != null && chatwnd.visible())
	    by = Math.min(by, chatwnd.c.y);
	if(nkeyBelt != null && nkeyBelt.visible())
	    by = Math.min(by, nkeyBelt.c.y);
	int msgx = CHAT_LEFT + UI.scale(10);
	if(cmdline != null) {
	    drawcmd(g, new Coord(msgx, by -= UI.scale(20)));
	} else if(lastmsg != null) {
	    if(LocalizationManager.autoTranslateChatRefresh() && lastmsgRaw != null
		&& LocalizationManager.autoTranslateUiGenerationSampled() != lastmsgTrGen) {
		lastmsgTrGen = LocalizationManager.autoTranslateUiGenerationSampled();
		MoonPerfOverlay.countTranslateRefresh();
		lastmsg.dispose();
		MoonPerfOverlay.countTextRender();
		lastmsg = RootWidget.msgfoundry.render(LocalizationManager.autoTranslateProcessed(lastmsgRaw), lastmsgCol);
	    }
	    if((Utils.rtime() - msgtime) > 3.0) {
		lastmsg = null;
		lastmsgRaw = null;
	    } else {
		Coord msgc;
		if(chatanchored) {
		    msgc = new Coord(chatwnd.c.x + UI.scale(10),
			Math.max(UI.scale(20), chatwnd.c.y - UI.scale(10) - lastmsg.sz().y));
		} else {
		    msgc = new Coord(msgx, Math.max(UI.scale(20), by - UI.scale(10) - lastmsg.sz().y));
		}
		g.chcolor(0, 0, 0, 192);
		g.frect(msgc.sub(UI.scale(2, 2)), lastmsg.sz().add(UI.scale(4, 4)));
		g.chcolor();
		g.image(lastmsg.tex(), msgc);
	    }
	}
	if(chat != null) {
	    Coord notifbr = new Coord(msgx, by);
	    if(chatwnd != null) {
		notifbr = new Coord(chatwnd.c.x + chatwnd.sz.x - UI.scale(10), Math.max(UI.scale(24), chatwnd.c.y - UI.scale(8)));
	    }
	    chat.drawsmall(g, notifbr, UI.scale(120));
	}
	MoonPerfOverlay.draw(this, g);
	drawWorldEntryOverlay(g);
    }
    
    private String iconconfname() {
	StringBuilder buf = new StringBuilder();
	buf.append("data/mm-icons");
	if(genus != null)
	    buf.append("/" + genus);
	if(ui.sess != null)
	    buf.append("/" + ui.sess.user.prsname());
	return(buf.toString());
    }

    private GobIcon.Settings loadiconconf() {
	String nm = iconconfname();
	try {
	    return(GobIcon.Settings.load(ui, nm));
	} catch(Exception e) {
	    new Warning(e, "could not load icon-conf").issue();
	}
	return(new GobIcon.Settings(ui, nm));
    }

    public class CornerMap extends MiniMap implements Console.Directory {
	private static final int CORNER_MAP_MIN_ZOOM_LEVEL = -4;
	private static final int CORNER_MAP_MAX_ZOOM_LEVEL = 8;

	public CornerMap(Coord sz, MapFile file) {
	    super(sz, file);
	    follow(new MapLocator(map));
	}

	public boolean dragp(int button) {
	    return(false);
	}

	public boolean clickmarker(DisplayMarker mark, Location loc, int button, boolean press) {
	    if(TeleportManager.handleArmedMapPick(GameUI.this, this, loc, button, press))
		return(true);
	    if(mark.m instanceof MapFile.SMarker) {
		Gob gob = MarkerID.find(ui.sess.glob.oc, (MapFile.SMarker)mark.m);
		if(gob != null)
		    mvclick(map, null, loc, gob, button);
	    }
	    return(false);
	}

	public boolean clickicon(DisplayIcon icon, Location loc, int button, boolean press) {
	    if(TeleportManager.handleArmedMapPick(GameUI.this, this, loc, button, press))
		return(true);
	    if(press) {
		mvclick(map, null, loc, icon.gob, button);
		return(true);
	    }
	    return(false);
	}

	public boolean clickloc(Location loc, int button, boolean press) {
	    if(TeleportManager.handleArmedMapPick(GameUI.this, this, loc, button, press))
		return(true);
	    if(press) {
		mvclick(map, null, loc, null, button);
		return(true);
	    }
	    return(false);
	}

	public void draw(GOut g) {
	    g.chcolor(18, 12, 30, 255);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    super.draw(g);
	}

	protected int maxZoomLevel() {
	    return(CORNER_MAP_MAX_ZOOM_LEVEL);
	}

	protected int minZoomLevel() {
	    return(CORNER_MAP_MIN_ZOOM_LEVEL);
	}

	protected boolean allowzoomout() {
	    /* Corner map still gets its own cap so accidental wheel spam does not push zoom too far out. */
	    if(zoomlevel >= maxZoomLevel())
		return(false);
	    return(super.allowzoomout());
	}
	private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
	{
	    cmdmap.put("rmseg", new Console.Command() {
		    public void run(Console cons, String[] args) {
			MiniMap.Location loc = curloc;
			if(loc != null) {
			    try(Locked lk = new Locked(file.lock.writeLock())) {
				file.segments.remove(loc.seg.id);
			    }
			}
		    }
		});
	}
	public Map<String, Console.Command> findcmds() {
	    return(cmdmap);
	}
    }

    private Coord lastsavegrid = null;
    private int lastsaveseq = -1;
    private void mapfiletick() {
	MapView map = this.map;
	MiniMap mmap = this.mmap;
	if((map == null) || (mmap == null))
	    return;
	Gob pl = ui.sess.glob.oc.getgob(map.plgob);
	Coord gc;
	if(pl == null)
	    gc = map.cc.floor(MCache.tilesz).div(MCache.cmaps);
	else
	    gc = pl.rc.floor(MCache.tilesz).div(MCache.cmaps);
	try {
	    MCache.Grid grid = ui.sess.glob.map.getgrid(gc);
	    if((grid != null) && (!Utils.eq(gc, lastsavegrid) || (lastsaveseq != grid.seq))) {
		mmap.file.update(ui.sess.glob.map, gc);
		lastsavegrid = gc;
		lastsaveseq = grid.seq;
	    }
	} catch(Loading l) {
	}
    }

    private double lastwndsave = 0;
    private double moonMiscRecheck = 0;
    private double moonHudHealAt = 0;
    private boolean moonGuideDefer;
    public void tick(double dt) {
	super.tick(dt);
	MoonStorage.tick(this);
	if(!moonGuideDefer && menu != null) {
	    moonGuideDefer = true;
	    if(!Utils.getprefb(MoonUiGuideWnd.GUIDE_SEEN_PREF, false)) {
		ui.loader.defer(() -> {
		    if(!Utils.getprefb(MoonUiGuideWnd.GUIDE_SEEN_PREF, false) && moonUiGuideWnd == null)
			toggleMoonUiGuideWindow();
		}, null);
	    }
	}
	double now = Utils.rtime();
	if(now >= moonHudHealAt) {
	    moonHudHealAt = now + 1.0;
	    if(moonNeedsHudHeal())
		ensureMoonHudPanels();
	}
	updateWorldEntryState(dt, now);
	boolean sceneScan = moonSceneScanDue(now);
	boolean hudRefresh = moonHudRefreshDue(now);
	moonMiscRecheck += dt;
	if(moonMiscRecheck >= 1.0) {
	    moonMiscRecheck = 0;
	    try {
		MoonMisc.refreshGameUIMiscWindows(this);
	    } catch(Exception ignored) {}
	}
	if(now - lastwndsave > 60) {
	    savewndpos();
	    lastwndsave = now;
	}
	double idle = now - ui.lastevent;
	if(!afk && (idle > 300)) {
	    afk = true;
	    wdgmsg("afk");
	} else if(afk && (idle <= 300)) {
	    afk = false;
	}
	mapfiletick();
	MoonAutomationRegistry.tickModule("mine", this, () -> MoonMineSweeperTracker.tick(this, dt));
	MoonAutomationRegistry.tickModule("autodrink", this, () -> MoonAutoDrink.tick(this, dt));
	MoonAutomationRegistry.tickModule("combat", this, () -> MoonCombatBot.tick(this, dt));
	MoonAutomationRegistry.tickModule("tree", this, () -> MoonTreeChopBot.tick(this, dt));
	MoonAutomationRegistry.tickModule("autodrop", this, () -> MoonAutoDrop.tick(this, dt));
	MoonAutomationRegistry.tickModule("mine", this, () -> MoonMineBot.tick(this, dt));
	MoonAutomationRegistry.tickModule("fishing", this, () -> MoonFishingBot.tick(this, dt));
	MoonAutomationRegistry.tickModule("native", this, () -> haven.botnative.HavenBotNative.tick(this, dt));
	MoonAutomationRegistry.tickModule("bulk", this, () -> MoonBulkStation.tick(this, Utils.rtime()));
	try { MoonSpeedBoost.tick(this, dt); } catch(Exception ignored) {}
	try { MoonSpeedBoost.tickSpeedMultResend(this, Utils.rtime()); } catch(Exception ignored) {}
	try { MoonSafeMode.tick(this, dt); } catch(Exception ignored) {}
	if(sceneScan) {
	    try { if(map != null) MoonXRay.tick(map); } catch(Exception ignored) {}
	    try { if(map != null) MoonEntityHitboxViz.tick(map); } catch(Exception ignored) {}
	    try { if(map != null) MoonHitboxMode.tick(map); } catch(Exception ignored) {}
	    try { if(map != null) MoonCropMode.tick(map); } catch(Exception ignored) {}
	    if(MoonConfig.gfxModEspOverlay)
		try { if(map != null) MoonEspHighlight.tick(map); } catch(Exception ignored) {}
	    try { MoonPlayerAlerts.tick(this, dt); } catch(Exception ignored) {}
	}
	if(hudRefresh) {
	    try { if(map != null) MoonMiningOverlay.tickSupportHpCache(map); } catch(Exception ignored) {}
	    if(MoonConfig.gfxModActivityHud)
		try { MoonActivityHud.tick(this); } catch(Exception ignored) {}
	}
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "err") {
	    String err = (String)args[0];
	    ui.error(err);
	} else if(msg == "msg") {
	    String text = (String)args[0];
	    ui.msg(text);
	} else if(msg == "prog") {
	    if(args.length > 0) {
		double p = Utils.dv(args[0]) / 100.0;
		if(worldEntryOverlayArmed && !worldEntryRevealed) {
		    worldEntrySawProg = true;
		    worldEntryGate = true;
		    worldEntryReleaseAt = -1.0;
		}
		if(prog == null)
		    prog = adda(new Progress(p), 0.5, 0.35);
		else
		    prog.set(p);
	    } else {
		if(prog != null) {
		    prog.reqdestroy();
		    prog = null;
		}
		if(worldEntryOverlayArmed && !worldEntryRevealed)
		    worldEntryReleaseAt = -1.0;
	    }
	} else if(msg == "setbelt") {
	    int slot = Utils.iv(args[0]);
	    if(args.length < 2) {
		setBaseBeltSlot(slot, null);
	    } else {
		Indir<Resource> res = ui.sess.getresv(args[1]);
		Message sdt = Message.nil;
		if(args.length > 2)
		    sdt = new MessageBuf((byte[])args[2]);
		ResData rdt = new ResData(res, sdt);
		ui.sess.glob.loader.defer(() -> {
			setBaseBeltSlot(slot, mkbeltslot(slot, rdt));
		    }, null);
	    }
	} else if(msg == "setbelt2") {
	    int slot = Utils.iv(args[0]);
	    if(args.length < 2) {
		setBaseBeltSlot(slot, null);
	    } else {
		switch((String)args[1]) {
		case "p": {
		    Object id = args[2];
		    setBaseBeltSlot(slot, new PagBeltSlot(slot, menu.paginafor(id, null)));
		    break;
		}
		case "r": {
		    Indir<Resource> res = ui.sess.getresv(args[2]);
		    ui.sess.glob.loader.defer(() -> {
			    setBaseBeltSlot(slot, new PagBeltSlot(slot, PagBeltSlot.resolve(menu, res)));
			}, null);
		    break;
		}
		case "d": {
		    Indir<Resource> res = ui.sess.getresv(args[2]);
		    Message sdt = Message.nil;
		    if(args.length > 2)
			sdt = new MessageBuf((byte[])args[3]);
		    setBaseBeltSlot(slot, new ResBeltSlot(slot, new ResData(res, sdt)));
		    break;
		}
		}
	    }
	} else if(msg == "polowner") {
	    int id = Utils.iv(args[0]);
	    String o = (String)args[1];
	    boolean n = Utils.bv(args[2]);
	    if(o != null)
		o = o.intern();
	    String cur = polowners.get(id);
	    if(map != null) {
		if((o != null) && (cur == null)) {
		    if(n)
			map.setpoltext(id, "Entering " + o);
		} else if((o == null) && (cur != null)) {
		    map.setpoltext(id, "Leaving " + cur);
		}
	    }
	    polowners.put(id, o);
	} else if(msg == "showhelp") {
	    Indir<Resource> res = ui.sess.getresv(args[0]);
	    if(help == null)
		help = adda(new HelpWnd(res), 0.5, 0.25);
	    else
		help.res = res;
	} else if(msg == "map-mark") {
	    long gobid = Utils.uiv(args[0]);
	    long oid = ((Number)args[1]).longValue();
	    Indir<Resource> res = ui.sess.getresv(args[2]);
	    String nm = (String)args[3];
	    if(mapfile != null)
		mapfile.markobj(gobid, oid, res, nm);
	} else if(msg == "map-icons") {
	    GobIcon.Settings conf = this.iconconf;
	    int tag = Utils.iv(args[0]);
	    if(args.length < 2) {
		if(conf.tag != tag)
		    wdgmsg("map-icons", conf.tag);
	    } else {
		conf.receive(args);
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if((sender == chrwdg) && (msg == "close")) {
	    chrwdg.hide();
	    return;
	} else if((sender == mapfile) && (msg == "close")) {
	    mapfile.hide();
	    Utils.setprefb("wndvis-map", false);
	    return;
	} else if((sender == help) && (msg == "close")) {
	    ui.destroy(help);
	    help = null;
	    return;
	} else if((sender == srchwnd) && (msg == "close")) {
	    ui.destroy(srchwnd);
	    srchwnd = null;
	    return;
	} else if((sender == iconwnd) && (msg == "close")) {
	    ui.destroy(iconwnd);
	    iconwnd = null;
	    Utils.setprefb("moon-mapmenu-icon-open", false);
	    try {
		Utils.prefs().flush();
	    } catch(Exception ignored) {}
	    return;
	}
	super.wdgmsg(sender, msg, args);
    }

    private static final int fitmarg = UI.scale(100);
    /** Package-visible for MooNWide windows ({@link MoonBotsWnd}, etc.). */
    Coord fitwdg(Widget wdg, Coord c) {
	Coord ret = new Coord(c);
	ret.x = Math.max(ret.x, Math.min(0, fitmarg - wdg.sz.x));
	ret.y = Math.max(ret.y, Math.min(0, fitmarg - wdg.sz.y));
	ret.x = Math.min(ret.x, sz.x - Math.min(fitmarg, wdg.sz.x));
	ret.y = Math.min(ret.y, sz.y - Math.min(fitmarg, wdg.sz.y));
	return(ret);
    }

    void fitwdg(Widget wdg) {
	wdg.c = fitwdg(wdg, wdg.c);
    }

    /** Resolves {@link Equipory} from the equipment window, if present. */
    public Equipory findEquipory() {
	if(equwnd == null)
	    return(null);
	return(equwnd.findchild(Equipory.class));
    }

    /** Для {@link Session#crashTest}: {@link ISBox} понимает {@code xfer} как Shift+клик. */
    private static ISBox findDescIsbox(Widget w) {
	if(w == null)
	    return(null);
	if(w instanceof ISBox)
	    return((ISBox)w);
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    ISBox r = findDescIsbox(ch);
	    if(r != null)
		return(r);
	}
	return(null);
    }

    private void keepinside(Widget wdg) {
	if(wdg == null)
	    return;
	wdg.c = Coord.of(Math.max(0, Math.min(wdg.c.x, Math.max(0, sz.x - wdg.sz.x))),
			 Math.max(0, Math.min(wdg.c.y, Math.max(0, sz.y - wdg.sz.y))));
    }

    /**
     * Craft shell: avoid {@link #fitwdg} on every server refresh — its margin math can yank the window when
     * {@code sz} is in flux. Only clamp if still on-screen after {@link Window#pack}.
     */
    private void keepCraftWndInside() {
	if(makewnd == null || makewnd.sz.x < 1 || makewnd.sz.y < 1)
	    return;
	keepinside(makewnd);
    }

    private boolean wndstate(Window wnd) {
	if(wnd == null)
	    return(false);
	return(wnd.visible());
    }

    private void togglewnd(Window wnd) {
	if(wnd != null) {
	    if(wnd.show(!wnd.visible())) {
		wnd.raise();
		fitwdg(wnd);
		setfocus(wnd);
	    }
	}
    }

    private void setchatvisible(boolean show, boolean focus) {
	if(chatwnd == null)
	    return;
	chatwnd.show(show);
	chat.targetshow = show;
	if(show) {
	    chatwnd.raise();
	    if(!chatwnd.locked())
		keepinside(chatwnd);
	    syncChatMinimapLayoutPrefs();
	    if(focus)
		setfocus(chat);
	}
	Utils.setprefb("chatvis", show);
    }

    private void togglechat() {
	if(chatwnd != null)
	    setchatvisible(!chatwnd.visible(), true);
    }

    private void showoptpanel(Consumer<OptWnd> open) {
	if((opts != null) && (open != null)) {
	    opts.show();
	    opts.raise();
	    open.accept(opts);
	    fitwdg(opts);
	    setfocus(opts);
	}
    }

    void toggleInventoryWindow() {
	if(invwnd != null) {
	    if(invwnd.show(!invwnd.visible())) {
		invwnd.raise();
		fitwdg(invwnd);
		setfocus(invwnd);
	    }
	}
    }

    void toggleEquipmentWindow() {
	if(equwnd != null) {
	    if(equwnd.show(!equwnd.visible())) {
		equwnd.raise();
		fitwdg(equwnd);
		setfocus(equwnd);
	    }
	}
    }

    void toggleCharacterWindow() {
	if(chrwdg != null) {
	    if(chrwdg.show(!chrwdg.visible())) {
		chrwdg.raise();
		fitwdg(chrwdg);
		setfocus(chrwdg);
	    }
	}
    }

    void toggleKinWindow() {
	if(zerg != null) {
	    if(zerg.show(!zerg.visible())) {
		zerg.raise();
		fitwdg(zerg);
		setfocus(zerg);
	    }
	}
    }

    void toggleActionSearch() {
	togglesearch();
    }

    void toggleCraftWindow() {
	if(makewnd == null) {
	    Widget browser = new Makewindow("");
	    String cap = LocalizationManager.tr("shortcut.crafting");
	    makewnd = new Window(Coord.z, cap, true) {
		public void wdgmsg(Widget sender, String msg, Object... args) {
		    if((sender == this) && msg.equals("close")) {
			if(makecontent != null)
			    makecontent.wdgmsg("close");
			return;
		    }
		    super.wdgmsg(sender, msg, args);
		}
		public void cdestroy(Widget w) {
		    if(w == makecontent) {
			makecontent = null;
			ui.destroy(this);
			makewnd = null;
		    }
		}
		public void destroy() {
		    Utils.setprefc("makewndc", makewndc = this.c);
		    super.destroy();
		}
	    };
	    makecontent = browser;
	    makewnd.add(browser, Coord.z);
	    makewnd.pack();
	    add(makewnd, makewndc);
	    keepCraftWndInside();
	    makewnd.raise();
	    setfocus(makewnd);
	    return;
	}
	if(!makewnd.visible())
	    makewnd.show();
	makewnd.raise();
	keepCraftWndInside();
	setfocus(makewnd);
    }

    void toggleOptionsWindow() {
	if(opts == null)
	    return;
	if(opts.visible())
	    opts.hide();
	else
	    showoptpanel(OptWnd::showMainPanel);
    }

    void toggleChatWindow() {
	togglechat();
    }

    void toggleMinimapWindow() {
	toggleminimap();
    }

    void toggleMapWindow() {
	togglewnd(mapfile);
	if(mapfile != null)
	    Utils.setprefb("wndvis-map", mapfile.visible());
    }

    private void ensureSessionSwitcher() {
	if(sesswnd != null)
	    return;
	sesswnd = add(new MoonSessionWnd(this), Utils.getprefc("wndc-sessions", UI.scale(new Coord(220, 36))));
	fitwdg(sesswnd);
    }

    /** Clears cached quality overlay textures on every {@link WItem} under this UI (options / inventory changes). */
    public void reloadAllItemQualityOverlays() {
	reloadItemQualityOverlaysRec(this);
    }

    private static void reloadItemQualityOverlaysRec(Widget w) {
	if(w instanceof WItem)
	    ((WItem)w).clearInventoryQualityOverlayCache();
	for(Widget ch : w.children())
	    reloadItemQualityOverlaysRec(ch);
    }

    /** Saved-account session switch; Steam users can also open it to spawn blank sessions. */
    public void toggleSessionSwitcher() {
	ensureSessionSwitcher();
	sesswnd.raise();
	setfocus(sesswnd);
    }

    void openAutomationPanel() {
	showoptpanel(OptWnd::showAutomationPanel);
    }

    /** Opens or focuses {@link MoonCombatBotWnd} (card AI / legacy bot settings). */
    public void openMoonCombatBotWindow() {
	if(moonCombatBotWnd != null) {
	    moonCombatBotWnd.show();
	    moonCombatBotWnd.raise();
	    setfocus(moonCombatBotWnd);
	    return;
	}
	MoonCombatBotWnd w = new MoonCombatBotWnd(this);
	Coord c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(100)));
	moonCombatBotWnd = add(w, c);
	fitwdg(moonCombatBotWnd);
	moonCombatBotWnd.raise();
	setfocus(moonCombatBotWnd);
    }

    public void openMoonTreeBotWindow() {
	if(moonTreeBotWnd != null) {
	    moonTreeBotWnd.raise();
	    setfocus(moonTreeBotWnd);
	    return;
	}
	MoonTreeBotWnd w = new MoonTreeBotWnd(this);
	Coord c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(90)));
	moonTreeBotWnd = add(w, c);
	fitwdg(moonTreeBotWnd);
	moonTreeBotWnd.raise();
	setfocus(moonTreeBotWnd);
    }

    /** Opens or toggles the bots window (combat bot + tree chop). */
    public void toggleMoonBotsWindow() {
	if(moonBotsWnd != null) {
	    if(moonBotsWnd.visible()) {
		moonBotsWnd.hide();
		return;
	    }
	    moonBotsWnd.show();
	    moonBotsWnd.raise();
	    setfocus(moonBotsWnd);
	    return;
	}
	MoonBotsWnd w = new MoonBotsWnd(this);
	Coord c = Utils.getprefc("wndc-moon-bots", null);
	if(c == null)
	    c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(80)));
	else
	    c = fitwdg(w, c);
	moonBotsWnd = add(w, c);
	fitwdg(moonBotsWnd);
	moonBotsWnd.raise();
	setfocus(moonBotsWnd);
    }

    /** Opens or focuses the mine / cave sweeper bot window. */
    public void openMoonMineBotWindow() {
	if(moonMineBotWnd != null) {
	    moonMineBotWnd.raise();
	    setfocus(moonMineBotWnd);
	    return;
	}
	MoonMineBotWnd w = new MoonMineBotWnd(this);
	Coord c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(90)));
	moonMineBotWnd = add(w, c);
	fitwdg(moonMineBotWnd);
	moonMineBotWnd.raise();
	setfocus(moonMineBotWnd);
    }

    public void openMoonAutoDropWindow() {
	if(moonAutoDropWnd != null) {
	    moonAutoDropWnd.raise();
	    setfocus(moonAutoDropWnd);
	    return;
	}
	MoonAutoDropWnd w = new MoonAutoDropWnd(this);
	Coord c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(110)));
	moonAutoDropWnd = add(w, c);
	fitwdg(moonAutoDropWnd);
	moonAutoDropWnd.raise();
	setfocus(moonAutoDropWnd);
    }

    public void openMoonFishingBotWindow() {
	if(moonFishingBotWnd != null) {
	    moonFishingBotWnd.raise();
	    setfocus(moonFishingBotWnd);
	    return;
	}
	MoonFishingBotWnd w = new MoonFishingBotWnd(this);
	Coord c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(120)));
	moonFishingBotWnd = add(w, c);
	fitwdg(moonFishingBotWnd);
	moonFishingBotWnd.raise();
	setfocus(moonFishingBotWnd);
    }

    public void openMoonAutomationToolsWindow() {
	if(moonAutomationToolsWnd != null) {
	    moonAutomationToolsWnd.raise();
	    setfocus(moonAutomationToolsWnd);
	    return;
	}
	MoonAutomationToolsWnd w = new MoonAutomationToolsWnd(this);
	Coord c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(90)));
	moonAutomationToolsWnd = add(w, c);
	fitwdg(moonAutomationToolsWnd);
	moonAutomationToolsWnd.raise();
	setfocus(moonAutomationToolsWnd);
    }

    /** Opens or toggles saved navigation points ({@link TeleportManager}). */
    public void toggleMoonTeleportWindow() {
	if(moonTeleportWnd != null) {
	    if(moonTeleportWnd.visible()) {
		moonTeleportWnd.hide();
		return;
	    }
	    moonTeleportWnd.show();
	    moonTeleportWnd.raise();
	    setfocus(moonTeleportWnd);
	    return;
	}
	MoonTeleportWnd w = new MoonTeleportWnd(this);
	Coord c = Utils.getprefc("wndc-moon-tpnav", null);
	if(c == null)
	    c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(70)));
	else
	    c = fitwdg(w, c);
	moonTeleportWnd = add(w, c);
	fitwdg(moonTeleportWnd);
	moonTeleportWnd.raise();
	setfocus(moonTeleportWnd);
    }

    /** Opens or toggles the global storage cache search window. */
    public void toggleMoonStorageSearchWindow() {
	if(moonStorageSearchWnd != null) {
	    if(moonStorageSearchWnd.visible()) {
		Utils.setprefc("wndc-moon-storage-search", moonStorageSearchWnd.c);
		moonStorageSearchWnd.hide();
		return;
	    }
	    moonStorageSearchWnd.show();
	    moonStorageSearchWnd.raise();
	    moonStorageSearchWnd.focusQuery();
	    setfocus(moonStorageSearchWnd);
	    return;
	}
	MoonStorageSearchWnd w = new MoonStorageSearchWnd(this);
	Coord c = Utils.getprefc("wndc-moon-storage-search", null);
	if(c == null)
	    c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(90)));
	else
	    c = fitwdg(w, c);
	moonStorageSearchWnd = add(w, c);
	fitwdg(moonStorageSearchWnd);
	moonStorageSearchWnd.raise();
	moonStorageSearchWnd.focusQuery();
	setfocus(moonStorageSearchWnd);
    }

    /** Opens or toggles the MooNWide interface guide (floating help). */
    public void toggleMoonUiGuideWindow() {
	if(moonUiGuideWnd != null) {
	    if(moonUiGuideWnd.visible()) {
		moonUiGuideWnd.hide();
		return;
	    }
	    moonUiGuideWnd.show();
	    moonUiGuideWnd.raise();
	    setfocus(moonUiGuideWnd);
	    return;
	}
	MoonUiGuideWnd w = new MoonUiGuideWnd(this);
	Coord c = Utils.getprefc("wpos-moon-ui-guide", null);
	if(c == null)
	    c = fitwdg(w, new Coord(sz.x / 2 - w.sz.x / 2, UI.scale(50)));
	else
	    c = fitwdg(w, c);
	moonUiGuideWnd = add(w, c);
	fitwdg(moonUiGuideWnd);
	moonUiGuideWnd.raise();
	setfocus(moonUiGuideWnd);
    }

    void openOverlayPanel() {
	showoptpanel(OptWnd::showOverlayPanel);
    }

    void openCombatPanel() {
	showoptpanel(OptWnd::showCombatPanel);
    }

    void openSpeedPanel() {
	showoptpanel(OptWnd::showSpeedPanel);
    }

    private void initQuickPanels() {
	if(gameShortcuts == null) {
	    gameShortcuts = new MoonQuickPanel("moon-game-shortcuts");
	    gameShortcuts.addButton(LocalizationManager.tr("shortcut.inv"), "custom/paginae/default/wnd/inv",
		iact -> toggleInventoryWindow());
	    gameShortcuts.addButton(LocalizationManager.tr("shortcut.equ"), "custom/paginae/default/wnd/equ",
		iact -> toggleEquipmentWindow());
	    gameShortcuts.addButton(LocalizationManager.tr("shortcut.chr"), "custom/paginae/default/wnd/char",
		iact -> toggleCharacterWindow());
	    gameShortcuts.addButton(LocalizationManager.tr("shortcut.kin"), "custom/paginae/default/wnd/kithnkin",
		iact -> toggleKinWindow());
	    gameShortcuts.addButton(LocalizationManager.tr("shortcut.map"), "custom/paginae/default/wnd/smap",
		iact -> toggleMinimapWindow());
	    gameShortcuts.addButton(LocalizationManager.tr("shortcut.bigmap"), "custom/paginae/default/wnd/lmap",
		iact -> toggleMapWindow());
	    add(gameShortcuts, Utils.getprefc("wpos-moon-game-shortcuts",
		Coord.of(sz.x - UI.scale(320), sz.y - UI.scale(60))));
	}
	if(moonTools == null) {
	    moonTools = new MoonQuickPanel("moon-tools");
	    moonTools.addButton(LocalizationManager.tr("shortcut.chat"), "custom/paginae/default/wnd/chat",
		iact -> toggleChatWindow());
	    moonTools.addButton(LocalizationManager.tr("shortcut.search"), "custom/paginae/default/wnd/search",
		iact -> toggleActionSearch());
	    moonTools.addButton(LocalizationManager.tr("shortcut.overlay"), "custom/paginae/default/wnd/builderwindow",
		iact -> openOverlayPanel());
	    moonTools.addButton(LocalizationManager.tr("shortcut.combat"), "custom/paginae/default/wnd/fakegrid",
		iact -> openCombatPanel());
	    moonTools.addButton(LocalizationManager.tr("shortcut.combatbot"), "custom/paginae/default/wnd/fakegrid",
		iact -> openMoonCombatBotWindow());
	    moonTools.addButton(LocalizationManager.tr("shortcut.opts"), "custom/paginae/default/wnd/opts",
		iact -> toggleOptionsWindow());
	    add(moonTools, Utils.getprefc("wpos-moon-tools",
		Coord.of(sz.x - UI.scale(320), sz.y - UI.scale(110))));
	}
	if(moonHandsToolbar == null) {
	    moonHandsToolbar = add(new MoonHandsToolbar(this), Utils.getprefc("wpos-moon-hands",
		UI.scale(new Coord(12, 200))));
	}
	if(moonSpeedWirePanel == null) {
	    moonSpeedWirePanel = add(new MoonSpeedWirePanel(), Utils.getprefc("wpos-moon-speed-wire",
		UI.scale(new Coord(12, 360))));
	}
	if(uimode != 2) {
	    gameShortcuts.show();
	    moonTools.show();
	    moonHandsToolbar.show();
	}
	if(moonSpeedWirePanel != null) {
	    if((uimode != 2) && Utils.getprefb("moon-speed-wire-visible", true))
		moonSpeedWirePanel.show();
	    else
		moonSpeedWirePanel.hide();
	}
	if(LoginScreen.authmech.get().equals("native"))
	    ensureSessionSwitcher();
    }

    private boolean clampHudWidget(Widget wdg) {
	if(!(wdg instanceof MovableWidget))
	    return(false);
	return(((MovableWidget)wdg).clampToParentBounds());
    }

    private void clampMoonHudPanelsNow() {
	boolean changed = false;
	changed = clampHudWidget(menugridwnd) || changed;
	changed = clampHudWidget(gameShortcuts) || changed;
	changed = clampHudWidget(moonTools) || changed;
	if((moonHandsToolbar != null) && !moonHandsToolbar.locked())
	    changed = clampHudWidget(moonHandsToolbar) || changed;
	if((moonSpeedWirePanel != null) && !moonSpeedWirePanel.locked())
	    changed = clampHudWidget(moonSpeedWirePanel) || changed;
	if(changed)
	    raiseHudOverlay();
    }

    private boolean moonNeedsHudHeal() {
	if(gameShortcuts == null || moonTools == null || moonHandsToolbar == null || moonSpeedWirePanel == null)
	    return(true);
	return((menu != null) && (menugridwnd == null));
    }

    private void ensureMoonHudPanels() {
	boolean changed = false;
	initQuickPanels();
	if((menu != null) && (menugridwnd == null)) {
	    menugridwnd = add(new MenuGridWnd(menu),
		Utils.getprefc("wpos-moon-menugrid",
		    sz.sub(UI.scale(200), UI.scale(200))));
	    changed = true;
	}
	if(uimode != 2) {
	    if(menugridwnd != null && !menugridwnd.visible()) {
		menugridwnd.show();
		changed = true;
	    }
	    if(nkeyBelt != null && !nkeyBelt.visible()) {
		nkeyBelt.show();
		changed = true;
	    }
	    if(fkeyBelt != null) {
		boolean want = Utils.getprefb("moon-belt-f-vis", false);
		if(fkeyBelt.visible() != want) {
		    fkeyBelt.show(want);
		    changed = true;
		}
	    }
	    if(numBelt != null) {
		boolean want = Utils.getprefb("moon-belt-num-vis", false);
		if(numBelt.visible() != want) {
		    numBelt.show(want);
		    changed = true;
		}
	    }
	} else {
	    if(menugridwnd != null && menugridwnd.visible()) {
		menugridwnd.hide();
		changed = true;
	    }
	    if(nkeyBelt != null && nkeyBelt.visible()) {
		nkeyBelt.hide();
		changed = true;
	    }
	    if(fkeyBelt != null && fkeyBelt.visible()) {
		fkeyBelt.hide();
		changed = true;
	    }
	    if(numBelt != null && numBelt.visible()) {
		numBelt.hide();
		changed = true;
	    }
	}
	if(changed)
	    raiseHudOverlay();
	clampMoonHudPanelsNow();
    }

    /** Show / hide {@link #moonSpeedWirePanel} and save visibility pref. */
    public void toggleMoonSpeedWirePanel() {
	if(moonSpeedWirePanel == null)
	    return;
	boolean show = !moonSpeedWirePanel.visible();
	if(show) {
	    moonSpeedWirePanel.show();
	    moonSpeedWirePanel.raise();
	} else {
	    moonSpeedWirePanel.hide();
	}
	Utils.setprefb("moon-speed-wire-visible", show);
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {
	}
	if(ui != null)
	    ui.msg(LocalizationManager.tr(show ? "speedwire.panel.shown" : "speedwire.panel.hidden"),
		java.awt.Color.WHITE, null);
    }

    void openPerformancePanel() {
	showoptpanel(OptWnd::showPerformancePanel);
    }

    public static class MenuButton extends IButton {
	MenuButton(String base, KeyBinding gkey, String tooltip) {
	    super("gfx/hud/" + base, "", "-d", "-h");
	    setgkey(gkey);
	    settip(tooltip);
	}
    }

    public static class MenuCheckBox extends HudStrip.CheckBox {
	MenuCheckBox(String base, KeyBinding gkey, String tooltip) {
	    super("gfx/hud/" + base, "", "-d", "-h", "-dh");
	    setgkey(gkey);
	    settip(tooltip);
	}
    }

    public static final KeyBinding kb_inv = KeyBinding.get("inv", KeyMatch.forcode(KeyEvent.VK_TAB, 0));
    public static final KeyBinding kb_equ = KeyBinding.get("equ", KeyMatch.forchar('E', KeyMatch.C));
    public static final KeyBinding kb_chr = KeyBinding.get("chr", KeyMatch.forchar('T', KeyMatch.C));
    public static final KeyBinding kb_bud = KeyBinding.get("bud", KeyMatch.forchar('B', KeyMatch.C));
    public static final KeyBinding kb_opt = KeyBinding.get("opt", KeyMatch.forchar('O', KeyMatch.C));
    public static final KeyBinding kb_mw_bot = KeyBinding.get("mw-bot", KeyMatch.forchar('B', KeyMatch.M));
    public static final KeyBinding kb_mw_tpnav = KeyBinding.get("mw-tpnav", KeyMatch.nil);
    public static final KeyBinding kb_mw_rec = KeyBinding.get("mw-rec", KeyMatch.forchar('R', KeyMatch.M));
    public static final KeyBinding kb_mw_view = KeyBinding.get("mw-view", KeyMatch.forchar('V', KeyMatch.M));
    public static final KeyBinding kb_mw_map = KeyBinding.get("mw-map", KeyMatch.forchar('M', KeyMatch.M));
    public static final KeyBinding kb_mw_fight = KeyBinding.get("mw-fight", KeyMatch.forchar('F', KeyMatch.M));
    public static final KeyBinding kb_mw_chat = KeyBinding.get("mw-chat", KeyMatch.forchar('C', KeyMatch.M));
    public static final KeyBinding kb_mw_perf = KeyBinding.get("mw-perf", KeyMatch.forchar('P', KeyMatch.M));
    public static final KeyBinding kb_mw_sys = KeyBinding.get("mw-sys", KeyMatch.forchar('O', KeyMatch.M));
    public static final KeyBinding kb_mw_expand = KeyBinding.get("mw-expand", KeyMatch.forchar('X', KeyMatch.M));
    public static final KeyBinding kb_mw_rotate = KeyBinding.get("mw-rotate", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.M));
    private static final Coord traypad = UI.scale(new Coord(6, 6));
    private static final Coord traybtnsz = UI.scale(new Coord(34, 20));
    private static final int traygap = UI.scale(4);
    private static final int traylockw = UI.scale(10);
    private static final int mmheaderh = UI.scale(16);
    private static final int mmpad = UI.scale(5);
    private static final int mmlockw = UI.scale(10);
    private static final Color traybg = new Color(9, 13, 12, 150);
    private static final Color trayfill = new Color(38, 57, 47, 180);
    private static final Color trayactive = new Color(92, 129, 97, 225);
    private static final Color traypressed = new Color(127, 168, 131, 235);
    private static final Color traytext = new Color(240, 236, 220);
    private static final Color trayaccent = new Color(245, 216, 150);
    private static final Text.Foundry trayf = new Text.Foundry(Text.sans.deriveFont(Font.BOLD, UI.scale(11f))).aa(true);

    public class CharacterPanel extends MovableWidget {
	public CharacterPanel() {
	    super("moon-charhud");
	}

	public void cresize(Widget ch) {
	    pack();
	}
    }

    public class MiniMapWidget extends MoonPanel {
	private final CornerMap mapview;
	private MapMenu mapmenu;
	private final Coord mapBaseSize;
	private final int menuPad = UI.scale(2);
	@Override
	protected java.awt.Color panelBodyColor() {
	    return new java.awt.Color(18, 12, 30, 255);
	}

	public MiniMapWidget(CornerMap view) {
	    super(Coord.z, "moon-minimap", LocalizationManager.tr("map.title"));
	    setChrome(true);
	    setResizable(true);
	    setMinSize(UI.scale(new Coord(100, 100)));
	    setShowLock(true);
	    this.mapview = view;
	    mapBaseSize = Coord.of(Math.max(UI.scale(176), view.sz.x), Math.max(UI.scale(176), view.sz.y));
	    resizeContent(Coord.of(mapBaseSize.x, mapBaseSize.y));
	    add(view, contentOffset());
	    mapview.resize(mapBaseSize);
	}

	public boolean ardMode() {
	    return false;
	}

	public void setArdMode(boolean ard) {
	    /* Removed: minimap always uses the standard MooNWide chrome. */
	}

	@Override
	protected boolean transparentChromeMode() {
	    return false;
	}

	@Override
	protected int actionStripHeight() {
	    return super.actionStripHeight();
	}

	@Override
	protected int headerHeight() {
	    return super.headerHeight();
	}

	@Override
	protected int dragBandHeight() {
	    return super.dragBandHeight();
	}

	/** Edge-to-edge map under the title bar (no side body padding) — removes empty bands left/right. */
	@Override
	public Coord wrapSize(Coord csz) {
	    if(!chrome)
		return(csz);
	    return(Coord.of(csz.x, csz.y + actionStripHeight() + headerHeight() + MoonPanel.PAD * 2));
	}

	@Override
	public Coord contentOffset() {
	    if(!chrome)
		return(Coord.z);
	    return(Coord.of(0, actionStripHeight() + headerHeight() + MoonPanel.PAD));
	}

	@Override
	public Coord contentSize() {
	    if(!chrome)
		return(sz);
	    return(Coord.of(
		Math.max(1, sz.x),
		Math.max(1, sz.y - actionStripHeight() - headerHeight() - MoonPanel.PAD * 2)));
	}

	public void attachMapMenu(MapMenu m) {
	    if(mapmenu != null) {
		ui.destroy(mapmenu);
		mapmenu = null;
	    }
	    this.mapmenu = m;
	    add(m, contentOffset());
	    layoutMapAndMenu();
	}

	/**
	 * {@link MoonPanel#loadSize()} and other {@code Widget.resize} paths update the outer
	 * frame but do not call {@link #onContentResize}; the map child would stay at the initial
	 * 176×176 and sit in the top-left with empty Moon body below/right.
	 */
	@Override
	public void resize(Coord sz) {
	    super.resize(sz);
	    layoutMapAndMenu();
	}

	private void layoutMapAndMenu() {
	    Coord co = contentOffset();
	    Coord cs = contentSize();
	    if(mapmenu != null) {
		mapmenu.relayout(cs.x);
		mapmenu.c = co;
		int mh = mapmenu.sz.y;
		mapview.c = co.add(0, mh + menuPad);
		/* Fill all remaining content height — fixed mapBaseSize left a huge empty MoonPanel body. */
		int availH = Math.max(UI.scale(100), cs.y - mh - menuPad);
		mapview.resize(Coord.of(cs.x, availH));
	    } else {
		mapview.c = co;
		mapview.resize(cs);
	    }
	}

	@Override
	protected void onContentResize(Coord csz) {
	    layoutMapAndMenu();
	}

	@Override
	protected void added() {
	    super.added();
	    layoutMapAndMenu();
	}
    }

    public static final KeyBinding kb_map = KeyBinding.get("map", KeyMatch.forchar('A', KeyMatch.C));
    public static final KeyBinding kb_claim = KeyBinding.get("ol-claim", KeyMatch.nil);
    public static final KeyBinding kb_vil = KeyBinding.get("ol-vil", KeyMatch.nil);
    public static final KeyBinding kb_rlm = KeyBinding.get("ol-rlm", KeyMatch.nil);
    public static final KeyBinding kb_ico = KeyBinding.get("map-icons", KeyMatch.nil);

    private static void saveMinimapTogglePref(String key, boolean v) {
	Utils.setprefb(key, v);
	try {
	    Utils.prefs().flush();
	} catch(Exception ignored) {}
    }

    /** Restore icon settings window if it was open last session. */
    void moonRestoreIconWndIfPref() {
	if(!Utils.getprefb("moon-mapmenu-icon-open", false))
	    return;
	if(iconconf == null || iconwnd != null)
	    return;
	iconwnd = new GobIcon.SettingsWindow(iconconf);
	fitwdg(add(iconwnd, Utils.getprefc("wndc-icon", new Coord(200, 200))));
    }

    /**
     * Minimap toolbar: no vanilla {@code gfx/hud/lbtn-*} art — compact Moon-styled toggles.
     */
    public class MapMenu extends Widget {
	private final int mmGap = UI.scale(2);
	private final int mmBtn = UI.scale(22);
	private MoonStripToggle claim, vil, rlm, mapbtn, icons, nav;

	/** Small text toggle matching {@link MoonPanel} chrome. */
	public class MoonStripToggle extends ACheckBox {
	    private final Text.Line label;
	    private boolean hover;

	    MoonStripToggle(String letter) {
		super(Coord.of(MapMenu.this.mmBtn, MapMenu.this.mmBtn));
		canactivate = true;
		this.label = Text.std.render(letter, MoonPanel.MOON_ACCENT);
	    }

	    @Override
	    public void draw(GOut g) {
		boolean on = state();
		g.chcolor(hover ? MoonPanel.MOON_HOVER : MoonPanel.MOON_HEADER2);
		g.frect(Coord.z, sz);
		g.chcolor(on ? MoonPanel.MOON_ACCENT : MoonPanel.MOON_BORDER);
		g.rect(Coord.z, sz);
		g.chcolor();
		g.aimage(label.tex(), sz.div(2), 0.5, 0.5);
	    }

	    @Override
	    public boolean mousedown(MouseDownEvent ev) {
		if(ev.b != 1)
		    return(false);
		click();
		return(true);
	    }

	    @Override
	    public void mousemove(MouseMoveEvent ev) {
		hover = ev.c.isect(Coord.z, sz);
	    }
	}

	private void toggleol(String tag, boolean a) {
	    if(map != null) {
		if(a)
		    map.enol(tag);
		else
		    map.disol(tag);
	    }
	}

	public MapMenu() {
	    super(Coord.z);
	    claim = add(new MoonStripToggle("C"));
	    claim.changed(a -> {
		    toggleol("cplot", a);
		    saveMinimapTogglePref("moon-mapmenu-cplot", a);
		});
	    claim.setgkey(kb_claim).settip(LocalizationManager.tr("map.tip.claim"));
	    vil = add(new MoonStripToggle("V"));
	    vil.changed(a -> {
		    toggleol("vlg", a);
		    saveMinimapTogglePref("moon-mapmenu-vlg", a);
		});
	    vil.setgkey(kb_vil).settip(LocalizationManager.tr("map.tip.vil"));
	    rlm = add(new MoonStripToggle("P"));
	    rlm.changed(a -> {
		    toggleol("prov", a);
		    saveMinimapTogglePref("moon-mapmenu-prov", a);
		});
	    rlm.setgkey(kb_rlm).settip(LocalizationManager.tr("map.tip.prov"));
	    mapbtn = add(new MoonStripToggle("M"));
	    mapbtn.state(() -> wndstate(mapfile)).click(() -> {
		    togglewnd(mapfile);
		    if(mapfile != null) {
			Utils.setprefb("wndvis-map", mapfile.visible());
			try {
			    Utils.prefs().flush();
			} catch(Exception ignored) {}
		    }
		});
	    mapbtn.setgkey(kb_map).settip(LocalizationManager.tr("map.tip.mapwnd"));
	    icons = add(new MoonStripToggle("I"));
	    icons.state(() -> wndstate(iconwnd)).click(() -> {
		    if(iconconf == null)
			return;
		    if(iconwnd == null) {
			iconwnd = new GobIcon.SettingsWindow(iconconf);
			fitwdg(GameUI.this.add(iconwnd, Utils.getprefc("wndc-icon", new Coord(200, 200))));
			saveMinimapTogglePref("moon-mapmenu-icon-open", true);
		    } else {
			ui.destroy(iconwnd);
			iconwnd = null;
			saveMinimapTogglePref("moon-mapmenu-icon-open", false);
		    }
		});
	    icons.setgkey(kb_ico).settip(LocalizationManager.tr("map.tip.icons"));
	    nav = add(new MoonStripToggle("N"));
	    nav.state(() -> ((moonTeleportWnd != null) && moonTeleportWnd.visible()) || TeleportManager.isMapPickArmed())
		.click(GameUI.this::toggleMoonTeleportWindow);
	    nav.settip(LocalizationManager.tr("map.tip.nav"));
	    relayout(UI.scale(176));
	    if(map != null) {
		if(Utils.getprefb("moon-mapmenu-cplot", false))
		    claim.set(true);
		if(Utils.getprefb("moon-mapmenu-vlg", false))
		    vil.set(true);
		if(Utils.getprefb("moon-mapmenu-prov", false))
		    rlm.set(true);
	    }
	    moonRestoreIconWndIfPref();
	}

	public void relayout(int width) {
	    MoonStripToggle[] row = {claim, vil, rlm, mapbtn, icons, nav};
	    int n = row.length;
	    int btnw = Math.max(mmBtn, (width - (n - 1) * mmGap) / n);
	    int x = 0;
	    for(MoonStripToggle w : row) {
		w.c = new Coord(x, UI.scale(2));
		w.resize(Coord.of(btnw, mmBtn));
		x += btnw + mmGap;
	    }
	    resize(Coord.of(width, mmBtn + UI.scale(4)));
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.propagate(this))
		return(true);
	    return(checkhit(ev.c));
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(ev.propagate(this))
		return(true);
	    return(checkhit(ev.c));
	}
    }

    public class MapMenuWnd extends MoonPanel {
	private final MapMenu menu;

	public MapMenuWnd(MapMenu menu) {
	    super(Coord.z, "moon-mapmenu", null);
	    setShowLock(true);
	    setResizable(false);
	    this.menu = add(menu, contentOffset());
	    resizeContent(menu.sz);
	}
    }

    public static final KeyBinding kb_shoot = KeyBinding.get("screenshot", KeyMatch.forchar('S', KeyMatch.M));
    public static final KeyBinding kb_chat = KeyBinding.get("chat-toggle", KeyMatch.forchar('C', KeyMatch.C));
    public static final KeyBinding kb_hide = KeyBinding.get("ui-toggle", KeyMatch.nil);
    public static final KeyBinding kb_logout = KeyBinding.get("logout", KeyMatch.nil);
    public static final KeyBinding kb_switchchr = KeyBinding.get("logout-cs", KeyMatch.nil);
    private static boolean matchesLayoutSafeHotkey(KeyBinding kb, GlobKeyEvent ev) {
	KeyMatch key = kb.key();
	if(key == null || key == KeyMatch.nil)
	    return(false);
	if(key.match(ev))
	    return(true);
	if((ev.mods & key.modmask) != key.modmatch)
	    return(false);
	int code = key.code;
	if((code == KeyEvent.VK_UNDEFINED) && (key.chr != 0))
	    code = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(key.chr));
	return((code != KeyEvent.VK_UNDEFINED) && (ev.code == code));
    }

    private static boolean matchesLayoutSafeHotkey(KeyBinding kb, GlobKeyEvent ev, int code, int mods) {
	if(matchesLayoutSafeHotkey(kb, ev))
	    return(true);
	if(ev.code != code)
	    return(false);
	int emods = (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M));
	return(emods == mods);
    }

    private boolean matchesOptionsHotkey(GlobKeyEvent ev) {
	if(matchesLayoutSafeHotkey(kb_opt, ev, KeyEvent.VK_O, KeyMatch.C))
	    return(true);
	/* Fallback by key-code to survive layout/prefs issues after restart.
	 * Accept both legacy Ctrl+O and reassigned plain O. */
	if(ev.code != KeyEvent.VK_O)
	    return(false);
	int mods = (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M));
	return((mods == KeyMatch.C) || (mods == 0));
    }

    private boolean handleReservedWindowHotkeys(GlobKeyEvent ev) {
	if(matchesLayoutSafeHotkey(kb_mw_bot, ev, KeyEvent.VK_B, KeyMatch.M)) {
	    toggleMoonBotsWindow();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_tpnav, ev)) {
	    toggleMoonTeleportWindow();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_rec, ev, KeyEvent.VK_R, KeyMatch.M)) {
	    togglesearch();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_view, ev, KeyEvent.VK_V, KeyMatch.M)) {
	    showoptpanel(OptWnd::showOverlayPanel);
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_map, ev, KeyEvent.VK_M, KeyMatch.M)) {
	    toggleminimap();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_fight, ev, KeyEvent.VK_F, KeyMatch.M)) {
	    showoptpanel(OptWnd::showCombatPanel);
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_chat, ev, KeyEvent.VK_C, KeyMatch.M)) {
	    togglechat();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_perf, ev, KeyEvent.VK_P, KeyMatch.M)) {
	    showoptpanel(OptWnd::showPerformancePanel);
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_mw_sys, ev, KeyEvent.VK_O, KeyMatch.M)) {
	    showoptpanel(OptWnd::showMainPanel);
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_chat, ev, KeyEvent.VK_C, KeyMatch.C)) {
	    if(chatwnd != null && chatwnd.visible() && !chat.hasfocus) {
		setfocus(chat);
	    } else {
		togglechat();
	    }
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_inv, ev, KeyEvent.VK_TAB, 0)) {
	    toggleInventoryWindow();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_map, ev, KeyEvent.VK_A, KeyMatch.C)) {
	    toggleMapWindow();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_equ, ev, KeyEvent.VK_E, KeyMatch.C)) {
	    toggleEquipmentWindow();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_chr, ev, KeyEvent.VK_T, KeyMatch.C)) {
	    toggleCharacterWindow();
	    return(true);
	}
	if(matchesLayoutSafeHotkey(kb_bud, ev, KeyEvent.VK_B, KeyMatch.C)) {
	    toggleKinWindow();
	    return(true);
	}
	if(matchesOptionsHotkey(ev)) {
	    toggleOptionsWindow();
	    return(true);
	}
	return(false);
    }

    public boolean globtype(GlobKeyEvent ev) {
	if(ev.c == ':') {
	    entercmd();
	    return(true);
	}
	if(handleReservedWindowHotkeys(ev))
	    return(true);
	/* When the action-menu window is open, pagina shortcuts may beat inv/chr/map toggles below. */
	if(MoonConfig.menuGridKeyboard && menugridwnd != null && menugridwnd.visible() && menugridwnd.tvisible()
	    && menu != null && menu.moonHandleScmKeys(ev))
	    return(true);
	/* Dev: «слепой» сбор по подстроке ресурса (Shift+B) / дамп подозрительных gob (Ctrl+Shift+B). */
	if(ev.code == KeyEvent.VK_B && (ev.mods & KeyMatch.S) != 0 && (ev.mods & KeyMatch.C) != 0
	    && (ev.mods & KeyMatch.M) == 0) {
	    try {
		MoonBlindCollect.dumpSuspiciousGobs(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonBlind] " + t);
	    }
	    return(true);
	}
	if(ev.code == KeyEvent.VK_B && (ev.mods & KeyMatch.S) != 0 && (ev.mods & KeyMatch.C) == 0
	    && (ev.mods & KeyMatch.M) == 0) {
	    try {
		MoonBlindCollect.blindCollect(GameUI.this, Utils.getpref("moon-blind-target", "herb"));
	    } catch(Throwable t) {
		System.err.println("[MoonBlind] " + t);
	    }
	    return(true);
	}
	/* Dev: xfer + мгновенный разрыв UDP (plain J) — {@link Session#crashTest}. */
	if(MoonNetworkProfiler.devtoolsEnabled()) {
	if(ev.code == KeyEvent.VK_J && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		if(ui.sess != null) {
		    ISBox box = findDescIsbox(GameUI.this);
		    Widget w = (box != null) ? box : maininv;
		    if(w != null) {
			int wid = ui.widgetid(w);
			if(wid >= 0)
			    ui.sess.crashTest(wid);
		    }
		}
	    } catch(Throwable t) {
		System.err.println("[Session.crashTest] " + t);
	    }
	    return(true);
	}
	/* Dev: agnostic sync — plain K run; Shift+K cycle mode; Ctrl+K ORIGINAL; Alt+K DIRTY; Ctrl+Shift+K DOUBLE. */
	if(ev.code == KeyEvent.VK_K) {
	    int km = ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M);
	    try {
		if(km == (KeyMatch.C | KeyMatch.S))
		    MoonNetworkProfiler.setAgnosticSyncMode(MoonNetworkProfiler.AGNOSTIC_MODE_DOUBLE_TAKE);
		else if(km == KeyMatch.C)
		    MoonNetworkProfiler.setAgnosticSyncMode(MoonNetworkProfiler.AGNOSTIC_MODE_ORIGINAL);
		else if(km == KeyMatch.M)
		    MoonNetworkProfiler.setAgnosticSyncMode(MoonNetworkProfiler.AGNOSTIC_MODE_DIRTY_CONTEXT);
		else if(km == KeyMatch.S)
		    MoonNetworkProfiler.cycleAgnosticSyncMode();
		else if(km == 0)
		    MoonNetworkProfiler.executeAgnosticSyncHotkey(GameUI.this);
		else
		    return(super.globtype(ev));
	    } catch(Throwable t) {
		System.err.println("[MoonNetworkProfiler] " + t);
	    }
	    return(true);
	}
	/* Dev: Burst-Shifter — серия transfer+focus+busy-wait (plain M) — {@link MoonInventoryStress#stressBurstShiftHotkey}. */
	if(ev.code == KeyEvent.VK_M && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		System.out.println("Burst-shift triggered!");
		MoonInventoryStress.stressBurstShiftHotkey(GameUI.this, -1);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Dev: tactic L transfer+drop (plain L) — {@link MoonInventoryStress#tacticsL}. */
	if(ev.code == KeyEvent.VK_L && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		System.out.println("Stress test triggered!");
		MoonInventoryStress.stressTransferDropHotkey(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Dev: tactic P split-transfer (plain P) — {@link MoonInventoryStress#tacticsP}. */
	if(ev.code == KeyEvent.VK_P && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		System.out.println("Stress test triggered!");
		MoonInventoryStress.stressSplitTransferHotkey(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Dev: tactic U 1 ms busy-wait (Shift+U) — {@link MoonInventoryStress#tacticsTransferHandSwapHotkey(GameUI, long)}. */
	if(ev.code == KeyEvent.VK_U && (ev.mods & KeyMatch.S) != 0 && (ev.mods & KeyMatch.C) == 0
	    && (ev.mods & KeyMatch.M) == 0) {
	    try {
		MoonInventoryStress.tacticsTransferHandSwapHotkey(GameUI.this, 1_000_000L);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Dev: race Transfer-Hand-Swap (plain U; pause {@link MoonInventoryStress#tacticsUPauseBetweenTransferAndTakeNs}). */
	if(ev.code == KeyEvent.VK_U && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		MoonInventoryStress.tacticsTransferHandSwapHotkey(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Dev: race Cross-Window Transfer (plain I) — {@link MoonInventoryStress#tacticsCrossWindowTransferHotkey}. */
	if(ev.code == KeyEvent.VK_I && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		MoonInventoryStress.tacticsCrossWindowTransferHotkey(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Dev: race Split-Join (Shift+O; plain O — переключатель опций) — {@link MoonInventoryStress#tacticsSplitJoinRaceHotkey}. */
	if(ev.code == KeyEvent.VK_O && (ev.mods & KeyMatch.S) != 0 && (ev.mods & KeyMatch.C) == 0
	    && (ev.mods & KeyMatch.M) == 0) {
	    try {
		MoonInventoryStress.tacticsSplitJoinRaceHotkey(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonInventoryStress] " + t);
	    }
	    return(true);
	}
	/* Smart interact: ближайший PLAYER/CONTAINER в радиусе — {@link MoonSmartInteract#interactNearestSmart}. */
	}
	if(ev.code == KeyEvent.VK_H && (ev.mods & (KeyMatch.S | KeyMatch.C | KeyMatch.M)) == 0) {
	    try {
		MoonSmartInteract.interactNearestSmart(GameUI.this);
	    } catch(Throwable t) {
		System.err.println("[MoonInteract] " + t);
	    }
	    return(true);
	}
	if(kb_shoot.key().match(ev) && (Screenshooter.screenurl.get() != null)) {
	    Screenshooter.take(this, Screenshooter.screenurl.get());
	    return(true);
	} else if(kb_hide.key().match(ev)) {
	    toggleui();
	    return(true);
	} else if(kb_logout.key().match(ev)) {
	    act("lo");
	    return(true);
	} else if(kb_switchchr.key().match(ev)) {
	    act("lo", "cs");
	    return(true);
	} else if((ev.c == 27) && (map != null) && !map.hasfocus) {
	    setfocus(map);
	    return(true);
	}
	return(super.globtype(ev));
    }

    private int uimode = 1;

    /**
     * Map is lowered behind the HUD. Belts are raised above most chrome so they stay usable; the
     * skill menu ({@link MenuGridWnd}) is then raised above belts so semi-opaque hotbars never paint
     * “mystery” icons over the grid; shortcut rows stay on top for click access.
     */
    private void raiseHudOverlay() {
	if(nkeyBelt != null)
	    nkeyBelt.raise();
	if(fkeyBelt != null)
	    fkeyBelt.raise();
	if(numBelt != null)
	    numBelt.raise();
	if(menugridwnd != null)
	    menugridwnd.raise();
	if(gameShortcuts != null)
	    gameShortcuts.raise();
	if(moonTools != null)
	    moonTools.raise();
	if(moonHandsToolbar != null)
	    moonHandsToolbar.raise();
	if(moonSpeedWirePanel != null && moonSpeedWirePanel.visible())
	    moonSpeedWirePanel.raise();
    }

    /**
     * {@link haven.sloth.gui.MovableWidget#raise()} and panel resize can move belts above the skill grid;
     * call this to put {@link #menugridwnd} back under shortcut bars but above hotbars.
     */
    public void restoreMoonHudZOrder() {
	raiseHudOverlay();
    }

    private void showMoonPanels(boolean show) {
	if(menugridwnd != null) { if(show) menugridwnd.show(); else menugridwnd.hide(); }
	if(nkeyBelt != null) { if(show) nkeyBelt.show(); else nkeyBelt.hide(); }
	if(fkeyBelt != null) { if(show) fkeyBelt.show(); else fkeyBelt.hide(); }
	if(numBelt != null) { if(show) numBelt.show(); else numBelt.hide(); }
	
	if(gameShortcuts != null) { if(show) gameShortcuts.show(); else gameShortcuts.hide(); }
	if(moonTools != null) { if(show) moonTools.show(); else moonTools.hide(); }
	if(moonHandsToolbar != null) { if(show) moonHandsToolbar.show(); else moonHandsToolbar.hide(); }
	if(moonSpeedWirePanel != null) {
	    if(show && Utils.getprefb("moon-speed-wire-visible", true))
		moonSpeedWirePanel.show();
	    else if(!show)
		moonSpeedWirePanel.hide();
	}
    }

    public void toggleui(int mode) {
	/* MooNWide: skip the vanilla "hide absolutely everything" step.
	 * With Moon panels replacing the old HUD, mode 2 leaves players in an almost empty world view. */
	if(mode == 2)
	    mode = 1;
	Hidepanel[] panels = {blpanel, ulpanel, umpanel, urpanel};
	switch(uimode = mode) {
	case 0:
	    for(Hidepanel p : panels)
		p.mshow(true);
	    if(charpanel != null)
		charpanel.show();
	    if(buffs != null)
		buffs.show();
	    if(fv != null)
		fv.show();
	    if(partywnd != null)
		partywnd.show();
	    for(Widget meter : meters)
		meter.show();
	    if((mmapwnd != null) && Utils.getprefb("moon-minimap-visible", true))
		mmapwnd.show();
	    if((chatwnd != null) && Utils.getprefb("chatvis", true))
		chatwnd.show();
	    showMoonPanels(true);
	    raiseHudOverlay();
	    break;
	case 1:
	    for(Hidepanel p : panels)
		p.mshow();
	    if(charpanel != null)
		charpanel.show();
	    if(buffs != null)
		buffs.show();
	    if(fv != null)
		fv.show();
	    if(partywnd != null)
		partywnd.show();
	    for(Widget meter : meters)
		meter.show();
	    if((mmapwnd != null) && Utils.getprefb("moon-minimap-visible", true))
		mmapwnd.show();
	    if((chatwnd != null) && Utils.getprefb("chatvis", true))
		chatwnd.show();
	    showMoonPanels(true);
	    raiseHudOverlay();
	    break;
	case 2:
	    for(Hidepanel p : panels)
		p.mshow(false);
	    if(charpanel != null)
		charpanel.hide();
	    if(buffs != null)
		buffs.hide();
	    if(fv != null)
		fv.hide();
	    if(partywnd != null)
		partywnd.hide();
	    for(Widget meter : meters)
		meter.hide();
	    if(mmapwnd != null)
		mmapwnd.hide();
	    if(chatwnd != null)
		chatwnd.hide();
	    showMoonPanels(false);
	    break;
	}
    }

    public void resetui() {
	Hidepanel[] panels = {blpanel, ulpanel, umpanel, urpanel};
	for(Hidepanel p : panels)
	    p.cshow(p.tvis);
	if(charpanel != null)
	    charpanel.show();
	if(buffs != null)
	    buffs.show();
	if(fv != null)
	    fv.show();
	if(partywnd != null)
	    partywnd.show();
	for(Widget meter : meters)
	    meter.show();
	if((mmapwnd != null) && Utils.getprefb("moon-minimap-visible", true))
	    mmapwnd.show();
	if((chatwnd != null) && Utils.getprefb("chatvis", true))
	    chatwnd.show();
	showMoonPanels(true);
	raiseHudOverlay();
	uimode = 1;
    }

    public void toggleui() {
	toggleui((uimode + 1) % 3);
    }

    /** Belt and small-chat at bottom-left when chat is in movable window. */
    private static final int CHAT_LEFT = UI.scale(20);

    private Coord defaultchatc() {
	int belth = (nkeyBelt != null) ? nkeyBelt.sz.y : UI.scale(50);
	int y = sz.y - belth - chatwnd.sz.y - UI.scale(18);
	return(new Coord(CHAT_LEFT, Math.max(UI.scale(90), y)));
    }

    private Coord defaultmmapc() {
	return(new Coord(Math.max(UI.scale(20), sz.x - mmapwnd.sz.x - UI.scale(24)), UI.scale(20)));
    }

    private Coord defaultmeterc(int idx) {
	int cols = 2;
	int gapx = UI.scale(5);
	int gapy = UI.scale(2);
	int x = charpanel.c.x + portrait.sz.x + UI.scale(10) + ((idx % cols) * (IMeter.fsz.x + gapx));
	int y = charpanel.c.y + ((idx / cols) * (IMeter.fsz.y + gapy));
	return(Coord.of(x, y));
    }

    private Coord defaultbuffc() {
	return(Coord.of(charpanel.c.x + portrait.sz.x + UI.scale(10),
			charpanel.c.y + (IMeter.fsz.y * 2) + UI.scale(10)));
    }

    private Coord defaultpartyc(Partyview pv) {
	return(Coord.of(charpanel.c.x, charpanel.c.y + portrait.sz.y + UI.scale(14)));
    }

    private Coord defaultfightc(Fightview fv) {
	return(Coord.of(Math.max(UI.scale(20), sz.x - fv.sz.x - UI.scale(24)), UI.scale(96)));
    }

    public boolean minimapvisible() {
	return((mmapwnd != null) && mmapwnd.visible());
    }

    public void setminimapvisible(boolean show) {
	if(mmapwnd != null) {
	    mmapwnd.show(show);
	    if(show) {
		mmapwnd.raise();
		if(!mmapwnd.locked())
		    keepinside(mmapwnd);
		syncChatMinimapLayoutPrefs();
	    }
	}
	Utils.setprefb("moon-minimap-visible", show);
    }

    private void toggleminimap() {
	if(mmapwnd != null)
	    setminimapvisible(!mmapwnd.visible());
    }

    public void resize(Coord sz) {
	super.resize(sz);
	ensureMoonHudPanels();
	if(map != null)
	    map.resize(sz);
	if(prog != null)
	    prog.move(sz.sub(prog.sz).mul(0.5, 0.35));
	if((chatwnd != null) && (Utils.getprefc("wndc-chat", null) == null)
	    && (Utils.getprefc("wpos-chat", null) == null)) {
	    chatwnd.c = defaultchatc();
	    Utils.setprefc("wndc-chat", chatwnd.c);
	}
	/* One-time layout migrations: only when no saved chat position (avoid clobbering user prefs). */
	if((chatwnd != null) && !Utils.getprefb("moon-chat-layout-v2", false)) {
	    if(Utils.getprefc("wndc-chat", null) == null && Utils.getprefc("wpos-chat", null) == null) {
		if(chatwnd.c.y < UI.scale(80))
		    chatwnd.c = defaultchatc();
		Utils.setprefc("wndc-chat", chatwnd.c);
	    }
	    Utils.setprefb("moon-chat-layout-v2", true);
	}
	if((chatwnd != null) && !Utils.getprefb("moon-chat-layout-v3", false)) {
	    if(Utils.getprefc("wndc-chat", null) == null && Utils.getprefc("wpos-chat", null) == null) {
		Coord defsz = UI.scale(new Coord(620, 220));
		Coord limsz = UI.scale(new Coord(720, 280));
		if((chatwnd.csz().x > limsz.x) || (chatwnd.csz().y > limsz.y))
		    chatwnd.resize(defsz);
		if(chatwnd.c.y < UI.scale(140))
		    chatwnd.c = defaultchatc();
		Utils.setprefc("wndc-chat", chatwnd.c);
		Utils.setprefc("wndsz-chat", chatwnd.csz());
		Utils.setprefb("chatvis", true);
		chatwnd.show();
	    }
	    Utils.setprefb("moon-chat-layout-v3", true);
	}
	if((mmapwnd != null) && (Utils.getprefc("wpos-moon-minimap", null) == null)) {
	    mmapwnd.move(defaultmmapc());
	    Utils.setprefc("wpos-moon-minimap", mmapwnd.c);
	}
	if((mmapwnd != null) && !Utils.getprefb("moon-minimap-layout-v2", false)) {
	    if(Utils.getprefc("wpos-moon-minimap", null) == null) {
		mmapwnd.move(defaultmmapc());
		Utils.setprefc("wpos-moon-minimap", mmapwnd.c);
	    }
	    Utils.setprefb("moon-minimap-visible", true);
	    mmapwnd.show();
	    Utils.setprefb("moon-minimap-layout-v2", true);
	}
	if(!Utils.getprefb("moon-hud-layout-v5", false)) {
	    /* Only fill defaults where prefs are missing — do not clobber saved positions. */
	    if((chatwnd != null) && (Utils.getprefc("wndc-chat", null) == null) && (Utils.getprefc("wpos-chat", null) == null)) {
		chatwnd.c = defaultchatc();
		Utils.setprefc("wndc-chat", chatwnd.c);
	    }
	    if((mmapwnd != null) && (Utils.getprefc("wpos-moon-minimap", null) == null)) {
		mmapwnd.move(defaultmmapc());
		Utils.setprefc("wpos-moon-minimap", mmapwnd.c);
	    }
	    if((charpanel != null) && (Utils.getprefc("wpos-moon-charhud", null) == null)) {
		charpanel.move(UI.scale(new Coord(10, 10)));
		Utils.setprefc("wpos-moon-charhud", charpanel.c);
	    }
	    if((buffs != null) && (Utils.getprefc("wpos-moon-buffs", null) == null)) {
		buffs.move(defaultbuffc());
		Utils.setprefc("wpos-moon-buffs", buffs.c);
	    }
	    for(int i = 0; i < meters.size(); i++) {
		Widget meter = meters.get(i);
		if(meter == null)
		    continue;
		boolean hasIdx = (Utils.getprefc(String.format("wpos-meter-%d", i), null) != null);
		if(!hasIdx) {
		    Coord def = defaultmeterc(i);
		    meter.move(def);
		    Utils.setprefc(String.format("wpos-meter-%d", i), def);
		}
	    }
	    if((fv != null) && (Utils.getprefc("wpos-fightview", null) == null)) {
		fv.move(defaultfightc(fv));
		Utils.setprefc("wpos-fightview", fv.c);
	    }
	    Utils.setprefb("moon-hud-layout-v5", true);
	}
	if(!Utils.getprefb("moon-ui-panels-v1", false) && !Utils.getprefb("moon-\u0061rd-panels-v1", false)) {
	    for(Hidepanel p : new Hidepanel[] {blpanel, ulpanel, umpanel, urpanel})
		p.cshow(true);
	    Utils.setprefb("chatvis", true);
	    Utils.setprefb("moon-minimap-visible", true);
	    if(chatwnd != null)
		chatwnd.show();
	    if(mmapwnd != null)
		mmapwnd.show();
	    Utils.setprefb("moon-ui-panels-v1", true);
	}
	if(!Utils.getprefb("moon-ui-panels-v2", false) && !Utils.getprefb("moon-\u0061rd-panels-v2", false)) {
	    if(nkeyBelt != null && Utils.getpref("wpos-moon-belt-n", null) == null) {
		nkeyBelt.move(new Coord(sz.x / 2 - nkeyBelt.sz.x / 2, sz.y - nkeyBelt.sz.y - UI.scale(10)));
		Utils.setprefc("wpos-moon-belt-n", nkeyBelt.c);
	    }
	    if(menugridwnd != null && Utils.getpref("wpos-moon-menugrid", null) == null) {
		menugridwnd.move(new Coord(sz.x - menugridwnd.sz.x - UI.scale(10), sz.y - menugridwnd.sz.y - UI.scale(10)));
		Utils.setprefc("wpos-moon-menugrid", menugridwnd.c);
	    }
	    Utils.setprefb("moon-ui-panels-v2", true);
	}
	if(!Utils.getprefb("moon-ui-panels-v3", false) && !Utils.getprefb("moon-\u0061rd-panels-v3", false)) {
	    if(fkeyBelt != null && Utils.getpref("wpos-moon-belt-f", null) == null) {
		fkeyBelt.move(new Coord(sz.x / 2 - fkeyBelt.sz.x / 2, sz.y - fkeyBelt.sz.y - UI.scale(60)));
		Utils.setprefc("wpos-moon-belt-f", fkeyBelt.c);
	    }
	    if(numBelt != null && Utils.getpref("wpos-moon-belt-num", null) == null) {
		numBelt.move(new Coord(sz.x / 2 - numBelt.sz.x / 2, sz.y - numBelt.sz.y - UI.scale(110)));
		Utils.setprefc("wpos-moon-belt-num", numBelt.c);
	    }
	    
	    Utils.setprefb("moon-ui-panels-v3", true);
	}
	if(chatwnd != null && !chatwnd.locked())
	    keepinside(chatwnd);
	keepinside(charpanel);
	keepinside(buffs);
	if(fv != null)
	    keepinside(fv);
	if((mmapwnd != null) && mmapwnd.visible() && !mmapwnd.locked())
	    keepinside(mmapwnd);
	if(menugridwnd != null)
	    keepinside(menugridwnd);
	if(sesswnd != null)
	    keepinside(sesswnd);
	if(nkeyBelt != null)
	    keepinside(nkeyBelt);
	if(fkeyBelt != null && fkeyBelt.visible())
	    keepinside(fkeyBelt);
	if(numBelt != null && numBelt.visible())
	    keepinside(numBelt);
	
	if(gameShortcuts != null)
	    keepinside(gameShortcuts);
	if(moonTools != null)
	    keepinside(moonTools);
	if(moonHandsToolbar != null && !moonHandsToolbar.locked())
	    keepinside(moonHandsToolbar);
	if(moonSpeedWirePanel != null && moonSpeedWirePanel.visible() && !moonSpeedWirePanel.locked())
	    keepinside(moonSpeedWirePanel);
	if(makewnd != null && makewnd.visible())
	    keepCraftWndInside();
	/* keepinside() adjusts c without MovableWidget.savePosition — sync prefs (flush on tick/dispose). */
	syncChatMinimapLayoutPrefs();
    }
    
    public void presize() {
	resize(parent.sz);
    }
    
    public static interface LogMessage extends UI.Notice {
	public ChatUI.Channel.Message logmessage();
    }

    public boolean msg(UI.Notice msg) {
	if(msg.handler(this))
	    return(true);
	ChatUI.Channel.Message logged;
	if(msg instanceof LogMessage)
	    logged = ((LogMessage)msg).logmessage();
	else
	    logged = new ChatUI.Channel.SimpleMessage(msg.message(), msg.color());
	String text = msg.message();
	if((text != null) && !text.isEmpty()) {
	    lastmsgRaw = text;
	    lastmsgCol = (msg.color() != null) ? msg.color() : Color.WHITE;
	    lastmsgTrGen = LocalizationManager.autoTranslateUiGenerationSampled();
	    MoonPerfOverlay.countTextRender();
	    if(lastmsg != null)
		lastmsg.dispose();
	    lastmsg = RootWidget.msgfoundry.render(LocalizationManager.autoTranslateProcessed(lastmsgRaw), lastmsgCol);
	    msgtime = Utils.rtime();
	} else {
	    lastmsg = null;
	    lastmsgRaw = null;
	}
	syslog.append(logged);
	ui.sfxrl(msg.sfx());
	return(true);
    }

    public void error(String msg) {
	ui.error(msg);
    }
    
    public void act(String... args) {
	wdgmsg("act", (Object[])args);
    }

    public void act(int mods, Coord mc, Gob gob, String... args) {
	int n = args.length;
	Object[] al = new Object[n];
	System.arraycopy(args, 0, al, 0, n);
	if(mc != null) {
	    al = Utils.extend(al, al.length + 2);
	    al[n++] = mods;
	    al[n++] = mc;
	    if(gob != null) {
		al = Utils.extend(al, al.length + 2);
		al[n++] = (int)gob.id;
		al[n++] = gob.rc;
	    }
	}
	wdgmsg("act", al);
    }

    public class FKeyBelt extends Belt implements DTarget, DropTarget {
	public final int beltkeys[] = {KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
				       KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
				       KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12};
	public int curbelt = 0;

	public FKeyBelt() {
	    super(UI.scale(new Coord(450, 34)));
	}

	private Coord beltc(int i) {
	    return(new Coord((((invsq.sz().x + UI.scale(2)) * i) + (10 * (i / 4))), 0));
	}
    
	public int beltslot(Coord c) {
	    for(int i = 0; i < 12; i++) {
		if(c.isect(beltc(i), invsq.sz()))
		    return(i + (curbelt * 12));
	    }
	    return(-1);
	}
    
	public void draw(GOut g) {
	    for(int i = 0; i < 12; i++) {
		int slot = i + (curbelt * 12);
		Coord c = beltc(i);
		g.image(invsq, beltc(i));
		try {
		    if(belt[slot] != null)
			belt[slot].draw(g.reclip(c.add(UI.scale(1), UI.scale(1)), invsq.sz().sub(UI.scale(2), UI.scale(2))));
		} catch(Loading e) {}
		g.chcolor(156, 180, 158, 255);
		FastText.aprintf(g, c.add(invsq.sz().sub(UI.scale(2), 0)), 1, 1, "F%d", i + 1);
		g.chcolor();
	    }
	}
	
	public boolean globtype(GlobKeyEvent ev) {
	    if(combatKeysReserved(ev))
		return(super.globtype(ev));
	    boolean M = (ev.mods & KeyMatch.M) != 0;
	    for(int i = 0; i < beltkeys.length; i++) {
		if(ev.code == beltkeys[i]) {
		    if(M) {
			curbelt = i;
			return(true);
		    } else {
			keyact(i + (curbelt * 12));
			return(true);
		    }
		}
	    }
	    return(super.globtype(ev));
	}
    }
    
    private static final Tex nkeybg = Resource.loadtex("gfx/hud/hb-main");
    public class NKeyBelt extends Belt {
	public int curbelt = 0;
	final Coord pagoff = UI.scale(new Coord(5, 25));

	public NKeyBelt() {
	    super(nkeybg.sz());
	    adda(new IButton("gfx/hud/hb-btn-chat", "", "-d", "-h") {
		    Tex glow;
		    {
			this.tooltip = RichText.render("Chat ($col[255,255,0]{Ctrl+C})", 0);
			glow = new TexI(PUtils.rasterimg(PUtils.blurmask(up.getRaster(), UI.scale(2), UI.scale(2), Color.WHITE)));
		    }

		    public void click() {
			togglechat();
		    }

		    public void draw(GOut g) {
			super.draw(g);
			Color urg = ChatUI.urgcols[chat.urgency];
			if(urg != null) {
			    GOut g2 = g.reclipl2(UI.scale(-4, -4), g.sz().add(UI.scale(4, 4)));
			    g2.chcolor(urg.getRed(), urg.getGreen(), urg.getBlue(), 128);
			    g2.image(glow, Coord.z);
			}
		    }
		}, sz, 1, 1);
	}
	
	private Coord beltc(int i) {
	    return(pagoff.add(UI.scale((36 * i) + (10 * (i / 5))), 0));
	}
    
	public int beltslot(Coord c) {
	    for(int i = 0; i < 10; i++) {
		if(c.isect(beltc(i), invsq.sz()))
		    return(i + (curbelt * 12));
	    }
	    return(-1);
	}
    
	public void draw(GOut g) {
	    g.image(nkeybg, Coord.z);
	    for(int i = 0; i < 10; i++) {
		int slot = i + (curbelt * 12);
		Coord c = beltc(i);
		g.image(invsq, beltc(i));
		try {
		    if(belt[slot] != null) {
			belt[slot].draw(g.reclip(c.add(UI.scale(1), UI.scale(1)), invsq.sz().sub(UI.scale(2), UI.scale(2))));
		    }
		} catch(Loading e) {}
		g.chcolor(156, 180, 158, 255);
		FastText.aprintf(g, c.add(invsq.sz().sub(UI.scale(2), 0)), 1, 1, "%d", (i + 1) % 10);
		g.chcolor();
	    }
	    super.draw(g);
	}
	
	public boolean globtype(GlobKeyEvent ev) {
	    if(combatKeysReserved(ev))
		return(super.globtype(ev));
	    if((ev.code < KeyEvent.VK_0) || (ev.code > KeyEvent.VK_9))
		return(super.globtype(ev));
	    int i = Utils.floormod(ev.code - KeyEvent.VK_0 - 1, 10);
	    boolean M = (ev.mods & KeyMatch.M) != 0;
	    if(M) {
		curbelt = i;
	    } else {
		keyact(i + (curbelt * 12));
	    }
	    return(true);
	}
    }
    
    {
	String val = Utils.getpref("belttype", "n");
	if(val.equals("n")) {
	    beltwdg = add(new NKeyBelt());
	} else if(val.equals("f")) {
	    beltwdg = add(new FKeyBelt());
	} else {
	    beltwdg = add(new NKeyBelt());
	}
	beltwdg.hide();
    }
    
    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("afk", new Console.Command() {
		public void run(Console cons, String[] args) {
		    afk = true;
		    wdgmsg("afk");
		}
	    });
	cmdmap.put("act", new Console.Command() {
		public void run(Console cons, String[] args) {
		    Object[] ad = new Object[args.length - 1];
		    System.arraycopy(args, 1, ad, 0, ad.length);
		    wdgmsg("act", ad);
		}
	    });
	cmdmap.put("belt", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args[1].equals("f")) {
			beltwdg.destroy();
			beltwdg = add(new FKeyBelt());
			Utils.setpref("belttype", "f");
			resize(sz);
		    } else if(args[1].equals("n")) {
			beltwdg.destroy();
			beltwdg = add(new NKeyBelt());
			Utils.setpref("belttype", "n");
			resize(sz);
		    }
		}
	    });
	cmdmap.put("chrmap", new Console.Command() {
		public void run(Console cons, String[] args) {
		    Utils.setpref("mapfile/" + chrid, args[1]);
		}
	    });
	cmdmap.put("tool", new Console.Command() {
		public void run(Console cons, String[] args) {
		    try {
			Object[] wargs = new Object[args.length - 2];
			for(int i = 0; i < wargs.length; i++)
			    wargs[i] = args[i + 2];
			add(gettype(args[1]).create(ui, wargs), 200, 200);
		    } catch(RuntimeException e) {
			e.printStackTrace(Debug.log);
		    }
		}
	    });
	cmdmap.put("interact", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length < 2) {
			if(cons.out != null)
			    cons.out.println("usage: interact <gob-id>  (optional: radius, default " + MoonSmartInteract.defaultInteractRadius + ")");
			return;
		    }
		    try {
			long gid = Long.parseLong(args[1].trim());
			double r = MoonSmartInteract.defaultInteractRadius;
			if(args.length >= 3)
			    r = Double.parseDouble(args[2].trim());
			MoonSmartInteract.interactById(GameUI.this, gid, r);
		    } catch(NumberFormatException e) {
			if(cons.out != null)
			    cons.out.println("interact: bad number");
		    }
		}
	    });
	cmdmap.put("blind", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args.length < 2) {
			if(cons.out != null)
			    cons.out.println("usage: blind <substring>  (e.g. blind herb) — synthetic LMB click on matching gobs");
			return;
		    }
		    StringBuilder sb = new StringBuilder();
		    for(int i = 1; i < args.length; i++) {
			if(i > 1)
			    sb.append(' ');
			sb.append(args[i]);
		    }
		    MoonBlindCollect.blindCollect(GameUI.this, sb.toString());
		}
	    });
	cmdmap.put("blinddump", new Console.Command() {
		public void run(Console cons, String[] args) {
		    MoonBlindCollect.dumpSuspiciousGobs(GameUI.this);
		}
	    });
	if(MoonNetworkProfiler.devtoolsEnabled()) {
	    cmdmap.put("netprof", new Console.Command() {
		    private void usage(Console cons) {
			if(cons.out != null) {
			    cons.out.println("usage: netprof status");
			    cons.out.println("   or: netprof passive <wid> <action1> <action2>");
			    cons.out.println("   or: netprof pair <wid> <action1> <action2>");
			    cons.out.println("   or: netprof single <wid> <action> [args...]");
			    cons.out.println("args: integer -> int, x,y -> Coord, otherwise string");
			}
		    }

		    public void run(Console cons, String[] args) {
			if(args.length < 2) {
			    usage(cons);
			    return;
			}
			String sub = args[1].trim().toLowerCase(Locale.ROOT);
			if("status".equals(sub)) {
			    if(cons.out != null)
				cons.out.println("netprof enabled via " + MoonNetworkProfiler.enableHint());
			    return;
			}
			if(ui == null || ui.sess == null) {
			    if(cons.out != null)
				cons.out.println("netprof: no active ui/session");
			    return;
			}
			try {
			    if("passive".equals(sub)) {
				if(args.length < 5) {
				    usage(cons);
				    return;
				}
				int wid = Integer.parseInt(args[2].trim());
				MoonNetworkProfiler.armPassiveTrace(ui, wid, args[3], args[4]);
				return;
			    }
			    if("pair".equals(sub)) {
				if(args.length < 5) {
				    usage(cons);
				    return;
				}
				int wid = Integer.parseInt(args[2].trim());
				MoonNetworkProfiler.traceTransactionConsistency(ui, wid, args[3], args[4]);
				return;
			    }
			    if("single".equals(sub)) {
				if(args.length < 4) {
				    usage(cons);
				    return;
				}
				int wid = Integer.parseInt(args[2].trim());
				MoonNetworkProfiler.traceSingleAction(ui, wid, args[3], MoonNetworkProfiler.parseConsoleArgs(args, 4));
				return;
			    }
			    usage(cons);
			} catch(NumberFormatException e) {
			    if(cons.out != null)
				cons.out.println("netprof: bad widget id");
			}
		    }
		});
	}
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
