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

/** Another way to compute article stats..
 */
public class ArticleAnalyzer2 extends  ArticleAnalyzer {

    static final boolean verbose = true;

    /** Norms of all real and concatenated fields of all documents */
    private final double[][] norms;
    private final int numdocs, maxdoc;
    private static String 	CONCAT = "concat";

    /** Contains df values of all terms accepted for the
	"concatenated" field". The absence of a term in the table
	means that the term has been excluded. Since nonce-words are not
	kept, the table size is around 1M keys.
    */
    private HashMap<String,Integer> concatDfTable = null;

    /** Computes norms for all real fields, as well as for one
	artificial field, which contains the "flattened document" (=
	concatenation of fields).
     */
    ArticleAnalyzer2(IndexReader _reader) throws IOException {
	reader = _reader;
	numdocs = reader.numDocs();
	maxdoc=reader.maxDoc() ;
	double[][] w0 =computeFieldNorms();
	int nf = upFields.length;
	norms = new double[nf+1][];
	double[][] weights = new double[nf][];
	for(int i=0;i<nf; i++) { 
	    norms[i] = w0[i]; 
	    weights[i] = new double[norms[i].length];
	    for(int j=0; j<weights[i].length; j++) weights[i][j]=1;
	}
	norms[nf]= multiNorms( fields, weights);	
	printStats(upFields, norms);
    }	
    
    private static double computeIdf(int numdocs, int df)  {
	double idf = 1+ Math.log(numdocs / (1.0 + df));
	return idf;
    }

    /** Retrieves or computes idf value as appropriate 
	@param key "field:text"
     */
    public double idf(String key) throws IOException {
	String[] q = key.split(":");
	if (q.length != 2) throw new IllegalArgumentException("Calling AA2.idf() expects a qualified term (f:t); received "+key);
	return idf(q[0], q[1]);
    }

    private double idf(String field, String text) throws IOException {

	if (field.equals(CONCAT)) {
	    Integer val = concatDfTable.get(text);
	    return val==null? 0: computeIdf(numdocs,val.intValue());
	} else {
	    Term term = new Term(field, text);
	    int df = reader.docFreq(term);
	    if (df<=0) throw new IllegalArgumentException("AA2.idf("+field+":"+ text+"): df=0");
	    return computeIdf(numdocs, df);
	}
    }


    /** Computes the norms of all documents' "real" fields */
    private double[][] computeFieldNorms() throws IOException{

	Logging.info("AA2: numdocs=" + numdocs + ", maxdoc=" + maxdoc + "; minDF=" + ArticleAnalyzer.getMinDf() );

	final int nf=upFields.length;
	double[][] w= new double[nf][];

	for(int i=0; i<nf; i++) {
	    String f= upFields[i];	
	    Logging.info("Field=" + f);
	    Term startTerm = new Term(f, "0");
	    TermEnum te = reader.terms(startTerm);

	    double[] v = w[i] = new double[maxdoc];

	    int tcnt =0;
	    long sumTf = 0;
	    double minIdf = 0, maxIdf = 0;
	    while(te.next()) {
		Term term = te.term();
		if (!term.field().equals(f)) break;

		String text = term.text();
		
		int df = reader.docFreq(term);
		if (df < ArticleAnalyzer.getMinDf() || UserProfile.isUseless(term)) continue;

		double idf = computeIdf(numdocs, df);
		if (Double.isNaN(idf) || idf < 0) {
		    Logging.error("term=" + term+", df=" + df + ", idf=" + idf);
		    throw new AssertionError("idf=" + idf);
		} 

		TermDocs td = reader.termDocs(term);
		td.seek(term);

		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();
		    sumTf += freq;
		    double z = (double)freq*(double)freq *idf;
		    v[p]+=z;
		}   
		tcnt++;
	    }
	    te.close();
	    Logging.info("Scanned " + tcnt + " terms; sumTf=" + sumTf);
	}

	for(int i=0; i<nf; i++) {
	    for(int docno=0; docno<maxdoc; docno++) {
		double q = w[i][docno];
		if (Double.isNaN(q) || q<0) {
		    Logging.error("w["+i+"]["+docno+"]=" +q);
		    throw new AssertionError("w["+i+"]["+docno+"]=" +q);
		}
		if (q!=0) w[i][docno]=Math.sqrt(q);
	    }
	}

	return w;
    }

    /** Computes and prints some of the norm statistics */
    void printStats(String[] fields, double[][] w) throws IOException {
	int nf = w.length;
	int cnt=0;
	double[] sum= new  double[nf];
	double[] maxNorm= new  double[nf];
	double[] minNorm= new  double[nf];
	int[] emptyFieldCnt= new  int[nf];
	for(int docno=0; docno<maxdoc; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno,ArticleStats.fieldSelectorAid);
	    if (doc==null) continue;
	    String aid = doc.get(ArxivFields.PAPER);
	    for(int i=0; i<nf; i++) {
		String f= (i < fields.length) ? fields[i] : "Composite";	
		double q = w[i][docno];
		sum[i] += q;
		if (cnt==0 || q>maxNorm[i]) maxNorm[i]=q;
		if (cnt==0 || q<minNorm[i]) minNorm[i]=q;
		if (q==0) {
		    if (verbose && emptyFieldCnt[i]<printMissing) {
			Logging.info("Empty field " + f + " in doc["+docno+" -> " +aid+"]");
		    }
		    emptyFieldCnt[i]++;
		}
	    }
	    cnt++;
	}
	Logging.info("counted " + cnt + " documents");
	for(int i=0; i<nf; i++) {
	    String f= (i < fields.length) ? fields[i] : "Composite";	
	    Logging.info("Field=" + f +", <|v|>=" + sum[i]/cnt +", " + 
			 minNorm[i] +"<=|v|<=" + maxNorm[i] +"; empty in " + emptyFieldCnt[i] + " docs");
	}
    }

    /** Sums the df values for the specified terms) */
    private int sumDF(Term [] terms) throws IOException {
	int totalDF = 0;
	for(Term t: terms) {
	    totalDF += reader.docFreq(t);
	}
	return totalDF;
    }
   
    /** Computing the norm of a "flattened" (concatenated) field. As a
	side effect, precomputes and saves IDFs of terms with respect
	to the concatenated field. */
    private double[] multiNorms(String[] fields, double[][] weights)  throws IOException{
	Logging.info("Computing norms for the concatenated field");
	final int nf = fields.length;
	double[] sum =  new double[maxdoc];
	TermEnum[] tes = new TermEnum[nf];
	boolean closed[] = new boolean[nf];
	for(int i=0; i<nf; i++) {
	    String f= fields[i];	
	    //Logging.info("Field=" + f);
	    Term startTerm = new Term(f, "0");
	    tes[i] = reader.terms(startTerm);
	    closed[i] = !tes[i].next() || !tes[i].term().field().equals(f);
	}

	concatDfTable = new HashMap<String,Integer>();
	double sumTf=0;
	int tcnt=0;
	while(true) {
	    String minText=null;
	    int chosen[] = new int[nf];
	    int nchosen = 0;
	    for(int i=0; i<nf; i++) {
		if (closed[i]) continue;
		String text= tes[i].term().text();
		if (minText==null || text.compareTo(minText)<0) {
		    minText=text;
		    nchosen=0;
		    chosen[nchosen++] = i;
		} else if (text.equals(minText)) {
		    chosen[nchosen++] = i;
		}
	    }
	    if (minText==null) break;

	    Term[] terms = new Term[nchosen];
	    double[][] wex = new double[nchosen][];
	    for(int k=0; k<nchosen;k++) {
		int i = chosen[k];
		wex[k] = weights[i];
		terms[k] = tes[i].term();
		String f= fields[i];	
		closed[i] = !tes[i].next() || !tes[i].term().field().equals(f);
	    }

	    int xdf = sumDF(terms);

	    if (xdf < ArticleAnalyzer.getMinDf() || UserProfile.isUseless(new Term(ArxivFields.ARTICLE, minText))) {
		// skip this term (based on a prelim upper bound for Df)
		continue;
	    }

	    MergeList merged = MergeList.merge(reader, terms, wex, 0, nchosen);

	    int df = merged.n;

	    if (df < ArticleAnalyzer.getMinDf()) {
		// skip this term (based on actual df)
		continue;
	    }

	    tcnt++;
	    concatDfTable.put(minText, new Integer(df));
	    double idf = computeIdf(numdocs, df);

	    //double norm2 = merged.norm2();
	    //	    Logging.info("Term=" + minText +", in " + nchosen + " fields, " +df + " docs; |m|^2=" + norm2);
	    //if (norm2==0) throw new AssertionError("0 for term " + minText);

	    for(merged.i=0; merged.i<merged.n; merged.i++) {
		int p = merged.ps[merged.i];
		double freq = merged.freqs[merged.i];
		sumTf += freq;
		double z = freq*freq *idf;
		sum[p] += z;
	    }   	    
	}

	Logging.info("Scanned " + tcnt + " terms; sumTf=" + sumTf);

	for(int docno=0; docno<maxdoc; docno++) {
	    double q = sum[docno];
	    if (Double.isNaN(q) || q<0) {
		Logging.error("w["+docno+"]=" +q);
		throw new AssertionError("w["+docno+"]=" +q);
	    }
	    if (q!=0) sum[docno]=Math.sqrt(q);
	}

	return sum;
    }

    /** An auxiliary class used to merge {doc,freq} lists for different fields
        when "flattening" a document (i.e., concatenating fields) */
    static private class MergeList {
	final int n;
	int i=0;
	int[] ps;
	double[] freqs;
	/** Makes a MergeList out of a section of the index */
	MergeList(IndexReader reader, Term term, double[] weights)  throws IOException{
	    TermDocs td = reader.termDocs(term);
	    i=0;
	    while(td.next()) { i++; }
	    n = i;
	    ps = new int[n];
	    freqs = new double[n];
	    td.seek(term);
	    i=0;
	    while(td.next()) {
		ps[i] = td.doc();
		freqs[i] = td.freq() * weights[ps[i]];
		i++;
	    }   
	    td.close();
	}


	double norm2() {
	    double sum=0;
	    for(i=0;i<n;i++) sum+= freqs[i]*freqs[i];
	    return sum;
	}

	/** Merge the lists based on terms[k1:k2-1] */
	static MergeList merge(IndexReader reader, Term[] terms, 
			       double[][] weights, int k1,int k2)
	throws IOException {
	    if (k2<=k1) throw new IllegalArgumentException("MergeList.merge: negative range");
	    else if (k2==k1+1) return new MergeList(reader, terms[k1], weights[k1]);
	    int m = (k1+k2)/2;
	    return new MergeList(merge( reader, terms, weights, k1, m),
				 merge( reader, terms, weights, m, k2));
	}


	/* Builds a new MergeList by merging of two */
	MergeList(MergeList a, MergeList b) {
	    ps = new int[a.n + b.n];
	    freqs = new double[a.n + b.n];
	    a.i = b.i = i = 0;
	    while(a.i<a.n || b.i<b.n) {
		MergeList q =
		    a.i==a.n ? b :
		    b.i==b.n ? a :
		    a.ps[a.i] < b.ps[b.i] ? a :
		    a.ps[a.i] > b.ps[b.i] ? b : null;

		if (q==null) {
		    ps[i] = a.ps[a.i];
		    freqs[i] = a.freqs[a.i] + b.freqs[b.i];
		    a.i++;
		    b.i++;
		} else {
		    ps[i] = q.ps[q.i];
		    freqs[i] = q.freqs[q.i];
		    q.i++;
		}
		i++;
	    }
	    n = i;
	}
    }

    /** Make a key for a particular qualified term in UserProfile */
    private String mkKey(String field, String text) {
	return field + ":" + text;
    }
    private String mkKey(Term t) {
	return mkKey(t.field(), t.text());
    }

    /** Computes a (weighted) term frequency vector for a specified document.

	@param docno Lucene's internal integer ID for the document,

	@return The raw frequency vector (which is normalized, but does 
	not incorporate the idf)
    */
   
    public HashMap<String,  MutableDouble> getCoef(int docno) 
	throws IOException {

	HashMap<String, MutableDouble> h = new HashMap<String, MutableDouble>();

	final int nf =upFields.length;
	TermFreqVector [] tfvs = new TermFreqVector[nf];

	for(int j=0; j<nf;  j++) {	    
	    String name= upFields[j];
	    TermFreqVector tfv= tfvs[j]=reader.getTermFreqVector(docno, name);
	    if (tfv==null) continue;
	    
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    
	    for(int i=0; i<terms.length; i++) {	

		Term term = new Term(name, terms[i]);
		int df = reader.docFreq(term);
		if (df < minDf || UserProfile.isUseless(term)) {
		    continue; // skip very rare words, non-words, and stop words
		}

		String key = mkKey(term);
		//double idf = computeIdf( numdocs, df);

		double z =  freqs[i] / norms[j][docno];
		h.put( key, new MutableDouble(z));

		key = mkKey(CONCAT, terms[i]);
		z =  freqs[i] / norms[nf][docno];
		MutableDouble val = h.get(key);
		if (val==null) h.put(key, new MutableDouble(z));
		else val.add(z);
	    }	
	}
	return h;
    }

 /**
       @param hq  User profile vector (UserProfile.hq)
     */
    TjA1EntryData prepareTjA1EntryData(int docno,
				       HashMap<String, UserProfile.TwoVal> hq,
				       Map<String,Integer> termMapper)
	throws IOException {

	final int nt=termMapper.size();
	TjA1EntryData tj = new TjA1EntryData(nt);

	double boostConcat = 1.0 / norms[fields.length][docno];

	for(int j=0; j< fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, fields[j]);
	    if (tfv==null) {
		Logging.warning("No tfv for docno="+docno+", field="+fields[j]);
		continue;
	    }

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    

	    double boost = 1.0 / norms[j][docno];

	    for(int i=0; i<terms.length; i++) {
		String key=mkKey(fields[j], terms[i]);	       
		UserProfile.TwoVal q= hq.get(key);
		if (q!=null) {
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
		if (q!=null) {
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

  
    private static int printMissing = 0;

    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	printMissing =	ht.getOption("printMissing", printMissing);

	IndexReader reader = Common.newReader();
	ArticleAnalyzer2 aa2 = new ArticleAnalyzer2(reader);
    }

}