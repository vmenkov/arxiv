package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;

import java.lang.reflect.*;
import javax.persistence.*;

import javax.servlet.*;
import javax.servlet.http.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.RatingButton;
import edu.rutgers.axs.html.QS;


public class ResultsBase {

    HttpServletRequest request;

    /** Will be set to true if an error happened */
    public boolean error = false;
    /** The JSP page should print this out if error==true */
    public String errmsg="[No message]";
    /** The JSP page may print this out if it is not null. */
    public Exception e=null;

    /** The JSP page should always print this message. Most often
        it is just an empty string, anyway; but it may be used
        for debugging and status messages. */
    public String infomsg = "";

    /** The "conext" part of our URL. It can be used by JSP pages to
     build correct links (without using lots of ".." etc). The value
     could be e.g. "/arxiv" or "". */
    // final
    public  String cp;

    /** All the data that are meant to be persistent between requests
     * in the same session */
    public SessionData sd;

    /** User name logged in this session */
    public String user=null;

    final public static String USER_NAME = "user_name",
	FORCE="force", FILE="file", ID="id";

    /** These two vars are here (and not e.g. in PersonalResultsBase)
	because they are also used by Search (which may or may not be
	invoked anonymously). Of course, the values are only set in some
	situations.
    */
    public ActionSource asrc = new ActionSource(Action.Source.UNKNOWN,0);

    /** This is an empty method in this class, but it will be overridden
	in some pages.
	
	FIXME: customizeSrc() is not of much use in Search and ViewSuggestions;
	there, asrc is set directly in the code. This really ought to be
	refactored.
    */
    void customizeSrc() {}


    /** Returns the user object for the currently logged-in user */
    public User getUserEntry() {
	return sd.getUserEntry(user);
    }

    /** Cached bits reflecting the roles of the user who has requested
	this page */
    private boolean roleBitsSet = false,
	isRunByAdmin=false, isRunByResearcher=false;
    
    private void checkRoleBits() {
	if (roleBitsSet) return;
	// the null situation in an offline (command-line) run
 	User u = (sd!=null? getUserEntry() : null);
	if (u!=null) {
	    isRunByAdmin = u.isAdmin();
	    isRunByResearcher = u.isResearcher();
	}
	roleBitsSet = true;
   }


    

    /** Is this command run by an admin-level user? */
    public boolean runByAdmin() {
	checkRoleBits();
	return isRunByAdmin;
    }

    /** Is this command run by a researcher-level user? */
    public boolean runByResearcher() {
	checkRoleBits();
	return isRunByResearcher;
    }

    public String userAgent=null;

    /**
       @param response If null, don't bother with checking.
     */
    public ResultsBase(HttpServletRequest _request, HttpServletResponse response) {
	request = _request;
	cp = request.getContextPath(); 

	String foundCookie = request.getHeader("Cookie");
	if (foundCookie!=null) {
	    infomsg+= "Cookie: " + foundCookie + "<br>\n" ;
	    Logging.info("Cookie: " + foundCookie);
	}

	try {
	    userAgent = request.getHeader("User-Agent");
	    infomsg+= "User-Agent: " + userAgent + "<br>\n";
	    infomsg+= "<br>Plain params:<br>\n";
	    for(Enumeration en=request.getParameterNames(); en.hasMoreElements();){
		String name = (String)en.nextElement();
		infomsg += name + "=" + request.getParameter(name) + "<br>";
	    }	    
	    sd = SessionData.getSessionData(request);	  
	    //Logging.info("obtained sd=" + sd);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext() );
	    user = sd.getRemoteUser(request);

	    if(!sd.isAuthorized(request,user)) {
		Logging.info("user " + user + " is not authorized to access servlet at " + request.getServletPath());
	
		if (response!=null) {
		
		    String redirect = cp + "/login2.jsp?sp=" +
			URLEncoder.encode( request.getServletPath(), "UTF-8");

		    String qs = request.getQueryString();
		    if (qs != null) redirect += "&qs="+	URLEncoder.encode(qs, "UTF-8");

		    //String eurl = response.encodeRedirectURL(redirect);
		    response.sendRedirect(redirect);

		    /*
	    String redirect = "index.jsp";	    
	    RequestDispatcher dis = request.getRequestDispatcher(redirect);
	    Logging.info("Logout: forward to=" + redirect);	    
	    dis.forward(request, response);
		    */
		}

		error=true;
		errmsg="Not authorized";
		return;
	    }

	    // for the benefit of e.g. Search or ViewSuggestions
	    asrc = new ActionSource(request);

	    // we call customizeSrc now, but some child classes may
	    // call it again later, once they have set their own params
	    customizeSrc(); 

	    // Do we need a "moving panel"? Once requested, it will stay on
	    boolean sb = getBoolean("sb", false);
	    if (sb) {
		sd.allowedSB = true;
		sd.researcherSB = true;
		sd.sbMergeMode = getInt("sbMerge", sd.sbMergeMode);
		sd.validateSbMergeMode();
		sd.sbDebug = getBoolean("sbDebug", sd.sbDebug);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	}	
    }

    /** Gets the integer param with the specified value from the
	request. If no such param is found in the request, returns the
	specified default value.
    */
    public long getLong(String name, long defVal) {
	return Tools.getLong(request, name, defVal);
    }
    public int getInt(String name, int defVal) {
	return (int)Tools.getLong(request, name, defVal);
    }

    public String getString(String name, String defVal) {
	return Tools.getString(request, name, defVal);
    }

    public boolean getBoolean(String name, boolean defVal) {
	return Tools.getBoolean(request, name, defVal);
    }

    public Enum getEnum(Class retType, String name, Enum defVal) {
	return Tools.getEnum(request, retType, name,  defVal);
    }


    void setEx(Exception _e) {
	error = true;
	if (_e instanceof edu.rutgers.axs.sql.IllegalInputException ) {
	    // although a subclass of WebException, it's ok to pass
	    // it: the JSP page should have special treatment
	    e = _e;
	} else if (_e instanceof WebException) {
	    // this is our own exception - we known where it came from,
	    // so no need to print stack etc.
	} else {
	    e = _e;
	}
	errmsg = "Error: " + _e.getMessage();
    }

    /** Returns the exception's stack trace, as a plain-text string 
     */
    public String exceptionTrace() {	
	StringWriter sw = new StringWriter();
	try {
	    if (e==null) return "No exception was caught";
	    e.printStackTrace(new PrintWriter(sw));
	    sw.close();
	} catch (IOException ex){}
	return sw.toString();
    }

    /** FIXME uhem... a silly way to refresh data! */
    /*    public int responseCnt(PhoneCall c) {
//	EntityManager em = sd.getEM();
	try {
//	    c = em.find(PhoneCall.class, c.getId()); // a silly way to re-load, but merge() won't work here
//	    return  c.computeResponseCnt();
	} finally {
	    em.close();
	}
	} */

    /** @return A text message of the form "such-and-such time (3 hours ago)", or "never"
     */
    static public String ago(Date d) {
	return Util.ago(d);
    }

    /** A hygienic way to close a connection, if it is open. This can be put into every "finally" clause. */
    public static void ensureClosed(EntityManager em) {
	ensureClosed( em, true);
    }

    /** A hygienic way to close a connection, if it is open. This can be put into every "finally" clause. */
    public static void ensureClosed(EntityManager em, boolean commit ) {
	if (em==null) return;
	if (!em.isOpen()) return;
	try {
	    if (em.getTransaction().isActive()) {
		if (commit) em.getTransaction().commit();
		else em.getTransaction().rollback();
	    }
	} catch (Exception _e) {}
	try {
	    em.close();
	} catch (Exception _e) {}
    }


    public  String urlAbstract( String id) {
	return ServletConstants.articleServletUrl(cp, id, Action.Op.VIEW_ABSTRACT, asrc);
    }

    public  String urlPDF( String id) {
	return ServletConstants.articleServletUrl(cp, id, Action.Op.VIEW_PDF,asrc);
    }


    /** Generates a "A" HTML element */
    static String a(String url, String body) {
	return  "<a href=\"" + url+ "\">"+body+"</a>";	
    }

    /** Add  RatingButton.NEED_COME_BACK_TEXT to this to provide for a 
	"come back here to judge" text. */
    private static int defaultFlags = RatingButton.NEED_HIDE | RatingButton.NEED_FOLDER;

   /** Generates a "div" HTML element with everything pertaining to
	a single article in a list of articles (e.g., a single search
	result or a single element of a suggestion list).
     */
    public String resultsDivHTML(ArticleEntry e, boolean isSelf ) {
	return resultsDivHTML(e, isSelf, defaultFlags);
    }
    
    public String resultsDivHTML(ArticleEntry e, boolean isSelf, int flags) {

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
	    researcherSpan(rt)+ 
	    e.idline + "; "+e.formatDate()+"\n" +
	    "[" + a( urlAbstract(e.id), "Details") + "]\n" +
	    "[" + a( urlPDF(e.id), "PDF/PS/etc") + "]\n" +
	    "<br>\n" +
	    e.titline + "<br>\n" +
	    e.authline+ "<br>\n" +
	    e.subjline+ "<br>\n";
	    

	// twoSpans(String id, boolean on, String texton, String textoff) 
	String id = "abs" + e.i;
	String title="Expand article details";
	String jsOn = "$.get('" + RatingButton.judge(cp,e.id, Action.Op.EXPAND_ABSTRACT, asrc)+ "', " +
		"function(data) { flipCheckedOn('#"+id+"')})";

	String expandButton =  "<a" +
	    //			  att("class", "add") +
	    RatingButton.att("title", title) +
	    RatingButton.att( "onclick", jsOn) + ">" +
	    "Expand" +	    "</a>&nbsp;&nbsp;";

	String jsOff = "flipCheckedOff('#"+id+"')";

	String collapseButton =  "<a" +
	    //			  att("class", "add") +
	    RatingButton.att("title", "Hide article details") +
	    RatingButton.att( "onclick", jsOff) + ">" +
	    "Collapse" +	    "</a>";

	String expanded = collapseButton + "<br>" +	    
	    (!e.commline.equals("") ? e.commline + "<br>" : "") +
	    "Abstract: " + e.abst + "<br>";

	// "on" means expanded
	s += RatingButton.twoSpans(id, false, expanded, expandButton);

	s += 
	    (!e.ourCommline.equals("") ? "<strong>"  + e.ourCommline + "</strong><br>" : "") +
	    "</div>\n" +
	    (isSelf? judgmentBarHTML(e, flags): "") +
	    "</div>\n";
	return s;
    }

    
    private String judgmentBarHTML(ArticleEntry entry, int flags) {
	return RatingButton.judgmentBarHTML( cp, entry, getUserProgram(),
					     flags, asrc);
    } 

    /** Generates a URL for a page similar to the currently viewed one,
	but showing a different section of the result list.

	<p>Currently used in ViewSuggestions and ViewActions.

	@param startat The value for the "startat" param in the new page's 
	URL.
     */
    public String repageUrl(StartAt startat) {
	return repageUrl( startat, null);
    }

    public String repageUrl(StartAt startat, Action.Op op) {
	String sp = request.getServletPath();
	QS qs = new QS( request.getQueryString());

	ActionSource.stripActionSource(qs);
	if (asrc!=null) qs.append( asrc.toQueryPairs());
	/*
	String rep = STARTAT + "=" + startat;
	Pattern p = Pattern.compile("\\b"+ STARTAT + "=\\d+");
	Matcher m = p.matcher(qs);
	qs = m.find()?  m.replaceAll( rep ) :
	    qs + (qs.length()>0 ?  "&" : "") + rep;
	*/
	startat.insertIntoQS(qs);
	if (op!=null) 	qs.append(BaseArxivServlet.ACTION, op.toString());

	String x = cp + sp + "?" + qs;
	return x;
    }

     /** Formats the specified text as a visible "span" element, or as
	a comment, based on the user's status. */
    public String researcherSpan(String s) {
	return researcherSpan(s,runByResearcher()); 
    }

   /** Formats the specified text as a visible "span" element, or as
	a comment, based on the user's status. */
    static public String researcherSpan(String s, boolean isResearcher) {
	if (isResearcher) {
	    return "<span class=\"researcher\">" + s + "</span>\n";
	} else return "<!-- "+s+"-->\n";
    }

    /** Formats the specified text as a visible "P" element, or as
	a comment, based on the user's status. */
   public String researcherP(String s) {
	if (runByResearcher()) {
	    return "<p class=\"researcher\">" + s + "</p>\n";
	} else return  "<!-- "+s+"-->\n";
    }

    /** The full URL corresponding to the request we're serving now */
    URL thisUrl() throws java.net.MalformedURLException {
	//	Logging.info("this URL is apparently " + s);
	return new URL( request.getScheme(), request.getServerName(),
			request.getServerPort(), request.getRequestURI());	
    }

    public String stackTrace() {
	return stackTrace(e);
    }

    static public String stackTrace(Exception e) {
	if (e==null)   return "";
	StringWriter sw=new StringWriter();
	e.printStackTrace(new PrintWriter(sw));
	return sw.toString();
    }


    /** Checks if a parameter value was empty, or equiavalent to empty */
    static boolean isBlank(String code) {
	return  (code==null || code.equals("") || code.equals("null"));
    }

    /** This is used to properly configure various controls
	(such as rating buttons) which are different in different
	experiments, This method is overidden in PersonalUserBase. */
    User.Program getUserProgram() {
	return null;
    }

    /** JS to be inserted into the HTML doc's head. The method is overridden
	by derived classes as needed. */
    public String headJS() {
	return "";
    }

    /** Used by ViewSuggestions and Search
     */
    String refreshEverythingJsCode() {
	if (user==null) return "";
	if (userAgent.toLowerCase().indexOf("firefox") >= 0) {
	    // Firefox does not need a silly "onload"
	    return "";
	}
	String url = cp + "/JudgmentServlet";
	StringBuffer args = new StringBuffer();
	addToArgs(args, BaseArxivServlet.ACTION, ""+Action.Op.NONE);
	for(String[] p: asrc.toQueryPairs()) {
	    addToArgs(args, p[0], p[1]);
	}
	String js =   " $.post('"+url+"', { "+ args +" }, " +
	    "function(data) { "+
	    //	    "alert('onload got response: ' + data); "+
	    "eval(data);});";
	return js;
    }
  
    /** Formatting arg list for a POST call
     */
    private static void addToArgs(StringBuffer args, String p0, String p1) {
	if (args.length()>0) args.append( ", ");
	args.append( p0 + " : '"+ p1 +"'");
    }

   /** Testing only */
    ResultsBase() { cp = ""; }

    /** This is inserted into BODY ONLOAD="...". Subclasses override
      as needed. 
    */
    public String onLoadJsCode() { return ""; }

    /** An instance of this class holds information on where the next 
	page of results should start
     */
    public static class StartAt {
	static final String STARTAT = "startat", START_ARTICLE="start_article";
	int startat = 0;
	/** The URL of an article which, if found, ought to be the
	    first article appearing on this page. This is meant as an
	    alternative to nextstart, useful in the situations when some
	    articles in the current page are judged as "Don't show again".
	*/
	String startArticle = null;
	
	public StartAt() {
	    reset();
	}

	StartAt(int _startat, String _startArticle) {
	    startat =_startat;
	    startArticle = _startArticle;	    
	}

	StartAt(HttpServletRequest request) {
	    startat = (int)Tools.getLong(request, STARTAT,0);
	    startArticle = Tools.getString(request, START_ARTICLE,null);
	}

	void reset() {
	    startat = 0;
	    startArticle = null;
	}

	/** Produces the StartAt object for the next or previous page.
	    @param n Offset, which may be positive (for the next page)
	    or negative (for the prev page)
	*/
	StartAt offset(int n) {
	    return new StartAt(Math.max(startat+n,0), null);
	}

	/** Modifies a query string so that it will incorporate the info
	    represented by this StartAt object.
	 */
	void insertIntoQS(QS qs) {
	    qs.strip(STARTAT);
	    qs.strip(START_ARTICLE);
	    qs.append( STARTAT, startat);
	    if (startArticle != null) {
		qs.append( START_ARTICLE, startArticle);
	    }
	}

	public String asQS() {
	    QS qs = new QS();
	    insertIntoQS(qs);
	    return qs.toString();
	}

    }

}

