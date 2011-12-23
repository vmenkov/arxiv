package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.options.Options;
//import edu.cornell.cs.osmot.logger.Logger;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;

/** Data for a single search result (an article) */
public class ArticleEntry {
    /** Sequence number in the overall search result sequence */
    public int i;
    /** Article id, as in the arXiv database */
    public String id;
    public String idline, titline, authline, commline, subjline;
    /** True if this article is in the user's personal folder */
    public boolean isInFolder=false;

    ArticleEntry(int _i, Document doc) {
	i = _i;
	id=doc.get("paper");
	idline="arXiv:" + id;
	titline = doc.get("title");
	authline=doc.get("authors");
	String c= doc.get("comments");
	commline=(c==null? "": "Comments:" + c);
	subjline="Subjects:" + doc.get("category");
    }
    
    /** The URL (relative to the CP) for recording a judgment on this doc */
    public String judge(Action.Op op) {
	return "JudgmentServlet?"+BaseArxivServlet.ID +"=" + id +
	    "&" +BaseArxivServlet.ACTION+ "=" + op;
    }

    /** May return null on failure */
    static ArticleEntry getArticleEntry(Searcher s, String aid, int pos) {
	try {
	    if (s==null) return null;
	    TermQuery tq = new TermQuery(new Term("paper", aid));
	    ScoreDoc[] 	scoreDocs = s.search(tq, 1).scoreDocs;
	    if (scoreDocs.length < 1) return null;
	    Document doc = s.doc(scoreDocs[0].doc);
	    if (doc==null) return null;
	    return new ArticleEntry(pos, doc);
	} catch (Exception ex) { return null; }
    }


}



