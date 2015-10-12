package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import org.json.*;

/** This class infers the user based on the cookie_hash field. The IP
    is simply disregarded (i.e., if no cookie is associated with the
    action entrie, we say that we can't infer the user).
*/

class CookieArxivUserInferrer extends ArxivUserInferrer {

    private final ArxivUserTable table;

    /** @param t The table that links cookies to ArXiv's registered users.
     */    
    CookieArxivUserInferrer(ArxivUserTable t) {
	table = t;
    }

    /** Looks at the ip and cookie information, and decides who was
	the user carrying out the action. 

	@return A string that identifies the user in some way. The
	algorithm is as follows: if the cookie is known to be
	associated with a registered user, return the appropriate user
	name (actually, user id hash); otherwise, we have an anonymous
	action, and the pseudo-user-id ("C-" + cookie_hash) is
	returned. If cookie_has is null (which does not seem to ever
	be the case in our usage logs), null will be returned.

     */
    String inferUser(String ip_hash, String cookie_hash) {
	if (cookie_hash==null ||cookie_hash.equals("")) {
	    ignoredCnt ++;
	    return null;
	} 

	String u = table.cookie2user.get(cookie_hash);
	if (u!=null) {
	    fromUserCookieCnt++;
	    return u;
	} else {
	    fromAnonCookieCnt++;
	    // avoiding potential ambiguity between cookie_hash
	    // and user_hash values
	    return "C-" + cookie_hash;
	}
    }
}