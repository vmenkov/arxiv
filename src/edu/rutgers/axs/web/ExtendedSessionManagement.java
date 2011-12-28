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

/** This is used to manage extended sessions, which survive over the
    server restart. This is used when the user checks the "remember
    me" button on login. The information is stored persistently in a
    User structure in the database,
 */
public class ExtendedSessionManagement {

    /** A random generator initialized at startup */
    static private final Random random= new Random(System.currentTimeMillis());
    public final static String COOKIE_NAME = "MyArxivExtendedSession";

    /** max lifetime of an extended session, in hours */
    static public final int maxHours = 24;   

    /** Creates a cookie with a temporary password for an "extended session",
	and adds the pertinent information to the user record. This method
	should be called inside a transaction, and followed by a "persist"
	call.
    */
    static Cookie makeCookie(User u) {
	final int maxSec = 3600 * maxHours;

	String unameEncoded=u.getUser_name();
	try {
	    unameEncoded = URLEncoder.encode(unameEncoded, "UTF-8");
	} catch(java.io.UnsupportedEncodingException ex) {}
		
	String tmpPass = "" + random.nextLong();
	String x = org.apache.catalina.realm.RealmBase.Digest(tmpPass, "MD5", "utf-8" );
	u.setEncEsPass( x);

	Date now = new Date();	
	Date expiration = new Date( now.getTime() + 1000 * maxSec ); // msec
	u.setEsEnd( expiration );

	String val = unameEncoded + ":" + tmpPass;
	Cookie cookie=new Cookie(COOKIE_NAME, val);
	cookie.setMaxAge( maxSec); // max age in seconds
	cookie.setPath("/");
	return cookie; 
    }

    /** Should be called upon logout, followed by a persist() call. */
    static void invalidateEs(User u) {
	u.setEsEnd(null);
	u.setEncEsPass("");
    }


    /** Checks if the cookie identifies a still-valid extended session, 
	and if so, returns the pertinent User instance.
	@return A User object, or null if there is no valid extended session. */
    static User getValidEsUser( EntityManager em, Cookie cookie) {
	if (cookie==null) return null;
	String val = cookie.getValue();
	if (val==null) return null;
	String[] z = val.split(":");
	if (z.length!=2) return null;
	String uname = z[0];
	try {
	    uname=URLDecoder.decode(uname, "UTF-8");
	} catch(java.io.UnsupportedEncodingException ex) {}
	
	String encPass= org.apache.catalina.realm.RealmBase.Digest(z[1], "MD5", "utf-8" );

	User u = User.findByName(em, uname);
	String storedEncPass = u.getEncEsPass();
	if (storedEncPass == null || !encPass.equals(storedEncPass)) {
	    Logging.info("Extended session password mismatch; ignoring cookie");
	    return null;
	}
	Date expiration = u.getEsEnd();
	if (expiration==null || expiration.compareTo( new Date())<0) {
	    Logging.info("Extended session expired; ignoring cookie");
	    return null;	    
	}
	Logging.info("Identified user " +uname + " from a valid extended session cookie");
	return u;
    }

    static Cookie findCookie( HttpServletRequest request) {
	for(Cookie c: request.getCookies()) {
	    if (c.getName().equals(COOKIE_NAME)) {
		return c;
	    }
	}
	return null;
    }



}