package haven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static haven.OCache.posres;

public final class MoonAutoDrop {
    private MoonAutoDrop() {}

    public static final class Category {
	public final String id;
	public final String ru;
	public final String en;
	public final String ruDesc;
	public final String enDesc;
	public final String[] tokens;
	/** If true, match resource basename (or /token suffix), not arbitrary substring. */
	public final boolean exactTokens;
	/** Inventory object resource for list/detail preview (e.g. gfx/invobjs/granite). */
	public final String previewRes;
	/** Shared key for collapsible block in auto-drop UI; {@code null} = not grouped. */
	public final String foldGroup;
	/** If true, this row is the umbrella header with a fold toggle. */
	public final boolean foldHeader;

	private Category(String id, String ru, String en, String ruDesc, String enDesc,
	    boolean exactTokens, String previewRes,
	    String foldGroup, boolean foldHeader,
	    String... tokens) {
	    this.id = id;
	    this.ru = ru;
	    this.en = en;
	    this.ruDesc = ruDesc;
	    this.enDesc = enDesc;
	    this.exactTokens = exactTokens;
	    this.previewRes = (previewRes != null && !previewRes.isEmpty()) ? previewRes : null;
	    this.foldGroup = foldGroup;
	    this.foldHeader = foldHeader;
	    this.tokens = tokens;
	}

	public String label() {
	    return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
	}

	public String desc() {
	    return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ruDesc : enDesc;
	}
    }

    private static void addStone(List<Category> L, String id, String ru, String en, String token) {
	String prev = "gfx/invobjs/" + token;
	L.add(new Category(id, ru, en,
	    "Камень: " + token + ".",
	    "Stone: " + token + ".",
	    true, prev, "stone", false, token));
    }

    private static void addOre(List<Category> L, String id, String ru, String en, String token) {
	String prev = "gfx/invobjs/" + token;
	L.add(new Category(id, ru, en,
	    "Руда / минерал: " + token + ".",
	    "Ore / mineral: " + token + ".",
	    true, prev, "ore", false, token));
    }

    private static Category umbrella(String id, String ru, String en, String ruD, String enD,
	String previewRes, String... tokens) {
	String fg = id.endsWith("_any") ? id.substring(0, id.length() - 4) : id;
	return new Category(id, ru, en, ruD, enD, true, previewRes, fg, true, tokens);
    }

    public static final List<Category> CATEGORIES;
    private static final Map<String, String> LEGACY_IDS;

	static {
	Map<String, String> leg = new HashMap<>();
	leg.put("stone", "stone_any");
	leg.put("ore", "ore_any");
	leg.put("coal", "coal_any");
	leg.put("claysoil", "soil_any");
	leg.put("bones", "bones_any");
	leg.put("mushrooms", "mushroom_any");
	LEGACY_IDS = Collections.unmodifiableMap(leg);

	List<Category> L = new ArrayList<>();

	/* --- Stones (per rock type) — basenames from common H&H / client lists --- */
	String[][] stones = {
	    {"st_granite", "Гранит", "Granite", "granite"},
	    {"st_gneiss", "Гнейс", "Gneiss", "gneiss"},
	    {"st_basalt", "Базальт", "Basalt", "basalt"},
	    {"st_schist", "Сланец", "Schist", "schist"},
	    {"st_dolomite", "Доломит", "Dolomite", "dolomite"},
	    {"st_limestone", "Известняк", "Limestone", "limestone"},
	    {"st_sandstone", "Песчаник", "Sandstone", "sandstone"},
	    {"st_porphyry", "Порфир", "Porphyry", "porphyry"},
	    {"st_feldspar", "Полевой шпат", "Feldspar", "feldspar"},
	    {"st_quartz", "Кварц", "Quartz", "quartz"},
	    {"st_flint", "Кремень", "Flint", "flint"},
	    {"st_marble", "Мрамор", "Marble", "marble"},
	    {"st_hornblende", "Роговая обманка", "Hornblende", "hornblende"},
	    {"st_zincspar", "Цинковый шпат", "Zincspar", "zincspar"},
	    {"st_apatite", "Апатит", "Apatite", "apatite"},
	    {"st_sodalite", "Содалит", "Sodalite", "sodalite"},
	    {"st_fluorospar", "Плавиковый шпат", "Fluorospar", "fluorospar"},
	    {"st_soapstone", "Мыльный камень", "Soapstone", "soapstone"},
	    {"st_olivine", "Оливин", "Olivine", "olivine"},
	    {"st_gabbro", "Габбро", "Gabbro", "gabbro"},
	    {"st_alabaster", "Алебастр", "Alabaster", "alabaster"},
	    {"st_microlite", "Микролит", "Microlite", "microlite"},
	    {"st_mica", "Слюда", "Mica", "mica"},
	    {"st_kyanite", "Кианит", "Kyanite", "kyanite"},
	    {"st_corund", "Корунд", "Corundum", "corund"},
	    {"st_orthoclase", "Ортоклаз", "Orthoclase", "orthoclase"},
	    {"st_breccia", "Брекчия", "Breccia", "breccia"},
	    {"st_diabase", "Диабаз", "Diabase", "diabase"},
	    {"st_arkose", "Аркоз", "Arkose", "arkose"},
	    {"st_diorite", "Диорит", "Diorite", "diorite"},
	    {"st_slate", "Шифер", "Slate", "slate"},
	    {"st_jasper", "Яшма", "Jasper", "jasper"},
	    {"st_rhyolite", "Риолит", "Rhyolite", "rhyolite"},
	    {"st_pegmatite", "Пегматит", "Pegmatite", "pegmatite"},
	    {"st_greenschist", "Зелёный сланец", "Greenschist", "greenschist"},
	    {"st_eclogite", "Эклогит", "Eclogite", "eclogite"},
	    {"st_pumice", "Пемза", "Pumice", "pumice"},
	    {"st_serpentine", "Серпентин", "Serpentine", "serpentine"},
	    {"st_chert", "Кремнёвый сланец", "Chert", "chert"},
	    {"st_graywacke", "Граувакка", "Graywacke", "graywacke"},
	    {"st_halite", "Каменная соль", "Rock salt (halite)", "halite"},
	    {"st_sunstone", "Солнечный камень", "Sunstone", "sunstone"},
	};
	List<String> stoneToks = new ArrayList<>();
	for(String[] row : stones)
	    stoneToks.add(row[3]);
	L.add(umbrella("stone_any",
	    "Весь камень", "All stone",
	    "Все перечисленные породы одним переключателем.",
	    "All listed rock types in one toggle.",
	    "gfx/invobjs/granite",
	    stoneToks.toArray(new String[0])));
	for(String[] row : stones)
	    addStone(L, row[0], row[1], row[2], row[3]);

	/* --- Ores & ore-like minerals --- */
	String[][] ores = {
	    {"ore_hematite", "Гематит", "Hematite", "hematite"},
	    {"ore_magnetite", "Магнетит", "Magnetite", "magnetite"},
	    {"ore_limonite", "Лимонит", "Limonite", "limonite"},
	    {"ore_chalcopyrite", "Халькопирит", "Chalcopyrite", "chalcopyrite"},
	    {"ore_malachite", "Малахит", "Malachite", "malachite"},
	    {"ore_cassiterite", "Касситерит", "Cassiterite", "cassiterite"},
	    {"ore_galena", "Галена", "Galena", "galena"},
	    {"ore_peacockore", "Павлинья руда", "Peacock ore", "peacockore"},
	    {"ore_ilmenite", "Ильменит", "Ilmenite", "ilmenite"},
	    {"ore_cuprite", "Куприт", "Cuprite", "cuprite"},
	    {"ore_leadglance", "Свинцовый блеск", "Lead glance", "leadglance"},
	    {"ore_hornsilver", "Роговое серебро", "Horn silver", "hornsilver"},
	    {"ore_argentite", "Аргентит", "Argentite", "argentite"},
	    {"ore_cinnabar", "Киноварь", "Cinnabar", "cinnabar"},
	    {"ore_petzite", "Пецит", "Petzite", "petzite"},
	    {"ore_sylvanite", "Сильванит", "Sylvanite", "sylvanite"},
	    {"ore_nagyagite", "Надьягит", "Nagyagite", "nagyagite"},
	};
	List<String> oreToks = new ArrayList<>();
	for(String[] row : ores)
	    oreToks.add(row[3]);
	L.add(umbrella("ore_any",
	    "Вся руда", "All ore",
	    "Все перечисленные руды и рудные минералы.",
	    "All listed ores and ore minerals.",
	    "gfx/invobjs/hematite",
	    oreToks.toArray(new String[0])));
	for(String[] row : ores)
	    addOre(L, row[0], row[1], row[2], row[3]);

	/* --- Coal --- */
	L.add(umbrella("coal_any",
	    "Весь уголь", "All coal",
	    "И blackcoal, и coal.",
	    "Both blackcoal and coal.",
	    "gfx/invobjs/blackcoal",
	    "blackcoal", "coal"));
	L.add(new Category("coal_black", "Чёрный уголь", "Black coal",
	    "Чёрные куски угля (blackcoal).",
	    "Black coal chunks (blackcoal).",
	    true, "gfx/invobjs/blackcoal", "coal", false, "blackcoal"));
	L.add(new Category("coal_brown", "Уголь (обычный)", "Coal",
	    "Ресурс coal, если отличается от blackcoal на сервере.",
	    "Resource coal where distinct from blackcoal.",
	    true, "gfx/invobjs/coal", "coal", false, "coal"));

	/* --- Soil / loose (substring: paths vary) --- */
	L.add(umbrella("soil_any",
	    "Глина и грунт", "Clay and soil",
	    "Глина, земля, песок, грязь.",
	    "Clay, soil, sand, mud.",
	    "gfx/invobjs/clay",
	    "clay", "soil", "earth", "sand", "mud", "dirt"));
	L.add(new Category("soil_clay", "Глина", "Clay",
	    "Глина и глиняные комья.",
	    "Clay lumps.",
	    false, "gfx/invobjs/clay", "soil", false, "clay"));
	L.add(new Category("soil_sand", "Песок", "Sand",
	    "Песок, сыпучий.",
	    "Sand.",
	    false, "gfx/invobjs/sand", "soil", false, "sand"));
	L.add(new Category("soil_earth", "Земля", "Earth / soil",
	    "Земля, почва.",
	    "Earth, topsoil.",
	    false, "gfx/invobjs/soil", "soil", false, "soil", "earth"));
	L.add(new Category("soil_mud", "Грязь", "Mud",
	    "Грязь, илистый грунт.",
	    "Mud.",
	    false, "gfx/invobjs/mud", "soil", false, "mud"));

	/* --- Bones & junk --- */
	L.add(umbrella("bones_any",
	    "Кости и мусор", "Bones and junk",
	    "Кости, черепа, зубы и похожий шахтный мусор.",
	    "Bones, skulls, teeth and similar mine junk.",
	    "gfx/invobjs/bone",
	    "bone", "bones", "skull", "tooth", "teeth"));

	/* --- Mushrooms --- */
	L.add(umbrella("mushroom_any",
	    "Грибы", "Mushrooms",
	    "Грибы, трюфели, пещерные грибы.",
	    "Mushrooms, truffles, cave fungus.",
	    "gfx/invobjs/mushroom",
	    "mushroom", "shroom", "truffle", "fung"));

	/* --- Mine curios & oddities --- */
	L.add(umbrella("cur_any",
	    "Курьёзы шахты", "Mine curios",
	    "Catgold, окаменелости, странные кристаллы, quarry quartz.",
	    "Catgold, petrified shell, strange crystal, quarry quartz.",
	    "gfx/invobjs/catgold",
	    "catgold", "petrifiedshell", "strangecrystal", "quarryquartz"));
	L.add(new Category("cur_catgold", "Кошачье золото", "Cat gold",
	    "Редкая находка catgold.", "Rare find catgold.",
	    true, "gfx/invobjs/catgold", "cur", false, "catgold"));
	L.add(new Category("cur_petrifiedshell", "Окаменелая ракушка", "Petrified shell",
	    "Окаменелости.", "Petrified shell.",
	    true, "gfx/invobjs/petrifiedshell", "cur", false, "petrifiedshell"));
	L.add(new Category("cur_strangecrystal", "Странный кристалл", "Strange crystal",
	    "Странный кристалл.", "Strange crystal.",
	    true, "gfx/invobjs/strangecrystal", "cur", false, "strangecrystal"));
	L.add(new Category("cur_quarryquartz", "Карьерный кварц", "Quarry quartz",
	    "Quarry quartz.", "Quarry quartz.",
	    true, "gfx/invobjs/quarryquartz", "cur", false, "quarryquartz"));

	CATEGORIES = Collections.unmodifiableList(L);
    }

    private static final double STEP_SEC = 0.30;
    private static final double FAIL_RESET_SEC = 2.5;
    /** When nothing to do (empty hand, no match, etc.), do not scan inventory every frame. */
    private static final double IDLE_POLL_SEC = 0.55;

    private static double nextStep = 0;
    private static double pendingSince = 0;
    private static boolean pendingDrop = false;
    private static volatile String selectedCacheRaw = null;
    private static volatile Set<String> selectedCache = Collections.emptySet();

    public static Set<String> selectedIds() {
	String raw = MoonConfig.mineAutoDropCategories;
	if(raw == null)
	    raw = "";
	String cachedRaw = selectedCacheRaw;
	Set<String> cached = selectedCache;
	if(raw.equals(cachedRaw))
	    return cached;
	Set<String> out = new LinkedHashSet<>();
	if(!raw.isBlank()) {
	    for(String tok : raw.split(",")) {
		String id = tok.trim().toLowerCase(Locale.ROOT);
		if(id.isEmpty())
		    continue;
		id = LEGACY_IDS.getOrDefault(id, id);
		out.add(id);
	    }
	}
	Set<String> fixed = Collections.unmodifiableSet(out);
	selectedCacheRaw = raw;
	selectedCache = fixed;
	return fixed;
    }

    public static boolean isSelected(String id) {
	return selectedIds().contains(id);
    }

    public static void setSelected(String id, boolean on) {
	Set<String> ids = new LinkedHashSet<>(selectedIds());
	if(on)
	    ids.add(id);
	else
	    ids.remove(id);
	saveSelected(ids);
    }

    public static void saveSelected(Set<String> ids) {
	List<String> ordered = new ArrayList<>();
	for(Category cat : CATEGORIES) {
	    if(ids.contains(cat.id))
		ordered.add(cat.id);
	}
	String raw = String.join(",", ordered);
	MoonConfig.setMineAutoDropCategories(raw);
	selectedCacheRaw = raw;
	selectedCache = Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    public static void tick(GameUI gui, double dt) {
	if(gui == null || !MoonConfig.mineAutoDropEnabled) {
	    pendingDrop = false;
	    nextStep = 0;
	    return;
	}
	if(gui.maininv == null || gui.map == null) {
	    scheduleIdle(Utils.rtime());
	    return;
	}
	double now = Utils.rtime();
	if(now < nextStep)
	    return;

	if(pendingDrop) {
	    if(!gui.hand.isEmpty()) {
		if(dropHandToFeet(gui)) {
		    nextStep = now + STEP_SEC;
		    pendingSince = now;
		    return;
		}
	    }
	    if(gui.hand.isEmpty() || (now - pendingSince > FAIL_RESET_SEC)) {
		pendingDrop = false;
	    }
	    nextStep = now + STEP_SEC;
	    return;
	}

	if(!gui.hand.isEmpty()) {
	    scheduleIdle(now);
	    return;
	}
	GItem item = findDropItem(gui.maininv);
	if(item == null) {
	    scheduleIdle(now);
	    return;
	}
	item.wdgmsg("take", Coord.z);
	pendingDrop = true;
	pendingSince = now;
	nextStep = now + STEP_SEC;
    }

    public static boolean isBusy() {
	return pendingDrop;
    }

    public static String statusSummary() {
	int count = selectedIds().size();
	if(!MoonConfig.mineAutoDropEnabled)
	    return("disabled");
	if(count <= 0)
	    return("enabled, no categories");
	if(pendingDrop)
	    return("dropping item (" + count + " cats)");
	return("armed (" + count + " cats)");
    }

    private static void scheduleIdle(double now) {
	nextStep = now + IDLE_POLL_SEC;
    }

    private static GItem findDropItem(Inventory inv) {
	Set<String> ids = selectedIds();
	if(ids.isEmpty())
	    return null;
	try {
	    for(GItem item : new ArrayList<>(inv.wmap.keySet())) {
		if(item == null || item.ui == null || item.parent == null)
		    continue;
		if(matchesAny(item, ids))
		    return item;
	    }
	} catch(Exception ignored) {
	}
	return null;
    }

    private static boolean matchesAny(GItem item, Set<String> ids) {
	for(Category cat : CATEGORIES) {
	    if(ids.contains(cat.id) && matches(item, cat))
		return true;
	}
	return false;
    }

    private static boolean matches(GItem item, Category cat) {
	String name = itemName(item);
	if(name.isEmpty())
	    return false;
	String base = itemBaseName(name);
	for(String token : cat.tokens) {
	    if(cat.exactTokens) {
		if(base.equals(token))
		    return true;
		if(name.endsWith("/" + token))
		    return true;
	    } else {
		if(name.contains(token))
		    return true;
	    }
	}
	return false;
    }

    private static String itemBaseName(String fullName) {
	int s = fullName.lastIndexOf('/');
	return s < 0 ? fullName : fullName.substring(s + 1);
    }

    private static String itemName(GItem item) {
	try {
	    Resource res = item.getres();
	    if(res != null && res.name != null)
		return res.name.toLowerCase(Locale.ROOT);
	} catch(Exception ignored) {
	}
	return "";
    }

    private static boolean dropHandToFeet(GameUI gui) {
	try {
	    Gob pl = gui.map.player();
	    if(pl == null)
		return false;
	    Coord3f sc = gui.map.screenxf(gui.map.glob.map.getzp(pl.rc));
	    if(sc == null)
		return false;
	    Coord pc = Coord.of(
		Utils.clip(Math.round(sc.x), 2, gui.map.sz.x - 3),
		Utils.clip(Math.round(sc.y), 2, gui.map.sz.y - 3));
	    gui.map.wdgmsg("drop", pc, pl.rc.floor(posres), 0);
	    return true;
	} catch(Exception e) {
	    return false;
	}
    }
}
