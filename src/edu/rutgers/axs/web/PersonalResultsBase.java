package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.html.Html;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/** Common class for many scripts in the "personal" folder
 */
public class PersonalResultsBase extends ResultsBase {
    /** The user name of the user whose activity we research */
    public String actorUserName=null;
    /** The object for the user whose activity we research. 
	Note that the "actor" may be different from the 
	user who is viewing the data, since researchers can
	(and will) view other users' action history,
	suggestion lists, etc.
     */
    public User actor;

    public EE4User ee4u = null;


    /** Is the user requesting a list for his own activity (rather
	than for someone's else, as a researcher)? */
    public boolean isSelf = false;

    /** "Expert mode" allows one to view more options in certain screens */
    public boolean expert = false;
    /** User has requested to create a new task. */
    public boolean force=false;
    
    /** The currently recorded last action id for the user in question */
    public long actorLastActionId=0;

    /** Only set if the user has explicitly requested an individual file to be viewed. */
    public String requestedFile=null;

    public static final  String MODE="mode", DAYS="days", EXPERT="expert";
    /** For ViewSuggestionList: give suggestions based on a specific
	profile, or on a specific data type */
    public static final String BASEDON = "basedon", BASEDON_TYPE = "basedon_type";

    public PersonalResultsBase(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	force= getBoolean(FORCE, false);
	expert= getBoolean(EXPERT, false);

	if (error) return;
	actorUserName =  getString(USER_NAME, user);
	isSelf =  (actorUserName==null) || (actorUserName.equals(user));
	requestedFile =  getString(FILE, null);

	EntityManager em = sd.getEM();
	try {
	    actor=User.findByName(em, actorUserName);
	    if (actor!=null) ee4u=(EE4User)em.find(EE4User.class,actor.getId());
	} catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	}

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

    /** Wrapper for the same method in ResultsBase. */
    public String resultsDivHTML(ArticleEntry e) {
	return resultsDivHTML(e, isSelf);
    }


    /**  Links to prev/next pages, if necessary */	
    public String prevNextLinks(SearchResults sr) {
	String s="";
	if (sr.needPrev) {     
	    s += Html.a( repageUrl(sr.prevstart), "[PREV PAGE]");
	}	
	if (sr.needNext) { 
	    s += Html.a( repageUrl(sr.nextstart), "[NEXT PAGE]");
	}
	return s;
    }

    /** Returns the user's experimental plan identifier ("program"), if the
	user acts on his own behalf. Returns null if this page is rendered
	for a researcher looking at some other's data. 

	This is used to properly configure various controls (such as
	rating buttons) which are different in different experiments,
     */
    User.Program getUserProgram() {	
	return isSelf? actor.getProgram() : null;
    }

    /** Testing only */
    PersonalResultsBase() {}



}
