package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;

/** This classes encapsulates most of the activity involved in the
    creation of session-based recommendation lists.

    <p> The recommendation list is presently (2014-03) created based
    on the articles that have been viewed during this session. For
    each of them, the list of most similar articles is compiled, based
    on the similarity of the texts of the articles' abstracts (using a
    Lucene search); the lists are then combined. After that, some
    articles are excluded from the combined list. The following articles
    are excluded:
    
    <ul>
    <li>The already-viewed articles themselves.

    <li>Articles that were mentioned (linked to) in any pages
    viewed during this session.

    <li>Any articles about which the user has expressed his desire not to 
    see tnem anymore. (This type of feedback is provided via buttons
    in the SB panel).
    </ul>

    FIXME: must not select based on similarity to "prohibited" articles!

    
 */
class SBRGThread extends Thread {
    private final SBRGenerator parent;
    private final int id;

    /** Creates a thread. You must call its start() next */
    SBRGThread(SBRGenerator _parent, int _id) {
	parent = _parent;
	id = _id;
    }

    /** When the list generation started and ended. We keep this 
     for statistics. */
    Date startTime, endTime;

    private ActionHistory his = null;

    /** The main class for the actual recommendation list
	generation. */
    public void run()  {
	startTime = new Date();
	
	IndexReader reader=null;
	IndexSearcher searcher = null;

	final boolean trivial = false;

	EntityManager em=null;
	try {
	    em = parent.sd.getEM();       
	    reader=Common.newReader();
	    searcher = new IndexSearcher( reader );
	    // get the list of article IDs to recommend by some algorithm
	    if (trivial) {
		computeRecListTrivial(em,searcher);
	    } else {
		computeRecList(em,searcher);
	    }
	    plid = saveAsPresentedList(em).getId();
	} catch(Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    System.out.println("Exception for SBRG thread " + getId());
	    ex.printStackTrace(System.out);
	    //sr = null;
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    try {
		if (searcher!=null) searcher.close();
		if (reader!=null) reader.close();
	    } catch(IOException ex) {}
	    endTime = new Date();
	}
	parent.completeRun();

    }

    int getActionCount() {
	return his==null? 0 : his.actionCount;
    }

    /** The recommendation list generated by this method */
    public SearchResults sr=null;
    /** The ID of the PresentedList structure in which the rec list was
	saved in the database. */
    public long plid=0;

    boolean error = false;
    String errmsg = "";

    /** Retrieves the list of articles viewed or "rated" by the user
	in this session so far. (Of course, if this is an anonymous
	session, there is only one type of "ratings" collected and
	recorded: "Don't show again").

	<P>Among all viewed articles, we identify a subset of
	"actionable" ones.  This includes those articles that have
	been viewed in any context other than the "main page" (the
	main suggestion list) and its derivatives. This restriction is
	as per the conference call on 2014-05-20; its purpose it to
	ensure the "orthogonality" of the two rec lists (the main
	[nightly] rec list and the SBRL).

	<p>
	FIXME: one can save on this one SQL call by storing the list
	in the SessionData object; but we already make so many SQL
	calls anyway...
    */
    private class ActionHistory {

	/** The number of actions in the session so far      */
	int actionCount=0;
	/** The number of viewed articles in the session so far. (It may be
	    smaller than actionCount, because the user may have viewed the
	    same page multiple times). */
	int articleCount=0;
    
	/** Lists  all viewed articles (both actionable and not) */
	Vector<String> viewedArticlesAll = new Vector<String>();
	/** Lists viewed articles that are "actionable" (i.e., based on which we
	    will compute suggestions) */
	Vector<String> viewedArticlesActionable = new Vector<String>();
	/** Lists  "prohibited" articles (don't show) */
	Vector<String> prohibitedArticles = new Vector<String>();

	ActionHistory(EntityManager em) {
	    Vector<Action> va = Action.actionsForSession( em, parent.sd.getSqlSessionId());
	    actionCount = va.size();

	    // lists of viewed articles; a true value is stored
	    // for "actionable" ones
	    HashMap<String, Boolean> viewedH = new HashMap<String, Boolean>();
	    // lists of "prohibited" articles
	    HashSet<String> prohibitedH = new HashSet<String>();
	    
	    for(Action a: va) {
		Article art = a.getArticle();
		if (art==null) continue; // "NEXT PAGE" etc
		String aid = art.getAid();
		
		Action.Op op=a.getOp();
		if (op.isHideSB()) {
		    if (!prohibitedH.contains(aid)) {
			prohibitedH.add(aid);
			prohibitedArticles.add(aid);
		    }
		} else {
		    Action.Source src = a.getSrc();
		    boolean actionable = (src!=null && !src.isMainPage());
		    Boolean val = viewedH.get(aid);
		    if (val!=null) actionable = actionable||val.booleanValue();
		    viewedH.put(aid, new Boolean(actionable));
		}
	    }

	    for(String aid:  viewedH.keySet()) {
		viewedArticlesAll.add(aid);
		if (viewedH.get(aid).booleanValue()) viewedArticlesActionable.add(aid);
	    }
	    
	    articleCount=viewedArticlesActionable.size();
	}
    }

    /** An auxiliary object for ascending-order sort (i.e. rank 1
	before rank 2, etc) */
    private static class ArticleRanks implements Comparable<ArticleRanks> {
	/** Lucene internal id */
	int docno;
	/** This field contains the sum of scores for this article
	    from various lists being merged. It is stored for
	    information purposes only, and is *not* used for
	    sorting. */
	double score=0;
	Vector<Integer> ranks=new Vector<Integer>();
	public int compareTo(ArticleRanks o) {
	    for(int i=0; i<ranks.size() && i<o.ranks.size(); i++) {
		int z=ranks.elementAt(i).intValue()-o.ranks.elementAt(i).intValue();
		if (z!=0) return z;
	    }
	    return o.ranks.size() - ranks.size();
	}
	ArticleRanks(int _docno) { docno = _docno; }
	/** Should be called in order of non-decreasing r */
	void add(int r, double deltaScore) {
	    if (ranks.size()>0 && r<ranks.elementAt(ranks.size()-1).intValue()){
		throw new IllegalArgumentException("ArticleRanks.add() calls must be made in order");
	    }
	    ranks.add(new Integer(r));
	    score +=  deltaScore;
	}
    }

    /** A text message containing the list of ArXiv article IDs of the
	articles that we decided NOT to show in the rec list (e.g., because
	they had already been shown to the user in this session in
	other contexts).
    */
    String excludedList = "";

    
  /**  Paul's suggestion on list length (2014-02-26): I have been
	thinking about how quickly the SB list grows. I would favor
	somewhat slower growth. Perhaps start with three; then add
	two; thereafter, add only one at a time unless there are two
	(or even three) that suddenly outrank anything already on the
	list. I am not at all clear on how that fact could be
	displayed --- flashing text? twinkling lights...:) ?

	@param n The number of articles viewed so far.
     */ 
    static private int recommendedListLength(int n) {
 	    final int maxRecLenTop = 20;
	    //int m =3*n;   
	    int m = (n<=2) ? 3: 2 + n;
	    m = Math.min(m, maxRecLenTop);   
	    return m;
    }
 
    /** Computes a suggestion list based on a single article */
    static private ScoreDoc[] computeArticleBasedList(IndexSearcher searcher, String aid, int maxlen) throws Exception {
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

    /** Generates the list of recommendations based on searching the Lucene
	index for articles whose abstracts are similar to those of the
	articles viewed by the user in this session.

     */
    private void computeRecList(EntityManager em, IndexSearcher searcher) {
	
	try {
	    his = new ActionHistory(em);

	    HashSet<String> exclusions = parent.linkedAids;
	    synchronized (exclusions) {
		exclusions.addAll(his.viewedArticlesAll);
		exclusions.addAll(his.prohibitedArticles);
	    }

	    // abstract match, separately for each article
	    final int maxlen = 100;
	    final int maxRecLen = recommendedListLength(his.viewedArticlesActionable.size());
	    ScoreDoc[][] asr  = new ScoreDoc[his.viewedArticlesActionable.size()][];
	    int k=0;
	    for(String aid: his.viewedArticlesActionable) {
		ScoreDoc[] z = parent.articleBasedSD.get(aid);
		if (z==null) {
		    z = computeArticleBasedList(searcher, aid, maxlen);
		    if (z!=null) parent.articleBasedSD.put(aid,z);
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
		for(ScoreDoc[] z: asr) {
		    if (z!=null && j<z.length) {
			int docno = z[j].doc;
			Integer key = new Integer(docno);
			ArticleRanks r= hr.get(key);
			if (r==null) { 
			    hr.put(key, r=new ArticleRanks(docno));
			}
			r.add(j, z[j].score);		      
		    }
		}
	    }
	    ArticleRanks[] ranked = (ArticleRanks[])hr.values().toArray(new ArticleRanks[0]);
	    Arrays.sort(ranked);

	    Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	    k=1;
	    for(ArticleRanks r: ranked) {
		/*
		ArticleEntry ae = new ArticleEntry(++k, r.aid);
		ae.setScore(r.score);
		*/
		ArticleEntry ae= new ArticleEntry(k, searcher.doc(r.docno),
						  new ScoreDoc(r.docno, (float)r.score));
		if (exclusions.contains(ae.id)) {
		    excludedList += " " + ae.id;
		    continue;
		}
		
		ae.researcherCommline= "L" + id + ":" + k;
		if (parent.sd.sbDebug) ae.ourCommline= ae.researcherCommline;
	 
		entries.add(ae);
		k++;
		if (entries.size()>=maxRecLen) break;
	    }

	    if (parent.sd.sbMergeMode==1) {
		entries = maintainStableOrder1( entries, maxRecLen);
	    } else   if (parent.sd.sbMergeMode==2) {
		entries = maintainStableOrder2( entries, maxRecLen);
	    }


	    sr = new SearchResults(entries); 
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
	list, in their original order
     */
    private Vector<ArticleEntry> maintainStableOrder2( Vector<ArticleEntry> entries, int maxRecLen) {

	if (parent.getSR()==null) return entries;
	Vector<ArticleEntry> previouslyDisplayedEntries =
	    parent.getSR().entries;
	if (previouslyDisplayedEntries==null) return entries;
	HashSet<String> exclusions = parent.linkedAids;

	HashSet<String> old=new HashSet<String>();
	// Old elements (not excluded)
	Vector<ArticleEntry> a = new 	Vector<ArticleEntry>();
	for(ArticleEntry e: previouslyDisplayedEntries) {
	    if (exclusions.contains(e.id)) continue;
	    try {  // We use clone() because we're going to modify e.i
		a.add((ArticleEntry)e.clone());
	    }  catch (CloneNotSupportedException ex) {}
	    old.add(e.id);
	}
	// new elements not found in the old list
	Vector<ArticleEntry> b = new 	Vector<ArticleEntry>();
	for(ArticleEntry e: entries) {
	    if (!old.contains(e.id)) b.add(e);
	}
	double bRatio = b.size() / (double)(b.size() + a.size());
	
	Vector<ArticleEntry> v = new 	Vector<ArticleEntry>();
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
     */
    private void computeRecListTrivial(EntityManager em, IndexSearcher searcher) {
	try {
	    his = new ActionHistory(em);
	    // trivial list: out=in
	    Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	    int k=0;
	    for(String aid:  his.viewedArticlesActionable) {
		ArticleEntry ae = new ArticleEntry(++k, aid);
		ae.setScore(1.0);
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

	@param entries The new suggesion list to be reordered.

	@param maxRecLen The desired length of the suggesion list to
	be returned.

	@return A suggestion list that contains the articles from
	"entries" (and possibly also a small number of
	additional articles from the previously displayed list), reordered
	as per the above rules.

     */
  private Vector<ArticleEntry> maintainStableOrder1( Vector<ArticleEntry> entries, int maxRecLen) {

	if (parent.getSR()==null) return entries;
	Vector<ArticleEntry> previouslyDisplayedEntries =
	    parent.getSR().entries;
	if (previouslyDisplayedEntries==null) return entries;
	HashSet<String> exclusions = parent.linkedAids;

	// Make a list and hashtable of all old elements which are excluded
	HashSet<String> old=new HashSet<String>();
	Vector<ArticleEntry> a = new 	Vector<ArticleEntry>();
	for(ArticleEntry e: previouslyDisplayedEntries) {
	    if (exclusions.contains(e.id)) continue;
	    try {  // We use clone() because we're going to modify e.i
		a.add((ArticleEntry)e.clone());
	    }  catch (CloneNotSupportedException ex) {}
	    old.add(e.id);
	}

	// create a reordered list 
	Vector<ArticleEntry> v = new 	Vector<ArticleEntry>();
	
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
    private void addArticleDetails(IndexSearcher searcher) throws IOException { 
	for(ArticleEntry ae: sr.entries) {
	    ae.populateOtherFields(searcher);
	}	    
    }


    private PresentedList saveAsPresentedList(EntityManager em) {
	PresentedList plist = new PresentedList(Action.Source.SB, null,  parent.sd.getSqlSessionId());
	plist.fillArticleList(sr.entries);	
	em.getTransaction().begin();
	em.persist(plist);
	em.getTransaction().commit();
	return plist;
    }



    /** A human-readable description of what this thread had done. */
    public String description() {
	String s = "Session-based recommendation list produced by thread " + getId() +"; started at " + startTime +", finished at " + endTime;
	if (startTime!=null && endTime!=null) {
	    long msec = endTime.getTime() - startTime.getTime();
	    s += " (" + (0.001 * (double)msec) + " sec)";
	}
	s += ".";
	if (his!=null) {
	    s += " The list is based on " +his.actionCount+ " user actions (" +
		his.articleCount + " viewed articles)";
	}
	return s;
    }

    /** This is used for testing, so that we could quickly view the list of 
	suggestions that would be prepared for a particular article
     */
    public static void main(String [] argv) throws Exception {
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	final int maxlen = 100;
	for(String aid: argv) {
	    System.out.println("Generating suggestion for aid=" + aid);
	    ScoreDoc[] z = SBRGThread.computeArticleBasedList(searcher, aid, maxlen);
	    if (z!=null) {
		//parent.articleBasedSD.put(aid,z);
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

