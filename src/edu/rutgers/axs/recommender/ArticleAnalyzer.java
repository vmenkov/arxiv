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
import edu.rutgers.axs.web.ArticleEntry;


/** Tools for getting numbers out of the Lucene index. This includes
 * computing/extracting DF (document frequency) on the Lucene index,
 * as well as the tf (term frequency) vector for a document.
 */
public class ArticleAnalyzer {

    IndexReader reader;
    private IndexReader[] surs;
    final String [] fields;
    /** Collection size */
    private int numdocs;
    ArticleAnalyzer(	IndexReader _reader,String [] _fields ) {
	fields = _fields;
	reader =_reader;
	surs = reader.getSequentialSubReaders();
	if (surs==null) {
	    surs = new IndexReader[]{ reader};
	}
	numdocs = reader.numDocs();
	baseBoost= initBoost(fields);
    }

    /** This is on top of the "boost" due to some fields being shorter.
     */
    private double[] baseBoost;

    

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

	int sum=0;
	for(IndexReader sur: surs) {
	    for(String name: fields) {
		Term term = new Term(name, t);
		sum += sur.docFreq(term);
	    }		
	}
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

    static final boolean useSqrt = false;

    /** 
	@param docno Lucene's internal integer ID for the document,
	@param as This is an output parameter. If non-null, update
	this object with the feature vector's statistics

	@param The frequency vector, which incorporates boost factors
	for different fields, but no idf.
    */
    HashMap<String, Double> getCoef(int docno, ArticleStats as) 
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
	    for(int j=0; j<nf; j++)  as.setBoost(j,0);
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
	    for(int j=0; j<nf; j++)  as.setBoost(j,boost[j]/norm);
	}

	//System.out.println("Document info for id=" + id +", doc no.=" + docno + " : " + h.size() + " terms");
	return h;
    }


    /** Computes (u*d)/|d|, where u=user profile (specified by hq),
	d=document(docno) with field boosts, (u*d)=idf-weighted dot
	product, |d|=idf-weighted two-norm of d. 

	@param docno=document position (internal id) in the Lucene index.

	@param as Contains precomputed stats for document(docno), in particular,
	boost factors for the fields of d, already nortmalized by |d|

	@param hq Represents the user vector.
	
     */
    double linSim(int docno, ArticleStats as, HashMap<String, UserProfile.TwoVal> hq) 
	throws IOException {
	
	double sum=0;
	
	for(int j=0; j<fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) continue;
	    double boost =  as.getBoost(j);

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



    /** Computes the idf-weighted 2-norm of a term frequency vector.
     @param h Represents the term frequency vector. */
    double tfNorm(HashMap<String, Double> h) {
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


    /* ArticleStats getStats(int docno) throws IOException {
       ArticleStats as = new ArticleStats();
       getCoef(docno, as);
       return as;
       } */

    /** Document fields used in creating the user profile */
    static final String upFields[] =  {
	ArxivFields.TITLE, 
	ArxivFields.AUTHORS, ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE};

    private static double[] initBoost(String [] fields) {
	double baseBoost[] = {3, 1, 3, 3};
	if (baseBoost.length != fields.length) throw new IllegalArgumentException("Expected 4 fields");
	return baseBoost;
    }
    
    /*
    ArticleStats getStats(int docno) {

	Document doc = reader.document(docno);
	String aid = doc.get(ArxivFields.PAPER);
	ArticleStats as = new ArticleStats(aid);
	
	int length=0;

	int lengths[] = new int[fields.length];

	for(int j=0; j<fields.length; j++) {	    
	    String name= fields[j];

	    //Fieldable f = doc.getFieldable(name);
	    //System.out.println("["+name+"]="+f);
	    TermFreqVector tfv=reader.getTermFreqVector(docno, name);
	    if (tfv==null) {
		//System.out.println("--No terms--");
		continue;
	    }
	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {
		int df = totalDF(terms[i]);
		if (df <= 1) {
		    continue; // skip nonce-words
		}
		if (UserProfile.isUseless(terms[i])) continue;
		    
		length += freqs[i];
		lengths[j] += freqs[i];
		    

		Integer val = h.get( terms[i]);
		int z = (val==null? 0: val.intValue()) + freqs[i];
		h.put( terms[i], new Integer(z));
		//Term term = new Term(name, terms[i]);		
		//System.out.println(" " + terms[i] + " : " + freqs[i] + "; df=" +sur.docFreq(term) );
	    }
	}
	//System.out.println("Document info for id=" + id +", doc no.=" + docno + " : " + h.size() + " terms");
   }
    */

    /** Scans the entire Lucene index, computing norms and related
	stats for all articles that don't yet have that info stored
	in the database. Does not try to recompute it when ...

	FIMXE: Should add re-compute when the document has been updated
	since ArticleStats.getDate().

	@param maxCnt Max number of docs to analyze. If negative, analyze all.
    */
    void computeAllMissingNorms(EntityManager em, int maxCnt, boolean recompute) throws  org.apache.lucene.index.CorruptIndexException, IOException {
	List<ArticleStats> aslist = ArticleStats.getAll( em);
	HashMap<String, ArticleStats> h=new HashMap<String, ArticleStats>();
	for(ArticleStats as: aslist) {
	    h.put(as.getAid(), as);
	}
	    
	numdocs = reader.numDocs();
	int doneCnt=0;

	for(int docno=0; docno<numdocs; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    ArticleStats as =h.get(aid);
	    if (as!=null) {
		if (recompute) {
		    Logging.info("Will re-do document " + aid + ", pos="+docno);
		} else {
		    // FIMXE: check dates and update perhaps?
		    Logging.info("Already have  document " + aid + ", pos="+docno);
		    continue;
		}
	    } else {
		as = new ArticleStats();
		as.setAid(aid);
	    }
	    computeAndSaveStats(em,docno,as);
	    Logging.info("Analyzed document " + aid + ", pos="+docno +
			       ", length="+as.getLength()+", norm=" + as.getNorm());

	    doneCnt++;
	    if (maxCnt>=0 && doneCnt>=maxCnt) {
		break;
	    }
	}
    }

    /** Computes and records in the database the stats for one document */
    ArticleStats computeAndSaveStats(EntityManager em, int docno) throws  org.apache.lucene.index.CorruptIndexException, IOException {
	Document doc = reader.document(docno,fieldSelectorAid);
	String aid = doc.get(ArxivFields.PAPER);
	ArticleStats as = new ArticleStats();
	as.setAid(aid);	
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


    private static class FsAidOnly implements FieldSelector {
	public FieldSelectorResult accept(String fieldName) {
	    return fieldName.equals(ArxivFields.PAPER) ?
		FieldSelectorResult.LOAD : 
		FieldSelectorResult.NO_LOAD;
	}
    }

    /** Used as a cost-saving measure when we only need to retrieve
     * the Article ID from Lucene */
    private static FieldSelector fieldSelectorAid = new FsAidOnly();

    /** Reads the pre-computed data from the SQL database.
	@return The index into the array is Lucene's current internal doc id */
    public static ArticleStats[] getArticleStatsArray( EntityManager em,
						       IndexReader reader)
	throws org.apache.lucene.index.CorruptIndexException, IOException
 {
	List<ArticleStats> aslist = ArticleStats.getAll( em);
	HashMap<String, ArticleStats> h=new HashMap<String, ArticleStats>();
	for(ArticleStats as: aslist) {
	    h.put(as.getAid(), as);
	}
	int numdocs = reader.numDocs();
	ArticleStats[] all = new ArticleStats[numdocs];
	int foundCnt=0, deletedCnt=0, nullCnt=0;
	for(int pos=0; pos<numdocs; pos++) {
	    if (reader.isDeleted(pos)) {
		deletedCnt++;
		continue;
	    }
	    Document doc = reader.document(pos,fieldSelectorAid);
	    String aid = doc.get(ArxivFields.PAPER);	    
	    ArticleStats as = h.get(aid);
	    if (as!=null) {
		foundCnt++;
		all[pos] = as;
	    } else {
		nullCnt++;
	    }
	}
	Logging.info("Found pre-computed ArticleStats for " + foundCnt + " docs; no stats found for " + nullCnt + " docs");
	return all;
    }

    /** Finds a document by ID in the Lucene index */
    int find(String aid) throws IOException{
	IndexSearcher s = new IndexSearcher( reader );
	return ArticleEntry.find(s, aid);
    }
    
   
    static public IndexReader getReader()  throws IOException {
	Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	IndexReader reader =  IndexReader.open( indexDirectory);            
	return reader;
    }

    /** -DmaxDocs=-1 -Drecompute=false
     */
    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	int maxDocs = ht.getOption("maxDocs", -1);
	boolean recompute = ht.getOption("recompute", false);
	
	IndexReader reader =  getReader();
	ArticleAnalyzer z = new ArticleAnalyzer(reader, upFields);

	EntityManager em  = Main.getEM();
	z.computeAllMissingNorms(em, maxDocs, recompute);
	em.close();
    }


}