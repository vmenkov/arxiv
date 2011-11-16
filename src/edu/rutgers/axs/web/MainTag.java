package edu.rutgers.axs.sql.tags;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import java.text.DecimalFormat;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import javax.persistence.*;



/** UNUSED
 */
public class MainTag extends TagSupport {

    private ResultsBase main=null;
    public void setMain(ResultsBase _main) {
	main = _main;
    }



    public int doStartTag() throws JspException {	
	PrintWriter out = new PrintWriter( pageContext.getOut());
        try {
	    if (main == null) {
		out.println("No class specified");
		return SKIP_BODY;
	    }
	    out.println("" + main.getClass() + "<br>");
	    out.println("" + main.infomsg + "<br>");

	    /*
	    Class c = Class.forName(className);
 
	    Object o = null;
	    if (id > 0) {
		SessionData sd = SessionData.getSessionData(pageContext);
		EntityManager em = sd.getEM();
		try {
		    o = em.find(c, id);
		    Logging.info("EFT: Found obj for class="+c+", id="+id);
		} catch(IllegalArgumentException ex) {
		    out.println("<p>Not a suitable class ("+c+"), or not a suitable key type. key="+id+".</p>");
		    return SKIP_BODY;		    
		}
	    } else {
		Logging.info("EFT: no id supplied");
	    }

	    out.println("<input type=\"hidden\" name=\"id\" value=\""+id+"\"/>");
	    out.println("<table>");
	    out.println("<tr><th>Data fields</th></tr>");

	    int dpCnt=0;

	    for(Reflect.Entry e: Reflect.getReflect(c).entries) {
		if (!e.editable) continue;
		String name = e.name;

		Class t= e.f.getType();

		Annotation[] an=e.f.getDeclaredAnnotations(); 

		Object old = (o==null)? null : e.g.invoke(o);
	
		out.println("<tr><td>" + name + "<br><small>("+t);
		for(Annotation a: an) out.println("<br>" + a);
		if (old != null) {
		    out.println("<br><em>old=" + old +",</em> type=" + old.getClass());
		}
		out.println(")</small></td>");
		out.println("<td>");
	       

		if (t.isEnum()) {
		    out.println("<SELECT NAME=\""+name+"\">");
		    Object[] con = t.getEnumConstants();
		    for(int j=0; j<con.length; j++) {
			boolean selected = (old == null)? (j==0): (old==con[j]);
			out.println(option( con[j], con[j], selected));
		    }
		    out.println("</SELECT>");
		} else if (t.equals(boolean.class)) {
		    out.println("<SELECT NAME=\""+name+"\">");
		    boolean ov = (old==null)? false: ((Boolean)old).booleanValue();
		    out.println(option("false", "No", !ov));
		    out.println(option("true", "Yes", ov));
		    out.println("</SELECT>");		    
		} else if (Date.class.isAssignableFrom(t)) {
		    String dpname =  "date-pick";
		    if (old==null) {
			out.println("<input name=\""+name+"\" id=\""+name+"\" class=\""+dpname+"\" />");
		    } else {
			dpname += "-" + (dpCnt++);
			Calendar cal = new GregorianCalendar();
			cal.setTime( (Date)old);
			String s = "<script type=\"text/javascript\" charset=\"utf-8\"> \n" + 
"$(function() {  $('."+dpname+"').datePicker({startDate:'01/01/2010'}).val(new Date("+
			    cal.get(Calendar.YEAR)+ ","+
			    cal.get(Calendar.MONTH)+ ","+
			    cal.get(Calendar.DAY_OF_MONTH)+
			    ").asString()).trigger('change');  });\n" +
			    "</script>";
			out.println(s);
			out.println("<input name=\""+name+"\" id=\""+name+"\" class=\""+dpname+"\" />");
		    }

		    if (e.f.isAnnotationPresent(Timing.class)) {
			String nameHm = makeHmName(e.name);
			int h0=8, h1=21, m0=0, m1=60, dm=30;

			Calendar cal = null;
			if (old != null) {
			    cal = new GregorianCalendar();
			    cal.setTime( (Date)old);
			}

			out.println("<SELECT NAME=\""+nameHm+"\">");

			String s = "";
			boolean selDone = false;
			for(int h=h0; h<h1; h++) {
			    for(int m=m0; m<m1; m+=dm) {
				boolean sel =  (cal != null) && !selDone &&
				    timeInRange(cal, h, m, dm);
				String hm = hhFmt.format(h) + ":" + 
				    hhFmt.format(m);
				s += option(hm, hm, sel);
				selDone = (selDone ||  sel);
			    }
			}
			out.println(option(NONE, "Time", !selDone));
			out.println(s);
			out.println("</SELECT>");		    		
		    } else {
			//out.println("[no hours needed]");    		
		    }

		} else {
		    out.println("<input type=\"text\" size=32 name=\"" +
				name + "\"");
		    if (old != null) out.println(" value=\"" + old + "\"");
		    out.println("/>");
		}
		out.println("</td></tr>");
	    }
	    out.println("</table>");

	    */
        } catch (Exception ex) {
            throw new JspException("IO problems");
        } finally {
	    out.flush();
	}

        return SKIP_BODY;
    }


}
