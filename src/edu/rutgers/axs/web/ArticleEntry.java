package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** An ARticleEntry instance contains data for a single search result
    (an article). This is used primarily for rendering the article
    entry in the search results page, suggestions page, or user folder page.  */
public class ArticleEntry implements Comparable<ArticleEntry>, Cloneable {
    /** Sequence number in the overall search result
     * sequence. (1-based, for human readers' convenience) */
    public int i;

    /** For PPP sugg lists, this is the value of i pre-perturbation.
     */
    public int iUnperturbed;

    /** Article id, same as in the arXiv database and arXiv URLs */
    public String id;
    /** Various metadata, in a printable form. */
    public String idline, titline, authline, commline, subj, subjline;
    /** The article abstract */
    public String abst;
    /** A line that our own server may add in some circumstances (for rendering with additional info) */
    public String ourCommline="";
    /** Additional information (e.g., score minutiae) that we way generate to
	show to recsearchers only. */
    public String researcherCommline="";
    /** True if this article is in the user's personal folder */
    public boolean isInFolder=false;
    /** The most recent rating, if any, given by the user to this article. */
    public Action.Op latestRating = Action.Op.NONE;
    /** This may be set if the article has come from some kind of a ranked-list
	search */
    public double score=0;
    /** This is the Lucene-stored article submission date, as a string */
    public String date=null;
    public String dateIndexed=null;

    static private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public String formatDate() {
	if (date==null) return "no date";
	
	Date docDate= null;
	try {
	    String s = dateFormat.format( DateTools.stringToDate(date));
	    //	    s += "(" + date + "; indexed on " +  dateIndexed + ")";
	    return s;
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
	@param sd Contains Lucene's internal doc id, and (possibly) score
    */
    public ArticleEntry(int _i, Document doc, ScoreDoc sd) {
	iUnperturbed = i = _i;
	docno = sd.doc;
	score=sd.score;
	// provenance is only marked in lists created by team-draft
	if (sd instanceof SearchResults.ScoreDocProv) {
	    prov = ((SearchResults.ScoreDocProv)sd).prov;
	}
	id=doc.get(ArxivFields.PAPER);
	idline="arXiv:" + id;
	populateOtherFields( doc);
    }

    public void populateOtherFields(IndexSearcher searcher) throws IOException {
	docno = getCorrectDocno(searcher);
	Document doc = searcher.getIndexReader().document(docno);
	populateOtherFields(doc);
    }

    public ArticleEntry(int _i, Document doc, int _docno) {
	this(_i, doc, new ScoreDoc(_docno, (float)0));
    }

    void populateOtherFields( Document doc) {
	titline = doc.get(ArxivFields.TITLE);
	authline=doc.get(ArxivFields.AUTHORS);
	String c= doc.get(ArxivFields.COMMENTS);
	commline=(c==null? "": "Comments:" + c);
	subj = doc.get((ArxivFields.CATEGORY));
	subjline="Subjects:" + subj;
	date = doc.get(ArxivFields.DATE);
	dateIndexed = doc.get(ArxivFields.DATE_INDEXED);
	abst = doc.get(ArxivFields.ABSTRACT);
    }
    

    /** Dummy constructor, leaves most fields (included docno) blank. Typically
     should be followed by looking up the Document in Lucene, and calling
     populateOtherFields(doc) */
    public ArticleEntry(int _i, String aid) {
 	iUnperturbed = i = _i;
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
	} catch (Exception ex) { return dummy; }
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
	    if (!code) throw new IOException("Failed to create directory: " + g);
	} else {
	    Logging.info("Saving " + entries.size() + " article entries to " + f);
	}

	PrintWriter w= new PrintWriter(new FileWriter(f));
	save(entries, w);
	w.close();
    }

    /** Used to encode iUnperturbed in data files, for PPP sugg */
    private static final String I_UNPERTURBED = "iUnperturbed";

   /** Writes a list of ArticleEntry objects (typically, a suggestion
	list) to a PrintWriter.
     */
    static public void save(Vector<ArticleEntry> entries, PrintWriter w) {
	//	w.println("#--- Entries are ordered by w(t)*idf(t)");
	//	w.println("#term\tw(t)\tw(sqrt(t))\tidf(t)");
	for(int i=0; i<entries.size(); i++) {
	    ArticleEntry e=entries.elementAt(i);
	    w.print(e.id + "\t" + e.score);

	    if (e.iUnperturbed!=e.i) {
		// this is only needed in 3PR, which has no other comments
		e.researcherCommline = I_UNPERTURBED + "=" + e.iUnperturbed;
	    }

	    if (e.researcherCommline!=null && e.researcherCommline.length()>0){
		String s=e.researcherCommline.replaceAll("\"", "'");
		w.print("\t\"" + s + "\"");
	    }
	    w.println();
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

	final Pattern p = Pattern.compile("(\\S+)\\s+(\\S+)\\s*");

	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    Matcher m = p.matcher(s);
	    if (!m.lookingAt())  {
		throw new IOException("Cannot parse line "+linecnt+" in file "+f);
	    }
	   
	    pos++;
	    String aid = m.group(1);
	    ArticleEntry e = new ArticleEntry(pos, aid);
	    e.setScore( Double.parseDouble(m.group(2)));
	    String tail = s.substring(m.end());
	    //System.out.println("readFile: s=["+s+"], tail=["+tail+"]");
	    if (tail.length()>0) {
		tail=tail.replaceAll("\"", ""); // FIXME: ...

		final Pattern pu = Pattern.compile(I_UNPERTURBED + "=(\\d+)");
		Matcher mu = pu.matcher(tail);
		if (mu.matches()) {
		    e.iUnperturbed = Integer.parseInt( mu.group(1));
		    e.researcherCommline = tail; // keep it for display, anyway
		} else {
		    e.researcherCommline = tail;
		}
	    }
	    entries.add(e);
	}
	r.close();
	return entries;
    }

    static public Vector<ArticleEntry> readStoredList(Vector<ListEntry> v) throws IOException {
	Vector<ArticleEntry> entries = new Vector<ArticleEntry>(v.size());
	int pos=0;
	for(ListEntry le: v) {
	    pos++;
	    Article a = le.getArticle();
	    ArticleEntry e = new ArticleEntry(pos, a.getAid());
	    e.iUnperturbed=le.getUnperturbedRank()+1;
	    e.setScore( le.getScore());
	    entries.add(e);
	}
	return entries;
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
	    docno = Common.find(s, id);		    
	}
	return docno;
    }

    /** Applies this user's exclusions, folder inclusions, and ratings.
	FIXME: parallel exclusions must happen on the provenance array too!
     */
    public static void applyUserSpecifics(Vector<ArticleEntry> entries,User u){
	if (u==null) return;
    
	HashMap<String, Action> exclusions = 
	    u.getActionHashMap(new Action.Op[]{Action.Op.DONT_SHOW_AGAIN});
		    
	// exclude some...
	// FIXME: elsewhere, this can be used as a strong negative
	// auto-feedback (e.g., Thorsten's two-pager's Algo 2)
	for(int i=0; i<entries.size(); i++) {
	    String aid=entries.elementAt(i).id;
	    if (exclusions.containsKey(aid)) {
		//Logging.info("AUS Exclusion: " +aid+ " --> " + exclusions.get(aid));
		entries.removeElementAt(i); 
		i--;
	    }
	}

	// Mark pages currently in the user's folder, or rated by the user
	markFolder(entries, u.getFolder());
	markRatings(entries, u.getActionHashMap(Action.ratingOps));
    }

    /** This can be used after some elements in a still-unperturbed list
	have been removed, etc.
     */
    public static void refreshOrder(Vector<ArticleEntry> entries) {
	int cnt=1;
	for(ArticleEntry e: entries) {
	    e.i = e.iUnperturbed = cnt;
	    cnt++;
	}
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

    /** The ID of a DIV element pertianing to this article in search results
	or a recommendation list
     */
    public String resultsDivId() {
	return "result" + i;
    }

    /** Javascript snippet that hides an article's entry. 
     */
    public String hideJS() { 
	return "$('#" + resultsDivId() +"').hide();"; 
    }

    /** Should a particular rating button for this article
	be shown as already checked?
     */
    public boolean buttonShouldBeChecked( Action.Op op) {
	if (op.isToFolder()) return isInFolder;
	else if (op==Action.Op.REMOVE_FROM_MY_FOLDER) return !isInFolder;
	else return (latestRating==op);
    }

    /** Tracing iformation for team-draft merging */
    static public class Provenance {
	final public int arank, brank;
	final public boolean fromA;

	/** Returns 0-based index of the matching element, or -1 if
	    none is found.
	 */
	static private int find(ScoreDoc[] a, ScoreDoc x, int i0) {
	    for(int i=i0; i<a.length; i++) {
		if (a[i].doc == x.doc) return i;
	    }
	    return -1;
	}
    
	Provenance(boolean useA, ScoreDoc[] a,  ScoreDoc[] b, 
			  int nexta, int nextb) {
	    ScoreDoc x = useA? a[nexta] : b[nextb];
	 
	    fromA=useA;
	    arank=find(a,x,nexta)+1;
	    brank=find(b,x,nextb)+1;
	    System.out.println("TeamDraft provenance: (useA="+useA+", x=" + x+", a["+nexta +" -> "+arank+"], b["+nextb+" -> "+brank+"])");
	}
    }
   


    /** For team-draft merging */
    private Provenance prov=null;
    public Provenance getProv() { return prov; }

    /** Sorting by score, in descending order. */
    public int compareTo(ArticleEntry  other) {
	double d = other.score - score; 
	return (d<0) ? -1 : (d>0) ? 1: 0;
    }

    public Object clone() throws CloneNotSupportedException {
	return super.clone();
    }

}



