package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

/** A tool for modifying named fields of an object
 */
@SuppressWarnings("unchecked")
public class Edit {

    /** Sets certain fields of the specified object to the values sent
      in the HTTP request string. 

      @param prefix Look for vars in the request whose names are
      prefixed with this prefix
      @param r An object that should be updated. It must be of one of
      the types Respondent, Call, etc. for which we have reflection
      info
      
      @throws  IllegalInputException If a problem with the input data is detected
     */
    static void editEntity(/*Respondent*/  OurTable r, 
			   HashMap<String,String> vmap)
	throws IllegalAccessException, InvocationTargetException, 
	       IllegalInputException {

	Class c = r.getClass();

	//	DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
	//DateFormat dfHm = new SimpleDateFormat("MM/dd/yyyy HH:mm");
	DateFormat df = new SimpleDateFormat("yyyy.MM.dd");


	Reflect ref = Reflect.getReflect(c);

	for(Reflect.Entry e: Reflect.getReflect(c).entries) {
	    String val = vmap.get(e.name);

	    if (val == null) continue;
	    val = val.trim(); 
	    if (val.equals("")) continue;
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
		    /*
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
		    */
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