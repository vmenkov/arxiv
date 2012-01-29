package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/** Retrieves the list of artciles on which the user performed various actions,
    and ranks them based on these actions.
 */
public class ViewSuggestions extends ResultsBase {
    /** The user name of the user whose activity we reasearch */
    public String actorUserName;
    public User actor;
    public Task activeTask = null, queuedTask=null, newTask=null;

    /** Null indicates that no file has been found */
    public Vector<ArticleEntry> entries = null;//new Vector<ArticleEntry>();

    public DataFile df =null;
    public static final  String MODE="mode", DAYS="days";

    /** The type of the suggestion list, as specified by the HTTP query string*/
    public DataFile.Type mode= DataFile.Type.LINEAR_SUGGESTIONS_1;
    public int days=0;

    public static final int maxRows = 100;
    
    public boolean isSelf = false, force=false;
    

    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	force= getBoolean(FORCE, false);
	mode = (DataFile.Type)getEnum(DataFile.Type.class, MODE, mode);
	days = (int)getLong(DAYS, days);
	if (error) return; // authentication error?

	actorUserName =  getString(USER_NAME, user);
	isSelf = (actorUserName.equals(user));

	Task.Op taskOp = Task.Op.LINEAR_SUGGESTIONS_1;

	EntityManager em = sd.getEM();
	try {
	    final int maxDays=30;

	    if (days < 0 || days >maxDays) throw new WebException("The date range must be a positive number (no greater than " + maxDays+"), or 0 (to mean 'all dates')");

	    if (actorUserName==null) throw new WebException("No user name specified!");

	    em.getTransaction().begin();
	    
	    df = DataFile.getLatestFile(em, actorUserName, mode, days);

	    List<Task> tasks = 
		Task.findOutstandingTasks(em, actorUserName, taskOp);

	    if (tasks != null) {
		for(Task t: tasks) {
		    if (t.getDays()!=days) continue; // range mismatch
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
			infomsg += "Update task not created, because the most recent update was completed less than " + minMinutesAllowed + 
			    " minutes ago. (Load control).";
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
		newTask = new Task(actorUserName, taskOp);
		newTask.setDays(days);
		em.persist(newTask);
	    }

	    em.getTransaction().commit();

	    if (df!=null) {
		IndexReader reader=ArticleAnalyzer.getReader();
		IndexSearcher s = new IndexSearcher( reader );

		// read the artcile IDs and scores from the file
		File f = df.getFile();
		entries = ArticleEntry.readFile(f);
		
		applyUserSpecifics(entries, User.findByName(em, actorUserName));

		// In docs to be displayed, populate other fields from Lucene
		for(int i=0; i<entries.size() && i<maxRows; i++) {
		    ArticleEntry e = entries.elementAt(i);
		    int docno = e.getCorrectDocno(s);		    
		    Document doc = reader.document(docno);
		    e.populateOtherFields(doc);
		}
		reader.close();
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    //em.close(); 
	}
    }

    /** Applies this user's exclusions, folder inclusions, and ratings */
    void applyUserSpecifics( Vector<ArticleEntry> entries, User u) {
	if (u==null) return;
    
	HashMap<String, Action> exclusions = 
	    u.getActionHashMap(new Action.Op[]{Action.Op.DONT_SHOW_AGAIN});
		    
	// exclude some...
	// FIXME: elsewhere, this can be used as a strong negative
	// auto-feedback (e.g., Throsten's two-pager's Algo 2)
	for(int i=0; i<entries.size(); i++) {
	    if (exclusions.containsKey(entries.elementAt(i).id)) {
		entries.removeElementAt(i); 
		i--;
	    }
	}

	// Mark pages currently in the user's folder, or rated by the user
	ArticleEntry.markFolder(entries, u.getFolder());
	ArticleEntry.markRatings(entries, 
				 u.getActionHashMap(Action.ratingOps));
    }

    public String forceUrl() {
	String s = cp + "/viewSuggestions.jsp?" + USER_NAME + "=" + actorUserName;
	s += "&" + FORCE + "=true";
	s += "&" + MODE + "=" +mode;
	if (days!=0) 	    s +=  "&" + DAYS+ "=" +days;
	return s;
    }

}