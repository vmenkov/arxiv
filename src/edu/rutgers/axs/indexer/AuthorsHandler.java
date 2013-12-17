package edu.rutgers.axs.indexer;

import java.util.Collection;
import java.util.Iterator;

import java.util.*;
import java.util.regex.*;
import java.io.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/** Converts something like this to a comma-separated list:
<authors>
<author><keyname>Balázs</keyname><forenames>C.</forenames></author>
<author><keyname>Berger</keyname><forenames>E. L.</forenames></author>
<author><keyname>Nadolsky</keyname><forenames>P.M.</forenames></author>
<author><keyname>Yuan</keyname><forenames>C. -P.</forenames>
<affiliation>IRISA - UBS</affiliation>
</author>
</authors>
*/
class AuthorsHandler extends FieldHandler {

    private XMLtoHash processAuthorTool = new XMLtoHash();
    AuthorsHandler(boolean doAffiliations) {
	processAuthorTool.recurse("author");
	processAuthorTool.add("keyname");
	processAuthorTool.add("forenames");
	processAuthorTool.add("suffix");
	if (doAffiliations) {
	    processAuthorTool.add("affiliation");
	} else {
	    processAuthorTool.ignore("affiliation");
	}
    }

    String convertElement(Element e) throws IOException { 
	XMLUtil.assertElement(e,"authors");
	String s = "";
	for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (n instanceof Text  && n.getNodeValue().trim().equals("")) {
		// white space; ignore
		continue;
	    }

	    XMLUtil.assertElement(n,"author");
	    HashMap<String, String> map=  processAuthorTool.process((Element)n);
	    String first=map.get("forenames"), last=map.get("keyname"), suffix=map.get("suffix");
	    if (s.length()>0) s += ", ";
	    if (first!=null) s+= first + " ";
	    if (last!=null) s += last;
	    if (suffix!=null) s+= ", "+suffix;
	    String affiliation = map.get("affiliation");
	    if (affiliation!=null) s+= " (" + affiliation + ")";
	}
	return s;
    }
}


