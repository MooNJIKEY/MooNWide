package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Client-side craft hints: RMS softcap from character attributes matching qmods,
 * per-skill lines, and hardcap from input item qualities when all required inputs report Q.
 */
public final class MoonCraft {

    private MoonCraft() {}

    /** Attribute key for Glob.getcattr — basename of gfx/hud/chr/… resource. */
    public static String qmodAttrKey(Resource res) {
        String n = res.name;
        final String pfx = "gfx/hud/chr/";
        if (n.startsWith(pfx))
            n = n.substring(pfx.length());
        int slash = n.indexOf('/');
        return (slash >= 0) ? n.substring(0, slash).intern() : n.intern();
    }

    public static String qmodLabel(Resource res) {
        try {
            Resource.Tooltip tt = res.flayer(Resource.tooltip);
            if (tt != null)
                return LocalizationManager.autoTranslateProcessed(tt.t);
        } catch (Loading ignored) {}
        return qmodAttrKey(res);
    }

    public static String resourceLabel(Indir<Resource> ir) {
        if(ir == null)
            return "";
        try {
            Resource res = ir.get();
            Resource.Tooltip tt = res.layer(Resource.tooltip);
            if(tt != null)
                return LocalizationManager.autoTranslateProcessed(tt.t);
            return res.name;
        } catch(Loading ignored) {
            return "...";
        }
    }

    public static int skillValue(Glob glob, Resource qmod) {
        String key = qmodAttrKey(qmod);
        Glob.CAttr a = glob.getcattr(key);
        return a.base + a.comp;
    }

    /** RMS of relevant skills (common Haven approximation for expected “soft” quality). */
    public static double softcapRms(Glob glob, List<Indir<Resource>> qmods) {
        if (qmods == null || qmods.isEmpty())
            return 0;
        double sumSq = 0;
        int n = 0;
        for (Indir<Resource> ir : qmods) {
            try {
                Resource r = ir.get();
                int v = skillValue(glob, r);
                sumSq += (double) v * (double) v;
                n++;
            } catch (Loading ignored) {
                return 0;
            }
        }
        return n > 0 ? Math.sqrt(sumSq / n) : 0;
    }

    /** Delegates to {@link MoonItemQuality#readQ(List)}. */
    public static double specQuality(Makewindow.Spec spec) {
        try {
            return MoonItemQuality.readQ(spec.info());
        } catch (Loading ignored) {
            return 0;
        }
    }

    /**
     * Minimum input quality for hardcap hint, or -1 if any required input has unknown quality
     * (workstation / hidden input Q — then we hide hardcap).
     */
    public static double hardcapMin(List<Makewindow.Input> inputs) {
	if (inputs == null || inputs.isEmpty())
	    return -1;
	double min = Double.MAX_VALUE;
        int req = 0;
        for (Makewindow.Input in : inputs) {
            boolean optional;
            try {
                optional = in.spec.opt();
            } catch (Loading e) {
                return -1;
            }
            if (optional)
                continue;
            req++;
            double q = specQuality(in.spec);
            if (q <= 0)
                return -1;
            min = Math.min(min, q);
        }
	if (req == 0)
	    return -1;
	return min;
    }

    public static double hardcapMin(List<Makewindow.Input> inputs, MoonCraftLedger.Snapshot stock) {
	if(inputs == null || inputs.isEmpty())
	    return(-1);
	double min = Double.MAX_VALUE;
	int req = 0;
	for(Makewindow.Input in : inputs) {
	    boolean optional;
	    try {
		optional = in.spec.opt();
	    } catch(Loading e) {
		return(-1);
	    }
	    if(optional)
		continue;
	    req++;
	    double q = specQuality(in.spec);
	    if((q <= 0) && (stock != null))
		q = stock.bestQualityFor(in.spec);
	    if(q <= 0)
		return(-1);
	    min = Math.min(min, q);
	}
	if(req == 0)
	    return(-1);
	return(min);
    }

    public static int maxCraftable(List<Makewindow.Input> inputs, MoonCraftLedger.Snapshot stock) {
	if(inputs == null || inputs.isEmpty() || stock == null)
	    return(0);
	int ret = Integer.MAX_VALUE;
	int req = 0;
	for(Makewindow.Input in : inputs) {
	    boolean optional;
	    try {
		optional = in.spec.opt();
	    } catch(Loading e) {
		return(0);
	    }
	    if(optional)
		continue;
	    req++;
	    int need = Math.max(1, in.spec.count);
	    int have = stock.countFor(in.spec);
	    ret = Math.min(ret, have / need);
	}
	return((req == 0 || ret == Integer.MAX_VALUE) ? 0 : ret);
    }

    public static int moonInfoLineCount(Glob glob, List<Indir<Resource>> qmod, List<Makewindow.Input> inputs) {
        if (qmod == null || qmod.isEmpty())
            return 0;
        try {
            for (Indir<Resource> ir : qmod)
                ir.get();
        } catch (Loading e) {
            return 0;
        }
        int lines = 1 + qmod.size();
        if (hardcapMin(inputs) > 0)
            lines++;
        return lines;
    }

    public static double expectedQuality(double softcap, double hardcap) {
        if(softcap <= 0)
            return 0;
        if(hardcap > 0)
            return Math.min(softcap, hardcap);
        return softcap;
    }

    public static int moonInfoHeight(UI ui, Makewindow w) {
        int n = moonInfoLineCount(ui.sess.glob, w.qmod, w.inputs);
        if (n <= 0)
            return 0;
        return UI.scale(6) + n * UI.scale(13);
    }

    public static void drawMoonInfo(GOut g, UI ui, Makewindow w, int topy) {
        Glob glob = ui.sess.glob;
        List<Indir<Resource>> qmod = w.qmod;
        List<Makewindow.Input> inputs = w.inputs;
        if (qmod == null || qmod.isEmpty())
            return;

        Text.Foundry small = new Text.Foundry(Text.sans, UI.scale(11)).aa(true);
        int y = topy;
        int x = UI.scale(5);

        try {
            double soft = softcapRms(glob, qmod);
            BufferedImage bi = small.render(String.format(LocalizationManager.tr("craft.softcap"), soft), Color.WHITE).img;
            g.image(bi, new Coord(x, y));
            y += bi.getHeight() + UI.scale(1);

            for (Indir<Resource> ir : qmod) {
                Resource res = ir.get();
                int sv = skillValue(glob, res);
                String lab = qmodLabel(res);
                BufferedImage li = small.render(String.format(LocalizationManager.tr("craft.skillline"), lab, sv), new Color(220, 220, 200)).img;
                g.image(li, new Coord(x, y));
                y += li.getHeight() + UI.scale(1);
            }

            double hc = hardcapMin(inputs);
            if (hc > 0) {
                BufferedImage hi = small.render(String.format(LocalizationManager.tr("craft.hardcap"), hc), new Color(255, 200, 160)).img;
                g.image(hi, new Coord(x, y));
            }
        } catch (Loading ignored) {}
    }
}
