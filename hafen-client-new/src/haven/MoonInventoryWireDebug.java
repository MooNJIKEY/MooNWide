package haven;

import core.Logger;

/** Optional stderr diagnostics for inventory {@code wdgmsg} and bulk replay wire. */
public final class MoonInventoryWireDebug {
    private MoonInventoryWireDebug() {}

    public static void maybeLogItem(String msg, Object... args) {
	if(!MoonConfig.debugInventoryWire)
	    return;
	StringBuilder sb = new StringBuilder(msg);
	for(Object a : args) {
	    sb.append(' ').append(a);
	}
	Logger.log("MoonInvWire", sb.toString());
    }

    public static void maybeLogBulkReplay(String kind, Object[] args) {
	if(!MoonConfig.debugInventoryWire)
	    return;
	StringBuilder sb = new StringBuilder("bulk replay ").append(kind);
	if(args != null) {
	    for(Object a : args) {
		sb.append(' ').append(a);
	    }
	}
	Logger.log("MoonInvWire", sb.toString());
    }

    public static void maybeLogBulkTrace(String msg, Object... args) {
	if(!MoonConfig.debugInventoryWire)
	    return;
	StringBuilder sb = new StringBuilder("bulk ").append(msg);
	for(Object a : args)
	    sb.append(' ').append(a);
	Logger.log("MoonInvWire", sb.toString());
    }
}
