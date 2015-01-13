package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.recommender.TorontoPPPThread;
import edu.rutgers.axs.recommender.DailyPPP;
import edu.rutgers.axs.ee5.TorontoEE5Thread;
import edu.rutgers.axs.ee5.Classifier;

/** The "Toronto system": user profile initialization, once PDF documents
    have been uploaded by UploadPapers.

    <p>This is the back end for personal/uploadPapersInitProfile.jsp.
    That page can be invoked with a variety of query-string
    parameters, and different actions are carried out on different
    requests. In particular, check=true means that we have started a
    background thread on an earlier request (a few seconds or minutes ago),
    and now want to show the updated status of the background thread processing
    to the user.

    <p>
    FIXME: need a check for "do we need it now?" (Maybe profile is already available...)
*/
public class UploadPapersInitProfile  extends ResultsBase {

    /** The "check=true" in the query string means that the user want
        to check the status of the current load process. Otherwise, it
        is a request to initiate a new process.  */
    public boolean check=false;
    /** This is user's request for StageTwo of EE5 processing (user sending
	back approved cat list) */
    public boolean stageTwo=false;

    /** To be displayed when check=true */
    public String checkTitle="???", checkText="?????", checkProgressIndicator="<!-- n/a -->";
    
   /** This will be set to true if we want the client to retry
        loading this page (or a slightly different one) in a few second.
        This is used on "progress" pages
     */
    public boolean wantReload=false;
    public String reloadURL;

    /** Special flag for EE5 uploads; indicates that we now show an intermediate screen
	askin the user to approve the addition of some categories on interest (based
	on the uploaded papers that have been processed) */
    public boolean needCatApproval=false;
    public Classifier.CategoryAssignmentReport newCatReport = null;
    /** An HTML snippet with checkboxes, based on newCatReport */
    public String catBoxes = null;

    /** Possible states for this page */
    public static enum Status {
	/** There was a request to check the status of a running or recently
	    completed  thread, but there was nothing to check!  Telling this to the
	    user and asking him to go to the main upload page. */
	NONE,
	    /** Work thread currently running. Display current progress and auto-reload */
	    RUNNING,
	    /** Thread completed, but only done part of the work. The
		user needs to respond before the second stage can
		commence */
	    DONE_NEED_APPROVAL,
	    /** All done; give the user a link to main page to view sug list */
	    DONE_ALL,
	    /** Thread finished with an error. Nothing much can be done... */
	    DONE_ERROR,
	    };

    public Status status = null;
 
    /** There are two main kinds of requests to uploadPapersInitProfile.jsp (which result 
	in the invocation of this constructor):
	<ul>
	<li>Those with check=false are meant to initiate processing.
	<li>Those with check=true are meant to inquire about the status of the processing
	that was started earlier, so that the status page can be updated.
	</ul>
     */
   public UploadPapersInitProfile(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	reloadURL = getReloadURL(false);

	check = getBoolean("check", check);
	stageTwo = getBoolean("stageTwo", check);

	if (check) { 
	    checkStatus();
            return;
        } 

	if (sd.upInitThread!=null && sd.upInitThread.getState()!=Thread.State.TERMINATED){
	    status = Status.RUNNING;
	    check = true;
            wantReload = false;
            checkTitle = "Processing is already going on";
            checkText =
                "Processing is already going on.\n" +
                "Uploading thread state = " + sd.upInitThread.getState()+ "\n"+
                sd.upInitThread.getProgressText();
            reloadURL = getReloadURL(true);       
            checkProgressIndicator=sd.upInitThread.getProgressIndicatorHTML(cp);
            return;
	}

	// starting a new profile initialization process
	EntityManager em = sd.getEM();
	IndexSearcher searcher=null;

	try {
	    User actor = User.findByName(em, user);
	    User.Program program = actor.getProgram();
	    if (program==null || program != User.Program.PPP
		&& program != User.Program.EE5) {
		throw new WebException("User " + user + " is not enrolled into experiment plan " + User.Program.PPP + " or "+User.Program.EE5+". Presently, there is no mechanism for initializing user profiles from uploaded documents for users in experiment plan " + program);
	    } 

	    // for PPP users, we check if there is a recent profile file already
	    if (program == User.Program.PPP) {
		DataFile.Type ptype = DataFile.Type.PPP_USER_PROFILE;
		DataFile existingProfile = DataFile.getLatestFileByVersion(em, user, ptype,  DailyPPP.allowedFileVersions());
		if (existingProfile != null) {
		    Date dfTime = existingProfile.getTime();
		    IndexReader reader=Common.newReader();
		    searcher = new IndexSearcher( reader );
		    Date latestUpload = mostRecentUploadDate(searcher, user);
		    if (latestUpload!=null && latestUpload.before( dfTime)) {
			throw new WebException("User " + user + " already has a user profile (id="+existingProfile.getId()+", "+dfTime+"), which has been computed more recently than the latest document upload ("+latestUpload+"). To view your recommendations, go to the <a href=\""+cp+"/index.jsp\">main page</a>!" );
		    }
		}
	    }
	    
	    if (stageTwo) {  // Stage Two of EE5 processing: update cats
		if (program != User.Program.EE5) throw new IllegalArgumentException("StageTwo only exists for EE5, not for " + program + "!");
		em.getTransaction().begin();
		addCats( actor, request);
		em.persist(actor);
		em.getTransaction().commit();
	    }

	    sd.loadStoplist();
	    sd.upInitThread = (program == User.Program.PPP)?
		new TorontoPPPThread(user) : new TorontoEE5Thread(user, stageTwo);
	    sd.upInitThread.start();
	
	    checkStatus();

	} catch(  Exception ex) {
	    setEx(ex);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    ensureClosedReader(searcher);
	}  
   }

    /** Checks the existence and status of the currently running background thread,
	and sets all flags.
     */
    private void checkStatus() {
	if (sd.upInitThread == null) {
	    status = Status.NONE;
	    checkTitle = "No processing is taking place";
	    checkText = "No processing is taking place right now or was taking place recently";
	} else if (sd.upInitThread.getState() == Thread.State.TERMINATED) {
	    if (sd.upInitThread.error) {
		error = true;
		status = Status.DONE_ERROR;
		checkTitle = "Error occurred";
		checkText = sd.upInitThread.getProgressText();
	    } else {
		TorontoEE5Thread ee5t= (sd.upInitThread instanceof TorontoEE5Thread)?
		    (TorontoEE5Thread)sd.upInitThread : null;
		needCatApproval = (ee5t != null) && ee5t.needCatApproval;
		
		checkText = sd.upInitThread.getProgressText();
		
		if (needCatApproval) {
		    status = Status.DONE_NEED_APPROVAL;
		    checkTitle = "Document analysis completed";
		    //Classifier.CategoryAssignmentReport 
		    newCatReport = ee5t.newCatReport;
		    catBoxes = ee5t.catBoxes;
		} else {
		    status = Status.DONE_ALL;
		    checkTitle = "Processing completed";
		    wantReload = true;
		    reloadURL = "../index.jsp";
		}
	    }
	} else {
	    status = Status.RUNNING;
	    wantReload = true;
	    checkTitle = "Processing in progress...";
	    checkText =
		"Processing thread state = "+sd.upInitThread.getState()+"\n"+
		sd.upInitThread.getProgressText();
	    reloadURL = getReloadURL(true);
	    checkProgressIndicator=sd.upInitThread.getProgressIndicatorHTML(cp);
	}
    }
 
    /** Generates the URL for the "Continue" button (and/or the "refresh" tag).
       This is either the same "check" page (to continue monitoring status),
       or the main page (to view the results).
     */
    private String getReloadURL(boolean check) {
	String s= cp +
	    (check ?   "/personal/uploadPapersInitProfile.jsp?check=true" :
	     "/index.jsp");
	return s;
    }

    /** Selector for the date field that we set in the importer */
    final private static FieldSelector fieldSelectorDate = 
	new OneFieldSelector(UploadImporter.DATE_FIELD);

    /** Look at all docs uploaded by this user in Lucene data store, and
	return the date of the most recent upload. 
	@return The date of the most recent doc upload (actually, of the doc importation into Lucene), or null if none found.
    */
    private static Date mostRecentUploadDate(IndexSearcher searcher, String uname) 
    throws IOException {
	// User-uploaded examples 
	ScoreDoc[] uuSd = Common.findAllUserFiles(searcher, uname);
	Date latest = null;
	for(ScoreDoc sd: uuSd) {
	    int docno = sd.doc;
	    Document doc = searcher.getIndexReader().document(docno, fieldSelectorDate);
	    String m =  "Check doc=" +docno;
	    if (doc==null) continue;
	    Date date=UploadImporter.getUploadDate( doc);
	    m += "; " + doc.get(ArxivFields.UPLOAD_FILE) + "; " + date;
	    if (latest==null || date!=null && date.after(latest)) {
		latest=date;
		m += "; LATEST";
	    }
	    //Logging.info(m);
	}
	return latest;
    }


    /** Adds categories to the user profile based on what's sent from the client.
	This method is used in EE5 upload, after the user approves the list of
	additional categories recently "discovered" by the classifier.

	@return true if one or more categories have in fact been added
     */
    private boolean addCats(User r, HttpServletRequest request) {

	int changeCnt=0;
	Set<String> c = r.getCats();
	if (c==null) c = new HashSet<String> ();
	for(String name: Categories.listAllStorableCats()) {
	    String sent=request.getParameter(EditUser.CAT_PREFIX+name);
	    if (sent != null && !c.contains(name)) {
		Logging.info("Adding cat " + name);
		c.add(name);
		changeCnt++;
	    }
	}
	r.setCats(c);		
	Logging.info("EditUser: changeCnt=" + changeCnt);
	return (changeCnt!=0);	    
    }


}