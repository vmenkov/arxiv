package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.recommender.*;
import edu.rutgers.axs.ee4.Categorizer;
import edu.rutgers.axs.ee4.DenseDataPoint;

/** An auxiliary application, this tool creates P-vectors describing 
    trivial clusters (one per category). The P-vectors in question
    are simply the centroids of (some sample of) documents from the
    respective categories.

    <p>There is, of course, no need for these P-vectors when
    processing ArXiv articles, since those already have category
    labels in Lucene.  These vectors, however, may be used for
    processing user-uploaded documents ("category-blind" cluster
    assigments) if no "real" clustering scheme has been provided for 
    some categories.
 */
public class TrivialCentroids {
    
    /** Reads some document examples for each category, l1-normalizes
	them, computes the category's centroid, and saves it.

	@param split If true, only use designated training examples to construct the centroid. Otherwise, use all examples available (within the time depth and maxlen specified)
    */
    private static void generateAllCentroids(File maindir, int nYears, boolean split) throws IOException {
	EntityManager em  = Main.getEM();
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );

	Vocabulary voc = Classifier.readVocabulary();
	final int L = voc.L;

	final int maxlen = 10000;
	Date since = SearchResults.daysAgo( nYears * 365);

	Vector<String> allCats = Categories.listAllStorableCats();
	Logging.info("Retrieving up to " +maxlen + " documents in each of the "+allCats.size()+" known categories "+allCats+", indexed within the last " + nYears + " years");

	Categorizer catz = new Categorizer(false);

	DenseDataPoint dummy = DenseDataPoint.allOnes(L);
	final double eps = 1e-6;
	dummy.multiply(eps * 1.0/(double)L);

	for(String cat: allCats) {
	    int skipCnt = 0, reserveCnt=0, usedCnt=0;
	    DenseDataPoint center = new DenseDataPoint(L);
	    String[] cats = {cat};
	    SubjectSearchResults sr=new SubjectSearchResults(searcher, cats, 
							     since, maxlen);
 	    for(ScoreDoc sd: sr.scoreDocs) {
		int docno = sd.doc;
		Document doc = reader.document(docno);	
		Categories.Cat primaryCat = catz.categorize(docno, doc);
		if (primaryCat==null || !primaryCat.fullName().equals(cat)) {
		    skipCnt++;
		    continue;
		}
		if (split && !isTraining(doc)) {
		    reserveCnt++;
		    continue;
		}
		usedCnt++;
		DenseDataPoint dp = Classifier.readArticle(doc, L, voc);
		dp.l1normalize();
		center.add(dp);
	    }
	    if (usedCnt == 0) {
		Logging.error("Not a single good document with primary category " + cat + " was found in the sample! Too bad for this category.");
		continue;
	    }
	    int nnz = center.nonzeroCount();
	    center.multiply( 1.0/usedCnt);
	    // ensure that there are no zero values (to keep logs happy)
	    center.multiply( 1.0 - eps);
	    center.add( dummy );

	    String msg = "Out of the retrieved sample of "+sr.scoreDocs.length+ 
		" docs for category " + cat + ", used " + usedCnt +", discarded " + skipCnt + " (different primary cat),";
	    if (split) msg += " reserved " +reserveCnt+ " for testing.";
	    msg += " |center|_1=" + center.l1norm() + " original nnz=" + nnz;
	    Logging.info(msg);

	    saveTrivialCentroid(maindir, cat, center);
	}
	em.close();
    }


    /** Writes out the specified centroid as a single P-vector into 
	a clustering description file, which thus will describe a 
	trivial (single-cluster) clustering scheme on the category 
	in question.
    */
    static void saveTrivialCentroid(File maindir, String cat, DenseDataPoint center)  throws IOException {
	File outdir = new File(maindir, cat);
	if (!outdir.exists()) {
	    outdir.mkdir();
	}
	// where the file would be when we read it later
	File wouldBe = Files.getDocClusterFile(cat);
	String name = wouldBe.getName();
	File out = new File(outdir, name);
	if (wouldBe.equals(out)) throw new IllegalArgumentException("You are not allowed to write a cluster file to path " + wouldBe +", because that file would be immediately accessed by the application. Use a temporary directory instead!");
	PrintWriter w= new PrintWriter(new FileWriter(out));
	w.print("0");
	center.printFloat(w);
	w.println();
	w.close();   
    }
    /** Auxiliary class for results sorting */
    static private class Pair implements Comparable<Pair> {
	/** Cat id */
	String key;
	/** Doc count */
	int val;
	Pair(String _key, int _val) {
	    key=_key;
	    val=_val;
	}
	/** Used for descending-order sort by stored value */
	public int compareTo(Pair other) {
	    return other.val - val;
	}
    }


    /** Reads some document examples for each category, classifies
	them (by means of category-blind cluster assignment, using
	stored P-vectors for all clusters in all categories), and then
	computes and reports the confusion matrix (which gauges
	how good the classifier was at assigning examples to their
	designated categories).

	@param split If true, only classify designated test
	examples. Otherwise, use all examples available (within the
	time depth and maxlen specified)
    */
    private static void testAllCentroids(int nYears, boolean split) throws IOException {
	EntityManager em  = Main.getEM();
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	// read the general doc cluster list from the database
	EE5DocClass.CidMapper cidMap = new EE5DocClass.CidMapper(em);

	Vocabulary voc = Classifier.readVocabulary();
	final int L = voc.L;

	final int maxlen = 10000;
	Date since = SearchResults.daysAgo( nYears * 365);

	Vector<String> allCats = Categories.listAllStorableCats();
	Logging.info("Retrieving up to " +maxlen + " documents in each of the "+allCats.size()+" known categories "+allCats+", indexed within the last " + nYears + " years");
	// reverse map from the cat name to its sequential number
	HashMap<String,Integer> cat2id = new HashMap<String,Integer>();
	for(int i=0; i<allCats.size(); i++) {
	    cat2id.put(allCats.elementAt(i), i);
	}

	Categorizer catz = new Categorizer(false);

	for(String cat: allCats) {
	    int skipCnt = 0, reserveCnt=0;

	    String[] cats = {cat};
	    SubjectSearchResults sr=new SubjectSearchResults(searcher, cats, 
							     since, maxlen);
 
	    Vector<ScoreDoc> testableSd = new Vector<ScoreDoc>();

	    for(ScoreDoc sd: sr.scoreDocs) {
		int docno = sd.doc;
		Document doc = reader.document(docno);	
		Categories.Cat primaryCat = catz.categorize(docno, doc);
		if (primaryCat==null || !primaryCat.fullName().equals(cat)) {
		    skipCnt++;
		    continue;
		}
		if (split && !isTest(doc)) {
		    reserveCnt++;
		    continue;
		}
		testableSd.add(sd);
	    }
	    int usedCnt=testableSd.size();
	    if (usedCnt == 0) {
		Logging.error("Not a single good document with primary category " + cat + " was found in the sample! Too bad for this category.");
		continue;
	    }

	    String msg1 = "Out of " + sr.scoreDocs.length + " retrieved docs for category " + cat +", skipping " + skipCnt + " docs where it is not the primary cat, setting aside " + reserveCnt + " docs that were used in training, and classifiying " + usedCnt + " docs";
	    Logging.info(msg1);

	    ScoreDoc[] scoreDocs = (ScoreDoc[])testableSd.toArray(new ScoreDoc[usedCnt]);
	    Classifier.CategoryAssignmentReport report =
		Classifier.classifyNewDocsCategoryBlind(em, reader, scoreDocs,
							cidMap, false, null);
	    int mT[]=report.mT;

	    // compute per-category totals
	    Pair[] catAndCnt = new Pair[allCats.size()];
	    for(int i=0; i<allCats.size(); i++) {
		catAndCnt[i] = new Pair(allCats.elementAt(i), 0);
	    }
	    for(int i=0; i<mT.length; i++) {
		if (mT[i]==0) continue;
		String mcat = cidMap.id2dc.get(i).getCategory();
		Integer id = cat2id.get(mcat);
		catAndCnt[id.intValue()].val += mT[i];
	    }

	    Arrays.sort(catAndCnt);
	    // print a confusion matrix of sorts
	    StringBuffer msg = new StringBuffer("Testing " +cat + " : " + scoreDocs.length + " =");
	    for(int i=0; i<catAndCnt.length && catAndCnt[i].val>0; i++) {
		Pair p = catAndCnt[i];
		msg.append( i>0? " + ":" ");
		msg.append( p.val + " ("+ p.key );
		if (i==0) msg.append( " : " + (float)p.val/(float)usedCnt);
		msg.append(")");
	    }
	    
	    System.out.println(msg);
	    int anyCnt = report.anyCatMatchCnt;
	    System.out.println("For cat=" + cat + ", " + anyCnt + " ("+(float)anyCnt /(float)usedCnt+") assigned to 'correct' primary or secondary cat");
	}
	em.close();
    }


    static boolean isTraining(Document doc) {
	String s = doc.get(ArxivFields.PAPER);
	if (s==null) s = doc.get(ArxivFields.UPLOAD_FILE);
	if (s==null) throw new IllegalArgumentException("Document " + doc + " does not have either of the fields " + ArxivFields.PAPER + " or " + ArxivFields.UPLOAD_FILE+"!");
	int hash = s.hashCode();
	return (hash % 2 == 0); 			 
    }

    static boolean isTest(Document doc) {
	return !isTraining(doc);
    }

    static void usage() {
	System.out.println("Usage:\n");
	System.out.println("  java TrivialCentroids init main_outdir");
	System.out.println("  java TrivialCentroids initsplit main_outdir");
	System.out.println("  java TrivialCentroids test main_indir");
	System.out.println("  java TrivialCentroids testsplit main_indir");
	System.exit(1);
   }

    private static boolean needSplit(String cmd) {
	return cmd.endsWith("split");
    }

    static public void main(String[] argv) throws IOException {
	if (argv.length<1) {
	    usage();
	}
	ParseConfig ht = new ParseConfig();


	final int nYears =ht.getOption("years", 3);
	Logging.info("Time horizon=" + nYears + " years");

	int ja=0;
	String cmd = argv[ja++];
	boolean split = needSplit(cmd);
	Logging.info("Split mode = " +split);

	if (cmd.equals("init") ||cmd.equals("initsplit")) {
	    if (ja>=argv.length) usage();
	    File maindir = new File(argv[ja++]);
	    if (!maindir.isDirectory()) {
		throw new IOException("Directory " +maindir+ " does not exist");
	    }	    
	    Logging.info("Will write output files into category-specific sudirectories of " + maindir);
	    generateAllCentroids(maindir, nYears, split);
	} else	if (cmd.equals("test") || cmd.equals("testsplit")) {

	    if (ja<argv.length) {
		File maindir = new File(argv[ja++]);
		if (!maindir.isDirectory()) {
		    throw new IOException("Directory " +maindir+ " does not exist");
		}	    
		Logging.info("Setting EE5 basedir=" + maindir);
		Files.setBasedir(maindir.getPath()); 
	    }

	    testAllCentroids(nYears, split);
	} else {
	    usage();
	}

    }

}