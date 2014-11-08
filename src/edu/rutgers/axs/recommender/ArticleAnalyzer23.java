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


/** The common parent for AA2 and AA3.
 */
public abstract class ArticleAnalyzer23 extends ArticleAnalyzer {

    /** Used as the name of the "flattened article" field in AA2 and AA3 */
    static final String 	CONCAT = "concat";

    public ArticleAnalyzer23(	IndexReader _reader,String [] _fields ) {
	super(_reader, _fields);
    }

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
    TjA1EntryData prepareTjA1EntryData(int docno,
				       HashMap<String, UserProfile.TwoVal> hq,
				       Map<String,Integer> termMapper)
	throws IOException {

	final int nt=termMapper.size();
	TjA1EntryData tj = new TjA1EntryData(nt);

	double[] nr = getFieldNorms(docno);

	double boostConcat = 1.0 / nr[fields.length];

	for(int j=0; j< fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) {
		//Logging.warning("No tfv for docno="+docno+", field="+fields[j]);
		continue;
	    }

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    

	    double boost = 1.0 / nr[j];

	    for(int i=0; i<terms.length; i++) {
		String key=mkKey(fields[j], terms[i]);	       
		UserProfile.TwoVal q= hq.get(key);
		if (q!=null && nr[j]>0) {
		    // term position in upro.terms[]
		    int iterm = termMapper.get(key).intValue();		    

		    double z = freqs[i] * boost;
		    double idf = idf(fields[j], terms[i]);	       

		    tj.sum1 += z * q.w1 *idf;
		    double w2q =  z * idf * q.w2 * q.w2 ;
		    if (w2q<0) throw new AssertionError("w2q<0: this is impossible!");
		    (q.w2 >= 0 ? tj.w2plus: tj.w2minus)[iterm] += w2q;
		}

		//-- the concat field
		key=mkKey(CONCAT, terms[i]);	       
		q= hq.get(key);
		if (q!=null && nr[fields.length]>0 ) {
		    int iterm = termMapper.get(key).intValue();
		    double z = freqs[i] * boostConcat;
		    double idf = idf(CONCAT, terms[i]);	       
		    tj.sum1 += z * q.w1 *idf;
		    double w2q =  z * idf * q.w2 * q.w2 ;
		    if (w2q<0) throw new AssertionError("w2q<0: this is impossible!");
		    (q.w2 >= 0 ? tj.w2plus: tj.w2minus)[iterm] += w2q;

		}
	    }
	}
	return tj;
    }

   
    /** Overridden in AA1 */
    //    CompactArticleStatsArray getCasa() {
    //	throw new UnsupportedOperationException("AA2/AA3 do not need CompactArticleStatsArray!");
    //    }
 

    /** For AA2 and AA3 only. This method exists so that we can have the
     same getCoef code */
    abstract double getFieldNorm(int docno, int fieldNo) throws IOException;

    final double[] getFieldNorms(int docno) throws IOException {
	final int nf =fields.length;
	double[] nr = new double[nf+1];
	for(int j=0; j<nf+1;  j++) {	    
	    nr[j] = getFieldNorm(docno,j);
	}
	return nr;
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

	double[] nr = getFieldNorms(docno);

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

		double z =  freqs[i] / nr[j];
		h.put( key, new MutableDouble(z));

		key =  ArticleAnalyzer2.mkKey(CONCAT, terms[i]);
		z =  freqs[i] / nr[nf];
		MutableDouble val = h.get(key);
		if (val==null) h.put(key, new MutableDouble(z));
		else val.add(z);
	    }	
	}
	return h;
    }

    abstract double idf(String field, String text) throws IOException;

    /** Make a key for a particular qualified term in UserProfile */
    static String mkKey(String field, String text) {
	return field + ":" + text;
    }
    static String mkKey(Term t) {
	return mkKey(t.field(), t.text());
    }



}

