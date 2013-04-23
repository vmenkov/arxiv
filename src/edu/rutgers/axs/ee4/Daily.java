package edu.rutgers.axs.ee4;

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
import edu.rutgers.axs.recommender.*;


/** Daily updates for the underlying data structure, and
    recommendation generation, for Peter Frazier's Exploration Engine ver. 4.
 */
public class Daily {

    static private final int maxlen = 100000;

    /** The main "daily updates" method. Calls all other methods.
     */
    static void updates() throws IOException {

	ArticleAnalyzer.setMinDf(10); // as PG suggests, 2013-02-06
	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	ArticleAnalyzer z = new ArticleAnalyzer();
	IndexReader reader = z.reader;

	EntityManager em  = Main.getEM();
	    
	final int days = EE4DocClass.T * 7; 
	Date since = SearchResults.daysAgo( days );

	HashMap<Integer,EE4DocClass> id2dc= updateClassStats(em,z,since);
	List<Integer> lu = User.selectByProgram( em, User.Program.EE4);

	IndexSearcher searcher = new IndexSearcher( reader );
	for(int uid: lu) {
	    try {
		User user = (User)em.find(User.class, uid);
		makeEE4Sug(em, searcher, since, id2dc, user);
	    } catch(Exception ex) {
		Logging.error(ex.toString());
		System.out.println(ex);
		ex.printStackTrace(System.out);
	    }
	}
	em.close();
    }

    private static HashMap<Integer,EE4DocClass> updateClassStats(EntityManager em, ArticleAnalyzer z, Date since)  throws IOException {
	org.apache.lucene.search.Query q= SearchResults.mkSinceDateQuery(since);

	IndexSearcher searcher = new IndexSearcher( z.reader );
	TopDocs    top = searcher.search(q, maxlen);
	System.out.println("Looking back to " + since + "; found " + 
			   top.scoreDocs.length +  " papers");

	// list classes
	HashMap<Integer,EE4DocClass> id2dc = readDocClasses(em);
	// classify all recent docs
	updateClassInfo(em,z,top.scoreDocs, id2dc);
	// ... and not-yet-classified but viewed old docs
	//classifyUnclassified( em, searcher);
	return  id2dc;
    }


    /** Prepares a suggestion list for one user and saves it. This
     method does not conduct any document classification itself; it
     relies only on already-classified documents. Therefore, one
     either should use it either in the daily update script *after*
     all judged docs have been classified, or in a new-user scenario,
     when there is no judgment history anyway. */
    private static DataFile makeEE4Sug(EntityManager em,  IndexSearcher searcher, Date since, HashMap<Integer,EE4DocClass> id2dc, User user) throws IOException {
	int uid = user.getId();
	EE4User ee4u = EE4User.getAlways( em, uid, true);
	int lai = updateUserVote(em, id2dc, user, ee4u);
	boolean nofile = false;
	return updateSugList(em, searcher, since, id2dc, user, ee4u, lai, nofile);
    }

    /** This version is invoked from the web server, for newly created users
     */
   public static DataFile makeEE4SugForNewUser(EntityManager em,  IndexSearcher searcher,  User user) throws IOException {
	final int days = EE4DocClass.T * 7; 
	Date since = SearchResults.daysAgo( days );
	// list classes
	HashMap<Integer,EE4DocClass> id2dc = readDocClasses(em);

	int uid = user.getId();
	EE4User ee4u = EE4User.getAlways( em, uid, true);
	int lai = updateUserVote(em, id2dc, user, ee4u);
	boolean nofile = true;
	return updateSugList(em, searcher, since, id2dc, user, ee4u, lai, nofile);
    }

    static private HashMap<Integer,EE4DocClass> readDocClasses(EntityManager em) {
	List<EE4DocClass> docClasses = EE4DocClass.getAll(em);
	HashMap<Integer,EE4DocClass> id2dc = new HashMap<Integer,EE4DocClass>();
	for(EE4DocClass c: docClasses) {
	    id2dc.put(new Integer(c.getId()), c);
	}
	return id2dc;
    }

    /** Sets stat values in document class entries ( EE4DocClass table in the SQL server). 

	<p>We set the initial alpha=1, beta=19, as per ZXT, 2013-02-21

	@param scoreDocs The list of all recent docs for the last T
	weeks. (The correct range is important, so that we can update
	m correctly)
	@param  scoreDocs recent docs
     */
    static void updateClassInfo(EntityManager em, ArticleAnalyzer z, ScoreDoc[] scoreDocs, HashMap<Integer,EE4DocClass> id2dc)
	throws IOException
    {
	int maxCid = 0;

	for(EE4DocClass c: id2dc.values()) {
	    maxCid = Math.max(maxCid, c.getId());
	    c.setAlpha0(1.0);
	    c.setBeta0(19.0);
	}
	
	int mT[] = new int[ maxCid + 1];

	KMeans.classifyNewDocs(em, z, scoreDocs, mT);

	System.out.println("mT = " +  KMeans.arrToString(mT));

	em.getTransaction().begin();
	for(EE4DocClass c: id2dc.values()) {
	    int cid = c.getId();
	    int m = (int)Math.round( (double)mT[cid]/(double)EE4DocClass.T);
	    c.setM( m ); // average *weekly* number, as per the specs
	    em.persist(c);
	}
	em.getTransaction().commit();	
    }

    /** 0 - ignore, +-1 - med, +-2 - high */
    private static int actionPriority(Action.Op op) {
	if (op==Action.Op.INTERESTING_AND_NEW ||
	    op==Action.Op.COPY_TO_MY_FOLDER) return 2;
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

    /** Does action a override action b? Return the most "relevant" one 
     (higher-priority, or more recent) */
    private static Action mostRelevant(Action a, Action b) {	
	int aval = actionPriority(a.getOp()), bval=actionPriority(b.getOp());
	if (Math.abs(aval) > Math.abs(bval)) return a;
	else if (Math.abs(bval) > Math.abs(aval)) return b;
	else return a.getTime().after(b.getTime()) ? a : b;
    }

    /** Recomputes alpha[z] and beta[z] for each class z for a
	specified user.  For each (user,page) pair, only the most
	recent action (of one of the requisite types) is considered as
	the user's current "vote" on that page.
	@return the last (most recent) action id used in the update
    */
    static int updateUserVote(EntityManager em, HashMap<Integer,EE4DocClass> id2dc, User u, 	EE4User ee4u) throws IOException {

	Set<Action> sa = u.getActions();
	long lai=0;
	// maps our Article.id to the latest Action
	HashMap<Integer, Action> lastActions = new HashMap<Integer,Action>();
	for( Action a: sa) {
	    if (actionPriority(a.getOp())==0) continue;
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

	System.out.println("--- Currently relevany actions for user " + u.getUser_name());
	for(Action a: lastActions.values()) {
	    boolean plus= actionPriority(a.getOp())>0;
	    int cid = a.getArticle().getEe4classId(); 	    
	    if (plus) {
		alpha[ cid] ++;
	    } else {
		beta[ cid] ++;
	    }
	    System.out.println((plus? "PLUS " : "MINUS ") +" page=" + 
			       a.getAid() + ", " + a.getOp() +  ", class=" +
			       cid);
	}
	
	HashSet<EE4Uci> uci=new HashSet<EE4Uci>();
	
	for(int cid: id2dc.keySet()) { 
	    EE4DocClass dc =  id2dc.get(cid);
	    EE4Uci w = new EE4Uci(cid,dc.getAlpha0()+alpha[cid], dc.getBeta0()+beta[cid]);
	    uci.add(w);
	}
	
	ee4u.setUci(uci);
	em.persist(ee4u);
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
    private static DataFile updateSugList(EntityManager em,  IndexSearcher searcher, Date since, HashMap<Integer,EE4DocClass> id2dc, User u, EE4User ee4u, int lai, final boolean nofile)
	throws IOException
     {
	 HashMap<Integer,EE4Uci> h = ee4u.getUciAsHashMap();

	 String[] cats = u.getCats().toArray(new String[0]);
	 SubjectSearchResults sr = new SubjectSearchResults(searcher, cats, since, maxlen);
	 // Order by recency (most recent first). This is the secondary key.
	 sr.reorderCatSearchResults(searcher.getIndexReader(), null);
	 //	 Logging.info("Daily.USL: |sr|=" + sr.scoreDocs.length);

	 Vector<ArxivScoreDoc> results= new Vector<ArxivScoreDoc>();
	 Vector<String> comments = new  Vector<String>();

	 for(ScoreDoc sd: sr.scoreDocs) {
	     Document doc = searcher.doc(sd.doc);
	     String aid = doc.get(ArxivFields.PAPER);
	     Article a = Article.getArticleAlways(em,aid);
	     final int cid = a.getEe4classId();
	     EE4DocClass c = id2dc.get(new Integer(cid));
	     EE4Uci ee4uci = h.get(new Integer(cid));
	     double alpha=ee4uci.getAlpha(),  beta=ee4uci.getBeta(); 
	    
	     EE4Uci.Stats stats = ee4uci.getStats(c.getM(), ee4u.getCCode());
	     double score = alpha/(alpha + beta);
	     if (stats.admit) { // add to list
		 //		 results.add(new ArxivScoreDoc(sd).setScore(score));
		 results.add(new ArxivScoreDoc(sd).setScore(stats.cStar));
		 comments.add("Cluster "+cid+", " + alpha+"/("+alpha+"+"+beta+")>mu=" +stats.mu +", c* = " + stats.cStar);
		 //		 Logging.info("Daily.USL: added, score=" + score);
	     } else {
		 //		 Logging.info("Daily.USL: not added, score=" + score);
	     }
	     if (results.size() >=  MAX_SUG) {
		 Logging.info("Daily.USL: truncating sug list at " + MAX_SUG);
		 break;
	     }
	 }
	 em.getTransaction().begin();	
	 DataFile outputFile=new DataFile(u.getUser_name(), 0, DataFile.Type.EE4_SUGGESTIONS);
	 outputFile.setSince(since);
	 outputFile.setLastActionId(lai);
	 Vector<ArticleEntry> entries = 
	     ArxivScoreDoc.packageEntries(results.toArray(new ArxivScoreDoc[0]), comments.toArray(new String[0]), searcher.getIndexReader());
	 ArticleEntry[] tmp = (ArticleEntry[])entries.toArray(new ArticleEntry[0]);
	 Arrays.sort(tmp);
	 entries = new Vector();
	 for(ArticleEntry ae: tmp) {	 entries.add(ae);}

	 Logging.info("Daily.USL: |entries|=" + entries.size());

	 if (nofile) { 
	     // we can't write a file now, due to file permissions reasons
	     outputFile.setThisFile(null);
	 } else {
	     ArticleEntry.save(entries, outputFile.getFile());
	 }
	 outputFile.fillArticleList(entries,  em);

	 em.persist(outputFile);
	 em.getTransaction().commit();	
	 return outputFile;
     }

  
    /** When the system is first set up, run
	<pre>
	Daily init
	Daily updateClassStats
	</pre>

	After that, run
	<pre>
	Daily updates
	</pre>
	every night
     */
    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	
	if (argv.length == 0) {
	    System.out.println("Usage: Daily [init|update]");
	    return;
	}


	String cmd = argv[0];
	if (cmd.equals("init")) {
	    throw new IllegalArgumentException("Please run KMeans instead!");
	} else if (cmd.equals("update")) {
	    updates();
	} else {
	    System.out.println("Unknown command: " + cmd);
	}

    }

}
