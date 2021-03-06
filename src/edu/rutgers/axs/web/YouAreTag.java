package edu.rutgers.axs.web;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import java.io.*;
import edu.rutgers.axs.sql.Logging;
import edu.rutgers.axs.html.Html;

/** This tag prints an information message about the user's current
 * login name */
public class YouAreTag extends TagSupport {

    public int doStartTag() throws JspException {
        try {
	    PrintWriter out = new PrintWriter( pageContext.getOut());
	    HttpServletRequest req=(HttpServletRequest)pageContext.getRequest();

	    //	    ServletContext context=  pageContext.getServletContext();
	    String cp = req.getContextPath(); // Could be "/arxiv" or "";

	    //Logging.info("YouAreTag: contextPath=" + cp);

	    SessionData sd = SessionData.getSessionData(req);	  
	    String user = sd.getRemoteUser(req);

	    String s= 
		(user==null)? 
		 "You are not participating in arXiv research. [" +
		Html.a( cp +  "/login2.jsp", "Log in?") + "] [" +
		Html.a( cp +  "/participation.jsp",  "Join!") + "]" :
		"You are logged in as <em>" + user + "</em> [" +
		Html.a( cp+"/personal/editUserFormSelf.jsp", "My account") + 
		"] [" +
		Html.a( cp+"/LogoutServlet", "Log out?") + "]";

	    out.println(s);
        } catch (Exception ex) {
	    System.out.println(ex);
	    ex.printStackTrace(System.out);
	    String trace= ResultsBase.stackTrace(ex).replaceAll("\n", "<br>\n");
            throw new JspException("IO problems: " + ex.getMessage() + 
				   "<br>\n" + trace);
        }
        return SKIP_BODY;
    }
}
