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


/** Records the user's "judgment" about an article. A "judgment" results
    from the user's clicking on one of the action buttons, such as 
    rating an article, "copying" it in one's personal folder, asking 
    the server not to show it again, etc.

    <p>
    The "page" returned by this servlet is not actually
    displayed to the user, because this servlet is invoked
    asynchronously (with something like jQuery's $.get(url); see
    http://api.jquery.com/jQuery.get/ )
 */
public class JudgmentServlet extends BaseArxivServlet {
  
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

	    response.setContentType("text/plain");


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
    static String mkUrl(String cp, String id, Action.Op op) {
	return cp + "/ArticleServlet?" +  ID +"="+id + "&"+ ACTION+ "="+op;
    }

}
