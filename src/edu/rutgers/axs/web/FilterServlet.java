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

	//String sp = request.getParameter(SP);
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
		
		// FIXME: need to record the viewing act

		//u.addAction(id, op);
		
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

	boolean willParse=false;
	if (code == HttpURLConnection.HTTP_OK) {
	    // Successful connection (code 200). Default response status
	    willParse = true;
	} else {  /* Response code was other than OK */
	    // Set the status code for the response. We should use
	    // setStatus and not sendError, because we must use
	    // the document sent from the remote site, not to
	    // generate a document of our own.

	    response.setStatus(code,lURLConnection.getResponseMessage());
	    willParse = false;
	    // for "Not Modified", the document request should be
	    // recorded (even though there is no doc body to parse)

	    //	    willCache = false;
		
	    /** If the response is "moved", make use of it
		to update our database */
	    
	    if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
		code == HttpURLConnection.HTTP_MOVED_PERM ) {
		/** Redirecting detected (code is 301 or 302) */	
	    }
	}

	final int ChunkSize = 8192;
	int lContentLength = lURLConnection.getContentLength();
	// e.g.  "Content-Type: text/html; charset=ISO-8859-4"
	String lContentType = lURLConnection.getContentType();
	Charset cs = null;
	if (lContentType!=null) {
	    Pattern p = Pattern.compile("charset=([\\w:\\-\\.]+)");
	    Matcher m = p.matcher(lContentType);
	    if (m.matches()) {
		String charsetName = charsetName=m.group(1);
		try {
		    cs = Charset.forName( charsetName);
		    Logging.info("For name " + charsetName+", got charset "+cs);
		} catch(Exception ex) {
		    Logging.info("No charset available for name "+charsetName);
		}
	    }
	}

	BufferedInputStream in = new BufferedInputStream( lURLConnection.getInputStream(),
							  ChunkSize);

	response.setContentType( lContentType);
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
	    String s = null;
	    while((s=r.readLine())!=null) {
		String z = convertLine(s);
		w.print(z);
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

    /** Returns a URL for this servlet */
    //static String mkUrl(String cp, String sp) {
    //	return cp + "/FilterServlet?" +  SP +"="+sp;
    //}


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

    Pattern pIcon = Pattern.compile("href=\"/favicon.ico\"");

    Pattern pHref = Pattern.compile("href=\"(.*?)\"");
    //if (m.matches()) {
    //	String charsetName = charsetName=m.group(1);

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

    private String convertLine(String s) {
	// <link rel="shortcut icon" href="/favicon.ico" type="image/x-icon" />
	Matcher m = pHref.matcher(s);
	String fs = getContextPath()+ "/FilterServlet";
	StringBuffer sb = new StringBuffer();

	while (m.find()) {
	    String link=m.group(1);
	    String replink = link;
	    if (link.equals("/favicon.ico")) {
		replink = getContextPath()+"/filter.ico";
	    } else if (link.startsWith( ARXIV_BASE )) {
		replink = link.replace( ARXIV_BASE , fs);
	    } else if (link.startsWith( "http://")) {
		// abs link to eslewhere, no change
		replink = link;
	    } else {
		// relative link
		replink = fs + link;
	    }

	    m.appendReplacement(sb, "href=\""+ replink + "\"");
	}
	m.appendTail(sb);
	s = sb.toString();
	return s;
    }


}
