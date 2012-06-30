package edu.rutgers.axs.web;

import java.net.*;

import java.io.*;
import java.util.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.ParseConfig;

/** Our interface for Lucene searches. Also can be used to store data
    read from a DataFile.
 */
public class  SearchResults {

    /** Fields used for searching */
    public static final String [] searchFields = {
	ArxivFields.PAPER, ArxivFields.TITLE, 
	ArxivFields.AUTHORS, ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE};


    /** Search results */
    public ScoreDoc[] 	scoreDocs = new ScoreDoc[0];
    /** Collection size */
    public int numdocs =0;
    /** Empty string or "at least", as appropriate. */
    public String atleast = "";
    
    /** Entries to be displayed */
    public Vector<ArticleEntry> entries= new Vector<ArticleEntry> ();
    /** Entries not to be displayed */	
    public Vector<ArticleEntry> excludedEntries= new Vector<ArticleEntry> ();
    
    /** Links to prev/next pages */
    public int prevstart, nextstart;
    public boolean needPrev, needNext;
    
    public int reportedLength;
    
    public org.apache.lucene.search.Query reportedLuceneQuery=null;
  
    /** Does nothing */
    protected SearchResults() {} 

    private SearchResults(Vector<ScoreDoc> v) {
	scoreDocs =  v.toArray(new ScoreDoc[0]);
    }

    /** Fill a SearchResults object from a DataFile. This involves looking
	up Lucene's internal document IDs using a searcher.
     */
    SearchResults(DataFile df, IndexSearcher searcher) throws IOException {

	// read the artcile IDs and scores from the file
	File f = df.getFile();
	entries = ArticleEntry.readFile(f);
	
	scoreDocs = new ScoreDoc[entries.size()];

	// In docs to be displayed, populate other fields from Lucene
	for(int i=0; i<entries.size(); i++) {
	    ArticleEntry e = entries.elementAt(i);
	    int docno = e.getCorrectDocno(searcher);		    
	    scoreDocs[i] = new ScoreDoc( docno, (float)e.score);
	}
    }

    /** Auxiliary for teamDraft */
    private static int adjustPos(ScoreDoc[] a, int nexta, HashSet<Integer> saved) {
	while( nexta < a.length && saved.contains(new Integer(a[nexta].doc))) nexta++;
	return nexta;
    }

    /** Merges two lists using the Team-Draft algorithm. This is
	Algorithm 2 in: Filip Radlinski, Madhu Kurup, Thorsten
	Joachims, "How Does Clickthrough Data Reflect Retrieval
	Quality?"

	http://www.cs.cornell.edu/People/tj/publications/radlinski_etal_08b.pdf 

	@param seed Random number generator seed. The caller can make it a function of the user name and calendar date,
	to ensure that during same-day page reload the user will see the same list. (Thorsten's suggestion, 2012-06)

	@return A wrapper around the "merged" ScoreDoc array
    */
    public static SearchResults teamDraft(ScoreDoc[] a,  ScoreDoc[] b, long seed) {
	HashSet<Integer> saved = new 	HashSet<Integer> ();
	Vector<ScoreDoc> v = new Vector<ScoreDoc>();
	int acnt = 0, bcnt=0;
	int nexta=0, nextb=0;

	
	Random ran = new Random(seed);

	while(nexta < a.length && nextb < b.length) {
	    boolean useA = (acnt < bcnt ||  acnt==bcnt && ran.nextBoolean());
	    ScoreDoc x = useA? a[nexta++] : b[nextb++];
	    saved.add(new Integer(x.doc));
	    v.add(x);
	    if (useA) {
		acnt ++;
	    } else {
		bcnt ++;		
	    }
	    nexta = adjustPos( a, nexta, saved);
	    nextb = adjustPos( b, nextb, saved);
	}

	return new SearchResults(v);
    }

    static org.apache.lucene.search.Query mkTermOrPrefixQuery(String field, String t) {
	int pos = t.indexOf('*');
	if (pos<0) return new TermQuery(new Term(field, t)); 
	else {
	    return new PrefixQuery(new Term(field, t.substring(0,pos))); 
	}
	
    }

    static TermRangeQuery mkSinceDateQuery(Date since) {
	String lower = 	DateTools.timeToString(since.getTime(), DateTools.Resolution.MINUTE);
	//System.out.println("date range: from " + lower);
	return new TermRangeQuery(ArxivFields.DATE,lower,null,true,false);
    }


    static boolean isAnAndQuery(org.apache.lucene.search.Query q) {
	if (!(q instanceof BooleanQuery)) return false;
	for(BooleanClause c: ((BooleanQuery)q).clauses()) {
	    if (c.getOccur()!= BooleanClause.Occur.MUST) return false;
	}
	return true;
    }


    static BooleanQuery andQuery( org.apache.lucene.search.Query q1, 
				  org.apache.lucene.search.Query q2) {
	if (isAnAndQuery(q1)) {
	    BooleanQuery b = (BooleanQuery)q1;
	    b.add( q2,  BooleanClause.Occur.MUST);
	    return b;
	} else if  (isAnAndQuery(q2)) {
	    BooleanQuery b = (BooleanQuery)q2;
	    b.add( q1,  BooleanClause.Occur.MUST);
	    return b;
	} else {
	    BooleanQuery b = new BooleanQuery();
	    b.add( q1,  BooleanClause.Occur.MUST);
	    b.add( q2,  BooleanClause.Occur.MUST);
	    return b;
	}
    }

    /** Fills the "entries" array with a section [startat:startat+M-1]
       of the full search results array */
    void setWindow(IndexSearcher searcher, int startat, int M, HashMap<String, Action> exclusions) throws IOException,  CorruptIndexException {
	prevstart = Math.max(startat - M, 0);
	nextstart = startat + M;
	needPrev = (prevstart < startat);
	
	needNext=(scoreDocs.length > 	nextstart);
	if (needNext) {
	    reportedLength =scoreDocs.length-1;
	    atleast = "over";
	} else {
	    reportedLength = scoreDocs.length;
	    atleast = "";
	}

	//Document doc = s.doc(scoreDocs[0].doc);
	System.out.println("" + scoreDocs.length + " results");
	
	int pos = startat+1;
	for(int i=startat; i< scoreDocs.length && i<nextstart; i++) {
	    int docno=scoreDocs[i].doc;
	    Document doc = searcher.doc(docno);
	    
	    // check if it's been "removed" by the user.
	    String aid = doc.get(ArxivFields.PAPER);
	    if (exclusions!=null && exclusions.containsKey(aid)) {
		int epos = excludedEntries.size()+1;
		excludedEntries.add( new ArticleEntry(epos, doc, docno, scoreDocs[i].score));
	    } else {
		entries.add( new ArticleEntry(pos, doc, docno, scoreDocs[i].score));
		pos++;
	    }			
	}
    }

    /** "foo" matches "foo*" */
    private static boolean wcMatch(String x, String wc) {
	return wc.endsWith("*") && x.startsWith(wc.substring(0,wc.length()-1));
    }

    /** Counts matches in two sorted (ascending) arrays */
    private static int matchCnt(String [] a1, String[] a2) {
	int cnt=0;
	int i1=0, i2=0;
	while(i1<a1.length && i2<a2.length) {
	    int d = a1[i1].compareTo(a2[i2]);
	    if (d==0 || wcMatch(a1[i1],a2[i2])) {
		i1++;
		i2++;
		cnt++;
	    } else  if (d<0) i1++;
	    else {//if (d>0) 
		i2++;
	    }
	}
	return cnt;
    }


    static class SDComparator implements Comparator<ScoreDoc> {
	// Descending order! 
	public int compare(ScoreDoc a, ScoreDoc  b) {
	    double d = b.score - a.score; 
	    return (d<0) ? -1 : (d>0) ? 1: 0;
	}
    }


    /** Sets scores in each scoreDocs[] element, so that the results
	of the category search (Treatment A) can be reordered as
	needed for June 2012 experiments. The number of matching cats
	is the primary key, the date is the secondary */	
    public static void setCatSearchScores(IndexReader reader, ScoreDoc[] scoreDocs, String[] _cats, Date since) throws IOException, CorruptIndexException{
	String[] cats = Arrays.copyOf(_cats, _cats.length);
	Arrays.sort(cats);

	Date now = new Date();
	long maxMsec = now.getTime() - since.getTime();
	if (maxMsec <= 0) maxMsec = 365 * 24 * 3600L * 1000L; // just in case

	for(int i=0; i<scoreDocs.length; i++) {
	    ScoreDoc sd = scoreDocs[i];
	    
	    TermFreqVector tfv=reader.getTermFreqVector(sd.doc, ArxivFields.CATEGORY);
	    // in ascending order already, as per API docs
	    String[] docCats=tfv.getTerms();	    
	    int matches = matchCnt( docCats,cats);
	    sd.score = (float)matches;

	    String dateString  = reader.document(sd.doc).get(ArxivFields.DATE);
	    if (dateString != null) {
		try {
		    Date docDate= DateTools.stringToDate(dateString);
		    double penalty = 0.5 * (now.getTime() - docDate.getTime())/(double)maxMsec;
		    sd.score -= (float) penalty;
		} catch(java.text.ParseException ex) {}
	    }
	}
    }


    /** Reorder the results of the category search (Treatment A) as
	needed for June 2012 experiments. The number of matching cats is
	the primary key, the date is the secondary */	
    static void reorderCatSearchResults(IndexReader reader, ScoreDoc[] scoreDocs, String[] _cats, Date since) throws IOException, CorruptIndexException{
	setCatSearchScores(reader, scoreDocs, _cats, since);
	Arrays.sort(scoreDocs, new SDComparator());
    }
    
    /** Creates a Date object for a time point that is a specified
	number of days ago */
    public static Date daysAgo(int days) {
	if (days <= 0) return null;
	Date now = new Date();
	Date since = new Date(now.getTime() - days *24L* 3600L*1000L);
	return since;
    }

    static void usage() {
	usage(null);
    }

    static void usage(String m) {
	/*
	System.out.println("Arxiv Importer Tool");
	System.out.println("Usage: java [options] ArxivImporter all [max-page-cnt]");
	System.out.println("Optons:");
	System.out.println(" [-Dtoken=xxx]");
	*/
	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }


    /** For testing */
    static public void main(String[] argv) throws Exception {
	if (argv.length==0) usage();

	ParseConfig ht = new ParseConfig();
	//int maxDocs = ht.getOption("maxDocs", -1);
	boolean cat = ht.getOption("cat", false);
	boolean custom = ht.getOption("custom", false);

	int days=ht.getOption("days", 0);
	Date since = (days <=0) ? null: daysAgo(days);

	String s="";
	for(String x: argv) {
	    if (s.length()>0) s += " ";
	    s += x;
	}

	System.out.println( (cat? "Subject search":"Text search") +
			    " for: " + s + "; since " + since);

	IndexReader reader = ArticleAnalyzer.getReader();
	IndexSearcher searcher = new IndexSearcher(reader);

	//	HashMap<String, Action> exc = new HashMap<String, Action>();
	SearchResults sr;
	if (cat) {
	    sr = new SubjectSearchResults(searcher, argv, since, 10000);
	    sr.reorderCatSearchResults(reader, sr.scoreDocs, argv, since);
	} else {
	    sr =  new TextSearchResults(searcher, s,  200);
	}

	// "windowing" not needed here
	//    sr.setWindow( searcher, startat, exc);


	int cnt=0;
  	for(ScoreDoc q: sr.scoreDocs) {
	    cnt++;
	    int docno = q.doc;
	    Document d = reader.document(docno);
	    System.out.println("["+cnt+"][s="+q.score+"] " + d.get(ArxivFields.PAPER) +
			       "; " + d.get(ArxivFields.CATEGORY) +
			       "; " + d.get(ArxivFields.TITLE) +
			       " ("+ d.get(ArxivFields.DATE)+")");

	}
	searcher.close();
	reader.close();
     
    }


}
