package haven;

public class MoonLocalizedLabel extends Img {
    private final String assetKey;
    private final String textKey;
    private final int maxW;
    private final int maxH;
    private String lang = MoonL10n.lang();

    public MoonLocalizedLabel(String assetKey, String textKey, int maxW, int maxH) {
	super(MoonUiTheme.labelTex(assetKey, LocalizationManager.tr(textKey), maxW, maxH));
	this.assetKey = assetKey;
	this.textKey = textKey;
	this.maxW = maxW;
	this.maxH = maxH;
    }

    public void tick(double dt) {
	super.tick(dt);
	String cur = MoonL10n.lang();
	if(!cur.equals(lang)) {
	    lang = cur;
	    setimg(MoonUiTheme.labelTex(assetKey, LocalizationManager.tr(textKey), maxW, maxH));
	}
    }
}
