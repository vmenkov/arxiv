package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;


import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** This class is responsible for looking up the "category" field
    in the Lucene data store for multiple articles, and keeping
    track of them all.
*/
class Categorizer {
    static final boolean primaryOnly=true;

    /** Are we converting from minor to major categories? */
    final boolean toMajor;
    Categorizer(boolean _toMajor) {
	toMajor = _toMajor;
    }
    
    private static final int NS = 100;
    
    /** The stored integers are Lucene doc ids */
    HashMap<String, Vector<Integer>> catMembers = new 	HashMap<String, Vector<Integer>>();
    Vector<Integer> nocatMembers= new Vector<Integer>();
    int multiplicityCnt[] = new int[NS];
    int cnt=0, unassignedCnt=0;	
    
    
    Categories.Cat categorize(int docno, Document doc) {

	String aid = doc.get(ArxivFields.PAPER);
	String cats = doc.get(ArxivFields.CATEGORY);
	// System.out.println("" + docno + " : " + aid + " : " + cats);
	
	String[] catlist = CatInfo.split(cats);
	
	multiplicityCnt[ Math.min(NS-1, catlist.length)]++;
	
	Integer o = new Integer(docno);
	
	Categories.Cat firstCat = null;
	
	boolean assigned=false;
	for(String cat: catlist) {
	    Categories.Cat c = toMajor?
		Categories.findMajorCat(cat):
		Categories.findActiveCat(cat);
	    if (c!=null) {
		if (firstCat == null) firstCat = c;
		String key = c.fullName();
		Vector<Integer> v = catMembers.get(key);
		if (v==null) catMembers.put(key, v=new Vector<Integer>());
		v.add(o);
		assigned=true;
	    } else {
		System.out.println("No assignment for cat=" + cat);
	    }
	    if (primaryOnly) break; // only the primary category (the one listed first) is analyzed
	}
	if (!assigned) {
	    unassignedCnt++;
	    nocatMembers.add(o);
	}
	cnt++;
	return firstCat;
    }

    String stats() {
	return "Analyzed " + cnt + " articles; identified " +catMembers.size() + " categories. There are " + unassignedCnt + " articles that do not belong to any currently active category.";
    }

    String affiStats() {
	String s= "Category affiliation count for articles:\n";
	for(int i=0; i<multiplicityCnt.length; i++) {
	    if (multiplicityCnt[i]>0) {
		s += "" + i;
		s += (i+1==multiplicityCnt.length? " (or more)": "");
		s += " categories: " + multiplicityCnt[i]+" articles\n";
	    }
	}
	return s;
    }

    String catSizesStats() {
	String s= "Cat sizes:\n";
	for(String c: catMembers.keySet()) {
	    s += c + ": " + catMembers.get(c).size() + "\n";
	}
	return s;
    }
}

