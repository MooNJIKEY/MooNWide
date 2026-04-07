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
import haven.render.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import haven.Resource.AButton;
import haven.ItemInfo.AttrCache;

public class MenuGrid extends Widget implements KeyBinding.Bindable {
    public final static Tex bg = Inventory.invsq;
    public final static Coord bgsz = Inventory.sqsz;
    public final static RichText.Foundry ttfnd = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, UI.scale(10f));
    public static Coord gsz;
    public final Set<Pagina> paginae = new HashSet<Pagina>();
    public final Map<String, SpecialPagina> specialpag = new LinkedHashMap<String, SpecialPagina>();
    public Pagina cur;

    /** Shared backing resource for client specials (action/layers); icon drawn as {@link MoonUiIcons}. */
    private static final Indir<Resource> moonSpecialRes = Resource.local().load("gfx/hud/sc-next");
    private static Tex moonSpecialPhTex;
    private static BufferedImage moonSpecialPhImg;

    private static void ensureMoonSpecialPlaceholder() {
	synchronized(MenuGrid.class) {
	    if(moonSpecialPhTex != null)
		return;
	    Coord isz = bgsz.sub(1, 1);
	    int w = isz.x, h = isz.y;
	    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	    Graphics2D g2 = img.createGraphics();
	    try {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(new Color(22, 16, 40, 255));
		g2.fillRect(0, 0, w, h);
		g2.setColor(new Color(90, 70, 125, 255));
		g2.drawRect(0, 0, w - 1, h - 1);
		g2.setColor(new Color(210, 185, 120, 210));
		int arm = Math.max(3, Math.min(w, h) / 5);
		int cx = w / 2, cy = h / 2;
		g2.fillRect(cx - arm, cy - 1, arm * 2 + 1, 3);
		g2.fillRect(cx - 1, cy - arm, 3, arm * 2 + 1);
	    } finally {
		g2.dispose();
	    }
	    moonSpecialPhImg = img;
	    moonSpecialPhTex = new TexI(img);
	}
    }

    static final class MoonSpecialSlotSprite extends GSprite implements GSprite.ImageSprite {
	private final Tex tex;
	private final BufferedImage img;

	MoonSpecialSlotSprite(Owner owner, Tex tex, BufferedImage img) {
	    super(owner);
	    this.tex = tex;
	    this.img = img;
	}

	public void draw(GOut g) {
	    g.image(tex, Coord.z);
	}

	public Coord sz() {
	    return(tex.sz());
	}

	public BufferedImage image() {
	    return(img);
	}
    }
    private final Map<Object, Pagina> pmap = new CacheMap<>(CacheMap.RefType.WEAK);
    private Pagina dragging;
    private Collection<PagButton> curbtns = Collections.emptyList();
    private PagButton pressed;
    private PagButton[][] layout;
    private UI.Grab grab;
    private int curoff = 0;
    private boolean recons = true, showkeys = false;
    private double fstart;

    static {
	Coord saved = Utils.getprefc("menugrid-size", new Coord(4, 4));
	gsz = new Coord(
	    Math.max(2, Math.min(8, saved.x)),
	    Math.max(2, Math.min(8, saved.y))
	);
    }

    public void setGridSize(Coord nsz) {
	nsz = new Coord(Math.max(2, Math.min(8, nsz.x)),
			Math.max(2, Math.min(8, nsz.y)));
	if(nsz.equals(gsz))
	    return;
	gsz = nsz;
	layout = new PagButton[gsz.x][gsz.y];
	resize(bgsz.mul(gsz).add(1, 1));
	Utils.setprefc("menugrid-size", gsz);
	curoff = 0;
	updlayout();
    }

    public Coord gridSize() { return gsz; }
	
    @RName("scm")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new MenuGrid());
	}
    }

    public static class Pagina {
	public final MenuGrid scm;
	public final Object id;
	public Indir<Resource> res;
	public byte[] sdt = null;
	public int anew, tnew;
	public Object[] rawinfo = {};

	public Pagina(MenuGrid scm, Object id, Indir<Resource> res) {
	    this.scm = scm;
	    this.id = id;
	    this.res = res;
	}

	public Resource res() {
	    return(res.get());
	}

	public Message data() {
	    return((sdt == null) ? Message.nil : new MessageBuf(sdt));
	}

	private void invalidate() {
	    button = null;
	}

	protected PagButton button = null;
	public PagButton button() {
	    if(button == null) {
		Resource res = res();
		PagButton.Factory f = res.getcode(PagButton.Factory.class, false);
		if(f == null)
		    button = new PagButton(this);
		else
		    button = f.make(this);
	    }
	    return(button);
	}

	public Pagina parent() {
	    return(button().parent());
	}
    }

    public static class SpecialPagina extends Pagina {
	public final String key;
	public final String title;
	private final Consumer<Interaction> action;

	public SpecialPagina(MenuGrid scm, String key, String title, Consumer<Interaction> action) {
	    super(scm, key, moonSpecialRes);
	    this.key = key;
	    this.title = title;
	    this.action = action;
	}

	public PagButton button() {
	    if(button == null) {
		button = new PagButton(this) {
		    private GSprite moonspr;

		    @Override
		    public GSprite spr() {
			if(moonspr == null) {
			    BufferedImage icon = MoonUiIcons.image(((SpecialPagina)pag).key, bgsz.sub(1, 1));
			    if(icon != null)
				moonspr = new MoonSpecialSlotSprite(this, new TexI(icon), icon);
			    else {
				ensureMoonSpecialPlaceholder();
				moonspr = new MoonSpecialSlotSprite(this, moonSpecialPhTex, moonSpecialPhImg);
			    }
			}
			return(moonspr);
		    }

		    public Pagina parent() {
			return(null);
		    }

		    public String name() {
			if(((SpecialPagina)pag).title != null)
			    return(((SpecialPagina)pag).title);
			return(super.name());
		    }

		    public KeyBinding binding() {
			return(KeyBinding.get("scm-special/" + ((SpecialPagina)pag).key, KeyMatch.nil));
		    }

		    /** Avoid {@link PagButton#sortkey} / {@link PagButton#hotkey} calling {@link #act} — custom tiles may lack {@link Resource#action}. */
		    @Override
		    public String sortkey() {
			return("\0" + name());
		    }

		    @Override
		    public KeyMatch hotkey() {
			return(KeyMatch.nil);
		    }

		    public BufferedImage rendertt(boolean withpg) {
			String tt = name();
			KeyMatch key = bind.key();
			int pos = -1;
			char vkey = 0;
			if(key.modmatch == 0) {
			    vkey = key.chr;
			    if((vkey == 0) && (key.keyname.length() == 1))
				vkey = key.keyname.charAt(0);
			}
			if((vkey != 0) && (key.modmatch == 0))
			    pos = tt.toUpperCase().indexOf(Character.toUpperCase(vkey));
			if(pos >= 0)
			    tt = RichText.Parser.quote(tt.substring(0, pos)) + "[$col[255,128,0]{" + RichText.Parser.quote(tt.substring(pos, pos + 1)) + "}]" + RichText.Parser.quote(tt.substring(pos + 1));
			else if(key != KeyMatch.nil)
			    tt += " [$b{$col[255,128,0]{" + key.name() + "}}]";
			return(ttfnd.render(tt, UI.scale(300)).img);
		    }

		    public void use(Interaction iact) {
			if(action != null)
			    action.accept(iact);
		    }
		};
	    }
	    return(button);
	}
    }

    public static class Interaction {
	public final int btn, modflags;
	public final Coord2d mc;
	public final ClickData click;

	public Interaction(int btn, int modflags, Coord2d mc, ClickData click) {
	    this.btn = btn;
	    this.modflags = modflags;
	    this.mc = mc;
	    this.click = click;
	}

	public Interaction(int btn, int modflags) {
	    this(btn, modflags, null, null);
	}

	public Interaction() {
	    this(1, 0);
	}
    }

    public static class PagButton implements ItemInfo.Owner, GSprite.Owner, RandomSource {
	public final Pagina pag;
	public final Resource res;
	public final KeyBinding bind;
	private GSprite spr;
	private AButton act;

	public PagButton(Pagina pag) {
	    this.pag = pag;
	    this.res = pag.res();
	    this.bind = binding();
	}

	public AButton act() {
	    if(act == null)
		act = res.flayer(Resource.action);
	    return(act);
	}

	private Pagina parent;
	public Pagina parent() {
	    if(parent == null)
		parent = pag.scm.paginafor(act().parent);
	    return(parent);
	}

	public GSprite spr() {
	    if(spr == null)
		spr = GSprite.create(this, res, Message.nil);
	    return(spr);
	}
	public String name() {return(act().name);}
	public KeyMatch hotkey() {
	    char hk = act().hk;
	    if(hk == 0)
		return(KeyMatch.nil);
	    return(KeyMatch.forchar(Character.toUpperCase(hk), KeyMatch.MODS & ~KeyMatch.S, 0));
	}
	public KeyBinding binding() {
	    return(KeyBinding.get("scm/" + res.name, hotkey()));
	}
	public void use(Interaction iact) {
	    Object[] eact = new Object[] {pag.scm.ui.modflags()};
	    if(iact.mc != null) {
		eact = Utils.extend(eact, iact.mc.floor(OCache.posres));
		if(iact.click != null)
		    eact = Utils.extend(eact, iact.click.clickargs());
	    }
	    if(pag.id instanceof Indir)
		pag.scm.wdgmsg("act", Utils.extend(Utils.extend(new Object[0], act().ad), eact));
	    else
		pag.scm.wdgmsg("use", Utils.extend(new Object[] {pag.id}, eact));
	}
	public void tick(double dt) {
	    if(spr != null)
		spr.tick(dt);
	}

	public BufferedImage img() {
	    GSprite spr = spr();
	    if(spr instanceof GSprite.ImageSprite)
		return(((GSprite.ImageSprite)spr).image());
	    return(null);
	}

	public final AttrCache<Pipe.Op> rstate = new AttrCache<>(this::info, info -> {
		ArrayList<GItem.RStateInfo> ols = new ArrayList<>();
		for(ItemInfo inf : info) {
		    if(inf instanceof GItem.RStateInfo)
			ols.add((GItem.RStateInfo)inf);
		}
		if(ols.size() == 0)
		    return(() -> null);
		if(ols.size() == 1) {
		    Pipe.Op op = ols.get(0).rstate();
		    return(() -> op);
		}
		Pipe.Op[] ops = new Pipe.Op[ols.size()];
		for(int i = 0; i < ops.length; i++)
		    ops[i] = ols.get(0).rstate();
		Pipe.Op cmp = Pipe.Op.compose(ops);
		return(() -> cmp);
	});
	public final AttrCache<GItem.InfoOverlay<?>[]> ols = new AttrCache<>(this::info, info -> {
		ArrayList<GItem.InfoOverlay<?>> buf = new ArrayList<>();
		for(ItemInfo inf : info) {
		    if(inf instanceof GItem.OverlayInfo)
			buf.add(GItem.InfoOverlay.create((GItem.OverlayInfo<?>)inf));
		}
		GItem.InfoOverlay<?>[] ret = buf.toArray(new GItem.InfoOverlay<?>[0]);
		return(() -> ret);
	});
	public final AttrCache<Double> meter = new AttrCache<>(this::info, AttrCache.map1(GItem.MeterInfo.class, minf -> minf::meter));

	public void drawmain(GOut g, GSprite spr) {
	    spr.draw(g);
	}
	public void draw(GOut g, GSprite spr) {
	    if(rstate.get() != null)
		g.usestate(rstate.get());
	    drawmain(g, spr);
	    g.defstate();
	    GItem.InfoOverlay<?>[] ols = this.ols.get();
	    if(ols != null) {
		for(GItem.InfoOverlay<?> ol : ols)
		    ol.draw(g);
	    }
	    Double meter = this.meter.get();
	    if((meter != null) && (meter > 0)) {
		g.chcolor(255, 255, 255, 64);
		Coord half = spr.sz().div(2);
		g.prect(half, half.inv(), half, meter * Math.PI * 2);
		g.chcolor();
	    }
	}

	public String sortkey() {
	    if((act().ad.length == 0) && (pag.id instanceof Indir))
		return("\0" + name());
	    return(name());
	}

	private char bindchr(KeyMatch key) {
	    if(key.modmatch != 0)
		return(0);
	    char vkey = key.chr;
	    if((vkey == 0) && (key.keyname.length() == 1))
		vkey = key.keyname.charAt(0);
	    return(vkey);
	}

	public static final Text.Foundry keyfnd = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 10);
	private Tex keyrend = null;
	private boolean haskeyrend = false;
	public Tex keyrend() {
	    if(!haskeyrend) {
		char vkey = bindchr(bind.key());
		if(vkey != 0)
		    keyrend = new TexI(Utils.outline2(keyfnd.render(Character.toString(vkey), Color.WHITE).img, Color.BLACK));
		else
		    keyrend = null;
		haskeyrend = true;
	    }
	    return(keyrend);
	}

	private List<ItemInfo> info = null;
	public List<ItemInfo> info() {
	    if(info == null) {
		info = ItemInfo.buildinfo(this, pag.rawinfo);
		Resource.Pagina pg = res.layer(Resource.pagina);
		if(pg != null)
		    info.add(new ItemInfo.Pagina(this, pg.text));
	    }
	    return(info);
	}
	private static final OwnerContext.ClassResolver<PagButton> ctxr = new OwnerContext.ClassResolver<PagButton>()
	    .add(PagButton.class, p -> p)
	    .add(MenuGrid.class, p -> p.pag.scm)
	    .add(Glob.class, p -> p.pag.scm.ui.sess.glob)
	    .add(Session.class, p -> p.pag.scm.ui.sess);
	public <T> T context(Class<T> cl) {return(ctxr.context(cl, this));}
	public Random mkrandoom() {return(new Random());}
	public Resource getres() {return(res);}

	public BufferedImage rendertt(boolean withpg) {
	    String tt = name();
	    KeyMatch key = bind.key();
	    int pos = -1;
	    char vkey = bindchr(key);
	    if((vkey != 0) && (key.modmatch == 0))
		pos = tt.toUpperCase().indexOf(Character.toUpperCase(vkey));
	    if(pos >= 0)
		tt = tt.substring(0, pos) + "$b{$col[255,128,0]{" + tt.charAt(pos) + "}}" + tt.substring(pos + 1);
	    else if(key != KeyMatch.nil)
		tt += " [$b{$col[255,128,0]{" + key.name() + "}}]";
	    BufferedImage ret = ttfnd.render(tt, UI.scale(300)).img;
	    if(withpg) {
		List<ItemInfo> info = info();
		info.removeIf(el -> el instanceof ItemInfo.Name);
		if(!info.isEmpty())
		    ret = ItemInfo.catimgs(0, ret, ItemInfo.longtip(info));
	    }
	    return(ret);
	}

	public static class FactMaker extends Resource.PublishedCode.Instancer.Chain<Factory> {
	    public FactMaker() {
		super(Factory.class);
		add(new Direct<>(Factory.class));
		add(new StaticCall<>(Factory.class, "mkpagina", PagButton.class, new Class<?>[] {Pagina.class},
				     (make) -> (pagina) -> make.apply(new Object[] {pagina})));
		add(new Construct<>(Factory.class, PagButton.class, new Class<?>[] {Pagina.class},
				    (cons) -> (pagina) -> cons.apply(new Object[] {pagina})));
	    }
	}

	@Resource.PublishedCode(name = "pagina", instancer = FactMaker.class)
	public interface Factory {
	    public PagButton make(Pagina info);
	}
    }

    public final PagButton next = new PagButton(new Pagina(this, null, Resource.local().loadwait("gfx/hud/sc-next").indir())) {
	    {pag.button = this;}

	    public void use(Interaction iact) {
		int step = (gsz.x * gsz.y) - 2;
		if((curoff + step) >= curbtns.size())
		    curoff = 0;
		else
		    curoff += step;
		updlayout();
	    }

	    public String name() {return("More...");}

	    public KeyBinding binding() {return(kb_next);}
	};

    public final PagButton bk = new PagButton(new Pagina(this, null, Resource.local().loadwait("gfx/hud/sc-back").indir())) {
	    {pag.button = this;}

	    public void use(Interaction iact) {
		pag.scm.change(pag.scm.cur.parent());
		curoff = 0;
	    }

	    public String name() {return("Back");}

	    public KeyBinding binding() {return(kb_back);}
	};

    public Pagina paginafor(Indir<Resource> res) {
	if(res == null)
	    return(null);
	synchronized(pmap) {
	    Pagina p = pmap.get(res);
	    if(p == null)
		pmap.put(res, p = new Pagina(this, res, res));
	    return(p);
	}
    }

    public Pagina paginafor(Object id, Indir<Resource> res) {
	synchronized(pmap) {
	    Pagina p = pmap.get(id);
	    if((p == null) && (res != null))
		pmap.put(id, p = new Pagina(this, id, res));
	    return(p);
	}
    }

    /**
     * True when two paginae refer to the same menu node. Session {@code fill} keys paginae by
     * server id or session Indir, while {@link PagButton#parent} resolves via resource pool
     * Indirs — different map keys, same {@link Resource}. Vanilla {@code parent == p} then
     * never matches and build/craft subtrees stay empty.
     */
    private boolean samePagina(Pagina a, Pagina b) {
	if(a == b)
	    return(true);
	if(a == null || b == null)
	    return(false);
	Resource ra = a.res();
	Resource rb = b.res();
	if(ra == rb)
	    return(true);
	return(ra.name.equals(rb.name) && ra.ver == rb.ver);
    }

    /** True if this pagina has no child actions in the current menu tree (activating it runs the action, not a submenu). */
    public boolean isLeafAction(Pagina p) {
	if(p == null)
	    return(false);
	List<PagButton> ch = new ArrayList<>();
	try {
	    if(!cons(p, ch))
		return(false);
	} catch(Loading e) {
	    return(false);
	}
	return(ch.isEmpty());
    }

    private boolean cons(Pagina p, Collection<PagButton> buf) {
	Pagina[] cp = new Pagina[0];
	Collection<Pagina> open, close = new HashSet<Pagina>();
	synchronized(pmap) {
	    for(Pagina pag : pmap.values())
		pag.tnew = 0;
	}
	synchronized(paginae) {
	    open = new LinkedList<Pagina>();
	    for(Pagina pag : paginae) {
		if(pag instanceof SpecialPagina)
		    continue;
		open.add(pag);
		if(pag.anew > 0) {
		    try {
			for(Pagina npag = pag; npag != null; npag = npag.parent())
			    npag.tnew = Math.max(npag.tnew, pag.anew);
		    } catch(Loading l) {
		    }
		}
	    }
	}
	boolean ret = true;
	while(!open.isEmpty()) {
	    Iterator<Pagina> iter = open.iterator();
	    Pagina pag = iter.next();
	    iter.remove();
	    try {
		Pagina parent = pag.parent();
		if(samePagina(parent, p))
		    buf.add(pag.button());
		else if((parent != null) && !close.contains(parent) && !open.contains(parent))
		    open.add(parent);
		close.add(pag);
	    } catch(Loading e) {
		ret = false;
	    }
	}
	return(ret);
    }

    private void announce(Pagina pag) {
	ui.loader.defer(() -> ui.msg("New discovery: " + pag.button().name(), Color.WHITE, null), null);
    }

    public MenuGrid() {
	super(bgsz.mul(gsz).add(1, 1));
	layout = new PagButton[gsz.x][gsz.y];
    }

    /**
     * Client-only menu tiles (bots, cave toggle, …). The {@code res} argument is kept for
     * call-site compatibility but ignored: every special uses the same square placeholder art
     * ({@link #ensureMoonSpecialPlaceholder}) so mmap / random HUD icons never appear in the grid.
     */
    public void registerSpecial(String key, String title, String res, java.util.function.Consumer<Interaction> action) {
	if(key == null)
	    return;
	synchronized(paginae) {
	    if(specialpag.containsKey(key))
		return;
	}
	try {
	    Resource.local().loadwait("gfx/hud/sc-next");
	} catch(Exception e) {
	    return;
	}
	addSpecial(new SpecialPagina(this, key, title, action));
	updlayout();
    }

    private void addSpecial(SpecialPagina pag) {
	synchronized(paginae) {
	    specialpag.put(pag.key, pag);
	}
    }

    private void updlayout() {
	synchronized(paginae) {
	    List<PagButton> cur = new ArrayList<>();
	    recons = !cons(this.cur, cur);
	    Collections.sort(cur, Comparator.comparing(PagButton::sortkey));
	    /* Client specials are not in paginae — they must not participate in cons() graph
	     * traversal (that broke server branches like Build / craft). Append at root only. */
	    if(this.cur == null) {
		for(SpecialPagina sp : specialpag.values())
		    cur.add(sp.button());
	    }
	    this.curbtns = cur;
	    int i = curoff;
	    for(int y = 0; y < gsz.y; y++) {
		for(int x = 0; x < gsz.x; x++) {
		    PagButton btn = null;
		    if((this.cur != null) && (x == gsz.x - 1) && (y == gsz.y - 1)) {
			btn = bk;
		    } else if((cur.size() > ((gsz.x * gsz.y) - 1)) && (x == gsz.x - 2) && (y == gsz.y - 1)) {
			btn = next;
		    } else if(i < cur.size()) {
			btn = cur.get(i++);
		    }
		    layout[x][y] = btn;
		}
	    }
	    fstart = Utils.rtime();
	}
    }

    public void draw(GOut g) {
	double now = Utils.rtime();
	for(int y = 0; y < gsz.y; y++) {
	    for(int x = 0; x < gsz.x; x++) {
		Coord p = bgsz.mul(new Coord(x, y));
		g.chcolor(24, 18, 38, 255);
		g.frect(p.add(1, 1), bgsz.sub(1, 1));
		g.chcolor();
		g.image(bg, p);
		PagButton btn = layout[x][y];
		if(btn != null) {
		    GSprite spr;
		    try {
			spr = btn.spr();
		    } catch(Loading l) {
			continue;
		    }
		    GOut g2 = g.reclip(p.add(1, 1), spr.sz());
		    Pagina info = btn.pag;
		    if(info.tnew != 0) {
			info.anew = 1;
			double a = 0.25;
			if(info.tnew == 2) {
			    double ph = (now - fstart) - (((x + (y * gsz.x)) * 0.15) % 1.0);
			    a = (ph < 1.25) ? (Math.cos(ph * Math.PI * 2) * -0.25) + 0.25 : 0.25;
			}
			g2.usestate(new ColorMask(new FColor(0.125f, 1.0f, 0.125f, (float)a)));
		    }
		    btn.draw(g2, spr);
		    g2.defstate();
		    if(showkeys && MoonConfig.menuGridKeyboard) {
			Tex ki = btn.keyrend();
			if(ki != null)
			    g2.aimage(ki, Coord.of(bgsz.x - UI.scale(2), UI.scale(1)), 1.0, 0.0);
		    }
		    if(btn == pressed) {
			g.chcolor(new Color(0, 0, 0, 128));
			g.frect(p.add(1, 1), bgsz.sub(1, 1));
			g.chcolor();
		    }
		}
	    }
	}
	super.draw(g);
	if(dragging != null) {
	    GSprite ds = dragging.button().spr();
	    ui.drawafter(new UI.AfterDraw() {
		    public void draw(GOut g) {
			ds.draw(g.reclip(ui.mc.sub(ds.sz().div(2)), ds.sz()));
		    }
		});
	}
    }

    private PagButton curttp = null;
    private boolean curttl = false;
    private Tex curtt = null;
    private double hoverstart;
    public Object tooltip(Coord c, Widget prev) {
	PagButton pag = bhit(c);
	double now = Utils.rtime();
	if(pag != null) {
	    if(prev != this)
		hoverstart = now;
	    boolean ttl = (now - hoverstart) > 0.5;
	    if((pag != curttp) || (ttl != curttl)) {
		BufferedImage ti = pag.rendertt(ttl);
		curtt = (ti == null) ? null : new TexI(ti);
		curttp = pag;
		curttl = ttl;
	    }
	    return(curtt);
	} else {
	    hoverstart = now;
	    return(null);
	}
    }

    private PagButton bhit(Coord c) {
	Coord bc = c.div(bgsz);
	if((bc.x >= 0) && (bc.y >= 0) && (bc.x < gsz.x) && (bc.y < gsz.y))
	    return(layout[bc.x][bc.y]);
	else
	    return(null);
    }

    public boolean mousedown(MouseDownEvent ev) {
	GameUI gui = getparent(GameUI.class);
	if(gui != null)
	    gui.restoreMoonHudZOrder();
	PagButton h = bhit(ev.c);
	if((ev.b == 1) && (h != null)) {
	    pressed = h;
	    grab = ui.grabmouse(this);
	}
	return(true);
    }

    public void mousemove(MouseMoveEvent ev) {
	if((dragging == null) && (pressed != null)) {
	    PagButton h = bhit(ev.c);
	    if(h != pressed)
		dragging = pressed.pag;
	}
    }

    public void change(Pagina dst) {
	this.cur = dst;
	curoff = 0;
	if(dst == null)
	    showkeys = false;
	updlayout();
    }

    public void use(PagButton r, Interaction iact, boolean reset) {
	if(isAdvancedCraftButton(r)) {
	    GameUI gui = getparent(GameUI.class);
	    if(gui != null)
		gui.toggleCraftWindow();
	    if(reset)
		change(null);
	    return;
	}
	Collection<PagButton> sub = new ArrayList<>();
	cons(r.pag, sub);
	if(sub.size() > 0) {
	    change(r.pag);
	} else {
	    r.pag.anew = r.pag.tnew = 0;
	    r.use(iact);
	    if(reset)
		change(null);
	}
    }

    private boolean isAdvancedCraftRoot(PagButton r) {
	if(r == null)
	    return(false);
	try {
	    Resource.AButton act = r.act();
	    if((act == null) || (act.ad == null) || (act.ad.length == 0))
		return(false);
	    if(act.ad.length != 1)
		return(false);
	    String path = act.ad[0];
	    if(path != null && path.equalsIgnoreCase("craft"))
		return(true);
	    String nm = LocalizationManager.autoTranslateProcessed(r.name()).toLowerCase(Locale.ROOT);
	    return(nm.equals("craft") || nm.equals("крафт"));
	} catch(Loading | Resource.NoSuchLayerException ignored) {
	    return(false);
	}
    }

    private boolean craftToken(String token) {
	if(token == null)
	    return(false);
	String v = token.toLowerCase(Locale.ROOT);
	return(v.equals("craft") || v.endsWith("/craft") || v.contains("/craft/") || v.contains("\\craft\\"));
    }

    private boolean isAdvancedCraftButton(PagButton r) {
	if(r == null)
	    return(false);
	if(r.pag instanceof SpecialPagina)
	    return(false);
	try {
	    Resource.AButton act = r.act();
	    if((act != null) && (act.ad != null)) {
		for(String path : act.ad) {
		    if(craftToken(path))
			return(true);
		}
	    }
	    if((r.res != null) && craftToken(r.res.name))
		return(true);
	    for(Pagina p = r.pag; p != null; p = p.parent()) {
		Resource pres = p.res();
		if((pres != null) && craftToken(pres.name))
		    return(true);
		try {
		    Resource.AButton pact = p.button().act();
		    if((pact != null) && (pact.ad != null)) {
			for(String path : pact.ad) {
			    if(craftToken(path))
				return(true);
			}
		    }
		} catch(Loading | Resource.NoSuchLayerException ignored) {
		}
	    }
	    String nm = LocalizationManager.autoTranslateProcessed(r.name()).toLowerCase(Locale.ROOT);
	    return(nm.equals("craft") || nm.equals("крафт"));
	} catch(Loading | Resource.NoSuchLayerException ignored) {
	    return(false);
	}
    }

    private boolean isAdvancedCraftPagina(Pagina pag) {
	if(pag == null)
	    return(false);
	try {
	    return(isAdvancedCraftButton(pag.button()));
	} catch(Loading ignored) {
	    return(false);
	}
    }

    public void tick(double dt) {
	if(recons)
	    updlayout();
	for(int y = 0; y < gsz.y; y++) {
	    for(int x = 0; x < gsz.x; x++) {
		if(layout[x][y] != null)
		    layout[x][y].tick(dt);
	    }
	}
    }

    public boolean mouseup(MouseUpEvent ev) {
	PagButton h = bhit(ev.c);
	if((ev.b == 1) && (grab != null)) {
	    if(dragging != null) {
		DropTarget.dropthing(ui.root, ui.mc, dragging);
		pressed = null;
		dragging = null;
	    } else if(pressed != null) {
		if(pressed == h)
		    use(h, new Interaction(), false);
		pressed = null;
	    }
	    grab.remove();
	    grab = null;
	}
	return(true);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "goto") {
	    if(args[0] == null) {
		change(null);
	    } else {
		Pagina dst = paginafor(ui.sess.getresv(args[0]));
		if(isAdvancedCraftPagina(dst)) {
		    GameUI gui = getparent(GameUI.class);
		    if(gui != null)
			gui.toggleCraftWindow();
		    change(null);
		} else {
		    change(dst);
		}
	    }
	} else if(msg == "fill") {
	    synchronized(paginae) {
		int a = 0;
		while(a < args.length) {
		    int fl = Utils.iv(args[a++]);
		    Pagina pag;
		    Object id;
		    if((fl & 2) != 0)
			pag = paginafor(id = args[a++], null);
		    else
			id = (pag = paginafor(ui.sess.getres(Utils.iv(args[a++]), -2))).res;
		    if((fl & 1) != 0) {
			if((fl & 2) != 0) {
			    Indir<Resource> res = ui.sess.getres(Utils.iv(args[a++]), -2);
			    if(pag == null) {
				pag = paginafor(id, res);
			    } else if(pag.res != res) {
				pag.res = res;
				pag.invalidate();
			    }
			}
			byte[] data = ((fl & 4) != 0) ? (byte[])args[a++] : null;
			if(!Arrays.equals(pag.sdt, data)) {
			    pag.sdt = data;
			    pag.invalidate();
			}
			if((fl & 8) != 0) {
			    pag.anew = 2;
			    announce(pag);
			}
			Object[] rawinfo = ((fl & 16) != 0) ? (Object[])args[a++] : new Object[0];
			if(!Arrays.deepEquals(pag.rawinfo, rawinfo)) {
			    pag.rawinfo = rawinfo;
			    pag.invalidate();
			}
			paginae.add(pag);
		    } else {
			paginae.remove(pag);
		    }
		}
		updlayout();
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final KeyBinding kb_root = KeyBinding.get("scm-root", KeyMatch.forcode(KeyEvent.VK_ESCAPE, 0));
    public static final KeyBinding kb_back = KeyBinding.get("scm-back", KeyMatch.forcode(KeyEvent.VK_BACK_SPACE, 0));
    public static final KeyBinding kb_next = KeyBinding.get("scm-next", KeyMatch.forchar('N', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S));

    /**
     * While the fight view is up with at least one relation, do not let action-menu letter shortcuts
     * (craft / adventure / etc.) consume keys — they otherwise override combat and other binds.
     */
    private boolean moonSuppressPagHotkeysForCombat() {
	GameUI gui = getparent(GameUI.class);
	if(gui == null || gui.fv == null || !gui.fv.visible())
	    return(false);
	return(gui.fv.lsrel != null && !gui.fv.lsrel.isEmpty());
    }

    /**
     * Root/back/next and pagina letter binds. Used from {@link GameUI#globtype} when the menu window
     * is visible so these win over inventory/character/etc. (same traversal order as vanilla children).
     */
    public boolean moonHandleScmKeys(GlobKeyEvent ev) {
	if(!MoonConfig.menuGridKeyboard)
	    return(false);
	if(kb_root.key().match(ev) && (this.cur != null)) {
	    change(null);
	    return(true);
	}
	if(kb_back.key().match(ev) && (this.cur != null)) {
	    use(bk, new Interaction(), false);
	    return(true);
	}
	if(kb_next.key().match(ev) && (layout != null) && (gsz.x >= 2) && (gsz.y >= 1)
	    && (layout[gsz.x - 2][gsz.y - 1] == next)) {
	    use(next, new Interaction(), false);
	    return(true);
	}
	if(!moonSuppressPagHotkeysForCombat()) {
	    int cp = -1;
	    PagButton pag = null;
	    for(PagButton btn : curbtns) {
		if(btn.bind.key().match(ev)) {
		    int prio = btn.bind.set() ? 1 : 0;
		    if((pag == null) || (prio > cp)) {
			pag = btn;
			cp = prio;
		    }
		}
	    }
	    if(pag != null) {
		use(pag, new Interaction(), (ev.mods & KeyMatch.S) == 0);
		if(this.cur != null)
		    showkeys = true;
		return(true);
	    }
	}
	return(false);
    }

    public boolean globtype(GlobKeyEvent ev) {
	if(moonHandleScmKeys(ev))
	    return(true);
	return(super.globtype(ev));
    }

    public KeyBinding getbinding(Coord cc) {
	PagButton h = bhit(cc);
	return((h == null) ? null : h.bind);
    }
}
