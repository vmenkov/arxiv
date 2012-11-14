package edu.rutgers.axs.web;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import java.io.*;
import edu.rutgers.axs.sql.Logging;

/** This tag prints an information message about the user's current
 * login name */
public class YouAreTag extends TagSupport {

    public int doStartTag() throws JspException {
        try {
	    PrintWriter out = new PrintWriter( pageContext.getOut());
	    HttpServletRequest req=(HttpServletRequest)pageContext.getRequest();

	    //	    ServletContext context=  pageContext.getServletContext();
	    String cp = req.getContextPath(); // Could be "/arxiv" or "";

	    Logging.info("YouAreTag: contextPath=" + cp);

	    SessionData sd = SessionData.getSessionData(req);	  
	    String user = sd.getRemoteUser(req);

	    String s= 
		(user==null)? 
		 "You are not participating in arXiv research. [<a href=\""
		+cp +  "/login2.jsp\">Log in?</a>] [<a href=\""
		+cp +  "/participation.jsp\">Join!</a>]" :
		"You are logged in as <em>" + user + "</em> [<a href=\""+cp+"/personal/index.jsp\">My account</a>] [<a href=\""+cp+"/LogoutServlet\">Log out?</a>]";				
	    out.println(s);
        } catch (Exception ex) {
	    System.out.println(ex);
	    ex.printStackTrace(System.out);
            throw new JspException("IO problems");
        }
        return SKIP_BODY;
    }
}
