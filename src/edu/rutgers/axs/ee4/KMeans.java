package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;


import java.util.*;
import java.io.*;
import java.text.*;

import javax.persistence.*;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.recommender.*;

import edu.cornell.cs.osmot.options.Options;

/** Document clustering (KMeans), for Peter Frazier's Exploration
    Engine ver. 4. 

    <P> NOTE: For good performance of this tool, in particularly, the
    ArticleAnalyzer.getCoef() call, it is highly important for the
    Lucene index to have been optimized during the last ArxivImporter
    run.  We are talking the loading rate difference between 20
    doc/sec and 100 doc/sec here!
    
 */
public class KMeans {
    static final boolean primaryOnly=true;

    static class Categorizer {
	private static final int NS = 100;

 	HashMap<String, Vector<Integer>> catMembers = new 	HashMap<String, Vector<Integer>>();
	Vector<Integer> nocatMembers= new Vector<Integer>();
	int multiplicityCnt[] = new int[NS];
	int cnt=0, unassignedCnt=0;	

	void categorize(int docno, Document doc) {

	    String aid = doc.get(ArxivFields.PAPER);
	    String cats = doc.get(ArxivFields.CATEGORY);
	    // System.out.println("" + docno + " : " + aid + " : " + cats);

	    String[] catlist = CatInfo.split(cats);

	    multiplicityCnt[ Math.min(NS-1, catlist.length)]++;

	    Integer o = new Integer(docno);

	    boolean assigned=false;
	    for(String cat: catlist) {
		Categories.Cat c = Categories.findActiveCat(cat);
		if (c==null) continue;
		Vector<Integer> v = catMembers.get(cat);
		if (v==null) catMembers.put(cat, v=new Vector<Integer>());
		v.add(o);
		assigned=true;
		if (primaryOnly) break; // one cat per doc only
	    }
	    if (!assigned) {
		unassignedCnt++;
		nocatMembers.add(o);
	    }
	    cnt++;
	}

	String stats() {
	    return "Analyzed " + cnt + " articles; identified " +catMembers.size() + " categories. There are " + unassignedCnt + " articles that do not belong to any currently active category.";
	}

	String affiStats() {
	    String s= "Category affiliation count for articles:\n";
	    for(int i=0; i<multiplicityCnt.length; i++) {
		if (multiplicityCnt[i]>0) {
		    s += "" + i;
		    s += (i+1==multiplicityCnt.length? " (or more)": "");
		    s += " categories: " + multiplicityCnt[i]+" articles\n";
		}
	    }
	    return s;
	}

	String catSizesStats() {
	    String s= "Cat sizes:\n";
	    for(String c: catMembers.keySet()) {
		s += c + ": " + catMembers.get(c).size() + "\n";
	    }
	    return s;
	}
   }

    /** Classify new docs */
    void classifyNewDocs(EntityManager em, IndexReader reader, ScoreDoc[] scoreDocs, HashMap<Integer,EE4DocClass> id2dc) throws IOException {

	Categorizer catz = new Categorizer();

	for(ScoreDoc sd: scoreDocs) {
	    int docno = sd.doc;
	    Document doc = reader.document(docno);
	    catz.categorize(docno, doc);
	}
	
	for(String c: catz.catMembers.keySet()) {
	}
	/*
	{
	    int cid = classify(doc);
	    String aid = doc.get(ArxivFields.PAPER);
	    Article a = Article.getArticleAlways(em,aid);
	    a.settEe4classId(cid);

	    em.getTransaction().begin();
	    em.persist(a);
	    em.getTransaction().commit();	
	    id2dc.get(new Integer(cid)).incM();
	}
	*/
    }
   

    /** @param maxn 
     */
    static void clusterAll(ArticleAnalyzer z, EntityManager em, int maxn) throws IOException {
	ArticleAnalyzer.setMinDf(10); // as PG suggests, 2013-02-06
	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	IndexReader reader = z.reader;

	int numdocs = reader.numDocs();
	int maxdoc = reader.maxDoc();
	Categorizer catz = new Categorizer();

	for(int docno=0; docno<maxdoc; docno++) {
	    if (reader.isDeleted(docno)) continue;
	    Document doc = reader.document(docno);
	    catz.categorize(docno, doc);
	    if (catz.cnt>=maxn) break;
	}	
	System.out.println(catz.stats());

	System.out.println(catz.affiStats());
	//	System.exit(0);

	//	System.out.println(catz.catSizesStats());
	int caCnt=0;

	int id0 = 1;
	for(String c: catz.catMembers.keySet()) {
	    System.out.println("Running clustering on category " + c + ", size=" + catz.catMembers.get(c).size());

	    DocSet dic = new DocSet();
	    Vector<Integer> vdocno = catz.catMembers.get(c);
	    Clustering clu = cluster(dic, z, vdocno);
	    clu.saveCenters(dic,  c, id0);

	    em.getTransaction().begin();
	    int pos=0;
	    for(int docno: vdocno) {
		Document doc = reader.document(docno);
		String aid = doc.get(ArxivFields.PAPER);
		Article a = Article.findByAid(  em, aid);
		int cid = id0 + clu.asg[pos];
		a.settEe4classId(cid);
		em.persist(a);
		pos ++;
		caCnt++;
	    }
	    em.getTransaction().commit();
	    id0 += clu.centers.length;
	}	

	em.getTransaction().begin();
	for(int docno: catz.nocatMembers) {
	    Document doc = reader.document(docno);
	    String aid = doc.get(ArxivFields.PAPER);
	    Article a = Article.findByAid(  em, aid);
	    int cid = 0;
	    a.settEe4classId(cid);
	    em.persist(a);
	}
	em.getTransaction().commit();

	System.out.println("Clustering completed on all "+ catz.catMembers.size()+" categories; " + id0 + " clusters created; " + caCnt + " articles classified, and " + catz.nocatMembers.size() + " more are unclassifiable");

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
		Profiler.profiler.push(Profiler.Code.CLU_Voronoi);
		voronoiAssignment();  //   asg <-- centers
		Profiler.profiler.pop(Profiler.Code.CLU_Voronoi);
		if (asg0!=null) {
		    int d = asgDiff(asg0,asg);
		    System.out.print(" " + d);
		    if (d==0) {
			System.out.println();
			return;
		    }
		}


		Profiler.profiler.push(Profiler.Code.CLU_fc);
		findCenters(nterms, centers.length); // centers <-- asg
		Profiler.profiler.pop(Profiler.Code.CLU_fc);
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

	/** Saves cluster centers in disk files. The files can be read
	    back later, to be used for classifying new documents
	    during nightly updates.
	    @param cat Category name (which becomes the directory name)
	    @param id0 the cluster id of the first cluster */
	void saveCenters(DocSet dic, String cat, int id0) throws IOException {
	    File catdir = new File( getCatDirPath(cat));
	    catdir.mkdirs();
	    for(int i=0; i<centers.length; i++) {
		int id = id0 + i;
		File f= new File(catdir, "" +id + ".dat");
		PrintWriter w= new PrintWriter(new FileWriter(f));
		centers[i].save( dic,  w);
		w.close();
	    }
	}


    }

    /** Generates full path for the directory where cluster center
	files are stored for a particular category.
     */
    static String  getCatDirPath(String cat)  {
	String s = "";
	try {
	    s = Options.get("DATAFILE_DIRECTORY") +	File.separator;
	} catch(IOException ex) {
	    Logging.error("Don't know where DATAFILE_DIRECTORY is");
	}
	s += "kmeans"  +	File.separator + cat;
	return s;
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
	returns the best result */
    static private Clustering cluster(DocSet dic, ArticleAnalyzer z,Vector<Integer> vdocno)
	throws IOException {

	Profiler.profiler.push(Profiler.Code.OTHER);
	
	Vector<SparseDataPoint> vdoc = new Vector<SparseDataPoint>();
	//System.out.println("Reading vectors...");
	int cnt=0;
	for(int docno: vdocno) {
	    SparseDataPoint q = new SparseDataPoint(z.getCoef( docno,null), dic, z);
	    q.normalize();
	    vdoc.add(q);
	    cnt++;
	    //if (cnt  % 100 == 0) 	System.out.print(" " + cnt);
	}
	//	System.out.println(" " + cnt);

	//	final int J = 5;
	//	final int nc = Math.min(J, vdoc.size());
	int nc = (int)Math.sqrt(  (double)vdoc.size()/200.0);
	if (nc <=1) nc = 1;

	System.out.println("Chose to create " + nc + " clusters");

	int nstarts = 10;
	if (vdoc.size() == nc||nc==1) nstarts=1;

	double minD = 0;
	Clustering bestClustering = null;

	for(int istart =0; istart<nstarts; istart++) {
	    System.out.println("Start no. " + istart);
	    int ci[] = randomSample( vdocno.size(), nc);
	    System.out.print("Random centers at: ");
	    for(int q: ci) 	    System.out.print(" " + q);
	    System.out.println();
	    Profiler.profiler.push(Profiler.Code.CLUSTERING);

	    Clustering clu = new Clustering(dic.size(), vdoc, ci);
	    Profiler.profiler.replace(Profiler.Code.CLUSTERING,Profiler.Code.CLU_sumDif);
	    double d = clu.sumDist2();
	    System.out.println("D=" + d);
	    //System.out.println("; asg=" + arrToString(clu.asg));
	    if (bestClustering==null || d<minD) {
		bestClustering = clu;
		minD = d;
	    }

	    Profiler.profiler.pop(Profiler.Code.CLU_sumDif);
	}
	Profiler.profiler.pop(Profiler.Code.OTHER);
	return bestClustering;
    }

    /** Out random number generator */
    static private Random gen = new  Random();

    /**
       -Dn=1000 - how many docs to process
     */
    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	int maxn = ht.getOption("n", 10000);
	ArticleAnalyzer z = new ArticleAnalyzer();
	EntityManager em  = Main.getEM();

	//	IndexReader reader =  Common.newReader();
	clusterAll(z,em, maxn); //z.reader);

	System.out.println("===Profiler report (wall clock time)===");
	System.out.println(	Profiler.profiler.report());

    }



   
}
