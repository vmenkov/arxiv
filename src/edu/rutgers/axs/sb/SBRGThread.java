package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.util.regex.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;

/** This class is used to run the creation of session-based
    recommendation lists in an asynchronous mode. The actual
    computations are encapsulated in SBRGWorker.

 */
class SBRGThread extends Thread {
    private final SBRGenerator parent;
    /** A sequential ID (zero-based) of this SBRL-generation run 
	within the user session.
     */
    private final int runID;

     //    private 
    SBRGWorker worker;

    /** Creates a thread. You must call its start() method next.
	@param _runID Run id, which identifies this run (and its results)
	within the session.
     */
    SBRGThread(SBRGenerator _parent, int _runID, SBRGWorker _worker) {
	parent = _parent;
	runID = _runID;
	worker = _worker;
    }

    /** When the list generation started and ended. We keep this 
     for statistics. */
    Date startTime, endTime;

    private ActionHistory his = null;
  
    /** The main class for the actual recommendation list
	generation. */
    public void run()  {
	startTime = new Date();
	
	IndexReader reader=null;
	IndexSearcher searcher = null;

	EntityManager em=null;
	try {
	    em = parent.sd.getEM();       
	    reader=Common.newReader();
	    searcher = new IndexSearcher( reader );

	    his = new ActionHistory(em, parent.sd.getSqlSessionId());

	    worker.work(em, searcher, runID, his);
	    if (worker.error) {
		error=true;
		errmsg = worker.errmsg;
		return;
	    }
	    sr = worker.sr;
	    plid = worker.plid;
	    excludedList = worker.excludedList;
	} catch(Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    try {
		if (searcher!=null) searcher.close();
		if (reader!=null) reader.close();
	    } catch(IOException ex) {}
	    endTime = new Date();
	    parent.completeRun();
	}
    }

    /** The number of articles in the current user action history */
    int getArticleCount() {
	return his==null? 0 : his.articleCount;
    }

    /** The recommendation list generated by this thread */
    public SearchResults sr=null;


    /** The ID of the PresentedList structure in which the rec list was
	saved in the database. */
    public long plid=0;

    boolean error = false;
    String errmsg = "";

    /** A human-readable text message containing the list of ArXiv
	article IDs of the articles that we decided NOT to show in the
	rec list (e.g., because they had already been shown to the
	user in this session in other contexts).
    */
    String excludedList = "";

     public String msecLine() {
	if (startTime!=null && endTime!=null) {
	    long msec = endTime.getTime() - startTime.getTime();
	    return " (" + (0.001 * (double)msec) + " sec)";
	} else return "";
     }


    /** Produces a human-readable description of what this thread has done. */
    public String description() {
	String s = "Session-based recommendation list no. "+plid+" produced by thread " + getId() +"; started at " + startTime +", finished at " + endTime;
	s += msecLine() + ".";
	s += "<br>\n" +  worker.description();

	if (his!=null) {
	    s += "<br>\nThe list is based on " +his.actionCount+ " user actions (" +
		his.articleCount + " viewed articles)";
	}
	return s;
    }

    /** This is created if the user reorders articles shown in sr */
    Vector<ArticleEntry> reorderedEntries = null;

    /** This method is called when the user reorders articles shown in
	sr, so that a local record is made, for use in any future
	"maintain stable order"  procedures. */
    synchronized void receiveReorderedList(String aids[]) {
	reorderedEntries = new Vector<ArticleEntry>();
	for(String aid: aids) {
	    boolean found=false;
	    for(ArticleEntry x: sr.entries) {
		if (x.id.equals(aid)) {
		    reorderedEntries.add(x);
		    found = true;
		    break;
		}
	    }
	    if (!found) {
		Logging.warning("SBRGThread " + getId() + ": Reordered list contains article " + aid + " which is not presented in the original list!");
	    }
	}
	Logging.info("SBRGThread " + getId() + ": Saved user-reordered list");
    }

    boolean nearCompletion=false;
    /** The thread's worker may call this method to report that it's near
	completion. That information may be used to optimize client-server
	communication.
     */
    void reportPartialProgress() {
	nearCompletion = true;
    }

 
}

