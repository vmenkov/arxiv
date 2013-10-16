package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import org.json.*;

/** This class infers the user based on the cookie_hash field. The IP
    is simply disregarded.
*/

class CookieArxivUserInferrer extends ArxivUserInferrer {

    private final ArxivUserTable table;
    
    CookieArxivUserInferrer(ArxivUserTable t) {
	table = t;
    }
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