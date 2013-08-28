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

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.recommender.Scheduler;
import edu.rutgers.axs.ee4.Daily;

/** This class is responsible for the retrieval, formatting, and
    displaying of a "suggestion list": a list of articles which some kind
    of automatic process has marked as potentially interesting to the
    user. 
*/
public class ViewSuggestions  extends ViewSuggestionsBase {

    public Task activeTask = null, queuedTask=null, newTask=null;

    /** This flag is set by this module if it has found no suitable
	data file, but instead generated a suggestion list directly
	when processing the HTTP request. This is done in our June
	2012 experiment for very recently created users, who may not
	have user profiles yet.
     */
    public boolean onTheFly = false;

     /** Data file whose content is to be displayed */
    public DataFile df =null;

    /** The type of the requested suggestion list, as specified by the
     * HTTP query string*/
    public DataFile.Type mode= 	    DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1;
    /** Date range on which is the list should be based. (0=all time). */
    public int days= 0;
    /** The starting point of the data range */
    public Date since = null;
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

    /** If true, we want to generate a list to be put into an email
     message, rather than into the web page. The list of articles will
     be the same, but a different source code will be saved in the asrc
     variable and in the PresentedList entry. 

     This variable is set in the special (no-web) constructor.
    */
    private boolean isMail = false;

    /** Set this flag to true if we do not want to record a PresentedList */
    private boolean dryRun = false;

    /** The id of the PresentedList object that describes the URL list 
	shown to the user. Typically, the list is generated in this 
	ViewSuggestion call; but there is also a "restore list" mode,
	when a previously generated list is displayed.
    */
    public long plid = 0;

    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response) {
	this(_request, _response, false);
    }

    /** The main constructor, invoked from the My.ArXiv's main page
	(index.jsp), as well as from the "view suggestions" page
	(viewSuggestions.jsp).
      
       @param mainPage If true, we're preparing a list to appear on
       the main page. This involves much simpler logic than the
       general-purpose page viewSuggestion.jsp
     */
    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response, boolean mainPage) {
	super(_request,_response);


	mainPage =getBoolean("main", mainPage);
	infomsg += "mainPage=" + mainPage +", isSelf=" + isSelf + "<br>\n";

	if (error) return; // authentication error?

	EntityManager em = sd.getEM();
	try {
	    
	    // A special mode used for  accessing the (possibly dated) main
	    // page from an email message
	    plid = Tools.getLong(request, "plid", 0);
	    if (plid != 0) {
		PresentedList plist=(PresentedList)em.find(PresentedList.class, plid);
		if (plist==null) 	throw new WebException("PresentedList " +plid+ " does not exist");
		long dfid = plist.getDataFileId();
		df = (dfid==0)? null : (DataFile)em.find(DataFile.class, dfid);
		actorUserName = plist.getUser();
		isSelf = (actorUserName!=null && actorUserName.equals(user));
		
		if (!isSelf) {
		    if (mainPage) {
			throw new WebException("You are trying to view a recommendation list (plid="+plid+") that was generated for a different user ("+actorUserName+"). This may happen, for example, if you have several user accounts, or if you are using a shared computer. Please make sure to log in with a correct user name. Currently, you are logged in as " + user);
		    }
		    if (!runByResearcher()) {
			throw new WebException("Users are not allowed to view other users' recommendations");
		    }  
	    	}
		presentOldList(em, plist);
		return;
	    }


	    // One very special mode: data file ID only. This is used
	    // for navigation inside the "view activity" system; this
	    // is also used when accessing the (possibly dated) main
	    // page from an email message
	    long id = Tools.getLong(request, "id", 0);
	    if (id>0) {
		df = (DataFile)em.find(DataFile.class, id);
		if (df==null) throw new WebException("No suggestion list with id=" +id+ " exists");
	
		DataFile.Type type = df.getType();
		if (type.isProfile()) {
		    // A special situation: a somewhat mistaken call from
		    // the page displayed by QueryServlet (due to the link=...
		    // atribute in DataFile.id
		    String redirect =  "viewUserProfile.jsp?" + ID + "=" +id;
	    
		    //String eurl = response.encodeRedirectURL(redirect);
		    _response.sendRedirect(redirect);
		}

		actorUserName = df.getUser();
		isSelf = (actorUserName!=null && actorUserName.equals(user));
		if (!isSelf) {
		    if (mainPage) {
			throw new WebException("You are trying to view a recommendation list that was generated for a different user. This may happen, for example, if you have several user accounts, or if you are using a shared computer. Please make sure to log in with a correct user name");
		    }
		    if (!runByResearcher()) {
			throw new WebException("Users are not allowed to view other users' suggestion lists");
		    }
		}
		
	    }

	    if (actorUserName==null) {
		throw new WebException("No user name specified!");
	    }
	    actor=User.findByName(em, actorUserName);
	    if (actor==null) {
		throw new WebException("There is no user named '"+ actorUserName+"'");
	    }
	    folderSize=actor.getFolderSize();

	    User.Program program = actor.getProgram();
	    if (program==null) {
		throw new WebException("Not known what experiment plan user "+actor+" is enrolled into");
	    } else if (program==User.Program.SET_BASED || program==User.Program.PPP) { // fine!
	    } else if (program.needBernoulli()) {
		throw new WebException("User "+actor+" is enrolled into Bernoulli plan, not set-based!");
	    } else if (program==User.Program.EE4) {
		if (!mainPage) throw new WebException("For users in program " + program +", suggestion list can only be viewed in the user's main page");
	    } else {
		throw new WebException("This tool does not support suggestion list view for program=" + program);
	    }


	    // Special modes
	    if (id>0) {  // displaying specific file
		initList(df, em, false);
		return;
	    } else if (mainPage) {
		infomsg += "initMainPage (df="+df+")<br>\n";
		recordAction(em, actor);  // records user's request, if it was NEXT/PREV page
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
		infomsg += "vs: call DataFile.getLatestFile(em, actorUserName="+actorUserName+", mode="+mode+", days="+days+",requestedFile ="+requestedFile+")<br>\n";
		df = DataFile.findFileByName(em, actorUserName, requestedFile);
	    } else if (basedon!=null) {
		// look for the most recent sugestion list based on
		// the specified user profile file...
		infomsg += "vs: call DataFile.getLatestFile(em, actorUserName="+actorUserName+", mode="+mode+", days="+days+", basedon="+basedon+")<br>\n";
		df = DataFile.getLatestFileBasedOn(em, actorUserName, 
						   mode, days, basedon);
	    } else if (basedonType!=null) {
		infomsg += "vs: call DataFile.getLatestFile(em, actorUserName="+actorUserName+", mode="+mode+", days="+days+", basedonType="+basedonType+")<br>\n";
		df = DataFile.getLatestFileBasedOn(em, actorUserName, 
						   mode, days, basedonType);
	    } else {
		infomsg += "vs: call DataFile.getLatestFile(em, actorUserName="+actorUserName+", mode="+mode+", days="+days+")<br>\n";
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
		DataFile basedonFile = DataFile.findFileByName(em, actorUserName, basedon);
		if (basedonFile ==null) {
		    throw new WebException("We have no record of a DataFile named " + basedon);
		}
		newTask = new Task(actorUserName, taskOp);
		newTask.setDays(days);
		if (basedon!=null) newTask.setInputFile(basedonFile);
		em.persist(newTask);
	    }

	    em.getTransaction().commit();

	    actorLastActionId= actor.getLastActionId();

	    if (df!=null) {
		initList(df, em, false);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    //em.close(); 
	}
    }

    /** Records a NEXT_PAGE or PREV_PAGE action if this page has
	resulted from a request of this kind. This method should only
	be called in mainPage requests. 

	<p>Introduced due to TJ's request that involves recording NEXT_PAGE/
	PREV_PAGE operations. 2013-02.
    */
    private void recordAction(EntityManager em, User actor) {
	Action.Op op = (Action.Op)Tools.getEnum(request, Action.Op.class,
						BaseArxivServlet.ACTION, Action.Op.NONE);	 

	Logging.info("recordAction: op=" + op);
	if (op==Action.Op.NONE) return;
	if (op!=Action.Op.NEXT_PAGE && op!=Action.Op.PREV_PAGE) {
	    Logging.error("Main page Request with op code " + op + " will not be recorded");
	    return;
	} else {
	    Logging.info("Recording page Request with op code " + op + ", asrc="+asrc);
	    infomsg += "Recording page Request with op code " + op + ", asrc="+asrc + "<br>";
	}

	em.getTransaction().begin();
	Action a = actor.addAction(em, null, op, asrc);
	//em.persist(u);	       
	em.getTransaction().commit(); 
    }

    /** Generates the suggestion list to be shown in the main page, or
     in an email message. This method has simpler logic than the
     general view suggestion page, which has lots of special modes and
     options.  

     @param actor The user for whom we need the sugg list
    */
    private void initMainPage(EntityManager em, User actor) throws IOException, WebException
 {
	if (error || actor==null) return; // no list needed
	// disregard most of params
	User.Program program = actor.getProgram();
	teamDraft= (program==User.Program.SET_BASED|| program==User.Program.PPP
		    || program==User.Program.EE4) 
	    && actor.getDay()==User.Day.EVAL;
	basedon=null;
	mode = (program==User.Program.EE4)? DataFile.Type.EE4_SUGGESTIONS :
	    (program==User.Program.SET_BASED)? DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1 :
	    DataFile.Type.PPP_SUGGESTIONS;
	    
	
	if (expert || force) throw new WebException("The 'expert' or 'force' mode cannot be used on the main page");
	
	infomsg += "main: mode=" + mode+"<br>\n";
	infomsg += "main: call DataFile.getLatestFile(em, actorUserName="+actorUserName+", mode="+mode+")<br>\n";

	// Look for the most recent sugestion list based on
	// the specified user profile file... 
	// Any day range ("-1") is accepted, because the user may have changed 
	// the range recently
	//	basedonType =DataFile.Type.TJ_ALGO_2_USER_PROFILE;
	//	df = DataFile.getLatestFileBasedOn(em, actorUserName, 
	//				   mode, -1, basedonType);


	if (df==null) {
	    /** Disregard source type (the initial file, created "on the fly",
		has no source at all!) */
	    df = DataFile.getLatestFile(em, actorUserName, mode);
	}
	infomsg += "main: df=" + df+"<br>\n";

	onTheFly = (df==null);
       
	String q= "otf " + onTheFly ;

	if (onTheFly) {
	    //	    days =actor.getDays();
	    //	    if (days<=0) days = Search.DEFAULT_DAYS;

	    since = SearchResults.daysAgo(Scheduler.maxRange);
	    System.out.println("calling initList OTF");
	    initList(null, em, true);

	} else {
	    days = df.getDays();
	    since = df.getSince();
	    System.out.println("calling initList(df=" + df.getId() + ")");
	    initList(df, em, true);
	}
	    	    
    }

    /** Puts together the list of suggestions (recommendations) to
	display. A variety of modes are supported, depending on the
	circumstances: reading a saved list from a file, generating a
	list on the fly, or merging the two with the team-draft
	algorithm. Whatever list is eventually produced, is both
	displayed to the user and is saved into a PresentedList
	structure in the database, to be available for researchers
	later on.

	<p> The data range starting point, this.since, is used if it's
	already set (which happens in the on-the-fly mode). Otherwise,
	the date range starting point is picked from the data file.

        @param df The data file to read. We will either display the
	suggestion list from this file, or (if in the teamDraft mode)
	mix it with the catSearch results.  If null is given, we are in
	the onTheFly mode, and create the list right here

	@param em Just so that we could save the presented list
	@param mainPage It is true if we want to generate a main-page
	list (either for the web site, or for an email message).
     */
    private void initList(DataFile df, 
			  EntityManager em, boolean mainPage) 
	throws IOException, WebException {

	if (since==null && df!=null) since = df.getSince();

	IndexReader reader=Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	int M = 10; //page size
	
	// The list (possibly empty) of pages that the user does
	// not want ever shown.
	// FIXME: strictly speaking, a positive rating should perhaps
	// "cancel" a "Don't show again" request
	HashMap<String, Action> exclusions = actor.listExclusions();
	
	if (df==null) {

	    if (mode == DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1 ||
		mode == DataFile.Type.PPP_SUGGESTIONS) {

		// The on-the-fly mode: simply generate and use cat search results for now
		sr = catSearch(searcher, since);    
	    } else if (mode== DataFile.Type.EE4_SUGGESTIONS) {
		df = Daily.makeEE4SugForNewUser(em,  searcher,  actor);
		sr = new SearchResults(df, searcher);
	    } else {
		throw new WebException("Sorry, your suggestion list has not been computed yet! (mode="+mode+")");
	    }

	    // Create a DataFile entry, and SQL record of the list,
	    // without the actual disk file 
	    df = saveResults(em,searcher, since );
	} else if (teamDraft) {
	    // The team-draft mode: merge the list from the file (list A) with
	    // the baseline list (cat search res; list B)
	    SearchResults asr = new SearchResults(df, searcher);
	    
	    since = df.getSince();
	    if (since == null) since = SearchResults.daysAgo( days );
	    SearchResults bsr = catSearch(searcher, since);
		    
	    long seed = (actorUserName.hashCode()<<16) | dfmt.format(new Date()).hashCode();
	    // merge
	    sr = SearchResults.teamDraft(asr.scoreDocs, bsr.scoreDocs, seed);
	} else {
	    // simply read the article IDs and scores from the file
	    sr = new SearchResults(df, searcher);
	}
	adjustStartat(em, mainPage);
	sr.setWindow( searcher, startat, M, exclusions);
	if (startat.startat>0 && sr.entries.size()==0) {
	    startat.reset();
	    sr.setWindow( searcher, startat, M, exclusions);	    
	}
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

	// Save the presented section of the suggestion list in the
	// database, and set ActionSource appropriately (to be
	// embedded in the HTML page)
	Action.Source mpType = 
	    (mode==DataFile.Type.EE4_SUGGESTIONS?  
	     (teamDraft? Action.Source.MAIN_EE4_MIX: Action.Source.MAIN_EE4):
	     (teamDraft? Action.Source.MAIN_MIX : Action.Source.MAIN_SL));
  
	Action.Source srcType =
	    mainPage? 	    ( isMail ? mpType.mainToEmail() : mpType) :	   
	    Action.Source.VIEW_SL;
	// Presented list is to be recorded if this is a web page
	// shown to the user himself (rather than to a researcher), or
	// if it's a mail page. There is also a way to explicitly
	// prevent the recording of a PL (the dryRun option)
	if ((isSelf || isMail) && !dryRun) {
	    PresentedList plist=sr.saveAsPresentedList(em,srcType,actorUserName,
						       df, null);
	    plid =  plist.getId();
	}
	asrc= new ActionSource(srcType, plid);
	Logging.info("Set asrc=" + asrc);
    }
    
    /** Prepares to present a list that was already presented in the
	past, and saved in a PresentedList structure. This is
	typically done when a user clicks on an article link in an
	emial message, in order to show exactly the list as it existed
	back then, without any exclusions (due to User Folder  
	
    */
    private void presentOldList(//DataFile df, 
			     EntityManager em,
			     //boolean mainPage,
			     PresentedList plist) 
	throws IOException, WebException {
	

	IndexReader reader=Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	sr = new SearchResults(plist, searcher);
	Action.Source mpType =  plist.getType();
	teamDraft = mpType.isMix();
	isRestored=true;

	searcher.close();
	reader.close();

	asrc= new ActionSource(mpType, plid);
	Logging.info("Set asrc=" + asrc);


    }

    private SearchResults catSearch(IndexSearcher searcher, Date since) throws IOException 
{
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

    /** Checks if paging makes sense, or we're viewing a different
	list now. If the latter, we reset to the 1st page of the
	results (as per TJ, 2013-01-22 meeting). This only applies to
	the main page.
     */
    private void adjustStartat(EntityManager em, boolean mainPage) {
	if (mainPage && startat.startat>0) {

	    // not the most suitable method, but it will do
	      PresentedList lastPl = PresentedList.findLatestPresentedSugList(em,  actor.getUser_name()); 
	    if (lastPl==null) return;
	    if (lastPl.getDataFileId()!= df.getId()) {
		Logging.info("Reset startat from " + startat.startat + " to 0, because there have been no views of DF=" + df.getId() + " yet."); //"Last main page action was " + la.getId());
		startat.reset();
	    }
	}
    }



    /** This method is used to create a new DataFile entry when a new
	suggestion list is created on the fly.  (Typically, soon upon
	the creation of a new user).  

	<p> This method presently does *not* create a disk file, 
	because of problem with file permissions etc. Instead, the data
	will be copied from the SQL table to a disk file later, by 
	TaskMaster.createMissingDataFiles(), from the TaskMaster process.
     */
    private DataFile saveResults(EntityManager em, IndexSearcher searcher, 
				 Date since) throws IOException {
	DataFile outputFile= new DataFile(actorUserName, 0, mode);
	outputFile.setDays(Scheduler.maxRange);
	outputFile.setSince(since);

	// Presently we cannot properly write files from the web
	// application, because of the file ownership/permissions
	// issues.
	final boolean canWriteFiles = false;

	sr.setWindow( searcher, new StartAt(), sr.scoreDocs.length , null);

	if (canWriteFiles) {
	    File f = outputFile.getFile();
	    ArticleEntry.save(sr.entries, f);
	    // FIXME: READABLE  by EVERYONE, eh?
	    outputFile.makeReadable();
	} else {
	    outputFile.setThisFile(null); // cannot write yet!
	}
	outputFile.fillArticleList(sr.entries,  em);

	em.getTransaction().begin(); 
	em.persist(outputFile);
	em.getTransaction().commit();



	return outputFile;
    }

    /** Creates a URL string with a "force" option (to trigger
     * re-creation of a suggestion list etc.)
     */
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

    /** Generates the explanation text that's inserted into the web
	page before the suggestion list. */
    public String describeList() {
	String s = "";
	if (onTheFly) {
	    s += "<p>This is the initial suggestion list generated right now.</p>\n";
	} else if (df==null) {
	    // this generally should not happen
	    s += "<p>List generation information is not presently available</p>";
	} else {
	    String f = df.getThisFile();
	    String x = "no. " +df.getId()+ (f==null? " (no file)" : " ("+f+")");
	    s += "<p>Suggestion list " + researcherSpan(x) + 
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
		    Html.a( viewProfileLink(df.getInputFile().getThisFile()), df.getInputFile().getThisFile())+
		    " to these articles.";
		s += researcherSpan(q);
	    }

	    String q = "Merge=" + teamDraft;
	    s += researcherP(q);
	}

	s += super.describeList();
	return s;
    }

    /** Generates a message that may be inserted into the web page
	after the list of suggestions.
     */
    public String excludedPagesMsg() {
	String s="";
	boolean excludeViewed = (actor!=null) && actor.getExcludeViewedArticles();

	if (sr.excludedEntries.size()>0) { 
	    s += "<div><small><p>We have excluded " +
		sr.excludedEntries.size() + 
		" articles from the list ";
	    s += sr.needPrev? "(including previous pages)": "";
	    
	    if (excludeViewed) { // only in SET_BASED
		s += " because you have earlier asked not to show them anymore, or because you have already viewed them. To see the complete list of the pages you've viewed or rated, please refer to your " +
		    Html.a( "personal/viewActionsSelfDetailed.jsp",
			    "activity history") +    ".";

		s += "<p>Note: Presently, already-viewed pages are excluded from the  the recommendation list. You can choose to have them shown. To do this, go to your " +
		    Html.a( "personal/editUserFormSelf.jsp", "settings") +
		    " and toggle the \"exclude already-viewed articles\" flag.";

	    } else { 
		s += " because you have earlier asked not to show them anymore, or because you have moved them to your " +
		    Html.a("personal/viewFolder.jsp", "personal folder") + ".";

		if (actor.getProgram()==User.Program.SET_BASED) {
		s += "<p>Note: You can choose to have already-viewed pages removed from your recommendation lists. To do this, go to your " +
		    Html.a( "personal/editUserFormSelf.jsp", "settings") +
		    " and toggle the \"exclude already-viewed articles\" flag.";
		}		

	    } 
	    s += "</p>\n</small></div>\n";
	}
	return s;
    }	


    /** Generates a message explaining that no suggestion list is
	available, and why */
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

    /** This constructor is used only to allow one to view a
	suggestion list by running a command-line application, without
	having the web application involved. It produces a similar
	suggestion list to what would be shown to the specified user
	in the main page. It is only used for use in command-line
	applications (for sending email, or for testing), rather than
	inside the web application.

	@param uname The user for whom we want to view suggestions.

	@param cp The context path to use in the URLs to generate.
	Unlike being in a servlet, we can't get the context path out
	of the HTTP request, so it must be provided explicitly.

	@param _dryRun  If the flag is true, this method will not create
	a PresentedList entry in the database. This is suitable for testing
	purposes.
    */
    ViewSuggestions(String uname, String cp, boolean _dryRun) throws IOException, WebException{

	actorUserName=user=uname;

	dryRun = _dryRun;
	isSelf=false;
	// the PresentedList will have a type indicating that this list
	// was presented via email, not via the web site
	isMail = true; 

	// Unlike being in a servlet, we can't get the context path out of the
	// request, so we must set it explicitly
	if (cp==null) {
	    cp =  "http://my.arxiv.org/arxiv";
	}

	EntityManager em =  Main.getEM();
	try {
	    actor=User.findByName(em, actorUserName);
	    if (actor==null) {
		throw new WebException("There is no user named '"+ actorUserName+"'");
	    }
	    User.Program program = actor.getProgram();
	    if (program==null) {
		throw new WebException("Not known what experiment plan user "+actor+" is enrolled into");
	    } else if (program==User.Program.SET_BASED || program==User.Program.PPP) { // fine!
	    } else if (program.needBernoulli()) {
		throw new WebException("User "+actor+" is enrolled into Bernoulli plan, not set-based!");
	    } else if (program==User.Program.EE4) {
	    } else {
		throw new WebException("This tool does not support suggestion list view for program=" + program);
	    }

	    System.out.println("error=" + error+"; calling initMainPage(" + actor.getUser_name() +")");
	    initMainPage(em,  actor);

	    System.out.println("sr=" + sr);

	}  catch (WebException ex) {
	    throw ex;
	}  catch (IOException ex) {
	    throw ex;
	} finally {
	    ResultsBase.ensureClosed( em, true);
	}
    }

    int getDfid() {
	return df==null? 0: df.getId();
    }
    
    /** testing 
     */
    static public void main(String argv[]) throws Exception {
	if (argv.length!=1) {
	    System.out.println("Usage: ViewSuggestions uname");
	    return;
	}
	ParseConfig ht = new ParseConfig();
	String uname = argv[0];
	boolean dryRun = ht.getOption("dryRun", true);

	ViewSuggestions vs = new ViewSuggestions(uname, null, dryRun);
	SearchResults sr = vs.sr; 
	for( ArticleEntry e: sr.entries) {
	    System.out.println(e);
	}

	System.out.println("Sample HTML:");

	//	int dfid = vs.getDfid();
	  
 	for( ArticleEntry e: sr.entries) {
	    int flags = 0;
	    String s =   EmailSug.formatArticleEntryForEmail( e, vs.plid);
	    // vs.resultsDivHTML(e, false, flags);
	    System.out.println(s);
	}
    }

    /** An approximate number to describe the day range of this
	suggestion list. This can be used e.g. in email messages.
	The actual underlying value is in the variable "this.since".
     */
    int estimateEffectiveDays() {
	if (since!=null) {
	    long msec = (new Date()).getTime() - since.getTime();
	    final long n = 24* 3600L * 1000L;
	    int d = (int)(msec/n);
	    if (d == 0) return 1;
	    if ((msec % n) > (n/2)) d ++;
	    return d;
	} else if (days !=0) {
	    return days;
	} else { // this ought not to happen
	    return 0;
	}
    }

}

