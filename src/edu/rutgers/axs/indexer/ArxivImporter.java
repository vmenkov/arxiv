package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;


import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.*;
import java.net.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.Logging;

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

    /** Parses an OAI-PMH element, and triggers appropriate operations.
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

	if (listRecordsE == null) return token; // empty response

	for(Node n = listRecordsE.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (!(n instanceof Element)) continue;
	    String name = n.getNodeName();
	    if (name.equals(Tags.RECORD)) {
		importRecord((Element)n, writer, rewrite); 
	    } else if (name.equals("resumptionToken")) {
		// <resumptionToken cursor="0" completeListSize="702029">245357|1001</resumptionToken>
		Node nx = n.getFirstChild();
		if (nx==null) {
		    System.out.println("Token is null; this must have been the last page");
		} else  if (nx instanceof Text) {
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

    /** Pulls in the article body over HTTP from
	search1.rutgers.edu. (This is enabled by them soft-linking
	their cache directory into their web server's file system, as
	per VM's request; 2012-01-16.
     */
    boolean getBodyFromWeb(String id, File storeTo) throws IOException
    {
	String doc_file = Cache.getFilename(id , "arXiv-cache");
	if (doc_file==null) {
	    System.out.println("Cannot figure remote file name for article id=" + id);
	    return false;
	} 
	String lURLString= "http://search.arxiv.org:8081/" + doc_file;
	URL lURL    =new URL( lURLString);
	//	Logging.info("FilterServlet requesting URL " + lURL);
	HttpURLConnection lURLConnection;
	try {
	    lURLConnection=(HttpURLConnection)lURL.openConnection();	
	    lURLConnection.setFollowRedirects(false); 
	    lURLConnection.connect();
	}  catch(Exception ex) {
	    String msg= "Failed to open connection to " + lURL;
	    //  Logging.error(msg);
	    //ex.printStackTrace(System.out);
	    System.out.println(msg);
	    return false;
	}
    
	int code = lURLConnection.getResponseCode();
	String gotResponseMsg = lURLConnection.getResponseMessage();

	//	Logging.info("code = " + code +", msg=" + gotResponseMsg);

	if (code != HttpURLConnection.HTTP_OK) {
	    String msg = "Response code=" + code + " for url " + lURL;
	    System.out.println(msg);
	    return false;
	}

	final int ChunkSize = 8192;
	int lContentLength = lURLConnection.getContentLength();
	// e.g.  "Content-Type: text/html; charset=ISO-8859-4"
	String lContentType = lURLConnection.getContentType();
    //	Logging.info("pi=" + pi + ", content-type=" +  lContentType);

	InputStream is=null;	
	try {
	    is = lURLConnection.getInputStream();
	}  catch(Exception ex) {
	    String msg= "Failed to obtain data from " + lURL;
	    System.out.println(msg);
	    return false;
	}
	if (is==null) {	// for errors such as 404, we get ErrorStream instead
	    String msg= "Failed to obtain data IS from " + lURL;
	    System.out.println(msg);
	    return false;
	}

	// simple bytewise copy
	int M = 4096;

	BufferedInputStream in= new BufferedInputStream(is, M*4);
	FileOutputStream aout = new FileOutputStream(storeTo);

	byte [] buf = new byte[ M ];
	while( true ) {
	    int n = in.read(buf);
	    if (n <= 0) break; // eof
	    aout.write(buf, 0, n);
	}

	in.close();
	aout.close();
	return true;
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
	    System.out.println("Cannot figure file name for article id=" + id);
	    return null;
	} 
	File f = new File( doc_file);

	if (!f.exists()) {
	    // can we get it from the web?
	    File g = f.getParentFile();
	    if (g!=null && !g.exists()) {
		boolean code = g.mkdirs();
		System.out.println("Creating dir " + g + "; success=" + code);
	    }
	    try {
		boolean code=getBodyFromWeb( id, f);
		System.out.println("Tried to get data from the web for  document file " + doc_file +", success=" + code);
	    } catch (IOException E) {
		System.out.println("Failed to copy data from the web for  document file " + doc_file);
		return null;
	    }	
	}

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

	if (!rewrite || fixCatsOnly) {
	    // see if the doc already exists (both the Lucene entry, and the cached body and metadata)

	    TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, paper));
	    TopDocs 	 top = searcher.search(tq, 1);
	    if (top.scoreDocs.length >0) {
		boolean isCached = metacache.fileExists(paper) && 
		    (new Cache( bodyCacheRoot)).fileExists(paper);

		if (!rewrite) {
		    if (isCached) {
			System.out.println("skip already stored doc, id=" + paper);
			return;
		    } else {
			System.out.println("Lucene entry exists, but no cached data, for doc id=" + paper);
		    }
		} else if (fixCatsOnly) {
		    // special mode: for articles already present, we rewrite
		    // them only when the format of categories to be stored
		    // should be changed

		    // FIXME: is there an easier way?
		    IndexReader reader = IndexReader.open(writer, false);

		    if (catsMatch(doc, top.scoreDocs[0].doc, reader)) {
			System.out.println("skip already stored doc with matching cats, id=" + paper);
			return;
		    }
		    reader.close();
		}
	    }
	}
	    

	String whole_doc = readBody( paper,  bodySrcRoot);
   
	if (whole_doc!=null) {
	    doc.add(new Field(ArxivFields.ARTICLE, whole_doc, Field.Store.NO, Field.Index.ANALYZED,  Field.TermVector.YES));

	    // Date document was indexed
	    doc.add(new Field(ArxivFields.DATE_INDEXED,
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

    private static boolean catsMatch(org.apache.lucene.document.Document newDoc, int oldDocno, IndexReader reader) throws IOException{
	String s = newDoc.get(ArxivFields.CATEGORY);
	if (s==null) return false;
	String[] newCats = s.split("\\s+");
	Arrays.sort(newCats);

	TermFreqVector tfv=reader.getTermFreqVector(oldDocno, ArxivFields.CATEGORY);
	String[] oldCats=tfv.getTerms();	    
	Arrays.sort(oldCats);

	if (newCats.length != oldCats.length) return false;
	for(int i=0; i< oldCats.length; i++) {
	    if (!newCats[i].equals(oldCats[i])) return false;
	}
	return true;
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

    /** Plls in all pages, or all pages in a date range.
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
		System.out.println("At "+new Date()+", requesting: " + us);
		Element e = getPage(us);	    
		tok = parseResponse(e, writer, rewrite);
		pagecnt++;
		System.out.println("done "+pagecnt+" pages, token =  " + tok);
		if (tok==null || tok.trim().equals("")) break;

		if (pagecnt % 1 == 0) {
		    System.out.println("At "+new Date()+", re-opening index... ");
		    writer.close();
		    writer =  makeWriter(); 
		}

	    }

	    if (optimize) {
		System.out.println("At "+new Date()+", optimizing index... ");
		writer.optimize();
	    }
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

    /** No more than one option, -Dfrom=YYYY-MM-DD or -Ddays=n, can be
	supplied. If -Dfrom is supplied, uses it; otherwise, looks for
	-Ddays and create an equivalent "from" value. The a assumption
	is that we're in the same timezone with the OAI2 server...

	@return The value of the -Dfrom=YYYY-MM-DD, or its equivalent
	computed from -Ddays=nnn (as today-days). Null if neither
	option is supplied.*/
    private static String getFrom(ParseConfig ht) {
	final String FROM="from", DAYS="days";
	String from=ht.getOption(FROM, null);
	if (ht.getOption(DAYS,null)==null) return from;
	if (from!=null) {
	    throw new IllegalArgumentException("Can't supply -Dfrom and -Ddays simultaneously!");
	}
	int days=ht.getOption(DAYS, 0);
	if (days<=0)  throw new IllegalArgumentException("-Ddays=nnn, if supplied, must be positive!");
	Date d = new Date();
	long msec = d.getTime() - days * 24L * 3600L * 1000L;
	d.setTime(msec);
	final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
	from=fmt.format( d );
	Logging.info("Converted days=" + days + " to from=" + from);
	return from;
    }

    static boolean  optimize=true;
    static boolean fixCatsOnly = false;

    /** Options:
	<pre>
	-Dtoken=xxxx   Resume from the specified resumption page
	-Drewrite=[true|false]  Default is true; if false, already stored pages are not modified  	
	-Dfrom=YYYY-MM-DD | -Ddays=3
	-Doptimize=true
	</pre>
     */
    static public void main(String[] argv) throws IOException, SAXException {
	if (argv.length==0) usage();
	ParseConfig ht = new ParseConfig();
	String tok=ht.getOption("token", null);
	boolean rewrite =ht.getOption("rewrite", true);
	String from=getFrom(ht); // based on "from" and "days"
	optimize =ht.getOption("optimize", optimize);
	fixCatsOnly = ht.getOption("fixCatsOnly", false);

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
	    System.out.println("Processing web data, up to "+max + " pages; from=" + from);
	    imp.importAll(tok, max, rewrite, from);
	} else if (argv[0].equals("allmeta")) {
	    if (!rewrite) throw new IllegalArgumentException("For a reload from metachache, ought to use -Drewrite=true");
	    imp.readingMetacache=true;
	    IndexWriter writer =  imp.makeWriter(); 
	    imp.processDir( new File(imp.metaCacheRoot), writer, rewrite );
	    if (optimize) {
		System.out.println("Optimizing index... ");
		writer.optimize();
	    }
	    writer.close();
	} else if (argv[0].equals("files")) {
	    // processing files
	    IndexWriter writer =  imp.makeWriter(); 
	    for(int i=1; i<argv.length; i++) {
		String s= argv[i];
	 	System.out.println("Processing " + s);
		Element e = XMLUtil.readFileToElement(s);
		if (e.getNodeName().equals(Tags.RECORD)) {
		    imp.importRecord(e, writer , rewrite);
		} else {
		    tok = imp.parseResponse(e, writer , rewrite);
		    System.out.println("resumptionToken =  " + tok);
		}
	    }
	    if (optimize) {
		System.out.println("Optimizing index... ");
		writer.optimize();
	    }
	    writer.close();
	} else {
	    System.out.println("Unrecognized command: " + argv[0]);
	}
	System.out.println("imported "+imp.pcnt+" docs, among which missing body files for "+ imp.missingBodyIdList.size() +" docs");
	PrintWriter fw = new PrintWriter(new FileWriter("missing.txt"));
	for(String s: imp.missingBodyIdList){
	    fw.println(s);
	}
	fw.close();

    }


}

