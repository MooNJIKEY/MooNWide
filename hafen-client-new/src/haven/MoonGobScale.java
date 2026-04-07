package haven;

import haven.render.Location;
import haven.render.Pipe;

/**
 * Client-only scale for palisade/brick walls (Z) and trees/bushes (uniform), matching common
 * Haven tweak semantics without server involvement.
 */
public final class MoonGobScale {
    private MoonGobScale() {}

    public static Pipe.Op gobstate(Gob gob) {
	if(gob == null)
	    return(null);
	try {
	    String name = MoonGobKind.resourceName(gob);
	    if(name == null)
		return(null);
	    if(isPalisadeWall(name)) {
		float z = MoonConfig.palisadeWallScalePct / 100f;
		if(z == 1f)
		    return(null);
		return(Location.scale(1f, 1f, z));
	    }
	    if(MoonCropMode.isCropRes(name)) {
		float s = MoonConfig.cropScalePct / 100f;
		if(s == 1f)
		    return(null);
		return(Location.scale(s, s, s));
	    }
	    MoonGobKind.Kind k = MoonGobKind.classify(name);
	    if(k == MoonGobKind.Kind.TREE || k == MoonGobKind.Kind.BUSH) {
		float s = MoonConfig.treeBushScalePct / 100f;
		if(s == 1f)
		    return(null);
		return(Location.scale(s, s, s));
	    }
	} catch(Loading ignored) {
	}
	return(null);
    }

    public static void refresh(GameUI gui) {
	if(gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.oc == null)
	    return;
	try {
	    synchronized(gui.map.glob.oc) {
		for(Gob g : gui.map.glob.oc) {
		    if(g != null)
			g.updated();
		}
	    }
	} catch(Exception ignored) {
	}
    }

    private static boolean isPalisadeWall(String name) {
	if(name == null)
	    return(false);
	if(name.contains("gate"))
	    return(false);
	return name.startsWith("gfx/terobjs/arch/palisade")
	    || name.startsWith("gfx/terobjs/arch/brickwall");
    }
}
