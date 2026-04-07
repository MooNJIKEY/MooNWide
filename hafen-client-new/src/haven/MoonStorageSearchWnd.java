package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MoonStorageSearchWnd extends Window {
    public final GameUI gui;

    private static final Coord WNDSZ = UI.scale(new Coord(520, 360));
    private static final Coord PAD = UI.scale(new Coord(10, 10));
    private static final int LIST_H = 11;
    private static final int ITEM_H = UI.scale(24);

    private final List<MoonStorage.SearchHit> rows = new ArrayList<>();
    private final TextEntry query;
    private final Label summary;
    private final CheckBox currentHouseOnly;
    private final Results list;
    private boolean houseScope = Utils.getprefb("moon-storage-search-current-house", false);
    private String currentHouseId = null;
    private String currentHouseName = null;

    public MoonStorageSearchWnd(GameUI gui) {
	super(WNDSZ, "Storage Search", true);
	this.gui = gui;

	Coord cur = ca().ul.add(PAD);
	int innerW = ca().sz().x - (PAD.x * 2);
	int queryW = innerW - UI.scale(190);
	query = add(new TextEntry(queryW, "") {
		protected void changed() {
		    refreshRows();
		}

		public void activate(String text) {
		    if(list.sel != null)
			activateResult(list.sel);
		    else
			refreshRows();
		}
	    }, cur);
	add(new Button(UI.scale(80), "Refresh", this::refreshRows), cur.add(query.sz.x + UI.scale(6), 0));
	add(new Button(UI.scale(96), "Clear Track", this::clearTrack), cur.add(query.sz.x + UI.scale(92), 0));

	cur = cur.add(0, query.sz.y + UI.scale(8));
	summary = add(new Label("Type an item name to search."), cur);
	currentHouseOnly = add(new CheckBox("Current House") {
		{
		    a = houseScope;
		}

		public void set(boolean val) {
		    houseScope = val;
		    a = val;
		    Utils.setprefb("moon-storage-search-current-house", val);
		    refreshRows();
		}
	    }, cur.add(innerW - UI.scale(110), 0));
	currentHouseOnly.settip("Limit results to the building you're currently inside.", true);

	cur = cur.add(0, Math.max(summary.sz.y, currentHouseOnly.sz.y) + UI.scale(6));
	list = add(new Results(new Coord(innerW, (ITEM_H * LIST_H) + UI.scale(6))), cur);

	refreshRows();
    }

    public void focusQuery() {
	if(parent != null)
	    parent.setfocus(query);
    }

    private void clearTrack() {
	MoonStorage.clearTracking();
	if(gui != null && gui.ui != null)
	    gui.ui.msg("Storage tracking cleared.", Color.WHITE, null);
    }

    private void refreshRows() {
	rows.clear();
	currentHouseId = null;
	currentHouseName = null;
	if(houseScope) {
	    currentHouseId = MoonStorage.currentBuildingId(gui);
	    currentHouseName = MoonStorage.currentBuildingName(gui);
	}
	rows.addAll(MoonStorage.search(query.text(), currentHouseId, 128));
	summary.settext(summaryText());
	list.reset();
	list.change(rows.isEmpty() ? null : rows.get(0));
    }

    private String summaryText() {
	String q = query.text().trim();
	String scope = currentScopeText();
	if(q.isEmpty())
	    return "Type an item name to search" + scope + ".";
	if(houseScope && currentHouseId == null)
	    return "Current house scope is on, but no building is detected.";
	if(rows.isEmpty())
	    return "No cached matches for \"" + q + "\"" + scope + ".";
	return rows.size() + " cached matches for \"" + q + "\"" + scope + ".";
    }

    private void activateResult(MoonStorage.SearchHit hit) {
	if(hit == null)
	    return;
	MoonStorage.track(hit);
	MoonStorage.flashTrackedResult(gui, hit);
	if(gui != null && gui.ui != null) {
	    String where = hit.containerDisplayName;
	    if(hit.buildingDisplayName != null && !hit.buildingDisplayName.trim().isEmpty())
		where += " / " + hit.buildingDisplayName;
	    gui.ui.msg("Tracking " + hit.itemDisplayName + " in " + where + ".", Color.WHITE, null);
	}
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == this && "close".equals(msg)) {
	    Utils.setprefc("wndc-moon-storage-search", this.c);
	    hide();
	    return;
	}
	super.wdgmsg(sender, msg, args);
    }

    private String rowText(MoonStorage.SearchHit hit) {
	String qty = "x" + Math.max(hit.quantity, 1);
	String age = ageText(System.currentTimeMillis() - hit.lastSeenAt);
	StringBuilder sb = new StringBuilder(96);
	sb.append(hit.itemDisplayName).append(' ').append(qty);
	sb.append("  |  ").append(hit.containerDisplayName);
	if(hit.buildingDisplayName != null && !hit.buildingDisplayName.trim().isEmpty())
	    sb.append("  |  ").append(hit.buildingDisplayName);
	sb.append("  |  ").append(age);
	return sb.toString();
    }

    private Object rowTooltip(MoonStorage.SearchHit hit) {
	StringBuilder sb = new StringBuilder(160);
	sb.append("$b{").append(RichText.Parser.quote(hit.itemDisplayName)).append("}");
	sb.append("\nContainer: ").append(RichText.Parser.quote(hit.containerDisplayName));
	if(hit.buildingDisplayName != null && !hit.buildingDisplayName.trim().isEmpty())
	    sb.append("\nHouse: ").append(RichText.Parser.quote(hit.buildingDisplayName));
	sb.append("\nType: ").append(RichText.Parser.quote(hit.containerType));
	sb.append("\nQuantity: ").append(Math.max(hit.quantity, 1));
	sb.append("\nLast seen: ").append(ageText(System.currentTimeMillis() - hit.lastSeenAt));
	if(Double.isFinite(hit.worldX) && Double.isFinite(hit.worldY))
	    sb.append("\nCoords: ").append(String.format(Locale.ROOT, "%.1f, %.1f", hit.worldX, hit.worldY));
	return RichText.render(sb.toString(), UI.scale(320));
    }

    private String currentScopeText() {
	if(!houseScope)
	    return "";
	if(currentHouseName != null && !currentHouseName.trim().isEmpty())
	    return " in " + currentHouseName;
	return " in the current house";
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
	return (hrs / 24L) + "d ago";
    }

    private class Results extends SListBox<MoonStorage.SearchHit, Widget> {
	private Results(Coord sz) {
	    super(sz, ITEM_H);
	}

	protected List<? extends MoonStorage.SearchHit> items() {
	    return rows;
	}

	protected Widget makeitem(MoonStorage.SearchHit el, int idx, Coord sz) {
	    return new ItemWidget<MoonStorage.SearchHit>(this, sz, el) {
		private double lastClickAt = 0;

		{
		    add(TextItem.of(sz, CharWnd.attrf, () -> rowText(item)), Coord.z);
		}

		public boolean mousedown(MouseDownEvent ev) {
		    boolean alreadySel = (sel == item);
		    super.mousedown(ev);
		    if(ev.b == 1) {
			double now = Utils.rtime();
			if(alreadySel && (now - lastClickAt < 0.45))
			    activateResult(item);
			lastClickAt = now;
		    }
		    return true;
		}

		public Object tooltip(Coord c, Widget prev) {
		    return rowTooltip(item);
		}
	    };
	}
    }
}
