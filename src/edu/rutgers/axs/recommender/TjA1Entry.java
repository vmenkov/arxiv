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
	/** Copy constructor */
	//public Coef(Coef c) { i =c.i; value=c.value;}
	//public void setValue(Coef c) {
	//    value = c.value;
	//}

	/** Compares column position */
	//public int compareTo(Coef x) {
	//    return icla - x.icla;
	//}

	static double uContrib(Coef[] q, double [] psi, double gamma) {
	    double m = 0;
	    for(Coef c: q) {
		m += Math.sqrt( psi[ c.i ] + c.value * gamma) -
		    Math.sqrt( psi[ c.i ] );
	    }
	    return m;
	}

    }


    ArticleEntry ae;
    /** ti[j]=k */
    //int ti[];
    /** tw[j] = w[k]^2 * idf(k)^2 * d[k] */
    //double tw[];
    Coef[] qplus, qminus;

    /** (w1,d) */
    double sum1;
    /** Non-negative */
    double mcPlus;
    /** Non-positive */
    double mcMinus;
    double lastGamma;

    /** Upper bound (possibly, no longer "tight") on this document's
     * contribution to the utility function */
    double ub() { return sum1 + mcPlus; }

    /** Descending order with respect to the max possible contribution. */
    public int compareTo(TjA1Entry o) {
	double x = o.ub() - ub();
	return (x>0) ? 1 : (x<0) ? -1 : 0;
    }

    TjA1Entry(ArticleEntry _ae,  ArticleStats as, UserProfile upro, Map<String,Integer> termMapper)
	throws IOException {
	ae = _ae;
	double sum1 = 0;
	//double w2sum = 0;

	int docno=ae.getStoredDocno();
	if (docno<0) throw new IllegalArgumentException("The caller should have loaded the docno for this article: " + ae);

	double[] w2plus =  new double[upro.terms.length],
	    w2minus =  new double[upro.terms.length];	
	for(int j=0; j<upro.dfc.fields.length;  j++) {	
	    TermFreqVector tfv=upro.dfc.reader.getTermFreqVector(docno, upro.dfc.fields[j]);
	    if (tfv==null) continue;
	    double boost =  as.getBoost(j);

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    

	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=upro.hq.get(terms[i]);
		if (q==null) continue;
		// term position in upro.terms[]
		int iterm = termMapper.get(terms[i]).intValue();

		double z = freqs[i];
		double idf = upro.dfc.idf(terms[i]);	       

		sum1 += z * boost * q.w1 *idf;

		double r = idf*q.w2;
		double w2q = z * boost * r*r;
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

    void addToPsi(	double[] psi, double gamma) {
	for(Coef c: qplus) {
	    psi[ c.i ] += c.value * gamma;
	}
	for(Coef c: qminus) {
	    psi[ c.i ] += c.value * gamma;
	}
    }

    /** How much would this document contribute to the utility function
	if it were to be added to the current Psi vector, with the weight
	gamma? As a side effect, updates the stored upper bounds on updates,
     */
    double wouldContributeNow(double[] psi, double gamma) {
	double sum = gamma * sum1;

	double mcPlus0 = mcPlus;
	mcPlus = Coef.uContrib(qplus, psi, gamma);
	if (mcPlus > mcPlus0) throw new AssertionError("mcPlus increased!");
	sum += mcPlus;

	double mcMinus0 = mcMinus;
	mcMinus = Coef.uContrib(qminus, psi, gamma);
	if (mcMinus > mcMinus0) throw new AssertionError("mcMinus increased!");
	sum -= mcMinus;
	
	lastGamma = gamma;

	return sum;
    }


}

