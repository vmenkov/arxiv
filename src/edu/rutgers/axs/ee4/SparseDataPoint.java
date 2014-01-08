package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** A sparse vector, for use in KMeans. An object of this type is
    constructed from a document stored in Lucene.  */
class SparseDataPoint extends DataPoint {

    /** Feature ids, as per the feature dictionary, in increasing order */
    int[] features;
    /** List of feature ids */
    //public int[] getFeatures() { return features; }

    /** Number of non-zeros */
    public int size() { return features.length; }

    /** Initializes the vector using the term frequencies for a document
	@param h Term frequencies (processed)
	@param s The feature dictionary, which maps (or will map)
	terms (strings) to integer term id.
	@param z Is used to get certain document stats from the SQL database
     */
    SparseDataPoint(HashMap<String, ?extends Number> h, DocSet s, ArticleAnalyzer z)  throws IOException{
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