package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.ResultsBase;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

/** A Scheduler object is created by the TaskMaster, and is used to
    automatically schedule data-updating tasks.  In particular,
    it checks user activity and schedule user profile updates
    and suggestion list updates for users who have had recent activity
    not yet reflected in user profiles and suggestion lists.
*/
public class Scheduler {
    
    /** When did this scheduler last run? (More precisely, when did it
	finish running?)
     */
    private Date lastSchedulingRunAt = null;

    /** every so often, see if we should check user activity
	and schedule more events.
    */
    final static int schedulingCheckIntervalSec = 2 * 60;

    /** How often do run TJ's Algorithm 2 to update the UP2 user profile?
     */
    final static int updateUP2intervalSec = 24 * 3600;

    private EntityManager em;

    Scheduler(	EntityManager _em ) {
	em = _em;
    }


    /** Do we need to run the scheduler again? */
    boolean needsToRunNow() {
	if (lastSchedulingRunAt == null) return true;
	Date now = new Date();
	return (now.getTime() >= lastSchedulingRunAt.getTime() + 
		 schedulingCheckIntervalSec * 1000);
    }
    
    /** Scheduling is run in two alternating stages: profile
	generation (1) and suggestion generation (2). This flag is
	false at the profile generation stage, true at the suggestion
	generation stage.
     */
    private boolean stage2 = false;

    int schedule() {
	int createdCnt=0;
	//	String qs = "select u.id from User u where " +
	//	    "(select max(a.id) from Action a where a.user = u) > "+
	//	    "(select max(df.lastActionId) from DataFile df where df.type=:t and df.days=0 and df.user = u.user_name)";

	String qs = "select u.id from User u";

	Query q = em.createQuery(qs);
	//	q.setParameter("t",  DataFile.Type.LINEAR_SUGGESTIONS_1);

	List<Long> lu  =  (List<Long>) q.getResultList();
	//Logging.info("Scheduler: Found " + lu.size() + " users whose UP or SL may need updating");
	Logging.info("Scheduler (stage="+(stage2?"SL":"UP")+"): Found " + lu.size() + " users");

	//for(long uid: lu) {	    Logging.info("uid=" + uid);	}

	for(long uid: lu) {
	    User u = (User)em.find(User.class, uid);
	    // apparently, "refresh" may be needed for us to notice recent changes 
	    em.refresh(u);
	    String uname= u.getUser_name();
	    long lai = u.getLastActionId();
	    if (lai <= 0) {
		Logging.info("Scheduler: user " + uname + "; skip, due to no activity ever");
		continue;
	    }

	    if (!stage2) { // profile generation stage
		int days=0; // does not matter for UP

		// updated the UP0 profile?
		DataFile.Type mode = DataFile.Type.USER_PROFILE;
		DataFile latestProfile= DataFile.getLatestFile(em, uname, mode);
		long plai = (latestProfile==null)? -1: latestProfile.getLastActionId();
		boolean need = ( latestProfile == null) || (plai < lai);
		
		Logging.info("Scheduler: user " + uname + "; lai="+lai+", plai="+plai+"; needed UP0 update? " + need);		
		if (need) {
		    addTask(em, uname, mode, days);
		    createdCnt++;
		}

		// update the UP2 profile?
		mode = DataFile.Type.TJ_ALGO_2_USER_PROFILE;
		latestProfile= DataFile.getLatestFile(em, uname, mode);
		need = ( latestProfile == null) ||
		    (latestProfile.getLastActionId() < lai &&
		     latestProfile.getTime().getTime() +  updateUP2intervalSec <
		     (new Date()).getTime());
		Logging.info("Scheduler: user " + uname + "; needed UP2 update? " + need);		
		if (need) {
		    addTask(em, uname, mode, days);
		    createdCnt++;
		}

	    } else { // suggestion list generation stage
		int days=0; // (all docs)
		// (A) linear suggestions based on a "plain" user profile
		DataFile.Type profileType = DataFile.Type.USER_PROFILE;
		DataFile.Type mode = DataFile.Type.LINEAR_SUGGESTIONS_1;
	
		DataFile latestProfile = 
		    DataFile.getLatestFile(em, uname, profileType);
		if ( latestProfile==null) {
		    Logging.warning("Scheduler: user " + uname + "; cannot do SUG1 update because there is no " + profileType + " profile available");
		    continue;
		}
		long plai = latestProfile.getLastActionId();
		DataFile sugg = DataFile.getLatestFileBasedOn(em, uname, mode, days, profileType);
		boolean need = sugg==null || (sugg.getLastActionId() < plai);
		Logging.info("Scheduler: user " + uname + "; needed SUG1 update? " + need);
		if (need) {
		    addTask(em, uname, mode, days);
		    createdCnt++;
		}

		// (B) Algo 1 suggestions based on an Algo 2 profile 
		profileType = DataFile.Type.TJ_ALGO_2_USER_PROFILE;
		mode = DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1;
		latestProfile = 
		    DataFile.getLatestFile(em, uname, profileType);
		if ( latestProfile==null) {
		    Logging.warning("Scheduler: user " + uname + "; cannot do SUG2 update because there is no " + profileType + " profile available");
		    continue;
		}
		plai = latestProfile.getLastActionId();
		sugg = DataFile.getLatestFileBasedOn(em, uname, mode, days, profileType);
		need = sugg==null || (sugg.getLastActionId() < plai);
		Logging.info("Scheduler: user " + uname + "; needed SUG2 update? " + need);
		if (need) {
		    addTask(em, uname, mode, days, latestProfile.getThisFile());
		    createdCnt++;
		}	
	    }
	   
	}

	lastSchedulingRunAt = new Date();

	stage2 = !stage2; // flip the flag
	return createdCnt;
    }

    private void addTask(EntityManager em, String uname, DataFile.Type mode, int days){
	addTask(em, uname, mode, days, null);
    }

    private void addTask(EntityManager em, String uname, DataFile.Type mode, int days, String basedon) {
	Task.Op taskOp = mode.producerFor(); // producer task type
	em.getTransaction().begin();
	Task newTask = new Task(uname, taskOp);
	newTask.setDays(days);
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