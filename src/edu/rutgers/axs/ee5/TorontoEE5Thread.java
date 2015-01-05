package edu.rutgers.axs.ee5;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.commons.lang.mutable.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.*;

import edu.rutgers.axs.upload.BackgroundThread;

/** Data processing that needs to be carried out once the user has
    uploaded all personal documents he's wanted to upload. This
    includes carrying out category-blind cluster assignment on these
    documents, updating the user profile data, and generating a suggestion
    list.
 */
public class TorontoEE5Thread extends BackgroundThread {

    /** This will be set to true if we have to ask user's permission to add categories.
	In this case, newCatReport is also set to contain the list of these additional
	cats.
     */
    public boolean needCatApproval=false;
    /** New categories discoverd by the cluster assignment.
     */
    public Classifier.CategoryAssignmentReport newCatReport = null;
    /** An HTML snippet with checkboxes, based on newCatReport */
    public String catBoxes = null;

    public String getProgressText() {
	String s = (startTime == null ?
		    "Data processing is about to start...\n" :
		    "Data processing started at " + startTime + "\n");
	s += progressText.toString();
	if (progressTextMore != null) {
	    s += progressTextMore + "\n";
	}
	if (endTime != null) {
	    s += "\n\nData processing completed at " + endTime;
	}
	return s;
    }

    private final String user;
    /** This thread has been created only to do Stage Two. (Stage One was completed
	before, and then we went thru the cat list approval) */
    private boolean doStageTwo;

    /** Creates a thread which will create the initial user profile
	and sug list.
     */
    public TorontoEE5Thread(String _user, boolean _doStageTwo) {
	user = _user;
	doStageTwo = _doStageTwo; 
    }

    /** There are two stages to the EE5 profile initialization, and
	this call may carry out one or both. 

	<p>
	Once the documents have been uploaded and the user clicked on
	the "generate recommendations" button, a TorontoEE5Thread will
	be created with doStageTwo=false. Its run method then will start
	with Stage One; if the results of Stage One indicate that we
	want the user to approve the addition of more categories, the
	method quits right here, otherwise it continues with Stage Two
	as well.

	<p>If the first  TorontoEE5Thread run ended with Stage One, the
	user will be shown the list of suggested categories to add, and
	will have to approve them. After that, a new  TorontoEE5Thread will
	be created, with doStageTwo=true. In that thread, the run() method will
	immediately proceed with Stage Two.
     */
    public void run()  {
	startTime = new Date();
	EntityManager em = null;
	IndexReader reader =  null;
	try {

	    pin = new ProgressIndicator(100, true);
	    
	    em  = Main.getEM();
	    reader = Common.newReader();
	    IndexSearcher searcher = new IndexSearcher( reader );
	   
	    final User.Program program = User.Program.EE5;
	    User u = User.findByName( em, user);
	    if (u==null) {
		error("User " + user + " does not exist");
		return;
	    }
	    if (!u.getProgram().equals(program)) {
		error("User " +user+ " is not enrolled in program " + program);
	    }

	    if (!doStageTwo) {
		newCatReport = stageOne( em, searcher, u);
		if (newCatReport.size()>0) {
		    needCatApproval=true;
		    catBoxes = Categories.mkCatBoxes2(u, newCatReport);
		    return;
		}
	    }

	    stageTwo( em, searcher, u);

	} catch(OutOfMemoryError ex) {	    
	    error("OutOfMemoryError in TorontoEE5Thread");
	    System.out.println("[out] exception report:");
	    ex.printStackTrace(System.out);
	} catch(Exception ex) {
	    String errmsg = ex.getMessage();
	    error("Exception for TorontoEE5Thread " + getId() + ": " + errmsg);
	    ex.printStackTrace(System.out);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    if (reader != null) {
		try {
		    reader.close();
		} catch(IOException ex) {}
	    }
	    endTime = new Date();
	}

    }

    private Classifier.CategoryAssignmentReport stageOne(EntityManager em, IndexSearcher searcher, User u) throws IOException {
       
	EE5DocClass.CidMapper cidMap = new EE5DocClass.CidMapper(em);
	ScoreDoc[] sd = Common.findAllUserFiles(searcher, u.getUser_name());
	progress("Will categorize " + sd.length + " docs uploaded by user " +u);
	Classifier.CategoryAssignmentReport report = 
	    Classifier.classifyNewDocsCategoryBlind(em, searcher.getIndexReader(), sd, cidMap, true, this);
	int n0 = report.size();
	for(String cat: u.getCats()) {
	    report.remove(cat);
	}
	int n = report.size();	
	progress("Uploaded docs assigned to " + n0 + " categories; " + report.size() + " of them are not on your original category list");
	return report;
    }	
    
    private void stageTwo(EntityManager em, IndexSearcher searcher, User u) throws IOException {
	final int days = EE5DocClass.TIME_HORIZON_DAY;
	Date since = SearchResults.daysAgo( days );
	HashMap<Integer,EE5DocClass> id2dc=EE5DocClass.CidMapper.readDocClasses(em);
	DataFile df = Daily.makeEE5Sug(em,  searcher, since, id2dc, u);
    }

}