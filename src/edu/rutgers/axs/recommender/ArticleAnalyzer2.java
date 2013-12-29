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

	    while(te.next()) {
		Term term = te.term();
		if (!term.field().equals(f)) break;
		if (UserProfile.isUseless(term.text())) continue;

		int df = reader.docFreq(term);
		double idf = 1+ Math.log(numdocs / (1.0 + df));

		TermDocs td = reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			
		    double z = freq*freq *idf;
		    v[p]+=z;
		}   
	    }
	    te.close();
	}

	int cnt=0;
	double[] sum= new  double[ArticleAnalyzer.upFields.length];
	double[] maxNorm= new  double[ArticleAnalyzer.upFields.length];
	double[] minNorm= new  double[ArticleAnalyzer.upFields.length];
	for(int docno=0; docno<maxdoc; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno,ArticleStats.fieldSelectorAid);
	    if (doc==null) continue;
	    String aid = doc.get(ArxivFields.PAPER);
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		double q = w[i][docno];
		sum[i] += Math.sqrt(w[i][docno]);
		if (cnt==0 || q>maxNorm[i]) maxNorm[i]=q;
		if (cnt==0 || q<minNorm[i]) minNorm[i]=q;
	    }
	    cnt++;
	}
	Logging.info("counted " + cnt + " documents");
	for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
	    String f= ArticleAnalyzer.upFields[i];	
	    Logging.info("Field=" + f +", <|v|>=" + sum[i]/cnt +", " + 
			 Math.sqrt(minNorm[i]) +"<=|v|<=" + Math.sqrt(maxNorm[i]));
	}

    }
    
    static public void main(String[] argv) throws IOException {
	IndexReader reader = Common.newReader();
	computeAllNorms(reader);
    }

}