package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.search.Queries;
import edu.rutgers.axs.ee4.Categorizer;
import edu.rutgers.axs.ee4.DenseDataPoint;

/** Document exporter (for Chen Bangrui in Frazier's team,
    2015-09). Exports documents described as vectors in a
    low-dimensional space (words having been mapped to word clusters).
 */
public class LowDimDocumentExporter {

    static void usage() {
	System.out.println("Usage:\n");
	System.out.println("To compute DF for all word clusters on the entire corpus (expensive)");
	System.out.println(" java  LowDimDocumentExporter df");
	System.out.println("To export specifed docs");
	System.out.println(" java  LowDimDocumentExporter aids id1 [id2 id3 ...]");
	System.out.println(" cat aid-list-file | java LowDimDocumentExporter aids -");
	System.out.println("To export docs from a cat");
	System.out.println(" java LowDimDocumentExporter cat [physics 2013]");
	System.out.println(" java [-Dfrom=2013-01-02 -Dto=2014-01-01] LowDimDocumentExporter cat [physics]");

	System.exit(1);
    }


    /** Computes document frequency for all word clusters on the entire corpus
	of ArXiv articles currently stored in our Lucene datastore. This is 
	a pretty expensive procedure, since the entire Lucene index
	(full stored texts, too!) needs to be read; it does it at the rate of about
	10,000-30,000 docs per minute on my laptop.

	<p>Paramters maxn (if maxn&gt;0) and fraction (if fraction&lt;0)
	can be used to only analyze some articles from the corpus, as a
	quick sample.

	@param maxn If maxn&ne;0, it is used as the restriction to the
	total number of articles. This is used as a not very good of
	quick sampling.

	@fraction If fraction &lt; 1.0, it is used to only select some
	percentage of articles for analysis. This is used for quick
	sampling.
     */
    static private void doAllDf(IndexSearcher searcher, Vocabulary voc, int maxn, double fraction) throws IOException {

	if (fraction <= 0 || fraction >1.0) throw new IllegalArgumentException("Illegal value of fraction=" + fraction +". The value must be in the range 0.0 < fraction <= 1.0"); 

	Date since = null; 
	ScoreDoc[] sd = Daily.getRecentArticles( searcher, since, null);
	String msg="Found " + sd.length + " articles in the index.";
	if (fraction < 1.0) {
	    msg += " Will only take sample of " + fraction + " of the index.";
	}
	if (maxn>0 &&  maxn < sd.length) {
	    msg += " Will analyze no more than  "+maxn+" articles.";
	}
	Logging.info(msg);

	int df[] = new int[voc.L];
	int doneCnt=0;
	for(int i=0; i<sd.length; i++) {
	    if (doneCnt >= fraction*(i+1)) continue;

	    DenseDataPoint p = ArticleDenseDataPoint.readArticle(sd[i].doc,  voc, searcher.getIndexReader());
	    for(int k=0; k<voc.L; k++) {
		if (p.elementAt(k)>0) df[k]++;
	    }
	    doneCnt++;
	    if (doneCnt% 50000==0) Logging.info("Done " + doneCnt + " docs");
	    if (maxn>0 && doneCnt>=maxn) break;
	}
	for(int k=0; k<voc.L; k++) {
	    System.out.println("" + k + "\t" + df[k]);
	}
    }

    //static private exportArticle(PrintWriter out);

    /** @param cat A major category (such as "physics")

	@param from Lower bound of the date range (inclusive). May be null, which means open range.
	@param to Upper bound of the date range (exclusive). May be null, which means open range.
    */
    static private void exportByCat(IndexSearcher searcher, Vocabulary voc, String cat, Date from, Date to, PrintWriter out) throws IOException {
	Logging.info("Exporting docs for major cat " + cat + ", date range from " +from+ " to " + to);
	org.apache.lucene.search.Query q= 
	     Queries.mkTermOrPrefixQuery(ArxivFields.CATEGORY, cat + "*");
	if (from != null || to != null) {
	    q = Queries.andQuery( q, Queries.mkDateRangeQuery(from, to));
	}
	Logging.info("Usinig Lucene query: " + q);
	TopDocs top = searcher.search(q, Daily.maxlen);
	ScoreDoc[] sd = top.scoreDocs;
	Logging.info("Found " +sd.length + " possibly matching documents");
	Categorizer catz = new Categorizer(false);
	IndexReader reader = searcher.getIndexReader();
	int usedCnt=0, skipCnt=0;
	for(int i=0; i<sd.length; i++) {
	    int docno = sd[i].doc;
	    Document doc = reader.document(docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    Categories.Cat primary = catz.categorize(docno, doc);
	    if (!cat.equals(primary.getMajor())) {
		skipCnt++;
		continue;
	    } else {
		usedCnt++;
	    }

	    DenseDataPoint p = ArticleDenseDataPoint.readArticle(docno, voc, reader);
	    out.print(aid);
	    p.printAsSparse(out);
	    out.println();
	}
	String msg = "Exported " + usedCnt + " documents";
	if (skipCnt>0) msg += "; ignored " +skipCnt+ " due to imprecise cat match"; 
	Logging.info(msg);
    }

    static public void main(String[] argv) throws IOException, java.text.ParseException {
	ParseConfig ht = new ParseConfig();      

	int ja=0;
	if (ja>= argv.length) usage();
	String cmd = argv[ja++];       

	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );



	String altVoc = ht.getOption("voc",null);
	Vocabulary voc = altVoc==null? Vocabulary.readVocabulary():
	    Vocabulary.readVocabulary(new File(altVoc));

	if (cmd.equals("aids")) { // export specified docs as feature vectors
	    int errcnt = 0;
	    PrintWriter out = new PrintWriter(System.out);
	    for(ArgvIterator it=new ArgvIterator(argv,ja); it.hasNext();){
		String aid = it.next();
		int docno = Common.findOrMinus(searcher, aid);
		if (docno<0) {
		    System.err.println("Cannot find document with aid=" + aid);
		    errcnt++;
		    continue;
		}
		DenseDataPoint p = ArticleDenseDataPoint.readArticle(docno, voc, reader);
		out.print(aid);
		p.printAsSparse(out);
		out.println();
	    }
	    out.flush();
	} else if (cmd.equals("cat")) {
	    Date from=ht.getOptionDate("from",null);
	    Date to=ht.getOptionDate("to",null);
	    if (ja>=argv.length) {
		usage();
	    }
	    String cat = argv[ja++];
	    if (ja<argv.length) {
		final DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
		int year=Integer.parseInt(argv[ja++]);
		from = fmt.parse( "" + year + "-01-01");
		to = fmt.parse( "" + (year+1) + "-01-01");
	    }
	    PrintWriter out = new PrintWriter(System.out);
	    exportByCat( searcher, voc, cat, from, to, out);
	    out.flush();
	} else if (cmd.equals("df")) {
	    // compute document frequency for all word clusters
	    int maxn=0;
	    double fraction = ht.getDouble("fraction", 1.0);
	    if (ja<argv.length) {
		maxn=Integer.parseInt(argv[ja++]);
	    }
	    doAllDf(searcher,voc,maxn, fraction);
	} else {
	    Logging.error("Unknown command: " + cmd);
	    usage();
	}
	reader.close();

    }

    
}
