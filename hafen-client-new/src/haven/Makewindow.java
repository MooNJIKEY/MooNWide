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
import java.awt.Font;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static haven.Inventory.invsq;

public class Makewindow extends Widget {
    private final Text qmodlLbl;
    private final Text toollLbl;
    public static final Coord boff = UI.scale(new Coord(7, 9));
    private static final Coord ADV_SZ = UI.scale(860, 560);
    private static final Coord SEARCH_POS = UI.scale(6, 24);
    private static final Coord RECIPE_LIST_POS = UI.scale(6, 52);
    private static final Coord RECIPE_LIST_SZ = UI.scale(270, 492);
    private static final Coord RIGHT_POS = UI.scale(290, 8);
    private static final int RIGHT_W = UI.scale(560);
    private static final int INPUT_ICON_Y = UI.scale(74);
    private static final int OUTPUT_ICON_Y = UI.scale(150);
    private static final int SECTION_GAP = UI.scale(18);
    public String rcpnm;
    public List<Input> inputs = Collections.emptyList();
    public List<SpecWidget> outputs = Collections.emptyList();
    public List<Indir<Resource>> qmod = Collections.emptyList();
    public List<Indir<Resource>> tools = new ArrayList<>();
    private final Map<Integer, List<MenuGrid.Pagina>> pendingRecipes = new HashMap<>();
    private final TextEntry recipeSearch;
    private final TextEntry craftCount;
    private final RecipeList recipeList;
    private final Button craftOneBtn;
    private final Button craftCountBtn;
    private final Button craftAllBtn;
    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private final List<Object> recipeRows = new ArrayList<>();
    private final Map<String, Boolean> categoryOpen = new HashMap<>();
    private RecipeEntry selectedRecipe;
    private String lastRecipeDigest = "";
    private MoonCraftLedger.Snapshot stock = MoonCraftLedger.empty();
    private double nextStockRefresh = 0;
    private int queuedCrafts = 0;
    private boolean queuedWaitFinish = false;
    private boolean queuedSawProgress = false;
    private double queuedNextAt = 0;
    private int qmx, toolx;

    @RName("make")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Makewindow((String)args[0]));
	}
    }

    private static final OwnerContext.ClassResolver<Makewindow> ctxr = new OwnerContext.ClassResolver<Makewindow>()
	.add(Makewindow.class, wdg -> wdg)
	.add(Glob.class, wdg -> wdg.ui.sess.glob)
	.add(Session.class, wdg -> wdg.ui.sess);
    public class Spec implements GSprite.Owner, ItemInfo.SpriteOwner, RandomSource {
	public Indir<Resource> res;
	public MessageBuf sdt;
	public Tex num;
	private GSprite spr;
	private Object[] rawinfo;
	private List<ItemInfo> info;
	public final int count;
	private final MoonItemQuality.SlotCache moonQualCache = new MoonItemQuality.SlotCache();

	public Spec(Indir<Resource> res, Message sdt, int num, Object[] info) {
	    this.res = res;
	    this.sdt = new MessageBuf(sdt);
	    this.count = num;
	    if(num >= 0)
		this.num = new TexI(Utils.outline2(Text.render(Integer.toString(num), Color.WHITE).img, Utils.contrast(Color.WHITE)));
	    else
		this.num = null;
	    this.rawinfo = info;
	}

	public GSprite sprite() {
	    if(spr == null)
		spr = GSprite.create(this, res.get(), sdt.clone());;
	    return(spr);
	}

	public void draw(GOut g) {
	    try {
		sprite().draw(g);
	    } catch(Loading e) {}
	    if(num != null)
		g.aimage(num, Inventory.sqsz, 1.0, 1.0);
	    if(MoonConfig.qualityObjects) {
		try {
		    double q = MoonItemQuality.readQ(info());
		    if(q > 0)
			moonQualCache.draw(g, Inventory.sqsz, q);
		} catch(Exception ignored) {}
	    }
	}

	private int opt = 0;
	public boolean opt() {
	    if(opt == 0)
		opt = (ItemInfo.find(Optional.class, info()) != null) ? 1 : 2;
	    return(opt == 1);
	}

	public BufferedImage shorttip() {
	    List<ItemInfo> info = info();
	    if(info.isEmpty()) {
		Resource.Tooltip tt = res.get().layer(Resource.tooltip);
		if(tt == null)
		    return(null);
		return(Text.render(tt.t).img);
	    }
	    return(ItemInfo.shorttip(info()));
	}
	public BufferedImage longtip() {
	    List<ItemInfo> info = info();
	    BufferedImage img;
	    if(info.isEmpty()) {
		Resource.Tooltip tt = res.get().layer(Resource.tooltip);
		if(tt == null)
		    return(null);
		img = Text.render(tt.t).img;
	    } else {
		img = ItemInfo.longtip(info);
	    }
	    Resource.Pagina pg = res.get().layer(Resource.pagina);
	    if(pg != null)
		img = ItemInfo.catimgs(0, img, RichText.render("\n" + pg.text, 200).img);
	    return(img);
	}

	private Random rnd = null;
	public Random mkrandoom() {
	    if(rnd == null)
		rnd = new Random();
	    return(rnd);
	}
	public Resource getres() {return(res.get());}
	public <T> T context(Class<T> cl) {return(ctxr.context(cl, Makewindow.this));}

	public List<ItemInfo> info() {
	    if(info == null)
		info = ItemInfo.buildinfo(this, rawinfo);
	    return(info);
	}
	public Resource resource() {return(res.get());}
    }

    public static final KeyBinding kb_make = KeyBinding.get("make/one", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, 0));
    public static final KeyBinding kb_makeall = KeyBinding.get("make/all", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, KeyMatch.C));
    public Makewindow(String rcpnm) {
	qmodlLbl = Text.render(LocalizationManager.tr("makewindow.quality"));
	toollLbl = Text.render(LocalizationManager.tr("makewindow.tools"));
	this.rcpnm = rcpnm;
	this.recipeSearch = add(new TextEntry(RECIPE_LIST_SZ.x, "") {
		protected void changed() {
		    refilterRecipes();
		}
	    }, SEARCH_POS);
	this.recipeList = add(new RecipeList(RECIPE_LIST_SZ), RECIPE_LIST_POS);
	Button one = new Button(UI.scale(130), LocalizationManager.tr("makewindow.craft"), false);
	one.action(() -> startSingleCraft()).setgkey(kb_make);
	this.craftOneBtn = add(one, Coord.z);
	this.craftCount = add(new TextEntry(UI.scale(56), "1"), Coord.z);
	Button count = new Button(UI.scale(130), LocalizationManager.tr("craft.make_count"), false);
	count.action(() -> queueCraft(parseCraftCount()));
	this.craftCountBtn = add(count, Coord.z);
	Button all = new Button(UI.scale(130), LocalizationManager.tr("craft.make_max"), false);
	all.action(() -> wdgmsg("make", 1)).setgkey(kb_makeall);
	this.craftAllBtn = add(all, Coord.z);
	resize(ADV_SZ);
	relayoutButtons();
	updateActionButtons();
    }

    private void relayoutButtons() {
	int y = sz.y - craftOneBtn.sz.y - UI.scale(10);
	int x = RIGHT_POS.x + RIGHT_W - craftAllBtn.sz.x;
	craftAllBtn.move(Coord.of(x, y));
	x -= UI.scale(8) + craftCountBtn.sz.x;
	craftCountBtn.move(Coord.of(x, y));
	x -= UI.scale(6) + craftCount.sz.x;
	craftCount.move(Coord.of(x, y + UI.scale(1)));
	x -= UI.scale(8) + craftOneBtn.sz.x;
	craftOneBtn.move(Coord.of(x, y));
    }

    private void updateActionButtons() {
	boolean canCraft = (selectedRecipe == null) || selectedRecipe.unlocked;
	craftOneBtn.disable(!canCraft);
	craftCountBtn.disable(!canCraft);
	craftCount.setcanfocus(canCraft);
	craftAllBtn.disable(!canCraft);
    }

    private void syncSpecVisibility() {
	boolean show = (selectedRecipe == null) || selectedRecipe.unlocked;
	for(Widget w : inputs)
	    w.show(show);
	for(Widget w : outputs)
	    w.show(show);
    }

    private int parseCraftCount() {
	try {
	    return(Math.max(1, Integer.parseInt(craftCount.text().trim())));
	} catch(Exception e) {
	    return(1);
	}
    }

    private void startSingleCraft() {
	queuedCrafts = 0;
	queuedWaitFinish = false;
	queuedSawProgress = false;
	wdgmsg("make", 0);
    }

    private void queueCraft(int count) {
	if(count <= 0)
	    return;
	queuedCrafts = count;
	queuedWaitFinish = false;
	queuedSawProgress = false;
	queuedNextAt = 0;
	tryRunQueuedCraft();
    }

    private void tryRunQueuedCraft() {
	if(queuedCrafts <= 0)
	    return;
	GameUI gui = getparent(GameUI.class);
	if(gui == null)
	    return;
	double now = Utils.rtime();
	if(queuedWaitFinish) {
	    if(gui.prog != null) {
		queuedSawProgress = true;
		return;
	    }
	    if(now < queuedNextAt)
		return;
	    queuedWaitFinish = false;
	}
	wdgmsg("make", 0);
	queuedCrafts--;
	queuedWaitFinish = true;
	queuedSawProgress = false;
	queuedNextAt = now + 0.45;
    }

    private void tryApplyPendingRecipes() {
	Iterator<Map.Entry<Integer, List<MenuGrid.Pagina>>> it = pendingRecipes.entrySet().iterator();
	while(it.hasNext()) {
	    Map.Entry<Integer, List<MenuGrid.Pagina>> e = it.next();
	    int idx = e.getKey();
	    if(idx >= 0 && idx < inputs.size()) {
		inputs.get(idx).recipes(e.getValue());
		it.remove();
	    }
	}
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "inpop") {
	    tools.clear();
	    pendingRecipes.clear();
	    List<Spec> inputs = new ArrayList<>();
	    for(int i = 0; i < args.length;) {
		Indir<Resource> res = ui.sess.getresv(args[i++]);
		Message sdt = (args[i] instanceof byte[]) ? new MessageBuf((byte[])args[i++]) : MessageBuf.nil;
		int num = Utils.iv(args[i++]);
		Object[] info = {};
		if((i < args.length) && (args[i] instanceof Object[]))
		    info = (Object[])args[i++];
		inputs.add(new Spec(res, sdt, num, info));
	    }
	    ui.sess.glob.loader.defer(() -> {
		    List<Input> wdgs = new ArrayList<>();
		    int idx = 0;
		    for(Spec spec : inputs)
			wdgs.add(new Input(spec, idx++));
		    synchronized(ui) {
			for(Widget w : this.inputs)
			    w.destroy();
			Position pos = new Position(RIGHT_POS.x, INPUT_ICON_Y);
			SpecWidget prev = null;
			for(Input wdg : wdgs) {
			    if((prev != null) && (wdg.opt != false))
				pos = pos.adds(10, 0);
			    add(wdg, pos);
			    pos = pos.add(Inventory.sqsz.x, 0);
			    prev = wdg;
			}
			this.inputs = wdgs;
			layoutSpecWidgets();
			tryApplyPendingRecipes();
			syncSpecVisibility();
		    }
		}, null);
	} else if(msg == "opop") {
	    List<Spec> outputs = new ArrayList<Spec>();
	    for(int i = 0; i < args.length;) {
		Indir<Resource> res = ui.sess.getresv(args[i++]);
		Message sdt = (args[i] instanceof byte[]) ? new MessageBuf((byte[])args[i++]) : MessageBuf.nil;
		int num = Utils.iv(args[i++]);
		Object[] info = {};
		if((i < args.length) && (args[i] instanceof Object[]))
		    info = (Object[])args[i++];
		outputs.add(new Spec(res, sdt, num, info));
	    }
	    ui.sess.glob.loader.defer(() -> {
		    List<SpecWidget> wdgs = new ArrayList<>();
		    for(Spec spec : outputs)
			wdgs.add(new SpecWidget(spec));
		    synchronized(ui) {
			for(Widget w : this.outputs)
			    w.destroy();
			Position pos = new Position(RIGHT_POS.x, OUTPUT_ICON_Y);
			SpecWidget prev = null;
			for(SpecWidget wdg : wdgs) {
			    if((prev != null) && (wdg.opt != prev.opt))
				pos = pos.adds(10, 0);
			    add(wdg, pos);
			    pos = pos.add(Inventory.sqsz.x, 0);
			    prev = wdg;
			}
			this.outputs = wdgs;
			layoutSpecWidgets();
			syncSpecVisibility();
		    }
		}, null);
	} else if(msg == "qmod") {
	    List<Indir<Resource>> qmod = new ArrayList<Indir<Resource>>();
	    for(Object arg : args)
		qmod.add(ui.sess.getresv(arg));
	    this.qmod = qmod;
	} else if(msg == "tool") {
	    tools.add(ui.sess.getresv(args[0]));
	} else if(msg == "inprcps") {
	    int idx = Utils.iv(args[0]);
	    List<MenuGrid.Pagina> rcps = new ArrayList<>();
	    GameUI gui = getparent(GameUI.class);
	    if((gui != null) && (gui.menu != null)) {
		for(int a = 1; a < args.length; a++)
		    rcps.add(gui.menu.paginafor(ui.sess.getresv(args[a])));
	    }
	    pendingRecipes.put(idx, rcps);
	    tryApplyPendingRecipes();
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final Coord qmodsz = UI.scale(20, 20);
    private static final Map<Indir<Resource>, Tex> qmicons = new WeakHashMap<>();
    private static Tex qmicon(Indir<Resource> qm) {
	return(qmicons.computeIfAbsent(qm, res -> new TexI(PUtils.convolve(res.get().flayer(Resource.imgc).img, qmodsz, CharWnd.iconfilter))));
    }

    private static String localizedName(String text) {
	return LocalizationManager.autoTranslateProcessed((text == null) ? "" : text);
    }

    private static boolean looksLikeStation(Resource res) {
	if(res == null || res.name == null)
	    return false;
	String rn = res.name.toLowerCase(Locale.ROOT);
	if(rn.startsWith("gfx/terobjs/"))
	    return true;
	return MoonGobKind.stationKey(MoonGobKind.Kind.WORKSTATION, rn) != null;
    }

    private String specLabel(Spec spec) {
	if(spec == null)
	    return "";
	try {
	    Resource.Tooltip tt = spec.res.get().layer(Resource.tooltip);
	    String name = (tt != null) ? localizedName(tt.t) : spec.res.get().name;
	    String opt = spec.opt() ? (" " + LocalizationManager.tr("craft.optional")) : "";
	    return (spec.count > 0) ? String.format("%s ×%d%s", name, spec.count, opt) : (name + opt);
	} catch(Loading e) {
	    return "...";
	}
    }

    private String specQualityLabel(Spec spec) {
	double q = MoonCraft.specQuality(spec);
	if(q > 0)
	    return String.format(Locale.ROOT, " Q%.1f", q);
	return "";
    }

    private void layoutSpecWidgets() {
	int inX = RIGHT_POS.x;
	int outX = RIGHT_POS.x;
	for(int i = 0; i < inputs.size(); i++) {
	    Widget w = inputs.get(i);
	    w.move(Coord.of(inX + (i * (Inventory.sqsz.x + UI.scale(2))), INPUT_ICON_Y));
	}
	for(int i = 0; i < outputs.size(); i++) {
	    Widget w = outputs.get(i);
	    w.move(Coord.of(outX + (i * (Inventory.sqsz.x + UI.scale(2))), OUTPUT_ICON_Y));
	}
    }

    private static final class RecipeEntry {
	final MenuGrid.PagButton btn;
	final MoonCraftWiki.Entry wiki;
	final String name;
	final String search;
	final boolean craftLike;
	final String category;
	final boolean unlocked;

	RecipeEntry(MenuGrid.PagButton btn, boolean craftLike, String category) {
	    this.btn = btn;
	    this.wiki = null;
	    this.name = localizedName(btn.name());
	    this.search = name.toLowerCase(Locale.ROOT);
	    this.craftLike = craftLike;
	    this.category = category;
	    this.unlocked = true;
	}

	RecipeEntry(MoonCraftWiki.Entry wiki, String category) {
	    this.btn = null;
	    this.wiki = wiki;
	    this.name = localizedName(wiki.title);
	    this.search = name.toLowerCase(Locale.ROOT);
	    this.craftLike = true;
	    this.category = category;
	    this.unlocked = false;
	}
    }

    private static final class CategoryRow {
	final String name;
	CategoryRow(String name) {
	    this.name = name;
	}
    }

    private static final class SectionRow {
	final String name;
	SectionRow(String name) {
	    this.name = name;
	}
    }

    private boolean isCraftRootPagina(MenuGrid.Pagina pag) {
	if(pag == null)
	    return false;
	try {
	    Resource res = pag.res();
	    String rn = (res == null || res.name == null) ? "" : res.name.toLowerCase(Locale.ROOT);
	    String nm = localizedName(pag.button().name()).toLowerCase(Locale.ROOT);
	    return rn.contains("craft") || rn.contains("paginae/craft") || nm.equals("craft") || nm.equals("крафт");
	} catch(Loading ignored) {
	    return false;
	}
    }

    private String recipeCategory(MenuGrid.Pagina pag) {
	if(pag == null)
	    return LocalizationManager.tr("craft.category.other");
	try {
	    ArrayList<MenuGrid.Pagina> path = new ArrayList<>();
	    for(MenuGrid.Pagina cur = pag; cur != null; cur = cur.parent())
		path.add(cur);
	    Collections.reverse(path);
	    int craftRoot = -1;
	    for(int i = 0; i < path.size(); i++) {
		if(isCraftRootPagina(path.get(i))) {
		    craftRoot = i;
		    break;
		}
	    }
	    if(craftRoot >= 0) {
		if(path.size() > craftRoot + 2)
		    return localizedName(path.get(craftRoot + 1).button().name());
		return LocalizationManager.tr("craft.category.general");
	    }
	    if(path.size() > 1)
		return localizedName(path.get(path.size() - 2).button().name());
	} catch(Loading ignored) {
	}
	return LocalizationManager.tr("craft.category.other");
    }

    private boolean isCraftCandidate(MenuGrid.Pagina pag) {
	try {
	    for(MenuGrid.Pagina cur = pag; cur != null; cur = cur.parent()) {
		Resource res = cur.res();
		String rn = (res == null || res.name == null) ? "" : res.name.toLowerCase(Locale.ROOT);
		String nm = localizedName(cur.button().name()).toLowerCase(Locale.ROOT);
		if(rn.contains("craft") || rn.contains("paginae/craft") || nm.contains("craft") || nm.contains("крафт"))
		    return true;
	    }
	} catch(Loading ignored) {
	}
	return false;
    }

    private String recipeDigest(Collection<MenuGrid.Pagina> pages) {
	StringBuilder sb = new StringBuilder();
	for(MenuGrid.Pagina pag : pages) {
	    try {
		Resource res = pag.res();
		if(res != null && res.name != null)
		    sb.append(res.name).append('|');
	    } catch(Loading ignored) {
	    }
	}
	return sb.toString();
    }

    private void rebuildRecipeCatalog() {
	GameUI gui = getparent(GameUI.class);
	if(gui == null || gui.menu == null)
	    return;
	ArrayList<MenuGrid.Pagina> pages = new ArrayList<>();
	synchronized(gui.menu.paginae) {
	    pages.addAll(gui.menu.paginae);
	}
	String digest = recipeDigest(pages);
	if(digest.equals(lastRecipeDigest))
	    return;
	lastRecipeDigest = digest;
	Set<MenuGrid.Pagina> hasChildren = Collections.newSetFromMap(new IdentityHashMap<>());
	for(MenuGrid.Pagina pag : pages) {
	    try {
		MenuGrid.Pagina parent = pag.parent();
		if(parent != null)
		    hasChildren.add(parent);
	    } catch(Loading ignored) {
	    }
	}
	LinkedHashMap<String, RecipeEntry> dedup = new LinkedHashMap<>();
	boolean anyCraft = false;
	for(MenuGrid.Pagina pag : pages) {
	    if(pag instanceof MenuGrid.SpecialPagina)
		continue;
	    if(hasChildren.contains(pag))
		continue;
	    try {
		RecipeEntry entry = new RecipeEntry(pag.button(), isCraftCandidate(pag), recipeCategory(pag));
		anyCraft |= entry.craftLike;
		String key = (entry.btn.res != null && entry.btn.res.name != null) ? entry.btn.res.name : entry.name;
		dedup.putIfAbsent(key, entry);
	    } catch(Loading ignored) {
	    }
	}
	allRecipes.clear();
	HashSet<String> unlockedNames = new HashSet<>();
	for(RecipeEntry entry : dedup.values()) {
	    if(!anyCraft || entry.craftLike) {
		allRecipes.add(entry);
		unlockedNames.add(entry.name.toLowerCase(Locale.ROOT));
	    }
	}
	for(MoonCraftWiki.Entry wiki : MoonCraftWiki.all()) {
	    String name = localizedName(wiki.title);
	    if(name.isEmpty() || unlockedNames.contains(name.toLowerCase(Locale.ROOT)))
		continue;
	    String skill = (wiki.skill == null || wiki.skill.isEmpty()) ? LocalizationManager.tr("craft.category.other") : localizedName(wiki.skill);
	    allRecipes.add(new RecipeEntry(wiki, skill));
	}
	allRecipes.sort(Comparator
	    .comparing((RecipeEntry e) -> e.unlocked ? 0 : 1)
	    .thenComparing(e -> e.category, String.CASE_INSENSITIVE_ORDER)
	    .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
	refilterRecipes();
	if(selectedRecipe != null && !filteredRecipes.contains(selectedRecipe))
	    selectedRecipe = null;
	if(selectedRecipe == null && !rcpnm.isEmpty()) {
	    for(RecipeEntry entry : allRecipes) {
		if(entry.name.equalsIgnoreCase(localizedName(rcpnm))) {
		    selectedRecipe = entry;
		    break;
		}
	    }
	}
	recipeList.change(selectedRecipe);
    }

    private void refilterRecipes() {
	String needle = recipeSearch.text().trim().toLowerCase(Locale.ROOT);
	filteredRecipes.clear();
	for(RecipeEntry entry : allRecipes) {
	    if(needle.isEmpty() || entry.search.contains(needle))
		filteredRecipes.add(entry);
	}
	if((selectedRecipe != null) && !filteredRecipes.contains(selectedRecipe))
	    selectedRecipe = null;
	rebuildRecipeRows();
	recipeList.change(selectedRecipe);
	recipeList.reset();
	syncSpecVisibility();
	updateActionButtons();
    }

    private void rebuildRecipeRows() {
	recipeRows.clear();
	ArrayList<RecipeEntry> unlocked = new ArrayList<>();
	ArrayList<RecipeEntry> wiki = new ArrayList<>();
	for(RecipeEntry entry : filteredRecipes) {
	    if(entry.unlocked)
		unlocked.add(entry);
	    else
		wiki.add(entry);
	}
	addRecipeSection(LocalizationManager.tr("craft.section_available"), unlocked);
	addRecipeSection(LocalizationManager.tr("craft.section_wiki"), wiki);
    }

    private void addRecipeSection(String sectionName, List<RecipeEntry> entries) {
	if(entries.isEmpty())
	    return;
	recipeRows.add(new SectionRow(sectionName));
	LinkedHashMap<String, ArrayList<RecipeEntry>> grouped = new LinkedHashMap<>();
	for(RecipeEntry entry : entries)
	    grouped.computeIfAbsent(entry.category, k -> new ArrayList<>()).add(entry);
	for(Map.Entry<String, ArrayList<RecipeEntry>> e : grouped.entrySet()) {
	    String cat = e.getKey();
	    categoryOpen.putIfAbsent(cat, true);
	    recipeRows.add(new CategoryRow(cat));
	    if(Boolean.TRUE.equals(categoryOpen.get(cat)))
		recipeRows.addAll(e.getValue());
	}
    }

    private void selectRecipe(RecipeEntry entry) {
	if(entry == null)
	    return;
	selectedRecipe = entry;
	rcpnm = entry.name;
	recipeList.change(entry);
	syncSpecVisibility();
	updateActionButtons();
	if(entry.btn != null) {
	    try {
		entry.btn.use(new MenuGrid.Interaction(1, ui.modflags()));
	    } catch(Exception ignored) {
	    }
	}
    }

    private class RecipeList extends SListBox<Object, Widget> {
	RecipeList(Coord sz) {
	    super(sz, UI.scale(34), UI.scale(2));
	}

	protected List<? extends Object> items() {
	    return recipeRows;
	}

	protected Widget makeitem(Object item, int idx, Coord sz) {
	    if(item instanceof SectionRow) {
		SectionRow row = (SectionRow)item;
		return new ItemWidget<Object>(this, sz, item) {
		    public void draw(GOut g) {
			g.chcolor(66, 52, 112, 245);
			g.frect(Coord.z, sz);
			g.chcolor(220, 205, 255, 255);
			g.line(Coord.of(UI.scale(8), sz.y - UI.scale(3)), Coord.of(sz.x - UI.scale(8), sz.y - UI.scale(3)), 1);
			g.chcolor();
			g.aimage(CharWnd.attrf.render(row.name, new Color(245, 235, 255)).tex(), Coord.of(UI.scale(8), sz.y / 2), 0, 0.5);
		    }
		};
	    }
	    if(item instanceof CategoryRow) {
		CategoryRow row = (CategoryRow)item;
		return new ItemWidget<Object>(this, sz, item) {
		    public void draw(GOut g) {
			g.chcolor(52, 46, 84, 230);
			g.frect(Coord.z, sz);
			g.chcolor();
			boolean open = Boolean.TRUE.equals(categoryOpen.get(row.name));
			String txt = (open ? "[-] " : "[+] ") + row.name;
			g.aimage(CharWnd.attrf.render(txt, new Color(235, 220, 255)).tex(), Coord.of(UI.scale(6), sz.y / 2), 0, 0.5);
		    }
		    public boolean mousedown(MouseDownEvent ev) {
			if(ev.b == 1) {
			    categoryOpen.put(row.name, !Boolean.TRUE.equals(categoryOpen.get(row.name)));
			    rebuildRecipeRows();
			    recipeList.reset();
			    return true;
			}
			return true;
		    }
		};
	    }
	    RecipeEntry el = (RecipeEntry)item;
	    return new ItemWidget<Object>(this, sz, item) {
		{
		    add(new IconText(sz) {
			protected BufferedImage img() {return (el.btn == null) ? null : el.btn.img();}
			protected String text() {return el.unlocked ? el.name : ("[Wiki] " + el.name);}
			protected int margin() {return UI.scale(4);}
			protected Text.Foundry foundry() {return CharWnd.attrf;}
		    }, Coord.z);
		}

		public boolean mousedown(MouseDownEvent ev) {
		    if(ev.propagate(this))
			return true;
		    list.change(item);
		    if(ev.b == 1) {
			selectRecipe(el);
			return true;
		    }
		    return true;
		}
	    };
	}
    }

    public static class SpecWidget extends Widget {
	public final Spec spec;
	public final boolean opt;

	public SpecWidget(Spec spec) {
	    super(invsq.sz());
	    this.spec = spec;
	    opt = spec.opt();
	}

	public void draw(GOut g) {
	    if(opt) {
		g.chcolor(0, 255, 0, 255);
		g.image(invsq, Coord.z);
		g.chcolor();
	    } else {
		g.image(invsq, Coord.z);
	    }
	    spec.draw(g);
	}

	private double hoverstart;
	Indir<Object> stip, ltip;
	public Object tooltip(Coord c, Widget prev) {
	    double now = Utils.rtime();
	    if(prev == this) {
	    } else if(prev instanceof SpecWidget) {
		double ps = ((SpecWidget)prev).hoverstart;
		hoverstart = (now - ps < 1.0) ? now : ps;
	    } else {
		hoverstart = now;
	    }
	    if(now - hoverstart < 1.0) {
		if(stip == null) {
		    BufferedImage tip = spec.shorttip();
		    Tex tt = (tip == null) ? null : new TexI(tip);
		    stip = () -> tt;
		}
		return(stip);
	    } else {
		if(ltip == null) {
		    BufferedImage tip = spec.longtip();
		    Tex tt = (tip == null) ? null : new TexI(tip);
		    ltip = () -> tt;
		}
		return(ltip);
	    }
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(spec.spr != null)
		spec.spr.tick(dt);
	}
    }

    public class Input extends SpecWidget {
	public final int idx;
	private List<MenuGrid.Pagina> rpag = null;
	private Coord cc = null;

	public Input(Spec spec, int idx) {
	    super(spec);
	    this.idx = idx;
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b == 1) {
		if(rpag == null)
		    Makewindow.this.wdgmsg("findrcps", idx);
		this.cc = ev.c;
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if((cc != null) && (rpag != null)) {
		if(!rpag.isEmpty()) {
		    /* Text-only list: recipe icons may be null for some paginae (menu would be empty). */
		    SListMenu.of(UI.scale(250, 120), rpag,
				 pag -> pag.button().name(),
				 pag -> pag.button().use(new MenuGrid.Interaction(1, ui.modflags())))
			.addat(this, cc.add(UI.scale(5, 5))).tick(dt);
		}
		cc = null;
	    }
	}

	public void recipes(List<MenuGrid.Pagina> pag) {
	    rpag = pag;
	}
    }

    public void draw(GOut g) {
	int x = RIGHT_POS.x;
	int qmy = INPUT_ICON_Y + Inventory.sqsz.y + UI.scale(10);
	int outy = OUTPUT_ICON_Y + Inventory.sqsz.y + UI.scale(10);
	boolean liveRecipe = (selectedRecipe == null) || selectedRecipe.unlocked;
	Color panel = new Color(18, 16, 30, 130);
	g.chcolor(panel);
	g.frect(SEARCH_POS.sub(UI.scale(4, 20)), Coord.of(RECIPE_LIST_SZ.x + UI.scale(8), RECIPE_LIST_SZ.y + UI.scale(48)));
	g.frect(RIGHT_POS.sub(UI.scale(6, 4)), Coord.of(RIGHT_W + UI.scale(12), sz.y - UI.scale(12)));
	g.chcolor();
	g.atext(LocalizationManager.tr("craft.recipes"), SEARCH_POS.add(UI.scale(0, -18)), 0, 0);
	String title = (selectedRecipe != null) ? selectedRecipe.name : localizedName(rcpnm);
	g.atext(title, RIGHT_POS, 0, 0);
	g.chcolor(180, 170, 220, 255);
	g.line(Coord.of(RIGHT_POS.x, UI.scale(20)), Coord.of(RIGHT_POS.x + RIGHT_W, UI.scale(20)), 1);
	g.chcolor();
	if(liveRecipe) {
	    g.atext(LocalizationManager.tr("craft.materials"), Coord.of(RIGHT_POS.x, UI.scale(48)), 0, 0);
	    g.atext(LocalizationManager.tr("craft.results"), Coord.of(RIGHT_POS.x, UI.scale(132)), 0, 0);
	}
	if(liveRecipe && !qmod.isEmpty()) {
	    g.aimage(qmodlLbl.tex(), new Coord(x, qmy + (qmodsz.y / 2)), 0, 0.5);
	    x += qmodlLbl.sz().x + UI.scale(5);
	    x = Math.max(x, RIGHT_POS.x);
	    qmx = x;
	    for(Indir<Resource> qm : qmod) {
		try {
		    Tex t = qmicon(qm);
		    g.image(t, new Coord(x, qmy));
		    x += t.sz().x + UI.scale(1);
		} catch(Loading l) {
		}
	    }
	    x += UI.scale(25);
	}
	if(liveRecipe && !tools.isEmpty()) {
	    g.aimage(toollLbl.tex(), new Coord(x, qmy + (qmodsz.y / 2)), 0, 0.5);
	    x += toollLbl.sz().x + UI.scale(5);
	    x = Math.max(x, RIGHT_POS.x);
	    toolx = x;
	    for(Indir<Resource> tool : tools) {
		try {
		    Tex t = qmicon(tool);
		    g.image(t, new Coord(x, qmy));
		    x += t.sz().x + UI.scale(1);
		} catch(Loading l) {
		}
	    }
	    x += UI.scale(25);
	}
	super.draw(g);
	drawDetails(g, outy + UI.scale(38));
    }

    public void tick(double dt) {
	super.tick(dt);
	rebuildRecipeCatalog();
	layoutSpecWidgets();
	relayoutButtons();
	GameUI gui = getparent(GameUI.class);
	if(Utils.rtime() >= nextStockRefresh) {
	    stock = MoonCraftLedger.snapshot(gui);
	    nextStockRefresh = Utils.rtime() + 0.5;
	}
	if((queuedCrafts > 0) && (gui != null))
	    tryRunQueuedCraft();
    }
    public Object tooltip(Coord mc, Widget prev) {
	Spec tspec = null;
	Coord c;
	int qmy = INPUT_ICON_Y + Inventory.sqsz.y + UI.scale(10);
	if(!qmod.isEmpty()) {
	    c = new Coord(qmx, qmy);
	    for(Indir<Resource> qm : qmod) {
		Coord tsz = qmicon(qm).sz();
		if(mc.isect(c, tsz))
		    return(LocalizationManager.autoTranslateProcessed(qm.get().flayer(Resource.tooltip).t));
		c = c.add(tsz.x + UI.scale(1), 0);
	    }
	}
	if(!tools.isEmpty()) {
	    c = new Coord(toolx, qmy);
	    for(Indir<Resource> tool : tools) {
		Coord tsz = qmicon(tool).sz();
		if(mc.isect(c, tsz))
		    return(LocalizationManager.autoTranslateProcessed(tool.get().flayer(Resource.tooltip).t));
		c = c.add(tsz.x + UI.scale(1), 0);
	    }
	}
	return(super.tooltip(mc, prev));
    }

    private String selectedDescription() {
	if((selectedRecipe != null) && !selectedRecipe.unlocked && (selectedRecipe.wiki != null))
	    return(selectedRecipe.wiki.description);
	try {
	    if(!outputs.isEmpty()) {
		Resource.Pagina pg = outputs.get(0).spec.res.get().layer(Resource.pagina);
		if((pg != null) && !pg.text.trim().isEmpty())
		    return(LocalizationManager.autoTranslateProcessed(pg.text));
	    }
	    if((selectedRecipe != null) && (selectedRecipe.btn != null)) {
		Resource.Pagina pg = selectedRecipe.btn.res.layer(Resource.pagina);
		if((pg != null) && !pg.text.trim().isEmpty())
		    return(LocalizationManager.autoTranslateProcessed(pg.text));
	    }
	} catch(Loading ignored) {
	}
	return("");
    }

    private int drawWrappedLine(GOut g, Text.Foundry fnd, String text, Color col, int x, int y, int width) {
	if((text == null) || text.trim().isEmpty())
	    return(y);
	BufferedImage img = fnd.renderwrap(text, col, width).img;
	g.image(img, Coord.of(x, y));
	return(y + img.getHeight() + UI.scale(4));
    }

    private void drawDetails(GOut g, int topy) {
	Text.Foundry small = new Text.Foundry(Text.sans, UI.scale(11)).aa(true);
	int leftX = RIGHT_POS.x;
	int rightX = RIGHT_POS.x + UI.scale(284);
	int colW = UI.scale(258);
	int leftY = topy;
	int rightY = topy;
	if(selectedRecipe == null) {
	    g.image(small.render(LocalizationManager.tr("craft.no_recipe"), Color.WHITE).img, Coord.of(leftX, leftY));
	    return;
	}
	boolean liveRecipe = selectedRecipe.unlocked;

	g.atext(LocalizationManager.tr("craft.description"), Coord.of(leftX, leftY), 0, 0);
	leftY += UI.scale(18);
	String desc = selectedDescription();
	if(desc.isEmpty() && !liveRecipe && (selectedRecipe.wiki != null))
	    desc = selectedRecipe.wiki.how;
	leftY = drawWrappedLine(g, small, desc.isEmpty() ? LocalizationManager.tr("craft.no_desc") : desc, Color.WHITE, leftX, leftY, colW);
	leftY += UI.scale(8);

	g.atext(LocalizationManager.tr("craft.materials"), Coord.of(leftX, leftY), 0, 0);
	leftY += UI.scale(18);
	if(liveRecipe) {
	    for(Input in : inputs) {
		int available = stock.countFor(in.spec);
		String line = specLabel(in.spec) + specQualityLabel(in.spec);
		if(available > 0)
		    line += "  [" + available + "]";
		leftY = drawWrappedLine(g, small, line, in.spec.opt() ? new Color(190, 190, 160) : Color.WHITE, leftX, leftY, colW);
	    }
	} else if(selectedRecipe.wiki != null) {
	    leftY = drawWrappedLine(g, small, selectedRecipe.wiki.materials, Color.WHITE, leftX, leftY, colW);
	}
	leftY += UI.scale(8);

	g.atext(LocalizationManager.tr("craft.results"), Coord.of(leftX, leftY), 0, 0);
	leftY += UI.scale(18);
	if(liveRecipe) {
	    for(SpecWidget out : outputs) {
		String line = specLabel(out.spec) + specQualityLabel(out.spec);
		leftY = drawWrappedLine(g, small, line, new Color(210, 235, 210), leftX, leftY, colW);
	    }
	} else {
	    leftY = drawWrappedLine(g, small, selectedRecipe.name, new Color(210, 235, 210), leftX, leftY, colW);
	}

	g.atext(LocalizationManager.tr("craft.characteristics"), Coord.of(rightX, rightY), 0, 0);
	rightY += UI.scale(18);
	if(liveRecipe) {
	    try {
		String prereq = selectedRecipe.btn.act().req;
		if(prereq != null && !prereq.trim().isEmpty())
		    rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.prereq"), localizedName(prereq)), Color.WHITE, rightX, rightY, colW);
	    } catch(Loading ignored) {
	    }
	    double soft = MoonCraft.softcapRms(ui.sess.glob, qmod);
	    double hard = MoonCraft.hardcapMin(inputs, stock);
	    double expected = MoonCraft.expectedQuality(soft, hard);
	    if(soft > 0) {
		rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.softcap"), soft), Color.WHITE, rightX, rightY, colW);
		for(Indir<Resource> ir : qmod) {
		    try {
			Resource res = ir.get();
			int sv = MoonCraft.skillValue(ui.sess.glob, res);
			rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.skillline"), MoonCraft.qmodLabel(res), sv), new Color(220, 220, 200), rightX, rightY, colW);
		    } catch(Loading ignored) {
		    }
		}
		if(hard > 0)
		    rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.hardcap"), hard), new Color(255, 200, 160), rightX, rightY, colW);
		else
		    rightY = drawWrappedLine(g, small, LocalizationManager.tr("craft.unknown_hardcap"), new Color(200, 170, 150), rightX, rightY, colW);
		rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.expected"), expected), new Color(180, 255, 180), rightX, rightY, colW);
	    }
	    int maxCraft = MoonCraft.maxCraftable(inputs, stock);
	    if(maxCraft > 0)
		rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.max_avail"), maxCraft), new Color(190, 220, 255), rightX, rightY, colW);
	} else if(selectedRecipe.wiki != null) {
	    if((selectedRecipe.wiki.skill != null) && !selectedRecipe.wiki.skill.isEmpty())
		rightY = drawWrappedLine(g, small, String.format(LocalizationManager.tr("craft.unlock_skill"), localizedName(selectedRecipe.wiki.skill)), Color.WHITE, rightX, rightY, colW);
	    if((selectedRecipe.wiki.how != null) && !selectedRecipe.wiki.how.isEmpty())
		rightY = drawWrappedLine(g, small, selectedRecipe.wiki.how, new Color(210, 210, 190), rightX, rightY, colW);
	}
	rightY += UI.scale(8);

	g.atext(LocalizationManager.tr("craft.reference"), Coord.of(rightX, rightY), 0, 0);
	rightY += UI.scale(18);
	if(liveRecipe) {
	    ArrayList<String> stationNames = new ArrayList<>();
	    ArrayList<String> toolNames = new ArrayList<>();
	    for(Indir<Resource> tool : tools) {
		try {
		    Resource res = tool.get();
		    String label = MoonCraft.resourceLabel(tool);
		    if(looksLikeStation(res))
			stationNames.add(label);
		    else
			toolNames.add(label);
		} catch(Loading ignored) {
		}
	    }
	    rightY = drawWrappedLine(g, small, LocalizationManager.tr("craft.tools_ref") + ": " + (toolNames.isEmpty() ? LocalizationManager.tr("craft.no_tool") : String.join(", ", toolNames)), Color.WHITE, rightX, rightY, colW);
	    rightY = drawWrappedLine(g, small, LocalizationManager.tr("craft.station_ref") + ": " + (stationNames.isEmpty() ? LocalizationManager.tr("craft.no_station") : String.join(", ", stationNames)), Color.WHITE, rightX, rightY, colW);
	} else if(selectedRecipe.wiki != null) {
	    rightY = drawWrappedLine(g, small, LocalizationManager.tr("craft.station_ref") + ": " + ((selectedRecipe.wiki.reference == null || selectedRecipe.wiki.reference.isEmpty()) ? LocalizationManager.tr("craft.no_station") : selectedRecipe.wiki.reference), Color.WHITE, rightX, rightY, colW);
	}
    }

    public static class Optional extends ItemInfo.Tip {
	public static final Text text = RichText.render("$i{Optional}", 0);
	public Optional(Owner owner) {
	    super(owner);
	}

	public BufferedImage tipimg() {
	    return(text.img);
	}

	public Tip shortvar() {return(this);}
    }

    public static class MakePrep extends ItemInfo implements GItem.ColorInfo, GItem.ContentsInfo {
	private final static Color olcol = new Color(0, 255, 0, 64);
	public MakePrep(Owner owner) {
	    super(owner);
	}

	public Color olcol() {
	    return(olcol);
	}

	public void propagate(List<ItemInfo> buf, Owner outer) {
	    if(ItemInfo.find(MakePrep.class, buf) == null)
		buf.add(new MakePrep(outer));
	}
    }
}
