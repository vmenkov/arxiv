package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

/*
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
*/

import edu.rutgers.axs.web.*;
//import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** Generating rating buttons (and accompanying buttons) that go under
    each article's info in various article lists (such as in search.jsp).
    One must include scripts/buttons_control.js into the JSP file that
    uses this class.
*/
public class RatingButton {
   
    static final Action.Op[] ops = {Action.Op.INTERESTING_AND_NEW,
		       Action.Op.INTERESTING_BUT_SEEN_TODAY,
		       Action.Op.INTERESTING_BUT_KNOWN,
		       Action.Op.USELESS};

    Action.Op op;
    String text, descr, imgSrc;
    
    RatingButton(Action.Op _op, String _text, String _descr, String _imgSrc) {
	op = _op;
	text= _text;
	descr= _descr;
	imgSrc= _imgSrc;
    }

    /** The full panel of rating buttons, used in search.jsp */
    public static final RatingButton[] allRatingButtons = {
	new RatingButton(Action.Op.INTERESTING_AND_NEW,
			 "Interesting and new",
			 "Mark this document as interesting, relevant, and new.",
			 "page_up.png"),
	new RatingButton(Action.Op.INTERESTING_BUT_SEEN_TODAY,
			 "Interesting, but seen today",
			 "Mark this if you have already seen a similar interesting and relevant document during this search session.",
			 "pages.png"),
	new RatingButton(Action.Op.INTERESTING_BUT_KNOWN,
			 "Interesting, but known",
			 "Mark this if document is interesting, but contains known information.",
			 "page_ok.png"),
	new RatingButton(Action.Op.USELESS,
			 "Useless",
			 "Mark this document as useles or irrelevant for you.",
			 "page_down.png")
    };


    static private String att(String name, String val) {
	return " " + name + "=\""+val+"\"";
    }

    /**  'style="display: none;"' or an empty string */
    static private String  spanStyle(boolean on) {
	return on? "" : att("style", "display: none;");
    }

    static private String span(String id, boolean on, String text) {
	String s=  	
	    "<span " + att("id", id) +    spanStyle(on) + ">" +
	    text +
	    "</span>";
	return s;
    }

    static private String twoSpans(String id, boolean on, String texton, String textoff) {
	return 
	    span(id +  "_checked", on, texton) + "\n" +
	    span(id, !on, textoff);
    }
   
    static private String img(String path) {
	return img(path, null);
    }

    static private String img(String path, String longdesc) {
	String s ="<img" +
	    att("src", path) +
	    att("class", "icon_instruction");
	if (longdesc!=null) {
	    s += att("longdesc", longdesc);
	}
	s += ">";		
	return s;
    }

    static private String strong(String text) {
	return "<strong>" + text +  "</strong>";
    }

    static private String nbsp(String text) {
	return text.replaceAll(" ", "&nbsp;");
    }

    /** The URL (relative to the CP) for recording a judgment on this doc */
    private static String judge(String cp, String aid, Action.Op op) {
	return cp + "/JudgmentServlet?"+BaseArxivServlet.ID +"=" + aid +
	    "&" +BaseArxivServlet.ACTION+ "=" + op;
    }

    public static final int NEED_FOLDER=1, NEED_HIDE=2;


    /** Generates the HTML inserted into various pages where articles
	are listed. Includes the enclosing DIV ,,, /DIV.

	Note that the "A" elements contain no "HREF" attribute, not even
	one with the "#" value. This is in order to prevent the page from
	scrolling needlessly when the user clicks on a link.
     */
    static public String judgmentBarHTML(String cp, ArticleEntry e,
					 RatingButton [] buttons,
					 int flags) {
	final String imgDir= cp + "/_technical/images/";
	String aid = e.id;

	String s="";
	s += "<div class=\"bar_instructions\">\n";

	if ((flags & NEED_FOLDER)!=0) {
	    String sn = "folder" + e.i;
	    String imgPath = imgDir + "folder_page.png";
	    String js = "$.get('" + judge(cp,aid, Action.Op.COPY_TO_MY_FOLDER)+ "', " +
		"function(data) { flipCheckedOn('#"+sn+"')})";
	    String title="Copy this document to your personal folder";
	    s += twoSpans(sn, e.isInFolder,
			  img(imgPath) + 
			  strong("(In your <a href=\""+cp+"/personal/viewFolder.jsp\">folder</a>)"),
			  "<a class=\"add\" " +
			  att("title", title) +
			  att( "onclick", js) + ">" +
			  img(imgPath) + nbsp("Copy to my folder") +
			  "</a>" +"&nbsp;&nbsp;") + "\n";
	}


	if (buttons!=null && buttons.length>0) {
	    s+= "<a id=\"rate"+e.i+"\"  title=\"Rate this document.\""+
		att("onclick",
		    "$(this).hide(100); $('#ratings"+e.i+"').show(500);") +
		">";
	    s += img(imgDir + "page_question.png", "Rate this document.");
	    s += "&nbsp;Rate</a>\n";		


	    String spanBody="";
	    for(int j=0; j<buttons.length; j++) {
		RatingButton b = buttons[j];
		boolean checked= (e.latestRating==b.op);
		String src= imgDir + b.imgSrc;
		String text="&nbsp;" + nbsp(b.text);
		
		String js = "$.get('" + judge(cp,aid,b.op) + "', ratingEntered("+e.i + "," + j + ","+buttons.length+"))";
		
		String q=twoSpans("ratings" +e.i + "_" + j, 
				  checked,
				  img( src ) + strong(text),
				  "<a " + att("title", b.descr) +
				  att("onclick", js) + ">" +
				  img(src, b.descr) + text + "</a>");
		q += "&nbsp;&nbsp;";
		spanBody += q;
	    }
	    s += span("ratings" + e.i, false, spanBody) + "\n";
	}

       	if ((flags & NEED_HIDE)!=0) {

	    String js="$.get('"+judge(cp,aid,Action.Op.DONT_SHOW_AGAIN)+"', " + 
	    "function(data) { $('#result"+e.i+"').hide();} )";
	    String title="Permanently remove this document from the search results";
	    s += "<a class=\"remove\" " + att("id", "remove"+e.i) +
		att("title", title) +
		att("onclick", js) + ">" +
		img( imgDir + "bin.png" , title) +
		nbsp( "Don't show again") + "</a>&nbsp;&nbsp\n";
	}
	s += "</div>";
	return s;
    }


}



