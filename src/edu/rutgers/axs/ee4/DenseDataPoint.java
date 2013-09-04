package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** A dense vector, for use in KMeans */
class DenseDataPoint extends DataPoint {

    DenseDataPoint(int n) {
	values = new double[n];
    }

    /** Copy constructor */
    DenseDataPoint(int n, DataPoint x) {
	this(n);
	add(x);
    }  

    DenseDataPoint(double[] v) {
	values = v;
    }


    void add(DataPoint a) {
	if (a instanceof SparseDataPoint) {
	   SparseDataPoint x = (SparseDataPoint)a;
	   for(int i=0; i<x.features.length; i++) {
	       // FIXME: check for "out of bounds"
	       values[x.features[i]] += x.values[i];
	   }
	} else if  (a instanceof DenseDataPoint) {
	    if (a.values.length > values.length) throw new IllegalArgumentException("DDP.add(DDP): dim mismatch!");
	    for(int i=0; i<a.values.length; i++) {
		values[i] += a.values[i];
	   }
	} else {
	   throw new IllegalArgumentException("DDP.add(?)");
	}
    }

    /** Computes the dot produce of two vectors. It is assumed that
	the two vectors are over the same feature space; however,
	one may have more positions than the other, in which case
	extra positions are ignored.
     */
    double dotProduct(DataPoint a) {
       double sum=0;
       if (a instanceof SparseDataPoint) {
	   SparseDataPoint x = (SparseDataPoint)a;
	   for(int i=0; i<x.features.length && x.features[i]<values.length;i++){
	         sum += values[x.features[i]] * x.values[i];
	   }
       } else if  (a instanceof DenseDataPoint) {
	   if (values.length != a.values.length) throw new IllegalArgumentException("DDP.dotProduct(DDP): dim mismatch!");
 	   for(int i=0; i<values.length && i<a.values.length; i++) {
	       sum += values[i] * a.values[i];
	   }
       } else {
	   throw new IllegalArgumentException("DDP.dotProduct(?)");
       }
       return sum;
    }

    /** Is used to save a data point to file */
    void save(DocSet dic, PrintWriter w) {
	//w.println("#--- Entries are ordered by w(t)*idf(t)");
	//w.println("#term\tw(t)\tw(sqrt(t))\tidf(t)");
	for(int i=0; i<values.length; i++) {
	    w.println(dic.getWord(i) + "\t" + values[i]);
	}
    }

    /** Reads the profile from a file. Does not set lastActionId, so that
     has to be done separately. */
    DenseDataPoint(DocSet dic, File f) throws IOException {
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
	String s;
	Vector<Double> v = new 	Vector<Double>();
	int linecnt = 0, cnt=0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;	    
	    String q[] = s.split("\\s+");
	    if (q==null || q.length != 2) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f);
	    }
	    String t = q[0];
	    int ti = dic.enter(t);
	    if (ti != cnt) throw new IllegalArgumentException("Problem interpreting file "+f+" as DenseDataPoint, line "+linecnt+": data line "+ cnt +", dic.enter("+t+")=" + ti);
	    v.add( Double.parseDouble(q[1]));
	    cnt++;
	}
	r.close();
	values = new double[cnt];
	for(int i=0; i<v.size(); i++) {
	    values[i] = v.elementAt(i).doubleValue();
	}
	//Logging.info("Read " + linecnt + " lines, " + vterms
    }


}