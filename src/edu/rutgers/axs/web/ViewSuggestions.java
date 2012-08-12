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
import edu.rutgers.axs.recommender.*;

/** Retrieves and displays a "suggestion list": a list of articles which some
    kind of automatic process has marked as potentially interesting to the user.
 */
public class ViewSuggestions extends PersonalResultsBase {

    public Task activeTask = null, queuedTask=null, newTask=null;

    /** This flag is set by this module if it has found no suitable
	data file, but generated a suggestion list directly when
	processing the HTTP request. This is done in our June 2012
	experiment for very recently created users, when the user has
	no profile yet.
     */
    public boolean onTheFly = false;

     /** Data file whose content is to be displayed */
    public DataFile df =null;

    /** The type of the requested suggestion list, as specified by the
     * HTTP query string*/
    public DataFile.Type mode= DataFile.Type.LINEAR_SUGGESTIONS_1;
    /** Date range on which is the list should be based. (0=all time). */
    public int days= 0;
    /** Max length to display. (Note that the suggestion list
     * generator may have its own truncation criteria!)  */
    //    public static final int maxRows = 100;
    
    /** If this is supplied, this specifies the particular user
	profile on which the requested suggestion list must be based.
     */
    public String basedon=null;
    /** If this is supplied, this specifies the type of the source file
	on which the requested suggestion list must be based.
     */
    DataFile.Type basedonType = null;

    static final String TEAM_DRAFT = "team_draft";
    public boolean teamDraft = false;

    private static DateFormat dfmt = new SimpleDateFormat("yyyyMMdd");

    /** List scrolling */
    public int startat = 0;
    /** the actual suggestion list to be displayed is stored here */
    public SearchResults sr;
 
    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response) {
	this(_request, _response, false);
    }

    private static enum Mode {
	SUG2, CAT_SEARCH, TEAM_DRAFT;
    };


    /**
       @param mainPage If true, we're preparing a list to appear on
       the main page.
     */
    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response, boolean mainPage) {
	super(_request,_response);
	if (error) return; // authentication error?

	if (actorUserName==null) {
	    error = true;
	    errmsg = "No user name specified!";
	    return;
	}

	EntityManager em = sd.getEM();
	try {

	    actor=User.findByName(em, actorUserName);
	    startat = (int)Tools.getLong(request, STARTAT,0);
	    
	    if (mainPage) {
		initMainPage(em, actor);
		return;
	    } 

	    mode = (DataFile.Type)getEnum(DataFile.Type.class, MODE, mode);
	    basedon=getString(BASEDON,null);
	    basedonType =  (DataFile.Type)getEnum(DataFile.Type.class, BASEDON_TYPE,null); // DataFile.Type.NONE);

	    days =actor.getDays();
	    if (days<=0) days = Search.DEFAULT_DAYS;
	    days = (int)getLong(DAYS, days);

	    // Looking at some request params. This is used as the standard
	    // setup in the standalone page, or as the override in the
	    // main page environment
	    teamDraft =getBoolean(TEAM_DRAFT, teamDraft);
	
	    Task.Op taskOp = mode.producerFor(); // producer task type
	    
	    final int maxDays=30;
	    
	    if (days < 0 || days >maxDays) throw new WebException("The date range must be a positive number (no greater than " + maxDays+"), or 0 (to mean 'all dates')");

	    if (force && !expert) {
		throw new WebException("The 'force' mode can only be used together with the 'expert' mode");
	    }	

	    em.getTransaction().begin();
	    
	    if (requestedFile!=null) {
		df = DataFile.findFileByName(em, actorUserName, requestedFile);
	    } else if (basedon!=null) {
		// look for the most recent sugestion list based on
		// the specified user profile file...
		df = DataFile.getLatestFileBasedOn(em, actorUserName, 
						   mode, days, basedon);
	    } else if (basedonType!=null) {
		df = DataFile.getLatestFileBasedOn(em, actorUserName, 
						   mode, days, basedonType);
	    } else {
		df = DataFile.getLatestFile(em, actorUserName, mode, days);
	    }

	    List<Task> tasks = 
		Task.findOutstandingTasks(em, actorUserName, taskOp);
	    activeTask=queuedTask=null;
	    if (tasks != null) {
		for(Task t: tasks) {
		    if (days>=0 && t.getDays()!=days) continue; // range mismatch
		    if (t.appearsActive()) {
			if ( activeTask==null) 	activeTask=t; 
			//break;
		    } else if (!t.getCanceled()) {
			if (queuedTask==null) queuedTask = t;
			//break;
		    }
		}
	    }

	    // Do we need to request a new task?
	    boolean needNewTask = false;
	    
	    if (force) {
		if (activeTask!=null) {
		    infomsg += "Update task not created, because a task is currently in progress already";
		} else if (queuedTask!=null) {
		    infomsg += "Update task not created, because a task is currently queued";
		} else if (df != null) {
		    long sec = ((new Date()).getTime() - df.getTime().getTime())/1000;
		    final int minMinutesAllowed = 10;
		    if (sec < minMinutesAllowed * 60) {
			infomsg += "Update task not created, because the most recent update was completed less than " + minMinutesAllowed + 
			    " minutes ago. (Load control).";
		    } else {
			needNewTask = true;
			infomsg += "Update task created as per request";
		    }
		}
	    } else {
		needNewTask= expert && (df==null && activeTask==null && queuedTask==null);
		infomsg += "Update task created since there are no current data to show, and no earlier update task in queue";
	    }

	    if (needNewTask) {
		newTask = new Task(actorUserName, taskOp);
		newTask.setDays(days);
		if (basedon!=null) newTask.setInputFile(basedon);
		em.persist(newTask);
	    }

	    em.getTransaction().commit();

	    actorLastActionId= actor.getLastActionId();

	    if (df!=null) {
		initList(df, startat, false);
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
	    	    
	initList(df, startat, onTheFly);

    }

    private void initList(DataFile df, int startat, boolean onTheFly) throws Exception {
	customizeSrc();

	IndexReader reader=ArticleAnalyzer.getReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	int M = 10; //page size
	
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
	sr.setWindow( searcher, startat, M, null);
	applyUserSpecifics(sr.entries, actor);
	
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
		    
    /** Applies this user's exclusions, folder inclusions, and ratings */
    private void applyUserSpecifics( Vector<ArticleEntry> entries, User u) {
	if (u==null) return;
    
	HashMap<String, Action> exclusions = 
	    u.getActionHashMap(new Action.Op[]{Action.Op.DONT_SHOW_AGAIN});
		    
	// exclude some...
	// FIXME: elsewhere, this can be used as a strong negative
	// auto-feedback (e.g., Thorsten's two-pager's Algo 2)
	for(int i=0; i<entries.size(); i++) {
	    if (exclusions.containsKey(entries.elementAt(i).id)) {
		entries.removeElementAt(i); 
		i--;
	    }
	}

	// Mark pages currently in the user's folder, or rated by the user
	ArticleEntry.markFolder(entries, u.getFolder());
	ArticleEntry.markRatings(entries, 
				 u.getActionHashMap(Action.ratingOps));
    }

    public String forceUrl() {
	String s = cp + "/viewSuggestions.jsp?" + USER_NAME + "=" + actorUserName;
	s += "&" + FORCE + "=true";
	s += "&" + MODE + "=" +mode;
	if (days!=0) 	    s +=  "&" + DAYS+ "=" +days;
	return s;
    }

    /** Generates a URL for a page similar to the currently viewed one,
	but showing a different section of the result list.

	@param startat The value for the "startat" param in the new page's 
	URL.
     */
    public String repageUrl(int startat) {
	String sp = request.getServletPath();
	String qs0=request.getQueryString();
	if (qs0==null) qs0="";

	Pattern p = Pattern.compile("\\b"+ STARTAT + "=\\d+");
	Matcher m = p.matcher(qs0);
	String rep = STARTAT + "=" + startat;
	String qs = m.find()?  m.replaceAll( rep ) :
	    qs0 + (qs0.length()>0 ?  "&" : "") + rep;
	String x = cp + sp + "?" + qs;
	return x;
    }

    /** Wrapper for the same method in ResultsBase */
    public String resultsDivHTML(ArticleEntry e
				 //,				 Article.Source src, long dfid
				 ) {
	return resultsDivHTML(e, isSelf);//, src, dfid);
    }

    /** Overrides the method in ResultsBase */
    void customizeSrc() {
	src = teamDraft? Action.Source.MAIN_MIX : Action.Source.MAIN_SL;
	if (df != null) {
	    dataFileId = df.getId();
	} else {
	    dataFileId = 0;
	}
    }


}