package edu.rutgers.axs.ee5;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;


import java.util.*;
import java.util.regex.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.recommender.*;
import edu.rutgers.axs.ee4.Categorizer;
import edu.rutgers.axs.ee4.DenseDataPoint;

/** Document classifier.
 */
public class Classifier {

      /** Classifies new docs, using the stored P files
	@param z
	@param scoreDocs list of documents to classify
	@param mT output parameter; number of docs per class
    */
    static void classifyNewDocs(EntityManager em, ArticleAnalyzer z, ScoreDoc[] scoreDocs,
				//, HashMap<Integer,EE4DocClass> id2dc
				int mT[]
				) throws IOException {



	File f = new File("/home/vmenkov/arxiv/ee5/kmeans1024.csv");
	if (!f.canRead()) {
	    throw new IOException("Vocabulary clustering file " + f + " does not exist or is not readable");
	}
	Vocabulary voc = new Vocabulary(f, 0);
	final int L = voc.L;
	Logging.info("Read the vocabulary with " + voc.size() + " multiwords, L=" + L);

	IndexReader reader = z.getReader();
	Categorizer catz = new Categorizer(false);

	for(ScoreDoc sd: scoreDocs) {
	    int docno = sd.doc;
	    Document doc = reader.document(docno);
	    catz.categorize(docno, doc);
	}
	
	//final Pattern p = Pattern.compile("([0-9]+)\\.dat");
	int caCnt=0;

	for(String cat: catz.catMembers.keySet()) {
	    

	    /*
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
		SparseDataPoint q = new SparseDataPoint(z.getCoef(docno), dic, z);
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
	    */
	}

	/*
	em.getTransaction().begin();
	for(int docno: catz.nocatMembers) {
	    int cid = 0;
	    recordClass(docno, reader, em, cid);
	}
	em.getTransaction().commit();

	System.out.println("Clustering applied to new docs in all "+ catz.catMembers.size()+" categories; " + 
			   //id0 + " clusters created; " + 
			   caCnt + " articles classified, and " + catz.nocatMembers.size() + " more are unclassifiable");
	*/

    }

    /**
       [vm293@en-myarxiv02 iter_950]$ pwd
       /data/xz337/communities/astro_ph_GA/iter_950
       [vm293@en-myarxiv02 iter_950]$ ls
       comm_affinities.dat  comm_clus_probs.dat  doc_clus.dat  user_comm.dat

       the file comm_clus_probs.dat  has one line for each cluster within
       cat astro_ph_GA, 
    */
    
    /** Reads "P vectors" each of them describes an article cluster
	within a given article category. Each vector is in
	L-dimensional space of word2vec word clusters.

	<p>
	Each line contains L+1 values (the first one being the
	0-based doc cluster ID), e.g.:
	<pre>
	0 8.88931e-05 9.65938e-06 5.11056e-05 8.26743e-06 ...
	</pre>

     */
    Vector<DenseDataPoint> readPVectors(File f, int L) {
	Vector<DenseDataPoint> v = new Vector<DenseDataPoint>();
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
 	String s;
	int linecnt = 0, cnt=0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();	
	    if (s.equals("") || s.startsWith("#")) continue;	    
	    String q[] = s.split("\\s+");
	    if (q==null || q.length != L+1) {
		throw new IOException("Cannot parse line " + linecnt + " in the P file " + f + " : " + s);
	    }
	    int cid = Integer.parseInt(q[1]);
	    double z[] = new double[L];
	    for(j=0; j<L; j++) {
		z[j] = Double.parseDouble(q[j+1]);
	    }
	    DenseDataPoint p = new DenseDataPoint(z);
	    v.setElement(cid, p);
	}
	for(int k=0; k<v.size(); k++) {
	    
	}
	
	return v;
    }
      

}
