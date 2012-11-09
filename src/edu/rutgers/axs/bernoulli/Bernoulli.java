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

    static final int defaultCluster = 0;

    /** Time horizon, in days */
    static public final int horizon = 28;

    static BernoulliArticleStats  
	initBernoulliArticleStats(
				  ArticleAnalyzer analyzer, 
				  HashMap<Integer, BernoulliTrainArticleStats> trainData,
				  int docno)  throws org.apache.lucene.index.CorruptIndexException, IOException {

	BernoulliArticleStats  bas = new BernoulliArticleStats();
	bas.setCluster(defaultCluster);

	CatInfo catInfo = new CatInfo( BernoulliArticleStats.cats, false);
	IndexReader reader = analyzer.getReader();
	Document doc = reader.document( docno);
	bas.setAid(doc.get(ArxivFields.PAPER));

	TermFreqVector tfv=reader.getTermFreqVector(docno, field);

	if (tfv==null) return;

	int numdocs = reader.numDocs(), maxdoc=reader.maxDoc();
	double scores[] = new double[maxdoc];	

	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();	    
	for(int i=0; i<terms.length; i++) {
	    //int df = totalDF(terms[i]);
	    //if (df <= 1 || UserProfile.isUseless(terms[i])) {
	    //continue; // skip nonce-words and stop words
	    //	}
	    Term term = new Term(field, terms[i]);
	    TermDocs td = reader.termDocs(term);
	    double normFactor = analyzer.idf(terms[i]);
	    double qval =  normFactor * normFactor;

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
	    }
	    double sim = scores[k]/tbas.getNorm();
	    if (sim>1) {
		Logging.error("Computed sim("+ docno+","+k+")=" + sim + ">1!");
	    }
	    sumR += sim * tbas.getBigR();
	    sumI += sim * tbas.getBigI();
	}
	if (sumI==0) {
	    Logging.error("sumI=0");
	    return 0;
	}
	bas.setPtilde(sumR/sumI);



    }

    static Stoplist stoplist = null;
    static {
	try {
	    stoplist = new Stoplist(new File("WEB-INF/stop200.txt"));
	} catch(IOException ex) {
	    System.err.print("Can't read stoplist: " + ex);
	}
    }

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
	    // searcher.getIndexReader();

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
    private static HashMap<Integer, BernoulliTrainArticleStats> readTrainData(EntityManager em, IndexSearcher searcher, String[] cats) {
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



    /** Scans recently added documents in the relevant categories.
	For each new document, computes its similarities to the
	"training" documents, and all the relevant statistics, and
	puts them into the new document's entry in
	BernoulliArticleStats.
     */
    static void addNewDocuments() throws IOException {
	EntityManager em  = Main.getEM();
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);

	HashMap<Integer, BernoulliTrainArticleStats> h = 
	    readTrainData(em, searcher, BernoulliArticleStats.cats);

	/** This plays a very auxiliary role; mostly, a cludge to piggyback
	    on AA's definition of IDF */
	//ArticleAnalyzer analyzer = new  ArticleAnalyzer(reader, new String[] {field});
	SearchResults sr = Bernoulli.catSearch(searcher, horizon);    
	for(ScoreDoc q: sr.scoreDocs) {
	    int docno = q.doc;
	    Document doc = reader.document( docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    em.getTransaction().begin();
	    BernoulliArticleStats bas = BernoulliArticleStats.findByAidAndCluster(em, aid, defaultCluster);
	    if (bas == null) {
		bas = initBernoulliArticleStats();

		em.persist(bas);
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

	if (argv.length != 1)     usage();
	String f = argv[0];

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

    }

}