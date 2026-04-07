package haven;

import haven.render.Pipe;
import java.awt.Color;

/**
 * 2D line from a {@link Homing} gob to its target (screen space).
 */
public class MoonChaseVectorSprite extends Sprite implements PView.Render2D {
    private static final String[] IGNORE_SUBSTR = {
	"gfx/terobjs/vehicle/cart",
	"gfx/terobjs/vehicle/wagon",
	"gfx/terobjs/vehicle/wheelbarrow",
    };

    public MoonChaseVectorSprite(Gob gob) {
	super(gob, null);
    }

    private Gob gb() {
	return (Gob)owner;
    }

    private static boolean ignoredRes(String path) {
	if(path == null)
	    return true;
	for(String s : IGNORE_SUBSTR) {
	    if(path.contains(s))
		return true;
	}
	return false;
    }

    @Override
    public void draw(GOut g, Pipe state) {
	if(!MoonConfig.combatDrawChaseVectors)
	    return;
	try {
	    Gob gob = gb();
	    if(gob == null)
		return;
	    RenderContext rcx = state.get(RenderContext.slot);
	    if(!(rcx instanceof PView.WidgetContext))
		return;
	    PView pv = ((PView.WidgetContext)rcx).widget();
	    if(!(pv instanceof MapView))
		return;
	    MapView mv = (MapView)pv;
	    Moving mov = gob.getattr(Moving.class);
	    if(!(mov instanceof Homing))
		return;
	    Gob target = ((Homing)mov).targetGob();
	    if(target == null)
		return;
	    String gn = MoonGobKind.resourceName(gob);
	    String tn = MoonGobKind.resourceName(target);
	    if(ignoredRes(gn) || ignoredRes(tn))
		return;
	    Color col;
	    if(gob.id == mv.plgob || gob.id == gob.glob.moonPlayerGobId)
		col = new Color(MoonConfig.combatChaseArgbSelf, true);
	    else if(gob.glob.party.memb.containsKey(gob.id))
		col = new Color(MoonConfig.combatChaseArgbFriend, true);
	    else
		col = new Color(MoonConfig.combatChaseArgbEnemy, true);
	    Coord a = mv.screenxf(gob.getc()).round2();
	    Coord b = mv.screenxf(target.getc()).round2();
	    g.chcolor(Color.BLACK);
	    g.line(a, b, 4);
	    g.chcolor(col);
	    g.line(a, b, 2);
	    g.chcolor();
	} catch(Loading ignored) {
	} catch(Exception ignored) {
	}
    }
}
