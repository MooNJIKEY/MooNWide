package haven;

import java.awt.Color;

/**
 * MooNWide floating panels that match {@link ChatWnd} body opacity ({@code moon-chat-alpha}) and chrome.
 */
public abstract class MoonChatStylePanel extends MoonPanel {

    public MoonChatStylePanel(Coord sz, String prefKey, String title) {
	super(sz, prefKey, title);
	setClosable(true);
	setResizable(true);
    }

    @Override
    protected Color panelBodyColor() {
	int a = Utils.getprefi("moon-chat-alpha", 210);
	return(new Color(18, 12, 30, Math.max(60, Math.min(255, a))));
    }
}
