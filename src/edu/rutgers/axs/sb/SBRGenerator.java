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

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.RatingButton;
import edu.rutgers.axs.web.*;

/** The "manager" part of the sesion-based recommendation
    generator. We have an SBRGenerator instance in every
    web.SessionData object.

    <p> Recommendation list computation for this class is carried out
    asynchronously, in the run() method of a SBRGThread object (a Java
    thread). At most one SBRGThread may have its thread running at any
    time on behalf of one SBRGenerator object. In this way some level
    of load control is achieved, and unnecessary computations are
    avoided.
 */
public class SBRGenerator {


    /** Whether this session needs a "moving panel" with session-based 
	recommendations (aka "session buddy") */
    private boolean allowedSB = false; 
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

    /** If true, the SB moving panel will be displayed in "researcher mode".
	Since SB is only shown to users who have not logged in, we can't
	use the usual researcher flag, but rather set this flag via a 
	"secret" query string parameter.
     */
    public boolean researcherSB = false; 


    /** Recommendation list generation methods
     */
    public static enum Method {
	/** Used for testing: the recommendation list is the same as
	    the list of articles the user has viewed. */
	TRIVIAL, 
	    /** Recommendations are based on the article similarity
		(titles + abstracts) */
	    ABSTRACTS, 
	    /** Recommendations are based on the article's historical
		coaccess data */
	    COACCESS,
	    /** The baseline method: a few most recent articles from 
		appropriate subject categories */
	    SUBJECTS,
	    /** Not an actual method. This param value is used on
		session initialization to randomly choose one of the
		"real" methods. After that, the sbMethod parameter in
		the SBR generator is set to that real method. 
	    */
	    RANDOM; 
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
    private Method requestedSbMethod = null;

    /** Pointer to a thread object that contains the most recently compiled
	recommendation list ready for display. */
    private SBRGThread sbrReady = null;

    /** Pointer to a thread running right now; it will compute the
     * next available list, but it is not ready for display yet. */
    private SBRGThread sbrRunning = null;
    
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
     */
    public synchronized void turnSBOn(ResultsBase rb) throws WebException {
	allowedSB = true;
	sbStableOrderMode = rb.getInt("sbStableOrder", sbStableOrderMode);
	validateSbStableOrderMode();
	// the same param initializes both vars now
	sbDebug = rb.getBoolean("sbDebug", sbDebug);
	researcherSB = rb.getBoolean("sbDebug", researcherSB || rb.runByResearcher());

	Method m = (SBRGenerator.Method)rb.getEnum(SBRGenerator.Method.class, "sbMethod", null); 

	if (requestedSbMethod == null) { //has not been set before, must set now
	    requestedSbMethod = (m==null) ? Method.ABSTRACTS : m;
	    if (sbMethod!=null) {
		throw new WebException("Somehow we have already set the SB method, and cannot change it anymore!");
	    }
	    sbMethod = (requestedSbMethod == Method.RANDOM)?
		pickRandomMethod() : requestedSbMethod;

	    Logging.info("SBRG(session="+sd.getSqlSessionId()+").turnSBOn(): requested method=" + requestedSbMethod +"; effective  method=" + sbMethod);

	    worker = new SBRGWorker(sbMethod, this, sbStableOrderMode);
	    // worker.setSbStableOrderMode(sbStableOrderMode);

	} else if  (m==null || requestedSbMethod == m ) {
	    // OK: has already been set, and no attempt to re-set now
	} else  {  // prohibited attempt to re-set
	    throw new WebException("Cannot change the SB method to " + m + " now, since "  + requestedSbMethod + " already was requested before");
	}

    }

    /** Randomly selects a SBR generation method to use in this session.
     */
    private static SBRGenerator.Method pickRandomMethod() {
	Method[] methods = {  Method.ABSTRACTS, Method.COACCESS,
			      Method.SUBJECTS};
	int z = (int)(methods.length * Math.random());	
	return  methods[z];
    }

    /** This is invoked from the ResultsBase constructor to see if the
	user has requested the SB to be activated.  Once requested, it
	will stay on for the rest of the session.

	@rb The ResultsBase object for the web page; used to access
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
     */
    synchronized public void sbCheck() {
	if (allowedSB) {
	    int articleCnt = maintainedActionHistory.articleCount;
	    needSBNow = 	     (articleCnt>=2);
	    if (needSBNow) {
		requestRun(articleCnt);
	    }
	}
    }


    /** List of all article IDs that have been mentioned anywhere in pages
	shown to the user during this session. This can be used an 
	exclusion list for the session-based recommendations.

	<P>This set also inbcludes all other articles that we should not show to the user.
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
	to exclude.
    */
    public SearchResults getSR() {
	if (sbrReady==null) return null;
	SearchResults sr =  sbrReady.sr;      
	if (sbrReady.sbMethod != Method.TRIVIAL) {
	    int rmCnt = sr.excludeSomeSB(linkedAids);
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+").getSR(): Removed " + rmCnt + " additional entries from display list");
	}
	return sr;
    }

    /** Does this generator has a running thread (which is going to
	generate a new list) in progress? */
    public boolean hasRunning() {
	return sbrRunning != null;
    }

    /** Retrieves the PresentedList id associated with  the most recently
	generated session-based recommendation list.
     */
    public long getPlid() {
	return sbrReady==null? 0 : sbrReady.plid;
    }

  
    synchronized public String description() {
	if (sbrReady==null) {
	    String s= "No rec list has been generated yet. Requested method=" + requestedSbMethod +"; effective  method=" + sbMethod + "\n";
	    return s;
	} else {
	    String s = sbrReady.description() + "\n";
	    s += "<br>Per-article result list sizes:\n";
	    HashMap<String,ScoreDoc[]> articleBasedSD= sbrReady.worker.articleBasedSD;
	    for(String aid: articleBasedSD.keySet()) {
		s += "<br>* "+aid+" : "+articleBasedSD.get(aid).length+"\n";
	    }
	    s += "<br>Excludable articles count: " + linkedAids.size()+"\n";
	    if (sbrReady!=null) {
		s += "<br>Actually excluded: " + sbrReady.excludedList+"\n";
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
	requestedActionCount is the action count at the time of the
	last received request to recompute SBRL;
	lastThreadRequestedActionCount is the value of the action
	count at the time the most recent request for which 
	we actually started a computational thread.
     */
    private int requestedArticleCount=0, lastThreadRequestedArticleCount=0;

    public SBRGenerator(SessionData _sd) {
	sd = _sd;
    }

    /** This method is invoked by front-end pages when they believe that 
	the user has carried out a new action, and the SBRL may need
	to be recomputed accordingly.
     */
    private synchronized void requestRun(int articleCount) {
	String prefix = "SBRG(session="+sd.getSqlSessionId()+"): ";
	Logging.info(prefix+"requested computations for articleCnt="+articleCount);
	if (sbrReady != null && sbrReady.getArticleCount() >= articleCount) {
	    Logging.info(prefix + "ignoring redundant request with articleCount=" + articleCount);
	    
	} else if (sbrRunning != null) {

	    requestedArticleCount = Math.max(requestedArticleCount,articleCount);
	    Logging.info(prefix+"recording request with articleCount=" + articleCount +", until the completion of the currently running thread " + sbrRunning.getId() + "/" + sbrRunning.getState()  );
	} else {
	    sbrRunning = new SBRGThread(this, runCnt++, worker);
	    lastThreadRequestedArticleCount=requestedArticleCount=articleCount;
	    Logging.info(prefix + "Immediately starting a new thread "+ sbrRunning.getId() +", for articleCnt=" + requestedArticleCount);
	    sbrRunning.start();
	}
    }

    /** The running SBRG thread calls this method when it has completed all
	computations (right before exiting from its run() method), and
	the results it has produced can be made available for display.
	If there has been a non-duplicate request to re-compute the
	list, we will now start running a thread for it.
     */
    synchronized void completeRun() {
	if (sbrRunning.sr!=null) {
	    sbrReady = sbrRunning;
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Thread " + sbrRunning.getId() + " finished successfully; |sr|=" + sbrReady.sr.entries.size());
	} else { // there must have been an error
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Thread " + sbrRunning.getId() + " finished with no result; error=" + sbrRunning.error + " errmsg=" + sbrRunning.errmsg);
	}
	sbrRunning = null;
	if (requestedArticleCount > lastThreadRequestedArticleCount) {
	    sbrRunning = new SBRGThread(this, runCnt++,worker);
	    lastThreadRequestedArticleCount=requestedArticleCount;
	    sbrRunning.start();
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Starting new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedArticleCount);
	}
    }

    ActionHistory maintainedActionHistory = new  ActionHistory();
    
    synchronized public void addAction(Action a) {
	maintainedActionHistory.augment(a);

	Article art = a.getArticle();
	String aid =  (art==null) ? "[none]" : art.getAid();

	Logging.info("SBRG(session="+sd.getSqlSessionId()+"): added action ("+a.getOp()+":"+ aid+"); article cnt = " + maintainedActionHistory.articleCount);


    }
    
    /** This is a URL used in the "Change Focus" buton in the SB window.
	The user will be redirected to this URL once he's gone through
	the logout servlet, where his current session will be terminated.
     */
    public String encodedChangeFocusURL() {
	String url = "index.jsp?sb=true&sbMethod=RANDOM";
	if (sbDebug) {
	    url += "&sbDebug=true";
	}
	return URLEncoder.encode(url);
    }

}