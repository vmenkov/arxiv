package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/*
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
*/
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;


import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.*;
import java.text.SimpleDateFormat;
//import java.util.regex.*;
import java.io.*;
import java.net.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

 /** The application for pulling data from the main arxiv server using
     the OAI interface, and importing them into our server's Lucene
     datastore.
 */
public class ArxivImporter {

    static private XMLtoLucene  xml2luceneMap = XMLtoLucene.makeMap();

    static class Tags {
	final static String RECORD = "record", HEADER="header", METADATA="metadata";
    }

    /** Returns null if doc is deleted */
    public static org.apache.lucene.document.Document parseRecordElement(Element e)  throws IOException {
	Element header = XMLUtil.findChild(e, Tags.HEADER, false);
	if (header!=null) {
	    String status=header.getAttribute("status");
	    if (status!=null && status.equals("deleted")) return null;
	}

	org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
	XMLUtil.assertElement(e,Tags.RECORD);
	xml2luceneMap.process(e, doc);
	return doc;

    }

    private IndexWriter makeWriter() throws IOException {
	IndexWriterConfig iwConf = new IndexWriterConfig(Common.LuceneVersion, analyzer);
	iwConf.setOpenMode(IndexWriterConfig.OpenMode.APPEND);	
	return new IndexWriter(indexDirectory, iwConf);
    }

    /** Parses an OAI-PMH element
	@return the resumption token
    */
    private String parseResponse(Element e, IndexWriter writer , boolean rewrite)  throws IOException {
	//org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
	XMLUtil.assertElement(e,"OAI-PMH");
	Element listRecordsE = null;
	for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (n instanceof Element && n.getNodeName().equals("ListRecords")) {
		listRecordsE = ( Element )n;
		break;
	    }
	}
	String token=null;

	for(Node n = listRecordsE.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (!(n instanceof Element)) continue;
	    String name = n.getNodeName();
	    if (name.equals(Tags.RECORD)) {
		importRecord((Element)n, writer, rewrite); 
	    } else if (name.equals("resumptionToken")) {
		// <resumptionToken cursor="0" completeListSize="702029">245357|1001</resumptionToken>
		Node nx = n.getFirstChild();
		if (nx instanceof Text) {
		    token=nx.getNodeValue();
		} else {
		    System.out.println("cannot parse the 'token' element: "+nx);
		}
	    }
	}

	return token;

    }


     private String  bodySrcRoot, bodyCacheRoot=Options.get("CACHE_DIRECTORY"),
	metaCacheRoot=Options.get("METADATA_CACHE_DIRECTORY");

    private Directory indexDirectory;
    /** Used in rewrite==false mode, to look up already existing entries */
    private IndexSearcher searcher; 

   /** List of doc ids of documents whose metadata we have harvested
       via OAI, but for which we could not find cached bodies on osmot
      */
    Vector<String> missingBodyIdList=new  Vector<String>();
    private Analyzer analyzer = new StandardAnalyzer(Common.LuceneVersion);

    int pcnt=0;

    public ArxivImporter() throws IOException{
	indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	searcher = new IndexSearcher( indexDirectory); 
    }

    public void setBodySrcRoot(String _bodySrcRoot)  {
	bodySrcRoot=_bodySrcRoot;
    }

    /** finds the body file in the specified dir tree, and reads it in if available
     */
    private String readBody( String id,  String bodySrcRoot) {
	String doc_file = Cache.getFilename(id , bodySrcRoot);
	System.out.println("id="+id+", body at " + doc_file);

	if (doc_file==null) {
	    System.out.println("No Document file " + doc_file + " missing.");
	    return null;
	} 
	File f = new File( doc_file);
	if (!f.canRead()) {
	    System.out.println("Document file " + doc_file + " missing.");   
	    return null;
	}
	try {
	    return Indexer.parseDocFile(doc_file);
	} catch (IOException E) {
	    System.out.println("Failed to read document file " + doc_file);
	    return null;
	}	
    }

    /** Takes an XML "record" element from the OAI feed, finds
	matching body file, and saves all the data as
	appropriate. This involves creating a Document object (with
	everything parsed and indexed in it) and storing it in the
	Lucene index, as well as caching both the abstract and the
	document body in their respective caches, (We have one cache
	for doc bodies, and one for metdata/abstracts).

	@param The metadata in the form of an XML format, as received
	from arxiv.org using the OAI2 inteface.

	@param writer A Lucene IndexWriter for the index to which the
	document will be added.

	@param rewrite If false, we leave alone documents that are
	already stored. If true, the stored doc is re-written. (FIXME:
	We probably should fine-tune this, adding some kind of "smart
	rewrite" option, when the document will be re-written only 
	when there are good reasons to believe it has actually changed
	since the last caching.)
    */
    public void importRecord(Element record, IndexWriter writer, boolean rewrite) throws IOException {
	org.apache.lucene.document.Document doc = parseRecordElement( record);
	if (doc==null) {
	    // deleted status
	    System.out.println("Ignoring deleted document");
	    return;
	}

	String paper = doc.get(ArxivFields.PAPER);

	if ( paper==null) {
	    // an occasional problematic doc, like this one:
	    // http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:0901.4014
	    System.out.println("Failed to extract id from the record. doc="+doc);
	    return;
	}

	Cache metacache = new Cache(metaCacheRoot);	
	metacache.setExtension(".xml");

	if (!rewrite) {
	    // see if the doc already exists (both the Lucene entry, and the cached body and metadata)
	    TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, paper));
	    TopDocs 	 top = searcher.search(tq, 1);
	    if (top.scoreDocs.length >0) {
		// already exists
		if (metacache.fileExists(paper) && 
		    (new Cache( bodyCacheRoot)).fileExists(paper) ) {
		    System.out.println("skip already stored doc, id=" + paper);
		    return;
		} else {
		    System.out.println("Lucene entry exists, but no cached data, for doc id=" + paper);
		}
	    }
	}

	String whole_doc = readBody( paper,  bodySrcRoot);
   
	if (whole_doc!=null) {
	    doc.add(new Field(ArxivFields.ARTICLE, whole_doc, Field.Store.NO, Field.Index.ANALYZED,  Field.TermVector.YES));

	    // Date document was indexed
	    doc.add(new Field("dateIndexed",
			      DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND),
			      Field.Store.YES, Field.Index.NOT_ANALYZED));		
	    // Set article length
	    doc.add(new Field("articleLength", Integer.toString(whole_doc.length()), Field.Store.YES, Field.Index.NOT_ANALYZED));
	} else {
	    missingBodyIdList.add(paper);
	}

	System.out.println("id="+paper);//+", Doc = " + doc);


	// write data to Lucene, and cache the doc body
	Indexer.processDocument(doc, 
			whole_doc==null? null: Cache.getFilename(paper, bodySrcRoot),
			true, writer, bodyCacheRoot);

	pcnt++;
	// cache the metadata - unless, of course, we ARE reading from the 
	// metadata cache!
	if (!readingMetacache) {
	    metacache.cacheDocument(paper, XMLUtil.XMLtoString(record));
	}
    }

    private static boolean mustRetry(HttpURLConnection conn )throws IOException {
	int code = conn.getResponseCode();
	if (code!=HttpURLConnection.HTTP_UNAVAILABLE  ) return false;

	String ra = conn.getHeaderField("Retry-After");
	//conn.close();
	System.out.println("got code "+code+", Retry-After="+ra);
	int interval=-1; // in seconds
	Date d = null;
	   
	try {
	    interval	=Integer.parseInt(ra);
	} catch(Exception ex) {}
	long msec1= System.currentTimeMillis();
	long msec2=msec1;
	
	if (interval>=0) {
	    msec2=msec1+ 1000 * interval;
	} else {
	    try {
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		d = format.parse(ra);
		msec2=d.getTime();  	
		if (msec2<= msec1) {
		     msec2=msec1+ 1000*60;
		}
	    } catch(Exception ex) {
		msec2=msec1+ 1000 * 60;
	    }
	}

	do {
	    try {
		System.out.println("sleep "+(msec2-msec1)+" msec...");    
		Thread.sleep(msec2-msec1);
	    } catch( InterruptedException ex) {}
	    msec1= System.currentTimeMillis();
	} while(msec1 < msec2);
	return true;
    }

    private static Element getPage(String us ) throws IOException,  org.xml.sax.SAXException {
	URL url = new URL(us);

	HttpURLConnection conn;
	do {
	    conn = (HttpURLConnection)url.openConnection();  
	    conn.setFollowRedirects(true) ;
	    conn.setRequestProperty("User-Agent", "arXiv_xs-Importer/0.1");
	} while( mustRetry(conn));

	int code = conn.getResponseCode();
 
	if (code!=HttpURLConnection.HTTP_OK ) {
	    System.out.println("got code "+code+", msg="+conn.getResponseMessage() +"; aborting import");
	    throw new IOException("got code "+code + " from "+url );
	}
	BufferedReader in = 
	    new BufferedReader(  new InputStreamReader(conn.getInputStream()));

	Element e = XMLUtil.readElement(in);
	return e;
    }

    /** pulls in all pages */
    public void importAll()  throws IOException,  org.xml.sax.SAXException {
	importAll(null, -1, true, null);
    }

    /** As per http://www.openarchives.org/OAI/2.0/openarchivesprotocol.htm

http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:0901.4014
    */
    private static String makeURL(String tok, String from) {
	String base="http://export.arxiv.org/oai2?verb=ListRecords";
	if  (tok!=null) {
 	    return base + "&resumptionToken="+tok;
	}
	String s = base + "&metadataPrefix=arXiv";
	if (from !=null) s +=  "&from="+from;
	return s;
    }

    /** pulls in all pages (or only some)
	@param tok start harvesting from the beginning (if null), or from this resumption token (otherwise)
	@param max if max non-negative, then ...; -1 means "all"
	@param from "YYYY-MM-DD", passed to OAI2 from=... option
     */
    public void importAll(String tok, int max, boolean rewrite, String from)  throws IOException,  org.xml.sax.SAXException {
	int pagecnt=0;

	IndexWriter writer =  makeWriter(); 

	try {

	while( max<0 || pagecnt < max) 	 {
	    String us = makeURL( tok, from);
	    System.out.println("Requesting: " + us);
	    Element e = getPage(us);	    
	    tok = parseResponse(e, writer, rewrite);
	    pagecnt++;
	    System.out.println("done "+pagecnt+" pages, token =  " + tok);
	    if (tok==null || tok.trim().equals("")) break;
	}

	writer.optimize();
	} finally {
	    writer.close();
	}
    }

    /** A flag that tells the importer not to try to rewrite the
	metadata cache, because we are reading from the metadata cache
	now!
     */
    private boolean readingMetacache=false;
    
    private void processDir( File root,IndexWriter writer, boolean rewrite)
	throws IOException   {
	System.out.println("Processing directory " + root);
	if (!root.isDirectory()) throw new IllegalArgumentException("'" + root + "' is not a directory");
	File[] children = root.listFiles();
	for(File f: children) {
	    if (f.isFile()) {
		System.out.println("Processing file: " + f);
		try {
		    Element e = XMLUtil.readFileToElement(f);
		    if (e.getNodeName().equals(Tags.RECORD)) {
			importRecord(e, writer , rewrite);	
		    } else {
			System.out.println("Not a 'record' element in file "+f);
			//tok = imp.parseResponse(e, writer , rewrite);
			//System.out.println("resumptionToken =  " + tok);
		    }
		} catch(Exception ex) {
		    System.out.println("Failure on file: " + f);
		    System.out.println(ex);			
		}
		
	    }
	}
	for(File f: children) {
	    if (f.isDirectory()) {
		processDir( f, writer, rewrite);
	    }
	}
    }


    static void usage() {
	usage(null);
    }

    static void usage(String m) {
	System.out.println("Arxiv Importer Tool");
	System.out.println("Usage: java [options] ArxivImporter all [max-page-cnt]");
	System.out.println("Options:");
	System.out.println(" [-Dtoken=xxx]");

	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }

    /** Options:
	<pre>
	-Dtoken=xxxx   Resume from the specified resumption page
	-Drewrite=[true|false]  Default is true; if false, already stored pages are not modified  	
	-Dfrom=YYYY-MM-DD
	</pre>
     */
    static public void main(String[] argv) throws IOException, SAXException {
	if (argv.length==0) usage();
	ParseConfig ht = new ParseConfig();
	String tok=ht.getOption("token", null);
	boolean rewrite =ht.getOption("rewrite", true);
	String from=ht.getOption("from", null);

	Options.init(); // read the legacy config file

	ArxivImporter imp =new  ArxivImporter();
	imp.setBodySrcRoot( "../arXiv-text/");

	if (argv.length==0) return;

	if ( argv[0].equals("all")) {
	    int max=-1;
	    if (argv.length>1) {
		try {
		    max	=Integer.parseInt(argv[1]);
		} catch(Exception ex) {}
	    }
	    System.out.println("Processing web data, up to "+max + " pages");
	    imp.importAll(tok, max, rewrite, from);
	} else if (argv[0].equals("allmeta")) {
	    if (!rewrite) throw new IllegalArgumentException("For a reload from metachache, ought to use -Drewrite=true");
	    imp.readingMetacache=true;
	    IndexWriter writer =  imp.makeWriter(); 
	    imp.processDir( new File(imp.metaCacheRoot), writer, rewrite );
	    writer.optimize();
	    writer.close();
	} else {
	    // processing files
	    for(String s: argv) {
	 	System.out.println("Processing " + s);
		Element e = XMLUtil.readFileToElement(s);
		IndexWriter writer =  imp.makeWriter(); 
		if (e.getNodeName().equals(Tags.RECORD)) {
		    imp.importRecord(e, writer , rewrite);
		} else {
		    tok = imp.parseResponse(e, writer , rewrite);
		    System.out.println("resumptionToken =  " + tok);
		}
		writer.optimize();
		writer.close();
	    }
	}
	System.out.println("imported "+imp.pcnt+" docs, among which missing body files for "+ imp.missingBodyIdList.size() +" docs");
	PrintWriter fw = new PrintWriter(new FileWriter("missing.txt"));
	for(String s: imp.missingBodyIdList){
	    fw.println(s);
	}
	fw.close();

    }


}