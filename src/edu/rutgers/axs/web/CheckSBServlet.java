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

    static public String mkUrl(String cp) {
	return cp + "/CheckSBServlet";
    }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);
	ActionSource asrc = new ActionSource(request);
	try {
	    SessionData sd =  SessionData.getSessionData(request);
	    String js = (sd.sbrg!=null)? sd.sbrg.mkJS(getContextPath()) : "";

	    //response.setContentType("text/plain");
	    response.setContentType("application/javascript");
	    OutputStream aout = response.getOutputStream();
	    PrintWriter w = new PrintWriter(aout);
	    w.println(js);
	    w.close();
	} catch (Exception e) {
	    Logging.error("Exception in CheckSBServlet: " + e);
	    e.printStackTrace(System.out);
	}

    }
   
}
