package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.web.ResultsBase;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

/** A Scheduler object is created by the TaskMaster, and is used to
    automatically create and schedule data-updating tasks.  In particular, it
    checks user activity and schedules user profile updates and
    suggestion list updates for the users who have had recent activity
    not yet reflected in user profiles and suggestion lists.  */
public class Scheduler {
    
    /** When did this scheduler last run? (More precisely, when did it
	finish running?)
     */
    private Date lastSchedulingRunAt = null;

    /** Every so often, see if we should check user activity
	and schedule more events.
    */
    private int schedulingIntervalSec = 10 * 60;

    int getSchedulingIntervalSec()  {
	return schedulingIntervalSec;	
    }
   
    void setSchedulingIntervalSec(int x) {
	schedulingIntervalSec = x;	
    }

    /** This flag is only set via the set method */
    private boolean articlesUpdated=false;

    boolean getArticlesUpdated() { 
	return  articlesUpdated;
    }

    /** Set this to true to tell the schedulers that (the local copy
	of) ArXiv has been updated, and we must update suggestion 
	lists too.
    */
    void setArticlesUpdated(boolean x) {
	articlesUpdated =x; 
	Logging.info("set articlesUpdated=" + articlesUpdated);
    }

    /** How often do run TJ's Algorithm 2 to update the UP2 user profile?
     */
    final static int updateUP2intervalSec = 24 * 3600;

    /** When the scheduler was created. This is used to figure that a
	particular suggestion list predates this run. */
    private final Date startTime = new Date();

    private EntityManager em;

    Scheduler(	EntityManager _em ) {
	em = _em;	
    }


    /** Do we need to run the scheduler again? */
    boolean needsToRunNow() {
	if (lastSchedulingRunAt == null) return true;
	if (stage2) return true; 
	Date now = new Date();
	return (now.getTime() >= lastSchedulingRunAt.getTime() + 
		 schedulingIntervalSec * 1000);
    }
    
    /** Scheduling is run in two alternating stages: profile
	generation (1) and suggestion generation (2). This flag is
	false at the profile generation stage, true at the suggestion
	generation stage.
     */
    private boolean stage2 = false;

    /** This is the Scheduler's main method. It is called by the
	TaskMaster's main thread every few minutes. On each call, it
	decides what type of updates it will be scheduling now
	(profile updates or suggestion list updates), and checks
	every user's situation, scheduling necessary updates for him.

	As a side effect, this method  flips the stage2 flag,
	so that consecutive calls to it schedule alternating types
	of operations.
    */
    int schedule() {
	int createdCnt=0;
	//	String qs = "select u.id from User u where " +
	//	    "(select max(a.id) from Action a where a.user = u) > "+
	//	    "(select max(df.lastActionId) from DataFile df where df.type=:t and df.days=0 and df.user = u.user_name)";

	String qs = "select u.id from User u where u.program=:p";

	Query q = em.createQuery(qs);
	q.setParameter("p",User.Program.SET_BASED);
	//	q.setParameter("t",  DataFile.Type.LINEAR_SUGGESTIONS_1);

	List<Long> lu  =  (List<Long>) q.getResultList();
	//Logging.info("Scheduler: Found " + lu.size() + " users whose UP or SL may need updating");
	Logging.info("Scheduler (stage="+(stage2?"SL":"UP")+"), au="+articlesUpdated+": Found " + lu.size() + " users");

	for(long uid: lu) {
	    User u = (User)em.find(User.class, uid);
	    // apparently, "refresh" may be needed for us to notice recent changes 
	    em.refresh(u);
	    String uname= u.getUser_name();
	    long lai = u.getLastActionId();


	    //if (lai <= 0) {
		//Logging.info("Scheduler: user " + uname + "; skip, due to no activity ever");
	    //	continue;
	    //}
	    if (u.catCnt()==0) {
		// Main suggestion lists are based on categories now...
		Logging.info("Scheduler: user " + uname + "; skip (catCnt=0)");
		continue;
	    }


	    if (!stage2) { // profile generation stage
		int days=0; // does not matter for UP

		// update the UP0 profile?
		DataFile.Type mode = DataFile.Type.USER_PROFILE;
		DataFile latestProfile= DataFile.getLatestFile(em, uname, mode);
		long plai = (latestProfile==null)? -1: latestProfile.getLastActionId();
		boolean need = ( latestProfile == null) || (plai < lai);
		
		Logging.info("Scheduler: user " + uname + "; lai="+lai+", plai="+plai+"; needed UP0 update? " + need);		
		if (need) {
		    addTask(em, uname, mode, days, null, null);
		    createdCnt++;
		}

		// update the UP2 profile?
		mode = DataFile.Type.TJ_ALGO_2_USER_PROFILE;
		latestProfile= DataFile.getLatestFile(em, uname, mode);
		need = (latestProfile == null) ||
		    (latestProfile.getLastActionId() < lai &&
		     latestProfile.getTime().getTime() +  updateUP2intervalSec <
		     (new Date()).getTime());
		Logging.info("Scheduler: user " + uname + "; needed UP2 update? " + need);		
		if (need) {
		    addTask(em, uname, mode, days, null, null);
		    createdCnt++;
		}

	    } else { // suggestion list generation stage
		//  Different types of suggestion lists to generate
		DataFile.Type modes[] = {
		    //		    DataFile.Type.LINEAR_SUGGESTIONS_1,
		    //		    DataFile.Type.LOG_SUGGESTIONS_1,
		    DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1};

		for(DataFile.Type mode: modes) {

		    // (0 means "all docs")
		    // (mode==DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1)?

		    int days= u.getDays(); // user-specific search horizon
		    if (days==0) days = Search.DEFAULT_DAYS;


		    DataFile.Type profileType = 
			(mode== DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1) ?
			DataFile.Type.TJ_ALGO_2_USER_PROFILE :
			DataFile.Type.USER_PROFILE;

		    DataFile latestProfile = 
			DataFile.getLatestFile(em, uname, profileType);
		    if ( latestProfile==null) {
			Logging.warning("Scheduler: user " + uname + "; cannot do "+mode +" update because there is no " + profileType + " profile available");
			continue;
		    }
		    long plai = latestProfile.getLastActionId();
		    DataFile sugg = DataFile.getLatestFileBasedOn(em, uname, mode, days, profileType);
		    boolean need = sugg==null || (sugg.getLastActionId() < plai);

		    // If the articles have been updated, profiles sugg lists
		    // must be updated too.
		    need = need || articlesUpdated && sugg.getTime().before(startTime);


		    Logging.info("Scheduler: au="+articlesUpdated+", user " + uname + "; needed "+mode+" ("+days +"d) update? " + need);
		    
		    if ( articlesUpdated && !need) {
			Logging.info("Explain au: sugg.getTime()="+sugg.getTime()+", startTime=" +startTime + ", before=" +   sugg.getTime().before(startTime));
		    }

		    if (need) {
			String requiredInput =
			    (mode== DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1) ?
			    latestProfile.getThisFile() : null;


			Date lastViewed = dateOfLastSeenSugList( em, uname);

			Date since = null;
			if (lastViewed==null) {
			    final int maxRange = 90;
			    since = Common.plusDays(new Date(), -maxRange);
			} else {
			    since = Common.plusDays(new Date(), -days);
			    if (since.after(lastViewed)) since = lastViewed;
			}
			Logging.info("For user " + uname + ", last viewed sug list generated at " +  lastViewed + "; set since=" + since);

			addTask(em, uname, mode, days, since, requiredInput );
			createdCnt++;
		    }
		}
	    }
	   
	}

	lastSchedulingRunAt = new Date();

	stage2 = !stage2; // flip the flag
	return createdCnt;
    }

    static private Date dateOfLastSeenSugList(EntityManager em, String  uname) {

	PresentedList  plist = PresentedList.findMostRecentPresntedSugList(em,  uname);
	if (plist == null) {
	    return null;
	}
	long dfid = plist.getDataFileId();
	if (dfid <= 0) return null;
	DataFile df = (DataFile)em.find(DataFile.class, dfid);
	if (df==null) return null;
	Date dfTime = df.getTime();
	System.out.println("Looks like the last sug list (df="+dfid+") presented to the user " + uname + " was generated at " + dfTime);
	return dfTime;
    }



    //    private void addTask(EntityManager em, String uname, DataFile.Type mode, int days, Date since){
    //	addTask(em, uname, mode, days, since, null);
    //    }

    /** Creates a new task of with the required parameters.
     */
    private void addTask(EntityManager em, String uname, DataFile.Type mode, int days, Date since, String basedon) {
	Task.Op taskOp = mode.producerFor(); // producer task type
	em.getTransaction().begin();
	Task newTask = new Task(uname, taskOp);
	newTask.setDays(days);
	if (since!=null) newTask.setSince(since);
	if (basedon!=null) newTask.setInputFile(basedon);
	em.persist(newTask);
	em.getTransaction().commit();
	Logging.info("Scheduler: user " + uname + "; created task: " + newTask);
    }


    /** Mostly used just for testing */
    static public void main(String[] argv) throws Exception {
	Main.memory("start");
	ParseConfig ht = new ParseConfig();
	EntityManager em = Main.getEM();
	Scheduler scheduler = new Scheduler( em );
	scheduler.schedule();
    }

}