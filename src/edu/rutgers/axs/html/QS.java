package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;
import java.util.regex.*;

//import edu.rutgers.axs.web.*;
//import edu.rutgers.axs.sql.*;


/** Utilities for rewriting query strings */
public class QS {
    
    private String qs;

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

    /** Strips  name=[A-Za-z_0-9]+ from the query string 
     */
    public void strip(String name) {
	Pattern p = Pattern.compile("\\b"+ name + "=\\w+\\b");
	Matcher m = p.matcher(qs);
	if (!m.find()) return;
	String a = qs.substring(0, m.start()), b=qs.substring(m.end());
	if (a.length()>0) {
	    if (!a.endsWith("&")) throw new IllegalArgumentException("Cannot parse query string: " + qs);
	    qs =  a.substring(0,a.length()-1) + b;
	} else if (b.length()>0) {
	    if (!b.startsWith("&")) throw new IllegalArgumentException("Cannot parse query string: " + qs);
	    qs = b.substring(1);
	} else {
	    qs = "";
	}
    }

    /**
       @param A Vector of (name, value) pairs, e.g. such as those produced by actionSource.toQueryPairs()
     */
    public void append( Vector<String[]> pairs) {
	for(String[] p:  pairs) {
	    if (qs.length()>0) qs += "&";
	    qs += p[0] +"=" + p[1];
	}
    }



}
