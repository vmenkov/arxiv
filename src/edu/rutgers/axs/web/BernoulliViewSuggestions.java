package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.indexer.Common;

/** Retrieves and displays a "suggestion list": a list of articles which some
    kind of automatic process has marked as potentially interesting to the user.
 */
public class BernoulliViewSuggestions extends PersonalResultsBase {

    public boolean exploitation = false;

    /** List scrolling */
    public int startat = 0;
    /** the actual suggestion list to be displayed is stored here */
    public SearchResults sr;
 
    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response) {
	this(_request, _response, false);
    }

    /** Exploration and Exploitation mode */
    private static enum Mode {
	ER, ET;
    };


    /**
       @param mainPage If true, we're preparing a list to appear on
       the main page.
     */
    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response, boolean mainPage) {
	super(_request,_response);
	if (error) return; // authentication error?

	EntityManager em = sd.getEM();
	try {
	    
	    if (actorUserName==null) {
		error = true;
		errmsg = "No user name specified!";
		return;
	    }

	    startat = (int)Tools.getLong(request, STARTAT,0);
	    actor=User.findByName(em, actorUserName);


	    Made mode = (Mode)getEnum(Mode.class, MODE, Mode.ER);
	    exploitation = (mode == Mode.ET);

	    days = 28; // as per PF's writeup ver 3
	    //	    days =actor.getDays();
	    //	    if (days<=0) days = Search.DEFAULT_DAYS;
	    //	    days = (int)getLong(DAYS, days);

	    
	    final int maxDays=30;
	    
	    if (days < 0 || days >maxDays) throw new WebException("The date range must be a positive number (no greater than " + maxDays+"), or 0 (to mean 'all dates')");


	    initList( startat, em);

	    em.getTransaction().begin();
	    

	    em.getTransaction().commit();

	    if (df!=null) {
		initList(df, startat, false, em);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    //em.close(); 
	}
    }

    /**
       @param em Just so that we could save the list
     */
    private void initList(int startat, EntityManager em) throws Exception {
	//customizeSrc();

	IndexReader reader=Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	int M = 10; //page size
	
	// All viewed (and judged) articles are excluded from presentation
	HashMap<String, Action> exclusions = 	    actor.getActionHashMap();

	// eligible candidates: based on categories and date range
	sr = catSearch(searcher);    

	// score based 
	


	sr.setWindow( searcher, startat, M, exclusions);
	ArticleEntry.applyUserSpecifics(sr.entries, actor);
	
	// In docs to be displayed, populate other fields from Lucene
	for(int i=0; i<sr.entries.size()// && i<maxRows
		; i++) {
	    ArticleEntry e = sr.entries.elementAt(i);
	    int docno = e.getCorrectDocno(searcher);
	    Document doc = reader.document(docno);
	    e.populateOtherFields(doc);
	}
	searcher.close();
	reader.close();

	// Save the presented (section of the) suggestion list in the
	// database, and set ActionSource appropriately (to be
	// embedded in the HTML page)
 	Action.Source srcType = 	    exploitation?
	    Action.Source.BERNOULLI_EXPLOITATION :
	    Action.Source.BERNOULLI_EXPLORATION;	   

	PresentedList plist=sr.saveAsPresentedList(em, srcType, actorUserName,
						   df, null);
	asrc= new ActionSource(srcType, plist.getId());
    }
    
     private SearchResults catSearch(IndexSearcher searcher) throws Exception {
	int maxlen = 10000;

	//String[] cats = actor.getCats().toArray(new String[0]);
	String[] cats = {"cs.AI", "cs.IR", "stat.ML"};
	Date since = SearchResults.daysAgo( days );
	SubjectSearchResults sr = new SubjectSearchResults(searcher, cats, since, maxlen);
	sr.reorderCatSearchResults(searcher.getIndexReader(), cats, since);

	if (sr.scoreDocs.length>=maxlen) {
	    String msg = "Catsearch: At least, or more than, " + maxlen + " results found; displayed list may be incomplete";
	    Logging.warning(msg);
	    infomsg += msg + "<br>";
	}
	return sr;
    }
		    
    /** Wrapper for the same method in ResultsBase. */
    public String resultsDivHTML(ArticleEntry e) {
	return resultsDivHTML(e, isSelf);
    }

    /** Overrides the method in ResultsBase */
    //void customizeSrc() {
    //	asrc= new ActionSource(teamDraft? Action.Source.MAIN_MIX : Action.Source.MAIN_SL,
    //				df != null ? df.getId() : 0);
    //}


}