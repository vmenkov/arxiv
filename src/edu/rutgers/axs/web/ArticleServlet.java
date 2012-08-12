package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import java.util.regex.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;


 /** Mostly, simply redirects to FilterServlet. It used to records the
     user's action before doing the redirect, but now even that is not done,
     as FilterServlet does the recording on its own.
 */
public class ArticleServlet extends BaseArxivServlet {
  
    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);


	Action.Op op = (Action.Op)Tools.getEnum(request, Action.Op.class,
					 ACTION, Action.Op.NONE);	 

	String id = request.getParameter(ID);

	EntityManager em = null;
	try {

	    if (op==Action.Op.NONE)  throw new WebException("No operation code supplied");
	    if (id==null) throw new WebException("No aticle id supplied");


	    SessionData sd =  SessionData.getSessionData(request);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext());
	    String user = sd.getRemoteUser(request);

	    /*
	    if (user!=null) {
		em = sd.getEM();
		// Begin a new local transaction so that we can persist a new entity
		em.getTransaction().begin();
		
		User u = User.findByName(em, user);
		
		u.addAction(id, op);
		
		em.persist(u);
		em.getTransaction().commit(); 
		em.close();
	    }
	    */

	    Action.Source src = (Action.Source)Tools.getEnum(request, Action.Source.class,
							     ResultsBase.SRC, Action.Source.UNKNOWN);	 
	    long dataFileId =  Tools.getLong(request, ResultsBase.DF, 0);

	    //String base = ARXIV_BASE;
	    String base =   getContextPath() +  FilterServlet.FS;
	    String url = base +
		FilterServlet.packActionSource( src,dataFileId ) +
		(op==Action.Op.VIEW_ABSTRACT ?  "/abs/" : "/format/" ) + id;
	    
	    // sendRedirect() sends a temporary redirect response to
	    // the client (code 302). Then the browser will send
	    // a request to the indicated FilterServlet URL.

	    String eurl = response.encodeRedirectURL(url);
	    response.sendRedirect(eurl);

	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in ArticleServer: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	    ResultsBase.ensureClosed( em, false);
	}

    }

     /** Returns a URL for this servlet */
    static String mkUrl(String cp, String id, Action.Op op, 
			Action.Source src, long dfid) {
	return cp + "/ArticleServlet?" +  ID +"="+id + "&"+ ACTION+ "="+op +
	    ResultsBase.packSrcInfo(src,dfid);
    }

}
