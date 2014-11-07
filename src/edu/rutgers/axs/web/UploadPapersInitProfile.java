package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;
import java.nio.charset.Charset;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.disk.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.recommender.TorontoPPPThread;

/** The "Toronto system": profile initialization, once PDF documents
    have been uploaded by UploadPapers.
*/
public class UploadPapersInitProfile  extends ResultsBase {

    /** The "check=true" in the query string means that the user want to check
        the status of the current load process */
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
                    "Processing thread state = " + sd.upInitThread.getState()+ "\n"+
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

	try {
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
	}

   }

   /** Generates the URL for the "Continue" button (and/or the "refresh" tag)
     */
    private String getReloadURL(boolean check) {
	String s= cp + "/personal/uploadPapersInitProfile.jsp" ;
	if (check) s +=  "?check=true";
	return s;
    }


}