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
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.recommender.Scheduler;

/** This class is responsible for the retrieval, formatting, and
    displaying of a "suggestion list": a list of articles which some kind
    of automatic process has marked as potentially interesting to the
    user. 
*/
public class ViewSuggestions  extends ViewSuggestionsBase {

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

	EntityManager em = sd.getEM();
	try {
	    
	    startat = (int)Tools.getLong(request, STARTAT,0);

	    // One very special mode: database ID only. This is used 
	    // for navigation inside the "view activity" system
	    long id = Tools.getLong(request, "id", 0);
	    if (id>0) {
		df = (DataFile)em.find(DataFile.class, id);
		if (df==null) throw new WebException("No suggestion list with id=" + id + " exists");
		actorUserName = df.getUser();
	    }

	    if (actorUserName==null) {
		error = true;
		errmsg = "No user name specified!";
		return;
	    }

	    actor=User.findByName(em, actorUserName);
	    User.Program program = actor.getProgram();
	    if (program==null) {
		throw new WebException("Not known what experiment plan user "+actor+" is enrolled into");
	    } else if (program==User.Program.SET_BASED) { // fine!
	    } else if (program.needBernoulli()) {
		throw new WebException("User "+actor+" is enrolled into Bernoulli plan, not set-based!");
	    } else if (program==User.Program.EE4) {
		if (!mainPage) throw new WebException("For users in program " + program +", suggestion list can only be viewed in the user's main page");
	    } else {
		throw new WebException("This tool does not support suggestion list view for program=" + program);
	    }


	    // Special modes
	    if (id>0) {  // displaying specific file
		initList(df, startat, null, em);
		return;
	    } else if (mainPage) {
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
	    
	    final int maxDays=Scheduler.maxRange;
	    
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
		initList(df, startat, null, em);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    //em.close(); 
	}
    }

    /** Generates the list for the main page. This has simpler logic
     than the general view suggestion page, which has lots of special
     modes and options. */
    private void initMainPage(EntityManager em, User actor) throws Exception {
	if (error || user==null) return; // no list needed
	// disregard most of params
	User.Program program = actor.getProgram();
	teamDraft = (program==User.Program.SET_BASED && actor.getDay()==User.Day.EVAL);
	basedon=null;
	mode = (program==User.Program.EE4) ?
	    DataFile.Type.EE4_SUGGESTIONS :
	    DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1;
	
	if (expert || force) throw new WebException("The 'expert' or 'force' mode cannot be used on the main page");

	// Look for the most recent sugestion list based on
	// the specified user profile file... 
	// Any day range ("-1") is accepted, because the user may have changed 
	// the range recently
	//	basedonType =DataFile.Type.TJ_ALGO_2_USER_PROFILE;
	//	df = DataFile.getLatestFileBasedOn(em, actorUserName, 
	//				   mode, -1, basedonType);


	/** Disregard source type (the initial file, created "on the fly",
	    has no source at all!) */
	df = DataFile.getLatestFile(em, actorUserName, mode);

	onTheFly = (df==null);
       
	if (onTheFly) {
	    //	    days =actor.getDays();
	    //	    if (days<=0) days = Search.DEFAULT_DAYS;

	    Date since = SearchResults.daysAgo(Scheduler.maxRange);
	    initList(null, startat, since, em, true);

	} else {
	    days = df.getDays();
	    initList(df, startat, null, em, true);
	}
	    	    
    }

   private void initList(DataFile df, int startat, 
			  Date since, EntityManager em) throws Exception {
       initList(df,startat, since, em, false);
   }

    /**
       @param df The data file to read. We will either display the
       suggestion list from this file, or (if in the teamDraft mode)
       mix it with the catSearch results.  If null is given, we are in
       the onTheFly mode, and create the list right here
       @param since Only used in the onTheFly mode. (Otherwise, the date
       range is picked from the data file).
       @param em Just so that we could save the presented list
       @param mainPage Only affects the marker recorded in the new PresentedList  entry
     */
    private void initList(DataFile df, int startat, 
			  Date since, EntityManager em, boolean mainPage) throws Exception {

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

	if (df==null) {
	    // FIXME: do it now!
	    if (mode!= DataFile.Type.LINEAR_SUGGESTIONS_1) throw new WebException("Sorry, your EE4 suggestion list has not been computed yet!");

	    // The on-the-fly mode: simply generate and use cat search results for now
	    sr = catSearch(searcher, since);    

	    // Save the list? Nah, too much trouble (file permissions etc)
	    //saveResults(em,searcher, sr, since );
	} else if (teamDraft) {
	    // The team-draft mode: merge the list from the file with
	    // the cat search res
	    SearchResults asr = new SearchResults(df, searcher);
	    
	    since = df.getSince();
	    if (since == null) since = SearchResults.daysAgo( days );
	    SearchResults bsr = catSearch(searcher, since);
		    
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
	for(int i=0; i<sr.entries.size(); i++) {
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
 	Action.Source srcType = mainPage?
	    (mode ==  DataFile.Type.EE4_SUGGESTIONS?  Action.Source.MAIN_EE4:
	     teamDraft? Action.Source.MAIN_MIX : Action.Source.MAIN_SL) :
	    Action.Source.VIEW_SL;
	PresentedList plist=sr.saveAsPresentedList(em, srcType, actorUserName,
						   df, null);
	asrc= new ActionSource(srcType, plist.getId());
    }
    
    private SearchResults catSearch(IndexSearcher searcher, Date since) throws Exception {
	int maxlen = 10000;
	SearchResults bsr = 
	    SubjectSearchResults.orderedSearch( searcher, actor, since, maxlen);
	if (bsr.scoreDocs.length>=maxlen) {
	    String msg = "Catsearch: At least, or more than, " + maxlen + " results found; displayed list may be incomplete";
	    Logging.warning(msg);
	    infomsg += msg + "<br>";
	}
	return bsr;
    }

    /** The plan was to call this when a new sugg list is created on the fly.
	But we don't do it, because of the problem with file permissions etc.
     */
    void saveResults(EntityManager em, IndexSearcher searcher, SearchResults sr, Date since) throws IOException {
	DataFile outputFile= new DataFile(actorUserName, 0, DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1);
	outputFile.setDays(Scheduler.maxRange);
	outputFile.setSince(since);
	// FIXME: ought to do fillArticleList as well, maybe
	// through a TaskMaster job
	sr.setWindow( searcher, 0, sr.scoreDocs.length , null);
	File f = outputFile.getFile();
	ArticleEntry.save(sr.entries, f);
	em.persist(outputFile);
	// FIXME: READABLE  by EVERYONE, eh?
	outputFile.makeReadable();
    }
		    
    public String forceUrl() {
	String s = cp + "/viewSuggestions.jsp?" + USER_NAME + "=" + actorUserName;
	s += "&" + FORCE + "=true";
	s += "&" + MODE + "=" +mode;
	if (days!=0) 	    s +=  "&" + DAYS+ "=" +days;
	return s;
    }

    /** Overrides the method in ResultsBase */
    //void customizeSrc() {
    //	asrc= new ActionSource(teamDraft? Action.Source.MAIN_MIX : Action.Source.MAIN_SL,
    //				df != null ? df.getId() : 0);
    //}

    /** Generates the explanation text that's inserted before the suggestion list. */
    public String describeList() {
	String s = "";
	if (onTheFly) {
	    s += "<p>This is the initial suggestion list generated in run time.</p>\n";
	} else {
	    s += "<p>Suggestion list " + 
		researcherSpan(df.getThisFile()) + 
		" was generated for user " + df.getUser() + " at: " + 
		Util.ago(df.getTime()) + ".";
	    //"; served at "+new Date()+".\n"; 

	    Date since = df.getSince();
	    s +=  "It contains some articles of possible interest to you selected from ";
	    if (since!=null) {
		s += "those released since " + since +".";
	    } else {
		s += days>0? 
		    "those released in the last " + days + " days." :
		    "the entire article archive (all times).";		
	    }

	    if (df.getInputFile()!=null) {
		String q=" The list was generated by applying the user profile "+
		Html.a( viewProfileLink(df.getInputFile()), df.getInputFile())+
		    " to these articles.";
		s += researcherSpan(q);
	    }

	    if (mode==DataFile.Type.LINEAR_SUGGESTIONS_1) {
		String q = "Merge=" + teamDraft;
		s += researcherP(q);
	    }
	}

	s += super.describeList();
	return s;
    }

    public String excludedPagesMsg() {
	String s="";
	boolean excludeViewed = (actor!=null) && actor.getExcludeViewedArticles();

	if (sr.excludedEntries.size()>0) { 
	    s += "<div><small><p>We have excluded " +
		sr.excludedEntries.size() + 
		" articles from the list ";
	    s += sr.needPrev? "(including previous pages)": "";
	    
	    if (excludeViewed) { 
		s += " because you have earlier asked not to show them anymore, or because you have already viewed them. To see the complete list of the pages you've viewed or rated, please refer to your " +
		    Html.a( "personal/viewActionsSelfDetailed.jsp",
			    "activity history") +
		    ".</p>\n";

		s += "<p>Note: Presently, already-viewed pages are excluded from the  the recommendation list. You can choose to have them shown. To do this, go to your " +
		    Html.a( "personal/editUserFormSelf.jsp", "settings") +
		    " and toggle the \"exclude already-viewed articles\" flag.</p>\n";

	    } else { 
		s += " because you have earlier asked not to show them anymore, or because you have moved them to your " +
		    Html.a("personal/viewFolder.jsp", "personal folder") +
		    ".</p>\n";

		s += "<p>Note: You can choose to have already-viewed pages removed from your recommendation lists. To do this, go to your " +
		    Html.a( "personal/editUserFormSelf.jsp", "settings") +
		    " and toggle the \"exclude already-viewed articles\" flag.</p>\n";
		
	    } 
	    s += "</small></div>\n";
	}
	return s;
    }	


    /** Generates a message explaining that no sugg list is available, and why */
    public String noListMsg() {
	String s= super.noListMsg();
	
	if (activeTask!=null) {
	    s += 		"<p>" +
		"Presently, My.Arxiv's recommendation engine is working on generating a suggestion list for you (task no. " + activeTask.getId() + 
		"). You may want to wait for a couple of minutes, and then refresh this page to see if the list is ready.</p>\n";
	} else if (queuedTask!=null) { 
	    s += 		"<p>" +
		"Presently,  a task is queued to generate a suggestion list for you (task no. " +
		queuedTask.getId() + 
		"). You may want to wait for a few minutes, and then refresh this page to see if it has started and completed.</p>\n";
	} else if (actor.catCnt()==0) {
	    s +=		"<p>" +
		"It appears that you have not specified any subject categories of interest. Please <a href=\"personal/editUserFormSelf.jsp\">modify your user profile</a>, adding some categories!</p>\n";
	} else {
	    s +=		"<p>" +
		"Perhaps you need to wait for a while for a recommendation list to be generated, and then reload this page.";
	}
	return s;
    }

}

