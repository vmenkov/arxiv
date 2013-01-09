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

    /** List scrolling */
    public int startat = 0;
    /** the actual suggestion list to be displayed is stored here */
    public SearchResults sr;

    public int folderSize=0;

 
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
	return s;
    }

    /** Should be properly overriden by all subclasses that do exclusions.
     */
    public String excludedPagesMsg() { return ""; }

    /** Testing only */
    ViewSuggestionsBase() {}

    /** URL for calling JudgmentServlet not to record a judgment, but
	simply to update certain elements of the main page 
    */
    /*
    private String onLoadJudge() {
	return RatingButton.judgeNoSrc(cp, null, Action.Op.NONE);
    }

    public String onLoadJsCode() {
	String js = "alert('Hi from onload!'); $.get('" + onLoadJudge()+ "', " +
	    "function(data) { alert('got response: ' + data); eval(data);});";
	return js;
    }
    */

    // $.post("test.php", { name: "John", time: "2pm" } );

    /** Formatting arg list for a POST call
     */
    private void addToArgs(StringBuffer args, String p0, String p1) {
	if (args.length()>0) args.append( ", ");
	args.append( p0 + " : '"+ p1 +"'");
    }

    /** This is inserted into BODY ONLOAD="..." */
    public String onLoadJsCode() {
	String url = cp + "/JudgmentServlet";
	StringBuffer args = new StringBuffer();
	addToArgs(args, BaseArxivServlet.ACTION, ""+Action.Op.NONE);
	for(String[] p: asrc.toQueryPairs()) {
	    addToArgs(args, p[0], p[1]);
	}

	String js = //"alert('Hi from onload!');" +
	    super.onLoadJsCode() +
	    " $.post('"+url+"', { "+ args +" }, " +
	    "function(data) { "+
	    //"alert('got response: ' + data); "+
	    "eval(data);});";
	return js;
    }
  

}
