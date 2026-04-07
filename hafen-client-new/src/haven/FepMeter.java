/*
 *  HUD FEP bar: extends {@link IMeter} with local frame res, reads live segments from
 *  {@link BAttrWnd.FoodMeter} on the character sheet.
 */

package haven;

import java.awt.Color;

public class FepMeter extends IMeter {
    private final BAttrWnd.FoodMeter food;

    public FepMeter(BAttrWnd.FoodMeter food) {
	/* Remote pool falls back past a bad local {@code res} entry (empty file) to {@code res-preload}. */
	super(Resource.remote().load("hud/meter/fepmeter"), "meter-fep");
	this.food = food;
    }

    @Override
    protected void drawMeters(GOut g) {
	if(food.cap <= 0.0001)
	    return;
	boolean mini = DefSettings.minimalisticmeter;
	double x = 0;
	int w = mini ? contentW() : msz.x;
	for(BAttrWnd.FoodMeter.El el : food.els) {
	    int l = (int)Math.floor((x / food.cap) * w);
	    int r = (int)Math.floor(((x += el.a) / food.cap) * w);
	    int bw = Math.max(0, r - l);
	    try {
		Color col = el.ev().col;
		g.chcolor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 255));
		if(!mini) {
		    g.frect(off.add(l, 0).mul(scale), Coord.of(bw, msz.y).mul(scale));
		} else {
		    Coord mo = miniOff.mul(scale);
		    g.frect(Coord.of(l, 0).mul(scale).add(mo),
			Coord.of((int)(bw * scale), sz.y).sub(mo.mul(2)));
		}
	    } catch(Loading e) {
	    }
	}
	g.chcolor();
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	double sum = 0;
	for(BAttrWnd.FoodMeter.El el : food.els)
	    sum += el.a;
	updatemeterinfo(String.format("%s/%s", Utils.odformat2(sum, 1), Utils.odformat(food.cap, 1)));
    }

    @Override
    protected String typedPosKey() {
	return("wpos-meter-fep");
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
	return(food.tooltip(c, prev));
    }
}
