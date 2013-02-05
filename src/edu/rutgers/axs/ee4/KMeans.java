package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;


import java.util.*;
import java.io.*;
import java.text.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
//import edu.rutgers.axs.recommender.ArxivScoreDoc;
import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** Document clustering (KMeans), for Peter Frazier's Exploration
    Engine ver. 4. 
    
 */
public class KMeans {
    static int maxn= 1000;

    static void clusterAll(ArticleAnalyzer z, EntityManager em) throws IOException {
	IndexReader reader = z.reader;

	int numdocs = reader.numDocs();
	int maxdoc = reader.maxDoc();
	int cnt=0;

	HashMap<String, Vector<Integer>> catMembers = new 	HashMap<String, Vector<Integer>>();

	for(int docno=0; docno<maxdoc; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    String cats = doc.get(ArxivFields.CATEGORY);
	    // System.out.println("" + docno + " : " + aid + " : " + cats);

	    String[] catlist = CatInfo.split(cats);

	    Integer o = new Integer(docno);

	    for(String cat: catlist) {
		Categories.Cat c = Categories.findActiveCat(cat);
		if (c==null) continue;
		Vector<Integer> v = catMembers.get(cat);
		if (v==null) catMembers.put(cat, v=new Vector<Integer>());
		v.add(o);
	    }

	   
	    cnt++;
	    if (cnt>=maxn) break;
	}	
	System.out.println("Analyzed " + cnt + " articles; identified " +catMembers.size() + " categories");
	/*
	System.out.println("Cat sizes:");
	for(String c: catMembers.keySet()) {
	    System.out.println(c + ": " + catMembers.get(c).size());
	}
	*/

	//	ArticleStats[] allStats = ArticleStats.getArticleStatsArray(em, reader); 
	for(String c: catMembers.keySet()) {
	    System.out.println("Running clustering on category " + c + ", size=" + catMembers.get(c).size());

	    cluster(z, catMembers.get(c));

	}	
    }

    /** Is ci[pos] different from all preceding array elements?
     */
    private static boolean isUnique(int[] ci, int pos) {
	for(int i=0; i<pos; i++) {
	    if (ci[i] == ci[pos]) return false;	    
	}
	return true;
    }

    /** Returns an array of nc distinct numbers randomly selected from
     * the range [0..n)
     */
    private static int[] randomSample(int n, int nc) {
	int ci[] = new int[nc]; 
	for(int i=0; i<ci.length; i++) {
	    do {
		ci[i] = gen.nextInt(n);
	    } while(!isUnique(ci,i));		
	}
	return ci;
    }
    


    /** How different are two assignment plans? */
    static int asgDiff(int asg1[], int asg2[]) {
	int d = 0;
	for(int i=0; i<asg1.length; i++) {
	    if (asg1[i]!=asg2[i]) d++;
	}
	return d;
    }
    

    static class Clustering {
	/** input */
	Vector<SparseDataPoint> vdoc;

	/** Output */
	int asg[];
	DenseDataPoint[] centers;
	
	/** Runs KMeans algorithm starting with an arrangemnt where a specified
	    set of points serve as cluster centers.
	*/
	Clustering(int nterms, Vector<SparseDataPoint> _vdoc, int[] ci) {
	    vdoc = _vdoc;
	    centers=new DenseDataPoint[ci.length];
	    for(int i=0;i<ci.length; i++) {
		centers[i] =new DenseDataPoint(nterms, vdoc.elementAt(ci[i]));
	    }	   
	    System.out.print("Assignment diff =");
	    while(true) {
		int[] asg0=asg;
		voronoiAssignment();  //   asg <-- centers
		if (asg0!=null) {
		    int d = asgDiff(asg0,asg);
		    System.out.print(" " + d);
		    if (d==0) {
			System.out.println();
			return;
		    }
		}
		findCenters(nterms, centers.length); // centers <-- asg
	    }
	}

	double [] centerNorms2() {
	    double[] centerNorm2 = new double[centers.length];
	    for(int i=0;i<centers.length;i++) centerNorm2[i]=centers[i].norm2();
	    return centerNorm2;
	}

	/** Sets asg[] based on centers[] */
	private  void voronoiAssignment() {
	    asg = new int[vdoc.size()];
	    double[] centerNorm2 =  centerNorms2();
	    // |v - c|^2 = |v|^2 + |c|^2 - 2(v,c) = 1 + |c|^2 - 2(v,c)
	    for(int j=0; j<vdoc.size(); j++) {
		SparseDataPoint p = vdoc.elementAt(j);
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
	    for(SparseDataPoint p: vdoc) {
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
	    for(SparseDataPoint p: vdoc) {
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


    static private void cluster(ArticleAnalyzer z,
				// ArticleStats[] as, 
				Vector<Integer> vdocno) throws IOException {
	
	DocSet dic = new DocSet();
	Vector<SparseDataPoint> vdoc = new Vector<SparseDataPoint>();
	System.out.println("Reading vectors...");
	int cnt=0;
	for(int docno: vdocno) {
	    SparseDataPoint q = new SparseDataPoint(z.getCoef( docno,null), dic, z);
	    q.normalize();
	    vdoc.add(q);
	    if (cnt++  % 100 == 0) 	System.out.print(" " + cnt);
	}
	System.out.println(" " + cnt);

	final int J = 5;
	final int nstarts = 10;
	final int nc = Math.min(J, vdocno.size());
	for(int istart =0; istart<nstarts; istart++) {
	    System.out.println("Start no. " + istart);
	    int ci[] = randomSample( vdocno.size(), nc);
	    System.out.print("Random centers at: ");
	    for(int q: ci) 	    System.out.print(" " + q);
	    System.out.println();
	    Clustering clu = new Clustering(dic.size(), vdoc, ci);
	    double d = clu.sumDist2();
	    System.out.print("D=" + d);
	    System.out.println("; asg=" + arrToString(clu.asg));
	    
	}
	
    }

    /** Out random number generator */
    static private Random gen = new  Random();

    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	maxn = ht.getOption("n", maxn);
	ArticleAnalyzer z = new ArticleAnalyzer();
	EntityManager em  = Main.getEM();

	//	IndexReader reader =  Common.newReader();
	clusterAll(z,em); //z.reader);
    }

}
