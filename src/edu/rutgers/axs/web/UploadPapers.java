package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
//import java.text.*;
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

/** The "Toronto system"
*/
public class UploadPapers  extends ResultsBase {

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
	    String url = null;

	    while (iter.hasNext()) {
		FileItem item = (FileItem) iter.next();
		
		if (item.isFormField()) {
		    String name = item.getFieldName();
		    String value = item.getString();	

		    /*
		    if (name.equals(SENSORDATA)) {
			// TEXTAREA upload
			u.sensorFileName = "Sample_Sensor";
			u.q = new Test(new BufferedReader(new StringReader(value)) , 
				       u.sensorFileName, 1);
			u.sensorFromTextarea = true;
			infomsg +="<p>Successfully read sensor description from uploaded data; using dummy name='"+ u.sensorFileName+"'</p>";
		    } else 
		    */
		    if (name.equals("url")) {
			url = value;
		    } 
		    /* else if (name.equals("id")) {

			try {
			    u.id = Integer.valueOf(value);
			} catch(Exception ex) {}
  
			} */ else {
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
			final int L = 32 * 1024;
			InputStream uploadedStream=sensorFile.getInputStream();
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


    public UploadPapers(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	if (ServletFileUpload.isMultipartContent(request)) {
	    // The only place this is done is in file upload
	    UploadingResults u = readUploadedFile();
	    if (!error) {
	    }
	} 

   }

 

}

 