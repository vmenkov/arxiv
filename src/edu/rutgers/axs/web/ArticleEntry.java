package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

//import edu.cornell.cs.osmot.options.Options;
//import edu.cornell.cs.osmot.logger.Logger;

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
    /** True if this article is in the user's personal folder */
    public boolean isInFolder=false;
    /** The most recent rating, if any, given by the user to this article. */
    public Action.Op latestRating = Action.Op.NONE;
    /** This may be set if the article has come from some kind of a ranked-list
	search */
    public double score=0;

    /** Initializes an ArticleEntry object based on the data in a Document
	object.
	@param doc a Document object, pfobably just retrieved from the
	Lucene data store.
     */
    public ArticleEntry(int _i, Document doc) {
	i = _i;
	id=doc.get(ArxivFields.PAPER);
	idline="arXiv:" + id;
	populateOtherFields( doc);
    }

    void populateOtherFields( Document doc) {
	titline = doc.get(ArxivFields.TITLE);
	authline=doc.get(ArxivFields.AUTHORS);
	String c= doc.get(ArxivFields.COMMENTS);
	commline=(c==null? "": "Comments:" + c);
	subjline="Subjects:" + doc.get((ArxivFields.CATEGORY));
    }
    

    /** Dummy constructor, leaves most fields blank */
    private ArticleEntry(int _i, String aid) {
 	i = _i;
	id=aid;
	idline="arXiv:" + id;
	titline = "";
	authline="";
	commline="";
	subjline="";
     }

    public void setScore(double x) { score=x;}

    public String toString() {
	return "ArticleEntry(i="+i+", aid=" + id+")"; 
    }

    /** Retrieves article's info from Lucene, and initializes the
	ArticleEntry object for it. If there are no data on the article in
	the local data store (e.g., becasue it has not been updated),
	may return a "dummy" entry. 
	@param aid Arxiv id for the article
	@param pos Position in our list (has nothing to do with Lucene)
    */
    static ArticleEntry getArticleEntry(Searcher s, String aid, int pos) {
	ArticleEntry dummy =  getDummyArticleEntry( aid,  pos);
	try {
	    if (s==null) return dummy;
	    TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, aid));
	    ScoreDoc[] 	scoreDocs = s.search(tq, 1).scoreDocs;
	    if (scoreDocs.length < 1) return dummy;
	    Document doc = s.doc(scoreDocs[0].doc);
	    if (doc==null) return dummy;
	    return new ArticleEntry(pos, doc);
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

    /** Saves the profile to the specified file. Before doing so, verifies
	that the necessary directory exists, and if it does not, tries to
	create it.
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

    static public void save(Vector<ArticleEntry> entries, PrintWriter w) {
	//	w.println("#--- Entries are ordered by w(t)*idf(t)");
	//	w.println("#term\tw(t)\tw(sqrt(t))\tidf(t)");
	for(int i=0; i<entries.size(); i++) {
	    ArticleEntry e=entries.elementAt(i);
	    w.println(e.id + "\t" + e.score);
	}
    }

    /** Reads a file saved earlier and creates a vector of "skeleton"
      entries (just article id)
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

}



