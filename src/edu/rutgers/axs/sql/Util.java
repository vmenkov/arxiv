package edu.rutgers.axs.sql;

import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
//import edu.rutgers.axs.web.Logging;

/** A bunch of auxiliary methods */
public class Util {
    static public Date today() {
	Calendar cal = new GregorianCalendar(); // cur time
	Calendar x = new GregorianCalendar(cal.get(Calendar.YEAR),
				     cal.get(Calendar.MONTH),
				     cal.get(Calendar.DAY_OF_MONTH));
	return x.getTime();
    }

    /** Retrieves the {@link edu.rutgers.axs.sql.EA} annotation 
	from an object of an Enum type. Never returns null; if nothing is 
	found, returns an empty string.
    */
    static public String getEA(Enum con) {
	return getEA(con, "");
    }

    static public String getEA(Enum con, String defVal) {
	EA anno = getEa(con);
	if (anno==null) return defVal;
	String s =anno.value();
	return (s==null || s.equals(""))? defVal: s;
    }

    /** Human-readable printable name for an enum constant: the actual
     * name, or EA.alt */
    static public String getPrintableName(Enum con) {
	EA anno = getEa(con);
	String def= con.toString();
	if (anno==null) return def;
	String s =anno.alt();
	return (s==null || s.equals(""))? def: s;
    }


    /** Checks if a given enum value carries an @EA(illegal=true) annotation.
     */
    static public boolean isIllegal(Enum con) {
	EA anno = getEa(con);
	return (anno!=null) && anno.illegal();
    }


    static public boolean isStoreNull(Enum con) {
	EA anno = getEa(con);
	return (anno!=null) && anno.storeNull();
    }

    static public EA getEa(Enum con) {
	Field f = null;
	try {
	    f = con.getClass().getDeclaredField(con.toString());
	} catch (NoSuchFieldException ex) {	}
	if (f==null) return null;
	return  (EA)f.getAnnotation(EA.class);
    }

    /** @param t time interval in seconds */
    private static String long2dhms(long t) {
	if (t<60) return ""+t + " sec";
	t /= 60;
	if (t<60) return ""+t + " min";
	t /= 60;
	if (t<24) return "" + t + " hours";
	if (t<48) return "1 day and " + (t%24) + " hours";
	return "" + (t/24) + " days";
    }


   /** @return A text message of the form "such-and-such time (3 hours ago)", or "never"
     */
    static public String ago(Date d) {
	if (d==null) return "never";
	String s = d.toString();
	Date now = new Date();
	long t = (now.getTime() - d.getTime())/1000;
	if (Math.abs(t)<=1) {
	    return s + " (right now)";
	} else if (t>0) {
	    return s + " (" + long2dhms( t) + " ago)";
	} else {
	    return s + " (" + long2dhms( -t) + " in the future)";
	}
    }

    static public String timeAndAgo(Date d) {
	return d==null? "never" :  Reflect.compactFormat(d) + " " + Util.ago(d);
    }

}