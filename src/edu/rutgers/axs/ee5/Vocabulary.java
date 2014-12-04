package edu.rutgers.axs.ee5;

/*
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
*/

import java.util.*;
import java.io.*;

/*
import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.recommender.*;
*/

/** Multiword vocabulary, as prepared by Alex Alemi */

class Vocabulary {

    /** The number of word clusters */
    final int L;

    static class Multiword {
	/** Sequence of 1 or more words forming this "multiword" */
	String [] seq;
	/** The ID of the word cluster to which this Multiword is
	    assigned */
	int wcid;
	Multiword(String [] _seq, int _wcid) {
	    seq = _seq;
	    wcid = _wcid;
	}
	int length() { return seq.length; }

	/** Does this multiword matches the same-length 
	    subsequence starting at text[start]?
	 */
	boolean matches(String [] text, int start) {
	    if (start + seq.length > text.length) return false;
	    for(int j=0; j<seq.length; j++) {
		if (!seq[j].equals(text[start+j]))  return false;
	    }
	    return true;
	}
    }

    final private Vector<Multiword> multiwords;
    private HashMap<String, Vector<Multiword>> firstWordToMultiwords;

    Vocabulary(File f, int _L) throws IOException {
	L = _L;
	multiwords = new Vector<Multiword>();
	firstWordToMultiwords =new HashMap<String,Vector<Multiword>>();
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
 	String s;
	int linecnt = 0, cnt=0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;	    
	    String q[] = s.split("\\s+");
	    if (q==null || q.length != 1) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f + " : " + s);
	    }

	    q = s.split(",");
	    if (q==null || q.length < 2) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f + " : " + s);
	    }

	    int wcid = Integer.parseInt(q[1]);

	    String words[] = q[0].split("_");
	    if (words==null || words.length < 1) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f + " : " + s);
	    } 
	    Multiword m = new Multiword(words,wcid);
	    multiwords.add(m);
	    String key = words[0];
	    Vector<Multiword> z = firstWordToMultiwords.get(key);
	    if (z==null) {
		firstWordToMultiwords.put(key,z=new Vector<Multiword>());
	    }
	    z.add(m);	    
	}
	r.close();
    }
    
    /** Finds the longest multiword (from our list of multiwors)
	that starts at text[start];
	@return the longest matching Multiword, or null if none
	matches
     */
    Multiword findLongestMatch(String [] text, int start) {
	Vector<Multiword> q = firstWordToMultiwords.get(text[start]);
	if (q==null) return null;
	int bestlen = 0;
	Multiword best = null;
	for(Multiword m: q) {
	    if (m.length() > bestlen && m.matches(text,start)) {
		best = m;
		bestlen = m.length();
	    }
	}
	return best;
    }

    double[] textToVector(String text, double[] v) {
	if (v==null) v = new double[L];
	return v;
    }

}
