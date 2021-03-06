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
import edu.rutgers.axs.html.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.recommender.*;
import edu.rutgers.axs.ee4.Categorizer;
import edu.rutgers.axs.ee4.DenseDataPoint;
import edu.rutgers.axs.upload.BackgroundThread;

/** Document classifier. Classifies documents within a major cat using Xiaoting's
    P files.
 */
public class Classifier {

    static final boolean debug = false;

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
	@return Array mT[]; number of docs per class. 
    */
    static int [] classifyDocuments(EntityManager em, IndexReader reader, ScoreDoc[] scoreDocs,
				  EE5DocClass.CidMapper cidMap) throws IOException {

	int maxCid = cidMap.maxId();	
	int mT[] = new int[ maxCid + 1];
	Vocabulary voc = Vocabulary.readVocabulary();
	final int L = voc.L;
	Logging.info("Read the vocabulary with " + voc.size() + " multiwords, L=" + L);

	Categorizer catz = new Categorizer(false);

	for(ScoreDoc sd: scoreDocs) {
	    int docno = sd.doc;
	    Document doc = reader.document(docno);
	    catz.categorize(docno, doc);
	}
	
	int allCnt=0, allReuseCnt=0;

	for(String cat: catz.catMembers.keySet()) {
	    
	    System.out.println("cat=" + cat);

	    // Read the new data points to be classified
	    Vector<Integer> vdocno = catz.catMembers.get(cat);

	    Logging.info("Classifying "+vdocno.size()+" recent articles in category "+cat);

	    int cnt=0, reuseCnt=0;
	    File f = Files.getDocClusterFile(cat);
	    // FIXME: in production, should just die here.
	    if (!f.exists()) {
		Logging.warning("No cluster vector files provided for category " + cat +"; assigning entire cat to a single cluster");
		
		int localCid = 0;
		EE5DocClass cluster = cidMap.getCluster(cat, localCid);
		for(int docno: vdocno) {
		    cnt++;
		    em.getTransaction().begin();
		    int cid = cluster.getId();
		    mT[ cid]++;
		    recordClass(docno, reader, em, cid, false);
		    em.getTransaction().commit();
		    if (debug) Logging.info("article no. " + docno + " assigned to trivial cluster "+ cluster);
		}
	    } else {
		Vector<DenseDataPoint> pvecs = readPVectors(f,L);
		Vector<DenseDataPoint> logPvecs = computeLogP(pvecs);	  
		//System.out.println("Reading vectors...");
		for(int docno: vdocno) {
		    em.getTransaction().begin();

		    Document doc = reader.document(docno);
		    Article a = Article.findAny(em, doc);   
		    int cid =a.getEe5classId();
		    boolean needAssignment = (cid==0) || 
			(a.getEe5missingBody() && doc.get(ArxivFields.ARTICLE)!=null);

		    if (needAssignment) {
			cnt++;
			ArticleDenseDataPoint q=ArticleDenseDataPoint.readArticle(doc, voc);
			int localCid = assignArticleToCluster(q, logPvecs, L);
			EE5DocClass cluster = cidMap.getCluster(cat, localCid);
			if (cluster==null) {
			    throw new IllegalArgumentException("No cluster entry found in the database for cat="+cat+", localCid=" + localCid + ". Need to update cat list and re-init clusters?");
			}
			cid = cluster.getId();
			a.setEe5classId(cid);
			a.setEe5missingBody(q.missingBody);
			em.persist(a);
			Logging.info("article no. " + docno + " assigned to cluster "+ cluster);
		    } else {
			reuseCnt++;
		    }
		    mT[cid]++;
		    em.getTransaction().commit();
		}
	    }

	    allCnt += cnt;
	    allReuseCnt += reuseCnt;
	    Logging.info("Recorded new cluster assignments for "+cnt + " and kept " + reuseCnt + " old assignments for recent articles in category " + cat);

	}

	em.getTransaction().begin();
	for(int docno: catz.nocatMembers) {
	    int cid = 0;
	    recordClass(docno, reader, em, cid, false);
	}
	em.getTransaction().commit();
	
	System.out.println("Cluster assignment applied to new docs in all "+ catz.catMembers.size()+" categories. " + 
			   allCnt + " articles assigned to clusters, "+allReuseCnt+" articles kept with old assignments, and " + catz.nocatMembers.size() + " more are unclassifiable");
	return mT;
	
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
	@param doWrite If true, this method record the computed category
	assignments in the database.

//	@return Array mT[]: mT[i] will be set to the number of docs in the i-th cluster found in scoreDocs
     */
    static CategoryAssignmentReport
classifyNewDocsCategoryBlind(EntityManager em, IndexReader reader, ScoreDoc[] scoreDocs,
			     EE5DocClass.CidMapper cidMap,  boolean doWrite, 
			     BackgroundThread thread) throws IOException {
	int maxCid = cidMap.maxId();	
	int mT[] = new int[ maxCid + 1];

	Vocabulary voc = Vocabulary.readVocabulary();
	final int L = voc.L;
	Logging.info("Read the vocabulary with " + voc.size() + " multiwords, L=" + L);
	if (thread!=null) thread.pin.setK(30);

	// an array, possibly with gaps, of logarithmized P-vecs for
	// all clusters for which such vectors have been supplied to us
	Vector<DenseDataPoint> cid2logPvec=new Vector<DenseDataPoint>(maxCid+1);
	cid2logPvec.setSize(maxCid+1);

	Vector<String> allCats = Categories.listAllStorableCats();
	Vector<String> usedCats = new Vector<String>();

	ProgressIndicator pin = (thread==null)? null:
	    new SectionProgressIndicator(thread.pin, 30, 50, allCats.size());

	int cnt=0;
	for(String cat: allCats) {
	    //if (thread!=null) thread.progress("cat=" + cat);
	    
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
	    cnt++;
	    //Logging.info("Loaded " +  logPvecs.size() + " vectors for cat=" + cat +"; cnt=" + cnt);
	    if (pin!=null) pin.setK(cnt);
	}
	if (thread!=null) thread.progress("Has read in cluster descriptions");

	// Read the new data points to be classified
	Vector<Integer> vdocno = new Vector<Integer>();
	for(ScoreDoc sd: scoreDocs) vdocno.add(sd.doc);

	pin = (thread==null)? null:
	    new SectionProgressIndicator(thread.pin, 50, 100, vdocno.size());

	Logging.info("Classifying "+vdocno.size()+" documents in a category-blind fashion, among "+ nonNullCnt(cid2logPvec) + " clusters in " +  usedCats.size() + " categories");

	cnt=0;
	Vector<EE5DocClass> assignedClusters=new Vector<EE5DocClass>();
	Vector<Boolean> missingBody =new Vector<Boolean>();
	Vector<String> names = new Vector<String>();
	int anyCatMatchCnt = 0; // assigned cat matches primary or any secondary cat
	for(int docno: vdocno) {
	    cnt++;
	    Document doc = reader.document(docno);

	    String name = doc.get(ArxivFields.PAPER);
	    if (name==null) name=doc.get(ArxivFields.UPLOAD_FILE);

	    ArticleDenseDataPoint q = ArticleDenseDataPoint.readArticle(doc,  voc);
	    int cid = assignArticleToCluster(q, cid2logPvec, L);
	    EE5DocClass cluster = cidMap.id2dc.get(cid);
	    assignedClusters.add(cluster);
	    missingBody.add(q.missingBody);
	    names.add(name);

	    CatInfo ci = new CatInfo(doc.get(ArxivFields.CATEGORY), false);
	    if (ci.match(cluster.getCategory()))  anyCatMatchCnt++;
	    String msg="Document no. "+docno+" ("+name+") assigned to cluster "+cluster+", cid="+cid;
	    Logging.info(msg);
	    if (thread!=null) thread.progress(msg);
	    if (pin != null) pin.setK(cnt);
	}

	countClusterAssignments(assignedClusters, mT);
	if (doWrite) {
	    Logging.info("Recording cluster assignments for "+vdocno.size()+" documents");
	    int caCnt = recordClusterAssignments(em, reader, vdocno, assignedClusters, missingBody, mT);
	    String msg="Category-blind clustering applied to " + vdocno.size() + " documents; assignments have been recorded for " + caCnt + " documents";	    
	    System.out.println(msg);
	    if (thread!=null) thread.progress(msg);
	}

	CategoryAssignmentReport report = new 
	    CategoryAssignmentReport(vdocno, assignedClusters,names, mT, anyCatMatchCnt);
	return report;
    }

    /** An auxiliary class used by the category-blind classifier to
	provide a concise report about categories to which documents
	have been assigned.
     */
    static public class CategoryAssignmentReport extends HashMap<String, Vector<String>> {
	
	/** A silly way to package information about per-cluster stats. This
 	    done simply so that a classifier method would have a single object
	    as its return value.
	 */
	final int mT[];

	/** For how many documents does the assigned cat matches any
	    category on the document's ArXiv cat list (either primary
	    or any secondary)?
	 */
	int anyCatMatchCnt;

	CategoryAssignmentReport(Vector<Integer> vdocno, Vector<EE5DocClass> assignedClusters, Vector<String> names, int[] _mT, int _anyCatMatchCnt) {
	    mT = _mT;
	    anyCatMatchCnt = _anyCatMatchCnt;
	    for(int i=0; i<vdocno.size(); i++) {
		EE5DocClass cluster = assignedClusters.elementAt(i);
		String cat = cluster.getCategory();
		Vector<String> v = get(cat);
		if (v==null) put(cat, v=new Vector<String>());
		v.add(names.elementAt(i));
	    }
	}

	public String toString() {
	    String[] cats = (String[])keySet().toArray(new String[0]);
	    Arrays.sort(cats);
	    StringBuffer b  = new StringBuffer();
	    for(String cat: cats) {
		b.append("[" + cat + "(" +  why(cat) + ")] ");
	    }
	    return b.toString();
	}

	/** Lists the names of the documents that caused the specified category
	    to be listed */
	public String why(String cat) {
	    StringBuffer b = new StringBuffer();
	    for(String name: get(cat)) {
		if (b.length()>0) b.append( " ");
		b.append(name);
	    }
	    return b.toString();	    
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

    
   private static int recordClusterAssignments(EntityManager em, IndexReader reader, Vector<Integer> vdocno, Vector<EE5DocClass> assignedClusters, 
					       Vector<Boolean> missingBody, int mT[])  throws IOException {
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
	    recordClass(docno, reader, em, cid, missingBody.elementAt(pos).booleanValue());
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

	@param f The data file which contains the P vectors for one category
	
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
		//Logging.info("set L=" + L);
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
      
    private static Vector<DenseDataPoint> computeLogP(Vector<DenseDataPoint> pvec) {
	Vector<DenseDataPoint> logp = new Vector<DenseDataPoint>(pvec.size());
	//int i=0;
	for(DenseDataPoint p: pvec) {
	    logp.add( p.log());
	    //Logging.info("|p|^2=" + p.norm2() +", |logP|^2="+logp.elementAt(i++).norm2());
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
	msg.append("|a|^2=" + article.norm2() +"; ");
	for(int k=0; k<logPvec.size(); k++) {
	    DenseDataPoint logp = logPvec.elementAt(k);
	    if (logp == null) continue; // a gap
	    double loglik = article.dotProduct(logp);
	    //	    msg.append("(ll("+k+")="+loglik+")");
	    if (bestk<0 || loglik>bestloglik) {
		bestk=k;
		bestloglik=loglik;
	    }
	}
	//	Logging.info(msg.toString());
	return bestk;
    }

    /** Sets the class field on the specified article's Article object
	in the database, and persists it. This method should be called
	from inside a transaction. */
    static private void recordClass(int docno, IndexReader reader, EntityManager em, int cid, boolean missingBody) throws IOException {
	Document doc = reader.document(docno);
	if (doc == null) throw new IllegalArgumentException("No Lucene document found for docno=" + docno);
	Article a = Article.findAny(em, doc);
	if (a==null)  throw new IllegalArgumentException("No database record found for document " + docno + "/" + Article.shortName(doc));
	a.setEe5classId(cid);   // null
	a.setEe5missingBody(missingBody);
	em.persist(a);
    }

}