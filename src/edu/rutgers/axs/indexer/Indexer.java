package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import java.text.*;
import java.io.*;

/**
 * This class implements adding documents to the index, and updating them. It is
 * very collection specific, so you certainly don't want to use it. It is
 * included in this source file just as an example of how to insert documents
 * into a Lucene index.
 * 
 * @author Filip Radlinski
 * @version 1.0, April 2005
 * @author Vladimir Menkov
 * @version 1.1, 2012
 */

public class Indexer {

    /** Additional suffix for GZipped files */
    static private final String GZ = ".gz";

    // This should be in j.u.GregorianCalendar... Its used to fetch
    // the current year later.
    private static int YEAR = 1;

    // Where our index lives.
    private Directory indexDirectory;
    
    private String cacheDirectory;
    private Analyzer analyzer;
    private IndexWriterConfig iwConf;
    
    public Indexer(String indexDir, String cacheDir)  throws IOException {
	indexDirectory =  FSDirectory.open(new File(indexDir));
	analyzer = new StandardAnalyzer(Common.LuceneVersion);
	iwConf = new IndexWriterConfig(Common.LuceneVersion, analyzer);
	iwConf.setOpenMode(IndexWriterConfig.OpenMode.APPEND);	
	cacheDirectory = cacheDir;
	log("Created an indexer.");
    }

	private static SimpleDateFormat df[] = {
			new SimpleDateFormat("dd MMM yyyy HH:mm:ss"),
			new SimpleDateFormat("dd MMM yyyy HH:mm"),
			new SimpleDateFormat("dd MMM yyyy"),
			new SimpleDateFormat("dd MMM yy HH:mm"),
			new SimpleDateFormat("d MMM yy HH:mm"),
			new SimpleDateFormat("MMM dd yyyy HH:mm:ss"),
			new SimpleDateFormat("MMM dd yyyy HH:mm"),
			new SimpleDateFormat("MMM dd HH:mm:ss yyyy"),
			new SimpleDateFormat("MMM dd yyyy"),
			new SimpleDateFormat("MMM dd yy HH:mm"),
			new SimpleDateFormat("MMM dd yy") };

    /** Makes a Lucene Document objects with a numerous fields, as
     * provided in the arguments to the function call.
     * @param date If null, the field is not added
     */
    private Document createDocument(String paper, String groups, String categories, String from, 
				    Date date,
				    String title, String authors, String comments,
				    String article_class, String journal_ref, String abs, String article) {

	Document document = new Document();
	
	document.add(new Field(ArxivFields.PAPER, paper, Field.Store.YES, Field.Index.NOT_ANALYZED));
	//	Field f = new Field(ArxivFields.CATEGORY, categories, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES);
	//	f.setTokenStream(new SubjectTokenizer(categories));

	String xcat = "dummy-field " + categories;	
	Field f = new Field(ArxivFields.CATEGORY, new SubjectTokenizer(xcat));
	System.out.println("Trying to save cats=" +xcat);

	document.add(f);
	document.add(new Field("group", groups, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	
	// Should be: Field(name, value, Field.Store.YES, Field.Index.ANALYZED)
	document.add(new Field("from", from, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));

	if (date != null) {
	    document.add(new Field(ArxivFields.DATE,
				   DateTools.timeToString(date.getTime(),DateTools.Resolution.MINUTE),
				   Field.Store.YES, Field.Index.NOT_ANALYZED));
	}

	document.add(new Field(ArxivFields.TITLE, title, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	document.add(new Field(ArxivFields.AUTHORS, authors, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	document.add(new Field(ArxivFields.COMMENTS, comments, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	document.add(new Field("article-class", article_class, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	document.add(new Field("journal-ref", journal_ref, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	document.add(new Field(ArxivFields.ABSTRACT, abs, Field.Store.YES, Field.Index.ANALYZED,  Field.TermVector.YES));
	
	document.add(new Field(ArxivFields.ARTICLE, article, Field.Store.NO, Field.Index.ANALYZED,  Field.TermVector.YES));

	// Date document was indexed (converted to String by Lucene's DateTools)
	document.add(new Field(ArxivFields.DATE_INDEXED,
			       DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND),
			       Field.Store.YES, Field.Index.NOT_ANALYZED));		
	
	// Set article length
	document.add(new Field("articleLength", Integer.toString(article.length()), Field.Store.YES, Field.Index.NOT_ANALYZED));
	
	return document;
    }

    // Add the document to the index
    public boolean indexDocument(Document document) throws Exception {
	
	IndexReader reader = IndexReader.open(indexDirectory);
	reader.deleteDocuments(new Term(ArxivFields.PAPER, 
					document.get(ArxivFields.PAPER)));
	reader.close();

	IndexWriter writer = new IndexWriter(indexDirectory, iwConf); //, false);
	writer.addDocument(document);
	writer.close();
	
	log("ADD: Wrote "+document.get(ArxivFields.PAPER)+" ("+document.get("group")+")");
	
	return true;
    }

    /** VM: Lucene will throw a low-level exception if the ID does not exist
     */
	public void deleteDocument(int docId) throws Exception {
		IndexReader reader = IndexReader.open(indexDirectory);
		reader.deleteDocument(docId);
		reader.close();
	}

        public void deletePaper(String paperId) throws Exception {
                IndexReader reader = IndexReader.open(indexDirectory);
                reader.deleteDocuments(new Term(ArxivFields.PAPER, paperId));
                reader.close();
         
	 	Cache cache = new Cache(cacheDirectory);
                cache.deleteDocument(paperId);
        }

        public void deletePapers(String paperfile) throws Exception {

                IndexReader reader = IndexReader.open(indexDirectory);
                Cache cache = new Cache(cacheDirectory);

                BufferedReader in = null;

                /* Open the paper list */
                try {
                        in = new BufferedReader(new FileReader(paperfile));
                } catch (Exception E) {
                        System.out.println("Paper file " + paperfile + " missing.");
                }

                String line;

                while ((line = in.readLine()) != null) {
                        reader.deleteDocuments(new Term(ArxivFields.PAPER, line));
                        cache.deleteDocument(line);
                        System.out.println("Paper "+line+" deleted.");
                }
                in.close();

                reader.close();
        }


	public void optimize() throws Exception {
	    IndexWriter writer = new IndexWriter(indexDirectory, iwConf);
	    writer.optimize();
	    writer.close();
	}

	// Creates a new index
	private void newIndex() throws Exception {  
	    IndexWriterConfig iwConfNew = new IndexWriterConfig(Common.LuceneVersion, analyzer);
	    iwConfNew.setOpenMode(IndexWriterConfig.OpenMode.CREATE);	
	    IndexWriter writer = new IndexWriter(indexDirectory, iwConfNew); 
	    writer.close();
	}

    static boolean isGZipped(String path) {
	return path.endsWith(GZ);
    }


    /** @param doc_file File name. It can be a text file, or a "*.gz"
	GZipped text file.
     */
    static String parseDocFile(String doc_file) throws IOException {
	
	char chars[] = new char[Options.getInt("INDEXER_MAX_LENGTH")];

	InputStreamReader fr = 
	    isGZipped(doc_file) ?
	    new InputStreamReader(new GZIPInputStream(new FileInputStream(doc_file))) :
	    new FileReader(doc_file);

	int i = fr.read(chars, 0, Options.getInt("INDEXER_MAX_LENGTH"));
	fr.close();
	String s = new String(chars);
	s = s.substring(0, i);		
	
	return s;
    }

    /** A wrapper around parse() */
    private Document parseFile(String abs_file, String doc_file)
			throws Exception {

	BufferedReader in;
	/* Open the abstract file */
	try {
	    in = new BufferedReader(new FileReader(abs_file));
	} catch (IOException E) {
	    System.out.println("Abstract file " + abs_file + " missing.");
	    return null;
	}
	
	String line;
	String whole_abstract = "";
	while ((line = in.readLine()) != null) {
	    whole_abstract += "\n" + line;
	}
	in.close();
	
	//System.out.println("Read abstract: " + whole_abstract);
	
	/* Open and read the document file */
	String whole_doc = "";  
	try {
	    whole_doc = parseDocFile(doc_file);
	} catch (IOException E) {
	    System.out.println("Document file " + doc_file + " missing.");
	    return null;
	}
	
	return parse(whole_abstract, whole_doc);
    }
    
    /** Given an abstract and the article body, parses the abstract
	and creates a Document object which later can be indexed in
	Lucene. Top level call.

	@param whole_abstract The text of the abstract, in the
	old-fashioned plain text "Header: Value" format.
	@param whole_doc The text of the document.
     */
    public Document parse(String whole_abstract, String whole_doc)
			throws Exception {
	
	boolean debug = Options.debug;

	int FIELD_TITLE=0, FIELD_AUTHORS=1, FIELD_COMMENTS=2, FIELD_JOURNAL=3;
	int FIELD_REPORT=4, FIELD_ARTICLE_CLASS=5, FIELD_ID=6, FIELD_FROM=7;
	int FIELD_GROUPS=8, FIELD_DATE=9, FIELD_CATEGORIES=10;
	
	int maxField = 10;
	
	// Find the meta-data
	String contents = "(([^\n]+|(\n  ))+)";
	String types[] = { "Title: ", "Authors?: ", "Comments: ", "Journal-ref: ",
			   "Report-no: ", "ACM-class: ", "MSC-class: ", "arXiv:", 
			   "From: ", "Groups: ", "Date( \\([a-z0-9 ]+\\))?: ", "Categories: "};
	int groups[] = { 1, 1, 1, 1, 
			 1, 1, 1, 1, 
			 1, 1, 2, 1 };
	int field[] = {FIELD_TITLE, FIELD_AUTHORS, FIELD_COMMENTS, FIELD_JOURNAL,
		       FIELD_REPORT, FIELD_ARTICLE_CLASS, FIELD_ARTICLE_CLASS, FIELD_ID,
		       FIELD_FROM, FIELD_GROUPS, FIELD_DATE, FIELD_CATEGORIES};
	String answer[] = new String[maxField+1];
	
	for (int i=0; i<answer.length; i++) 
	    answer[i] = "";
	
	for (int i = 0; i < types.length; i++) {
	    Pattern pattern = Pattern.compile("\n" + types[i] + contents);
	    Matcher matcher = pattern.matcher(whole_abstract);
	    // Since this is a while loop, we'll get the last entry matching 
	    // any type. This is important for making sure we get the date
	    // right.
	    while (matcher.find()) {
		if (debug)
		    log("Found "+types[i]+matcher.group(groups[i]));
		if (field[i] != FIELD_ID) {
		    if (answer[field[i]].length() > 0)
			answer[field[i]] += " ";
		    answer[field[i]] += matcher.group(groups[i]);
		} else { // arXiv IDs: we only ever take the first one
		    if (answer[field[i]] == "")
			answer[field[i]] = matcher.group(groups[i]);
		}
	    }
	}
	//System.out.println("Parsing done");
	
	// Trim the arXiv line to not have any gunk after it, just in case
	int pos = answer[FIELD_ID].indexOf(" ");
	if (pos > 0) {
	    String msg = "arXiv line trimmed from '"+answer[FIELD_ID]+"' to '";
	    answer[FIELD_ID] = answer[FIELD_ID].substring(0,pos);
	    log(msg+answer[FIELD_ID]+"'");
	}
	
	// Find the abstract
	String patternStr = "\\\\\\\\\\n\\s+(([^\\n]+|\\n)+)\\n\\\\\\\\$";
	Pattern pattern = Pattern.compile(patternStr);
	Matcher matcher = pattern.matcher(whole_abstract);
	if (matcher.find()) {
	    //System.out.println("Abstract itself:\n"+matcher.group(1)+"!");
	    whole_abstract = matcher.group(1);
	} else {
	    patternStr = "\\\\\\\\\\nAbstract:(([^\\n]+|\\n)+)\\n\\\\\\\\$";
	    pattern = Pattern.compile(patternStr);
	    matcher = pattern.matcher(whole_abstract);
	    if (matcher.find()) {
		whole_abstract = matcher.group(1);
	    }
	}
	
	// Remove the day of the week, anything in parenthesis, any timezone
	// info
	answer[FIELD_DATE] = answer[FIELD_DATE]
	    .replaceFirst(
			  "([Mm][Oo][Nn]|[Tt][Uu][Ee]|[Ww][Ee][Dd]|[Tt][Hh][Uu]|[Ff][Rr][Ii]|[Ss][Aa][Tt]|[Ss][Uu][Nn]),? ",
			  "");
	answer[FIELD_DATE] = answer[FIELD_DATE].replaceAll("\\(.*\\)", "");
	answer[FIELD_DATE] = answer[FIELD_DATE].replaceFirst("[-\\+][0-9]{3,4} ", "");
	
	// Remove common time zones (the first letter is one that doesn't
	// start a month name)
	answer[FIELD_DATE] = answer[FIELD_DATE].replaceFirst("[BCEGHIKLPQRT-Z][A-Z][A-Z] ", "");
	
	//System.out.println("Time is now "+answer[9]);
	
	// Try get the date into date format
	Date email_date = null;
	int i = 0;
	while (email_date == null && i < df.length) {
	    try {
		email_date = df[i].parse(answer[9]);
	    } catch (Exception e) {
		// Can't convert the date, it stays null.
	    }
	    i++;
	}

	//System.out.println("Parsed as "+email_date);
	
	// Fix the year if necessary (converstion to Calendar necessary
	// since most Date() functions are deprecated into Calendar()
	// for some reason beyond me.
	if (email_date != null) {
	    GregorianCalendar cal = new GregorianCalendar();
	    cal.setTime(email_date);
	    if (cal.get(YEAR) < 70) {
		// Its a lost cause, we probably didn't get a year at all.
		email_date = null;
	    } else if (cal.get(YEAR) < 1900) {
		cal.set(YEAR, cal.get(YEAR) + 1900);
		email_date = cal.getTime();
	    }
	}
	
	// Remove grp_ at start of groups
	answer[FIELD_GROUPS] = answer[FIELD_GROUPS].replaceAll("grp_","");
	
	Document d;
	if (email_date == null) {
	    Logger.log("Error [Indexer.java]: Date is still null for paper "+answer[FIELD_ID]+". Got '" + answer[FIELD_DATE] + "'");
	}

	/* Use the fields to index document */
	d = createDocument(answer[FIELD_ID], answer[FIELD_GROUPS], answer[FIELD_CATEGORIES], 
			   answer[FIELD_FROM], email_date, 
			   answer[FIELD_TITLE], answer[FIELD_AUTHORS], answer[FIELD_COMMENTS], 
			   answer[FIELD_ARTICLE_CLASS], answer[FIELD_JOURNAL], whole_abstract, 
			   whole_doc);
	return d;
    }

    /**
     * Read in documents filenames from this file and add them to the index.
     * 
     * @param filename     List of document filenames
     * @throws Exception
     */
    private void readMulti(String filename) throws Exception {
	
	this.readMulti(filename, false);
    }

    /**
     * Read in document filenames from this file, and either add them to the
     * index, or update them in the index
     * 
     * @param filename      List of document filenames to add. Each line of this file contains an "abstract_file body_file" pair. 
     * @param allowUpdate   If true, delete any documents with identical document id first.
     * @throws Exception
     */
    private void readMulti(String filename, boolean allowUpdate)
	throws Exception {
	
	int errors = 0;
	    
	// Prepare the things we need
	IndexWriter writer = new IndexWriter(indexDirectory, iwConf);
	
	// Open the file
	BufferedReader in = new BufferedReader(new FileReader(filename));
	int count = 0;
	String line;

	// Get the pairs one at a time
	while ((line = in.readLine()) != null) {
	    count++;
	    String abs_file, doc_file;
	    
	    // If there is a space, we got both names 
	    String parts[] = line.split(" ");
	    if (parts.length == 2) {
		if (parts[0].endsWith("abs")) {
		    abs_file = parts[0];
		    doc_file = parts[1];
		} else {
		    abs_file = parts[1];
		    doc_file = parts[0];
		}
	    } else {
		doc_file = line;
		abs_file = line.replaceAll("txt", "abs");
	    }
	    System.out.println("Adding " + abs_file + ", " + doc_file);
	    
	    Document doc = this.parseFile(abs_file, doc_file);
	    
	    if (doc != null) {
		processDocument(doc, doc_file, allowUpdate, writer, cacheDirectory);
 		
	    } else {
		Logger.log("Warx1ning: Error parsing files "+abs_file+", "+doc_file);
		System.out.println("Warning: Error parsing files "+abs_file+", "+doc_file);
		errors++;
	    }
	    
	}
	in.close();
	
	//System.out.println("Optimizing final index...");
	//writer.optimize();
	
	System.out.println("There are finally " + writer.numDocs()
			   + " documents in the index");
	if (errors > 0)	    System.out.print("WARNING: ");
	System.out.println(errors + " document(s) not correctly parsed.");
	
	System.out.println("Optimizing index one last time...");
	writer.optimize();
	
	// Close everything
	writer.close();
    }

    /** Given an already fully-constructed Document object, this
       method uses Lucene's IndexWriter to write it into the index,
       and caches the document body. This is a top level method, used
       from ArxivImporter.
       
       @param   doc_file The name of the disk file from which the document body is to be read. If null, there is no document body.

       @param cacheDirectory The root of the cache directory tree, into which a copy of the document body will be saved, at an appropriate location.
     */
    static void processDocument(Document doc, String doc_file, boolean allowUpdate,
				IndexWriter writer, String cacheDirectory) throws IOException {
	String aid = doc.get(ArxivFields.PAPER);
	// Remove it if its already there
	if (allowUpdate) {
	    writer.deleteDocuments(new Term(ArxivFields.PAPER, aid));
	}
		
	writer.addDocument(doc);
	
	if (doc_file != null) {
	    // Cache the document
	    Cache cache = new Cache(cacheDirectory);
	    cache.cacheDocument(aid, parseDocFile(doc_file));
	}
	int n = writer.numDocs();
	if (n % 100 == 0) {
	    Logger.log("There are now " + n + " documents.");
	    System.out.println("There are now " +n + " documents");
	    if (n % 200000 == 0) {
		System.out.println("Optimizing Index...");
		writer.optimize();
	    }
	}
    }

    /** @return path/id.txt. path/id.txt.gz, or null, based on what
	can be found
     */
    static File locateBodyFile( String id,  String bodySrcRoot) {
 	String doc_file = Cache.getFilename(id , bodySrcRoot);
	System.out.println("id="+id+", body at " + doc_file);
 	if (doc_file==null) {
	    System.out.println("No Document file " + doc_file + " missing.");
	    return null;
	} 
	String doc_file_gz = doc_file + GZ;

	File f0 = new File( doc_file);
	File fz = new File( doc_file_gz);

	
	File q = f0.exists() ? f0 : fz.exists()? fz : null;
	if (q==fz) {
	    System.out.println("Will use compressed file: " + q);
	}
	return q;
    }

    private String readBody( String id,  String bodySrcRoot) {

	File q =  locateBodyFile(id,  bodySrcRoot);

	if (!q.canRead()) {
	    System.out.println("Document file " + q + " is not readable.");   
	    return null;
	}
	try {
	    return parseDocFile(q.getCanonicalPath());
	} catch (IOException E) {
	    System.out.println("Failed to read document file " + q);
	    return null;
	}	
    }

    private void fromXML(String f, String bodySrcRoot) throws Exception {
	System.out.println("Processing XML file " + f);
	org.w3c.dom.Element e = XMLUtil.readFileToElement(f);
	Document doc = ArxivImporter.parseRecordElement( e);
	String paper =	    doc.get(ArxivFields.PAPER);

	String whole_doc = readBody( paper,  bodySrcRoot);
   
	if (whole_doc!=null) {
	    doc.add(new Field(ArxivFields.ARTICLE, whole_doc, Field.Store.NO, Field.Index.ANALYZED,  Field.TermVector.YES));

	    // Date document was indexed
	    doc.add(new Field(ArxivFields.DATE_INDEXED,
			      DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND),
			      Field.Store.YES, Field.Index.NOT_ANALYZED));		
	    // Set article length
	    doc.add(new Field("articleLength", Integer.toString(whole_doc.length()), Field.Store.YES, Field.Index.NOT_ANALYZED));
	}
	System.out.println("id="+paper+", Doc = " + doc);

	IndexWriter writer = new IndexWriter(indexDirectory, iwConf);
	processDocument(doc, 
			whole_doc==null? null:
			Indexer.locateBodyFile(paper,  bodySrcRoot).getCanonicalPath(),
			true, writer, cacheDirectory);
	writer.optimize();
	writer.close();
    }

    public static void main(String[] args) throws Exception {
	
	Options.init(); // read the config file

	Indexer i = new Indexer(Options.get("INDEX_DIRECTORY"), Options
				.get("CACHE_DIRECTORY"));

	if (args.length == 0) {
	    help();
	} else if (args[0].equals("delete")) {
	    
	    int id = new Integer(args[1]).intValue();
	    System.out.println("Deleting document with id " + id);
	    i.deleteDocument(id);
	    
	} else if (args[0].equals("deletePaper")) {
	    // Delete the paper with this id from the index as well as
	    // from the cache.
	    String paperid = args[1];
	    i.deletePaper(paperid);
	    
	} else if (args[0].equals("deletePapers")) {
	    // Delete all the papers listed in this file
	    i.deletePapers(args[1]);
	    
	} else if (args[0].equals("update")) {
	    
	    // Read filenames from here, index and cache them all.
	    i.readMulti(args[1], true);
	    
	} else if (args[0].equals("reindex")) {
	    
	    Document d = i.parseFile(args[1], args[2]);
	    i.indexDocument(d);
	    // We don't cache the document since we assume we are indexing
	    // from the cache.
	    System.out.println("Document was NOT cached.");
	    i.optimize();
	    
	} else if (args[0].equals("new")) {
	    
	    i.newIndex();
	    
	} else if (args[0].equals("optimize")) {
	    
	    System.out.println("Running optimizer...");
	    i.optimize();
	    System.out.println("Done.");
	    
	} else if (args[0].equals("fields")) {
	    
	    // Print the list of fields indexed in this index
	    System.out.println("The fields are:");
	    String fields[] = i.getFields();
	    
	    for (int c = 0; c < fields.length; c++)
		System.out.println(fields[c]);
	    
	} else if (args[0].equals("selfupdate")) {
	    
	    // Used very rarely, you probably don't want this option.
	    // Re-parse every document (using the cache), do stuff only we 
	    // know we want to do here.
	    // This was written to add the category to every single
	    // document.
	    i.selfUpdate();
	    
	} else if (args[0].equals("addmulti")) {
	    
	    // Got a file with a list of files to _add_ to the index. Do that.
	    // If you want to _update_ or _replace_ documents, use "update" command
	    // line option.
	    i.readMulti(args[1]);


	} else if (args[0].equals("addfromxml")) {	    
	    i.fromXML(args[1], args[2]);

	} else if (args[0].equals("show")) {
	    System.out.println("Default encoding is: " + System.getProperty("file.encoding"));
	    Show show = new Show();
	    for(int j=1; j<args.length; j++) {
		String v = args[j];
		// is it numeric?
		int docno=show.figureDocno(v);
		show.show(docno);  
	    }
	} else if (args[0].equals("showcoef")) {
	    System.out.println("Note: the following stop words are not stored in the Lucene index:");
	    for(Object x: org.apache.lucene.analysis.standard.StandardAnalyzer.STOP_WORDS_SET) {
		if (x instanceof char[]) {
		    char[] ca = (char[]) x;
		    String q="";
		    for(char c: ca) q+= c;
		    System.out.println("["+q+"]" );
		} else {
		    System.out.println( x.getClass() + ": " + x + "; ");
		}
	    }
	    System.out.println();
	    Show show = new Show();
	    for(int j=1; j<args.length; j++) {
		String v = args[j];
		// is it numeric?
		int docno=show.figureDocno(v);
		show.showCoef(docno); 
	    }
	} else if (args[0].equals("showcoef2")) {  // CSV format
	    Show show = new Show(false);
	    show.showFieldHeaders2();
	    for(int j=1; j<args.length; j++) {
		String v = args[j];
		if (v.equals("-")) {
		    // read doc IDs from the standard input
		    LineNumberReader r = new LineNumberReader(new InputStreamReader(System.in));
		    String s = null;
		    while((s=r.readLine())!=null) {
			s = s.trim();
			if (s.equals("") || s.startsWith("#")) continue;
			show.showCoef2(s); 
		    }
		} else {
		    show.showCoef2(v); 
		}
	    }
	} else if (args[0].equals("list")) {
	    int max=-1;
	    if (args.length >1) {
		try {
		    max=Integer.parseInt(args[1]);
		} catch(Exception ex) {}
	    }
	    IndexList il = new IndexList();
	    il.list(max);
	} else if (args[0].equals("listterms")) {
	    int minFreq=0;
	    String field=null;
	    if (args.length >1) field=args[1];	       
	    if (args.length >2) {		
		try {
		    minFreq=Integer.parseInt(args[2]);
		} catch(Exception ex) {}
	    }
	    i.listTerms(field, minFreq);
	} else {
	    help();
	}

    }

    /** Prints an info message and exits */
    private static void help() {
	System.err.println("This class implements general index operations.\nUsage:\n");
			
	System.err.println("Index operations:");
	System.err.println("\tIndexer new                        Make a new index.");
	System.err.println("\tIndexer optimize                   Reoptimize the index.");			
	
	System.err.println("\nIndexing options:");
	System.err.println("\tIndexer delete <docid>             Delete the document with this id");
	System.err.println("\tIndexer deletePaper <paperId>      Delete the document with this paper id");
	System.err.println("\tIndexer reindex <absfile> <doc>    Reindex this doc, DON'T cache it.");
	System.err.println("\tIndexer update <file_with_list>    Update these docs in the index.");
	System.err.println("\tIndexer addmutli <file_with_list>  Like update, but only add (faster).");
	    
	System.err.println("\nRarely used:");
	System.err.println("\tIndexer selfupdate                 Reparse everything in the cache.");
	System.err.println("\tIndexer fields                     Print the list of fields in the index.");
	System.err.println("\nAdditional:");
	System.err.println("\tshow docid                         View a particular document's indexed data");
	System.exit(-1);
			
    }

    /**
     * Re-parse every document (using the cache), do stuff only we 
     * know we want to do here.
     * Note: Used very rarely, you probably don't want to use this. 
     * It was written to add the category to every single document.
     * 
     * @param i
     * @throws IOException
     */
    private void selfUpdate() throws IOException, Exception {
	
	IndexReader ir = IndexReader.open(indexDirectory);
	Cache cache = new Cache(Options.get("CACHE_DIRECTORY"));
	
	// For every document
	Document d;
	String article;
	boolean changes = true;
	while (changes) {
	    changes = false;
	    
	    int n = ir.maxDoc();
	    for (int j = 0; j < n; j++) {
			    
		System.out.println("Getting paper " + j);
		
		// Get it from the index
		
		if (ir.isDeleted(j)) {
		    System.out.println("Paper " + j + " is deleted.");
					continue;
		}
		d = ir.document(j);
		if (d == null) {
		    System.out.println("Paper " + j + " missing.");
		    continue;
		}
		String paper = d.get(ArxivFields.PAPER),
		    cat =d.get(ArxivFields.CATEGORY);

		if (cat != null && !cat.equals("")) {
		    System.out.println("Paper " + paper +
				       " already updated to "  + cat);
		    continue;
		}
		
		System.out.println("Updating " + paper);
		
		changes = true;
		
		// Add the category (we already have all the other fields)
		//d.add(new Field(ArxivFields.CATEGORY), getGroups(paper), Field.Store.YES, Field.Index.NOT_ANALYZED));
		
		// Re-add the document itself, since ir.document only
		// returns the stored
		// fields
		try {
		    article = parseDocFile(cache.getFilename(paper));
		} catch (Exception E) {
		    System.out.println("Document file "
				       + cache.getFilename(paper) + " missing.");
		    continue;
		}
		
		d.add(new Field(ArxivFields.ARTICLE, article, Field.Store.NO, Field.Index.ANALYZED,  Field.TermVector.YES));

		// re-index it (includes delete)
		ir.close();
		indexDocument(d);
		System.out.println("Category set to "
				   + d.get(ArxivFields.CATEGORY));
		ir = IndexReader.open(indexDirectory);
	    }
	    
	    System.out.println("Optimizing index.");
	    optimize();
	    System.out.println("Done.");
	}
    }

	public String[] getFields() throws IOException {

		String[] fields;
		IndexReader reader = IndexReader.open(indexDirectory);

		Collection fn = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
		int size = fn.size();

		fields = new String[fn.size()];

		Iterator it = fn.iterator();

		for (int i = 0; i < size; i++) {
			fields[i] = (String) it.next();
		}

		reader.close();

		return fields;
	}

    /** Reports all terms from the Lucene index with df greater or equal than 
	a specified threshold
	
	@param field If non-null, only terms from this field are retrieved.
	@param minFreq Only terms with the document frequency at least this
	high are retrieved. (The df count is separate for each field).
     */
    void listTerms(String field, int minFreq) throws IOException {
	IndexReader reader = IndexReader.open(indexDirectory);
	boolean all = (field==null) || field.equals("all");
	TermEnum te = all? reader.terms() : reader.terms( new Term(field, ""));
	    
	while(te.next()) {
	    Term term = te.term();
	    int df = te.docFreq(); 

	    if (!all && !term.field().equals(field)) break;

	    if (df >= minFreq) {
		System.out.println("" + term + " : " + df);
	    }
	}
	reader.close();
    }

    private static void log(String s) {
	Logger.log(s);
    }
 
}
