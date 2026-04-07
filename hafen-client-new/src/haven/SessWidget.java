/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.net.*;

public class SessWidget extends AWidget {
    private final Defer.Future<Result> conn;
    private boolean rep = false;

    @RName("sess")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    String host = (String)args[0];
	    int port = Utils.iv(args[1]);
	    byte[] cookie = (args[2] instanceof byte[]) ? (byte[])args[2] : Utils.hex.dec((String)args[2]);
	    Object[] sargs = Utils.splice(args, 3);
	    Session.User acct = ui.sess.user.copy().alias(null);
	    ui.msg("Connecting to game (UDP)...");
	    return(new SessWidget(host, port, acct, ui.sess.conn.encrypted(), cookie, sargs));
	}
    }

    static class Result {
	final Session sess;
	final Connection.SessionError error;

	Result(Session sess, Connection.SessionError error) {
	    this.sess = sess;
	    this.error = error;
	}
    }

    public SessWidget(final String addr, final int port, Session.User acct, boolean encrypt, final byte[] cookie, final Object... args) {
	MoonConnectLog.log("play: SessWidget created host=" + addr + " port=" + port + " user=" + acct.name);
	show();
	conn = Defer.later(new Defer.Callable<Result>() {
		public Result call() throws InterruptedException {
	    InetAddress host;
	    try {
		MoonConnectLog.log("play: resolving DNS " + addr);
		host = Utils.hostaddr(addr);
		MoonConnectLog.log("play: resolved " + addr + " -> " + host.getHostAddress());
	    } catch(UnknownHostException e) {
		MoonConnectLog.log("play: DNS failed for " + addr + ": " + e.getMessage());
		return(new Result(null, new Connection.SessionConnError()));
	    }
		    try {
			return(new Result(new Session(new InetSocketAddress(host, port), acct, encrypt, cookie, args), null));
		    } catch(Connection.SessionError err) {
			MoonConnectLog.log("play: Session failed: " + err.getMessage() + " code=" + err.code);
			return(new Result(null, err));
		    }
		}
	    });
    }

    public void tick(double dt) {
	super.tick(dt);
	if(!rep && conn.done()) {
	    rep = true;
	    try {
		Result r = conn.get();
		if(r.error != null) {
		    MoonConnectLog.log("play: reporting error to UI: " + r.error.getMessage() + " code=" + r.error.code);
		    GameUI gui = getparent(GameUI.class);
		    if(gui != null)
			gui.msg(new UI.ErrorMessage(r.error.getMessage()));
		    ui.error(r.error.getMessage());
		} else {
		    MoonConnectLog.log("play: session ready, sent res=0");
		}
		wdgmsg("res", (r.error == null) ? 0 : r.error.code);
	    } catch(Defer.DeferredException e) {
		Throwable c = e.getCause();
		String detail = (c != null && c.getMessage() != null) ? c.getMessage() : String.valueOf(e.getMessage());
		if(detail == null || "null".equals(detail))
		    detail = (c != null) ? c.getClass().getSimpleName() : "Connection failed";
		MoonConnectLog.log("play: deferred failure: " + detail);
		GameUI gui = getparent(GameUI.class);
		if(gui != null)
		    gui.msg(new UI.ErrorMessage(detail));
		ui.error(detail);
		wdgmsg("res", Session.SESSERR_CONN);
	    }
	}
    }

    public void uimsg(String name, Object... args) {
	if(name == "exec") {
	    try {
		Result r = conn.get();
		if(r.sess != null) {
		    ((RemoteUI)ui.rcvr).ret(r.sess);
		} else if(r.error != null) {
		    ui.error(r.error.getMessage());
		}
	    } catch(Defer.DeferredException e) {
		MoonConnectLog.log("play: exec after failure: " + e.getCause());
		ui.error("Connection failed");
	    }
	} else {
	    super.uimsg(name, args);
	}
    }

    public void destroy() {
	super.destroy();
	/* XXX: There's a race condition here, but I admit I'm not
	 * sure what can properly be done about it, and it ought at
	 * least be uncommon. */
	if(conn.done()) {
	    try {
		Result res = conn.get();
		if(res.sess != null)
		    res.sess.close();
	    } catch(Defer.DeferredException e) {
		/* nothing to close */
	    }
	} else {
	    conn.cancel();
	}
    }
}
