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
				int mT[]) throws IOException {

	Vocabulary voc = readVocabulary();
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
	    File f = Files.getDocClusterFile(cat);
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

	    countClusterAssignments(assignedClusters, mT);
	    caCnt += recordClusterAssignments(em,reader,vdocno,assignedClusters,mT);   
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

    /** Similar to classifyNewDocs(), this method processes documents
	in a "category-blind" fashion. That is, it does not make use
	of the category field in Lucene records, and thus can be used
	with documents that don't have category information. (This is
	the case with user-uploaded docs). Therefore, instead of
	assigning each document to a cluster within a known category,
	it has to choose the best cluster out of all clusters in all
	categories.

	@param scoreDocs list of documents to classify
	@param mT output parameter: mT[i] will be set to the 
	number of docs in the i-th cluster found in scoreDocs
	@param nowrite If true, this method does not store the new 
     */
    static void classifyNewDocsCategoryBlind(EntityManager em, IndexReader reader, ScoreDoc[] scoreDocs,
					     EE5DocClass.CidMapper cidMap,
					     int mT[], boolean nowrite) throws IOException {

	Vocabulary voc = readVocabulary();
	final int L = voc.L;
	Logging.info("Read the vocabulary with " + voc.size() + " multiwords, L=" + L);

	int maxCid = cidMap.maxId();
	// an array, possibly with gaps, of logarithmized P-vecs for
	// all clusters for which such vectors have been supplied to us
	Vector<DenseDataPoint> cid2logPvec=new Vector<DenseDataPoint>(maxCid+1);
	cid2logPvec.setSize(maxCid+1);

	Vector<String> allCats = Categories.listAllStorableCats();
	Vector<String> usedCats = new Vector<String>();
	for(String cat: allCats) {
	    File f = Files.getDocClusterFile(cat);
	    if (!f.exists()) {
		Logging.warning("No cluster vector files provided for category " + cat +"; exclding this category from assignment candidate list");
		continue;
	    }
	    usedCats.add(cat);
	    Vector<DenseDataPoint> pvecs = readPVectors(f,L);
	    Vector<DenseDataPoint> logPvecs = computeLogP(pvecs);	  
	    int i=0;
	    for(DenseDataPoint x: logPvecs) {		
		int cid = cidMap.getCluster(cat, i++).getId();
		cid2logPvec.set(cid,x);  // ??
	    }
	}

	// Read the new data points to be classified
	Vector<Integer> vdocno = new Vector<Integer>();
	for(ScoreDoc sd: scoreDocs) vdocno.add(sd.doc);

	Logging.info("Classifying "+vdocno.size()+" documents in a category-blind fashion, among "+ nonNullCnt(cid2logPvec) + " clusters in " +  usedCats.size() + " categories");

	int cnt=0;
	Vector<EE5DocClass> assignedClusters=new Vector<EE5DocClass>();
	for(int docno: vdocno) {
	    cnt++;
	    DenseDataPoint q = readArticle(docno, L, voc, reader);
	    int cid = assignArticleToCluster(q, cid2logPvec, L);
	    EE5DocClass cluster = cidMap.id2dc.get(cid);
	    assignedClusters.add(cluster);
	    Logging.info("Document no. "+docno+" assigned to cluster "+cluster+", cid="+cid);
	}

	countClusterAssignments(assignedClusters, mT);
	if (!nowrite) {
	    Logging.info("Recording cluster assignments for "+vdocno.size()+" documents");

	    int caCnt = recordClusterAssignments(em, reader, vdocno, assignedClusters, mT);

	    System.out.println("Category-blind clustering applied to " + vdocno.size() + " documents; assignments have been recorded for " + caCnt + " documents");
	}

    }

    private static int nonNullCnt(Vector v) {
	int cnt=0;
	for(Object q: v) {
	    if (q!=null) cnt++;
	}
	return cnt;
    }

    private static void countClusterAssignments(Vector<EE5DocClass> assignedClusters, int mT[])  throws IOException {
      for(EE5DocClass cluster: assignedClusters) {
	  if (cluster==null) {
	      Logging.warning("There was no cluster assignment for one of the articles! Need to update cat list and re-init clusters?");
	      continue;
	  }
	  int cid = cluster.getId();
	  mT[ cid]++;
      }
   }

   private static int recordClusterAssignments(EntityManager em, IndexReader reader, Vector<Integer> vdocno, Vector<EE5DocClass> assignedClusters, int mT[])  throws IOException {
 	em.getTransaction().begin();
	int pos=0;
	int caCnt=0;
	for(int docno: vdocno) {
	    EE5DocClass cluster = assignedClusters.elementAt(pos);
	    if (cluster==null) {
		Logging.warning("There was no cluster assignment for article no. " +docno+"! Need to update cat list and re-init clusters?");
		continue;
	    }
	    int cid = cluster.getId();
	    mT[ cid]++;
	    recordClass(docno, reader, em, cid);
	    pos ++;
	    caCnt++;
	}
	em.getTransaction().commit();
	return caCnt;
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
	//Logging.info("Reading P vector file " + f);
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
	return readArticle(doc, L, voc);
    }

    static DenseDataPoint readArticle(Document doc, int L, Vocabulary voc) throws IOException {

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
	The clusters don't have to be from the same document 
	category.

	@param article A document represented as a vector in the
	L-dimensional word2vec word cluster space
	@param logPvec Contains the K logarithmized "P vectors", which
	describe the K document clusters. The array can contain gaps
	(null values), indicating that we do not have a P vector for a
	particular cluster. (This would only happen when we are
	choosing the optimal cluster over multiple categories, but
	some of these categories do not have a clustering scheme supplied.)

	@return index into the array logPvec, indicating the position of 
	the best cluster. If logPvec is empty, or only contains nulls, -1
	will be returned.
     */
    static int assignArticleToCluster(DenseDataPoint article,
				      Vector<DenseDataPoint> logPvec,
				      int L) {
	double bestloglik = 0;
	int bestk= -1;
	StringBuffer msg=new StringBuffer();
	for(int k=0; k<logPvec.size(); k++) {
	    DenseDataPoint logp = logPvec.elementAt(k);
	    if (logp == null) continue; // a gap
	    double loglik = article.dotProduct(logp);
	    //msg.append("(ll("+k+")="+loglik+")");
	    if (bestk<0 || loglik>bestloglik) {
		bestk=k;
		bestloglik=loglik;
	    }
	}
	//Logging.info(msg.toString());
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

    static Vocabulary readVocabulary()  throws IOException{
	File f = Files.getWordClusterFile();
	if (!f.canRead()) {
	    throw new IOException("Vocabulary clustering file " + f + " does not exist or is not readable");
	}
	return new Vocabulary(f, 0);
    }

}