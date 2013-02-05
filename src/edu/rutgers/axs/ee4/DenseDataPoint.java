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

    DenseDataPoint(int n, DataPoint x) {
	this(n);
	add(x);
    }

    void add(DataPoint a) {
	if (a instanceof SparseDataPoint) {
	   SparseDataPoint x = (SparseDataPoint)a;
	   for(int i=0; i<x.features.length; i++) {
	       values[x.features[i]] += x.values[i];
	   }
	} else if  (a instanceof DenseDataPoint) {
	    if (values.length != a.values.length) throw new IllegalArgumentException("DDP.add(DDP): dim mismatch!");
	   for(int i=0; i<values.length; i++) {
	       values[i] += a.values[i];
	   }
	} else {
	   throw new IllegalArgumentException("DDP.add(?)");
	}
    }

    double dotProduct(DataPoint a) {
       double sum=0;
       if (a instanceof SparseDataPoint) {
	   SparseDataPoint x = (SparseDataPoint)a;
	   for(int i=0; i<x.features.length; i++) {
	       sum += values[x.features[i]] * x.values[i];
	   }
       } else if  (a instanceof DenseDataPoint) {
	   if (values.length != a.values.length) throw new IllegalArgumentException("DDP.dotProduct(DDP): dim mismatch!");
 	   for(int i=0; i<values.length; i++) {
	       sum += values[i] * a.values[i];
	   }
       } else {
	   throw new IllegalArgumentException("DDP.dotProduct(?)");
       }
       return sum;
    }

}