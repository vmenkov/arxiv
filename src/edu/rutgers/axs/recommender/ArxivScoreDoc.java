package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.web.ArticleEntry;

/** Our replacement of sorts for Lucene's ScoreDoc class.

    <p>(Hmm... Why did I create this class? My recollection is that
    I had to do it because Lucene's ScoreDoc did not have a public 
    constructor - but that's obviously wrong, since it does!)
 */
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

    /** Sorting by score, in descending order. */
    public int compareTo(ArxivScoreDoc  other) {
	double d = other.score - score; 
	return (d<0) ? -1 : (d>0) ? 1: 0;
    }
    public ArxivScoreDoc(int _docno, 	double _score) {
	doc=_docno;  score=_score;
    }

    static public ArxivScoreDoc[] toArxivScoreDoc(ScoreDoc[] a) {
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
	return  packageEntries( scores, null, reader);
    }

    /** Converts an array of ArxivScoreDoc objects (as produced
       e.g. by a search) into a vector of ArticleEntry objects,
       suitable for saving into a suggestion list file.

       @param comments Array of optional comments (to be shown to researchers). Null values are allowed, and ignored.
     */
    static public Vector<ArticleEntry> packageEntries(ArxivScoreDoc[] scores, String comments[], IndexReader reader)  
	throws IOException    {
	Vector<ArticleEntry> entries = new Vector<ArticleEntry>(scores.length);
	for(int i=0; i< scores.length; i++) {
	    ArxivScoreDoc sd = scores[i];
	    Document doc = reader.document( sd.doc);
	    // FIXME: could use  "skeleton" constructor instead to save time   
	    ArticleEntry ae= new ArticleEntry(i+1, doc, sd.doc);
	    ae.setScore( sd.score);
	    if (comments!=null && comments[i]!=null) {
		ae.researcherCommline = comments[i];
	    }
	    entries.add( ae);
	}	
	return entries;
    }
 
    /** Sorts the values in the vector, and returns the M top values.
	<p> If M &ll; v.size(), a more efficient algorithm can be employed
	instead of complete sorting.
     */
    public static ArxivScoreDoc[] getTopResults(Vector<ArxivScoreDoc> v, int M) {
	ArxivScoreDoc[] a = (ArxivScoreDoc[])v.toArray(new ArxivScoreDoc[v.size()]);
	Arrays.sort(a);
	return (a.length <= M) ? a : Arrays.copyOf(a, M);
    }
 
}