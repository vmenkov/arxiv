package edu.rutgers.axs.web;

import edu.rutgers.axs.sql.*;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import java.text.DecimalFormat;

//import javax.servlet.jsp.*;
//import javax.servlet.jsp.tagext.*;

import javax.persistence.*;


/** Methods generating HTML form elements for EntryFormTag and other
 * similar classes
 */
public class EntryForms {

    /** Generates an HTML table row with a data entry element (an HTML
	INPUT element) and an explanation.

	@param o An object one of whose fields, if available, we'll use as the initial value for the field
	@return A TR element, or an empty string
     */
    static public String mkTableRow(String prefix, Object o, Reflect.Entry e) throws IllegalAccessException, InvocationTargetException{

	if (!e.editable) return "";

	final boolean verbose=false;//true;
	//final boolean verbose=true;

	if (prefix==null) prefix = "";

	StringBuffer b=new StringBuffer();

	
	Class t= e.f.getType();
	
	Annotation[] an=e.f.getDeclaredAnnotations(); 
	Display anDisplay = (Display)e.f.getAnnotation(Display.class);

	Object old = (o==null)? null : e.g.invoke(o);
	
	String title = (anDisplay!=null && anDisplay.alt()!=null && anDisplay.alt().length()>0) ?
	    anDisplay.alt() :    e.name;
	b.append("<tr><td>" + title);
	if (anDisplay!=null && anDisplay.text().length()>0) {
	    b.append(" ("+anDisplay.text()+")");
	}

	if (verbose) {
	    b.append("<br><small>("+t);
	    for(Annotation a: an) b.append("<br>" + a);
	    if (old != null) {
		b.append("<br><em>old=" + old +",</em> type=" + old.getClass());
	    }
	    b.append(")</small>");
	}
	b.append("</td>");
	b.append("<td>");
	
	String name = prefix + e.name;
	if (t.isEnum()) {
	    b.append(Tools.mkSelector(name, t, old));
	} else if (t.equals(boolean.class)) {
	    b.append(Tools.mkSelectorBoolean(name, old));
	} else if (Date.class.isAssignableFrom(t)) {
	    Date q = (Date) old;
	    if (q==null) q=new Date();
	    b.append(Tools.mkDateTimeBox( name, q, e));
	} else if (t.equals(int.class)||t.equals(long.class)||t.equals(double.class)) {
	    b.append(Tools.inputText(name, old, 8));
	} else {
	    int len=32;
	    if (String.class.isAssignableFrom(t)) {
		Column coDisplay = (Column)e.f.getAnnotation(Column.class);
		if (coDisplay!=null && coDisplay.length()>0) len=coDisplay.length();
	    }
	    if (len <= 64 ) {
		b.append(Tools.inputText(name, old, len));
	    } else {
		int nc = 64;
		int nr = (len/nc) + ((len%nc>0) ?  1: 0);
		b.append(Tools.inputTextArea(name, old, nr,nc));
	    }
	}
	b.append("</td></tr>\n");
	return b.toString();
    }
	
    static String mkRadioSet(String name, Class t) {
	return mkRadioSet(name,  t,  null);
    }

    /** Creates a set of radio buttons for a given Enum type. Labels are based on the names of enum type values, and/or any EA annotations provided */
    static String mkRadioSet(String name, Class t, Object old) {
	StringBuffer b = new StringBuffer();

	Object[] con = t.getEnumConstants();
	for(int j=0; j<con.length; j++) {
	    boolean selected = (old == null)? (j==0): (old==con[j]);

	    EA an = Util.getEa((Enum)con[j]);
	
	    String text = (an==null)? "": an.alt();
	    text = (text!=null && text.length()>0) ? text:  con[j].toString();

	    String annoText = (an==null)? "": an.value();
	    //	    if (con[j] instanceof PhoneCall.Disposition) {
	    //	annoText += 		(annoText.length()>0 ? ". " : "") +
	    //	    ((PhoneCall.Disposition) con[j]).simplify();
	    //}
	    if (annoText.length()>0) {
		text += "<small>("+annoText+")</small>";
	    }
	    b.append(Tools.radio( name, con[j], text, selected));
	}
	return b.toString();
    }
}