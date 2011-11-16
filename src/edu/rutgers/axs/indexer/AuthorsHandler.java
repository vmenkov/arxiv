package edu.rutgers.axs.indexer;
/*
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
*/
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
<author><keyname>Yuan</keyname><forenames>C. -P.</forenames></author>
</authors>
*/
class AuthorsHandler extends FieldHandler {

    static XMLtoHash processAuthorTool = new XMLtoHash();
    static {
	processAuthorTool.recurse("author");
	processAuthorTool.add("keyname");
	processAuthorTool.add("forenames");
	processAuthorTool.add("suffix");
	processAuthorTool.ignore("affiliation");

    }

    String convertElement(Element e) throws IOException { 
	XMLUtil.assertElement(e,"authors");
	String s = "";
	for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    XMLUtil.assertElement(n,"author");
	    HashMap<String, String> map=  processAuthorTool.process((Element)n);
	    String first=map.get("forenames"), last=map.get("keyname"), suffix=map.get("suffix");
	    if (s.length()>0) s += ", ";
	    if (first!=null) s+= first + " ";
	    if (last!=null) s += last;
	    if (suffix!=null) s+= ", "+suffix;
	}
	return s;
    }
}


