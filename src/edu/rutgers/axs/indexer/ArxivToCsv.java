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
     the OAI interface, and saving them as a CSV file, for David Blei and
     Laurent (2013-12-15). This is based on ArxivImporter, but is streamlined
     into a separate, much simpler application.
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

    /** Pulls in all pages, or all pages in a date range.
	@param tok start harvesting from the beginning (if null), or from this resumption token (otherwise)
	@param max if max non-negative, then ...; -1 means "all"
	@param from "YYYY-MM-DD", passed to OAI2 from=... option
     */
    public void importAll(String tok, int max,  String from, PrintWriter w)  throws IOException,  org.xml.sax.SAXException {
	int pagecnt=0;

	try {

	    while( max<0 || pagecnt < max) 	 {
		String us = ArxivImporter.makeURL( tok, from);
		System.out.println("At "+new Date()+", requesting: " + us);
		Element e = ArxivImporter.getPage(us);	    
		tok = parseResponse(e,w);
		w.flush();
		pagecnt++;
		System.out.println("done "+pagecnt+" pages, token =  " + tok);
		if (tok==null || tok.trim().equals("")) break;

		if (pagecnt % 1 == 0) {
		    System.out.println("At "+new Date()+", re-opening index... ");
		}

	    }

	} finally {
	}
    }

  /** Parses an OAI-PMH element, and triggers appropriate operations.
	@return the resumption token
    */
    private String parseResponse(Element e, PrintWriter w)  throws IOException {
	//org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
	XMLUtil.assertElement(e,"OAI-PMH");
	Element listRecordsE = null, getRecordE = null;
	for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (n instanceof Element && n.getNodeName().equals("ListRecords")) {
		listRecordsE = ( Element )n;
		break;
	    } else if (n instanceof Element && n.getNodeName().equals("GetRecord")) {
		getRecordE = ( Element )n;
		break;
	    }
	}
	String token=null;

	Element outer = (listRecordsE != null) ? listRecordsE:  getRecordE;
	if (outer == null) return token; // empty response

	for(Node n = outer.getFirstChild(); n!=null; n = n.getNextSibling()) {
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

    /** Parses a "record" XML elements. Expected children are "header"
	and "metadata".
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
	System.out.println("Usage: java [options] ArxivToCsv all [max-page-cnt]");
	System.out.println("Options:");
	System.out.println(" [-Dtoken=xxx]");

	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }

    static public void main(String[] argv) throws IOException, SAXException,
						  java.text.ParseException
 {
	if (argv.length==0) usage();
	ParseConfig ht = new ParseConfig();
	String tok=ht.getOption("token", null);
	String from=ArxivImporter.getFrom(ht); // based on "from" and "days"

	ArxivToCsv imp =new  ArxivToCsv();

	if (argv.length==0) return;
	final String cmd =argv[0];

	PrintWriter fw = new PrintWriter(new FileWriter("details.csv"));
	fw.println(headerToString());
	fw.flush();

	if ( cmd.equals("all")) {
	    int max=-1;
	    if (argv.length>1) {
		try {
		    max	=Integer.parseInt(argv[1]);
		} catch(Exception ex) {}
	    }
	    System.out.println("Processing web data, up to "+max + " pages; from=" + from);
	    imp.importAll(tok, max, from, fw);
	} else {
	    System.out.println("Unrecognized command: " + cmd);
	}
	System.out.println("imported "+imp.pcnt+" docs");
	fw.close();
    }

}