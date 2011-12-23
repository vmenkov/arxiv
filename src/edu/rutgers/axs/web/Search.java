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

/** The base class for the classes that are behind the Frontier Finder
 * Lite JSP pages.
 */
public class Search extends ResultsBase {
    
    public final static int M=25;

    public String query, queryEncoded ;
    public SearchResults sr;
    public int startat = 0;

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

	    try {
		startat = Integer.parseInt(request.getParameter("startat"));
		if (startat<0) startat=0;
	    } catch(Exception _e) {}

	    User u = null;

	    if (user!=null) {
		em = sd.getEM();
		u = User.findByName(em, user);    
	    }

	    // Pages the user does not want ever shown (may be empty)
	    HashMap<String, Action> exclusions = 
		(u==null) ? new HashMap<String, Action>() :
		u.getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN});
	    // Pages currently in the user's folder
	    HashMap<String, Action> folder = 
		(u==null) ? new HashMap<String, Action>() :
		new HashMap<String, Action>();

	    sr  = new SearchResults(query, exclusions, startat);
	    sr.markFolder(folder );

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
	    
	SearchResults(String query, HashMap<String, Action> exclusions, int startat) throws Exception {
	    prevstart = Math.max(startat - M, 0);
	    nextstart = startat + M;
	    needPrev = (prevstart < startat);

	    String terms[]= query.toLowerCase().split("[^a-zA-Z0-9_]+");
	    BooleanQuery q = new BooleanQuery();
	    final String [] fields = {"paper", "title", "authors", "abstract", "article"};
	    int tcnt=0;
	    for(String t: terms) {
		if (t.trim().length()==0) continue;
		BooleanQuery b = new BooleanQuery(); 	
		for(String f: fields) {
		    TermQuery tq = new TermQuery(new Term(f, t));
		    b.add( tq, BooleanClause.Occur.SHOULD);		
		}
		q.add( b,  BooleanClause.Occur.MUST);
		tcnt++;
	    }
	    if (tcnt==0) throw new WebException("Empty query");
	
	    Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	    IndexSearcher searcher = new IndexSearcher( indexDirectory);
	    
	    numdocs = searcher.getIndexReader().numDocs() ;
	    System.out.println("index has "+numdocs +" documents");
	    
	    int maxlen = startat + M;
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


	    int pos = 1;
	    for(int i=startat; i< scoreDocs.length && i<maxlen; i++) {
		Document doc = searcher.doc(scoreDocs[i].doc);

		// check if it's been "removed" by the user.
		String aid = doc.get("paper");
		if (exclusions!=null && exclusions.containsKey(aid)) {
		    int epos = excludedEntries.size()+1;
		    excludedEntries.add( new ArticleEntry(epos, doc));
		} else {
		    entries.add( new ArticleEntry(pos, doc));
		    pos++;
		}			
     /*
		System.out.println("("+(i+1)+") internal id=" + scoreDocs[i].doc +", id=" + doc.get("paper"));
		System.out.println("arXiv:" + doc.get("paper"));
		System.out.println(doc.get("title"));
		System.out.println(doc.get("authors"));
		System.out.println("Comments:" + doc.get("comments"));
		System.out.println("Subjects:" + doc.get("category"));
			     */
	    }
	}

	void markFolder(HashMap<String, Action> folder ) {
	    for(ArticleEntry e: entries) {
		e.isInFolder = folder.containsKey(e.id);
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


    /** For testing */
    static public void main(String[] argv) throws Exception {
	if (argv.length==0) usage();
	String s="";
	for(String x: argv) {
	    if (s.length()>0) s += " ";
	    s += x;
	}
	HashMap<String, Action> exc = new HashMap<String, Action>();
	SearchResults sr = new SearchResults(s, exc, 0);
       
    }

}
