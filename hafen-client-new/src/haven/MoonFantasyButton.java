package haven;

import java.awt.image.BufferedImage;

/**
 * Rounded “stone” action control using {@link MoonUiTheme#paintFantasyButton} — login, char list, sessions.
 */
public class MoonFantasyButton extends Widget {
    private String label;
    private final Runnable action;
    private boolean hover, pressed, disabled;
    private UI.Grab grab;
    private Tex tex;
    private int minW;

    public void setMinWidth(int w) {
        int nw = Math.max(UI.scale(72), w);
        if(nw == minW)
            return;
        minW = nw;
        rebuild();
    }

    public MoonFantasyButton(int minWidth, String text, Runnable action) {
        super(Coord.z);
        this.minW = Math.max(UI.scale(72), minWidth);
        this.label = text;
        this.action = action;
        rebuild();
    }

    public void setLabel(String s) {
        if(s.equals(label))
            return;
        label = s;
        rebuild();
    }

    public void setDisabled(boolean v) {
        if(disabled == v)
            return;
        disabled = v;
        rebuild();
    }

    private void rebuild() {
        BufferedImage cont = MoonUiTheme.renderThemedText(label);
        int iw = Math.max(minW, cont.getWidth() + UI.scale(20));
        int ih = Math.max(UI.scale(30), cont.getHeight() + UI.scale(10));
        Coord nsz = Coord.of(iw, ih);
        BufferedImage img = TexI.mkbuf(nsz);
        MoonUiTheme.paintFantasyButton(img, nsz, pressed, hover, disabled, cont);
        if(tex != null)
            tex.dispose();
        tex = new TexI(img);
        resize(nsz);
    }

    @Override
    public void draw(GOut g) {
        if(tex != null)
            g.image(tex, Coord.z);
    }

    @Override
    public boolean checkhit(Coord c) {
        return c.isect(Coord.z, sz);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(disabled || ev.b != 1 || !checkhit(ev.c))
            return super.mousedown(ev);
        pressed = true;
        rebuild();
        grab = ui.grabmouse(this);
        ui.sfx(Button.clbtdown.stream());
        return true;
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if(grab != null && ev.b == 1) {
            grab.remove();
            grab = null;
            boolean hit = checkhit(ev.c);
            boolean fire = pressed && hit && !disabled;
            pressed = false;
            rebuild();
            ui.sfx(Button.clbtup.stream());
            if(fire && action != null)
                action.run();
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        boolean h = !disabled && checkhit(ev.c);
        if(h != hover) {
            hover = h;
            rebuild();
        }
        super.mousemove(ev);
    }

    @Override
    public boolean gkeytype(GlobKeyEvent ev) {
        if(!disabled && action != null)
            action.run();
        return true;
    }

    @Override
    public void dispose() {
        if(tex != null)
            tex.dispose();
        super.dispose();
    }
}
