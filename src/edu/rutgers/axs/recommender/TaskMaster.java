package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.Common;

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
       -DexitAfter=24  : time in hours
       -Duser=username : only handle tasks for this one user (for testing)

       FIXME: this program has just one reader (never re-opened)
       during its operation, and a once-read
       CompactArticleStatsArray. This means that it should NOT ever
       run concurrently with the ArxivImporter, to avoid using stale
       data.
    */
    static public void main(String[] argv) throws Exception {
	Main.memory("start");
	ParseConfig ht = new ParseConfig();
	final int pid = Main.getMyPid();

	int exitAfter=ht.getOption("exitAfter", 0);
	Date exitAfterTime = (exitAfter<=0) ? null:
	    new Date( (new Date()).getTime() + exitAfter * 3600*1000);
	String onlyUser = ht.getOption("user", null);

	ShutDownThread shutDown = new ShutDownThread(Thread.currentThread(), exitAfterTime);
	Runtime.getRuntime().addShutdownHook(shutDown);	

	IndexReader reader =  Common.newReader();
	//	AllStatsReader asr = new  AllStatsReader(reader);
	Main.memory("main:calling CASA");
	CompactArticleStatsArray.CASReader asr = new CompactArticleStatsArray.CASReader(reader);
	//CompactArticleStatsArray casa = null;
	asr.start(); CompactArticleStatsArray casa = asr.getResults();

	// Use run() instead of start() for single-threading
	//asr.run();	

	Scheduler scheduler = new Scheduler();
	scheduler.setOnlyUser(onlyUser);
	int schedulingIntervalSec=ht.getOption("schedulingIntervalSec", 0);
	if (schedulingIntervalSec > 0) scheduler.setSchedulingIntervalSec(schedulingIntervalSec);
	scheduler.setArticlesUpdated( ht.getOption("articlesUpdated", false));


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
	    // make sure to use a new EM each time, to avoid looking at 
	    // stale date (esp. in the scheduler)
	    EntityManager em = Main.getEM(); 
	    task = grabNextTask(em,pid);
	    if (task==null) {

		if (scheduler.needsToRunNow()) {
		    Logging.info("no task: calling scheduler");
		    int newTaskCnt = scheduler.schedule(em);
		    continue;
		}

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
			   || op == Task.Op.LOG_SUGGESTIONS_1
			   || op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {

		    Vector<DataFile> ptr = new Vector<DataFile>(0);
		    if (task.getInputFile()!=null) {
			inputFile=DataFile.findFileByName(em, user,task.getInputFile()); 
			ptr.add(inputFile);
		    }

		    UserProfile upro = 
			getSuitableUserProfile(task, ptr, em, reader,
					       op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1 ?  DataFile.Type.TJ_ALGO_2_USER_PROFILE : DataFile.Type.USER_PROFILE);

		    inputFile = ptr.elementAt(0);
		    final boolean raw=true;
		    //ArticleStats[] allStats = asr.getResults();
		    //		    CompactArticleStatsArray casa = asr.getResults();
		    //Logging.info("Read CASA, size=" + casa.size());

		    boolean useLog = (op == Task.Op.LOG_SUGGESTIONS_1);
		    int days = task.getDays();		    
		    ArxivScoreDoc[] sd;
		    Date since =null;

		    if (op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
			// restricting the scope by category and date,
			// as per Thorsten 2012-06-18
			User u = User.findByName( em, user);
			String[] cats = u.getCats().toArray(new String[0]);
			since = task.getSince();
			if (since==null) since = SearchResults.daysAgo( days );
			IndexSearcher searcher = new IndexSearcher(reader);	
			// Lucene search + set scores for use in tie-breaking
			int maxlen = 10000;
			SearchResults sr = 
			    SubjectSearchResults.orderedSearch(searcher,u,since, maxlen);
			sd = ArxivScoreDoc.toArxivScoreDoc( sr.scoreDocs);
			Logging.info("since="+since+", cat search got " +sd.length+ " results");
			searcher.close();
			// rank by TJ Algo 1
			TjAlgorithm1 algo = new TjAlgorithm1();
			sd = algo.rank( upro, sd, casa, em, maxDocs);

			// FIXME: is there a better place for the day-setting?
			boolean isTrivial = (upro.terms.length==0);
			if (isTrivial) {
			    u.forceNewDay(User.Day.LEARN);
			} else {
			    u.forceNewDay();
			}
			em.persist(u);

		    } else {
			sd = raw? upro.luceneRawSearch(maxDocs,casa,em,days, useLog):
			upro.luceneQuerySearch(maxDocs, days);
		    }

		    Vector<ArticleEntry> entries = upro.packageEntries(sd);
		    Logging.info("since="+since+", |sd|=" +sd.length+", |entries|=" + entries.size());
		    
		    outputFile=new DataFile(task);
		    outputFile.setDays(days);
		    if (since!=null) outputFile.setSince(since);
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
		    // an empty (zero) profile. (as per TJ, 2012-06-19).
		    // The day range does not matter, as the user is allowed 
		    // to change it		   

		    inputFile = DataFile.
			getLatestFile(em, user,
				      DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1, 
				      DataFile.Type.TJ_ALGO_2_USER_PROFILE,
				      -1);
		    
		    //		    if (inputFile==null) {
		    //	inputFile = DataFile.getLatestFile(em, user,  DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1);
		    //		    }
		    
		    UserProfile upro;

		    ArxivScoreDoc[] sd;
		    if (inputFile==null) {
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
			searcher.close();
		    }

		    TjAlgorithm2 algo=new TjAlgorithm2( upro);
		    algo.updateProfile(user, em, sd);
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
		task.setCompleteTime(new Date());
		task.setFailed(!success);
		if (outputFile!=null) {
		    task.setOutputFile(outputFile.getThisFile());
		    em.persist(outputFile);
		}
		if (inputFile!=null) {
		    task.setInputFile(inputFile.getThisFile());
		}
		Logging.info("task=" + task+", recording success="+success);
		em.persist(task);
		ResultsBase.ensureClosed(em);
	    }
	}
	reader.close();
	Logging.info("Finished");
    }

    /** Gets the next available task to be fulfilled from the Task
     * table, and marks it as "started" */
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
	as an output param, reporting to what data file we have read
	the user profile from, or to what file we have just written
	the user profile, as the case may be.

	@param ptype   DataFile.Type.USER_PROFILE or ...

     */
    private static UserProfile 
	getSuitableUserProfile(Task task, Vector<DataFile> ptr, 
			       EntityManager em, IndexReader reader,
			       DataFile.Type ptype)
	throws IOException {

	if (ptr.size()>0) { //explicitly specified input file
	    return new UserProfile(ptr.elementAt(0), reader);
	}
	
	DataFile inputFile = 
	    DataFile.getLatestFile(em, task.getUser(), ptype);

	UserProfile upro;
	if (inputFile != null) {
	    // read it
	    upro = new UserProfile(inputFile, reader);
	} else {
	    // generate it
	    if (ptype== DataFile.Type.USER_PROFILE) {
		// based on user activity, in a linear way (obsolete)
		upro = new UserProfile(task.getUser(), em, reader);
	    } else if (ptype==  DataFile.Type.TJ_ALGO_2_USER_PROFILE) {
		// empty profile, as TJ wants (06-2012)
		upro = new UserProfile(reader);
	    } else {
		throw new AssertionError("TM.getSuitableUserProfile(): unsupported type=" + ptype);
	    }
	    DataFile uproFile=
		upro.saveToFile(task, ptype);
	    //		upro.saveToFile(task, DataFile.Type.TJ_ALGO_2_USER_PROFILE_1);
	    if (uproFile==null) {
		
	    }
	    em.persist(uproFile);
	    inputFile = uproFile;
	}
	ptr.add(inputFile);
	return upro;
    }

    /*
    private static  ArxivScoreDoc[] algo1Wrapper(EntityManager em, String user, int days,
				IndexReader reader, UserProfile upro) {
	// restricting the scope by category and date,
	// as per Thorsten 2012-06-18
	User u = User.findByName( em, user);
	String[] cats = u.getCats().toArray(new String[0]);
	Date since = SearchResults.daysAgo( days );
	IndexSearcher searcher = new IndexSearcher(reader);	
	SearchResults sr = new SubjectSearchResults(searcher, cats, since, 10000);
	// set scores for use in tie-breaking
	sr.setCatSearchScores(reader, sr.scoreDocs,cats,since);
	
	ArxivScoreDoc[] sd = ArxivScoreDoc.toArxivScoreDoc( sr.scoreDocs);
	searcher.close();
	// rank by TJ Algo 1
	TjAlgorithm1 algo = new TjAlgorithm1();
	sd = algo.rank( upro, sd, casa, em, maxDocs);
	
	// FIXME: is there a better place for the day-setting?
	boolean isTrivial = (upro.terms.length==0);
	if (isTrivial) {
	    u.forceNewDay(User.Day.LEARN);
	} else {
	    u.forceNewDay();
	}
	em.persist(u);
	return sd;
    }
    */
}
