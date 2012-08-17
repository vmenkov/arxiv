package edu.rutgers.axs.sql;

import java.io.*;
//import java.util.*;
import java.util.regex.*;
import javax.servlet.*;
import javax.servlet.http.*;

//import javax.persistence.*;

import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.web.ResultsBase;

public class ActionSource {

    /** Special (optional) parameters for JudgmentServlet, Search, etc */
    private final static String SRC = "src", PL = "pl";

    public Action.Source src=Action.Source.UNKNOWN;
    public long presentedListId=0;
    public ActionSource( Action.Source _src, long _presentedListId) {
	src = _src;
	presentedListId= _presentedListId;
    }

    /** Used for inserting ActionSource info into a FilterServlet url */
    static private String myPrefix = "/my.";

    /** Generating the string that needs to be inserted into the
	beginning of PathInfo when ArticleServlet redirects to 
	FilterServlet
     */
    public String toFilterServletString() {
	String q="";
	if (src == null || src == Action.Source.UNKNOWN) return q;
	q = myPrefix + SRC + ":" + src;
	if (presentedListId>0) {
	    q +=  myPrefix + PL + ":" + presentedListId;
	}
	return q;	
    }

    static private Pattern patSrc = 
	Pattern.compile( Pattern.quote(myPrefix + SRC + ":" ) + "([A-Z_0-9]+)"),
	patDf =
	Pattern.compile( Pattern.quote(myPrefix + PL + ":" ) + "([0-9]+)");

    /** Checks if a special text, such as '/my.src:MAIN_SL/my.df:128',
	has been prepended to the PathInfo, to indicate that 

	@param asrc Primarily an output parameter: modified based on what we've found
     */
    public String extractFromFilterServletPathInfo(String pi) {
	Matcher m = patSrc.matcher(pi);

	//	Logging.info("FS: Matching: pi=" + pi + "\npat=" + patSrc +
	// "\nlookingAt=" + m.lookingAt());


	if (!m.lookingAt() || m.start()!=0) return pi;
	src = (Action.Source)Enum.valueOf(Action.Source.class, m.group(1));
	pi = pi.substring(m.end());

	m = patDf.matcher(pi);
	if (!m.lookingAt() || m.start()!=0) return pi;
	presentedListId = Long.parseLong(m.group(1));
	return pi.substring(m.end());
    }

    public ActionSource( HttpServletRequest request) {
	src = (Action.Source)Tools.getEnum(request, Action.Source.class,
					  SRC, Action.Source.UNKNOWN);	 
	presentedListId =  Tools.getLong(request, PL, 0);
    }

    /** Produces a component to be added to the query string, containing
	action source information. This is passed to JudgmentServlet etc. */
    public String toQueryString() {
	String s="";	       
	if (src==null || src==Action.Source.UNKNOWN) return s;
	s += "&"+SRC +"=" + src;
	if (presentedListId >0) {
	    s += "&"+PL +"=" + presentedListId;
	}
	return s;
    }

    /** This is set and used by FilterServlet only. */
    public boolean filterServletMustInheritSrc=false;

}