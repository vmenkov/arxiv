package edu.rutgers.axs;

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

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;


//    static 
class UserProfile {
    /** 0 means "all" */
    static int maxTerms = 1024;

    static Stoplist stoplist=null;   

    ArticleAnalyzer dfc;
    //UserProfile(ArticleAnalyzer _dfc) {
    //	dfc = _dfc;
    //}
    
    /** Maps term to value (cumulative tf) */
    HashMap<String, Double> hq = new HashMap<String, Double>();	
    void add(String key, double inc) {
	Double val = hq.get(key);
	double d = (val==null? 0 : val.doubleValue()) + inc;
	hq.put( key, new Double(d));	  
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

	dfc=new ArticleAnalyzer(reader,Search.searchFields);
	// descending score order
	UserPageScore[]  ups =  UserPageScore.rankPagesForUser(actor);
	int cnt=0;
	for(UserPageScore up : ups) {
	    if (up.getScore() <=0) break; // don't include "negative" pages
	    String aid = up.getArticle();
	    HashMap<String, Double> h = dfc.getCoef(aid);
	    // FIXME: used stored norm instead
	    double norm = dfc.tfNorm(h);
	    double gamma = getGamma(cnt);
	    double f = gamma / norm;
	    for(Map.Entry<String,Double> e: h.entrySet()) {
		add( e.getKey(), f * e.getValue().doubleValue());
	    }
	    cnt++;
	}
	int size0 = hq.size();
	
	purgeUselessTerms();

	String[] terms = hq.keySet().toArray(new String[0]);
	//Arrays.sort(terms);
	Arrays.sort(terms, getByDescVal());
	System.out.println( "User profile has " + terms.length + " terms (was "+size0+" before purging)");
	for(int i=0; i<terms.length; i++) {
	    String t=terms[i];
	    double idf=	dfc.idf(t);
	    System.out.println( t + " : " + hq.get(t) + "*" + idf+
				"\t="+ hq.get(t)*idf);
	}
	//df= +sur.docFreq(term);
    }


    /** Sorts the keys of the hash table according to the
	corresponding values time IDF, in descending order.
    */
    class ByDescVal implements  Comparator<String> {
	public int compare(String o1,String o2) {
	    double d = hq.get(o2).doubleValue() * dfc.idf(o2)- 
		hq.get(o1).doubleValue() * dfc.idf(o1);
	    return (d<0)? -1 : (d>0) ? 1 : 0;
	}
    }
    
    Comparator getByDescVal() {
	return new  ByDescVal();
    }
    
    org.apache.lucene.search.Query firstQuery() {
	BooleanQuery q = new BooleanQuery();
	String [] terms = hq.keySet().toArray(new String[0]);
	// Sort by value, in descending order
	Arrays.sort(terms, new ByDescVal());
	
	int maxCC = BooleanQuery.getMaxClauseCount();
	if (maxTerms > maxCC) {
	    System.out.println("Raising MaxClauseCount from " + maxCC + " to " + maxTerms);
	    BooleanQuery.setMaxClauseCount(maxTerms);
	    maxCC = BooleanQuery.getMaxClauseCount();
	}
	
	int mt = maxCC;
	if (maxTerms > 0 && maxTerms < mt) mt = maxTerms;
	System.out.println("Max clause count=" + maxCC +", maxTerms="+maxTerms+"; profile has " + terms.length + " terms; using top " + mt);
	
	int tcnt=0;
	for(String t: terms) {
	    BooleanQuery b = new BooleanQuery(); 	
	    for(String f: Search.searchFields) {
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

    Vector<ArticleEntry> luceneRawSearch(int maxDocs, ArticleStats[] allStats) throws IOException {
	String [] terms = hq.keySet().toArray(new String[0]);
	
	int numdocs = dfc.reader.numDocs() ;
	double scores[] = new double[numdocs];	
	
	// Sort by value, in descending order
	Arrays.sort(terms, getByDescVal());
	
	// norms for fields that were stored in Lucene
	byte norms[][] = new byte[ Search.searchFields.length][];
	
	for(int i=0; i<Search.searchFields.length; i++) {
	    String f= Search.searchFields[i];
	    if (!dfc.reader.hasNorms(f)) throw new IllegalArgumentException("Lucene index has no norms stored for field '"+f+"'");
	    norms[i] = dfc.reader.norms(f);
	}
	IndexSearcher searcher = new IndexSearcher( dfc.reader);
	Similarity simi = searcher.getSimilarity(); 
	
	int tcnt=0,	missingStatsCnt=0;
	for(String t: terms) {
	    double idf = dfc.idf(t);
	    double qval = hq.get(t).doubleValue() * idf;
	    for(int i=0; i<Search.searchFields.length; i++) {
		String f= Search.searchFields[i];
		Term term = new Term(f, t);
		TermDocs td = dfc.reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			
		    //		    float normFactor = simi.decodeNormValue( norms[i][p]);

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
	System.out.println("nnzc=" + nnzc);
	if (missingStatsCnt>0) {
	    System.out.println("used zeros for " + missingStatsCnt + " values, because of missing stats");
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


  Vector<ArticleEntry> luceneRawSearchOrig(int maxDocs) throws IOException {
	String [] terms = hq.keySet().toArray(new String[0]);
	
	int numdocs = dfc.reader.numDocs() ;
	double scores[] = new double[numdocs];	
	
	// Sort by value, in descending order
	Arrays.sort(terms, getByDescVal());
	
	// norms for fields that were stored in Lucene
	byte norms[][] = new byte[ Search.searchFields.length][];
	
	for(int i=0; i<Search.searchFields.length; i++) {
	    String f= Search.searchFields[i];
	    if (!dfc.reader.hasNorms(f)) throw new IllegalArgumentException("Lucene index has no norms stored for field '"+f+"'");
	    norms[i] = dfc.reader.norms(f);
	}
	IndexSearcher searcher = new IndexSearcher( dfc.reader);
	Similarity simi = searcher.getSimilarity(); 
	
	int tcnt=0;
	for(String t: terms) {
	    double idf = dfc.idf(t);
	    double qval = hq.get(t).doubleValue() * idf;
	    for(int i=0; i<Search.searchFields.length; i++) {
		String f= Search.searchFields[i];
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
	System.out.println("nnzc=" + nnzc);
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
