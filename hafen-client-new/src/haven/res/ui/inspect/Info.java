package haven.res.ui.inspect;

import haven.OwnerContext;
import haven.UI;
import haven.Utils;
import haven.Widget;

import java.awt.Color;

public class Info implements UI.Notice {
    public final long gobid;
    public final boolean syn;
    public final String text;

    public Info(long gobid, boolean syn, String text) {
	this.gobid = gobid;
	this.syn = syn;
	this.text = text;
    }

    public static UI.Notice mkmessage(OwnerContext owner, Object... args) {
	long gobid = Utils.uiv(args[0]);
	String text = (String)args[1];
	boolean syn = (args.length > 2) ? Utils.bv(args[2]) : false;
	return new Info(gobid, syn, text);
    }

    public String message() { return text; }
    public Color color() { return Color.WHITE; }

    public boolean handle(Widget w) {
	return false;
    }
}
