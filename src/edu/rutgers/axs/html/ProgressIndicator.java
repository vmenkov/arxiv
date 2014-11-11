package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

/** A simple progress indicator, for displaying in updatable pages */
public class ProgressIndicator {
    /** Size of the display bar on the screen */
    final int L = 400;
    /** Current progress (k) out of the range (n) */
    protected int k;
    /** The range of this indicator */
    final int n;
    /** This flag controls the text message in the indicator display */
    boolean pct;
    /** @param _n The range of the progress value */
    public ProgressIndicator(int _n, boolean _pct) {
	n = _n;
	k = 0;
	pct = _pct;
    }
    /** @param k Progress indicator position, between 0 and n */
      public void setK(int _k) {
	k = _k;
    }
    public void addK(int x) {
	k += x;
    }

    /** @param r Progress indicator position, between 0.0 and 1.0 */
    public void setKReal(double r) {
	setK((int)(r * n));
    }
 
    public SectionProgressIndicator mkSectionProgressIndicator(double from, double to, int _n) {
	return new SectionProgressIndicator(this,
					    (int)(n*from),
					    (int)(n*to), _n);
    }


    /** Generates a progress bar, using HTML TABLE syntax
	<p>The image came from 
http://www.kathleens-graphics.com/Animated%20Gifs/animals/green%20turtle%20walking.gif
    */
    public String toHTML(String cp) {
	int w1 = (k*L)/n;
	int w2 = L - w1;
	StringBuffer b = new StringBuffer();
	b.append("<table>\n");
	b.append("<tr>\n");
	b.append("<td colspan=1 style=\"width:"+w1+"px\"></td>");
	b.append("<td colspan=2 style=\"width:"+w2+"px\" align=left>");
	b.append("<img src=\""+cp +"/_technical/images/green turtle walking.gif\"></td>");
	b.append("</tr>\n");
	b.append("<tr>");
	b.append("<td colspan=1 bgcolor=\"#101010\" style=\"width:"+w1+"px\"></td>");
	b.append("<td colspan=1 bgcolor=\"#d0d0d0\" style=\"width:"+w2+"px\"></td>");

	String text = pct ?	    (k * 100) / n  + "%" :
	    ""+ k +" link"+(k>1?"s":"")+ " out of "+ n;
	b.append("<td colspan=1 bgcolor=\"#ffffff\">"+ text +  "</td>");
	b.append("<tr>\n");
	b.append("</table>\n");
	return b.toString();
    }
}