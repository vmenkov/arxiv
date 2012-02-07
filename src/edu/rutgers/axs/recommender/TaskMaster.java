package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
//import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.ResultsBase;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

public class TaskMaster {
    

    static final int maxDocs=200;


    /** For asynchronous reading of allStats */
    static class AllStatsReader extends Thread {
	ArticleStats[] allStats = null;
	boolean error = false;
	Exception ex;
	private IndexReader reader;
	AllStatsReader(IndexReader _reader) { reader= _reader;}
	public void 	run() {
	    try {
		EntityManager em = Main.getEM();
		allStats = ArticleAnalyzer.getArticleStatsArray(em, reader); 
		ResultsBase.ensureClosed( em, false);
	    } catch(Exception _ex) {
		ex = _ex;
		error=true;
		Logging.error("Failed to read pre-computed AllStats. exception=" + ex);
	    }
	}

	/** This is called from the parent's thread, to verify that this thread
	    has completed. */
	ArticleStats[] getResults() throws Exception {
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

    private static class ShutDownThread extends Thread 	    {
	final Thread mainThread;
	final Date exitAfterTime;
	boolean interrupted = false;

	ShutDownThread(Thread _mainThread, Date _exitAfterTime) {
	    mainThread = _mainThread;
	    exitAfterTime=   _exitAfterTime;
	}

	/** This gets invoked on Ctrl-C, etc.
	    // FIXME: maybe should put a "failed" flag on the current task,
	    // and do other niceties...
	 */
	@Override
	    public void run()
	{
	    interrupted = true;
	    Logging.info("Shutdown signal received; will exit");
	    mainThread.interrupt();
	    //System.out.println("Shutdown hook ran!");
	}

	/** Check if the current time is after the specified "end time" */
	boolean timeOut() {
	    Date now = new Date();
	    if (exitAfterTime!=null && now.after(exitAfterTime)) {
		Logging.info("The time limit reached; will exit");
		return true;
	    } else {
		return false;
	    }
	}

	boolean mustExit() {
	    return interrupted || timeOut();
	}	
    };

    /**
       -DexitAfter=24  - time in hours
    */
    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	final int pid = Main.getMyPid();

	int exitAfter=ht.getOption("exitAfter", 0);
	Date exitAfterTime = (exitAfter<=0) ? null:
	    new Date( (new Date()).getTime() + exitAfter * 3600*1000);

	ShutDownThread shutDown = new ShutDownThread(Thread.currentThread(), exitAfterTime);
	Runtime.getRuntime().addShutdownHook(shutDown);

	IndexReader reader =  ArticleAnalyzer.getReader();
	AllStatsReader asr = new  AllStatsReader(reader);
	asr.start();
    
	EntityManager em = Main.getEM();


	int taskCnt=0;
	int noneCnt=0; // how many "no task" loops w/o a message
	final long sleepMsec = 3 * 1000;
	final long sleepMsgIntervalMsec = 10*60*1000;

	Logging.info("My.arXiv TaskMaster starting. pid=" + pid);

	if (exitAfterTime==null) {
	    Logging.info("No time limit");
	} else {
	    Logging.info("Requested to stop after " + exitAfter + 
			 " hours, ca. " + exitAfterTime);
	}

	// FIXME: here we ought to scan the Task table and see if
	// there are "zombie" tasks: those which a previous TaskMaster
	// run started but never finished. However, one needs to be
	// careful with this, as some of those may be "tar babies":
	// you start one and you die of the same exception as the
	// previous run. Something like re-try count my be kept in the
	// task entry for this...
	Task task=null;
	boolean stopNow=false;

	while(!stopNow && !shutDown.mustExit()) {
	    task = grabNextTask(em,pid);
	    if (task==null) {
		if (noneCnt == 0 || noneCnt*sleepMsec > sleepMsgIntervalMsec) {
		    Logging.info("no task");
		    noneCnt=0;
		} 
		noneCnt++;
		try {		    
		    Thread.sleep(sleepMsec);
		} catch ( InterruptedException ex) {
		}
		continue;
	    } 
	    noneCnt=0;
	    taskCnt++;
	    boolean success = false;
	    DataFile outputFile=null;
	    DataFile inputFile = null;
	    try {
		Logging.info("task["+taskCnt+"]: " + task);
		final Task.Op op = task.getOp();
		if (op== Task.Op.STOP) {		
		    Logging.info("Stop requested!");
		    stopNow=true;
		} else if (op == Task.Op.HISTORY_TO_PROFILE) {	
		    //Logging.info("");
		    UserProfile upro=new UserProfile(task.getUser(),em,reader);
		    outputFile=upro.saveToFile(task,op.outputFor());
		} else if (op == Task.Op.LINEAR_SUGGESTIONS_1
			   || op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
		    // FIXME: should also support individual file specs
		    inputFile = 
			//			(task.getInputFile()) != null ?
			// new DataFile(task, task.getInputFile()
			DataFile.getLatestFile(em, task.getUser(),
					       DataFile.Type.USER_PROFILE);

		    UserProfile upro;
		    if (inputFile != null) {
			// read it
			upro = new UserProfile(inputFile, reader);
		    } else {
			// generate it
			upro = new UserProfile(task.getUser(), em, reader);
			DataFile uproFile=
			    upro.saveToFile(task, DataFile.Type.USER_PROFILE);
			em.persist(uproFile);
			inputFile = uproFile;
		    }

		    final boolean raw=true;
		    ArticleStats[] allStats = asr.getResults();
		    
		    int days = task.getDays();		    
		    Vector<ArticleEntry> entries = 
			raw ? upro.luceneRawSearch(maxDocs, asr.allStats, em, days):
			upro.luceneQuerySearch(maxDocs, days);

		    if (op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
			TjAlgorithm1 algo = new TjAlgorithm1();
			entries = algo.rank( upro, entries, asr.allStats, em, maxDocs);
		    }

		    outputFile=DataFile.newOutputFile(task);
		    outputFile.setDays(days);
		    ArticleEntry.save(entries, outputFile.getFile());
		}
		success=true;
		Logging.info("task=" + task+", successfully completed");
	    } catch(Exception ex) {
		ex.printStackTrace(System.err);
		Logging.info("task=" + task+", failed, exception caught: " + ex);
	    } finally {
		Logging.info("task=" + task+", recording success="+success);
		task.setCompleteTime(new Date());
		task.setFailed(!success);
		if (outputFile!=null) {
		    task.setOutputFile(outputFile.getThisFile());
		    em.persist(outputFile);
		}
		if (inputFile!=null) {
		    task.setInputFile(inputFile.getThisFile());
		}
		em.persist(task);
	    }
	}
	reader.close();
	Logging.info("Finished");
    }

    private static Task grabNextTask(EntityManager em, int pid) {
	em.getTransaction().begin();
	try {
	    Task task = Task.getNextTask(em);
	    if (task!=null) {
		task.setPid(pid);
		task.setStartTime(new Date());
		em.persist(task);
	    }
	    return task;
	} finally {
	    em.getTransaction().commit();
	}
    }


}
