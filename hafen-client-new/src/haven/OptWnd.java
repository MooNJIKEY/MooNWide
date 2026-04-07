/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import config.ModuleConfig;
import core.ModuleLoader;
import haven.discord.MoonDiscordService;
import haven.render.*;
import managers.automation.TaskQueue;
import managers.combat.CombatAnalyzer;
import managers.combat.CombatManager;
import managers.resource.ResourceScanner;
import managers.ui.UIEnhancer;
import modules.Module;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class OptWnd extends Window {
    public final Panel main;
    public final Panel moonchat, moonautomation, moonoverlay, mooncombat, moonperformance, moonintegrations, moonfunctions, moonexperimental, moonspeed, mooninvquality;
    public Panel current;
    private static final Coord moonsz = UI.scale(new Coord(736, 500));
    private static final int optpadx = UI.scale(14);
    private static final int optpady = UI.scale(10);
    private static final int optpadbr = UI.scale(18);
    private static final String EXPERIMENTAL_PASSWORD_SHA256 = "6b0ddab8e6b622e9e07a717531db01dcdebc47b483213da6306181a151166917";
    private boolean experimentalUnlocked = false;

    /** Viewport width inside {@link Scrollport} (minus scrollbar) for wrapping long option notes. */
    private static int moonScrollInnerW() {
	return(Math.max(UI.scale(240), moonsz.x - UI.scale(48)));
    }

    private static Coord moonScrollViewport() {
	return(new Coord(moonsz.x, Math.max(UI.scale(220), moonsz.y - UI.scale(46))));
    }

    private Coord fixedBackPos(Scrollport scroll) {
	return(scroll.pos("bl").adds(optpadx, UI.scale(12)));
    }

    private PButton addFixedBackButton(Panel owner, Scrollport scroll, int width, Panel prev) {
	return(owner.add(new PButton(width, LocalizationManager.tr("opt.back"), 27, prev), fixedBackPos(scroll)));
    }

    private static Label addMoonWrappedNote(Widget cont, Widget prev, int xMargin, String l10nKey) {
	return(addMoonWrappedText(cont, prev, xMargin, LocalizationManager.tr(l10nKey)));
    }

    private static Label addMoonWrappedText(Widget cont, Widget prev, int xMargin, String text) {
	int w = moonScrollInnerW() - xMargin;
	if(w < UI.scale(140))
	    w = UI.scale(140);
	Label lb = new Label(text, w);
	cont.add(lb, prev.pos("bl").adds(0, 2).x(xMargin));
	return(lb);
    }

    private static String mapLoadCutsText(int cuts) {
	if(cuts <= 0)
	    return(LocalizationManager.tr("opt.perf.mapload.default"));
	return(String.format(Locale.ROOT, LocalizationManager.tr("opt.perf.mapload.val"), cuts, cuts * MCache.cutsz.x));
    }

    private static String perfPresetText(MoonConfig.PerfPreset preset) {
	switch(preset) {
	case MAX_FPS:
	    return(LocalizationManager.tr("opt.perf.preset.maxfps"));
	case VISUAL_FIRST:
	    return(LocalizationManager.tr("opt.perf.preset.visual"));
	default:
	    return(LocalizationManager.tr("opt.perf.preset.balanced"));
	}
    }

    private static String runtimeCpuProfileText(MoonConfig.RuntimeCpuProfile profile) {
	switch(profile) {
	case AMD_RYZEN5_6C:
	    return(LocalizationManager.tr("opt.perf.cpu.amd.ryzen5"));
	case AMD_RYZEN7_8C:
	    return(LocalizationManager.tr("opt.perf.cpu.amd.ryzen7"));
	case AMD_RYZEN_X3D_8C:
	    return(LocalizationManager.tr("opt.perf.cpu.amd.x3d"));
	case INTEL_I5_6C:
	    return(LocalizationManager.tr("opt.perf.cpu.intel.i5"));
	case INTEL_HYBRID_MID:
	    return(LocalizationManager.tr("opt.perf.cpu.intel.hybrid.mid"));
	case INTEL_HYBRID_HIGH:
	    return(LocalizationManager.tr("opt.perf.cpu.intel.hybrid.high"));
	case CUSTOM:
	    return(LocalizationManager.tr("opt.perf.cpu.custom"));
	case AUTO:
	default:
	    return(LocalizationManager.tr("opt.perf.cpu.auto"));
	}
    }

    private static String runtimeTuneSummaryText() {
	MoonConfig.RuntimeTune tune = MoonConfig.runtimeTune();
	return(String.format(Locale.ROOT, LocalizationManager.tr("opt.perf.cpu.summary"),
	    tune.activeProcessors, tune.loaderThreads, tune.deferThreads,
	    tune.heapMinGb, tune.heapMaxGb,
	    Runtime.getRuntime().availableProcessors()));
    }

    private static String perfBudgetText(String key, int val) {
	return(String.format(Locale.ROOT, LocalizationManager.tr(key), val));
    }

    private Widget addPlayerAlertEditor(Widget cont, Widget prev, String key, MoonPlayerAlerts.Kind kind,
				       Supplier<Boolean> enabled, Consumer<Boolean> setEnabled,
				       Supplier<Integer> volume, IntConsumer setVolume,
				       Supplier<String> path, Consumer<String> setPath) {
	prev = cont.add(new CheckBox(LocalizationManager.tr(key)) {
		{ a = enabled.get(); }
		public void set(boolean val) {
		    setEnabled.accept(val);
		    a = val;
		}
	    }, prev.pos("bl").adds(0, 8).x(0));
	{
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
	    prev = cont.add(new HSlider(UI.scale(220), 0, 1000, volume.get()) {
		    protected void added() {
			dpy.settext(pct(val / 1000.0));
		    }

		    public void changed() {
			setVolume.accept(val);
			dpy.settext(pct(val / 1000.0));
		    }
		}, prev.pos("bl").adds(UI.scale(18), 3).x(0));
	}
	TextEntry pth = cont.add(new TextEntry(UI.scale(360), path.get()) {
		{
		    dshow = true;
		}

		public void activate(String text) {
		    setPath.accept(text);
		    commit();
		}
	    }, prev.pos("bl").adds(UI.scale(18), 5).x(0));
	cont.add(new Button(UI.scale(76), LocalizationManager.tr("opt.audio.alert.test"), false).action(() -> {
		    setPath.accept(pth.text());
		    MoonPlayerAlerts.test(ui, kind);
		}), pth.pos("ur").adds(UI.scale(8), 0));
	prev = pth;
	return(prev);
    }

    public void chpanel(Panel p) {
	if(current != null)
	    current.hide();
	(current = p).show();
	cresize(p);
    }

    public void cresize(Widget ch) {
	if(ch == current) {
	    Coord cc = this.c.add(this.sz.div(2));
	    pack();
	    move(cc.sub(this.sz.div(2)));
	}
    }

    public class PButton extends Button {
	public final Panel tgt;
	public final int key;

	public PButton(int w, String title, int key, Panel tgt) {
	    super(w, title, false);
	    this.tgt = tgt;
	    this.key = key;
	}

	public void click() {
	    chpanel(tgt);
	}

	public boolean keydown(KeyDownEvent ev) {
	    if((this.key != -1) && (ev.c == this.key)) {
		click();
		return(true);
	    }
	    return(super.keydown(ev));
	}
    }

    public class Panel extends Widget {
	public Panel() {
	    visible = false;
	    c = Coord.z;
	}
    }

    private void error(String msg) {
	GameUI gui = getparent(GameUI.class);
	if(gui != null)
	    gui.error(msg);
    }

    private Widget addMoonExperimentalJniSection(Widget cont, Widget prev) {
	prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.experimental.wire") + " ---"), prev.pos("bl").adds(0, 14).x(0));

	prev = cont.add(new Label(LocalizationManager.tr("exp.jni.subhdr")), prev.pos("bl").adds(0, 8).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("exp.jni.enable")) {
		{ a = MoonConfig.experimentalJniWireHook; }
		public void set(boolean val) {
		    MoonConfig.setExperimentalJniWireHook(val);
		    a = val;
		}
	    }, prev.pos("bl").adds(0, 6).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "exp.jni.note.short");
	prev = cont.add(new CheckBox(LocalizationManager.tr("exp.jni.log")) {
		{ a = MoonConfig.debugJniWireLog; }
		public void set(boolean val) {
		    MoonConfig.setDebugJniWireLog(val);
		    a = val;
		}
	    }, prev.pos("bl").adds(0, 6).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "exp.jni.log.note");
	prev = cont.add(new CheckBox(LocalizationManager.tr("exp.jni.mirrorstdout")) {
		{ a = MoonConfig.debugJniWireMirrorStdout; }
		public void set(boolean val) {
		    MoonConfig.setDebugJniWireMirrorStdout(val);
		    a = val;
		}
	    }, prev.pos("bl").adds(0, 4).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "exp.jni.mirrorstdout.note");
	prev = cont.add(new CheckBox(LocalizationManager.tr("exp.jni.nativestderr")) {
		{ a = MoonConfig.debugJniWireNativeStderr; }
		public void set(boolean val) {
		    MoonConfig.setDebugJniWireNativeStderr(val);
		    a = val;
		}
	    }, prev.pos("bl").adds(0, 4).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "exp.jni.nativestderr.note");
	prev = addMoonWrappedText(cont, prev, UI.scale(20),
	    MoonJniPacketHook.isNativeLoaded()
		? LocalizationManager.tr("exp.jni.status.yes") : LocalizationManager.tr("exp.jni.status.no"));
	return prev;
    }

    private Widget addMoonSpeedSection(Widget cont, Widget prev, final Widget wirePingAnchor) {
	prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.speed.basic") + " ---"), prev.pos("bl").adds(0, 14).x(0));

	prev = cont.add(new CheckBox(LocalizationManager.tr("speed.remember")) {
		{ a = MoonConfig.rememberSpeedMode; }
		public void set(boolean val) { MoonConfig.setRememberSpeedMode(val); a = val; }
	    }, prev.pos("bl").adds(0, 8).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("diablo.move")) {
		{ a = MoonConfig.diabloMove; }
		public void set(boolean val) { MoonConfig.setDiabloMove(val); a = val; }
	    }, prev.pos("bl").adds(0, 6).x(0));

	prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.speed.visual") + " ---"), prev.pos("bl").adds(0, 14).x(0));

	prev = cont.add(new Label(LocalizationManager.tr("speed.visualmult")), prev.pos("bl").adds(0, 8).x(0));
	{
	    int vmInit = (int) Math.round(Utils.clip(MoonConfig.linMoveVisualSpeedMult * 10.0, 10.0, 30.0));
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(220)));
	    prev = cont.add(new HSlider(UI.scale(200), 10, 30, vmInit) {
		    protected void added() {
			dpy.settext(String.format(LocalizationManager.tr("speed.visualmult.val"), val / 10.0));
		    }
		    public void changed() {
			Glob gl = (ui != null && ui.sess != null) ? ui.sess.glob : null;
			MoonConfig.setLinMoveVisualSpeedMult(val / 10.0, gl);
			dpy.settext(String.format(LocalizationManager.tr("speed.visualmult.val"), val / 10.0));
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	}
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.visualmult.note");
	prev = addMoonWrappedText(cont, prev, UI.scale(20),
	    LocalizationManager.LANG_RU.equals(MoonL10n.lang())
		? "Экспериментальные функции бега, серверного sprint и скрытых режимов стамины перенесены в Experimental."
		: "Experimental running, server sprint and hidden stamina tweaks were moved to Experimental.");

	return prev;
    }

    private Widget addAutoDrinkSection(Widget cont, Widget prev) {
	prev = cont.add(new Label("--- " + moonText("Автопитьё и стамина", "Auto drink & stamina") + " ---"), prev.pos("bl").adds(0, 14).x(0));

	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.enable")) {
		{ a = MoonConfig.autoDrink; }
		public void set(boolean val) { MoonConfig.setAutoDrink(val); a = val; }
	    }, prev.pos("bl").adds(0, 10).x(0));

	prev = cont.add(new Label(LocalizationManager.tr("autodrink.threshold")), prev.pos("bl").adds(0, 5).x(0));
	{
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(250), 10, 80, MoonConfig.autoDrinkThreshold) {
		    protected void added() { dpy.settext(val + "%"); }
		    public void changed() {
			MoonConfig.setAutoDrinkThreshold(val);
			dpy.settext(val + "%");
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	}

	prev = cont.add(new Label(LocalizationManager.tr("autodrink.interval")), prev.pos("bl").adds(0, 6).x(0));
	{
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(250), 1, 15, MoonConfig.autoDrinkIntervalSec) {
		    protected void added() { dpy.settext(val + "s"); }
		    public void changed() {
			MoonConfig.setAutoDrinkIntervalSec(val);
			dpy.settext(val + "s");
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	}

	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.whatever")) {
		{ a = MoonConfig.autoDrinkWhatever; }
		public void set(boolean val) { MoonConfig.setAutoDrinkWhatever(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.sipmode")) {
		{ a = MoonConfig.autoDrinkUseSip; }
		public void set(boolean val) { MoonConfig.setAutoDrinkUseSip(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.smartsip")) {
		{ a = MoonConfig.autoDrinkSmartSip; }
		public void set(boolean val) { MoonConfig.setAutoDrinkSmartSip(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.siponce")) {
		{ a = MoonConfig.autoSipOnce; }
		public void set(boolean val) { MoonConfig.setAutoSipOnce(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));

	prev = cont.add(new Label(LocalizationManager.tr("autodrink.sipthreshold")), prev.pos("bl").adds(0, 6).x(0));
	{
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(250), 10, 100, MoonConfig.autoSipThreshold) {
		    protected void added() { dpy.settext(val + "%"); }
		    public void changed() {
			MoonConfig.setAutoSipThreshold(val);
			dpy.settext(val + "%");
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	}

	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.msg")) {
		{ a = MoonConfig.autoDrinkMessage; }
		public void set(boolean val) { MoonConfig.setAutoDrinkMessage(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.directsip")) {
		{ a = MoonConfig.autoDrinkDirectSip; }
		public void set(boolean val) { MoonConfig.setAutoDrinkDirectSip(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.serverhook")) {
		{ a = MoonConfig.autoDrinkServerHook; }
		public void set(boolean val) { MoonConfig.setAutoDrinkServerHook(val); a = val; }
	    }, prev.pos("bl").adds(0, 5).x(0));
	prev = cont.add(new Label(LocalizationManager.tr("autodrink.serverhook.note")), prev.pos("bl").adds(20, 2).x(UI.scale(20)));

	prev = cont.add(new Label(LocalizationManager.tr("autodrink.directsip.int")), prev.pos("bl").adds(0, 6).x(0));
	{
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(250), 150, 2000, MoonConfig.autoDrinkDirectSipIntervalMs) {
		    protected void added() { dpy.settext(val + "ms"); }
		    public void changed() {
			MoonConfig.setAutoDrinkDirectSipIntervalMs(val);
			dpy.settext(val + "ms");
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	}

	prev = cont.add(new CheckBox(LocalizationManager.tr("autodrink.maintainfull")) {
		{ a = MoonConfig.autoDrinkMaintainFull; }
		public void set(boolean val) { MoonConfig.setAutoDrinkMaintainFull(val); a = val; }
	    }, prev.pos("bl").adds(0, 8).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "autodrink.maintainfull.note");
	return prev;
    }

    private Widget addMoonExperimentalSpeedSection(Widget cont, Widget prev, final Widget wirePingAnchor) {
	prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.speed.wire") + " ---"), prev.pos("bl").adds(0, 14).x(0));

	prev = cont.add(new CheckBox(LocalizationManager.tr("exp.speedwire.enable")) {
		{ a = MoonConfig.experimentalSpeedWireAssist; }
		public void set(boolean val) {
		    MoonConfig.setExperimentalSpeedWireAssist(val);
		    a = val;
		    MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor);
		}
	    }, prev.pos("bl").adds(0, 6).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "exp.speedwire.note");

	prev = cont.add(new Label(LocalizationManager.tr("speed.wiremult")), prev.pos("bl").adds(0, 10).x(0));
	{
	    int smInit = (int)Math.round(Utils.clip(MoonConfig.speedMultiplier * 10.0, 10.0, 50.0));
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(240), 10, 50, smInit) {
		    protected void added() { dpy.settext(String.format(LocalizationManager.tr("speed.wiremult.val"), val / 10.0)); }
		    public void changed() {
			MoonConfig.setSpeedMultiplier(val / 10.0);
			dpy.settext(String.format(LocalizationManager.tr("speed.wiremult.val"), val / 10.0));
		    }
		    public void fchanged() { MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor); }
		}, prev.pos("bl").adds(0, 3).x(0));
	}
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.wiremult.note");

	prev = cont.add(new CheckBox(LocalizationManager.tr("speed.clientscale")) {
		{ a = MoonConfig.clientSpeedScale; }
		public void set(boolean val) {
		    MoonConfig.setClientSpeedScale(val);
		    a = val;
		    MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor);
		}
	    }, prev.pos("bl").adds(0, 8).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.clientscale.note");

	prev = cont.add(new CheckBox(LocalizationManager.tr("speed.serversprint")) {
		{ a = MoonConfig.clientScaleServerSprint; }
		public void set(boolean val) {
		    MoonConfig.setClientScaleServerSprint(val);
		    a = val;
		    MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor);
		}
	    }, prev.pos("bl").adds(0, 6).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.serversprint.note");

	prev = cont.add(new Label(LocalizationManager.tr("speed.serverlift")), prev.pos("bl").adds(0, 8).x(0));
	{
	    int liftInit = Utils.clip(MoonConfig.serverSpeedLift, 0, 3);
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(240), 0, 3, liftInit) {
		    protected void added() { dpy.settext(String.format(LocalizationManager.tr("speed.serverlift.val"), val)); }
		    public void changed() {
			MoonConfig.setServerSpeedLift(val);
			dpy.settext(String.format(LocalizationManager.tr("speed.serverlift.val"), val));
		    }
		    public void fchanged() { MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor); }
		}, prev.pos("bl").adds(0, 3).x(0));
	}
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.serverlift.note");

	prev = cont.add(new Label(LocalizationManager.tr("speed.mult.resend")), prev.pos("bl").adds(0, 8).x(0));
	{
	    int rsInit = (int)Math.round(Utils.clip(MoonConfig.speedMultResendIntervalSec, 0.0, 120.0));
	    Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
	    prev = cont.add(new HSlider(UI.scale(240), 0, 120, rsInit) {
		    protected void added() { dpy.settext(val == 0 ? LocalizationManager.tr("speed.mult.resend.off") : (val + "s")); }
		    public void changed() {
			MoonConfig.setSpeedMultResendIntervalSec(val);
			dpy.settext(val == 0 ? LocalizationManager.tr("speed.mult.resend.off") : (val + "s"));
		    }
		    public void fchanged() { MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor); }
		}, prev.pos("bl").adds(0, 3).x(0));
	}
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.mult.resend.note");

	prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.speed.boost") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("speed.boost")) {
		{ a = MoonConfig.speedBoost; }
		public void set(boolean val) {
		    MoonConfig.setSpeedBoost(val);
		    a = val;
		    MoonSpeedBoost.pingSpeedgetWire(wirePingAnchor);
		}
	    }, prev.pos("bl").adds(0, 6).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.note");

	prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.speed.debug") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	prev = cont.add(new CheckBox(LocalizationManager.tr("speed.debugwire")) {
		{ a = MoonConfig.debugSpeedWire; }
		public void set(boolean val) { MoonConfig.setDebugSpeedWire(val); a = val; }
	    }, prev.pos("bl").adds(0, 6).x(0));
	prev = addMoonWrappedNote(cont, prev, UI.scale(20), "speed.debugwire.note");
	return prev;
    }

    private void open(Panel panel) {
	chpanel(panel);
	clearStoredFocus();
	Widget gui = getparent(GameUI.class);
	if(gui != null)
	    move(gui.sz.sub(sz).div(2));
	super.show();
	raise();
    }

    private void clearStoredFocus() {
	if(focused != null) {
	    focused.hasfocus = false;
	    focused = null;
	}
    }

    public void showMainPanel() {
	open(main);
    }

    public void showChatPanel() {
	open(moonchat);
    }

    public void showAutomationPanel() {
	open(moonautomation);
    }

    public void showOverlayPanel() {
	open(moonoverlay);
    }

    public void showCombatPanel() {
	open(mooncombat);
    }

    public void showExperimentalPanel() {
	open(moonexperimental);
    }

    public void showPerformancePanel() {
	open(moonperformance);
    }

    public void showSpeedPanel() {
	open(moonspeed);
    }

    private boolean moduleenabled(String moduleName) {
	Module module = ModuleLoader.getModule(moduleName);
	return((module != null) && module.isEnabled());
    }

    private void setmoduleenabled(String moduleName, boolean enabled) {
	if(enabled)
	    ModuleLoader.enable(moduleName);
	else
	    ModuleLoader.disable(moduleName);
    }

    private CheckBox modulebox(String label, String moduleName, String tooltip) {
	return(new CheckBox(label) {
		{
		    a = moduleenabled(moduleName);
		    this.tooltip = tooltip;
		}

		public void set(boolean val) {
		    setmoduleenabled(moduleName, val);
		    a = val;
		}

		public void tick(double dt) {
		    super.tick(dt);
		    boolean cur = moduleenabled(moduleName);
		    if(a != cur)
			a = cur;
		}
	    });
    }

    private Label dynlabel(Supplier<String> supplier) {
	return(new Label("") {
		public void tick(double dt) {
		    super.tick(dt);
		    settext(supplier.get());
		}
	    });
    }

    private Coord chatsz() {
	GameUI gui = getparent(GameUI.class);
	if((gui != null) && (gui.chatwnd != null))
	    return(gui.chatwnd.csz());
	return(Utils.getprefc("wndsz-chat", UI.scale(new Coord(620, 220))));
    }

    private void applychatsz(Coord sz) {
	Utils.setprefc("wndsz-chat", sz);
	GameUI gui = getparent(GameUI.class);
	if((gui != null) && (gui.chatwnd != null))
	    gui.chatwnd.resize(sz);
    }

    private void resetchatpos() {
	GameUI gui = getparent(GameUI.class);
	Coord def = UI.scale(new Coord(20, 180));
	if((gui != null) && (gui.chatwnd != null)) {
	    int belth = (gui.nkeyBelt != null) ? gui.nkeyBelt.sz.y : UI.scale(50);
	    int y = gui.sz.y - belth - gui.chatwnd.sz.y - UI.scale(18);
	    def = new Coord(UI.scale(20), Math.max(UI.scale(90), y));
	}
	Utils.setprefc("wndc-chat", def);
	if((gui != null) && (gui.chatwnd != null))
	    gui.chatwnd.c = def;
    }

    private String pct(double val) {
	return(String.format(Locale.ROOT, "%.0f%%", val * 100.0));
    }

    private static String moonText(String ru, String en) {
	return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }

    private static String sha256Hex(String text) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder buf = new StringBuilder(dig.length * 2);
            for(byte b : dig)
                buf.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return buf.toString();
        } catch(Exception e) {
            return "";
        }
    }

    private boolean unlockExperimental(String pass) {
        experimentalUnlocked = EXPERIMENTAL_PASSWORD_SHA256.equals(sha256Hex(pass == null ? "" : pass));
        return experimentalUnlocked;
    }

    private GameUI gui() {
	return(getparent(GameUI.class));
    }

    private void clear(Widget parent) {
	for(Widget ch = parent.child; ch != null; ) {
	    Widget next = ch.next;
	    ch.destroy();
	    ch = next;
	}
    }

    private int chatalpha() {
	GameUI gui = gui();
	if((gui != null) && (gui.chatwnd != null))
	    return(gui.chatwnd.alpha());
	return(Utils.getprefi("moon-chat-alpha", 210));
    }

    private void applychatalpha(int alpha) {
	alpha = Math.max(60, Math.min(255, alpha));
	Utils.setprefi("moon-chat-alpha", alpha);
	GameUI gui = gui();
	if((gui != null) && (gui.chatwnd != null))
	    gui.chatwnd.setAlpha(alpha);
    }

    private boolean minimapvisible() {
	GameUI gui = gui();
	if(gui != null)
	    return(gui.minimapvisible());
	return(Utils.getprefb("moon-minimap-visible", true));
    }

    private void applyminimapvisible(boolean show) {
	Utils.setprefb("moon-minimap-visible", show);
	GameUI gui = gui();
	if(gui != null)
	    gui.setminimapvisible(show);
    }

    private String currentcamera() {
	return(Utils.getpref("defcam", "ortho"));
    }

    private void applycamera(String camid) {
	Utils.setpref("defcam", camid);
	Utils.setprefb("camargs", Utils.serialize(new String[0]));
	GameUI gui = gui();
	if((gui != null) && (gui.map != null)) {
	    Console.Command cmd = gui.map.findcmds().get("cam");
	    if(cmd != null) {
		try {
		    cmd.run(gui.ui.cons, new String[] {"cam", camid});
		} catch(Exception e) {
		    new Warning(e, "could not switch camera").issue();
		}
	    }
	}
    }

    public class VideoPanel extends Panel {
	private final Scrollport scroll;
	private final Widget back;
	private GSettings bound;

	public VideoPanel(Panel prev) {
	    super();
	    scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    back = addFixedBackButton(this, scroll, UI.scale(220), prev);
	}

	protected void added() {
	    super.added();
	    rebuild();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(visible && (ui != null) && (ui.gprefs != null) && (ui.gprefs != bound))
		rebuild();
	}

	private void rebuild() {
	    Widget root = scroll.cont;
	    clear(root);
	    if((ui == null) || (ui.gprefs == null))
		return;
	    try {
		final GSettings prefs = bound = ui.gprefs;
		Widget prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.video.title")), 0, 0);
		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.video.intro")), prev.pos("bl").adds(0, 4).x(0));

		prev = root.add(new CheckBox(LocalizationManager.tr("opt.vanilla.shadows")) {
			{a = prefs.lshadow.val;}

			public void set(boolean val) {
			    try {
				ui.setgprefs(ui.gprefs.update(null, ui.gprefs.lshadow, val));
				a = val;
			    } catch(GSettings.SettingException e) {
				error(e.getMessage());
			    }
			}
		    }, prev.pos("bl").adds(0, 12).x(0));

		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.render_scale")), prev.pos("bl").adds(0, 12).x(0));
		{
		    final int steps = 8;
		    Label dpy = root.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		    prev = root.add(new HSlider(UI.scale(220), -2 * steps, 1 * steps,
				       (int)Math.round(steps * Math.log(prefs.rscale.val) / Math.log(2.0f))) {
			    protected void added() {
				dpy.settext(String.format(Locale.ROOT, "%.2fx", Math.pow(2, val / (double)steps)));
			    }

			    public void changed() {
				try {
				    float v = (float)Math.pow(2, val / (double)steps);
				    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.rscale, v));
				    dpy.settext(String.format(Locale.ROOT, "%.2fx", v));
				} catch(GSettings.SettingException e) {
				    error(e.getMessage());
				}
			    }
			}, prev.pos("bl").adds(0, 3).x(0));
		}

		prev = root.add(new CheckBox(LocalizationManager.tr("opt.vanilla.vsync")) {
			{a = prefs.vsync.val;}

			public void set(boolean val) {
			    try {
				ui.setgprefs(ui.gprefs.update(null, ui.gprefs.vsync, val));
				a = val;
			    } catch(GSettings.SettingException e) {
				error(e.getMessage());
			    }
			}
		    }, prev.pos("bl").adds(0, 12).x(0));

		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.fps_active")), prev.pos("bl").adds(0, 12).x(0));
		{
		    final int max = 250;
		    int cur = (prefs.hz.val == Float.POSITIVE_INFINITY) ? max : prefs.hz.val.intValue();
		    Label dpy = root.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		    prev = root.add(new HSlider(UI.scale(220), 30, max, cur) {
			    protected void added() {
				dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			    }

			    public void changed() {
				try {
				    float v = (val == max) ? Float.POSITIVE_INFINITY : val;
				    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.hz, v));
				    dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
				} catch(GSettings.SettingException e) {
				    error(e.getMessage());
				}
			    }
			}, prev.pos("bl").adds(0, 3).x(0));
		}

		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.fps_bg")), prev.pos("bl").adds(0, 12).x(0));
		{
		    final int max = 250;
		    int cur = (prefs.bghz.val == Float.POSITIVE_INFINITY) ? max : prefs.bghz.val.intValue();
		    Label dpy = root.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		    prev = root.add(new HSlider(UI.scale(220), 15, max, cur) {
			    protected void added() {
				dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			    }

			    public void changed() {
				try {
				    float v = (val == max) ? Float.POSITIVE_INFINITY : val;
				    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.bghz, v));
				    dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
				} catch(GSettings.SettingException e) {
				    error(e.getMessage());
				}
			    }
			}, prev.pos("bl").adds(0, 3).x(0));
		}

		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.lighting_mode")), prev.pos("bl").adds(0, 12).x(0));
		{
		    boolean[] done = {false};
		    RadioGroup grp = new RadioGroup(root) {
			    public void changed(int btn, String lbl) {
				if(!done[0])
				    return;
				try {
				    ui.setgprefs(ui.gprefs
						.update(null, ui.gprefs.lightmode, GSettings.LightMode.values()[btn])
						.update(null, ui.gprefs.maxlights, 0));
				    rebuild();
				} catch(GSettings.SettingException e) {
				    error(e.getMessage());
				}
			    }
			};
		    prev = grp.add(LocalizationManager.tr("opt.vanilla.light.global"), prev.pos("bl").adds(5, 2));
		    prev = grp.add(LocalizationManager.tr("opt.vanilla.light.zoned"), prev.pos("bl").adds(0, 2));
		    grp.check(prefs.lightmode.val.ordinal());
		    done[0] = true;
		}

		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.light_limit")), prev.pos("bl").adds(0, 12).x(0));
		{
		    int cur = prefs.maxlights.val;
		    int max = 32;
		    if(cur == 0) {
			cur = (prefs.lightmode.val == GSettings.LightMode.ZONED) ? Lighting.LightGrid.defmax : Lighting.SimpleLights.defmax;
		    }
		    if(prefs.lightmode.val == GSettings.LightMode.SIMPLE)
			max = 4;
		    Label dpy = root.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		    prev = root.add(new HSlider(UI.scale(220), 1, max, Math.max(1, cur / 4)) {
			    protected void added() {
				dpy.settext(Integer.toString(val * 4));
			    }

			    public void changed() {
				dpy.settext(Integer.toString(val * 4));
			    }

			    public void fchanged() {
				try {
				    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.maxlights, val * 4));
				    dpy.settext(Integer.toString(val * 4));
				} catch(GSettings.SettingException e) {
				    error(e.getMessage());
				}
			    }
			}, prev.pos("bl").adds(0, 3).x(0));
		}

		prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.frame_sync")), prev.pos("bl").adds(0, 12).x(0));
		{
		    boolean[] done = {false};
		    RadioGroup grp = new RadioGroup(root) {
			    public void changed(int btn, String lbl) {
				if(!done[0])
				    return;
				try {
				    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.syncmode, JOGLPanel.SyncMode.values()[btn]));
				} catch(GSettings.SettingException e) {
				    error(e.getMessage());
				}
			    }
			};
		    prev = root.add(new Label(LocalizationManager.tr("opt.vanilla.sync.hint")), prev.pos("bl").adds(0, 4).x(0));
		    prev = grp.add(LocalizationManager.tr("opt.vanilla.sync.0"), prev.pos("bl").adds(5, 2));
		    prev = grp.add(LocalizationManager.tr("opt.vanilla.sync.1"), prev.pos("bl").adds(0, 2));
		    prev = grp.add(LocalizationManager.tr("opt.vanilla.sync.2"), prev.pos("bl").adds(0, 2));
		    prev = grp.add(LocalizationManager.tr("opt.vanilla.sync.3"), prev.pos("bl").adds(0, 2));
		    grp.check(prefs.syncmode.val.ordinal());
		    done[0] = true;
		}

		prev = root.add(new Button(UI.scale(220), LocalizationManager.tr("opt.vanilla.reset_renderer"), false).action(() -> {
		    ui.setgprefs(GSettings.defaults());
		    rebuild();
		}), prev.pos("bl").adds(0, 18).x(0));
		root.pack();
		back.move(fixedBackPos(scroll));
		pack();
	    } catch(Exception e) {
		clear(root);
		root.add(new Label(LocalizationManager.tr("opt.vanilla.video_err")), 0, 0);
		root.add(new Label(LocalizationManager.tr("opt.vanilla.video_err2")), 0, UI.scale(20));
		root.pack();
		back.move(fixedBackPos(scroll));
		pack();
		new Warning(e, "could not rebuild video settings panel").issue();
	    }
	}
    }

    public class AudioPanel extends Panel {
	public AudioPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.audio.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.audio.intro")), prev.pos("bl").adds(0, 4).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.vol.master")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 1000, (int)(Audio.volume * 1000)) {
			protected void added() {
			    dpy.settext(pct(val / 1000.0));
			}

			public void changed() {
			    Audio.setvolume(val / 1000.0);
			    dpy.settext(pct(val / 1000.0));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.vol.ui")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 1000, 0) {
			protected void attach(UI ui) {
			    super.attach(ui);
			    val = (int)(ui.audio.aui.volume * 1000);
			    dpy.settext(pct(val / 1000.0));
			}

			public void changed() {
			    ui.audio.aui.setvolume(val / 1000.0);
			    dpy.settext(pct(val / 1000.0));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.vol.event")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 1000, 0) {
			protected void attach(UI ui) {
			    super.attach(ui);
			    val = (int)(ui.audio.pos.volume * 1000);
			    dpy.settext(pct(val / 1000.0));
			}

			public void changed() {
			    ui.audio.pos.setvolume(val / 1000.0);
			    dpy.settext(pct(val / 1000.0));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.vol.ambient")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 1000, 0) {
			protected void attach(UI ui) {
			    super.attach(ui);
			    val = (int)(ui.audio.amb.volume * 1000);
			    dpy.settext(pct(val / 1000.0));
			}

			public void changed() {
			    ui.audio.amb.setvolume(val / 1000.0);
			    dpy.settext(pct(val / 1000.0));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.music")) {
		    { a = Music.enabled; }

		    public void set(boolean val) {
			Music.enable(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.audio_latency")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 128, Math.round(Audio.fmt.getSampleRate() / 4), Audio.bufsize()) {
			protected void added() {
			    dpy.settext(Math.round((val * 1000.0) / Audio.fmt.getSampleRate()) + " ms");
			}

			public void changed() {
			    Audio.bufsize(val, true);
			    dpy.settext(Math.round((val * 1000.0) / Audio.fmt.getSampleRate()) + " ms");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.audio_latency.hint")), prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.audio.alert.section")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.audio.alert.master")) {
		    { a = MoonConfig.playerAlertSounds; }
		    public void set(boolean val) {
			MoonConfig.setPlayerAlertSounds(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.audio.alert.cooldown")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(250)));
		prev = cont.add(new HSlider(UI.scale(220), 1, 60, MoonConfig.playerAlertCooldownSec) {
			protected void added() {
			    dpy.settext(val + " s");
			}

			public void changed() {
			    MoonConfig.setPlayerAlertCooldownSec(val);
			    dpy.settext(val + " s");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = addMoonWrappedNote(cont, prev, UI.scale(4), "opt.audio.alert.note");
	    prev = addPlayerAlertEditor(cont, prev, "opt.audio.alert.friendly", MoonPlayerAlerts.Kind.FRIENDLY,
		() -> MoonConfig.playerAlertFriendly, MoonConfig::setPlayerAlertFriendly,
		() -> MoonConfig.playerAlertFriendlyVolume, MoonConfig::setPlayerAlertFriendlyVolume,
		() -> MoonConfig.playerAlertFriendlyPath, MoonConfig::setPlayerAlertFriendlyPath);
	    prev = addPlayerAlertEditor(cont, prev, "opt.audio.alert.unknown", MoonPlayerAlerts.Kind.UNKNOWN,
		() -> MoonConfig.playerAlertUnknown, MoonConfig::setPlayerAlertUnknown,
		() -> MoonConfig.playerAlertUnknownVolume, MoonConfig::setPlayerAlertUnknownVolume,
		() -> MoonConfig.playerAlertUnknownPath, MoonConfig::setPlayerAlertUnknownPath);
	    prev = addPlayerAlertEditor(cont, prev, "opt.audio.alert.hostile", MoonPlayerAlerts.Kind.HOSTILE,
		() -> MoonConfig.playerAlertHostile, MoonConfig::setPlayerAlertHostile,
		() -> MoonConfig.playerAlertHostileVolume, MoonConfig::setPlayerAlertHostileVolume,
		() -> MoonConfig.playerAlertHostilePath, MoonConfig::setPlayerAlertHostilePath);
	    prev = addMoonWrappedNote(cont, prev, UI.scale(20), "opt.audio.alert.path.note");
	    cont.pack();
	    pack();
	}
    }

    public class InterfacePanel extends Panel {
	public InterfacePanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.display.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.display.intro")), prev.pos("bl").adds(0, 4).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.uiscale")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		final double gran = 0.05;
		final double smin = 1, smax = Math.floor(UI.maxscale() / gran) * gran;
		final int steps = (int)Math.round((smax - smin) / gran);
		prev = cont.add(new HSlider(UI.scale(220), 0, steps,
				       (int)Math.round(steps * (Utils.getprefd("uiscale", 1.0) - smin) / (smax - smin))) {
			protected void added() {
			    double cur = smin + (((double)val / steps) * (smax - smin));
			    dpy.settext(String.format(Locale.ROOT, "%.2fx", cur));
			}

			public void changed() {
			    double cur = smin + (((double)val / steps) * (smax - smin));
			    Utils.setprefd("uiscale", cur);
			    dpy.settext(String.format(Locale.ROOT, "%.2fx", cur));
			}
		    	}, prev.pos("bl").adds(0, 3).x(0));
	    }

	    String borderlessLabel = MoonL10n.LANG_RU.equals(MoonL10n.lang()) ? "Полноэкранное окно без рамок" : "Borderless fullscreen window";
	    String borderlessNote = MoonL10n.LANG_RU.equals(MoonL10n.lang()) ?
		"Заполняет монитор как fullscreen, но остается обычным окном и не захватывает курсор." :
		"Covers the monitor like fullscreen, but stays a normal window and does not trap the cursor.";
	    prev = cont.add(new CheckBox(borderlessLabel) {
		    { a = Utils.getprefb("wndborderless", false) || ((MainFrame.getInstance() != null) && MainFrame.getInstance().isBorderlessFullscreen()); }
		    public void set(boolean val) {
			Utils.setprefb("wndborderless", val);
			MainFrame mf = MainFrame.getInstance();
			if(mf != null)
			    java.awt.EventQueue.invokeLater(() -> mf.setborderless(val));
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(new Label(borderlessNote), prev.pos("bl").adds(UI.scale(16), 2).x(UI.scale(16)));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.theme_min")) {
		    { a = Window.minimalTheme; }
		    public void set(boolean val) {
			Window.minimalTheme = val;
			Utils.setprefb("moon-minimal-theme", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.simple_drag")) {
		    { a = DefSettings.simpledraging; }
		    public void set(boolean val) {
			DefSettings.simpledraging = val;
			Utils.setprefb("simpledraging", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.compact_meters")) {
		    { a = DefSettings.minimalisticmeter; }
		    public void set(boolean val) {
			DefSettings.minimalisticmeter = val;
			Utils.setprefb("minimalisticmeter", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.meter_text")) {
		    { a = DefSettings.showmetertext; }
		    public void set(boolean val) {
			DefSettings.showmetertext = val;
			Utils.setprefb("showmetertext", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.meter_trans")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = DefSettings.imetertransparency;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 40, 255, cur) {
			protected void added() {
			    dpy.settext(val + " / 255");
			}

			public void changed() {
			    DefSettings.imetertransparency = val;
			    Utils.setprefi("imetertransparency", val);
			    dpy.settext(val + " / 255");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.chat_bg_opacity")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = chatalpha();
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 60, 255, cur) {
			protected void added() {
			    dpy.settext(val + " / 255");
			}

			public void changed() {
			    applychatalpha(val);
			    dpy.settext(val + " / 255");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.minimap_startup")) {
		    { a = minimapvisible(); }
		    public void set(boolean val) {
			applyminimapvisible(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Label("--- " + moonText("Инвентарь", "Inventory") + " ---"), prev.pos("bl").adds(0, 16).x(0));
	    prev = cont.add(new CheckBox(moonText("Нумерация слотов инвентаря", "Inventory slot numbering")) {
		    { a = MoonConfig.inventorySlotNumbers; }
		    public void set(boolean val) {
			MoonConfig.setInventorySlotNumbers(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 8).x(0));
	    {
		int cur = MoonConfig.inventorySlotNumberSize;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(240)));
		prev = cont.add(new HSlider(UI.scale(220), 8, 24, cur) {
			protected void added() {
			    dpy.settext(moonText("Размер номера слота", "Slot number size") + ": " + cur);
			}
			public void changed() {
			    MoonConfig.setInventorySlotNumberSize(val);
			    dpy.settext(moonText("Размер номера слота", "Slot number size") + ": " + val);
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.inventorySlotNumberRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(240)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(moonText("Номер слота R", "Slot number R") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setInventorySlotNumberRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(moonText("Номер слота R", "Slot number R") + ": " + val);
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(240)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(moonText("Номер слота G", "Slot number G") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setInventorySlotNumberRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(moonText("Номер слота G", "Slot number G") + ": " + val);
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(240)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(moonText("Номер слота B", "Slot number B") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setInventorySlotNumberRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(moonText("Номер слота B", "Slot number B") + ": " + val);
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    cont.pack();
	    pack();
	}
    }

    public class CameraPanel extends Panel {
	public CameraPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.camera.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.camera.intro")), prev.pos("bl").adds(0, 4).x(0));

	    final String[] camids = {"ortho", "follow", "bad", "worse"};
	    final String[] labels = {LocalizationManager.tr("opt.vanilla.cam.ortho"), LocalizationManager.tr("opt.vanilla.cam.follow"), LocalizationManager.tr("opt.vanilla.cam.free"), LocalizationManager.tr("opt.vanilla.cam.legacy")};
	    boolean[] done = {false};
	    RadioGroup grp = new RadioGroup(cont) {
		    public void changed(int btn, String lbl) {
			if(!done[0])
			    return;
			applycamera(camids[btn]);
		    }
		};
	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.default_cam")), prev.pos("bl").adds(0, 12).x(0));
	    for(String label : labels)
		prev = grp.add(label, prev.pos("bl").adds(5, 3));
	    int cur = 0;
	    for(int i = 0; i < camids.length; i++) {
		if(camids[i].equals(currentcamera())) {
		    cur = i;
		    break;
		}
	    }
	    grp.check(cur);
	    done[0] = true;

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.place_gran")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int curv = (MapView.plobpgran == 0) ? 17 : (int)Math.round(MapView.plobpgran);
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 2, 17, curv) {
			protected void added() {
			    dpy.settext((val == 17) ? "\u221e" : Integer.toString(val));
			}

			public void changed() {
			    MapView.plobpgran = (val == 17) ? 0 : val;
			    Utils.setprefd("plobpgran", MapView.plobpgran);
			    dpy.settext((val == 17) ? "\u221e" : Integer.toString(val));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.place_angle")), prev.pos("bl").adds(0, 12).x(0));
	    {
		final int[] vals = {4, 5, 6, 8, 9, 10, 12, 15, 18, 20, 24, 30, 36, 40, 45, 60, 72, 90, 120, 180, 360};
		int curv = 0;
		for(int i = 0; i < vals.length; i++) {
		    if(Math.abs((MapView.plobagran * 2) - vals[i]) < Math.abs((MapView.plobagran * 2) - vals[curv]))
			curv = i;
		}
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, vals.length - 1, curv) {
			protected void added() {
			    dpy.settext(String.format(Locale.ROOT, "%d\u00b0", 360 / vals[val]));
			}

			public void changed() {
			    MapView.plobagran = vals[val] / 2.0;
			    Utils.setprefd("plobagran", MapView.plobagran);
			    dpy.settext(String.format(Locale.ROOT, "%d\u00b0", 360 / vals[val]));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.vanilla.reset_cam"), false).action(() -> {
		applycamera("ortho");
		MapView.plobpgran = 8;
		MapView.plobagran = 12;
		Utils.setprefd("plobpgran", MapView.plobpgran);
		Utils.setprefd("plobagran", MapView.plobagran);
	    }), prev.pos("bl").adds(0, 16).x(0));

	    cont.pack();
	    pack();
	}
    }

    private static final Text kbtt = RichText.render(LocalizationManager.tr("opt.kb.help"), 0);
    public class BindingPanel extends Panel {
	private static final int KB_SCROLL_W = UI.scale(390);
	private static final int KB_SCROLL_H = UI.scale(380);
	private static final int KB_BIND_BTN_W = UI.scale(166);
	private static final int KB_ROW_GAP = UI.scale(12);

	private int addbtn(Widget cont, String nm, KeyBinding cmd, int y) {
	    int labelw = Math.max(UI.scale(120), cont.sz.x - KB_BIND_BTN_W - KB_ROW_GAP);
	    Label lbl = new Label(nm, labelw);
	    SetButton btn = new SetButton(KB_BIND_BTN_W, cmd);
	    int rowh = Math.max(lbl.sz.y, btn.sz.y);
	    cont.add(lbl, Coord.of(0, y + ((rowh - lbl.sz.y) / 2)));
	    cont.add(btn, Coord.of(cont.sz.x - btn.sz.x, y + ((rowh - btn.sz.y) / 2)));
	    return(y + rowh + UI.scale(2));
	}

	public BindingPanel(Panel back) {
	    super();
	    Scrollport scroll = add(new Scrollport(Coord.of(KB_SCROLL_W, KB_SCROLL_H)), 0, 0);
	    Widget cont = scroll.cont;
	    Widget prev;
	    int y = 0;
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.main")), cont.sz.x / 2, y, 0.5, 0.0).pos("bl").adds(0, 5).y;
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.inv"), GameUI.kb_inv, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.equ"), GameUI.kb_equ, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.chr"), GameUI.kb_chr, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.map"), GameUI.kb_map, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.bud"), GameUI.kb_bud, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.opt"), GameUI.kb_opt, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.srch"), GameUI.kb_srch, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.chat"), GameUI.kb_chat, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.quick"), ChatUI.kb_quick, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.shoot"), GameUI.kb_shoot, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.ico"), GameUI.kb_ico, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.hide"), GameUI.kb_hide, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.logout"), GameUI.kb_logout, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.switchchr"), GameUI.kb_switchchr, y);
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.mapopts")), cont.sz.x / 2, y + UI.scale(10), 0.5, 0.0).pos("bl").adds(0, 5).y;
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.claim"), GameUI.kb_claim, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.vil"), GameUI.kb_vil, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.rlm"), GameUI.kb_rlm, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.grid"), MapView.kb_grid, y);
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.cam")), cont.sz.x / 2, y + UI.scale(10), 0.5, 0.0).pos("bl").adds(0, 5).y;
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.camleft"), MapView.kb_camleft, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.camright"), MapView.kb_camright, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.camin"), MapView.kb_camin, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.camout"), MapView.kb_camout, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.camreset"), MapView.kb_camreset, y);
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.mapwnd")), cont.sz.x / 2, y + UI.scale(10), 0.5, 0.0).pos("bl").adds(0, 5).y;
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.mhome"), MapWnd.kb_home, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.mmark"), MapWnd.kb_mark, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.mhmark"), MapWnd.kb_hmark, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.mcompact"), MapWnd.kb_compact, y);
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.speed")), cont.sz.x / 2, y + UI.scale(10), 0.5, 0.0).pos("bl").adds(0, 5).y;
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.speedup"), Speedget.kb_speedup, y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.speeddn"), Speedget.kb_speeddn, y);
	    for(int i = 0; i < 4; i++)
		y = addbtn(cont, String.format(LocalizationManager.tr("opt.kb.speedset"), i + 1), Speedget.kb_speeds[i], y);
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.combat")), cont.sz.x / 2, y + UI.scale(10), 0.5, 0.0).pos("bl").adds(0, 5).y;
	    for(int i = 0; i < Fightsess.kb_acts.length; i++)
		y = addbtn(cont, String.format(LocalizationManager.tr("opt.kb.fightact"), i + 1), Fightsess.kb_acts[i], y);
	    y = addbtn(cont, LocalizationManager.tr("opt.kb.relcycle"), Fightsess.kb_relcycle, y);
	    y = cont.adda(new Label(LocalizationManager.tr("opt.kb.section.mw")), cont.sz.x / 2, y + UI.scale(10), 0.5, 0.0).pos("bl").adds(0, 5).y;
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.esp"), MoonKeybinds.kb_toggleEsp, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.xray"), MoonKeybinds.kb_toggleXray, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.tree-hitbox"), MoonKeybinds.kb_toggleTreeHitbox, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.entity-hitbox"), MoonKeybinds.kb_cycleEntityHitboxViz, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.trace-hostile"), MoonKeybinds.kb_toggleTraceHostile, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.trace-neutral"), MoonKeybinds.kb_toggleTraceNeutral, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.trace-players"), MoonKeybinds.kb_toggleTracePlayers, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.autodrink"), MoonKeybinds.kb_toggleAutoDrink, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.combatbot"), MoonKeybinds.kb_toggleCombatBot, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.aggro"), MoonKeybinds.kb_toggleAggroRadius, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.speedboost"), MoonKeybinds.kb_toggleSpeedBoost, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.flatterrain"), MoonKeybinds.kb_toggleFlatTerrain, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.daylight"), MoonKeybinds.kb_toggleAlwaysDaylight, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.qual-objs"), MoonKeybinds.kb_toggleQualityObjects, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.crop-hide"), MoonKeybinds.kb_toggleCropHide, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.speed-wire-panel"), MoonKeybinds.kb_toggleSpeedWirePanel, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.tpnav"), GameUI.kb_mw_tpnav, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.tp-quick1"), MoonKeybinds.kb_tp_quick1, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.tp-quick2"), MoonKeybinds.kb_tp_quick2, y);
	    y = addbtn(cont, LocalizationManager.tr("keybind.mw.tp-quick3"), MoonKeybinds.kb_tp_quick3, y);
	    prev = adda(new PointBind(UI.scale(240)), scroll.pos("bl").adds(0, 10).x(scroll.sz.x / 2), 0.5, 0.0);
	    add(new PButton(UI.scale(200), LocalizationManager.tr("opt.back"), 27, back), scroll.pos("bl").adds(optpadx, UI.scale(44)));
	    pack();
	}

	public class SetButton extends KeyMatch.Capture {
	    public final KeyBinding cmd;

	    public SetButton(int w, KeyBinding cmd) {
		super(w, cmd.key());
		this.cmd = cmd;
	    }

	    public void set(KeyMatch key) {
		super.set(key);
		cmd.set(key);
	    }

	    public void draw(GOut g) {
		if(cmd.key() != key)
		    super.set(cmd.key());
		super.draw(g);
	    }

	    protected KeyMatch mkmatch(KeyEvent ev) {
		return(KeyMatch.forevent(ev, ~cmd.modign));
	    }

	    protected boolean handle(KeyEvent ev) {
		if(ev.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
		    cmd.set(null);
		    super.set(cmd.key());
		    return(true);
		}
		return(super.handle(ev));
	    }

	    public Object tooltip(Coord c, Widget prev) {
		return(kbtt.tex());
	    }
	}
    }


    public static class PointBind extends Button implements CursorQuery.Handler {
	public static final String msg = "Bind other elements...";
	public static final Resource curs = Resource.local().loadwait("gfx/hud/curs/wrench");
	private UI.Grab mg, kg;
	private KeyBinding cmd;

	public PointBind(int w) {
	    super(w, msg, false);
	    tooltip = RichText.render("Bind a key to an element not listed above, such as an action-menu " +
				      "button. Click the element to bind, and then press the key to bind to it. " +
				      "Right-click to stop rebinding.",
				      300);
	}

	public void click() {
	    if(mg == null) {
		change("Click element...");
		mg = ui.grabmouse(this);
	    } else if(kg != null) {
		kg.remove();
		kg = null;
		change(msg);
	    }
	}

	private boolean handle(KeyEvent ev) {
	    switch(ev.getKeyCode()) {
	    case KeyEvent.VK_SHIFT: case KeyEvent.VK_CONTROL: case KeyEvent.VK_ALT:
	    case KeyEvent.VK_META: case KeyEvent.VK_WINDOWS:
		return(false);
	    }
	    int code = ev.getKeyCode();
	    if(code == KeyEvent.VK_ESCAPE) {
		return(true);
	    }
	    if(code == KeyEvent.VK_BACK_SPACE) {
		cmd.set(null);
		return(true);
	    }
	    if(code == KeyEvent.VK_DELETE) {
		cmd.set(KeyMatch.nil);
		return(true);
	    }
	    KeyMatch key = KeyMatch.forevent(ev, ~cmd.modign);
	    if(key != null)
		cmd.set(key);
	    return(true);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(!ev.grabbed)
		return(super.mousedown(ev));
	    Coord gc = ui.mc;
	    if(ev.b == 1) {
		this.cmd = KeyBinding.Bindable.getbinding(ui.root, gc);
		return(true);
	    }
	    if(ev.b == 3) {
		mg.remove();
		mg = null;
		change(msg);
		return(true);
	    }
	    return(false);
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(mg == null)
		return(super.mouseup(ev));
	    Coord gc = ui.mc;
	    if(ev.b == 1) {
		if((this.cmd != null) && (KeyBinding.Bindable.getbinding(ui.root, gc) == this.cmd)) {
		    mg.remove();
		    mg = null;
		    kg = ui.grabkeys(this);
		    change("Press key...");
		} else {
		    this.cmd = null;
		}
		return(true);
	    }
	    if(ev.b == 3)
		return(true);
	    return(false);
	}

	public boolean getcurs(CursorQuery ev) {
	    return(ev.grabbed ? ev.set(curs) : false);
	}

    public boolean keydown(KeyDownEvent ev) {
	    if(!ev.grabbed)
		return(super.keydown(ev));
	    if(handle(ev.awt)) {
		kg.remove();
		kg = null;
		cmd = null;
		change(LocalizationManager.tr("opt.kb.click_other"));
		mg = ui.grabmouse(this);
	    }
	    return(true);
	}
    }

    public class MoonChatPanel extends Panel {
	public MoonChatPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.moonchat.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonchat.intro")), prev.pos("bl").adds(0, 4).x(0));

	    CheckBox vis = new CheckBox(LocalizationManager.tr("opt.moonchat.startup")) {
		    {
			a = Utils.getprefb("chatvis", true);
		    }

		    public void set(boolean val) {
			Utils.setprefb("chatvis", val);
			a = val;
		    }
		};
	    prev = cont.add(vis, prev.pos("bl").adds(0, 12).x(0));

	    Coord cs = chatsz();
	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonchat.width")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), UI.scale(420), UI.scale(980), cs.x) {
			protected void added() {
			    dpy.settext(cs.x + " px");
			}

			public void changed() {
			    Coord cur = chatsz();
			    applychatsz(Coord.of(val, cur.y));
			    dpy.settext(val + " px");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonchat.height")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), UI.scale(150), UI.scale(420), cs.y) {
			protected void added() {
			    dpy.settext(cs.y + " px");
			}

			public void changed() {
			    Coord cur = chatsz();
			    applychatsz(Coord.of(cur.x, val));
			    dpy.settext(val + " px");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonchat.opacity")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = chatalpha();
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 60, 255, cur) {
			protected void added() {
			    dpy.settext(val + " / 255");
			}

			public void changed() {
			    applychatalpha(val);
			    dpy.settext(val + " / 255");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    {
		CheckBox ms = new CheckBox(LocalizationManager.tr("opt.chat.suppress.minesupport")) {
		    {
			a = Utils.getprefb("moon-suppress-minesupport-msg", false);
		    }

		    public void set(boolean val) {
			Utils.setprefb("moon-suppress-minesupport-msg", val);
			a = val;
		    }
		};
		ms.settip(LocalizationManager.tr("opt.chat.suppress.minesupport.hint"), true);
		prev = cont.add(ms, prev.pos("bl").adds(0, 10).x(0));
	    }

	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.moonchat.reset_size"), false).action(() -> {
			applychatsz(UI.scale(new Coord(620, 220)));
	    }), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.moonchat.reset_pos"), false).action(() -> {
			resetchatpos();
	    }), prev.pos("bl").adds(0, 6).x(0));
	    cont.pack();
	    pack();
	}
    }

    public class MoonAutomationPanel extends Panel {
	public MoonAutomationPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget 	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonauto.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonauto.intro")), prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("speed.see.functions")), prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.autodrink"), "Auto Drink/Eat", LocalizationManager.tr("opt.mod.autodrink.tip")), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.taskq"), "Task Queue", LocalizationManager.tr("opt.mod.taskq.tip")), prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.smarthk"), "Smart Hotkeys", LocalizationManager.tr("opt.mod.smarthk.tip")), prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(dynlabel(() -> String.format(LocalizationManager.tr("opt.automation.queue_fmt"), TaskQueue.size(),
		    (TaskQueue.peek() != null) ? (LocalizationManager.tr("opt.automation.next") + TaskQueue.peek().name()) : "")), prev.pos("bl").adds(0, 10).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonauto.drink")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = (int)Math.round(ModuleConfig.getOptionDouble("Auto Drink/Eat", "drinkThreshold", 0.30) * 100.0);
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 5, 95, cur) {
			protected void added() {
			    dpy.settext(pct(cur / 100.0));
			}

			public void changed() {
			    double v = val / 100.0;
			    ModuleConfig.setOption("Auto Drink/Eat", "drinkThreshold", v);
			    dpy.settext(pct(v));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonauto.eat")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = (int)Math.round(ModuleConfig.getOptionDouble("Auto Drink/Eat", "eatThreshold", 0.30) * 100.0);
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 5, 95, cur) {
			protected void added() {
			    dpy.settext(pct(cur / 100.0));
			}

			public void changed() {
			    double v = val / 100.0;
			    ModuleConfig.setOption("Auto Drink/Eat", "eatThreshold", v);
			    dpy.settext(pct(v));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.moonauto.clearq"), false).action(TaskQueue::clear), prev.pos("bl").adds(0, 14).x(0));
	    cont.pack();
	    pack();
	}
    }

    public class MoonOverlayPanel extends Panel {
	public MoonOverlayPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.moonoverlay.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonoverlay.intro")), prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.uiclean"), "UI Cleaner", LocalizationManager.tr("opt.mod.uiclean.tip")), prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.pathhl"), "Path Highlighter", LocalizationManager.tr("opt.mod.pathhl.tip")), prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.radar"), "Resource Radar", LocalizationManager.tr("opt.mod.radar.tip")), prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.moonoverlay.hideclutter")) {
		    {
			a = UIEnhancer.isHideClutter();
		    }

		    public void set(boolean val) {
			UIEnhancer.setHideClutter(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.moonoverlay.compact")) {
		    {
			a = UIEnhancer.isCompactBars();
		    }

		    public void set(boolean val) {
			UIEnhancer.setCompactBars(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.moonoverlay.radar_dist")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = (int)Math.round(ResourceScanner.getMaxDistance());
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 10, 200, cur) {
			protected void added() {
			    dpy.settext(String.format(LocalizationManager.tr("opt.vanilla.tiles_fmt"), cur));
			}

			public void changed() {
			    ResourceScanner.setMaxDistance(val);
			    dpy.settext(String.format(LocalizationManager.tr("opt.vanilla.tiles_fmt"), val));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.moonoverlay.reset_hud"), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui != null)
			    gui.resetui();
	    }), prev.pos("bl").adds(0, 14).x(0));

	    final Runnable invalidateTerrain = () -> {
		GameUI gui = getparent(GameUI.class);
		if(gui != null && gui.map != null && gui.map.glob != null && gui.map.glob.map != null)
		    gui.map.glob.map.invalidateAll();
	    };
	    final Runnable refreshGobStatus = () -> MoonGobStatus.refresh(getparent(GameUI.class));
	    final Runnable refreshScales = () -> {
		GameUI gui = getparent(GameUI.class);
		MoonConfig.refreshGobScaleRendering(gui);
	    };

	    prev = cont.add(new Label("--- " + (LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? "Визуал мира" : "World Visuals") + " ---"),
		prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("flat.terrain")) {
		    { a = MoonConfig.flatTerrain; }
		    public void set(boolean val) {
			MoonConfig.setFlatTerrain(val); a = val;
			invalidateTerrain.run();
		    }
		}, prev.pos("bl").adds(0, 8).x(0));
	    {
		prev = cont.add(new Label(LocalizationManager.tr("flat.terrain.relief")), prev.pos("bl").adds(0, 6).x(0));
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
		prev = cont.add(new HSlider(UI.scale(250), 0, 100, (int)Math.round(MoonConfig.flatTerrainRelief * 100)) {
			protected void added() {
			    dpy.settext(String.format("%d%%", val));
			}
			public void changed() {
			    MoonConfig.setFlatTerrainRelief(val / 100.0);
			    dpy.settext(String.format("%d%%", val));
			    invalidateTerrain.run();
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
		prev = addMoonWrappedNote(cont, prev, UI.scale(20), "flat.terrain.relief.note");
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("daylight.always")) {
		    { a = MoonConfig.alwaysDaylight; }
		    public void set(boolean val) { MoonConfig.setAlwaysDaylight(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = addMoonWrappedNote(cont, prev, UI.scale(20), "daylight.note");
	    {
		int nv = (int)Math.round(MoonConfig.nightVisionBlend * 1000);
		Label nvLab = cont.add(new Label(""), prev.pos("bl").adds(0, 10).x(UI.scale(260)));
		prev = cont.add(new Label(moonText("Ночное зрение / ярче мир", "Night vision / brighter world")), prev.pos("bl").adds(0, 8).x(0));
		final HSlider[] nvSlider = new HSlider[1];
		prev = cont.add(nvSlider[0] = new HSlider(UI.scale(220), 0, 650, nv) {
			protected void added() {
			    nvLab.settext(moonText("Смешивание: ", "Blend: ") + String.format(Locale.ROOT, "%.0f%%", val / 6.5));
			}
			public void changed() {
			    MoonConfig.setNightVisionBlend(val / 1000.0);
			    nvLab.settext(moonText("Смешивание: ", "Blend: ") + String.format(Locale.ROOT, "%.0f%%", val / 6.5));
			    GameUI gui = getparent(GameUI.class);
			    if(gui != null && gui.map != null && gui.map.glob != null)
				gui.map.glob.brighten();
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
		prev = cont.add(new Button(UI.scale(90), moonText("Сброс", "Reset"), false).action(() -> {
		    MoonConfig.setNightVisionBlend(0);
		    if(nvSlider[0] != null) {
			nvSlider[0].val = 0;
			nvSlider[0].changed();
		    }
		}), prev.pos("bl").adds(0, 6).x(0));
		prev = addMoonWrappedNote(cont, prev, UI.scale(20), "moon.nightvision.note");
	    }
	    {
		Label palLab = cont.add(new Label(""), prev.pos("bl").adds(0, 10).x(UI.scale(260)));
		prev = cont.add(new Label(moonText("Высота палисадов / кирпичных стен", "Palisade / brick wall height")), prev.pos("bl").adds(0, 8).x(0));
		HSlider palSl = new HSlider(UI.scale(220), 40, 200, MoonConfig.palisadeWallScalePct) {
			protected void added() {
			    palLab.settext(val + "%");
			}
			public void changed() {
			    MoonConfig.setPalisadeWallScalePct(val);
			    palLab.settext(val + "%");
			    refreshScales.run();
			}
		    };
		prev = cont.add(palSl, prev.pos("bl").adds(0, 3).x(0));
		prev = cont.add(new Button(UI.scale(90), moonText("Сброс", "Reset"), false).action(() -> {
		    palSl.val = 100;
		    MoonConfig.setPalisadeWallScalePct(100);
		    palLab.settext("100%");
		    refreshScales.run();
		}), prev.pos("bl").adds(0, 6).x(0));
		Label treeLab = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(260)));
		prev = cont.add(new Label(moonText("Масштаб деревьев и кустов", "Tree & bush scale")), prev.pos("bl").adds(0, 8).x(0));
		HSlider treeSl = new HSlider(UI.scale(220), 30, 200, MoonConfig.treeBushScalePct) {
			protected void added() {
			    treeLab.settext(val + "%");
			}
			public void changed() {
			    MoonConfig.setTreeBushScalePct(val);
			    treeLab.settext(val + "%");
			    refreshScales.run();
			}
		    };
		prev = cont.add(treeSl, prev.pos("bl").adds(0, 3).x(0));
		prev = cont.add(new Button(UI.scale(90), moonText("Сброс", "Reset"), false).action(() -> {
		    treeSl.val = 100;
		    MoonConfig.setTreeBushScalePct(100);
		    treeLab.settext("100%");
		    refreshScales.run();
		}), prev.pos("bl").adds(0, 6).x(0));
	    }
	    prev = cont.add(new Label("--- " + moonText("Посевы", "Crops") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(moonText("Скрывать посевы", "Hide crops")) {
		    { a = MoonConfig.cropHide; }
		    public void set(boolean val) {
			MoonConfig.setCropHide(val);
			a = val;
			GameUI gui = getparent(GameUI.class);
			if(gui != null && gui.map != null)
			    MoonCropMode.refresh(gui.map);
		    }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new CheckBox(moonText("Показывать этап роста", "Show growth stage")) {
		    { a = MoonConfig.cropShowStage; }
		    public void set(boolean val) {
			MoonConfig.setCropShowStage(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    {
		Label cropLab = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(260)));
		prev = cont.add(new Label(moonText("Размер посевов", "Crop size")), prev.pos("bl").adds(0, 8).x(0));
		HSlider cropSl = new HSlider(UI.scale(220), 30, 200, MoonConfig.cropScalePct) {
			protected void added() {
			    cropLab.settext(val + "%");
			}
			public void changed() {
			    MoonConfig.setCropScalePct(val);
			    cropLab.settext(val + "%");
			    refreshScales.run();
			}
		    };
		prev = cont.add(cropSl, prev.pos("bl").adds(0, 3).x(0));
		prev = cont.add(new Button(UI.scale(90), moonText("Сброс", "Reset"), false).action(() -> {
		    cropSl.val = 100;
		    MoonConfig.setCropScalePct(100);
		    cropLab.settext("100%");
		    refreshScales.run();
		}), prev.pos("bl").adds(0, 6).x(0));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.caveflat")) {
		    { a = MoonConfig.flatCaves; }
		    public void set(boolean val) {
			MoonConfig.setFlatCaves(val);
			a = val;
			invalidateTerrain.run();
		    }
		}, prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.cavestraight")) {
		    { a = MoonConfig.straightCaveWalls; }
		    public void set(boolean val) {
			MoonConfig.setStraightCaveWalls(val);
			a = val;
			invalidateTerrain.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.flatwalls")) {
		    { a = MoonConfig.flatWalls; }
		    public void set(boolean val) {
			MoonConfig.setFlatWalls(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.flatcupboards")) {
		    { a = MoonConfig.flatCupboards; }
		    public void set(boolean val) {
			MoonConfig.setFlatCupboards(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = addMoonWrappedNote(cont, prev, UI.scale(20), "opt.flatcupboards.note");
	    prev = cont.add(new Label("--- " + moonText("Текст и подписи", "Text & labels") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    {
		int cur = MoonConfig.overlayTextSize;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 8, 32, cur) {
			protected void added() {
			    dpy.settext(moonText("Размер текста мира", "World text size") + ": " + cur);
			}
			public void changed() {
			    MoonConfig.setOverlayTextSize(val);
			    dpy.settext(moonText("Размер текста мира", "World text size") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.overlayTextRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(moonText("Текст мира R", "World text R") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setOverlayTextRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(moonText("Текст мира R", "World text R") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(moonText("Текст мира G", "World text G") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setOverlayTextRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(moonText("Текст мира G", "World text G") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(moonText("Текст мира B", "World text B") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setOverlayTextRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(moonText("Текст мира B", "World text B") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new Label("--- " + moonText("Шахта и подпорки", "Mining overlay") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.safedig")) {
		    { a = MoonConfig.mineSupportSafeTiles; }
		    public void set(boolean val) {
			MoonConfig.setMineSupportSafeTiles(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.mine.risk")) {
		    { a = MoonConfig.mineSweeperRiskTiles; }
		    public void set(boolean val) {
			MoonConfig.setMineSweeperRiskTiles(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(moonText("Показывать прочность подпорок", "Show mine support durability")) {
		    { a = MoonConfig.mineSupportShowHp; }
		    public void set(boolean val) {
			MoonConfig.setMineSupportShowHp(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(moonText("Показывать текст и метки сапёра", "Show mine sweeper labels")) {
		    { a = MoonConfig.mineSweeperShowLabels; }
		    public void set(boolean val) {
			MoonConfig.setMineSweeperShowLabels(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    {
		int cur = MoonConfig.mineSupportRadiusTiles;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 3, 32, cur) {
			protected void added() {
			    dpy.settext(LocalizationManager.tr("opt.safedig.r") + ": " + cur);
			}
			public void changed() {
			    MoonConfig.setMineSupportRadiusTiles(val);
			    dpy.settext(LocalizationManager.tr("opt.safedig.r") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int fa = MoonConfig.mineSupportFillAlpha;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 160, fa) {
			protected void added() {
			    dpy.settext(LocalizationManager.tr("opt.safedig.fill") + ": " + fa);
			}
			public void changed() {
			    MoonConfig.setMineSupportFillAlpha(val);
			    dpy.settext(LocalizationManager.tr("opt.safedig.fill") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int ea = MoonConfig.mineSupportOutlineAlpha;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, ea) {
			protected void added() {
			    dpy.settext(LocalizationManager.tr("opt.safedig.edgea") + ": " + ea);
			}
			public void changed() {
			    MoonConfig.setMineSupportOutlineAlpha(val);
			    dpy.settext(LocalizationManager.tr("opt.safedig.edgea") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int ew = MoonConfig.mineSupportOutlineWidth;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 1, 8, ew) {
			protected void added() {
			    dpy.settext(LocalizationManager.tr("opt.safedig.edgew") + ": " + ew);
			}
			public void changed() {
			    MoonConfig.setMineSupportOutlineWidth(val);
			    dpy.settext(LocalizationManager.tr("opt.safedig.edgew") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.mineSupportOutlineRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(LocalizationManager.tr("opt.safedig.or") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setMineSupportOutlineRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(LocalizationManager.tr("opt.safedig.or") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(LocalizationManager.tr("opt.safedig.og") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setMineSupportOutlineRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(LocalizationManager.tr("opt.safedig.og") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(LocalizationManager.tr("opt.safedig.ob") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setMineSupportOutlineRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(LocalizationManager.tr("opt.safedig.ob") + ": " + val);
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new Label("--- " + moonText("Текст шахты", "Mining text") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    {
		int cur = MoonConfig.mineSupportTextSize;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 8, 28, cur) {
			protected void added() {
			    dpy.settext(moonText("Размер текста подпорок", "Support text size") + ": " + cur);
			}
			public void changed() {
			    MoonConfig.setMineSupportTextSize(val);
			    dpy.settext(moonText("Размер текста подпорок", "Support text size") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int cur = MoonConfig.mineSweeperTextSize;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 8, 28, cur) {
			protected void added() {
			    dpy.settext(moonText("Размер текста сапёра", "Sapper text size") + ": " + cur);
			}
			public void changed() {
			    MoonConfig.setMineSweeperTextSize(val);
			    dpy.settext(moonText("Размер текста сапёра", "Sapper text size") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.mineSupportTextRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(moonText("Текст подпорок R", "Support text R") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setMineSupportTextRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(moonText("Текст подпорок R", "Support text R") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(moonText("Текст подпорок G", "Support text G") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setMineSupportTextRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(moonText("Текст подпорок G", "Support text G") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(moonText("Текст подпорок B", "Support text B") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setMineSupportTextRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(moonText("Текст подпорок B", "Support text B") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.mineSweeperSafeRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(moonText("Безопасно R", "Safe mark R") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setMineSweeperSafeRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(moonText("Безопасно R", "Safe mark R") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(moonText("Безопасно G", "Safe mark G") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setMineSweeperSafeRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(moonText("Безопасно G", "Safe mark G") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(moonText("Безопасно B", "Safe mark B") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setMineSweeperSafeRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(moonText("Безопасно B", "Safe mark B") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.mineSweeperAutoSafeRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(moonText("Можно копать R", "Dig-ok mark R") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setMineSweeperAutoSafeRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(moonText("Можно копать R", "Dig-ok mark R") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(moonText("Можно копать G", "Dig-ok mark G") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setMineSweeperAutoSafeRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(moonText("Можно копать G", "Dig-ok mark G") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(moonText("Можно копать B", "Dig-ok mark B") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setMineSweeperAutoSafeRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(moonText("Можно копать B", "Dig-ok mark B") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    {
		int rgb = MoonConfig.mineSweeperRiskRgb;
		int r0 = (rgb >> 16) & 0xff, g0 = (rgb >> 8) & 0xff, b0 = rgb & 0xff;
		final int[] chan = {r0, g0, b0};
		Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, r0) {
			protected void added() {
			    dr.settext(moonText("Обвал R", "Risk mark R") + ": " + r0);
			}
			public void changed() {
			    chan[0] = val;
			    MoonConfig.setMineSweeperRiskRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dr.settext(moonText("Обвал R", "Risk mark R") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, g0) {
			protected void added() {
			    dg.settext(moonText("Обвал G", "Risk mark G") + ": " + g0);
			}
			public void changed() {
			    chan[1] = val;
			    MoonConfig.setMineSweeperRiskRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    dg.settext(moonText("Обвал G", "Risk mark G") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
		Label db = cont.add(new Label(""), prev.pos("bl").adds(0, 6).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 255, b0) {
			protected void added() {
			    db.settext(moonText("Обвал B", "Risk mark B") + ": " + b0);
			}
			public void changed() {
			    chan[2] = val;
			    MoonConfig.setMineSweeperRiskRgb((chan[0] << 16) | (chan[1] << 8) | chan[2]);
			    db.settext(moonText("Обвал B", "Risk mark B") + ": " + val);
			}
		}, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label("--- " + LocalizationManager.tr("opt.status.section") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.dframe")) {
		    { a = MoonConfig.showDframeStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowDframeStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.rack")) {
		    { a = MoonConfig.showRackStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowRackStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.storage")) {
		    { a = MoonConfig.showCupboardStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowCupboardStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.storage.partial")) {
		    { a = MoonConfig.showPartialStorageStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowPartialStorageStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.shed")) {
		    { a = MoonConfig.showShedStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowShedStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.coop")) {
		    { a = MoonConfig.showCoopStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowCoopStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.hutch")) {
		    { a = MoonConfig.showHutchStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowHutchStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.trough")) {
		    { a = MoonConfig.showTroughStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowTroughStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.beehive")) {
		    { a = MoonConfig.showBeehiveStatus; }
		    public void set(boolean val) {
			MoonConfig.setShowBeehiveStatus(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.status.pots")) {
		    { a = MoonConfig.highlightPots; }
		    public void set(boolean val) {
			MoonConfig.setHighlightPots(val);
			a = val;
			refreshGobStatus.run();
		    }
		}, prev.pos("bl").adds(0, 4).x(0));

	    cont.pack();
	    pack();
	}
    }

    public class MoonCombatPanel extends Panel {
	public MoonCombatPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.mooncombat.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.mooncombat.intro")), prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new Label("--- " + LocalizationManager.tr("opt.mooncombat.mapguides") + " ---"), prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.mooncombat.chasevec")) {
		    { a = MoonConfig.combatDrawChaseVectors; }
		    public void set(boolean val) {
			MoonConfig.setCombatDrawChaseVectors(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = addMoonWrappedNote(cont, prev, UI.scale(20), "opt.mooncombat.mapguides.note");
	    prev = cont.add(new Label("--- " + LocalizationManager.tr("opt.combatai.section") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new Button(UI.scale(240), LocalizationManager.tr("opt.combatai.open"), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui == null)
			    return;
			gui.openMoonCombatBotWindow();
			OptWnd.this.hide();
	    }), prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.combatai.where")), prev.pos("bl").adds(0, 4).x(UI.scale(8)));
	    prev = cont.add(modulebox(LocalizationManager.tr("opt.mod.combatov"), "Combat Overlay", LocalizationManager.tr("opt.mod.combatov.tip")), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(dynlabel(() -> String.format(LocalizationManager.tr("opt.mooncombat.suggest_fmt"),
		    CombatAnalyzer.getSuggestedAction().isEmpty() ? LocalizationManager.tr("opt.mooncombat.idle") : CombatAnalyzer.getSuggestedAction())), prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(dynlabel(() -> String.format(Locale.ROOT, LocalizationManager.tr("opt.mooncombat.stamina_fmt"), CombatManager.getLastStamina() * 100.0, CombatManager.getLastEnergy() * 100.0)), prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.mooncombat.cooldown")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = (int)CombatManager.getAttackCooldownMs();
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 300, 2500, cur) {
			protected void added() {
			    dpy.settext(cur + " ms");
			}

			public void changed() {
			    CombatManager.setAttackCooldownMs(val);
			    dpy.settext(val + " ms");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.combathud")) {
		    { a = MoonConfig.combatDamageHud; }
		    public void set(boolean val) {
			MoonConfig.setCombatDamageHud(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.combathud.dealt")) {
		    { a = MoonConfig.combatHudShowDealt; }
		    public void set(boolean val) {
			MoonConfig.setCombatHudShowDealt(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.combathud.taken")) {
		    { a = MoonConfig.combatHudShowTaken; }
		    public void set(boolean val) {
			MoonConfig.setCombatHudShowTaken(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));
	    {
		int hm = MoonConfig.combatHudEnemyHpMode;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 2, hm) {
		    private String hpLabel(int v) {
			String key = v == 0 ? "opt.combathud.hp.0" : (v == 1 ? "opt.combathud.hp.1" : "opt.combathud.hp.2");
			return LocalizationManager.tr("opt.combathud.hp") + ": " + LocalizationManager.tr(key);
		    }
		    protected void added() {
			dpy.settext(hpLabel(val));
		    }
		    public void changed() {
			MoonConfig.setCombatHudEnemyHpMode(val);
			dpy.settext(hpLabel(val));
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.combathud.swap")) {
		    { a = MoonConfig.combatHudSwapFightIp; }
		    public void set(boolean val) {
			MoonConfig.setCombatHudSwapFightIp(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 8).x(0));
	    {
		int cur = MoonConfig.combatHudPersistScope;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 8).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 1, cur) {
		    private String scopeLabel(int v) {
			String key = (v == 0) ? "opt.combathud.persist.scope.0" : "opt.combathud.persist.scope.1";
			return LocalizationManager.tr("opt.combathud.persist.scope") + ": " + LocalizationManager.tr(key);
		    }
		    protected void added() {
			dpy.settext(scopeLabel(val));
		    }
		    public void changed() {
			MoonConfig.setCombatHudPersistScope(val);
			dpy.settext(scopeLabel(val));
		    }
		}, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.dmg.resetbtn"), false).action(() -> {
			MoonFightHud.resetTakenDamage();
			GameUI gui = getparent(GameUI.class);
			if(gui != null)
			    gui.error(LocalizationManager.tr("msg.dmg.reset"));
	    }), prev.pos("bl").adds(0, 8).x(0));

	    cont.pack();
	    pack();
	}
    }

    public class MoonPerformancePanel extends Panel {
	public MoonPerformancePanel(Panel back) {
	    final GSettings gp = GSettings.defaults();
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.perf.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.intro")), prev.pos("bl").adds(0, 4).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.section.cpu")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.cpu_profile")), prev.pos("bl").adds(0, 10).x(0));
	    Label cpuDpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
	    Label cpuSummary = cont.add(new Label(""), cpuDpy.pos("bl").adds(0, 4).x(UI.scale(230)));
	    final boolean[] cpuDone = {false};
	    Runnable refreshCpu = () -> {
		cpuDpy.settext(runtimeCpuProfileText(MoonConfig.runtimeCpuProfile));
		cpuSummary.settext(runtimeTuneSummaryText());
	    };
	    RadioGroup cpuGrp = new RadioGroup(cont) {
		    public void changed(int btn, String lbl) {
			if(!cpuDone[0])
			    return;
			MoonConfig.RuntimeCpuProfile[] values = MoonConfig.RuntimeCpuProfile.values();
			if((btn >= 0) && (btn < values.length)) {
			    MoonConfig.setRuntimeCpuProfile(values[btn]);
			    refreshCpu.run();
			}
		    }
		};
	    MoonConfig.RuntimeCpuProfile[] cpuProfiles = MoonConfig.RuntimeCpuProfile.values();
	    for(MoonConfig.RuntimeCpuProfile profile : cpuProfiles)
		prev = cpuGrp.add(runtimeCpuProfileText(profile), prev.pos("bl").adds(5, 3));
	    cpuGrp.check(MoonConfig.runtimeCpuProfile.ordinal());
	    cpuDone[0] = true;
	    refreshCpu.run();
	    prev = addMoonWrappedNote(cont, prev, UI.scale(4), "opt.perf.cpu.note");

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.cpu.active")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 2, 16, MoonConfig.runtimeTune().activeProcessors) {
			protected void added() {
			    dpy.settext(Integer.toString(val));
			}

			public void changed() {
			    MoonConfig.setRuntimeActiveProcessors(val);
			    cpuGrp.check(MoonConfig.runtimeCpuProfile.ordinal());
			    dpy.settext(Integer.toString(MoonConfig.runtimeTune().activeProcessors));
			    refreshCpu.run();
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.cpu.loader")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 1, 8, MoonConfig.runtimeTune().loaderThreads) {
			protected void added() {
			    dpy.settext(Integer.toString(val));
			}

			public void changed() {
			    MoonConfig.setRuntimeLoaderThreads(val);
			    cpuGrp.check(MoonConfig.runtimeCpuProfile.ordinal());
			    dpy.settext(Integer.toString(MoonConfig.runtimeTune().loaderThreads));
			    refreshCpu.run();
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.cpu.defer")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 1, 8, MoonConfig.runtimeTune().deferThreads) {
			protected void added() {
			    dpy.settext(Integer.toString(val));
			}

			public void changed() {
			    MoonConfig.setRuntimeDeferThreads(val);
			    cpuGrp.check(MoonConfig.runtimeCpuProfile.ordinal());
			    dpy.settext(Integer.toString(MoonConfig.runtimeTune().deferThreads));
			    refreshCpu.run();
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.cpu.heapmax")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 4, 16, MoonConfig.runtimeTune().heapMaxGb) {
			protected void added() {
			    dpy.settext(String.format(Locale.ROOT, "%d GB", val));
			}

			public void changed() {
			    MoonConfig.setRuntimeHeapMaxGb(val);
			    cpuGrp.check(MoonConfig.runtimeCpuProfile.ordinal());
			    dpy.settext(String.format(Locale.ROOT, "%d GB", MoonConfig.runtimeTune().heapMaxGb));
			    refreshCpu.run();
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.section.profile")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.profile")), prev.pos("bl").adds(0, 10).x(0));
	    Label presetDpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
	    Runnable refreshPreset = () -> presetDpy.settext(perfPresetText(MoonConfig.perfPreset));
	    refreshPreset.run();
	    Widget presetRow = cont.add(new Widget(Coord.of(UI.scale(252), UI.scale(24))), prev.pos("bl").adds(0, 7).x(0));
	    presetRow.add(new Button(UI.scale(78), LocalizationManager.tr("opt.perf.preset.balanced"), false).action(() -> {
		MoonConfig.setPerfPreset(MoonConfig.PerfPreset.BALANCED);
		refreshPreset.run();
	    }), Coord.z);
	    presetRow.add(new Button(UI.scale(78), LocalizationManager.tr("opt.perf.preset.maxfps"), false).action(() -> {
		MoonConfig.setPerfPreset(MoonConfig.PerfPreset.MAX_FPS);
		refreshPreset.run();
	    }), Coord.of(UI.scale(86), 0));
	    presetRow.add(new Button(UI.scale(78), LocalizationManager.tr("opt.perf.preset.visual"), false).action(() -> {
		MoonConfig.setPerfPreset(MoonConfig.PerfPreset.VISUAL_FIRST);
		refreshPreset.run();
	    }), Coord.of(UI.scale(172), 0));
	    prev = presetRow;
	    prev = addMoonWrappedNote(cont, prev, UI.scale(4), "opt.perf.profile.note");
	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.perf.apply_current"), false).action(() -> {
		if(ui == null)
		    return;
		MoonConfig.PerfPreset preset = MoonConfig.perfPreset;
		MoonConfig.applyMoonPerfPreset(preset);
		try {
		    ui.setgprefs(MoonConfig.applyPerfPreset(ui.gprefs, preset));
		} catch(GSettings.SettingException e) {
		    error(e.getMessage());
		}
		refreshPreset.run();
	    }), prev.pos("bl").adds(0, 6).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.shadows")) {
		    {
			a = gp.lshadow.val;
		    }

		    public void set(boolean val) {
			if(ui == null)
			    return;
			try {
			    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.lshadow, val));
			    a = val;
			} catch(GSettings.SettingException e) {
			    error(e.getMessage());
			}
		    }

		    public void tick(double dt) {
			super.tick(dt);
			if(ui != null)
			    a = ui.gprefs.lshadow.val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.vsync")) {
		    {
			a = gp.vsync.val;
		    }

		    public void set(boolean val) {
			if(ui == null)
			    return;
			try {
			    ui.setgprefs(ui.gprefs.update(null, ui.gprefs.vsync, val));
			    a = val;
			} catch(GSettings.SettingException e) {
			    error(e.getMessage());
			}
		    }

		    public void tick(double dt) {
			super.tick(dt);
			if(ui != null)
			    a = ui.gprefs.vsync.val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.render_scale")), prev.pos("bl").adds(0, 12).x(0));
	    {
		final int steps = 8;
		int cur = (int)Math.round(steps * Math.log(gp.rscale.val) / Math.log(2.0f));
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), -2 * steps, 1 * steps, cur) {
			protected void attach(UI ui) {
			    super.attach(ui);
			    this.val = (int)Math.round(steps * Math.log(ui.gprefs.rscale.val) / Math.log(2.0f));
			    dpy.settext(String.format(Locale.ROOT, "%.2fx", Math.pow(2, this.val / (double)steps)));
			}

			protected void added() {
			    dpy.settext(String.format(Locale.ROOT, "%.2fx", Math.pow(2, val / (double)steps)));
			}

			public void changed() {
			    if(ui == null)
				return;
			    try {
				float v = (float)Math.pow(2, this.val / (double)steps);
				ui.setgprefs(ui.gprefs.update(null, ui.gprefs.rscale, v));
				dpy.settext(String.format(Locale.ROOT, "%.2fx", v));
			    } catch(GSettings.SettingException e) {
				error(e.getMessage());
			    }
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.fps_active")), prev.pos("bl").adds(0, 12).x(0));
	    {
		final int max = 250;
		int cur = (gp.hz.val == Float.POSITIVE_INFINITY) ? max : gp.hz.val.intValue();
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 30, max, cur) {
			protected void attach(UI ui) {
			    super.attach(ui);
			    this.val = (ui.gprefs.hz.val == Float.POSITIVE_INFINITY) ? max : ui.gprefs.hz.val.intValue();
			    dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			}

			protected void added() {
			    dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			}

			public void changed() {
			    if(ui == null)
				return;
			    try {
				float v = (val == max) ? Float.POSITIVE_INFINITY : val;
				ui.setgprefs(ui.gprefs.update(null, ui.gprefs.hz, v));
				dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			    } catch(GSettings.SettingException e) {
				error(e.getMessage());
			    }
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.fps_bg")), prev.pos("bl").adds(0, 12).x(0));
	    {
		final int max = 250;
		int cur = (gp.bghz.val == Float.POSITIVE_INFINITY) ? max : gp.bghz.val.intValue();
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 15, max, cur) {
			protected void attach(UI ui) {
			    super.attach(ui);
			    this.val = (ui.gprefs.bghz.val == Float.POSITIVE_INFINITY) ? max : ui.gprefs.bghz.val.intValue();
			    dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			}

			protected void added() {
			    dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			}

			public void changed() {
			    if(ui == null)
				return;
			    try {
				float v = (val == max) ? Float.POSITIVE_INFINITY : val;
				ui.setgprefs(ui.gprefs.update(null, ui.gprefs.bghz, v));
				dpy.settext((val == max) ? LocalizationManager.tr("opt.vanilla.fps_unlimited") : String.format(Locale.ROOT, LocalizationManager.tr("opt.vanilla.fps_fmt"), val));
			    } catch(GSettings.SettingException e) {
				error(e.getMessage());
			    }
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.section.mapload")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.mapload")), prev.pos("bl").adds(0, 10).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(250)));
		prev = cont.add(new HSlider(UI.scale(220), 0, 12, MoonConfig.mapLoadExtraCuts) {
			protected void added() {
			    dpy.settext(mapLoadCutsText(val));
			}

			public void changed() {
			    MoonConfig.setMapLoadExtraCuts(val);
			    dpy.settext(mapLoadCutsText(val));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = addMoonWrappedNote(cont, prev, UI.scale(4), "opt.perf.mapload.note");
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.progressive_load")) {
		    { a = MoonConfig.progressiveWorldLoad; }
		    public void set(boolean val) {
			MoonConfig.setProgressiveWorldLoad(val);
			a = val;
		    }
		    public void tick(double dt) {
			super.tick(dt);
			a = MoonConfig.progressiveWorldLoad;
		    }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.autoshed")) {
		    { a = MoonConfig.perfAutoShed; }
		    public void set(boolean val) {
			MoonConfig.setPerfAutoShed(val);
			a = val;
		    }
		    public void tick(double dt) {
			super.tick(dt);
			a = MoonConfig.perfAutoShed;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.section.streaming")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.budget.critical")), prev.pos("bl").adds(0, 10).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(250)));
		prev = cont.add(new HSlider(UI.scale(220), 1, 24, MoonConfig.perfTerrainCriticalBudget) {
			protected void added() {
			    dpy.settext(perfBudgetText("opt.perf.budget.critical.val", val));
			}
			public void changed() {
			    MoonConfig.setPerfTerrainCriticalBudget(val);
			    dpy.settext(perfBudgetText("opt.perf.budget.critical.val", val));
			}
			public void tick(double dt) {
			    super.tick(dt);
			    if(val != MoonConfig.perfTerrainCriticalBudget) {
				val = MoonConfig.perfTerrainCriticalBudget;
				dpy.settext(perfBudgetText("opt.perf.budget.critical.val", val));
			    }
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.budget.secondary")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(250)));
		prev = cont.add(new HSlider(UI.scale(220), 1, 24, MoonConfig.perfTerrainSecondaryBudget) {
			protected void added() {
			    dpy.settext(perfBudgetText("opt.perf.budget.secondary.val", val));
			}
			public void changed() {
			    MoonConfig.setPerfTerrainSecondaryBudget(val);
			    dpy.settext(perfBudgetText("opt.perf.budget.secondary.val", val));
			}
			public void tick(double dt) {
			    super.tick(dt);
			    if(val != MoonConfig.perfTerrainSecondaryBudget) {
				val = MoonConfig.perfTerrainSecondaryBudget;
				dpy.settext(perfBudgetText("opt.perf.budget.secondary.val", val));
			    }
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.budget.gobs")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(250)));
		prev = cont.add(new HSlider(UI.scale(220), 4, 96, MoonConfig.perfGobBuildBudget) {
			protected void added() {
			    dpy.settext(perfBudgetText("opt.perf.budget.gobs.val", val));
			}
			public void changed() {
			    MoonConfig.setPerfGobBuildBudget(val);
			    dpy.settext(perfBudgetText("opt.perf.budget.gobs.val", val));
			}
			public void tick(double dt) {
			    super.tick(dt);
			    if(val != MoonConfig.perfGobBuildBudget) {
				val = MoonConfig.perfGobBuildBudget;
				dpy.settext(perfBudgetText("opt.perf.budget.gobs.val", val));
			    }
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.section.gfx")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.espdraw")) {
		    { a = MoonConfig.gfxModEspOverlay; }
		    public void set(boolean val) { MoonConfig.setGfxModEspOverlay(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.gfxModEspOverlay; }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.xraymod")) {
		    { a = MoonConfig.gfxModXray; }
		    public void set(boolean val) { MoonConfig.setGfxModXray(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.gfxModXray; }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.hitboxmod")) {
		    { a = MoonConfig.gfxModHitboxes; }
		    public void set(boolean val) { MoonConfig.setGfxModHitboxes(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.gfxModHitboxes; }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("hitbox.keybind.hint")), prev.pos("bl").adds(20, 2).x(UI.scale(20)));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.miningov")) {
		    { a = MoonConfig.gfxModMiningOverlay; }
		    public void set(boolean val) { MoonConfig.setGfxModMiningOverlay(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.gfxModMiningOverlay; }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.fighthud")) {
		    { a = MoonConfig.gfxModFightHud; }
		    public void set(boolean val) { MoonConfig.setGfxModFightHud(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.gfxModFightHud; }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.activityhud")) {
		    { a = MoonConfig.gfxModActivityHud; }
		    public void set(boolean val) { MoonConfig.setGfxModActivityHud(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.gfxModActivityHud; }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.section.hud")), prev.pos("bl").adds(0, 18).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.fpsping")) {
		    { a = MoonConfig.showFpsPingHud; }
		    public void set(boolean val) { MoonConfig.setShowFpsPingHud(val); a = val; }
		    public void tick(double dt) { super.tick(dt); a = MoonConfig.showFpsPingHud; }
		}, prev.pos("bl").adds(0, 12).x(0));
	    prev = addMoonWrappedNote(cont, prev, UI.scale(4), "opt.perf.fpsping.note");
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.compact_hud")) {
		    { a = MoonConfig.perfHudCompact; }
		    public void set(boolean val) {
			MoonConfig.setPerfHudCompact(val);
			a = val;
		    }
		    public void tick(double dt) {
			super.tick(dt);
			a = MoonConfig.perfHudCompact;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.hideclutter")) {
		    { a = UIEnhancer.isHideClutter(); }
		    public void set(boolean val) {
			UIEnhancer.setHideClutter(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.compactbars")) {
		    { a = UIEnhancer.isCompactBars(); }
		    public void set(boolean val) {
			UIEnhancer.setCompactBars(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.simpledrag")) {
		    { a = DefSettings.simpledraging; }
		    public void set(boolean val) {
			DefSettings.simpledraging = val;
			Utils.setprefb("simpledraging", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.minmeters")) {
		    { a = DefSettings.minimalisticmeter; }
		    public void set(boolean val) {
			DefSettings.minimalisticmeter = val;
			Utils.setprefb("minimalisticmeter", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.perf.fepmeter")) {
		    { a = Utils.getprefb("fepmeter", true); }
		    public void set(boolean val) {
			Utils.setprefb("fepmeter", val);
			a = val;
			GameUI g = getparent(GameUI.class);
			if(g != null && g.fepmeter != null)
			    g.fepmeter.show(val);
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.meter_text")) {
		    { a = DefSettings.showmetertext; }
		    public void set(boolean val) {
			DefSettings.showmetertext = val;
			Utils.setprefb("showmetertext", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.vanilla.meter_trans")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = DefSettings.imetertransparency;
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 40, 255, cur) {
			protected void added() {
			    dpy.settext(val + " / 255");
			}

			public void changed() {
			    DefSettings.imetertransparency = val;
			    Utils.setprefi("imetertransparency", val);
			    dpy.settext(val + " / 255");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.perf.radar_dist")), prev.pos("bl").adds(0, 12).x(0));
	    {
		int cur = (int)Math.round(ResourceScanner.getMaxDistance());
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		prev = cont.add(new HSlider(UI.scale(220), 10, 200, cur) {
			protected void added() {
			    dpy.settext(String.format(LocalizationManager.tr("opt.vanilla.tiles_fmt"), val));
			}

			public void changed() {
			    ResourceScanner.setMaxDistance(val);
			    dpy.settext(String.format(LocalizationManager.tr("opt.vanilla.tiles_fmt"), val));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    cont.pack();
	    pack();
	}
    }

    @Override
    protected Deco makedeco() {
	return new MoonOptDeco();
    }

    private class MoonOptDeco extends Deco {
	private UI.Grab dm = null;
	private Coord doff;
	private static final int hdr = 24;
	private final Text.Line captex = Text.std.render(LocalizationManager.tr("opt.deco.title"), MoonPanel.MOON_ACCENT);

	public void iresize(Coord isz) { resize(isz.add(0, UI.scale(hdr))); }
	public Area contarea() {
	    return Area.sized(Coord.of(0, UI.scale(hdr)), sz.sub(0, UI.scale(hdr)));
	}

	public void draw(GOut g) {
	    g.chcolor(new Color(MoonPanel.MOON_BG.getRed(), MoonPanel.MOON_BG.getGreen(), MoonPanel.MOON_BG.getBlue(), 255));
	    g.frect(Coord.z, sz);
	    int h = UI.scale(hdr);
	    int halfH = h / 2;
	    g.chcolor(new Color(MoonPanel.MOON_HEADER.getRed(), MoonPanel.MOON_HEADER.getGreen(), MoonPanel.MOON_HEADER.getBlue(), 255));
	    g.frect(Coord.z, Coord.of(sz.x, halfH));
	    g.chcolor(new Color(MoonPanel.MOON_HEADER2.getRed(), MoonPanel.MOON_HEADER2.getGreen(), MoonPanel.MOON_HEADER2.getBlue(), 255));
	    g.frect(Coord.of(0, halfH), Coord.of(sz.x, h - halfH));
	    g.chcolor();
	    g.image(captex.tex(), Coord.of(UI.scale(8), UI.scale(4)));
	    Coord cbtnsz = UI.scale(14, 14);
	    Coord cbtnp = Coord.of(sz.x - cbtnsz.x - UI.scale(6), UI.scale(5));
	    g.chcolor(MoonPanel.MOON_ACCENT);
	    g.atext("X", cbtnp.add(cbtnsz.div(2)), 0.5, 0.5);
	    g.chcolor();
	    super.draw(g);
	}

	public boolean checkhit(Coord c) {
	    return c.isect(Coord.z, sz);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    Coord cbtnsz = UI.scale(14, 14);
	    Coord cbtnp = Coord.of(sz.x - cbtnsz.x - UI.scale(6), UI.scale(5));
	    if(ev.b == 1 && ev.c.isect(cbtnp, cbtnsz)) {
		OptWnd.this.wdgmsg("close");
		return true;
	    }
	    if(ev.b == 1 && ev.c.y < UI.scale(hdr)) {
		dm = ui.grabmouse(this);
		doff = ev.c;
		return true;
	    }
	    return super.mousedown(ev);
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(dm != null) {
		dm.remove();
		dm = null;
		Utils.setprefc("wndc-opts", OptWnd.this.c);
		return true;
	    }
	    return super.mouseup(ev);
	}

	public void mousemove(MouseMoveEvent ev) {
	    if(dm != null) {
		OptWnd.this.move(OptWnd.this.c.add(ev.c.sub(doff)));
	    } else {
		super.mousemove(ev);
	    }
	}
    }

    public class MoonIntegrationsPanel extends Panel {
	public MoonIntegrationsPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.integ.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.intro")), prev.pos("bl").adds(0, 4).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.dpi.all.hdr")), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.dpi.all.master")) {
		    { a = Utils.getprefb(HavenDpiBypass.PREF_MASTER, false); }
		    public void set(boolean val) {
			Utils.setprefb(HavenDpiBypass.PREF_MASTER, val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.dpi.all.budget")), prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new TextEntry(UI.scale(120), String.valueOf(Utils.getprefi(HavenDpiBypass.PREF_TCP_SPLIT_BUDGET, 8192))) {
		    @Override
		    public void activate(String text) {
			try {
			    Utils.setprefi(HavenDpiBypass.PREF_TCP_SPLIT_BUDGET, Math.max(0, Integer.parseInt(text.trim())));
			} catch(NumberFormatException e) {
			    Utils.setprefi(HavenDpiBypass.PREF_TCP_SPLIT_BUDGET, 8192);
			}
			rsettext(String.valueOf(Utils.getprefi(HavenDpiBypass.PREF_TCP_SPLIT_BUDGET, 8192)));
			super.activate(text);
		    }
		}, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.dpi.hdr")), prev.pos("bl").adds(0, 14).x(0));
	    prev = addMoonWrappedNote(cont, prev, 0, "opt.integ.dpi.note");
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.dpi.obf")) {
		    { a = Utils.getprefb("haven-auth-dpi-obf-first", false); }
		    public void set(boolean val) {
			Utils.setprefb("haven-auth-dpi-obf-first", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.dpi.split")) {
		    { a = Utils.getprefb("haven-auth-dpi-split", false); }
		    public void set(boolean val) {
			Utils.setprefb("haven-auth-dpi-split", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 6).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.netproxy.hdr")), prev.pos("bl").adds(0, 14).x(0));
	    prev = addMoonWrappedNote(cont, prev, 0, "opt.integ.netproxy.note");
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.netproxy.game")), prev.pos("bl").adds(0, 8).x(0));
	    TextEntry gameChainTe = new TextEntry(UI.scale(480), HavenNetProxy.gameChainPrefForUi());
	    prev = cont.add(gameChainTe, prev.pos("bl").adds(0, 3).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.netproxy.auth")), prev.pos("bl").adds(0, 8).x(0));
	    TextEntry authChainTe = new TextEntry(UI.scale(480), HavenNetProxy.authChainPrefForUi());
	    prev = cont.add(authChainTe, prev.pos("bl").adds(0, 3).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.netproxy.strict")) {
		    { a = Utils.getprefb(HavenNetProxy.PREF_PROXY_STRICT, true); }
		    public void set(boolean val) {
			Utils.setprefb(HavenNetProxy.PREF_PROXY_STRICT, val);
			a = val;
			HavenNetProxy.clearCache();
		    }
		}, prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.integ.netproxy.apply"), false).action(() -> {
		    Utils.setpref(HavenNetProxy.PREF_GAME_CHAIN, gameChainTe.text().trim());
		    Utils.setpref(HavenNetProxy.PREF_AUTH_CHAIN, authChainTe.text().trim());
		    HavenNetProxy.clearCache();
	    }), prev.pos("bl").adds(0, 8).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.confirm")) {
		    { a = Config.confirmclose; }
		    public void set(boolean val) {
			Utils.setprefb("confirmclose", Config.confirmclose = val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.discord")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.url")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry discordUrl = new TextEntry(UI.scale(280), Utils.getpref("moon-discord-url", ""));
	    prev = cont.add(discordUrl, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.token")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry discordToken = new TextEntry(UI.scale(280), Utils.getpref("moon-discord-token", ""));
	    prev = cont.add(discordToken, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.discord_status")) {
		    { a = Utils.getprefb("moon-discord-status", false); }
		    public void set(boolean val) {
			Utils.setprefb("moon-discord-status", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Button(UI.scale(140), LocalizationManager.tr("opt.integ.save"), false).action(() -> {
			Utils.setpref("moon-discord-url", discordUrl.text());
			Utils.setpref("moon-discord-token", discordToken.text());
	    }), prev.pos("bl").adds(0, 10).x(0));

	    prev = cont.add(new Button(UI.scale(140), LocalizationManager.tr("opt.integ.connect"), false).action(() -> {
	    }), prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_bridge")), prev.pos("bl").adds(0, 18).x(0));
	    prev = addMoonWrappedNote(cont, prev, 0, "opt.integ.bot_bridge_hint");

	    final CheckBox botEnabled = cont.add(new CheckBox(LocalizationManager.tr("opt.integ.bot_enable")) {
		    { a = Utils.getprefb("moon-discord-enabled", false); }
		    public void set(boolean val) {
			a = val;
		    }
		}, prev.pos("bl").adds(0, 10).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_secret")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry botSecret = new TextEntry(UI.scale(340), Utils.getpref("moon-discord-secret", ""));
	    prev = cont.add(botSecret, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_api_url")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry botApiUrl = new TextEntry(UI.scale(340), Utils.getpref("moon-discord-bot-url", "http://127.0.0.1:3000"));
	    prev = cont.add(botApiUrl, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_listen_port")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry botPort = new TextEntry(UI.scale(120), String.valueOf(Utils.getprefi("moon-discord-port", 3001)));
	    prev = cont.add(botPort, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_listen_host")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry botListenHost = new TextEntry(UI.scale(200), Utils.getpref("moon-discord-listen-host", "127.0.0.1"));
	    prev = cont.add(botListenHost, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_notify_url")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry botNotifyUrl = new TextEntry(UI.scale(340), Utils.getpref("moon-discord-notify-url", ""));
	    prev = cont.add(botNotifyUrl, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.bot_hb_sec")), prev.pos("bl").adds(0, 10).x(0));
	    TextEntry botHbSec = new TextEntry(UI.scale(80), String.valueOf(Utils.getprefi("moon-discord-heartbeat-sec", 20)));
	    prev = cont.add(botHbSec, prev.pos("bl").adds(0, 3).x(0));

	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.integ.bot_apply"), false).action(() -> {
		    Utils.setprefb("moon-discord-enabled", botEnabled.a);
		    Utils.setpref("moon-discord-secret", botSecret.text());
		    Utils.setpref("moon-discord-bot-url", botApiUrl.text().trim());
		    try {
			Utils.setprefi("moon-discord-port", Integer.parseInt(botPort.text().trim()));
		    } catch(NumberFormatException e) {
			Utils.setprefi("moon-discord-port", 3001);
		    }
		    Utils.setpref("moon-discord-listen-host", botListenHost.text().trim());
		    Utils.setpref("moon-discord-notify-url", botNotifyUrl.text().trim());
		    try {
			int hb = Integer.parseInt(botHbSec.text().trim());
			Utils.setprefi("moon-discord-heartbeat-sec", Math.max(5, hb));
		    } catch(NumberFormatException e) {
			Utils.setprefi("moon-discord-heartbeat-sec", 20);
		    }
		    GameUI gui = getparent(GameUI.class);
		    MoonDiscordService.reload(gui);
	    }), prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.status")), prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.webdash")), prev.pos("bl").adds(0, 18).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.webdash1")), prev.pos("bl").adds(0, 4).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.integ.webdash2")), prev.pos("bl").adds(0, 4).x(0));

	    cont.pack();
	    pack();
	}
    }

    public class MoonLayoutPanel extends Panel {
	public MoonLayoutPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(220), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("opt.layout.title")), 0, 0);
	    prev = cont.add(new Label(LocalizationManager.tr("opt.layout.intro")), prev.pos("bl").adds(0, 4).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.layout.theme_all")) {
		    { a = Window.minimalTheme; }
		    public void set(boolean val) {
			Window.minimalTheme = val;
			Utils.setprefb("moon-minimal-theme", val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 12).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.layout.chrome_style")),
		prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.layout.chrome_style.note"), UI.scale(260)),
		prev.pos("bl").adds(0, 3).x(0));
	    {
		final Button styleBtn = cont.add(new Button(UI.scale(220), "", false),
		    prev.pos("bl").adds(0, 8).x(0));
		Runnable refreshStyle = () -> styleBtn.change(LocalizationManager.tr(
		    (MoonUiTheme.chromeStyle() == MoonUiTheme.CHROME_STYLE_COMPACT)
			? "opt.layout.chrome_style.compact"
			: "opt.layout.chrome_style.orbital"));
		refreshStyle.run();
		styleBtn.action(() -> {
		    int next = (MoonUiTheme.chromeStyle() == MoonUiTheme.CHROME_STYLE_COMPACT)
			? MoonUiTheme.CHROME_STYLE_ORBITAL
			: MoonUiTheme.CHROME_STYLE_COMPACT;
		    MoonUiTheme.setChromeStyle(next);
		    refreshStyle.run();
		});
		prev = styleBtn;
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.vanilla.minimap_startup")) {
		    { a = minimapvisible(); }
		    public void set(boolean val) {
			applyminimapvisible(val);
			a = val;
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.layout.menu_cols")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		Coord gsz = MenuGrid.gsz;
		prev = cont.add(new HSlider(UI.scale(220), 2, 8, gsz.x) {
			protected void added() { dpy.settext(String.valueOf(val)); }
			public void changed() {
			    dpy.settext(String.valueOf(val));
			    GameUI gui = getparent(GameUI.class);
			    if(gui != null && gui.menu != null)
				gui.menu.setGridSize(new Coord(val, gui.menu.gridSize().y));
			    if(gui != null && gui.menugridwnd != null)
				gui.menugridwnd.resize(gui.menugridwnd.wrapSize(
				    MenuGrid.bgsz.mul(gui.menu.gridSize()).add(1,1)));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }
	    prev = cont.add(new Label(LocalizationManager.tr("opt.layout.menu_rows")), prev.pos("bl").adds(0, 12).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(230)));
		Coord gsz = MenuGrid.gsz;
		prev = cont.add(new HSlider(UI.scale(220), 2, 8, gsz.y) {
			protected void added() { dpy.settext(String.valueOf(val)); }
			public void changed() {
			    dpy.settext(String.valueOf(val));
			    GameUI gui = getparent(GameUI.class);
			    if(gui != null && gui.menu != null)
				gui.menu.setGridSize(new Coord(gui.menu.gridSize().x, val));
			    if(gui != null && gui.menugridwnd != null)
				gui.menugridwnd.resize(gui.menugridwnd.wrapSize(
				    MenuGrid.bgsz.mul(gui.menu.gridSize()).add(1,1)));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("opt.layout.belt_panels")), prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.layout.belt_f")) {
		    { a = Utils.getprefb("moon-belt-f-vis", false); }
		    public void set(boolean val) {
			Utils.setprefb("moon-belt-f-vis", val);
			a = val;
			GameUI gui = getparent(GameUI.class);
			if(gui != null && gui.fkeyBelt != null)
			    gui.fkeyBelt.show(val);
		    }
		}, prev.pos("bl").adds(0, 5).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("opt.layout.belt_num")) {
		    { a = Utils.getprefb("moon-belt-num-vis", false); }
		    public void set(boolean val) {
			Utils.setprefb("moon-belt-num-vis", val);
			a = val;
			GameUI gui = getparent(GameUI.class);
			if(gui != null && gui.numBelt != null)
			    gui.numBelt.show(val);
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Button(UI.scale(220), LocalizationManager.tr("opt.layout.reset_panels"), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui != null)
			    gui.resetui();
		}), prev.pos("bl").adds(0, 14).x(0));
	    cont.pack();
	    pack();
	}
    }

    public class MoonSpeedPanel extends Panel {
	public MoonSpeedPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(240), back);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("moonwide.speed")), 0, 0);
	    prev = addMoonWrappedNote(cont, prev, 0, "moonwide.speed.note");

	    prev = addMoonSpeedSection(cont, prev, OptWnd.this);
	    prev = addAutoDrinkSection(cont, prev);

	    cont.pack();
	    pack();
	}
    }

    public class MoonExperimentalPanel extends Panel {
	private final Scrollport scroll;
	public MoonExperimentalPanel(Panel back) {
	    scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(240), back);
	    rebuild(back);
	}

	private void rebuild(Panel back) {
	    Widget cont = scroll.cont;
	    clear(cont);
	    Widget prev = cont.add(new Label(LocalizationManager.tr("moonwide.experimental")), 0, 0);
	    prev = addMoonWrappedNote(cont, prev, 0, "moonwide.experimental.note");
	    prev = addMoonWrappedText(cont, prev, 0,
		LocalizationManager.LANG_RU.equals(MoonL10n.lang())
		    ? "Раздел закрыт паролем. Здесь лежат спорные и экспериментальные настройки движения, бега, стамины и wire-ускорения."
		    : "This section is password-protected. It contains experimental movement, running, stamina and wire-speed settings.");

	    if(!experimentalUnlocked) {
		prev = cont.add(new Label(LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? "Пароль" : "Password"),
		    prev.pos("bl").adds(0, 12).x(0));
		TextEntry pass = cont.add(new TextEntry(UI.scale(260), ""), prev.pos("bl").adds(0, 4).x(0));
		pass.pw = true;
		prev = pass;
		prev = cont.add(new Button(UI.scale(220), LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? "Открыть Experimental" : "Unlock Experimental", false).action(() -> {
		    if(unlockExperimental(pass.text())) {
			rebuild(back);
		    } else {
			error(LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? "Неверный пароль для Experimental." : "Wrong Experimental password.");
		    }
		}), prev.pos("bl").adds(0, 10).x(0));
		cont.pack();
		pack();
		return;
	    }

	    prev = addMoonExperimentalJniSection(cont, prev);
	    prev = addMoonExperimentalSpeedSection(cont, prev, OptWnd.this);

	    cont.pack();
	    pack();
	}
    }

    private static int moonInvQualArgbFromChan(int[] chan) {
	return(((chan[3] & 0xff) << 24) | ((chan[0] & 0xff) << 16) | ((chan[1] & 0xff) << 8) | (chan[2] & 0xff));
    }

    private Widget addMoonInvQualColorRows(Widget cont, Widget prev, final int tier, final Runnable reload) {
	int argb = MoonConfig.invQualityColorArgb[tier];
	int a0 = (argb >>> 24) & 0xff, r0 = (argb >> 16) & 0xff, g0 = (argb >> 8) & 0xff, b0 = argb & 0xff;
	final int[] chan = {r0, g0, b0, a0};
	final int lx = UI.scale(16);
	final int gapAfterRow = UI.scale(10);
	final int gapLabelToSlider = UI.scale(4);

	Label dr = cont.add(new Label(""), prev.pos("bl").adds(0, gapAfterRow).x(lx));
	prev = dr;
	prev = cont.add(new HSlider(UI.scale(200), 0, 255, r0) {
		protected void added() {
		    dr.settext(LocalizationManager.tr("moon.inv.qual.r") + ": " + r0);
		}
		public void changed() {
		    chan[0] = val;
		    MoonConfig.setInvQualityColorArgb(tier, moonInvQualArgbFromChan(chan));
		    dr.settext(LocalizationManager.tr("moon.inv.qual.r") + ": " + val);
		    reload.run();
		}
	    }, prev.pos("bl").adds(0, gapLabelToSlider).x(lx));
	Label dg = cont.add(new Label(""), prev.pos("bl").adds(0, gapAfterRow).x(lx));
	prev = dg;
	prev = cont.add(new HSlider(UI.scale(200), 0, 255, g0) {
		protected void added() {
		    dg.settext(LocalizationManager.tr("moon.inv.qual.g") + ": " + g0);
		}
		public void changed() {
		    chan[1] = val;
		    MoonConfig.setInvQualityColorArgb(tier, moonInvQualArgbFromChan(chan));
		    dg.settext(LocalizationManager.tr("moon.inv.qual.g") + ": " + val);
		    reload.run();
		}
	    }, prev.pos("bl").adds(0, gapLabelToSlider).x(lx));
	Label db = cont.add(new Label(""), prev.pos("bl").adds(0, gapAfterRow).x(lx));
	prev = db;
	prev = cont.add(new HSlider(UI.scale(200), 0, 255, b0) {
		protected void added() {
		    db.settext(LocalizationManager.tr("moon.inv.qual.b") + ": " + b0);
		}
		public void changed() {
		    chan[2] = val;
		    MoonConfig.setInvQualityColorArgb(tier, moonInvQualArgbFromChan(chan));
		    db.settext(LocalizationManager.tr("moon.inv.qual.b") + ": " + val);
		    reload.run();
		}
	    }, prev.pos("bl").adds(0, gapLabelToSlider).x(lx));
	Label da = cont.add(new Label(""), prev.pos("bl").adds(0, gapAfterRow).x(lx));
	prev = da;
	prev = cont.add(new HSlider(UI.scale(200), 0, 255, a0) {
		protected void added() {
		    da.settext(LocalizationManager.tr("moon.inv.qual.a") + ": " + a0);
		}
		public void changed() {
		    chan[3] = val;
		    MoonConfig.setInvQualityColorArgb(tier, moonInvQualArgbFromChan(chan));
		    da.settext(LocalizationManager.tr("moon.inv.qual.a") + ": " + val);
		    reload.run();
		}
	    }, prev.pos("bl").adds(0, gapLabelToSlider).x(lx));
	prev = cont.add(new Button(UI.scale(100), LocalizationManager.tr("moon.inv.qual.reset"), false).action(() -> {
		MoonConfig.resetInvQualityTierDefaults(tier);
		MoonInvQualityPanel p = cont.getparent(MoonInvQualityPanel.class);
		if(p != null)
		    p.rebuildInvQualPanelContent();
		reload.run();
	    }), prev.pos("bl").adds(0, gapAfterRow).x(lx));
	return(prev);
    }

    public class MoonInvQualityPanel extends Panel {
	private final Panel pback;

	public MoonInvQualityPanel(Panel back) {
	    this.pback = back;
	    rebuildInvQualPanelContent();
	}

	private void rebuildInvQualPanelContent() {
	    for(Widget w : new ArrayList<>(children()))
		w.reqdestroy();

	    final Runnable reload = () -> {
		GameUI gui = getparent(GameUI.class);
		if(gui != null)
		    gui.reloadAllItemQualityOverlays();
	    };

	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(240), pback);
	    Widget cont = scroll.cont;
	    Widget prev = cont.add(new Label(LocalizationManager.tr("moon.inv.qual.title")), 0, 0);
	    prev = addMoonWrappedNote(cont, prev, 0, "moon.inv.qual.note");

	    prev = cont.add(new CheckBox(LocalizationManager.tr("quality.objects")) {
		    { a = MoonConfig.qualityObjects; }
		    public void set(boolean val) {
			MoonConfig.setQualityObjects(val);
			a = val;
			reload.run();
			GameUI gui = getparent(GameUI.class);
			if(gui != null)
			    gui.reloadAllItemQualityOverlays();
		    }
		}, prev.pos("bl").adds(0, 8).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("moon.inv.qual.rounded")) {
		    { a = MoonConfig.invQualityRounded; }
		    public void set(boolean val) {
			MoonConfig.setInvQualityRounded(val);
			a = val;
			reload.run();
		    }
		}, prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("moon.inv.qual.customcolors")) {
		    { a = MoonConfig.invQualityCustomColors; }
		    public void set(boolean val) {
			MoonConfig.setInvQualityCustomColors(val);
			a = val;
			reload.run();
		    }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("moon.inv.qual.corner")), prev.pos("bl").adds(0, 12).x(0));
	    prev = cont.add(new HSlider(UI.scale(220), 0, 4, MoonConfig.invQualityCorner) {
		    @Override
		    public void fchanged() {
			MoonConfig.setInvQualityCorner(val);
			reload.run();
		    }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("moon.inv.qual.fontsize")), prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new HSlider(UI.scale(220), 6, 32, MoonConfig.invQualityFontPx) {
		    @Override
		    public void fchanged() {
			MoonConfig.setInvQualityFontPx(val);
			reload.run();
		    }
		}, prev.pos("bl").adds(0, 6).x(0));

	    for(int tier = 0; tier < MoonConfig.INV_QUAL_TIER_COUNT; tier++) {
		final int t = tier;
		prev = cont.add(new Label(LocalizationManager.tr("moon.inv.qual.tier." + tier)), prev.pos("bl").adds(0, 12).x(0));
		prev = cont.add(new TextEntry(UI.scale(72), Integer.toString(MoonConfig.invQualityThreshold[tier])) {
			@Override
			public void changed(ReadLine buf) {
			    super.changed(buf);
			    try {
				int v = Integer.parseInt(buf.line().trim().replaceAll("[^0-9]", ""));
				MoonConfig.setInvQualityThreshold(t, v);
				reload.run();
			    } catch(NumberFormatException ignored) {}
			}
		    }, prev.pos("bl").adds(0, 6).x(0));
		prev = OptWnd.this.addMoonInvQualColorRows(cont, prev, t, reload);
	    }

	    prev = cont.add(new Button(UI.scale(280), LocalizationManager.tr("moon.inv.qual.import.legacy"), false).action(() -> {
			if(MoonConfig.importLegacyInvQualityPrefs()) {
			    if(ui != null)
				ui.msg(LocalizationManager.tr("moon.inv.qual.import.ok"), java.awt.Color.WHITE, null);
			    MoonInvQualityPanel.this.rebuildInvQualPanelContent();
			    reload.run();
			} else {
			    if(ui != null)
				ui.msg(LocalizationManager.tr("moon.inv.qual.import.none"), java.awt.Color.WHITE, null);
			}
		}), prev.pos("bl").adds(0, 14).x(0));

	    cont.pack();
	    pack();
	}
    }

    public class MoonFunctionsPanel extends Panel {
	public MoonFunctionsPanel(Panel back) {
	    Scrollport scroll = add(new Scrollport(moonScrollViewport()), Coord.z);
	    addFixedBackButton(this, scroll, UI.scale(240), back);
	    Widget cont = scroll.cont;
	    Widget prev;

	    prev = cont.add(new Label(LocalizationManager.tr("moonwide.func")), 0, 0);

	    prev = cont.add(new Button(UI.scale(240), LocalizationManager.tr("session.switch.open"), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui != null)
			    gui.toggleSessionSwitcher();
		}), prev.pos("bl").adds(0, 8).x(0));

	    prev = cont.add(new Label("--- " + moonText("Шахта", "Mining") + " ---"), prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new Button(UI.scale(240), moonText("Бот копки / сапёр", "Mining bot / sapper"), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui == null)
			    return;
			gui.openMoonMineBotWindow();
			OptWnd.this.hide();
		}), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new Label(moonText("Окно маршрута копки, сапёра и предпросмотра туннеля.", "Route mining, sapper and tunnel preview window.")),
		prev.pos("bl").adds(0, 3).x(UI.scale(8)));
	    prev = cont.add(new CheckBox(moonText("Включить автодроп из инвентаря", "Enable inventory auto-drop")) {
		    { a = MoonConfig.mineAutoDropEnabled; }
		    public void set(boolean val) { MoonConfig.setMineAutoDropEnabled(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new Label(moonText("Не требует запущенного бота копки.", "Does not require the mining bot to be running.")),
		prev.pos("bl").adds(0, 3).x(UI.scale(8)));
	    prev = cont.add(new Button(UI.scale(240), moonText("Категории автодропа...", "Auto-drop categories..."), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui == null)
			    return;
			gui.openMoonAutoDropWindow();
			OptWnd.this.hide();
		}), prev.pos("bl").adds(0, 6).x(0));

	    prev = cont.add(new Label("--- " + LocalizationManager.tr("cat.combat") + " ---"), prev.pos("bl").adds(0, 14).x(0));

	    prev = cont.add(new Button(UI.scale(240), LocalizationManager.tr("opt.combatai.open"), false).action(() -> {
			GameUI gui = getparent(GameUI.class);
			if(gui == null)
			    return;
			gui.openMoonCombatBotWindow();
			OptWnd.this.hide();
	    }), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("opt.combatai.where.short")), prev.pos("bl").adds(0, 3).x(UI.scale(8)));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("trace.hostile")) {
		    { a = MoonConfig.traceHostile; }
		    public void set(boolean val) { MoonConfig.setTraceHostile(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("trace.players")) {
		    { a = MoonConfig.tracePlayers; }
		    public void set(boolean val) { MoonConfig.setTracePlayers(val); a = val; }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("trace.neutral")) {
		    { a = MoonConfig.traceNeutralMobs; }
		    public void set(boolean val) { MoonConfig.setTraceNeutralMobs(val); a = val; }
		}, prev.pos("bl").adds(0, 5).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("opt.esp.section")), prev.pos("bl").adds(0, 10).x(0));
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.hostile"), MoonEspProfile.HOSTILE_MOB);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.player"), MoonEspProfile.PLAYER);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.neutral"), MoonEspProfile.NEUTRAL_MOB);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.vehicles"), MoonEspProfile.VEHICLE);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.buildings"), MoonEspProfile.BUILDING);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.resources"), MoonEspProfile.RESOURCE);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.containers"), MoonEspProfile.CONTAINER);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.herbs"), MoonEspProfile.HERB);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.dungeons"), MoonEspProfile.DUNGEON);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.workstations"), MoonEspProfile.WORKSTATION);
	    prev = addEspRow(cont, prev, LocalizationManager.tr("esp.items"), MoonEspProfile.ITEM);
	    {
		int indent = UI.scale(14);
		prev = cont.add(new Label(LocalizationManager.tr("esp.item.font")), prev.pos("bl").adds(0, 4).x(indent));
		prev = cont.add(new TextEntry(UI.scale(44), Integer.toString(MoonEspProfile.ITEM.itemLabelPx())) {
		    @Override
		    public void activate(String text) {
			try {
			    MoonEspProfile.ITEM.setItemFontPx(Integer.parseInt(text.trim()));
			} catch (Exception ignored) {}
			super.activate(text);
		    }
		}, prev.pos("ur").adds(UI.scale(8), 0));
		prev = cont.add(new Label(LocalizationManager.tr("esp.item.hitbox")), prev.pos("bl").adds(0, 6).x(indent));
		prev = cont.add(new TextEntry(UI.scale(44), String.format(Locale.US, "%.2f", MoonEspProfile.ITEM.itemBoxHalfW())) {
		    @Override
		    public void activate(String text) {
			try {
			    MoonEspProfile.ITEM.setItemBoxHalfW(Double.parseDouble(text.trim().replace(',', '.')));
			} catch (Exception ignored) {}
			super.activate(text);
		    }
		}, prev.pos("ur").adds(UI.scale(8), 0));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("aggro.radius")) {
		    { a = MoonConfig.aggroRadius; }
		    public void set(boolean val) { MoonConfig.setAggroRadius(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("safe.mode")) {
		    { a = MoonConfig.safeMode; }
		    public void set(boolean val) { MoonConfig.setSafeMode(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("safe.mode.busyonly")) {
		    { a = MoonConfig.safeModeBusyOnly; }
		    public void set(boolean val) { MoonConfig.setSafeModeBusyOnly(val); a = val; }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("safe.mode.nofight")) {
		    { a = MoonConfig.safeModeRespectCombat; }
		    public void set(boolean val) { MoonConfig.setSafeModeRespectCombat(val); a = val; }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new Label(LocalizationManager.tr("safe.mode.mintiles")), prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new TextEntry(UI.scale(50), String.format(Locale.US, "%.1f", MoonConfig.safeModeMinTiles)) {
		    @Override
		    public void activate(String text) {
			try {
			    MoonConfig.setSafeModeMinTiles(Double.parseDouble(text.trim().replace(',', '.')));
			} catch (Exception ignored) {}
			super.activate(text);
		    }
		}, prev.pos("ur").adds(UI.scale(8), 0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("activity.hud")) {
		    { a = MoonConfig.activityHud; }
		    public void set(boolean val) { MoonConfig.setActivityHud(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("activity.hud.range")), prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new TextEntry(UI.scale(50), String.format(Locale.US, "%.0f", MoonConfig.activityHudStructRange)) {
		    @Override
		    public void activate(String text) {
			try {
			    MoonConfig.setActivityHudStructRange(Double.parseDouble(text.trim().replace(',', '.')));
			} catch (Exception ignored) {}
			super.activate(text);
		    }
		}, prev.pos("ur").adds(UI.scale(8), 0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("xray.enable")) {
		    { a = MoonConfig.xrayEnabled; }
		    public void set(boolean val) { MoonConfig.setXrayEnabled(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("xray.style.tiles")) {
		    { a = MoonConfig.xrayStyle == 1; }
		    public void set(boolean val) {
			MoonConfig.setXrayStyle(val ? 1 : 0);
			a = val;
		    }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("xray.style.tiles.tip"); }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("xray.hide.rocks")) {
		    { a = MoonConfig.xrayHideMode == 1; }
		    public void set(boolean val) {
			MoonConfig.setXrayHideMode(val ? 1 : 0);
			a = val;
			if(ui != null) {
			    String msg = val ? LocalizationManager.tr("xray.mode.trees.rocks") : LocalizationManager.tr("xray.mode.trees.only");
			    ui.msg(msg, java.awt.Color.WHITE, null);
			}
		    }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("xray.hide.rocks.tip"); }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));

	    prev = cont.add(new Label("[" + MoonEspProfile.colorName(MoonConfig.xrayColorIdx) + "]") {
		    { setcolor(MoonConfig.xrayColor()); }
		    public boolean mousedown(MouseDownEvent ev) {
			if(ev.b == 1) {
			    int next = (MoonConfig.xrayColorIdx + 1) % MoonEspProfile.presetCount();
			    MoonConfig.setXrayColorIdx(next);
			    settext("[" + MoonEspProfile.colorName(next) + "]");
			    setcolor(MoonConfig.xrayColor());
			    return true;
			}
			return super.mousedown(ev);
		    }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("xray.color.tip"); }
		}, prev.pos("bl").adds(0, 4).x(UI.scale(16)));

	    prev = cont.add(new CheckBox(LocalizationManager.tr("menugrid.keyboard")) {
		    { a = MoonConfig.menuGridKeyboard; }
		    public void set(boolean val) { MoonConfig.setMenuGridKeyboard(val); a = val; }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("menugrid.keyboard.tip"); }
		}, prev.pos("bl").adds(0, 14).x(0));
	    prev = cont.add(new Label("--- " + LocalizationManager.tr("bulk.station.title") + " ---"), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.fill")) {
		    { a = MoonConfig.bulkStationFill; }
		    public void set(boolean val) { MoonConfig.setBulkStationFill(val); a = val; }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("bulk.station.fill.tip"); }
		}, prev.pos("bl").adds(0, 6).x(0));
	    prev = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.takeall")) {
		    { a = MoonConfig.bulkStationTakeAll; }
		    public void set(boolean val) { MoonConfig.setBulkStationTakeAll(val); a = val; }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("bulk.station.takeall.tip"); }
		}, prev.pos("bl").adds(0, 4).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.modmask")), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.modmask.tip")), prev.pos("bl").adds(UI.scale(16), 2).x(UI.scale(16)));
	    {
		final CheckBox[] bulkMaskBox = new CheckBox[3];
		Runnable syncBulkMask = () -> {
		    int m = MoonConfig.bulkStationFillModMask;
		    if(bulkMaskBox[0] != null)
			bulkMaskBox[0].a = (m & UI.MOD_SHIFT) != 0;
		    if(bulkMaskBox[1] != null)
			bulkMaskBox[1].a = (m & UI.MOD_CTRL) != 0;
		    if(bulkMaskBox[2] != null)
			bulkMaskBox[2].a = (m & UI.MOD_META) != 0;
		};
		bulkMaskBox[0] = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.modshift")) {
			{ a = (MoonConfig.bulkStationFillModMask & UI.MOD_SHIFT) != 0; }
			public void set(boolean val) {
			    int m = MoonConfig.bulkStationFillModMask;
			    if(val) m |= UI.MOD_SHIFT; else m &= ~UI.MOD_SHIFT;
			    MoonConfig.setBulkStationFillModMask(m);
			    syncBulkMask.run();
			}
		    }, prev.pos("bl").adds(0, 4).x(UI.scale(16)));
		bulkMaskBox[1] = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.modctrl")) {
			{ a = (MoonConfig.bulkStationFillModMask & UI.MOD_CTRL) != 0; }
			public void set(boolean val) {
			    int m = MoonConfig.bulkStationFillModMask;
			    if(val) m |= UI.MOD_CTRL; else m &= ~UI.MOD_CTRL;
			    MoonConfig.setBulkStationFillModMask(m);
			    syncBulkMask.run();
			}
		    }, bulkMaskBox[0].pos("bl").adds(0, 3).x(0));
		bulkMaskBox[2] = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.modalt")) {
			{ a = (MoonConfig.bulkStationFillModMask & UI.MOD_META) != 0; }
			public void set(boolean val) {
			    int m = MoonConfig.bulkStationFillModMask;
			    if(val) m |= UI.MOD_META; else m &= ~UI.MOD_META;
			    MoonConfig.setBulkStationFillModMask(m);
			    syncBulkMask.run();
			}
			public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("bulk.station.modalt.tip"); }
		    }, bulkMaskBox[1].pos("bl").adds(0, 3).x(0));
		prev = bulkMaskBox[2];
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.debugwire")) {
		    { a = MoonConfig.debugInventoryWire; }
		    public void set(boolean val) { MoonConfig.setDebugInventoryWire(val); a = val; }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("bulk.station.debugwire.tip"); }
		}, prev.pos("bl").adds(0, 8).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.interval")), prev.pos("bl").adds(0, 8).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
		prev = cont.add(new HSlider(UI.scale(250), 50, 500, MoonConfig.bulkStationRepeatIntervalMs) {
			protected void added() { dpy.settext(val + "ms"); }
			public void changed() {
			    MoonConfig.setBulkStationRepeatIntervalMs(val);
			    dpy.settext(val + "ms");
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.maxrepeats")), prev.pos("bl").adds(0, 6).x(0));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
		prev = cont.add(new HSlider(UI.scale(250), 1, 128, Utils.clip(MoonConfig.bulkStationMaxRepeats, 1, 128)) {
			protected void added() { dpy.settext(Integer.toString(val)); }
			public void changed() {
			    MoonConfig.setBulkStationMaxRepeats(val);
			    dpy.settext(Integer.toString(val));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.stallrepeats")), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.stallrepeats.tip")), prev.pos("bl").adds(UI.scale(16), 2).x(UI.scale(16)));
	    {
		Label dpy = cont.add(new Label(""), prev.pos("bl").adds(0, 3).x(UI.scale(260)));
		prev = cont.add(new HSlider(UI.scale(250), 0, 128, Utils.clip(MoonConfig.bulkStationStallRepeats, 0, 128)) {
			protected void added() { dpy.settext(val == 0 ? LocalizationManager.tr("bulk.station.stallrepeats.off") : Integer.toString(val)); }
			public void changed() {
			    MoonConfig.setBulkStationStallRepeats(val);
			    dpy.settext(val == 0 ? LocalizationManager.tr("bulk.station.stallrepeats.off") : Integer.toString(val));
			}
		    }, prev.pos("bl").adds(0, 3).x(0));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("bulk.station.tubfirst")) {
		    { a = MoonConfig.bulkTubSendFirstItemact; }
		    public void set(boolean val) { MoonConfig.setBulkTubSendFirstItemact(val); a = val; }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("bulk.station.tubfirst.tip"); }
		}, prev.pos("bl").adds(0, 8).x(0));

	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.blacklist")), prev.pos("bl").adds(0, 8).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("bulk.station.blacklist.tip")), prev.pos("bl").adds(UI.scale(16), 2).x(UI.scale(16)));
	    for(String key : MoonBulkStation.knownStationKeys()) {
		final String stationKey = key;
		prev = cont.add(new CheckBox(LocalizationManager.trOr(MoonBulkStation.stationLabelKey(stationKey), stationKey)) {
			{ a = MoonConfig.bulkStationBlacklistContains(stationKey); }
			public void set(boolean val) {
			    MoonConfig.setBulkStationBlacklisted(stationKey, val);
			    a = val;
			}
		    }, prev.pos("bl").adds(0, 3).x(UI.scale(16)));
	    }

	    prev = cont.add(new CheckBox(LocalizationManager.tr("fight.overlay.fixed")) {
		    { a = MoonConfig.fightOverlayFixed; }
		    public void set(boolean val) { MoonConfig.setFightOverlayFixed(val); a = val; }
		}, prev.pos("bl").adds(0, 10).x(0));
	    prev = cont.add(new Label(LocalizationManager.tr("fight.overlay.hint")), prev.pos("bl").adds(20, 2).x(UI.scale(20)));

	    cont.pack();
	    pack();
	}

	private Widget addEspRow(Widget cont, Widget prev, String label, MoonEspProfile prof) {
	    int indent = UI.scale(14);
	    prev = cont.add(new CheckBox(label) {
		    { a = prof.enabled; }
		    public void set(boolean val) { prof.setEnabled(val); a = val; }
		}, prev.pos("bl").adds(0, 6).x(0));

	    cont.add(new CheckBox(LocalizationManager.tr("esp.opt.dist")) {
		    { a = prof.showDist; }
		    public void set(boolean val) { prof.setShowDist(val); a = val; }
		}, prev.pos("ur").adds(UI.scale(10), 0));

	    cont.add(new CheckBox(LocalizationManager.tr("esp.opt.name")) {
		    { a = prof.showName; }
		    public void set(boolean val) { prof.setShowName(val); a = val; }
		}, prev.pos("ur").adds(UI.scale(80), 0));

	    cont.add(new CheckBox(LocalizationManager.tr("esp.opt.speed")) {
		    { a = prof.showSpeed; }
		    public void set(boolean val) { prof.setShowSpeed(val); a = val; }
		}, prev.pos("ur").adds(UI.scale(150), 0));

	    cont.add(new Label("[" + MoonEspProfile.colorName(prof.colorIdx()) + "]") {
		    { setcolor(prof.color); }
		    public boolean mousedown(MouseDownEvent ev) {
			if(ev.b == 1) {
			    int next = (prof.colorIdx() + 1) % MoonEspProfile.presetCount();
			    prof.setColorIdx(next);
			    settext("[" + MoonEspProfile.colorName(next) + "]");
			    setcolor(prof.color);
			    return true;
			}
			return super.mousedown(ev);
		    }
		    public Object tooltip(Coord c, Widget prev) { return LocalizationManager.tr("esp.opt.color.tip"); }
		}, prev.pos("ur").adds(UI.scale(220), 0));

	    return prev;
	}
    }

    public OptWnd(boolean gopts) {
	super(Coord.z, LocalizationManager.tr("opt.window.title"), true);
	main = add(new Panel());
	moonchat = add(new MoonChatPanel(main));
	moonautomation = add(new MoonAutomationPanel(main));
	moonoverlay = add(new MoonOverlayPanel(main));
	mooncombat = add(new MoonCombatPanel(main));
	moonperformance = add(new MoonPerformancePanel(main));
	moonintegrations = add(new MoonIntegrationsPanel(main));
	moonfunctions = add(new MoonFunctionsPanel(main));
	mooninvquality = add(new MoonInvQualityPanel(main));
	moonspeed = add(new MoonSpeedPanel(main));
	moonexperimental = add(new MoonExperimentalPanel(main));
	Panel video = add(new VideoPanel(main));
	Panel audio = add(new AudioPanel(main));
	Panel iface = add(new InterfacePanel(main));
	Panel camera = add(new CameraPanel(main));
	Panel keybind = add(new BindingPanel(main));
	Panel layout = add(new MoonLayoutPanel(main));

	int y = optpady;
	int col2 = optpadx + UI.scale(230);
	int col3 = optpadx + UI.scale(460);
	int btnW = UI.scale(200);
	java.awt.Color hdrCol = MoonPanel.MOON_ACCENT;
	Text.Foundry hdrFnd = new Text.Foundry(Text.sans, 13).aa(true);

	{Label h = main.add(new Label(LocalizationManager.tr("menu.moonwide"), hdrFnd), optpadx, y); h.setcolor(hdrCol);}
	y += UI.scale(20);

	main.add(new PButton(btnW, LocalizationManager.tr("opt.moonwide"), -1, moonfunctions) {
		public Object tooltip(Coord c, Widget prev) {
		    return(LocalizationManager.tr("moonwide.func"));
		}
	    }, optpadx, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.speed"), -1, moonspeed) {
		public Object tooltip(Coord c, Widget prev) {
		    return(LocalizationManager.tr("opt.speed.tip"));
		}
	    }, col2, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.experimental"), 'e', moonexperimental) {
		public Object tooltip(Coord c, Widget prev) {
		    return LocalizationManager.tr("opt.experimental.tip");
		}
	    }, col3, y);
	y += UI.scale(30);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.overlay"), 'w', moonoverlay), optpadx, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.combat"), 'c', mooncombat) {
		public Object tooltip(Coord c, Widget prev) {
		    return LocalizationManager.tr("opt.combat.tip");
		}
	    }, col2, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.performance"), 'p', moonperformance), col3, y);
	y += UI.scale(30);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.integrations"), 'i', moonintegrations), optpadx, y);
	main.add(new Button(btnW, LocalizationManager.tr("opt.guide"), false).action(() -> {
	    GameUI gui = getparent(GameUI.class);
	    if(gui != null)
		gui.toggleMoonUiGuideWindow();
	    OptWnd.this.hide();
	}), col2, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.invQuality"), -1, mooninvquality), col3, y);
	y += UI.scale(38);

	{Label h = main.add(new Label(LocalizationManager.tr("menu.vanilla"), hdrFnd), optpadx, y); h.setcolor(hdrCol);}
	y += UI.scale(20);

	main.add(new PButton(btnW, LocalizationManager.tr("opt.video"), 'v', video), optpadx, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.audio"), 'a', audio), col2, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.display"), 'd', iface), col3, y);
	y += UI.scale(30);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.camera.btn"), 'm', camera), optpadx, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.keybindings"), 'k', keybind), col2, y);
	main.add(new PButton(btnW, LocalizationManager.tr("opt.chat"), 'h', moonchat), col3, y);
	y += UI.scale(30);
	y += UI.scale(38);

	{Label h = main.add(new Label(LocalizationManager.tr("menu.general"), hdrFnd), optpadx, y); h.setcolor(hdrCol);}
	y += UI.scale(20);

	main.add(new PButton(btnW, LocalizationManager.tr("opt.layout"), 'l', layout), optpadx, y);
	y += UI.scale(30);

	{
	    Label langLbl = new Label(LocalizationManager.tr("settings.language") + ":");
	    main.add(langLbl, optpadx, y);
	    Button langBtn = new Button(UI.scale(100), MoonL10n.lang().equals("ru") ? "Русский" : "English", false) {
		    public void click() {
			if(MoonL10n.lang().equals("ru"))
			    MoonL10n.setLang("en");
			else
			    MoonL10n.setLang("ru");
			change(MoonL10n.lang().equals("ru") ? "Русский" : "English");
		    }
		};
	    main.add(langBtn, optpadx + UI.scale(180), y);
	    y += UI.scale(28);
	}

	{
	    CheckBox atEn = new CheckBox(LocalizationManager.tr("opt.auto_translate.enable")) {
		public void changed(boolean val) {
		    Utils.setprefb("moon-auto-translate", val);
		}
	    };
	    atEn.a = Utils.getprefb("moon-auto-translate", false);
	    y = main.add(atEn, optpadx, y).pos("bl").adds(0, 6).y;
	    main.add(new Label(LocalizationManager.tr("opt.auto_translate.url")), optpadx, y);
	    y += UI.scale(18);
	    TextEntry atUrl = new TextEntry(UI.scale(420), Utils.getpref("moon-translate-api-url", "https://libretranslate.com/translate")) {
		public boolean keydown(KeyDownEvent ev) {
		    if(ev.code == KeyEvent.VK_ENTER) {
			Utils.setpref("moon-translate-api-url", text());
			return(true);
		    }
		    return(super.keydown(ev));
		}
	    };
	    y = main.add(atUrl, optpadx, y).pos("bl").adds(0, 4).y;
	    main.add(new Label(LocalizationManager.tr("opt.auto_translate.threads")), optpadx, y);
	    y += UI.scale(18);
	    Label atThrVal = new Label(String.valueOf(Utils.getprefi("moon-translate-max-threads", 3)));
	    HSlider atThr = new HSlider(UI.scale(160), 1, 8, Utils.getprefi("moon-translate-max-threads", 3)) {
		public void changed() {
		    Utils.setprefi("moon-translate-max-threads", val);
		    atThrVal.settext(String.valueOf(val));
		}
	    };
	    main.add(atThr, optpadx, y);
	    main.add(atThrVal, optpadx + UI.scale(170), y + UI.scale(2));
	    y += UI.scale(28);
	    main.add(new Button(UI.scale(200), LocalizationManager.tr("opt.auto_translate.apply_url"), false).action(() -> {
		Utils.setpref("moon-translate-api-url", atUrl.text());
	    }), optpadx, y);
	    y += UI.scale(26);
	    main.add(new Button(UI.scale(240), LocalizationManager.tr("opt.auto_translate.metrics_btn"), false).action(() -> {
		ui.msg(LocalizationManager.autoTranslateMetricsLine(), java.awt.Color.WHITE, null);
	    }), optpadx, y);
	    y += UI.scale(30);
	}

	CheckBox confirmCloseCb = new CheckBox(LocalizationManager.tr("opt.confirm_close"));
	confirmCloseCb.a = Config.confirmclose;
	confirmCloseCb.changed = val -> { Utils.setprefb("confirmclose", Config.confirmclose = val); };
	y = main.add(confirmCloseCb, optpadx, y).pos("bl").adds(0, 6).y;
	y += UI.scale(10);
	if(gopts) {
	    if((SteamStore.steamsvc.get() != null) && (Steam.get() != null)) {
		y = main.add(new Button(btnW, LocalizationManager.tr("opt.store"), false).action(() -> {
			    SteamStore.launch(ui.sess);
		}), optpadx, y).pos("bl").adds(0, 4).y;
	    }
	    y = main.add(new Button(btnW, LocalizationManager.tr("opt.switch_char"), false).action(() -> {
			getparent(GameUI.class).act("lo", "cs");
	    }), optpadx, y).pos("bl").adds(0, 8).y;
	    y = main.add(new Button(btnW, LocalizationManager.tr("opt.logout"), false).action(() -> {
			getparent(GameUI.class).act("lo");
	    }), optpadx, y).pos("bl").adds(0, 8).y;
	    y = main.add(new Button(btnW, moonText("Выйти из игры", "Exit game"), false).action(() -> {
			MainFrame mf = MainFrame.getInstance();
			if(mf != null)
			    mf.requestQuit();
	    }), optpadx, y).pos("bl").adds(0, 8).y;
	}
	this.main.pack();
	this.main.resize(this.main.sz.add(optpadbr, optpadbr));

	chpanel(this.main);
    }

    public OptWnd() {
	this(true);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if((sender == this) && (msg == "close")) {
	    hide();
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    public void show() {
	if(current == null)
	    chpanel(main);
	clearStoredFocus();
	super.show();
	raise();
    }

    public void hide() {
	super.hide();
	clearStoredFocus();
    }
}
