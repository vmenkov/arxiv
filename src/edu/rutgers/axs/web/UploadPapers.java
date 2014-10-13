package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;
import java.nio.charset.Charset;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

//import org.apache.lucene.document.*;
//import org.apache.lucene.index.*;
//import org.apache.lucene.search.IndexSearcher;


import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.disk.*;


import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.indexer.Common;
//import edu.rutgers.axs.recommender.Scheduler;
//import edu.rutgers.axs.recommender.DailyPPP;
//import edu.rutgers.axs.ee4.Daily;

/** The "Toronto system": uploading PDF documents
*/
public class UploadPapers  extends ResultsBase {

    /** How many PDF files have been uploaded on this invocation? */
    public int uploadCnt=0;

    /** An auxiliary class, used in parsing requests sent in "multipart" format
	(data uploads)
    */
    static class UploadingResults {
	//Test q=null;
	//boolean sensorFromTextarea = false;
	//	String sensorFileName = "?";
	//int id = 0;
    }

    /** Reads and parses a sensor description uploaded from the web
     * form (as an uploaded file, or via a TEXTAREA element 
     */
    UploadingResults readUploadedFile() {
	UploadingResults u = new UploadingResults();
	try {

	    final String SENSORDATA = "sensordata";

	    // Create a factory for disk-based file items
	    FileItemFactory factory = new DiskFileItemFactory();
	    
	    // Create a new file upload handler
	    ServletFileUpload upload = new ServletFileUpload(factory);
	    
	    // Parse the request
	    List<FileItem> items = upload.parseRequest(request);
	    
	    // Process the uploaded items
	    Iterator iter = items.iterator();
	    FileItem sensorFile = null;

	    // if supplied by param...
	    //String url = null;

	    while (iter.hasNext()) {
		FileItem item = (FileItem) iter.next();
		
		if (item.isFormField()) {
		    String name = item.getFieldName();
		    String value = item.getString();	

		    if (name.equals("url")) {
			String url = value;
			pullPage(url);
		    } else {
			infomsg += "<p>Ignoring parameter "+name+"=<pre>"+value+"</pre></p>\n";
		    }
		} else {
		    
		    String fieldName = item.getFieldName();
		    String fileName = item.getName();
		    String contentType = item.getContentType();
		    boolean isInMemory = item.isInMemory();
		    long sizeInBytes = item.getSize();
		    
		    if (!fieldName.equals( "pdf"))  {
			infomsg += "<p>Ignoring file field parameter named "+fieldName+", with file Name "+ fileName+"</p>\n";
		    } else if (fileName.equals("")) {
			error = true;
			errmsg = "It appears that you have not uploaded a file! Please go back to the file upload form, and make sure to pick an existing sensor description file!";
			return u;
		    } else if (!fileName.endsWith(".pdf")) {
			error = true;
			errmsg = "Uploaded file name does not end with '.pdf', as expected";
			return u;
		    } else {
			//u.sensorFileName= fileName;
			sensorFile = item;

			// we have data file; now read it in	
			InputStream uploadedStream=sensorFile.getInputStream();
			savePdf(uploadedStream, fileName);

		    }
		    
		    infomsg += "<p>File field name="+fieldName+", file name="+fileName+", in mem=" + isInMemory +", len=" + sizeInBytes+"</p>"; 
		}
	    }

	    /*
	    if (u.q==null) {
		error=true;
		errmsg = "No file data seems to have been uploaded!";
	    } else if (sensorname != null && !sensorname.trim().equals("")) {
		sensorname = sensorname.trim();
		u.q.setName(sensorname);
	    }
	    */

	}  catch (Exception _e) {
	    e = _e;
	    error = true;
	    errmsg = "Failed to receive uploaded file, or to parse the file data. Please make sure that you are uploading file in the correct format! Error: " + e.getMessage();
	}
	return u;
	    
    }


    /** Saves the data from an input stream in a PDF file.
     */

    private DataFile savePdf(InputStream uploadedStream, String fileName ) 
    throws IOException {
	final int L = 32 * 1024;

	BufferedInputStream bis = new BufferedInputStream(uploadedStream, L);
	
	DataFile df = new DataFile(user, DataFile.Type.UPLOAD_PDF, fileName);
	
	File file = df.getFile();
	File dir = file.getParentFile();
	dir.mkdirs();
	
	FileOutputStream fos = new FileOutputStream(file);
	BufferedOutputStream bos=new BufferedOutputStream(fos,L);
	
	int n=0;
	byte[] data = new byte[L];
	while((n=bis.read(data))>0) {
	    bos.write(data, 0, n);
	}
	bis.close();
	bos.close();
	
	infomsg +="<p>Successfully uploaded PDF file '"+ 
	    fileName+"'</p>";
	uploadCnt ++;
	return df;
    }

    public UploadPapers(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	try {
	    if (ServletFileUpload.isMultipartContent(request)) {
		// The only place this is done is in file upload
		UploadingResults u = readUploadedFile();
		if (!error) {
		}
	    } else {
		String url = getString("url", null);
		if (url!=null) {
		    pullPage(url);
		}
	    }
	    
	} catch(  Exception ex) {
	    setEx(ex);
	}

   }

    private static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    public String dirInfo() {
	File d = DataFile.getDir(user, DataFile.Type.UPLOAD_PDF);
	if (d==null) {
	    return "<p>No files have been uploaded by user <em>" + user + "</em> so far</p>\n";
	}
	StringBuffer b = new StringBuffer();
	File[] files = d.listFiles();
	if (files==null) {
	    return "<p>No files have been uploaded by user <em>" + user + "</em> so far</p>\n";
	}
	if (files.length==0) {
	    b.append("<p>No files have been uploaded to the directory <tt>" + d + "</tt></p>\n");
	    return b.toString();
	}
	b.append("<p>The following  "+files.length+ " files have been uploaded so far to the directory <tt>" + d + "</tt></p>\n");
	b.append("<table>\n");
	for(File f: files) {
	    b.append("<tr><td><tt>" +f.getName() + "</tt>");
	    b.append("<td align='right'><tt>" +f.length() + " bytes</tt>");
	    b.append("<td align='right'><tt>" +sqlDf.format(f.lastModified())+ "</tt>");
	    b.append("</tr>\n");
	}
	b.append("</table>\n");
	return b.toString();
	
    }

    private void pullPage(String lURLString) 
	throws WebException, IOException, java.net.MalformedURLException {

	URL lURL    =new URL( lURLString);
	Logging.info("UploadPapers requesting URL " + lURL);
	HttpURLConnection lURLConnection;
	try {
	    lURLConnection=(HttpURLConnection)lURL.openConnection();	
	}  catch(Exception ex) {
	    String msg= "Failed to open connection to " + lURL;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
	    throw new WebException(msg);
	}

	lURLConnection.setInstanceFollowRedirects(true); 

	//	copyRequestHeaders( request, lURLConnection);

	try {
	    lURLConnection.connect();
	} catch(Exception ex) {
	    String msg= "Failed to connect to " + lURL;
	    Logging.error(msg);
	    //ex.printStackTrace(System.out);
	    throw new WebException(msg);
	}
    
	int code = lURLConnection.getResponseCode();
	String gotResponseMsg = lURLConnection.getResponseMessage();

	Logging.info("code = " + code +", msg=" + gotResponseMsg + "; requested url = " + lURL);
	//	LineConverter conv = new LineConverter(request, u, ae, asrc);

	if (code != HttpURLConnection.HTTP_OK) {
	    String msg= "UploadPapers: Error code " + code + " received when trying to retrieve page from " + lURL;
	    Logging.error(msg);
	    //ex.printStackTrace(System.out);
	    throw new WebException(msg);	    
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
	    throw new WebException(xcode, msg);
	}

	if (expectPdf) {
	    // simple bytewise copy
	    savePdf(is, fileName);
	} else {
	    BufferedInputStream in= new BufferedInputStream(is, ChunkSize);
	    /*
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
	    in.close();
	    */
	}
    }

    /** Checks the content type to figure whether this is a PDF document
	or not. ("Not PDF" is expected to be HTML)
	
	// e.g.  "Content-Type: text/html; charset=ISO-8859-4"

	@return Updated value of expectPdf */
    private boolean checkContentType(URL eURL, String lContentType) {
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
    private Charset getCharset(String lContentType) {
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
    private String getRecommendedFileName(//URL lURL, 
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
	Logging.info("UploadPapers: set file='"+f+"' based on url=" + lURL);
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

 