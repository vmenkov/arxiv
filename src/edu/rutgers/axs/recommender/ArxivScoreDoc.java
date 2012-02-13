package edu.rutgers.axs.recommender;

//import org.apache.lucene.document.*;
//import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import edu.rutgers.axs.web.ArticleEntry;

class ArxivScoreDoc implements Comparable<ArxivScoreDoc> {
    int doc;
    double score;
    ArxivScoreDoc(ScoreDoc x) {
	this(x.doc, x.score);
    }

    ArxivScoreDoc(ArticleEntry ae, IndexSearcher s) throws IOException  {
	this(ae.getCorrectDocno(s), ae.score);
    }
    // Descending order! 
    public int compareTo(ArxivScoreDoc  other) {
	double d = other.score - score; 
	return (d<0) ? -1 : (d>0) ? 1: 0;
    }
    ArxivScoreDoc(int _docno, 	double _score) {
	doc=_docno;  score=_score;
    }

    static ArxivScoreDoc[] toArxivScoreDoc(Vector<ArticleEntry> entries,
					   IndexSearcher searcher) throws IOException  {
	ArxivScoreDoc[] sd  = new ArxivScoreDoc[entries.size()];
	for(int i=0; i<sd.length; i++) {
	    sd[i] = new ArxivScoreDoc(entries.elementAt(i), searcher);
	}
	return sd;
    }


//    static class OurScoreDoc extends ScoreDoc {
//	OurScoreDoc() { super(); }
//	OurScoreDoc(float score, int doc) { super(score, doc); }
//    }


}