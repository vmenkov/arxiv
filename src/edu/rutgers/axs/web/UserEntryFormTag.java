package edu.rutgers.axs.web;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import java.text.DecimalFormat;

import javax.servlet.http.*;
import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import javax.persistence.*;


/** Generates an HTML table containing input fields for various
 * members of a specified class. For classes that have a
 * system-assigned integer id, this tag also allows to create an entry
 * from scratch, but for the User class, only to edit existing entries.
 */
public class UserEntryFormTag extends TagSupport {

    /** Prefix for HTTP request params */
    //static final String PREFIX = "r.";

    static final String USER_NAME = "user_name";

    private String user_name = null;
    public void setUser_name(       String x) { user_name = x; }

    public int doStartTag() throws JspException {	
	PrintWriter out = new PrintWriter( pageContext.getOut());
        try {
	    Class c = User.class;
	    User o=null;

	    if (user_name==null) {
		// Cannot edit an entry w/o a user name
		Logging.error("EFT: no user_name supplied");
		out.println("No user_name specified");
		return SKIP_BODY;
	    } else {
		//SessionData sd = SessionData.getSessionData(pageContext);
		SessionData sd = SessionData.getSessionData((HttpServletRequest )pageContext.getRequest());
		EntityManager em = sd.getEM();
		try {
		    o = User.findByName(em, user_name);
		    Logging.info("EFT: Found user by name, name="+user_name);
		} catch(IllegalArgumentException ex) {
		    out.println("<p>Not a suitable class ("+c+"), or not a suitable key type. key="+user_name+".</p>");
		    return SKIP_BODY;		    
		} finally {
		    em.close();
		}
	    }

	    out.println(Tools.inputHidden(USER_NAME, user_name));
	    out.println("<table>");
	    out.println("<tr><th>Data fields</th></tr>");

	    for(Reflect.Entry e: Reflect.getReflect(c).entries) {
		if (!e.editable) continue;
		out.println( EntryForms.mkTableRow(EntryFormTag.PREFIX, o,e));
	    }
	    out.println("</table>");

        } catch (Exception ex) {
	    System.out.println(ex);
	    ex.printStackTrace(System.out);
            throw new JspException("IO problems: " + ex);
        } finally {
	    out.flush();
	}
        return SKIP_BODY;
    }
    
}
