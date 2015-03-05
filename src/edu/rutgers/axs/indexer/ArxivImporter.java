package edu.rutgers.axs.indexer;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.*;
import java.net.*;

import javax.persistence.*;

// stuff for handling XML
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

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

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.Logging;
import edu.rutgers.axs.sql.Article;
import edu.rutgers.axs.sql.Main;

 /** The application for pulling data from the main arxiv server using
     the OAI interface, and importing them into our server's Lucene
     datastore.

     <p>This application is quite flexible with respect to the sources
     of data and the list of documents to be imported. Some useful
     modes are listed below:

     <p>Metadata:
     <ul>
     <li>From the export.arxiv.org server, via the OAI2 interface
     <li>From preloaded XML files
     </ul>

     <p>Document bodies:
     <ul>

     <li>From search.arxiv.org (where, on our request, they have
     sym-linked their article cache directory into their web server
     document directory

     <li>From preloaded text files
     </ul>

     <p>List of documents to import:
     <ul>
     <li>From the export.arxiv.org server, via the OAI2 interface; restricted
     by date range, if desired
     <li>All files for which there are metadata XML files in a specified
     directory tree
     <li>From the command line or a document list file
     </ul>
  
 */
public class ArxivImporter {

    static private XMLtoLucene  xml2luceneMap = XMLtoLucene.makeMap();

    static class Tags {
	final static String RECORD = "record", HEADER="header", METADATA="metadata", IDENTIFIER = "identifier";
    }

    /** @param hie Something like this:  <identifier>oai:arXiv.org:0901.4014</identifier> 
	@return Something like oai:arXiv.org:0901.4014
     */
    private static String parseIdentifierTag( Element hie) {
	if (hie==null) return null;
	Node nx = hie.getFirstChild();
	return (nx instanceof Text) ? nx.getNodeValue() : null;
    }

    /** Returns null if doc is deleted */
    public static org.apache.lucene.document.Document parseRecordElement(Element e)  throws IOException {
	Element header = XMLUtil.findChild(e, Tags.HEADER, false);
	if (header!=null) {
	    String status=header.getAttribute("status");
	    // <identifier>oai:arXiv.org:0901.4014</identifier>
	    Element hie = XMLUtil.findChild(e, Tags.IDENTIFIER, false);
	    String oaiId = parseIdentifierTag(hie);
	    if (status!=null && status.equals("deleted")) {
		System.out.println("Found record for deleted document " + oaiId);
		return null;
	    }
	}

	org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
	XMLUtil.assertElement(e,Tags.RECORD);
	xml2luceneMap.process(e, doc);
	return doc;

    }

    private IndexWriter makeWriter() throws IOException {
	Logging.info("makeWriter, dir=" + indexDirectory);
	IndexWriterConfig iwConf = new IndexWriterConfig(Common.LuceneVersion, analyzer);
	iwConf.setOpenMode(IndexWriterConfig.OpenMode.APPEND);	
	return new IndexWriter(indexDirectory, iwConf);
    }

    /** Parses an OAI-PMH element, and triggers appropriate operations.
	@return the resumption token
    */
    private String parseResponse(Element e, 
				 //IndexWriter writer, IndexReader reader, 
				 boolean rewrite)  throws IOException {
	//org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

	IndexWriter writer =  makeWriter(); 
	// FIXME: is there an easier way?
	IndexReader reader = IndexReader.open(writer, false);

	try {

	XMLUtil.assertElement(e,"OAI-PMH");
	Element listRecordsE = null, getRecordE = null;
	for(Node n = e.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (n instanceof Element && n.getNodeName().equals("ListRecords")) {
		listRecordsE = ( Element )n;
		break;
	    } else if (n instanceof Element && n.getNodeName().equals("GetRecord")) {
		getRecordE = ( Element )n;
		break;
	    }
	}
	String token=null;

	Element outer = (listRecordsE != null) ? listRecordsE:  getRecordE;
	if (outer == null) return token; // empty response

	for(Node n = outer.getFirstChild(); n!=null; n = n.getNextSibling()) {
	    if (!(n instanceof Element)) continue;
	    String name = n.getNodeName();
	    if (name.equals(Tags.RECORD)) {
		importRecord((Element)n, writer, reader, rewrite); 
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

	} finally {
	    reader.close();
	    writer.close();
	}

    }

    private String  
     /** Location of pre-loaded article bodies (bulk copy) */
	 bodySrcRoot = Options.get("ARXIV_TEXT_DIRECTORY"),
     /** My.ArXiv article body cache */
	bodyCacheRoot = Options.get("CACHE_DIRECTORY"),
     /** My.ArXiv article metadata cache */
	metaCacheRoot = Options.get("METADATA_CACHE_DIRECTORY");

    private Directory indexDirectory;
    /** Used in rewrite==false mode, to look up already existing entries */
    private IndexSearcher searcher; 

   /** List of doc ids of documents whose metadata we have harvested
       via OAI, but for which we could not find cached bodies on osmot
      */
    Vector<String> missingBodyIdList=new  Vector<String>();
    private Analyzer analyzer = new StandardAnalyzer(Common.LuceneVersion);

    int pcnt=0;

    EntityManager em=null;


    public ArxivImporter() throws IOException{
	indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	searcher = new IndexSearcher( indexDirectory); 
	em  = Main.getEM();
    }

    /** Pulls in the article body over HTTP from
	search1.rutgers.edu. (This is enabled by Thorsten's people
	soft-linking their cache directory into their web server's
	file system, as per VM's request; 2012-01-16.)

	<P>Eventually, this ad hoc process should be replaced by simply
	reading the file from some directory on our own server,
	to which Paul Ginsparg's people will upload them regularly
	using rsync).

	@param id Arxiv Article ID
	@param storeTo Disk file to write the article body to
     */
    static boolean getBodyFromWeb(String id, File storeTo) throws IOException    {
	// The file path on the remote server
	String doc_file = Cache.getFilenameTraditional(id, "arXiv-cache");
	if (doc_file==null) {
	    System.out.println("Cannot figure remote file name for article id=" + id);
	    return false;
	} 
	String lURLString= "http://search.arxiv.org:8081/" + doc_file;
	URL lURL    =new URL( lURLString);
	//	Logging.info("ArxivImporter requesting URL " + lURL);
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

	//Logging.info("code = " + code +", msg=" + gotResponseMsg);

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

    /** Sets the root for the input directory from which file bodies 
	(earlier transferred by FTP from osmot, or kindly uploaded
	by Paul Ginsparg) can be found.
     */
    public void setBodySrcRoot(String _bodySrcRoot)  {
	bodySrcRoot=_bodySrcRoot;
    }

    /** Reads the document body from the specified directory tree. No
	attempt to download from the web.
     */
    public static String readBodyFromCache(String id, String cacheRoot) {
	File q = readBody( id, cacheRoot, false);
	return q==null? null : readBodyFile(q);
    }

    /** Finds the document body file in the specified dir tree
	(document body cache), and reads it in if available. Both
	original files (*.txt) and gzipped ones (*.*.gz) are looked
	for and read. 

	@param canUpdateCache If this parameter is true, and the file
	is not in the specified cache directory tree, this method also
	tries to download it to that directory tree from Thorsten's
	team's server (osmot.cornell.edu). (Therefore, updating of the
	cache from the web is a side effect of this method; it should
	not be called unless you are also following up with importing
	the document into Lucene.  Otherwise, the "isCached" test will
	be mislead in the future ArxivImporter runs!)
	
	@param  bodySrcRoot The root of a file directory tree in which 
	we'll look for the document body.

	@return The full text of the document's body, or null if none
	could be obtained.
    */
    private static File readBody( String id,  String bodySrcRoot,
				   boolean canUpdateCache) {

	if (useRsyncData &&  canUpdateCache) throw new IllegalArgumentException("We are not supposed to update the rsync directory; rsync does it!");

	//Cache.Structure structure=useRsyncData? Cache.Structure.RSYNC: Cache.Structure.TRADITIONAL;
	File q =  Indexer.locateBodyFile(id,  bodySrcRoot//, structure
					 );

	if (q==null) {
	    if (canUpdateCache) {
		// can we get it from the web?
		String doc_file = Cache.getFilename(id, bodySrcRoot);
		q = new File( doc_file);
		File g = q.getParentFile();
		if (g!=null && !g.exists()) {
		    boolean code = g.mkdirs();
		    System.out.println("Creating dir " + g + "; success=" + code);
		}
		try {
		    boolean code=getBodyFromWeb( id, q);
		    System.out.println("Tried to get data from the web for  document file " + doc_file +", success=" + code);
		} catch (IOException E) {
		    System.out.println("Failed to copy data from the web for  document file " + doc_file);
		    E.printStackTrace(System.out);
		    System.exit(0);
		    return null;
		}	
	    } else {
		System.out.println("There is no pre-loaded document file for " + id);
		return null;
	    }
	}

	if (!q.canRead()) {
	    System.out.println("Document file is not readable: " + q);
	    return null;
	}
	return q;
    }

    /** Reads the specified document body file into a string */
    private static String readBodyFile(File q) {
	try {
	    return Indexer.parseDocFile(q.getCanonicalPath());
	} catch (IOException E) {
	    System.out.println("Failed to read or parse document file " + q);
	    return null;
	}	       
    }

    /** Returns the string value that should be stored in the
	DATE_FIRST_MY field of the new document for a specified article id. 
	This is a "now" timestamp if the article has never been imported into 
	Lucene, or the value of that field (or the DATE field) from the
	pre-existing record. The purpose of this is to keep track
	when an article was first imported into My.Arxiv
     */
    private static String getMyArxivDate(IndexReader reader, String aid) {
	String now =   DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND);
	IndexSearcher searcher=null;
	try {
	    searcher = new IndexSearcher(reader);
	    int docno = Common.find(searcher, aid);
	    Document doc = reader.document(docno);
	    String s= doc.get(ArxivFields.DATE_FIRST_MY);
	    if (s!=null) return s;
	    s = doc.get(ArxivFields.DATE);
	    if (s!=null) return s;
	} catch(Exception ex) {    
	    return now;
	} finally {
	    try {
		searcher.close();
	    } catch(Exception ex) {}
	}
	return now;
    }


    /** Takes an XML "record" element from the OAI feed, finds
	matching body file, and saves all the data as
	appropriate. This involves creating a Document object (with
	everything parsed and indexed in it) and storing it in the
	Lucene index, as well as caching both the abstract and the
	document body in their respective caches. (We have one cache
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
    private void importRecord(Element record, IndexWriter writer, IndexReader reader, boolean rewrite) throws IOException {
	org.apache.lucene.document.Document doc = parseRecordElement( record);
	if (doc==null) {
	    // deleted status
	    System.out.println("Ignoring deleted document");
	    return;
	}

	String paper = doc.get(ArxivFields.PAPER);

	if ( paper==null) {
	    // When a document is deleted, its new entry ( which comes
	    // with <header status="deleted">) appears in the recent
	    // doc feed. These entries look like this:
	    // http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:0901.4014
	    System.out.println("Failed to extract id from the record. doc="+doc);
	    return;
	}

	Cache metacache = new Cache(metaCacheRoot);
	Cache bodycache = new Cache(bodyCacheRoot);
	metacache.setExtension(".xml");

	// Keep track of when this article first appeared in My.Arxiv's Lucene
	// index
	String dateFirstMy =   getMyArxivDate( reader, paper);
	doc.add(new Field(ArxivFields.DATE_FIRST_MY,  dateFirstMy,
			  Field.Store.YES, Field.Index.NOT_ANALYZED));

	if (!rewrite || fixCatsOnly) {
	    // see if the doc already exists (both the Lucene entry, and the cached body and metadata)

	    int docno = Common.findOrMinus(searcher, paper);
	    if (docno >= 0) {
		boolean isCached = metacache.fileExists(paper) && 
		    bodycache.fileOrGzExists(paper);

		if (!rewrite) {
		    if (isCached && bodyStoredInLucene(reader, docno)) {
			System.out.println("skip already stored doc, id="+paper);
			return;
		    } else if (isCached) {
			System.out.println("Lucene entry exists, but doc body not stored (even if cached), for doc id=" + paper);
		    } else {
			System.out.println("Lucene entry exists, but no cached data, for doc id=" + paper);
		    }
		} else if (fixCatsOnly) {
		    // special mode: for articles already present, we
		    // rewrite them only when the format of categories
		    // to be stored should be changed

		    if (catsMatch(doc, docno, reader)) {
			System.out.println("skip already stored doc with matching cats, id=" + paper);
			return;
		    }

		}
	    }
	}
	    
	File q = readBody(paper, bodySrcRoot, !useRsyncData);
	String whole_doc = (q==null)? null: readBodyFile(q);
   
	if (whole_doc!=null) {
	    // this used Field.Store.NO before 2014-12-02; Field.Store.YES
	    // since then, for the sake of EE5
	    doc.add(new Field(ArxivFields.ARTICLE, whole_doc, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));

	    // Record current time as the date the document was indexed
	    doc.add(new Field(ArxivFields.DATE_INDEXED,
			      DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND),
			      Field.Store.YES, Field.Index.NOT_ANALYZED));
	    // Set article length
	    doc.add(new Field("articleLength", Integer.toString(whole_doc.length()), Field.Store.YES, Field.Index.NOT_ANALYZED));
	} else {
	    missingBodyIdList.add(paper);
	}

	System.out.println("id="+paper+", date=" + doc.get(ArxivFields.DATE) +", body " + (whole_doc!=null ? "available" : "not available"));
	
	// Create a SQL database entry, if it does not exist yet
	Article.getArticleAlways( em,  paper);

	// write data to Lucene, and cache the doc body
	String doc_file = whole_doc==null? null:   q.getCanonicalPath();
	Indexer.processDocument(doc, doc_file,	true, writer, bodyCacheRoot);

	pcnt++;
	// cache the metadata - unless, of course, we ARE reading from the 
	// metadata cache!
	if (!readingMetacache) {
	    metacache.cacheDocument(paper, XMLUtil.XMLtoString(record));
	}
    }

    private static boolean bodyStoredInLucene(IndexReader reader, int docno) 
    throws IOException {
	Document doc = reader.document(docno);
	String s = doc.get(ArxivFields.ARTICLE);
	return (s!=null && s.length()>0);
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


    /** This    primarily   handles    the   server's    return   code
	HTTP_UNAVAILABLE  (503),  which  comes  with  the  Retry-After
	code. (As per the OAI2 standard, the server may ask us to wait
	and to  retry). In  addition, we also  do a limited  amount of
	retries   for   code   500   (HTTP_INTERNAL_ERROR),   or   for
	java.net.ConnectException     ("Connection     refused")    in
	conn.getResponseCode(), which very occasionally happen too.
    */
    private static boolean mustRetry(HttpURLConnection conn ) throws IOException {

	boolean refused = false;
	int code = 0;
	Exception savedEx = null;
	try {
	    code = conn.getResponseCode();
	    if (code==HttpURLConnection.HTTP_OK) return false;
	} catch( java.net.ConnectException ex) {
	    //Logging.warning("Caught exception " + ex)
	    savedEx = ex;
	    refused = true;
	} catch(  java.net.SocketException ex) {
	    // Logging.warning("Caught exception " + ex)
	    savedEx = ex;
	    refused = true;
	}

	long msec1= System.currentTimeMillis();
	long msec2= msec1 + 1000 * 120; // default long wait	
	if (refused) {
	    // a very occasional "java.net.ConnectException: Connection refused"
	    // or "java.net.SocketException: Connection reset"
	    System.out.println("Had a 'connection refused' or 'connection reset' (ex="+savedEx+"). Let's retry just in case");
	} else if (code==HttpURLConnection.HTTP_UNAVAILABLE) {
	    // this usually is a typical OAI2 retry message
	    String ra = conn.getHeaderField("Retry-After");
	    System.out.println("got code "+code+", Retry-After="+ra);
	    int padding=5; // pad the interval with so many seconds
	
	    int interval=-1; // in seconds
	    try {
		interval = Integer.parseInt(ra);
	    } catch(Exception ex) {}
	
	    if (interval>=0) {
		msec2=msec1+ 1000 * (interval+padding);
	    } else {
		try {
		    SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		    Date d = format.parse(ra);
		    long q = d.getTime();  
		    if (q > msec1) msec2 = q;
		} catch(Exception ex) {}
	    }
	} else if (code==HttpURLConnection.HTTP_INTERNAL_ERROR) {
	    // Very rarely, HTTP_INTERNAL_ERROR (code 500) is also reported
	    System.out.println("got code "+code+", let's retry just in case");
	} else return false;

	do {
	    try {
		System.out.println("sleep "+(msec2-msec1)+" msec...");    
		Thread.sleep(msec2-msec1);
	    } catch( InterruptedException ex) {}
	    msec1= System.currentTimeMillis();
	} while(msec1 < msec2);
	return true;
    }

    private static Date lastRequestTime = null;

    /** Reads the content of a URL into an XML element */
    static Element getPage(String urlString ) throws IOException,  org.xml.sax.SAXException {
	URL url = new URL(urlString);

	// space requests by at least 21 sec (to avoid the "wait" response)
	final long necessaryIntervalMsec = 1000 * 21L;

	HttpURLConnection conn;
	final int maxAttemptCnt = 10;
	int attemptCnt = 0;
	do {
	    Date now = new Date();	    
	    if (lastRequestTime!=null) {
		//		long mustWait = necessaryIntervalMsec - 
		//		    (now.getTime()-lastRequestTime.getTime());
		long mustWait = 0;
		if (mustWait>0) {
		    try {
			System.out.println("sleep "+mustWait+" msec...");    
			Thread.sleep(mustWait);
		    } catch( InterruptedException ex) {}
		    now = new Date();
		}
	    }

	    lastRequestTime = now;
	    conn = (HttpURLConnection)url.openConnection();  
	    conn.setFollowRedirects(true) ;
	    conn.setRequestProperty("User-Agent", "arXiv_xs-Importer/0.1");
	} while( ++attemptCnt < maxAttemptCnt && mustRetry(conn));

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
	importAll(null, -1, true, null, null);
    }

    /** As per http://www.openarchives.org/OAI/2.0/openarchivesprotocol.htm

http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:0901.4014
    */
    static String makeURL(String tok, String from, String until) {
	String base="http://export.arxiv.org/oai2?verb=ListRecords";
	if  (tok!=null) {
 	    return base + "&resumptionToken="+tok;
	}
	String s = base + "&metadataPrefix=arXiv";
	if (from  !=null) s += "&from=" +from;
	if (until !=null) s += "&until="+until;
	return s;
    }

    /** Plls in all pages, or all pages in a date range.
	@param tok start harvesting from the beginning (if null), or from this resumption token (otherwise)
	@param max if max non-negative, then ...; -1 means "all"
	@param from "YYYY-MM-DD", passed to OAI2 from=... option
     */
    public void importAll(String tok, int max, boolean rewrite, String from, String until)  throws IOException,  org.xml.sax.SAXException {
	int pagecnt=0;

	//	IndexWriter writer =  makeWriter(); 
	// FIXME: is there an easier way?
	//	IndexReader reader = IndexReader.open(writer, false);

	try {

	    while( max<0 || pagecnt < max) 	 {
		String us = makeURL( tok, from, until);
		System.out.println("At "+new Date()+", requesting: " + us);
		Element e = getPage(us);	    
		tok = parseResponse(e, 
				    //writer, reader, 
				    rewrite);
		pagecnt++;
		System.out.println("done "+pagecnt+" pages, token =  " + tok);
		if (tok==null || tok.trim().equals("")) break;

		/*
		if (pagecnt % 1 == 0) {
		    System.out.println("At "+new Date()+", re-opening index... ");
		    reader.close();
		    writer.close();
		    writer =  makeWriter(); 
		    reader = IndexReader.open(writer, false);
		}
		*/

	    }

	    if (optimize) {
		System.out.println("At "+new Date()+", optimizing index...");
		IndexWriter writer = makeWriter(); 
		writer.optimize();
		System.out.println("At "+new Date()+", done optimizing index.");
		writer.close();
	    }
	} finally {
	    //	    reader.close();
	    //	    writer.close();
	}
    }

    /** This is used when we specifically want to import a single page */
    public void importOnePage( String aid, boolean rewrite//, 
			       //IndexReader reader, IndexWriter writer
			       )  throws IOException,  org.xml.sax.SAXException {
	int pagecnt=0;

	    String us = 
	    "http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:" + aid;

	    System.out.println("At "+new Date()+", requesting: " + us);
	    Element e = getPage(us);	    
	    parseResponse(e, 
			  //writer, reader, 
			  rewrite);
	    pagecnt++;

    }


    /** A flag that tells the importer not to try to rewrite the
	metadata cache, because we are reading from the metadata cache
	now!
     */
    private boolean readingMetacache=false;
    
    /** Imports data from pre-read XML files in a specified directory.
     */
    private void processDir( File root,IndexWriter writer, IndexReader reader, boolean rewrite)
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
			importRecord(e, writer, reader, rewrite);	
		    } else {
			System.out.println("Not a 'record' element in file "+f);
			//tok = imp.parseResponse(e, writer , rewrite);
			//System.out.println("resumptionToken =  " + tok);
		    }
		} catch(Exception ex) {		    
		    System.out.println("Failure on file: " + f);
		    ex.printStackTrace(System.out);
		    System.out.println(ex);			
		}
		
	    }
	}
	for(File f: children) {
	    if (f.isDirectory()) {
		processDir( f, writer, reader, rewrite);
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
    static String getFrom(ParseConfig ht) {
	final String FROM="from", DAYS="days";
	String from=ht.getOption(FROM, null);
	if (ht.getOption(DAYS,null)==null) return from;
	if (from!=null) {
	    throw new IllegalArgumentException("Can't supply -Dfrom and -Ddays simultaneously!");
	}
	int days=ht.getOption(DAYS, 0);
	if (days<=0)  throw new IllegalArgumentException("-Ddays=nnn, if supplied, must be positive!");
	Date d = Common.plusDays(new Date(), -days);
	final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
	from=fmt.format( d );
	Logging.info("Converted days=" + days + " to from=" + from);
	return from;
    }

    static String getUntil(ParseConfig ht) {
	final String UNTIL="until";
	String until=ht.getOption(UNTIL, null);
	return until;
    }

    static boolean  optimize=true;
    static boolean fixCatsOnly = false;

    static final boolean useRsyncData = true;
  
    /** Options:
	<pre>
	-Dtoken=xxxx   Resume from the specified resumption page
	-Drewrite=[true|false]  Default is true; if false, already stored pages are not modified  	
	-Dfrom=YYYY-MM-DD | -Ddays=3
	-Doptimize=true
	</pre>
     */
    static public void main(String[] argv) throws IOException, SAXException,
						  java.text.ParseException
 {
	if (argv.length==0) usage();
	ParseConfig ht = new ParseConfig();
	String tok=ht.getOption("token", null);
	boolean rewrite =ht.getOption("rewrite", true);
	String from=getFrom(ht); // based on "from" and "days"
	String until=getUntil(ht); // based on "until"
	optimize =ht.getOption("optimize", optimize);
	fixCatsOnly = ht.getOption("fixCatsOnly", false);

	Options.init(); // read the legacy config file

	ArxivImporter imp =new  ArxivImporter();

	imp.metaCacheRoot =  ht.getOption("meta", imp.metaCacheRoot);
	if (!(new File(imp.metaCacheRoot)).isDirectory()) {
	    System.out.println("Cache directory " + imp.metaCacheRoot + " does not exist!");
	    System.exit(1);
	}
	imp.setBodySrcRoot( ht.getOption("bodies", imp.bodySrcRoot));
	if (!(new File(imp.bodySrcRoot)).isDirectory()) {
	    System.out.println("Warning: body src root directory " + imp.bodySrcRoot + " does not exist!");
	}

	if (argv.length==0) return;
	final String cmd =argv[0];

	if ( cmd.equals("all")) {
	    int max=-1;
	    if (argv.length>1) {
		try {
		    max	=Integer.parseInt(argv[1]);
		} catch(Exception ex) {}
	    }
	    System.out.println("Processing web data, up to "+max + " pages; from=" + from);
	    imp.importAll(tok, max, rewrite, from, until);
	} else if (cmd.equals("allmeta")) {
	    if (!rewrite) throw new IllegalArgumentException("For a reload from metachache, ought to use -Drewrite=true");
	    imp.readingMetacache=true;
	    IndexWriter writer =  imp.makeWriter(); 
	    IndexReader reader = IndexReader.open(writer, false);
	    imp.processDir( new File(imp.metaCacheRoot), writer, reader, rewrite );
	    if (optimize) {
		System.out.println("Optimizing index... ");
		writer.optimize();
	    }
	    reader.close();
	    writer.close();
	} else if (cmd.equals("files")) {
	    // processing XML files with metadata
	    for(int i=1; i<argv.length; i++) {
		String s= argv[i];
		if (s.equals("")) continue;
		final boolean isUrl=
		    s.startsWith("http://")||s.startsWith("https://");
		System.out.println("["+i+"] Processing " + (isUrl?"URL ": "file ") + s);

		Element e = isUrl?  getPage(s) :  XMLUtil.readFileToElement(s);
		if (e.getNodeName().equals(Tags.RECORD)) {
		    System.out.println("Found a record; processing");

		    IndexWriter writer =  imp.makeWriter(); 
		    IndexReader reader = IndexReader.open(writer, false);
		    imp.importRecord(e, writer, reader, rewrite);
		    reader.close();
		    writer.close();
		} else {
		    System.out.println("parseResponse");
		    tok = imp.parseResponse(e, //writer, reader, 
					    rewrite);
		    System.out.println("resumptionToken =  " + tok);
		}
	    }
	    if (optimize) {
		System.out.println("Optimizing index... ");
		IndexWriter writer = imp.makeWriter(); 
		writer.optimize();
		writer.close();
	    }

	} else if (cmd.equals("test")) {
	    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	    String a = "2012-11-14";
	    String b0 = a.replaceAll("-", "") + "0000";

	    Date b1 = dateFormat.parse(a);

	    //  DateTools.timeToString(date.getTime(),DateTools.Resolution.MINUTE),

	    String b = DateTools.dateToString(b1,DateTools.Resolution.HOUR  );

	    // b is understood as time in GMT 
	    Date c = DateTools.stringToDate(b);
	    String d = dateFormat.format(c);
	    System.out.println( a + " --> " + b1 + " --> "+ b + " --> "
 +c + " --> " +d);

	} else if (cmd.equals("articles")) {
	    // Get specified articles
	    IndexReader reader = Common.newReader();
	    IndexWriter writer =  imp.makeWriter(); 
	    for(ArgvIterator it=new ArgvIterator(argv,1); it.hasNext();){
		String aid = it.next();
		if (aid.trim().equals("")) continue;
		imp.importOnePage(  aid, rewrite//, reader, writer
				    );
	    }
	    if (optimize) {
		System.out.println("Optimizing index... ");
		writer.optimize();
	    }
	    reader.close();
	    writer.close();
	} else {
	    System.out.println("Unrecognized command: " + cmd);
	}
	System.out.println("imported "+imp.pcnt+" docs, among which missing body files for "+ imp.missingBodyIdList.size() +" docs");
	File ms = (new File("missing.txt")).getAbsoluteFile();
	if (!ms.getParentFile().canWrite() ||
	    ms.exists() && !ms.canWrite()) {
	    throw new IOException("Cannot write file " + ms +"; please check file and directory permissions!");
	}
	PrintWriter fw = new PrintWriter(new FileWriter(ms));
	for(String s: imp.missingBodyIdList){
	    fw.println(s);
	}
	fw.close();
    }
}

