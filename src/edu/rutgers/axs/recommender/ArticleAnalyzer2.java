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

    static void computeAllNorms(IndexReader reader) throws IOException {

	final int numdocs = reader.numDocs(), maxdoc=reader.maxDoc() ;
	Logging.info("AA2: numdocs=" + numdocs + ", maxdoc=" + maxdoc);

	double[][] w= new double[ArticleAnalyzer.upFields.length][];

	for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
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
		boolean watch = false;
		//text.toLowerCase().startsWith("gpd") ||   text.toLowerCase().startsWith("ssa");

		if (watch) {
		    Logging.info("Term " + term +", useless=" + UserProfile.isUseless(term));
		}
		
		if (UserProfile.isUseless(term)) continue;

		int df = reader.docFreq(term);
		double idf = 1+ Math.log(numdocs / (1.0 + df));
		if (Double.isNaN(idf) || idf < 0) {
		    Logging.error("term=" + term+", df=" + df + ", idf=" + idf);
		    throw new AssertionError("idf=" + idf);
		} 

		TermDocs td = reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    if (watch) Logging.info("Term " + term + "in doc " +p);
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

	int cnt=0;
	double[] sum= new  double[ArticleAnalyzer.upFields.length];
	double[] maxNorm= new  double[ArticleAnalyzer.upFields.length];
	double[] minNorm= new  double[ArticleAnalyzer.upFields.length];
	int[] emptyFieldCnt= new  int[ArticleAnalyzer.upFields.length];
	for(int docno=0; docno<maxdoc; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno,ArticleStats.fieldSelectorAid);
	    if (doc==null) continue;
	    String aid = doc.get(ArxivFields.PAPER);
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		String f= ArticleAnalyzer.upFields[i];	
		double q = w[i][docno];
		if (Double.isNaN(q) || q<0) {
		    Logging.error("w["+f+"]["+docno+" -> " +aid+"]=" +q);
		    throw new AssertionError("w["+f+"]["+docno+" -> " +aid+"]=" +q);
		}
		sum[i] += Math.sqrt(q);
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
	for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
	    String f= ArticleAnalyzer.upFields[i];	
	    Logging.info("Field=" + f +", <|v|>=" + sum[i]/cnt +", " + 
			 Math.sqrt(minNorm[i]) +"<=|v|<=" + Math.sqrt(maxNorm[i]) +"; empty in " + emptyFieldCnt[i] + " docs");
	}

    }
    
    private static int printMissing = 0;

    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	printMissing =	ht.getOption("printMissing", printMissing);

	IndexReader reader = Common.newReader();
	computeAllNorms(reader);
    }

}