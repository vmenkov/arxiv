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
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.Common;
//import edu.rutgers.axs.recommender.Scheduler;
//import edu.rutgers.axs.recommender.DailyPPP;
//import edu.rutgers.axs.ee4.Daily;

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

		    /*
		    if (name.equals("url")) {
			String url = value;
			URL lURL = new URL(url);

			Vector<DataFile> results = UploadProcessingThread.pullPage(user, lURL, false);
			uploadCnt += results.size();
		    } else {
		    */
			infomsg += "<p>UploadPapers: Ignoring parameter "+name+"=<pre>"+value+"</pre></p>\n";
			/*
		    }
			*/
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
		    } else if (fileNameLC.endsWith(".html") ||
			       fileNameLC.endsWith(".htm")) {
			InputStream uploadedStream=item.getInputStream();
			HTMLParser.Outliner outliner = HTMLParser.parse(null, uploadedStream);
			uploadedStream.close();
			if (outliner != null) {
			    // start asynchronous processing of the links
			    //UploadProcessingThread 
			    sd.upThread = new UploadProcessingThread(user, outliner);
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
	return;
	    
    }

    /** The "check=true" in the query string means that the user want to check
	the status of the current load process */
    public boolean check=false;

    /** To be displayed when check=true */
    public String checkTitle="???", checkText="?????";
    
   /** This will be set to true if we want the client to retry
	loading this page (or a slightly different one) in a few second.
	This is used on "progress" pages
     */
    public boolean wantReload=false;
    public String reloadURL;

    /** Generates the URL for the "Continue" button (and/or the "refresh" tag)
     */
    private String getReloadURL(boolean check) {
	String s= cp + "/personal/uploadPapers.jsp" ;
	if (check) s +=  "?check=true";
	return s;
    }

    public UploadPapers(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	reloadURL = getReloadURL(false);

	check = getBoolean("check", check);

	if (check) {
	    if (sd.upThread == null) {
		checkTitle = "No uploading is taking place";
		checkText = "No uploading process is taking place right now or was taking place recently";
	    } else if (sd.upThread.getState() == Thread.State.TERMINATED) {
		checkTitle = "Uploading completed";
		checkText = sd.upThread.getProgressText();
	    } else {
		wantReload = true;
		checkTitle = "Uploading in progress";
		checkText =
		    "Uploading thread state = " + sd.upThread.getState()+ "\n"+
		    sd.upThread.getProgressText();		
		reloadURL = getReloadURL(true);
	    }
	    return;
	} else if (sd.upThread != null && sd.upThread.getState() != Thread.State.TERMINATED) {
	    check = true;
	    wantReload = false;
	    checkTitle = "Wait for the previous uploading process to finish!";
	    checkText =
		"You cannot upload more documents until the previous uploading process has completed.\n" +
		"Uploading thread state = " + sd.upThread.getState()+ "\n"+
		sd.upThread.getProgressText();		
	    reloadURL = getReloadURL(true);	  
	    return;
	}

	try {
	    if (ServletFileUpload.isMultipartContent(request)) {
		// The only place this is done is in file upload
		readUploadedFile();
		if (!error) {
		}
	    } else {
		String url = getString("url", null);
		if (url!=null) {
		    if (url.trim().equals("")) {
			throw new WebException("No URL specified!");
		    }
		    URL lURL = new URL(url);
		    sd.upThread = new UploadProcessingThread(user, lURL);
		    sd.upThread.start();
		    //		    Vector<DataFile> results = UploadProcessingThread.pullPage(user, lURL, false);
		    //		    uploadCnt += results.size();
		}
	    }
	    

	    if (sd.upThread != null && sd.upThread.getState() != Thread.State.TERMINATED) {
		check=true;
		wantReload = true;
		checkTitle = "Uploading in progress";
		checkText =
		    "Uploading thread state = " + sd.upThread.getState()+ "\n"+
		    sd.upThread.getProgressText();		
		reloadURL = getReloadURL(true);		
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

}

 