package edu.rutgers.axs.upload;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.nio.charset.Charset;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

/** Extracting links from an HTML document. 

    <p>This code is loosely based on the example at
http://www.java2s.com/Tutorial/Java/0120__Development/UsejavaxswingtexthtmlHTMLEditorKittoparseHTML.htm
*/
public class HTMLParser {

    /** Testing the parser */
    public static void main(String[] args) throws Exception {
	String[] q = (args.length>0)?args:new String[]{"http://www.java2s.com"};
	for(String u: q) {	    	    
	    URL url = new URL(u);	    
	    InputStream in = url.openStream();
	    Outliner qlinks = parse(url, in);
	    stats(qlinks);
	}
    }

    
    public static Outliner parse(URL url, InputStream in) throws IOException {
	return parse(url, in, null);
    }

    /** Reads the document from a given input stream, and extracts the URL links

	@param url The URL at which the document is located. This is
	used to reconstruct absolute link URLs out of relative ones
	that may be found in the document.  If null is passed, only absolute
	link URLs will be extracted from the document, and relative ones will 
	have to be ignored.
	
	@param in An opened input stream through which the document can be read in.
     */
    public static Outliner parse(URL url, InputStream in, Charset cs) throws IOException {
	if (cs==null) {
	    String encoding = "ISO-8859-1";
	    cs =  Charset.forName(encoding);
	}
	ParserGetter kit = new ParserGetter();
	HTMLEditorKit.Parser parser = kit.getParser();
	InputStreamReader r = new InputStreamReader(in, cs);
	Outliner callback = new Outliner(url);
	
	System.out.println("Using Outliner");
	parser.parse(r, callback, true);
	return   callback; //callback.getLinks();
    }

    /** Prints a simple report */
    static void stats(Outliner z) {
	Vector<URL> links = z.getLinks();
	System.out.println(links.size() + "links found:");
	int i=0;
	for(URL q: links) {
	    i++;
	    System.out.println("" + i + ". " + q);
	}
 	Vector<String> errors = z.getErrors();
	System.out.println(errors.size() + " errors occurred:");
	i = 0;
 	for(String q: errors) {
	    i++;
	    System.out.println("" + i + ". " + q);
	}
   }


    /* This class is created just to expose HTMLEditorKit.getParser() */
    static class ParserGetter extends HTMLEditorKit {
	public HTMLEditorKit.Parser getParser() {
	    return super.getParser();
	}
    }

    /** Looks for "A" elements in the document, and saves the links
	found in them.
     */
    public static class Outliner extends HTMLEditorKit.ParserCallback {

	private Vector<URL> links = new Vector<URL>();
	private Vector<String> errors = new Vector<String>();

	/** Found linked, successfully interpreted as URLs  */
	Vector<URL> getLinks() {return  links; }
	/** Errors for links that could not be resolved as URLs */
	Vector<String> getErrors() {return  errors; }

	private URL baseURL;

	/** @param baseURL The URL in whose context links found in the
	    document are to be interepreted. The value of null is
	    allowed, in which case all links found in the document
	    need to be absolute. */ 
	public Outliner(URL _baseURL) {
	    this.baseURL =  _baseURL;
	}

	/** Checks if the tag is an "A" tag; if so, extracts and
	    processes the URL in the "HREF" attribute. If it's
	    relative, the URL is converted to an absolute URL using
	    the document's stored base URL.  URLs with the "javascript:"
	    protocols are explicitly ignored, to avoid the URL constructor
	    throwing an exception.
	 */
	public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
	    
	    if (tag == HTML.Tag.A) {
		String x = (String)attributes.getAttribute(HTML.Attribute.HREF);
		if (x==null) return;
		if (x.startsWith("javascript:")) return;
		try {
		    URL u = new URL(baseURL, x);
		    links.add(u);
		} catch( java.net.MalformedURLException ex) {
		    String msg="Cannot resolve the URL in the link '" + x +
			"' in the context '"+baseURL+"'";
		    errors.add(msg);
		}
	    }
	}
    
	public void handleEndTag(HTML.Tag tag, int position) {
	    // work around bug in the parser that fails to call flush	
	    //if (tag == HTML.Tag.HTML) this.flush();
	}
	
	public void handleText(char[] text, int position) {
	}
    }

}


