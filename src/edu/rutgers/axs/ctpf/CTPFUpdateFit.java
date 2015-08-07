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


    /** Returns the suffix used to modify data file names, to refer to
	the full data set or the 10K subset, depending on which host
	we are. */
    static private String getSuffix() {
	boolean atHome = Hosts.atHome();
	Logging.info("CTPFUpdateFit: Will use " +
		     (atHome? "the 10K sample" : "the full data set"));	
	final String suffix  = atHome ? "_10K" : "";
	return suffix;
    }

    /** As per skype conversation with LC, 2015-02-04 */
    private static double defaultEpsilonLog() {
	double alpha=0.3, beta=0.3;
	double epsilon_log = Gamma.digamma(alpha) - Math.log(beta);
	return epsilon_log;
    }


    /** @param f Sample count file produced by LDA (ldafit-test.doc.states)
	Each line of the contains the T sample counts for one document.

	@param outDir Directory into which 3 new output files will be written.

	The necessary output files are:
	"theta_log",
	"epsilon_log" = 0, 
	"epsilon_plus_theta" = ?]

	There is no need to write out "theta", but we do, just for review.

     */
    static void convertSampleCounts(File f, File outDir, int topics, double alpha) throws IOException {
	final String suff = "_update.tsv";
	File 
	    thetaFile=new File(outDir, "theta" + suff),
	    thetaLogFile=new File(outDir, "theta_log" + suff),
	    epsLogFile=new File(outDir, "epsilon_log" + suff),
	    epsPlusThetaFile=new File(outDir, "epsilon_plus_theta" + suff);

	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);

	PrintWriter 
	    thetaW = new PrintWriter(new FileWriter(thetaFile)),
	    thetaLogW = new PrintWriter(new FileWriter(thetaLogFile)),
	    epsLogW = new PrintWriter(new FileWriter(epsLogFile)),
	    epsPlusThetaW = new PrintWriter(new FileWriter(epsPlusThetaFile));

	String s = null;
	int lineCnt = 0;

	// epsilon_log stays a constant (not 0, though...)
	final double epsilon_log[] = new double[topics];
	final double defaultEpsilonLog = defaultEpsilonLog();
	for(int i=0; i<topics; i++) { 
	    epsilon_log[i] = defaultEpsilonLog;
	}


	while( (s=r.readLine())!=null) {
	    lineCnt++;
	    String counts[] = s.split("\\s+");
	    if (counts.length != topics) {
		usage("Mismatch in file " + f+", line " + r.getLineNumber() + ": Found " + counts.length + " tokens, expected " + topics);
	    }
	    double c[] = new double[topics];
	    for(int k=0; k<topics; k++) {
		c[k] = Integer.parseInt(counts[k]);
	    }


	    double sum = 0;
	    double[] v1 = new double[topics], v2 = new double[topics];
	    double[] epsilon_plus_theta = new double[topics];
	    for(int k=0; k<topics; k++) {
		// E[theta]
		v1[k] = (alpha + c[k]);
		sum += v1[k];
	    }

	    for(int k=0; k<topics; k++) {
		v1[k]  /= sum;
		// this is the theta_log=E[log(theta) for this doc
		v2[k] = Gamma.digamma(alpha + c[k]) - Gamma.digamma(sum);
		epsilon_plus_theta[k] = v1[k] + 1.0;
	    }

	    writeTsvLine(thetaW, lineCnt, v1);
	    writeTsvLine(thetaLogW, lineCnt, v2);
	    writeTsvLine(epsLogW, lineCnt, epsilon_log);
	    writeTsvLine(epsPlusThetaW,  lineCnt, epsilon_plus_theta);

	}



	r.close();

	thetaW.close();
	thetaLogW.close();
	epsLogW.close();
	epsPlusThetaW.close();

    }


    private static NumberFormat tsvFmt = new DecimalFormat( "0.00000000");

    /** Writes a line of a file such as epsilon_plus_theta.tsv
     */
    private static void writeTsvLine(PrintWriter w, int k, double[] values) {
	w.print("" + k + "\t" + k);
	for(double x: values) {
	    w.print("\t" + tsvFmt.format(x));
	}
	w.println();
    }

    static void usage() {
	usage(null);
    }

    /** Prints a usage message and an optional error message, and exits
	with error code 1.
     */
    static void usage(String msg) {
	System.out.println("Usage:\n");
	System.out.println("To export all new documents for an LDA run:");
	System.out.println(" java [options] CTPFUpdateFit export new");
	System.out.println("To export a number of specified documents for an LDA run:");
	System.out.println(" java [options] CTPFUpdateFit export aids aid1 [aid2 ...]");
	System.out.println("To process the results of an LDA run:");
	System.out.println(" java [options] CTPFUpdateFit post.lda");
	System.out.println("\nOptions for 'export':\n");
	System.out.println(" -Dout=mult.dat -- Output file for 'export'");
	System.out.println(" -DitemsOut=new-items.tsv -- Items list output file for 'export'");
	System.out.println("\nOptions for 'post.lda':\n");
	System.out.println(" -Dtopics=250 -- the number of topics with which LDA has been run");
	System.out.println(" -DitemsNew=new-items.tsv -- the AIDS list file produced with -DitemsOut during export");
	System.out.println(" -Dstates=ldafit-test.doc.states -- the sample count file produced by the LDA run");
	System.out.println(" -DoutDir=/data/arxiv/ctpf/lda.update -- the directory into which theta etc files are to be written");
	System.out.println();
	if (msg!=null) System.out.println(msg);
	System.exit(1);
    }

    static public void main(String[] argv) throws IOException, java.text.ParseException {
	ParseConfig ht = new ParseConfig();      

	// the directory with the data produced by the LDA init run
	String path = ht.getString("ldainit", "/data/arxiv/ctpf/ldainit/");
	File oldFitDir = new File(path);

	File f = new File(oldFitDir, "vocab.dat");
	Vocabulary voc = new Vocabulary(f);

	Vector<String> newAids;

	int ia=0;

	if (ia>=argv.length) usage("No command specified");
	String cmd = argv[ia++];


	if (cmd.equals("export")) { // exporting some docs, to later run LDA on them in test mode
	    if (ia>=argv.length) usage("Command 'export' requires subcommand 'new' or 'aids'");
	    String subcmd = argv[ia++];

	    if (subcmd.equals("aids")) {  
		// Exporting specified documents (as per the list of 
		// AIDs on the command line)
		newAids = new Vector<String>(0);
		for(ArgvIterator it=new ArgvIterator(argv,ia); it.hasNext();){
		    String aid = it.next();
		    newAids.add(aid);
		}
		if (newAids.size()==0) usage("No valid AIDs has been specified on the command line!"); 
		Logging.info("CTPFUpdateFit: Will dump data for " + newAids.size() + " specified docs");
		
	    } else if (subcmd.equals("new")) { 
		// Exporting all docs that are not in the old map.

		final String suffix  = getSuffix();
		File mf = new File(oldFitDir,  "items"+suffix+".tsv.gz");
		CTPFMap map = new CTPFMap(mf, false, false);
		double fraction = ht.getDouble("fraction", 1.0);
		Logging.info("CTPFUpdateFit: Loaded map, size=" + map.size());
		newAids = identifyNewDocs(map, fraction);
	    } else {
		usage("Error: command 'export' must be followed by 'aids' or 'new'!");
		return;
	    }


	    String out = ht.getString("out", "mult.dat");
	    File g = new File(out);

	    String itemsOut = ht.getString("itemsOut", "new-items.tsv");
	    File itemsFile = new File(itemsOut);
	    Logging.info("Writing data to file " + g + ", items list to " + itemsFile);
	    PrintWriter w = new PrintWriter(new FileWriter(g));
	    PrintWriter itemsW = new PrintWriter(new FileWriter(itemsFile));
	    CTPFDocumentExporter.exportAll(voc, newAids,  w, itemsW);
	    w.close();
	    itemsW.close();
	} else if (cmd.equals("post.lda")) { 
	    // Processing the data produced by an LDA run

	    int topics = ht.getInt("topics", 250);

	    String itemsNew = ht.getString("itemsNew", "new-items.tsv");
	    File newItemsFile = new File(itemsNew);
	    Logging.info("Will read new items list from "+newItemsFile);
	    // Assuming that AIDs do not need to be validated
	    // (purely for efficiency's sake)
	    CTPFMap newItemsMap = new CTPFMap(newItemsFile,  true, false);

	    String states = ht.getString("states","ldafit-test.doc.states");
	    File statesFile = new File(states);
	    Logging.info("Will read sample counts from  "+statesFile);

	    String outDirPath = ht.getString("outDir", "/data/arxiv/ctpf/lda.update");
	    File outDir = new File( outDirPath);
	    if (!outDir.exists() || !outDir.isDirectory()) usage("Directory " + outDir + " does not exist");
	    double alpha = ht.getDouble("alpha", 0.01); // same as in LDA app itself
	    Logging.info("Using alpha=" + alpha);
	    convertSampleCounts(statesFile,  outDir, topics, alpha);

	} else {
	    usage("Unknonw command: " + cmd);
	}
    }

}
