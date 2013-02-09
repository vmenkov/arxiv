package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** A sparse vector, for use in KMeans */
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
    double norm() {
	return Math.sqrt(norm2());
    }

    void normalize() {
	multiply( 1.0 / norm());
    }

    void multiply(double c) {
	for(int i=0; i<values.length; i++) {
	    values[i] *= c;
	}
    }

}