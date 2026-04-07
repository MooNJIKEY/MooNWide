package haven;

import haven.render.BaseColor;
import haven.render.DataBuffer;
import haven.render.Homo3D;
import haven.render.Model;
import haven.render.NumberFormat;
import haven.render.Pipe;
import haven.render.Rendered;
import haven.render.RenderTree;
import haven.render.States;
import haven.render.VectorFormat;
import haven.render.VertexArray;
import java.util.Arrays;

/**
 * Placement / gob hitbox wireframe: local-space line segments (x, -y, z) under the same
 * {@link Gob.Placed} transform as the mesh.
 */
public final class MoonGobHitboxOl extends Sprite {
    public static final int OLID = "moon-gob-hitbox-wire".hashCode();

    private static final VertexArray.Layout LAYOUT = new VertexArray.Layout(
	new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 12));

    /** Line mode uses xray-style depth; draw wire on top without depth fighting. */
    private static final Pipe.Op RDR_PREP = Pipe.Op.compose(
	new Material(new BaseColor(120, 245, 255, 220), States.maskdepth),
	States.Depthtest.none,
	new States.Facecull(States.Facecull.Mode.NONE),
	new States.LineWidth(1.8f),
	Rendered.last);

    private static final double MAX_LOCAL_EDGE = MCache.tilesz.x * 60.0;

    private Model model;
    private final int sdtSig;
    private final int bboxSig;

    public MoonGobHitboxOl(Owner owner, Coord2d[][] bbox, int sdtSig, int bboxSig) {
	super(owner, null);
	this.sdtSig = sdtSig;
	this.bboxSig = bboxSig;
	this.model = makeModel(bbox);
    }

    public int sdtSignature() {
	return sdtSig;
    }

    public int bboxSignature() {
	return bboxSig;
    }

    static Pipe.Op renderState() {
	return RDR_PREP;
    }

    static Model makeModel(Coord2d[][] bbox) {
	if(bbox == null)
	    return null;
	int nFloats = 0;
	for(Coord2d[] poly : bbox) {
	    if(poly == null || poly.length < 2)
		continue;
	    nFloats += poly.length * 6;
	}
	if(nFloats == 0)
	    return null;
	float[] buf = new float[nFloats];
	int w = 0;
	for(Coord2d[] poly : bbox) {
	    if(poly == null || poly.length < 2)
		continue;
	    int n = poly.length;
	    for(int i = 0; i < n; i++) {
		Coord2d c0 = poly[i], c1 = poly[(i + 1) % n];
		if(c0 == null || c1 == null)
		    continue;
		if(!Double.isFinite(c0.x) || !Double.isFinite(c0.y) || !Double.isFinite(c1.x) || !Double.isFinite(c1.y))
		    continue;
		if(c0.dist(c1) > MAX_LOCAL_EDGE)
		    continue;
		buf[w++] = (float)c0.x;
		buf[w++] = -(float)c0.y;
		buf[w++] = 1.0f;
		buf[w++] = (float)c1.x;
		buf[w++] = -(float)c1.y;
		buf[w++] = 1.0f;
	    }
	}
	if(w == 0)
	    return null;
	if(w < buf.length)
	    buf = Arrays.copyOf(buf, w);
	int nvert = w / 3;
	if(nvert < 2)
	    return null;
	VertexArray.Buffer vdat = new VertexArray.Buffer(LAYOUT.inputs[0].stride * nvert, DataBuffer.Usage.STATIC,
	    DataBuffer.Filler.of(buf));
	VertexArray va = new VertexArray(LAYOUT, vdat);
	return new Model(Model.Mode.LINES, va, null);
    }

    @Override
    public void added(RenderTree.Slot slot) {
	slot.ostate(RDR_PREP);
	if(model != null)
	    slot.add(model);
    }

    @Override
    public void dispose() {
	if(model != null) {
	    model.dispose();
	    model = null;
	}
	super.dispose();
    }
}
