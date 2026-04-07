package haven.res.gfx.fx.cavewarn;

import haven.Gob;
import haven.MCache;
import haven.Message;
import haven.MoonMineSweeperData;
import haven.Resource;
import haven.render.RenderTree;
import haven.Sprite;

/**
 * Hooks the server-sent cavewarn effect and persists the minesweeper clue number
 * for the tile where the dust spawned.
 */
@haven.FromResource(name = "gfx/fx/cavewarn", version = 8)
public class Cavein extends Sprite implements Sprite.CDel {
    private boolean done = false;

    public Cavein(Owner owner, Resource res, Message sdt) {
        super(owner, res);
        int strength = sdt.uint8();
        rememberClue(owner, Math.max(0, Math.min(8, (int)Math.round(strength / 30.0))));
    }

    private void rememberClue(Owner owner, int clue) {
        Gob gob = null;
        if(owner instanceof Gob)
            gob = (Gob)owner;
        else if(owner instanceof Gob.Overlay)
            gob = ((Gob.Overlay)owner).gob;
        if(gob == null)
            return;
        try {
            MoonMineSweeperData.observeClue(gob.rc.floor(MCache.tilesz), clue);
        } catch(Exception ignored) {
        }
    }

    public boolean tick(double dt) {
        if(done)
            return true;
        done = true;
        return false;
    }

    public void delete() {
        done = true;
    }

    @Override
    public void added(RenderTree.Slot slot) {
        /* Hook-only sprite: no render node, no lighting program setup. */
    }
}
