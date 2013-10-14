package edu.rutgers.axs.ee4;

import java.util.*;

import cern.colt.matrix.*;
import cern.colt.matrix.impl.*;

/** Our own implementation of partial Singular Value Decomposition. */
public class SVD {

    private SparseDoubleMatrix2Dx matrix;
    private boolean verbose = true;

    SVD(SparseDoubleMatrix2Dx a) {
	matrix = a;
    }

    private double[] sval = null;    
    double[] getSingularValues() {
	return sval;
    }

    /** Stored singular vectors, i.e. columns  of V */
    private Vector<DenseDoubleMatrix1D> results;

    /** Partial SVD of matrix a */
    void findTopSingularVectors(int k_svd) {
	SparseDoubleMatrix2Dx a=matrix;
	int n = a.columns();
	if (k_svd > n) k_svd = n;

	results = new Vector<DenseDoubleMatrix1D>(k_svd);
	sval = new double[k_svd];
	for(int j = 0; j<k_svd; j++) {

	    DenseDoubleMatrix1D x = randomVector(n);	
	    normalize(x);
	    int i = 0;
	    boolean foundZeroValue=false;
	    while(true) {
		DenseDoubleMatrix1D y = a.zMult(x,null);
		DenseDoubleMatrix1D z = a.zMultTrans(y, null);
		ortho(z, results);

		double norm = Math.sqrt(norm2(z));
		times(z,1.0/norm);
		DenseDoubleMatrix1D d = diff(z, x);
		double normDiff = Math.sqrt(norm2(d));
		i++;
		if (verbose) {
		    System.out.println("["+i+"], |PAtAx|=" + norm +", |z-x|=" + normDiff);
		}
		x = z;

		if (norm < 1e-12) {
		    // it does not help to compute normDiff here, as 
		    // computation errors may be too big 
		    if (verbose) {
			System.out.println("Apparently found a zero singular value... finishing SVD");
		    }
		    foundZeroValue=true;
		    break;
		}

		if (normDiff < 1e-6) {
		    sval[results.size()] = Math.sqrt(norm);
		    break;
		}
	    }
	    if (foundZeroValue) break;
	    results.add(x);
	}
	if (results.size() < k_svd) {
	    k_svd = results.size();
	    sval = Arrays.copyOfRange(sval, 0, k_svd);
	}
    }

    /** Creates a random vector (not normalized) */
    private static  DenseDoubleMatrix1D randomVector(int n) {
	DenseDoubleMatrix1D x = new DenseDoubleMatrix1D(n);
	for(int i=0; i<n; i++) {
	    x.setQuick(i, Math.random());
	}
	return x;
    }

    private static double norm2(DenseDoubleMatrix1D x) {
	double sum = 0;
	for(double q: x.toArray()) {
	    sum += q*q;
	}
	return sum;
    }

    private static void times(DenseDoubleMatrix1D x, double c) {
	int n = x.size();
	for(int i=0; i<n; i++) {
	    x.setQuick( i, x.getQuick(i)*c);
	}
    }

    private static void normalize(DenseDoubleMatrix1D x) {
	double norm = Math.sqrt( norm2(x));
	times(x, 1.0/norm);
    }

    private static DenseDoubleMatrix1D diff(DenseDoubleMatrix1D a, DenseDoubleMatrix1D b) {
	int n = a.size();
	DenseDoubleMatrix1D c = new  DenseDoubleMatrix1D(n);
	for(int i=0; i<n; i++) {
	    c.setQuick( i, a.getQuick(i) - b.getQuick(i));
	}
	return c;
    }

    /** a := a + c*b
     */
    private static void daxpy(DenseDoubleMatrix1D a, DenseDoubleMatrix1D b, double c) {
	int n = a.size();
	for(int i=0; i<n; i++) {
	    a.setQuick( i, a.getQuick(i) + c* b.getQuick(i));
	}
    }


    /** Projects x to the space orthogonal to span{ other[0...m-1] }
	@param other An orray of orthonormal vectors (i.e., they are
	orthogonal to each other, and the norm of each one is 1)
     */
    static void ortho(DenseDoubleMatrix1D x, Vector<DenseDoubleMatrix1D> other) {
	for(DenseDoubleMatrix1D q: other) {
	    double p = x.zDotProduct(q);
	    daxpy( x, q, -p);
	}
    }


    /** Converts the matrix V (whose columns are [some of the] right singular
	vectors of A) into a 2-D array of doubles, stored by row.
	Each row of V will be packaged into an arrray of doubles. */
    double[][] vIntoArrayOfRows() {

	if (results.size()==0) throw new IllegalArgumentException("Empty matrix - nothing to do!");
	int ndoc  = results.elementAt(0).size();
	double[][] z = new double[ndoc][];

	for(int i=0; i<ndoc; i++) {
	    double[] q= new double[results.size()];
	    for(int j=0; j<q.length; j++) {
		q[j] = 	results.elementAt(j).getQuick(i); 
	    }
	    z[i] = q;
	}
	return z;
    }


}
