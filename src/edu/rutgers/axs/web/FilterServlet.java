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


 /** Pulls in and serves out a page from arxiv.org

     URLs are: http://my.arxiv.org/arxiv/FilterServlet/XXX[?query_string]

     with XXX is the original URI of a request to arxiv.org. E.g., XXX=
          /find/all/1/all:+reptile/0/1/0/all/0/1  
     (in a properly encoded form),  representing the URL such as 
          http://arxiv.org/find/all/1/all:+reptile/0/1/0/all/0/1

 */
public class FilterServlet extends  BaseArxivServlet  {

    /** Artcile ID, in the format used arxiv.org */
    //final static public String SP="sp";

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);

	String pi=request.getPathInfo();

	EntityManager em = null;
	try {

	    if (pi==null)   throw new WebException("No page specified");

	    /* Get the referer (if supplied by the HTTP request header). It
	       will be null if the header isn't there */
	    
	    //String referer =  request.getHeader("Referer");

	    // if ( request.getMethod().equals("GET")) {....}
    
	    SessionData sd =  SessionData.getSessionData(request);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext());
	    String user = sd.getRemoteUser(request);

	    if (user!=null) {
		em = sd.getEM();
		// Begin a new local transaction so that we can persist a new entity
		em.getTransaction().begin();
		
		User u = User.findByName(em, user);
		
		Pattern p = Pattern.compile( "/(abs|format|pdf|ps)/(.+)");

		Matcher m = p.matcher(pi);
		if (m.matches()) {
		    String prefix = m.group(1);
		    String idv = m.group(2);
		    Pattern pv = Pattern.compile("(.+)v(\\d+)");
		    Matcher mv = pv.matcher(idv);
		    String id = idv, version = null;
		    if (mv.matches()) {
			id = mv.group(1);
			version = mv.group(2);
		    }

		    Action.Op op = Action.Op.NONE;
		    if (prefix.equals("abs")) op = Action.Op.VIEW_ABSTRACT;
		    else if (prefix.equals("format")) op=Action.Op.VIEW_FORMATS;
		    else if (prefix.equals("pdf")) op= Action.Op.VIEW_PDF;
		    else if (prefix.equals("ps")) op= Action.Op.VIEW_PS;
		    Logging.info("pi="+pi+", recordable as mode " + op.toString());
		    if (op !=  Action.Op.NONE) u.addAction(id, op);
		} else {
		    Logging.info("pi="+pi+", not a recordable URL");
		}

		// FIXME: need to record the viewing act for all other pages as well


		em.persist(u);
		em.getTransaction().commit(); 
		em.close();
	    }

	    pullPage(request, response);

	    /*
	    String url = (op==Action.Op.VIEW_ABSTRACT) ?
		"http://arxiv.org/abs/" + id :
		"http://arxiv.org/format/" + id;
	    
	    String eurl = response.encodeRedirectURL(url);
	    response.sendRedirect(eurl);
	    */
	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in FilterServer: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	    ResultsBase.ensureClosed( em, false);
	}

    }

    
    private void pullPage(HttpServletRequest request, HttpServletResponse response) 
	throws WebException, IOException, java.net.MalformedURLException {
	String pi=request.getPathInfo();
	String qs=request.getQueryString();
	boolean lPost = request.getMethod().equals("POST");
 	if (pi==null)   throw new WebException("No page specified");
	Logging.info("FilterServlet request for pi=" + pi + ", post=" + lPost);

	String lURLString=ARXIV_BASE +  pi;
	if (qs != null) {
	    lURLString += "?" + qs;
	}
	URL lURL    =new URL( lURLString);
	Logging.info("FilterServlet requesting URL " + lURL);
	HttpURLConnection lURLConnection=(HttpURLConnection)lURL.openConnection();	

	lURLConnection.setFollowRedirects(false); 

	if (lPost) {
	    lURLConnection.setDoOutput(true);
	    lURLConnection.setRequestMethod("POST");
	}
	copyRequestHeaders( request, lURLConnection);

	//System.out.println("Trying connect");
	lURLConnection.connect();
	//System.out.println("connect OK");
	    
	if (lPost) copyRequestBody( request, lURLConnection);

	int code = lURLConnection.getResponseCode();
	Logging.info("code = " + code);
	LineConverter conv = new LineConverter(request);

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
	    // Make sure we don't try to parse PDF, JPG, etc -
	    // only parse files explicitly declared to contain HTML, and those
	    // without explicit type description
	    if (lContentType.indexOf("text/html")<0) {
		willParse=false;
	    }
	    response.setContentType( lContentType);
	}

	BufferedInputStream in = new BufferedInputStream( lURLConnection.getInputStream(),
							  ChunkSize);

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

    */

  static private void  copyRequestHeaders(HttpServletRequest areq,
					  HttpURLConnection aHttpURLConnection){

      Enumeration reqKeys = areq.getHeaderNames();
      while( reqKeys.hasMoreElements() ) {
	  String name = (String)(reqKeys.nextElement());
	  String value = areq.getHeader(name);
	  aHttpURLConnection.setRequestProperty(name, value);
      }
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
	String cp = getContextPath();
	String fs = cp +  FS;

	LineConverter(HttpServletRequest request) {
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
		    link.endsWith(".ps")
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
		// (convertibg to ARXIV_BASE in the !mayRewrite case would
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
    }

}
