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
    ensure complete log out; but there is option mex=true, which can
    be used to maintains the extended session. This option is used to merely
    terminate the transient session, and thus allow to start a new session
    in the SB context.
*/
public class LogoutServlet extends HttpServlet {

    public static final String STAY = "stay";

    /** The values of the "stay" parameter, which determines what happens
     after the session is terminated. This was added for the "Change Focus"
     functionality in the session-based (SB) recommender. */
    public static enum Stay {
	/** The user is fully logged out, and any extended session is 
	    terminated.	Any new session that will start thereafter will be
	    an anonymous session (at least until the user explicitly logs 
	    in again). This is the default behavior. */
	OFF, 
	    /** The user is logged out, unless there is an extended session in 
	       effect (which is controlled by the ES info stored in the 
	       database). In the latter case, we merely start a new session
	       for the same user. There does not seem to be much use for this
	       mode.
	     */
	    ESONLY, 
	    /** The user continues to be logged in, but a new session starts 
		for him. This is the behavior needed with the "change focus"
		functionality in the SB recommender.
	     */ 
	    ON;
    };

    
    public void	service(HttpServletRequest request, HttpServletResponse response) {
	EntityManager em=null;
	try {

	    // maintain extended session?
	    //boolean mex = Tools.getBoolean(request, "mex", false);
	    Stay stay = (Stay)Tools.getEnum(request, Stay.class, STAY, Stay.OFF);
	  
	    // where to go after?
	    String redirect = Tools.getString(request,"redirect","index.jsp");

	    SessionData sd = SessionData.getSessionData(request);	  
	    long sid = sd.getSqlSessionId();

	    String user = sd.getRemoteUser(request);

	    if (user==null) {
		// Simply invalidate the current anon session. A new
		// one will be created at the next HTTP request.
		SessionData.discardSessionData(request);
		Logging.info("Invalidated anon session "+sid);
	    } else {
		if (stay == Stay.ON) {
		    // The user stays logged in, but the current session
		    // will be terminated, and a new one will start
		    sd = SessionData.replaceSessionData(request);
		    long newSid = sd.getSqlSessionId();
		    Logging.info("Focus change: replaced session "+sid + " with new session " + newSid);

		    em = sd.getEM();
		    User u = User.findByName(em, user);
		    if (u==null) throw new WebException("No such user: "+user);
		    sd.storeUserName(user);
		    sd.storeUserInfoInSQL(em, u); // this has a commit inside
		} else {
		    // Current session should be abandoned.  (If
		    // stay==Stay.ESONLY, and the user is remembered
		    // in ES, a new session for him will be started
		    // for him at the next HTTP request)
		    SessionData.discardSessionData(request);
		    Logging.info("Invalidated session "+sid+" for user "+user);
		}

		if (stay==Stay.OFF) {  // invalidate extended session too
		    em = sd.getEM();
		    User u = User.findByName(em, user);
		    if (u!=null) {
			em.getTransaction().begin();
			ExtendedSessionManagement.invalidateEs( u);
			em.persist(u);
			em.getTransaction().commit(); 
			Logging.info("Logout: invalidated ES for user: " + user);
		    }
		    sd.storeUserName(null);
		}

	    }

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
