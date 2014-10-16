package edu.rutgers.axs.upload;


import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.regex.*;
//import javax.persistence.*;


import javax.servlet.*;
import javax.servlet.http.*;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
//import edu.rutgers.axs.html.*;
import edu.rutgers.axs.upload.*;
//import edu.rutgers.axs.indexer.Common;

public class UploadProcessingThread extends Thread {
    
    private final String user;
    private HTMLParser.Outliner outliner = null;

    /** When the list generation started and ended. We keep this 
     for statistics. */
    Date startTime, endTime;
    
    boolean error = false;
    String errmsg = "";

    String statusText = "";
    int pdfCnt = 0;

    public UploadProcessingThread(String _user, HTMLParser.Outliner _outliner) {
	user = _user;
	outliner = _outliner;
    }

    /** The main class for the actual recommendation list
	generation. */
    public void run()  {
	startTime = new Date();
	try {
	    if (outliner!=null) {
		HashSet<URL> doneLinks = new 	HashSet<URL>();
		for(URL url: outliner.getLinks()) {
		    if (doneLinks.contains(url)) continue;		    
		    doneLinks.add(url);
		    Vector<DataFile> results = pullPage(user, url, true);
		    pdfCnt += results.size();
		}
	    }
	} catch(Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    System.out.println("Exception for UploadProcessingThread " + getId());
	    ex.printStackTrace(System.out);
	} finally {
	}

    }


    /** Saves the data from an input stream in a PDF file.
     */

    static public DataFile savePdf(String user, InputStream uploadedStream, String fileName ) 
    throws IOException {
	final int L = 32 * 1024;

	BufferedInputStream bis = new BufferedInputStream(uploadedStream, L);
	
	DataFile df = new DataFile(user, DataFile.Type.UPLOAD_PDF, fileName);
	
	File file = df.getFile();
	File dir = file.getParentFile();
	if (dir.exists()) {
	    if (!dir.isDirectory() || !dir.canWrite()) {
		throw new IOException("Server error: Cannot receive files, because " + dir + " already exists, but is not a directory, or is not writeable!");
	    }
	} else {
	    if (!dir.mkdirs()) {
		throw new IOException("Server error: Cannot receive files: failed to create directory " + dir);
	    }
	    Logging.info("Created directory " + dir);
	}

	FileOutputStream fos = new FileOutputStream(file);
	BufferedOutputStream bos=new BufferedOutputStream(fos,L);
	
	int n=0;
	byte[] data = new byte[L];
	while((n=bis.read(data))>0) {
	    bos.write(data, 0, n);
	}
	bis.close();
	bos.close();
	
	return df;
    }

    static public Vector<DataFile> pullPage(String user, URL lURL, boolean pdfOnly) 
	throws //WebException,
	    IOException, java.net.MalformedURLException {

	Vector<DataFile> results = new Vector<DataFile>();
	Logging.info("UploadPapers requesting URL " + lURL);
	HttpURLConnection lURLConnection;
	try {
	    lURLConnection=(HttpURLConnection)lURL.openConnection();	
	}  catch(Exception ex) {
	    String msg= "Failed to open connection to " + lURL;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
	    throw new IOException(msg);
	}

	lURLConnection.setInstanceFollowRedirects(true); 

	try {
	    lURLConnection.connect();
	} catch(Exception ex) {
	    String msg= "UP: Failed to connect to " + lURL;
	    Logging.error(msg);
	    throw new IOException(msg);
	}

	int code = lURLConnection.getResponseCode();
	String gotResponseMsg = lURLConnection.getResponseMessage();

	Logging.info("code = " + code +", msg=" + gotResponseMsg + "; requested url = " + lURL);

	if (code != HttpURLConnection.HTTP_OK) {
	    String msg= "UploadPapers: Error code " + code + " received when trying to retrieve page from " + lURL;
	    Logging.error(msg);
	    throw new IOException(msg);	    
	}

	final int ChunkSize = 8192;
	int lContentLength = lURLConnection.getContentLength();
	String lContentType = lURLConnection.getContentType();
	Logging.info("content-type=" +  lContentType);

	// effective URL (after any redirect)
	URL eURL = lURLConnection.getURL();

	boolean expectPdf = checkContentType( eURL, lContentType);
	Charset cs = getCharset(lContentType);
	String fileName = getRecommendedFileName(//lURL,
						 lURLConnection);

	InputStream is=null;	
	try {
	    is = lURLConnection.getInputStream();
	}  catch(Exception ex) {
	    String msg= "UploadPapers: Failed to obtain data from " + lURL;
	    Logging.error(msg);
	    //	    ex.printStackTrace(System.out);
	    int xcode = (code==HttpURLConnection.HTTP_OK)? 
		HttpServletResponse.SC_INTERNAL_SERVER_ERROR :  code;
	    throw new //WebException(xcode, msg);
		IOException( msg);
	}

	if (expectPdf) {
	    // simple bytewise copy
	    DataFile df = UploadProcessingThread.savePdf(user, is, fileName);
	    if (df != null) {
		results.add(df);
		//		infomsg +="<p>Successfully uploaded PDF file '"+ 		    fileName+"'</p>";
	    }
	} else if (pdfOnly) {
	    return results;
	    //throw new IOException("Not a PDF file: " + lURL);
	} else {
	    BufferedInputStream in= new BufferedInputStream(is, ChunkSize);

	}
	return results;

    }

    /** Checks the content type to figure whether this is a PDF document
	or not. ("Not PDF" is expected to be HTML)
	
	// e.g.  "Content-Type: text/html; charset=ISO-8859-4"

	@return Updated value of expectPdf */
    static private boolean checkContentType(URL eURL, String lContentType) {
	String lURLString = eURL.toString();
	boolean expectPdf =  lURLString.toLowerCase().endsWith(".pdf");

	if ( lContentType==null) return expectPdf;
	String ct = lContentType.toLowerCase();
	if ( ct.endsWith("pdf")) {
	    if (!expectPdf) {
		String msg = "UploadPage: Although URL '"+lURLString+"' does not end in '.pdf', the server specified content type "  + lContentType + "; accordingly, process as PDF";
		Logging.info(msg);
	    }
	    expectPdf = true;
	} else if (ct.startsWith("text/html")) {
	    if (expectPdf) {
		String msg = "UploadPage: Although URL '"+lURLString+"' ends in '.pdf', the server specified content type "  + lContentType + "; accordingly, process as HTML";
		Logging.info(msg);
	    }
	    expectPdf = false;
	}
	return expectPdf;
    }

    /** Gets the charset from the content type, whenever supplied. (E.g. 
	"Content-Type: text/html; charset=ISO-8859-4"
    */
    static private Charset getCharset(String lContentType) {
	Charset cs = null;
	if (lContentType!=null) {
	    Pattern p = Pattern.compile("charset=([\\w:\\-\\.]+)");
	    Matcher m = p.matcher(lContentType);
	    if (m.find()) {
		String charsetName = m.group(1);
		try {
		    cs = Charset.forName( charsetName);
		    Logging.info("For name " + charsetName+", got charset "+cs);
		} catch(Exception ex) {
		    Logging.info("No charset available for name "+charsetName);
		}
	    }
	}
	return cs;
    }

    /** Figures out the file name under which we should save the PDF
	file retrieved over an HTTP connection. This is typically
	based on the file name present in the URL; but we also take a
	look at the "Content-Disposition" header which we may receive
	in the HTTP response.

	<p>
	See advice on Content-Disposition at 
	 http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
	 <p>Example:  <br>
	 Content-Disposition: attachment; filename="fname.ext"

	 // FIXME: We let URLConnection follow redirects; so how 
	 // do we handle changed location?
     */
    static private String getRecommendedFileName(//URL lURL, 
					  HttpURLConnection lURLConnection) {
	String f = null;
	URL eURL = lURLConnection.getURL();
	String path = eURL.getPath();
	String q[] = path.split("/");
	if (q.length > 0) {
	    int j=q.length-1;
	    if (q[j].equals("")) j--;
	    if (j>=0) f = q[j];
	}
	Logging.info("UploadPapers: set file='"+f+"' based on url=" + eURL);
	String dispo  = lURLConnection.getHeaderField("Content-Disposition");
	if (dispo==null) return f;

	Pattern p1 = Pattern.compile("filename=\"(.*?)\"");
	Pattern p2 = Pattern.compile("filename=(.+)");
	Matcher m = p1.matcher(dispo);
	if (!m.find()) m = p2.matcher(dispo.trim());
	if (m.find()) {
	    f = m.group(1);
	    Logging.info("UploadPapers: set file='"+f+"' based on Content-Disposition: " + dispo);
	    // FIXME: any encoding/special char escaping used?
	}
	
	return f;
    }


}
