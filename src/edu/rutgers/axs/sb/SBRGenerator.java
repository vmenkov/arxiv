package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import java.net.*;
import java.nio.charset.Charset;

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
	articles in the rec list */
    public int sbMergeMode = 1;

    void validateSbMergeMode() throws WebException {
	if (sbMergeMode<0 || sbMergeMode>2) throw new WebException("Illegal SB merge mode = " + sbMergeMode);
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
	TRIVIAL, ABSTRACTS;
    };

    /**  Recommendation list generation method 
     */
    Method sbMethod = Method.ABSTRACTS;

    /** Pointer to a thread object that contains the most recently compiled
	recommendation list ready for display. */
    private SBRGThread sbrReady = null;

    /** Pointer to a thread running right now; it will compute the
     * next available list, but it is not ready for display yet. */
    private SBRGThread sbrRunning = null;
    
    /** Used to keep track of the number of SBRG runs we've done */
    private int runCnt=0;

    /** Used instead of setSBFromRequest() if we know that SB must be
	turned on. This happens when the user explicitly loads sessionBased.jsp
     */
    public void turnSBOn(ResultsBase rb) throws WebException {
	allowedSB = true;
	sbMergeMode = rb.getInt("sbMerge", sbMergeMode);
	validateSbMergeMode();
	// the same param initializes both vars now
	sbDebug = rb.getBoolean("sbDebug", sbDebug);
	researcherSB = rb.getBoolean("sbDebug", researcherSB || rb.runByResearcher());
	sbMethod = (SBRGenerator.Method)rb.getEnum(SBRGenerator.Method.class, "sbMethod", sbMethod);
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
    synchronized public void sbCheck(EntityManager em) {
	if (allowedSB) {
	    // count the actions in this session...
	    int actionCnt= Action.actionCntForSession(em, sd.getSqlSessionId());
	    needSBNow = 	     (actionCnt>=2);
	    if (needSBNow) {
		requestRun(actionCnt);
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

    /** This is simply the length of the list of "actionable" viewed articles */
    public int getMaxAge() {
	return sbrReady==null? 0 : sbrReady.maxAge;
    }

    synchronized public String description() {
	String s = sbrReady==null? "n/a" : sbrReady.description();
	s += "<br>Per-article result list sizes:\n";
	for(String aid: articleBasedSD.keySet()) {
	    s += "<br>* "+aid+" : "+articleBasedSD.get(aid).length+"\n";
	}
	s += "<br>Excludable articles count: " + linkedAids.size()+"\n";
	if (sbrReady!=null) {
	    s += "<br>Actually excluded: " + sbrReady.excludedList+"\n";
	}
	return s;
	
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
    private int requestedActionCount=0, lastThreadRequestedActionCount=0;

    /** Pre-computed suggestion lists based on individual articles. In
	the hashmap, the keys are ArXiv article IDs; the values are
	arrays of ScoreDoc objects of the kind that a Lucene search
	may return.
     */
    HashMap<String,ScoreDoc[]> articleBasedSD= new HashMap<String,ScoreDoc[]>();

    public SBRGenerator(SessionData _sd) {
	sd = _sd;
    }

    /** This method is invoked by front-end pages when they believe that 
	the user has carried out a new action, and the SBRL may need
	to be recomputed accordingly.
     */
    public synchronized void requestRun(int actionCount) {
	Logging.info("SBRG(session="+sd.getSqlSessionId()+"): requested computations for actionCnt="+actionCount);
	if (sbrReady != null && sbrReady.getActionCount() >= actionCount) {
	    Logging.info("SBRG: ignoring redundant request with actionCount=" + actionCount);
	    //	} else if (sbrRunning != null && sbrRunning.getState()==Thread.State.TERMINATED) {
	    
	} else if (sbrRunning != null) {

	    requestedActionCount = Math.max(requestedActionCount,actionCount);
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): recording request with actionCount=" + actionCount +", until the completion of the currently running thread " + sbrRunning.getId() + "/" + sbrRunning.getState()  );
	} else {
	    sbrRunning = new SBRGThread(this, runCnt++);
	    lastThreadRequestedActionCount=requestedActionCount=actionCount;
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Immediately starting a new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedActionCount);
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
	if (requestedActionCount > lastThreadRequestedActionCount) {
	    sbrRunning = new SBRGThread(this, runCnt++);
	    lastThreadRequestedActionCount=requestedActionCount;
	    sbrRunning.start();
	    Logging.info("SBRG(session="+sd.getSqlSessionId()+"): Starting new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedActionCount);
	}
    }
}