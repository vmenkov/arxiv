package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.index.IndexReader;


import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;
import edu.rutgers.axs.indexer.Common;

/** Retrieves and displayed a user profile file, which contains a
 * weighted list of terms.
 */
@SuppressWarnings("unchecked")
public class ViewUserProfile extends PersonalResultsBase {

    public UserProfile upro=null;

    public Task activeTask = null, queuedTask=null, newTask=null;

    /** The file, if any, whose content is being displayed. */
    public DataFile df =null;
    public int id = 0;

    /** In some applications (Algo 2), the "ancestor" data file 
	(specifically, suggestion list) based on which the currently
	displayed data file has been generated.
     */
    public DataFile ancestor =null;

    /** The currently recorded last action id for the user in question */
    public long actorLastActionId=0;

    public long reflectedOpCnt=0, allOpCnt=0;

    public DataFile.Type mode =  DataFile.Type.PPP_USER_PROFILE;
    //DataFile.Type.USER_PROFILE;

    public String sugTable="No matching suggestions lists have been found", 
	thisUproTable = "Description of this user profile goes here",
	prevUproTable = "It is not know based on which older user profile this user profile has been created",
	nextUproTable = "No user profile created based on this profile has been found";

    public boolean showAll = false;

    /**
       FIXME: ought to add reader.close() code, but before that, must modify
       the JSPs so that they won't be using it
     */
    public ViewUserProfile(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	if (error) return;

	mode = (DataFile.Type)getEnum(DataFile.Type.class, MODE, mode);
	if (modeIsWrong()) return;
	   
	showAll = getBoolean("all", showAll);

	id = (int)getLong(ID, 0);
	if (id > 0 && requestedFile!=null) {
	    error=true;
	    errmsg="Cannot combine parameters '"+ID+"' and '"+FILE+"'";
	    return;	    
	}
       
	EntityManager em = sd.getEM();
	IndexReader reader = null;
	try {

	    setActor(em);
	    em.getTransaction().begin();

	    if (id>0) {
		df = (DataFile)em.find(DataFile.class, id);
		String actorUserName1 = df.getUser();
		if (!actorUserName.equals(actorUserName1)) {
		    actorUserName = actorUserName1;
		    setActor(em);
		}
	    } else if (requestedFile!=null) {
		df = DataFile.findFileByName(em, actorUserName, requestedFile);
	    } else {
		df = DataFile.getLatestFile(em, actorUserName, mode);
	    }
	    mode = df.getType();
	    infomsg += "Determined mode=" + mode + ", file"+df.getThisFile()+"<br>\n";
	    if (modeIsWrong()) return;

	    allOpCnt = actor.actionCnt( em);
	    reflectedOpCnt = actor.actionCnt( em, df.getLastActionId());

	    // tasks were only used in the pre-3PR world
	    if (mode!=DataFile.Type.PPP_USER_PROFILE) manageTasks(em);

	    em.getTransaction().commit();

	    if (df!=null) {
		reader = Common.newReader();
		//ArticleAnalyzer aa=new ArticleAnalyzer(reader,ArticleAnalyzer.upFields);
		upro = new UserProfile(df, null);

		if (df.getType()== DataFile.Type.TJ_ALGO_2_USER_PROFILE) {
		    ancestor =  df.getInputFile();
		    //	DataFile.findFileByName(em,actorUserName, df.getInputFile());
		}

		DataFile.Type expectedSugType = df.getType().profileToSug();
		sugTable = mkDerivedFilesTable(em, expectedSugType, df);
		nextUproTable = mkDerivedFilesTable(em, mode, df);
		DataFile parent=df.getInputFile();
		if (parent!=null) {
		    prevUproTable = mkTable(parent);
		}
		thisUproTable = mkTable(df);
	    }

	}  catch (WebException _e) {
	    error = true;
	    errmsg = _e.getMessage();
	    return;
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    // Don't close the reader: it will still be used from the JSP file
	    //	    try {
	    //		if (reader!=null)   reader.close();
	    //	    } catch(IOException ex) {}
	}
    }

    private void setActor(EntityManager em) throws WebException{
	if (actorUserName==null) throw new WebException("No user name specified!");
	if (!actorUserName.equals(user) && !runByResearcher()) {
	    throw new WebException("Non-researcher users cannot view other users' profiles");
	}
	actor = User.findByName(em, actorUserName);
	actorLastActionId= actor.getLastActionId();
    }


    private boolean modeIsWrong() {
	if (mode== DataFile.Type.USER_PROFILE ||
	    mode== DataFile.Type.TJ_ALGO_2_USER_PROFILE||
	    mode== DataFile.Type.PPP_USER_PROFILE) return false;
	error=true;
	errmsg="DataFile type " + mode + " is not supported as a user profile type";
	return true;
    }


    /** How many operations have been recorded for a given user? */
    /*
    static private int actionCnt(EntityManager em, String actorUserName, long maxId) {
	String qs= "select count(a) from Action a where a.id<=:m and a.user.user_name=:u";
	Query q = em.createQuery(qs);
	
	q.setParameter("u",actorUserName );
	q.setParameter("m",  maxId);

	try {
	    Object o = q.getSingleResult();
	    return ((Number)o).intValue();
	} catch(Exception ex) { return 0; }
    }
    */

    private void manageTasks(EntityManager em) {
	    Task.Op op = mode.producerFor();
	    List<Task> tasks = 
		Task.findOutstandingTasks(em, actorUserName, op);

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
			infomsg += "Update task not created, because the most recent update was completed less than " + minMinutesAllowed + " minutes ago. (Load control).";
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
		newTask = new Task(actorUserName, op);
		em.persist(newTask);
	    }	    

    }

    
    private static String mkTable(DataFile df) {
	return "<table border=1>" +
	    Reflect.htmlHeaderRow(df.getClass(), true) + "\n"+
	    Reflect.htmlRow(df, true) +
	    "</table>";
    }


    private String mkDerivedFilesTable(EntityManager em, DataFile.Type t, DataFile parent) {
	String query = "select m from DataFile m where  m.user=:u and  m.type=:t and m.deleted=FALSE and m.inputFile=:sf";

	Query q = em.createQuery(query);

	q.setParameter("u", actorUserName);
	q.setParameter("t", t);
	q.setParameter("sf", parent);

	List<DataFile> list = q.getResultList();
	StringWriter sw=new StringWriter();
	QueryServlet.printList(new PrintWriter(sw), query, list, true, true);
	return sw.toString();

    }


}
