package edu.rutgers.axs.upload;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.index.*;
import org.apache.lucene.document.Document;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.indexer.UploadImporter;
import edu.rutgers.axs.html.ProgressIndicator;

/** An UploadProcessingThread object is created to handle all the
    operations that need to be carried out when the user uploads one
    or several files.  This class has several constructors, one of
    which is used depending on what kind of upload mode was used
    (direct upload from the user's desktop, or file loading from a
    URL). All actual work is carried out in the run() method; a
    reference to the thread is stored in the current user session's
    SessionData object, whereby the thread's progress can be monitored
    from later HTTP requests.

    <p>The PDF-to-text conversion is carried out by <a href="http://www.unixuser.org/~euske/python/pdfminer/">pdfminer</a> (pdf2txt.py)

 */
public class UploadProcessingThread extends BackgroundThread {
    
    private final String user;
    private HTMLParser.Outliner outliner;
    private final URL startURL;
    private final DataFile startDf;
    private final SessionData sd;

    int pdfCnt = 0, convCnt = 0;

    protected String taskName() {
	return "Uploading and processing";
    }

    /** Creates a thread which will follow the links listed in the
	Outliner structure. This is used if the user has just uploaded
	an HTML document via the web UI.
     */
    public UploadProcessingThread(SessionData _sd, String _user, HTMLParser.Outliner _outliner) {
	sd = _sd;
	user = _user;
	outliner = _outliner;
	startURL = null;
 	startDf = null;
    }

    /** Creates a thread which will get a document from a specified
	URL, and, if it is HTML, will follow the links in it as well.
	This is used if the user has specified a URL via the web UI.
     */
    public UploadProcessingThread(SessionData _sd, String _user, URL url) {
	sd = _sd;
	user = _user;
	outliner = null;
	startURL = url;
	startDf = null;
   }

    /** Creates a thread that will run pdf2txt conversion for a single pre-read
	file. This is done if the user has just uploaded a PDF file.  */
    public UploadProcessingThread(SessionData _sd, String _user, DataFile df) {
	sd = _sd;
	user = _user;
	outliner = null;
	startURL = null;
	startDf = df;
    }
    

    /** Used to import data into Lucene */
    private IndexWriter writer = null;

    /** The main class for the actual document processing process.     */
    public void run()  {
	startTime = new Date();
	EntityManager em = sd.getEM();
	try {

	    writer = UploadImporter.makeWriter();

	    if (startDf != null) {
		pdfCnt = 1;
		DataFile txt = importPdf(em, startDf);
		if (txt != null) {
		    convCnt ++;
		}
		return;
	    } else if (startURL != null) {
		PullPageResult pulled = pullPage(user, startURL, false);
		if (pulled==null) {
		    return; // error
		} else if (pulled.pdfFile != null) {
		    pdfCnt ++;
		    DataFile txt = importPdf(em, pulled.pdfFile);
		    if (txt != null) convCnt ++;
		} else if (pulled.outliner!=null) {
		    outliner = pulled.outliner;
		    processOutliner(em);
		} else {
		    // nothing useful has been read
		    return;
		}
		    
		//progress("The total of " + pdfCnt +  " PDF files have been retrieved from " + startURL);
	    } else if (outliner != null) {		
		// outliner has been supplied in the constructor 
		processOutliner(em);
	    }
	    
	} catch(org.apache.lucene.store.LockObtainFailedException ex) {
	    // this happens in makeWriter() if some other process
	    // is writing to Lucene at the moment
	    // Lock obtain timed out: NativeFSLock@/data/arxiv/arXiv-index/write.lock
	    String errmsg = ex.getMessage();
	    error("LockObtainFailedException in UploadProcessingThread " + getId() + ": " + errmsg + "\n" +
		  "Probably, the server is busy at the moment. You may try to wait for a few minutes and then repeat the upload"  );
	    ex.printStackTrace(System.out);


	} catch(Exception ex) {
	    String errmsg = ex.getMessage();
	    error("Other exception in UploadProcessingThread " + getId() + ": " + errmsg);
	    ex.printStackTrace(System.out);
	} finally {
	    if (writer != null) {
		try {
		    writer.close();
		} catch(IOException ex) {}
	    }
	    ResultsBase.ensureClosed( em, true);
	    endTime = new Date();
	}

    }

    /** Gets the PDF focuments from all URLs listed in this thread's
	Outliner object */
    private void processOutliner(EntityManager em) {
	if (outliner==null) return;
	if (outliner.getErrors().size()>0) {
	    progress("During processing of the HTML document " + outliner.getErrors().size() + " errors were encountered", false, true, true);
	}
	int i=0;
	for(String error: outliner.getErrors()) {
	    i++;
	    progress("" + i + ". " + error);
	}

	int nLinks = outliner.getLinks().size();
	progress("Will follow all " + nLinks + " links found in the HTML document, looking for PDF documents. (May skip some of them if duplicate, though)", false, true, false);
	pin = new ProgressIndicator(nLinks, false);
	HashSet<URL> doneLinks = new 	HashSet<URL>();
	int cnt=0;
	for(URL url: outliner.getLinks()) {
	    pin.setK(cnt++);
	    if (doneLinks.contains(url)) continue;		    
	    doneLinks.add(url);
	    try {
		PullPageResult pulled = pullPage(user, url, true);
		if (pulled !=null && pulled.pdfFile != null) {
		    pdfCnt ++;
		    DataFile txt = importPdf(em, pulled.pdfFile);
		    if (txt != null) convCnt ++;
		} else {
		    // error has been reported inside pullPage()
		}
	    } catch(IOException ex) {
		error(ex.getMessage());
	    }
	}
	pin.setK(cnt);
	progress("The total of " + doneLinks.size() + " links have been followed; " + pdfCnt + " PDF files have been retrieved from them.", false, true, true);
	progress("The total of " + convCnt + " PDF files have been successfully converted to text", false, true, true);
    }


    /** Saves the data from an input stream into a PDF file. The stream
	can be opened from direct file uploading, or during downloading
	a file from the web.
	@param user User name. This controls choice of the directory to save the file.
	@param fileName The name of the downloaded file (without dir path)
	@param uploadedStream The input strem.
     */
    static public DataFile savePdf(String user, InputStream uploadedStream, String fileName ) 
    throws IOException {
	final int L = 32 * 1024;

	BufferedInputStream bis = new BufferedInputStream(uploadedStream, L);
	
	DataFile df = new DataFile(user, DataFile.Type.UPLOAD_PDF, fileName);
	
	File file = df.getFile();
	File dir = file.getParentFile();
	if (dir.exists()) {
	    if (!dir.isDirectory() || !dir.canWrite()) {
		throw new IOException("Server error: Cannot receive files, because " + dir + " already exists, but is not a directory, or is not writeable!");
	    }
	} else {
	    if (!dir.mkdirs()) {
		throw new IOException("Server error: Cannot receive files: failed to create directory " + dir);
	    }
	    Logging.info("Created directory " + dir);
	}

	FileOutputStream fos = new FileOutputStream(file);
	BufferedOutputStream bos=new BufferedOutputStream(fos,L);
	
	int n=0;
	byte[] data = new byte[L];
	while((n=bis.read(data))>0) {
	    bos.write(data, 0, n);
	}
	bis.close();
	bos.close();
	
	return df;
    }

    /** Convenience class for returning whatever we have found when
	connecting to a specified URL */
    static class PullPageResult {
	/** This is set if a PDF document has been read */
	DataFile pdfFile=null;
	/** This structure is set if an HTML document has been
	    read. It contains all links found inside the HTML
	    document */
	HTMLParser.Outliner outliner=null;
	PullPageResult( DataFile _pdfFile) { pdfFile= _pdfFile; }
	PullPageResult( HTMLParser.Outliner _outliner) { outliner=_outliner; }
		       
    }

    /** Downloads an HTML or PDF document from a specified URL. If a
	PDF file is found, returns info about it in a DataFile
	object; if an HTML file is found, sets this.outliner  
	@param pdfOnly If true, only look for a PDF file; otehrwise, read HTML as well
    */
    private PullPageResult pullPage(String user, URL lURL, boolean pdfOnly) 
	throws IOException, java.net.MalformedURLException {

	progress("Requesting URL " + lURL, false, true, false);
	HttpURLConnection lURLConnection;
	try {
	    lURLConnection=(HttpURLConnection)lURL.openConnection();	
	}  catch(Exception ex) {
	    String msg= "Failed to open connection to " + lURL;
	    error(msg);
	    ex.printStackTrace(System.out);
	    //throw new IOException(msg);
	    return null;
	}

	lURLConnection.setInstanceFollowRedirects(true); 

	try {
	    lURLConnection.connect();
	} catch(Exception ex) {
	    String msg= "UploadPapers: Failed to connect to " + lURL;
	    error(msg);
	    return null;
	}

	int code = lURLConnection.getResponseCode();
	String gotResponseMsg = lURLConnection.getResponseMessage();

	Logging.info("code = " + code +", msg=" + gotResponseMsg + "; requested url = " + lURL);

	if (code != HttpURLConnection.HTTP_OK) {
	    String msg= "UploadPapers: Error code " + code + " received when trying to retrieve page from " + lURL;
	    error(msg);
	    return null;
	}

	final int ChunkSize = 8192;
	int lContentLength = lURLConnection.getContentLength();
	String lContentType = lURLConnection.getContentType();
	Logging.info("content-type=" +  lContentType);

	// effective URL (after any redirect)
	URL eURL = lURLConnection.getURL();
	boolean pdfComing = checkContentType( eURL, lContentType);

	if (pdfOnly && !pdfComing) {
	    //progress("No PDF file could be retrieved from " + url, false, true);
	    progress("Ignoring document from "+lURL+" (not a PDF file)",false,true, false);
	    return null;
	}

	String fileName = getRecommendedFileName(lURLConnection);

	InputStream is=null;	
	try {
	    is = lURLConnection.getInputStream();
	}  catch(Exception ex) {
	    String msg= "UploadPapers: Failed to obtain data from " + lURL;
	    error(msg);
	    int xcode = (code==HttpURLConnection.HTTP_OK)? 
		HttpServletResponse.SC_INTERNAL_SERVER_ERROR :  code;
	    //	    throw new //WebException(xcode, msg);
	    //		IOException( msg);
	    return null;
	}

	if (pdfComing) {
	    // simple bytewise copy
	    DataFile results = savePdf(user,is,fileName);
	    progress("Retrieved PDF file from " + lURL, false, true, false);
	    return new PullPageResult(results);
	} else {
	    Charset cs = getCharset(lContentType);
	    // set the outliner for the main function to process
	    HTMLParser.Outliner outliner = HTMLParser.parse(lURL, is, cs);
	    is.close();
	    progress("Retrieved HTML file from " + lURL, false, true, false);
	    return new PullPageResult(outliner);
	}
    }

    /** Checks the content type to figure whether this is a PDF document
	or not. ("Not PDF" is expected to be HTML)
	
	// e.g.  "Content-Type: text/html; charset=ISO-8859-4"

	@return Updated value of expectPdf */
    static private boolean checkContentType(URL eURL, String lContentType) {
	String lURLString = eURL.toString();
	boolean expectPdf =  lURLString.toLowerCase().endsWith(".pdf");

	if ( lContentType==null) return expectPdf;
	String ct = lContentType.toLowerCase();
	if ( ct.endsWith("pdf")) {
	    if (!expectPdf) {
		String msg = "UploadPage: Although URL '"+lURLString+"' does not end in '.pdf', the server specified content type "  + lContentType + "; accordingly, process as PDF";
		Logging.info(msg);
	    }
	    expectPdf = true;
	} else if (ct.startsWith("text/html")) {
	    if (expectPdf) {
		String msg = "UploadPage: Although URL '"+lURLString+"' ends in '.pdf', the server specified content type "  + lContentType + "; accordingly, process as HTML";
		Logging.info(msg);
	    }
	    expectPdf = false;
	}
	return expectPdf;
    }

    /** Gets the charset from the content type, whenever supplied. (E.g. 
	"Content-Type: text/html; charset=ISO-8859-4" )
	@return An Charset object based on the document's Content-Type header, or null if there was no content type indication in that header
    */
    static private Charset getCharset(String lContentType) {
	Charset cs = null;
	if (lContentType==null) return cs;
	Pattern p = Pattern.compile("charset=([\\w:\\-\\.]+)");
	Matcher m = p.matcher(lContentType);
	if (m.find()) {
	    String charsetName = m.group(1);
	    try {
		cs = Charset.forName( charsetName);
		Logging.info("For name " + charsetName+", got charset "+cs);
	    } catch(Exception ex) {
		Logging.info("No charset available for name "+charsetName);
	    }
	}
	return cs;
    }

    /** Figures out the file name under which we should save the PDF
	file retrieved over an HTTP connection. This is typically
	based on the file name present in the URL; but we also take a
	look at the "Content-Disposition" header which we may receive
	in the HTTP response.

	<p>
	See advice on Content-Disposition at 
	 http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
	 <p>Example:  <br>
	 Content-Disposition: attachment; filename="fname.ext"

	 // FIXME: We let URLConnection follow redirects; so how 
	 // do we handle changed location?
     */
    static private String getRecommendedFileName(//URL lURL, 
					  HttpURLConnection lURLConnection) {
	String f = null;
	URL eURL = lURLConnection.getURL();
	String path = eURL.getPath();
	String q[] = path.split("/");
	if (q.length > 0) {
	    int j=q.length-1;
	    if (q[j].equals("")) j--;
	    if (j>=0) f = q[j];
	}
	Logging.info("UploadPapers: set file='"+f+"' based on url=" + eURL);
	String dispo  = lURLConnection.getHeaderField("Content-Disposition");
	if (dispo==null) return f;

	Pattern p1 = Pattern.compile("filename=\"(.*?)\"");
	Pattern p2 = Pattern.compile("filename=(.+)");
	Matcher m = p1.matcher(dispo);
	if (!m.find()) m = p2.matcher(dispo.trim());
	if (m.find()) {
	    f = m.group(1);
	    Logging.info("UploadPapers: set file='"+f+"' based on Content-Disposition: " + dispo);
	    // FIXME: any encoding/special char escaping used?
	}
	
	return f;
    }

    /** Looks up the location of the script pdf2txt.py.  */
    private static File findScript() {
	final String [] dirs = {"/usr/bin", "/usr/local/bin"};
	for(String x: dirs) {
	    File dir = new File(x);
	    File g = new File(dir, "pdf2txt.py");
	    if (g.exists()) return g;
	}
	return null;
    }

    /** The complete processing sequence for a recently uploaded PDF
	file. Uses PDFMiner to convert a PDF file to text; after that,
	imports the file's content into the Lucene datastore, and
	creates an Article object and an Action object in the SQL
	database.

	<p>pdf2txt.py occasionally breaks, incapable of dealing with
	certain (old-fashioned?) PDF files, such as
	http://www.joachims.org/publications/joachims_96a.pdf .
	As per TJ's comment ("Document upload UI changes", 2015-01-13),
	we don't expose the script's long and messy message (essentially,
	a stack trace) to users.

 	@param pdf A DataFile object with the information (mostly, file name) about a recently uploaded PDF file.
	@return A DataFile object with the information (mostly, file name) about the text file produced during the conversion, or null on an error
    */
    private DataFile importPdf(EntityManager em, DataFile pdf) {
	File pdfFile = pdf.getFile();
	String pdfFileName = pdfFile.getName();
	try {
	    final String suffix = ".pdf";
	    if (!pdfFileName.toLowerCase().endsWith(suffix)) {
		error("File name " +pdfFileName+ " does not have expected suffix '.pdf'; won't convert");
		return null;
	    }
	    String txtFileName = pdfFileName.substring(0, pdfFileName.length() - suffix.length()) + ".txt";

	    DataFile txtDf = new DataFile(user, DataFile.Type.UPLOAD_TXT, txtFileName);

	    File dir = txtDf.getFile().getParentFile();
	    if (!dir.exists() && !dir.mkdirs()) {
		error("Server error: Failed to create directory " + dir);
		return null;
	    }
	    Logging.info("Created directory " + dir);

	    File script = findScript();
	    if (script==null) {
		error("Cannot find pdf2txt.py");
		return null;
	    }

	    String[] cmdarray={"python", script.getPath(),"-o",txtDf.getPath(),pdf.getPath()};

	    Runtime ru = Runtime.getRuntime();
	    Process proc = ru.exec(cmdarray, null);
	    String sb = collectProcessStderr(proc);

	    try {
		proc.waitFor();
	    } catch(java.lang.InterruptedException ex) {}
	    int ev = proc.exitValue();
	    if (sb.length()>0 || ev!=0) {
		//-- This would be too long and messy text to impose on users;
		//-- example, http://www.joachims.org/publications/joachims_96a.pdf
		//error(sb);
		error("Error in PDF-to-text converter while processing " + pdfFileName + " (exit code=" + ev + ")");
		Logging.error("Error from "+script+" (exit code="+ev+"): "+sb);
	 	return null;
	    }

	    progress("Converted " + pdfFile + " to " + txtDf.getPath(), false, true, false);
	    Document doc=UploadImporter.importFile(user,txtDf.getFile(),writer);
	    Article art =  Article.getUUDocAlways(em, doc);
	    User u = User.findByName(em, user);
	    ActionSource asrc = new ActionSource(Action.Source.UNKNOWN, 0);

	    em.getTransaction().begin(); 
	    Action a=sd.addNewAction(em, u, Action.Op.UPLOAD, art, null, asrc);
	    em.getTransaction().commit(); 
	    progress("Successfully imported " + pdfFile.getName(), false, true, true);
	    return txtDf;

	} catch (IOException ex) {
	    error("I/O error when trying to convert " + pdfFile + ": " + ex.getMessage());
	    return null;
	}      
    }

    /** Reads whatever a specified process writes to its stderr */
    private static String collectProcessStderr(Process proc) throws java.io.IOException {
	InputStream stderr = proc.getErrorStream();
	LineNumberReader rerr = new LineNumberReader(new InputStreamReader(stderr));
	String s=null;
	StringBuffer sb=new StringBuffer();
	while((s=rerr.readLine())!=null) { 
	    sb.append(s + "\n");
	}
	return sb.toString();
    }



}
