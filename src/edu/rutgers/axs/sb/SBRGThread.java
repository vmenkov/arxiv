package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;

class SBRGThread extends Thread {
    private final SBRGenerator parent;

    /** Creates a thread. You must call its start() next */
    SBRGThread(SBRGenerator _parent) {
	parent = _parent;
    }

    /** The number of actions in the session so far      */
    int actionCount=0;
    /** The number of viewed pages in the session so far. (It may be
     smaller than actionCount, because the user may have viewed the
     same page multiple times). */
    int articleCount=0;
    
    /** When the list generation started and ended. We keep this 
     for statistics. */
    Date startTime, endTime;

    /** The main class for the actual generation */
    public void run()  {
	startTime = new Date();
	
	IndexReader reader=null;
	IndexSearcher searcher = null;

	final boolean trivial = false;

	EntityManager em=null;
	try {
	    em = parent.sd.getEM();       
	    reader=Common.newReader();
	    searcher = new IndexSearcher( reader );
	    // get the list of article IDs to recommend by some algorithm
	    if (trivial) {
		computeRecListTrivial(em,searcher);
	    } else {
		computeRecList(em,searcher);
	    }
	    plid = saveAsPresentedList(em).getId();
	} catch(Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	    //sr = null;
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    try {
		if (searcher!=null) searcher.close();
		if (reader!=null) reader.close();
	    } catch(IOException ex) {}
	    endTime = new Date();
	}
	parent.completeRun();

    }

    /** The recommendation list generated by this method */
    public SearchResults sr=null;
    /** The ID of the PresentedList structure in which the rec list was
	saved in the database. */
    public long plid=0;

    boolean error = false;
    String errmsg = "";

    /** Retrieves the list of articles viewed by the user in this session 
	so far. */
    private Vector<String> listViewedArticles(EntityManager em) {
	Vector<Action> va = Action.actionsForSession( em, parent.sd.getSqlSessionId());
	actionCount = va.size();
	Vector<String> viewedArticles = new Vector<String>();
	HashSet<String> h = new HashSet<String>();
	for(Action a: va) {
	    Article art = a.getArticle();
	    String aid = art.getAid();
	    if (!h.contains(aid)) {
		viewedArticles.add(aid);
		h.add(aid);
	    }
	}
	articleCount=viewedArticles.size();
	return viewedArticles;
    }

    /** For ascending-order sort, i.e. rank 1 before rank 2 etc */
    private static class ArticleRanks implements Comparable<ArticleRanks> {
	int docno;
	/** This is *not* used for sorting */
	double score=0;
	Vector<Integer> ranks=new Vector<Integer>();
	public int compareTo(ArticleRanks o) {
	    for(int i=0; i<ranks.size() && i<o.ranks.size(); i++) {
		int z=ranks.elementAt(i).intValue() - o.ranks.elementAt(i).intValue();
		if (z!=0) return z;
	    }
	    return o.ranks.size() - ranks.size();
	}
	ArticleRanks(int _docno) { docno = _docno; }
	/** Should be called in order of non-decreasing r */
	void add(int r, double deltaScore) {
	    if (ranks.size()>0 && r<ranks.elementAt(ranks.size()-1).intValue()){
		throw new IllegalArgumentException("ArticleRanks.add() calls must be made in order");
	    }
	    ranks.add(new Integer(r));
	    score +=  deltaScore;
	}
    }

    /** A text message containing the list of ArXiv article IDs of the
	articles that we decided NOT to show in the rec list (e.g., because
	they had already been shown to the user in this session in
	other contexts).
    */
    String excludedList = "";
 
    /** Generates the list of recommendations based on searching the Lucene
	index for articles whose abstracts are similar to those of the
	articles viewed by the user in this session.
     */
    private void computeRecList(EntityManager em, IndexSearcher searcher) {
	try {
	    Vector<String> viewedArticles = listViewedArticles(em);

	    HashSet<String> exclusions = parent.linkedAids;
	    exclusions.addAll(viewedArticles);

	    // abstract match, separately for each article
	    final int maxlen = 100;
	    final int maxRecLenTop = 25;
	    final int maxRecLen = Math.min(3*viewedArticles.size(), maxRecLenTop);
	    ScoreDoc[][] asr  = new ScoreDoc[viewedArticles.size()][];
	    int k=0;
	    for(String aid: viewedArticles) {
		ScoreDoc[] z = parent.articleBasedSD.get(aid);
		if (z==null) {
		    int docno= Common.find(searcher, aid);
		    Document doc = searcher.doc(docno);
		    String abst = doc.get(ArxivFields.ABSTRACT);
		    abst = abst.replaceAll("\"", " ").replaceAll("\\s+", " ").trim();
		    z = (new LongTextSearchResults(searcher, abst, maxlen)).scoreDocs;
		    parent.articleBasedSD.put(aid,z);
		}
		asr[k++] = z;
	    }

	    // merge all lists
	    //	    HashMap<String,ArticleRanks> hr= new HashMap<String,ArticleRanks>();
	    HashMap<Integer,ArticleRanks> hr= new HashMap<Integer,ArticleRanks>();
	    for(int j=0; j<maxlen; j++) {
		for(ScoreDoc[] z: asr) {
		    if (j<z.length) {
			int docno = z[j].doc;
			Integer key = new Integer(docno);
			ArticleRanks r= hr.get(key);
			if (r==null) { 
			    hr.put(key, r=new ArticleRanks(docno));
			}
			r.add(j, z[j].score);		      
		    }
		}
	    }
	    ArticleRanks[] ranked = (ArticleRanks[])hr.values().toArray(new ArticleRanks[0]);
	    Arrays.sort(ranked);

	    Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	    k=1;
	    for(ArticleRanks r: ranked) {
		/*
		ArticleEntry ae = new ArticleEntry(++k, r.aid);
		ae.setScore(r.score);
		*/
		ArticleEntry ae= new ArticleEntry(k, searcher.doc(r.docno),
						  new ScoreDoc(r.docno, (float)r.score));
		if (exclusions.contains(ae.id)) {
		    excludedList += " " + ae.id;
		    continue;
		}
		entries.add(ae);
		k++;
		if (entries.size()>=maxRecLen) break;
	    }
	    sr = new SearchResults(entries); 
	    //sr.saveAsPresentedList(em,Action.Source.SB,null,null, null);
	}  catch (Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    Logging.error(""+ex);
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	}
    }

    /** Generates the trivial recommendation list: 
	(rec list) = (list of viewed articles). This method was used
	for quick testing.
     */
    private void computeRecListTrivial(EntityManager em, IndexSearcher searcher) {
	try {
	    Vector<String> viewedArticles = listViewedArticles(em);
	    // trivial list: out=in
	    Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	    int k=0;
	    for(String aid:  viewedArticles) {
		ArticleEntry ae = new ArticleEntry(++k, aid);
		ae.setScore(1.0);
		entries.add(ae);
	    }
	    sr = new SearchResults(entries); 
	    addArticleDetails(searcher);
	    //sr.saveAsPresentedList(em,Action.Source.SB,null,null, null);
	}  catch (Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    Logging.error(""+ex);
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	}
    }

    /** Adds article title etc to each entry in sr.entries */
    private void addArticleDetails(IndexSearcher searcher) throws IOException { 
	for(ArticleEntry ae: sr.entries) {
	    ae.populateOtherFields(searcher);
	}	    
    }


    private PresentedList saveAsPresentedList(EntityManager em) {
	PresentedList plist = new PresentedList(Action.Source.SB, null);
	plist.fillArticleList(sr.entries);	
	em.getTransaction().begin();
	em.persist(plist);
	em.getTransaction().commit();
	return plist;
    }

    /** A human-readable description of what this thread had done. */
    public String description() {
	String s = "Session-based recommendation list produced by thread " + getId() +"; started at " + startTime +", finished at " + endTime;
	if (startTime!=null && endTime!=null) {
	    long msec = endTime.getTime() - startTime.getTime();
	    s += " (" + (0.001 * (double)msec) + " sec)";
	}
	s += ".";
	s += " The list is based on " + actionCount + " user actions (" +
	    articleCount + " viewed articles)";
	return s;
    }

}

