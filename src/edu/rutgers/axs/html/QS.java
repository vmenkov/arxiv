package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Utilities for rewriting query strings */
public class QS {
    
    private String qs;

    public QS() { 
	this("");
    }

    public QS(String s) { 
	qs = s; 
	if (qs==null) qs = "";
    }
    public String toString() { 
	return qs; 
    }

    public void append(String name, String value) {
	qs +=  (qs.equals("") ? "" : "&") + name + "=" + value;
    }
 
    public void append(String name, int value) {
	append(name, "" + value);
    }

    /** Strips  name=[A-Za-z_0-9]+ from the query string 
     */
    public void strip(final String name) {
	String[] z = qs.split("\\&");

	int found = -1;
	for(int i=0; i<z.length; i++) {
	    if (z[i].startsWith( name + "=")) {
		found = i;
		break;
	    }
	}

	if (found<0) return;
	StringBuffer b = new StringBuffer(qs.length());
	for(int i=0; i<z.length; i++) {
	    if (i==found) continue;
	    if (b.length()>0) b.append("&");
	    b.append(z[i]);
	}
	qs = b.toString();
    }

    /**
       @param pairs A Vector of (name, value) pairs, e.g. such as those produced by actionSource.toQueryPairs()
     */
    public void append( Vector<String[]> pairs) {
	for(String[] p:  pairs) {
	    if (qs.length()>0) qs += "&";
	    qs += p[0] +"=" + p[1];
	}
    }



}
