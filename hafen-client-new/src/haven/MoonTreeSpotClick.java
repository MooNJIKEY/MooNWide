package haven;

import java.awt.Color;
import java.util.Locale;

/**
 * Shift-Alt-LMB on terrain: append a tree-bot stand waypoint.
 * {@link MoonTreeChopBot} walks stand points in order before work when spots are set.
 */
public final class MoonTreeSpotClick {
    private MoonTreeSpotClick() {}

    public static boolean tryConsume(UI ui, Coord2d world, int button, int modflags) {
	if(ui == null || world == null)
	    return false;
	if(button != 1)
	    return false;
	if((modflags & (UI.MOD_META | UI.MOD_SHIFT)) != (UI.MOD_META | UI.MOD_SHIFT))
	    return false;
	String line = String.format(Locale.US, "%.3f %.3f", world.x, world.y);
	MoonConfig.appendTreeBotSpot(line);
	MoonTreeChopBot.onSpotsEdited();
	GameUI gui = ui.root.findchild(GameUI.class);
	if(gui != null && gui.moonTreeBotWnd != null)
	    gui.moonTreeBotWnd.refreshSpotsUi();
	int n = MoonConfig.treeBotSpotCount();
	ui.msg(String.format(LocalizationManager.tr("treebot.spot.added"), n, line), Color.WHITE, null);
	return true;
    }
}
