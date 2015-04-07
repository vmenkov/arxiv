package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.commons.lang.mutable.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;


/** Tools for getting numbers out of the Lucene index. This includes
    computing/extracting DF (document frequency) on the Lucene index,
    as well as the tf (term frequency) vector for a document.

    <p>This is an abstract class, with several derived classes available;
    they differ in how they manage the data.
 */
public abstract class ArticleAnalyzer {

     /** Document fields used in creating the user profile */
    public static final String upFields[] =  {
	ArxivFields.TITLE, 
	ArxivFields.AUTHORS, ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE};

    /** Used as the name of the "flattened article" field in AA2 and AA3 */
    static final String 	CONCAT = "concat";

    IndexReader reader;
    public IndexReader getReader()  throws IOException {
	return reader;
    }

    final String [] fields;

    public ArticleAnalyzer(	IndexReader _reader,String [] _fields ) {
	fields = _fields;
	reader =_reader;
    }

    /** Computes <em>idf = 1 + log ( numDocs/docFreq+1)</em>, much
	like it is done in Lucene's own searcher as well. 
	@param term May mean a particular word in AA1, or field:word in AA2/AA3
    */
    abstract public double idf(String term) throws IOException;

    /** Gets a TF vector for a document from the Lucene data store. This is
	used e.g. when initializing and updating user profiles. The vector
	does not include IDF, but is divided by the doc norm.
	@param aid Arxiv article ID.
    */
    public HashMap<String, ?extends Number> getCoef(String aid) throws IOException {
	int docno = -1;
	try {
	    docno = Common.find(reader, aid);
	} catch(Exception ex) {
	    Logging.warning("No document found in Lucene data store for id=" + aid +"; skipping");
	    return new HashMap<String, Double>();
	}
	return getCoef(docno);
    }

    public abstract HashMap<String, ?extends Number> getCoef(int docno) throws IOException;

    /** Minimum DF needed for a term not to be ignored. My original
     default was to only ignore nonce words (df&lt;2), but Paul
     Ginsparg suggests ignoring terms with DF&lt;10 (2013-02-06). */
    static int minDf = 10;

    static public void setMinDf(int x) { minDf = x; }
    static public int getMinDf() { return minDf; }

    /** An auxiliary structure in which data are packed for TjA1Entry */
    static class TjA1EntryData {
	double sum1 = 0;
	double[] w2plus, w2minus;
	TjA1EntryData(int nt) {
	    w2plus =  new double[nt];
	    w2minus =  new double[nt];
	}
    }

    /** This flag controls whether TF or sqrt(TF) is used to compute
      dot products and norms of documents. The norms so computed are
      incorporated in the fields' boost factors stored in the
      database, so one should not change this flag without rerunning
      ArticleAnalyzer on the entire corpus...
     */
    static final boolean useSqrt = false;

    /** Computes various dot products that are used to initialize
	a TjA1Entry structure for Algorithm 1. 

	<p>FIXME: Strictly speaking, we should verify for each term
	that it is not a stopword or excludable term. However, for
	efficiency's sake, we don't do it, in the expectation that the
	user profile (which should have been computed the same day or
	a few days ago, using the appropriate stopword list and
	"useless term" exclusion rules) will not contain such terms.
	This aproach will break, however, if the stopword list changes,
	and the user profile ends up dragging in terms that were
	"useful" in the past, but are considered useless now.

       @param hq  User profile vector (UserProfile.hq)
     */
    abstract TjA1EntryData prepareTjA1EntryData(int docno,
    				       HashMap<String, UserProfile.TwoVal> hq,
    				       Map<String,Integer> termMapper)
    	throws IOException;

    abstract Term keyToTerm(String key); //throws IOException 

}
