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

/** The "Toronto system": profile initialization, once PDF documents
    have been uploaded by UploadPapers.


    FIXME: need a check for "do we need it now?" (Maybe profile is already available...)
*/
public class UploadPapersInitProfile  extends ResultsBase {

    /** The "check=true" in the query string means that the user want
        to check the status of the current load process. Otherwise, it
        is a request to initiate a new process.  */
    public boolean check=false;

    /** To be displayed when check=true */
    public String checkTitle="???", checkText="?????", checkProgressIndicator="<!-- n/a -->";
    
   /** This will be set to true if we want the client to retry
        loading this page (or a slightly different one) in a few second.
        This is used on "progress" pages
     */
    public boolean wantReload=false;
    public String reloadURL;

   public UploadPapersInitProfile(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	reloadURL = getReloadURL(false);

	check = getBoolean("check", check);

	if (check) { 
            if (sd.upInitThread == null) {
                checkTitle = "No processing is taking place";
                checkText = "No processing is taking place right now or was taking place recently";
            } else if (sd.upInitThread.getState() == Thread.State.TERMINATED) {
                checkTitle = "Processing completed";
                checkText = sd.upInitThread.getProgressText();
            } else {
                wantReload = true;
                checkTitle = "Processing in progress...";
                checkText =
                    "Processing thread state = "+sd.upInitThread.getState()+"\n"+
                    sd.upInitThread.getProgressText();
                reloadURL = getReloadURL(true);
                checkProgressIndicator=sd.upInitThread.getProgressIndicatorHTML(cp);
            }
            return;
        } else if (sd.upInitThread != null && sd.upInitThread.getState() != Thread.State.TERMINATED) {
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

	EntityManager em = sd.getEM();
	IndexSearcher searcher=null;

	try {
	    User actor = User.findByName(em, user);
	    User.Program program = actor.getProgram();
	    if (program==null || program != User.Program.PPP) {
		throw new WebException("User " + user + " is not enrolled into experiment plan " + User.Program.PPP + ". Presently, there is no mechanism to for initializing user profiles from uploaded documents for users in experiment plan " + program);
	    } 

	    // check if there is a recent profile file already
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
	    

	    sd.loadStoplist();
	    sd.upInitThread = new TorontoPPPThread(user);
	    sd.upInitThread.start();
        
            if (sd.upInitThread != null && sd.upInitThread.getState() != Thread.State.TERMINATED) {
                check=true;
                wantReload = true;
                checkTitle = "Processing in progress";
                checkText =
                    "Processing thread state = " + sd.upInitThread.getState()+ "\n"+
                    sd.upInitThread.getProgressText();
                reloadURL = getReloadURL(true);
                checkProgressIndicator=sd.upInitThread.getProgressIndicatorHTML(cp);
            }
 
	} catch(  Exception ex) {
	    setEx(ex);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    ensureClosedReader(searcher);
	}
  
   }

   /** Generates the URL for the "Continue" button (and/or the "refresh" tag)
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

}