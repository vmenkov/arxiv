package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import java.util.Collection;
import java.util.Iterator;

import java.util.*;
import java.util.regex.*;
import java.io.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;


/** Code for parsing an abstract in XML format, as per the OAI specs.
 */

public class ParseAbstractXML {

    //   static private XMLtoLucene xml2luceneMap = XMLtoLucene.makeMap();
    
    /*
    static void assertElement(Node n) throws IOException {
	assertElement(n, null);
    }

    static void assertElement(Node n, String expectedName) throws IOException {
	if (!(n instanceof Element)) {
	    throw new IOException("Expected to find an XML element, found node: "+n);
	}
	//int type = n.getNodeType();
	String name = n.getNodeName();
	
	if (expectedName!=null&& 
	    !name.equals(expectedName))  {
	    throw new IOException("Expected to find an '"+ expectedName+"' XML element, found element: "+n);
	}
    }
    */

    /*
    static class Tags {
	final static String RECORD = "record", HEADER="header", METADATA="metadata";
    }
    */

    /*
    public static org.apache.lucene.document.Document parseRecordElement(Element e)  throws IOException {
	org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
	assertElement(e,Tags.RECORD);
	xml2luceneMap.process(e, doc);
	return doc;
    }
    */
   
    static public void main(String[] argv) throws IOException, SAXException {
	for(String s: argv) {
	    System.out.println("Processing " + s);
	    Element e = XMLUtil.readFileToElement(s);
	    org.apache.lucene.document.Document doc = ArxivImporter.parseRecordElement( e);
	    if (doc==null) {
		System.out.println("Found a deleted Document");		
	    } else {
		System.out.println("Doc = " + doc);
	    }
	}
    }


}