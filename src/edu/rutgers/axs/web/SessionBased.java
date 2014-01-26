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

    public SessionBased(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	sr = null;
	long plid=0;
	if (sd.sbrg!=null) {
	    // get the pre-computed rec list
	    sr = sd.sbrg.getSR();
	    if (sr!=null) {
		infomsg += "<br>" + sd.sbrg.description() + "\n";
		plid = sd.sbrg.getPlid();
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
    public String resultsDivHTMLLite(ArticleEntry e, int i) {

	String rt = "[" + e.idline + "; score="+e.score+ "; "+
	    e.formatDate()+"]";
	if (e.researcherCommline!=null && e.researcherCommline.length()>0) {
	    rt += "["+e.researcherCommline+"]";
	}
	rt += " ";

	String aName = "article_" + e.id;

	String re = new String();

	switch(i) {
	case 0: re = "<a onclick=		\"javascript:window.opener.parent.location.href = '"; break;
	case 1: re = "<a onclick=\"javascript:window.opener.location.href = '"; break;
	default: re = ""; //Shouldn't happen
	}


	String s = 
	    "<div class=\"result\" id=\"" + e.resultsDivId() + "\">\n" +
	    "<div class=\"document\">\n" +
	    "<a name=\""+ aName +"\" title=\""+ rt +
	    "\" onclick=\""  + re + urlAbstract(e.id) + "';"; + "\">\n" +
	    e.i + ". " + e.titline + "</a><br>\n"+
	    abbreviateAuthline(e.authline)+ " &mdash; "+ abbreviateSubj(e.subj)+ "\n";
	    //researcherSpan(rt, sd.researcherSB)+  	    "<br>\n" +

	s += 
	    (!e.ourCommline.equals("") ? "<strong>"  + e.ourCommline + "</strong><br>" : "") +
	    "</div>\n" +
	    "</div>\n";
	return s;
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
