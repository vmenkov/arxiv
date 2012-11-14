package edu.rutgers.axs.bernoulli;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.indexer.ArxivFields;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.SearchResults;
import edu.rutgers.axs.web.SubjectSearchResults;
import edu.rutgers.axs.web.ArticleEntry;

import edu.rutgers.axs.recommender.UserProfile;
import edu.rutgers.axs.recommender.Stoplist;
import edu.rutgers.axs.recommender.ArticleAnalyzer;

/** Methods used in the Exploration Engine */
public class Bernoulli {
    /** The only field used for doc sim, as per ver. 3 (2012-05) writeup */
    static final String field =  ArxivFields.ABSTRACT;

    /** FIXME: when multiple clusters are introduced, all methods that
	currently simply update various data structures for cluster=defaultCluster
	will have to do it for *each* cluster.
     */
    public static final int defaultCluster = 0;

    /** Time horizon, in days */
    static public final int horizon = 28;

    /** FIXME: this has to be updated once I have good training data */
    static class ClusterStats {
	double b = 0;
	double v = 0.01;
	double gamma= 0.999891;
    }

    static ClusterStats clusterStats = new ClusterStats();

    /** This is a *provisional* method for initializing the "training set"
	(2012-10-04). 	It all will change once we get "real" training data
	from Xiaoting.

	<p>Here we create or overwrite entries for the specified
	documents; we don't <em>delete</em> entries for documents that
	don't appear in the list. If you need to do that, just do
	<tt>delete from BernoulliTrainArticleStats</tt> from the SQL
	console.
     */
    static void initTrainingData(String[] aids )  throws org.apache.lucene.index.CorruptIndexException, IOException {

	EntityManager em  = Main.getEM();
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);

	/** This plays a very auxiliary role; mostly, a cludge to piggyback
	    on AA's definition of IDF */
	//    static 
	ArticleAnalyzer analyzer = new  ArticleAnalyzer(reader, new String[] {field});
	for(String aid: aids) {
	    int docno = ArticleEntry.find(searcher, aid);
	    double norm = computeNorm(analyzer, docno);
	    int cluster = defaultCluster;
	    em.getTransaction().begin();
	    BernoulliTrainArticleStats bas = BernoulliTrainArticleStats.findByAidAndCluster(em, aid, cluster);
	    if (bas == null) {
		bas = new BernoulliTrainArticleStats();
		bas.setCluster(cluster);
		bas.setAid(aid);
	    }
	    bas.setBigR(1);
	    bas.setBigI(1);
	    bas.setNorm(norm);
	    em.persist(bas);
	    em.getTransaction().commit();
	}
    }

    /** FIXME: We'll be in trouble if index optimization happens after this 
	map is created and  document ids change.
	@param cats Restrict to these categories
	@return A map that maps Lucene's doc id to norm.	
     */
    private static HashMap<Integer, BernoulliTrainArticleStats> readTrainData(EntityManager em, IndexSearcher searcher, String[] cats) 
	throws IOException, CorruptIndexException {
	IndexReader reader=searcher.getIndexReader();
	HashMap<Integer, BernoulliTrainArticleStats> h = new  HashMap<Integer, BernoulliTrainArticleStats>();
	CatInfo catInfo = new CatInfo(cats, false);
	List<BernoulliTrainArticleStats> res = BernoulliTrainArticleStats.findAllByCluster(em,  defaultCluster);
	for(BernoulliTrainArticleStats bas: res) {
	    int docno = ArticleEntry.find(searcher, bas.getAid());
	    // verify categories
	    Document doc2 = reader.document(docno);
	    String cat2 =doc2.get(ArxivFields.CATEGORY);
	    boolean catMatch =  catInfo.match(cat2);
	    if (catMatch) {
		//		bas.getNorm(); 
		//		bas.getBigR();
		//		bas.getBigI();
		h.put(new Integer(docno), bas);
	    }
	}
	return h;
    }

    /** Computes the norm of the document as required for the Exploration 
     Engine experiment. Just the abstract is used.
     
     <p>
     FIXME: we'll need a better weighting than idf()
    */
    static double computeNorm(ArticleAnalyzer analyzer, int docno) throws org.apache.lucene.index.CorruptIndexException, IOException {
	IndexReader reader = analyzer.getReader();
	Document doc = reader.document( docno);
	TermFreqVector tfv=reader.getTermFreqVector(docno, field);

	if (tfv==null) return 0;

	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();	    
	double sum = 0;
	for(int i=0; i<terms.length; i++) {
	    if (UserProfile.isUseless(terms[i])) continue;
	    sum += freqs[i] *  freqs[i] * analyzer.idf(terms[i]);
	}
	return Math.sqrt(sum);
    }

    /** Looks for recent articles in the Lucene index. Search restricted by 
	this subproject's categories of interest.
	@param days Time horizon
     */
    public static SearchResults catSearch(IndexSearcher searcher, int days)
     throws IOException  {

	int maxlen = 10000;

	String[] cats = BernoulliArticleStats.cats;
	Date since = SearchResults.daysAgo( days );
	SubjectSearchResults sr = new SubjectSearchResults(searcher, cats, since, maxlen);

	if (sr.scoreDocs.length>=maxlen) {
	    String msg = "Bernoulli cat search: At least, or more than, " + maxlen + " results found; displayed list may be incomplete";
	    Logging.warning(msg);
	    //infomsg += msg + "<br>";
	}
	return sr;
    }

    public static void sort(SearchResults sr,EntityManager em, IndexReader reader, User.Program program, int cluster) throws IOException, CorruptIndexException{
	for(int i=0; i<sr.scoreDocs.length; i++) {
	    ScoreDoc sd = sr.scoreDocs[i];
	    Document doc = reader.document(sd.doc);
	    String aid = doc.get(ArxivFields.PAPER);
	    BernoulliArticleStats bas = BernoulliArticleStats.findByAidAndCluster(em, aid, defaultCluster);
	    sd.score = (float)(program == User.Program.BERNOULLI_EXPLORATION ?
		Gittins.nu( bas.getAlpha(), bas.getBeta(), clusterStats.gamma):
			       bas.getAlpha()/( bas.getAlpha() +  bas.getBeta()));
	}    

	Arrays.sort(sr.scoreDocs, new SearchResults.SDComparator());
    }

    /** Creates a new BernoulliArticleStats object for a new document,
	and initializes its fields based on the object's similarity to
	rated training documents.

	@param bas The object in which this method will set the values

	@return true on succes, false on failure
     */
    static boolean fillBernoulliArticleStats(EntityManager em, ArticleAnalyzer analyzer, 
				  HashMap<Integer, BernoulliTrainArticleStats> trainData,
				  int docno, 
				  BernoulliArticleStats   bas
				  )  throws org.apache.lucene.index.CorruptIndexException, IOException {

	double norm = computeNorm(analyzer, docno);
	bas.setNorm(norm);

	CatInfo catInfo = new CatInfo( BernoulliArticleStats.cats, false);
	IndexReader reader = analyzer.getReader();

	TermFreqVector tfv=reader.getTermFreqVector(docno, field);

	if (tfv==null) return false;

	int numdocs = reader.numDocs(), maxdoc=reader.maxDoc();
	double scores[] = new double[maxdoc];	

	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();	    
	for(int i=0; i<terms.length; i++) {
	    Term term = new Term(field, terms[i]);
	    TermDocs td = reader.termDocs(term);
	    double normFactor = analyzer.idf(terms[i]);
	    double qval =  normFactor;

	    td.seek(term);
	    while(td.next()) {
		int p = td.doc();
		int freq = td.freq();			
		double z =qval * freq;
		scores[p] += z;		
	    }
	    td.close();
	}

	double sumR=0, sumI = 0;
	for(int k=0; k<scores.length; k++) {
	    if (scores[k]==0) continue;
	    BernoulliTrainArticleStats tbas = trainData.get(new Integer(k));
	    if (tbas==null) continue;
	    if (tbas.getNorm()==0) {
		Logging.error("Retrieved document with norm=0; aid="+
			      tbas.getAid());
		continue;
	    }
	    double knorm = tbas.getNorm();
	    double sim = scores[k]/(knorm*norm);
	    //	    Logging.info("sim(" + docno+","+k+")=" +  scores[k]+ "/(" + knorm +			 "*" + norm+")=" +sim);
	    if (sim>1) {
		Logging.error("Computed sim("+ docno+","+k+")=" + sim + ">1!");
	    }
	    sumR += sim * tbas.getBigR();
	    sumI += sim * tbas.getBigI();
	}
	if (sumI==0) {
	    Logging.error("sumI=0");
	    return false;
	}
	double pTilde = sumR/sumI;
	bas.setPtilde(sumR/sumI);
	double m = pTilde + clusterStats.b;
	double n = -1 + m*(1-m)/clusterStats.v;
	double alpha = Math.max( m*n, 0.5);
	double beta = Math.max( (1-m)*n, 0.5);
	if (n<0) alpha=beta=0.5;
	Logging.info("for(" + docno+"), pTilde="+pTilde+", alpha=" + alpha +", beta="+beta);

	bas.setAlphaTrain(alpha);
	bas.setBetaTrain(beta);

	bas.setAlpha(alpha);
	bas.setBeta(beta);

	bas.updateStats(em);

	return true;
    }


    /** Scans recently added documents in the relevant categories.
	For each new document, computes its similarities to the
	"training" documents, and all the relevant statistics, and
	puts them into the new document's entry in
	BernoulliArticleStats.
     */
    static void addNewDocuments(boolean forceRewrite) throws IOException {
	EntityManager em  = Main.getEM();
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);
	ArticleAnalyzer analyzer = new  ArticleAnalyzer(reader, new String[] {field});

	HashMap<Integer, BernoulliTrainArticleStats> trainData = 
	    readTrainData(em, searcher, BernoulliArticleStats.cats);

	SearchResults sr = Bernoulli.catSearch(searcher, horizon);   
	Logging.info("Bernoulli: adding new docs: found " + sr.scoreDocs.length + " docs to add");
	for(ScoreDoc q: sr.scoreDocs) {
	    int docno = q.doc;
	    Document doc = reader.document( docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    em.getTransaction().begin();
	    BernoulliArticleStats bas = BernoulliArticleStats.findByAidAndCluster(em, aid, defaultCluster);
	    boolean mustFill = (bas==null || forceRewrite);
	    if (bas == null) {
		bas = new BernoulliArticleStats();
		bas.setCluster(defaultCluster);
		bas.setAid(aid);
	    }

	    if (mustFill) {
		Logging.info("Adding doc no. " + docno +", aid=" + aid);

		if (fillBernoulliArticleStats(em, analyzer, trainData,docno,bas)) {
		    em.persist(bas);
		}
	    } else {
		Logging.info("Skip doc no. " + docno +", aid=" + aid);
	    }
	    em.getTransaction().commit();
	}
	
    }


    static void usage() {
	usage(null);
    }

    static void usage(String m) {
	System.out.println("Bernoulli training set initialization");
	System.out.println("Usage: java [options] Bernoulli datafile");
	//	System.out.println("Options:");
	//	System.out.println(" [-Dtoken=xxx]");

	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }


    /** Initializes training set data in the SQL datase. */
    public static void main(String[] argv) throws org.apache.lucene.index.CorruptIndexException, IOException {
	ParseConfig ht = new ParseConfig();
	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));

	if (argv.length < 1)     usage();
	String cmd = argv[0];

	if (cmd.equals("train")) {
	    if (argv.length != 2)     usage();
	    String f = argv[1];
	    FileReader fr = new FileReader(f);
	    LineNumberReader r =  new LineNumberReader(fr);
	    Vector<String> aids= new 	Vector<String>();
	    String s=null;
	    while((s = r.readLine())!=null) {
		s = s.trim();
		if (s.equals("") || s.startsWith("#")) continue;
		// id : cnt
		String[] q = s.split("\\s*:\\s*");
		aids.add(q[0]);
		//aids.add(s);
	    }

	    System.out.println("Importing data for " + aids.size() + " training docs");
	    initTrainingData( aids.toArray(new String[0])); // IndexSearcher searcher, String aid)
	} else if (cmd.equals("recent")) {
	    addNewDocuments(false);
	} else if (cmd.equals("recentRewrite")) {
	    addNewDocuments(true);
	} else {
	    usage("Unknown commmand '"+cmd+"'");
	}

    }

}