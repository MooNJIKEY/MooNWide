package haven;

/**
 * Starts additional game sessions inside the same client, without spawning
 * a second JVM. Uses {@link GLPanel#addParallelSession(UI.Runner)} and the same
 * {@code newui} / {@code Runner.run} flow as {@link MainFrame}.
 */
public final class MoonParallelSessions {
    private MoonParallelSessions() {}

    public static boolean startBlank() {
	Bootstrap.clearPendingReconnectCred();
	return(spawnThread());
    }

    public static boolean startWithSaved(LoginData ld) {
	if(ld == null)
	    return(false);
	Bootstrap.setPendingReconnectCred(new AuthClient.NativeCred(ld.username, ld.password));
	return(spawnThread());
    }

    /**
     * If a session for this saved account is already connected, bring it to the front
     * instead of opening another login.
     */
    public static boolean activateSavedIfOpen(LoginData ld) {
	if(ld == null || GLPanelHolder.instance == null)
	    return(false);
	for(UI u : GLPanelHolder.instance.sessionUIs()) {
	    try {
		if((u.sess != null) && (u.sess.user != null) && ld.username.equals(u.sess.user.name)) {
		    GLPanelHolder.instance.setActiveSession(u);
		    return(true);
		}
	    } catch(Throwable t) {
		/* ignore */
	    }
	}
	return(false);
    }

    private static boolean spawnThread() {
	if(GLPanelHolder.instance == null)
	    return(false);
	new HackThread(MainFrame.havenThreadGroup(), () -> {
	    try {
		runSessionLoop(GLPanelHolder.instance);
	    } catch(InterruptedException e) {
		Thread.currentThread().interrupt();
	    } catch(Throwable t) {
		MoonCrashLog.record(Thread.currentThread(), t);
		new Warning(t, "parallel session").issue();
	    }
	}, "Parallel-session").start();
	return(true);
    }

    private static void runSessionLoop(GLPanel p) throws InterruptedException {
	UI.Runner task = new Bootstrap();
	UI lui = p.addParallelSession(task);
	try {
	    while(task != null) {
		task = task.run(lui);
		if(task == null)
		    break;
		lui = p.newui(task);
	    }
	} finally {
	    if(lui != null)
		p.endParallelSession(lui);
	}
    }
}
