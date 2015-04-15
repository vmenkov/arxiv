package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import java.nio.charset.Charset;
import java.net.URLEncoder;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.RatingButton;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.util.OptionAccess;

/** The "manager" part of the sesion-based recommendation
    generator. We have an SBRGenerator instance in every
    web.SessionData object.

    <p> Recommendation list computation for this class is carried out
    asynchronously, in the run() method of a SBRGThread object (a Java
    thread). At most one SBRGThread may have its thread running at any
    time on behalf of one SBRGenerator object. In this way some level
    of load control is achieved, and unnecessary computations are
    avoided. It is the  SBRGenerator instance which is responsible for
    managing threads in this fashion.

    <P> The SBRGenerator normally has pointers to the object for the
    currently running computational thread, if any (sbrRunning); the most
    recently successfully thread, if any (sbrReady); and the most 
    recent failed thread, if any (sbrFailed).
 */
public class SBRGenerator {

    //    private final boolean sbOnByDefault = true;
    /** Whether this session needs a "moving panel" with session-based 
	recommendations (aka "session buddy") */
    private boolean allowedSB = false; 
    void setAllowedSB(boolean x) { allowedSB =x;}
    private boolean needSBNow = false;
    public boolean getNeedSBNow() { return needSBNow;}

    /** Additional mode parameters for the SB generator */
    public boolean sbDebug = false;
    /** This controls the way of achieving "stable order" of the
	articles in the rec list. (This parameter has nothing to do 
	with merging lists obtained by different methods!)
    */
    public int sbStableOrderMode = 1;

    void validateSbStableOrderMode() throws WebException {
	if (sbStableOrderMode<0 || sbStableOrderMode>2) throw new WebException("Illegal SB merge mode = " + sbStableOrderMode);
    }

    /** If true (= default), the results are merged with the baseline */
    public boolean sbMergeWithBaseline = true;

    /** If true, the SB moving panel will be displayed in "researcher mode".
	Since SB is only shown to users who have not logged in, we can't
	use the usual researcher flag, but rather set this flag via a 
	"secret" query string parameter.
     */
    public boolean researcherSB = false; 


    /** Recommendation list generation methods
     */
    public static enum Method {
	/** Not a real list generation method, but an indicator that
	    this list is to be created by a team-draft merge from two
	    other lists, generated by their specific methods. */
	MERGE,
	    /** Used for testing: the recommendation list is the same as
		the list of articles the user has viewed. */
	    TRIVIAL, 
	    /** Recommendations are based on the article similarity
		(titles + abstracts) */
	    ABSTRACTS, 
	    /** Recommendations are based on the article's historical
		coaccess data */
	    COACCESS,
	    /** Team-draft merge of ABSTRACTS and COACCESS */
	    ABSTRACTS_COACCESS,
	    /** The baseline method: a few most recent articles from 
		appropriate subject categories */
	    SUBJECTS,
	    /** Not an actual method. This param value is used on
		session initialization to randomly choose one of the
		"real" methods. After that, the sbMethod parameter in
		the SBR generator is set to that real method. 
	    */
	    RANDOM,
        /** Collaborative topic Poisson factorization (Gopalan, Charlin, Blei, 2014) */
        CTPF; 
    };

    /**  Recommendation list generation method currently used in this session.
	 This is what's called the "Similarity" parameter in PK's 2014-06-04
	 experiment proposal. This is initialized with NULL, so that we'll
	 detect any attempt at re-initialization.
     */
    Method sbMethod = null;


    /**  Recommendation list generation method that was requested for
	 this session. This can be RANDOM, while sbMethod will contain
	 the actual (randomly chosen) method.
     */
    Method requestedSbMethod = null;

    /** Pointer to a thread object that contains the most recently compiled
	recommendation list ready for display. */
    SBRGThread sbrReady = null;

    /** Pointer to a thread running right now; it will compute the
     * next available list, but it is not ready for display yet. */
    SBRGThread sbrRunning = null;

    /** Link to the most recent failed-run thread object, if any.
     */
    private SBRGThread sbrFailed = null;
    

    /** Used to keep track of the number of SBRG runs we've done so
	far on behalf of this particular session. */
    private int runCnt=0;

    SBRGWorker worker=null;

    /** Enables SB generation, and sets all necessary mode parameters
	etc. This method may be invoked directly (from
	SessionBased.java, when the user explicitly loads
	sessionBased.jsp), or from setSBFromRequest() (which happens
	whenever the user loads any page with sb=true in the URL).

	<p>It may have been more logical to put this functionality
	into the constructor, but in our setup the constructor is
	invoked immediately when the session is created, and merely
	created a dummy (and disabled) SBRG object. 

	@param rb Provides access to the query-string parameters,
	which controls various SBRG modes and options. Since
	2014-09-10, we call turnSBOn() in the constructor, with
	rb=null, for the SB-on-by-default functionality. On this call,
	all defaults are used.
     */
    public synchronized void turnSBOn(ResultsBase rb) throws WebException {
	init(rb, rb!=null && rb.runByResearcher(), false);
    }

    synchronized void init(OptionAccess rb, boolean runByResearcher, boolean cmdLine) throws WebException {
	setAllowedSB(true);
	if (rb!=null) sbStableOrderMode = rb.getInt("sbStableOrder", sbStableOrderMode);
	validateSbStableOrderMode();
	Method m = null;
	// the same param initializes both vars now
	if (rb!=null) {
	    sbDebug = rb.getBoolean("sbDebug", sbDebug);
	    researcherSB = rb.getBoolean("sbDebug", researcherSB || runByResearcher);
	    m = (SBRGenerator.Method)rb.getEnum(SBRGenerator.Method.class, "sbMethod", null);
	}
 
	final boolean nothingDoneYet = sbrRunning==null && sbrReady==null;

	if (requestedSbMethod == null ||
	    (m!=null && nothingDoneYet)) { //has not been set before, must set now
	    requestedSbMethod = (m==null) ? Method.ABSTRACTS : m;
	    if (sbMethod!=null && !nothingDoneYet) {
		throw new WebException("Somehow we have already set the SB method, and cannot change it anymore!");
	    }
	    sbMethod = (requestedSbMethod == Method.RANDOM)?
		pickRandomMethod() : requestedSbMethod;

	    if (cmdLine)  sbMergeWithBaseline = false;
	    if (rb!=null)  sbMergeWithBaseline = rb.getBoolean("sbMergeWithBaseline", sbMergeWithBaseline);

	    String desc = "Requested method=" + requestedSbMethod +"; effective  method=" + sbMethod + ". Merge with baseline = " + sbMergeWithBaseline +"; sbStableOrderMode=" + sbStableOrderMode;
	    
	    worker =createWorker(this);

	    boolean isCTPF = (worker instanceof SBRGWorkerCTPF);
	
	    if (rb!=null) {
		String key = "sb.CTPF.T";
		if (rb.containsKey(key)) {
		    if (isCTPF) {
			SBRGWorkerCTPF w = (SBRGWorkerCTPF)worker;
			w.temperature = rb.getDouble(key, w.temperature);
		    } else {
			Logging.warning("SBRG.init((): Ignoring option " + key + " (not a CTPF model!)");
		    } 
		} else {
		    desc += "; [no key "+key+"]";
		}
	    }

	    if (isCTPF) {
		SBRGWorkerCTPF w = (SBRGWorkerCTPF)worker;
		desc += "; CTPF.T=" + w.temperature;
	    }

	    Logging.info("SBRG(session="+sd.getSqlSessionId()+").init(): " + desc);


	} else if  (m==null || requestedSbMethod == m ) {
	    // OK: has already been set, and no attempt to change it now 
	} else  {  // prohibited attempt to re-set it differently
	    String msg = "Cannot change the SB method to " + m + " now, since "  + requestedSbMethod + " already was requested before";
	    Logging.error(msg);
	    throw new WebException(msg);
	}

    }


    /** Creates a SBRGWorker object of an appropriate type for the job.
	@param sbrg The SBR generator which contains all relevant parameters
	@return A new worker object.
     */
    static SBRGWorker createWorker(SBRGenerator sbrg) {
	// If merging with baseline is to follow, then the stable-order
	// procedure is only carried out after that merging.
	int soMode = sbrg.sbMergeWithBaseline? 0:  sbrg.sbStableOrderMode;

	SBRGWorker w;
	if (sbrg.sbMethod == Method.ABSTRACTS_COACCESS) {
	    w = new SBRGWorkerMerge(sbrg, soMode,
				    new SBRGWorker(Method.ABSTRACTS, sbrg, 0),
				    new SBRGWorker(Method.COACCESS, sbrg, 0));
	} else if (sbrg.sbMethod == Method.CTPF) { 
	    w = new SBRGWorkerCTPF(sbrg, soMode);
    } else {
	    w = new SBRGWorker(sbrg.sbMethod, sbrg, soMode);
	}

	if (sbrg.sbMergeWithBaseline) {
	    return new SBRGWorkerMerge(sbrg, sbrg.sbStableOrderMode,
				       w,
				       new SBRGWorker(Method.SUBJECTS, sbrg, 0));
	} else {
	    return w;
	}

    }

    /** Randomly selects a SBR generation method to use in this session.
     */
    static SBRGenerator.Method pickRandomMethod() {
	Method[] methods = {  Method.ABSTRACTS, Method.COACCESS,
			      Method.ABSTRACTS_COACCESS,
			      //Method.SUBJECTS
	};
	int z = (int)(methods.length * Math.random());	
	return  methods[z];
    }

    /** This is invoked from the ResultsBase constructor to see if the
	user has requested the SB to be activated.  Once requested, it
	will stay on for the rest of the session.

	@param rb The ResultsBase object for the web page; used to access
	command line parameters
     */
    public void setSBFromRequest(ResultsBase rb) throws WebException {
	boolean sb = rb.getBoolean("sb", false);
	if (sb) {
	    turnSBOn(rb);
	}
    }
    

    /** Turns the flag on to activate the moving panel for the
	Session-Based recommendations. Requests the suggestion
	list generation.

	<p>
	We need at least 2 page views to start requesting SB generation.
	This is because anyone can request one page without starting 
	a real session (robots do it often), while having 2 pages requested 
	in the same session is a good indicator of real user activity.

	@return Pointer to the now-started thread, or null if no new thread
	has been started right now. The return value is not utilized in the 
	context of the web application, but can be used in the test harness
	application (which wants to ensure single-thread processing,
	and to report separately on each thread)
     */
    synchronized public SBRGThread sbCheck() {
	//Logging.info("sbCheck: allowedSB=" + allowedSB);
	if (allowedSB) {
	    int articleCnt = maintainedActionHistory.articleCount;
	    needSBNow = 	     (articleCnt>=2);
	    //Logging.info("sbCheck: articleCnt="+articleCnt+", needSBNow=" +needSBNow);
	    return needSBNow ? requestRun(articleCnt) : null;
	} else return null;
    }


    /** List of all article IDs that have been mentioned anywhere in pages
	shown to the user during this session. This can be used an 
	exclusion list for the session-based recommendations.

	<P>This set also includes all other articles that we should not show to the user.
     */
    HashSet<String> linkedAids = new HashSet<String>();
    /** This method can be called to record the fact that a link of a
	particular article has appeared in some page shown to the user.
     */
    public void recordLinkedAid(String aid) {
	synchronized(	linkedAids) {
	    linkedAids.add(aid);
	}
    }

   /** This method can be called to record the fact that links to a number
       of articles have appeared in some page shown to the user.
     */
    public void recordLinkedAids(Vector<ArticleEntry> entries) {
  	synchronized(	linkedAids) {
	    for(ArticleEntry ae: entries) {
		linkedAids.add(ae.id);
	    }
	}
    }

    /** Retrieves the SearchResults structure encapsulating the most recently
	generated session-based recommendation list.

	<p>Unless the TRIVIAL SBR method (which uses no exclusions) is
	used, here we also double check if there are any new articles
	to exclude. This means that the actually displayed list
	(returned by this method) may sometimes be slightly shorter than 
	the list stored in the PresentedList object in the database!
	(FIXME: The above means that the PresentedList objects may not
	always exactly reflect the "displayed reality"...)
    */
    public synchronized SearchResults getSR() {
	if (sbrReady==null) return null;
	SearchResults sr =  sbrReady.sr;      
	if (sbrReady.worker.sbMethod != Method.TRIVIAL) {
	    int rmCnt = sr.excludeSomeSB(linkedAids);
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+").getSR(): Removed " + rmCnt + " additional entries from display list based on plid="+sbrReady.plid);
	}
	return sr;
    }

    /** Returns the list that was previously displayed in the SB window, with
	any user-initiated reorderings applied to it. This list is used in the
	"maintain stable order" procedure in SBRGWorker.
     */
    synchronized Vector<ArticleEntry> getBaseList() {
	if (sbrReady==null) return null;
	if (sbrReady.reorderedEntries != null) return sbrReady.reorderedEntries;
	SearchResults sr =  sbrReady.sr;      
	if (sbrReady.worker.sbMethod != Method.TRIVIAL) {
	    int rmCnt = sr.excludeSomeSB(linkedAids);
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+").getSR(): Removed " + rmCnt + " additional entries from display list");
	}
	return sr.entries;
    }

    /** This method is called when the user reorders articles shown in
	SB, so that a local record is made, for use in any future
	"maintain stable order" procedures. It calls the eponymous
	method in the SBRGThread object which was responsible for
	generating the presented list now being reordered.
	
	@param oplid The id of the original PresentedList whose reordering the new list purports to be 
	@param aids The list of article IDs in the reordered list, as arrived from the web browser
    */
    public synchronized void receiveReorderedList(long oplid, String aids[]) {
	if (sbrReady==null) {
	    // This could happen in exceptional cases, e.g. on session timeout
	    Logging.warning("SBRG(session="+sd.getSqlSessionId()+") asked to record a reordered list (PL=" + oplid + ") while there is no original list!");
	} else if (sbrReady.plid != oplid) {
	    Logging.warning("SBRG(session="+sd.getSqlSessionId()+").getSR(): Request to receive reordered list for PL=" + oplid + " has been denied, because that PL has expired; the currently available PL is " + sbrReady.plid);
	} else {
	    sbrReady.receiveReorderedList(aids);
	}
    }


    /** Does this generator has a running thread (which is going to
	generate a new list) in progress? */
    synchronized public boolean hasRunning() {
	return sbrRunning != null;
    }

    /** Returns true is this generator has a running thread (which is
	going to generate a new list) in progress, AND it believes
	that the thread will complete pretty soon. This method is used
	to help us make a better prediction as to when the client
	should check the server's status the next time.
    */
    synchronized boolean runningNearCompletion() {
	return sbrRunning != null && sbrRunning.nearCompletion;
    }

    /** Retrieves the PresentedList id associated with the most recently
	generated session-based recommendation list.
     */
    synchronized public long getPlid() {    
	return   sbrReady==null? 0 : sbrReady.plid;
    }

    /** Retrieves the PresentedList id associated with the most recently       
	generated session-based recommendation list.
	@param record If true, record this request in the belief that this
	PresentedList will now be sent to the client
     */
    synchronized public long getPlid(boolean record) {
	long c = getPlid();
	if (record) lastDisplayedPlid = c;
	return c;
    }
  
    /** This is what we beleive was the PLID for the most recent rec list 
	displayed in the SB window. SessionBased.java is supposed to set it.
	FIXME: This is just what we were about to send; we do not receive
	a confirmation from the client that the list in fact was successfully
	received and displayed there!
    */
    private long lastDisplayedPlid = 0;
    private long getLastDisplayedPlid() { return lastDisplayedPlid;}

    synchronized public String description() {
	if (sbrReady==null) {
	    String s= "No rec list has been generated yet. Requested method=" + requestedSbMethod +"; effective  method=" + sbMethod + ". Merge with baseline = " + sbMergeWithBaseline +"<br>\n";
	    if (sbrFailed != null) {
		s += "Error message from the most recent failed thread: " + sbrFailed.errmsg + "<br>\n";
	    }
	    return s;
	} else {
	    String s = sbrReady.description() + "<br>\n";
	    s += "<br>Excludable articles count: " + linkedAids.size()+"<br>\n";
	    if (sbrReady!=null) {
		s += "<br>Actually excluded: " + sbrReady.excludedList+"<br>\n";
	    }
	    return s;
	}
	
    }

    /** Link back to the SessionData object with the information about
	this session. In particular, we make use of the SQL session
	ID, to retrieve the recorded user activity from the SQL
	database.
     */
    final SessionData sd;

    /** The "action count" at a particular moment of time is the
	number of recorded user actions in the session so far.
	requestedActionCount stores the value of the "action count" at
	the time of the last received request to recompute SBRL.  */
    private int requestedArticleCount=0;
    /**
	lastThreadRequestedActionCount stores the value of the action
	count at the time of the most recent request for which 
	we actually started a computational thread.     */
    private int lastThreadRequestedArticleCount=0;

    /** Since 2014-09-10, the SB functionality is turned on by
	default, right here in the constructor.
     */
    public SBRGenerator(SessionData _sd, boolean turnOnNow) throws WebException {
	sd = _sd;
	if (turnOnNow) turnSBOn(null);
    }

    /** This method is invoked by front-end pages when they believe that 
	the user has carried out a new action, and the SBRL may need
	to be recomputed accordingly.

	@return Pointer to the now-started thread, or null if no new thread
	has been started right now. The return value is not utilized in the 
	context of the web application, but can be used in the test harness
	application (which wants to ensure single-thread processing,
	and to report separately on each thread)
     */
    private synchronized SBRGThread requestRun(int articleCount) {
	String prefix = "SBRG(session="+sd.getSqlSessionId()+"): ";
	Logging.info(prefix+"requested computations for articleCnt="+articleCount);
	if (sbrReady != null && sbrReady.getArticleCount() >= articleCount) {
	    Logging.info(prefix + "ignoring redundant request with articleCount=" + articleCount);
	    return null;
	} else if (sbrRunning != null) {

	    requestedArticleCount= Math.max(requestedArticleCount,articleCount);
	    Logging.info(prefix+"recording request with articleCount=" + articleCount +", until the completion of the currently running thread " + sbrRunning.getId() + "/" + sbrRunning.getState()  );
	    return null;
	} else {
	    sbrRunning = new SBRGThread(this, runCnt++, worker);
	    lastThreadRequestedArticleCount=requestedArticleCount=articleCount;
	    Logging.info(prefix + "Immediately starting a new thread "+ sbrRunning.getId() +", for articleCnt=" + requestedArticleCount);
	    sbrRunning.start();
	    return sbrRunning;
	}
    }

    /** The running SBRG thread (this.sbRunning) calls this method
	once it has completed all computations (right before exiting
	from its run() method), and the results it has produced can be
	made available for display.  If there has been a non-duplicate
	request to re-compute the list, we will now start running a
	thread for it.
     */
    synchronized void completeRun() {
	if (sbrRunning.sr!=null) {
	    sbrReady = sbrRunning;
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Thread " + sbrRunning.getId() + " finished successfully; plid="+ sbrRunning.plid+", |sr|=" + sbrReady.sr.entries.size() + "; " + sbrReady.msecLine());
	} else { // there must have been an error
	    sbrFailed = sbrRunning;
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Thread " + sbrRunning.getId() + " finished with no result; error=" + sbrRunning.error + " errmsg=" + sbrRunning.errmsg);
	}
	sbrRunning = null;
	// is another run needed?
	if (requestedArticleCount > lastThreadRequestedArticleCount) {
	    sbrRunning = new SBRGThread(this, runCnt++,worker);
	    lastThreadRequestedArticleCount=requestedArticleCount;
	    sbrRunning.start();
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Starting new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedArticleCount);
	}
    }


    private ActionHistory maintainedActionHistory = new  ActionHistory();
    
    synchronized public void addAction(Action a) {
	maintainedActionHistory.augment(a);

	Article art = a.getArticle();
	String aid =  (art==null) ? "[none]" : art.getAid();

	Logging.info("SBRG(session="+sd.getSqlSessionId()+"): added action ("+a.getOp()+":"+ aid+"); articleCnt = " + maintainedActionHistory.articleCount);

    }
    
    /** Generates the query string for the URL. Used in tools/index.jsp */
    static public String qsd(Method method, boolean withBaseline) {
	return qs(method, true, withBaseline);
    }

    static public String qs(Method method, boolean debug, boolean withBaseline) {
	String s= "sb=true";
	if (debug) { s += "&sbDebug=true"; }
	s += "&sbMethod="+ method;
	s += "&sbMergeWithBaseline=" +withBaseline ;
	return s;
    }


    /** This is a URL used in the "Change Focus" buton in the SB window.
	The user will be redirected to this URL once he's gone through
	the logout servlet, where his current session will be terminated.
     */
    public String encodedChangeFocusURL() {
	String url = "index.jsp?" + qs( Method.RANDOM, sbDebug, sbMergeWithBaseline);
	return URLEncoder.encode(url);
    }

    /** Creates a JS statement to be sent back to the client by CheckSBServlet.
	The statement advises the client to get a new rec list immediately,
	or wait and come again later, depending on the state of data in the
	SBRGenerator.

	<p>The logic is as follows: if the server has a more recent
	presented list than the one on the clinet, the client is told
	to download it immediately. Instead of this, or in addition of
	this, if the server currently has a running rec list
	recomputation thread, the client is told to send another check
	request a couple seconds later.

	<p>This method makes its decision based, in part, on the
	comparison of the presented list ID values for the list
	currently in the client (in the browser's SB pop-up window)
	and in the server (in the most recent SBR list available). To
	figure what the client has, we check what we last sent to the
	client (getLastDisplayedPlid()), instead of looking at the
	request's asrc.presentedListId (which may have come from MAIN
	or SEARCH contexts, rather than SB, and thus will be irrelevant).
	
     */
    public synchronized String mkJS( String cp) {

	String js="";
	long serverHasPlid = getPlid();
	long clientHasPlid = getLastDisplayedPlid(); 

	if ( serverHasPlid > clientHasPlid) {
	    js= "openSBMovingPanelNow('"+cp+"'); ";
	}

	boolean r = hasRunning();
	if (r) {
	    int msec = runningNearCompletion()? 1000: 2000;
	    String url = CheckSBServlet.mkUrl(cp);
	    js += "checkSBAgainLater('"+url+"', "+msec+");";
	}
	Logging.info("CheckSBServlet(session="+sd.getSqlSessionId()+", client="+clientHasPlid+", server="+serverHasPlid+", running="+r+") will send back: " + js);
	return js;
    }

}
