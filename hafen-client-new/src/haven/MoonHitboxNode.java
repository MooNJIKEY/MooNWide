package haven;

import haven.render.Model;
import haven.render.Pipe;
import haven.render.RenderTree;
import haven.render.TickList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Detached local-space wireframe node. Used for world gobs and carried terobjs so hitboxes survive
 * mesh hiding and no longer depend on screen-space projection.
 */
public final class MoonHitboxNode implements RenderTree.Node, TickList.TickNode, TickList.Ticking, Disposable {
    private final Model model;
    private final Supplier<? extends Pipe.Op> state;
    private final Collection<RenderTree.Slot> slots = new ArrayList<>(1);
    private Pipe.Op curstate;

    public MoonHitboxNode(Coord2d[][] bbox, Supplier<? extends Pipe.Op> state) {
	this.model = MoonGobHitboxOl.makeModel(bbox);
	this.state = state;
    }

    public boolean valid() {
	return model != null;
    }

    private Pipe.Op state() {
	return state.get();
    }

    private void update() {
	try {
	    Pipe.Op next = state();
	    if(Utils.eq(next, curstate))
		return;
	    for(RenderTree.Slot slot : slots)
		slot.ostate(next);
	    curstate = next;
	} catch(Loading ignored) {
	}
    }

    @Override
    public void added(RenderTree.Slot slot) {
	if(model == null)
	    return;
	if(curstate == null)
	    curstate = state();
	slot.ostate(curstate);
	slot.add(model);
	slots.add(slot);
    }

    @Override
    public void removed(RenderTree.Slot slot) {
	slots.remove(slot);
    }

    @Override
    public TickList.Ticking ticker() {
	return this;
    }

    @Override
    public void autotick(double dt) {
	update();
    }

    @Override
    public void dispose() {
	if(model != null)
	    model.dispose();
    }
}
