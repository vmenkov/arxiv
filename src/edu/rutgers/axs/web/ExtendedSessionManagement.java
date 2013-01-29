package edu.rutgers.axs.web;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.catalina.realm.RealmBase;
import edu.rutgers.axs.sql.*;

/** This class is used to manage extended sessions, which survive over the
    web server restart. This feature is used when the user checks the
    "remember me" button on login. The information is stored
    persistently in a User structure in the database,

    <p>
    FIXME: Since a new cookie (with a new single-use password) is generated 
    every time a user tries to log in, and only one such single-use password 
    is stored, a user cannot be logged in from more than one user agent at
    once. 
 */
public class ExtendedSessionManagement {

    /** A random number generator initialized at startup */
    static private final Random random= new Random(System.currentTimeMillis());
    public final static String COOKIE_NAME = "MyArxivExtendedSession";

    /** max lifetime of an extended session, in hours */
    static public final int maxDays = 180;   
    static private final int maxHours = maxDays*24;   

    /** Creates a cookie with a temporary password for an "extended session",
	and adds the pertinent information to the user record. This method
	should be called inside a transaction, and followed by a "persist"
	call.

	@param u The user record. Information about the new session will be added to this record by this method.
	@return The new cookie, to be sent to the user agent
    */
    static Cookie makeCookie(User u) {
	final int maxSec = 3600 * maxHours;

	String unameEncoded=u.getUser_name();
	try {
	    unameEncoded = URLEncoder.encode(unameEncoded, "UTF-8");
	} catch(java.io.UnsupportedEncodingException ex) {}
		
	String tmpPass = "" + random.nextLong();
	String x = org.apache.catalina.realm.RealmBase.Digest(tmpPass, "MD5", "utf-8" );

	Date now = new Date();	
	Date expiration = new Date( now.getTime() + 1000L * (long)maxSec ); // msec

	//	u.setEncEsPass( x);
	//u.setEsEnd( expiration );
	ExtendedSession es = new	ExtendedSession(x, expiration ); 

	Set<ExtendedSession> eset = u.getEs();
	sizeControl(eset);
	eset.add(es);

	String val = unameEncoded + ":" + tmpPass;
	Cookie cookie=new Cookie(COOKIE_NAME, val);
	cookie.setMaxAge( maxSec); // max age in seconds
	cookie.setPath("/");

	Logging.info("Created cookie for user " + u.getUser_name() + " ["+val+"]; tmp pass encoded as " + x);


	return cookie; 
    }

    /** Scans the set of sessions, removing some, to keep its size
	under control.
     */
    private static void sizeControl(Set<ExtendedSession> eset) {
	final int N=3; // max set size

	HashSet<ExtendedSession>  expired = new HashSet<ExtendedSession>();

	ExtendedSession oldest = null;
	for(ExtendedSession es: eset) {
	    Date expiration = es.getEsEnd();
	    if (expiration==null || expiration.compareTo( new Date())<0) {
		expired.add(es);
	    }
	    if (oldest==null|| expiration.before(oldest.getEsEnd())) {
		oldest=es;
	    }
	}
	for(ExtendedSession z: expired) {
	    eset.remove(z);
	}
	if (eset.size()<N) return;
	eset.remove(oldest);
    }

    /** Should be called upon logout, followed by a persist()
        call. This terminates all extended session for this user (on
        all machines). */
    static void invalidateEs(User u) {
	Set<ExtendedSession> s = u.getEs();
	if (s==null) {
	    s=new HashSet<ExtendedSession>();
	} else {
	    s.clear();
	}
	u.setEs(s);
	//	u.setEsEnd(null);
	//      u.setEncEsPass("");
    }


    /** Checks if the cookie identifies a still-valid extended session, 
	and if so, returns the pertinent User instance.
	@param cookie The cookie received by the server from the user agent
	@return A User object, or null if there is no valid extended session. */
    static User getValidEsUser( EntityManager em, Cookie cookie) {
	if (cookie==null) return null;
	String val = cookie.getValue();
	if (val==null) return null;
	String[] z = val.split(":");
	if (z.length!=2) return null;

	Logging.info("received cookie: ["+val+"]");

	String uname = z[0];
	try {
	    uname=URLDecoder.decode(uname, "UTF-8");
	} catch(java.io.UnsupportedEncodingException ex) {}
	
	String encPass= org.apache.catalina.realm.RealmBase.Digest(z[1], "MD5", "utf-8" );

	User u = User.findByName(em, uname);
	Set<ExtendedSession> s = u.getEs();

	if (s==null) return null;
	for(ExtendedSession es: s) {
	    String storedEncPass = es.getEncEsPass();
	    if (storedEncPass == null || !encPass.equals(storedEncPass)) continue;
	    Date expiration = es.getEsEnd();
	    if (expiration==null || expiration.compareTo( new Date())<0)  continue;
	    return u; // match!	    
	}
	return null;

	/*
	String storedEncPass = u.getEncEsPass();
	if (storedEncPass == null || !encPass.equals(storedEncPass)) {
	    Logging.info("Extended session password mismatch (stored="+storedEncPass+", cookie has="+z[1]+", which encrypts to "+encPass+"); ignoring cookie");
	    return null;
	}
	Date expiration = u.getEsEnd();
	if (expiration==null || expiration.compareTo( new Date())<0) {
	    Logging.info("Extended session expired; ignoring cookie");
	    return null;	    
	}
	//Logging.info("Identified user " +uname + " from a valid extended session cookie");
	return u;
	*/

    }

    static Cookie findCookie( HttpServletRequest request) {
	Cookie[] cs = request.getCookies();
	if (cs==null) return null;
	for(Cookie c: cs) {
	    if (c.getName().equals(COOKIE_NAME)) {
		return c;
	    }
	}
	return null;
    }



}