package haven;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Vanilla inventory slot clicks ({@link WItem}): maps mouse button + modifiers to {@link GItem} widget messages.
 * Optional {@link Extension}s run first for modules / experiments.
 */
public final class InventoryItemClickPolicy {
    private InventoryItemClickPolicy() {}

    public interface Extension {
	/** @return true if handled (no further processing). */
	boolean tryHandle(WItem wi, Widget.MouseDownEvent ev);
    }

    private static final List<Extension> extensions = new CopyOnWriteArrayList<>();

    public static void registerExtension(Extension ext) {
	if(ext != null)
	    extensions.add(ext);
    }

    public static boolean handle(WItem wi, Widget.MouseDownEvent ev) {
	for(Extension ext : extensions) {
	    try {
		if(ext.tryHandle(wi, ev))
		    return(true);
	    } catch(Exception ignored) {
	    }
	}
	return(applyVanilla(wi, ev));
    }

    private static boolean applyVanilla(WItem wi, Widget.MouseDownEvent ev) {
	GItem item = wi.item;
	UI ui = wi.ui;
	if(ev.b == 1) {
	    if(ui.modshift) {
		int n = ui.modctrl ? -1 : 1;
		MoonInventoryWireDebug.maybeLogItem("transfer", ev.c, Integer.valueOf(n));
		item.wdgmsg("transfer", ev.c, n);
	    } else if(ui.modctrl) {
		int n = ui.modmeta ? -1 : 1;
		MoonInventoryWireDebug.maybeLogItem("drop", ev.c, Integer.valueOf(n));
		item.wdgmsg("drop", ev.c, n);
	    } else {
		MoonInventoryWireDebug.maybeLogItem("take", ev.c);
		item.wdgmsg("take", ev.c);
	    }
	    return(true);
	} else if(ev.b == 3) {
	    int mf = ui.modflags();
	    MoonInventoryWireDebug.maybeLogItem("iact", ev.c, Integer.valueOf(mf));
	    item.wdgmsg("iact", ev.c, mf);
	    return(true);
	}
	return(false);
    }
}
