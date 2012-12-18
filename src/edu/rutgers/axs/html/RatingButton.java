package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.sql.*;

/** Generating rating buttons (and accompanying buttons) that go under
    each article's info in various article lists (such as the search
    results list in search.jsp, or recommendation list in index.jsp).
    One must include scripts/buttons_control.js into each JSP file
    that uses this class. 

    <p>
    Button list, labels, and functionality updated on 2012-10-17, as per
    an email exchange with Paul Kantor and Thorsten Joachims.
 */
public class RatingButton //implements Cloneable //extends HTML
{
   
    static final Action.Op[] ops = {
	/** Types for SetBased recommender (
	Action.Op.INTERESTING_AND_NEW,
				    //Action.Op.INTERESTING_BUT_SEEN_TODAY,
		       Action.Op.INTERESTING_BUT_KNOWN,
		       Action.Op.USELESS;
		       /** For Exploration Engine v4 */
	//	Action.Op.EE4.INTERESTING;
};

    Action.Op op;
    String text, descr, imgSrc;
    String checkedText;

    /** Clicking on the button will cause the article entry removed from the display */
    boolean willRemove=false;
    
    private RatingButton(Action.Op _op, String _text, String _descr, String _imgSrc) {
	this(_op,_text,  _descr, _imgSrc, false);
    }
    private RatingButton(Action.Op _op, String _text, String _descr, String _imgSrc,
			 boolean _willRemove) {
	op = _op;
	checkedText = text= _text;
	descr= _descr;
	imgSrc= _imgSrc;
	willRemove = _willRemove;
    }

    RatingButton setCheckedText(String x) {checkedText =x; return this;}

    /** The full panel of rating buttons, used under each article entry
     with the SetBased recommender. */
    private
	static final RatingButton[] allRatingButtons = {
	new RatingButton(Action.Op.INTERESTING_AND_NEW,
			 "Interesting and new",
			 "Mark this document as interesting, relevant, and new.",
			 "page_up.png"),
	//	new RatingButton(Action.Op.INTERESTING_BUT_SEEN_TODAY,
	//			 "Interesting, but seen today",
	//			 "Mark this if you have already seen a similar interesting and relevant document during this search session.",
	//			 "pages.png"),
	new RatingButton(Action.Op.INTERESTING_BUT_KNOWN,
			 "Interesting, but known",
			 "Mark this if document is interesting, but contains known information.",
			 "page_ok.png"),
	new RatingButton(Action.Op.USELESS,
			 "Not useful for me",
			 "Mark this document as useles or irrelevant for you.",
			 "page_down.png")
    };


    static final RatingButton[] ee4RatingButtons = {
       	(new RatingButton(Action.Op.INTERESTING_AND_NEW,
			 "Interesting; remove from list",
			 "This document is interesting. It will not show in recommendations in the future.",
			  "page_up.png", true)).
	setCheckedText("(Interesting. Won't show in future recommendations)")
    };

    static private RatingButton[] chooseRatingButtonSet(User.Program program) {
	return program==User.Program.EE4?  allRatingButtons: ee4RatingButtons;
    }

    static public String att(String name, String val) {
	return " " + name + "=\""+val+"\"";
    }

    /**  'style="display:none;"' or an empty string */
    static private String  spanStyle(boolean on) {
	return on? "" : att("style", "display:none;");
    }
    
    /** Generates an HTML "span" element, possibly with a "style"
       attribute making it (initially) invisible.
       @param on If false, a 'style="display: none;"' attribute is
       added, in order to render the span (initially) invisible.
     */
    static private String span(String id, boolean on, String text) {
	String s=  	
	    "<span " + att("id", id) +    spanStyle(on) + ">" +
	    text +
	    "</span>";
	return s;
    }

    /** Generates 2 HTML "span" elements, one of which is (initially) visible
	and the other isn't.	
    */
    static public String twoSpans(String id, boolean on, String texton, String textoff) {
	return 
	    span(id +  "_checked", on, texton) + "\n" +
	    span(id, !on, textoff);
    }
   
    static private String img(String path) {
	return img(path, null);
    }

    /** Generates an HTML "img" element */
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

    /** Replaces spaces with the HTML non-breaking space (&nbsp;) */
    static public String nbsp(String text) {
	return text.replaceAll(" ", "&nbsp;");
    }

    /** The URL (relative to the CP) for recording a judgment (or
	performing another user action) on this doc */
    public static String judge(String cp, String aid, Action.Op op,
			       ActionSource asrc) {
	return cp + "/JudgmentServlet?"+BaseArxivServlet.ID +"=" + aid +
	    "&" +BaseArxivServlet.ACTION+ "=" + op + 
	    asrc.toQueryString();
    }

    /** The following are bit flags from which the "flags" parameter of
	judgmentBarHTML() may be composed. */
    /** If the bit is set, create the "add to my folder" button */
    public static final int NEED_FOLDER=1,
    /** If the bit is set, create the "remove from my folder" button */
	 NEED_RM_FOLDER=2, 
    /** If the bit is set, create the "never show again" button */
	NEED_HIDE=4, 
    /** If the bit is set, initially hide the ratings buttons inside a
	single "Rate" button, which the user will need to expand with
	a separate click */
	FOLD_JB=8,
    /** Add the "please come back" text */
	NEED_COME_BACK_TEXT = 16;

    private static String mkImgPath(String cp, String file) {
	return cp +  "/_technical/images/" + file;
    }

    static private final RatingButton toFolderButton = 
	(new RatingButton( Action.Op.COPY_TO_MY_FOLDER,
			  "Move to my folder",
			  "Move this document to your personal folder. It will not show in recommendations in the future.",
			   "folder_page.png",  true)).
	setCheckedText(	"(In your <a href=\"%s/personal/viewFolder.jsp\">folder</a>)");

    static private final RatingButton toFolderButtonEE4 = 
	(new RatingButton( Action.Op.COPY_TO_MY_FOLDER,
			  "Interesting: move to my folder, remove from list",
			  "This document is interesting. Move this document to your personal folder. It will not show in recommendations in the future.",
			   "folder_page.png", true)).
	setCheckedText(	"(In your <a href=\"%s/personal/viewFolder.jsp\">folder</a>)");

    static private RatingButton chooseToFolderButton(User.Program program) {
	return program==User.Program.EE4?  toFolderButtonEE4 :  toFolderButton;
    }


    static private final RatingButton
	rmFromFolderButton =
	(new RatingButton(  Action.Op.REMOVE_FROM_MY_FOLDER,
			   "Remove from my folder",
			   "Remove this document from your personal folder",
			    "bin.png" )).setCheckedText("(Removed from your folder)"),
	hideButton = 
	new RatingButton( Action.Op.DONT_SHOW_AGAIN,
			  "Not useful, remove",
			  "Permanently remove this document from recommendations and search results",
			  "bin.png" ,  true),

	hideButtonEE4 =
	new RatingButton( Action.Op.DONT_SHOW_AGAIN,
			  "Not interesting; remove from list",
			  "Permanently remove this document from recommendations and search results",
			  "bin.png" ,  true);


    /** Generates the HTML inserted into various pages where articles
	are listed. (E.g. the search results list in search.jsp, or
	the suggestion list in index.jsp) This HTML code includes the
	enclosing DIV element (DIV ...  /DIV), with various rating buttons
	etc. inside it.

	<p>
	Note that the "A" elements contain no "HREF" attribute, not even
	one with the "#" value. This is in order to prevent the page from
	scrolling needlessly when the user clicks on a link.

	@param cp The context path for this application.
	@param flags Controls the appearance of the button set. E.g.  NEED_FOLDER | NEED_HIDE
     */
    static public String judgmentBarHTML(String cp, ArticleEntry e,  User.Program program,
					 int flags, ActionSource asrc) {


	if (program==User.Program.EE4) return judgmentBarHTML_EE4(cp, e, program, flags, asrc);

	RatingButton [] buttons = allRatingButtons;

	String aid = e.id;
	boolean willRate=false;
	String s= "<div class=\"bar_instructions\">\n";

	if ((flags & NEED_FOLDER)!=0) {
	    RatingButton b = chooseToFolderButton(program);
	    String sn = "folder" + e.i;
	    String js = "$.get('" + judge(cp,aid, b.op,asrc)+ "', " +
		"function(data) { flipCheckedOn('#"+sn+"')})";
	    s += twoSpans(sn, e.isInFolder,
			  b.renderCkText(cp),  b.aText(cp,js)) + "\n";		
	}

	if ((flags & NEED_RM_FOLDER)!=0) {
	    RatingButton b = rmFromFolderButton;
	    String sn = "remove" + e.i;
	    String js = "$.get('" + judge(cp,aid, b.op,asrc)+ "', " +
		"function(data) { flipCheckedOn('#"+sn+"')})";
	    s += twoSpans(sn, !e.isInFolder,
			  b.renderCkText(cp),  b.aText(cp,js)) + "\n";			 
	}

	boolean someChecked = false;

	if (buttons!=null && buttons.length>0) {
	    willRate=true;
	    if ((flags & FOLD_JB)!=0) {
		String imgPath =  mkImgPath(cp, "page_question.png" );

		s+= "<a" + 
		    att("id", "rate"+e.i) +
		    att("title", "Rate this document.") +
		    att("onclick",
			"$(this).hide(100); $('#ratings"+e.i+"').show(500);") + ">";
		s += img(imgPath, "Rate this document.");
		s += "&nbsp;Rate</a>\n";		
	    }

	    String spanBody="";
	    for(int j=0; j<buttons.length; j++) {
		RatingButton b = buttons[j];
		boolean checked= (e.latestRating==b.op);
		someChecked = (someChecked || checked);
			
		String js = "$.get('" + judge(cp,aid,b.op,asrc) + "', ratingEntered("+e.i + "," + j + ","+buttons.length+"))";
		
		spanBody +=twoSpans("ratings" +e.i + "_" + j,   checked,
				    b.renderCkText(cp), b.aText(cp, js)) + nbsp("  ");
	    }
	    boolean showJB = (flags & FOLD_JB)==0;
	    s += span("ratings" + e.i, showJB, spanBody) + "\n";
	}

       	if ((flags & NEED_HIDE)!=0) {
	    RatingButton b=hideButton;
	    String js=b.hideArticleJs(cp,  e,  asrc);
	    s += b.aText(cp,  js);
	}

	// we probably don't need this text inside a ViewFolder screen
	if (willRate && !someChecked && (flags&NEED_COME_BACK_TEXT)!=0) s+=comeBackMsg(e);

	s += "</div>";
	return s;
    }


    static private String comeBackMsg(ArticleEntry e) {
	// font-size:0.7em; 
	return "<p" +		att("id", "advice" + e.i) +
	    att("style", "font-size:0.9em;color:#333333") +
	    ">If you cannot judge until you have seen the document, please come back to this page to provide your valuation.</p>\n";
    }

    static private String judgmentBarHTML_EE4(String cp, ArticleEntry e, User.Program program, 
					 int flags, ActionSource asrc) {
  
	RatingButton [] buttons = ee4RatingButtons;
	String aid = e.id;
	boolean willRate=false;
	String s= "<div class=\"bar_instructions\">\n";

	if ((flags & NEED_FOLDER)!=0) {
	    RatingButton b = chooseToFolderButton(program);
	    String sn = "folder" + e.i;
	    String js = "$.get('" + judge(cp,aid, b.op,asrc)+ "', " +
		"function(data) { flipCheckedOn('#"+sn+"')})";
	    s += twoSpans(sn, e.isInFolder,
			  b.renderCkText(cp),  b.aText(cp,js)) + "\n";		
	}

	if ((flags & NEED_RM_FOLDER)!=0) {
	    RatingButton b = rmFromFolderButton;
	    String sn = "remove" + e.i;
	    String js = "$.get('" + judge(cp,aid, b.op,asrc)+ "', " +
		"function(data) { flipCheckedOn('#"+sn+"')})";
	    s += twoSpans(sn, !e.isInFolder,
			  b.renderCkText(cp), b.aText(cp,js)) + "\n";	 
	}

	
	String spanBody="";
	for(int j=0; j<buttons.length; j++) {
	    RatingButton b = buttons[j];
	    boolean checked= (e.latestRating==b.op);
	    String js = "$.get('" + judge(cp,aid,b.op,asrc) + "', ratingEntered("+e.i + "," + j + ","+buttons.length+"))";

	    spanBody += twoSpans("ratings" +e.i + "_" + j, checked,
				 b.renderCkText(cp), b.aText(cp, js)) + nbsp("  ");

	}
	boolean showJB = (flags & FOLD_JB)==0;
	s += span("ratings" + e.i, showJB, spanBody) + "\n";

       	if ((flags & NEED_HIDE)!=0) {
	    RatingButton b=	hideButtonEE4;
	    String js=b.hideArticleJs(cp,  e,  asrc);
	    s += b.aText(cp,  js);
	}

	s += "</div>";
	return s;
    }

    private String hideArticleJs(String cp, ArticleEntry e,  ActionSource  asrc) {
	String js="$.get('"+
	    judge(cp,e.id, op ,asrc)+"', " + 
	    "function(data) { $('#result"+e.i+"').hide();})";
	return js;
    }

    /** The HTML snippet (probably, an A element) that renders the button in its
	original (unchecked) state
     */
    private String aText(String cp, String js) {
	String imgPath= mkImgPath(cp, imgSrc);
	return "<a " + att("title", descr) +
	    att("onclick", js) + ">" +
	    img(imgPath, descr) + 	"&nbsp;" + nbsp(text) + "</a>" + 	"&nbsp;";
    }

   /** The HTML snippet that renders the button in its checked state. The checkedText
       may contain a '%s', which would be replaced with cp.
     */
     private String renderCkText(String cp) {
	String imgPath= mkImgPath(cp, imgSrc);
	String s = checkedText.replace("%s", cp); 
	return img(imgPath) +"&nbsp;" + strong(nbsp(s));
    }


    private String buttonCode(String cp, ArticleEntry e,  ActionSource asrc, int j, int setSize) {
	boolean checked= (e.latestRating==op);
	//	someChecked = (someChecked || checked);
	String img= mkImgPath(cp, imgSrc);

	String aid = e.id;

	String js = "$.get('" + judge(cp,aid,op,asrc) + "', ratingEntered("+e.i + "," + j + ","+setSize+"))";
		
	String q=twoSpans("ratings" +e.i + "_" + j, 
			  checked,
			  img( imgSrc ) + strong(text),
			  "<a " + att("title", descr) +
			  att("onclick", js) + ">" +
			  img(imgSrc, descr) + 	"&nbsp;" + nbsp(text) + "</a>");
	q += "&nbsp;&nbsp;";
	return q;
    }



    static public String js_script(String scriptLocation) {
	return "<script" +
	    att("type", "text/javascript") +
	    att("src", scriptLocation)  +
	    "></script>\n";
    }

}
