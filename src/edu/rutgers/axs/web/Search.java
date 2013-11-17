package edu.rutgers.axs.web;

import java.net.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** Our interface for Lucene searches
 */
public class Search extends ResultsBase {
    
    public String query, queryEncoded ;
    /** the actual search results list is stored here */
    public SearchResults sr;
    /** List "paging" */
    public StartAt startat;

    /** Used for cat search, to restrict date range (the default) */
    static final public int DEFAULT_DAYS=7;
    /** Used for cat search, to restrict date range */
    public int days=DEFAULT_DAYS;

    static final String DAYS="days",
	SIMPLE_SEARCH="simple_search", USER_CAT_SEARCH="user_cat_search";

    /** The web interface to various searches that My.ArXiv can carry out
      over its own Lucene article repository. In the user_cat_search
      mode, it retrieves all recent articles from the user's
      categories of interest. Otherwise, a user-entered query is
      expected. In this case, we conduct either phrase search (when
      the entire query is double-quoted) or keyword search.
     */
    public Search(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	startat = new StartAt(request);

	if (error) return;

	//customizeSrc();

	EntityManager em = null;
	try {
	    query = request.getParameter(SIMPLE_SEARCH);
	    boolean user_cat_search = getBoolean( USER_CAT_SEARCH, false);

	    if (user_cat_search) { 
		if (query != null) {
		    error=true;
		    errmsg="You cannot use " + SIMPLE_SEARCH + " and " + USER_CAT_SEARCH + " at the same time!";
		    return;		    
		} else if (user==null) {
		    error = true;
		    errmsg = "Not logged in";
		    return;
		}
	    } else if (query==null || query.trim().equals("")) {
		error=true;
		errmsg="No query entered";
		return;
	    } else {		// simple_search, by query 
		queryEncoded = URLEncoder.encode(query);
	    }

	    // just checking
	    SessionData sd = SessionData.getSessionData(request);  
            edu.cornell.cs.osmot.options.Options.init(sd.getServletContext() );

	    em = sd.getEM();
	    User u = (user!=null) ?  User.findByName(em, user) : null;    

	    // The list (possibly empty) of pages that the user does
	    // not want ever shown 
	    HashMap<String, Action> exclusions = 
		(u==null) ? new HashMap<String, Action>() :
		u.getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN});

	    IndexReader reader = Common.newReader();
	    IndexSearcher searcher = new IndexSearcher( reader);

	    // "Display window" size (how many results are displayed on one screen)
	    //	    final int M=25;
	    final int M=10;

	    int maxlen = ( user_cat_search ? 10000 : 100);
	    int necessary = startat.startat + M + exclusions.size() + 1;
	    if (maxlen <= necessary) maxlen = necessary;
	    
	    if (user_cat_search) {
		String[] cats = u.getCats().toArray(new String[0]);
		if (u!=null) days=u.getDays(); // Use user-specific horizon!
		if (days<=0) days = DEFAULT_DAYS;
		days = getInt( DAYS, days);
		Date since = SearchResults.daysAgo( days );
		sr = new SubjectSearchResults(searcher, cats, since, maxlen);
		if (sr.scoreDocs.length>=maxlen) {
		    String msg = "Catsearch: At least, or more than, " + maxlen + " results found; displayed list may be incomplete";
		    Logging.warning(msg);
		    infomsg += msg + "<br>";
		}
		sr.reorderCatSearchResults(reader, cats);
	    } else {
		sr  = new TextSearchResults(searcher, query,  maxlen);
	    }
	    sr.setWindow( searcher, startat, M, exclusions);

	    searcher.close();

	    if (u!=null) {
		// Mark pages currently in the user's folder, or rated by the user
		ArticleEntry.markFolder(sr.entries, u.getFolder());
		ArticleEntry.markRatings(sr.entries, 
					 u.getActionHashMap(Action.ratingOps));
	    }

	    EnteredQuery eq=null;
	    if (!user_cat_search) {
		em.getTransaction().begin();
		if (user!=null && u!=null) {
		    u = User.findByName(em, user); // re-read, just in case   
		    eq=u.addQuery(query, sd, sr.nextstart.startat, sr.scoreDocs.length);
		    // FIXME: ideally, we may want to store a link to
		    // EQ as part of the ActionSource info of recorded actions
		    em.persist(u);
		} else {
		    // anon action
		    EnteredQuery r = new EnteredQuery(null, sd, query,
						      sr.nextstart.startat, sr.scoreDocs.length);
		    em.persist(r);
		}
		em.getTransaction().commit(); 
	    }

	    PresentedList plist = null;
	    // Save the presented search results in the database
	    plist=sr.saveAsPresentedList(em, Action.Source.SEARCH, u, null,eq);	
	    if (em!=null) em.close();

	    long plid = (plist==null? 0: plist.getId());
	    // ActionSource, for use in the web page 
	    asrc = new ActionSource(Action.Source.SEARCH,plid);

	    // record the list of presentd URLs in the SessionData, for 
	    // potential use (in the excliusion list) by the SBR
	    sd.recordLinkedAids(sr.entries);

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

    /** Overrides the method in ResultsBase */
    //    void customizeSrc() {    }

    public String onLoadJsCode() {
	return refreshEverythingJsCode();
    }

}


