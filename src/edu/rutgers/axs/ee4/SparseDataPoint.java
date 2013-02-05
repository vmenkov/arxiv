package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** A sparse vector, for use in KMeans */
class SparseDataPoint extends DataPoint {

    /** Feature ids, as per the FeatureDictionary, in increasing order */
    int[] features;
    /** List of feature ids */
    //public int[] getFeatures() { return features; }

    /** Number of non-zeros */
    public int size() { return features.length; }

    SparseDataPoint(HashMap<String, Double> h, DocSet s, ArticleAnalyzer z) {
	int n = h.size();
	features = new int[n];
	values = new double[n];
	int i=0;
	for(String word: h.keySet()) {
	    Integer pos = s.enter(word);
	    features[i++] = pos.intValue();
	}
	Arrays.sort(features);
	for(i=0; i<n; i++) {
	    String word = s.getWord(features[i]);
	    double weight =z.idf(word);
	    values[i] = h.get(word).doubleValue() * Math.sqrt(weight);
	}
    }

}