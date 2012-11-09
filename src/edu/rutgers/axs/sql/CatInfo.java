package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;

/** Category matcher tool */
public class CatInfo {
   
    /** If true, the match is in terms of major categories ("cs") rather
	than minor ones ("cs.ai").
    */
    private boolean toMajor;
    private Vector<String> bases;

    public CatInfo(String cats, boolean _toMajor) {
	this(split(cats),  _toMajor);
    }

    public CatInfo(String cats[], boolean _toMajor) {
	toMajor  = _toMajor;
	bases = new Vector<String>(cats.length);
	for(String x: cats) {		
	    String z = toMajor? catBase(x) :  x;
	    if (!dup(bases,z)) bases.add(z);
	}
    }

    public String toString() {
	String s="";
	for(String q: bases) {
	    s += (s.length()==0 ? "(" : ", ") + q;
	}
	return s+")";
    }

    /** Looks for an overlap of two lists */
    public boolean match(String otherCats) {	    
	String [] other =  toMajor?catBases(otherCats): split(otherCats);
	for(String a: other) {
	    if (dup(bases,a)) return true;
	}
	return false;
    }

    private static 	String[] split(String cats) {
	return (cats==null)  ? new String[0] : cats.split("\\s+");
    }

    /** Creates an array of (unique) category prefixes */
    private static Vector<String> catBases(String cats) {
	String[] x = split(cats);
	Vector<String> vb = new  Vector<String>(x.length);
	for(String c: x) {
	    String b = catBase(c);
	    if (!dup(vb,b)) vb.add(b);
	}
	return vb;
    }
    
    /** If b contained in vb? */
    private static boolean dup(Vector<String> vb, String b) {
	for(String z: vb) {
	    if (b.equals(z)) return true;
	}
	return false;	
    }

    /** converts "cat.subcat" to "cat"
     */
    private static String catBase(String cat) {
	if (cat==null) return null;
	int p = cat.indexOf(".");
	return (p<0)? cat: cat.substring(0, p);
    }
}
 

