package haven;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MoonAutoDrink {
    public static volatile boolean pendingFlower = false;
    public static volatile String pendingPreferredPetal = null;
    private static double pendingSince = 0;
    private static double lastDrinkTime = 0;
    private static final double FLOWER_TIMEOUT = 5.0;
    private static final double DIRECT_FLOWER_TIMEOUT = 0.35;
    private static final double NO_FLOWER_RETRY_TIMEOUT = 0.9;
    private static WItem pendingWItem = null;
    private static boolean pendingRetried = false;
    private static final boolean DEBUG_AUTODRINK = Boolean.getBoolean("moon.autodrink.debug");
    private static int tickDbgCounter = 0;
    private static volatile boolean dbgSearchThisTick = false;

    private static boolean pendingTransfer = false;
    private static double pendingTransferSince = 0;
    private static final double TRANSFER_TIMEOUT = 2.0;
    private static GItem transferredItem = null;
    private static Inventory transferSourceInv = null;

    private static boolean pendingReturn = false;
    private static double pendingReturnSince = 0;
    private static final double RETURN_DELAY = 3.5;

    private static double cooldownSec() {
        if (MoonConfig.autoDrinkDirectSip || MoonConfig.autoDrinkServerHook)
            return Math.max(0.08, MoonConfig.autoDrinkDirectSipIntervalMs / 1000.0);
        return Math.max(1.0, MoonConfig.autoDrinkIntervalSec);
    }

    private static double flowerTimeoutSec() {
        if ((MoonConfig.autoDrinkDirectSip || MoonConfig.autoDrinkServerHook)
            && "Sip".equalsIgnoreCase(pendingPreferredPetal))
            return DIRECT_FLOWER_TIMEOUT;
        return FLOWER_TIMEOUT;
    }

    /**
     * Called from {@link IMeter#uimsg} after the server applies a stamina {@code set} — same UI command queue as
     * an incoming widget message, so reaction is tied to server updates instead of only {@link GameUI#tick}.
     */
    public static void onServerStaminaSet(GameUI gui, int stamPercent) {
        if (!MoonConfig.autoDrink || !MoonConfig.autoDrinkServerHook)
            return;
        if (gui == null || gui.ui == null)
            return;
        if (stamPercent < 0)
            return;
        if (stamPercent >= effectiveDrinkThreshold())
            return;
        if (pendingFlower || pendingTransfer)
            return;
        double now = Utils.rtime();
        if (now - lastDrinkTime < cooldownSec())
            return;
        if (tryDrink(gui))
            lastDrinkTime = now;
    }

    public static void tick(GameUI gui, double dt) {
        dbgSearchThisTick = false;
        if (DEBUG_AUTODRINK && (++tickDbgCounter % 600 == 1)) {
            dbgSearchThisTick = true;
            int stam = (gui != null && gui.ui != null) ? getCurrentStamina(gui) : -99;
            int effTh = effectiveDrinkThreshold();
            adbg("tick: enabled=" + MoonConfig.autoDrink + " stam=" + stam
                + " thresh=" + effTh + (MoonConfig.autoDrinkMaintainFull ? "(maintainFull)" : "")
                + " pending=" + pendingFlower + " xfer=" + pendingTransfer);
        }
        if (!MoonConfig.autoDrink) return;
        if (gui == null || gui.ui == null) return;

        if (pendingTransfer) {
            double elapsed = Utils.rtime() - pendingTransferSince;
            if (elapsed > TRANSFER_TIMEOUT) {
                if (DEBUG_AUTODRINK) adbg("transfer timeout");
                clearTransferState();
            } else {
                if (gui.maininv != null && transferredItem != null) {
                    WItem wi = gui.maininv.wmap.get(transferredItem);
                    if (wi != null) {
                        if (DEBUG_AUTODRINK) adbg("transferred item arrived in maininv, activating");
                        pendingTransfer = false;
                        lastDrinkTime = Utils.rtime();
                        activateForDrink(gui, wi);
                    }
                }
            }
            return;
        }

        if (pendingReturn) {
            double elapsed = Utils.rtime() - pendingReturnSince;
            if (elapsed >= RETURN_DELAY) {
                if (DEBUG_AUTODRINK) adbg("return transfer now");
                transferItemBack(gui);
                pendingReturn = false;
            }
            /* Don't block other autodrink while waiting to return item */
        }

        if (pendingFlower) {
            double elapsed = Utils.rtime() - pendingSince;
            boolean flowerOpen = (gui.ui.root != null) && (gui.ui.root.findchild(FlowerMenu.class) != null);
            if (!flowerOpen && pendingWItem != null && !pendingRetried && elapsed > 0.3) {
                try {
                    if (DEBUG_AUTODRINK) adbg("retry iact on pending WItem");
                    activateWItem(gui, pendingWItem);
                    pendingRetried = true;
                    pendingSince = Utils.rtime();
                    return;
                } catch (Exception ignored) {}
            }
            if (!flowerOpen && elapsed > NO_FLOWER_RETRY_TIMEOUT) {
                if (transferredItem != null && !pendingReturn) {
                    if (DEBUG_AUTODRINK) adbg("no flower, returning item immediately");
                    transferItemBack(gui);
                    pendingReturn = false;
                }
                clearPendingFlower();
            } else if (elapsed > flowerTimeoutSec()) {
                if (transferredItem != null && !pendingReturn) {
                    if (DEBUG_AUTODRINK) adbg("flower timeout, returning item immediately");
                    transferItemBack(gui);
                    pendingReturn = false;
                }
                clearPendingFlower();
            }
        }
        if (pendingFlower) return;

        double now = Utils.rtime();
        if (now - lastDrinkTime < cooldownSec()) return;

        int stam = getCurrentStamina(gui);
        if (stam < 0) return;
        if (stam >= effectiveDrinkThreshold()) return;

        if (tryDrink(gui)) {
            lastDrinkTime = now;
        }
    }

    /** Target stamina % to start / continue drinking: full bar when {@link MoonConfig#autoDrinkMaintainFull}. */
    public static int effectiveDrinkThreshold() {
        return MoonConfig.autoDrinkMaintainFull ? 100 : MoonConfig.autoDrinkThreshold;
    }

    public static int getCurrentStamina(GameUI gui) {
        try {
            java.util.List<IMeter.Meter> ml = gui.getmeters("stam");
            if (ml != null && !ml.isEmpty())
                return ml.get(0).a;
        } catch (Exception ignored) {}
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof IMeter) {
                IMeter meter = (IMeter) w;
                if (meter.lastTip != null && meter.lastTip.toLowerCase().contains("stam")) {
                    if (!meter.meters.isEmpty())
                        return meter.meters.get(0).a;
                }
            }
        }
        return -1;
    }

    public static boolean tryDrinkBelowPercent(GameUI gui, int minPercent) {
	return tryDrinkBelowPercent(gui, minPercent, MoonConfig.treeBotWater);
    }

    /** @param waterGate when false, no drink attempt (e.g. tree bot vs mine bot water toggle). */
    public static boolean tryDrinkBelowPercent(GameUI gui, int minPercent, boolean waterGate) {
        if (!waterGate) return false;
        if (pendingFlower && (Utils.rtime() - pendingSince > flowerTimeoutSec()))
            clearPendingFlower();
        if (pendingFlower) return false;
        double now = Utils.rtime();
        if (now - lastDrinkTime < cooldownSec()) return false;
        int stam = getCurrentStamina(gui);
        if (stam < 0 || stam >= minPercent) return false;
        if (tryDrink(gui)) {
            lastDrinkTime = now;
            return true;
        }
        return false;
    }

    /* ── Main drink search ── */
    private static boolean tryDrink(GameUI gui) {
        WItem wi = null;

        /* 1. Main inventory — always works with iact */
        if (gui.maininv != null)
            wi = findDrinkableWItem(gui.maininv, true);
        if (dbgSearchThisTick) adbg("maininv: " + descWItem(wi));

        /* 2. Direct equipment slots */
        Equipory ep = gui.findEquipory();
        if (wi == null && ep != null)
            wi = findDrinkableWItem(ep, true);
        if (wi == null && dbgSearchThisTick) adbg("equipory direct: none");

        /* 3. Open windows with loaded contents */
        if (wi == null)
            wi = findDrinkInAnyWindow(gui, true);
        if (wi == null && dbgSearchThisTick) adbg("windows-strict: none");

        /* Steps 1-3 found an item with confirmed liquid — drink directly */
        if (wi != null)
            return activateForDrink(gui, wi);

        /* 4. Belt/container items (may lack loaded info) — transfer to main inventory first */
        WItem containerItem = findDrinkInAnyWindow(gui, false);
        if (containerItem == null && ep != null)
            containerItem = findDrinkInsideEquippedContainers(ep);
        if (containerItem == null) {
            if (dbgSearchThisTick) adbg("containers-relaxed: none");
            return false;
        }
        if (DEBUG_AUTODRINK) adbg("found in container (no contents loaded): " + descWItem(containerItem));
        return initiateTransfer(gui, containerItem);
    }

    /* ── Transfer to main inventory ── */
    private static boolean initiateTransfer(GameUI gui, WItem wi) {
        if (gui.maininv == null) return false;

        int totalSlots = gui.maininv.isz.x * gui.maininv.isz.y;
        int usedSlots = gui.maininv.wmap.size();
        if (usedSlots >= totalSlots) {
            if (DEBUG_AUTODRINK) adbg("maininv full (" + usedSlots + "/" + totalSlots + "), cannot transfer");
            if (MoonConfig.autoDrinkMessage && gui.ui != null)
                gui.ui.msg("Autodrink: inventory full, can't drink from belt.");
            return false;
        }

        Inventory srcInv = null;
        for (Widget w = wi.parent; w != null; w = w.parent) {
            if (w instanceof Inventory) { srcInv = (Inventory) w; break; }
        }

        if (DEBUG_AUTODRINK) {
            String rn = "?";
            try { Resource r = wi.item.getres(); if (r != null) rn = r.name; } catch (Exception ignored) {}
            adbg("transferring " + rn + " to maininv (free=" + (totalSlots - usedSlots) + ")");
        }

        transferredItem = wi.item;
        transferSourceInv = srcInv;
        pendingTransfer = true;
        pendingTransferSince = Utils.rtime();
        wi.item.wdgmsg("transfer", Coord.z);
        return true;
    }

    private static void transferItemBack(GameUI gui) {
        if (transferredItem == null || transferSourceInv == null) return;
        if (gui.maininv == null) return;
        WItem wi = gui.maininv.wmap.get(transferredItem);
        if (wi != null) {
            if (DEBUG_AUTODRINK) adbg("sending transfer-back to source container");
            transferredItem.wdgmsg("transfer", Coord.z);
        }
        clearTransferState();
    }

    private static void clearTransferState() {
        pendingTransfer = false;
        /* Keep transferredItem/transferSourceInv for return transfer */
    }

    public static void scheduleReturnTransfer() {
        if (transferredItem != null && transferSourceInv != null) {
            pendingReturn = true;
            pendingReturnSince = Utils.rtime();
            if (DEBUG_AUTODRINK) adbg("scheduled return transfer in " + RETURN_DELAY + "s");
        }
    }

    /* ── Activation ── */
    private static boolean activateForDrink(GameUI gui, WItem wi) {
        try {
            String petal = choosePreferredPetal(gui, wi.item);
            pendingPreferredPetal = petal;
            pendingFlower = true;
            pendingSince = Utils.rtime();
            pendingWItem = wi;
            pendingRetried = false;

            if (DEBUG_AUTODRINK) {
                String rn = "?";
                try { Resource r = wi.item.getres(); if (r != null) rn = r.name; } catch (Exception ignored) {}
                int wid = -1;
                try { wid = wi.item.wdgid(); } catch (Exception ignored) {}
                adbg("activate iact res=" + rn + " petal=" + petal + " wdgid=" + wid);
            }

            activateWItem(gui, wi);
            return true;
        } catch (Exception e) {
            clearPendingFlower();
            if (MoonConfig.autoDrinkMessage && gui.ui != null)
                gui.ui.msg("Autodrink: activation failed.");
            return false;
        }
    }

    private static void activateWItem(GameUI gui, WItem wi) {
        wi.item.wdgmsg("iact", Coord.z, gui.ui.modflags());
    }

    /* ── Search helpers ── */

    /**
     * Direct WItem scan inside a widget (Equipory, Inventory, etc.).
     * @param strict if true, require loaded Contents info with liquid; if false, match by resource name only.
     */
    private static WItem findDrinkableWItem(Widget parent, boolean strict) {
        for (Widget c = parent.child; c != null; c = c.next) {
            if (c instanceof WItem) {
                WItem wi = (WItem) c;
                if (wi.item != null && canDrinkFromItem(wi.item, strict))
                    return wi;
            }
        }
        return null;
    }

    /** Scan all Window children of GameUI for drinkable items. */
    private static WItem findDrinkInAnyWindow(GameUI gui, boolean strict) {
        for (Widget w = gui.lchild; w != null; w = w.prev) {
            if (!(w instanceof Window)) continue;
            WItem found = deepFindDrinkWItem(w, strict);
            if (found != null) return found;
        }
        return null;
    }

    /** Scan equipped items' sub-inventories (nested contents). */
    private static WItem findDrinkInsideEquippedContainers(Equipory ep) {
        for (Widget c = ep.child; c != null; c = c.next) {
            if (!(c instanceof WItem)) continue;
            WItem equippedWi = (WItem) c;
            if (equippedWi.item == null) continue;
            Widget contents = equippedWi.item.contents;
            if (contents instanceof Inventory) {
                WItem inner = findDrinkableWItem(contents, false);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    /** Recursive search through arbitrary widget tree for a drinkable WItem. */
    private static WItem deepFindDrinkWItem(Widget root, boolean strict) {
        for (Widget c = root.child; c != null; c = c.next) {
            if (c instanceof WItem) {
                WItem wi = (WItem) c;
                if (wi.item != null && canDrinkFromItem(wi.item, strict))
                    return wi;
                if (wi.item != null && wi.item.contents instanceof Inventory) {
                    WItem nested = findDrinkableWItem(wi.item.contents, strict);
                    if (nested != null) return nested;
                }
            }
            WItem deeper = deepFindDrinkWItem(c, strict);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /* ── Item classification ── */

    private static boolean isDrinkContainer(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("waterflask") || n.contains("waterskin") || n.contains("waterbag")
            || n.contains("watersack") || n.contains("canteen")
            || n.contains("bucket-water") || n.contains("kuksa")
            || n.contains("tankard") || n.contains("glass-")
            || n.contains("cup") || n.contains("mug")
            || n.contains("flask") || n.contains("bottle")
            || n.contains("jug") || n.contains("skin")
            || n.contains("tea") || n.contains("birchsap")
            || (n.contains("invobjs") && (n.contains("water") || n.contains("drink") || n.contains("sip")));
    }

    private static ItemInfo.Contents getContents(GItem item) {
        if (item == null || item.ui == null) return null;
        synchronized (item.ui) {
            try {
                for (ItemInfo info : item.info()) {
                    if (info instanceof ItemInfo.Contents)
                        return (ItemInfo.Contents) info;
                }
            } catch (Loading ignored) {}
        }
        return null;
    }

    /**
     * @param strict if true, require loaded Contents info; if false, resource name is enough.
     */
    private static boolean canDrinkFromItem(GItem item, boolean strict) {
        if (item == null) return false;
        String rname = null;
        try {
            Resource res = item.getres();
            if (res == null) return false;
            rname = res.name;
            if (!isDrinkContainer(rname)) return false;
        } catch (Loading l) { return false; }

        if (!strict) {
            if (MoonConfig.autoDrinkWhatever) return true;
            if (rname != null) {
                String n = rname.toLowerCase();
                if (n.contains("waterskin") || n.contains("waterflask")
                    || n.contains("waterbag") || n.contains("watersack") || n.contains("canteen"))
                    return true;
            }
            return false;
        }

        ItemInfo.Contents c = getContents(item);
        if (c == null || c.sub == null) return false;

        if (MoonConfig.autoDrinkWhatever) return true;
        String need = MoonConfig.autoDrinkLiquid;
        String needLc = (need == null) ? "" : need.toLowerCase();
        for (ItemInfo info : c.sub) {
            if (info instanceof ItemInfo.Name) {
                ItemInfo.Name nm = (ItemInfo.Name) info;
                if (nm.str != null || nm.dynsrc != null) {
                    String txt = nm.nameText();
                    String txtLc = txt.toLowerCase();
                    if (txt.contains(need) || txtLc.contains(needLc)) return true;
                    if ("water".equals(needLc) && txtLc.contains("вода")) return true;
                }
            }
        }
        return false;
    }

    /* ── Petal selection ── */

    private static String choosePreferredPetal(GameUI gui, GItem item) {
        if (MoonConfig.autoDrinkServerHook)
            return "Sip";
        String byCombat = preferredPetalByCombat(gui);
        if (byCombat != null) return byCombat;
        if (MoonConfig.autoDrinkDirectSip) return "Sip";
        if (MoonConfig.autoDrinkSmartSip) {
            try {
                ItemInfo.Contents c = getContents(item);
                String liquid = "";
                if (c != null && c.sub != null) {
                    for (ItemInfo info : c.sub) {
                        if (info instanceof ItemInfo.Name) {
                            ItemInfo.Name nm = (ItemInfo.Name) info;
                            if (nm.str != null || nm.dynsrc != null) { liquid = nm.nameText(); break; }
                        }
                    }
                }
                return liquid.contains("Water") ? "Drink" : "Sip";
            } catch (Exception ignored) {}
        }
        if (MoonConfig.autoDrinkUseSip) return "Sip";
        return "Drink";
    }

    private static String preferredPetalByCombat(GameUI gui) {
        if (gui == null) return null;
        return isActiveCombat(gui) ? "Sip" : "Drink";
    }

    private static boolean isActiveCombat(GameUI gui) {
        if (gui == null || gui.fv == null) return false;
        try {
            return (gui.fv.current != null) || (gui.fv.lsrel != null && !gui.fv.lsrel.isEmpty());
        } catch (Exception ignored) { return false; }
    }

    /* ── State management ── */

    public static void clearPendingFlower() {
        pendingFlower = false;
        pendingPreferredPetal = null;
        pendingWItem = null;
        pendingRetried = false;
    }

    public static boolean isBusy() {
        return pendingFlower || pendingTransfer || pendingReturn;
    }

    public static String statusSummary(GameUI gui) {
        if(!MoonConfig.autoDrink)
            return "disabled";
        if(pendingTransfer)
            return "transferring drink item";
        if(pendingFlower)
            return "waiting flower menu";
        if(pendingReturn)
            return "returning borrowed item";
        int stam = (gui != null) ? getCurrentStamina(gui) : -1;
        String th = Integer.toString(effectiveDrinkThreshold());
        return (stam >= 0) ? ("armed, stam " + stam + "% / " + th + "%") : ("armed, threshold " + th + "%");
    }

    /* ── Debug ── */

    private static String descWItem(WItem wi) {
        if (wi == null) return "none";
        try {
            Resource r = wi.item.getres();
            return (r != null ? r.name : "no-res");
        } catch (Exception e) { return "err"; }
    }

    private static void adbg(String s) {
        System.err.println("[AUTODRINK] " + s);
        try {
            Path p = FileSystems.getDefault().getPath(System.getProperty("user.home", "."), ".haven", "moon-autodrink-debug.log");
            Path dir = p.getParent();
            if (dir != null) Files.createDirectories(dir);
            try (java.io.FileWriter fw = new java.io.FileWriter(p.toFile(), true)) {
                fw.write(s + "\n");
            }
        } catch (Throwable t) {
            System.err.println("[AUTODRINK] log write failed: " + t);
        }
    }
}
