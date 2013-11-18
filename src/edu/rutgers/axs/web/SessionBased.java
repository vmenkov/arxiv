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
	if 	(sd.sbrg!=null) {
	    sr = sd.sbrg.getSR();
	    infomsg += "<br>" + sd.sbrg.description() + "\n";
	}
	infomsg += "<br>List presented at " + (new Date()) +  "\n";

	
    }

    public String resultsDivHTMLLite(ArticleEntry e) {

	String rt = "[score="+e.score+ "]";
	if (e.researcherCommline!=null && e.researcherCommline.length()>0) {
	    rt += "["+e.researcherCommline+"]";
	}
	rt += " ";

	String aName = "article_" + e.id;

	String s = 
	    "<div class=\"result\" id=\"" + e.resultsDivId() + "\">\n" +
	    "<div class=\"document\">\n" +
	    "<a name=\""+ aName +"\">\n" +
	    e.i + ".</a>" + 
	    researcherSpan(rt, sd.researcherSB)+ 
	    "<a onclick=\"javascript:window.opener.location.href = '"  +urlAbstract(e.id) + "';\">" + 
	    e.idline + "; " +e.formatDate()+"\n" +
	    //	    "<input type=\"button\" value=\"View\" onclick=\"javascript:window.opener.location.href = '"   +urlAbstract(e.id) + "';\">\n" +	
    //"[" + a( urlAbstract(e.id), "View") + "]\n" +
	    //	    "[" + a( urlPDF(e.id), "PDF/PS/etc") + "]\n" +
	    "<br>\n" +
	    e.titline + "</a><br>\n" +
	    e.authline+ "<br>\n" +
	    e.subjline+ "<br>\n";
	    
	s += 
	    (!e.ourCommline.equals("") ? "<strong>"  + e.ourCommline + "</strong><br>" : "") +
	    "</div>\n" +
	    "</div>\n";
	return s;
    }

    


}