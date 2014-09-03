package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.search.IndexSearcher;

import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.sb.SBRGenerator;


/** An auxiliary servlet used to check if the server's SB
    Recommendation Generator has a fresh rec list at the moment.

    <p> The "page" returned by this servlet is not actually displayed
    to the user, because this servlet is invoked asynchronously (with
    something like jQuery's $.get(url); see
    http://api.jquery.com/jQuery.get/ ). Instead, it contains a
    snippet of JavaScript, which will be evaluated in the browser
    (function "eval" inside the get()). Depending on the current
    situation, that JS snippet will either cause the browser to reload
    the SB popup window with the new list (i.e., have the browser pull in
    the current sessionBased.jsp), or will ask the browser to wait a bit
    and then query CheckSBServlet again.    
 */
public class CheckSBServlet extends BaseArxivServlet {

    static private String mkUrl(String cp /*, ActionSource asrc */) {
	//	return cp + "/CheckSBServlet?" + asrc.toQueryString(true);
	return cp + "/CheckSBServlet";
    }

    /** Compares the IDs of the PresentedList object currently displayed in
	the SB popup in the web browser against that of the object
	currently available on the server, and produces an appropriate
	JavaScript statement to be executed in the main window.
    */
    static String mkJS(SessionData sd, String cp) {
	SBRGenerator sbrg  = sd.sbrg;
	if (sbrg==null) return "";

	//boolean good = Math.random() < 0.5;

	String js="";
	synchronized(sbrg) {
	    long serverHasPlid = sbrg.getPlid();
	    // asrc.presentedListId won't do, because it may have come
	    // from MAIN or SEARCH contexts, rather than SB!
	    long clientHasPlid = sbrg.getLastDisplayedPlid(); 

	    if ( serverHasPlid > clientHasPlid) {
		js= "openSBMovingPanelNow('"+cp+"');";
	    } else if (sbrg.hasRunning()) {
		int msec = sbrg.runningNearCompletion()? 1000: 2000;
		String url = mkUrl(cp);
		js= "checkSBAgainLater('"+url+"', "+msec+");";
	    }
	    Logging.info("CheckSBServlet (session="+sd.getSqlSessionId()+", client="+clientHasPlid+", server="+serverHasPlid+") will send back: " + js);
	}					  

	return js;
    }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);
	ActionSource asrc = new ActionSource(request);
	try {
	    SessionData sd =  SessionData.getSessionData(request);
	    String js = mkJS(sd, getContextPath());

	    response.setContentType("text/plain");
	    OutputStream aout = response.getOutputStream();
	    PrintWriter w = new PrintWriter(aout);
	    w.println(js);
	    w.close();
	} catch (Exception e) {
	    /*
	    try {
	    */
	    Logging.error("Exception in CheckSBServlet: " + e);
	    e.printStackTrace(System.out);
		/*
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in ArticleServer: " + e); //e.getMessage());
	    } catch(IOException ex) {};		
	} finally {
	    */
	}

    }
   
}
