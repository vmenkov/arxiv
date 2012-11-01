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

	    // Special modes
	    if (id>0) {
		initList(df, startat, false, em);
		return;
	    } else if (mainPage) {
		initMainPage(em, actor);
		return;
	    } 

	    Made mode = (Mode)getEnum(Mode.class, MODE, Mode.ER);
	    exploitation = (mode == Mode.ET);

	    days = 28; // as per PF's writeup ver 3
	    //	    days =actor.getDays();
	    //	    if (days<=0) days = Search.DEFAULT_DAYS;
	    //	    days = (int)getLong(DAYS, days);

	    
	    final int maxDays=30;
	    
	    if (days < 0 || days >maxDays) throw new WebException("The date range must be a positive number (no greater than " + maxDays+"), or 0 (to mean 'all dates')");


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

    /** Generates the list for the main page */
    private void initMainPage(EntityManager em, User actor) throws Exception {
	if (error || user==null) return; // no list needed
	// disregard most of params
	teamDraft = (actor.getDay()==User.Day.EVAL);
	basedon=null;
	mode = DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1;
	basedonType =DataFile.Type.TJ_ALGO_2_USER_PROFILE;
	//	    days = (int)getLong(DAYS,Search.DEFAULT_DAYS);
	// "-1" means "any horizon"; this way we take care of the case
	// when the user changes the horizon
	// days = (int)getLong(DAYS,-1);
		
	if (expert || force) throw new WebException("The 'expert' or 'force' mode cannot be used on the main page");

	// Look for the most recent sugestion list based on
	// the specified user profile file... 
	// Any day range ("-1") is accepted, because the user may have changed 
	// the range recently
	df = DataFile.getLatestFileBasedOn(em, actorUserName, 
					   mode, -1, basedonType);

	onTheFly = (df==null);
       
	if (df == null) {
	    days =actor.getDays();
	    if (days<=0) days = Search.DEFAULT_DAYS;
	} else {
	    days = df.getDays();
	}
	    	    
	initList(df, startat, onTheFly, em);
    }

    /**
       @param em Just so that we could save the list
     */
    private void initList(DataFile df, int startat, boolean onTheFly, EntityManager em) throws Exception {
	//customizeSrc();

	//	IndexReader reader=ArticleAnalyzer.getReader();
	IndexReader reader=Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	int M = 10; //page size
	
	// The list (possibly empty) of pages that the user does
	// not want ever shown.
	// FIXME: strictly speaking, a positive rating should perhaps
	// "cancel" a "Don't show again" request
	HashMap<String, Action> exclusions = 
	    (actor==null) ? new HashMap<String, Action>() :
	    actor.getExcludeViewedArticles()?
	    actor.getActionHashMap() :
	    User.union(actor.getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN}),
		       actor.getFolder());

	if (onTheFly) {
	    // simply generate and use cat search results for now
	    sr = catSearch(searcher);    
	} else if (teamDraft) {
	    // merge the list from the file with the cat search res
	    SearchResults asr = new SearchResults(df, searcher);
	    
	    SearchResults bsr = catSearch(searcher);
		    
	    long seed =  (actorUserName.hashCode() << 16) | dfmt.format(new Date()).hashCode();
	    // merge
	    sr = SearchResults.teamDraft(asr.scoreDocs, bsr.scoreDocs, seed);
	} else {
	    // simply read the artcile IDs and scores from the file
	    sr = new SearchResults(df, searcher);
	}
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
 	Action.Source srcType = teamDraft? Action.Source.MAIN_MIX : Action.Source.MAIN_SL;
	PresentedList plist=sr.saveAsPresentedList(em, srcType, actorUserName,
						   df, null);
	asrc= new ActionSource(srcType, plist.getId());
    }
    
     private SearchResults catSearch(IndexSearcher searcher) throws Exception {
	int maxlen = 10000;
	SearchResults bsr = 
	    SubjectSearchResults.orderedSearch( searcher, actor, days, maxlen);
	if (bsr.scoreDocs.length>=maxlen) {
	    String msg = "Catsearch: At least, or more than, " + maxlen + " results found; displayed list may be incomplete";
	    Logging.warning(msg);
	    infomsg += msg + "<br>";
	}
	return bsr;
    }
		    
    public String forceUrl() {
	String s = cp + "/viewSuggestions.jsp?" + USER_NAME + "=" + actorUserName;
	s += "&" + FORCE + "=true";
	s += "&" + MODE + "=" +mode;
	if (days!=0) 	    s +=  "&" + DAYS+ "=" +days;
	return s;
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