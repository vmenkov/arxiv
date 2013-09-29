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

    /** Square of norms of the objects */
    double [] vdocNorm2;

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
		     Vector<? extends DataPoint> _vdoc, 
		     DenseDataPoint[] _centers) {
	nterms = _nterms;
	vdoc = _vdoc;
	vdocNorm2 = new double[vdoc.size()];
	centers=_centers;
	int i=0;
	for(DataPoint p: vdoc) vdocNorm2[i++]=p.norm2();
    }

    /** Initializes the clustering object using a specified
	set of data points to serve as cluster centers.
 	@param _vdoc The list of objects to cluster
    */
    KMeansClustering(int _nterms, Vector<? extends DataPoint> _vdoc, int[] ci) {
	this( _nterms, _vdoc, new DenseDataPoint[ci.length]);
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
	    handleEmptyClusters(centers.length); // adjust asg if needed

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
	    findCenters( centers.length); // centers <-- asg
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
	    double d2min = 0;
	    for(int i=0; i<centers.length; i++) {
		double d2 = vdocNorm2[i] + centerNorm2[i] - 2*centers[i].dotProduct( vdoc.elementAt(j)  );
		if (i==0 || d2 < d2min) {
		    asg[j] = i;
		    d2min = d2;
		} else if (d2==d2min) {		    // tie? 
		    //System.out.print(" [point "+j+": "+asg[j]+" ~ " +i+"]");
		}
	    }
	}	
    }

    /** Checks the current assignment in asg[] for any empty clusters
	(which is a possible, even if rare, outcome of Voronoi tesselation).
	When such a cluster is found, it is "reused" for splitting 
	one of the existing clusters.
     */
    private void handleEmptyClusters(int nc) {
	int population[] = clusterPopulations(nc);

	int eCnt=0;
	for(int nchecked=0; nchecked < nc; nchecked++) {
	    if (population[nchecked]>0) continue; // not empty
	    eCnt++;
	    System.out.print(" [Empty cluster "+nchecked+"] ");
	    // find the biggest non-empty cluster to split
	    int mi=0;
	    for(int i=0; i<nc; i++) {
		if (population[i]  > population[mi]) mi = i;
	    }
	    if (population[mi]<2) throw new IllegalArgumentException("Way too many clusters for way too few examples?");
	    // reassign about half of the examples from this largest cluster
	    // to the empty cluster
	    for(int j=0; population[nchecked] + 1 < population[mi]; j++) {
		if (j>=asg.length) throw new AssertionError();
		if (asg[j]==mi) {
		    asg[j]=nchecked;
		    population[mi]--;
		    population[nchecked]++;
		}
	    }
	    
	}
	
    }
   
    /** Reports current cluster sizes (as in asg[]) */
    private int[] clusterPopulations(int nc) {
	int population[] = new int[nc];
	for(int c: asg) {
	    population[c]++;
	}
	return population;
    }
    

    double sumDist2() {
	double sum=0;
	double[] centerNorm2 =  centerNorms2();

	//	System.out.println("Centers' norm^2 =" + arrToString(centerNorm2));
	
	// |v - c|^2 = |v|^2 + |c|^2 - 2(v,c) = 1 + |c|^2 - 2(v,c)
	int j=0;
	for(DataPoint p: vdoc) {
	    int ci=asg[j];
	    sum += vdocNorm2[j] + centerNorm2[ci] - 2*centers[ci].dotProduct(p);
	    j++;
	}	
	return sum;
    }

    String describeStats() {
	StringBuffer s=new StringBuffer();
	double[] centerNorm2 =  centerNorms2();
	int[] pop = clusterPopulations(centers.length);
	double r[] = new double[centers.length];
	int j=0;
	for(DataPoint p: vdoc) {
	    int ci=asg[j];
	    // FIXME: not "1", but |v|^2... here and everewhere!
	    r[ci] +=vdocNorm2[j]+centerNorm2[ci] - 2*centers[ci].dotProduct(p);
	    j++;
	}	
	for(int i=0; i<centers.length; i++) {
	    r[i] = Math.sqrt(r[i]/pop[i]);
	    if (i>0) s.append(", ");
	    s.append("{pop=" + pop[i] + 
		     " |c|=" + Math.sqrt( centerNorm2[i]) +
		     " |r|=" + r[i] + "}");
	}
	return s.toString();
    }

    /** This is the list of empty clusters, if any. The vector is
	re-initialized by each findCenters() call.
     */
    private Vector<Integer> emptyClusters = new Vector<Integer>();

    /** Set centers[] based on asg[]. Before calling this method, the array asg[] should be checked for empty clusters, and any such clusters should be removed.
	@param nc The expected number of clusters. The values in asg[] should
	be in the range [0..nc-1]
    */
    private void findCenters( int nc) {
	emptyClusters.clear();
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
		String msg = "Invalid assigment: no doc in cluster "+i+"?; asg=" + arrToString(asg);
		System.out.println(msg);
		throw new IllegalArgumentException(msg);
	    } else {
		centers[i].multiply(1.0/(double) clusterSize[i]);
	    }
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
	int bestIstart = 0;

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
	    System.out.println("Clustering scheme no. " + istart + ": " +
			       clu.describeStats());

	    double d = clu.sumDist2();
	    System.out.println("D=" + d);
	    //System.out.println("; asg=" + arrToString(clu.asg));
	    if (bestClustering==null || d<minD) {
		bestIstart = istart;
		bestClustering = clu;
		minD = d;
	    }

	    //	    Profiler.profiler.pop(Profiler.Code.CLU_sumDif);
	}
	//	Profiler.profiler.pop(Profiler.Code.OTHER);
	
	System.out.println("Chose clustering scheme from run " + bestIstart + " as the best");
	return bestClustering;
    }

}

