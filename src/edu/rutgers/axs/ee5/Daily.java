package edu.rutgers.axs.ee5;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.recommender.*;
import edu.rutgers.axs.ee4.DenseDataPoint;


/** Daily updates for the underlying data structure, and
    recommendation generation, for Peter Frazier's Exploration Engine
    ver. 5 (EE5).
 */
public class Daily {

    static private final int maxlen = 1000000;

    private static void reportEx(Exception ex) {
	Logging.error(ex.toString());
	System.out.println(ex);
	ex.printStackTrace(System.out);
    }

   /** The main "daily updates" method. Calls all other methods. Operations involved 
	are:
	<ul>
	<li>Classify (assign to clusters) recent articles
	<li>Compute and record average daily submission rates for all doc clusters
	<li>Create suggestion lists for all EE5 users
	</ul>

	@param onlyUser If not null, only generate sug list for this user
	(rather than for every user in program EE5)

     */
    static void updates(String onlyUser) throws IOException {

	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);
	EntityManager em  = Main.getEM();
	    
	final int days = EE5DocClass.TIME_HORIZON_DAY;
	Date since = SearchResults.daysAgo( days );

	// list document clusters
	EE5DocClass.CidMapper cidMap = new EE5DocClass.CidMapper(em);

	// assign recent ArXiv articles to clusters
	ScoreDoc[] sd = getRecentArticles( em, searcher, since);
	// classify all recent docs	
	int mT[] = Classifier.classifyDocuments(em, reader, sd,cidMap);
	recordSubmissionRates(em, cidMap, mT);

	final User.Program program = User.Program.EE5;

	if (onlyUser!=null) {
	    User user = User.findByName( em, onlyUser);
	    if (user==null) throw new IllegalArgumentException("User " + onlyUser + " does not exist");
	    if (!user.getProgram().equals(program)) throw new IllegalArgumentException("User " + onlyUser + " is not enrolled in program " + program);
	    try {
		makeEE5Sug(em, searcher, since, cidMap.id2dc, user);
	    } catch(Exception ex) {
		reportEx(ex);
	    }
	} else {
	    List<Integer> lu = User.selectByProgram( em, User.Program.EE5);
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    makeEE5Sug(em, searcher, since, cidMap.id2dc, user);
		} catch(Exception ex) {
		    reportEx(ex);
		}
	    }
	}
	em.close();
    }

    /** A simplified version of the updates() method, to be used
	with the command-line harness, with a simulated user.
    */
    public static DataFile simulatedUserUpdates(EntityManager em, IndexSearcher searcher, User user) throws IOException {
	    
	final int days = EE5DocClass.TIME_HORIZON_DAY;
	Date since = SearchResults.daysAgo( days );

	// list document clusters
	EE5DocClass.CidMapper cidMap = new EE5DocClass.CidMapper(em);

	final User.Program program = User.Program.EE5;
	if (!user.getProgram().equals(program)) throw new IllegalArgumentException("User " + user + " is not enrolled in program " + program);
	try {
	    return makeEE5Sug(em, searcher, since, cidMap.id2dc, user);
	} catch(Exception ex) {
	    reportEx(ex);
	    return null;
	}
    }

    /** Assigns specified ArXiv articles to clusters. This method
	can be used, for example, if we decided to classify 
	just a few docs that have not been classified before.
    */
    private static void classifySomeDocs(String[] aids) throws IOException {

	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);
	EntityManager em  = Main.getEM();
	    
	// list document clusters
	EE5DocClass.CidMapper cidMap = new EE5DocClass.CidMapper(em);

	ScoreDoc[] sd = new ScoreDoc[aids.length];
	for(int i=0; i<aids.length; i++) {
	    int docno = Common.find(searcher, aids[i]);
	    sd[i] = new ScoreDoc(docno, 0);
	}
	
	// classify all recent docs	
	int mT[] = Classifier.classifyDocuments(em, reader, sd,cidMap);
	em.close();
    }



    /** Retrieve a specified set of documents stored in the Lucene
	data store, and assign each document to the appropriate cluster.

	@param since Retrieve all ArXiv articles submitted since this date.
     */
    private static int[] updateClusters(EntityManager em, IndexSearcher searcher,
					EE5DocClass.CidMapper cidMap, Date since) throws IOException {
	ScoreDoc[] sd = getRecentArticles( em, searcher, since);
	// classify all recent docs
	int mT[] = Classifier.classifyDocuments(em, searcher.getIndexReader(), sd,cidMap);
	return mT;
    }

    /** Retrieve a specified set of documents stored in the Lucene
	data store.

	@param since Retrieve all ArXiv articles submitted since this date.
     */
    private static ScoreDoc[] getRecentArticles(EntityManager em, IndexSearcher searcher, Date since) throws IOException {
	org.apache.lucene.search.Query q= 
	    Queries.andQuery(Queries.mkSinceDateQuery(since),
			     Queries.hasAidQuery());

	TopDocs top = searcher.search(q, maxlen);

	System.out.println("Looking back to " + since + "; found " + 
			   top.scoreDocs.length + " papers");
	
	return top.scoreDocs;
    }

    /** Updates the per-cluster submission rate recorded in the SQL database.

	@param mT Recently-compute per-cluster submission totals over
	the last TIME_HORIZON_DAY days. (The correct range is
	important, so that we can compute the average daily rate correctly)
    */
    static void recordSubmissionRates(EntityManager em, final EE5DocClass.CidMapper cidMap, int mT[])
	throws IOException
    {
	em.getTransaction().begin();
	for(EE5DocClass c: cidMap.id2dc.values()) {
	    int cid = c.getId();
	    double lx = (double)mT[cid]/(double)EE5DocClass.TIME_HORIZON_DAY;
	    c.setL( lx ); // average *daily* number, as per the specs
	    em.persist(c);
	}
	em.getTransaction().commit();	
    }


    /** Prepares a suggestion list for one user and saves it. This
     method does not conduct any document classification itself; it
     relies only on already-classified documents. Therefore, one
     should use it either in the daily update script *after*
     all judged docs have been classified, or in a new-user scenario,
     when there is no judgment history anyway. */
    static DataFile makeEE5Sug(EntityManager em,  IndexSearcher searcher, Date since, 
				       HashMap<Integer,EE5DocClass> id2dc,
				       //EE5DocClass.CidMapper cidMap, 
				       User user) throws IOException {
	int uid = user.getId();
	EE5User ee5u = EE5User.getAlways( em, uid, true);
	int lai = updateUserVote(em, id2dc, user, ee5u);
	boolean nofile = false;
	return updateSugList(em, searcher, since, id2dc, user, ee5u, lai, nofile);
    }

    /** This version is invoked from the web server, for newly created users
     */
   public static DataFile makeEE5SugForNewUser(EntityManager em,  IndexSearcher searcher,  User user) throws IOException {

	final int days = EE5DocClass.TIME_HORIZON_DAY; 
	Date since = SearchResults.daysAgo( days );
	// list classes
	HashMap<Integer,EE5DocClass> id2dc = (new EE5DocClass.CidMapper(em)).id2dc;

	int uid = user.getId();
	EE5User ee5u = EE5User.getAlways( em, uid, true);
	int lai = updateUserVote(em, id2dc, user, ee5u);
	boolean nofile = true;
	return updateSugList(em, searcher, since, id2dc, user, ee5u, lai, nofile);
    }

  
    /** How important is this action for the user's "opinion" on the 
	article? <br>
	0 - ignore, +-1 - med, +-2 - high */
    private static int actionPriority(Action.Op op) {
	if (op==Action.Op.INTERESTING_AND_NEW || op.isToFolder() ||
	    op==Action.Op.UPLOAD) return 2;
	else if (op==Action.Op.DONT_SHOW_AGAIN ||
		 op==Action.Op.REMOVE_FROM_MY_FOLDER) return -2;
	else if (op==Action.Op.EXPAND_ABSTRACT||
		 op==Action.Op.VIEW_ABSTRACT||
		 op==Action.Op.VIEW_FORMATS||
		 op==Action.Op.VIEW_PDF||
		 op==Action.Op.VIEW_PS||
		 op==Action.Op.VIEW_HTML||
		 op==Action.Op.VIEW_OTHER) return 1;
	else return 0;
    }

    /** Does action a override action b? Returns the most "relevant" one 
	(higher-priority, or more recent) */
    private static Action mostRelevant(Action a, Action b) {	
	int aval = actionPriority(a.getOp()), bval=actionPriority(b.getOp());
	if (Math.abs(aval) > Math.abs(bval)) return a;
	else if (Math.abs(bval) > Math.abs(aval)) return b;
	else return a.getTime().after(b.getTime()) ? a : b;
    }

    /** Should this user action be taken into consideration? Generally,
	only article views from the EE5 suggestion list pages are taken
	into consideration, as well as user uploads. */
    private static boolean acceptAction(EntityManager em, Action a) {
	if (actionPriority(a.getOp())==0) return false;

	// Check source
	Action.Source src = a.getSrc();
	if (src == Action.Source.EMAIL_EE5 ||
	    src == Action.Source.MAIN_EE5) {
	    // include always
	    System.out.println("Learning day action " + a + " accepted");
	    return true;
	} else if(src == Action.Source.EMAIL_EE5_MIX||
		  src == Action.Source.MAIN_EE5_MIX ) {
	    // include if the URL originated from List A
	    long plid = a.getPresentedListId();
	    PresentedList pl =(PresentedList)em.find(PresentedList.class, plid);
	    Article art = a.getArticle();
	    if (pl==null || art==null) return false;
	    PresentedListEntry ple =  pl.getDocByAid(art.getAid());
	    if (ple!=null && ple.getFromA()) 
		System.out.println("Mixed list action " + a + " accepted");
	    else
		System.out.println("Mixed list action " + a + " ignored");

	    return ple!=null && ple.getFromA();
	} else if (a.getOp()==Action.Op.UPLOAD) {
	    System.out.println("Upload action " + a + " accepted");
	    return true;
	} else {
	    System.out.println("Action " + a + " ignored");
	    return false;
	}
    }

    /** Recomputes alpha[z] and beta[z] for each class z for a
	specified user.  For each (user,page) pair, only the most
	recent action (of one of the requisite types) is considered as
	the user's current "vote" on that page.

	<P>This method is identical to the one in ee4.Daily.

	@return the last (most recent) action id used in the update
    */
    static int updateUserVote(EntityManager em, HashMap<Integer,EE5DocClass> id2dc, User u, EE5User ee5u) throws IOException {

	System.out.println("updateUserVote(user=" + u + ")");
	Set<Action> sa = u.getActions();
	long lai=0;
	// maps our Article.id to the latest acceptable Action on that article
	HashMap<Integer, Action> lastActions = new HashMap<Integer,Action>();
	for( Action a: sa) {
	    if (!acceptAction( em, a)) continue; 

	    Article article = a.getArticle();
	    if (article==null) continue;
	    Integer key = new Integer( article.getId());
	    Action z  = lastActions.get(key);
	    if (z==null || mostRelevant(z,a)==a) {
		lastActions.put(key,a);
		if (a.getId()>lai) lai=a.getId();
	    }
	}


	int maxCid = 0;
	for(int k: id2dc.keySet()) { if (k>maxCid) maxCid=k; }
	
	double[] alpha = new double[maxCid+1], beta= new double[maxCid+1];
	
	em.getTransaction().begin();

	System.out.println("--- Currently relevant actions for user " + u.getUser_name());
	for(Action a: lastActions.values()) {
	    boolean plus= actionPriority(a.getOp())>0;
	    int cid = a.getArticle().getEe5classId(); 	    
	    if (plus) {
		alpha[ cid] ++;
	    } else {
		beta[ cid] ++;
	    }
	    System.out.println((plus? "PLUS " : "MINUS ") +" page=" + 
			       a.describeArticle()+ ", " +a.getOp()+", class=" +
			       cid);
	}
	
	HashSet<EE5Uci> uci=new HashSet<EE5Uci>();
	
	for(int cid: id2dc.keySet()) { 
	    EE5DocClass dc =  id2dc.get(cid);
	    EE5Uci w = new EE5Uci(cid,dc.getAlpha0()+alpha[cid], dc.getBeta0()+beta[cid]);
	    uci.add(w);
	}
	
	ee5u.setUci(uci);
	em.persist(ee5u);
	em.getTransaction().commit();	
	return (int)lai;
    }


    /** Max length of the sugg list, as per Peter Frazier, 2013-02-13 */
    static final int MAX_SUG = 100; 

 
    /** Generates a current suggestion list for a specified user. 
	@param nofile This is set to true when we're running inside a
	web server, and aren't allowed to write disk files (due to
	file ownership/permission issues). This flag is false in the
	"normal operation" of this tool, i.e. in the nightly script.
     */
    private static DataFile updateSugList(EntityManager em,  IndexSearcher searcher, Date since, HashMap<Integer,EE5DocClass> id2dc, User u, EE5User ee5u, int lai, final boolean nofile)
	throws IOException
     {
	 Logging.info("Preparing EE5 suggestions for user " + u);
	 HashMap<Integer,EE5Uci> h = ee5u.getUciAsHashMap();

	 String[] cats = u.getCats().toArray(new String[0]);
	 SubjectSearchResults sr = new SubjectSearchResults(searcher, cats, since, maxlen);
	 // Order by recency (most recent first). Later, this will be
	 // used as the secondary key.
	 sr.reorderCatSearchResults(searcher.getIndexReader(), null);
	 //	 Logging.info("Daily.USL: |sr|=" + sr.scoreDocs.length);
	 // random reorder within same-day groups
	 //sr.randomlyBreakTies();

	 Vector<ArxivScoreDoc> results= new Vector<ArxivScoreDoc>();

	 CStarLookup csLookup = new CStarLookup(1000, 10);

	 int maxCid = EE5DocClass.maxId(em);
	 // keeping track of how many docs from each cluster we have already 
	 // processed
	 int rankInCluster[] = new int[maxCid+1];

	 HashMap<Integer, String> commentsHash= new HashMap<Integer, String>();

	 for(ScoreDoc sd: sr.scoreDocs) {
	     Document doc = searcher.doc(sd.doc);
	     String aid = doc.get(ArxivFields.PAPER);
	     Article a = Article.getArticleAlways(em,aid);
	     final int cid = a.getEe5classId();
	     if (cid == 0) continue; // skip unclassified docs
	     EE5DocClass c = id2dc.get(new Integer(cid));
	     EE5Uci ee5uci = h.get(new Integer(cid));
	     if (ee5uci == null) throw new IllegalArgumentException("No EE5Uci srecord is available for cid=" + cid);
	     double alpha=ee5uci.getAlpha(),  beta=ee5uci.getBeta(); 
	    
	     int rank = ++rankInCluster[cid]; // 1-based rank
	     double gamma = CStarLookup.onlyGamma;

	     double lx = c.getL(); // average *daily* number
	     double xi = 1.0 / (1.0 + lx);
	     double cstar = csLookup.lookup(gamma, xi, alpha, beta, rank);

	     final boolean admit = true;
	     if (admit) {
		 results.add(new ArxivScoreDoc(sd).setScore(cstar));
		 String cmt="Cluster "+cid+", xi=" + (float)xi +", u=" +rank+", cstar=" + (float)cstar;
		 commentsHash.put(sd.doc, cmt);
		 //  Logging.info("Daily.USL: added, cstar=" + cstar);
	     } else {
		 //  Logging.info("Daily.USL: not added, cstar=" + cstar);
	     }
	     //	     if (results.size() >=  MAX_SUG) {
	     //		 Logging.info("Daily.USL: truncating sug list at " + MAX_SUG);
	     //	 break;
	     //}
	 }

	 ArxivScoreDoc[] topResults = ArxivScoreDoc.getTopResults(results, MAX_SUG);

	 String comments[] = new  String[topResults.length];
	 for(int i=0; i<topResults.length; i++) {
	     comments[i] = commentsHash.get(topResults[i].doc);
	 }

	 em.getTransaction().begin();	
	 DataFile outputFile=new DataFile(u.getUser_name(), 0, DataFile.Type.EE5_SUGGESTIONS);
	 outputFile.setSince(since);
	 outputFile.setLastActionId(lai);
	 Vector<ArticleEntry> entries = 
	     ArxivScoreDoc.packageEntries(topResults, comments, searcher.getIndexReader());
	
	 Logging.info("Daily.USL: |entries|=" + entries.size());

	 if (nofile) { 
	     // we can't write a file now, due to file permissions reasons
	     outputFile.setThisFile(null);
	 } else {
	     ArticleEntry.save(entries, outputFile.getFile());
	 }
	 outputFile.fillArticleList(entries,  em);

	 em.persist(outputFile);
	 
	 // FIXME: could use some kind of "day 1" rule
	 boolean isTrivial = false; 
	 if (isTrivial) {
	     u.forceNewDay(User.Day.LEARN);
	 } else {
	     u.forceNewDay();
	 }
	 em.persist(u);

	 em.getTransaction().commit();	
	 return outputFile;
     }

    private static void randomlyBreakTies(ArticleEntry[] entries) {
	int k=0;
	while(k < entries.length) {
	    int j = k;
	    while(j<entries.length && entries[j].score==entries[k].score){
		j++;
	    }
	    if (j>k+1) {
		System.out.println("Reordering group ["+k+":"+(j-1)+"], score=" +entries[k].score);
		int[] p = Util.randomPermutation(j-k);
		ArticleEntry[] tmp = new ArticleEntry[j-k];
		for(int i=0; i<p.length; i++) {
		    tmp[i] = entries[k + p[i]];
		}
		for(int i=0; i<p.length; i++) {
		    entries[k + i] = tmp[i];
		}	
	    }
	    k = j;
	}
    }

    /**
       @param gamma 0.99
     */
    static double cstar(int u, double alphaX, double betaX, 
		 double gamma, double xiX,
		 double alpha0, double beta0) {
	return 0;
    }

   /** A one-off method, to create and initialize an entry in the
       EE5DocClass table for each document class. This method needs to
       be invoked every time we switch to a different clustering
       scheme, since the number of clusters in each category may
       become different, and so will the global cluster numbering
       scheme.

	<p> To find the the number of clusters per category, this
	method looks at the stored P-vectors. In the absence of such
	vectors for a particular category, we create a singleton
	(trivial) cluster for that category.

	<p>For each cluster, we set the initial alpha=1, beta=19, as
	per ZXT, 2013-02-21

   */
    private static void initClusters(EntityManager em) throws IOException {

	int oldCnt = EE5DocClass.count(em);
	if (oldCnt > 0) {
	    throw new IllegalArgumentException("Cannot run initialization, because some objects ("+oldCnt +" of them) already exist in the table EE5DocClass. Please delete that table, and run 'init' again!");
	}
	
	Vector<String> allCats = Categories.listAllStorableCats();
	HashSet<String> allCatSet = new HashSet<String>();
	allCatSet.addAll(allCats);

	String fileCats[] = Files.listCats();
	System.out.println("Found cluster dirs for " + fileCats.length + " categories");
	
	int errcnt=0;
	for(String cat: fileCats) {
	    if (!allCatSet.contains(cat)) {
		Logging.error("There is a cluster definition directory for unknown category " + cat);
		errcnt ++;
	    }
	}

	if (errcnt > 0) {
	    throw new IllegalArgumentException("There are " + errcnt + " cluster definition files in "+Files.getDocClusterDir()+" referring to otherwise unknown categories (see messages above). This won't do! Please make sure that the directory names there match cat names in sql/Categories.java!");
	}
	
	// default number of clusters per cat is 1 (a trivial cluster)
	HashMap<String, Integer> clusterCnt=new HashMap<String, Integer>();
	for(String cat: allCats) {
	    clusterCnt.put(cat, new Integer(1));
	}

	for(String cat: fileCats) {
	    File f = Files.getDocClusterFile(cat);
	    if (!f.exists()) {
		throw new IllegalArgumentException("No cluster file "+f+" exists! Empty directory? Just delete it!");
		//Logging.warning("No cluster file "+f+" exists! Creating a single cluster for category " + cat);
		//locCnt = 1;
	    } else {
		Vector<DenseDataPoint> pvecs = Classifier.readPVectors(f,0);
		int locCnt = pvecs.size();
		clusterCnt.put(cat, new Integer(locCnt));
	    }
	}

	em.getTransaction().begin();
	int crtCnt=0;
	int cid=0;
	String cats[] = (String[])allCats.toArray(new String[allCats.size()]);
	Arrays.sort(cats);
	
	for(String cat: cats) {
	    int m = clusterCnt.get(cat).intValue();
	    for(int j=0; j<m; j++) {
		cid++;
		EE5DocClass c = new EE5DocClass();
		c.setId(cid);
		c.setCategory(cat);
		c.setLocalCid(j);
		c.setAlpha0(1.0);
		c.setBeta0(19.0);
		em.persist(c);
		crtCnt++;
	    }
	}
	em.getTransaction().commit();		
	Logging.info("Created "+crtCnt+" cluster objects");
	if (fileCats.length<cats.length) {
	    Logging.warning("Out of " + cats.length + " categories, only "+
			     fileCats.length + " cats had clustering data provided; for the other " + (cats.length-fileCats.length) + " cats, trivial clusters have been created");
	} else {
	    Logging.info("All " + cats.length + " categories had clustering data provided");
	}
    }

    /** Deletes all EE5 clustering data and document-class assignments
	from the database. This should be done whenever a new clustering
	scheme is deployed, before cluster assignment is redone on all documents
     */
    private static void deleteAll()  throws IOException {
	EntityManager em  = Main.getEM();
	String queries[] = {
	    "delete from EE5DocClass c",
	    "update Article a set a.ee5classId=0, a.ee5missingBody=false"
	};
	for(String query: queries) {
	    javax.persistence.Query q = em.createQuery(query);
	    Logging.info("Query: " + query);
	    em.getTransaction().begin();
	    int n= q.executeUpdate();
	    em.getTransaction().commit();
	    Logging.info("" + n + " rows updated");
	}
	em.close();
    }



    /** A one-off procedure, which needs to be invoked after a new
	clustering scheme has been installed. It will create
	EE5DocClass objects in the database for each cluster in the
	clustering scheme. After that, it will re-run cluster
	assignments on all documents that are potentially useful for
	EE5 recommendation generation. This includes all ArXiv
	articles that EE5 users may have seen in the past in their
	suggestion lists (so we go for all articles uploaded since
	2013-01-01), as well as all user-uploaded documents.
    */
    private static void init(Date since)  throws IOException {
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);

	EntityManager em  = Main.getEM();
	initClusters(em);
	EE5DocClass.CidMapper cidMap = new EE5DocClass.CidMapper(em);

	Logging.info("(Re)Classifying documents dated since " + since);
	ScoreDoc[] sd = getRecentArticles( em, searcher, since);
	Classifier.classifyDocuments(em, searcher.getIndexReader(), sd,cidMap);

	org.apache.lucene.search.Query q = Queries.hasUserQuery();
	sd = searcher.search(q, maxlen).scoreDocs;
	Logging.info("Found " + sd.length + " user-uploaded docs to (re)classify");
	Classifier.classifyNewDocsCategoryBlind(em, reader, sd, cidMap, true, null);

	reader.close();
	em.close();
    }

  
    /** When the system is first set up, run
	<pre>
	Daily init
	</pre>

	After that, run
	<pre>
	Daily updates
	</pre>
	every night
     */
    static public void main(String[] argv) throws IOException, java.text.ParseException {
	ParseConfig ht = new ParseConfig();      
	String stoplist = "WEB-INF/stop200.txt";
	stoplist = ht.getOption("stoplist",stoplist);
	UserProfile.setStoplist(new Stoplist(new File(stoplist)));
	String onlyUser = ht.getOption("user", null);

	String basedir = ht.getOption("basedir", Files.getBasedir());
	Files.setBasedir(basedir);
	Files.mode2014 = ht.getOption("mode2014", Files.mode2014);

	Logging.info("basedir=" + Files.getBasedir() +"; mode2014=" + Files.mode2014);

	if (argv.length == 0) {
	    System.out.println("Usage: Daily [delete|init|update]");
	    return;
	}


	int ia=0;
	String cmd = argv[ia++];
	if (cmd.equals("delete")) {
	    deleteAll();
	} else if (cmd.equals("init")) {
	    Date since = ht.getOptionDate("since", "2013-01-01");
	    init(since);
	} else if (cmd.equals("update")) {	    
	    updates(onlyUser);
	} else if (cmd.equals("classifySome")) {	    
	    String infile = argv[ia++];
	    String[] aids=FileIterator.readAidsFlat(infile);
	    System.out.println("Will classify " + aids.length + " docs");
	    classifySomeDocs(aids);
	} else {
	    System.out.println("Unknown command: " + cmd);
	}




    }

}
