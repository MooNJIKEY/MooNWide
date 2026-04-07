package haven.discord;

import haven.*;
import java.awt.Color;
import java.awt.Font;

/**
 * In-game MooNWide-styled Discord notification: compact popup and full reader.
 */
public final class MoonDiscordNotifyWnd {
    private static final Color ACCENT = new Color(122, 63, 255);
    private static final Color MUTED = new Color(160, 150, 190);
    private static final Text.Foundry TITLE = new Text.Foundry(Text.sans.deriveFont(Font.BOLD, UI.scale(14f))).aa(true);
    private static final Text.Foundry BODY = new Text.Foundry(Text.sans.deriveFont(UI.scale(12f))).aa(true);

    private MoonDiscordNotifyWnd() {}

    public static void showPopup(GameUI gui, String type, String title, String message) {
	if(gui == null)
	    return;
	String shortBody = message;
	if(shortBody.length() > 100)
	    shortBody = shortBody.substring(0, 97) + "…";
	String cap = "MooNWide";
	Window w = new Window(UI.scale(new Coord(300, 150)), cap, true) {
	    @Override
	    public void wdgmsg(Widget sender, String msg, Object... args) {
		if((sender == this) && msg.equals("close")) {
		    reqdestroy();
		} else {
		    super.wdgmsg(sender, msg, args);
		}
	    }
	};
	Label typeL = w.add(new Label(type != null && !type.isEmpty() ? type : "notice", BODY), UI.scale(10, 10));
	typeL.setcolor(ACCENT);
	Label titleL = w.add(new Label(title != null ? title : "—", TITLE), typeL.pos("bl").adds(0, 6));
	titleL.setcolor(Color.WHITE);
	Label prev = w.add(new Label(shortBody, BODY), titleL.pos("bl").adds(0, 4));
	prev.setcolor(MUTED);
	w.add(new Button(UI.scale(100), "Open", () -> {
	    w.reqdestroy();
	    gui.add(new FullReader(title, message, type), gui.sz.div(2).sub(UI.scale(200, 180)));
	}), prev.pos("bl").adds(0, 10));
	w.pack();
	gui.add(w, gui.sz.div(2).sub(w.sz.div(2)));
	w.show();
    }

    private static class FullReader extends Window {
	FullReader(String title, String message, String type) {
	    super(UI.scale(new Coord(400, 320)), "MooNWide", true);
	    Label t = add(new Label(title != null ? title : "—", TITLE), UI.scale(10, 10));
	    t.setcolor(ACCENT);
	    String head = (type != null && !type.isEmpty()) ? ("[" + type + "]") : "";
	    if(!head.isEmpty()) {
		Label ty = add(new Label(head, BODY), t.pos("bl").adds(0, 4));
		ty.setcolor(MUTED);
		t = ty;
	    }
	    Textlog log = add(new Textlog(UI.scale(new Coord(380, 220))), t.pos("bl").adds(0, 8));
	    for(String line : (message != null ? message : "").split("\n", -1))
		log.append(line.isEmpty() ? " " : line, Color.DARK_GRAY);
	    add(new Button(UI.scale(80), "Close", () -> reqdestroy()), log.pos("bl").adds(0, 8));
	}

	@Override
	public void wdgmsg(Widget sender, String msg, Object... args) {
	    if((sender == this) && msg.equals("close"))
		reqdestroy();
	    else
		super.wdgmsg(sender, msg, args);
	}
    }
}
