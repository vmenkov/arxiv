package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.ScoreDoc;

import java.io.*;
import java.util.*;
import java.util.regex.*;
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

    /** This class is responsible for looking up the "category" field
	in the Lucene data store for multiple articles, and keeping
	track of them all.
     */
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

    /** Classifies new docs, using the stored datapoint files for
	cluster center points. 
	@param mT output parameter; number of docs per class
    */
    static void classifyNewDocs(EntityManager em, ArticleAnalyzer z, ScoreDoc[] scoreDocs,
				//, HashMap<Integer,EE4DocClass> id2dc
				int mT[]
				) throws IOException {

	IndexReader reader = z.reader;
	Categorizer catz = new Categorizer();

	for(ScoreDoc sd: scoreDocs) {
	    int docno = sd.doc;
	    Document doc = reader.document(docno);
	    catz.categorize(docno, doc);
	}
	
	final Pattern p = Pattern.compile("([0-9]+)\\.dat");
	int caCnt=0;

	for(String cat: catz.catMembers.keySet()) {
	    // init empty dictionary
	    DocSet dic = new DocSet();

	    // read stored cluster center point files, filling the dictionary
	    // in the process.
	    File catdir = new File( getCatDirPath(cat));
	    if (!catdir.exists()) throw new IOException("Category directory " + catdir + " does not exist. Has a new category been added?");
	    File[] cfiles = catdir.listFiles();
	    int cids [] = new int[cfiles.length];
	    int nc=0;
	    for(File cf: cfiles) {
		String fname = cf.getName();
		Matcher m = p.matcher(fname);
		if (!m.matches()) {
		    System.out.println("Ignore file " + cf + ", because it is not named like a center file");
		    continue;
		}
		cids[nc++] = Integer.parseInt( m.group(1));
	    }

	    Arrays.sort(cids, 0, nc);
	    System.out.println("Found " + nc + " center files in " + catdir);
	    DenseDataPoint centers[] = new DenseDataPoint[nc];
	    for(int i=0; i<nc; i++) {
		File f= new File(catdir, "" +cids[i] + ".dat");
		centers[i] =new DenseDataPoint(dic, f);
	    }

	    // Read the new data points to be classified, using the same
	    // dictionary (expanding it in the process)
	    Vector<Integer> vdocno = catz.catMembers.get(cat);
	    Vector<SparseDataPoint> vdoc = new Vector<SparseDataPoint>();
	    //System.out.println("Reading vectors...");
	    int cnt=0;
	    for(int docno: vdocno) {
		SparseDataPoint q = new SparseDataPoint(z.getCoef( docno,null), dic, z);
		q.normalize();
		vdoc.add(q);
		cnt++;
	    }
	    // "classify" each point based on which existing cluster center
	    // is closest to it
	    Clustering clu = new Clustering(dic.size(), vdoc, centers);
	    clu.voronoiAssignment();  //   asg <-- centers

	    em.getTransaction().begin();
	    int pos=0;
	    for(int docno: vdocno) {
		int cid = cids[clu.asg[pos]];		
		mT[cid] ++;
		recordClass(docno, reader, em, cid);
		pos ++;
		caCnt++;
	    }
	    em.getTransaction().commit();
	}

	em.getTransaction().begin();
	for(int docno: catz.nocatMembers) {
	    int cid = 0;
	    recordClass(docno, reader, em, cid);
	}
	em.getTransaction().commit();

	System.out.println("Clustering applied to new docs in all "+ catz.catMembers.size()+" categories; " + 
			   //id0 + " clusters created; " + 
			   caCnt + " articles classified, and " + catz.nocatMembers.size() + " more are unclassifiable");

  }
   

    /** @param maxn 
     */
    static int clusterAll(ArticleAnalyzer z, EntityManager em, int maxn) throws IOException {
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
		int cid = id0 + clu.asg[pos];
		recordClass(docno, reader, em, cid);
		pos ++;
		caCnt++;
	    }
	    em.getTransaction().commit();
	    id0 += clu.centers.length;
	}	

	em.getTransaction().begin();
	for(int docno: catz.nocatMembers) {
	    int cid = 0;
	    recordClass(docno, reader, em, cid);
	}
	em.getTransaction().commit();

	System.out.println("Clustering completed on all "+ catz.catMembers.size()+" categories; " + id0 + " clusters created; " + caCnt + " articles classified, and " + catz.nocatMembers.size() + " more are unclassifiable");
	return id0;

    }

    /** Sets the class field on the specified article's Article object
	in the database, and persists it. This method should be called
	from inside a transaction. */
    static private void recordClass(int docno, IndexReader reader, EntityManager em, int cid) throws IOException {
	Document doc = reader.document(docno);
	String aid = doc.get(ArxivFields.PAPER);
	Article a = Article.findByAid(  em, aid);
	a.settEe4classId(cid);
	em.persist(a);
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
	final Vector<SparseDataPoint> vdoc;

	/** Output */
	int asg[];
	DenseDataPoint[] centers;
	final int nterms;
	
	/** Initializes the clustering object using a specified
	    list of pre-computed center points.
	*/
	Clustering(int _nterms, Vector<SparseDataPoint> _vdoc, DenseDataPoint[] _centers) {
	    nterms =  _nterms;
	    vdoc = _vdoc;
	    centers=_centers;
	}

	/** Initializes the clustering object using a specified
	    set of data points to serve as cluster centers.
	*/
	Clustering(int _nterms, Vector<SparseDataPoint> _vdoc, int[] ci) {
	    nterms =  _nterms;
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
	    clu.optimize();
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

    /** A one-off method, to create all document classes
	in the EE4DocClass table. */
    static void initClasses(int nc) {
	System.out.println("Checking or creating classes 1 through " + nc);
	EntityManager em  = Main.getEM();
	em.getTransaction().begin();
	int crtCnt=0;
	for(int cid=1; cid<=nc; cid++) {
	    EE4DocClass c = (EE4DocClass)em.find(EE4DocClass.class, cid);
	    if (c!=null) continue;
	    c = new EE4DocClass();
	    c.setId(cid);
	    em.persist(c);
	    crtCnt++;
	}
	em.getTransaction().commit();		
	System.out.println("Created "+crtCnt+" classes");
    }



    /** Out random number generator */
    static private Random gen = new  Random();

    static void usage() {
	System.out.println("Usage: [-Dn=...] [-Dclasses=...] KMeans [all|classes]");
	System.exit(0);
    }

    /**
       -Dn=1000 - how many docs to process
     */
    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	int maxn = ht.getOption("n", 10000);

	boolean all = false;
	if (argv.length != 1) {
	    usage();
	} else if (argv[0].equals("all")) {
	    all = true;
	} else  if (argv[0].equals("classes")) {
	} else {
	    usage();
	}

	int nClu =0;
	if (all) {
	    ArticleAnalyzer z = new ArticleAnalyzer();
	    EntityManager em  = Main.getEM();
	    
	    //	IndexReader reader =  Common.newReader();
	    nClu = clusterAll(z,em, maxn); //z.reader);
	} else {
	    nClu = ht.getOption("classes", 0);
	    if (nClu<0) usage();
	}

	// creating class entries
	initClasses(nClu);

	System.out.println("===Profiler report (wall clock time)===");
	System.out.println(	Profiler.profiler.report());

    }



   
}
