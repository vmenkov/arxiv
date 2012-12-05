package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.ArxivFields;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

public class UserProfile {
    /** 0 means "all" */
    static int maxTerms = 1024;

    private static Stoplist stoplist=null;   
    
    public static void setStoplist( Stoplist x) {
	stoplist = x;
	System.out.println("UserProfile.setStoplist: |stoplist|="+
			   stoplist.size());

    }

    static Stoplist getStoplist() {
	return stoplist;
    }


    /** The action id of the most recent user action that contributed to
	the creation of this profile.
    */
    private long lastActionId=0;
    long getLastActionId() {  return lastActionId;}
    

    public ArticleAnalyzer dfc;

    /** Ordered list by importance, in descending order. */
    public String[] terms = {};

    public static class TwoVal {	
	/** Coefficients for   phi(t) and sqrt(phi(t)) */
	public double w1, w2;
	TwoVal(double _w1, double _w2) { 
	    w1=_w1; w2=_w2;
	}
    }

    /** Maps term to value (cumulative tf) */
    public HashMap<String, TwoVal> hq = new HashMap<String, TwoVal>();	

    /** Used with Approach 2 */
    private void add1(String key, double inc1) {
	TwoVal val = hq.get(key);
	if (val==null) {
	    hq.put( key, new TwoVal(inc1, 0));	  
	} else {
	    val.w1 += inc1;
	}
    }

    /** Used on initalization with Algo 1 Approach 1, and in updates in Algo 2. */
    void add(String key, double inc1, double inc2) {
	TwoVal val = hq.get(key);
	if (val==null) {
	    hq.put( key, new TwoVal(inc1, inc2));	  
	} else {
	    val.w1 += inc1;
	    val.w2 += inc2;
	}
    }
    
    /** Computes w'' := sqrt(w'). This is used with Approach 2 */
    private void computeSqrt() {
	for( TwoVal val:  hq.values()) {
	    val.w2 = Math.sqrt(val.w1);
	}
    }



    /** Is this term to be excluded from the user profile?
     */
    public static boolean isUseless(String t) {
	if (t.length() <= 2) return true;
	if (Character.isDigit( t.charAt(0))) return true;
	if (stoplist!=null && stoplist.contains(t)) return true;

	// Presently only allow words starting with an English letter,
	// and including alhanumeric letters and dots.
	if (!Character.isJavaIdentifierStart(t.charAt(0))) return true;
	for(int i=1; i<t.length(); i++) {
	    char c= t.charAt(i);
	    if (!Character.isJavaIdentifierPart(c) &&
		c!='.') return true;
	}

	return false;
    }


    /** This is not used anymore, as useless terms are removed earlier on. */
    private void purgeUselessTerms() {
	Set<String> keys = hq.keySet();
	for( Iterator<String> it=keys.iterator(); it.hasNext(); ) {
	    String t = it.next();
	    // removal from the key set is supposed to remove the element
	    // the underlying HashMap, as per the API
	    if (isUseless(t)) {
		it.remove();
	    }
	}
    }


    /** Discount factor for lower-ranking docs in constructing user profiles.
	@param i Page rank, 0-based*/
    static double getGamma(int i) {
	return 1.0 / (1 + Math.log( 1.0 + i ));
    }
    
    /** Empty-profile constructor */
    UserProfile(IndexReader reader) throws IOException {
	lastActionId = 0;

	dfc=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
	terms = new String[0];
	Logging.info( "Created an empty user profile");
    }


    /** Initialization of the user profile vector w  in a way suitable
	for later use in TJ's Algo 1 and Algo 2. Uses the entire available
	record of the user activity.

	Here, w1 is initialized as sum(d_i/|d_i|_IDF); w2, as sqrt(w1).
	Unlike my "official" (conceptual) writeup, no sqrt(IDF) (and
	IDF^{1/4}, for w2) is factored into the stored weights. Instead,
	IDF and sqrt(IDF) are brought in when the utility is actually
	computed in Algo 1.
     */
    UserProfile(String uname, EntityManager em, IndexReader reader) throws IOException {
	User actor = User.findByName(em, uname);
	if (actor == null) {
	    throw new IllegalArgumentException( "No user with user_name="+ uname+" has been registered");
	}

	lastActionId = actor.getLastActionId();

	dfc=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
	// descending score order
	UserPageScore[]  ups =  UserPageScore.rankPagesForUser(actor);
	int cnt=0;
	for(UserPageScore up : ups) {
	    if (up.getScore() <=0) break; // don't include "negative" pages
	    String aid = up.getArticle();
	    HashMap<String, Double> h = dfc.getCoef(aid);
	    double gamma = getGamma(cnt); 	    // discount factor
	    // FIXME: can we use stored norm instead?
	    double norm = dfc.tfNorm(h);
	    double f = gamma / norm;
	    if (TjAlgorithm1.approach2) {
		// For Approach 2, w2 will be initialized  later
		for(Map.Entry<String,Double> e: h.entrySet()) {
		    double q = e.getValue().doubleValue();
		    add1( e.getKey(), f * q);
		}
	    } else { // the original, abandoned, approach
		// for the "sqrt(phi)" part
		double norm2 = dfc.normOfSqrtTf(h);
		double f2 = gamma/norm2;
		// For Approach 1, w2 is initialized right here
		for(Map.Entry<String,Double> e: h.entrySet()) {
		    double q = e.getValue().doubleValue();
		    add( e.getKey(), f * q, f2 * Math.sqrt(q));
		}
	    } 

	    cnt++;
	}
	int size0 = hq.size();
	
	//purgeUselessTerms();
	if (TjAlgorithm1.approach2) {
	    computeSqrt();
	}
	setTermsFromHQ();
	Logging.info( "User profile has " + terms.length + " terms");
	//save(new File("profile.tmp"));
    }

    /** Load the terms[] array as the keys of hq, sorted by w1*idf
     */
    void setTermsFromHQ() {
	terms = hq.keySet().toArray(new String[0]);
	Arrays.sort(terms, getByDescVal());
    }

    /** Reads the profile from a file, and set the lastActionId from the 
     DataFile structure. */
    public UserProfile(DataFile df, IndexReader reader) throws IOException {
	this(df.getFile(), reader);
	lastActionId = df.getLastActionId();
    }

    /** Reads the profile from a file. Does not set lastActionId, so that
     has to be done separately. */
    private UserProfile(File f, IndexReader reader) throws IOException {
	dfc=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
	String s;
	Vector<String> vterms = new 	Vector<String>();
	int linecnt = 0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    String q[] = s.split("\\s+");
	    if (q==null || q.length != 4) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f);
	    }
	    String t = q[0];
	    vterms.add(t);
	    hq.put(t, new TwoVal(Double.parseDouble(q[1]), Double.parseDouble(q[2])));
	    // q[3] is dfc, and can be ignored
	}
	r.close();
	terms = vterms.toArray(new String[0]);
	//Logging.info("Read " + linecnt + " lines, " + vterms
    }

    /** DataFile uproFile=new DataFile(task, Task.Op.HISTORY_TO_PROFILE);
	upro.save(uproFile.getFile());
    */
    DataFile saveToFile(Task task, DataFile.Type type) throws IOException {
	DataFile uproFile=  new DataFile(task, type);
	uproFile.setLastActionId( lastActionId);
	this.save(uproFile.getFile());
	return uproFile;
    }

    /** Saves the profile to the specified file. Before doing so, verifies
	that the necessary directory exists, and if it does not, tries to
	create it.
     */
    public void save(File f) throws IOException {
	File g = f.getParentFile();
	if (g!=null && !g.exists()) {
	    boolean code = g.mkdirs();
	    Logging.info("Creating dir " + g + "; success=" + code);
	}

	PrintWriter w= new PrintWriter(new FileWriter(f));
	save(w);
	w.close();
    }

    public void save(PrintWriter w) {
	w.println("#--- Entries are ordered by w(t)*idf(t)");
	w.println("#term\tw(t)\tw(sqrt(t))\tidf(t)");
	for(int i=0; i<terms.length; i++) {
	    String t=terms[i];
	    double w1 = hq.get(t).w1;
	    double w2 = hq.get(t).w2;
	    double idf=	dfc.idf(t);
	    w.println(t + "\t" + w1 + "\t"+w2 + "\t" + idf);
	}
    }


    /** Sorts the keys of the hash table according to the
	corresponding values time IDF, in descending order.
    */
    private class TermsByDescVal implements  Comparator<String> {
	public int compare(String o1,String o2) {
	    double d = hq.get(o2).w1 * dfc.idf(o2)- 
		hq.get(o1).w1 * dfc.idf(o1);
	    return (d<0)? -1 : (d>0) ? 1 : 0;
	}
    }
    
    Comparator getByDescVal() {
	return new  TermsByDescVal();
    }

    /** Creates a Lucene query that looks for any and all terms
	from this user profile. Term weights from the user
	profile are not used, as weight are hard to properly
	incorporate into a Lucene query. 

	An alternative to this approach is to compute properly weighted
	dot products over the Lucene index directly, without 
	using Lucene's query mechanism.
     */
    org.apache.lucene.search.Query firstQuery() {
	BooleanQuery q = new BooleanQuery();
	
	int maxCC = BooleanQuery.getMaxClauseCount();
	if (maxTerms > maxCC) {
	    Logging.info("Raising MaxClauseCount from " + maxCC + " to " + maxTerms);
	    BooleanQuery.setMaxClauseCount(maxTerms);
	    maxCC = BooleanQuery.getMaxClauseCount();
	}
	
	int mt = maxCC;
	if (maxTerms > 0 && maxTerms < mt) mt = maxTerms;
	Logging.info("Max clause count=" + maxCC +", maxTerms="+maxTerms+"; profile has " + terms.length + " terms; using top " + mt);
	
	int tcnt=0;
	for(String t: terms) {
	    BooleanQuery b = new BooleanQuery(); 	
	    for(String f: ArticleAnalyzer.upFields) {
		TermQuery tq = new TermQuery(new Term(f, t));
		b.add( tq, BooleanClause.Occur.SHOULD);		
	    }
	    q.add( b,  BooleanClause.Occur.SHOULD);
	    tcnt++;
	    if (tcnt >= mt) break;
	}	    
	return q;
    }
    
    /** A comparator used to order integer values, interpreted as
	indexes into the score array, based on the values of the
	corresponding elements in that score array. The ordering is
	in the descending order of the scores.
    */
    static class ScoresComparator implements Comparator<Integer> {
	double scores[];
	ScoresComparator(double _scores[]) { scores=_scores; }	    
	/** descending order */
	public int compare(Integer o1,Integer o2) {
	    double d = scores[ o2.intValue()] -
		scores[ o1.intValue()];
	    return (d<0)? -1 : (d>0) ? 1 : 0;
	}	

	/** Sort the values {0,1,2,...,n-1} based on the descending values
	    of the elements of the array scores[0:n-1]
	*/
	static int[] sortIndexesDesc(double scores[])  {
	    Integer qpos[]  = new Integer[scores.length];
	    for(int k=0; k<scores.length; k++) {		
		qpos[k] = new Integer(k);
	    }
	    Arrays.sort( qpos, 0, scores.length, new  ScoresComparator(scores));
	    int p[] = new int[scores.length];
	    for(int k=0; k<scores.length; k++) {		
		p[k] = qpos[k].intValue();
	    }
	    return p;
	}


    }

    /** This is an "autonomous" version, which goes for the real cosine
      similarity, So lots of numbers are precomputed by ourselves,
      stored in the SQL database, and then pulled with ArticleStats[].
      
      @param  maxDocs Max length of the list to return.

      @param allStats Structures with pre-computed norms and field
      boost factors, pulled from the SQL database.

      @param days Article date range (so many most recent days).  0
      means "all dates".
     */
    //    Vector<ArticleEntry>
    ArxivScoreDoc[] 
	luceneRawSearch(int maxDocs, 
			//ArticleStats[] allStats, 
			CompactArticleStatsArray   allStats, 
			EntityManager em, int days, boolean useLog) throws IOException {

	if (days>0) {
	    return luceneRawSearchDateRange(maxDocs, allStats, em, days, useLog);
	}

	int numdocs0 = dfc.reader.numDocs(), maxdoc=dfc.reader.maxDoc() ;
	Logging.info("UP: numdocs=" + numdocs0 + ", maxdoc=" + maxdoc);
	double scores[] = new double[maxdoc];	
		
	int tcnt=0,	missingStatsCnt=0;
	for(String t: terms) {
	    double idf = dfc.idf(t);
	    double qval = hq.get(t).w1 * idf;
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		String f= ArticleAnalyzer.upFields[i];
		Term term = new Term(f, t);
		TermDocs td = dfc.reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			

		    double normFactor = useLog? 1: allStats.getNormalizedBoost(p,i);
		    /*
		    if (allStats[p]!=null) {			
			normFactor = allStats[p].getNormalizedBoost(i);
		    } else {
			missingStatsCnt++;
		    }
		    */
		    double fr = useLog? Math.log(1.0 + freq) :
			(ArticleAnalyzer.useSqrt? Math.sqrt(freq) : freq);    
		    double z =qval * normFactor * fr;

		    scores[p] += z;
		    if (debug!=null) {
			String aid=debug.getAid(p);
			if (aid!=null) System.out.println("CONTR[" + aid + "]("+f+":"+t+")="+z);
		    }
		}
		td.close();
	    }
	    tcnt++;		
	    if (maxTerms>0 && tcnt >= maxTerms) break;
	}	    
	ArxivScoreDoc[] sd = new ArxivScoreDoc[maxdoc];
	int  nnzc=0;
	for(int k=0; k<scores.length; k++) {		
	    if (scores[k]>0) sd[nnzc++] = new ArxivScoreDoc(k, scores[k]);
	}
	Logging.info("nnzc=" + nnzc + ", tcnt=" + tcnt + ", |terms|=" + terms.length );
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " values, because of missing stats");
	}

	ArxivScoreDoc[] tops=topOfTheList(sd, nnzc, maxDocs);
	return tops;
    }

    /** This method first gets an article list by date range, and then
	orders them.
     */
    ArxivScoreDoc[] 
	luceneRawSearchDateRange(int maxDocs, 
				 //ArticleStats[] allStats, 
				 CompactArticleStatsArray   allStats, 
				 EntityManager em, int days,
				 boolean useLog) throws IOException {
	long msec = (new Date()).getTime() - 24*3600*1000 * days;
	TermRangeQuery q = 
	    new TermRangeQuery(ArxivFields.DATE_INDEXED,
			       DateTools.timeToString(msec, DateTools.Resolution.SECOND),
			       null, true, true);
	
	final int M = 10000; // well, the range is supposed to be narrow...
	IndexSearcher searcher = new IndexSearcher( dfc.reader);	
	TopDocs 	 top = searcher.search(q, M+1);
	ScoreDoc[] scoreDocs = top.scoreDocs;
	boolean needNext=(scoreDocs.length > M);
	Logging.info("Search over the range of " + days + " days; found " + scoreDocs.length + " docs in range");
	if (needNext) Logging.warning("Dropped some docs in range search (more results than " + M);

	ArxivScoreDoc[] scores = new ArxivScoreDoc[scoreDocs.length];
	int  nnzc=0;
	int missingStatsCnt =0;

	for(int i=0; i< scoreDocs.length ; i++) {
	    int docno = scoreDocs[i].doc;

	    if (docno > allStats.size()) {
		Logging.warning("linSim: no stats for docno=" + docno + " (out of range)");
		missingStatsCnt ++;
		continue;
	    } 
	    /*
	    ArticleStats as =allStats[docno];
	    if (as==null) {
		as = allStats[docno] = dfc.computeAndSaveStats(em, docno);
		Logging.info("linSim: Computed and saved missing stats for docno=" + docno + " (gap)");
	    } 
	    */

	    double sim = useLog? 
		dfc.logSim(docno, allStats, hq) :
		dfc.linSim(docno, allStats, hq);

	    if (sim>0) 	scores[nnzc++]= new ArxivScoreDoc(docno, sim);
	}

	Logging.info("nnzc=" + nnzc);
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " docs, because of missing stats");
	}

	ArxivScoreDoc[] tops=topOfTheList(scores, nnzc, maxDocs);
	return tops;
	//return packageEntries( tops);
    }

    /** Creates an array of ArxivScoreDoc objects containing the (up to)
	maxDocs top scores from the given list.
     */
    public static ArxivScoreDoc[] topOfTheList(ArxivScoreDoc[] scores, 
				       int nnzc, int  maxDocs)  {
	Arrays.sort( scores, 0, nnzc);
	int maxCnt = Math.min(nnzc, maxDocs);
	ArxivScoreDoc[] out = new 	ArxivScoreDoc[maxCnt];
	for(int i=0; i<out.length; i++) out[i] = scores[i];
	return out;
    }

    Vector<ArticleEntry> packageEntries(ArxivScoreDoc[] scores)  
	throws IOException    {
	Vector<ArticleEntry> entries = new Vector<ArticleEntry>(scores.length);
	for(int i=0; i< scores.length; i++) {
	    ArxivScoreDoc sd = scores[i];
	    Document doc = dfc.reader.document( sd.doc);
	    // FIXME: could use  "skeleton" constructor instead to save time   
	    ArticleEntry ae= new ArticleEntry(i+1, doc, sd.doc);
	    ae.setScore( sd.score);
	    entries.add( ae);
	}	
	return entries;
    }
  
    /** This is the "original" version, that relies to a large extent
     * on values (norms) stored in Lucene.
     */
    Vector<ArticleEntry> luceneRawSearchOrig(int maxDocs) throws IOException {
	String [] terms = hq.keySet().toArray(new String[0]);
	
	int maxdoc = dfc.reader.maxDoc() ;
	double scores[] = new double[maxdoc];	
	
	// Sort by value, in descending order
	Arrays.sort(terms, getByDescVal());
	
	// norms for fields that were stored in Lucene
	byte norms[][] = new byte[ ArticleAnalyzer.upFields.length][];
	
	for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
	    String f= ArticleAnalyzer.upFields[i];
	    if (!dfc.reader.hasNorms(f)) throw new IllegalArgumentException("Lucene index has no norms stored for field '"+f+"'");
	    norms[i] = dfc.reader.norms(f);
	}
	IndexSearcher searcher = new IndexSearcher( dfc.reader);
	Similarity simi = searcher.getSimilarity(); 
	
	int tcnt=0;
	for(String t: terms) {
	    double idf = dfc.idf(t);
	    double qval = hq.get(t).w1 * idf;
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		String f= ArticleAnalyzer.upFields[i];
		Term term = new Term(f, t);
		TermDocs td = dfc.reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			
		    float normFactor = simi.decodeNormValue( norms[i][p]);
		    double z =qval * Math.sqrt(freq) * normFactor;
		    scores[p] += z;
		    if (debug!=null) {
			String aid=debug.getAid(p);
			if (aid!=null) System.out.println("CONTR[" + aid + "]("+f+":"+t+")="+z);
		    }
		}
		td.close();
	    }
	    tcnt++;		
	    if (maxTerms>0 && tcnt >= maxTerms) break;
	}	    
	// qpos[] will contain internal document numbers
	Integer qpos[]  = new Integer[maxdoc];
	int  nnzc=0;
	for(int k=0; k<scores.length; k++) {		
	    if (scores[k]>0) {
		qpos[nnzc++] = new Integer(k);
	    }
	}
	Logging.info("nnzc=" + nnzc);
	Arrays.sort( qpos, 0, nnzc, new  ScoresComparator(scores));
	

	Vector<ArticleEntry> entries = new  Vector<ArticleEntry>();
	for(int i=0; i< nnzc && i<maxDocs; i++) {
	    final int k=qpos[i].intValue();
	    Document doc = searcher.doc(k);
	    //String aid = doc.get(ArxivFields.PAPER);
	    ArticleEntry ae= new ArticleEntry(i+1, doc, k);
	    ae.setScore( scores[k]);
	    entries.add( ae);
	}	
	return entries;
    }

    /** Uses a Lucene query to create a matching document list. 

	This method builds a Lucene query (using firstQuery()) that
	pulls all documents from the index that contain some of the
	terms from the user profile; applies this query, and uses 
	the scores computed by Lucene based on its own algorithm.

	@param Date range, in days. (FIXME: not supported yet!)
     */
    //    Vector<ArticleEntry> 
    ArxivScoreDoc[] 
	luceneQuerySearch(int maxDocs, int days) throws IOException {
	org.apache.lucene.search.Query q = firstQuery();
	
	IndexSearcher searcher = new IndexSearcher( dfc.reader);	
	TopDocs 	 top = searcher.search(q, maxDocs + 1);
	ScoreDoc[] scoreDocs = top.scoreDocs;
	ArxivScoreDoc[] out = new ArxivScoreDoc[scoreDocs.length];
	for(int i=0; i<out.length; i++) {
	    out[i] = new ArxivScoreDoc(scoreDocs[i]);
	}
	return out;
	/*
	boolean needNext=(scoreDocs.length > maxDocs);
	
	int startat=0;
	Vector<ArticleEntry> entries = new  Vector<ArticleEntry>();
	for(int i=startat; i< scoreDocs.length && i<maxDocs; i++) {
	    int docno=scoreDocs[i].doc;
	    Document doc = searcher.doc(docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    ArticleEntry ae= new ArticleEntry(i+1, doc, docno);
	    ae.setScore( scoreDocs[i].score);
	    entries.add( ae);
	}	
	return  entries;
	*/
    }

    /** Creates a map that maps terms to their position in the terms[]
	array of this UserProfile. */
    HashMap<String,Integer> mkTermMapper() {
	HashMap<String,Integer> h = new HashMap<String,Integer>();
	for(int i=0; i<terms.length; i++) {
	    h.put( terms[i], new Integer(i));
	}
	Logging.info("Created a term mapper for " + terms.length + " terms");
	return h;
    }



   

    /** Modifies this existing profile by adding to it, with 0
	coefficients, all terms present in the current user activity
	log. (So this blows up the profile's vocabulary, but does
	not affect any term's weight). Updates this profile's lastActionId accordingly.

	@return ranked list of pages viewed since the last update
     */
    UserPageScore[] updateVocabulary(String uname, EntityManager em) throws IOException {
	User actor = User.findByName(em, uname);
	if (actor == null) {
	    throw new IllegalArgumentException( "No user with user_name="+ uname+" has been registered");
	}

	long lastActionId0 = lastActionId;
	lastActionId = actor.getLastActionId();

	// descending score order
	UserPageScore[]
	    ups =  UserPageScore.rankPagesForUserSince(actor, lastActionId0);
	int cnt=0;
	for(UserPageScore up : ups) {
	    if (up.getScore() <=0) break; // don't include "negative" pages
	    String aid = up.getArticle();
	    HashMap<String, Double> h = dfc.getCoef(aid);
	    for(Map.Entry<String,Double> e: h.entrySet()) {
		add1( e.getKey(),0);
	    }
	    cnt++;
	}
	// updates terms[]
	terms = hq.keySet().toArray(new String[0]);
	Arrays.sort(terms, getByDescVal());
	Logging.info( "Updated vocabulary for the user profile has " + terms.length + " terms");
	return ups;
    }



    /** Stuff used to control debugging and additional verbose reporting. */
    static class Debug {
	static int watchPos[]= {};
	static String watchAid[]= {};
	Debug(IndexReader reader, ParseConfig ht) {
	    String watch = ht.getOption("watch", null);
	    if (watch==null) return;
	    watchAid = watch.split(":");
	    watchPos = new int[watchAid.length];
	    IndexSearcher s = new IndexSearcher( reader );	    
	    for(int i=0; i<watchAid.length; i++) {
		watchPos[i]=-1;
		try {
		    watchPos[i] =  Common.find(s, watchAid[i]);
		} catch (IOException ex) {};
	    }
	}
	String getAid(int pos) {
	    for(int i=0; i<watchPos.length; i++) {
		if (pos==watchPos[i]) return watchAid[i];
	    }
	    return null;
	}
    }

    private static Debug debug=null;

    static void setDebug(IndexReader reader, ParseConfig ht) {
	if (ht.getOption("watch", null)!=null) {
	    debug = new UserProfile.Debug(reader, ht);
	}
    }

    
    public static void main(String argv[]) 
	throws  org.apache.lucene.index.CorruptIndexException, IOException {

	String username = argv[0];
	String file = argv[1];

	System.out.println("User="+username+", profile="+file);

	EntityManager em = Main.getEM();
	IndexReader reader =  Common.newReader();

	for(int j=2; j<argv.length; j++) {
	    String aid = argv[j];

	    DataFile df = DataFile.findFileByName( em,  username,  file);
	    
	    UserProfile up = new UserProfile( df, reader);

	    System.out.println("Doc="+aid);
	    int docno = up.dfc.find(aid);

	    ArticleStats as =  up.dfc.getArticleStatsByAidAlways( em, aid);
	    double linsim = up.dfc.linSimReport(docno, as, up.hq);
	    System.out.println("linsim="+linsim);
	    double logsim = up.dfc.logSimReport(docno, as, up.hq);
	    System.out.println("logsim="+logsim);
	}
    }

}
