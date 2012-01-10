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

public class Test {

    // Where our index lives.
    //private Directory indexDirectory;
    private IndexReader reader;
    
    public Test()  throws IOException {
	Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	reader =  IndexReader.open( indexDirectory);     
    }

   
    /** Discount factor for lower-ranking docs.
     @param i rank, 0-based*/
    static double getGamma(int i) {
	return 1.0 / (1 + Math.log( 1.0 + i ));
    }


    /** Is this term to be excluded from the user profile?
     */
    static private boolean isUseless(String t) {
	if (t.length() <= 2) return true;
	if (Character.isDigit( t.charAt(0))) return true;

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


    static class UserProfile {
	DFCounter dfc;
	UserProfile(DFCounter _dfc) {
	    dfc = _dfc;
	}

	/** Maps term to value (cumulative tf) */
	HashMap<String, Double> hq = new HashMap<String, Double>();	
	void add(String key, double inc) {
	    Double val = hq.get(key);
	    double d = (val==null? 0 : val.doubleValue()) + inc;
	    hq.put( key, new Double(d));	  
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

	Vector<ArticleEntry> luceneRawSearch() throws IOException {
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
			scores[p] += qval * freq * normFactor;
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
	    for(int i=0; i< nnzc && i<maxlen; i++) {
		final int k=qpos[i].intValue();
		Document doc = searcher.doc(k);
		//String aid = doc.get(ArxivFields.PAPER);
		ArticleEntry ae= new ArticleEntry(i+1, doc);
		ae.setScore( scores[k]);
		entries.add( ae);
	    }	
	    return entries;
	}

    }

    UserProfile buildUserProfile(String uname) throws IOException {
	EntityManager em = Main.getEM();
	User actor = User.findByName(em, uname);
	if (actor == null) {
	    throw new IllegalArgumentException( "No user with user_name="+ uname+" has been registered");
	}

	DFCounter dfc=new DFCounter(reader,Search.searchFields);
	UserProfile upro = new UserProfile(dfc);

	// descending score order
	UserPageScore[]  ups =  UserPageScore.rankPagesForUser(actor);
	int cnt=0;
	for(UserPageScore up : ups) {
	    if (up.getScore() <=0) break; // don't include "negative" pages
	    String aid = up.getArticle();
	    HashMap<String, Integer> h = dfc.getCoef(aid);
	    double gamma = getGamma(cnt);
	    for(Map.Entry<String,Integer> e: h.entrySet()) {
		upro.add( e.getKey(), gamma * e.getValue().intValue());
	    }
	    cnt++;
	}
	int size0 = upro.hq.size();
	
	upro.purgeUselessTerms();

	String[] terms = upro.hq.keySet().toArray(new String[0]);
	//Arrays.sort(terms);
	Arrays.sort(terms, upro.getByDescVal());
	System.out.println( "User profile has " + terms.length + " terms (was "+size0+" before purging)");
	for(int i=0; i<terms.length; i++) {
	    String t=terms[i];
	    double idf=	upro.dfc.idf(t);
	    System.out.println( t + " : " + upro.hq.get(t) + "*" + idf+
				"\t="+ upro.hq.get(t)*idf);
	}
	//df= +sur.docFreq(term);
	return upro;
    }


    static class DFCounter {

	IndexReader reader;
	private IndexReader[] surs;
	private String [] fields;
	/** Collection size */
	private int numdocs;
	DFCounter(	IndexReader _reader,String [] _fields ) {
	    reader =_reader;
	    surs = reader.getSequentialSubReaders();
	    if (surs==null) {
		surs = new IndexReader[]{ reader};
	    }
	    fields = _fields;
	    numdocs = reader.numDocs();
	}


	/** Finds a document by ID in the Lucene index */
	private int find(String id) throws IOException{
	    IndexSearcher s = new IndexSearcher( reader );
	    TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, id));
	    System.out.println("query=("+tq+")");
	    TopDocs 	 top = s.search(tq, 1);
	    ScoreDoc[] 	scoreDocs = top.scoreDocs;
	    if (scoreDocs.length < 1) {
		System.out.println("No document found with paper="+id);
		throw new IOException("No document found with paper="+id);
	    }
	    return scoreDocs[0].doc;
	}

	private HashMap<String, Integer> h = new HashMap<String, Integer>();
	/** Document friequency for a term. When a term occurs in multiple 
	    fields of a doc, it is counted multiple times, because it's easier
	    to do in Lucene.
	 */
	int totalDF(String t) throws IOException {
	    Integer val = h.get(t);
	    if (val!=null) return val.intValue();
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

	/** Idf =  	 1 + log ( numDocs/docFreq+1)
	 */
	double idf(String term) {
	    try {
		return  1+ Math.log(numdocs*fields.length / (1.0 + totalDF(term)));
	    } catch(IOException ex) { 
		// not likely to happen, as df is normally already cached
		return 1;
	    }
	}

  
	/** Gets a TF vector for a document from the Lucene data store */
	HashMap<String, Integer> getCoef(String id) throws IOException {

	    //long utc = sur.getUniqueTermCount();
	    //System.out.println("subindex has "+utc +" unique terms");

	    HashMap<String, Integer> h = new HashMap<String, Integer>();
	    
	    int docno = -1;
	    try {
		docno = find(id);
	    } catch(Exception ex) {
		System.out.println("No document found in Lucene data store for id=" + id +"; skipping");
		return h;
	    }
	    Document doc = reader.document(docno);
	    for(String name: Search.searchFields) {
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
		    
		    Integer val = h.get( terms[i]);
		    int z = (val==null? 0: val.intValue()) + freqs[i];
		    h.put( terms[i], new Integer(z));
		    //Term term = new Term(name, terms[i]);		
		    //System.out.println(" " + terms[i] + " : " + freqs[i] + "; df=" +sur.docFreq(term) );
		}
	    }
	    //System.out.println("Document info for id=" + id +", doc no.=" + docno + " : " + h.size() + " terms");
	    return h;
	}
    }

    Vector<ArticleEntry> luceneQuerySearch(UserProfile upro) throws IOException {
	org.apache.lucene.search.Query q = upro.firstQuery();
	
	IndexSearcher searcher = new IndexSearcher( reader);
	
	//numdocs = searcher.getIndexReader().numDocs() ;
	//System.out.println("index has "+numdocs +" documents");

	TopDocs 	 top = searcher.search(q, maxlen + 1);
	ScoreDoc[] scoreDocs = top.scoreDocs;
	boolean needNext=(scoreDocs.length > maxlen);
	
	int startat=0;
	Vector<ArticleEntry> entries = new  Vector<ArticleEntry>();
	for(int i=startat; i< scoreDocs.length && i<maxlen; i++) {
	    Document doc = searcher.doc(scoreDocs[i].doc);
	    String aid = doc.get(ArxivFields.PAPER);
	    ArticleEntry ae= new ArticleEntry(i+1, doc);
	    ae.setScore( scoreDocs[i].score);
	    entries.add( ae);
	}	
	return  entries;
    }

    static final int maxlen = 100;

    /** 0 means "all" */
    static int maxTerms = 1024;

    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	maxTerms = ht.getOption("maxTerms", maxTerms);
	boolean raw = ht.getOption("raw", true);

	System.out.println("maxTerms=" + maxTerms +", raw=" + raw);

	Test x = new Test();
	for(String uname: argv) {
	    System.out.println("User=" + uname);
	    UserProfile upro = x.buildUserProfile(uname);	   
 
	    //IndexSearcher searcher = new IndexSearcher( x.reader);	    
	    Vector<ArticleEntry> entries=
		raw ? upro.luceneRawSearch() :
		x.luceneQuerySearch(upro);

	    int startat=0;
	    int pos = startat+1;
	    for(int i=startat; i< entries.size() ; i++) {
		ArticleEntry ae= entries.elementAt(i);

		//		System.out.println("("+(i+1)+") internal id=" + scoreDocs[i].doc +", id=" +aid);
		System.out.println("["+ae.i+"] (score="+ae.score+
				   ") arXiv:" + ae.id + ", " + ae.titline);
		System.out.println(ae.authline);
	    }
	}
    }

}