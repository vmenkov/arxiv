package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

//import org.apache.commons.lang.mutable.MutableDouble;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

/** Thorsten's Algo 1 */
class TjAlgorithm1 {
    /** This flag is on when using the alternative approach to the
     * initialization of w'', viz. w'' = sqrt(phi) */
    static final boolean approach2=true;

    TjA1Entry [] tjEntries;

    TjAlgorithm1() {}

    ArxivScoreDoc[] 
	rank(UserProfile upro, 
	     ArxivScoreDoc[] sd,
	     ArticleStats[] allStats, EntityManager em, int maxDocs )  throws IOException{
	IndexSearcher searcher = new IndexSearcher( upro.dfc.reader);	

	HashMap<String,Integer> termMapper=upro.mkTermMapper();

	int missingStatsCnt=0, storedCnt=0;
	tjEntries = new TjA1Entry[sd.length];

	for(int i=0; i<sd.length; i++) {
	    ArticleStats as=upro.dfc.getAS(allStats, sd[i].doc, em);
	    if (as==null) {
		missingStatsCnt ++;
		continue;
	    }
	    TjA1Entry tje = new TjA1Entry( sd[i], as, upro, termMapper);
	    tjEntries[storedCnt++] = tje;
	}

	Arrays.sort(tjEntries, 0, storedCnt);  

	if (storedCnt==0) return new ArxivScoreDoc[0]; // nothing!

	//Vector<ArticleEntry> results= new Vector<ArticleEntry>();
	Vector<ArxivScoreDoc> results= new Vector<ArxivScoreDoc>();
       
	double[] phi = new double[ upro.terms.length];
	double gamma = upro.getGamma(0);
	int usedCnt=0;

	double utility = 0;
	int imax=-1;
	for(int i=0; i<storedCnt; i++) {
	    TjA1Entry tje = tjEntries[i];
	    double u = tje.ub() - tje.mcMinus;
	    if (imax<0 || u>utility) {
		imax = i;
		utility=u;
	    }
	}

	TjA1Entry tje = tjEntries[imax];
	tje.addToPhi(phi, gamma);
	tje.setScore(utility);
	results.add(tje.getSd());

	Logging.info("A1: results[" + usedCnt + "]:=tje["+imax+"], utility=du=" + utility);

	usedCnt++;

	while( results.size() < maxDocs && usedCnt < storedCnt) {
	    gamma =  upro.getGamma(results.size());
	    double maxdu = 0;
	    imax=-1;	    
	    int i;
	    for(i=usedCnt; i<storedCnt; i++) {
		tje = tjEntries[i];
		if (maxdu >= tje.ub()) break; 
		double du= tje.wouldContributeNow(phi, gamma);		
		if (imax<0 || du>maxdu) {
		    imax = i;
		    maxdu=du;
		}
	    }

	    if (maxdu<=0) {
		Logging.info("No further improvement to the utility can be achieved");
		return results.toArray(new ArxivScoreDoc[0]);
	    }

	    int undisturbed = i;
	    tje = tjEntries[imax];
	    tje.addToPhi(phi, gamma);
	    tje.setScore(maxdu);
	    results.add(tje.getSd());
	    utility += maxdu;

	    if (imax > usedCnt) {		// swap
		tjEntries[imax] = tjEntries[usedCnt];
		tjEntries[usedCnt] = tje;		
	    }

	    Logging.info("A1: results[" + usedCnt + "]:=tje["+imax+"], utility=" + utility + ", du=" + maxdu);
	    usedCnt++;
	    
	    finishSort(tjEntries, usedCnt, undisturbed, storedCnt);

	}

	return results.toArray(new ArxivScoreDoc[0]);
    }

    /** "Finalizes" sorting of the array section a[n1:n3), within
     * which the section a[n2:n3) is already sorted.

     @param n1:  n1 &le; n2 &le; n3;
     */
    private static void finishSort(TjA1Entry [] a, int n1, int n2, int n3) {
	if (n1==n2) return; // the first (unsorted) section is empty
	Arrays.sort(a, n1, n2); // sort the first section
	if (n2==n3) return;  // the second section is empty

	// see if the beginning of the first section is already at the 
	// right place
	int i1 = n1;
	int i2 = n2;
	while(i1 < n2 && a[i1].compareTo(a[i2])<=0) { i1++; }

	// merge the remainder of the first section and the second section
	final int i1start = i1;

	TjA1Entry  merged[] = new TjA1Entry[n3-i1];
	int k=0;
	while(i1 < n2 && i2 < n3) {
	    if (a[i1].compareTo(a[i2])<=0) {
		merged[k++] = a[i1++];
	    } else {
		merged[k++] = a[i2++];
	    }
	}

	Logging.info("Sorting to involve " + (n2-n1) + " + " + (i2-n2) + " values");

	if (i1 < n2) {	    // move up the remaining part of the first section
	    if (i2!=n3) throw new AssertionError("error 1 in merge sort");
	    for(int q=n2; q>i1; ) {
		a[--i2] = a[--q];
	    }
	}

	if (k!= (i2-i1start)) throw new AssertionError("error 2 in merge sort");
	
	for(int j=0; j<k; j++) {
	    a[i1start + j] = merged[j];
	}
    }

}