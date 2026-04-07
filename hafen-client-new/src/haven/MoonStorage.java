package haven;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import haven.render.Texture;

/**
 * Event-driven local cache for world storage containers.
 */
public final class MoonStorage {
    private static final Object lock = new Object();

    private static final long FRESH_MS = 10L * 60L * 1000L;
    private static final long AGING_MS = 60L * 60L * 1000L;
    private static final long SAVE_DEBOUNCE_MS = 500L;
    private static final long CAPTURE_DEBOUNCE_MS = 50L;
    private static final long EMPTY_CAPTURE_DEBOUNCE_MS = 225L;
    private static final int MAX_TOOLTIP_ITEMS = 9;
    private static final int MAX_BUILDING_TOOLTIP_ITEMS = 9;
    private static final int SEARCH_LIMIT_DEFAULT = 128;
    private static final double BUILDING_MIN_AREA = MCache.tilesz.x * MCache.tilesz.y * 2.0;
    private static final int TOOLTIP_CELL = UI.scale(24);
    private static final int TOOLTIP_PAD = UI.scale(6);
    private static final int TOOLTIP_SLOT_PAD = UI.scale(2);
    private static final int TOOLTIP_TEXT_GAP = UI.scale(3);
    private static final Color TOOLTIP_BG = new Color(27, 27, 31, 238);
    private static final Color TOOLTIP_HDR = new Color(58, 52, 96, 245);
    private static final Color TOOLTIP_BORDER = new Color(118, 109, 175, 220);
    private static final Color TOOLTIP_META = new Color(214, 214, 220);
    private static final Text.Foundry tooltipTitleF = new Text.Foundry(Text.fraktur.deriveFont((float)UI.scale(15))).aa(true);
    private static final Text.Foundry tooltipMetaF = new Text.Foundry(Text.sans, UI.scale(10)).aa(true);

    private static final WeakHashMap<Window, LiveWindow> liveWindows = new WeakHashMap<>();
    private static final WeakHashMap<Inventory, LiveWindow> liveInventories = new WeakHashMap<>();

    private static final LinkedHashMap<String, ContainerRecord> containers = new LinkedHashMap<>();
    private static final Map<String, LinkedHashSet<String>> itemNameIndex = new HashMap<>();
    private static final Map<String, LinkedHashSet<String>> itemResNameIndex = new HashMap<>();
    private static final Map<String, LinkedHashSet<String>> buildingIndex = new HashMap<>();
    private static final Map<String, String> gobIndex = new HashMap<>();
    private static final Map<String, String> stableIndex = new HashMap<>();
    private static final Map<String, BufferedImage> previewImageCache = new HashMap<>();

    private static final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
	Thread t = new Thread(r, "moon-storage-io");
	t.setDaemon(true);
	return t;
    });
    private static ScheduledFuture<?> pendingSave = null;
    private static boolean loaded = false;

    private static String trackedContainerId = null;
    private static String trackedItemName = null;
    private static String trackedItemResName = null;

    private MoonStorage() {}

    private static boolean disabled() {
	return !MoonConfig.smartStorageEnabled;
    }

    public static final class ItemRecord {
	public String itemResName;
	public String displayName;
	public double quality;
	public int quantity;
	public int stackSize;
	public int slotIndex;
	public int slotW = 1;
	public int slotH = 1;
	public long lastSeenAt;

	private ItemRecord copy() {
	    ItemRecord ret = new ItemRecord();
	    ret.itemResName = this.itemResName;
	    ret.displayName = this.displayName;
	    ret.quality = this.quality;
	    ret.quantity = this.quantity;
	    ret.stackSize = this.stackSize;
	    ret.slotIndex = this.slotIndex;
	    ret.slotW = this.slotW;
	    ret.slotH = this.slotH;
	    ret.lastSeenAt = this.lastSeenAt;
	    return ret;
	}
    }

    public static final class ContainerRecord {
	public String containerId;
	public String worldId;
	public String wndId;
	public String buildingId;
	public String buildingDisplayName;
	public String buildingType;
	public String zoneId;
	public String containerType;
	public String displayName;
	public String ownershipTag;
	public String lastUpdatedBy;
	public String staleReason;
	public String gobResName;
	public String stableKey;
	public long gobId;
	public double worldX = Double.NaN;
	public double worldY = Double.NaN;
	public int gridW;
	public int gridH;
	public long lastSeenAt;
	public int revision;
	public boolean isStale;
	public final List<ItemRecord> items = new ArrayList<>();

	private transient Tex tooltipCache;
	private transient String tooltipCacheKey;

	private ContainerRecord copy() {
	    ContainerRecord ret = new ContainerRecord();
	    ret.containerId = this.containerId;
	    ret.worldId = this.worldId;
	    ret.wndId = this.wndId;
	    ret.buildingId = this.buildingId;
	    ret.buildingDisplayName = this.buildingDisplayName;
	    ret.buildingType = this.buildingType;
	    ret.zoneId = this.zoneId;
	    ret.containerType = this.containerType;
	    ret.displayName = this.displayName;
	    ret.ownershipTag = this.ownershipTag;
	    ret.lastUpdatedBy = this.lastUpdatedBy;
	    ret.staleReason = this.staleReason;
	    ret.gobResName = this.gobResName;
	    ret.stableKey = this.stableKey;
	    ret.gobId = this.gobId;
	    ret.worldX = this.worldX;
	    ret.worldY = this.worldY;
	    ret.gridW = this.gridW;
	    ret.gridH = this.gridH;
	    ret.lastSeenAt = this.lastSeenAt;
	    ret.revision = this.revision;
	    ret.isStale = this.isStale;
	    for(ItemRecord item : this.items)
		ret.items.add(item.copy());
	    return ret;
	}

	private void invalidateTooltipCache() {
	    if(tooltipCache != null)
		tooltipCache.dispose();
	    tooltipCache = null;
	    tooltipCacheKey = null;
	}
    }

    public static final class SearchHit {
	public final String containerId;
	public final String containerDisplayName;
	public final String containerType;
	public final String buildingId;
	public final String buildingDisplayName;
	public final String itemDisplayName;
	public final String itemResName;
	public final int quantity;
	public final long lastSeenAt;
	public final long gobId;
	public final double worldX;
	public final double worldY;

	public SearchHit(String containerId, String containerDisplayName, String containerType,
			 String buildingId, String buildingDisplayName,
			 String itemDisplayName, String itemResName, int quantity, long lastSeenAt,
			 long gobId, double worldX, double worldY) {
	    this.containerId = containerId;
	    this.containerDisplayName = containerDisplayName;
	    this.containerType = containerType;
	    this.buildingId = buildingId;
	    this.buildingDisplayName = buildingDisplayName;
	    this.itemDisplayName = itemDisplayName;
	    this.itemResName = itemResName;
	    this.quantity = quantity;
	    this.lastSeenAt = lastSeenAt;
	    this.gobId = gobId;
	    this.worldX = worldX;
	    this.worldY = worldY;
	}
    }

    private static final class LiveWindow {
	final GameUI gui;
	final Window window;
	final String wndId;
	final long gobId;
	Inventory inventory;
	long captureAt = 0L;

	LiveWindow(GameUI gui, Window window, String wndId, long gobId) {
	    this.gui = gui;
	    this.window = window;
	    this.wndId = wndId;
	    this.gobId = gobId;
	}
    }

    private static final class SearchHitBuilder {
	final String containerId;
	final String containerDisplayName;
	final String containerType;
	final String buildingId;
	final String buildingDisplayName;
	final String itemDisplayName;
	final String itemResName;
	final long lastSeenAt;
	final long gobId;
	final double worldX;
	final double worldY;
	int quantity = 0;

	SearchHitBuilder(ContainerRecord rec, String itemDisplayName, String itemResName) {
	    this.containerId = rec.containerId;
	    this.containerDisplayName = defaultContainerName(rec);
	    this.containerType = defaultContainerType(rec);
	    this.buildingId = rec.buildingId;
	    this.buildingDisplayName = defaultBuildingName(rec);
	    this.itemDisplayName = itemDisplayName;
	    this.itemResName = itemResName;
	    this.lastSeenAt = rec.lastSeenAt;
	    this.gobId = rec.gobId;
	    this.worldX = rec.worldX;
	    this.worldY = rec.worldY;
	}

	SearchHit build() {
	    return new SearchHit(containerId, containerDisplayName, containerType,
		buildingId, buildingDisplayName,
		itemDisplayName, itemResName, quantity, lastSeenAt, gobId, worldX, worldY);
	}
    }

    private static final class ResolvedBuilding {
	final String buildingId;
	final String displayName;
	final String buildingType;
	final long gobId;
	final double worldX;
	final double worldY;

	ResolvedBuilding(String buildingId, String displayName, String buildingType, long gobId, double worldX, double worldY) {
	    this.buildingId = buildingId;
	    this.displayName = displayName;
	    this.buildingType = buildingType;
	    this.gobId = gobId;
	    this.worldX = worldX;
	    this.worldY = worldY;
	}
    }

    private static final class BuildingRect {
	final double minX;
	final double minY;
	final double maxX;
	final double maxY;
	final double area;

	BuildingRect(double minX, double minY, double maxX, double maxY) {
	    this.minX = minX;
	    this.minY = minY;
	    this.maxX = maxX;
	    this.maxY = maxY;
	    this.area = Math.max(0.0, maxX - minX) * Math.max(0.0, maxY - minY);
	}
    }

    public static void registerWindow(GameUI gui, Window window, String wndId, long gobId) {
	if(disabled() || gui == null || window == null || MoonMisc.isBeltOrKeyring(window, wndId))
	    return;
	ensureLoaded();
	LiveWindow live = new LiveWindow(gui, window, wndId, gobId);
	Inventory existing = findInventory(window);
	synchronized(lock) {
	    liveWindows.put(window, live);
	    if(existing != null) {
		live.inventory = existing;
		liveInventories.put(existing, live);
	    }
	}
	if(existing != null && shouldCaptureWindow(live))
	    requestCapture(live);
    }

    public static void onWindowClosed(Window window) {
	if(disabled() || window == null)
	    return;
	synchronized(lock) {
	    LiveWindow live = liveWindows.remove(window);
	    if(live != null && live.inventory != null)
		liveInventories.remove(live.inventory);
	}
    }

    public static void onWindowCapChanged(Window window) {
	if(disabled() || window == null)
	    return;
	LiveWindow live;
	synchronized(lock) {
	    live = liveWindows.get(window);
	}
	if(live != null && live.inventory != null && shouldCaptureWindow(live))
	    requestCapture(live);
    }

    public static void onWindowChildAdded(Window window, Widget child) {
	if(disabled() || !(child instanceof Inventory))
	    return;
	LiveWindow live;
	synchronized(lock) {
	    live = liveWindows.get(window);
	    if(live == null)
		return;
	    live.inventory = (Inventory)child;
	    liveInventories.put((Inventory)child, live);
	}
	if(shouldCaptureWindow(live))
	    requestCapture(live);
    }

    public static void onWindowChildRemoved(Window window, Widget child) {
	if(disabled() || !(child instanceof Inventory))
	    return;
	synchronized(lock) {
	    LiveWindow live = liveWindows.get(window);
	    if(live != null && live.inventory == child)
		live.inventory = null;
	    liveInventories.remove((Inventory)child);
	}
    }

    public static void onInventoryChanged(Inventory inv) {
	if(disabled())
	    return;
	LiveWindow live;
	synchronized(lock) {
	    live = liveInventories.get(inv);
	}
	if(live != null && shouldCaptureWindow(live))
	    requestCapture(live);
    }

    public static void tick(GameUI gui) {
	if(disabled() || gui == null)
	    return;
	List<LiveWindow> due = new ArrayList<>();
	long now = System.currentTimeMillis();
	synchronized(lock) {
	    for(LiveWindow live : liveWindows.values()) {
		if(live == null || live.captureAt <= 0L)
		    continue;
		if(now >= live.captureAt) {
		    live.captureAt = 0L;
		    due.add(live);
		}
	    }
	}
	for(LiveWindow live : due) {
	    if(live != null && shouldCaptureWindow(live))
		captureInventory(live);
	}
    }

    public static void track(SearchHit hit) {
	if(disabled() || hit == null)
	    return;
	synchronized(lock) {
	    trackedContainerId = hit.containerId;
	    trackedItemName = hit.itemDisplayName;
	    trackedItemResName = hit.itemResName;
	}
    }

    public static void clearTracking() {
	if(disabled())
	    return;
	synchronized(lock) {
	    trackedContainerId = null;
	    trackedItemName = null;
	    trackedItemResName = null;
	}
    }

    public static boolean hasTracking() {
	if(disabled())
	    return false;
	synchronized(lock) {
	    return trackedContainerId != null;
	}
    }

    public static Gob gobFromClickData(ClickData inf) {
	if(inf == null || inf.ci == null)
	    return null;
	if(inf.ci instanceof Gob.GobClick)
	    return ((Gob.GobClick)inf.ci).gob;
	if(inf.ci instanceof Composited.CompositeClick)
	    return ((Composited.CompositeClick)inf.ci).gi.gob;
	return null;
    }

    public static void flashTrackedResult(GameUI gui, SearchHit hit) {
	if(disabled() || gui == null || hit == null || gui.map == null)
	    return;
	Gob gob = (hit.gobId > 0 && gui.ui != null && gui.ui.sess != null) ? gui.ui.sess.glob.oc.getgob(hit.gobId) : null;
	if(gob == null && !isBlank(hit.buildingId))
	    gob = findVisibleBuildingGob(gui.map, hit.buildingId);
	if(gob != null)
	    MapView.moonNavTriggerFlash(gob.rc);
    }

    public static void drawInventoryHighlights(Inventory inv, GOut g) {
	if(disabled())
	    return;
	LiveWindow live;
	String trackContainer;
	String trackName;
	String trackResName;
	synchronized(lock) {
	    live = liveInventories.get(inv);
	    trackContainer = trackedContainerId;
	    trackName = trackedItemName;
	    trackResName = trackedItemResName;
	}
	if(live == null || trackContainer == null)
	    return;
	String containerId = containerIdFor(live);
	if(containerId == null || !containerId.equals(trackContainer))
	    return;
	if(isBlank(trackName) && isBlank(trackResName))
	    return;
	g.chcolor(255, 224, 96, 220);
	for(Map.Entry<GItem, WItem> e : inv.wmap.entrySet()) {
	    GItem item = e.getKey();
	    WItem wi = e.getValue();
	    if(item == null || wi == null)
		continue;
	    if(!matchesItem(item, trackName, trackResName))
		continue;
	    Coord ul = wi.c.sub(1, 1);
	    Coord sz = wi.sz.add(1, 1);
	    g.rect(ul, sz);
	    if(sz.x > 4 && sz.y > 4)
		g.rect(ul.add(1, 1), sz.sub(2, 2));
	}
	g.chcolor();
    }

    public static void drawTrackingOverlay(GOut g, MapView mv) {
	if(disabled())
	    return;
	ContainerRecord rec;
	String trackName;
	synchronized(lock) {
	    ensureLoaded();
	    rec = (trackedContainerId == null) ? null : copyContainer(containers.get(trackedContainerId));
	    trackName = trackedItemName;
	}
	if(rec == null || mv == null || mv.ui == null)
	    return;
	String worldId = worldId(mv);
	if(!safeEquals(worldId, rec.worldId))
	    return;
	Gob gob = (rec.gobId > 0) ? mv.ui.sess.glob.oc.getgob(rec.gobId) : null;
	if(gob == null && !isBlank(rec.buildingId))
	    gob = findVisibleBuildingGob(mv, rec.buildingId);
	if(gob == null)
	    return;
	try {
	    MoonHitboxMode.drawOneGobHitboxWorldOutline(g, mv, gob);
	    Coord3f gc = gob.getc();
	    if(gc != null) {
		Coord3f sc = mv.screenxf(new Coord3f(gc.x, gc.y, gc.z + UI.scale(18)));
		if(sc != null && sc.z >= 0 && sc.z <= 1) {
		    String label = defaultContainerName(rec);
		    if(rec.gobId <= 0 || gob.id != rec.gobId)
			label = defaultBuildingName(rec);
		    if(!isBlank(trackName))
			label = trackName + " -> " + label;
		    g.atext(label, Coord.of(Math.round(sc.x), Math.round(sc.y)), 0.5, 1.0);
		}
	    }
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
    }

    public static List<SearchHit> search(String query) {
	return search(query, null, SEARCH_LIMIT_DEFAULT);
    }

    public static List<SearchHit> search(String query, int limit) {
	return search(query, null, limit);
    }

    public static List<SearchHit> search(String query, String buildingId, int limit) {
	if(disabled())
	    return Collections.emptyList();
	ensureLoaded();
	String needle = normalize(query);
	if(needle.isEmpty())
	    return Collections.emptyList();
	Map<String, SearchHitBuilder> agg = new LinkedHashMap<>();
	synchronized(lock) {
	    LinkedHashSet<String> cids = matchingContainerIds(needle, buildingId);
	    for(String cid : cids) {
		ContainerRecord rec = containers.get(cid);
		if(rec == null)
		    continue;
		for(ItemRecord item : rec.items) {
		    if(!matchesItem(item, needle))
			continue;
		    String itemName = displayItemName(item);
		    String itemRes = blankToNull(item.itemResName);
		    String key = rec.containerId + "|" + normalize(itemName) + "|" + normalize(itemRes);
		    SearchHitBuilder cur = agg.get(key);
		    if(cur == null) {
			cur = new SearchHitBuilder(rec, itemName, itemRes);
			agg.put(key, cur);
		    }
		    cur.quantity += Math.max(item.quantity, 1);
		}
	    }
	}
	List<SearchHit> ret = new ArrayList<>();
	for(SearchHitBuilder b : agg.values())
	    ret.add(b.build());
	Collections.sort(ret, Comparator
	    .comparingInt((SearchHit h) -> h.quantity).reversed()
	    .thenComparingLong(h -> -h.lastSeenAt)
	    .thenComparing(h -> safeLower(h.containerDisplayName)));
	if(limit > 0 && ret.size() > limit)
	    return new ArrayList<>(ret.subList(0, limit));
	return ret;
    }

    public static Object tooltipForGob(MapView mv, Gob gob) {
	if(mv == null || gob == null)
	    return null;
	if(disabled())
	    return buildingTooltip(mv, gob);
	synchronized(lock) {
	    ensureLoaded();
	    String worldId = worldId(mv);
	    String cid = findContainerIdLocked(worldId, gob.id, stableKey(worldId, gob));
	    ContainerRecord rec = (cid == null) ? null : containers.get(cid);
	    if(rec != null)
		return tooltip(mv.ui, rec);
	}
	return buildingTooltip(mv, gob);
    }

    private static void captureInventory(LiveWindow live) {
	if(live == null || live.gui == null || live.inventory == null)
	    return;
	Inventory inv = live.inventory;
	String worldId = worldId(live.gui);
	if(isBlank(worldId))
	    return;
	Gob gob = currentGob(live);
	String stableKey = stableKey(worldId, gob);
	String containerId = containerIdFor(worldId, live.wndId, live.gobId, stableKey);
	if(isBlank(containerId))
	    return;

	int oldRevision = 0;
	ContainerRecord prevRecord = null;
	synchronized(lock) {
	    ensureLoaded();
	    ContainerRecord old = containers.get(containerId);
	    if(old != null) {
		oldRevision = old.revision;
		prevRecord = old.copy();
	    }
	}

	ContainerRecord rec = new ContainerRecord();
	rec.containerId = containerId;
	rec.worldId = worldId;
	rec.wndId = live.wndId;
	rec.containerType = detectContainerType(live);
	rec.displayName = detectDisplayName(live);
	rec.ownershipTag = "unknown";
	rec.lastUpdatedBy = updatedBy(live.gui);
	rec.lastSeenAt = System.currentTimeMillis();
	rec.revision = oldRevision + 1;
	rec.gobId = live.gobId;
	rec.stableKey = stableKey;
	rec.gridW = Math.max(inv.isz.x, 0);
	rec.gridH = Math.max(inv.isz.y, 0);

	if(gob != null) {
	    rec.worldX = gob.rc.x;
	    rec.worldY = gob.rc.y;
	    rec.gobResName = MoonGobKind.resourceName(gob);
	    if(isBlank(rec.displayName))
		rec.displayName = gobDisplayName(gob);
	}
	ResolvedBuilding building = resolveBuilding(live.gui, gob);
	if(building != null) {
	    rec.buildingId = building.buildingId;
	    rec.buildingDisplayName = building.displayName;
	    rec.buildingType = building.buildingType;
	}

	Map<String, ArrayDeque<ItemRecord>> prevItems = previousItemBuckets(prevRecord);
	List<Map.Entry<GItem, WItem>> entries = new ArrayList<>(inv.wmap.entrySet());
	Collections.sort(entries, Comparator.comparingInt(e -> slotIndex(inv, e.getValue())));
	for(Map.Entry<GItem, WItem> e : entries) {
	    GItem item = e.getKey();
	    WItem wi = e.getValue();
	    if(item == null || wi == null)
		continue;
	    ItemRecord ir = buildItemRecord(inv, item, wi, rec.lastSeenAt, prevItems);
	    if(ir != null)
		rec.items.add(ir);
	}
	rec.isStale = false;
	rec.staleReason = null;

	synchronized(lock) {
	    putRecord(rec);
	    scheduleSave();
	}
    }

    private static ItemRecord buildItemRecord(Inventory inv, GItem item, WItem wi, long seenAt,
					      Map<String, ArrayDeque<ItemRecord>> prevItems) {
	ItemRecord ret = new ItemRecord();
	ret.itemResName = safeItemResName(item);
	ret.displayName = safeItemDisplayName(item);
	ret.quantity = Math.max(item.num, 1);
	ret.stackSize = ret.quantity;
	ret.slotIndex = slotIndex(inv, wi);
	ret.slotW = Math.max(1, (wi.sz.x + Inventory.sqsz.x - 1) / Inventory.sqsz.x);
	ret.slotH = Math.max(1, (wi.sz.y + Inventory.sqsz.y - 1) / Inventory.sqsz.y);
	ret.quality = reuseKnownQuality(prevItems, ret);
	ret.lastSeenAt = seenAt;
	return ret;
    }

    private static Map<String, ArrayDeque<ItemRecord>> previousItemBuckets(ContainerRecord rec) {
	Map<String, ArrayDeque<ItemRecord>> buckets = new HashMap<>();
	if(rec == null || rec.items == null)
	    return buckets;
	for(ItemRecord item : rec.items) {
	    if(item == null)
		continue;
	    buckets.computeIfAbsent(itemReuseKey(item), k -> new ArrayDeque<>()).add(item);
	}
	return buckets;
    }

    private static double reuseKnownQuality(Map<String, ArrayDeque<ItemRecord>> prevItems, ItemRecord current) {
	if(prevItems == null || current == null)
	    return 0;
	ArrayDeque<ItemRecord> bucket = prevItems.get(itemReuseKey(current));
	if(bucket == null || bucket.isEmpty())
	    return 0;
	ItemRecord prev = bucket.pollFirst();
	return (prev == null) ? 0 : prev.quality;
    }

    private static String itemReuseKey(ItemRecord item) {
	if(item == null)
	    return "";
	return normalize(item.itemResName) + "|" + normalize(item.displayName) + "|" +
	    item.quantity + "|" + item.slotW + "|" + item.slotH;
    }

    private static int slotIndex(Inventory inv, WItem wi) {
	if(inv == null || wi == null)
	    return -1;
	Coord slot = wi.c.sub(1, 1).div(Inventory.sqsz);
	return (slot.y * Math.max(inv.isz.x, 1)) + slot.x;
    }

    private static boolean matchesItem(GItem item, String trackName, String trackResName) {
	String dn = normalize(safeItemDisplayName(item));
	String rn = normalize(safeItemResName(item));
	String tn = normalize(trackName);
	String tr = normalize(trackResName);
	return (!tn.isEmpty() && dn.contains(tn)) || (!tr.isEmpty() && safeEquals(rn, tr));
    }

    private static boolean matchesItem(ItemRecord item, String needle) {
	String dn = normalize(item.displayName);
	String rn = normalize(item.itemResName);
	return dn.contains(needle) || rn.contains(needle);
    }

    private static void putRecord(ContainerRecord rec) {
	ContainerRecord prev = containers.remove(rec.containerId);
	if(prev != null)
	    prev.invalidateTooltipCache();
	containers.put(rec.containerId, rec);
	reindexAll();
    }

    private static LinkedHashSet<String> matchingContainerIds(String needle, String buildingId) {
	LinkedHashSet<String> ret = new LinkedHashSet<>();
	for(Map.Entry<String, LinkedHashSet<String>> e : itemNameIndex.entrySet()) {
	    if(e.getKey().contains(needle))
		ret.addAll(e.getValue());
	}
	for(Map.Entry<String, LinkedHashSet<String>> e : itemResNameIndex.entrySet()) {
	    if(e.getKey().contains(needle))
		ret.addAll(e.getValue());
	}
	if(!isBlank(buildingId)) {
	    LinkedHashSet<String> allowed = buildingIndex.get(buildingId);
	    if(allowed == null || allowed.isEmpty())
		return new LinkedHashSet<>();
	    ret.retainAll(allowed);
	}
	return ret;
    }

    private static void reindexAll() {
	itemNameIndex.clear();
	itemResNameIndex.clear();
	buildingIndex.clear();
	gobIndex.clear();
	stableIndex.clear();
	for(ContainerRecord rec : containers.values())
	    indexContainer(rec);
    }

    private static void indexContainer(ContainerRecord rec) {
	if(rec == null)
	    return;
	if(rec.gobId > 0 && !isBlank(rec.worldId))
	    gobIndex.put(gobKey(rec.worldId, rec.gobId), rec.containerId);
	String stableKey = stableKey(rec);
	if(!isBlank(stableKey))
	    stableIndex.put(stableKey, rec.containerId);
	indexInto(buildingIndex, rec.buildingId, rec.containerId);
	for(ItemRecord item : rec.items) {
	    indexInto(itemNameIndex, normalize(item.displayName), rec.containerId);
	    indexInto(itemResNameIndex, normalize(item.itemResName), rec.containerId);
	}
    }

    private static void indexInto(Map<String, LinkedHashSet<String>> index, String key, String containerId) {
	if(key.isEmpty() || containerId == null)
	    return;
	index.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(containerId);
    }

    private static void ensureLoaded() {
	synchronized(lock) {
	    if(loaded)
		return;
	    loaded = true;
	}
	Path p = cachePath();
	if(!Files.isRegularFile(p))
	    return;
	try {
	    String raw = Files.readString(p, StandardCharsets.UTF_8);
	    Object root = MoonJsonLite.parse(raw);
	    Map<String, Object> m = MoonJsonLite.asMap(root);
	    Object carr = m.get("containers");
	    if(!(carr instanceof List))
		return;
	    LinkedHashMap<String, ContainerRecord> parsed = new LinkedHashMap<>();
	    for(Object co : MoonJsonLite.asList(carr)) {
		ContainerRecord rec = parseContainer(co);
		if(rec != null && !isBlank(rec.containerId))
		    parsed.put(rec.containerId, rec);
	    }
	    synchronized(lock) {
		containers.clear();
		containers.putAll(parsed);
		reindexAll();
	    }
	} catch(Exception e) {
	    new Warning(e, "moon-storage: failed to load cache").issue();
	}
    }

    private static ContainerRecord parseContainer(Object obj) throws MoonJsonLite.MoonJsonParseException {
	Map<String, Object> m = MoonJsonLite.asMap(obj);
	ContainerRecord rec = new ContainerRecord();
	rec.containerId = blankToNull(MoonJsonLite.asString(m.get("containerId")));
	rec.worldId = blankToNull(MoonJsonLite.asString(m.get("worldId")));
	rec.wndId = blankToNull(MoonJsonLite.asString(m.get("wndId")));
	rec.buildingId = blankToNull(MoonJsonLite.asString(m.get("buildingId")));
	rec.buildingDisplayName = blankToNull(MoonJsonLite.asString(m.get("buildingDisplayName")));
	rec.buildingType = blankToNull(MoonJsonLite.asString(m.get("buildingType")));
	rec.zoneId = blankToNull(MoonJsonLite.asString(m.get("zoneId")));
	rec.containerType = blankToNull(MoonJsonLite.asString(m.get("containerType")));
	rec.displayName = blankToNull(MoonJsonLite.asString(m.get("displayName")));
	rec.ownershipTag = blankToNull(MoonJsonLite.asString(m.get("ownershipTag")));
	rec.lastUpdatedBy = blankToNull(MoonJsonLite.asString(m.get("lastUpdatedBy")));
	rec.staleReason = blankToNull(MoonJsonLite.asString(m.get("staleReason")));
	rec.gobResName = blankToNull(MoonJsonLite.asString(m.get("gobResName")));
	rec.stableKey = blankToNull(MoonJsonLite.asString(m.get("stableKey")));
	rec.gobId = asLong(m.get("gobId"), 0L);
	rec.worldX = MoonJsonLite.asDouble(m.get("worldX"), Double.NaN);
	rec.worldY = MoonJsonLite.asDouble(m.get("worldY"), Double.NaN);
	rec.gridW = MoonJsonLite.asInt(m.get("gridW"), 0);
	rec.gridH = MoonJsonLite.asInt(m.get("gridH"), 0);
	rec.lastSeenAt = asLong(m.get("lastSeenAt"), 0L);
	rec.revision = MoonJsonLite.asInt(m.get("revision"), 0);
	rec.isStale = Boolean.TRUE.equals(m.get("isStale"));
	Object iarr = m.get("items");
	if(iarr instanceof List) {
	    for(Object io : MoonJsonLite.asList(iarr)) {
		ItemRecord item = parseItem(io);
		if(item != null)
		    rec.items.add(item);
	    }
	}
	return rec;
    }

    private static ItemRecord parseItem(Object obj) throws MoonJsonLite.MoonJsonParseException {
	Map<String, Object> m = MoonJsonLite.asMap(obj);
	ItemRecord item = new ItemRecord();
	item.itemResName = blankToNull(MoonJsonLite.asString(m.get("itemResName")));
	item.displayName = blankToNull(MoonJsonLite.asString(m.get("displayName")));
	item.quality = MoonJsonLite.asDouble(m.get("quality"), 0);
	item.quantity = MoonJsonLite.asInt(m.get("quantity"), 1);
	item.stackSize = MoonJsonLite.asInt(m.get("stackSize"), item.quantity);
	item.slotIndex = MoonJsonLite.asInt(m.get("slotIndex"), -1);
	item.slotW = Math.max(1, MoonJsonLite.asInt(m.get("slotW"), 1));
	item.slotH = Math.max(1, MoonJsonLite.asInt(m.get("slotH"), 1));
	item.lastSeenAt = asLong(m.get("lastSeenAt"), 0L);
	return item;
    }

    private static void scheduleSave() {
	synchronized(lock) {
	    if(pendingSave != null)
		pendingSave.cancel(false);
	    pendingSave = io.schedule(MoonStorage::flushToDisk, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}
    }

    private static void flushToDisk() {
	List<ContainerRecord> snapshot = new ArrayList<>();
	synchronized(lock) {
	    for(ContainerRecord rec : containers.values())
		snapshot.add(rec.copy());
	}
	Path p = cachePath();
	try {
	    Files.createDirectories(p.getParent());
	    Files.writeString(p, encode(snapshot), StandardCharsets.UTF_8);
	} catch(Exception e) {
	    new Warning(e, "moon-storage: failed to save cache").issue();
	}
    }

    private static String encode(List<ContainerRecord> snapshot) {
	StringBuilder sb = new StringBuilder(Math.max(512, snapshot.size() * 256));
	sb.append("{\n");
	sb.append("  \"version\": 1,\n");
	sb.append("  \"containers\": [");
	for(int i = 0; i < snapshot.size(); i++) {
	    if(i > 0)
		sb.append(',');
	    appendContainer(sb, snapshot.get(i), 4);
	}
	sb.append("\n  ]\n");
	sb.append("}\n");
	return sb.toString();
    }

    private static void appendContainer(StringBuilder sb, ContainerRecord rec, int ind) {
	String p = spaces(ind);
	String p2 = spaces(ind + 2);
	sb.append("\n").append(p).append("{\n");
	appendStringField(sb, p2, "containerId", rec.containerId, true);
	appendStringField(sb, p2, "worldId", rec.worldId, true);
	appendStringField(sb, p2, "wndId", rec.wndId, true);
	appendStringField(sb, p2, "buildingId", rec.buildingId, true);
	appendStringField(sb, p2, "buildingDisplayName", rec.buildingDisplayName, true);
	appendStringField(sb, p2, "buildingType", rec.buildingType, true);
	appendStringField(sb, p2, "zoneId", rec.zoneId, true);
	appendStringField(sb, p2, "containerType", rec.containerType, true);
	appendStringField(sb, p2, "displayName", rec.displayName, true);
	appendStringField(sb, p2, "ownershipTag", rec.ownershipTag, true);
	appendStringField(sb, p2, "lastUpdatedBy", rec.lastUpdatedBy, true);
	appendStringField(sb, p2, "staleReason", rec.staleReason, true);
	appendStringField(sb, p2, "gobResName", rec.gobResName, true);
	appendStringField(sb, p2, "stableKey", rec.stableKey, true);
	appendNumberField(sb, p2, "gobId", rec.gobId, true);
	appendDoubleField(sb, p2, "worldX", rec.worldX, true);
	appendDoubleField(sb, p2, "worldY", rec.worldY, true);
	appendNumberField(sb, p2, "gridW", rec.gridW, true);
	appendNumberField(sb, p2, "gridH", rec.gridH, true);
	appendNumberField(sb, p2, "lastSeenAt", rec.lastSeenAt, true);
	appendNumberField(sb, p2, "revision", rec.revision, true);
	appendBooleanField(sb, p2, "isStale", rec.isStale, true);
	sb.append(p2).append("\"items\": [");
	for(int i = 0; i < rec.items.size(); i++) {
	    if(i > 0)
		sb.append(',');
	    appendItem(sb, rec.items.get(i), ind + 4);
	}
	sb.append("\n").append(p2).append("]\n");
	sb.append(p).append("}");
    }

    private static void appendItem(StringBuilder sb, ItemRecord item, int ind) {
	String p = spaces(ind);
	String p2 = spaces(ind + 2);
	sb.append("\n").append(p).append("{\n");
	appendStringField(sb, p2, "itemResName", item.itemResName, true);
	appendStringField(sb, p2, "displayName", item.displayName, true);
	appendDoubleField(sb, p2, "quality", item.quality, true);
	appendNumberField(sb, p2, "quantity", item.quantity, true);
	appendNumberField(sb, p2, "stackSize", item.stackSize, true);
	appendNumberField(sb, p2, "slotIndex", item.slotIndex, true);
	appendNumberField(sb, p2, "slotW", item.slotW, true);
	appendNumberField(sb, p2, "slotH", item.slotH, true);
	appendNumberField(sb, p2, "lastSeenAt", item.lastSeenAt, false);
	sb.append(p).append("}");
    }

    private static void appendStringField(StringBuilder sb, String p, String key, String val, boolean comma) {
	sb.append(p).append(LibreTranslateService.jsonString(key)).append(": ");
	if(val == null)
	    sb.append("null");
	else
	    sb.append(LibreTranslateService.jsonString(val));
	if(comma)
	    sb.append(',');
	sb.append('\n');
    }

    private static void appendNumberField(StringBuilder sb, String p, String key, long val, boolean comma) {
	sb.append(p).append(LibreTranslateService.jsonString(key)).append(": ").append(val);
	if(comma)
	    sb.append(',');
	sb.append('\n');
    }

    private static void appendDoubleField(StringBuilder sb, String p, String key, double val, boolean comma) {
	sb.append(p).append(LibreTranslateService.jsonString(key)).append(": ");
	if(Double.isFinite(val))
	    sb.append(String.format(Locale.ROOT, "%.3f", val));
	else
	    sb.append("null");
	if(comma)
	    sb.append(',');
	sb.append('\n');
    }

    private static void appendBooleanField(StringBuilder sb, String p, String key, boolean val, boolean comma) {
	sb.append(p).append(LibreTranslateService.jsonString(key)).append(": ").append(val ? "true" : "false");
	if(comma)
	    sb.append(',');
	sb.append('\n');
    }

    private static Path cachePath() {
	String home = System.getProperty("user.home", ".");
	return Paths.get(home, ".haven", "moon-storage-cache.json");
    }

    private static ContainerRecord copyContainer(ContainerRecord rec) {
	return (rec == null) ? null : rec.copy();
    }

    private static Object tooltip(UI ui, ContainerRecord rec) {
	long ageBucket = Math.max(0L, (System.currentTimeMillis() - rec.lastSeenAt) / 60_000L);
	String cacheKey = rec.revision + ":" + ageBucket;
	if(cacheKey.equals(rec.tooltipCacheKey) && rec.tooltipCache != null)
	    return rec.tooltipCache;
	if(rec.tooltipCache != null)
	    rec.tooltipCache.dispose();
	rec.tooltipCache = renderContainerTooltip(rec, ui);
	rec.tooltipCacheKey = cacheKey;
	return rec.tooltipCache;
    }

    private static Tex renderContainerTooltip(ContainerRecord rec, UI ui) {
	BufferedImage img = renderContainerTooltipImage(rec, ui);
	return (img == null) ? null : new TexI(img, false).filter(Texture.Filter.NEAREST);
    }

    private static BufferedImage renderContainerTooltipImage(ContainerRecord rec, UI ui) {
	if(rec == null)
	    return null;
	int gridW = tooltipGridW(rec);
	int gridH = tooltipGridH(rec);
	Text.Line title = tooltipTitleF.render(defaultContainerName(rec), Color.WHITE);
	String meta = ageText(System.currentTimeMillis() - rec.lastSeenAt) + "  |  " + statusText(System.currentTimeMillis() - rec.lastSeenAt);
	Text.Line metaLine = tooltipMetaF.render(meta, TOOLTIP_META);
	int gridPxW = Math.max(gridW, 1) * TOOLTIP_CELL;
	int gridPxH = Math.max(gridH, 1) * TOOLTIP_CELL;
	int width = Math.max(gridPxW, Math.max(title.sz().x, metaLine.sz().x)) + (TOOLTIP_PAD * 2);
	int height = TOOLTIP_PAD + title.sz().y + TOOLTIP_TEXT_GAP + metaLine.sz().y + TOOLTIP_TEXT_GAP + gridPxH + TOOLTIP_PAD;
	BufferedImage buf = TexI.mkbuf(Coord.of(Math.max(width, UI.scale(96)), Math.max(height, UI.scale(72))));
	Graphics2D g = buf.createGraphics();
	try {
	    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	    g.setColor(TOOLTIP_BG);
	    g.fillRect(0, 0, buf.getWidth(), buf.getHeight());
	    g.setColor(TOOLTIP_HDR);
	    g.fillRect(0, 0, buf.getWidth(), TOOLTIP_PAD + title.sz().y + TOOLTIP_TEXT_GAP + metaLine.sz().y + TOOLTIP_TEXT_GAP);
	    g.setColor(TOOLTIP_BORDER);
	    g.drawRect(0, 0, buf.getWidth() - 1, buf.getHeight() - 1);
	    g.drawImage(title.img, TOOLTIP_PAD, TOOLTIP_PAD, null);
	    g.drawImage(metaLine.img, TOOLTIP_PAD, TOOLTIP_PAD + title.sz().y + TOOLTIP_TEXT_GAP, null);
	    int gridY = TOOLTIP_PAD + title.sz().y + TOOLTIP_TEXT_GAP + metaLine.sz().y + TOOLTIP_TEXT_GAP;
	    drawTooltipGrid(g, rec, gridW, gridH, ui, TOOLTIP_PAD, gridY);
	} finally {
	    g.dispose();
	}
	return buf;
    }

    private static void drawTooltipGrid(Graphics2D g, ContainerRecord rec, int gridW, int gridH, UI ui, int gridX, int gridY) {
	BufferedImage slotImg = tooltipSlotImage();
	for(int y = 0; y < Math.max(gridH, 1); y++) {
	    for(int x = 0; x < Math.max(gridW, 1); x++) {
		int px = gridX + (x * TOOLTIP_CELL);
		int py = gridY + (y * TOOLTIP_CELL);
		if(slotImg != null)
		    g.drawImage(slotImg, px, py, TOOLTIP_CELL, TOOLTIP_CELL, null);
		else {
		    g.setColor(new Color(54, 72, 58, 170));
		    g.fillRect(px, py, TOOLTIP_CELL, TOOLTIP_CELL);
		    g.setColor(new Color(38, 51, 39, 220));
		    g.drawRect(px, py, TOOLTIP_CELL - 1, TOOLTIP_CELL - 1);
		}
	    }
	}
	if(rec.items.isEmpty()) {
	    Text.Line empty = tooltipMetaF.render("(empty)", TOOLTIP_META);
	    int ex = gridX + ((Math.max(gridW, 1) * TOOLTIP_CELL - empty.sz().x) / 2);
	    int ey = gridY + ((Math.max(gridH, 1) * TOOLTIP_CELL - empty.sz().y) / 2);
	    g.drawImage(empty.img, Math.max(ex, gridX), Math.max(ey, gridY), null);
	    return;
	}
	for(ItemRecord item : rec.items) {
	    if(item == null || item.slotIndex < 0)
		continue;
	    int sx = (gridW <= 0) ? 0 : (item.slotIndex % Math.max(gridW, 1));
	    int sy = (gridW <= 0) ? 0 : (item.slotIndex / Math.max(gridW, 1));
	    if(sx < 0 || sy < 0 || sx >= Math.max(gridW, 1) || sy >= Math.max(gridH, 1))
		continue;
	    int px = gridX + (sx * TOOLTIP_CELL);
	    int py = gridY + (sy * TOOLTIP_CELL);
	    int iw = (Math.max(item.slotW, 1) * TOOLTIP_CELL) - (TOOLTIP_SLOT_PAD * 2);
	    int ih = (Math.max(item.slotH, 1) * TOOLTIP_CELL) - (TOOLTIP_SLOT_PAD * 2);
	    BufferedImage icon = loadItemPreviewImage(item.itemResName, ui);
	    if(icon != null)
		g.drawImage(icon, px + TOOLTIP_SLOT_PAD, py + TOOLTIP_SLOT_PAD, iw, ih, null);
	    if(item.quantity > 1) {
		BufferedImage count = GItem.NumberInfo.numrender(item.quantity, Color.WHITE);
		g.drawImage(count,
		    px + (Math.max(item.slotW, 1) * TOOLTIP_CELL) - count.getWidth() - TOOLTIP_SLOT_PAD,
		    py + (Math.max(item.slotH, 1) * TOOLTIP_CELL) - count.getHeight() - TOOLTIP_SLOT_PAD,
		    null);
	    }
	}
    }

    private static BufferedImage tooltipSlotImage() {
	if(Inventory.invsq instanceof TexI)
	    return ((TexI)Inventory.invsq).back;
	return null;
    }

    private static int tooltipGridW(ContainerRecord rec) {
	if(rec != null && rec.gridW > 0)
	    return rec.gridW;
	int guess = maxSlotExtent(rec, true);
	if(guess > 0)
	    return guess;
	if(rec != null && "cupboard".equals(rec.containerType))
	    return 8;
	return 4;
    }

    private static int tooltipGridH(ContainerRecord rec) {
	if(rec != null && rec.gridH > 0)
	    return rec.gridH;
	int guess = maxSlotExtent(rec, false);
	if(guess > 0)
	    return guess;
	if(rec != null && "cupboard".equals(rec.containerType))
	    return 8;
	return 4;
    }

    private static int maxSlotExtent(ContainerRecord rec, boolean width) {
	if(rec == null || rec.items.isEmpty())
	    return 0;
	int gridW = (rec.gridW > 0) ? rec.gridW : 8;
	int max = 0;
	for(ItemRecord item : rec.items) {
	    if(item == null || item.slotIndex < 0)
		continue;
	    int sx = item.slotIndex % Math.max(gridW, 1);
	    int sy = item.slotIndex / Math.max(gridW, 1);
	    max = Math.max(max, width ? (sx + Math.max(item.slotW, 1)) : (sy + Math.max(item.slotH, 1)));
	}
	return max;
    }

    private static BufferedImage loadItemPreviewImage(String resName, UI ui) {
	if(isBlank(resName))
	    return WItem.missing.layer(Resource.imgc).img;
	synchronized(lock) {
	    BufferedImage cached = previewImageCache.get(resName);
	    if(cached != null)
		return cached;
	}
	BufferedImage img = null;
	try {
	    img = loadPreviewLayer(Resource.remote().load(resName));
	    if(img == null)
		img = loadPreviewLayer(Resource.local().load(resName));
	    if(img == null && ui != null)
		img = loadPreviewFromSpec(ui, Resource.remote().load(resName));
	    if(img == null && ui != null)
		img = loadPreviewFromSpec(ui, Resource.local().load(resName));
	} catch(Loading ignored) {
	}
	if(img == null)
	    img = WItem.missing.layer(Resource.imgc).img;
	synchronized(lock) {
	    previewImageCache.put(resName, img);
	}
	return img;
    }

    private static BufferedImage loadPreviewLayer(Indir<Resource> indir) {
	if(indir == null)
	    return null;
	try {
	    Resource res = indir.get();
	    if(res == null)
		return null;
	    Resource.Image layer = res.layer(Resource.imgc);
	    return (layer == null) ? null : layer.img;
	} catch(Loading e) {
	    throw e;
	} catch(Exception e) {
	    return null;
	}
    }

    private static BufferedImage loadPreviewFromSpec(UI ui, Indir<Resource> res) {
	if(ui == null || res == null)
	    return null;
	try {
	    ItemSpec sp = new ItemSpec(OwnerContext.uictx.curry(ui), new ResData(res, Message.nil), null);
	    return sp.image();
	} catch(Loading e) {
	    throw e;
	} catch(Exception e) {
	    return null;
	}
    }

    public static String currentBuildingId(GameUI gui) {
	ResolvedBuilding building = resolveCurrentBuilding(gui);
	return (building == null) ? null : building.buildingId;
    }

    public static String currentBuildingName(GameUI gui) {
	ResolvedBuilding building = resolveCurrentBuilding(gui);
	return (building == null) ? null : building.displayName;
    }

    private static ResolvedBuilding resolveCurrentBuilding(GameUI gui) {
	if(gui == null || gui.map == null)
	    return null;
	String worldId = worldId(gui);
	if(isBlank(worldId))
	    return null;
	Gob player = gui.map.player();
	if(player == null || player.rc == null)
	    return null;
	return resolveBuildingAt(gui.map, worldId, player.rc);
    }

    private static ResolvedBuilding resolveBuilding(GameUI gui, Gob gob) {
	if(gui == null || gui.map == null)
	    return null;
	String worldId = worldId(gui);
	if(isBlank(worldId))
	    return null;
	if(gob != null && gob.rc != null) {
	    ResolvedBuilding atGob = resolveBuildingAt(gui.map, worldId, gob.rc);
	    if(atGob != null)
		return atGob;
	}
	if(gob == null)
	    return resolveCurrentBuilding(gui);
	return null;
    }

    private static ResolvedBuilding resolveBuildingAt(MapView mv, String worldId, Coord2d point) {
	if(mv == null || mv.glob == null || mv.glob.oc == null || isBlank(worldId) || point == null)
	    return null;
	List<Gob> snapshot = new ArrayList<>();
	synchronized(mv.glob.oc) {
	    for(Gob gob : mv.glob.oc)
		snapshot.add(gob);
	}
	ResolvedBuilding best = null;
	double bestArea = Double.POSITIVE_INFINITY;
	double bestCenterDist = Double.POSITIVE_INFINITY;
	Gob fallbackGob = null;
	double fallbackDist = Double.POSITIVE_INFINITY;
	double pad = MCache.tilesz.x * 0.35;
	double fallbackLimit = MCache.tilesz.x * 1.5;
	for(Gob gob : snapshot) {
	    if(!isBuildingCandidate(gob))
		continue;
	    BuildingRect rect = buildingRect(gob);
	    if(rect == null || rect.area < BUILDING_MIN_AREA)
		continue;
	    if(containsPoint(rect, gob.rc, gob.a, point, pad)) {
		ResolvedBuilding building = buildResolvedBuilding(worldId, gob);
		if(building == null)
		    continue;
		double centerDist = gob.rc.dist(point);
		if((best == null) || (rect.area < bestArea) ||
		   ((Math.abs(rect.area - bestArea) < 0.001) && (centerDist < bestCenterDist))) {
		    best = building;
		    bestArea = rect.area;
		    bestCenterDist = centerDist;
		}
	    } else {
		double dist = distanceToRect(rect, gob.rc, gob.a, point);
		if(dist < fallbackDist) {
		    fallbackDist = dist;
		    fallbackGob = gob;
		}
	    }
	}
	if(best != null)
	    return best;
	if(fallbackGob != null && fallbackDist <= fallbackLimit)
	    return buildResolvedBuilding(worldId, fallbackGob);
	return null;
    }

    private static Gob findVisibleBuildingGob(MapView mv, String buildingId) {
	if(mv == null || mv.glob == null || mv.glob.oc == null || isBlank(buildingId))
	    return null;
	String worldId = worldId(mv);
	if(isBlank(worldId))
	    return null;
	synchronized(mv.glob.oc) {
	    for(Gob gob : mv.glob.oc) {
		if(!isBuildingCandidate(gob))
		    continue;
		if(safeEquals(buildingId, buildingIdFor(worldId, gob)))
		    return gob;
	    }
	}
	return null;
    }

    private static boolean isBuildingCandidate(Gob gob) {
	if(gob == null || gob.virtual || gob.rc == null)
	    return false;
	if(MoonGobKind.isFlatWallTarget(gob))
	    return false;
	try {
	    return MoonOverlay.classifyGob(gob) == MoonOverlay.GobType.BUILDING;
	} catch(Exception e) {
	    return false;
	}
    }

    private static ResolvedBuilding buildResolvedBuilding(String worldId, Gob gob) {
	if(isBlank(worldId) || !isBuildingCandidate(gob))
	    return null;
	String buildingId = buildingIdFor(worldId, gob);
	if(isBlank(buildingId))
	    return null;
	String displayName = buildingDisplayName(gob);
	String buildingType = buildingTypeName(gob);
	if(isBlank(buildingType))
	    buildingType = "Building";
	if(isBlank(displayName))
	    displayName = buildingType;
	return new ResolvedBuilding(buildingId, displayName, buildingType, gob.id,
	    gob.rc.x, gob.rc.y);
    }

    private static String buildingIdFor(String worldId, Gob gob) {
	if(isBlank(worldId) || gob == null || gob.rc == null)
	    return null;
	String resName = blankToNull(MoonGobKind.resourceName(gob));
	if(isBlank(resName))
	    return null;
	return stableKey(worldId, resName, gob.rc.x, gob.rc.y);
    }

    private static String buildingDisplayName(Gob gob) {
	String name = blankToNull(gobDisplayName(gob));
	if(name != null)
	    return name;
	return buildingTypeName(gob);
    }

    private static String buildingTypeName(Gob gob) {
	String name = blankToNull(gobDisplayName(gob));
	if(name != null)
	    return name;
	String resName = blankToNull(MoonGobKind.resourceName(gob));
	String type = humanizeId(basename(resName));
	return (type == null) ? "Building" : type;
    }

    private static BuildingRect buildingRect(Gob gob) {
	if(gob == null)
	    return null;
	Coord2d[][] bbox;
	try {
	    bbox = MoonHitboxMode.getBBox(gob);
	} catch(Loading e) {
	    return null;
	} catch(RuntimeException e) {
	    return null;
	}
	if(bbox == null || bbox.length == 0)
	    return null;
	double minX = Double.POSITIVE_INFINITY;
	double minY = Double.POSITIVE_INFINITY;
	double maxX = Double.NEGATIVE_INFINITY;
	double maxY = Double.NEGATIVE_INFINITY;
	boolean any = false;
	for(Coord2d[] poly : bbox) {
	    if(poly == null)
		continue;
	    for(Coord2d p : poly) {
		if(p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y))
		    continue;
		minX = Math.min(minX, p.x);
		minY = Math.min(minY, p.y);
		maxX = Math.max(maxX, p.x);
		maxY = Math.max(maxY, p.y);
		any = true;
	    }
	}
	if(!any || minX > maxX || minY > maxY)
	    return null;
	return new BuildingRect(minX, minY, maxX, maxY);
    }

    private static boolean containsPoint(BuildingRect rect, Coord2d origin, double angle, Coord2d point, double pad) {
	Coord2d local = toLocal(origin, angle, point);
	if(local == null)
	    return false;
	return (local.x >= (rect.minX - pad)) && (local.x <= (rect.maxX + pad)) &&
	    (local.y >= (rect.minY - pad)) && (local.y <= (rect.maxY + pad));
    }

    private static double distanceToRect(BuildingRect rect, Coord2d origin, double angle, Coord2d point) {
	Coord2d local = toLocal(origin, angle, point);
	if(local == null)
	    return Double.POSITIVE_INFINITY;
	double dx = 0.0;
	if(local.x < rect.minX)
	    dx = rect.minX - local.x;
	else if(local.x > rect.maxX)
	    dx = local.x - rect.maxX;
	double dy = 0.0;
	if(local.y < rect.minY)
	    dy = rect.minY - local.y;
	else if(local.y > rect.maxY)
	    dy = local.y - rect.maxY;
	return Math.hypot(dx, dy);
    }

    private static Coord2d toLocal(Coord2d origin, double angle, Coord2d point) {
	if(origin == null || point == null)
	    return null;
	return point.sub(origin).rot(-angle);
    }

    private static Object buildingTooltip(MapView mv, Gob gob) {
	if(mv == null || gob == null || !isBuildingCandidate(gob))
	    return null;
	String worldId = worldId(mv);
	ResolvedBuilding building = buildResolvedBuilding(worldId, gob);
	if(building == null)
	    return null;
	String text;
	synchronized(lock) {
	    ensureLoaded();
	    text = buildingTooltipTextLocked(building);
	}
	return (text == null) ? null : RichText.render(text, UI.scale(320));
    }

    private static String tooltipText(ContainerRecord rec) {
	StringBuilder sb = new StringBuilder(256);
	sb.append("$b{").append(RichText.Parser.quote(defaultContainerName(rec))).append("}");
	sb.append("\nType: ").append(RichText.Parser.quote(defaultContainerType(rec)));
	if(!isBlank(rec.buildingDisplayName))
	    sb.append("\nHouse: ").append(RichText.Parser.quote(rec.buildingDisplayName));
	sb.append("\nLast seen: ").append(ageText(System.currentTimeMillis() - rec.lastSeenAt));
	sb.append("\nStatus: ").append(statusText(System.currentTimeMillis() - rec.lastSeenAt));
	if(!isBlank(rec.lastUpdatedBy))
	    sb.append("\nUpdated by: ").append(RichText.Parser.quote(rec.lastUpdatedBy));
	if(rec.items.isEmpty()) {
	    sb.append("\n(empty)");
	    return sb.toString();
	}
	int shown = 0;
	for(ItemRecord item : rec.items) {
	    if(shown >= MAX_TOOLTIP_ITEMS)
		break;
	    sb.append("\n").append(RichText.Parser.quote(displayItemName(item)));
	    sb.append(" x").append(Math.max(item.quantity, 1));
	    if(item.quality > 0)
		sb.append("  q").append(String.format(Locale.ROOT, "%.1f", item.quality));
	    shown++;
	}
	if(rec.items.size() > shown)
	    sb.append("\n+").append(rec.items.size() - shown).append(" more");
	return sb.toString();
    }

    private static String buildingTooltipTextLocked(ResolvedBuilding building) {
	LinkedHashSet<String> cids = buildingIndex.get(building.buildingId);
	if(cids == null || cids.isEmpty())
	    return null;
	Map<String, Integer> qty = new LinkedHashMap<>();
	Map<String, String> names = new LinkedHashMap<>();
	long lastSeen = 0L;
	int knownContainers = 0;
	for(String cid : cids) {
	    ContainerRecord rec = containers.get(cid);
	    if(rec == null)
		continue;
	    knownContainers++;
	    lastSeen = Math.max(lastSeen, rec.lastSeenAt);
	    for(ItemRecord item : rec.items) {
		String dn = displayItemName(item);
		String key = normalize(dn) + "|" + normalize(item.itemResName);
		names.putIfAbsent(key, dn);
		qty.put(key, qty.getOrDefault(key, 0) + Math.max(item.quantity, 1));
	    }
	}
	if(knownContainers <= 0)
	    return null;
	List<Map.Entry<String, Integer>> items = new ArrayList<>(qty.entrySet());
	Collections.sort(items, Comparator
	    .comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed()
	    .thenComparing(e -> safeLower(names.get(e.getKey()))));
	StringBuilder sb = new StringBuilder(256);
	sb.append("$b{").append(RichText.Parser.quote(building.displayName)).append("}");
	sb.append("\nType: ").append(RichText.Parser.quote(building.buildingType));
	sb.append("\nKnown containers: ").append(knownContainers);
	if(lastSeen > 0)
	    sb.append("\nLast seen: ").append(ageText(System.currentTimeMillis() - lastSeen));
	if(items.isEmpty()) {
	    sb.append("\n(no known items)");
	    return sb.toString();
	}
	int shown = 0;
	for(Map.Entry<String, Integer> e : items) {
	    if(shown >= MAX_BUILDING_TOOLTIP_ITEMS)
		break;
	    sb.append("\n").append(RichText.Parser.quote(names.get(e.getKey())));
	    sb.append(" x").append(e.getValue());
	    shown++;
	}
	if(items.size() > shown)
	    sb.append("\n+").append(items.size() - shown).append(" more");
	return sb.toString();
    }

    private static String gobDisplayName(Gob gob) {
	if(gob == null)
	    return null;
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    if(d instanceof ResDrawable) {
		Resource r = d.getres();
		if(r != null) {
		    Resource.Tooltip tt = r.layer(Resource.tooltip);
		    if(tt != null && !isBlank(tt.t))
			return tt.t;
		}
	    }
	} catch(Loading ignored) {
	}
	return null;
    }

    private static Gob currentGob(LiveWindow live) {
	if(live == null || live.gui == null || live.gobId <= 0 || live.gui.ui == null || live.gui.ui.sess == null)
	    return null;
	try {
	    return live.gui.ui.sess.glob.oc.getgob(live.gobId);
	} catch(Exception e) {
	    return null;
	}
    }

    private static String detectDisplayName(LiveWindow live) {
	String cap = (live.window != null) ? blankToNull(live.window.cap) : null;
	if(cap != null)
	    return cap;
	Gob gob = currentGob(live);
	String gobName = gobDisplayName(gob);
	if(gobName != null)
	    return gobName;
	String type = humanizeId(live.wndId);
	return (type == null) ? "Container" : type;
    }

    private static String detectContainerType(LiveWindow live) {
	if(!isBlank(live.wndId))
	    return normalizeType(live.wndId);
	Gob gob = currentGob(live);
	String res = MoonGobKind.resourceName(gob);
	if(!isBlank(res))
	    return normalizeType(basename(res));
	return "container";
    }

    private static String normalizeType(String raw) {
	String h = humanizeId(raw);
	return (h == null) ? "container" : safeLower(h).replace(' ', '-');
    }

    private static String safeItemDisplayName(GItem item) {
	if(item == null)
	    return "item";
	try {
	    Resource res = item.getres();
	    if(res != null) {
		Resource.Tooltip tt = res.layer(Resource.tooltip);
		if(tt != null && !isBlank(tt.t))
		    return tt.t;
		return res.basename();
	    }
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
	return "item";
    }

    private static String safeItemResName(GItem item) {
	if(item == null)
	    return null;
	try {
	    Resource res = item.getres();
	    return (res == null) ? null : res.name;
	} catch(Loading ignored) {
	    return null;
	} catch(Exception ignored) {
	    return null;
	}
    }

    private static String updatedBy(GameUI gui) {
	if(gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.user == null)
	    return blankToNull(gui != null ? gui.chrid : null);
	String prs = blankToNull(gui.ui.sess.user.prsname());
	return (prs != null) ? prs : blankToNull(gui.chrid);
    }

    private static String containerIdFor(LiveWindow live) {
	if(live == null)
	    return null;
	String worldId = worldId(live.gui);
	String stableKey = stableKey(worldId, currentGob(live));
	return containerIdFor(worldId, live.wndId, live.gobId, stableKey);
    }

    private static String containerIdFor(String worldId, String wndId, long gobId, String stableKey) {
	if(isBlank(worldId))
	    return null;
	synchronized(lock) {
	    ensureLoaded();
	    String cid = findContainerIdLocked(worldId, gobId, stableKey);
	    if(cid != null)
		return cid;
	}
	if(!isBlank(stableKey))
	    return worldId + "/stable/" + stableKey;
	if(gobId > 0)
	    return worldId + "/gob/" + gobId;
	if(!isBlank(wndId))
	    return worldId + "/wnd/" + normalize(wndId);
	return null;
    }

    private static String worldId(GameUI gui) {
	if(gui == null)
	    return null;
	String chr = blankToNull(gui.chrid);
	if(chr != null)
	    return chr;
	if(gui.ui != null && gui.ui.sess != null && gui.ui.sess.user != null)
	    return blankToNull(gui.ui.sess.user.name);
	return null;
    }

    private static String worldId(MapView mv) {
	if(mv == null || mv.ui == null)
	    return null;
	GameUI gui = mv.getparent(GameUI.class);
	if(gui != null)
	    return worldId(gui);
	if(mv.ui.sess != null && mv.ui.sess.user != null)
	    return blankToNull(mv.ui.sess.user.name);
	return null;
    }

    private static String gobKey(String worldId, long gobId) {
	return worldId + ":" + gobId;
    }

    private static String stableKey(String worldId, Gob gob) {
	if(isBlank(worldId) || gob == null)
	    return null;
	String resName = blankToNull(MoonGobKind.resourceName(gob));
	Coord2d rc = gob.rc;
	if(isBlank(resName) || rc == null)
	    return null;
	return stableKey(worldId, resName, rc.x, rc.y);
    }

    private static String stableKey(ContainerRecord rec) {
	if(rec == null)
	    return null;
	if(!isBlank(rec.stableKey))
	    return rec.stableKey;
	if(isBlank(rec.worldId) || isBlank(rec.gobResName) || !Double.isFinite(rec.worldX) || !Double.isFinite(rec.worldY))
	    return null;
	return stableKey(rec.worldId, rec.gobResName, rec.worldX, rec.worldY);
    }

    private static String stableKey(String worldId, String resName, double worldX, double worldY) {
	if(isBlank(worldId) || isBlank(resName) || !Double.isFinite(worldX) || !Double.isFinite(worldY))
	    return null;
	long gridX = Math.round(Math.floor(worldX / OCache.posres.x));
	long gridY = Math.round(Math.floor(worldY / OCache.posres.y));
	return worldId + ":" + safeLower(resName) + ":" + gridX + ":" + gridY;
    }

    private static String findContainerIdLocked(String worldId, long gobId, String stableKey) {
	if(!isBlank(stableKey)) {
	    String cid = stableIndex.get(stableKey);
	    if(cid != null)
		return cid;
	}
	if(!isBlank(worldId) && gobId > 0) {
	    String cid = gobIndex.get(gobKey(worldId, gobId));
	    if(cid != null)
		return cid;
	}
	return null;
    }

    private static boolean shouldCaptureWindow(LiveWindow live) {
	if(live == null)
	    return false;
	if(live.gobId > 0)
	    return true;
	return MoonMisc.isMiscStorage(live.window, live.wndId);
    }

    private static void requestCapture(LiveWindow live) {
	if(live == null)
	    return;
	long now = System.currentTimeMillis();
	long delay = inventoryHasAnyItems(live.inventory) ? CAPTURE_DEBOUNCE_MS : EMPTY_CAPTURE_DEBOUNCE_MS;
	synchronized(lock) {
	    live.captureAt = now + delay;
	}
    }

    private static boolean inventoryHasAnyItems(Inventory inv) {
	return inv != null && !inv.wmap.isEmpty();
    }

    private static Inventory findInventory(Widget root) {
	if(root == null)
	    return null;
	if(root instanceof Inventory)
	    return (Inventory)root;
	for(Widget ch = root.child; ch != null; ch = ch.next) {
	    Inventory found = findInventory(ch);
	    if(found != null)
		return found;
	}
	return null;
    }

    private static String defaultContainerName(ContainerRecord rec) {
	return !isBlank(rec.displayName) ? rec.displayName : "Container";
    }

    private static String defaultContainerType(ContainerRecord rec) {
	return !isBlank(rec.containerType) ? rec.containerType : "container";
    }

    private static String defaultBuildingName(ContainerRecord rec) {
	if(rec == null)
	    return "Building";
	if(!isBlank(rec.buildingDisplayName))
	    return rec.buildingDisplayName;
	if(!isBlank(rec.buildingType))
	    return humanizeId(rec.buildingType);
	return "Building";
    }

    private static String displayItemName(ItemRecord item) {
	if(item == null)
	    return "item";
	if(!isBlank(item.displayName))
	    return item.displayName;
	if(!isBlank(item.itemResName))
	    return basename(item.itemResName);
	return "item";
    }

    private static String ageText(long ageMs) {
	long sec = Math.max(0L, ageMs / 1000L);
	if(sec < 5)
	    return "just now";
	if(sec < 60)
	    return sec + "s ago";
	long min = sec / 60L;
	if(min < 60)
	    return min + "m ago";
	long hrs = min / 60L;
	long rem = min % 60L;
	if(hrs < 24L) {
	    if(rem == 0)
		return hrs + "h ago";
	    return hrs + "h " + rem + "m ago";
	}
	long days = hrs / 24L;
	return days + "d ago";
    }

    private static String statusText(long ageMs) {
	if(ageMs < FRESH_MS)
	    return "Fresh";
	if(ageMs < AGING_MS)
	    return "Aging";
	return "Stale";
    }

    private static String normalize(String s) {
	if(s == null)
	    return "";
	String t = s.trim().toLowerCase(Locale.ROOT);
	if(t.isEmpty())
	    return "";
	StringBuilder sb = new StringBuilder(t.length());
	boolean sp = false;
	for(int i = 0; i < t.length(); i++) {
	    char c = t.charAt(i);
	    if(Character.isWhitespace(c) || c == '_' || c == '-') {
		if(!sp) {
		    sb.append(' ');
		    sp = true;
		}
	    } else {
		sb.append(c);
		sp = false;
	    }
	}
	return sb.toString().trim();
    }

    private static String humanizeId(String id) {
	String n = normalize(id);
	if(n.isEmpty())
	    return null;
	String[] parts = n.split(" ");
	StringBuilder sb = new StringBuilder(n.length());
	for(int i = 0; i < parts.length; i++) {
	    if(parts[i].isEmpty())
		continue;
	    if(sb.length() > 0)
		sb.append(' ');
	    sb.append(Character.toUpperCase(parts[i].charAt(0)));
	    if(parts[i].length() > 1)
		sb.append(parts[i].substring(1));
	}
	return sb.toString();
    }

    private static String basename(String path) {
	if(path == null)
	    return null;
	int p = path.lastIndexOf('/');
	return (p >= 0 && p + 1 < path.length()) ? path.substring(p + 1) : path;
    }

    private static String safeLower(String s) {
	return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
	return isBlank(s) ? null : s;
    }

    private static boolean isBlank(String s) {
	return s == null || s.trim().isEmpty();
    }

    private static boolean safeEquals(String a, String b) {
	return (a == null) ? (b == null) : a.equals(b);
    }

    private static long asLong(Object o, long def) {
	if(o instanceof Number)
	    return ((Number)o).longValue();
	if(o instanceof String) {
	    try {
		return Long.parseLong(((String)o).trim());
	    } catch(NumberFormatException ignored) {
	    }
	}
	return def;
    }

    private static String spaces(int n) {
	StringBuilder sb = new StringBuilder(n);
	for(int i = 0; i < n; i++)
	    sb.append(' ');
	return sb.toString();
    }
}
