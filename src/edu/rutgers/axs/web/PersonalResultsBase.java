package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/** Common class for many scripts in the "personal" folder
 */
public class PersonalResultsBase extends ResultsBase {
    /** The user name of the user whose activity we reasearch */
    public String actorUserName=null;
    public User actor;


    /** Is the user requesting a list for his own activity (rather
     * than for someone's else, as a researcher)? */
    public boolean isSelf = false;
    /** User has requested to create a new task. */
    public boolean force=false;
    
    /** The currently recorded last action id for the user in question */
    public long actorLastActionId=0;

    /** Only set if the user has explicitly requested an individual file to be viewed. */
    public String requestedFile=null;

    public static final  String MODE="mode", DAYS="days";
    /** For ViewSuggestionList: give suggestions based on a specific profile. */
    public static final String BASEDON = "basedon";

    public PersonalResultsBase(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	force= getBoolean(FORCE, false);

	if (error) return;
	actorUserName =  getString(USER_NAME, user);
	isSelf = (actorUserName.equals(user));
	requestedFile =  getString(FILE, null);

    }

    public String viewActionsLink() {
	if (isSelf) {
	    return cp + "/personal/viewActionsSelf.jsp";
	} else {
	    return cp + "/tools/viewActions.jsp?" +USER_NAME+"=" + actorUserName ;
	}
    }

    /** FIXME: must specify profile type, too, if multiple types are
     * supported (Algo 2)... */
    public String viewLatestProfileLink() {
	return cp + "/personal/viewUserProfile.jsp?" +
	    USER_NAME+"=" + actorUserName;
    }
  
    public String viewProfileLink(String file) {
	return cp + "/personal/viewUserProfile.jsp?" +
	    USER_NAME+"=" + actorUserName +
	    "&" + FILE+"=" + file;
    }

    public String viewSuggestionsLink(String file) {
	return cp + "/personal/viewSuggestions.jsp?" +
	    USER_NAME+"=" + actorUserName +
	    "&" + FILE+"=" + file;
    }

    public String formatSuggestionsLink(String file) {
	return "<a href=\"" + viewSuggestionsLink(file)+ "\">"+file+"</a>";
    }
    

}
