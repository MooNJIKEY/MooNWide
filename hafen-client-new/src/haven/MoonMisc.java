package haven;

import java.util.Locale;

/**
 * Server-driven misc windows (belt container, keyring, etc.): Moon styling and controls.
 */
public final class MoonMisc {
    private MoonMisc() {}

    /**
     * Detect belt / keyring from server window id or title ({@code cap} message).
     * <p>
     * Do not use {@link Inventory} slot dimensions alone: many crates and cabinets use the same
     * 3×2 or 8×1 grids as the belt bag / keyring and would incorrectly get {@link MoonMiscDeco}.
     */
    public static boolean isBeltOrKeyring(Window w, String wndid) {
	if(wndid != null && !wndid.isEmpty()) {
	    String id = wndid.toLowerCase(Locale.ROOT);
	    if(id.contains("belt") || id.contains("keyring"))
		return(true);
	}
	if(w == null)
	    return(false);
	String c = w.cap;
	if(c == null)
	    return(false);
	String n = c.toLowerCase(Locale.ROOT).trim();
	if(n.equals("belt") || n.equals("keyring"))
	    return(true);
	/* English titles may include extra words */
	if(n.contains("belt") || n.contains("keyring") || n.contains("key ring"))
	    return(true);
	/* Russian: пояс = belt; ключница = key cabinet (avoid bare "ключ" — too many false positives) */
	if(n.contains("пояс") || n.contains("ключниц"))
	    return(true);
	return(false);
    }

    /**
     * Apply {@link MoonMiscDeco} to server {@code misc} windows: belt/keyring and storage (crates,
     * chests, barrels, cupboards, etc.). Previously only belt/keyring were styled; containers kept vanilla {@link Window.DefaultDeco}.
     */
    public static void applyIfEligible(Window w, String wndid) {
	if(w == null)
	    return;
	if(!isBeltOrKeyring(w, wndid) && !isMiscStorage(w, wndid))
	    return;
	String id = (wndid != null && !wndid.isEmpty()) ? wndid : "misc";
	w.chdeco(new MoonMiscDeco(id));
    }

    /** Heuristic: misc windows that are inventories in world objects (not main inv / craft-only). */
    public static boolean isMiscStorage(Window w, String wndid) {
	if(wndid != null && !wndid.isEmpty()) {
	    String id = wndid.toLowerCase(Locale.ROOT);
	    if(id.contains("crate") || id.contains("chest") || id.contains("cupboard") || id.contains("cabinet")
		|| id.contains("barrel") || id.contains("cistern") || id.contains("locker") || id.contains("shelf")
		|| id.contains("rack") || id.contains("bin") || id.contains("basket") || id.contains("stockpile")
		|| id.contains("wagon") || id.contains("container") || id.contains("storage"))
		return(true);
	}
	if(w == null || w.cap == null)
	    return(false);
	String n = w.cap.toLowerCase(Locale.ROOT);
	if(n.contains("crate") || n.contains("chest") || n.contains("cupboard") || n.contains("barrel")
	    || n.contains("cabinet") || n.contains("cistern") || n.contains("locker") || n.contains("storage")
	    || n.contains("ящик") || n.contains("сундук") || n.contains("шкаф") || n.contains("бочк")
	    || n.contains("хранили") || n.contains("короб"))
	    return(true);
	return(false);
    }

    /**
     * Re-scan top-level {@link GameUI} windows — catches belt after {@code cap} arrives or inventory children attach.
     */
    public static void refreshGameUIMiscWindows(GameUI gui) {
	if(gui == null)
	    return;
	for(Widget ch = gui.child; ch != null; ch = ch.next) {
	    if(ch instanceof Window) {
		Window w = (Window)ch;
		applyIfEligible(w, gui.miscWndIdFor(w));
	    }
	}
    }

    /**
     * Call after {@link Window#chcap} — title often arrives in a {@code cap} message after the widget is created,
     * so the first {@link GameUI#addchild} pass may have had {@code cap == null}.
     */
    public static void onWindowCapChanged(Window w) {
	GameUI gui = w.getparent(GameUI.class);
	if(gui == null)
	    return;
	applyIfEligible(w, gui.miscWndIdFor(w));
    }
}
