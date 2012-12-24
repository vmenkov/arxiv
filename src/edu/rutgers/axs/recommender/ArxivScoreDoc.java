package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.web.ArticleEntry;

/** Our replacement of sort's for Lucene's ScoreDoc class */
public class ArxivScoreDoc implements Comparable<ArxivScoreDoc> {
    /** Lucene doc id */
    public int doc;
    public double score;
    public ArxivScoreDoc(ScoreDoc x) {
	this(x.doc, x.score);
    }

    ArxivScoreDoc(ArticleEntry ae, IndexSearcher s) throws IOException  {
	this(ae.getCorrectDocno(s), ae.score);
    }

    public ArxivScoreDoc setScore(double x) {
	score=x;
	return this;
    }

    // Descending order! 
    public int compareTo(ArxivScoreDoc  other) {
	double d = other.score - score; 
	return (d<0) ? -1 : (d>0) ? 1: 0;
    }
    public ArxivScoreDoc(int _docno, 	double _score) {
	doc=_docno;  score=_score;
    }

    static ArxivScoreDoc[] toArxivScoreDoc(ScoreDoc[] a) {
	ArxivScoreDoc[] b = new  ArxivScoreDoc[a.length];
	for(int i=0; i<a.length; i++) {
	    b[i] = new ArxivScoreDoc(a[i]);
	}
	return b;
    }

    static ArxivScoreDoc[] toArxivScoreDoc(Vector<ArticleEntry> entries,
					   IndexSearcher searcher) throws IOException  {
	ArxivScoreDoc[] sd  = new ArxivScoreDoc[entries.size()];
	for(int i=0; i<sd.length; i++) {
	    sd[i] = new ArxivScoreDoc(entries.elementAt(i), searcher);
	}
	return sd;
    }

    static public Vector<ArticleEntry> packageEntries(ArxivScoreDoc[] scores, IndexReader reader)  
	throws IOException    {
	Vector<ArticleEntry> entries = new Vector<ArticleEntry>(scores.length);
	for(int i=0; i< scores.length; i++) {
	    ArxivScoreDoc sd = scores[i];
	    Document doc = reader.document( sd.doc);
	    // FIXME: could use  "skeleton" constructor instead to save time   
	    ArticleEntry ae= new ArticleEntry(i+1, doc, sd.doc);
	    ae.setScore( sd.score);
	    entries.add( ae);
	}	
	return entries;
    }
  


//    static class OurScoreDoc extends ScoreDoc {
//	OurScoreDoc() { super(); }
//	OurScoreDoc(float score, int doc) { super(score, doc); }
//    }


}