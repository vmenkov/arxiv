package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** This class is responsible for looking up the "category" field
    in the Lucene data store for multiple articles, and keeping
    track of them all. It is used in EE4 and EE5, as a preliminary
    step for the document-cluster assignment.
*/
public class Categorizer {
    static final boolean primaryOnly=true;

    /** Are we converting from minor to major categories? */
    final boolean toMajor;
    /**
       @param _toMajor If this flag is true, the actual category
       stored in the article will be converted to the "major category"
       (e.g. "physics.bio-ph" to "physics"), thus resulting in a
       "coarser" categorization.
     */
    public Categorizer(boolean _toMajor) {
	toMajor = _toMajor;
    }
    
    private static final int NS = 100;
    
    /** Maps full category name to a vector of Lucene doc ids of articles
	that belong to that category    */
    public HashMap<String, Vector<Integer>> catMembers = new HashMap<String, Vector<Integer>>();
    public Vector<Integer> nocatMembers= new Vector<Integer>();
    int multiplicityCnt[] = new int[NS];
    int cnt=0, unassignedCnt=0;	
    
    /** Adds information about one more document to the categorizer 
	@param docno Lucene doc id of an article
	@param doc The actual article, already retrived from Lucene
	@return The primary category (the first cat in the cat list of the doc)
     */
    public Categories.Cat categorize(int docno, Document doc) {

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

	    if (c==null) {
		// check if this is an abolished category
		String newcat = Categories.subsumedBy(cat);
		if (newcat!=null) { 
		    c = toMajor?
			Categories.findMajorCat(newcat):
			Categories.findActiveCat(newcat);
		}
	    }


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

