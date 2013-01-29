package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;


import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.recommender.ArxivScoreDoc;


/** Methods needed to compute mu*() for Peter Frazier's Exploration
    Engine ver. 4. 
    
    <P>A single EE4Mu object represents the array of mu*[alpha+beta]
 */
public class EE4Mu {

    static int N=1000;
    
    //    double [] muValues;
    double muValue;

    EE4Mu(double alpha, double beta, int m, EE4User.CCode ccode) {
	double gamma = gamma(m);
	double c = ccode.doubleValue();
	//final double alpha0=1, beta0=0;
	//muValues = Mu.EE4_DocRevVal_simp( alpha0, beta0, N, gamma, c);
	double [] muValues = Mu.EE4_DocRevVal_simp( alpha, beta, N, gamma, c);
	muValue = muValues[0];
    }
    
    static class Key {
	final int m;
	final double alpha, beta;
	Key(double a, double b, int _m) {
	    alpha=a;
	    beta=b;
	    m=_m;
	}
	public int hashCode() {
	    return (int)(m * (long)alpha * (long)beta);
	}
    }

    /** Maps m to an array (index based on CCode) of EE4Mu objects. */
    static HashMap<Key, EE4Mu[]> mToCMu= new  HashMap<Key, EE4Mu[]>();
	
    static double gamma(double m) { 
	return  1 - 1.0 /(m*EE4DocClass.T +1.0);
    }

    
    static double getMu(double a, double b, EE4User.CCode ccode, int m) {
	Key key = new Key(a,b,m);
	EE4Mu[] w =  mToCMu.get(key);
	if (w==null) {
	    mToCMu.put(key, w = new EE4Mu[EE4User.CCode.allValues.length]);
	}
	EE4Mu q = w[ccode.ordinal()];
	if (q==null) {
	    q = w[ccode.ordinal()] = new EE4Mu(a, b, m, ccode);
	}
	return q.muValue;
    }

 /** Testing 

   Xiaoting's explanation (2013-01-15):
    The output of the code will split out mu*. You will run
    EE4_DocRevVal_simp(alpha0, beta0, N=1000, gamma, c) for each
    needed (gamma,c) pair, and interpret the returned array as [
    mu*(alpha0+beta0+0), mu*(alpha0+beta0+1), mu*(alpha0+beta0+2),
    ....mu*(alpha0+beta0+N] for this (gamma,c) pair.
 */
    static public void main(String[] argv) {
	ParseConfig ht = new ParseConfig();
	N = ht.getOption("N", N);

	if (argv.length < 2 || argv.length>4) {
	    System.out.println("Usage: java edu.rutgers.axs.ee4.Mu alpha beta [ccode [m]]");
	    System.out.println("Star ('*') can be used instead of cc or m");
	    //System.out.println("Usage: java edu.rutgers.axs.ee4.Mu alpha beta m");
	    return;
	}
	int k=0;
	double alpha = Double.parseDouble(argv[k++]);
	double beta = Double.parseDouble(argv[k++]);
	String 	q = (k<argv.length? argv[k++] : "*");
	EE4User.CCode[] ccodes = q.equals("*") ?
	    EE4User.CCode.class.getEnumConstants() :
	    new EE4User.CCode[]{ EE4User.CCode.valueOf(EE4User.CCode.class,q)};
	q = (k<argv.length? argv[k++] : "*");
	int ms[] =  q.equals("*") ? 
	    new int[] {1, 2, 5, 10, 20, 100} : new int[] {Integer.parseInt(q)};
	for(int m: ms) {
	    for(EE4User.CCode ccode: ccodes ) {
		double mu = getMu( alpha, beta, ccode, m);
		System.out.println("mu(a="+alpha+",b="+beta+", c("+ccode+")="+ccode.doubleValue()+",gamma(m="+m+")="+
				   gamma(m)+ ") = " + mu);
	    }
	    System.out.println();
	}

    }

}