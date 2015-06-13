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

    /** Maps words to 0-based indexes */
    static class Vocabulary {
	private Vector<String> v = new  Vector<String>();
	HashMap<String, Integer> h = new HashMap<String, Integer>();
	String pos2word(int i) { return v.elementAt(i); }
	boolean containsWord(String w) { return h.containsKey(w); }
	int word2pos(String w) { return h.get(w).intValue();}
	Vocabulary(File f)  throws IOException {
	    FileIterator it = new FileIterator(f);	    
	    while(it.hasNext()) {
		String w = it.next();
		v.add(w);
		h.put(w, new Integer(v.size()-1));
	    }
	    it.close();
	}
	int size() { return h.size(); }
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
	    if (aid==null) throw new IllegalArgumentException("Found null in the list of AIDs");
	    if (!map.containsAid(aid)) v.add(aid);
	}
	int n = v.size();
	Logging.info("Identified " +n+ " articles not in the old fit data set");
	if (fraction >=1.0) return v;

	int nc = (int)( n * fraction);
	int[] sample = Util.randomSample(n, nc);
	Vector<String> w = new Vector<String>(nc);
	w.setSize(nc);
	for(int i=0; i<nc; i++) {
	    int pos = sample[i];
	    w.set(i, v.elementAt(pos));
	}
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

	Vector<String> newAids;

	int ia=0;
	if (ia<argv.length && argv[ia].equals("aids")) {
	    newAids = new Vector<String>(0);
	    // Dump the data for some specific AIDs
	    for(ArgvIterator it=new ArgvIterator(argv,ia+1); it.hasNext();){
		String aid = it.next();
		newAids.add(aid);
	    }
	    Logging.info("CTPFUpdateFit: Will dump data for " + newAids.size() + " specified docs");
	} else {
	    // just find some or all docs that are not in the old map
	    // Modifies data file names, to refer to the full data set or the 10K subset 
	    boolean atHome = Hosts.atHome();
	    Logging.info("CTPFUpdateFit: Will use " +
			 (atHome? "the 10K sample" : "the full data set"));

	    final String suffix  = atHome ? "_10K" : "";
	    File mf = new File(oldFitDir,  "items"+suffix+".tsv.gz");
	    CTPFMap map = new CTPFMap(mf, -1);
	    double fraction = ht.getDouble("fraction", 1.0);
	    Logging.info("CTPFUpdateFit: Loaded map, size=" + map.size());
	    newAids = identifyNewDocs(map, fraction);
	}

	File g = new File("mult.dat");
	File itemsFile = new File("new-items.tsv");
	Logging.info("Writing to file " + g);
	PrintWriter w = new PrintWriter(new FileWriter(g));
	PrintWriter itemsW = new PrintWriter(new FileWriter(itemsFile));
	CTPFDocumentExporter.exportAll(voc, newAids,  w, itemsW);
	w.close();
	itemsW.close();


  }


}
