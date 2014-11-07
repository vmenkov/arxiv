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

    /*
    public ArticleAnalyzer() throws IOException {
	this(   Common.newReader(), upFields);
    }
    */

    public ArticleAnalyzer(	IndexReader _reader,String [] _fields ) {
	fields = _fields;
	reader =_reader;
    }

    /** Computes <em>idf = 1 + log ( numDocs/docFreq+1)</em>, much
	like it is done in Lucene's own searcher as well. 
	@term May mean a particular word in AA1, or field:word in AA2/AA3
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

    /** Overridden in AA1 */
    CompactArticleStatsArray getCasa() {
	throw new IllegalArgumentException("AA2 does not need CompactArticleStatsArray!");
    }
 

    /** For AA2 and AA3 only. This method exists so that we can have the
     same getCoef code */
    double getFieldNorm(int docno, int fieldNo) throws IOException {
	throw new UnsupportedOperationException("This method is only used in AA2 and AA3");
 
    }

    /** The common getCoef code for AA2 and AA3.

	Computes a (weighted) term frequency vector for a specified
	document.  The refined representation is used; the data for
	each "real" field being retrieved from Lucene, and the DF
	for the additional CONCAT field being obtained by summation.

	@param docno Lucene's internal integer ID for the document,

	@return The raw frequency vector (which is normalized, but does 
	not incorporate the idf)
    */
   
    public HashMap<String,  MutableDouble> getCoef23(int docno) 
	throws IOException {

	HashMap<String, MutableDouble> h = new HashMap<String,MutableDouble>();

	final int nf =fields.length;
	TermFreqVector [] tfvs = new TermFreqVector[nf];

	for(int j=0; j<nf;  j++) {	    
	    String name= fields[j];
	    TermFreqVector tfv= tfvs[j]=reader.getTermFreqVector(docno, name);
	    if (tfv==null) continue;
	    
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {	

		Term term = new Term(name, terms[i]);
		int df = reader.docFreq(term);
		if (df < minDf || UserProfile.isUseless(term)) {
		    continue;// skip very rare words, non-words, and stop words
		}

		String key = ArticleAnalyzer2.mkKey(term);

		double z =  freqs[i] / getFieldNorm(docno,j);
		h.put( key, new MutableDouble(z));

		key =  ArticleAnalyzer2.mkKey(CONCAT, terms[i]);
		z =  freqs[i] / getFieldNorm(docno, nf);
		MutableDouble val = h.get(key);
		if (val==null) h.put(key, new MutableDouble(z));
		else val.add(z);
	    }	
	}
	return h;
    }


}
