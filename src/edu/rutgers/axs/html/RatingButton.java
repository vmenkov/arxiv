package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.sql.*;

/** Generating rating buttons (and accompanying buttons) that go under
    each article's info in various article lists (such as in search.jsp).
    One must include scripts/buttons_control.js into the JSP file that
    uses this class.
*/
public class RatingButton //extends HTML
{
   
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

    /**  'style="display:none;"' or an empty string */
    static private String  spanStyle(boolean on) {
	return on? "" : att("style", "display:none;");
    }
    
    /**
       @param on If false,  'style="display: none;"' is used to render the span
       (initially) invisible.
     */
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
    public static String judge(String cp, String aid, Action.Op op) {
	return cp + "/JudgmentServlet?"+BaseArxivServlet.ID +"=" + aid +
	    "&" +BaseArxivServlet.ACTION+ "=" + op;
    }

    /** This are bit flags from which the "flags" parameter of
	judgmentBarHTML() may be composed. */
    /** If the bit is set, create the "add to my folder" button */
    public static final int NEED_FOLDER=1, 
    /** If the bit is set, create the "never show again" button */
	NEED_HIDE=2, 
    /** If the bit is set, initially hide the ratings buttons inside a
	single "Rate" button, which the user will need to expand with
	a separate click */
	FOLD_JB=4;

    /** Generates the HTML inserted into various pages where articles
	are listed. Includes the enclosing DIV element (DIV ...  /DIV).

	Note that the "A" elements contain no "HREF" attribute, not even
	one with the "#" value. This is in order to prevent the page from
	scrolling needlessly when the user clicks on a link.

	@param flags Controls the appearance of the button set. E.g.  NEED_FOLDER | NEED_HIDE
     */
    static public String judgmentBarHTML(String cp, ArticleEntry e,
					 RatingButton [] buttons,
					 int flags) {
	final String imgDir= cp + "/_technical/images/";
	String aid = e.id;
	boolean willRate=false;
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
			  "<a" +
			  att("class", "add") +
			  att("title", title) +
			  att( "onclick", js) + ">" +
			  img(imgPath) + nbsp("Copy to my folder") +
			  "</a>" +"&nbsp;&nbsp;") + "\n";
	}

	boolean someChecked = false;

	if (buttons!=null && buttons.length>0) {
	    willRate=true;
	    if ((flags & FOLD_JB)!=0) {

		s+= "<a" + 
		    att("id", "rate"+e.i) +
		    att("title", "Rate this document.") +
		    att("onclick",
			"$(this).hide(100); $('#ratings"+e.i+"').show(500);") +
		    ">";
		s += img(imgDir + "page_question.png", "Rate this document.");
		s += "&nbsp;Rate</a>\n";		
	    }

	    String spanBody="";
	    for(int j=0; j<buttons.length; j++) {
		RatingButton b = buttons[j];
		boolean checked= (e.latestRating==b.op);
		someChecked = (someChecked || checked);
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
	    boolean showJB = (flags & FOLD_JB)==0;
	    s += span("ratings" + e.i, showJB, spanBody) + "\n";
	}

       	if ((flags & NEED_HIDE)!=0) {

	    String js="$.get('"+judge(cp,aid,Action.Op.DONT_SHOW_AGAIN)+"', " + 
	    "function(data) { $('#result"+e.i+"').hide();} )";
	    String title="Permanently remove this document from the search results";
	    s += "<a " + att("class", "remove") + att("id", "remove"+e.i) +
		att("title", title) +
		att("onclick", js) + ">" +
		img( imgDir + "bin.png" , title) +
		nbsp( "Don't show again") + "</a>&nbsp;&nbsp\n";
	}

	if (willRate && !someChecked) {

	    // font-size:0.7em; 

	    s += "<p" +		att("id", "advice" + e.i) +
		att("style", "font-size:0.9em;color:#333333") +
		">If you cannot judge until you have seen the document, please come back to this page to provide your valuation.</p>\n";

	}

	s += "</div>";
	return s;
    }


    static public String js_script(String scriptLocation) {
	return "<script" +
	    att("type", "text/javascript") +
	    att("src", scriptLocation)  +
	    "></script>\n";
    }

}
