package edu.rutgers.axs.web;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;

import java.io.*;
import java.util.*;
import javax.servlet.http.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import javax.persistence.*;


/** Generates an HTML table containing input fields for various
 * members of a specified class. The class must have a
 * system-assigned integer id. 
 */
@SuppressWarnings("unchecked")
public class EntryFormTag extends TagSupport {

    static final String ID="id";

    /** Prefix for HTTP request params */
    public static final String PREFIX = "r.";

    private String className;
    public void setClassName(String name) {
	className = name;
    }

    /** The id of the entry being edited. The value of -1
     */
    private long id = -1;

    public void setId(String s) {
        try {
            id = Long.parseLong(s);
        } catch(Exception ex) {}
    }


    public int doStartTag() throws JspException {	
	PrintWriter out = new PrintWriter( pageContext.getOut());
        try {
	    if (className == null) {
		out.println("No class specified");
		return SKIP_BODY;
	    }

	    Class c = Class.forName(className);
	    Object o = null;

	    if (id<0) {
		Logging.info("EFT: no id supplied");
	    } else {
		//		SessionData sd = SessionData.getSessionData(pageContext);
		SessionData sd = SessionData.getSessionData((HttpServletRequest )pageContext.getRequest());

		EntityManager em = sd.getEM();
		try {
		    o = em.find(c, id);
		    Logging.info("EFT: Found obj for class="+c+", id="+id);
		} catch(IllegalArgumentException ex) {
		    out.println("<p>Not a suitable class ("+c+"), or not a suitable key type. key="+id+".</p>");
		    return SKIP_BODY;		    
		} finally {
		    em.close();
		}
	    }

	    out.println(Tools.inputHidden(ID, id));
	    out.println("<table>");
	    out.println("<tr><th>Data fields</th></tr>");

	    for(Reflect.Entry e: Reflect.getReflect(c).entries) {
		if (!e.editable) continue;
		out.println( EntryForms.mkTableRow(PREFIX, o,e));
	    }
	    out.println("</table>");

        } catch (Exception ex) {
            throw new JspException("IO problems");
        } finally {
	    out.flush();
	}
        return SKIP_BODY;
    }
    

}
