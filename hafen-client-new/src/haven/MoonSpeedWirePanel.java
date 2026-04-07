package haven;

public class MoonSpeedWirePanel extends MoonPanel {
    private CheckBox assistCb, boostCb, sprintCb;
    private HSlider multSl, visualSl, resendSl;
    private Label multDpy, visualDpy, resendDpy;
    private double syncAcc;

    public MoonSpeedWirePanel() {
	super(computeOuterSz(), "moon-speed-wire", LocalizationManager.tr("speedwire.panel.title"));
	setMinSize(sz);
	Coord c0 = contentOffset();
	int x = c0.x;
	int sw = UI.scale(200);

	assistCb = add(new CheckBox(LocalizationManager.tr("speedwire.panel.assist"), true) {
	    { a = MoonConfig.experimentalSpeedWireAssist; }
	    public void set(boolean val) {
		MoonConfig.setExperimentalSpeedWireAssist(val);
		a = val;
		MoonSpeedBoost.pingSpeedgetWire(MoonSpeedWirePanel.this);
	    }
	}, c0);

	boostCb = add(new CheckBox(LocalizationManager.tr("speedwire.panel.boost"), true) {
	    { a = MoonConfig.speedBoost; }
	    public void set(boolean val) {
		MoonConfig.setSpeedBoost(val);
		a = val;
		MoonSpeedBoost.pingSpeedgetWire(MoonSpeedWirePanel.this);
	    }
	}, assistCb.pos("bl").adds(0, UI.scale(4)).x(x));

	sprintCb = add(new CheckBox(LocalizationManager.tr("speedwire.panel.sprint"), true) {
	    { a = MoonConfig.clientScaleServerSprint && MoonConfig.clientSpeedScale; }
	    public void set(boolean val) {
		MoonConfig.setClientSpeedScale(val);
		MoonConfig.setClientScaleServerSprint(val);
		a = val;
		MoonSpeedBoost.pingSpeedgetWire(MoonSpeedWirePanel.this);
	    }
	}, boostCb.pos("bl").adds(0, UI.scale(4)).x(x));

	multDpy = add(new Label(multLine()), sprintCb.pos("bl").adds(0, UI.scale(8)).x(x));
	int smInit = (int) Math.round(Utils.clip(MoonConfig.speedMultiplier * 10.0, 10.0, 50.0));
	multSl = add(new HSlider(sw, 10, 50, smInit) {
	    public void changed() {
		MoonConfig.setSpeedMultiplier(val / 10.0);
		multDpy.settext(multLine());
		if(val > 10 && !MoonConfig.experimentalSpeedWireAssist) {
		    MoonConfig.setExperimentalSpeedWireAssist(true);
		    assistCb.a = true;
		}
	    }
	    public void fchanged() {
		MoonSpeedBoost.pingSpeedgetWire(MoonSpeedWirePanel.this);
	    }
	}, multDpy.pos("bl").adds(0, UI.scale(3)).x(x));

	visualDpy = add(new Label(visualLine()), multSl.pos("bl").adds(0, UI.scale(8)).x(x));
	int vmInit = (int) Math.round(Utils.clip(MoonConfig.linMoveVisualSpeedMult * 10.0, 10.0, 30.0));
	visualSl = add(new HSlider(sw, 10, 30, vmInit) {
	    public void changed() {
		Glob gl = null;
		if(ui != null && ui.sess != null)
		    gl = ui.sess.glob;
		MoonConfig.setLinMoveVisualSpeedMult(val / 10.0, gl);
		visualDpy.settext(visualLine());
	    }
	}, visualDpy.pos("bl").adds(0, UI.scale(3)).x(x));

	resendDpy = add(new Label(resendLine()), visualSl.pos("bl").adds(0, UI.scale(8)).x(x));
	int rsInit = (int) Math.round(Utils.clip(MoonConfig.speedMultResendIntervalSec, 0.0, 120.0));
	resendSl = add(new HSlider(sw, 0, 120, rsInit) {
	    public void changed() {
		MoonConfig.setSpeedMultResendIntervalSec(val);
		resendDpy.settext(resendLine());
	    }
	    public void fchanged() {
		MoonSpeedBoost.pingSpeedgetWire(MoonSpeedWirePanel.this);
	    }
	}, resendDpy.pos("bl").adds(0, UI.scale(3)).x(x));

	add(new Button(sw, LocalizationManager.tr("speedwire.panel.opts"), false) {
	    public void click() {
		GameUI gui = getparent(GameUI.class);
		if(gui != null)
		    gui.openSpeedPanel();
	    }
	}, resendSl.pos("bl").adds(0, UI.scale(10)).x(x));
    }

    private static Coord computeOuterSz() {
	Coord inner = UI.scale(new Coord(256, 290));
	return Coord.of(inner.x + MoonPanel.PAD * 2, inner.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2);
    }

    private String multLine() {
	return String.format(LocalizationManager.tr("speedwire.panel.mult"), MoonConfig.speedMultiplier);
    }

    private String visualLine() {
	return String.format(LocalizationManager.tr("speedwire.panel.visual"), MoonConfig.linMoveVisualSpeedMult);
    }

    private String resendLine() {
	double iv = MoonConfig.speedMultResendIntervalSec;
	return iv <= 0 ? LocalizationManager.tr("speedwire.panel.resend.off")
	    : String.format(LocalizationManager.tr("speedwire.panel.resend.on"), (int) iv);
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	syncAcc += dt;
	if(syncAcc < 0.35)
	    return;
	syncAcc = 0;
	int m = (int) Math.round(Utils.clip(MoonConfig.speedMultiplier * 10.0, 10.0, 50.0));
	if(multSl.val != m) {
	    multSl.val = m;
	    multDpy.settext(multLine());
	}
	int vm = (int) Math.round(Utils.clip(MoonConfig.linMoveVisualSpeedMult * 10.0, 10.0, 30.0));
	if(visualSl.val != vm) {
	    visualSl.val = vm;
	    visualDpy.settext(visualLine());
	}
	if(assistCb.a != MoonConfig.experimentalSpeedWireAssist)
	    assistCb.a = MoonConfig.experimentalSpeedWireAssist;
	if(boostCb.a != MoonConfig.speedBoost)
	    boostCb.a = MoonConfig.speedBoost;
	boolean sc = MoonConfig.clientScaleServerSprint && MoonConfig.clientSpeedScale;
	if(sprintCb.a != sc)
	    sprintCb.a = sc;
	int rs = (int) Math.round(Utils.clip(MoonConfig.speedMultResendIntervalSec, 0.0, 120.0));
	if(resendSl.val != rs) {
	    resendSl.val = rs;
	    resendDpy.settext(resendLine());
	}
    }
}
