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

    /** Classifies new docs, using the stored datapoint files for
	cluster center points. 
	@param mT output parameter; number of docs per class
    */
    static void classifyNewDocs(EntityManager em, ArticleAnalyzer z, ScoreDoc[] scoreDocs,
				//, HashMap<Integer,EE4DocClass> id2dc
				int mT[]
				) throws IOException {

	IndexReader reader = z.reader;
	Categorizer catz = new Categorizer(false);

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
	    File catdir =  getCatDirPath(cat);
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
	    KMeansClustering clu = new KMeansClustering(dic.size(), vdoc, centers);
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
	Categorizer catz = new Categorizer(false);

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
	    KMeansClustering clu = cluster(dic, z, vdocno);
	    saveCenters(clu, dic,  c, id0);

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

    /** Saves cluster centers in disk files. The files can be read
	back later, to be used for classifying new documents
	during nightly updates.
	@param clu Clustering whose cluster centers are to be saved
	@param cat Category name (which becomes the directory name)
	@param id0 the cluster id of the first cluster */
    private static void saveCenters(KMeansClustering clu, DocSet dic, String cat, int id0) throws IOException {
	File catdir = getCatDirPath(cat);
	catdir.mkdirs();
	for(int i=0; i<clu.centers.length; i++) {
	    int id = id0 + i;
	    File f= new File(catdir, "" +id + ".dat");
	    PrintWriter w= new PrintWriter(new FileWriter(f));
	    clu.centers[i].save( dic,  w);
	    w.close();
	}
    }

    /** Generates a file object for the directory where cluster center
	files are stored for a particular category. Does not actually
	create the directory.
	@return Something like ~/arxiv/arXiv-data/kmeans/physics.bio-ph
     */
    static private File getCatDirPath(String cat)  {
	File d = DataFile.getMainDatafileDirectory();
	d = new File(d,  "kmeans");
	d = new File(d, cat);
	return d;
    }

    /** Runs several KMeans clustering attempts on the specified set, and
	returns the best result.
	@param vdocno The list of Lucene IDs of the documents to be clustered
    */
    static private KMeansClustering cluster(DocSet dic, ArticleAnalyzer z,
					    Vector<Integer> vdocno)
	throws IOException {

	Profiler.profiler.push(Profiler.Code.OTHER);
	
	Vector<SparseDataPoint> vdoc = new Vector<SparseDataPoint>();
	//System.out.println("Reading vectors...");
	int cnt=0;
	for(int docno: vdocno) {
	    SparseDataPoint q=new SparseDataPoint(z.getCoef(docno,null),dic,z);
	    q.normalize();
	    vdoc.add(q);
	    cnt++;
	    //if (cnt  % 100 == 0) 	System.out.print(" " + cnt);
	}

	//	final int J = 5;
	//	final int nc = Math.min(J, vdoc.size());
	int nc = (int)Math.sqrt(  (double)vdoc.size()/200.0);
	if (nc <=1) nc = 1;

	return KMeansClustering.findBestClustering(vdoc, dic.size(), nc);
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
