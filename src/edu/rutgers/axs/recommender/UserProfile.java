package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.commons.lang.mutable.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.ArxivFields;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;

/** Used to maintain a variety of used profiles for TJ's methods, in
    particular SET_BASED and PPP (aka 3PR). A user profile is stored
    in a HashMap, keyed by the term. The stored values are based on
    linear combinations of document vectors, normalized, but *not*
    including sqrt(idf).     
 */
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

    /** An ArticleAnalyzer object is used to obtain document
	statistics etc. In the "light" version of UserProfile - only
	used to view pre-created user profile data files - it may be
	null.
     */
    ArticleAnalyzer dfc;


    /** This is only used in "light" UserProfile, when dfc==null */
    private HashMap<String, Double> lightIdfTable = null;

    /** Ordered list by importance, in descending order. */
    public String[] terms = {};

    public static class TwoVal {	
	/** Coefficients for   phi(t) and sqrt(phi(t)) */
	public double w1, w2;
	TwoVal(double _w1, double _w2) { 
	    w1=_w1; w2=_w2;
	}
    }

    /** The vector w, stored as a hash maps that maps each feature to
	the pair of values (cumulative tf) */
    public HashMap<String, TwoVal> hq = new HashMap<String, TwoVal>();	

    /** Used with the (currently used) Approach 2 */
    private void add1(String key, double inc1) {
	TwoVal val = hq.get(key);
	if (val==null) {
	    hq.put( key, new TwoVal(inc1, 0));	  
	} else {
	    val.w1 += inc1;
	}
    }

    /** Used in updates in Algo 2. */
    void add(String key, double inc1, double inc2) {
	TwoVal val = hq.get(key);
	if (val==null) {
	    hq.put( key, new TwoVal(inc1, inc2));	  
	} else {
	    val.w1 += inc1;
	    val.w2 += inc2;
	}
    }
    
    /** Computes w'' := sqrt(w').  */
    
    private void computeSqrt() {
	for( TwoVal val:  hq.values()) {
	    val.w2 = Math.sqrt(val.w1);
	}
    }
    

    /** Use this in the version with no nonlinear part (as in the standard 3PR)
     */
    private void zeroNonlinear() {
	for( TwoVal val:  hq.values()) {
	    val.w2 = 0;
	}
    }

    /** Checks if there are any "useless" terms among the features, and
	removes them. Normally, there shouldn't be any. But there could
	be excpetional situations, e.g. a change in the composition of the
	stopword list.

	<P>FIXME: Do we need to also hunt for very rare terms?
    */
    private void removeUselessTerms()  {
	/** // this triggers  java.util.ConcurrentModificationException
	for(String key: hq.keySet()) {
	    if (isUseless( dfc.keyToTerm(key))) {
		hq.remove(key);
	    }			    
	}
	*/
	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");
	int rmCnt=0;
	for(Iterator<Map.Entry<String, TwoVal>> it = 
		hq.entrySet().iterator();   it.hasNext(); ) {	    
	    String key = it.next().getKey();
	    if (isUseless( dfc.keyToTerm(key))) {
		it.remove();
		rmCnt++;
	    }			    
	}
	if (rmCnt>0) Logging.info("Removed " + rmCnt + " useless terms from the dictionary");

    }


     /** Is this term to be excluded from the user profile? 

	<p>Note that the criteria for the author field are different
	than for other fields. This is because we have authors
	surnamed Li, Ma, Yi, Du, etc, as well as Z. Was and H. Then.

	(FIXME: Note, however, that the stopwords have already been excluded
	during indexing, so we'll need to re-index the whole thing to bring
	Mr. Then back. 2013-12-29)
     */

    public static boolean isUseless(Term term) {
	String t = term.text();
	final boolean isAuthors = term.field().equals(ArxivFields.AUTHORS);
	final int L = isAuthors ? 1 : 2;
	if (t.length() <= L) return true;
	char c = t.charAt(0);
	if (Character.isDigit(c) || c=='_') return true;
	if (!isAuthors && stoplist!=null && stoplist.contains(t)) return true;

	// Presently only allow words starting with an English letter,
	// and including only alphanumeric letters, apostrophes and dots.
	if (!Character.isJavaIdentifierStart(c)) return true;
	for(int i=1; i<t.length(); i++) {
	    c= t.charAt(i);
	    if (!Character.isJavaIdentifierPart(c) && c!='.' && c!='\'') return true;
	}
	return false;
    }

  
    /** Discount factor for lower-ranking docs in constructing user profiles.
	@param i Page rank, 0-based*/
    static double getGamma(int i) {
	return 1.0 / (1 + Math.log( 1.0 + i ));
    }
    
    /** Empty-profile constructor */
    //    UserProfile(IndexReader reader) throws IOException {
    UserProfile(ArticleAnalyzer aa) throws IOException {
	lastActionId = 0;
	dfc = aa;
	//dfc=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
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

	This really should be deprecated, because we initialize from 0 now.
     */
    //    UserProfile(String uname, EntityManager em, IndexReader reader) throws IOException {
    UserProfile(String uname, EntityManager em, ArticleAnalyzer aa) throws IOException {
	User actor = User.findByName(em, uname);
	if (actor == null) {
	    throw new IllegalArgumentException( "No user with user_name="+ uname+" has been registered");
	}

	lastActionId = actor.getLastActionId();

	//dfc=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
	dfc=aa;
	// descending score order
	UserPageScore[]  ups =  UserPageScore.rankPagesForUser(actor);
	int cnt=0;
	for(UserPageScore up : ups) {
	    if (up.getScore() <=0) break; // don't include "negative" pages
	    String aid = up.getArticle();
	    HashMap<String, ?extends Number> h = dfc.getCoef(aid);
	    double gamma = getGamma(cnt); 	    // discount factor

	    // w2 will be initialized  later
	    for(Map.Entry<String,?extends Number> e: h.entrySet()) {
		double q = e.getValue().doubleValue();
		add1( e.getKey(), gamma * q);
	    }

	    cnt++;
	}
	int size0 = hq.size();
	
	computeSqrt();

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
    //    public UserProfile(DataFile df, IndexReader reader) throws IOException {
    public UserProfile(DataFile df, ArticleAnalyzer aa) throws IOException {
	this(df.getVersion(), df.getFile(), aa);
	lastActionId = df.getLastActionId();
    }

    /** Reads the profile from a file. Does not set lastActionId, so that
     has to be done separately. 

     <p>This construction can accept aa=null; in which case, a "light"
     UserProfile object is created, which can only be used for displaying
     the data from an existing user profile file.
    */
    //    private UserProfile(int version, File f, IndexReader reader) throws IOException {
    private UserProfile(int version, File f, ArticleAnalyzer aa) throws IOException {

	if (aa==null) {
	    Logging.warning("Creating a 'light' UserProfile, only for viewing a file");
	} else if ((version==2) ^ (aa instanceof ArticleAnalyzer2)) throw new IllegalArgumentException("DF.version=" + version +", AA type=" + aa.getClass());

	//	    new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
	dfc=aa;

	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
	String s;
	Vector<String> vterms = new 	Vector<String>();
	int linecnt = 0;
	lightIdfTable = new HashMap<String, Double>();
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
	    // q[3] is idf, and can be ignored, unless it's a "light" UserPrrofile
	    if (dfc==null) {
		lightIdfTable.put(t, new Double(q[3]));
	    }
	}
	r.close();
	terms = vterms.toArray(new String[0]);
	//Logging.info("Read " + linecnt + " lines, " + vterms
    }

    /** DataFile uproFile=new DataFile(task, Task.Op.HISTORY_TO_PROFILE);
	upro.save(uproFile.getFile());
    */
    DataFile saveToFile(Task task, DataFile.Type type) throws IOException {
	return saveToFile(task.getUser(), task.getId(), type);
    }

    /** Creates a disk file and a matching DataFile object to store this
	UserProfile.

	<p>FIXME: the file version is chosen based on the class of the
	ArticleAnalyzer dfc. This may need to be finessed once we have more
	than 2 versions.
    */
    DataFile saveToFile(String user, long taskId, DataFile.Type type) 
	throws IOException {
	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");

	DataFile uproFile=  new DataFile(user, taskId, type);
	int version = (dfc instanceof ArticleAnalyzer2) ? 2 : 1;
	uproFile.setLastActionId( lastActionId);
	uproFile.setVersion(version);
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
	    if (!code) throw new IOException("Failed to create directory " + g);
	}

	PrintWriter w= new PrintWriter(new FileWriter(f));
	save(w);
	w.close();
    }

    public void save(PrintWriter w) throws IOException {

	w.println("#--- Entries are ordered by w(t)*idf(t)");
	w.println("#term\tw(t)\tw(sqrt(t))\tidf(t)");
	for(int i=0; i<terms.length; i++) {
	    String t=terms[i];
	    double w1 = hq.get(t).w1;
	    double w2 = hq.get(t).w2;
	    double idf=	idf(t);
	    w.println(t + "\t" + w1 + "\t"+w2 + "\t" + idf);
	}
    }


    /** Sorts the keys of the hash table according to the
	corresponding values time IDF, in descending order.
    */
    private class TermsByDescVal implements  Comparator<String> {
	public int compare(String o1,String o2) {
	    try {
		double d = hq.get(o2).w1 * dfc.idf(o2)- 
		    hq.get(o1).w1 * dfc.idf(o1);
		return (d<0)? -1 : (d>0) ? 1 : 0;
	    } catch(IOException ex) { return 0; }
	}
    }
    
    Comparator<String> getByDescVal() {
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
			EntityManager em, int days, boolean useLog) throws IOException {

	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");
	if (days>0) {
	    return luceneRawSearchDateRange(maxDocs, em, days, useLog);
	}

	int numdocs0 = dfc.reader.numDocs(), maxdoc=dfc.reader.maxDoc() ;
	Logging.info("UP: numdocs=" + numdocs0 + ", maxdoc=" + maxdoc);
	double scores[] = new double[maxdoc];	
	CompactArticleStatsArray   allStats = dfc.getCasa();
	if (allStats ==null) throw new IllegalArgumentException();

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

    /** This method first gets an article list by category and date range, and then
	orders them.
	@param u User object whose category list we use
     */
    ArxivScoreDoc[] 
	catAndDateSearch(int maxDocs, 
			 EntityManager em, User u, int days,
			 boolean useLog) throws IOException {

	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");
	if (dfc.getCasa()==null) throw new IllegalArgumentException("AA.catAndDateSearch() called without initializing AA.CASA first!");

	Date since = SearchResults.daysAgo( days );
	final int M = 10000; // well, the range is supposed to be narrow...
	IndexSearcher searcher = new IndexSearcher( dfc.reader);	

	SearchResults sr = SubjectSearchResults.orderedSearch(searcher, u, since, M+1);
	ScoreDoc[] scoreDocs = sr.scoreDocs;
	boolean needNext=(scoreDocs.length > M);
	Logging.info("Searched within user's subjects over the range of " + days + " days; found " + scoreDocs.length + " docs in range");
	if (needNext) Logging.warning("Dropped some docs in range search (more results than " + M);

	ArxivScoreDoc[] scores = new ArxivScoreDoc[scoreDocs.length];
	int  nnzc=0;
	int missingStatsCnt =0;

	for(int i=0; i< scoreDocs.length ; i++) {
	    int docno = scoreDocs[i].doc;

	    if (docno > dfc.getCasa().size()) {
		Logging.warning("linSim: no stats for docno=" + docno + " (out of range)");
		missingStatsCnt ++;
		continue;
	    } 

	    double sim = useLog? 
		dfc.logSim(docno, dfc.getCasa(), hq) :
		dfc.linSim(docno, dfc.getCasa(), hq);

	    if (sim>0) 	scores[nnzc++]= new ArxivScoreDoc(docno, sim);
	}

	Logging.info("nnzc=" + nnzc);
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " docs, because of missing stats");
	}

	ArxivScoreDoc[] tops=topOfTheList(scores, nnzc, maxDocs);
	return tops;
    }


    /** This method first gets an article list by date range, and then
	orders them.
     */
   ArxivScoreDoc[] 
	luceneRawSearchDateRange(int maxDocs, 
				 //CompactArticleStatsArray   allStats, 
				 EntityManager em, int days,
				 boolean useLog) throws IOException {
	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");

	Date since = SearchResults.daysAgo( days );
	final int M = 10000; // well, the range is supposed to be narrow...
	IndexSearcher searcher = new IndexSearcher( dfc.reader);	

	TermRangeQuery q = 
	    new TermRangeQuery(ArxivFields.DATE_INDEXED,
			       DateTools.dateToString(since, DateTools.Resolution.SECOND),
			       null, true, true);
	Logging.info("Query=" + q);
	TopDocs 	 top = searcher.search(q, M+1);
	ScoreDoc[] scoreDocs = top.scoreDocs;

	boolean needNext=(scoreDocs.length > M);
	Logging.info("Searched within user's subjects over the range of " + days + " days; found " + scoreDocs.length + " docs in range");
	if (needNext) Logging.warning("Dropped some docs in range search (more results than " + M);

	ArxivScoreDoc[] scores = new ArxivScoreDoc[scoreDocs.length];
	int  nnzc=0;
	int missingStatsCnt =0;

	CompactArticleStatsArray   allStats = dfc.getCasa();
	if (allStats ==null) throw new IllegalArgumentException();

	for(int i=0; i< scoreDocs.length ; i++) {
	    int docno = scoreDocs[i].doc;

	    if (docno > allStats.size()) {
		Logging.warning("linSim: no stats for docno=" + docno + " (out of range)");
		missingStatsCnt ++;
		continue;
	    } 

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
	return ArxivScoreDoc.packageEntries(scores, dfc.reader);
    }

    /** This is the "original" version, that relies to a large extent
     * on values (norms) stored in Lucene.
     */
    Vector<ArticleEntry> luceneRawSearchOrig(int maxDocs) throws IOException {
	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");

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
	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");

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
	    HashMap<String, ?extends Number> h = dfc.getCoef(aid);
	    for(String key: h.keySet()) {
		add1( key, 0);
	    }
	    cnt++;
	}
	// updates terms[]
	terms = hq.keySet().toArray(new String[0]);
	Arrays.sort(terms, getByDescVal());
	Logging.info( "Updated vocabulary for the user profile has " + terms.length + " terms");
	return ups;
    }

    /** Updates this user profile in the PPP (3PR) framework: adds a linear
	combination of document vectors, Rocchio-style. (Isn't it nice
	to have a linear model?)

	@param updateCo Rocchio-type update: weights for documents
     */ 
    void rocchioUpdate(HashMap<String,? extends Number> updateCo ) throws IOException {
	if (dfc==null) throw new IllegalArgumentException("This method should not be called in the 'Light' UserProfile");

	int cnt=0;
	for(String aid: updateCo.keySet()) {
	    double w =  updateCo.get(aid).doubleValue();
	    HashMap<String, ?extends Number> h = dfc.getCoef(aid);
	    for(Map.Entry<String, ?extends Number> e: h.entrySet()) {
		double q = e.getValue().doubleValue();
		add1( e.getKey(), w*q);
	    }
	    cnt++;
	}

	removeUselessTerms(); // just in case

	// Rocchio type update (rather than Delta Psi) is only used in methods
	// with no non-linear part       
	zeroNonlinear();

	// updates terms[]
	terms = hq.keySet().toArray(new String[0]);
	Arrays.sort(terms, getByDescVal());
	Logging.info( "Updated vocabulary for the user profile has " + terms.length + " terms");
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
	ArticleAnalyzer aa=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);

	for(int j=2; j<argv.length; j++) {
	    String aid = argv[j];

	    DataFile df = DataFile.findFileByName( em,  username,  file);
	    
	    UserProfile up = new UserProfile( df, aa);

	    System.out.println("Doc="+aid);
	    int docno = up.dfc.find(aid);

	    ArticleStats as =  up.dfc.getArticleStatsByAidAlways( em, aid);
	    double linsim = up.dfc.linSimReport(docno, as, up.hq);
	    System.out.println("linsim="+linsim);
	    double logsim = up.dfc.logSimReport(docno, as, up.hq);
	    System.out.println("logsim="+logsim);
	}
    }

    public double idf(String t) throws IOException {
	if (dfc==null) {
	    Double q = lightIdfTable.get(t);
	    return q==null? 0: q.doubleValue();
	} else {
	    return dfc.idf(t);
	}
    }

}
