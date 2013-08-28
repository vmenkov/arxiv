package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.bernoulli.*;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.html.RatingButton;

/** Retrieves and displays a "suggestion list": a list of articles which some
    kind of automatic process has marked as potentially interesting to the user.
 */
public  class ViewSuggestionsBase extends PersonalResultsBase {

    /** Contols suggestion list paging. Typically supplied via the URL
	(0 by default), but may be adjusted by application if it does
	not have much sense otherwise. */
    public StartAt startat;
    /** the actual suggestion list to be displayed is stored here */
    public SearchResults sr;

    public int folderSize=0;

    /** This flag is set on "redisplay" calls (with plid=... in the URL),
	usually email-driven.
     */
    public boolean isRestored = false;
 
    public ViewSuggestionsBase(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	startat = new StartAt(_request);
    }

    /** Decides what kind of recommendation list is needed, and returns it
     */
    public static ViewSuggestionsBase getMainPageSuggestions(HttpServletRequest _request, HttpServletResponse _response) {

	ViewSuggestionsBase base = new  ViewSuggestionsBase( _request, _response);
	if (base.error || base.actorUserName==null) return base;

	EntityManager em = base.sd.getEM();
	try {

	    User.Program program = base.actor.getProgram();

	    if (program==null) {
		base.error = true;
		base.errmsg = "It is not known what experiment plan user " + base.actorUserName + " participates in";
		return base;
	    }


	    if (program.needBernoulli()) {
		return new BernoulliViewSuggestions(_request, _response);
	    } else {
		return new ViewSuggestions(_request, _response,true);
	    }
	} catch (Exception _e) {
	    base.setEx(_e);
	    return base;
	} finally {
	    ResultsBase.ensureClosed( em, true);
	}
    }

    public boolean noList() {
	return (sr == null || sr.reportedLength==0);
    }

    public String noListMsg() {
	return "<p>Presently, no recommendations are available for you.</p>\n";
    }

    public String describeList() {
	String s="";
	s += "<p>The entire list contains " + sr.atleast + " " +
	    sr.reportedLength + " articles.";
	
	if (sr.entries.size()==0) { 
	    s += " There is nothing to display.";
	} else { 
	    s += " Articles ranked from " +  sr.entries.elementAt(0).i +
		" through " + sr.entries.lastElement().i + " are shown below.";
	}
	s += "</p>\n";

	if (isRestored) {
	    s += "<p>";
	    s += "Note: this is a re-display of the top section of a recommendation list generated earlier. Your <a href=\""+ cp + "/index.jsp\">most current recommendation list</a> may be different.";
	    s += "</p>\n";
	}

	return s;
    }

    /** Should be properly overriden by all subclasses that do exclusions.
     */
    public String excludedPagesMsg() { return ""; }

    /** Testing only */
    ViewSuggestionsBase() {}


    public String onLoadJsCode() {
	return refreshEverythingJsCode();
    }


}
