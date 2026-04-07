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

package haven.resutil;

import haven.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class Curiosity extends ItemInfo.Tip implements GItem.ColorInfo {
    public final Color better = new Color(0, 255, 0, 64), worse = new Color(255, 0, 0, 64);
    public final int exp, mw, enc, time;
    public final UI ui;

    public Curiosity(Owner owner, int exp, int mw, int enc, int time) {
	super(owner);
	this.exp = exp;
	this.mw = mw;
	this.enc = enc;
	this.time = time;
	UI ui = null;
	if(owner instanceof Widget) {
	    Widget wdg = (Widget)owner;
	    if(wdg.getparent(CharWnd.class) != null)
		ui = wdg.ui;
	}
	this.ui = ui;
    }

    static String u(int i) {
	return(LocalizationManager.tr(new String[] {"curiosity.unit.s", "curiosity.unit.m", "curiosity.unit.h", "curiosity.unit.d"}[i]));
    }
    static int[] div = {60, 60, 24};
    static String timefmt(int time) {
	final int n = div.length + 1;
	int[] vals = new int[n];
	vals[0] = time;
	for(int i = 0; i < div.length; i++) {
	    vals[i + 1] = vals[i] / div[i];
	    vals[i] = vals[i] % div[i];
	}
	StringBuilder buf = new StringBuilder();
	for(int i = n - 1; i >= 0; i--) {
	    if(vals[i] > 0) {
		if(buf.length() > 0)
		    buf.append(' ');
		buf.append(vals[i]);
		buf.append(u(i));
	    }
	}
	return(buf.toString());
    }

    public BufferedImage tipimg() {
	StringBuilder buf = new StringBuilder();
	if(exp > 0)
	    buf.append(String.format(LocalizationManager.tr("curiosity.lp"), Utils.thformat(exp), Utils.thformat(Math.round(exp / (time / 3600.0)))));
	if(time > 0)
	    buf.append(String.format(LocalizationManager.tr("curiosity.time"), timefmt(time)));
	if(mw > 0)
	    buf.append(String.format(LocalizationManager.tr("curiosity.mw"), mw));
	if(enc > 0)
	    buf.append(String.format(LocalizationManager.tr("curiosity.enc"), enc));
	return(RichText.render(buf.toString(), 0).img);
    }

    public Color olcol() {
	Object tip = (ui == null) ? null : ui.lasttip;
	if(tip instanceof ItemInfo.InfoTip) {
	    Curiosity that = ItemInfo.find(Curiosity.class, ((ItemInfo.InfoTip)tip).info());
	    if(that != null) {
		double crate = (double)that.exp / (double)that.time;
		double trate = (double)this.exp / (double)this.time;
		if(Debug.ff)
		    Debug.dump(trate, crate);
		double ε = 0.5 / 3600.0;
		if(trate < crate - ε)
		    return(worse);
		if(trate > crate + ε)
		    return(better);
	    }
	}
	return(null);
    }
}
