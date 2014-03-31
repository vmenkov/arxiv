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

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.indexer.Common;

/** This class is responsible for the retrieval, formatting, and
    displaying of a session-based recommendation list. This is a bit
    like a "lite" version of ViewSuggestions.
*/
public class SessionBased  extends ResultsBase {
   /** the actual suggestion list to be displayed is stored here */
    public SearchResults sr;
    
    /** Indicates if the article list will be displayed in a separate popout
	window or in an IFRAME
     */
    public final boolean popout;

    /** This will be set to true if we want the client to retry
	loading this page in a few second. One such situation is when
	there is no rec list available yet, but computing one is in
	progress.
     */
    public boolean wantReload=false;

    public SessionBased(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	popout = getBoolean("popout", true);

	sr = null;
	long plid=0;
	if (sd.sbrg!=null) {
	    // get the pre-computed rec list
	    sr = sd.sbrg.getSR();
	    if (sr!=null) {
		infomsg += "<br>" + sd.sbrg.description() + "\n";
		plid = sd.sbrg.getPlid();
	    } else if (sd.sbrg.hasRunning()) {
		// tell the browser to come ask again in a few sec
		wantReload=true;
	    }
	} 
	infomsg += "<br>List presented at " + (new Date()) +  "\n";

	// setting this page's "action source" information, which is used
	// to form FilterServlet URLs
	Action.Source srcType =	 Action.Source.SB;
	asrc= new ActionSource(srcType, plid);
	
    }

    /** Creates a snippet  of HTML describing one article entry. 

	<ul>
	<li> title
	<li> subject
	<li> first 2 authors, ..., last
	</ul>

    */
    public String resultsDivHTMLLite(ArticleEntry e, double largest) {

	String rt = "[" + e.idline + "; score="+e.score+ "; "+
	    e.formatDate()+"]";
	if (e.researcherCommline!=null && e.researcherCommline.length()>0) {
	    rt += "["+e.researcherCommline+"]";
	}
	rt += " ";

	String aName = "article_" + e.id;

	// The JavaScript snippet used to load the article in question
	// into the main window
	String js = popout?
	    "javascript:window.opener.location.href='" +urlAbstract(e.id)+ "';":
	    "javascript:window.parent.location.href='" +urlAbstract(e.id)+ "';";

	String s =  

	    "<table id=\"table" + e.resultsDivId() + "\"><tr><td>\n" +  
	    "<div class=\"chart\" id=\"chart" + e.resultsDivId() + "\">" +
	    htmlRectangle(e.score, largest) + "</div>\n</td>\n" +
            "<td style=\"width:100%\">" +
	    "<div class=\"result\" id=\"" + e.resultsDivId() + "\">\n" +
	    "<div class=\"document\">\n" +
            "<table style=\"width:100%\"><tr><td>\n" +
	    "<a name=\""+ aName +"\" title=\""+ rt +
	    "\" onclick=\""  + js + "\">\n" +
	    e.i + ". " + e.titline + "</a></td></tr>\n" +
	    "<tr><td colspan=2>" +
	    abbreviateAuthline(e.authline)+ " &mdash; "+abbreviateSubj(e.subj)+
	    "<tr><td>" + judgmentBarHTML_sb(e) +
	    "</td></tr>" +
	    "</td></tr>\n" +
            "</table>\n";
	    //researcherSpan(rt, sd.researcherSB)+  	    "<br>\n" +

	s += 
	    (!e.ourCommline.equals("") ? "<tr><td colspan=2><strong>"  + e.ourCommline + "</strong></td></tr>\n" : "") +
	    
	    "</div>\n" +
	    "</div>\n" +
            "</td>\n" +
	    "</tr>\n" + 
	    "</table>\n";
	return s;
    }

    /** The bar with rating buttons, to be inserted after each
	suggested article. David can incorporate this method into
	resultsDivHTMLLite(e)
     */
   private String judgmentBarHTML_sb(ArticleEntry e) {
       return RatingButton.judgmentBarHTML( cp, e, User.Program.SB_ANON,
					     0, asrc);
    } 
  

    /** Creates an HTML element that shows as a rectangle of specified length */
    static String htmlRectangle(double score, double largest) {
	final int h = 8;
	final int M = 67;
	int w = (int)((M *  score) / largest);
	if (w < 0) w = 0;
	if (w > M) w = M;
	
	int w2 = M -w;
	return
	     
	    // empty box (for an offset)
	    "<div style=\"width:"+w2 +"px;height:"+h+"px;margin-top:2px;margin-right:3px;\"></div>" +
	    // solid box
	    "<div style=\"width:"+w+"px;height:"+h+"px;border:1px solid #FF0000; background-color:#FF0000;margin-left:" + w2 + "px;\"></div>";

    }


    /** A,B,C,D ---&gt;  A,D,...,D */
    private static String abbreviateAuthline(String auth) {
	String[] q = auth.split(",");
	return (q.length<=3) ? auth :
	    q[0] + ", " + q[1] +",... ," + q[q.length-1];
    }

    private static String abbreviateSubj(String subj) {
	String[] q = subj.split(" ");
	return (q.length<=3) ? subj :    q[0] + " " + q[1] +" ...";
    }


}
