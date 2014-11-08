package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

/** A simple progress indicator, for displaying in updatable pages */
public class ProgressIndicator {
    private int k, n;
    final int L = 400;
    public ProgressIndicator(int _n) {
	n = _n;
	k = 0;
    }
    public void setK(int _k) {
	k = _k;
    }
    public void addK(int x) {
	k += x;
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
	b.append("<td colspan=1 bgcolor=\"#ffffff\">"+ k +" link"+(k>1?"s":"")+
		 " out of "+ n +  "</td>");
	b.append("<tr>\n");
	b.append("</table>\n");
	return b.toString();
    }
}