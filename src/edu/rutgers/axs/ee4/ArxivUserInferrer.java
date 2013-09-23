package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;
//import java.text.*;

import javax.persistence.*;

import org.json.*;

/** This class encapsulates the process whereby we decide what
    real-life user (a human being) effected a particular recorded action. 
*/

abstract class ArxivUserInferrer {

    int ignoredCnt=0, fromIPCnt=0, fromAnonCookieCnt=0, fromUserCookieCnt=0;

    /** Looks at the ip and cookie information, applies some (possibly
	trivial) algorithm, and decides who was the user carrying out the 
	action.

	@return A string that identifies the user in some
	way. Possibly null, if it think the user can't be inferred.
     */
    abstract String inferUser(String ip_hash, String cookie_hash);

    String report() {
	return "" +  
	    ignoredCnt + " actions ignored due to lack of user information; " + 
	    fromIPCnt + " interpreted based on IP address; " +
	    fromAnonCookieCnt + " based on anon cookies; " +  
	    fromUserCookieCnt + " based on user cookies";
    }

}