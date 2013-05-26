package  edu.rutgers.axs;

/** Contains the version name of this application, for encoding in
  XML files etc. Also used by the Javadoc script for inserting into the 
  API doc pages.

 <h3>Recent history</h3>
 <pre>
</pre>
 */
public class Version {
    /** The version number of the applications */
    public final static String version = "0.2.031";
    public final static String date = "2012-05-25";

    public static String getVersion() { return version; }

    /** Version number and date */
    public static String getInfo() { return version + " ("+date+")"; }
    
    
    /** Compares two version numbers (described as strings). 
	@return a negative number if the first argument is smaller, a positive if the second is smaller; 0 if they are the same.
     */
    static public int compare(String v1, String v2) 
	throws IllegalArgumentException {
	if (v1.equals(v2)) return 0;
	String[] a1=v1.split("\\.");
	String[] a2=v2.split("\\.");
	int i=0;
	for(;i<a1.length && i<a2.length; i++) {
	    int x1, x2;
	    try {
		x1 = Integer.parseInt(a1[i]);
	    } catch ( NumberFormatException ex) {
		throw new IllegalArgumentException("Not a valid version number: " + v1);
	    }
	    try {
		x2 = Integer.parseInt(a2[i]);
	    } catch ( NumberFormatException ex) {
		throw new IllegalArgumentException("Not a valid version number: " + v2);
	    }
	    int d  = x1-x2;
	    if (d!=0) return d;
	}
	// either no string has any non-compared sections left, or only one does
	return  (a1.length - a2.length);
    }
}
