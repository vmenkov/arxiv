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

/** Retrieves and displays a "suggestion list": a list of articles which some
    kind of automatic process has marked as potentially interesting to the user.
 */
public class ViewSuggestions extends PersonalResultsBase {

    public Task activeTask = null, queuedTask=null, newTask=null;

    /** Null indicates that no file has been found */
    public Vector<ArticleEntry> entries = null;//new Vector<ArticleEntry>();
    /** Data file whose content is to be displayed */
    public DataFile df =null;

    /** The type of the requested suggestion list, as specified by the
     * HTTP query string*/
    public DataFile.Type mode= DataFile.Type.LINEAR_SUGGESTIONS_1;
    /** Date range on which is the list should be based. (0=all time). */
    public int days=0;
    /** Max length to display. (Note that the suggestion list
     * generator may have its own truncatin criteria!)  */
    public static final int maxRows = 100;
    
    /** The currently recorded last action id for the user in question */
    public long actorLastActionId=0;
    /** If this is supplied, this specifies the particular user
	profile on which the requested suggestion list must be based.
     */
    public String basedon=null;

    public ViewSuggestions(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	mode = (DataFile.Type)getEnum(DataFile.Type.class, MODE, mode);
	days = (int)getLong(DAYS, days);
	basedon=getString(BASEDON,null);
	if (error) return; // authentication error?

	Task.Op taskOp = mode.producerFor(); // producer task type

	EntityManager em = sd.getEM();
	try {
	    final int maxDays=30;

	    if (days < 0 || days >maxDays) throw new WebException("The date range must be a positive number (no greater than " + maxDays+"), or 0 (to mean 'all dates')");

	    if (actorUserName==null) throw new WebException("No user name specified!");

	    em.getTransaction().begin();
	    
	    if (requestedFile!=null) {
		df = DataFile.findFileByName(em, actorUserName, requestedFile);
	    } else if (basedon!=null) {
		// look for the most recent sugestion list based on
		// the specified user profile file...
		df = DataFile.getLatestFileBasedOn(em, actorUserName, 
						   mode, days, basedon);
	    } else {
		df = DataFile.getLatestFile(em, actorUserName, mode, days);
	    }

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
		if (basedon!=null) newTask.setInputFile(basedon);
		em.persist(newTask);
	    }

	    em.getTransaction().commit();

	    if (df!=null) {

		IndexReader reader=ArticleAnalyzer.getReader();
		IndexSearcher s = new IndexSearcher( reader );

		// read the artcile IDs and scores from the file
		File f = df.getFile();
		entries = ArticleEntry.readFile(f);
		
		actor=User.findByName(em, actorUserName);
		actorLastActionId= actor.getLastActionId();
		applyUserSpecifics(entries, actor);

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
	// auto-feedback (e.g., Thorsten's two-pager's Algo 2)
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

    public String resultsDivHTML(ArticleEntry e) {
	return resultsDivHTML(e, isSelf);
    }
}