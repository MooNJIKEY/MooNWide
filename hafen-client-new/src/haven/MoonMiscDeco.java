package haven;

/**
 * Header for server "misc" windows (belt, keyring): Moon styling with a dedicated lock key.
 */
public class MoonMiscDeco extends Window.MinimalDeco {
    private final String id;

    public MoonMiscDeco(String wndid) {
	this.id = (wndid != null && !wndid.isEmpty()) ? wndid : "misc";
    }

    @Override
    protected String lockKey() {
	return "wlock-moonmisc-" + id;
    }
}
