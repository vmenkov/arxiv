package edu.rutgers.axs.indexer;

import java.util.*;
//import java.util.regex.*;
import java.io.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;


/** This class lists the default (trivial) transformations for the
    content of the fields. Methods may be overriden for some fields that
    need special treatment.
*/
class FieldHandler {
    String convertText(String s) { return s; }
    String convertElement(Element e) throws IOException { 
	String text="";
	for(Node n = e.getFirstChild(); n!=null; n=n.getNextSibling()) {
	    //String name = n.getNodeName();
	    if (n instanceof Text) {
		if (text.length() > 0) text += "; ";
		text += n.getNodeValue();
	    } else {
		throw new IOException("Expected that element '" + e.getNodeName()+ 
				      "' will only have a TEXT child; found " + n);
		}
	}
	return text;
    }

    static FieldHandler trivial =new  FieldHandler();

};

    