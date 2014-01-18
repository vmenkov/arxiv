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

/** The sesion-based recommendation generator. We have an SBRGenerator instance
    in every web.SessionData object.

    Recommendation list computation for this class is carried out in the run()
    method of a SBRGThread object (a thread). At most one SBRGThread may have
    its thread running at any time.
 */
public class SBRGenerator {

    /** Already compiled recommendations */
    private SBRGThread sbrReady = null;

    /** Currrently being computed */
    private SBRGThread sbrRunning = null;
    
    /** Used to keep track of the number of SBRG runs we've done */
    private int runCnt=0;

    /** List of all article IDs that have been mentioned anywhere in pages
	shown to the user during this session. This can be used an 
	exclusion list for the session-based recommendations.
     */
    HashSet<String> linkedAids = new HashSet<String>();
    /** This method can be called to record the fact that a link of a
	particular article has appeared in some page shown to the user.
     */
    public void recordLinkedAid(String aid) {
	linkedAids.add(aid);
    }
   /** This method can be called to record the fact that links to a number
       of articles have appeared in some page shown to the user.
     */
    public void recordLinkedAids(Vector<ArticleEntry> entries) {
       for(ArticleEntry ae: entries) {
	   linkedAids.add(ae.id);
       }
    }

    /** Retrieves the SearchResults structure encapsulating the most recently
	generated session-based recommendation list.
    */
    public SearchResults getSR() {
	return sbrReady==null? null : sbrReady.sr;       
    }
    /** Retrieves the PresentedList id associated with  the most recently
	generated session-based recommendation list.
     */
    public long getPlid() {
	return sbrReady==null? 0 : sbrReady.plid;
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

    final SessionData sd;

    private int requestedActionCount=0, lastThreadRequestedActionCount=0;

    /** Pre-computed suggestion lists based on individual articles. Keys are
	ArXiv article IDs.
     */
    HashMap<String,ScoreDoc[]> articleBasedSD= new HashMap<String,ScoreDoc[]>();

    public SBRGenerator(SessionData _sd) {
	sd = _sd;
    }

    public synchronized void requestRun(int actionCount) {
	Logging.info("SBRG: requested computations for actionCnt="+actionCount);
	if (sbrReady != null && sbrReady.actionCount >= actionCount) {
	    Logging.info("SBRG: ignoring redundant request with actionCount=" + actionCount);
	    //	} else if (sbrRunning != null && sbrRunning.getState()==Thread.State.TERMINATED) {
	    
	} else if (sbrRunning != null) {

	    requestedActionCount = Math.max(requestedActionCount,actionCount);
	    Logging.info("SBRG: recording request with actionCount=" + actionCount +", until the completion of the currently running thread " + sbrRunning.getId() + "/" + sbrRunning.getState()  );
	} else {
	    sbrRunning = new SBRGThread(this, runCnt++);
	    lastThreadRequestedActionCount=requestedActionCount=actionCount;
	    Logging.info("SBRG: Immediately starting a new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedActionCount);
	    sbrRunning.start();
	}
    }

    /** The running thread calls this method when it has completed all
	computations (right before exiting from its run() method), and
	the results it has produced can be made available for display.
	If there has been a non-duplicate request to re-compute the
	list, start running a thread for it.
     */
    synchronized void completeRun() {
	if (sbrRunning.sr!=null) {
	    sbrReady = sbrRunning;
	    Logging.info("SBRG: Thread " + sbrRunning.getId() + " finished successfully; |sr|=" + sbrReady.sr.entries.size());
	} else { // there must have been an error
	    Logging.info("SBRG: Thread " + sbrRunning.getId() + " finished with no result; error=" + sbrRunning.error + " errmsg=" + sbrRunning.errmsg);
	}
	sbrRunning = null;
	if (requestedActionCount > lastThreadRequestedActionCount) {
	    sbrRunning = new SBRGThread(this, runCnt++);
	    lastThreadRequestedActionCount=requestedActionCount;
	    sbrRunning.start();
	    Logging.info("SBRG: Starting new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedActionCount);
	}
    }
}