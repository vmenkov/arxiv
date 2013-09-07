package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
//import java.text.*;

import cern.colt.matrix.impl.DenseDoubleMatrix1D;

/** A replacement for cern.colt.matrix.impl.SparseDoubleMatrix2D,
    necessitated by the restriction, |cols|*|rows|&lt;MAX_INT that the
    latter has.
 */
class SparseDoubleMatrix2Dx {
    private int ncol, nrow;
    int columns() { return ncol;}
    int rows() { return nrow; }

    static class Row {
	int [] pos;
	double [] val;
	Row(int[] _pos, double[] _val) {
	    pos = Arrays.copyOfRange(_pos, 0, _pos.length);
	    val = Arrays.copyOfRange(_val, 0, _val.length);
	}
 	Row(int[] _pos, double _val) {
	    pos = Arrays.copyOfRange(_pos, 0, _pos.length);
	    val = new double[_pos.length];
	    for(int i=0; i<val.length; i++) val[i] = _val;
	}
    }

    Row[] matrix;

    SparseDoubleMatrix2Dx(int _nrow, int _ncol) {
	nrow = _nrow;
	ncol = _ncol;
	matrix = new Row[nrow];
	System.out.println("SparseDoubleMatrix2Dx(r="+nrow+","+ncol+")");
    }

    void setRow(int i, int[] _pos, double[] _val) {
	matrix[i] = new Row(_pos, _val);	
    }
    
    void setRowSameValue(int i, int[] _pos, double _val) {
	matrix[i] = new Row(_pos, _val);	
    }
     
    int cardinality() {
	int sum=0;
	for(Row r: matrix) sum += r.pos.length;
	return sum;
    }

    /** y := this*x + y ; return y. */
    DenseDoubleMatrix1D zMult(DenseDoubleMatrix1D x, DenseDoubleMatrix1D y) {
	if (y==null) y = new DenseDoubleMatrix1D(rows());
	int k=0;
	for(Row r: matrix) {
	    double sum = 0;
	    for(int i=0; i<r.pos.length; i++) {
		sum += r.val[i] * x.getQuick( r.pos[i] );
	    }
	    y.setQuick(k, y.getQuick(k) + sum);
	    k++;
	}
	return y;
    }


    /** y := this^T * x + y ; return y. */
    DenseDoubleMatrix1D zMultTrans(DenseDoubleMatrix1D x,DenseDoubleMatrix1D y){
 	if (y==null) y = new DenseDoubleMatrix1D(columns());
	int k=0;
	double[] sum = new double[ columns()];
	for(Row r: matrix) {
	    final double q =  x.getQuick(k++);
	    for(int i=0; i<r.pos.length; i++) {
		sum[r.pos[i]] += r.val[i] * q;
	    }
	}
	for(int j=0; j<sum.length; j++) {
	    y.setQuick(j, y.getQuick(j) + sum[j]);
	}
	return y;
    }


}


