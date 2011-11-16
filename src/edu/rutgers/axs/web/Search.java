package edu.rutgers.axs.web;

import java.net.*;
//import java.sql.*;

import java.io.*;
import java.util.*;
//import java.text.SimpleDateFormat;
//import java.util.regex.*;

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
	    sr  = new SearchResults(query, startat);

	    if (user!=null) {
		em = sd.getEM();
		em.getTransaction().begin();
		User u = User.findByName(em, user);    
		if (u!=null) {
		    u.addQuery(query, sr.nextstart, sr.scoreDocs.length);
		    em.persist(u);
		}
		em.getTransaction().commit(); 
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

    /** Data for a single search result (an article) */
    public static class Entry {
	/** Sequence number in the overall search result sequence */
	public int i;
	public String id, idline, titline, authline, commline, subjline;
	
	Entry(int _i, Document doc) {
	    i = _i;
	    id=doc.get("paper");
	    idline="arXiv:" + id;
	    titline = doc.get("title");
	    authline=doc.get("authors");
	    commline="Comments:" + doc.get("comments");
	    subjline="Subjects:" + doc.get("category");
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
	public Vector<Entry> entries= new Vector<Entry> ();

	/** Links to prev/next pages */
	public int prevstart, nextstart;
	public boolean needPrev, needNext;

	public int reportedLength;
	    
	SearchResults(String query, int startat) throws Exception {
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

	    
	    for(int i=startat; i< scoreDocs.length && i<maxlen; i++) {
		Document doc = searcher.doc(scoreDocs[i].doc);
		entries.add( new Entry(i+1, doc));
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
    }

    public String urlAbstract( String id) {
	return ArticleServlet.mkUrl(cp, id, Action.Op.VIEW_ABSTRACT);
    }

    public String urlPDF( String id) {
	return  ArticleServlet.mkUrl(cp, id, Action.Op.VIEW_PDF);
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
	SearchResults sr = new SearchResults(s, 0);
       
    }

}
