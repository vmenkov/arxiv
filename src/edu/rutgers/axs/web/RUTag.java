package edu.rutgers.axs.web;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import java.io.*;
import edu.rutgers.axs.sql.Logging;
import edu.rutgers.axs.Version;

public class RUTag extends TagSupport {

    public int doStartTag() throws JspException {
        try {
	    PrintWriter out = new PrintWriter( pageContext.getOut());
	    HttpServletRequest req=(HttpServletRequest)pageContext.getRequest();

	    //	    ServletContext context=  pageContext.getServletContext();
	    String cp = req.getContextPath(); // Could be "/icd" or "";

	    //Logging.info("RUTag: contextPath=" + cp);

	    SessionData sd = SessionData.getSessionData(req);	  
	    String user = sd.getRemoteUser(req);

	    out.println("<hr><div align=right>" +
			(user==null? "Not logged in" : 
			 (user.equals(SessionData.defaultUser))? "Working as user <em>" + user + "</em>" :
			 "Logged in as user <em>" + user +
			 "</em> (<a href=\""+cp+"/personal/editUserFormSelf.jsp\">My account</a>) (<a href=\""+cp+"/LogoutServlet\">Log out</a>)"
) +
			"</div>");
	    out.println("<hr><div align=center>Rutgers University <a href=\""+cp+"\">My.arXiv</a>. Application ver. "+Version.getVersion()+", "+Version.date+"</div>");
        } catch (Exception ex) {
            throw new JspException("IO problems");
        }
        return SKIP_BODY;
    }
}
