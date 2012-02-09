package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/** Retrieves the list of artciles on which the user performed various actions,
    and ranks them based on these actions.
 */
public class ViewUserProfile extends PersonalResultsBase {

    public UserProfile upro=null;

    public Task activeTask = null, queuedTask=null, newTask=null;

    public DataFile df =null;

    /** The currently recorded last action id for the user in question */
    public long actorLastActionId=0;

    public ViewUserProfile(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	if (error) return;

	EntityManager em = sd.getEM();
	try {

	    if (actorUserName==null) throw new WebException("No user name specified!");
	    actor = User.findByName(em, actorUserName);
	    actorLastActionId= actor.getLastActionId();
	    em.getTransaction().begin();

	    if (requestedFile!=null) {
		df = DataFile.findFileByName(em, actorUserName, requestedFile);
	    } else {
		df = DataFile.getLatestFile(em, actorUserName, 
					    DataFile.Type.USER_PROFILE);
	    }

	    List<Task> tasks = 
		Task.findOutstandingTasks(em, actorUserName, 
					  Task.Op.HISTORY_TO_PROFILE);
	    if (tasks != null) {
		for(Task t: tasks) {
		    if (t.appearsActive()) {
			activeTask=t; 
			break;
		    } else if (!t.getCanceled()){			    
			queuedTask = t;
			break;
		    }
		}
	    }

	    // Do we need to request a new task?
	    boolean needNewTask = false;
	    
	    if (requestedFile!=null) {  // just a specific file-view request
		needNewTask = false;
	    } else if (force) {
		if (activeTask!=null) {
		    infomsg += "Update task not created, because a task is currently in progress already";
		} else if (queuedTask!=null) {
		    infomsg += "Update task not created, because a task is currently queued";
		} else if (df != null) {
		    long sec = ((new Date()).getTime() - df.getTime().getTime())/1000;
		    final int minMinutesAllowed = 10;
		    if (sec < minMinutesAllowed * 60) {
			infomsg += "Update task not created, because the most recent update was completed less than " + minMinutesAllowed + " minutes ago. (Loadf control).";
		    } else {
			needNewTask = true;
			infomsg += "Update task created as per request";
		    }
		}
	    } else {
		needNewTask= (df==null && activeTask==null && queuedTask==null);
		infomsg += "Update task created since there are no current data to show, and no earlier update task in queue";
	    }

	    if (needNewTask) {
		newTask = new Task(actorUserName, 
				   Task.Op.HISTORY_TO_PROFILE);
		em.persist(newTask);
	    }	    
	    em.getTransaction().commit();

	    if (df!=null) {
		upro = new UserProfile(df, ArticleAnalyzer.getReader());
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    //em.close(); 
	}
    }

}
