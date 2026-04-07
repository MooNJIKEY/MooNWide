package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a scrollable summary of equipped items: mining-related lines from {@link ItemInfo.AdHoc}
 * plus stacked long tooltips (same rendering as hover).
 *
 * <p>Estimated mining power uses the widely repeated community formula
 * {@code √(STR × k × Q)} where {@code Q} is item quality from tooltips, {@code STR} is effective
 * strength ({@link Glob.CAttr#comp}), {@code k = 2} for pickaxes and {@code k = 1} for stone axes
 * (developer note quoted on forums: pickaxe quality counts double toward mining). This is a client
 * estimate only; the game server may differ.
 */
public final class MoonEquBuffSummary {
    private MoonEquBuffSummary() {}

    private static final Pattern MINING_PCT = Pattern.compile("(?i)mining[^%]*?(-?\\d+(?:\\.\\d+)?)\\s*%");
    /** Fallback when QBuff/quality ItemInfo is missing but plain tooltip text lists quality. */
    private static final Pattern QUALITY_LINE = Pattern.compile(
	"(?i)(quality|качество)\\b[\\s:：\\-–—]{0,6}(-?\\d+(?:\\.\\d+)?)");

    private static final class PowerEst {
	double power;
	int str;
	double q;
	double k;
	String kindKey;
    }

    /** @return 0 = not a supported mining tool path; 1 = stone axe; 2 = pickaxe */
    private static double miningKindMultiplier(String resPath) {
	if(resPath == null)
	    return(0);
	String p = resPath.toLowerCase(Locale.ROOT);
	if(p.contains("pickaxe"))
	    return(2.0);
	if(p.contains("stoneaxe") || p.contains("stone-axe") || p.contains("/saxe"))
	    return(1.0);
	return(0);
    }

    private static double qualityFromTips(List<ItemInfo> infos) {
	if(infos == null)
	    return(0);
	List<ItemInfo.Tip> tips = new ArrayList<>();
	collectTips(infos, tips);
	for(ItemInfo.Tip t : tips) {
	    if(t instanceof ItemInfo.AdHoc) {
		String s = ((ItemInfo.AdHoc)t).raw;
		if(s == null)
		    continue;
		Matcher m = QUALITY_LINE.matcher(s);
		if(m.find()) {
		    try {
			return(Double.parseDouble(m.group(2)));
		    } catch(NumberFormatException ignored) {}
		}
	    }
	}
	return(0);
    }

    /** Uses {@link MoonItemQuality#readQ(List)} on {@link GItem} info, then tooltip text fallback. */
    private static double gitemQuality(GItem gi) {
	try {
	    List<ItemInfo> infos = gi.info();
	    double q = MoonItemQuality.readQ(infos);
	    if(q > 0)
		return(q);
	    double fromText = qualityFromTips(infos);
	    if(fromText > 0)
		return(fromText);
	} catch(Loading ignored) {}
	return(0);
    }

    /** Equipped {@link GItem} instances (authoritative via {@link Equipory#wmap}). */
    private static List<GItem> collectEquipped(Equipory eq) {
	List<GItem> items = new ArrayList<>();
	if(eq != null && eq.wmap != null && !eq.wmap.isEmpty()) {
	    for(GItem g : eq.wmap.keySet()) {
		if(g != null)
		    items.add(g);
	    }
	} else if(eq != null) {
	    for(Widget w = eq.child; w != null; w = w.next) {
		if(w instanceof GItem)
		    items.add((GItem)w);
	    }
	}
	return(items);
    }

    /**
     * Changes when slots or item tooltip generations change — needed because quality often arrives
     * after {@code childseq} stabilizes.
     */
    static int equipChangeDigest(Equipory eq) {
	int d = (eq == null) ? 0 : eq.childseq;
	for(GItem gi : collectEquipped(eq)) {
	    d = d * 31 + gi.infoseq;
	}
	Glob glob = (eq == null || eq.ui == null || eq.ui.sess == null) ? null : eq.ui.sess.glob;
	d = d * 31 + Equipory.attrcomp(glob, "str");
	d = d * 31 + Equipory.attrcomp(glob, "prc");
	d = d * 31 + Equipory.attrcomp(glob, "int");
	d = d * 31 + Equipory.attrcomp(glob, "explore");
	d = d * 31 + Equipory.attrcomp(glob, "stealth");
	return(d);
    }

    private static int strComp(Equipory eq) {
	try {
	    if(eq != null && eq.ui != null && eq.ui.sess != null) {
		Glob.CAttr ca = eq.ui.sess.glob.getcattr("str");
		if(ca != null)
		    return(ca.comp);
	    }
	} catch(Exception ignored) {}
	return(0);
    }

    private static PowerEst computeMiningPowerEst(Equipory eq, List<GItem> items) {
	if(eq == null || items == null || items.isEmpty())
	    return(null);
	int str = strComp(eq);
	if(str <= 0)
	    return(null);
	PowerEst best = null;
	for(GItem gi : items) {
	    try {
		if(gi.res == null)
		    continue;
		Resource res = gi.res.get();
		String nm = res.name;
		double k = miningKindMultiplier(nm);
		if(k <= 0)
		    continue;
		double q = gitemQuality(gi);
		if(q <= 0)
		    continue;
		double power = Math.sqrt(str * k * q);
		if(best == null || power > best.power) {
		    best = new PowerEst();
		    best.power = power;
		    best.str = str;
		    best.q = q;
		    best.k = k;
		    best.kindKey = (k >= 1.9) ? "equ.side.mining_kind_pick" : "equ.side.mining_kind_saxe";
		}
	    } catch(Loading ignored) {
	    }
	}
	return(best);
    }

    /** {@code toolSlot} = equipped pickaxe/stone axe; {@code toolHasQ} = that tool has parsable Q. */
    private static void scanMiningToolQuality(List<GItem> items, boolean[] toolSlot, boolean[] toolHasQ) {
	toolSlot[0] = false;
	toolHasQ[0] = false;
	if(items == null)
	    return;
	for(GItem gi : items) {
	    try {
		if(gi.res == null)
		    continue;
		Resource res = gi.res.get();
		if(miningKindMultiplier(res.name) <= 0)
		    continue;
		toolSlot[0] = true;
		if(gitemQuality(gi) > 0)
		    toolHasQ[0] = true;
	    } catch(Loading ignored) {
	    }
	}
    }

    private static String miningPowerFallbackKey(Equipory eq, List<GItem> items) {
	int str = strComp(eq);
	if(str <= 0)
	    return("equ.side.mining_power_no_str");
	boolean[] tool = new boolean[1], qok = new boolean[1];
	scanMiningToolQuality(items, tool, qok);
	if(!tool[0])
	    return("equ.side.mining_power_no_tool");
	if(!qok[0])
	    return("equ.side.mining_power_no_q");
	return("equ.side.mining_power_na");
    }

    private static void collectTips(List<ItemInfo> list, List<ItemInfo.Tip> out) {
	for(ItemInfo ii : list) {
	    if(ii instanceof ItemInfo.Contents) {
		collectTips(((ItemInfo.Contents)ii).sub, out);
	    } else if(ii instanceof ItemInfo.Tip) {
		out.add((ItemInfo.Tip)ii);
	    }
	}
    }

    private static void miningFromInfo(List<ItemInfo> info, LinkedHashSet<String> lines, double[] sumPct) {
	List<ItemInfo.Tip> tips = new ArrayList<>();
	collectTips(info, tips);
	for(ItemInfo.Tip t : tips) {
	    if(t instanceof ItemInfo.AdHoc) {
		String s = ((ItemInfo.AdHoc)t).raw;
		if(s == null)
		    continue;
		if(s.toLowerCase().contains("mining")) {
		    lines.add(s);
		    Matcher m = MINING_PCT.matcher(s);
		    while(m.find()) {
			try {
			    sumPct[0] += Double.parseDouble(m.group(1));
			} catch(NumberFormatException ignored) {}
		    }
		}
	    }
	}
    }

    static BufferedImage compose(Equipory eq) {
	List<GItem> items = collectEquipped(eq);
	List<BufferedImage> blocks = new ArrayList<>();
	int wmax = UI.scale(40);

	if(items.isEmpty()) {
	    BufferedImage empty = Text.render(LocalizationManager.tr("equ.side.empty"), Color.GRAY).img;
	    blocks.add(empty);
	    wmax = Math.max(wmax, empty.getWidth());
	} else {
	    int[] summary = Equipory.summaryMetrics(eq);
	    Text.Line sh = Text.render("Equipment stats", MoonPanel.MOON_ACCENT);
	    blocks.add(sh.img);
	    wmax = Math.max(wmax, sh.img.getWidth());
	    BufferedImage det = Text.render(String.format(Locale.ROOT, "Per*Exp: %s", summary[0]), MoonUiTheme.ACCENT_DARK).img;
	    BufferedImage subtle = Text.render(String.format(Locale.ROOT, "Int*Ste: %s", summary[1]), MoonUiTheme.ACCENT_DARK).img;
	    BufferedImage arm = Text.render(String.format(Locale.ROOT, "Armor class: %s (%s + %s)",
		summary[2], summary[3], summary[4]), MoonUiTheme.ACCENT_DARK).img;
	    blocks.add(det);
	    blocks.add(subtle);
	    blocks.add(arm);
	    wmax = Math.max(wmax, Math.max(det.getWidth(), Math.max(subtle.getWidth(), arm.getWidth())));

	    Text.Line mh = Text.render(LocalizationManager.tr("equ.side.hdr_mining"), MoonPanel.MOON_ACCENT);
	    blocks.add(mh.img);
	    wmax = Math.max(wmax, mh.img.getWidth());
	    PowerEst est = computeMiningPowerEst(eq, items);
	    if(est != null) {
		BufferedImage main = Text.render(String.format(Locale.ROOT,
		    LocalizationManager.tr("equ.side.mining_power_main"), est.power), MoonUiTheme.ACCENT).img;
		BufferedImage sub = Text.render(String.format(Locale.ROOT,
		    LocalizationManager.tr("equ.side.mining_power_sub"), est.str, est.q, est.k,
		    LocalizationManager.tr(est.kindKey)), MoonUiTheme.ACCENT_DARK).img;
		blocks.add(main);
		blocks.add(sub);
		wmax = Math.max(wmax, Math.max(main.getWidth(), sub.getWidth()));
	    } else {
		BufferedImage fallback = Text.render(LocalizationManager.tr(miningPowerFallbackKey(eq, items)), Color.GRAY).img;
		blocks.add(fallback);
		wmax = Math.max(wmax, fallback.getWidth());
	    }

	    Text.Line mm = Text.render(LocalizationManager.tr("equ.side.hdr_mining_mods"), MoonPanel.MOON_ACCENT);
	    blocks.add(mm.img);
	    wmax = Math.max(wmax, mm.img.getWidth());
	    LinkedHashSet<String> miningLines = new LinkedHashSet<>();
	    double[] sumPct = new double[] {0.0};
	    for(GItem gi : items) {
		try {
		    miningFromInfo(gi.info(), miningLines, sumPct);
		} catch(Loading ignored) {}
	    }
	    if(miningLines.isEmpty()) {
		BufferedImage none = Text.render(LocalizationManager.tr("equ.side.mining_mods_none"), Color.GRAY).img;
		blocks.add(none);
		wmax = Math.max(wmax, none.getWidth());
	    } else {
		for(String line : miningLines) {
		    BufferedImage mined = Text.render(line, MoonUiTheme.ACCENT_DARK).img;
		    blocks.add(mined);
		    wmax = Math.max(wmax, mined.getWidth());
		}
		BufferedImage sum = Text.render(String.format(Locale.ROOT,
		    LocalizationManager.tr("equ.side.mining_sum"), sumPct[0]), MoonUiTheme.ACCENT).img;
		blocks.add(sum);
		wmax = Math.max(wmax, sum.getWidth());
	    }

	    Text.Line bh = Text.render(LocalizationManager.tr("equ.side.hdr_buffs"), MoonPanel.MOON_ACCENT);
	    blocks.add(bh.img);
	    wmax = Math.max(wmax, bh.img.getWidth());
	    for(GItem gi : items) {
		try {
		    BufferedImage lt = ItemInfo.longtip(gi.info());
		    if(lt != null) {
			blocks.add(lt);
			wmax = Math.max(wmax, lt.getWidth());
		    }
		} catch(Loading ignored) {}
	    }
	}

	int gap = UI.scale(6);
	int h = -gap;
	for(BufferedImage b : blocks) {
	    h += b.getHeight() + gap;
	}
	BufferedImage out = TexI.mkbuf(new Coord(wmax, Math.max(UI.scale(8), h)));
	java.awt.Graphics g = out.getGraphics();
	int y = 0;
	for(BufferedImage b : blocks) {
	    g.drawImage(b, 0, y, null);
	    y += b.getHeight() + gap;
	}
	g.dispose();
	return(out);
    }

    public static final class BuffBody extends Widget {
	public final Equipory eq;
	private int lastDigest = -1;
	private TexI content;

	public BuffBody(Equipory eq) {
	    super(Coord.z);
	    this.eq = eq;
	}

	@Override
	public void tick(double dt) {
	    super.tick(dt);
	    int dig = equipChangeDigest(eq);
	    if((dig != lastDigest) || (content == null)) {
		if(rebuild())
		    lastDigest = dig;
	    }
	}

	private boolean rebuild() {
	    try {
		BufferedImage img = compose(eq);
		if(content != null)
		    content.dispose();
		content = (img == null) ? null : new TexI(img);
		if(img != null)
		    resize(img.getWidth(), img.getHeight());
		else
		    resize(UI.scale(8), UI.scale(8));
		for(Widget p = parent; p != null; p = p.parent) {
		    if(p instanceof Scrollport.Scrollcont) {
			((Scrollport.Scrollcont)p).update();
			break;
		    }
		}
		return(true);
	    } catch(Loading ignored) {
		return(false);
	    }
	}

	@Override
	public void draw(GOut g) {
	    if(content != null)
		g.image(content, Coord.z);
	}

	@Override
	public void dispose() {
	    if(content != null) {
		content.dispose();
		content = null;
	    }
	    super.dispose();
	}
    }
}
