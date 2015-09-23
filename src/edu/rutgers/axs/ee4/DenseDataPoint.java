package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

/** A dense vector, for use in KMeans. A derived class
    (ArticleDenseDataPoint) of this class is also used in ee5,
    to represent documents converted to low-dimensional 
    feature space.
 */
public class DenseDataPoint extends DataPoint {

    public DenseDataPoint(int n) {
	values = new double[n];
    }

    /** Copy constructor */
    DenseDataPoint(int n, DataPoint x) {
	this(n);
	add(x);
    }  

    public DenseDataPoint(double[] v) {
	values = v;
    }


    public void add(DataPoint a) {
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
    public double dotProduct(DataPoint a) {
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


    /** Creates a DenseDataPoint object each element of which
	is a natural logarithm of the corresponding element
	of this data point.
     */
    public DenseDataPoint log() {
	double[] q = new double[values.length];
	for(int i=0; i<values.length; i++) {
	    q[i] = Math.log(values[i]);
	}
	return new DenseDataPoint(q);
    }

    /** Prints the values out in space-separated format, with a leading space:
	" v1 v2 v3 ... vL"
    */
    public void print(PrintWriter w) {
	for(double v: values) {
	    w.print(" " + v);
	}
    }

    /** Same as above, with fewer digits */
    public void printFloat(PrintWriter w) {
	for(double v: values) {
	    w.print(" " + (float)v);
	}
    }

    static public DenseDataPoint allOnes(int n) {
	DenseDataPoint p = new DenseDataPoint(n);
	for(int i=0; i<n; i++) p.values[i] = 1.0;
	return p;
    }

}