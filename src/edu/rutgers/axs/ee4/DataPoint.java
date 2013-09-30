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

    double norm2() {
	double sum=0;
	for(double q: values) sum += q*q;
	return sum;
    }
    /** Computes the 2-norm of the vector */
    double norm() {
	return Math.sqrt(norm2());
    }

    /** Normalizes this vector "in place" */
    void normalize() {
	multiply( 1.0 / norm());
    }

    void multiply(double c) {
	for(int i=0; i<values.length; i++) {
	    values[i] *= c;
	}
    }

}