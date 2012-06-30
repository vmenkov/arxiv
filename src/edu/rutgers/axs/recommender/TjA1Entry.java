package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

//import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

/** A temporary object, stores a semi-computed term vector 
    during TJ Algorithm 1.
*/
class TjA1Entry implements Comparable<TjA1Entry>  {

    static class Coef { //implements Comparable<Coef>  {
	/** index into UserProfile.terms[] */
	int i;
	double value;
	public Coef(int _i, double v) { i =i; value=v;}

	/** @param phi  Vector phi, already component-wise multiplied
	    by w2 
	 */
	static double uContrib(Coef[] q, double [] phi, double gamma) {
	    double m = 0;
	    for(Coef c: q) {

		if (phi[c.i]<0) throw new AssertionError("phi["+c.i+"]<0");
		if (c.value+gamma<0) throw new AssertionError("c.value+gamma<0");

		m += Math.sqrt( phi[ c.i ] + c.value * gamma) -
		    Math.sqrt( phi[ c.i ] );
	    }
	    return m;
	}
    }


    private ArxivScoreDoc sd;
    ArxivScoreDoc getSd() { return sd;}
    /** Tie-breaking, using the saved score */
    double compareTieTo(TjA1Entry o) {
	return sd.score - o.sd.score;
    }

    //    String datestring="";

    void setScore(double x) { sd.score=(float)x;}

    /** Components of phi, pre-multiplied by w2^2, and (in the actual
	implementation), also by IDF. (Conceptually, IDF does not
	figure here explicitly, as IDF^{1/2} is conceptually already
	factored into phi and w1, and IDF^{1/4}, into w2.)  For any
	feature t, the wplus[t] values over multiple TjAentry objects
	(corresponding to multiple docs) need to be later summed over
	the documents involved, and then the sqrt computed, to produce
	t's contribution to the utility.

	The vectors vplus and vminus are for the coefficients
	corresponding to the positive and negative values in w2,
	respectively.
    */
    private Coef[] qplus, qminus;

    /** Conceptually, the dot product (w1,d) */
    private double sum1;
    /** Max possible contribution to the utility of the parts of 
	sqrt(phi) which have non-negative coefficients in w2*/
    private double mcPlus;
    /** Non-positive */
    double mcMinus;
    private double lastGamma;

    /** Upper bound (possibly, no longer "tight") on this document's
     * contribution to the utility function */
    double ub() { return sum1 + mcPlus; }

    /** Descending order with respect to the max possible contribution. */
    public int compareTo(TjA1Entry o) {
	double x = o.ub() - ub();
	return (x>0) ? 1 : (x<0) ? -1 : 0;
    }

    TjA1Entry(ArxivScoreDoc _sd,  //String _datestring,
	      CompactArticleStatsArray casa, //ArticleStats as, 
	      UserProfile upro, Map<String,Integer> termMapper)
	throws IOException {
	sd = _sd;
	//	datestring = _datestring;
	double sum1 = 0;

	int docno=sd.doc;

	double[] w2plus =  new double[upro.terms.length],
	    w2minus =  new double[upro.terms.length];	

	for(int j=0; j<upro.dfc.fields.length;  j++) {	
	    TermFreqVector tfv=upro.dfc.reader.getTermFreqVector(docno, upro.dfc.fields[j]);
	    if (tfv==null) {
		Logging.warning("No tfv for docno=" + docno + ", field=" + upro.dfc.fields[j]);
		continue;
	    }
	    //double boost =  as.getNormalizedBoost(j);
	    double boost =  casa.getNormalizedBoost(docno, j);

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    

	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=upro.hq.get(terms[i]);
		if (q==null) continue;
		// term position in upro.terms[]
		int iterm = termMapper.get(terms[i]).intValue();

		double z = freqs[i] * boost;
		double idf = upro.dfc.idf(terms[i]);	       

		sum1 += z *  q.w1 *idf;

		double r = idf*q.w2;
		double w2q = 		    TjAlgorithm1.approach2? 
		    z * idf * q.w2 * q.w2 :
		    z * idf * idf * q.w2 * q.w2;
		if (w2q<0) throw new AssertionError("w2q<0: this is impossible!");
		if (q.w2 >= 0) {
		    w2plus[iterm] += w2q;
		} else {
		    w2minus[iterm] += w2q;
		}
	    }
	}
	
	Vector<Coef> vplus = new Vector<Coef> (upro.terms.length),
	    vminus = new Vector<Coef> (upro.terms.length);

	mcPlus =  mcMinus = 0;

	double gamma= upro.getGamma(0);
	for(int i=0; i<upro.terms.length; i++) {
	    if (w2plus[i]!=0) {
		vplus.add(new Coef(i, w2plus[i]));
		mcPlus += Math.sqrt(gamma * w2plus[i]);
	    } else if (w2minus[i]!=0) {
		vminus.add(new Coef(i, w2minus[i]));
		mcMinus += Math.sqrt(gamma * w2minus[i]);
	    } 
	}
	   
	qplus = vplus.toArray(new Coef[0]);
	qminus = vminus.toArray(new Coef[0]);


	lastGamma=gamma;
    }

    void addToPhi(	double[] phi, double gamma) {
	for(Coef c: qplus) {
	    phi[ c.i ] += c.value * gamma;
	}
	for(Coef c: qminus) {
	    phi[ c.i ] += c.value * gamma;
	}
    }

    /** How much would this document contribute to the utility function
	if it were to be added to the current Phi vector, with the weight
	gamma? As a side effect, updates the stored upper bounds on updates,
     */
    double wouldContributeNow(double[] phi, double gamma) {
	double sum = gamma * sum1;

	double mcPlus0 = mcPlus;
	mcPlus = Coef.uContrib(qplus, phi, gamma);
	if (mcPlus > mcPlus0) throw new AssertionError("mcPlus increased!");
	sum += mcPlus;

	double mcMinus0 = mcMinus;
	mcMinus = Coef.uContrib(qminus, phi, gamma);
	if (mcMinus > mcMinus0) throw new AssertionError("mcMinus increased!");
	sum -= mcMinus;
	
	lastGamma = gamma;

	return sum;
    }


}

