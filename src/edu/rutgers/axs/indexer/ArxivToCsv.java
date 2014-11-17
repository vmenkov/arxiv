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

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.Logging;

 /** The application for pulling data from the main arxiv server using
     the OAI interface, and saving them as a CSV file, for David Blei
     and Laurent Charlin (2013-12-15). This class is based on
     ArxivImporter, but is streamlined into a separate, much simpler
     application.

     <p>For details, see the email threads:
     <ul>
     <li>
     "accessing the data", starting 2013-11-19
     <li>
     "adding meta-data to abstracts.dat",  starting 2013-12-12
     </ul>

     <p>What is the best time of day to run this application if you
     want to get the recently uploaded articles as soon as they are
     released? This, apparently, is around 20:15 (or, to be safe,
     20:30) New York time.  PG writes (2012-11-19): "the pages are
     updated on the five days /M/Tu/W/Th at 20:00 EDT/EST, currently
     we're on EST so that's a five hour difference and they're updated
     at 01:00 GMT. in the summer we're on EDT and they're updated at
     00:00 GMT". "5 days/wk S-Th at 20:15 should work (or perhaps
     20:30)"

 */
public class ArxivToCsv {

    /*
-id     
- paper title
- authors (& affiliations when available)
- date posted (If multiple versions, dates of different versions)
- article categories (subjects)
- DOI
- journal ref
- abstract
*/
    static final String [] fields = {
	"id",
	"created",
	"updated",
	"title",
	ArxivFields.AUTHORS, //"authors",
	"categories",
	"doi",
	"journal-ref",
	"abstract"
    };

    static private XMLtoCSV  xml2csvMap = XMLtoCSV.makeMap(fields);

    /** Pulls in all pages, or all pages in a date range. The "from"
	and "until" parameters correspond to the parametres of the
	same name in the OAI2 request's query string. The date values
	these parameters refer to are the ones shown as the
	"datestamp" field of each document entry in the retrieved. (On
	the semantics of this fiels, as explained by Dr. Ginsparg, see
	the email thread "Semantics of the "datestamp" field in ArXiv's
	OAI2", dated 2013-12-18).

	@param tok start harvesting from the beginning (if null), or from this resumption token (otherwise)
	@param max if max non-negative, then ...; -1 means "all"
	@param from "YYYY-MM-DD" (inclusively), passed to OAI2 from=... option
	@param until "YYYY-MM-DD" (inclusively), passed to OAI2 until=... option
       	
     */
    public void importAll(String tok, int max,  String from, String until, PrintWriter w)  throws IOException,  org.xml.sax.SAXException {
	int pagecnt=0;

	try {

	    while( max<0 || pagecnt < max) 	 {
		String us = ArxivImporter.makeURL( tok, from, until);
		System.out.println("At "+new Date()+", requesting: " + us);
		Element e = ArxivImporter.getPage(us);	    
		tok = parseResponse(e,w);
		w.flush();
		pagecnt++;
		System.out.println("done "+pagecnt+" pages, token =  " + tok);
		if (tok==null || tok.trim().equals("")) break;
	    }

	} finally {
	}
    }

  /** Parses an OAI-PMH element, and triggers appropriate operations.
      
      <p>The XML element being parsed is "OAI-PMH". It typically has
      child elements "responseDate", "request", and "ListRecords". It
      is the "ListRecords" element which contains all the data we need
      (as a sequence of "record" elements, followed by a
      "resumptionToken" element).

      <p>In case of an error, an "error" element is found instea of
      "ListRecords".

      @return the resumption token
    */
    private String parseResponse(Element e, PrintWriter w)  throws IOException {
	XMLUtil.assertElement(e,"OAI-PMH");
	Element listRecordsE = null, getRecordE = null;
	for(Node n=e.getFirstChild(); n!=null; n=n.getNextSibling()) {
	    if (!(n instanceof Element)) continue;
	    if (n.getNodeName().equals("ListRecords")) {
		listRecordsE = (Element)n;
		break;
	    } else if (n.getNodeName().equals("GetRecord")) {
		getRecordE = (Element)n;
		break;
	    } else if (n.getNodeName().equals("error")) {
		Element err = (Element)n;
		System.out.println("Received an error response: "+err);
		break;
	    }
	}
	String token=null;

	Element outer = (listRecordsE != null) ? listRecordsE:  getRecordE;
	if (outer == null) return token; // empty response

	for(Node n=outer.getFirstChild(); n!=null; n=n.getNextSibling()) {
	    if (!(n instanceof Element)) continue;
	    String name = n.getNodeName();
	    if (name.equals( ArxivImporter.Tags.RECORD)) {
		importRecord((Element)n,w); 
	    } else if (name.equals("resumptionToken")) {
		// <resumptionToken cursor="0" completeListSize="702029">245357|1001</resumptionToken>
		Node nx = n.getFirstChild();
		if (nx==null) {
		    System.out.println("Token is null; this must have been the last page");
		} else  if (nx instanceof Text) {
		    token=nx.getNodeValue();
		} else {
		    System.out.println("cannot parse the 'token' element: "+nx);
		}
	    }
	}
	return token;

    }

    private int pcnt=0;

    private void importRecord(Element record, PrintWriter w) throws IOException {
	HashMap<String,String>  doc = parseRecordElement( record);
	if (doc==null) {
	    // deleted status
	    System.out.println("Ignoring deleted document");
	    return;
	}

	String paper = doc.get("id");

	if ( paper==null) {
	    // an occasional problematic doc, like this one:
	    // http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:0901.4014
	    System.out.println("Failed to extract id from the record. Doc={"+ hashToString(doc) + "}");
	    return;
	}

	w.println(hashToString(doc));
   	pcnt++;

    }

    /** Parses a "record" XML elements. The expected children are
	"header" and "metadata" elements.
     */
    public static HashMap<String,String> parseRecordElement(Element e)  throws IOException {
	Element header = XMLUtil.findChild(e, ArxivImporter.Tags.HEADER, false);
	if (header!=null) {
	    String status=header.getAttribute("status");
	    if (status!=null && status.equals("deleted")) return null;
	}

	//	org.apache.lucene.document.Document
	HashMap<String,String>	    doc = new 	HashMap<String,String>();
	XMLUtil.assertElement(e,ArxivImporter.Tags.RECORD);

	xml2csvMap.process(e, doc);
	return doc;
    }
    
    static String headerToString() {
	HashMap<String,String> doc=new HashMap<String,String>();
	for(String field: fields) {
	    doc.put(field,field);
	}
	return hashToString(doc);
    }

    static String hashToString(HashMap<String,String> doc) {
	StringBuffer b=new StringBuffer();
	for(String field: fields) {
	    if (b.length()>0) {
		b.append(",");
	    }
	    String q = doc.get(field);
	    if (q!=null && q.length() >0) {
		q=q.trim();
		b.append('"');
		q=q.replace('\n',' ').replaceAll("\\s+", " ").replace('"', '\'');		b.append(q);
		b.append('"');
	    }
	}
	return b.toString();
    }

    static void usage() {
	usage(null);
    }

   static void usage(String m) {
	System.out.println("Arxiv-to-CSV Tool");
	System.out.println("Usage: java [options] ArxivToCsv [max-page-cnt]");
	System.out.println("Options:");
	System.out.println(" [-Dout=detail.csv]   : out file");
	System.out.println(" [-Dfrom=2013-01-01]  : first day of the date range (inclusive), in the YYYY-MM-DD format");
	System.out.println(" [-Duntil=2014-12-31] : last day of the date range (inclusive), in the YYYY-MM-DD format");
	System.out.println(" [-Ddays=7]           : an alternative to 'from', specifies the first day of the date range as being so many days ago");
	System.out.println("\nExample: to download all documents with datestamp in 2013 and save them into file details-2013.csv, run the program as follows:");
	System.out.println("java -Dfrom=2013-01-01 -Duntil=2013-12-31 -Dout=details-2013.csv ArxivToCsv all [max-page-cnt]");
	
	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
   }

    
    static public void main(String[] argv) throws IOException, SAXException,
						  java.text.ParseException
    {
	//if (argv.length==0) usage();
	ParseConfig ht = new ParseConfig();
	String tok=ht.getOption("token", null);
	// The name of the output file
	String outfile=ht.getOption("out", "details.csv");
	String from=ArxivImporter.getFrom(ht); // based on "from" and "days"
	String until=ArxivImporter.getUntil(ht); // based on "until"
     
	ArxivToCsv imp =new  ArxivToCsv();
	
	int ja=0;
	if (argv.length==0) usage();
	final String cmd =argv[ja++];
	
	PrintWriter fw = new PrintWriter(new FileWriter(outfile));
	fw.println(headerToString());
	fw.flush();
	
	if ( cmd.equals("help")) {
	    usage();
	} else if (cmd.equals("csv")) {
	    int max=-1;
	    if (ja<argv.length) {
		try {
		    max	=Integer.parseInt(argv[ja++]);
		} catch(Exception ex) {}
	    }
	    System.out.println("Processing web data, up to "+max + " pages; from=" + from +  " until=" + until);
	    imp.importAll(tok, max, from, until, fw);
	} else {
	    System.out.println("Unrecognized command: " + cmd);
	}
	System.out.println("imported "+imp.pcnt+" docs");
	fw.close();
    }
    
}