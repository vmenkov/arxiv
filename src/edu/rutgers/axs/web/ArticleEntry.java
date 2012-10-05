package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** Data for a single search result (an article). This is used
    primarily for rendering the article entry in the search results
    page or user folder page.
*/
public class ArticleEntry {
    /** Sequence number in the overall search result
     * sequence. (1-based, for human readers' convenience) */
    public int i;
    /** Article id, same as in the arXiv database and arXiv URLs */
    public String id;
    /** Various metadata, in a printable form. */
    public String idline, titline, authline, commline, subjline;
    /** A line that our own server may add in some circumstances (for rendering with additional info) */
    public String ourCommline="";
    /** True if this article is in the user's personal folder */
    public boolean isInFolder=false;
    /** The most recent rating, if any, given by the user to this article. */
    public Action.Op latestRating = Action.Op.NONE;
    /** This may be set if the article has come from some kind of a ranked-list
	search */
    public double score=0;
    /** This is the Lucene-stored article submission date, as a string */
    public String date=null;

    static private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public String formatDate() {
	if (date==null) return "no date";
	
	Date docDate= null;
	try {
	    return dateFormat.format( DateTools.stringToDate(date));
	} catch(java.text.ParseException ex) {
	    return "date not known"; // ought not happen
	}
	
    }


    /** This field may be used in some applications to store the
      Lucene internal (ephemeral) document number. We put -1 to mean
      "unknown". */
    public int docno=-1;

    /** Initializes an ArticleEntry object based on the data in a Document
	object.
	@param doc a Document object, probably just retrieved from the
	Lucene data store.
	@param docno Lucene's internal doc id
     */
    public ArticleEntry(int _i, Document doc, int _docno, double _score) {
	i = _i;
	docno = _docno;
	score=_score;
	id=doc.get(ArxivFields.PAPER);
	idline="arXiv:" + id;
	populateOtherFields( doc);
    }

    public ArticleEntry(int _i, Document doc, int _docno) {
	this(_i, doc, _docno, 0);
    }

    void populateOtherFields( Document doc) {
	titline = doc.get(ArxivFields.TITLE);
	authline=doc.get(ArxivFields.AUTHORS);
	String c= doc.get(ArxivFields.COMMENTS);
	commline=(c==null? "": "Comments:" + c);
	subjline="Subjects:" + doc.get((ArxivFields.CATEGORY));
	date = doc.get(ArxivFields.DATE);
    }
    

    /** Dummy constructor, leaves most fields (included docno) blank */
    private ArticleEntry(int _i, String aid) {
 	i = _i;
	id=aid;
	idline="arXiv:" + id;
	titline = "";
	authline="";
	commline="";
	subjline="";
     }

    public String getAid() { return id; }

    public void setScore(double x) { score=x;}

    public String toString() {
	return "ArticleEntry(i="+i+", aid=" + id+")"; 
    }

    /** Retrieves article's info from Lucene, and initializes the
	ArticleEntry object for it. If there are no data on the article in
	the local data store (e.g., becasue it has not been updated),
	may return a "dummy" entry. 
	@param aid Arxiv id for the article
	@param pos Position in our list (has nothing to do with Lucene!)
    */
    static ArticleEntry getArticleEntry(Searcher s, String aid, int pos) {
	ArticleEntry dummy =  getDummyArticleEntry( aid,  pos);
	try {
	    if (s==null) return dummy;
	    TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, aid));
	    ScoreDoc[] 	scoreDocs = s.search(tq, 1).scoreDocs;
	    if (scoreDocs.length < 1) return dummy;
	    int docno = scoreDocs[0].doc;
	    Document doc = s.doc(docno);
	    if (doc==null) return dummy;
	    return new	ArticleEntry(pos, doc, docno);
	} catch (Exception ex) { return null; }
    }

    /*
	@param aid Arxiv id for the article
	@param pos Position in our list (has nothing to do with Lucene)
    */
    static ArticleEntry getDummyArticleEntry(String aid, int pos) {
	return new ArticleEntry(pos, aid);      
    }

    /** Goes through the entries, marks those that have been
	previously put by the user into his folder. */
    static public void markFolder( Vector<ArticleEntry> entries,
				   HashMap<String, Action> folder ) {
	for(ArticleEntry e: entries) {
	    e.isInFolder = folder.containsKey(e.id);
	}
    }

    /** Goes through the entries, marks those that have been
	previously rated by the user */
    static public void markRatings( Vector<ArticleEntry> entries,
				    HashMap<String, Action> ratings ) {
	for(ArticleEntry e: entries) {
	    if (ratings.containsKey(e.id)) {
		e.latestRating = ratings.get(e.id).getOp();
	    }
	}
    }

    /** Saves a list of ArticleEntry objects (typically, a suggestion
	list) to the specified file. Before doing so, verifies that
	the necessary directory exists, and if it does not, tries to
	create it.

	This method is a wrapper around save(Vector<ArticleEntry> entries,
	PrintWriter w).
     */
    static public void save(Vector<ArticleEntry> entries, File f) throws IOException {
	File g = f.getParentFile();
	if (g!=null && !g.exists()) {
	    boolean code = g.mkdirs();
	    Logging.info("Creating dir " + g + "; success=" + code);
	}

	PrintWriter w= new PrintWriter(new FileWriter(f));
	save(entries, w);
	w.close();
    }

   /** Writes a list of ArticleEntry objects (typically, a suggestion
	list) to a PrintWriter.
     */
    static public void save(Vector<ArticleEntry> entries, PrintWriter w) {
	//	w.println("#--- Entries are ordered by w(t)*idf(t)");
	//	w.println("#term\tw(t)\tw(sqrt(t))\tidf(t)");
	for(int i=0; i<entries.size(); i++) {
	    ArticleEntry e=entries.elementAt(i);
	    w.println(e.id + "\t" + e.score);
	}
    }

    /** Reads a file saved earlier and creates a vector of "skeleton"
	entries.
	@return A Vector of "skeleton" entries (each one containing
	just the ArXiv article ID and the score read from the file).
     */
    static public Vector<ArticleEntry> readFile(File f) throws IOException {
	Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
	String s;
	int linecnt = 0, pos=0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    String q[] = s.split("\\s+");
	    if (q==null || q.length != 2) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f);
	    }
	    pos++;
	    String aid = q[0];
	    ArticleEntry e = new ArticleEntry(pos, aid);
	    e.setScore( Double.parseDouble(q[1]));
	    entries.add(e);
	}
	r.close();
	return entries;
    }

    /** Find a document by article ID, using a given searcher.
     @return Lucene internal doc id.
     @throws IOException if not found.
    */
    static public int find(IndexSearcher s, String aid) throws IOException {
	TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, aid));
	//System.out.println("query=("+tq+")");
	TopDocs 	 top = s.search(tq, 1);
	ScoreDoc[] 	scoreDocs = top.scoreDocs;
	if (scoreDocs.length < 1) {
	    System.out.println("No document found with paper="+aid);
	    throw new IOException("No document found with paper="+aid);
	}
	return scoreDocs[0].doc;
    }

    /** @return the Lucene doc no, or -1 (if we have not queried Lucene yet) 
     */
    public int getStoredDocno() {
	return docno;
    }
     
    /** Checks if the Lucene docno is set in this entry, sets it if it
	is not, and returns it.
     */
    public int getCorrectDocno(IndexSearcher s) throws IOException {
	if (docno < 0) {
	    docno = find(s, id);		    
	}
	return docno;
    }

    /** Applies this user's exclusions, folder inclusions, and ratings */
    static void applyUserSpecifics( Vector<ArticleEntry> entries, User u) {
	if (u==null) return;
    
	HashMap<String, Action> exclusions = 
	    u.getActionHashMap(new Action.Op[]{Action.Op.DONT_SHOW_AGAIN});
		    
	// exclude some...
	// FIXME: elsewhere, this can be used as a strong negative
	// auto-feedback (e.g., Thorsten's two-pager's Algo 2)
	for(int i=0; i<entries.size(); i++) {
	    if (exclusions.containsKey(entries.elementAt(i).id)) {
		entries.removeElementAt(i); 
		i--;
	    }
	}

	// Mark pages currently in the user's folder, or rated by the user
	markFolder(entries, u.getFolder());
	markRatings(entries, u.getActionHashMap(Action.ratingOps));
    }

    /** Appends an extra text to the article's comment line.  This is a somewhat cludgy
	way to add extra information to the display formatted in a standard way.

	FIXME: It would be better to have a separate field besides
	commline, so that the formatting can be more flexible.
     */
    void appendComment(String s) {
	if (ourCommline==null) ourCommline="";
	ourCommline += (ourCommline.length()==0? "" : " ") + s;
    }

 

}



