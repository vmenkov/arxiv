package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.ResultsBase;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

public class TaskMaster {
    

    static final int maxDocs=200;


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
		String user = task.getUser();

		if (op== Task.Op.STOP) {		
		    Logging.info("Stop requested!");
		    stopNow=true;
		} else if (op == Task.Op.HISTORY_TO_PROFILE) {	
		    //Logging.info("");
		    UserProfile upro=new UserProfile(user, em,reader);
		    outputFile=upro.saveToFile(task,op.outputFor());
		} else if (op == Task.Op.LINEAR_SUGGESTIONS_1
			   || op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
		    // FIXME: should also support individual file specs
		    Vector<DataFile> ptr = new Vector<DataFile>(0);
		    if (task.getInputFile()!=null) {
			inputFile=DataFile.findFileByName(em, user,task.getInputFile()); 
			ptr.add(inputFile);
		    }

		    UserProfile upro = 
			getSuitableUserProfile(task, ptr, em, reader);
		    inputFile = ptr.elementAt(0);
		    final boolean raw=true;
		    ArticleStats[] allStats = asr.getResults();
		    
		    int days = task.getDays();		    
		    ArxivScoreDoc[] sd =
			raw? upro.luceneRawSearch(maxDocs,allStats,em,days):
			upro.luceneQuerySearch(maxDocs, days);

		    if (op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
			TjAlgorithm1 algo = new TjAlgorithm1();
			sd = algo.rank( upro, sd, allStats, em, maxDocs);
		    }

		    Vector<ArticleEntry> entries = upro.packageEntries(sd);
		    Logging.info("|sd|=" +sd.length+", |entries|=" + entries.size());
		    
		    outputFile=DataFile.newOutputFile(task);
		    outputFile.setDays(days);
		    outputFile.setLastActionId(upro.getLastActionId());
		    if (inputFile!=null) {
			outputFile.setInputFile(inputFile.getThisFile());
		    }
		    ArticleEntry.save(entries, outputFile.getFile());
		    // Save the sugg list to the SQL database
		    ArticleAnalyzer aa=new ArticleAnalyzer();
		    outputFile.fillArticleList(entries, aa, em);
	    
		}  else if (op == Task.Op.TJ_ALGO_2_USER_PROFILE) {

		    // We need to find the most recent Algo1 suggestion
		    // list, and the user profile on which it was based.
		    // We first see if there is a sug list already based
		    // on an Algo 2 profile, but if not, we'll be building on
		    // an "original" profile.

		    inputFile = DataFile.
			getLatestFile(em, user,
				      DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1, 
				      DataFile.Type.TJ_ALGO_2_USER_PROFILE,
				      -1);
		    
		    if (inputFile==null) {
			inputFile = DataFile.
			    getLatestFile(em, user,
					  DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1);
		    }
		    
		    UserProfile upro;

		    ArxivScoreDoc[] sd;
		    if (inputFile==null) {
			// there have been no Algo 1 suggestions... so
			// we should default to an empty profile
			// and empty sugg list!
			upro = new UserProfile(reader);
			sd = new ArxivScoreDoc[0];
		    } else {
			DataFile uproInputFile = 
			    DataFile.findFileByName(em,user, inputFile.getInputFile());
			upro = new UserProfile(uproInputFile, reader);
			File f = inputFile.getFile();
			Vector<ArticleEntry> entries = ArticleEntry.readFile(f);
			IndexSearcher searcher = new IndexSearcher(reader);	
			sd = ArxivScoreDoc.toArxivScoreDoc(entries,searcher);
		    }

		    //      DataFile.Type.TJ_ALGO_2_USER_PROFILE);
	
		    TjAlgorithm2 algo=new TjAlgorithm2( upro);
		    //ArticleStats[] allStats = asr.getResults();
		    algo.updateProfile(user, em, sd);//, allStats);
		    outputFile=upro.saveToFile(task,op.outputFor());
		    if (inputFile!=null) {
			outputFile.setInputFile(inputFile.getThisFile());
		    }
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

    /** Reads in, or generates, an "input" UserProfile, as appropriate
	for the task.
	@param ptr If non-empty, it is used as an input parameter,
	exactly specifying the file to read. If empty, it is used
	as an output param, reporting as to what data file we have read
	(or just written) the user profile from (or to),


	    TJ_ALGO_2_USER_PROFILE,

     */
    private static UserProfile 
	getSuitableUserProfile(Task task, Vector<DataFile> ptr, 
			       EntityManager em, IndexReader reader)
	throws IOException {

	if (ptr.size()>0) { //explicitly specified input file
	    return new UserProfile(ptr.elementAt(0), reader);
	}
	
	DataFile inputFile = 
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
	    //		upro.saveToFile(task, DataFile.Type.TJ_ALGO_2_USER_PROFILE_1);
	    if (uproFile==null) {
		
	    }
	    em.persist(uproFile);
	    inputFile = uproFile;
	}
	ptr.add(inputFile);
	return upro;
    }

}
