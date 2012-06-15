package edu.rutgers.axs.web;

import java.net.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.document.*;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.html.RatingButton;
import edu.rutgers.axs.ParseConfig;

/** Our interface for Lucene searches
 */
public class Search extends ResultsBase {
    
    public final static int M=25;

    public String query, queryEncoded ;
    public SearchResults sr;
    public int startat = 0;
    //public boolean useLog = false;

    public Search(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	if (error) return;
	EntityManager em = null;
	try {
	    query = request.getParameter("simple_search");
	    if (query==null || query.trim().equals("")) {
		error=true;
		errmsg="No query entered";
		return;
	    }

	    queryEncoded = URLEncoder.encode(query);
	    
	    // just checking
	    SessionData sd = SessionData.getSessionData(request);  
            edu.cornell.cs.osmot.options.Options.init(sd.getServletContext() );

	    startat = (int)Tools.getLong(request, "startat",0);
	    User u = null;

	    if (user!=null) {
		em = sd.getEM();
		u = User.findByName(em, user);    
	    }

	    // Pages the user does not want ever shown (may be empty)
	    HashMap<String, Action> exclusions = 
		(u==null) ? new HashMap<String, Action>() :
		u.getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN});

	    sr  = new TextSearchResults(query, exclusions, startat);
	    if (u!=null) {
		// Mark pages currently in the user's folder, or rated by the user
		ArticleEntry.markFolder(sr.entries, u.getFolder());
		ArticleEntry.markRatings(sr.entries, 
					 u.getActionHashMap(Action.ratingOps));
	    }

	    if (user!=null) {
		if (u!=null) {
		    em.getTransaction().begin();
		    u = User.findByName(em, user); // re-read, just in case   
		    u.addQuery(query, sr.nextstart, sr.scoreDocs.length);
		    em.persist(u);
		    em.getTransaction().commit(); 
		}
		em.close();
	    }
	}  catch (WebException _e) {
	    error=true;
	    errmsg=_e.getMessage();
	    return;
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, false);
	}
    }


    /** Fields used for searching */
    public static final String [] searchFields = {
	ArxivFields.PAPER, ArxivFields.TITLE, 
	ArxivFields.AUTHORS, ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE};

    public static class SearchResults {

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
    }

    /** Full-text search */
    public static class TextSearchResults extends SearchResults {
	/**@param query Text that the user typed into the Search box
	   @param startat How many top search results to skip (0, 25, ...)
	 */
	TextSearchResults(String query, HashMap<String, Action> exclusions, int startat) throws Exception {
	    prevstart = Math.max(startat - M, 0);
	    nextstart = startat + M;
	    needPrev = (prevstart < startat);

	    query=query.trim();
	    boolean isPhrase= query.length()>=2 &&
		query.startsWith("\"") && query.endsWith("\"");
	    if (isPhrase) query = query.substring(1, query.length()-1);

	    // ":", "-", "." and "*" are allowed in terms, for later processing
	    String terms[]= query.split("[^a-zA-Z0-9_:\\.\\*\\-]+");
	    BooleanQuery q = new BooleanQuery();

	    if (isPhrase) {
		// the entire phrase must occur... somewhere
		for(String f: searchFields) {
		    PhraseQuery ph = new PhraseQuery();
		    int tcnt=0;
		    for(String t: terms) {
			if (t.trim().length()==0) continue;
			ph.add( new Term(f, t.toLowerCase()));
			tcnt++;
		    }
		    q.add( ph, BooleanClause.Occur.SHOULD);
		}
	    } else {
		// each term must occur... somewhere
		int tcnt=0;
		for(String t: terms) {
		    if (t.trim().length()==0) continue;
		    org.apache.lucene.search.Query zq= mkWordClause(t);
		    if (zq == null) continue;
		    q.add( zq,  BooleanClause.Occur.MUST);
		    tcnt++;
		}
		if (tcnt==0) throw new WebException("Empty query");
	    }	

	    Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	    IndexSearcher searcher = new IndexSearcher( indexDirectory);
	    
	    numdocs = searcher.getIndexReader().numDocs() ;
	    System.out.println("index has "+numdocs +" documents");
	    
	    int maxlen = startat + M;
	    reportedLuceneQuery=q;
	    TopDocs 	 top = searcher.search(q, maxlen + 1);
	    scoreDocs = top.scoreDocs;
	    needNext=(scoreDocs.length > maxlen);
	    if (needNext) {
		reportedLength =maxlen;
		atleast = "over";
	    } else {
		reportedLength = scoreDocs.length;
		atleast = "";
	    }

	    //Document doc = s.doc(scoreDocs[0].doc);
	    System.out.println("Lucene query=" + q);
	    System.out.println("" + scoreDocs.length + " results");


	    int pos = startat+1;
	    for(int i=startat; i< scoreDocs.length && i<maxlen; i++) {
		int docno=scoreDocs[i].doc;
		Document doc = searcher.doc(docno);

		// check if it's been "removed" by the user.
		String aid = doc.get(ArxivFields.PAPER);
		if (exclusions!=null && exclusions.containsKey(aid)) {
		    int epos = excludedEntries.size()+1;
		    excludedEntries.add( new ArticleEntry(epos, doc, docno));
		} else {
		    entries.add( new ArticleEntry(pos, doc, docno));
		    pos++;
		}			
     /*
		System.out.println("("+(i+1)+") internal id=" + scoreDocs[i].doc +", id=" +aid);
		System.out.println("arXiv:" + aid);
		System.out.println(doc.get("title"));
		System.out.println(doc.get("authors"));
		System.out.println("Comments:" + doc.get("comments"));
		System.out.println("Subjects:" + doc.get("category"));
			     */
	    }
	}

	static private org.apache.lucene.search.Query mkWordClause(String t) {

	    String f0 = ArxivFields.CATEGORY;
	    String prefix = f0 + ":";
	    if (t.startsWith(prefix)) {
		String w = t.substring(prefix.length());
		return mkTermOrPrefixQuery(f0, w);
	    }

	    for(String f: searchFields) {
		prefix = f + ":";
		if (t.startsWith(prefix)) {
		    String w = t.substring(prefix.length());
		    return mkTermOrPrefixQuery(f, w.toLowerCase());
		}
	    }

	    f0 = "days";
	    prefix = f0 + ":";
	    if (t.startsWith(prefix)) {
		String s = t.substring(prefix.length());
		try {
		    int  days = Integer.parseInt(s);
		    Date since = new Date( (new Date()).getTime() - days *24L* 3600L*1000L);
		    return mkSinceDateQuery(since);
		} catch (Exception ex) { return null; }
	    } else {
		BooleanQuery b = new BooleanQuery(); 
		for(String f: searchFields) {
		    b.add(  mkTermOrPrefixQuery(f, t.toLowerCase()),
			    BooleanClause.Occur.SHOULD);		
		}
		return b;
	    }
	}
    }




   /** Subject search */
    public static class SubjectSearchResults extends SearchResults {
	/**@param cat
	   @param startat How many top search results to skip (0, 25, ...)
	 */
	SubjectSearchResults(String[] cats, Date rangeStartDate, 
			     HashMap<String, Action> exclusions, 
			     int startat) throws Exception {
	    prevstart = Math.max(startat - M, 0);
	    nextstart = startat + M;
	    needPrev = (prevstart < startat);

	    org.apache.lucene.search.Query q;
	    if (cats.length<1) {
		Logging.warning("No categories specified!");
		return;
	    } else if (cats.length==1) {
		q =   mkTermOrPrefixQuery(ArxivFields.CATEGORY, cats[0]);
	    } else {
		BooleanQuery bq = new BooleanQuery();
		for(String t: cats) {
		    bq.add(  mkTermOrPrefixQuery(ArxivFields.CATEGORY, t),
			     BooleanClause.Occur.SHOULD);
		}
		q = bq;
	    }

	    if ( rangeStartDate!=null) {
		q = andQuery( q, mkSinceDateQuery(rangeStartDate));
	    }

	    System.out.println("Lucene query: " +q);

	    Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	    IndexSearcher searcher = new IndexSearcher( indexDirectory);
	    
	    numdocs = searcher.getIndexReader().numDocs() ;
	    System.out.println("index has "+numdocs +" documents");
	    
	    int maxlen = startat + M;
	    reportedLuceneQuery=q;
	    TopDocs 	 top = searcher.search(q, maxlen + 1);
	    scoreDocs = top.scoreDocs;
	    needNext=(scoreDocs.length > maxlen);
	    if (needNext) {
		reportedLength =maxlen;
		atleast = "over";
	    } else {
		reportedLength = scoreDocs.length;
		atleast = "";
	    }

	    //Document doc = s.doc(scoreDocs[0].doc);
	    System.out.println("" + scoreDocs.length + " results");


	    int pos = startat+1;
	    for(int i=startat; i< scoreDocs.length && i<maxlen; i++) {
		int docno=scoreDocs[i].doc;
		Document doc = searcher.doc(docno);

		// check if it's been "removed" by the user.
		String aid = doc.get(ArxivFields.PAPER);
		if (exclusions!=null && exclusions.containsKey(aid)) {
		    int epos = excludedEntries.size()+1;
		    excludedEntries.add( new ArticleEntry(epos, doc, docno));
		} else {
		    entries.add( new ArticleEntry(pos, doc, docno));
		    pos++;
		}			
	    }
	}
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


    static BooleanQuery andQuery( org.apache.lucene.search.Query q1, 
				  org.apache.lucene.search.Query q2) {
	if (q1 instanceof BooleanQuery) {
	    BooleanQuery b = (BooleanQuery)q1;
	    b.add( q2,  BooleanClause.Occur.MUST);
	    return b;
	} else if  (q2 instanceof BooleanQuery) {
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

    /** For testing */
    static public void main(String[] argv) throws Exception {
	if (argv.length==0) usage();

	ParseConfig ht = new ParseConfig();
	//int maxDocs = ht.getOption("maxDocs", -1);
	boolean cat = ht.getOption("cat", false);

	int days=ht.getOption("days", 0);
	Date since = (days <=0) ? null:
	    new Date( (new Date()).getTime() - days *24L* 3600L*1000L);

	String s="";
	for(String x: argv) {
	    if (s.length()>0) s += " ";
	    s += x;
	}

	System.out.println( (cat? "Subject search":"Text search") + " for: " + s );

	HashMap<String, Action> exc = new HashMap<String, Action>();
	SearchResults sr =
	    cat? 
	    new SubjectSearchResults(argv, since, exc, 0) :
	    new TextSearchResults(s, exc, 0);

	IndexReader reader = ArticleAnalyzer.getReader();

	int cnt=0;
  	for(ScoreDoc q: sr.scoreDocs) {
	    cnt++;
	    int docno = q.doc;
	    Document d = reader.document(docno);
	    System.out.println("["+cnt+"] " + d.get(ArxivFields.PAPER) +
			       "; " + d.get(ArxivFields.CATEGORY) +
			       "; " + d.get(ArxivFields.TITLE) +
			       " ("+ d.get(ArxivFields.DATE)+")");

	}
     
    }

}
