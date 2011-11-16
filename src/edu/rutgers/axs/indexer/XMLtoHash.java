package edu.rutgers.axs.indexer;

import java.util.*;
//import java.util.regex.*;
import java.io.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;


/** an auxiliary class for parsing XML
 */

public class XMLtoHash   {

    /** Flags for Xml2Lucene objects */
    static class Flags {
	static final int IGNORE=1, RECURSE=2;//, FLATTEN=4;
    }

 	
    /** An instance of this class determines how a particular field of
	the XML file is mapped
    */
    static class Action {
	/** A boolean OR of some Flag constants */
	int flags;
	/** The name of the field in the OAI XML feed */
	String xmlName;
	/** The name under which we want to store the field in the hash table */
	String hashName;
	Action(String xml,String lu,int  _flags) {
	    xmlName = xml;
	    hashName=lu;
	    flags=_flags;	
	}
	FieldHandler h= FieldHandler.trivial;
	Action setHandler(FieldHandler _h) {
	    h = _h;
	    return this;
	}
    }

    HashMap<String, Action> map = new HashMap<String, Action>();

    Action add(String xmlName, String hashName, int flags) {
	Action e = new  Action( xmlName,hashName,flags);
	map.put( xmlName, e);
	return e;
    } 
    Action add(String xmlName, String luceneName) {
	return add(xmlName, luceneName, 0);
    }
    Action add(String sameName) {
	return add(sameName, sameName, 0);
    }
    Action ignore(String xmlName) {
	return add(xmlName, xmlName, Flags.IGNORE);
    }
    Action recurse(String xmlName) {
	return add(xmlName, xmlName, Flags.RECURSE);
    }
    
    /** The main exposed method */
    HashMap<String, String> process(Element e)	throws IOException   {
	HashMap<String, String> doc=new HashMap<String, String> ();
	process(e,doc);
	return doc;
    }

    void process(Element e, HashMap<String, String> doc) 
    	throws IOException  
	{

	String name0 = e.getNodeName();  
	// Disposition plan
	Action q = map.get(name0);
	if (q==null) {
	    // Logger.log
	    System.err.println("There are no instructions for processing Element '"+name0+"'; ignoring it and any children");
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
	
	doc.put(q.hashName, text);
    }
}
