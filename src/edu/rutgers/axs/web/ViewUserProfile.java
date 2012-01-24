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
public class ViewUserProfile extends ResultsBase {
    /** The user name of the user whose activity we reasearch */
    public String actorUserName=null;
    public User actor;

    public UserProfile upro=null;

    public Task activeTask = null, queuedTask=null, newTask=null;

    public DataFile df =null;

    public ViewUserProfile(HttpServletRequest _request, HttpServletResponse _response, boolean self) {
	super(_request,_response);
	if (error) return;

	actorUserName = self ? user :  getString(USER_NAME, null);

	boolean force= getBoolean(FORCE, false);

	EntityManager em = sd.getEM();
	try {

	    if (actorUserName==null) throw new WebException("No user name specified!");
	    em.getTransaction().begin();
	    df = DataFile.getLatestFile(em, actorUserName, 
					DataFile.Type.USER_PROFILE);

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
	    
	    if (force) {
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
		File f = df.getFile();
		upro = new UserProfile(f, ArticleAnalyzer.getReader());
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    //em.close(); 
	}
    }

    

}
