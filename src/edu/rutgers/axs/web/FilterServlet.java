package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import java.net.*;
import java.nio.charset.Charset;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.commons.lang.mutable.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.RatingButton;


 /** Pulls in and serves out a page from arxiv.org

     URLs are: http://my.arxiv.org/arxiv/FilterServlet/XXX[?query_string]

     with XXX is the original URI of a request to arxiv.org. E.g., XXX=
          /find/all/1/all:+reptile/0/1/0/all/0/1  
     (in a properly encoded form),  representing the URL such as 
          http://arxiv.org/find/all/1/all:+reptile/0/1/0/all/0/1

 */
public class FilterServlet extends  BaseArxivServlet  {

    /** May be changed (e.g. to export.arxiv.org) in init(), by means
	of parameters supplied from web.xml     */
    static String ARXIV_BASE = "http://arxiv.org";
    /** This one stays constant.
	FIXME: maybe we can also make it configurable */
    private final static String ARXIV_BASE_PDF = "http://arxiv.org";

    /** Counts 403, 404, etc. errors we have received from arxiv.org */
    static final TreeMap<Integer,MutableInt> errorCodeCount = new TreeMap<Integer,MutableInt>();

    /** Keeps track of arxiv.org server errors */
    private static void incErrorCodeCount(Integer code) {
	synchronized( errorCodeCount) {
	    MutableInt v = errorCodeCount.get(code);
	    if (v == null) {
		errorCodeCount.put(code, new MutableInt(1));
	    } else {
		v.add(1);
	    }
	}
    }

    /** A record used for keeping track of an "access denied" report from ArXiv
     */
    private static class ArxivRejection {
	String url;
	Date time;
	int code;
	ArxivRejection(String _url, Date _time, int _code) {
	    url = _url;
	    time = _time;
	    code = _code;
	}
	public String toString() {
	    return "" + code + " " + time + " " + url;
	}
    }

    private static Vector<ArxivRejection> rejections=new Vector<ArxivRejection>();

    public void init(ServletConfig config)     throws ServletException {
	super.init(config);

	String hostname =  EmailSug.determineHostname();
	
	boolean weAreBlacklisted = !hostname.endsWith("orie.cornell.edu")
	    && !hostname.endsWith("cactuar.scilsnet.rutgers.edu");

	/** Martin H. Lessmeister, 2014-04-17: "Please continue to use
	    arxiv.org instead of export.arxiv.org in the future, since
	    future changes to the export service may make it less
	    browse-friendly."
	 */
	ARXIV_BASE = weAreBlacklisted? "http://dev.arxiv.org" : 
	    	    "http://arxiv.org";
	//	    "http://export.arxiv.org";

	/*
	final String name = "ArxivBaseURL";
	String s  = config.getInitParameter(name);
	if (s!=null && !s.equals("")) {
	    ARXIV_BASE = s;
	    Logging.info("Read property " + name + "=" + 	    ARXIV_BASE);
	} else {
	    Logging.info("No property value found for " + name + "; keep " + 	    ARXIV_BASE);
	}
	*/
    }

    static RunningCount rc = new RunningCount(10*60, 5);

    /** Adjusts the request counter and denies some FilterServer
	requests to prevent denial-of-service attacks against
	arxiv.org.

	<p>
	Simeon Warner, 2014-02-26: "no more than 120 requests per
	minute, and no more than 300 requests in each 10 minute
	interval" would actually be a reasonable starting point.
    */
    public boolean overloadRequestRejected(HttpServletRequest request, HttpServletResponse response) {
	boolean overload = false;
	int n1=0, n10=0;
	synchronized(rc) {
	    n1=rc.recentCount(60);
	    n10=rc.recentCount(10*60);
	    //	    overload =  (n10>300) || (n1>120);
	    overload =  (n10>=300) || (n1>=120);
	    if (!overload) {
		rc.inc();
		return false;
	    }
	}

	rejectedOverloadRequestCnt++;
	String msg = "At the moment, My.ArXiv server load is a bit too high ("+n1+", "+n10+"); please retry a few minutes later";
	try {
	    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, msg);
	} catch (java.io.IOException ex) {}
	return true;    
   }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	if (robotRequestRejected(request,response)) return;
	if (overloadRequestRejected(request,response)) return;
	reinit(request);
	filterServletRequestCnt++;

	/** For handling action source, as applicable. The default source
	    for FilterServlet is FILTER, but it is overridden via the HTTP
	    request if we've come e.g. from a suggestion list of some
	    kind.  

	    Note that in the SB context, asrc contains the
	    PresentedList ID pertaining to the list in SB; this can be
	    used to decide if/when we need to refresh SB.
	*/
	
	ActionSource asrc = new ActionSource(Action.Source.FILTER,0);

	String pi=request.getPathInfo();

	pi = asrc.extractFromFilterServletPathInfo(pi);

	EntityManager em = null;
	try {

	    if (pi==null) throw new WebException(HttpURLConnection.HTTP_BAD_REQUEST, "No page specified");

	    /* Get the referer (if supplied by the HTTP request header). It
	       will be null if the header isn't there */
	    
	    //String referer =  request.getHeader("Referer");

	    // if ( request.getMethod().equals("GET")) {....}

	    /** This will be set if the page in question performs some special
		article-wise operations */
	    //Action.Op op = Action.Op.NONE;

	    // A (partial) entry for the article. Only set in
	    // article-wise operations, and only when there is a real
	    // user logged in.
	    ArticleEntry  skeletonAE=null;

    
	    SessionData sd =  SessionData.getSessionData(request);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext());
	    String user = sd.getRemoteUser(request);

	    Actionable actionable = new Actionable(pi);
	    
	    // should (some) pages linked from here inherit the action source?
	    asrc.filterServletMustInheritSrc =
		(asrc.src != Action.Source.FILTER &&
		 asrc.src != Action.Source.UNKNOWN) &&
		actionable.isActionable();

	    User u=null;
	    // FIXME: need to record the viewing act for all other arxiv.org pages as well
	    if (actionable.isActionable())  {
		em = sd.getEM();
		em.getTransaction().begin();		
		u = (user!=null) ? User.findByName(em, user) : null;

		Logging.info("FS: pi="+pi+", recording as " + actionable);
		Action a = sd.addNewAction(em, u, actionable.op, 
					   actionable.aid, null, asrc);

		if (u!=null) {
		    skeletonAE = ArticleEntry.getDummyArticleEntry(actionable.aid, 1);
		    Vector<ArticleEntry> entries= new  Vector<ArticleEntry> ();
		    entries.add(skeletonAE);
		    // Mark pages currently in the user's folder, or rated by the user
		    ArticleEntry.markFolder(entries, u.getFolder());
		    ArticleEntry.markRatings(entries, 
					     u.getActionHashMap(Action.ratingOps));
		}
		//em.persist(u);

		em.getTransaction().commit(); 
		em.close();

		// Now SB is enabled for logged-in users too, not only for anon
		// users. (2014-05-20)
		sd.sbrg.sbCheck();
	    }


	    // Some pages (such as "view PDF") we won't "filter", but
	    // rather will simply redirect to (so that the user's
	    // browser will send a new request directly to the
	    // arxiv.org server).  This is per Simeon Warner's
	    // request 2011-12-21, 2012-01-04
	    boolean mustRedirect= actionable.mustRedirect();
	    Logging.info("pi="+pi+", redirect=" + mustRedirect);
	    if (mustRedirect) {
		/*
		  String url = (op==Action.Op.VIEW_ABSTRACT) ?
		  "http://arxiv.org/abs/" + aid :
		  "http://arxiv.org/format/" + aid;
		*/
		String url=ARXIV_BASE_PDF +  pi;
		String eurl = response.encodeRedirectURL(url);
		Logging.info("sendRedirect to: " + eurl);
		response.sendRedirect(eurl);
	    } else {
		// get the page from the arxiv.org server, modify, and serve
		pullPage(request, response, u, pi, asrc, skeletonAE);
	    }


	} catch (WebException e) {
	    try { // Here the message is already localized and obvious
		response.sendError(e.getCode(), e.getMessage());
	    } catch(IOException ex) {};
	} catch (Exception e) { // Here, need more debugging info
	    try {
		e.printStackTrace(System.out);
		String msg = "error in FilterServer: " + e;
		StringWriter w = new StringWriter();
		e.printStackTrace(new PrintWriter(w));
		msg += ";\n" + w; // debug
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
				   msg);
	    } catch(IOException ex) {};
	} finally {
	    ResultsBase.ensureClosed( em, false);
	}
    }

    /** Information about whether the currently viewed URL is one of
	those "view abstract", "view pdf" etc.  pages.
     */
    static private class Actionable {
	String aid=null;
	Action.Op op = Action.Op.NONE;

	private static Pattern p = Pattern.compile( "/(abs|format|pdf|ps|html|dvi|e-print|src|PS_cache)/(.+)");

	Actionable(String pi) {
	    Matcher m = p.matcher(pi);
	    aid = null;
	    if (!m.matches()) return;
	    String prefix = m.group(1), idv = m.group(2), version=null;
	    Matcher mv = Pattern.compile("(.+)v(\\d+)").matcher(idv);
	    if (mv.matches()) {
		aid = mv.group(1);
		version = mv.group(2);
	    } else {
		aid = idv;
	    }
	    
	    // List of formats as per Simeon Warner, 2012-01-04
	    if (prefix.equals("abs")) op = Action.Op.VIEW_ABSTRACT;
	    else if (prefix.equals("format")) op=Action.Op.VIEW_FORMATS;
	    else if (prefix.equals("pdf")) op= Action.Op.VIEW_PDF;
	    else if (prefix.equals("ps"))  op= Action.Op.VIEW_PS;
	    else if (prefix.equals("html"))  op= Action.Op.VIEW_HTML;
	    else if (prefix.equals("dvi") ||
		     prefix.equals("e-print")||
		     prefix.equals("src")||
		     prefix.equals("PS_cache"))  op= Action.Op.VIEW_OTHER;

	}

	boolean isActionable() { return op != Action.Op.NONE; }
	boolean mustRedirect() { return  op.isViewArticleBody(); }
	public String toString() { return "" + op + "("+aid+")";}
    }
    

    /** Retrieves the page from the arxiv.org server, modifies it as
	needed, and serves to our user.

	@param The pre-read user object. It may be null if the user has not
	logged in.

	@param pi PathInfo, extracted from the request, and possibly further
	modified by the caller.

	@param ae In article-wise pages, when a user is logged in, this
       should be the information about the currently viewed article.
       Otherwise, this must be null.

       @throws WebException If we have a unique meaningful message
	and don't need to print a stack trace etc to the end user.
     */
    private void pullPage(HttpServletRequest request, HttpServletResponse response, 
			  User u, String pi, ActionSource asrc, ArticleEntry ae) 
	throws WebException, IOException, java.net.MalformedURLException {

	String qs=request.getQueryString();
	boolean lPost = request.getMethod().equals("POST");
 	if (pi==null)   throw new WebException(HttpURLConnection.HTTP_BAD_REQUEST, "No page specified");

	Logging.info("FilterServlet request for pi=" + pi + ", post=" + lPost);

	String lURLString=ARXIV_BASE +  pi;
	if (qs != null) {
	    lURLString += "?" + qs;
	}
	URL lURL    =new URL( lURLString);
	Logging.info("FilterServlet requesting URL " + lURL);
	HttpURLConnection lURLConnection;
	try {
	    lURLConnection=(HttpURLConnection)lURL.openConnection();	
	}  catch(Exception ex) {
	    String msg= "Failed to open connection to " + lURL;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
	    throw new WebException(msg);
	}

	lURLConnection.setFollowRedirects(false); 

	if (lPost) {
	    lURLConnection.setDoOutput(true);
	    lURLConnection.setRequestMethod("POST");
	}
	copyRequestHeaders( request, lURLConnection);

	try {
	    lURLConnection.connect();
	} catch(Exception ex) {
	    String msg= "Failed to connect to " + lURL;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
	    throw new WebException(msg);
	}
    
	if (lPost) copyRequestBody( request, lURLConnection);

	int code = lURLConnection.getResponseCode();
	String gotResponseMsg = lURLConnection.getResponseMessage();

	Logging.info("code = " + code +", msg=" + gotResponseMsg + "; requested url = " + lURL);
	LineConverter conv = new LineConverter(request, u, ae, asrc);

	boolean willParse=false, willAddNote=false;
	if (code == HttpURLConnection.HTTP_OK) {
	    // Successful connection (code 200). Default response status
	    willParse = true;
	    willAddNote=true;
	} else {  /* Response code was other than OK */
	    // Set the status code for the response. We should use
	    // setStatus and not sendError, because we must use
	    // the document sent from the remote site, not to
	    // generate a document of our own.

	    response.setStatus(code,lURLConnection.getResponseMessage());

	    // for "Not Modified", the document request should be
	    // recorded (even though there is no doc body to parse)

	    //	    willCache = false;
		
	    /** If the response is "moved", make use of it
		to update our database */
	    
	    if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
		code == HttpURLConnection.HTTP_MOVED_PERM ) {
		/** Redirecting detected (code is 301 or 302) */
		String location = lURLConnection.getHeaderField("Location");
		if (location!=null) {
		    response.setHeader("Location",
				       conv.convertLink(location, true)); 
		}
		willParse=true;
	    } else  if (code == HttpURLConnection.HTTP_NOT_FOUND) {
		// 404; there may be error stream 
		willParse=true;
	    } else {
		// 403 (the dreaded "Access denied" etc.
		willParse = false;

		if (rejections.size()<100) {
		    rejections.add(new ArxivRejection(lURLString, new Date(),code));		    
		}
	    }
	    incErrorCodeCount(code);
	    
	}

	String foundCookie = lURLConnection.getHeaderField("Set-Cookie");
	if (foundCookie != null) {
	    Logging.info("FS: remote server does Set-Cookie: " + foundCookie);
	}

	final int ChunkSize = 8192;
	int lContentLength = lURLConnection.getContentLength();
	// e.g.  "Content-Type: text/html; charset=ISO-8859-4"
	String lContentType = lURLConnection.getContentType();
	Logging.info("pi=" + pi + ", content-type=" +  lContentType);
	Charset cs = null;
	if (lContentType!=null) {
	    Pattern p = Pattern.compile("charset=([\\w:\\-\\.]+)");
	    Matcher m = p.matcher(lContentType);
	    if (m.find()) {
		String charsetName = charsetName=m.group(1);
		try {
		    cs = Charset.forName( charsetName);
		    Logging.info("For name " + charsetName+", got charset "+cs);
		} catch(Exception ex) {
		    Logging.info("No charset available for name "+charsetName);
		}
	    }
	    // Make sure we don't try to parse PDF, JPG, etc: only
	    // parse files explicitly declared to contain HTML, and
	    // those without explicit type description.
	    if (lContentType.indexOf("text/html")<0) {
		willParse=false;
	    }
	    response.setContentType( lContentType);
	}


	InputStream is=null;	
	try {
	    is = lURLConnection.getInputStream();
	}  catch(Exception ex) {
	    String msg= "Failed to obtain data from " + lURL;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
	}
	if (is==null) {	// for errors such as 404, we get ErrorStream instead
	    try {
		is = lURLConnection.getErrorStream();
	    }  catch(Exception ex) {
		String msg= "Failed to obtain error stream from " + lURL;
		Logging.error(msg);
		ex.printStackTrace(System.out);
	    }
	}
	if (is==null) {	
	    String msg= "Failed to obtain any data from " + lURL;
	    int xcode = (code==HttpURLConnection.HTTP_OK)? 
		HttpServletResponse.SC_INTERNAL_SERVER_ERROR :  code;
	    throw new WebException(xcode,  msg);
	}

	BufferedInputStream in= new BufferedInputStream(is, ChunkSize);
	OutputStream aout = response.getOutputStream();

	if (!willParse) {
	    // simple bytewise copy
	    if ( lContentLength > 0) {
		response.setContentLength( lContentLength);
	    }
	    int M = 4096;
	    byte [] buf = new byte[ M ];
	    System.out.println("FS: no parsing for "+lURL+". Top:\n[");
	    int loglen = 0;
	    final int MAXLOG = 1024;
	    while( true ) {
		int n = in.read(buf);
		if (n <= 0) break; // eof
		aout.write(buf, 0, n);

		StringBuffer debug=new StringBuffer();
		for(int j = 0; j<n  && loglen < MAXLOG; j++) {
		    debug.append((char)buf[j]);
		    loglen++;
		}
		System.out.println(debug +"...]");
	    }

	 	    
	} else {
	    LineNumberReader r = 
		new LineNumberReader(cs==null ?
				     new InputStreamReader(in) :
				     new InputStreamReader(in, cs));

	    PrintWriter w = new PrintWriter(aout);

	    //System.out.println("FS: do parsing for "+lURL+". Top:\n[");
	    int loglen = 0;
	    final int MAXLOG = 1024;
	    String logInput="", logOutput="";
	    

	    for(String s = null; (s=r.readLine())!=null; ) {
		String addBefore = conv.getAddBefore(s);
		if (addBefore!=null) w.println(addBefore);
		String z = conv.convertLine(s, 	    willAddNote);
		w.println(z);
		if (loglen < MAXLOG) {
		    loglen += z.length();
		    logInput+=s;
		    logOutput+=z;
		}

	    }
	    //System.out.println("input =["+logInput+"...]");
	    //System.out.println("output=["+logOutput+"...]");
	    w.flush();
	}
	in.close();
	aout.close();
    }
  

    /** This function sets the headers of the aHttpURLConnection --
    content length, browser type, cookies, and all -- to look as if
    this connection is coming from our user's browser.  This way the
    remote server will send us the same doc the user would get from it
    directly.
    
    <p>Since 2013-07-07, the accept-encoding header received from the
    user agent is not forwarded to the arxiv.org server. This way we
    obviate the need to handle compressed data (gzip, deflate, etc).

    @param  aHttpURLConnection  HTTP connection whose parameters we're setting

    @param areq The request from the browser whose parameters we're reading
    and using
		
    @param ipOnly If true, only X-Forwarded-For is set (based on the
    incoming request's X-Forwarded-For, if supplied, or on the
    incoming request's own source IP)

    ("127.0.0.1" and "0:0:0:0:0:0:0:1" both mean localhost, so aren't
    forwarded)

    */

    static private void  copyRequestHeaders(HttpServletRequest areq,
					  HttpURLConnection aHttpURLConnection){

      String remoteIP = areq.getRemoteAddr();
      boolean remoteUseless= remoteIP==null || remoteIP.equals("127.0.0.1") || remoteIP.equals("0:0:0:0:0:0:0:1");
   
      final String ACCEPT_ENCODING = "accept-encoding";

      boolean hasForward = false;
      final String XFF="X-Forwarded-For";
      Enumeration reqKeys = areq.getHeaderNames();      
      while( reqKeys.hasMoreElements() ) {
	  String name = (String)(reqKeys.nextElement());
	  String value = areq.getHeader(name);
	  if (name.equals(XFF)) {
	      hasForward = true;
	      if (!remoteUseless)   value += ", " + remoteIP;
	  } else if (name.equalsIgnoreCase(ACCEPT_ENCODING)) {
	      // FIXME: Simply stripping the accept-encoding header
	      // is the easiest way to avoid having to deal with
	      // "gzip" or "deflate"; but it would be more
	      // efficient to build in some support for them...
	      Logging.info("FS: NOT sending the "+name+" header to the arxiv.org server");
	      continue;
	  } else if (name.equalsIgnoreCase("Cookie") ||
		     name.equalsIgnoreCase("Set-Cookie")) {
	      Logging.info("FS: copyRequestHeaders detects: " + name + ": " +value);
	  }
	  aHttpURLConnection.setRequestProperty(name, value);
	  //Logging.info("Copy header: " + name + ": " + value);
	  //	  System.out.println("FS: Copy header: " + name + ": " + value);
      }

      if (!hasForward && !remoteUseless) {
	  aHttpURLConnection.setRequestProperty(XFF, remoteIP );
      }

      // let's belabor the obvious a bit...
      aHttpURLConnection.setRequestProperty(ACCEPT_ENCODING, "identity");
      
      Logging.info("remote IP=" + remoteIP+", useless=" +remoteUseless);

  }

    /*
  static private void  copyResponseHeaders(HttpServletResponse response,
					  HttpURLConnection aHttpURLConnection){

      Enumeration reqKeys = areq.getHeaderNames();
      while( reqKeys.hasMoreElements() ) {
	  String name = (String)(reqKeys.nextElement());
	  String value = areq.getHeader(name);
	  aHttpURLConnection.setRequestProperty(name, value);
      }
  }
    */


    /** The from-client stream should not be closed here, because 
	in LAPS it may cause the to-browser socket closing as wlel! */
    private void copyRequestBody( HttpServletRequest areq, 
				  HttpURLConnection  aHttpURLConnection)
	throws IOException    {
	OutputStream aOS = aHttpURLConnection.getOutputStream();

	int length=areq.getContentLength();
	
	if (length > 0) {
	    InputStream in =areq.getInputStream();
	    byte[] lArrayByte= readFully( in, length);
	    aOS.write( lArrayByte, 0,  lArrayByte.length);
	    //	    in.close(); // DON"T DO THIS!
	}
    }



  /** Repeats the read() call until the requested number of
	bytes has been read, or until the end of stream */
    private static byte[] readFully(InputStream in, int len) 
	throws IOException {
	byte[] buf = new byte[len];
	int pos=0;
	/** The read() call MUST be repeated until -1 is returned.
	    (A single call always may return less data than requested!) */
	while( pos < len) {
	    int state=in.read( buf, pos, len-pos);
	    if (state == -1) break;  // end of input
	    pos += state;
	}
	if (pos < len) {
	    System.err.println("Warning: "+len+" bytes requested, but only "+
			       pos + " read until the end of stream!");
	    byte [] newBuf = new byte[ pos];
	    for(int i=0; i<pos; i++) newBuf[i]=buf[i];
	    return newBuf;
	}
	return buf;
    }

    final public static String FS =  "/FilterServlet";

    /** Picks the right servlet name based on which host we're running
	on.  The two servlets use the same Java class; the difference
	between the two is the name of the arxiv.org host (specified
	in web.xml) they pull pages from. This is necessary because we
	want to use export.arxiv.org on our best hosts, but have to use
	dev.arxiv.org on other hosts (blacklisted by export.arxiv.org)
    */
    /*
    static private String determineFSName() {
	String hostname =  Tools.determineHostname();
	return hostname.endsWith("orie.cornell.edu")? 
	    "/FilterServlet" : "/FilterServletDEV";
    }
    */

    /** An auxiliary class used in modifying HTML code pulled from arxiv.org 
	(primarily, converting links arxiv.org to "involve" our FilterServlet).
     */
    private class LineConverter {
	
	final SessionData sd;
	User user=null;
	final String cp = getContextPath();
	final String fs = cp +  FS;

	final ArticleEntry skeletonAE;

	ActionSource asrc;

	/**
	   @param _ae In article-wise pages, when a user is logged in,
	   this should be the information about the currently viewed article.
	   Otherwise, this must be null.
	 */
	LineConverter(HttpServletRequest request, User _user, ArticleEntry _ae,
		      ActionSource _asrc) throws WebException, IOException {
	    skeletonAE = _ae;
	    asrc = _asrc;
	    user = _user;
	    sd = SessionData.getSessionData(request);
	}


    /** Given a relative or absolute URL found in the processed
	document, this method returns a URL with which it has to be
	replaced. URLs can be found in various parts of the document,
	e.g. in "A HREF=...", "IMG SRC=...", etc. The replacement
	process only affects URLs (absolute or relative) into
	arxiv.org; depending on the context, these URLs may be
	re-written to point to our FilterServlet. Even when this kind
	or re-writing is not needed, relative URLs may need to be
	converted to absolute.

	<p>
     Typical HTML elements' attributes that need to be converted:

     <ol>
     <li>
     Replace:
     link rel="shortcut icon" href="/favicon.ico" type="image/x-icon"
     
     <li>
     Add ARXIV_BASE host:
     link rel="stylesheet" (etc) ... href="/relative" ...
     img ... src="/relative"

     <li>
     Rewrite with a link to FilterServlet
     a ... href="/relative"

     <li>
     Rewrite with a link to FilterServlet, and "hidden" sp:
     form ... (method="post")  action="/relative"
     </ol>

     @param link The URL (absolute or relative) to process
     @return The replacement URL
     
    */    
	String convertLink(String link, boolean mayRewrite) {

	    // some heuristics: never need to proxy some file types
	    if (mayRewrite) {
		if (link.endsWith(".css")||link.endsWith(".js") ||
		    link.endsWith(".jpg")||link.endsWith(".png") ||
		    link.endsWith(".gif")||link.endsWith(".pdf") ||
		    link.endsWith(".ps")|| link.endsWith(".dvi")||
		    link.endsWith(".gz")
		    ) {
		    mayRewrite = false;
		}
	    }

	    String effectiveFs = fs + 
		(asrc.filterServletMustInheritSrc? asrc.toFilterServletString() : "");
				  

	    if (link.equals("/favicon.ico")) {
		return cp+"/filter.ico";
	    } else if (link.startsWith( "http://") ||
		       link.startsWith( "https://") ) {
		// absolute link with a host name
		if (mayRewrite && link.startsWith( ARXIV_BASE )) {
		    // abs link to a rewriteable target on arxiv.org
		    recordLink(link);
		    return link.replace( ARXIV_BASE , effectiveFs);
		} else {
		    // abs link to eslewhere, or to a non-rewriteable
		    // file on arxiv.org; no change
		    return link;
		}
	    } else if (link.startsWith("/")) {
		// absolute URL on the same host
		if (mayRewrite) {
		    recordLink(link);
		    return effectiveFs + link;
		} else {
		    // CSS files, like PDF, should be always read from arxiv.org
		    // (not export.arxiv.org)
		    return ARXIV_BASE_PDF + link;
		}
	    } else {
		// relative URL
		// (converting to ARXIV_BASE in the !mayRewrite case would
		// have been useful, but too much trouble). 
		
		// FIXME: Note that using relative links also causes
		// the automatic (implicit) inheritance of the "action
		// source", when it's supplied. This may perhaps
		// become inappropriate in the case of a long browsing
		// session... but it apparently is not a real issue,
		// as arxiv.org does not seem to actually have
		// relative URLs
		return link;
	    }
	}

	/** This method is applied to detected links to arxiv.org
	    pages.  It checks if the URL is an article abstract URL (
	    e.g., ..../abs/1009.5718, ..../abs/q-bio/0511026 ), and if
	    it is, it records it in the SessionData. This cumulative
	    list of "displayed links" is used (as an exclusion list)
	    when generating Session Based Recommendations.
	 */
	private void recordLink(String link) {
	    final String z= "/abs/";
	    int pos = link.indexOf(z);
	    if (pos>=0) {
		String aid = link.substring(pos + z.length());
		sd.recordLinkedAid(aid);
	    }
	}


	//Pattern pIcon = Pattern.compile("href=\"/favicon.ico\"");
	private final Pattern pBody = Pattern.compile("<body\\b.*?>");
	private final Pattern pEndHead = Pattern.compile("</head>");
	
	private final Pattern pHref = Pattern.compile("(href|action|src)=\"(.*?)\"");

	//if (m.matches()) {
	//	String charsetName = charsetName=m.group(1);

	/** Processes one line of the original HTML file, modifying
	    it so that it can be used in the HTML code served by our
	    server's FilterServlet.

	    <p>
//<li><a href="/arxiv/FilterServlet/pdf/0806.4449v1" accesskey="f">PDF</a></li>

//<li><a href="/pdf/q-bio/0611055v1" accesskey="f">PDF only</a></li>

*/
	String convertLine(String s, boolean	    willAddNote) {
	    // <link rel="shortcut icon" href="/favicon.ico" type="image/x-icon" />
	    Matcher m = pHref.matcher(s);

	    StringBuffer b = new StringBuffer();   
	    while (m.find()) {
		String tag=m.group(1);
		String link=m.group(2);
		String replink = convertLink(link, !tag.equals("src"));
		String replacement =  tag +  "=\""+ replink + "\"";
		if ((link.startsWith("/pdf/") ||link.startsWith("/ps/")) &&
		    (tag.equals("href") ||tag.equals("action"))) {
		    // Paul's request, 2012-04-22
		    replacement += " target=\"_blank\"";
		}
		m.appendReplacement(b, replacement);
	    }
	    m.appendTail(b);
	    s = b.toString();
	    
	    // Display an additional DIV right after the opening
	    // <body ...> element 
	    if (willAddNote) {
		m = pBody.matcher(s);
		if (m.find()) {
		    b = new StringBuffer();
		    s = b.toString();		    
		    m.appendReplacement(b, m.group(0));
		    String msg = "<div style=\"border:1px;color:#00FF00;position:fixed\">" + 
			"Please note: You are now browsing arxiv.org via My.arXiv, "+
			(user==null? "anonymously" : "as user <em>" +  user.getUser_name() + "</em>") +
			". You can return to the <a href=\""+cp+"\">My.arXiv main page</a>." + 
			"</div>";

		    b.append(msg);
		    m.appendTail(b);
		    s = b.toString();
		}
	    }

	    // Insert JS for the Moving Panel inside the HEAD element if needed.
	    // The code will cause the SB pop-up window to open, or its content
	    // to be updated.
	    if (sd.sbrg.getNeedSBNow()) {
		m = pEndHead.matcher(s);
		if (m.find()) {
		    s = s.substring(0,m.start()) + "\n" +
			mkSBJS() +
			s.substring(m.start());
		}
	    }

	    return s;
	}


	/** Wraps the specified JS code into a no-argument function,
	    and associates that function with a window.onload handler;
	    wraps everything into a SCRIPT element.
	 */
	private /* static*/ String wrapJSForOnload(String js0) {
	    final String f ="myWindowOnloadFunction";
	    String js = 
		"function "+f+"() { "+js0+" }\n" +
		"window.onload="+f+";";
	    return 
		RatingButton.js_snippet(js);

	}

	/** Generates SCRIPT elements with the JavaScript needed for
	    the opening or reloading of the SB pop-up window.  */
	private String mkSBJS() {
	    String js = CheckSBServlet.mkJS(cp, sd.sbrg);
	    return
		RatingButton.js_script(cp+"/scripts/filterServletSB.js")+
		wrapJSForOnload(js);
	}

	/** This was used before the introduction of timeout-check-eval
	    loop in August 2014. Superseded by mkSBJS().  */
	private String mkSBJS_orig() {
	    String js = 
		"openSBMovingPanel('"+cp+"', 1500);";
	    return 
		RatingButton.js_script(cp+"/scripts/filterServletSB.js") +
		wrapJSForOnload(js);
	}

	
	/** Any additional HTML needs to be inserted before the specified
	    line of HTML? 
	    E.g., in FilterServlet/abs/1110.3154 we'd insert rating
	    buttons for the article 1110.3154.
	*/
	String getAddBefore( String nextLine) {
	    if (skeletonAE != null) {
		// Judgment buttons in each "View abstract" pages 
		if (nextLine.indexOf("<div id=\"footer\">") >= 0) {
		    String s = "\n";
		    final String[] scripts = {
			"_technical/scripts/jquery.js",
			"_technical/scripts/jquery-transitions.js",
			"scripts/buttons_control.js"
		    };
		    for(String x: scripts) {
			s += RatingButton.js_script(cp + "/" + x);
		    }
		    s += RatingButton.judgmentBarHTML
			(cp, skeletonAE, user.getProgram(),
			 //RatingButton.allRatingButtons,
			 RatingButton.NEED_FOLDER, asrc);
		    return s;
		}
	    }
	    return null;
	}
    }


    static String report() {
	if (filterServletRequestCnt == 0) {
	    return "FilterServlet has not made any page requests to the ArXiv server yet";
	}
	String q ="\nFilterServlet requests involve page retrieval from the ArXiv server at " + FilterServlet.ARXIV_BASE + "\n";

	int sum = 0;
	StringBuffer b = new 	StringBuffer();
	for(Integer code:  errorCodeCount.keySet()) {
	    int cnt = errorCodeCount.get(code).intValue();
	    b.append("Error code "+code+ " : " + cnt +"\n");
	    sum += cnt;
	}
	if (rejections.size()>0) {
	    b.append("\nArXiv error log:\n");
	    for(ArxivRejection r: rejections) {
		b.append(r + "\n");
	    }
	}

	if (b.length() == 0) {
	    return q + "No errors have been reported from the ArXiv server\n";
	} else {
	    return q + sum + " errors have been reported when accessing the ArXiv server, as follows:\n" + b;
	}
    
    }
}
