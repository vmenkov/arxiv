package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

/** This is an auxiliary class used for TJ's Algorithm 1 (part of TJ's
    original 2011 "two-pager", as well as for the 3PR algorithm
    (2013). A TjA1Entry instance is a temporary object, which  stores a
    semi-computed term vector during TJ Algorithm 1.  */
class TjA1Entry   {

    private static class Coef { 
	/** index into UserProfile.terms[] */
	int i;
	double value;
	Coef(int _i, double v) { i =i; value=v;}

	/** If we add q*gamma to the current vector phi, how much
	    will it contribute to the non-linear part of Psi?
	    (The linear part, sum1*gamma, is not included here, as it
	    is taken care of elsewhere).

	    @param phi  Vector phi, already component-wise multiplied
	    by w2 
	 */
	static private double uContrib(Coef[] q, double [] phi, double gamma) {
	    double m = 0;
	    for(Coef c: q) {
		if (phi[c.i]<0) throw new AssertionError("phi["+c.i+"]<0");
		if (c.value+gamma<0)throw new AssertionError("c.value+gamma<0");

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

    /** Is the nonlinear (sqrt()) part used in the utility Psi? This
	flag is true for the original (2011) set based algorithm, and
	false in the 3PR (PPP) (2013) algorithm.
    */
    private boolean hasNonlinear = true;

    /** Components of phi, pre-multiplied by w2^2, and (in the actual
	implementation), also by IDF. (Conceptually, IDF does not
	figure here explicitly, as IDF^{1/2} is conceptually already
	factored into phi and w1, and IDF^{1/4}, into w2.)  For any
	feature t, the wplus[t] values over multiple TjA1Entry objects
	(corresponding to multiple docs) need to be later summed over
	the documents involved, and then the sqrt computed, to produce
	t's contribution to the utility.

	The vectors qplus and qminus are for the coefficients
	corresponding to the positive and negative values in w2,
	respectively.
    */
    private Coef[] qplus, qminus;

    /** Conceptually, the dot product (w1,d). If we multiply it by the
	position-dependent gamma_i, this will be the linear part of the
	document's contribution to utility Psi. */
    private double sum1;
    /** Max possible contribution to the utility of the parts of 
	sqrt(phi) which have non-negative coefficients in w2*/
    private double mcPlus;
    /** Ditto, non-positive */
    double mcMinus;
    private double lastGamma;

    /** Upper bound (possibly, no longer "tight") on this document's
     * contribution to the utility function */
    double ub(double gamma) { 
	if (Double.isInfinite(gamma)) throw new AssertionError("gamma=inf");
	if (Double.isInfinite(sum1)) throw new AssertionError("sum1=inf");
	if (Double.isInfinite(mcPlus)) throw new AssertionError("mcPlus=inf");

	return sum1*gamma + mcPlus; 
    }
  
    /** Descending order with respect to the max possible contribution. */
    static class DescendingUBComparator implements Comparator<TjA1Entry> {
	private final double gamma;
	DescendingUBComparator(double _gamma) { gamma=_gamma;}
	public int compare(TjA1Entry o1,TjA1Entry o2) {
	    double x = o2.ub(gamma) - o1.ub(gamma);
	    return (x>0) ? 1 : (x<0) ? -1 : 0;
	}
    }

    /** Initializes the linear part of the utility (sum1) as the
	idf-weighted dot product of the user profile and the
	(idf-weight-)normalized document vector.  In the non-linear
	mode, initializes building blocks for the non-linear
	part of the utility as well.
     */
    TjA1Entry(ArxivScoreDoc _sd, 
	      UserProfile upro, Map<String,Integer> termMapper,
	      boolean _hasNonlinear)
	throws IOException {
	hasNonlinear =  _hasNonlinear;
	sd = _sd;

	ArticleAnalyzer.TjA1EntryData tj = 
	    upro.dfc.prepareTjA1EntryData(sd.doc, upro.hq, termMapper);

	sum1 = tj.sum1;
	if (Double.isInfinite(sum1)) throw new AssertionError("sum1=inf, doc=" + _sd.doc);



	mcPlus =  mcMinus = 0;

	Vector<Coef> vplus = new Vector<Coef> (upro.terms.length),
	    vminus = new Vector<Coef> (upro.terms.length);
	
	double gamma= upro.getGamma(0);
	for(int i=0; i<upro.terms.length; i++) {
	    if (tj.w2plus[i]!=0) {
		vplus.add(new Coef(i, tj.w2plus[i]));
		if (hasNonlinear) {
		    mcPlus += Math.sqrt(gamma * tj.w2plus[i]);
		}
	    } else if (tj.w2minus[i]!=0) {
		vminus.add(new Coef(i, tj.w2minus[i]));
		if (hasNonlinear) {
		    mcMinus += Math.sqrt(gamma * tj.w2minus[i]);
		}
	    } 
	} 
	qplus = vplus.toArray(new Coef[0]);
	qminus = vminus.toArray(new Coef[0]);
	lastGamma=gamma;
    }

    void addToPhi(double[] phi, double gamma) {
	for(Coef c: qplus) {
	    phi[ c.i ] += c.value * gamma;
	}
	for(Coef c: qminus) {
	    phi[ c.i ] += c.value * gamma;
	}
    }

    /** How much would this document contribute to the utility
	function if it were to be added to the current Phi vector,
	with the weight gamma? As a side effect, updates the stored
	value of mcPlus, can be used later to compute the upper bounds
	of updates.
     */
    double wouldContributeNow(double[] phi, double gamma) {
	double sum = gamma * sum1; // linear part

	if (hasNonlinear) {
	    double mcPlus0 = mcPlus;
	    mcPlus =  Coef.uContrib(qplus, phi, gamma);
	    if (mcPlus > mcPlus0) throw new AssertionError("mcPlus increased!");
	    sum += mcPlus;
	    
	    double mcMinus0 = mcMinus;
	    mcMinus = Coef.uContrib(qminus, phi, gamma);
	    if (mcMinus > mcMinus0) throw new AssertionError("mcMinus increased!");
	    sum -= mcMinus;
	    if (Double.isNaN(sum)) {
		String msg = "TjA1Entry.wouldContributeNow(), sum is NaN; mcPlus=" + mcPlus +", mcMinus=" + mcMinus;
		Logging.error(msg);
		//throw new AssertionError(msg);
	    }
	} else {
	    mcPlus = mcMinus = 0;
	}
	
	lastGamma = gamma;
	return sum;
    }


}

