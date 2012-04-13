package edu.rutgers.axs.sql;

import java.util.*;
import java.io.*;

import javax.persistence.*;

//import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
//import org.apache.lucene.search.*;

import edu.rutgers.axs.web.ResultsBase;


    /** An auxiliary thread for the asynchronous reading of the
	allStats[] array on start-up. The idea is that if the first
	tasks to process do not require allStats[], the main thread
	can start working on them even as allStats[] is loading.
     */
public class AllStatsReader extends Thread {
	ArticleStats[] allStats = null;
	boolean error = false;
	Exception ex;
	private IndexReader reader;
	public AllStatsReader(IndexReader _reader) { reader= _reader;}
	public void 	run() {
	    try {
		EntityManager em = Main.getEM();
		allStats = ArticleStats.getArticleStatsArray(em, reader); 
		ResultsBase.ensureClosed( em, false);
	    } catch(Exception _ex) {
		ex = _ex;
		error=true;
		Logging.error("Failed to read pre-computed AllStats. exception=" + ex);
	    }
	}

	/** This is a blocking method, which is called from the
	    parent's thread. It waits for this thread to complete, 
	    and return the results. */
	public ArticleStats[] getResults() throws Exception {
	    while(getState()!=Thread.State.TERMINATED) {
		Logging.info("Waiting for ASR");
		try {	 // it is the parent thread who waits!	    
		    Thread.sleep(10 * 1000);
		} catch ( InterruptedException ex) {}			
	    }
	    if (allStats==null) {
		Logging.error("Ouch, no allStats");
		throw (ex!=null)? ex:
		    new IOException("Somehow could not read allStats from the SQL server");		
	    } else {
		return allStats;
	    }
	}

}

