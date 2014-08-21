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

/** This is a derivative class of SBRGWorker which constructs a rec list
    by a team-draft merge of the lists computed by two other, "underlying", workers.
 */
class SBRGWorkerMerge extends  SBRGWorker  {

    /** The underlying workers */
    SBRGWorker worker1, worker2;

    SBRGWorkerMerge(SBRGenerator _parent,
		    int _sbStableOrderMode,
		    SBRGWorker _worker1, SBRGWorker _worker2) {
	super( SBRGenerator.Method.MERGE, _parent, _sbStableOrderMode);
	worker1 = _worker1;
	worker2 = _worker2;
    }

    synchronized void work(EntityManager em, IndexSearcher searcher, int runID, ActionHistory _his)  {

	his = _his;
	error = false;
	errmsg = "";
	sr=null;
	excludedList = "";

	for(SBRGWorker w : new SBRGWorker[] { worker1, worker2} ) {
	    w.work(em, searcher, runID, _his);
	    if (w.error) {
		error = true;
		errmsg = w.errmsg;
		return;
	    }
	}
	
	// merge
	final int maxAge = his.viewedArticlesActionable.size();
	final int maxRecLen = recommendedListLength(maxAge);

	long seed = SearchResults.teamDraftSeed("sb");
	sr = SearchResults.teamDraft(worker1.sr.scoreDocs, worker2.sr.scoreDocs, seed);

	try {
	    sr.setWindow( searcher, new ResultsBase.StartAt(), maxRecLen, null);
	} catch(IOException ex) {
	    error = true;
	    errmsg = "I/O Error in setWindow(): " + ex;
	    return;
	}

	stableOrderCheck(maxRecLen);

	excludedList = worker1.excludedList  + " ; " + worker2.excludedList;
	plid = saveAsPresentedList(em).getId();

    }


    /** Produces a human-readable description of this worker's particulars. */
    public String description() {
	String s = "SBR obtained by team-draft merge of lists of the following types (";
	s += worker1.describeLength()  + " + ";
	s += worker2.describeLength()  +  " gives ";
	if (sr!=null) {
	    s += describeLength();
	} else {
	    s += "[no sr?!]";
	}
	s +=  "):<ul>" +
	    "\n<li>" + worker1.description() + 
	    "\n<li>" + worker2.description() +
	    "\n</ul>";

	return s;
    }
   
}
 