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


 /** Records the user's choice, 
 */
public class ArticleServlet extends HttpServlet {

    /** Artcile ID, in the format used arxiv.org */
    final static public String ID="id", ACTION="action";

    final static String ARXIV_BASE = "http://arxiv.org";

    public void	service(HttpServletRequest request, HttpServletResponse response
) {


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

	    String url = (op==Action.Op.VIEW_ABSTRACT) ?
		ARXIV_BASE + "/abs/" + id :
		ARXIV_BASE + "/format/" + id;
	    
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
    static String mkUrl(String cp, String id, Action.Op op) {
	return cp + "/ArticleServlet?" +  ID +"="+id + "&"+ ACTION+ "="+op;
    }

}
