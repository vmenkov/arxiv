package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

/** An abstract class for representing objects (such as documents)
    being clustered in KMeansClustering. Objects are represented as 
    vectors in a Euclidean linear space. Presently, there are two
    derived classes, SparseDataPoint and DenseDataPoint, which can be 
    used depending on the nature of the objects being clustered. 

    <p>Depending on the algorithm, the components of the vectors may
    stand for the actual term frequencies in the documents, or for something
    more "abstract", for example the projections of the document vectors on
    the singular vectors of a certain matrix. This is of no importance for
    the k-Means algorithm itself.
*/
class DataPoint {

    /** Feature values. Application programs should not modify this array. */   
    double[] values;
    /** List of values (in the same order as features). Application programs should not modify this array. */
    //public double[] getValues() { return values; }

    public double norm2() {
	double sum=0;
	for(double q: values) sum += q*q;
	return sum;
    }
    /** Computes the 2-norm of the vector */
    public double norm() {
	return Math.sqrt(norm2());
    }

    /** Normalizes this vector "in place" (using L2 norm) */
    public void normalize() {
	multiply( 1.0 / norm());
    }

    public void multiply(double c) {
	for(int i=0; i<values.length; i++) {
	    values[i] *= c;
	}
    }

    /** Normalizes this vector "in place" (using L1 norm). Assumes
     all components are non-negative. */
    public void l1normalize() {
	multiply( 1.0 / l1norm());
    }


    /** The sum of abs values of all elements. If all elements are
	non-negative, this will be the same as the sum of them.
     */
    public double l1norm() {
 	double s = 0;
	for(double q: values) {
	    s += Math.abs(q);		 
	}
	return s;
    }

    /** How many stored values are actually non-zeros? */
    public int nonzeroCount() {
	int cnt=0;
	for(double q: values) {
	    if (q!=0) cnt++;
	}
	return cnt; 	
    }

}