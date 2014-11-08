package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

/** Thorsten's Algorithm 1 */
class TjAlgorithm1 {
   
    TjA1Entry [] tjEntries;

    TjAlgorithm1() {}

    /** Orders (ranks) a given set of documents in accordance with Thorsten's
	cumulative utility function for a given user.

	@param upro The user profile with respect to whose "w" the utility
	function will be computed
	@param sd List of documents to rank. The scores will be used by this 
	algorithm as 	secondary keys for tie-breaking; they should be pre-set
	as appropriate before calling this method.
	@param nonlinear The utility function psi has a non-linear (sqrt) part.
     */
    ArxivScoreDoc[] 
	rank( UserProfile upro,    ArxivScoreDoc[] sd,
	      EntityManager em, int maxDocs,
	      boolean nonlinear)  throws IOException{

	Logging.info("A1(nonlinear=" + nonlinear+"); sd.length="+ sd.length);


	HashMap<String,Integer> termMapper=upro.mkTermMapper();

	int missingStatsCnt=0, storedCnt=0;
	tjEntries = new TjA1Entry[sd.length];

	for(int i=0; i<sd.length; i++) {
	    if (upro.dfc instanceof ArticleAnalyzer2 || upro.dfc instanceof ArticleAnalyzer3) { // no restriction
	    } else if (sd[i].doc > upro.dfc.getCasa().size()) continue;
	    TjA1Entry tje=new TjA1Entry(sd[i],upro,termMapper,nonlinear);
	    tjEntries[storedCnt++] = tje;
	}

	double gamma = upro.getGamma(0);
	Arrays.sort(tjEntries, 0, storedCnt,new TjA1Entry.DescendingUBComparator(gamma));
	if (storedCnt==0) return new ArxivScoreDoc[0]; // nothing!

	Vector<ArxivScoreDoc> results= new Vector<ArxivScoreDoc>();
	double[] phi = new double[upro.terms.length];
	int usedCnt=0;

	double utility = 0;
	int imax=-1;
	for(int i=0; i<storedCnt; i++) {
	    TjA1Entry tje = tjEntries[i];
	    double u = tje.ub(gamma) - tje.mcMinus;
	    // includes date-based tie-breaking clause
	    if (imax<0 || u>utility || (u==utility && tje.compareTieTo(tjEntries[imax])>0)) {
		imax = i;
		utility=u;

		//if (Double.isInfinite(utility)) throw new AssertionError("utility=inf: imax="+imax); // here


	    }
	}

	TjA1Entry tje = tjEntries[imax];
	tje.addToPhi(phi, gamma);
	tje.setScore(utility);
	results.add(tje.getSd());

	Logging.info("A1: results[" + usedCnt + "]:=tje["+imax+"], utility=du=" + utility 
		     +" (tje.ub("+gamma+")="+tje.ub(gamma)+", tje.mcMinus="+ tje.mcMinus+")");


	if (imax > usedCnt) {		// swap if needed
	    tjEntries[imax] = tjEntries[usedCnt];
	    tjEntries[usedCnt] = tje;		
	}
	usedCnt++;

	final boolean stopAtMaxUtility = false;

	while( results.size() < maxDocs && usedCnt < storedCnt) {
	    gamma = upro.getGamma(results.size());
	    imax=usedCnt;
	    double maxdu = tjEntries[usedCnt].wouldContributeNow(phi, gamma);

	    int i;
	    for(i=usedCnt+1; i<storedCnt && tjEntries[i].ub(gamma)>=maxdu; i++){
		tje = tjEntries[i];
		double du= tje.wouldContributeNow(phi, gamma);		
	// includes date-based tie-breaking clause
		if ( du>maxdu ||
		    (du==maxdu && tje.compareTieTo(tjEntries[imax])>0)) {
		    imax = i;
		    maxdu=du;
		}
	    }

	    if (stopAtMaxUtility && maxdu<0) {
		Logging.info("No further improvement to the utility can be achieved (maxdu=" + maxdu+")");
		return results.toArray(new ArxivScoreDoc[0]);
	    } 

	    int undisturbed = i;
	    tje = tjEntries[imax];
	    tje.addToPhi(phi, gamma);
	    tje.setScore(maxdu);
	    results.add(tje.getSd());
	    utility += maxdu;
 
	    if (imax > usedCnt) {		// swap if needed
		tjEntries[imax] = tjEntries[usedCnt];
		tjEntries[usedCnt] = tje;		
	    }

	    //Logging.info("A1: tested du vals {" + q + "}");
	    Logging.info("A1: results[" + usedCnt + "]:=tje["+imax+"], utility=" + utility + ", du=" + maxdu +"; scanned up to " + undisturbed);
	    usedCnt++;
	    
	    TjA1Entry.DescendingUBComparator cmp=new TjA1Entry.DescendingUBComparator(gamma);
	    finishSort(tjEntries, usedCnt, undisturbed, storedCnt, cmp);
	}

	return results.toArray(new ArxivScoreDoc[0]);
    }

    /** "Finalizes" sorting of the array section a[n1:n3), within
       which the section a[n2:n3) is already sorted.

     @param n1:  n1 &le; n2 &le; n3;
     */
    private static void finishSort(TjA1Entry [] a, int n1, int n2, int n3,
				   Comparator<TjA1Entry> cmp) {
	if (n1==n2) return; // the first (unsorted) section is empty
	Arrays.sort(a, n1, n2, cmp); // sort the first section
	if (n2==n3) return;  // the second section is empty

	// see if the beginning of the first section is already in the right place
	int i1 = n1;
	int i2 = n2;
	while(i1 < n2 && cmp.compare(a[i1],a[i2])<=0) { i1++; }

	// merge the remainder of the 1st section and (the beginning of) the 2nd section
	final int i1start = i1;

	TjA1Entry  merged[] = new TjA1Entry[n3-i1];
	int k=0;
	while(i1 < n2) {
	    merged[k++] = (i2 < n3 && cmp.compare(a[i2],a[i1])<0) ? a[i2++] : a[i1++];
	}

	Logging.info("Sorting to involve " + (n2-n1) + " + " + (i2-n2) + " values");

	if (k!= (i2-i1start)) throw new AssertionError("error 2 in merge sort");
	
	for(int j=0; j<k; j++) {
	    a[i1start + j] = merged[j];
	}
    }

}