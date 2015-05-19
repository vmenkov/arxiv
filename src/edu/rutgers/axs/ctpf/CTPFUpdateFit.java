package edu.rutgers.axs.ctpf;

import java.io.*;
import java.lang.Math;
import java.nio.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.util.Random.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.commons.math3.special.Gamma;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.util.Hosts;

/** Partial update of the CTPF Fit data.
 */
public class CTPFUpdateFit {

    /** One-based */
    static class Vocabulary {
	private Vector<String> v = new  Vector<String>();
	HashMap<String, Integer> h = new HashMap<String, Integer>();
	String pos2word(int i) { return v.elementAt(i+1); }
	boolean containsWord(String w) { return h.containsKey(w); }
	int word2pos(String w) { return h.get(w).intValue();}
	Vocabulary(File f)  throws IOException {
	    FileIterator it = new FileIterator(f);	    
	    while(it.hasNext()) {
		String w = it.next();
		v.add(w);
		h.put(w, new Integer(v.size()));
	    }
	    it.close();
	}
    }

    /** Lists all (or some) articles that are in Lucene but not in the old doc 
	map.
	@param What fraction of the articles (&le;1.0) to list.
     */
    private static Vector<String> identifyNewDocs(CTPFMap map, double fraction) throws IOException { 
	IndexList il = new IndexList();
	HashSet<String> allAIDs = il.listAsSet(); // all articles in Lucene
	Vector<String> v = new Vector<String>();
	for(String aid: allAIDs) {
	    if (!map.containsAid(aid)) v.add(aid);
	}
	int n = v.size();
	Logging.info("Identified " +n+ " articles not in the old fit data set");
	if (fraction >=1.0) return v;

	int nc = (int)( n * fraction);
	int[] sample = Util.randomSample(n, nc);
	Vector<String> w = new Vector<String>(nc);
	for(int i=0; i<nc; i++) w.set(i, v.elementAt(i));
	Logging.info("For this run, out of " +n+ " new articles, will only hande " + nc);
	return w;
   }


  static void usage() {
	System.out.println("Usage:\n");
	//	System.out.println(" java Daily [delete|init|update]");
	//	System.out.println(" java Daily classifySome input-file");
	System.exit(1);
   }

  static public void main(String[] argv) throws IOException, java.text.ParseException {
	ParseConfig ht = new ParseConfig();      

	String path = "/data/arxiv/ctpf/ldainit/";
	File oldFitDir = new File(path);

	File f = new File(oldFitDir, "vocab.dat");
	Vocabulary voc = new Vocabulary(f);

	boolean atHome = Hosts.atHome();
	Logging.info("CTPFUpdateFit: Will use " +
			 (atHome? "the 10K sample" : "the full data set"));

	// Modifies data file names, to refer to the full data set or the 10K subset 
	final String suffix  = atHome ? "_10K" : "";

	File mf = new File(oldFitDir,  "items"+suffix+".tsv.gz");
	CTPFMap map = new CTPFMap(mf, -1);
	Logging.info("CTPFUpdateFit: Loaded map, size=" + map.size());

	double fraction = ht.getDouble("fraction", 1.0);
	Vector<String> newAids = identifyNewDocs(map, fraction);

  }


}
