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
public class RatingButton {
   
    //List<String> stooges = Arrays.asList("Larry", "Moe", "Curly");

    //    static HashSet<Action.Op> causeHiding = new HashSet<Action.Op>
    //	(Arrays.asList(
					      
    final public Action.Op op;
    String text, descr, imgSrc;
    String checkedText;

    /** All buttons which have this flag on form a "group"; if one of them
	is flipped on, other's will go off. */
    boolean inGroup=true;

    /** Clicking on the button will cause the article entry removed from the display */
    boolean willRemove=false;
    
    private RatingButton(Action.Op _op, String _text, String _descr, String _imgSrc) {
	this(_op,_text,  _descr, _imgSrc, true, false);
    }


    /** Creates a RatingButton object describing the appearance and behavior of
	a button in the rating panel.

	@param op What user action gets recorded in the database when
	the user clicks on this button

	@param  _willRemove  If true, clicking on this button will cause the article entry removed from the display */
 
    private RatingButton(Action.Op _op, String _text, String _descr, String _imgSrc,
			 boolean _inGroup, boolean _willRemove) {
	op = _op;
	checkedText = text= _text;
	descr= _descr;
	imgSrc= _imgSrc;
	inGroup = _inGroup;
	willRemove = _willRemove;
    }

    RatingButton setCheckedText(String x) {checkedText =x; return this;}

    /** The full panel of rating buttons, used under each article entry
     with the SetBased recommender. */
    private static final RatingButton[] allRatingButtons = {
	(new RatingButton( Action.Op.COPY_TO_MY_FOLDER,
			  "Copy to my folder",
			  "Copy this document to your personal folder.",
			   "folder_page.png",  false, false)).
	setCheckedText(	"(In your <a href=\"%s/personal/viewFolder.jsp\">folder</a>)"),	

	(new RatingButton(  Action.Op.REMOVE_FROM_MY_FOLDER,
			   "Remove from my folder",
			   "Remove this document from your personal folder",
			    "bin.png", false, true)).setCheckedText("(Removed from your folder)"),

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
			 "page_down.png"),

	new RatingButton( Action.Op.DONT_SHOW_AGAIN,
			  "Not useful, remove",
			  "Permanently remove this document from recommendations and search results",
			  "bin.png" ,  false, true)
    };


    static final RatingButton[] ee4RatingButtons = {
	(new RatingButton( Action.Op.MOVE_TO_MY_FOLDER,
			  "Interesting: move to my folder, remove from list",
			  "This document is interesting. Move this document to your personal folder. It will not show in recommendations in the future.",
			   "folder_page.png", false, true)).
	setCheckedText(	"(In your <a href=\"%s/personal/viewFolder.jsp\">folder</a>)"),

	(new RatingButton(  Action.Op.REMOVE_FROM_MY_FOLDER,
			   "Not interesting; remove",
			   "This document is not interesting. Remove it from your personal folder. It will not show in recommendations in the future.",
			    "bin.png", false, true)).setCheckedText("(Removed from your folder)"),

       	(new RatingButton(Action.Op.INTERESTING_AND_NEW,
			 "Interesting; remove from list",
			 "This document is interesting. It will not show in recommendations in the future.",
			  "page_up.png", false, true)).
	setCheckedText("(Interesting. Won't show in future recommendations)"),

	new RatingButton( Action.Op.DONT_SHOW_AGAIN,
			  "Not interesting; remove from list",
			  "Permanently remove this document from recommendations and search results",
			  "bin.png" ,  false, true).
	setCheckedText("(Not interesting. Won't show in future recommendations)")

    };

    /** The list of buttons shown to anonymous users in the SBRL panel
     */
    static final RatingButton[] sbAnonRatingButtons = {
	new RatingButton(Action.Op.INTERESTING_BUT_KNOWN,
			 "I have seen it already",
			  "I am aware of this article already; remove this document from the recommendation list",
			 "page_ok.png",  false, true),
	new RatingButton( Action.Op.DONT_SHOW_AGAIN,
			  "Not useful, remove",
			  "This suggestion is not useful; remove this document from the recommendation list",
			  "bin.png" ,  false, true)
    };

    /** Selects an appropriate list of buttons to display depending on the
	experiment plan the user is enrolled in.
	@param program The code for the experiment plan
     */
    static public RatingButton[] chooseRatingButtonSet(User.Program program) {
	return 
	    program==User.Program.SB_ANON?  sbAnonRatingButtons:
	    program==User.Program.EE4?   ee4RatingButtons: 
	    allRatingButtons;
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

    public static String judgeNoSrc(String cp, String aid, Action.Op op) {
	return cp + "/JudgmentServlet?"+BaseArxivServlet.ID +"=" + aid;
    }


    /** The URL (relative to the CP) for recording a judgment (or
	performing another user action) on this doc */
    public static String judge(String cp, String aid, Action.Op op,
			       ActionSource asrc) {
	return judgeNoSrc(cp,aid,op) +
	    "&" +BaseArxivServlet.ACTION+ "=" + op + asrc.toQueryString();
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

 
    /** The ID of the HTML SPAN element for this button. (This is
     relied upon in buttons_control.js!) */
    public String sn( ArticleEntry e) {
	return "ratings"+e.i+"_"+op.ordinal();
    }

    /** Should this button be displayed in this particular case? Some
	buttons are skipped unless requested by specific flags. 
     */
    private boolean isAllowed(User.Program program, int flags) {
	// Special case 1: in EE4's "View Folder", we only have 1 button
	if (program==User.Program.EE4 && (flags & NEED_RM_FOLDER)!=0) {
	    return op==Action.Op.REMOVE_FROM_MY_FOLDER;
	}
	// Special case 2: the SB button list is short, and nothing
	// needs to be skipped
	if(program==User.Program.SB_ANON) {
	    return true;
	}
	if ((flags & NEED_FOLDER)==0 && op.isToFolder()||
	    (flags & NEED_RM_FOLDER)==0 && op==Action.Op.REMOVE_FROM_MY_FOLDER||
	    (flags & NEED_HIDE)==0  && op==Action.Op.DONT_SHOW_AGAIN)
	    return false;
	return true;
    }

   
      /** Generates the HTML code which will be inserted into various
	pages where articles are listed. (E.g. the search results list
	in search.jsp, or the suggestion list in index.jsp) This HTML
	code includes the enclosing DIV element (DIV ...  /DIV), with
	various rating buttons etc. inside it.

	<p>
	Note that the "A" elements contain no "HREF" attribute, not even
	one with the "#" value. This is in order to prevent the page from
	scrolling needlessly when the user clicks on a link.

	@param cp The context path for this application. This can be obtained from the HTTP request received by the servlet of JSP page.
	@param e The article for which the buttons will be generated.
	@param program This describes the experiment plan into which the user is enrolled, which in its turn controls the set of buttons she should be shown
	@param flags Customizes the appearance of the button set (presence or absence of some "optional" buttons) for a particular environment. E.g.  NEED_FOLDER | NEED_HIDE. You can pass 0 for the default set.
	@param asrc This describes the context in which the buttons are presented, so that it can be properly logged on the server when a button is clicked on.
     */
    static public String judgmentBarHTML(String cp, ArticleEntry e,  User.Program program,
					 int flags, ActionSource asrc) {

	//	if (program==User.Program.EE4) return judgmentBarHTML_EE4(cp, e, program, flags, asrc);

	RatingButton [] buttons =chooseRatingButtonSet(program);

	String aid = e.id;
	boolean willRate=false;
	String s= "<div class=\"bar_instructions\">\n";

	boolean someChecked = false;

	for(int j=0; j<buttons.length; j++) {
	    RatingButton b = buttons[j];
	    if (!b.isAllowed(program,flags)) continue; 
	    //As of Version 481 this line is causing problems for Session Based browsing. Doesn't insert the DONT SHOW AGAIN button correctly. 

	    boolean checked= e.buttonShouldBeChecked(b.op);
	    if (b.inGroup) {
		willRate=true;
		someChecked = (someChecked || checked);
	    }
	    String sn = b.sn(e);
	    
	    String afterJS = b.inGroup? "ratingEntered("+e.i+ ", '" +sn +"');":
		"flipCheckedOn('#"+sn+"');";
	    if (b.willRemove) afterJS += e.hideJS();
	    if (program == User.Program.SB_ANON) afterJS += "$('#table"+ e.resultsDivId() +  "').hide();";

	    afterJS += " eval(data);";

	    String js = "$.get('" + judge(cp,aid,b.op,asrc) + "',  " +
		"function(data) { " + afterJS + "})";
	    
	    s +=twoSpans(sn, checked,
			 b.renderCkText(cp), b.aText(cp, js));
	    s += nbsp("  \n");
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


    static public String js_script(String scriptLocation) {
	return "<script" +
	    att("type", "text/javascript") +
	    att("src", scriptLocation)  +
	    "></script>\n";
    }

    /** Generates JS code to be inserted into each HTML page's HEAD
	element. In this case, it is an array of ordinals for actions
	whose buttons/texts may need to be "flipped" sometimes.
     */
    static public String headJS(User.Program program) {
	RatingButton[] buttons = chooseRatingButtonSet(program);
	String s= "";
	int cnt=0;
	
	for(RatingButton b: buttons) {
	    if (!b.inGroup) continue;
	    if (s.length()>0)  s += ", ";
	    s += b.op.ordinal();
	}
	s ="hideables=["+s+ "];\n";
	return s;	
    }

}
