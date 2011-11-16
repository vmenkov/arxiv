package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.net.URLEncoder;

import java.lang.reflect.*;
import javax.persistence.*;

import javax.servlet.*;
import javax.servlet.http.*;
import edu.rutgers.axs.sql.*;


public class ResultsBase {

    HttpServletRequest request;

    /** Will be set to true if an error happened */
    public boolean error = false;
    /** The JSP page should print this out if error==true */
    public String errmsg="[No message]";
    /** The JSP page may print this out if it is not null. */
    public Exception e=null;

    /** The JSP page should always print this message. Most often
        it is just an empty string, anyway; but it may be used
        for debugging and status messages. */
    public String infomsg = "";

    /** The "conext" part of our URL. It can be used by JSP pages to
     build correct links (without using lots of ".." etc). The value
     could be e.g. "/arxiv" or "". */
    public final String cp;

    /** All the data that are meant to be persistent between requests
     * in the same session */
    public SessionData sd;

    /** User name logged in this session */
    public String user=null;

    /** Returns the user object for the currently logged-in user */
    public User getUserEntry() {
	EntityManager em = sd.getEM();
	User u = User.findByName(em, user);
	em.close();
	return u;
    }

    /** Is this command run by an admin? */
    boolean runByAdmin() {
	User u = getUserEntry();
	return u!=null && u.isAdmin();
    }

    /**
       @param response If null, don't bother with checking.
     */
    public ResultsBase(HttpServletRequest _request, HttpServletResponse response) {
	request = _request;
	cp = request.getContextPath(); 
	try {
	    infomsg+= "<br>Plain params:<br>";
	    for(Enumeration en=request.getParameterNames(); en.hasMoreElements();){
		String name = (String)en.nextElement();
		infomsg += name + "=" + request.getParameter(name) + "<br>";
	    }	    
	    sd = SessionData.getSessionData(request);	  
	    Logging.info("obtained sd=" + sd);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext() );
	    user = sd.getRemoteUser(request);

	    if (response!=null && !isAuthorized()) {
		Logging.info("user " + user + " is not authorized to access servlet at " + request.getServletPath());
		
		String redirect = cp + "/login2.jsp?sp=" +
		    URLEncoder.encode( request.getServletPath(), "UTF-8");
		//String eurl = response.encodeRedirectURL(redirect);
		response.sendRedirect(redirect);

		/*
	    String redirect = "index.jsp";	    
	    RequestDispatcher dis = request.getRequestDispatcher(redirect);
	    Logging.info("Logout: forward to=" + redirect);	    
	    dis.forward(request, response);
		*/


		error=true;
		errmsg="Not authorized";
		return;
	    } else {
		Logging.info("user " + user + " is authorized to access servlet at " + request.getServletPath());
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	}	
    }

    /** Gets the list of authorized roles for this URL, from a
	hard-coded list. This is a poor substitute for specifiying
	them in a set of "security-constraint" elements in web.xml

	@return null if no restriction is imposed, or a list of
	allowed roles (may be empty) otherwise
     */
    static Role.Name[] authorizedRoles(String sp) {
	if (sp.startsWith("/personal")) return new Role.Name[] 
					    {Role.Name.subscriber,
					     Role.Name.researcher,
					     Role.Name.admin};
	else if (sp.startsWith("/tools")) return new Role.Name[] 
					      {Role.Name.admin,
					       Role.Name.researcher};
	else if (sp.startsWith("/admin")) return new Role.Name[] 
					      {Role.Name.admin};
	else return null;
    }

    /** Is this user authorized to access this url? */
    private boolean isAuthorized() {
	String sp = request.getServletPath();
	Role.Name[] ar = authorizedRoles(sp);
	if (ar==null) return true; // no restrictions
	if (user==null) return false; // no user 
	User u = getUserEntry();
	if (u==null) return false;
	return u.hasAnyRole(ar);
    }
    
    /** Gets the integer param with the specified value from the
	request. If no such param is found in the request, returns the
	specified default value.
    */
    long getLong(String name, long defVal) {
	return Tools.getLong(request, name, defVal);
    }

    String getString(String name, String defVal) {
	return Tools.getString(request, name, defVal);
    }

    
    void setEx(Exception _e) {
	error = true;
	if (_e instanceof edu.rutgers.axs.sql.IllegalInputException ) {
	    // although a subclass of WebException, it's ok to pass
	    // it: the JSP page should have special treatment
	    e = _e;
	} else if (_e instanceof WebException) {
	    // this is our own exception - we known where it came from,
	    // so no need to print stack etc.
	} else {
	    e = _e;
	}
	errmsg = "Error: " + _e.getMessage();
    }

    /** Returns the exception's stack trace, as a plain-text string 
     */
    public String exceptionTrace() {	
	StringWriter sw = new StringWriter();
	try {
	    if (e==null) return "No exception was caught";
	    e.printStackTrace(new PrintWriter(sw));
	    sw.close();
	} catch (IOException ex){}
	return sw.toString();
    }

    /** FIXME uhem... a silly way to refresh data! */
    /*    public int responseCnt(PhoneCall c) {
//	EntityManager em = sd.getEM();
	try {
//	    c = em.find(PhoneCall.class, c.getId()); // a silly way to re-load, but merge() won't work here
//	    return  c.computeResponseCnt();
	} finally {
	    em.close();
	}
	} */

    /** @return A text message of the form "such-and-such time (3 hours ago)", or "never"
     */
    static public String ago(Date d) {
	return Util.ago(d);
    }

    /** Prints time, along with the "... ago" message */
    //static public String timeAndAgo(Date d) {
    //	return d==null? "never" : Reflect.compactFormat(d) + " " + Util.ago(d);
    //}

    /** This can be put into every "finally" clause ... */
    static void ensureClosed(EntityManager em) {
	ensureClosed( em, true);
    }

    static void ensureClosed(EntityManager em, boolean commit ) {
	if (em==null) return;
	if (!em.isOpen()) return;
	try {
	    if (em.getTransaction().isActive()) {
		if (commit) em.getTransaction().commit();
		else em.getTransaction().rollback();
	    }
	} catch (Exception _e) {}
	try {
	    em.close();
	} catch (Exception _e) {}
    }

}
