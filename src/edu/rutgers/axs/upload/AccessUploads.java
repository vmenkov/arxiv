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

import org.apache.commons.lang.mutable.*;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.upload.*;
//import edu.rutgers.axs.html.*;
//import edu.rutgers.axs.indexer.Common;

/** Code for accessing the content of documents that have been uploaded by users
    and converted to text. 

    <p> This class now has little use, since, once uploaded docs have
    been indexed, we typically access them via Lucene.
 */
public class AccessUploads {
   
    /** Returns the list of documents that have been uploaded by a specified
	user and converted to text */
    public static Vector<String> listDocNames(String user) throws IOException {	
	Vector<String> v = new Vector<String>();
	DataFile.Type type = DataFile.Type.UPLOAD_TXT;
	File d = DataFile.getDir(user, type);
	if (d==null) return v;
	File[] files = d.listFiles();
	if (files==null || files.length==0)  return v;
	for(File f: files) {
	    v.add( f.getName());
	}
	return v;
    }

    
    public HashMap<String,  MutableInt> readDoc(String user, String name) throws IOException {
	DataFile.Type type = DataFile.Type.UPLOAD_TXT;
	File d = DataFile.getDir(user, type);
	File f = new File(d, name);

	HashMap<String,  MutableInt> h = new  HashMap<String,  MutableInt>();
	LineNumberReader r = new LineNumberReader(new FileReader(f));
	String s=null;
	while((s=r.readLine())!=null) { 
	    String words[] = s.split("\\s+");
	    for(String w: words) {
		MutableInt val = h.get(w);
		if (val==null) h.put(w, new MutableInt(1));
		else val.add(1);
	    }
	}
	return h;
    }

}