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

/** Retrieves and displays a "suggestion list": a list of articles which some
    kind of automatic process has marked as potentially interesting to the user.
 */
public  class ViewSuggestionsBase extends PersonalResultsBase {

    /** List scrolling */
    public int startat = 0;
    /** the actual suggestion list to be displayed is stored here */
    public SearchResults sr;
 
    public ViewSuggestionsBase(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
    }

    /** Decides what kind of recommendation list is needed, and returns it
     */
    public static ViewSuggestionsBase getMainPageSuggestions(HttpServletRequest _request, HttpServletResponse _response) {

	ViewSuggestionsBase base = new  ViewSuggestionsBase( _request, _response);
	if (base.error || base.actorUserName==null) return base;

	EntityManager em = base.sd.getEM();
	try {

	    User actor=User.findByName(em, base.actorUserName);
	    User.Program program = actor.getProgram();
	    if (program==null) {
		base.error = true;
		base.errmsg = "It is not known what experiment plan user " + actor + " participates in";
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
	return s;
    }

}
