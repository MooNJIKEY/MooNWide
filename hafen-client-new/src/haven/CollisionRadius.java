package haven;

import java.util.Objects;

/**
 * Cached collision circle derived from a gob's current footprint.
 *
 * The pathfinder uses it as a fast clearance proxy, while still keeping the original polygon
 * footprint for tighter tile rasterisation and line-of-sight tests.
 */
public final class CollisionRadius extends GAttrib {
    public final double radius;
    public final Coord2d[][] footprint;
    public final String resourceName;
    public final int sdtHash;

    private CollisionRadius(Gob gob, double radius, Coord2d[][] footprint, String resourceName, int sdtHash) {
	super(gob);
	this.radius = radius;
	this.footprint = footprint;
	this.resourceName = resourceName;
	this.sdtHash = sdtHash;
    }

    public static CollisionRadius get(Gob gob) {
	if(gob == null)
	    return(null);
	String resName = resourceName(gob);
	int sdt = sdtHash(gob);
	CollisionRadius cur = gob.getattr(CollisionRadius.class);
	if(cur != null && Objects.equals(cur.resourceName, resName) && cur.sdtHash == sdt)
	    return(cur);
	Coord2d[][] bbox;
	try {
	    bbox = MoonHitboxMode.getBBox(gob);
	} catch(Loading e) {
	    return(cur);
	} catch(Exception e) {
	    return(cur);
	}
	if(bbox == null || bbox.length == 0)
	    return(null);
	double radius = 0.0;
	for(Coord2d[] poly : bbox) {
	    if(poly == null)
		continue;
	    for(Coord2d p : poly) {
		if(p != null)
		    radius = Math.max(radius, p.abs());
	    }
	}
	if(!(radius > 0.0))
	    return(null);
	CollisionRadius next = new CollisionRadius(gob, radius, bbox, resName, sdt);
	synchronized(gob) {
	    gob.setattr(next);
	}
	return(next);
    }

    private static String resourceName(Gob gob) {
	try {
	    Drawable d = gob.getattr(Drawable.class);
	    if(d == null)
		return(null);
	    Resource res = d.getres();
	    return((res == null) ? null : res.name);
	} catch(Loading e) {
	    return(null);
	}
    }

    private static int sdtHash(Gob gob) {
	ResDrawable rd = gob.getattr(ResDrawable.class);
	if(rd == null || rd.sdt == null || rd.sdt.rbuf == null)
	    return(0);
	return(java.util.Arrays.hashCode(rd.sdt.rbuf));
    }
}
