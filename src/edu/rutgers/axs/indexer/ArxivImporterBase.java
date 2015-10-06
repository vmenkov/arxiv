package edu.rutgers.axs.indexer;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.*;
import java.net.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.Logging;

/** Common methods for ArxivImporter and ArxivToCsv */
class ArxivImporterBase {
    static class Tags {
	final static String RECORD = "record", HEADER="header", METADATA="metadata", IDENTIFIER = "identifier";
    }

    /** No more than one option, -Dfrom=YYYY-MM-DD or -Ddays=n, can be
	supplied. If -Dfrom is supplied, uses it (and ignores the
	-Ddays option, if any); otherwise, looks for -Ddays and
	createS an equivalent "from" value. The assumption is that
	we're in the same timezone with the OAI2 server...

	@return The value of the -Dfrom=YYYY-MM-DD, or its equivalent
	computed from -Ddays=nnn (as today-days). Null if neither
	option is supplied.*/
    static String getFrom(ParseConfig ht) {
	final String FROM="from", DAYS="days";
	String from=ht.getOption(FROM, null);
	if (ht.getOption(DAYS,null)==null) return from;
	if (from!=null) {
	    throw new IllegalArgumentException("Can't supply -Dfrom and -Ddays simultaneously!");
	}
	int days=ht.getOption(DAYS, 0);
	if (days<=0)  throw new IllegalArgumentException("-Ddays=nnn, if supplied, must be positive!");
	Date d = plusDays(new Date(), -days);
	final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
	from=fmt.format( d );
	Logging.info("Converted days=" + days + " to from=" + from);
	return from;
    }

    static String getUntil(ParseConfig ht) {
	final String UNTIL="until";
	String until=ht.getOption(UNTIL, null);
	return until;
    }

    /** As per http://www.openarchives.org/OAI/2.0/openarchivesprotocol.htm

http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:0901.4014
    */
    static String makeURL(String tok, String from, String until) {
	String base="http://export.arxiv.org/oai2?verb=ListRecords";
	if  (tok!=null) {
 	    return base + "&resumptionToken="+tok;
	}
	String s = base + "&metadataPrefix=arXiv";
	if (from  !=null) s += "&from=" +from;
	if (until !=null) s += "&until="+until;
	return s;
    }

    private static Date lastRequestTime = null;

    /** Reads the content of a URL into an XML element. May make 
	several attempts if necessary.

	@return Content of the requested page parsed into an XML element

	@throws IOException If it could not get the data, even after
	several attempts.
    */
    static Element getPage(String urlString ) throws IOException,  org.xml.sax.SAXException {
	URL url = new URL(urlString);

	// space requests by at least 21 sec (to avoid the "wait" response)
	final long necessaryIntervalMsec = 1000 * 21L;

	HttpURLConnection conn;
	final int maxAttemptCnt = 10;
	int attemptCnt = 0;
	do {
	    Date now = new Date();	    
	    if (lastRequestTime!=null) {
		//		long mustWait = necessaryIntervalMsec - 
		//		    (now.getTime()-lastRequestTime.getTime());
		long mustWait = 0;
		if (mustWait>0) {
		    try {
			System.out.println("sleep "+mustWait+" msec...");    
			Thread.sleep(mustWait);
		    } catch( InterruptedException ex) {}
		    now = new Date();
		}
	    }

	    lastRequestTime = now;
	    conn = (HttpURLConnection)url.openConnection();  
	    conn.setFollowRedirects(true) ;
	    conn.setRequestProperty("User-Agent", "arXiv_xs-Importer/0.1");
	} while( ++attemptCnt < maxAttemptCnt && mustRetry(conn));

	int code = conn.getResponseCode();
 
	if (code!=HttpURLConnection.HTTP_OK ) {
	    System.out.println("got code "+code+", msg="+conn.getResponseMessage() +"; aborting import");
	    throw new IOException("got code "+code + " from "+url );
	}
	BufferedReader in = 
	    new BufferedReader(  new InputStreamReader(conn.getInputStream()));

	Element e = XMLUtil.readElement(in);
	return e;
    }

    /** This    primarily   handles    the   server's    return   code
	HTTP_UNAVAILABLE  (503),  which  comes  with  the  Retry-After
	code. (As per the OAI2 standard, the server may ask us to wait
	and to  retry). In  addition, we also  do a limited  amount of
	retries   for   code   500   (HTTP_INTERNAL_ERROR),   or   for
	java.net.ConnectException     ("Connection     refused")    in
	conn.getResponseCode(), which very occasionally happen too.

	@return true if we think it's a good idea to send the same
	HTTP request one more time

    */
    private static boolean mustRetry(HttpURLConnection conn ) throws IOException {

	boolean refused = false;
	int code = 0;
	Exception savedEx = null;
	try {
	    code = conn.getResponseCode();
	    if (code==HttpURLConnection.HTTP_OK) return false;
	} catch( java.net.ConnectException ex) {
	    //Logging.warning("Caught exception " + ex)
	    savedEx = ex;
	    refused = true;
	} catch(  java.net.SocketException ex) {
	    // Logging.warning("Caught exception " + ex)
	    savedEx = ex;
	    refused = true;
	}

	long msec1= System.currentTimeMillis();
	long msec2= msec1 + 1000 * 120; // default long wait	
	if (refused) {
	    // a very occasional "java.net.ConnectException: Connection refused"
	    // or "java.net.SocketException: Connection reset"
	    System.out.println("Had a 'connection refused' or 'connection reset' (ex="+savedEx+"). Let's retry just in case");
	} else if (code==HttpURLConnection.HTTP_UNAVAILABLE) {
	    // this usually is a typical OAI2 retry message
	    String ra = conn.getHeaderField("Retry-After");
	    System.out.println("got code "+code+", Retry-After="+ra);
	    int padding=5; // pad the interval with so many seconds
	
	    int interval=-1; // in seconds
	    try {
		interval = Integer.parseInt(ra);
	    } catch(Exception ex) {}
	
	    if (interval>=0) {
		msec2=msec1+ 1000 * (interval+padding);
	    } else {
		try {
		    SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		    Date d = format.parse(ra);
		    long q = d.getTime();  
		    if (q > msec1) msec2 = q;
		} catch(Exception ex) {}
	    }
	} else if (code==HttpURLConnection.HTTP_INTERNAL_ERROR) {
	    // Very rarely, HTTP_INTERNAL_ERROR (code 500) is also reported
	    System.out.println("got code "+code+", let's retry just in case");
	} else return false;

	do {
	    try {
		System.out.println("sleep "+(msec2-msec1)+" msec...");    
		Thread.sleep(msec2-msec1);
	    } catch( InterruptedException ex) {}
	    msec1= System.currentTimeMillis();
	} while(msec1 < msec2);
	return true;
    }

    /** Add (or subtract) so many days to the given date */
    static public Date plusDays(Date d, int days) {
	long msec = d.getTime() + days * 24L * 3600L * 1000L;
	return new Date(msec);
    }



}