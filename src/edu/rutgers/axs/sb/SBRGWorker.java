package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.util.regex.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.commons.lang.mutable.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.search.*;

/** This class encapsulates most of the activity involved in the
    creation of session-based recommendation lists.

    <p> There are several algorithms for creating recommendation
    lists. The list is created based on the articles that have been
    viewed during this session, using various mathematical algorithms. 

    <p>For example, in the ABSTRACTS mode, for each viewed article,
    the list of most similar articles is compiled, based on the
    similarity of the texts of the articles' abstracts (using a Lucene
    search); the lists are then combined. After that, some articles
    are excluded from the combined list. The following articles are
    excluded:
    
    <ul>
    <li>The already-viewed articles themselves.
 
    <li>Articles that were mentioned (linked to) in any pages
    viewed during this session.

    <li>Any articles about which the user has expressed his desire not to 
    see tnem anymore. (This type of feedback is provided via buttons
    in the SB panel).
    </ul>

    <p>A single SBRGWorker instance is created for each session. A new
    worker() call is made every time the rec list needs to be updated
    (typically, after every user action). Certain data computed on
    earlier invocations (namely, lists of related articles for each
    viewed article) are preserved through the session and used on
    subsequent invocations.

    <p>
    FIXME: must not select based on similarity to "prohibited" articles!

    <p>To implement a new SBRG algorithm, you may expand this class'
    work() method (if the algo is similar to those in it), or you can
    subclass this class.
    
 */
class SBRGWorker  {
    /** Back link to the SBRGenerator on whose behalf this worker works.  */
    private final SBRGenerator parent;

    /** The method with which the recommendation list is generated here. */
    final SBRGenerator.Method sbMethod;

   /** Pre-computed suggestion lists based on individual articles. In
	the hashmap, the keys are ArXiv article IDs; the values are
	arrays of ScoreDoc objects of the kind that a Lucene search
	may return.
     */
    HashMap<String,ScoreDoc[]> articleBasedSD= new HashMap<String,ScoreDoc[]>();
    
    /** This controls the way of achieving "stable order" of the
	articles in the rec list. (This parameter has nothing to do 
	with merging lists obtained by different methods!). The value 0 means
	"none".
    */
    private final int sbStableOrderMode;

    /** Set to true to "inner nodes" of a merge tree, to indicate that
	rec lists produced by these workers are never actually
	presented to the user.*/
    boolean hidden=false;

    /** Creates a worker object for a given user session. Later, you can call
	its work() method every time you need an updated rec list.
	@param _id Run id, which identifies this run (and its results)
	within the session.
     */
    SBRGWorker(SBRGenerator.Method _sbMethod,
	       SBRGenerator _parent,
	       int _sbStableOrderMode	       ) {
	parent = _parent;
	sbMethod = _sbMethod;
	sbStableOrderMode = _sbStableOrderMode;
    }

    ActionHistory his = null;

    /** Looks up the JVM thread ID for the current thread (the SBRGThread within whose
	context this worker works)
     */
    static private long getId() { 
	return Thread.currentThread().getId(); 
    }

    /** The main method for the actual recommendation list
	generation. It may be invoked several times over the life of a
	SBRGWorker instance (i.e., over a particular user session),
	with a new invocation every time when the rec list needs to be
	updated. 

	<p>Several different algos are supported here; all of them
	are based on computing individual lists of related
	articles for each viewed article, and then merging these lists.
	After this, exclusions are applied.
	
	@param runID A sequential ID (zero-based) of this
	SBRL-generation run within the user session.

	@param _his An ActionHistory object containing all user
	actions within this session. Since a single SBRGWorker
	instance is supposed to be used within the same user session,
	the _his parameter on subsequent calls to the work() method of
	a particular SBRGWorker instance you are supposed to use
	(updated) ActionHistory objects for the same session. 
     */
    synchronized void work(EntityManager em, IndexSearcher searcher, int runID, ActionHistory _his)  {

	his = _his;
	error = false;
	errmsg = "";
	sr=null;
	excludedList = "";

	//	Logging.info("Started SBRGWorker.work("+sbMethod+")"); 


	try {
	    // get the list of article IDs to recommend by some algorithm
	    if (sbMethod==SBRGenerator.Method.TRIVIAL) {
		computeRecListTrivial(em,searcher);
	    } else if (sbMethod==SBRGenerator.Method.ABSTRACTS ||
		       sbMethod==SBRGenerator.Method.COACCESS ||
		       sbMethod==SBRGenerator.Method.COACCESS_LOCAL ||
		       sbMethod==SBRGenerator.Method.COACCESS2 ||
		       sbMethod==SBRGenerator.Method.SUBJECTS) {

		computeRecList(em,searcher,  runID);
	    } else {
		error = true;
		errmsg = "Illegal SRB generation method: " + sbMethod;
		System.out.println(errmsg);
		return;
	    }
	    if (error) return;
	    plid = saveAsPresentedList(em).getId();
	} catch(Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	}

    }

    int getArticleCount() {
	return his==null? 0 : his.articleCount;
    }

    /** The recommendation list generated by the last work() call */
    public SearchResults sr=null;
    /** The ID of the PresentedList structure in which the rec list was
	saved in the database. */
    public long plid=0;

    /** The error flag set by the most recent call to worker(). */
    boolean error = false;
    String errmsg = "";

    /** A human-readable text message containing the list of ArXiv
	article IDs of the articles that we decided NOT to show in the
	rec list (e.g., because they had already been shown to the
	user in this session in other contexts). This variable is only
	used in reporting.
    */
    String excludedList = "";

   
    /** An auxiliary object used to "integrate" information about an
	article's position in multiple ranked lists. In particular, it
	is used for ascending-order sort (i.e. rank 1 before rank 2,
	etc) */
    private static class ArticleRanks implements Comparable<ArticleRanks> {
	/** Lucene internal id */
	int docno;
	/** This field contains the sum of scores for this article
	    from various lists being merged. It is stored for
	    information purposes only, and is *not* used for
	    sorting. */
	double score=0;

	/** How old was the most recent user action which caused this article
	    to be suggested? 0 &le; age &lt; n, where n is the number of
	    distinct-article actions used for generating the rec list.
	 */
	int age=0;
	/** List of ranks this article possesses in various ranked
	    lists, in increasing order. For example (0, 1, 1, 5) means
	    that this article has rank 0 (the top rank) in one list,
	    rank 1 in two other lists, and rank 5 in yet another list.
	*/
	Vector<Integer> ranks=new Vector<Integer>();
	public int compareTo(ArticleRanks o) {
	    for(int i=0; i<ranks.size() && i<o.ranks.size(); i++) {
		int z=ranks.elementAt(i).intValue()-o.ranks.elementAt(i).intValue();
		if (z!=0) return z;
	    }
	    return o.ranks.size() - ranks.size();
	}
	ArticleRanks(int _docno, int _age) { 
	    docno = _docno;
	    age= _age;
	}
	/** Adds the information about this article being ranked in
	    yet another list being merged. For a given article, calls
	    to this method should be carried out in order of
	    non-decreasing r. */
	void add(int r, double deltaScore, int _age) {
	    if (ranks.size()>0 && r<ranks.elementAt(ranks.size()-1).intValue()){
		throw new IllegalArgumentException("ArticleRanks.add() calls must be made in order");
	    }
	    ranks.add(new Integer(r));
	    score +=  deltaScore;
	    if (_age < age) age = _age;  // old-style ages
	}

	public String toString() {
	    String s = "(" + docno + ": " + score + " {";
	    for(int r : ranks) s+= " " + r;
	    s += "})";
	    return s;

	}
    }

  /**  Paul's suggestion on list length (2014-02-26): I have been
	thinking about how quickly the SB list grows. I would favor
	somewhat slower growth. Perhaps start with three; then add
	two; thereafter, add only one at a time unless there are two
	(or even three) that suddenly outrank anything already on the
	list. I am not at all clear on how that fact could be
	displayed --- flashing text? twinkling lights...:) ?

	@param n The number of articles viewed so far.
     */ 
    static int recommendedListLength(int n) {
 	    final int maxRecLenTop = 20;
	    //int m =3*n;   
	    int m = (n<=2) ? 3: 2 + n;
	    m = Math.min(m, maxRecLenTop);   
	    return m;
    }
 
    /** Computes a suggestion list based on a single article, using
	text similarity of titles and abstracts */
    static private ScoreDoc[] computeArticleBasedListAbstracts(IndexSearcher searcher, String aid, int maxlen) throws Exception {
	int docno=0;
	try {
	    docno= Common.find(searcher, aid);
	} catch(IOException ex) {
	    return null;
	}
	Document doc = searcher.doc(docno);
	String abst = doc.get(ArxivFields.ABSTRACT);
	abst = abst.replaceAll("\"", " ").replaceAll("\\s+", " ").trim();
	ScoreDoc[] z = (new LongTextSearchResults(searcher, abst, maxlen)).scoreDocs;
	return z;
    }

    /** Computes a suggestion list based on a single article, using
	subject categories. This is used in our baseline method. */
    static private ScoreDoc[] computeArticleBasedListSubjects(IndexSearcher searcher, String aid, int maxlen) throws Exception {

	int docno= Common.findOrMinus(searcher, aid);
	if (docno < 0) {
	    Logging.warning("SBRGThread " + getId() + ": ignoring article " + aid + " which is not (yet?) in Lucene");
	    return new  ScoreDoc[0];
	}
	Document doc = searcher.doc(docno);

	String catJoined =doc.get(ArxivFields.CATEGORY);
	String[] allCats =  CatInfo.split(catJoined);
	if (allCats.length<1) { return new  ScoreDoc[0]; }

	String[] cats = new String[] {allCats[0]}; // main cat only

	Date since = SearchResults.daysAgo(7); // date range
	SearchResults bsr = SubjectSearchResults.orderedSearch(searcher, cats, since, null, maxlen);
	ScoreDoc[] z = bsr.scoreDocs;
	return z;
    }


    /** Computes a suggestion list based on a single article, using
	the historical ArXiv.org coaccess data. This is used in our
	COACCESS method (and, by extension, in COACCESS2).

	@param aid The article for which suggestions are to be computed
	@return An array each element of which contains a Lucene internal document ID and the ArXiv historic coaccess count, as obtained from the coaccess server
    */
    static private ScoreDoc[] computeArticleBasedListCoaccess(IndexSearcher searcher, String aid, int maxlen) throws Exception {

       ScoreDoc [] results =  new ScoreDoc[0];

       // http://my.arxiv.org/coaccess/CoaccessServlet
       // http://my.arxiv.org/coaccess/CoaccessServlet?arxiv_id=0704.0001&maxlen=5

       String query = "arxiv_id=" + aid + "&maxlen=" + maxlen;

       String lURLString = "http://my.arxiv.org/coaccess/CoaccessServlet";
       lURLString += "?" + query;

       URL lURL = new URL( lURLString);
       Logging.info("SBRG requesting URL " + lURL);
       HttpURLConnection lURLConnection;
       lURLConnection=(HttpURLConnection)lURL.openConnection();	
       lURLConnection.connect();

	int code = lURLConnection.getResponseCode();
	if (code != HttpURLConnection.HTTP_OK) {
	    throw new IOException("Got an error code from " + lURL + ": " + 
				  lURLConnection.getResponseMessage());
	}

	InputStream is= lURLConnection.getInputStream();
	if (is==null) {
	    String errmsg= "Failed to obtain data from " + lURL;
	    throw new IOException(errmsg);
	}

	LineNumberReader r = 
	    new LineNumberReader(new InputStreamReader(is));

	String line=null;
	
	Vector<ScoreDoc> v = new Vector<ScoreDoc>();
	while((line=r.readLine())!=null) {
	    String q[] = line.split("\\s+");
	    if (q.length!=2) continue; // FIXME
	    String zaid = q[0];
	    int coaccessCnt = Integer.parseInt(q[1]);

	    int docno=0;
	    try {
		docno= Common.find(searcher, zaid);
	    } catch(IOException ex) {
		continue; // FIXME
	    }
	    v.add( new ScoreDoc( docno, (float)coaccessCnt));
	}
	results = (ScoreDoc[])v.toArray(results);
	return results;
    }

    /** (The original version now retired)

	For a given article, creates a sug list based strictly on the
	recent local (My.ArXiv) coaccess. This is used in COACCESS_LOCAL (and,
	by extension, in COACCESS2).  
	
	This method summed all accesses, so the same user interacting
	with the same article multuiple times in the same session
	would bring in multiple contribution. To avoid this, this method
	has been replaced by computeArticleBasedListLocalCoaccess(),
	which used sum_{sessions} max_{within each session}, instead
	of plain sum.

    */
    static private ScoreDoc[] computeArticleBasedListLocalCoaccessVer1(EntityManager em, IndexSearcher searcher, String aid0, int maxlen, SessionData thisSession) throws Exception {

	String uname = thisSession.getStoredUserName();
	long thisSid = thisSession.getSqlSessionId();

	ScoreDoc [] results =  new ScoreDoc[0];

	try {
	    
	    final Date t0 = SearchResults.daysAgo(365);

	    String qs= "select a2.article.aid, sum( aw1.weight * aw2.weight)  "+
		"from Action a1, Action a2, ActionWeight aw1,  ActionWeight aw2  " +
		"where a1.session=a2.session and a1.time>:t0 and a2.time>:t0 ";
	    if (uname != null) {
		qs +=
		    "and a1.user.user_name <> :usim and a2.user.user_name <> :usim ";
	    }

	    qs +=
		"and a1.article.aid is not null and a2.article.aid is not null " +
		"and a1.op = aw1.op and a2.op = aw2.op "+
		"and a1.article.aid = :aid and a2.article <> a1.article " +
		"group by a2.article.aid order by sum( aw1.weight * aw2.weight) desc";
	    
	    Query q = em.createQuery(qs);	
	    q.setParameter("aid",aid0);
	    q.setParameter("t0", t0);
	    if (uname != null) {
		q.setParameter("usim",  uname);
	    }
	    List res = q.getResultList();
	    Vector<ScoreDoc> v = new Vector<ScoreDoc>();
	    for(Object o: res) {			    

		if (!(o instanceof Object[])) continue;
		Object[] oa = (Object[])o;
		String zaid=(String)oa[0];
		Double w = (Double)oa[1];

		int docno=0;
		try {
		    docno= Common.find(searcher, zaid);
		} catch(IOException ex) {
		    continue; // FIXME
		}

		ScoreDoc sd = new ScoreDoc(docno, (float)w.doubleValue());
		v.add(sd);
	    }
	    results = (ScoreDoc[])v.toArray(results);
	} catch(Exception ex) {
	    Logging.warning("Exception in SBRGWorker.computeArticleBasedListLocalCoaccessVer1("+aid0+")" + ex);
	    ex.printStackTrace(System.err);
	} 
	return results;
    }

    /*
 	For a given article, creates a sug list based strictly on the
	recent local (My.ArXiv) coaccess. This is used in COACCESS_LOCAL (and,
	by extension, in COACCESS2).  
	
	Unlike the simpler method  computeArticleBasedListLocalCoaccessVer1, 
	which simply sums all accesses (so the same user interacting
	with the same article multuiple times in the same session
	would bring in multiple contribution), this method computes
	sum_{sessions} max_{within each session}.

	Unfortunately, in JPQL it's apparently impossible to compute sum max
	in a single query; so unlike the ver1 method, this method uses
	multiple queries.

	FIXME: on a system with a large amount of user activity,
	computing coaccess data "on the fly" would be unacceptably
	expensive.  To make it possible to have a recommender like
	COACCESS_LOCAL, we'd need to precompute most of the data (say,
	run a script daily), only leaving the most recent sessions
	(less than the last 24 hours) for the on-the-fly
	computations. This is actually similar to the COACCESS2 idea.
	However, if we set up this precomputing, removing user's own
	activity from the coaccess sums will be more difficult.

	@param aid0 We're looking for the coaccess data for this page
	@param sd A link to the SessionData object for the current
	session. Used to obtain the user name (uname), so that the
	ctivity of this user will be disregarded, in order to provide
	the user with coaccess numbers based on *other* users'
	activity. If an anon user, at least the activity within the current 
	session can be excluded.
    */
    static private ScoreDoc[] computeArticleBasedListLocalCoaccess(EntityManager em, IndexSearcher searcher, String aid0, int maxlen, SessionData thisSession) throws Exception {

	String uname = thisSession.getStoredUserName();
	long thisSid = thisSession.getSqlSessionId();

	ScoreDoc [] results =  new ScoreDoc[0];

	//	Logging.info("SBRGWorker.computeArticleBasedListLocalCoaccess("+aid0+")");

	try {

	    final String usim =  SBRGeneratorCmdLine.simulatedUname;
	    boolean needUname = (uname!=null) && !uname.equals(usim);
	    
	    final Date t0 = SearchResults.daysAgo(365);

	    // Get all sessions where "aid0" was involved. Sessions
	    // by the simulated user, and by the current user are
	    // ignored.
	    String qs1= 
		"select a1.session, max(aw1.weight) "+
		"from Action a1, ActionWeight aw1 " +
		"where a1.time > :t0 and a1.session > 0 " +
		"and a1.op = aw1.op and a1.article.aid = :aid " +
		"and (a1.user is null or a1.user.user_name <> :usim " +
		(needUname ? "and a1.user.user_name <> :uname" : "") +
		") " +
		"group by a1.session ";
			 	    
	    Query q = em.createQuery(qs1);	
	    q.setParameter("aid",aid0);
	    q.setParameter("t0", t0);
	    q.setParameter("usim", usim);
	    if (needUname) {
		q.setParameter("uname", uname);
	    }
	    List res = q.getResultList();

	    Vector<Long> sids = new Vector<Long>();
	    Vector<Double> aw1s = new Vector<Double>();   

	    for(Object o: res) {			    
		if (!(o instanceof Object[])) continue;
		Object[] oa = (Object[])o;
		Long sid=(Long)oa[0];
		Double w = (Double)oa[1];
		if (sid.longValue() == thisSid) continue; // skip this session
		sids.add(sid);
		aw1s.add(w);
	    }
	    //	    Logging.info("COACCESS_LOCAL for " + aid0 + " will use " + sids.size() + " sessions");

	    String qs2 = "select a2.article.aid, max(aw2.weight)  " +
		"from Action a2, ActionWeight aw2  " +
		"where a2.session=:sid and a2.op=aw2.op " +
		"and a2.article.aid is not null and a2.article.aid<>:aid " + 
		"group by a2.article.aid ";    
	    q = em.createQuery(qs2);	
	    HashMap<String, MutableDouble> pageValue = new HashMap<String, MutableDouble>();
	    q.setParameter("aid",aid0);

	    // now, get the numbers from all sessions
	    for(int i=0; i< sids.size(); i++) {
		long sid = sids.elementAt(i);
		// FIXME: Yes, the session id is Long in Action, but Int in Session
		q.setParameter("sid", (int)sid);
		double w1 = aw1s.elementAt(i);
		res = q.getResultList();
		for(Object o: res) {			    
		    Object[] oa = (Object[])o;
		    String aid=(String)oa[0];
		    Double w2 = (Double)oa[1];
		    MutableDouble val =  pageValue.get(aid);
		    if (val==null) pageValue.put(aid, val=new MutableDouble());
		    val.add( w1 * w2);
		}
	    }

	    // get the sums out ...
	    Vector<ScoreDoc> v = new Vector<ScoreDoc>();
	    for(String zaid: pageValue.keySet()) {
		MutableDouble w = pageValue.get(zaid);

		int docno=0;
		try {
		    docno= Common.find(searcher, zaid);
		} catch(IOException ex) {
		    continue; // FIXME
		}

		ScoreDoc sd = new ScoreDoc(docno, (float)w.doubleValue());
		v.add(sd);
	    }
	    results = (ScoreDoc[])v.toArray(results);
	    Arrays.sort(results, new SearchResults.SDComparator());
	    Logging.info("COACCESS_LOCAL for " + aid0 + " used " + sids.size() + " sessions, found " + results.length + " articles");
	} catch(Exception ex) {
	    Logging.warning("Exception in SBRGWorker.computeArticleBasedListLocalCoaccess("+aid0+")" + ex);
	    ex.printStackTrace(System.err);
	} 
	return results;
    }
    
    /** Merges two ScoreDoc arrays, by summing scores. This is used in
	COACCESS2, to add recent "local" (My.ArXiv's) coaccess numbers
	to the historic "global" (ArXiv.org's) coaccess numbers.
     */
    static private ScoreDoc[] addCoaccessLists(ScoreDoc[] list1, ScoreDoc[] list2) {
	HashMap<Integer, ScoreDoc> map2 = new 	HashMap<Integer, ScoreDoc>();
	for(ScoreDoc q: list2) map2.put(new Integer(q.doc), q);
	Vector<ScoreDoc> v = new Vector<ScoreDoc>();
	for(ScoreDoc q: list1) {
	    Integer key = new  Integer(q.doc);
	    ScoreDoc z = map2.get(key);
	    if (z==null) v.add(q);
	    else {
		v.add( new ScoreDoc(q.doc, q.score + z.score));
		map2.remove(key);
	    }
	}
	for(ScoreDoc z: map2.values()) v.add(z);
	ScoreDoc[] results =  (ScoreDoc[])v.toArray(new ScoreDoc[0]);
	Arrays.sort(results, new SearchResults.SDComparator());
	return results;
    }

    HashSet<String> findExclusions() {
	HashSet<String> exclusions = parent.linkedAids;
	synchronized (exclusions) {
	    if (his!=null) {
		exclusions.addAll(his.viewedArticlesAll);
		exclusions.addAll(his.prohibitedArticles);
	    }
	}
	return exclusions;
    }


    /** Generates the list of recommendations based on searching the Lucene
	index for articles whose abstracts are similar to those of the
	articles viewed by the user in this session. The results go into
	array sr.
     */
    private void computeRecList(EntityManager em, IndexSearcher searcher, int runID) {
	
	//	Logging.info("SBRGWorker.computeRecList("+sbMethod+") for "+his.viewedArticlesActionable.size()+" articles"); 
	try {
	    HashSet<String> exclusions = findExclusions();

	    // A list of related articles (by abstract match, or by
	    // coaccess, as appropriate), is obtained separately for
	    // each article. 
	    final int maxlen = 100;
	    int maxAge = his.viewedArticlesActionable.size();
	    final int maxRecLen = recommendedListLength(maxAge);
	    ScoreDoc[][] asr  = new ScoreDoc[maxAge][];
	    int k=0;
	    for(String aid: his.viewedArticlesActionable) {
		// see if the list has been precomputed on 
		// a previous invocation of this worker's work()
		// method
		ScoreDoc[] z = articleBasedSD.get(aid);
		if (z==null) {
		    //String uname =parent.sd.getStoredUserName();
		    if (sbMethod==SBRGenerator.Method.ABSTRACTS) {
			z=computeArticleBasedListAbstracts(searcher,aid,maxlen);
		    } else if (sbMethod==SBRGenerator.Method.SUBJECTS) {
			z=computeArticleBasedListSubjects(searcher,aid,maxlen);
		    } else if (sbMethod==SBRGenerator.Method.COACCESS 
			       // || sbMethod==SBRGenerator.Method.COACCESS2
			       ) {
			// exception may be thrown; caught in an outer "catch"
			z=computeArticleBasedListCoaccess(searcher,aid,maxlen);
		    } else if (sbMethod==SBRGenerator.Method.COACCESS_LOCAL) {
			z=computeArticleBasedListLocalCoaccess(em, searcher,aid,maxlen, parent.sd);

		    } else if (sbMethod==SBRGenerator.Method.COACCESS2) {
			ScoreDoc[] z1=computeArticleBasedListCoaccess(searcher,aid,maxlen);
			ScoreDoc[] z2=computeArticleBasedListLocalCoaccess(em, searcher,aid,maxlen, parent.sd);
			z =  addCoaccessLists(z1,z2);

		    } else {
			error = true;
			errmsg = "Illegal SRB generation method: " + sbMethod;
		    }

		    if (error) {
			Logging.error(errmsg);
			System.out.println(errmsg);
			return;
		    }

		    if (z!=null) articleBasedSD.put(aid,z);
		    else {
			// this may happen if the article is too new,
			// and is not in our Lucene datastore yet
			Logging.warning("SBRGThread " + getId() + ": skip unavailable page " + aid);	    
		    }
		}
		asr[k++] = z;
	    }

	    // merge all lists
	    HashMap<Integer,ArticleRanks> hr= new HashMap<Integer,ArticleRanks>();
	    for(int j=0; j<maxlen; j++) {
		int age=0;
		for(ScoreDoc[] z: asr) {
		    if (z!=null && j<z.length) {
			int docno = z[j].doc;
			Integer key = new Integer(docno);
			ArticleRanks r= hr.get(key);
			if (r==null) { 
			    r=new ArticleRanks(docno,age);
			    hr.put(key, r);
			}
			r.add(j, z[j].score, age);		      
		    }
		    age++;
		}
	    }
	    ArticleRanks[] ranked = (ArticleRanks[])hr.values().toArray(new ArticleRanks[0]);

	    //System.out.println("Articles ranks (unsorted)");
	    //for(int j=0; j<ranked.length; j++) System.out.println(ranked[j]);

	    Arrays.sort(ranked);
	    //System.out.println("Articles ranks (sorted) (top)");
	    //for(int j=0; j<ranked.length && j<10; j++) System.out.println(ranked[j]);

	    Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	    Vector<ScoreDoc> sd = new  Vector<ScoreDoc>();
	    k=1;
	    for(ArticleRanks r: ranked) {
		ScoreDoc z = new ScoreDoc(r.docno, (float)r.score);
		ArticleEntry ae= new ArticleEntry(k, searcher.doc(r.docno), z);
		ae.age = r.age;
		// check this article against the exclusion list
		if (exclusions.contains(ae.id)) {
		    excludedList += " " + ae.id;
		    continue;
		}
		
		// A label that identifies when the article first appeared
		// on the SB rec list
		ae.researcherCommline= "L" + runID + ":" + k;
		if (parent.sbDebug) ae.ourCommline= ae.researcherCommline;
		
		entries.add(ae);
		sd.add(z);
		k++;
		if (entries.size()>=maxRecLen) break;
	    }

	    sr = new SearchResults(entries); 
	    stableOrderCheck(maxRecLen);

	    sr.scoreDocs=(ScoreDoc[])sd.toArray(new ScoreDoc[0]); // for use in merges
	    //sr.saveAsPresentedList(em,Action.Source.SB,null,null, null);
	}  catch (Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    Logging.error(""+ex);
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	}
    }

    /** Reorders the new suggestion list (entries) so that it includes
	all (or almost all) elements from the previously displayed
	list, in their original order.

	<p>Note: ArticleEntry.age setting overrides any previous settings.

	<p>FIXME: As the "old" ordering we use is parent.getSR(), this won't
	work in case of "stabilizing" intermediary (pre-merge) lists in 
	a team-draft merge context. Only the final list can be stabilized.
	
	<p>FIXME: no ScoreDoc here, thus not suitable for
	merging. (Normally this does not matter)       

	@param entries The newly generated rec list, which needs to be
	integrated with the old list

	@return The new integrated list, which includes elements from
	both the new list and the old list, in the order based on the
	old list
     */
    private Vector<ArticleEntry> maintainStableOrder2( Vector<ArticleEntry> entries, int maxRecLen) {

	HashMap<String,ArticleEntry> allNew=new HashMap<String,ArticleEntry>();	

	for(ArticleEntry e: entries) {
	    e.age=0;
	    allNew.put(e.id, e);
	}
	
	Vector<ArticleEntry> previouslyDisplayedEntries = parent.getBaseList();
	if (previouslyDisplayedEntries==null) return entries;
	HashSet<String> exclusions = parent.linkedAids;

	HashSet<String> old=new HashSet<String>();
	// a[] = Old elements (not excluded under current exclusion criteria)
	Vector<ArticleEntry> a = new 	Vector<ArticleEntry>();
	for(ArticleEntry e: previouslyDisplayedEntries) {
	    if (exclusions.contains(e.id)) continue;
	    try {  // Use clone(), because we'll modify e.i, e.age, e.score
		ArticleEntry q = (ArticleEntry)e.clone();
		q.age ++;
		if (allNew.containsKey(q.id)) q.score = allNew.get(q.id).score;
		a.add(q);
		old.add(q.id);
	    }  catch (CloneNotSupportedException ex) {}
	}
	// b[] = New elements not found in the old list
	Vector<ArticleEntry> b = new Vector<ArticleEntry>();
	for(ArticleEntry e: entries) {
	    if (!old.contains(e.id)) {
		b.add(e);
	    }
	}
	double bRatio = b.size() / (double)(b.size() + a.size());
	
	Vector<ArticleEntry> v = new Vector<ArticleEntry>();
	int na=0, nb=0;
	while( v.size() <  maxRecLen &&
	       (na < a.size() || nb < b.size())) {
	    boolean useB = 
		(na == a.size()) ||
		(nb < b.size() &&  (nb+1) <= (na+nb+1)*bRatio );
	    
	    v.add( useB ? b.elementAt(nb++) : a.elementAt(na++));   	    
	}
	// Adjust positions
	int k=1;
	for(ArticleEntry e: v) {
	    e.i = k++;
	}	
	return  v;
    }

    /** Generates the trivial recommendation list: 
	(rec list) = (list of viewed articles). This method was used
	for quick testing.

	<p>FIXME: no ScoreDoc here, thus not suitable for
	merging. (Normally this does not matter)
     */
    private void computeRecListTrivial(EntityManager em, IndexSearcher searcher) {
	try {
	    // trivial list: out=in
	    Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	    //	    Vector<ScoreDoc> sd = new Vector<ScoreDoc>();
	    int k=0;
	    int maxAge = his.viewedArticlesActionable.size();
	    for(String aid:  his.viewedArticlesActionable) {
		ArticleEntry ae = new ArticleEntry(++k, aid);
		ae.setScore(1.0);
		ae.age = k-1;
		entries.add(ae);

	    }
	    sr = new SearchResults(entries); 
	    addArticleDetails(searcher);
	    //sr.saveAsPresentedList(em,Action.Source.SB,null,null, null);
	}  catch (Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    Logging.error(""+ex);
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	}
    }


    /**	This methods takes the new suggestion list (the one just
	generated) and reorders it so that it looks a bit more like
	the "old" suggestion list (the one return by the SBR generator
	at the previous call). The reordering is carried out in the
	following fashion:

	<ul> 

	<li> The "new" elements of the new list (those that are
	present in the new list, but were not shown in the previous
	list) are kept at their positions
	
	<li> The "old" elements of the new list (those that are
	present in the new list and also were shown in the previous
	list) are considered as a group. The set of positions they 
	occupy in the new list is kept unchanged, but they are moved
	around within this set so that they appear in the same relative
	order (with respect to each other) as they were in the old list.
	</ul>

	<p>This process is supposed to achieve the following effect
	for the user who observe the list change: The previously
	displayed articles maintain their relative order (although a
	few of them may disappear from the list), while some new
	(additional) articles became inserted at various positions
	between them.

	<p>Note: ArticleEntry.age setting overrides any previous settings.

	@param entries The new suggesion list to be reordered.

	@param maxRecLen The desired length of the suggesion list to
	be returned.

	@return A suggestion list that contains the articles from
	"entries" (and possibly also a small number of
	additional articles from the previously displayed list), reordered
	as per the above rules.

	<p>FIXME: As the "old" ordering we use is parent.getSR(), this won't
	work in case of "stabilizing" intermediary (pre-merge) lists in 
	a team-draft merge context. Only the final list can be stabilized.
     */
    private Vector<ArticleEntry> maintainStableOrder1( Vector<ArticleEntry> entries, int maxRecLen) {

	HashMap<String,ArticleEntry> allNew=new HashMap<String,ArticleEntry>();	

	for(ArticleEntry e: entries) {
	    e.age=0;
	    allNew.put(e.id, e);
	}
	
	Vector<ArticleEntry> previouslyDisplayedEntries = parent.getBaseList();
	if (previouslyDisplayedEntries==null) return entries;
	HashSet<String> exclusions = parent.linkedAids;
	
	// Make a list and hashtable of all old elements which are not excluded
	HashSet<String> old=new HashSet<String>();
	Vector<ArticleEntry> a = new Vector<ArticleEntry>();
	for(ArticleEntry e: previouslyDisplayedEntries) {
	    if (exclusions.contains(e.id)) continue;
	    try {  // We use clone() because we'll modify e.i, e.age, e.score
		ArticleEntry q = (ArticleEntry)e.clone();
		q.age++;
		if (allNew.containsKey(q.id)) q.score = allNew.get(q.id).score;
		a.add(q);
		old.add(q.id);
	    }  catch (CloneNotSupportedException ex) {}
	}
	
	// create a reordered list 
	Vector<ArticleEntry> v = new Vector<ArticleEntry>();
	
	int na=0;
	for(ArticleEntry e: entries) {
	    boolean recent = !old.contains(e.id);
	    ArticleEntry q = recent?
		// keep the "new" element in place
		e :
		// this position was occupied by an "old" element,
		// and we put the appropriately-ranked "old" element here
		a.elementAt(na++);
	    q.recent=recent;
	    v.add(q);
	}

	// Bonus old documents: they are added if too few of them have been
	// preserved
	final int minOldKept=2;
	if (na<minOldKept && na<a.size()) {
	    ArticleEntry q =a.elementAt(na++); 
	    q.recent=false;
	    v.add(q);
	}
	
	// Adjust positions
	int k=1;
	for(ArticleEntry e: v) {
	    e.i = k++;
	}	
	return  v;
    }

    /** Adds article title etc to each entry in sr.entries */
    protected void addArticleDetails(IndexSearcher searcher) throws IOException { 
	for(ArticleEntry ae: sr.entries) {
	    ae.populateOtherFields(searcher);
	}	    
    }

    /** May be overridden  in WorkerMerge */
    SBRGenerator.Method getSbMethodForPlist() { return sbMethod; }
    
    /** Auxiliary forsaveAsPresentedList(): overridden by
	SBRGWorkerMerge, it adds worker-specific info to the
	PresentedList object before it is saved in the database.
     */
    void addExtraPresentedListInfo(PresentedList plist) {}

    /** Records the list of suggestions as a PresentedList object in
      the database. Note that that the "user" object is usually null
      (an anon session), which is fine.
    */
    PresentedList saveAsPresentedList(EntityManager em) {
	String uname = parent.sd.getStoredUserName();
	User user = User.findByName(em, uname); 
	PresentedList plist = new PresentedList(Action.Source.SB, user,  parent.sd.getSqlSessionId());
	plist.setSbMethod(getSbMethodForPlist());
	plist.setHidden(hidden);
	addExtraPresentedListInfo(plist);
	plist.fillArticleList(sr.entries);	
	em.getTransaction().begin();
	em.persist(plist);
	em.getTransaction().commit();
	return plist;
    }

    /** Carry out the "maintain stable order" procedure, if required by the
	sbStableOrderMode parameter.  If there is indeed an MSO in order,
	this method also signals to the parent thread that the worker is
	near completion; this information can be used elsewhere to optimize
	client-server communication.
	
	<p>FIXME: this gets sr.scoreDocs out of sync with sr.entries, but who cares?
    */
    void stableOrderCheck(int maxRecLen) {
	if (sr==null || sr.entries==null) {
	    Logging.warning("called SBRGW.stableOrderCheck() with no data, WTF?");
	    return;
	}
	if (sbStableOrderMode==0) {  // no MSO
	    return;
	}

	Thread t = Thread.currentThread();
	if (t instanceof SBRGThread) {
	    ((SBRGThread)t).reportPartialProgress();
	}

	if (sbStableOrderMode==1) {
	    sr.entries = maintainStableOrder1( sr.entries, maxRecLen);
	} else   if (sbStableOrderMode==2) {
	    sr.entries = maintainStableOrder2( sr.entries, maxRecLen);
	}
    }


    /** Produces a human-readable description of this worker's particulars. The
	method may be overriden by derivative classes, to report more details. */
    public String description() {
	String s = "SBR method=" + sbMethod + "; stableOrder=" + sbStableOrderMode;
	return s;
    }
   
    /** A very auxiliary function, used in debugging... */
    String describeLength() {
	if (sr==null) return "[No sr!]";
	if (sr.entries==null) return "[No sr.entries!]";
	if (sr.scoreDocs==null) return "[No sr.scoreDocs!]";
	String s = sr.entries.size() + "/";
	s += sr.scoreDocs.length;
	return s;
    }

    /** This is used for command-line testing, so that we could
	quickly view the list of suggestions that would be prepared
	for a particular article
     */
    public static void main(String [] argv) throws Exception {
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	final int maxlen = 100;
	for(String aid: argv) {
	    System.out.println("Generating suggestion for aid=" + aid);
	    ScoreDoc[] z = SBRGWorker.computeArticleBasedListCoaccess(searcher, aid, maxlen);
	    if (z!=null) {
		for(int j=0; j<z.length && j<5; j++) {
		    int docno = z[j].doc;
		    Document doc = searcher.doc(docno);
		    String said = doc.get(ArxivFields.PAPER);
		    System.out.println("doc["+j+"]=" + said + " : " + z[j].score);
		}
	    }    else {
		// this may happen if the article is too new,
		// and is not in our Lucene datastore yet
		System.out.println("skip unavailable page " + aid);	    
	    }
	}
    }


}

