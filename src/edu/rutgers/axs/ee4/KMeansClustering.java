package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.rutgers.axs.sql.Util;

/** Our own implementation of k-means clustering. The objects being
    clustered are of the DataPoint class (or, actually, one of its
    subclasses). */
class KMeansClustering {
    /** Input: the objects to be clustered */
    final Vector<? extends DataPoint> vdoc; 

    /** Output: an array with the cluster id assigned to each object */
    int asg[];
    /** Cluster centers. */
    DenseDataPoint[] centers;
    final int nterms;
    
    /** Initializes the clustering object using a specified
	list of pre-computed center points.
	@param _vdoc The list of objects to cluster
	@param _nterm The dimension of the vector space to which the
	objects from _vdoc belong. In case of sparse vectors, this is
	the number of distinct features; in case of dense vectors, the
	size of each one.
	@param _centers Initial centers of the clusters. During the
	clustering process, the cluster centers will change, but their
	number will stay constant.
    */
    KMeansClustering(int _nterms, 
		     //Vector<SparseDataPoint> _vdoc, 
		     Vector<? extends DataPoint> _vdoc, 
		     DenseDataPoint[] _centers) {
	nterms = _nterms;
	vdoc = _vdoc;
	centers=_centers;
    }

    /** Initializes the clustering object using a specified
	set of data points to serve as cluster centers.
 	@param _vdoc The list of objects to cluster
    */
    KMeansClustering(int _nterms, Vector<? extends DataPoint> _vdoc, int[] ci) {
	nterms = _nterms;
	vdoc = _vdoc;
	centers=new DenseDataPoint[ci.length];
	for(int i=0;i<ci.length; i++) {
	    centers[i] =new DenseDataPoint(nterms, vdoc.elementAt(ci[i]));
	}	   
    }
    
    /** Runs KMeans algorithm starting with an arrangement where a specified
	set of points serve as cluster centers.
    */
    void optimize() {
	System.out.print("Assignment diff =");
	while(true) {
	    int[] asg0=asg;
	    //	    Profiler.profiler.push(Profiler.Code.CLU_Voronoi);
	    voronoiAssignment();  //   asg <-- centers
	    //	    Profiler.profiler.pop(Profiler.Code.CLU_Voronoi);
	    if (asg0!=null) {
		int d = asgDiff(asg0,asg);
		System.out.print(" " + d);
		if (d==0) {
		    System.out.println();
		    return;
		}
	    }
	    

	    //	    Profiler.profiler.push(Profiler.Code.CLU_fc);
	    findCenters(nterms, centers.length); // centers <-- asg
	    // Profiler.profiler.pop(Profiler.Code.CLU_fc);
	}
    }

    double [] centerNorms2() {
	double[] centerNorm2 = new double[centers.length];
	for(int i=0;i<centers.length;i++) centerNorm2[i]=centers[i].norm2();
	return centerNorm2;
    }

    /** Sets asg[] based on centers[] */
    void voronoiAssignment() {
	asg = new int[vdoc.size()];
	double[] centerNorm2 =  centerNorms2();
	// |v - c|^2 = |v|^2 + |c|^2 - 2(v,c) = 1 + |c|^2 - 2(v,c)
	for(int j=0; j<vdoc.size(); j++) {
	    DataPoint p = vdoc.elementAt(j);
	    double d2min = 0;
	    for(int i=0; i<centers.length; i++) {
		double d2 = 1 + centerNorm2[i] - 2*centers[i].dotProduct(p);
		if (i==0 || d2 < d2min) {
		    asg[j] = i;
		    d2min = d2;
		}
	    }
	}	
    }
   
    double sumDist2() {
	double sum=0;
	double[] centerNorm2 =  centerNorms2();

	System.out.println("Centers' norm^2 =" + arrToString(centerNorm2));
	
	// |v - c|^2 = |v|^2 + |c|^2 - 2(v,c) = 1 + |c|^2 - 2(v,c)
	int j=0;
	for(DataPoint p: vdoc) {
	    int ci=asg[j++];
	    sum += 1 + centerNorm2[ci] - 2*centers[ci].dotProduct(p);
	}	
	return sum;
    }

    /** Set centers[] based on asg[] */
    void findCenters(int nterms, int nc) {
	centers = new DenseDataPoint[nc];
	int[] clusterSize = new int[nc];
	int i=0;
	for(DataPoint p: vdoc) {
	    int ic = asg[i++];
	    if (centers[ic]==null) {
		centers[ic] = new DenseDataPoint(nterms, p);
	    } else {
		centers[ic].add(p);
	    }
	    clusterSize[ic]++;
	}
	for(i=0; i<nc; i++) {
	    if (centers[i]==null) {
		System.out.println("Invalid assigment: no doc in cluster "+i+"?; asg=" + arrToString(asg));
		throw new IllegalArgumentException();
	    }
	    centers[i].multiply(1.0/(double) clusterSize[i]);
	}
    }

    /** How different are two assignment plans? */
    private static int asgDiff(int asg1[], int asg2[]) {
	int d = 0;
	for(int i=0; i<asg1.length; i++) {
	    if (asg1[i]!=asg2[i]) d++;
	}
	return d;
    }
    
    static String arrToString(double a[]) {
	StringBuffer b=new StringBuffer("(");
	for(double q: a) 	    b.append(" " + q);
	b.append(")");
	return b.toString();
    }

    static String arrToString(int asg[]) {
	StringBuffer b=new StringBuffer("(");
	for(int q: asg) 	    b.append(" " + q);
	b.append(")");
	return b.toString();
    }

    /** Runs several KMeans clustering attempts on the specified set, and
	returns the best result.
	@param vdoc The list of vectors (data points) in some
	Euclidean space to be clustered
	@param nFeature The dimension of the space in which the vectors are
	@param nc The desired number of clusters. The number should be
	no greater than vdoc.size()
    */
    static KMeansClustering findBestClustering(Vector<? extends DataPoint> vdoc, int nFeatures, int nc) {

	System.out.println("Chose to create " + nc + " clusters");

	int nstarts = 10;
	if (vdoc.size() == nc||nc==1) nstarts=1;

	double minD = 0;
	KMeansClustering bestClustering = null;

	for(int istart =0; istart<nstarts; istart++) {
	    System.out.println("Start no. " + istart);
	    int ci[] = Util.randomSample( vdoc.size(), nc);
	    System.out.print("Random centers at: ");
	    for(int q: ci) 	    System.out.print(" " + q);
	    System.out.println();
	    //	    Profiler.profiler.push(Profiler.Code.CLUSTERING);

	    KMeansClustering clu = new KMeansClustering(nFeatures, vdoc, ci);
	    clu.optimize();
	    //	    Profiler.profiler.replace(Profiler.Code.CLUSTERING,Profiler.Code.CLU_sumDif);
	    double d = clu.sumDist2();
	    System.out.println("D=" + d);
	    //System.out.println("; asg=" + arrToString(clu.asg));
	    if (bestClustering==null || d<minD) {
		bestClustering = clu;
		minD = d;
	    }

	    //	    Profiler.profiler.pop(Profiler.Code.CLU_sumDif);
	}
	//	Profiler.profiler.pop(Profiler.Code.OTHER);
	return bestClustering;
    }

}

