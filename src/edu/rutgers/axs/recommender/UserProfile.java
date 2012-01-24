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
import edu.rutgers.axs.indexer.ArxivFields;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

public class UserProfile {
    /** 0 means "all" */
    static int maxTerms = 1024;

    static Stoplist stoplist=null;   

    public ArticleAnalyzer dfc;
    //UserProfile(ArticleAnalyzer _dfc) {
    //	dfc = _dfc;
    //}

    /** Ordered list by importance, in descending order. */
    public String[] terms = {};

    public static class TwoVal {	
	/** Coefficients for   phi(t) and sqrt(phi(t)) */
	public double w1, w2;
	TwoVal(double _w1, double _w2) { w1=_w1; w2=_w2;}
    }

    /** Maps term to value (cumulative tf) */
    public HashMap<String, TwoVal> hq = new HashMap<String, TwoVal>();	

    void add(String key, double inc1, double inc2) {
	TwoVal val = hq.get(key);
	if (val==null) {
	    hq.put( key, new TwoVal(inc1, inc2));	  
	} else {
	    val.w1 += inc1;
	    val.w2 += inc1;
	}
    }
    

    /** Is this term to be excluded from the user profile?
     */
    static boolean isUseless(String t) {
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


    void purgeUselessTerms() {
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
    
    UserProfile(String uname, EntityManager em, IndexReader reader) throws IOException {
	User actor = User.findByName(em, uname);
	if (actor == null) {
	    throw new IllegalArgumentException( "No user with user_name="+ uname+" has been registered");
	}

	dfc=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
	// descending score order
	UserPageScore[]  ups =  UserPageScore.rankPagesForUser(actor);
	int cnt=0;
	for(UserPageScore up : ups) {
	    if (up.getScore() <=0) break; // don't include "negative" pages
	    String aid = up.getArticle();
	    HashMap<String, Double> h = dfc.getCoef(aid);
	    // discount factor
	    double gamma = getGamma(cnt);
	    // FIXME: used stored norm instead
	    double norm = dfc.tfNorm(h);
	    double f = gamma / norm;
	    // for the "sqrt(phi)" part
	    double norm2 = dfc.sqrtTfNorm(h);
	    double f2 = gamma/norm2;

	    for(Map.Entry<String,Double> e: h.entrySet()) {
		double q = e.getValue().doubleValue();
		add( e.getKey(), f * q, f2 * Math.sqrt(q));
	    }
	    cnt++;
	}
	int size0 = hq.size();
	
	purgeUselessTerms();

	terms = hq.keySet().toArray(new String[0]);
	//Arrays.sort(terms);
	Arrays.sort(terms, getByDescVal());
	Logging.info( "User profile has " + terms.length + " terms");
	//save(new PrintWriter(System.out));
	save(new File("profile.tmp"));
    }

    /** Reads the profile from a file */
    public UserProfile(File f, IndexReader reader) throws IOException {
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
    class ByDescVal implements  Comparator<String> {
	public int compare(String o1,String o2) {
	    double d = hq.get(o2).w1 * dfc.idf(o2)- 
		hq.get(o1).w1 * dfc.idf(o1);
	    return (d<0)? -1 : (d>0) ? 1 : 0;
	}
    }
    
    Comparator getByDescVal() {
	return new  ByDescVal();
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
	String [] terms = hq.keySet().toArray(new String[0]);
	// Sort by value, in descending order
	Arrays.sort(terms, new ByDescVal());
	
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
    
    private class ScoresComparator implements Comparator<Integer> {
	double scores[];
	ScoresComparator(double _scores[]) { scores=_scores; }	    
	/** descending order */
	public int compare(Integer o1,Integer o2) {
	    double d = scores[ o2.intValue()] -
		scores[ o1.intValue()];
	    return (d<0)? -1 : (d>0) ? 1 : 0;
	}	
    }

    /** This is an "autonomous" version, which goes for the real cosine
      similarity, So lots of numbers are precomputed by ourselves,
      stored in the SQL database, and then pulled with ArticleStats[].
      
      @param allStats Structures with pre-computed norms and field
      boost factors, pulled from the SQL database.
     */
    Vector<ArticleEntry> luceneRawSearch(int maxDocs, ArticleStats[] allStats) throws IOException {
	String [] terms = hq.keySet().toArray(new String[0]);
	
	int numdocs = dfc.reader.numDocs() ;
	double scores[] = new double[numdocs];	
	
	// Sort by value, in descending order
	Arrays.sort(terms, getByDescVal());
	
	// norms for fields that were stored in Lucene
	//byte norms[][] = new byte[ ArticleAnalyzer.upFields.length][];
	
	//for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
	//    String f= ArticleAnalyzer.upFields[i];
	//    if (!dfc.reader.hasNorms(f)) throw new IllegalArgumentException("Lucene index has no norms stored for field '"+f+"'");
	//    norms[i] = dfc.reader.norms(f);
	//}
	IndexSearcher searcher = new IndexSearcher( dfc.reader);
	Similarity simi = searcher.getSimilarity(); 
	
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
		    //   float normFactor = simi.decodeNormValue( norms[i][p]);

		    double normFactor = 0;
		    if (allStats[p]!=null) {			
			normFactor = allStats[p].getBoost(i);
		    } else {
			missingStatsCnt++;
		    }

		    double z =qval * normFactor * 
			(ArticleAnalyzer.useSqrt? Math.sqrt(freq) : freq);
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
	Integer qpos[]  = new Integer[numdocs];
	int  nnzc=0;
	for(int k=0; k<scores.length; k++) {		
	    if (scores[k]>0) {
		qpos[nnzc++] = new Integer(k);
	    }
	}
	Logging.info("nnzc=" + nnzc);
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " values, because of missing stats");
	}
	Arrays.sort( qpos, 0, nnzc, new  ScoresComparator(scores));
	

	Vector<ArticleEntry> entries = new  Vector<ArticleEntry>();
	for(int i=0; i< nnzc && i<maxDocs; i++) {
	    final int k=qpos[i].intValue();
	    Document doc = searcher.doc(k);
	    //String aid = doc.get(ArxivFields.PAPER);
	    ArticleEntry ae= new ArticleEntry(i+1, doc);
	    ae.setScore( scores[k]);
	    entries.add( ae);
	}	
	return entries;
    }

    /** This is the "original" version, that more relies on values
     * (norms) stored in Lucene.
     */
  Vector<ArticleEntry> luceneRawSearchOrig(int maxDocs) throws IOException {
	String [] terms = hq.keySet().toArray(new String[0]);
	
	int numdocs = dfc.reader.numDocs() ;
	double scores[] = new double[numdocs];	
	
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
	Integer qpos[]  = new Integer[numdocs];
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
	    ArticleEntry ae= new ArticleEntry(i+1, doc);
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
     */
    Vector<ArticleEntry> luceneQuerySearch(int maxDocs) throws IOException {
	org.apache.lucene.search.Query q = firstQuery();
	
	IndexSearcher searcher = new IndexSearcher( dfc.reader);	
	TopDocs 	 top = searcher.search(q, maxDocs + 1);
	ScoreDoc[] scoreDocs = top.scoreDocs;
	boolean needNext=(scoreDocs.length > maxDocs);
	
	int startat=0;
	Vector<ArticleEntry> entries = new  Vector<ArticleEntry>();
	for(int i=startat; i< scoreDocs.length && i<maxDocs; i++) {
	    Document doc = searcher.doc(scoreDocs[i].doc);
	    String aid = doc.get(ArxivFields.PAPER);
	    ArticleEntry ae= new ArticleEntry(i+1, doc);
	    ae.setScore( scoreDocs[i].score);
	    entries.add( ae);
	}	
	return  entries;
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
		    watchPos[i] =  Test.find(s, watchAid[i]);
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



}
