package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Terminates the current web session. By default, this also
    terminates the "extended session" (maintained in the database), to
    ensure complete log out; but there is option mexs=true, which can
    be used to maintains the extended session. This option is used to merely
    terminate the transient session, and thus allow to start a new session
    in the SB context.
*/
public class LogoutServlet extends HttpServlet {

    public void	service(HttpServletRequest request, HttpServletResponse response) {
	EntityManager em=null;
	try {

	    // maintain extended session?
	    boolean mex = Tools.getBoolean(request, "mex", false);

	    // where to go after?
	    String redirect = Tools.getString(request,"redirect","index.jsp");

	    SessionData sd = SessionData.getSessionData(request);	  
	    long sid = sd.getSqlSessionId();

	    String user = sd.getRemoteUser(request);
	    if (user!=null && !mex) {  // invalidate extended session too
		em = sd.getEM();
		User u = User.findByName(em, user);
		if (u!=null) {
		    em.getTransaction().begin();
		    ExtendedSessionManagement.invalidateEs( u);
		    em.persist(u);
		    em.getTransaction().commit(); 
		    Logging.info("Logout: invalidated ES on user: " + user);
		}
		em.close();
		sd.storeUserName(null);
	    }
	    request.getSession().invalidate();
	    RequestDispatcher dis = request.getRequestDispatcher(redirect);
	    Logging.info("Log out (session "+sid+"): forward to=" + redirect);	    
	    dis.forward(request, response);

	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in LogoutServlet: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	    ResultsBase.ensureClosed( em, false);
	}
    }

}
