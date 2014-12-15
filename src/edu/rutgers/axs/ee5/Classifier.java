package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

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

      /** Classifies new docs, using the stored P files.

	 <p>FIXME: Presently this method reclassifies all docs within
	 the stated time horizon (the one used to measure the average
	 article submission rate for each cluster). This can be
	 finessed by only classifying those docs that have not been
	 previously classified, as well as reclassifying those that
	 had been previously classified based on incomplete data (no
	 article body). We'd need to properly compute mT in this case,
	 making use of pre-recorded values.

	@param scoreDocs list of documents to classify. Contains all recent docs within the standard time horizon
	@param mT output parameter; number of docs per class. 
    */
    static void classifyNewDocs(EntityManager em, IndexReader reader, ScoreDoc[] scoreDocs,
				EE5DocClass.CidMapper cidMap,
				int mT[]
				) throws IOException {

	File f = Files.getWordClusterFile();
	if (!f.canRead()) {
	    throw new IOException("Vocabulary clustering file " + f + " does not exist or is not readable");
	}
	Vocabulary voc = new Vocabulary(f, 0);
	final int L = voc.L;
	Logging.info("Read the vocabulary with " + voc.size() + " multiwords, L=" + L);

	Categorizer catz = new Categorizer(false);

	for(ScoreDoc sd: scoreDocs) {
	    int docno = sd.doc;
	    Document doc = reader.document(docno);
	    catz.categorize(docno, doc);
	}
	
	//final Pattern p = Pattern.compile("([0-9]+)\\.dat");
	int caCnt=0;

	for(String cat: catz.catMembers.keySet()) {
	    
	    System.out.println("cat=" + cat);

	    // Read the new data points to be classified
	    Vector<Integer> vdocno = catz.catMembers.get(cat);

	    Logging.info("Classifying "+vdocno.size()+" recent articles in category " + cat);

	    int cnt=0;
	    Vector<EE5DocClass> assignedClusters=new Vector<EE5DocClass>();
	    f = Files.getDocClusterFile(cat);
	    // FIXME: in production, should just die here.
	    if (!f.exists()) {
		Logging.warning("No cluster vector files provided for category " + cat +"; assigning entire cat to a single cluster");
		
		int localCid = 0;
		EE5DocClass cluster = cidMap.getCluster(cat, localCid);
		for(int docno: vdocno) {
		    cnt++;
		    assignedClusters.add(cluster);
		    Logging.info("article no. " + docno + " assigned to trivial cluster "+ cluster);
		}
	    } else {
		Vector<DenseDataPoint> pvecs = readPVectors(f,L);
		Vector<DenseDataPoint> logPvecs = computeLogP(pvecs);	  
		//System.out.println("Reading vectors...");
		for(int docno: vdocno) {
		    cnt++;
		    DenseDataPoint q = readArticle(docno, L, voc, reader);
		    int localCid = assignArticleToCluster(q, logPvecs, L);
		    EE5DocClass cluster = cidMap.getCluster(cat, localCid);
		    assignedClusters.add(cluster);
		    Logging.info("article no. " + docno + " assigned to cluster "+ cluster);
		}
	    }

	    Logging.info("Recording cluster assignments for "+vdocno.size()+" recent articles in category " + cat);
	    em.getTransaction().begin();
	    int pos=0;
	    for(int docno: vdocno) {
		EE5DocClass cluster = assignedClusters.elementAt(pos);
		int cid = cluster.getId();
		mT[ cid]++;
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
			   caCnt + " articles classified, and " + catz.nocatMembers.size() + " more are unclassifiable");

    }

    /**
       [vm293@en-myarxiv02 iter_950]$ pwd
       /data/xz337/communities/astro_ph_GA/iter_950
       [vm293@en-myarxiv02 iter_950]$ ls
       comm_affinities.dat  comm_clus_probs.dat  doc_clus.dat  user_comm.dat

       the file comm_clus_probs.dat  has one line for each cluster within
       cat astro_ph_GA, 
    */
    
    /** Reads the "P vectors"; each of them describes an article
	cluster within a given article category. Each vector is in
	L-dimensional space of word2vec word clusters.

	<p>
	Each line contains L+1 values (the first one being the
	0-based doc cluster ID), e.g.:
	<pre>
	0 8.88931e-05 9.65938e-06 5.11056e-05 8.26743e-06 ...
	</pre>

	@param L the expected dimension of P vectors. If L=0 is
	supplied, the value is inferred from the file
     */
    static Vector<DenseDataPoint> readPVectors(File f, int L) throws IOException {
	Vector<DenseDataPoint> v = new Vector<DenseDataPoint>();
	Logging.info("Reading P vector file " + f);
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
 	String s;
	int linecnt = 0, cnt=0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();	
	    if (s.equals("") || s.startsWith("#")) continue;	    
	    String q[] = s.split("\\s+");
	    if (L==0) {
		L=q.length-1;
		Logging.info("set L=" + L);
	    }
	    if (q==null || q.length<2 || q.length != L+1) {
		throw new IOException("Cannot parse line " + linecnt + " in the P file " + f + " : " + s);
	    }
	    int cid = Integer.parseInt(q[0]);
	    double z[] = new double[L];
	    for(int j=0; j<L; j++) {
		z[j] = Double.parseDouble(q[j+1]);
	    }
	    DenseDataPoint p = new DenseDataPoint(z);
	    if (cid>=v.size()) v.setSize(cid+1);
	    v.set(cid, p);
	}
	for(int k=0; k<v.size(); k++) {
	    if (v.elementAt(k)==null) {
		throw new IOException("There is no P vector for cid=" + k + " in the P file " + f);
	    }
	}	
	return v;
    }
      
    /** Fields whose content we use to cluster documents */
    final static String fields[] = {
	ArxivFields.TITLE,
	ArxivFields.AUTHORS,
	ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE
    };
    
    /** Reads in all relevant fields of the specified article from
	Lucene, and converts them into a single vector in the
	L-dimensional word2vec word cluster space.
     */
    static DenseDataPoint readArticle(int docno, int L, Vocabulary voc, IndexReader reader) throws IOException {
	Document doc = reader.document(docno);
	boolean missingBody  = false;
	double[] v = new double[L];
	for(String field: fields) {
	    String s = doc.get(field);
	    if (s==null) {
		if (field.equals(ArxivFields.ARTICLE))missingBody=true;
		continue;
	    }
	    voc.textToVector(s, v);	    
	}
	return new DenseDataPoint(v);
    }

    private static Vector<DenseDataPoint> computeLogP(Vector<DenseDataPoint> pvec) {
	Vector<DenseDataPoint> logp = new Vector<DenseDataPoint>(pvec.size());
	for(DenseDataPoint p: pvec) {
	    logp.add( p.log());
	}
	return logp;
    }

    /** Assigns a given article to one of the K document clusters.

	@param article A document represented as a vector in the
	L-dimensional word2vec word cluster space
	@param logPvec The K logarithmized "P vectors", which describe
	the K document clusters
     */
    static int assignArticleToCluster(DenseDataPoint article,
				      Vector<DenseDataPoint> logPvec,
				      int L) {
	double bestloglik = 0;
	int bestk= -1;
	for(int k=0; k<logPvec.size(); k++) {
	    DenseDataPoint logp = logPvec.elementAt(k);
	    double loglik = article.dotProduct(logp);
	    if (bestk<0 || loglik>bestloglik) {
		bestk=k;
		bestloglik=loglik;
	    }
	}
	return bestk;
    }

    /** Sets the class field on the specified article's Article object
	in the database, and persists it. This method should be called
	from inside a transaction. */
    static private void recordClass(int docno, IndexReader reader, EntityManager em, int cid) throws IOException {
	Document doc = reader.document(docno);
	String aid = doc.get(ArxivFields.PAPER);
	Article a = Article.findByAid(  em, aid);
	a.setEe5classId(cid);
	em.persist(a);
    }


}