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

    /** Initializes an ArticleEntry object based on the data in a Document
	object.
	@param doc a Document object, pfobably just retrieved from the
	Lucene data store.
     */
    public ArticleEntry(int _i, Document doc) {
	i = _i;
	id=doc.get("paper");
	idline="arXiv:" + id;
	titline = doc.get("title");
	authline=doc.get("authors");
	String c= doc.get("comments");
	commline=(c==null? "": "Comments:" + c);
	subjline="Subjects:" + doc.get("category");
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

    public String toString() {
	return "ArticleEntry(i="+i+", aid=" + id+")"; 
    }

    /** Retrieves article's info from Lucene, and initializes the
	ArticleEntry object for it. If there are no data on the article in
	the local data store (e.g., becasue it has not been updated),
	may return a "dummy" entry. */
    static ArticleEntry getArticleEntry(Searcher s, String aid, int pos) {
	ArticleEntry dummy =  getDummyArticleEntry( aid,  pos);
	try {
	    if (s==null) return dummy;
	    TermQuery tq = new TermQuery(new Term("paper", aid));
	    ScoreDoc[] 	scoreDocs = s.search(tq, 1).scoreDocs;
	    if (scoreDocs.length < 1) return dummy;
	    Document doc = s.doc(scoreDocs[0].doc);
	    if (doc==null) return dummy;
	    return new ArticleEntry(pos, doc);
	} catch (Exception ex) { return null; }
    }

    static ArticleEntry getDummyArticleEntry(String aid, int pos) {
	return new ArticleEntry(pos, aid);      
    }

    static public void markFolder( Vector<ArticleEntry> entries,
				   HashMap<String, Action> folder ) {
	for(ArticleEntry e: entries) {
	    e.isInFolder = folder.containsKey(e.id);
	}
    }

    static public void markRatings( Vector<ArticleEntry> entries,
				    HashMap<String, Action> ratings ) {
	for(ArticleEntry e: entries) {
	    if (ratings.containsKey(e.id)) {
		e.latestRating = ratings.get(e.id).getOp();
	    }
	}
    }

}



