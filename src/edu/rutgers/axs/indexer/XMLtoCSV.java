package edu.rutgers.axs.indexer;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.text.SimpleDateFormat;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/** This class controls how various fields of the XML structure are
    processed. The XML element is obtained from arxiv.org via the OAI
    interface. The processing of the XML element involves finding
    relevant fields in it, extracting them, and placing them into a
    hash table which can be later saved as a line of CSV file.
 */
class XMLtoCSV extends XMLtoHash {
    
  
    /** Sets conversion rules for various fields. Note the special
	treatment of the time field: the numbers coming via OAI2, such
	as 2012-06-01 should be understood as midnight on EST, while
	strings inside Lucene are interpreted (by
	org.apache.lucene.document.DateTools) as GMT time stamps.
     */
    static  XMLtoCSV makeMap(String[] fields) {
	XMLtoCSV  map = new 	XMLtoCSV();

	for(String field: fields) {
	    if (field.equals(ArxivFields.AUTHORS)) {
		map.add( field).setHandler(new AuthorsHandler(true));
	    } else {
		map.add(field);
	    }
	}
	map.ignore("datestamp");
	map.ignore("setSpec");
	map.ignore("comments");
	map.ignore("license");
	map.ignore("identifier");
	map.ignore("msc-class");
	map.ignore("acm-class");
	map.ignore("report-no");
	map.ignore("proxy");

	map.recurse(ArxivImporterBase.Tags.RECORD);
	map.recurse(ArxivImporterBase.Tags.HEADER);
	map.recurse(ArxivImporterBase.Tags.METADATA);
	map.recurse("arXiv");
	return map;
    }

}

