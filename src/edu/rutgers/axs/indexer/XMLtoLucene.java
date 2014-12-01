package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

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
    Lucene Document object, which can later be indexed and stored in
    the Lucene datastore,
 */
class XMLtoLucene {
/** The map describes how various fields of the XML structure are
    processed. */
    private HashMap<String, Xml2Lucene> map = new HashMap<String, Xml2Lucene>();

    /** Flags for Xml2Lucene objects */
    static class Flags {
	static final int IGNORE=1, RECURSE=2, FLATTEN=4, NOT_ANALIZED=8;
    }
	
    /** An instance of this class determines how a particular field of
	the XML file is mapped
    */
    static class Xml2Lucene {
	/** A boolean OR of some Flag constants */
	int flags;
	/** The name of the field in the OAI XML feed */
	String xmlName;
	/** The name under which we want to store the field in Lucene */
	String luceneName;
	Xml2Lucene(String xml,String lu,int _flags) {
	    xmlName = xml;
	    luceneName=lu;
	    flags=_flags;	
	}
	FieldHandler h= FieldHandler.trivial;
	Xml2Lucene setHandler(FieldHandler _h) {
	    h = _h;
	    return this;
	}
    }

    XMLtoLucene() {
	super();
    }

    Xml2Lucene add(String xmlName, String luceneName, int flags) {
	    Xml2Lucene e = new  Xml2Lucene( xmlName,luceneName,flags);
	    map.put( xmlName, e);
	    return e;
    } 
    Xml2Lucene add(String xmlName, String luceneName) {
	return add(xmlName, luceneName, 0);
    }
    Xml2Lucene add(String sameName) {
	return add(sameName, sameName, 0);
    }
    Xml2Lucene ignore(String xmlName) {
	return add(xmlName, xmlName, Flags.IGNORE);
    }
    Xml2Lucene recurse(String xmlName) {
	return add(xmlName, xmlName, Flags.RECURSE);
    }
    
    void process(Element e, org.apache.lucene.document.Document doc) 
	throws IOException  	{
	
	String name0 = e.getNodeName();  
	// Disposition plan
	Xml2Lucene q = map.get(name0);
	if (q==null) {
	    if (XMLtoHash.debug) Logger.log("There are no instructions for processing XML element '"+name0+"'; ignoring it and any children");
	    return;
	}
	if ((q.flags & Flags.IGNORE)!=0) return;
	if ((q.flags & Flags.RECURSE)!=0) {
	    // expect that all children are elements, and process each one
	    for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
		if (n instanceof Text  && n.getNodeValue().trim().equals("")) {
		    // white space; ignore
		} else if (n instanceof Element) {
		    process( (Element)n, doc);
		} else {
		    throw new IOException("Expected that all children of '" + name0+ 
					  "' will be elements or white space; found " + n);
		}
	    }
	    return;
	}
	// General or special conversion for XML
	String text=q.h.convertElement(e);
	// special treatment for some fields 
	text = q.h.convertText(text);
	
	boolean doIndex = ((q.flags & Flags.NOT_ANALIZED)==0);
	Field field =
	    new Field(q.luceneName, text, Field.Store.YES, 
		      (doIndex? Field.Index.ANALYZED: Field.Index.NOT_ANALYZED),
		      (doIndex? Field.TermVector.YES: Field.TermVector.NO));
	
	/** Special parsing required for categories, to preserve hyphens */
	if (q.luceneName.equals(ArxivFields.CATEGORY)) {
	    field.setTokenStream(new SubjectTokenizer(text));
	}

	doc.add(field);	
    }


    static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");


    /** Sets conversion rules for various fields. Note the special
	treatment of the time field: the numbers coming via OAI2, such
	as 2012-06-01 should be understood as midnight on EST, while
	strings inside Lucene are interpreted (by
	org.apache.lucene.document.DateTools) as GMT time stamps.
     */
    static  XMLtoLucene makeMap( ) {
	XMLtoLucene  map = new 	XMLtoLucene();

	map.add( "setSpec", "group").
	    setHandler(new FieldHandler() {
		    String convertText(String text) {
			// only save the first half
			String z[] = text.split(":");
			return (z.length>0) ? z[0] : text;
		    }});
	map.ignore("identifier");
	map.add("id", ArxivFields.PAPER, Flags.NOT_ANALIZED);
	map.add("created",  ArxivFields.DATE, Flags.NOT_ANALIZED).
	    setHandler(new FieldHandler() {
		    String convertText(String text) {
			//			return text.replaceAll("-", "") + "0000";
			String s="";
			try {
			    // in as local time (EST)
			    Date d = dateFormat.parse(text);
			    // out as GMT
			    s=DateTools.dateToString(d,DateTools.Resolution.DAY);
			} catch( java.text.ParseException ex) {}
			return s;
		    }});
	map.add(ArxivFields.AUTHORS).
	    setHandler(new AuthorsHandler(false));
	map.add(ArxivFields.TITLE);
	map.add(ArxivFields.COMMENTS);
	map.add("journal-ref");
	map.add(ArxivFields.ABSTRACT);

	map.add("categories",ArxivFields.CATEGORY);

	map.ignore("updated");
	map.ignore("datestamp");
	map.ignore("report-no>");
	map.ignore("doi");

	map.recurse(ArxivImporter.Tags.RECORD);
	map.recurse(ArxivImporter.Tags.HEADER);
	map.recurse(ArxivImporter.Tags.METADATA);
	map.recurse("arXiv");
	return map;
    }

}

