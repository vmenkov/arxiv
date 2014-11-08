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

/** Another way to compute article stats: only the norms of
    needed docs are computed.
 */
public class ArticleAnalyzer3 extends  ArticleAnalyzer23 {

    HashMap<Integer, double[]> norms = new  HashMap<Integer, double[]>();
    double getFieldNorm(int docno, int fieldNo)  throws IOException{
	Integer key = new Integer(docno);
	double[] q = norms.get(key);
	if (q == null) {
	    computeNorms(docno);
	    q = norms.get(key);
	}
	return q[fieldNo];
    }


    ArticleAnalyzer3(IndexReader _reader, String [] _fields) throws IOException {
	super(_reader, _fields);
    }

    /** Retrieves or computes idf value as appropriate.
	In AA3, it's exactly same as in AA2.
	@param key "field:text"
     */
    double idf(String key) throws IOException {
	String[] q = key.split(":");
	if (q.length != 2) throw new IllegalArgumentException("Calling AA2.idf() expects a qualified term (f:t); received "+key);
	return idf(q[0], q[1]);
    }


    /** This is designed to be similar to the function in AA2, but the
	value for the CONCAT is diffierent, because we don't have the
	pre-filled arrat concatDfTable. Here, for CONCAT we just use
	the max of the df in all fields.
    */
    private double idf(String field, String text) throws IOException {
	int df = 0;	
	if (field.equals(CONCAT)) {
	    final int nf =fields.length;
	    for(int j=0; j<nf;  j++) {	    
		String name= fields[j];
		Term term = new Term(name, text);
		int d = reader.docFreq(term);
		df = (d > df)? d : df;
	    }
	} else {
	    Term term = new Term(field, text);
	    df = reader.docFreq(term);
	}
	if (df<=0) throw new IllegalArgumentException("AA2.idf("+field+":"+ text+"): df=0");
	return ArticleAnalyzer2.computeIdf(reader.numDocs(), df);
    }

    /** Exactly same as in AA2 */
    Term keyToTerm(String key) //throws IOException 
    {
	String[] q = key.split(":");
	if (q.length != 2) throw new IllegalArgumentException("Calling AA3.idf() expects a qualified term (f:t); received "+key);
	return new Term(q[0], q[1]);
    }

    void computeNorms(int docno) throws IOException {

	final int nf =fields.length;
	TermFreqVector [] tfvs = new TermFreqVector[nf];
	double q[] = new double[nf+1];

	CoMap<String> concatDf = new CoMap<String>();

	for(int j=0; j<nf;  j++) {	    
	    String name= fields[j];
	    TermFreqVector tfv= tfvs[j]=reader.getTermFreqVector(docno, name);
	    if (tfv==null) continue;
	 
	    double s = 0;
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {	
		double w =  idf(name, terms[i]);
		s += freqs[i]*freqs[i]*w;
		concatDf.addCo(  terms[i], freqs[i]);
	    }
	    q[j] = Math.sqrt(s);
	}
	double s=0;
	for(String term:  concatDf.keySet()) {
	    double z = concatDf.get(term).doubleValue();
	    double w =  idf(CONCAT, term);
	    s += z*z * w;
	}
	q[nf] =  Math.sqrt(s);	
	//	Integer key = new Integer(docno);
	norms.put(docno, q);
	System.out.println("Done norms for " + docno);
    }

    /** Stores DF for terms */
    private HashMap<String, Integer> h = new HashMap<String, Integer>();

    /** Document friequency for a term. When a term occurs in multiple 
	fields of a doc, it is counted multiple times, because it's easier
	to do it this way in Lucene.
    */ 
    private int totalDF(String t) throws IOException {
	Integer val = h.get(t);
	if (val!=null) return val.intValue();
	
	if (h.size()> 1000000) {
	    // FIXME: this could be done in a more intelligent way,
	    // maybe removing smallest values (least likely to be used
	    // again)...
	    Logging.info("Clearing totalDF hash table");
	    h.clear(); // trying to prevent OutOfMemoryError
	}

        int sum = 0;
	for(String name: fields) {
	    Term term = new Term(name, t);
	    sum += reader.docFreq(term);
	}
	h.put(t, new Integer(sum));
	return sum;
    }
    
    /** This method is not needed in AA3, as it is never used with the full 
	Algo 1.
     */
    TjA1EntryData prepareTjA1EntryData(int docno,
				       HashMap<String, UserProfile.TwoVal> hq,
				       Map<String,Integer> termMapper)
    //throws IOException 
    {
	throw new UnsupportedOperationException();
    }

    public HashMap<String,  MutableDouble> getCoef(int docno) 
	throws IOException {
	return  getCoef23( docno);
    }


}
    