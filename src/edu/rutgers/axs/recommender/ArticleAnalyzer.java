package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;


/** Tools for getting numbers out of the Lucene index. This includes
 * computing/extracting DF (document frequency) on the Lucene index,
 * as well as the tf (term frequency) vector for a document.
 */
public class ArticleAnalyzer {

    public IndexReader reader;
    final String [] fields;
    /** Collection size */
    private int numdocs, maxdoc;
    ArticleAnalyzer() throws IOException {
	this(   Common.newReader(), upFields);
    }
    public ArticleAnalyzer(	IndexReader _reader,String [] _fields ) {
	fields = _fields;
	reader =_reader;

	numdocs = reader.numDocs();
	maxdoc = reader.maxDoc();
	baseBoost= initBoost(fields);
    }

    /** This is on top of the "boost" due to some fields being shorter.
     */
    private double[] baseBoost;

    
    /** Stores DF for terms */
    private HashMap<String, Integer> h = new HashMap<String, Integer>();
    /** Document friequency for a term. When a term occurs in multiple 
	fields of a doc, it is counted multiple times, because it's easier
	to do it this way in Lucene.
    */
    int totalDF(String t) throws IOException {
	Integer val = h.get(t);
	if (val!=null) return val.intValue();
	
	if (h.size()> 1000000) {
	    // FIXME
	    h.clear(); // trying to prevent OutOfMemoryError
	}

        int sum = 0;
	for(String name: fields) {
	    Term term = new Term(name, t);
	    sum += reader.docFreq(term);
	}
	/*
	System.out.println("Term " + t + ": sum =" + sum);
	IndexReader[] surs = reader.getSequentialSubReaders();
	if (surs!=null) {
	    int sum1 = 0;
	    for(IndexReader sur: surs) {
		for(String name: fields) {
		    Term term = new Term(name, t);
		    sum1 += sur.docFreq(term);
		}		
	    }
	    System.out.println("Term " + t + ": sum1=" + sum1 + ", over " + surs.length + " subreaders");
	}
	*/
	h.put(t, new Integer(sum));
	return sum;
    }
    
    /** Computes <em>idf = 1 + log ( numDocs/docFreq+1)</em>, much
	like it is done in Lucene's own searcher as well.
     */
    public double idf(String term) {
	try {
	    return  1+ Math.log(numdocs*fields.length / (1.0 + totalDF(term)));
	} catch(IOException ex) { 
	    // not likely to happen, as df is normally already cached
	    return 1;
	}
    }

    /** Gets a TF vector for a document from the Lucene data store. This is
	used e.g. when initializing and updating user profiles. 
	@param aid Arxiv article ID.
    */
    HashMap<String, Double> getCoef(String aid) throws IOException {
	int docno = -1;
	try {
	    docno = find(aid);
	} catch(Exception ex) {
	    Logging.warning("No document found in Lucene data store for id=" + aid +"; skipping");
	    return new HashMap<String, Double>();
	}
	return getCoef(docno,null);
    }

    /** This flag controls whether TF or sqrt(TF) is used to compute
      dot products and norms of documents. The norms so computed are
      incorporated in the fields' boost factors stored in the
      database, so one should not change this flag without rerunning
      ArticleAnalyzer on the entire corpus...
     */
    static final boolean useSqrt = false;

    /** Computes a (weighted) term frequency vector for a specified document.

	@param docno Lucene's internal integer ID for the document,
	@param as This is an output parameter. If non-null, update
	this object with the feature vector's statistics

	@return The frequency vector, which incorporates boost factors
	for different fields, but no idf.
    */
    public HashMap<String, Double> getCoef(int docno, ArticleStats as) 
	throws IOException {
	boolean mustUpdate = (as!=null);

	//long utc = sur.getUniqueTermCount();
	//System.out.println("subindex has "+utc +" unique terms");
	
	HashMap<String, Double> h = new HashMap<String, Double>();
	final Double zero = new Double(0);

	final int nf =fields.length;
	TermFreqVector [] tfvs = new TermFreqVector[nf];
	int length=0;
	int lengths[] = new int[nf];

	for(int j=0; j<nf;  j++) {	    
	    String name= fields[j];
	    TermFreqVector tfv=reader.getTermFreqVector(docno, name);
	    tfvs[j]=tfv;
	    if (tfv==null) continue;

	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {
		int df = totalDF(terms[i]);
		if (df <= 1 || UserProfile.isUseless(terms[i])) {
		    continue; // skip nonce-words and stop words
		}
		// create a dummy entry for each real word
		h.put(terms[i],zero);
		length += freqs[i];
		lengths[j] += freqs[i];
	    }	
	}

	if (mustUpdate) {
	    as.setLength(length);
	    as.setTermCnt(h.size());
	    as.setNorm(0);
	    for(int j=0; j<nf; j++)  as.setRawBoost(j,0);
	    as.setTime( new Date());
	}

	if (length==0) return h;

	// individual fields' boost factors
	double boost[] = new double[nf];
	for(int j=0; j<nf;  j++) {	
	    boost[j] = (lengths[j]==0) ? 0 : 
		(baseBoost[j] * length) / lengths[j];
	}

	for(int j=0; j<nf;  j++) {	
	    TermFreqVector tfv= tfvs[j];
	    if (tfv==null) continue;

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {
		int df = totalDF(terms[i]);
		
		Double val = h.get( terms[i]);
		// Non-words don't have table entries
		if (val==null) continue;
		double z = useSqrt? Math.sqrt(freqs[i]) : freqs[i];
		z =  val.doubleValue() + z * boost[j];
		h.put( terms[i], new Double(z));
		//Term term = new Term(name, terms[i]);		
		//System.out.println(" " + terms[i] + " : " + freqs[i] + "; df=" +sur.docFreq(term) );
	    }
	}

	if (mustUpdate) { 
	    double norm=tfNorm(h);
	    as.setNorm(norm);
	    for(int j=0; j<nf; j++)  {
		as.setRawBoost(j,boost[j]);
	    }
	}

	//System.out.println("Document info for id=" + id +", doc no.=" + docno + " : " + h.size() + " terms");
	return h;
    }


    /** Computes (u*d)/|d|, where u=user profile (specified by hq),
	d=document(docno) with field boosts, (u*d)=idf-weighted dot
	product, |d|=idf-weighted two-norm of d. 

	@param docno=document position (internal id) in the Lucene index.

	@param as Contains precomputed stats for document(docno), in particular,
	boost factors for the fields of d, and the norm of |d|

	@param hq Represents the user profile vector.
	
     */
    double linSim(int docno, 
		  CompactArticleStatsArray   allStats, 
		  HashMap<String, UserProfile.TwoVal> hq) 
	throws IOException {
	
	double sum=0;
	
	for(int j=0; j<fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) continue;
	    //double boost =  as.getNormalizedBoost(j);
	    double boost =  allStats.getNormalizedBoost(docno,j);

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=hq.get(terms[i]);
		if (q==null) continue;

		double z = useSqrt? Math.sqrt(freqs[i]) : freqs[i];
		sum += z * boost * q.w1 *idf(terms[i]);
	    }
	}
	return sum;
    }

    /** This version of linSim() is used for reporting */
    double linSimReport(int docno, 
		       ArticleStats as, 
		       HashMap<String, UserProfile.TwoVal> hq) 
	throws IOException {
	
	double sum=0;
	
	for(int j=0; j<fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) continue;
	    double boost =  as.getNormalizedBoost(j);
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    

	    double[] products = new double[terms.length];

	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=hq.get(terms[i]);
		if (q==null) continue;

		double z = useSqrt? Math.sqrt(freqs[i]) : freqs[i];
		double s = products[i] = z * boost * q.w1 *idf(terms[i]);
		sum += s;
	    }

	    int[] ind = UserProfile.ScoresComparator.sortIndexesDesc(products);
	    System.out.print("LinSim: Top contributions for "+fields[j]+": (");
	    for(int i=0; i<ind.length && i<10 ; i++) {
		System.out.print(" " + terms[ind[i]] +":"+ products[ind[i]]);
	    }
	    System.out.println(")");

	}
	return sum;
    }

    double logSim(int docno, 
		  CompactArticleStatsArray   allStats, 
		  //ArticleStats as, 
		  HashMap<String, UserProfile.TwoVal> hq) 
	throws IOException {
	
	double sum=0;
	
	for(int j=0; j<fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) continue;

	    //double boost =  allStats.getRawBoost(docno,j);
	    double boost = 1.0;

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=hq.get(terms[i]);
		if (q==null) continue;

//		double z = useSqrt? Math.sqrt(freqs[i]) : freqs[i];
//		double s = products[i] = z * boost * q.w1 *idf(terms[i]);

		double z = Math.log(1.0 + freqs[i]);
		sum += z * boost * q.w1 *idf(terms[i]);
	    }
	}
	return sum;
    }

  double logSimReport(int docno, 
		  ArticleStats as, 
		  HashMap<String, UserProfile.TwoVal> hq) 
	throws IOException {
	
	double sum=0;
	
	for(int j=0; j<fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) continue;

	    //double boost =  allStats.getRawBoost(docno,j);
	    double boost = 1.0;

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    double[] products = new double[terms.length];
	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=hq.get(terms[i]);
		if (q==null) continue;

//		double z = useSqrt? Math.sqrt(freqs[i]) : freqs[i];
//		double s = products[i] = z * boost * q.w1 *idf(terms[i]);

		double z = Math.log(1.0 + freqs[i]);
		double s = products[i] =z * boost * q.w1 *idf(terms[i]);
		sum += s;
	    }

	    int[] ind = UserProfile.ScoresComparator.sortIndexesDesc(products);
	    System.out.print("LogSim: Top contributions for "+fields[j]+": (");
	    for(int i=0; i<ind.length && i<10 ; i++) {
		System.out.print(" " + terms[ind[i]] +":"+ products[ind[i]]);
	    }
	    System.out.println(")");


	}
	return sum;
    }


    /** Computes the idf-weighted 2-norm of a term frequency vector.
     @param h Represents the term frequency vector. */
    public double tfNorm(HashMap<String, Double> h) {
	double sum=0;
	for(String t: h.keySet()) {
	    double q= h.get(t).doubleValue();
	    sum += q*q * idf(t);
	}
	return Math.sqrt(sum);
    }

    /** This is the idf-weighted 2-norm of a vector composed of SQUARE
     ROOTS of term frequencies.
     @param h Represents the term frequency vector. */ 
    double normOfSqrtTf(HashMap<String, Double> h) {
	double sum=0;
	for(String t: h.keySet()) {
	    double q= h.get(t).doubleValue();
	    sum += q * idf(t);
	}
	return Math.sqrt(sum);
    }


     /** Document fields used in creating the user profile */
    public static final String upFields[] =  {
	ArxivFields.TITLE, 
	ArxivFields.AUTHORS, ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE};


    private static double[] initBoost(String [] fields) {
	double q[] = {3, 1, 3, 3};
	HashMap<String,Double> h=new HashMap<String,Double> ();
	for(int i=0; i< upFields.length; i++)  {
	    h.put( upFields[i], new Double(q[i]));
	}

	double baseBoost[] = new double[fields.length];
	for(int i=0; i< fields.length; i++)  {
	    Double d = h.get(fields[i]);
	    if (d==null) {
		throw new IllegalArgumentException("Unexpected field: " + fields[i]);
	    }
	    baseBoost[i] = d.doubleValue();
	}

	//	if (baseBoost.length != fields.length) throw new IllegalArgumentException("Expected 4 fields");
	return baseBoost;
    }
    
 
    /** Scans the entire Lucene index, computing norms and related
	stats for all articles that don't yet have that info stored
	in the database. Does not try to recompute it when ...

	FIXME: Should add re-computing when the document has been updated
	since ArticleStats.getDate().

	@param maxCnt Max number of docs to analyze. If negative, analyze all.
    */
    void computeAllMissingNorms(EntityManager em, int maxCnt, boolean recompute) throws  org.apache.lucene.index.CorruptIndexException, IOException {

	final boolean verbose=false;

	List<ArticleStats> aslist = ArticleStats.getAll( em);
	HashMap<String, ArticleStats> h=new HashMap<String, ArticleStats>();
	for(ArticleStats as: aslist) {
	    h.put(as.getAid(), as);
	}
	    
	numdocs = reader.numDocs();
	int doneCnt=0, skipCnt=0;
	Logging.info("Analyzer starting. The reader has "+numdocs+" documents");

	for(int docno=0; docno<maxdoc; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    ArticleStats as =h.get(aid);
	    if (as!=null) {
		if (recompute) {
		    Logging.info("Will re-do document " + aid + " (forced), pos="+docno);
		} else if (as.mayBeOutOfDate(doc)) {
		    // checked dates, to see if we must update
		    Logging.info("Will re-do document " + aid + " (out of date), pos="+docno);
		    // update the date on the existing document
		    as.setTime(new Date());
		} else {
		    if (verbose) Logging.info("Already have  document " + aid + ", pos="+docno);
		    skipCnt++;
		    continue;
		}
	    } else {
		Article a = Article.getArticleAlways(em, aid); // look up or add
		if (a==null) {
		    Logging.error("no Article entry for aid=" + aid + ", and one could nort be created");
		    continue;
		}
		as = new ArticleStats(a);
	    }
	    computeAndSaveStats(em,docno,as);
	    Logging.info("Analyzed document " + aid + ", pos="+docno +
			       ", length="+as.getLength()+", norm=" + as.getNorm());

	    doneCnt++;
	    if (maxCnt>=0 && doneCnt>=maxCnt) {
		break;
	    }
	}
	Logging.info("Overall, analyzed " +doneCnt + " documents, skipped " + skipCnt + " previously analyzed ones");
    }

    /** Computes and records in the database the stats for one document */
    ArticleStats computeAndSaveStats(EntityManager em, int docno) throws  org.apache.lucene.index.CorruptIndexException, IOException {
	Document doc = reader.document(docno,ArticleStats.fieldSelectorAid);
	String aid = doc.get(ArxivFields.PAPER);
	Article a = Article.getArticleAlways(em, aid); // look up or add
	ArticleStats as = new ArticleStats(a);
	computeAndSaveStats(em,docno,as);
	return as;
    }


    /** Computes and records in the database the stats for one document.
	
	@param as A structure with an already set aid. This method
	will compute and put the norm and boosts into this structure.
     */
    private void computeAndSaveStats(EntityManager em, int docno, ArticleStats as)  throws  org.apache.lucene.index.CorruptIndexException, IOException {
	getCoef(docno, as);
	// now, put the new record into the database...
	em.getTransaction().begin();
	em.persist(as);
	em.getTransaction().commit();	
    }


   
    /** Finds a document by ID in the Lucene index */
    int find(String aid) throws IOException{
	IndexSearcher s = new IndexSearcher( reader );
	return Common.find(s, aid);
    }
    
    public IndexReader getReader()  throws IOException {
	return reader;
    }

    /** FIXME: maybe need to resize allStats[]... */
    ArticleStats getAS(ArticleStats allStats[], int docno, EntityManager em) 
	throws IOException //,org.apache.lucene.index.CorruptIndexException
    {
	//int docno=ae.getCorrectDocno(searcher);
	if (docno > allStats.length) {
	    Logging.warning("linSim: no stats for docno=" + docno + " (out of range)");
	    //missingStatsCnt ++;
	    //continue;
	    return null;
	} 
	ArticleStats as =allStats[docno];
	if (as==null) {
	    as = allStats[docno] = computeAndSaveStats(em, docno);
	    Logging.info("linSim: Computed and saved missing stats for docno=" + docno + " (gap)");
	} 
	return as;
    } 

    /** Retrieves an existing ArticleStats record, or creates and
	saves a new one, based on the ArXiv article id */
    public ArticleStats getArticleStatsByAidAlways( EntityManager em, String _aid )  throws  org.apache.lucene.index.CorruptIndexException, IOException {
	ArticleStats as = ArticleStats.findByAid( em, _aid);
	if (as != null) return as;
	int docno =find( _aid);
	as = computeAndSaveStats(em,  docno);
	return as;
    }

    /** Computes similarities of a given document (d1) to all other
	docs in the database. Used for Bernoulli rewards.

	@param cat If not null, restrict matches to docs from the specified category
     */
    void simToAll( HashMap<String, Double> doc1, ArticleStats[] allStats, EntityManager em, String cat) throws IOException {

	final double threshold[] = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6};

	double norm1=tfNorm(doc1);

	int numdocs = reader.numDocs(), maxdoc=reader.maxDoc();
	double scores[] = new double[maxdoc];	
	int tcnt=0,	missingStatsCnt=0;

 	for(String t: doc1.keySet()) {
	    double q= doc1.get(t).doubleValue();

	    double qval = q * idf(t);
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		String f= ArticleAnalyzer.upFields[i];
		Term term = new Term(f, t);
		TermDocs td = reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			

		    double normFactor = 0;
		    if (allStats[p]!=null) {			
			normFactor = allStats[p].getNormalizedBoost(i);
		    } else {
			missingStatsCnt++;
		    }

		    double z =qval * normFactor * freq;
		    scores[p] += z;
		}
		td.close();
	    } // for fields
	    tcnt++;		
	} // for terms 	    
	ArxivScoreDoc[] sd = new ArxivScoreDoc[maxdoc];
	int  nnzc=0, abovecnt[]= new int[threshold.length];

	final CatInfo catInfo = new CatInfo(cat, true);

	for(int k=0; k<scores.length; k++) {
	    if (scores[k]>0) {
		Document doc2 = reader.document(k);
		String cat2 =doc2.get(ArxivFields.CATEGORY);
		boolean catMatch =  catInfo.match(cat2);

		if (catMatch) {
		    double q = scores[k]/norm1;
		    for(int j=0; j<threshold.length; j++) {
			if (q>= threshold[j]) abovecnt[j]++;
		    }

		    if (q>=threshold[0]) {
			sd[nnzc++] = new ArxivScoreDoc(k, q);
		    }
		}		
	    }
	}
	String msg="nnzc=" + nnzc;
	for(int j=0; j<threshold.length; j++) {
	    msg += "; ";
	    msg += "above("+threshold[j]+")="+ abovecnt[j];
	}
	Logging.info(msg);
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " values, because of missing stats");
	}
	
	int maxDocs = 20;
	if (maxDocs > nnzc) maxDocs = nnzc;
	ArxivScoreDoc[] tops=UserProfile.topOfTheList(sd, nnzc, maxDocs);
	System.out.println("Neighbors:");
	for(int i=0; i<tops.length; i++) {
	    
	    System.out.print((i==0? "{" : ", ") +
			     "("   + allStats[tops[i].doc].getAid() +
			     " : " + tops[i].score+")");
	}
	System.out.println("}");

	//return tops;
    }

    /** -DmaxDocs=-1 -Drecompute=false
     */
    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	int maxDocs = ht.getOption("maxDocs", -1);
	boolean recompute = ht.getOption("recompute", false);
	
	//	IndexReader reader =  getReader();
	//	ArticleAnalyzer z = new ArticleAnalyzer(reader, upFields);

	if (argv.length>0 && argv[0].equals("allsims")) {
	    Similarities.allSims();
	} else if (argv.length>0 && argv[0].equals("newsims")) {
	    Similarities.newSims();
	} else if (argv.length>0 && argv[0].equals("sim")) {
	    UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	
	    ArticleAnalyzer z = new ArticleAnalyzer();
	    EntityManager em  = Main.getEM();

	    ArticleStats[] allStats = ArticleStats.getArticleStatsArray(em, z.reader); 

	    String aids [] = {"math/0110197",
			      "1101.0579",
			      "1002.0068",
			      "nlin/0109025",
			      "q-bio/0701040"
	    };
	    for(String aid: aids) {
		System.out.println("Doc=" + aid);
		int docno = -1;
		try {
		    docno = z.find(aid);
		} catch(Exception ex) {
		    Logging.warning("No document found in Lucene data store for id=" + aid +"; skipping");
		    continue;
		}
		HashMap<String, Double> doc1 = z.getCoef(docno, null);		
		Document doc = z.reader.document(docno);
		String cat =doc.get(ArxivFields.CATEGORY);
		Logging.info("Doing " + aid +", cat=" + cat);

		z.simToAll( doc1, allStats, em, cat);
	    }

	} else if (argv.length>0 && argv[0].equals("rated")) {
	    // list all rated pages
	    EntityManager em  = Main.getEM();
	    String[] rated = Action.getAllPossiblyRatedDocs( em);
	    em.close();
	    for(String a: rated) {
		System.out.println(a);
	    }
	} else 	    {
	    // default: update stats

	    ArticleAnalyzer z = new ArticleAnalyzer();
	    
	    EntityManager em  = Main.getEM();
	    z.computeAllMissingNorms(em, maxDocs, recompute);
	    em.close();
	}
    }


}