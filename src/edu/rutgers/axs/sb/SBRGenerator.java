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

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.RatingButton;
import edu.rutgers.axs.web.*;

/** The sesion-based recommendation generator. We have an SBRGenerator instance
    in every web.SessionData object.

    Recommndation list computation for this class is carried out in the run()
    method of a SBRGThread object (a thread). At most one SBRGThread may have
    its thread running at any time.
 */
public class SBRGenerator {

    /** Already compiled recommendations */
    private SBRGThread sbrReady = null;

    /** Currrently being computed */
    private SBRGThread sbrRunning = null;
    
    public SearchResults getSR() {
	return sbrReady==null? null : sbrReady.sr;
    }

    public String description() {
	String s = sbrReady==null? "n/a" : sbrReady.description();
	s += "<br>Per-article result list sizes:\n";
	for(String aid: articleBasedSR.keySet()) {
	    s += "<br>* "+aid+" : "+articleBasedSR.get(aid).scoreDocs.length+"\n";
	}
	return s;
	
    }

    final SessionData sd;

    private int requestedActionCount=0, lastThreadRequestedActionCount=0;

    /** Pre-computed suggestion lists based on individual articles. Keys are
	ArXiv article IDs.
     */
    HashMap<String, SearchResults> articleBasedSR = new HashMap<String, SearchResults>();

    public SBRGenerator(SessionData _sd) {
	sd = _sd;
    }

    public synchronized void requestRun(int actionCount) {
	Logging.info("SBRG: requested computations for actionCnt="+actionCount);
	if (sbrReady != null && sbrReady.actionCount >= actionCount) {
	    Logging.info("SBRG: ignoring redundant request with actionCount=" + actionCount);
	} else if (sbrRunning != null) {
	    requestedActionCount = Math.max(requestedActionCount,actionCount);
	    Logging.info("SBRG: recording request with actionCount=" + actionCount +", until the completion of the currently running thread " + sbrRunning.getId() + "/" + sbrRunning.getState()  );
	} else {
	    sbrRunning = new SBRGThread(this);
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
	    sbrRunning = new SBRGThread(this);
	    lastThreadRequestedActionCount=requestedActionCount;
	    sbrRunning.start();
	    Logging.info("SBRG: Starting new thread "+ sbrRunning.getId() +", for actionCnt=" + requestedActionCount);
	}
    }
}