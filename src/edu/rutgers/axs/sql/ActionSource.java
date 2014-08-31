package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.servlet.*;
import javax.servlet.http.*;

import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.web.ResultsBase;
import edu.rutgers.axs.html.QS;

/** An ActionSource objects indicates in what kind of context the user
    has carried out a particular action. Support is provided for
    tracking the user's action context through a sequence of visited
    pages. This is usually done by means of encapsulating the
    ActionSource information into a URL: an ActionSource object can be
    converted into a string that can be inserted into a URL, and it
    can also be initialized based on the data extracted from a URL.
 */
public class ActionSource {

    /** Special (optional) parameters for JudgmentServlet, Search, etc */
    private final static String SRC = "src", PL = "pl";

    /** The context type (e.g., vieweing the main page, viewing the
	user folder, etc.) */
    public Action.Source src=Action.Source.UNKNOWN;
    /** Whenever applicable, refers to the particular {@link edu.rutgers.axs.sql.PresentedList presented list}
	(typically, a one-page section of the suggestion list) in the
	context of which the action was carried out. The value of 0 means that
	the context did not involve a PresentedList.
     */
    public long presentedListId=0;
    public ActionSource( Action.Source _src, long _presentedListId) {
	src = _src;
	presentedListId= _presentedListId;
    }

    /** Used for inserting ActionSource info into a FilterServlet url */
    static private String myPrefix = "/my.";

    /** Generates the string that needs to be inserted into the
	beginning of PathInfo when ArticleServlet redirects to 
	{@link edu.rutgers.axs.web.FilterServlet}.
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
	has been prepended to the PathInfo, to indicate the action
	source. If this has been found, initializes this ActionSource
	object accordingly, and removes the action source information
	from the path. This is used when processing {@link
	edu.rutgers.axs.web.FilterServlet} requests.

	@param pi The PathInfo string to be analyzed
	@return What's left of the PathInfo string after the
	ActionSource information (if found) has been removed from it.
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

    /** Extracting ActionSource info from an HTTP request, e.g. for a
	judgment button click. */
    public ActionSource( HttpServletRequest request) {
	src = (Action.Source)Tools.getEnum(request, Action.Source.class,
					  SRC, Action.Source.UNKNOWN);	 
	presentedListId =  Tools.getLong(request, PL, 0);
    }

    /** Removes the ActionSource information from a query string. This
	is used in URL rewriting (repaging).	
     */
    public static void stripActionSource(QS qs) {
	qs.strip( SRC);
	qs.strip( PL);
    } 
 
   
    /** Produces a component to be added to the query string, containing
	action source information. This is passed to JudgmentServlet etc. 
	@return A query string component (including the leading '&amp;') */
   public String toQueryString() {
       return toQueryString(false);
   }

   /** Produces a component to be added to the query string, containing
	action source information. This is passed to JudgmentServlet etc. 
	@param leading This is going to be used as the first parameter
	after '?' (and thus does not need the leading ampersand)
	@return A query string component (including the leading '&amp;', unless
	leading=true) */
     public String toQueryString(boolean leading) {
	String s="";
	for(String[] p:  toQueryPairs()) {
	    if (!leading || s.length()>0) s += "&";
	    s += p[0] +"=" + p[1];
	}
	return s;
    }

    public String toString() {
	return "[" + toQueryString() + "]";
    }

    public Vector<String[]> toQueryPairs() {
	Vector<String[]> v= new Vector<String[]>(2);
	if (src==null || src==Action.Source.UNKNOWN) return v;
	v.add( new String[] {SRC, ""+src});
	if (presentedListId >0) {
	    v.add( new String[]{PL, ""+presentedListId});
	}
	return v;
    }


    /** This is set and used by FilterServlet only. */
    public boolean filterServletMustInheritSrc=false;

}