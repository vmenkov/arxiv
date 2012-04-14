package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.*;
import java.lang.reflect.*;
import java.lang.annotation.*;


import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


//import org.apache.lucene.document.*;
import edu.rutgers.axs.indexer.ArxivFields;

import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.recommender.UserProfile;
import edu.rutgers.axs.recommender.ArxivScoreDoc;



/** One row of the similarity table */
public class SimRow {

    static class Entry {
	/** Refers to the ArticleStat.id of the relevant page. This is
	    truncated from long to int, as we don't expect the id to 
	    go over 2^31 */
	int astid;
	/** The actual cosine similarity */
	float sim;
	Entry(long _astid, double _sim) {
	    astid = (int)_astid;
	    sim=(float)_sim;
	}
	Entry(ArxivScoreDoc x,  ArticleStats[] allStats) {
	    this( allStats[x.doc].getId(), x.score);
	}
    }

    Vector<Entry> entries=new Vector<Entry>();

    /** Computes similarities of a given document (d1) to all other
	docs in the database. Used for Bernoulli rewards.

	@param cat If not null, restrict matches to docs from the specified category
    */
    public SimRow( HashMap<String, Double> doc1, ArticleStats[] allStats, EntityManager em, String cat, ArticleAnalyzer z) throws IOException {

	final double threshold = 0.1;
	final double thresholds[] = {threshold};

	double norm1=z.tfNorm(doc1);

	int numdocs = z.reader.numDocs() ;
	double scores[] = new double[numdocs];	
	int tcnt=0,	missingStatsCnt=0;

 	for(String t: doc1.keySet()) {
	    double q= doc1.get(t).doubleValue();

	    double qval = q * z.idf(t);
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		String f= ArticleAnalyzer.upFields[i];
		Term term = new Term(f, t);
		TermDocs td = z.reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			

		    double normFactor = 0;
		    if (allStats[p]!=null) {			
			normFactor = allStats[p].getBoost(i);
		    } else {
			missingStatsCnt++;
		    }

		    double w =qval * normFactor * freq;
		    scores[p] += w;
		}
		td.close();
	    } // for fields
	    tcnt++;		
	} // for terms 	    
	ArxivScoreDoc[] sd = new ArxivScoreDoc[numdocs];
	int  nnzc=0, abovecnt[]= new int[thresholds.length];


	final String cat1base = catBase(cat);

	for(int k=0; k<scores.length; k++) {
	    if (scores[k]>0) {
		Document doc2 = z.reader.document(k);
		String cat2 =doc2.get(ArxivFields.CATEGORY);
		boolean catMatch = (cat1base==null || cat1base.equals(catBase(cat2)));

		if (catMatch) {
		    double q = scores[k]/norm1;
		    for(int j=0; j<thresholds.length; j++) {
			if (q>= thresholds[j]) abovecnt[j]++;
		    }

		    if (q>=threshold) {
			sd[nnzc++] = new ArxivScoreDoc(k, q);
		    }
		}		
	    }
	}
	String msg="nnzc=" + nnzc;
	for(int j=0; j<thresholds.length; j++) {
	    msg += "; ";
	    msg += "above("+thresholds[j]+")="+ abovecnt[j];
	}
	Logging.info(msg);
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " values, because of missing stats");
	}

	for(int i=0; i<sd.length; i++) {
	    entries.add(new Entry(sd[i],  allStats));
	}
	
	/*
	int maxDocs = 20;
	if (maxDocs > nnzc) maxDocs = nnzc;
	ArxivScoreDoc[] tops=UserProfile.topOfTheList(sd, nnzc, maxDocs);
	System.out.println("Neighbors:");
	for(int i=0; i<tops.length; i++) {
	    
	    System.out.print((i==0? "{" : ", ") +
			     "("   + allStats[tops[i].doc].getAid() +
			     " : " + tops[i].score+")");
	}
	System.out.println("}");
	*/

	
    }

    /** converts "cat.subcat" to "cat"
     */
    public static String catBase(String cat) {
	if (cat==null) return null;
	int p = cat.indexOf(".");
	return (p<0)? cat: cat.substring(0, p);
    }
 

}