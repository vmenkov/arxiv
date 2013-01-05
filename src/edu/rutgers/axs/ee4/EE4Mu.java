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

    static final int N=1000;
    
    double [] muValues;

    EE4Mu(int m, EE4User.CCode ccode) {
	double gamma = gamma(m);
	double c = ccode.doubleValue();
	final double alpha0=1, beta0=0;
	muValues = Mu.EE4_DocRevVal_simp( alpha0, beta0, N, gamma, c);
    }

    /** Maps m to an array (index based on CCode) of EE4Mu objects. */
    static HashMap<Integer, EE4Mu[]> mToCMu= new  HashMap<Integer, EE4Mu[]>();
	
    static double gamma(double m) { 
	return  1 - 1.0 /(m*EE4DocClass.T +1.0);
    }

    
    static double getMu(double ab, EE4User.CCode ccode, int m) {
	Integer key = new Integer(m);
	EE4Mu[] w =  mToCMu.get(key);
	if (w==null) {
	    mToCMu.put(key, w = new EE4Mu[EE4User.CCode.allValues.length]);
	}
	EE4Mu q = w[ccode.ordinal()];
	if (q==null) {
	    q = w[ccode.ordinal()] = new EE4Mu(m, ccode);
	}
	int iab = (int)Math.round(ab);
	return (iab-1< q.muValues.length)? q.muValues[iab-1] : ccode.doubleValue();
    }

    /*
    stats(HashMap<Integer,EE4DocClass> id2dc)     {
	int maxM = 0;
	for(EE4DocClass c: id2dc.values()) {
	    (c.getM()>maxM) maxM=c.getM();
	}

    }
    */

 /** Testing */
    static public void main(String[] argv) {
	if (argv.length != 3) {
	    System.out.println("Usage: java edu.rutgers.axs.ee4.Mu alpha+beta ccode m");
	    return;
	}
	double alpha = Double.parseDouble(argv[0]);
	EE4User.CCode ccode = EE4User.CCode.valueOf( EE4User.CCode.class, argv[1]);
	int m = Integer.parseInt(argv[2]);
	double mu = getMu( alpha, ccode, m);
	System.out.println("mu(a+b="+alpha+",c="+ccode+",gamma(m="+m+")="+
			   gamma(m)+ ") = " + mu);

    }

}