package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import edu.rutgers.axs.sql.*;


/** Various methods related to generating HTML forms and their
    components, and processing requests sent by the web browser when
    those forms are filled
 */
@SuppressWarnings("unchecked")
public class Tools {

    final static String NONE = "none";

    static String makeHmName(String name) {
	return name + "_hm";
    }

    /** Creates an "input type=hidden" HTML tag */
    public static String inputHidden(String name, boolean val) {
	return "<input type=\"hidden\" name=\""+name+"\" value=\""+val+"\">\n";
    }
    public static String inputHidden(String name, long val) {
	return "<input type=\"hidden\" name=\""+name+"\" value=\""+val+"\">\n";
    }
    public static String inputHidden(String name, String val) {
	return "<input type=\"hidden\" name=\""+name+"\" value=\""+val+"\">\n";
    }

    public static String inputText(String name) {
	return inputText(name, null, 0);
    }

    static String inputText(String name, long val) {
	return inputText(name, ""+val, 0);
    }

    /** Creates an 'input type=text' tag.
	@param val the value to display (if not null)
	@param size 0 means default
     */
    public static String inputText(String name, Object val, int size) {
	String s = "<input type=\"text\" name=\""+name+"\"";
	if (val!=null) s += " value=\""+val+"\"";
	if (size > 0) s += " size="+size;			  
	s += ">\n";
	return s;
    }

    public static String inputTextArea(String name, Object val, 
				       int rows, int cols) {
	String s = "<textarea name=\""+name+"\"";
	s += " rows="+rows;			  
	s += " cols="+cols;			  
	s += ">";
	if (val!=null) s += val;
	s +=  "</textarea>\n";
	return s;
    }

    public static String radio(String name, Object value, Object text, boolean selected) {
	return radioOrBox(name, "radio", value, text, selected);
    }

    public static String checkbox(String name, Object value, Object text, boolean selected) {
	return radioOrBox(name, "checkbox", value, text, selected);
    }

    /** Creates an HTML "input" element of the "radio" or "checkbox" type.
	@param type must be "radio" or "checkbox"
     */
    public static String radioOrBox(String name, String type, Object value, Object text, boolean selected) {
	return  radioOrBox( name,  type,  value,  text, selected,  null);
    }

    /** Creates an HTML "input" element of the "radio" or "checkbox" type.
	@param type must be "radio" or "checkbox"
	@param style Is meant to control presentation. For example, in a system providing canned scripts to telephone operators, items that the user needs to read aloud may be rendered differently.
     */
    public static String radioOrBox(String name, String type, Object value, Object text, boolean selected, String style) {
	String s = 
	    "<input type=\""+type+"\" name=\""+name+"\" value=\""+value+"\"" +
	    (selected? "  checked=\"checked\"/>" : "/>");
	if (text != null) {
	    s += (style!=null)? Style.SPAN(style) +  text + "</SPAN>" : text;
	}
	s += "\n";
	return s;

    }


    /** Retrives an integer HTTP request parameter. If not found in
      the HTTP request, also looks in the attributes (which can be used
      by SurveyLogicServlet in case of internal redirect)
     */
    static public long getLong(HttpServletRequest request, String name, long defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Long a = (Long)request.getAttribute(name);
	    return (a!=null) ? a.longValue() : defVal;
	}
	try {
	    return Long.parseLong(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }


    static public boolean getBoolean(HttpServletRequest request, String name, boolean defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Boolean a = (Boolean)request.getAttribute(name);
	    return (a!=null) ? a.booleanValue() : defVal;
	}
	try {
	    return Boolean.parseBoolean(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }

    static public Enum getEnum(HttpServletRequest request, Class retType, String name, Enum defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    return defVal;
	}
	try {
	    return Enum.valueOf(retType, s);
	} catch (Exception ex) {
	    return defVal;
	}
    }


    static public String getString(HttpServletRequest request, String name, String defVal) {
	String s = request.getParameter(name);
	return  (s==null)? defVal : s;
    }


    static final DecimalFormat hhFmt = new DecimalFormat("00");

    /** 
     @param name May be different from e.name (because of a prefix)
    */
    static public String mkDateTimeBox( String name, Object old, Reflect.Entry e) {
	StringBuffer b=new StringBuffer();
	String dpname =  "date-pick";
	if (old==null) {
	    b.append("<input name=\""+name+"\" id=\""+name+"\" class=\""+dpname+"\" size=\"10\"/>\n");
	} else {
	    dpname += "-" + name.replace(".", "_");
	    Calendar cal = new GregorianCalendar();
	    cal.setTime( (Date)old);
	    b.append("<script type=\"text/javascript\" charset=\"utf-8\"> \n");
	    b.append("$(function() {  $('."+dpname+"').datePicker({startDate:'01/01/2010'}).val(new Date("+
		cal.get(Calendar.YEAR)+ ","+
		cal.get(Calendar.MONTH)+ ","+
		cal.get(Calendar.DAY_OF_MONTH)+
		     ").asString()).trigger('change');  });\n");
	    b.append(	"</script>\n");
	    b.append("<input name=\""+name+"\" id=\""+name+"\" class=\""+dpname+"\" size=\"10\"/>");
	}
	
	if (e.f.isAnnotationPresent(Timing.class)) {
	    String nameHm = makeHmName(name);
	    int h0=8, h1=21, m0=0, m1=60, dm=30;
	    
	    Calendar cal = null;
	    if (old != null) {
		cal = new GregorianCalendar();
		cal.setTime( (Date)old);
	    }
	    
	    b.append("<SELECT NAME=\""+nameHm+"\">");
	    
	    String s = "";
	    boolean selDone = false;
	    for(int h=h0; h<h1; h++) {
		for(int m=m0; m<m1; m+=dm) {
		    boolean sel =  (cal != null) && !selDone &&
			timeInRange(cal, h, m, dm);
		    String hm = hhFmt.format(h) + ":" + 
			hhFmt.format(m);
		    String suff = (h < 12) ? "AM" : (h==12? "NOON" : "PM");
		    // US civilian time formatting, as per cgal
		    String text = hhFmt.format((h-1)%12+1) + ":" + 
			hhFmt.format(m) + " " + suff;
		    s += option(hm, text, sel);
		    selDone = (selDone ||  sel);
		}
	    }
	    b.append(option(NONE, "Time", !selDone));
	    b.append(s);
	    b.append("</SELECT>");		    		
	} else {
	    //b.append("[no hours needed]");    		
	}
	return b.toString();
    }
	


    /** Sets certain fields of the specified object to the values sent
      in the HTTP request string. 

      @param prefix Look for vars in the request whose names are
      prefixed with this prefix
      @param r An object that should be updated. It must be of one of
      the types User, Action, etc. for which we have reflection      info
      
      @throws  IllegalInputException If a problem with the input data is detected
     */
    static void editEntity(String prefix, Object /*OurTable*/ r, HttpServletRequest request)
	throws IllegalAccessException, InvocationTargetException, 
	       IllegalInputException {

	Class c = r.getClass();

	DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
	DateFormat dfHm = new SimpleDateFormat("MM/dd/yyyy HH:mm");

	for(Reflect.Entry e: Reflect.getReflect(c).entries) {
	    String val = request.getParameter(prefix+e.name);

	    if (val != null) {
		val = val.trim(); 
		Class retType = e.f.getType();
		Object[] arg=null;

		if (retType.equals(String.class)) {
		    if (e.isDigest()) {
			// FIXME: well, we don't set passwords like this anyway
			continue;
		    } else {
			Logging.info("String set:" + e.name + "=" + val);
			arg= new Object[]{val};		    
		    }
		} else if (retType.equals(boolean.class)) {
		    boolean x = Boolean.parseBoolean(val);
		    Logging.info("Boolean set:" + e.name + "=" + x);
		    arg= new Object[]{x};		    
		} else if (retType.equals(double.class)) { 
		    double x =(val.trim().equals(""))? 0 : Double.parseDouble(val);
		    Logging.info("Double set:" + e.name + "=" + x);
		    arg= new Object[]{x};		    
		} else if (retType.equals(int.class)) { 
		    int x = (val.trim().equals(""))? 0 :Integer.parseInt(val);
		    Logging.info("Int set:" + e.name + "=" + x);
		    arg= new Object[]{x};		    
		} else if (retType.equals(long.class)) { 
		    long x = (val.trim().equals(""))? 0 : Long.parseLong(val);
		    Logging.info("Long set:" + e.name + "=" + x);
		    arg= new Object[]{x};		    
		} else if (Date.class.isAssignableFrom(retType)) {
		    if (val.length()==0) {
			// interpret empty string as null
			arg = new Object[] {null}; continue;
		    }
		    Date x = df.parse(val, new ParsePosition(0));
		    Logging.info("Date set:" + e.name + "=" + x + ", from parsing value: " + val);
		    if (e.f.isAnnotationPresent(Timing.class)) {
			// If appropriate, look for the matching HH:mm field
			// and parse it along with the date
			String nameHm = makeHmName(e.name);
			String valHm = request.getParameter(prefix+nameHm);
			if (valHm != null && !valHm.equals(NONE)){
			    x = dfHm.parse(val+" "+valHm, new ParsePosition(0));
			    Logging.info("Date adjusted:" + e.name + "=" + x + ", from parsing values: " + val + ", " + valHm);
			}
		    }

		    arg= new Object[]{x};		    
		} else if (retType.isEnum()) {
		    Enum x = null;
		    try {
			x = Enum.valueOf(retType, val);
		    } catch(IllegalArgumentException ex) {
			Logging.warning("Impossible enum value (for type "+retType+") submitted: " + e.name + "=" + x);
			throw new IllegalInputException("HTML form or CSV file submitted inappropriate value (for type "+retType+"),  " + e.name + "=" + x + ". This could be a data entry error, or an error in the HTML form");
		    }

		    if ( Util.isIllegal(x)) {
			Logging.warning("Illegal enum value submitted: " + e.name + "=" + x);
			throw new IllegalInputException("Value " + x + "("+Util.getEA(x) + ") for parameter "+e.name+" should never be selected");
		    } else if (Util.isStoreNull(x)) {
			Logging.info("Convert " + x + " to null");
			x = null;
		    }
		    Logging.info("Enum set:" + e.name + "=" + x);
		    arg= new Object[]{x};
		} else {
		    Logging.warning("Can't handle the type of " + e.f);
		}

		if (arg!=null && arg.length>0) {
		    Logging.info("Invoke: " + e.s + "("+arg[0]+")");
		    e.s.invoke(r, arg);
		}
	    }
	}
    }

    /** Returns true if the time expressed by cal is at exactly h:m,
     * or within the next dm minutes (not inclusive)
     */
    static boolean timeInRange(Calendar cal, int h, int m, int dm) {
	int hNext=h, mNext = m + dm;
	if (mNext>=60) {
	    mNext=0;
	    hNext++;
	}
	Calendar t1 = new GregorianCalendar( cal.get(Calendar.YEAR),
					     cal.get(Calendar.MONTH),
					     cal.get(Calendar.DAY_OF_MONTH),
					     h, m),
	    t2 = new GregorianCalendar(cal.get(Calendar.YEAR),
				       cal.get(Calendar.MONTH),
				       cal.get(Calendar.DAY_OF_MONTH),
				       hNext, mNext);

	boolean result= (t1.compareTo(cal)<=0) && (cal.compareTo(t2)<0);
	if (result) Logging.info("" + t1 + " <= " + cal + " < " + t2);
	return result;
    }

    private static String option(Object value, Object text) {
	return option(value, text, false);
    }
    private static String option(Object value, Object text, boolean selected) {
	return  "<OPTION value=\"" + value + "\"" +
	    (selected?  " SELECTED>" : ">") +  text + "</OPTION>\n";	
    }

    /** Generates a SELECT HTML tage with OPTIONs for all values of a given
	enum type
	@param name name of the request parameter to be sent by the form due to this tag
	@param t an Enum type
	@param old Contains the default value
     */
    static String mkSelector(String name, Class t, Object old) {
	StringBuffer b = new StringBuffer();
	b.append("<SELECT NAME=\""+name+"\">\n");
	Object[] con = t.getEnumConstants();
	for(int j=0; j<con.length; j++) {
	    boolean selected = (old == null)? (j==0): (old==con[j]);
	    String text = Util.getEA((Enum)con[j], con[j].toString());
	    b.append(option( con[j], text, selected));
	}
	b.append("</SELECT>");
	return b.toString();
    }

    static String mkSelectorBoolean(String name, Object old) {
	StringBuffer b = new StringBuffer();
	b.append("<SELECT NAME=\""+name+"\">\n");
	boolean ov = (old==null)? false: ((Boolean)old).booleanValue();
	b.append(option("false", "No", !ov));
	b.append(option("true", "Yes", ov));
	b.append("</SELECT>");		    
	return b.toString();
    }

}
