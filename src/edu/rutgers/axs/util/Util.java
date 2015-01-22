package edu.rutgers.axs.util;

/** Various useful methods */
public class Util {
    /** Like "join()" in perl. */
    public static String join(String sep, String[] v) {
	StringBuffer s = new StringBuffer();
	for(String z: v) {
	    if (s.length()>0) s.append(" ");
	    s.append(z);
	}
	return s.toString();
    }


}