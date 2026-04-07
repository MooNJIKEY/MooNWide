package haven;

import haven.render.MixColor;
import haven.render.Pipe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Workstation and storage status tinting.
 *
 * <p>Defaults follow the requested semantics:
 * empty = green, full/done = red, partial/missing = yellow, water-only = blue.
 */
public final class MoonGobStatus {
    private MoonGobStatus() {}

    private static final Color COL_EMPTY = new Color(0, 255, 0, 180);
    private static final Color COL_FULL = new Color(255, 0, 0, 180);
    private static final Color COL_PARTIAL = new Color(255, 215, 0, 180);
    private static final Color COL_WATER = new Color(70, 135, 255, 180);

    private static final List<StorageStageRule> STORAGE_RULES = Arrays.asList(
	new StorageStageRule("/jotunclam", new int[] {113, 114}, new int[] {1, 2}),
	new StorageStageRule("/cupboard", new int[] {29, 30}, new int[] {1, 2}),
	new StorageStageRule("/metalcabinet", new int[] {65, 66}, new int[] {1, 2}),
	new StorageStageRule("/matalcabinet", new int[] {65, 66}, new int[] {1, 2}),
	new StorageStageRule("/chest", new int[] {29, 30}, new int[] {1, 2}),
	new StorageStageRule("/exquisitechest", new int[] {29, 30}, new int[] {1, 2}),
	new StorageStageRule("/crate", new int[] {16}, new int[] {0}),
	new StorageStageRule("/largechest", new int[] {17, 18}, new int[] {1, 2})
    );

    public static Pipe.Op gobstate(Gob gob) {
	Color col = statusColor(gob);
	return (col == null) ? null : new MixColor(col);
    }

    public static void refresh(GameUI gui) {
	if(gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.oc == null)
	    return;
	try {
	    synchronized(gui.map.glob.oc) {
		for(Gob gob : gui.map.glob.oc) {
		    if(gob != null)
			gob.updated();
		}
	    }
	} catch(Exception ignored) {
	}
    }

    static Color statusColor(Gob gob) {
	if(gob == null)
	    return null;
	String name = MoonGobKind.resourceName(gob);
	if(name == null)
	    return null;
	try {
	    Color c;
	    if((c = cheeseRackColor(gob, name)) != null)
		return c;
	    if((c = storageColor(gob, name)) != null)
		return c;
	    if((c = shedColor(gob, name)) != null)
		return c;
	    if((c = tanningTubColor(gob, name)) != null)
		return c;
	    if((c = coopColor(gob, name)) != null)
		return c;
	    if((c = hutchColor(gob, name)) != null)
		return c;
	    if((c = troughColor(gob, name)) != null)
		return c;
	    if((c = beehiveColor(gob, name)) != null)
		return c;
	    if((c = dryingFrameColor(gob, name)) != null)
		return c;
	    if((c = potColor(gob, name)) != null)
		return c;
	} catch(Loading ignored) {
	}
	return null;
    }

    private static Color cheeseRackColor(Gob gob, String name) {
	if(!MoonConfig.showRackStatus || !name.equals("gfx/terobjs/cheeserack"))
	    return null;
	int eq = overlayCount(gob, "gfx/fx/eq");
	if(eq <= 0)
	    return COL_EMPTY;
	if(eq >= 3)
	    return COL_FULL;
	return MoonConfig.showPartialStorageStatus ? COL_PARTIAL : null;
    }

    private static Color storageColor(Gob gob, String name) {
	if(!MoonConfig.showCupboardStatus)
	    return null;
	int stage = resStage(gob);
	if(stage == Integer.MIN_VALUE)
	    return null;
	for(StorageStageRule rule : STORAGE_RULES) {
	    if(!name.endsWith(rule.suffix))
		continue;
	    if(rule.matches(rule.fullStages, stage))
		return COL_FULL;
	    if(rule.matches(rule.emptyStages, stage))
		return COL_EMPTY;
	    return MoonConfig.showPartialStorageStatus ? COL_PARTIAL : null;
	}
	return null;
    }

    private static Color shedColor(Gob gob, String name) {
	if(!MoonConfig.showShedStatus || !name.endsWith("/shed"))
	    return null;
	int stage = resStage(gob);
	if(stage == 30 || stage == 29)
	    return COL_FULL;
	if(stage == 1 || stage == 2)
	    return COL_EMPTY;
	return MoonConfig.showPartialStorageStatus ? COL_PARTIAL : null;
    }

    private static Color tanningTubColor(Gob gob, String name) {
	if(!MoonConfig.showDframeStatus || !name.equals("gfx/terobjs/ttub"))
	    return null;
	int stage = resStage(gob);
	if(stage == 2)
	    return COL_EMPTY;
	if(stage == 10 || stage == 9 || stage == 8)
	    return COL_FULL;
	if(stage == 0 || stage == 1 || stage == 4 || stage == 5)
	    return COL_WATER;
	return null;
    }

    private static Color coopColor(Gob gob, String name) {
	if(!MoonConfig.showCoopStatus || !name.endsWith("/coop"))
	    return null;
	int stage = resStage(gob);
	if(stage == 0)
	    return COL_FULL;
	if(stage == 1)
	    return COL_PARTIAL;
	if(stage == 2)
	    return COL_EMPTY;
	return null;
    }

    private static Color hutchColor(Gob gob, String name) {
	if(!MoonConfig.showHutchStatus || !name.endsWith("/hutch"))
	    return null;
	int stage = resStage(gob);
	if(stage == 2 || stage == 1 || stage == -62 || stage == -63 || stage == 66 || stage == 65)
	    return COL_FULL;
	if(stage == 6 || stage == 5 || stage == -58 || stage == -59 || stage == 69 || stage == 70 || stage == -51 || stage == -50)
	    return COL_PARTIAL;
	if(stage == -38 || stage == 58 || stage == 57 || stage == -6 || stage == -7 || stage == 122 || stage == 121)
	    return COL_EMPTY;
	return null;
    }

    private static Color troughColor(Gob gob, String name) {
	if(!MoonConfig.showTroughStatus || !name.endsWith("/trough"))
	    return null;
	int stage = resStage(gob);
	if(stage == 0)
	    return COL_EMPTY;
	if(stage == 1)
	    return COL_PARTIAL;
	if(stage == 7)
	    return COL_FULL;
	return null;
    }

    private static Color beehiveColor(Gob gob, String name) {
	if(!MoonConfig.showBeehiveStatus || !name.endsWith("/beehive"))
	    return null;
	int stage = resStage(gob);
	if(stage == 5 || stage == 6 || stage == 7 || stage == 9 || stage == 15)
	    return COL_FULL;
	return null;
    }

    private static Color dryingFrameColor(Gob gob, String name) {
	if(!MoonConfig.showDframeStatus || !name.equals("gfx/terobjs/dframe"))
	    return null;
	boolean empty = true;
	boolean done = true;
	for(String ov : overlayNames(gob)) {
	    empty = false;
	    if(ov.endsWith("-blood") || ov.endsWith("-windweed") || ov.endsWith("fishraw")) {
		done = false;
		break;
	    }
	}
	if(empty)
	    return COL_EMPTY;
	if(done)
	    return COL_FULL;
	return COL_PARTIAL;
    }

    private static Color potColor(Gob gob, String name) {
	if(!MoonConfig.highlightPots)
	    return null;
	if(!name.equals("gfx/terobjs/moundbed") && !name.endsWith("/gardenpot"))
	    return null;
	return overlayCount(gob, "gfx/fx/eq") == 2 ? COL_FULL : null;
    }

    private static int resStage(Gob gob) {
	ResDrawable rd = gob.getattr(ResDrawable.class);
	if(rd == null || rd.sdt == null || rd.sdt.rbuf == null || rd.sdt.rbuf.length == 0)
	    return Integer.MIN_VALUE;
	return rd.sdt.rbuf[0];
    }

    private static int overlayCount(Gob gob, String token) {
	int n = 0;
	for(String name : overlayNames(gob)) {
	    if(name.contains(token))
		n++;
	}
	return n;
    }

    private static List<String> overlayNames(Gob gob) {
	List<String> out = new ArrayList<>();
	List<Gob.Overlay> snap;
	synchronized(gob) {
	    snap = new ArrayList<>(gob.ols);
	}
	for(Gob.Overlay ol : snap) {
	    if(ol == null)
		continue;
	    String nm = null;
	    try {
		if(ol.spr != null && ol.spr.res != null)
		    nm = ol.spr.res.name;
		if(nm == null && ol.sm instanceof Sprite.Mill.FromRes)
		    nm = ((Sprite.Mill.FromRes)ol.sm).res.get().name;
	    } catch(Loading ignored) {
	    }
	    if(nm != null)
		out.add(nm.toLowerCase(Locale.ROOT));
	}
	return out;
    }

    private static final class StorageStageRule {
	final String suffix;
	final int[] fullStages;
	final int[] emptyStages;

	StorageStageRule(String suffix, int[] fullStages, int[] emptyStages) {
	    this.suffix = suffix;
	    this.fullStages = fullStages;
	    this.emptyStages = emptyStages;
	}

	boolean matches(int[] stages, int stage) {
	    if(stages == null)
		return false;
	    for(int cur : stages) {
		if(cur == stage)
		    return true;
	    }
	    return false;
	}
    }
}
