package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
//import java.text.*;

import javax.persistence.*;
import javax.servlet.*;
import javax.servlet.http.*;

import edu.rutgers.axs.sql.*;


/** For use in the absence of Tomcat's native support (context.xml etc) */
public class LoginServlet extends HttpServlet {

     public void service(HttpServletRequest request,HttpServletResponse response) {

	EntityManager em=null;
	String user= request.getParameter("j_username");
	String p= request.getParameter("j_password");
	String sp =  request.getParameter("sp");
	String qs =  request.getParameter("qs");
	if (qs!=null && qs.equals("null")) qs=null;
	String remember = request.getParameter("remember");
	
	//boolean error = false;
	//String errmsg = "";

	try {

	    if (user==null || p==null) throw new WebException("No username or password supplied");    
	    SessionData sd = SessionData.getSessionData(request);	  
	    em = sd.getEM();
	    User u = User.findByName(em, user);
	    //em.close();  // will close it in the "finally" clause
	    if (u==null)  throw new WebException("No such user: " + user);

	    if (!u.checkPassword(p)) throw new WebException("Wrong password for user " + user);


	    Role.Name[] ar = SessionData.authorizedRoles(sp);
	    
	    if (ar!=null) {
		// authorization indeed is required 
		if (!u.hasAnyRole(ar)) {
		    throw new WebException("User " + user + " has no permission for page " + sp + ". If you think you should have it, please contact the site administrator");
		}
	    }
	    // all OK
	    sd.storeUserName(user);

	    boolean newDay = u.changeDayIfNeeded();
	    Logging.info("LoginServlet("+user+"); change day=" + newDay +"; now day=" + u.getDay());

	    Cookie cookie = null;
	    if (remember!=null || newDay) {
		em.getTransaction().begin();
		if (remember!=null) cookie =  ExtendedSessionManagement.makeCookie(u);
		em.persist(u);
		em.getTransaction().commit(); 
		Logging.info("Login: remembered user");		
	    } else {
		Logging.info("Login: won't remember user (box not checked)");
	    }

	    String redirect =  "/index.jsp";
	    if (sp!=null && !sp.equals("null")) {
		redirect = sp;
		if (qs != null) {
		    redirect += "?" + qs;
		}
	    }
	    Logging.info("Login: sp="+sp+", qs="+qs);
	    Logging.info("Redirect (relative to cp) to=" + redirect);	    

	    //	    RequestDispatcher dis = request.getRequestDispatcher(redirect);
	    //	    Logging.info("Login: forward to=" + redirect);	    
	    //	    dis.forward(request, response);

	    String cp = request.getContextPath(); 
	    String eurl = response.encodeRedirectURL(cp + redirect);
	    if (cookie!=null) response.addCookie(cookie);
	    response.sendRedirect(eurl);


	} catch (WebException e) {
	    try {
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access denied: " + e.getMessage());
	    } catch(IOException ex) {
		Logging.error("Can't redirect" + ex);
	    };
	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in LogoutServlet: " + e); //e.getMessage());
	    } catch(IOException ex) {
		Logging.error("Can't redirect" + ex);
	    };
	} finally {
	    ResultsBase.ensureClosed(em, false);
	}
    }

}
