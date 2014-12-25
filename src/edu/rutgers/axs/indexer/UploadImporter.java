package edu.rutgers.axs.indexer;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.*;
import java.net.*;

import javax.persistence.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.Logging;
//import edu.rutgers.axs.sql.Article;
import edu.rutgers.axs.sql.Main;
import edu.rutgers.axs.web.SearchResults;

/** Importing files uploaded by users (the "Toronto System") */
public class UploadImporter {
    
    private static String getFileNameBase(File f) {
 	return f.getName().replaceAll("\\.gz$","").replaceAll("\\.txt$",""); 
    }

    /** Which date field we set in Lucene docs? */
    static public final String DATE_FIELD = ArxivFields.DATE_INDEXED;

    /** Imports one user-uploaded file into the Lucene data store. 
	@param user User name, to be used as the UPLOAD_USER attribute
	of the Lucene document to be created.
	@param f A text file to import (probably, generated by
	PDFMiner). The file name, without extension, will be used as
	the UPLOAD_FILE attribute of the Lucene document to be
	created.

	@return the Document object that has been created and imported
	into the Lucene doc store
     */
    public static Document importFile(String user, File f, IndexWriter writer)
	throws IOException {
	boolean rewrite = true;

	if (!f.exists() || !f.canRead()) throw new IOException("UploadImporter: File "+f+" does not exist or cannot be read");

	org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

	doc.add(new Field(ArxivFields.UPLOAD_USER, user,
			  Field.Store.YES, Field.Index.NOT_ANALYZED));

	String fileNameBase= getFileNameBase(f);

	doc.add(new Field(ArxivFields.UPLOAD_FILE, fileNameBase,
			  Field.Store.YES, Field.Index.NOT_ANALYZED));

	String now = DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND);

	// FIXME: simplistic...
	//doc.add(new Field(ArxivFields.DATE,  now,
	//		  Field.Store.YES, Field.Index.NOT_ANALYZED));

	
	String whole_doc = Indexer.parseDocFile(f.getCanonicalPath());

	doc.add(new Field(ArxivFields.ARTICLE, whole_doc, Field.Store.NO, Field.Index.ANALYZED,  Field.TermVector.YES));

	// Record current time as the date the document was indexed 
	doc.add(new Field(DATE_FIELD,
			  DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND),
			  Field.Store.YES, Field.Index.NOT_ANALYZED));		
	// Store doc length
	int len = whole_doc.length();
	doc.add(new Field(ArxivFields.ARTICLE_LENGTH, Integer.toString(len), Field.Store.YES, Field.Index.NOT_ANALYZED));

	Logging.info("UploadImporter: indexed "+user+":"+ fileNameBase+", date=" + doc.get(DATE_FIELD) +"; len=" + len );
	
	// write data to Lucene

	writer.deleteDocuments(Common.userFileQuery(user,fileNameBase));
	writer.addDocument(doc);

	//	writer.commit();
	return doc;

    }

    /** Retrieves the importing date recorded in the Lucene document */
    public static Date getUploadDate(Document doc) {
	if (doc==null) return null;
	String dateString = doc.get(UploadImporter.DATE_FIELD);
	if (dateString==null) return null;
	try {
	    Date date= DateTools.stringToDate(dateString);
	    return date;
	} catch (java.text.ParseException ex) {
	    //	    System.out.println("Date reject: parse exception on " + dateString);
	    return null;
	}
    }

    static void usage() {
	usage(null);
    }

    static void usage(String m) {
	System.out.println("UploadImporter test");
	System.out.println("Usage: java [options] UploadImporter user path");
	//	System.out.println("Options:");
	//	System.out.println(" [-Dtoken=xxx]");

	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }

    public static IndexWriter makeWriter() throws IOException {
       Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
       Analyzer analyzer = new StandardAnalyzer(Common.LuceneVersion);      
       IndexWriterConfig iwConf = new IndexWriterConfig(Common.LuceneVersion, analyzer);
       iwConf.setOpenMode(IndexWriterConfig.OpenMode.APPEND);	
       return new IndexWriter(indexDirectory, iwConf);
   }


  static public void main(String[] argv) throws IOException {
      if (argv.length<1) usage();
      int ia = 0;
      String cmd = argv[ia++];

      if (cmd.equals("index")) {
	  String user = argv[ia++], path = argv[ia++];
	  File f =  new File(path);

	  IndexWriter writer = makeWriter();
	  //IndexReader reader = IndexReader.open(writer, true);
	  importFile(user, f, writer);
	  writer.close();

	  IndexReader reader = Common.newReader();
	  String fileNameBase= getFileNameBase(f);
	  int docno = Common.findUserFile(new IndexSearcher(reader), user, fileNameBase);

	  Logging.info("UploadImporter: found for "+user+":"+ fileNameBase+" : docno=" + docno );
	
	  reader.close();

      } else if  (cmd.equals("find")) {
	  String user = argv[ia++];
	  String f =  (ia < argv.length) ? argv[ia++]: null;
	  IndexReader reader = Common.newReader();
	  IndexSearcher s = new IndexSearcher( reader );
	  if (f==null) {
	      ScoreDoc[] sd = Common.findAllUserFiles(s, user);
	      System.out.println("Found " + sd.length + " uploaded documents for user " + user + " in Lucene");
	      for(int i=0; i<sd.length; i++) {
		  int docno = sd[i].doc;
		  Document doc = reader.document(docno);
		  Date date = getUploadDate( doc);
		  
		  System.out.println(""+i+". docno=" + docno +"; file=" + doc.get(ArxivFields.UPLOAD_FILE) + ", imported on " + date);
	      }
	  } else {	      
	      int docno = Common.findUserFile(s, user, f);
	      Document doc = reader.document(docno);
	      Date date = getUploadDate( doc);
	      System.out.println("docno=" + docno +"; file=" + doc.get(ArxivFields.UPLOAD_FILE) + ", imported on " + date);	      
	  }
      } else {
	  usage("Unknown command: " + cmd);
      }

  }

}