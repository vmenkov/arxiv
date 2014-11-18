package edu.rutgers.axs.indexer;

import java.util.*;
import java.io.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


class XMLUtil {

  /** Reads an entire file into an XML element. 

     @throws IOException E.g., if the file can't be opened
     @throws SAXException If the W3C DOM parser finds that the
     document isn't good XML
    */
    public static Element readFileToElement(File f)
 	throws IOException, SAXException
    {
	//Logging.info("Parsing XML file " + f.getPath());
	return readFileToElement(f.getPath());
    }



   /** Reads an entire file into an XML element. If one wants to use
     * the element later to parse as a dataset, the file must contain
     * a "dataset" element as its top-level element.

     @throws IOException E.g., if the file can't be opened
     @throws SAXException If the W3C DOM parser finds that the
     document isn't good XML
   */
    public static Element readFileToElement(String fname) 
	throws IOException, SAXException {
	if (!(new File(fname)).exists()) {
	    throw new IllegalArgumentException("Input file " + fname + 
					       " does not exist");
	}
	DOMParser parser = new DOMParser();
	parser.parse(fname);
	org.w3c.dom.Document doc = parser.getDocument();
	//System.out.println("Read in document: "+doc);
	Element e = doc.getDocumentElement();
	return e;
    }

    public static Element readElement(Reader r) 
	throws IOException, SAXException {
	DOMParser parser = new DOMParser();
	parser.parse(new InputSource(r)  );
	return parser.getDocument().getDocumentElement();
    }

    public static String XMLtoString(Element e) {
	StringWriter w =new StringWriter();
	writeXML( e, w);
	return w.toString();
    }





  /** An auxiliary method for Learner.saveAsXML() etc;
     * saves a prepared XML doc into a file */
    public static void writeXML(Document xmldoc, String fname) {
	try {
	    FileOutputStream fos = new FileOutputStream(fname);
	    writeXML( xmldoc.getDocumentElement(), fos);
	    fos.close();
	} catch (IOException ex) {
	    System.err.println("Exception when trying to write XML file " + fname + "\n" + ex);
	}
    }

    /** An auxiliary method for Learner.saveAsXML() etc;
      saves a prepared XML doc into a stream. This is a wrapper 
      around {@link #writeXML(Element e, OutputStream fos)}
    */
    public static void writeXML(Document xmldoc, OutputStream fos) {
	writeXML( xmldoc.getDocumentElement(), fos);
    }

    public static void writeXML(Document xmldoc, Writer w) {
	writeXML( xmldoc.getDocumentElement(), w);
    }

    /** Writes an XML element into a Writer, without closing it.

	This is essentially a wrapper around
	org.apache.xml.serialize.XMLSerializer( OutputStream,
	OutputFormat).
     */
    public static void writeXML(Element e, Writer w) {
	try {
	    OutputFormat of = new OutputFormat("XML","utf-8",true);
	    of.setIndent(1);
	    of.setIndenting(true);
	    //of.setDoctype(null,"bxr-eg.dtd");
	    XMLSerializer serializer = new XMLSerializer(w,of);
	    // As a DOM Serializer
	    serializer.asDOMSerializer();
	    serializer.serialize( e );
	    w.flush();
	} catch (IOException ex) {
	    System.err.println("Exception when trying to write XML document out:\n" + ex);
	}
    }

   public static void writeXML(Element e, OutputStream fos) {
	try {
	    OutputFormat of = new OutputFormat("XML","utf-8",true);
	    of.setIndent(1);
	    of.setIndenting(true);
	    //of.setDoctype(null,"bxr-eg.dtd");
	    XMLSerializer serializer = new XMLSerializer(fos,of);
	    // As a DOM Serializer
	    serializer.asDOMSerializer();
	    serializer.serialize( e );
	    fos.flush();
	} catch (IOException ex) {
	    System.err.println("Exception when trying to write XML document out:\n" + ex);
	}
    }

    
    static void assertElement(Node n) throws IOException {
	assertElement(n, null);
    }

    static void assertElement(Node n, String expectedName) throws IOException {
	if (!(n instanceof Element)) {
	    throw new IOException("Expected to find an XML element '"+expectedName+"', found node: "+n);
	}
	//int type = n.getNodeType();
	String name = n.getNodeName();
	
	if (expectedName!=null&& 
	    !name.equals(expectedName))  {
	    throw new IOException("Expected to find an '"+ expectedName+"' XML element, found element: "+n);
	}
    }

    /** Given an element, finds its child (or a descendant of any level, if in recursive mode) with a specified name.

    @param expectedName Looks for an element with this name
    @return The first matching child (or descendant), or null, if none is found.   
     */
    static Element findChild(Element e, String expectedName, boolean recursive) throws IOException {

	for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (n instanceof Element) {
		if (expectedName.equals(n.getNodeName())) return (Element)n;
	    }
	}
	if (recursive) {
	    for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
		if (n instanceof Element) {
		    Element c = findChild((Element)n, expectedName, recursive);
		    if (c!=null) return c;
		}
	    }
	}
	return null;
    }

}