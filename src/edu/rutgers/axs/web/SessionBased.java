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

    <p>An instance of this class is created in sessionBased.jsp
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

    public int maxAge=0;

    private long  plid=0;

    /** Retrieves the currently available session-based rec list, and
	sets various values that are needed to display it correctly */
    public SessionBased(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	
	// turn SB on in case this has not been done yet (which could happen
	// if the user has opened the SB popup via the speical button
	// on the main screen, instead of the normal SB URL)
	try {
	    sd.sbrg.turnSBOn(this); 
	} catch(WebException ex) {} // no exceptions are expected here

	popout = getBoolean("popout", true);

	sr = null;
	if (sd.sbrg!=null) {
	    synchronized(sd.sbrg) { // make sure the SR list and PLID match
		// get the pre-computed rec list
		sr = sd.sbrg.getSR();
		infomsg += "<br>" + sd.sbrg.description() + "\n";
		if (sr!=null) {
		    plid = sd.sbrg.getPlid(true);
		    maxAge = sr.getMaxAge();
		} else if (sd.sbrg.hasRunning()) {
		    // tell the browser to come ask again in a few sec
		    wantReload=true;
		}
	    }
	}
	infomsg += "<br>List presented at " + (new Date()) +  "\n";

	// setting this page's "action source" information, which is used
	// to form FilterServlet URLs
	Action.Source srcType =	 Action.Source.SB;
	asrc= new ActionSource(srcType, plid);
	//infomsg += "<br>Session " + sd.getSqlSessionId() +  "\n";
    }

    /** The prefix that is used to form element IDs for draggable elements.
	It is sent back from the client to the server, to be stripped to
	reveal pure article IDs. */
    static final String AID_REORDER_PREFIX = "table";

    /** The prefix used to form URLs for sending to the servers information
	about a user-performed result list reordering. URLs like this can
	be inserted into the SB code, to be activated via jQuery once the user
	moves links around.
     */
    public  String urlReorderPrefix() {
	return RatingButton.judgePrefix(cp,  Action.Op.REORDER,  asrc,
					AID_REORDER_PREFIX);
    }
        
    /** Creates a snippet  of HTML describing one article entry. 

	<ul>
	<li> title 
	<li> date (as per Oct 2014 meeting) 
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
	if (maxAge>0) {
	    rt += "[age=" + e.age + "/" + maxAge + "]";
	}
	rt += " ";

	String aName = "article_" + e.id;

	// The JavaScript snippet used to load the article in question
	// into the main window. The PLID is included into the URL,
	// to be sent by the client back to CheckSBServlet on the server
	String url = urlAbstract(e.id);

	String js = "javascript:window.opener.location.href='" +url+ "';";

	//When a new article is added to the list, it will be highlighted a different color.
	//This code snipet will determine to highlight the given article
	String colorCode = "#ffffff";
	if(e.age > 3) e.age = 3;

	switch(e.age) {
	case 1: colorCode = "#e0e0e0"; break;
	case 2: colorCode = "#d8d8d8"; break;
	case 3: colorCode = "#d0d0d0"; break;
	default: colorCode = "#ffffff"; break;
	}

	String colorBack =  "<div class=\"result\" id=\"" + e.resultsDivId() + "\" style=\"background-color:" + colorCode + "\">\n"; 
	String divBackColor = "style=\"background-color:" + colorCode + "\"";

	String chartTD = "<td>\n" +  
	    "<div class=\"chart\" id=\"chart" + e.resultsDivId() + "\" " + divBackColor + ">" +
	    htmlRectangle(e.score, largest) + "</div>\n";

	if (sd.sbrg.researcherSB && !e.ourCommline.equals("")) {
	    chartTD += 
		"<br><strong>"+e.ourCommline+"</strong>\n";    
	}
	chartTD += "</td>\n";
     
	String docInfoTD =  "<td style=\"width:100%\">" +
	    colorBack +
	    "<div class=\"document\">\n" +
            "<table style=\"width:100%\"><tr><td>\n" +
	    "<a name=\""+ aName +"\" title=\""+ rt +
	    "\" onclick=\""  + js + "\">\n" +
	    e.i + ". " + e.titline + "</a> ("+
	    e.formatDate() + ")" +
	    "</td></tr>\n" +
	    "<tr><td colspan=2>" +
	    abbreviateAuthline(e.authline)+" &mdash; \n"+
	    abbreviateSubj(e.subj)+"</td>\n" +
	    "<tr><td>" + judgmentBarHTML_sb(e) +
	    "</td></tr>\n" +
	    "</table></div></div></td>\n";

	String resultsTable = 
	    "<table id=\"" + e.resultsTableId() + "\">\n" + 
	    "<tr>\n" + 
	    chartTD +
	    docInfoTD +
	    "</tr>\n" +
	    "</table>\n";



	return resultsTable;
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
