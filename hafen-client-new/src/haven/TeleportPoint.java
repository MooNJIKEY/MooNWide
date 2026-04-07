package haven;

/**
 * Saved navigation target for {@link TeleportManager} (world plane x/y, label, category).
 */
public class TeleportPoint {
    public static final String CAT_HOME = "home";
    public static final String CAT_RESOURCE = "resource";
    public static final String CAT_FRIEND = "friend";
    public static final String CAT_OTHER = "other";
    public static final long NO_SEG = -1L;

    public String name;
    public double x, y;
    public String category;
    public long lastUsed;
    public long mapSeg;
    public int mapTcX;
    public int mapTcY;
    private transient String cachedLabelText;
    private transient Text cachedLabel;

    public TeleportPoint(String name, double x, double y, String category) {
	this(name, x, y, category, NO_SEG, 0, 0);
    }

    public TeleportPoint(String name, double x, double y, String category, long mapSeg, int mapTcX, int mapTcY) {
	this.name = name != null ? name : "";
	this.x = x;
	this.y = y;
	this.category = normalizeCategory(category);
	this.lastUsed = 0L;
	this.mapSeg = mapSeg;
	this.mapTcX = mapTcX;
	this.mapTcY = mapTcY;
    }

    public static String normalizeCategory(String c) {
	if(c == null || c.isEmpty())
	    return CAT_OTHER;
	if(CAT_HOME.equals(c) || CAT_RESOURCE.equals(c) || CAT_FRIEND.equals(c) || CAT_OTHER.equals(c))
	    return c;
	return CAT_OTHER;
    }

    public Coord2d pos() {
	return new Coord2d(x, y);
    }

    public boolean hasMapLocation() {
	return mapSeg != NO_SEG;
    }

    public Coord mapTc() {
	return Coord.of(mapTcX, mapTcY);
    }

    public void setMapLocation(long seg, Coord tc) {
	this.mapSeg = seg;
	if(tc != null) {
	    this.mapTcX = tc.x;
	    this.mapTcY = tc.y;
	}
    }

    public Tex labelTex() {
	String cur = (name != null) ? name : "";
	if((cachedLabel == null) || !cur.equals(cachedLabelText)) {
	    if(cachedLabel != null)
		cachedLabel.dispose();
	    cachedLabelText = cur;
	    cachedLabel = Text.render(cur);
	}
	return cachedLabel.tex();
    }

    static String jsonEscape(String s) {
	if(s == null)
	    return "";
	StringBuilder sb = new StringBuilder(s.length() + 8);
	for(int i = 0; i < s.length(); i++) {
	    char c = s.charAt(i);
	    switch(c) {
	    case '\\':
		sb.append("\\\\");
		break;
	    case '"':
		sb.append("\\\"");
		break;
	    case '\n':
		sb.append("\\n");
		break;
	    case '\r':
		sb.append("\\r");
		break;
	    case '\t':
		sb.append("\\t");
		break;
	    default:
		if(c < 0x20)
		    sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
		else
		    sb.append(c);
		break;
	    }
	}
	return sb.toString();
    }
}
