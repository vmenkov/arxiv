package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;


import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;


/** Another way to compute article stats..
 */
public class ArticleAnalyzer2 {

    static final boolean verbose = true;

    static double idf(int numdocs, int df)  {
	double idf = 1+ Math.log(numdocs / (1.0 + df));
	return idf;
    }


    static void computeAllNorms(IndexReader reader) throws IOException {

	final int numdocs = reader.numDocs(), maxdoc=reader.maxDoc() ;
	Logging.info("AA2: numdocs=" + numdocs + ", maxdoc=" + maxdoc);

	final int nf=ArticleAnalyzer.upFields.length;
	double[][] w= new double[nf][];

	for(int i=0; i<nf; i++) {
	    String f= ArticleAnalyzer.upFields[i];	
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
		
		if (UserProfile.isUseless(term)) continue;

		int df = reader.docFreq(term);
		double idf = idf(numdocs, df);
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
		String f= ArticleAnalyzer.upFields[i];	
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
	    String f= ArticleAnalyzer.upFields[i];	
	    Logging.info("Field=" + f +", <|v|>=" + sum[i]/cnt +", " + 
			 minNorm[i] +"<=|v|<=" + maxNorm[i] +"; empty in " + emptyFieldCnt[i] + " docs");
	}
    }

    /** Computing the norm of a "flattened" (concatenated) field */
    /*
    static double[] multiNorms(IndexReader reader, int maxdoc, String[] fields, double[][] weights) {
	final int nf = fields.length;
	double[] sum =  new double[maxdoc];
	TermEnum[] tes = new TermEnum[nf];
	boolean closed[] = new boolean[nf];
	for(int i=0; i<nf; i++) {
	    String f= fields[i];	
	    //Logging.info("Field=" + f);
	    Term startTerm = new Term(f, "0");
	    tes[i] = reader.terms(startTerm);
	    closed[i] = !tes[i].next();
	}

	while(true) {
	    String minText=null;
	    int chosen[] = new int[nf];
	    int nchosen = 0;
	    for(int i=0; i<nf; i++) {
		if (!closed(i)) {
		    String text= tes[i].term().text();
		    if (minText==null || text.compareTo(minText)<0) {
			minText=text;
			nchosen=0;
			chosen[nchosen++] = i;
		    } else if (text.equals(minText)) {
			chosen[nchosen++] = i;
		    }
		}
		
	    }
	    if (minText==null) break;

	    if (UserProfile.isUseless(new Term(ArxivFields.ARTICLE, minText))) {
		// skip this term
		for(int k=0; k<nchosen;k++) {
		    int i = chosen[k];
		    closed[i] = !tes[i].next();
		}
		continue;
	    }

	    TermDocs[] tds = new TermDocs[nchosen];
	    for(int k=0; k<nchosen;k++) {
		int i = chosen[k];
		tds[k] = reader.termDocs(tes[i].term);
		closed[i] = !tes[i].next();
	    }


	    for(int k=0; k<nchosen;k++) {

		TermDocs td = reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
	    int p = td.doc();
		    int freq = td.freq();
		    sumTf += freq;
		    double z = (double)freq*(double)freq *idf;
		    v[p]+=z;
		}   
	

	    
	}


	
    }
    */
    
    private static int printMissing = 0;

    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	printMissing =	ht.getOption("printMissing", printMissing);

	IndexReader reader = Common.newReader();
	computeAllNorms(reader);
    }

}