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

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.disk.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.*;

/** The "Toronto system": uploading PDF documents
*/
public class UploadPapers  extends ResultsBase {

    /** How many PDF files have been uploaded on this invocation? */
    public int uploadCnt=0;

    /** Reads a file uploaded from the web form
     */
    void readUploadedFile() {
	try {

	    // Create a factory for disk-based file items
	    FileItemFactory factory = new DiskFileItemFactory();
	    
	    // Create a new file upload handler
	    ServletFileUpload upload = new ServletFileUpload(factory);
	    
	    // Parse the request
	    List<FileItem> items = upload.parseRequest(request);
	    
	    // Process the uploaded items
	    for(Iterator iter = items.iterator(); iter.hasNext(); ) {
		FileItem item = (FileItem) iter.next();
		
		if (item.isFormField()) {
		    String name = item.getFieldName();
		    String value = item.getString();	
		    infomsg += "<p>UploadPapers: Ignoring parameter "+name+"=<pre>"+value+"</pre></p>\n";
		} else {
		    
		    String fieldName = item.getFieldName();
		    String fileName = item.getName();
		    String contentType = item.getContentType();
		    boolean isInMemory = item.isInMemory();
		    long sizeInBytes = item.getSize();
		    
		    String fileNameLC =  fileName.toLowerCase();
		
		    if (!fieldName.equals( "pdf"))  {
			infomsg += "<p>Ignoring file field parameter named "+fieldName+", with file Name "+ fileName+"</p>\n";
		    } else if (fileName.equals("")) {
			error = true;
			errmsg = "It appears that you have not uploaded a file! Please go back to the file upload form, and make sure to pick an existing document file!";
			return;
		    } else if (fileNameLC.endsWith(".pdf")) {
			// we have data file; now read it in	
			InputStream uploadedStream=item.getInputStream();
			DataFile df = UploadProcessingThread.savePdf(user, uploadedStream, fileName);
			if (df != null) {
			    uploadCnt++;
			    infomsg +="<p>Successfully uploaded PDF file '"+ 
				fileName+"'</p>";
			}

			sd.upThread = new UploadProcessingThread(sd, user, df);
			sd.upThread.start();
		
		    } else if (fileNameLC.endsWith(".html") ||
			       fileNameLC.endsWith(".htm")) {
			InputStream uploadedStream=item.getInputStream();
			HTMLParser.Outliner outliner = HTMLParser.parse(null, uploadedStream);
			uploadedStream.close();
			if (outliner != null) {
			    // start asynchronous processing of the links
			    //UploadProcessingThread 
			    sd.upThread = new UploadProcessingThread(sd, user, outliner);
			    sd.upThread.start();
			}
		    } else {
			error = true;
			errmsg = "Uploaded file name does not end with '.pdf' or '.html', as expected";
			return;
		    }
		    
		    infomsg += "<p>File field name="+fieldName+", file name="+fileName+", in mem=" + isInMemory +", len=" + sizeInBytes+"</p>"; 
		}
	    }

	}  catch (Exception _e) {
	    e = _e;
	    error = true;
	    errmsg = "Failed to receive uploaded file, or to parse the file data. Please make sure that you are uploading file in the correct format! Error: " + e.getMessage();
	}
	return;
	    
    }

    /** The "check=true" in the query string means that the user want to check
	the status of the current load process */
    public boolean check=false;

    /** To be displayed when check=true */
    public String checkTitle="???", checkText="?????", checkProgressIndicator="<!-- n/a -->";
    
   /** This will be set to true if we want the client to retry
	loading this page (or a slightly different one) in a few second.
	This is used on "progress" pages
     */
    public boolean wantReload=false;
    public String reloadURL;

    /** We set it to true if the main screen should be shown to the user */
    public boolean wantMainScreen = true;
    /** We set it to true if the current thread's status should be shown to the user */    
    public boolean wantStatus = false;


    /** Generates the URL for the "Continue" button (and/or the "refresh" tag)
	@param checkAgain If true, the URL well send the user to the 
	check status page again.
     */
    private String getReloadURL(boolean checkAgain) {
	String s= cp + "/personal/uploadPapers.jsp" ;
	if (checkAgain) s +=  "?check=true";
	return s;
    }

    /** The main class for document uploading; it is the back end for
	uploadPapers.jsp. There are several cases when this page is
	used:

	<ul>

	<li> check=true : look up the current state of the uploading
	process running in a background thread.

	<li>form  enctype="multipart/form-data" : uploading a PDF or HTML
	document

	<li>url=... : getting a PDF or HTML document from a URL.
	</ul>
     */
    public UploadPapers(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	if (user==null) {
	    error = true;
	    errmsg = "Not logged in";
	    return;
	}

	reloadURL = getReloadURL(false);

	check = getBoolean("check", check);

	if (check) {  // The user wants to check the status of the upload process (the "check=true" URL)
	    wantStatus = true;
	    statusCheck();    	 
	    return;
	} else if (isRunning()) {  // The user did NOT request the check page, but the thread is already running!
	    wantStatus = true;
	    wantReload = false;
	    wantMainScreen = false;
	    checkTitle = "Wait for the previous uploading process to finish!";
	    checkText =
		"You cannot upload more documents until the previous uploading process has completed.\n" +
		"Uploading thread state = " + sd.upThread.getState()+ "\n"+
		sd.upThread.getProgressText();		
	    reloadURL = getReloadURL(true);	  
	    checkProgressIndicator=sd.upThread.getProgressIndicatorHTML(cp);
	    return;
	}

	try {
	    URL lURL = null;
	    if (ServletFileUpload.isMultipartContent(request)) {
		// File upload from the desktop
		readUploadedFile();
		if (error) return;
		wantStatus = true;
		statusCheck();
	    } else if ((lURL=getDocumentURL())!=null) {
		// File loading from a URL
		sd.upThread = new UploadProcessingThread(sd, user, lURL);
		sd.upThread.start();
		wantStatus = true;
		statusCheck();
	    } else {
		// The user simply wants to view the main screen
		wantMainScreen = true;
	    }
	  
	} catch(  Exception ex) {
	    setEx(ex);
	}
    }

    /** Is the background thread running right now? */
    private boolean isRunning() {
	return sd.upThread != null && sd.upThread.getState() != Thread.State.TERMINATED;
    }

    /** Checks the status of the background thread which we think is
	running, or was running recently, and prepares all the necessary
	reporting.
     */
    private void statusCheck() {
    
	if (sd.upThread == null) {
	    checkTitle = "No uploading is taking place";
	    checkText = "No uploading process is taking place right now or was taking place recently";
	    wantMainScreen = true;
	} else if (sd.upThread.error) {
	    checkTitle = "Error happened";
	    checkText = sd.upThread.getProgressText();
	    wantMainScreen = false;
	} else if (sd.upThread.getState() == Thread.State.TERMINATED) {
	    checkTitle = "Uploading completed";
	    checkText = sd.upThread.getProgressTextBrief();
	    wantMainScreen = true;
	} else {
	    check=true;
	    wantReload = true;
	    wantMainScreen = false;
	    checkTitle = "Uploading in progress...";
	    checkText =
		//"Uploading thread state = " + sd.upThread.getState()+ "\n"+
		sd.upThread.getProgressText();		
	    reloadURL = getReloadURL(true);		
	    checkProgressIndicator=sd.upThread.getProgressIndicatorHTML(cp);
	}
	return;
    } 

    /** See if there is a url=... param in the query screen, requesting
	that a document be loaded from the web. */
    URL getDocumentURL() throws WebException {
	String url = getString("url", null);
	if (url==null) return null;
	if (url.trim().equals("")) {
	    throw new WebException("No URL specified in the url=... parameter!");
	}
	if (url.indexOf("://")<0) { // add omitted protocol
	    url = "http://" + url;
	}
	try {
	    return new URL(url);
	} catch( java.net.MalformedURLException ex) {
	    throw new WebException("Malformed URL: " + url);
	}
    }


    private static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    /** Produces a long message describing the content of this user's
	upload directory. (Actually, there are 2 directories for each
	user: one of the actually uploaded PDF files, and the other
	for the text files into which these PDF files are converted
	immediately after uploading. This method can display the
	content of either directory).
	@param convertedToTxt List the converted text files (rather
	than the original PDF files)
     */
    public String dirInfo(boolean convertedToTxt) {

	boolean showFiles = false;
	
	DataFile.Type type = convertedToTxt? DataFile.Type.UPLOAD_TXT:
	    DataFile.Type.UPLOAD_PDF;

	if (user==null) return "<p>Not logged in</p>";
	File d = DataFile.getDir(user, type);
	if (d==null) {
	    return "<p>No files have been uploaded by user <em>" + user + "</em> so far</p>\n";
	}
	StringBuffer b = new StringBuffer();
	File[] files = d.listFiles();
	if (files==null) {
	    return "<p>No files have been uploaded by user <em>" + user + "</em> so far</p>\n";
	}
	if (files.length==0) {
	    b.append("<p>There are no files in the directory <tt>" + d + "</tt></p>\n");
	    return b.toString();
	}
	if (showFiles) {
	    b.append("<p>The following  "+files.length+ " files have been " +
		     (convertedToTxt ? "converted" : "uploaded") + 
		     " so far to the directory <tt>" + d + "</tt></p>\n");
	    b.append("<table>\n");
	    for(File f: files) {
		b.append("<tr><td><tt>" +f.getName() + "</tt>");
		b.append("<td align='right'><tt>" +f.length() + " bytes</tt>");
		b.append("<td align='right'><tt>" +sqlDf.format(f.lastModified())+ "</tt>");
		b.append("</tr>\n");
	    }
	    b.append("</table>\n");
	} else {
	    b.append("<p>"+files.length+ " files have been " +
		     (convertedToTxt? "converted to text" : "uploaded")+ " so far\n");
	}

	if (!convertedToTxt)	return b.toString();
	try {
	    IndexReader reader=Common.newReader();
	    IndexSearcher searcher = new IndexSearcher( reader );
	    ScoreDoc[] sd = Common.findAllUserFiles(searcher, user);
	    
	    b.append("<p>" + sd.length + " uploaded documents have been made available for analysis <!-- (stored in Lucene data store) -->");
	    b.append("<table>\n");
	    b.append("<tr><td>Doc No.<td>Name <td> Length <td> Importing date</tr>\n");
	    for(int i=0; i<sd.length; i++) {
		int docno = sd[i].doc;
		Document doc = reader.document(docno);
		b.append("<tr><td align=right><tt>" + docno +"</tt>");
		b.append("<td><tt>" + doc.get(ArxivFields.UPLOAD_FILE) +"</tt>");
		b.append("<td align=right><tt>" + doc.get(ArxivFields.ARTICLE_LENGTH) +"</tt>");
		b.append("<td align=right><tt>" + doc.get(ArxivFields.DATE_INDEXED) +"</tt>");
		b.append("</tr>\n");


	    }
	    b.append("</table>\n");
	    reader.close();
	} catch(IOException ex) {
	    b.append("Cannot access Lucene data store");
	}

	return b.toString();
	
    }

}

 