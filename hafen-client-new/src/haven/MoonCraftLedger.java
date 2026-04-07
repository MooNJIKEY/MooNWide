package haven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Snapshot of visible crafting inputs from open inventories and stockpile-style ISBox windows.
 * Quality can only be read from real item tooltips; ISBox piles contribute count but not quality.
 */
public final class MoonCraftLedger {
    private MoonCraftLedger() {}

    public static final class Stock {
	public final String resource;
	public final String label;
	public int count;
	public double bestQuality;
	public boolean qualityKnown;

	public Stock(String resource, String label) {
	    this.resource = resource;
	    this.label = label;
	}
    }

    public static final class Snapshot {
	private final Map<String, Stock> byResource;
	private final Map<String, Stock> byLabel;

	private Snapshot(Map<String, Stock> byResource, Map<String, Stock> byLabel) {
	    this.byResource = byResource;
	    this.byLabel = byLabel;
	}

	private Stock find(Makewindow.Spec spec) {
	    if(spec == null)
		return(null);
	    try {
		Resource res = spec.res.get();
		if((res != null) && (res.name != null)) {
		    Stock hit = byResource.get(res.name);
		    if(hit != null)
			return(hit);
		}
	    } catch(Loading ignored) {
	    }
	    String label = norm(MoonCraft.resourceLabel(spec.res));
	    if(!label.isEmpty()) {
		Stock hit = byLabel.get(label);
		if(hit != null)
		    return(hit);
	    }
	    return(null);
	}

	public int countFor(Makewindow.Spec spec) {
	    Stock hit = find(spec);
	    return((hit == null) ? 0 : hit.count);
	}

	public double bestQualityFor(Makewindow.Spec spec) {
	    Stock hit = find(spec);
	    if((hit == null) || !hit.qualityKnown)
		return(0);
	    return(hit.bestQuality);
	}

	public boolean qualityKnownFor(Makewindow.Spec spec) {
	    Stock hit = find(spec);
	    return((hit != null) && hit.qualityKnown);
	}
    }

    public static Snapshot empty() {
	return(new Snapshot(Collections.emptyMap(), Collections.emptyMap()));
    }

    public static Snapshot snapshot(GameUI gui) {
	Map<String, Stock> byResource = new LinkedHashMap<>();
	Map<String, Stock> byLabel = new LinkedHashMap<>();
	if((gui == null) || (gui.ui == null) || (gui.ui.root == null))
	    return(new Snapshot(byResource, byLabel));
	List<Inventory> invs = new ArrayList<>();
	List<ISBox> boxes = new ArrayList<>();
	collect(gui.ui.root, invs, boxes);
	for(Inventory inv : invs) {
	    for(WItem wi : inv.wmap.values()) {
		if((wi == null) || !wi.tvisible())
		    continue;
		String resource = "";
		String label = "";
		try {
		    Resource res = wi.item.getres();
		    if((res != null) && (res.name != null))
			resource = res.name;
		    Resource.Tooltip tt = res.layer(Resource.tooltip);
		    label = (tt == null) ? res.basename() : LocalizationManager.autoTranslateProcessed(tt.t);
		} catch(Exception ignored) {
		}
		Stock stock = ensure(byResource, byLabel, resource, label);
		stock.count += itemCount(wi);
		double q = itemQuality(wi);
		if(q > 0) {
		    stock.qualityKnown = true;
		    stock.bestQuality = Math.max(stock.bestQuality, q);
		}
	    }
	}
	for(ISBox box : boxes) {
	    if((box == null) || !box.tvisible())
		continue;
	    String resource = "";
	    String label = "";
	    try {
		Resource res = box.resource().get();
		if((res != null) && (res.name != null))
		    resource = res.name;
		Resource.Tooltip tt = res.layer(Resource.tooltip);
		label = (tt == null) ? res.basename() : LocalizationManager.autoTranslateProcessed(tt.t);
	    } catch(Exception ignored) {
	    }
	    Stock stock = ensure(byResource, byLabel, resource, label);
	    stock.count += Math.max(0, box.remaining());
	}
	return(new Snapshot(byResource, byLabel));
    }

    private static void collect(Widget w, List<Inventory> invs, List<ISBox> boxes) {
	if(w == null || !w.visible())
	    return;
	if(w instanceof Inventory) {
	    invs.add((Inventory)w);
	} else if(w instanceof ISBox) {
	    boxes.add((ISBox)w);
	}
	for(Widget ch = w.child; ch != null; ch = ch.next)
	    collect(ch, invs, boxes);
    }

    private static Stock ensure(Map<String, Stock> byResource, Map<String, Stock> byLabel, String resource, String label) {
	Stock stock = null;
	if((resource != null) && !resource.isEmpty())
	    stock = byResource.get(resource);
	if((stock == null) && (label != null) && !label.isEmpty())
	    stock = byLabel.get(norm(label));
	if(stock == null) {
	    stock = new Stock((resource == null) ? "" : resource, (label == null) ? "" : label);
	    if((resource != null) && !resource.isEmpty())
		byResource.put(resource, stock);
	    if((label != null) && !label.isEmpty())
		byLabel.put(norm(label), stock);
	}
	return(stock);
    }

    private static String norm(String s) {
	return((s == null) ? "" : s.trim().toLowerCase(Locale.ROOT));
    }

    private static int itemCount(WItem wi) {
	try {
	    List<ItemInfo> info = wi.item.info();
	    if(info != null) {
		for(ItemInfo inf : info) {
		    if(inf instanceof GItem.NumberInfo)
			return(Math.max(1, ((GItem.NumberInfo)inf).itemnum()));
		}
	    }
	} catch(Exception ignored) {
	}
	return(1);
    }

    private static double itemQuality(WItem wi) {
	try {
	    return MoonItemQuality.readQ(wi.item.info());
	} catch(Exception ignored) {
	    return 0;
	}
    }
}
