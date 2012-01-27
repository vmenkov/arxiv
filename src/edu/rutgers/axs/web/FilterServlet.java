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

    public void init(ServletConfig config)     throws ServletException {
	super.init(config);
    }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);

	String pi=request.getPathInfo();

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

	    // A (partial) entry for the article. Only set in article-wise operations, and only when there is 
	    // a real user logged in. 
	    ArticleEntry  skeletonAE=null;

    
	    SessionData sd =  SessionData.getSessionData(request);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext());
	    String user = sd.getRemoteUser(request);

	    Actionable actionable = new Actionable(pi);
	    
	    // FIXME: need to record the viewing act for all other arxiv.org pages as well
	    if (user!=null &&  actionable.isActionable())  {
		em = sd.getEM();
		em.getTransaction().begin();		
		User u = User.findByName(em, user);
		if (u!=null) {
		    Logging.info("pi="+pi+", recording as " + actionable);
		    u.addAction(actionable.aid, actionable.op);
		    skeletonAE = ArticleEntry.getDummyArticleEntry(actionable.aid, 1);
		    Vector<ArticleEntry> entries= new  Vector<ArticleEntry> ();
		    entries.add(skeletonAE);
		    // Mark pages currently in the user's folder, or rated by the user

		    ArticleEntry.markFolder(entries, u.getFolder());
		    ArticleEntry.markRatings(entries, 
					     u.getActionHashMap(Action.ratingOps));

		    em.persist(u);
		}
		em.getTransaction().commit(); 
		em.close();
	    }


	    // Some pages (such as "view PDF") we won't "filter", but
	    // rather will simply redirect to.  This is per Simeon
	    // Warner's request 2011-12-21, 2012-01-04
	    boolean mustRedirect= actionable.mustRedirect();
	    Logging.info("pi="+pi+", redirect=" + mustRedirect);
	    if (mustRedirect) {
		/*
		  String url = (op==Action.Op.VIEW_ABSTRACT) ?
		  "http://arxiv.org/abs/" + aid :
		  "http://arxiv.org/format/" + aid;
		*/
		String url=ARXIV_BASE +  pi;
		String eurl = response.encodeRedirectURL(url);
		Logging.info("sendRedirect to: " + eurl);
		response.sendRedirect(eurl);
	    } else {
		pullPage(request, response, skeletonAE);
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
	    
	    // List of formats as of Simeon Warner, 2012-01-04
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
    

    /**
       @param ae In article-wise pages, when a user is logged in, this
       should be the information about the currently viewed article.
       Otherwise, this must be null.

       @throws WebException If we have a unique meaningful message
	and don't need to print a stack trace etc to the end user.
     */
    private void pullPage(HttpServletRequest request, HttpServletResponse response, ArticleEntry ae) 
	throws WebException, IOException, java.net.MalformedURLException {
	String pi=request.getPathInfo();
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

	Logging.info("code = " + code +", msg=" + gotResponseMsg);
	LineConverter conv = new LineConverter(request, ae);

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
		willParse = false;
	    }
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
	    while( true ) {
		int n = in.read(buf);
		if (n <= 0) break; // eof
		aout.write(buf, 0, n);
	    }
	} else {
	    LineNumberReader r = 
		new LineNumberReader(cs==null ?
				     new InputStreamReader(in) :
				     new InputStreamReader(in, cs));

	    PrintWriter w = new PrintWriter(aout);
	    for(String s = null; (s=r.readLine())!=null; ) {
		String addBefore = conv.getAddBefore(s);
		if (addBefore!=null) w.println(addBefore);
		String z = conv.convertLine(s, 	    willAddNote);
		w.println(z);
	    }
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
   

      boolean hasForward = false;
      final String XFF="X-Forwarded-For";
      Enumeration reqKeys = areq.getHeaderNames();      
      while( reqKeys.hasMoreElements() ) {
	  String name = (String)(reqKeys.nextElement());
	  String value = areq.getHeader(name);
	  if (name.equals(XFF)) {
	      hasForward = true;
	      if (!remoteUseless)   value += ", " + remoteIP;
	  }
	  aHttpURLConnection.setRequestProperty(name, value);
	  //Logging.info("Copy header: " + name + ": " + value);
      }

      if (!hasForward && !remoteUseless) {
	  aHttpURLConnection.setRequestProperty(XFF, remoteIP );
      }
      
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

    final static String FS =  "/FilterServlet";

    private class LineConverter {
	
	String user;
	final String cp = getContextPath();
	final String fs = cp +  FS;

	final ArticleEntry skeletonAE;

	/**
	   @param _ae In article-wise pages, when a user is logged in,
	   this should be the information about the currently viewed article.
	   Otherwise, this must be null.
	 */
	LineConverter(HttpServletRequest request, ArticleEntry _ae)  {
	    skeletonAE = _ae;
	    try {
		SessionData sd =  SessionData.getSessionData(request);
		user = sd.getRemoteUser(request);
	    } catch(Exception ex) {}
	}


    /**
     Typical HTML elements' attributes that need to be converted:

     Replace:
     link rel="shortcut icon" href="/favicon.ico" type="image/x-icon"
     
     Add ARXIV_BASE host:
     link rel="stylesheet" (etc) ... href="/relative" ...
     img ... src="/relative"


     Rewrite with a link to FilterServlet
     a ... href="/relative"

     Rewrite with a link to FilterServlet, and "hidden" sp:
     form ... (method="post")  action="/relative"
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



	    if (link.equals("/favicon.ico")) {
		return cp+"/filter.ico";
	    } else if (link.startsWith( "http://") ||
		       link.startsWith( "https://") ) {
		// absolute link with a host name
		if (mayRewrite && link.startsWith( ARXIV_BASE )) {
		    // abs link to a rewriteable target on arxiv.org
		    return link.replace( ARXIV_BASE , fs);
		} else {
		    // abs link to eslewhere, or to a non-rewriteable
		    // file on arxiv.org; no change
		    return link;
		}
	    } else if (link.startsWith("/")) {
		// absolute URL on the same host
		if (mayRewrite) {
		    return fs + link;
		} else {
		    return ARXIV_BASE + link;
		}
	    } else {
		// relative URL
		// (converting to ARXIV_BASE in the !mayRewrite case would
		// have been useful, but too much trouble)
		return link;
	    }
	}

	//Pattern pIcon = Pattern.compile("href=\"/favicon.ico\"");
	Pattern pBody = Pattern.compile("<body\\b.*?>");
	
	Pattern pHref = Pattern.compile("(href|action|src)=\"(.*?)\"");

	//if (m.matches()) {
    //	String charsetName = charsetName=m.group(1);

	String convertLine(String s, boolean	    willAddNote) {
	    // <link rel="shortcut icon" href="/favicon.ico" type="image/x-icon" />
	    Matcher m = pHref.matcher(s);

	    StringBuffer sb = new StringBuffer();   
	    while (m.find()) {
		String tag=m.group(1);
		String link=m.group(2);
		String replink = convertLink(link, !tag.equals("src"));
		m.appendReplacement(sb, tag +  "=\""+ replink + "\"");
	    }
	    m.appendTail(sb);
	    s = sb.toString();
	    
	    if (willAddNote) {
		m = pBody.matcher(s);
		if (m.find()) {
		    sb = new StringBuffer();
		    s = sb.toString();
		    m.appendReplacement(sb, m.group(0));
		    String msg = "<div>" + 
			"Please note: You are browsing arxiv.org via My.arXiv, "+
			(user==null? "anonymously" : "as user <em>" + user + "</em>") +
			". You can return to the <a href=\""+cp+"\">My.arXiv main page</a>." + 
			"</div>";

		    sb.append(msg);
		    m.appendTail(sb);
		    s = sb.toString();
		}
	    }
	    return s;
	}

	/** Any additional HTML needs to be inserted before line s? 
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
			(cp, skeletonAE, 
			 RatingButton.allRatingButtons,
			 RatingButton.NEED_FOLDER);
		    return s;
		}
	    }
	    return null;
	}
    }



}
