package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.commons.lang.mutable.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.ArticleEntry;

/** Part of the 3PR (aka PPP) algorithm, as per TJ's Coactive Learning paper,
    with additional provisions for document promotions and demotions.

    <p> A PPPFeedback instance is a hash map which stores, for each
    relevant article ID, a PPPActionSummary object that contains the
    summary of relevant user actions on the article in question.
 */
class PPPFeedback extends HashMap<String,PPPActionSummary> {	
    final int sugListId;
    private long lastActionId = 0;
    long getLastActionId() { return lastActionId; }
     
    /** Creates an object that describes a user's feedback on a
	particular suggestion list. A valid  PPPFeedback is always created
	by this constructor, but if the user did not carry out any
	"useful" actions on the particular suggestion list, the 
	produced PPPFeedback will be an empty hash map.

	@param actor The user whose actions we study
	@param  sugListId DataFile.getId() for the appropriate sugg list
    */
    PPPFeedback(EntityManager em, User actor, int _sugListId) {
	super();
	sugListId =  _sugListId;
	User.ActionListTable alTable = new User.ActionListTable();
	
	Set<Action> actions = actor.getActions();
	AcceptMap plid2accept= new AcceptMap(sugListId);
	for(Action a: actions) {
	    lastActionId = Math.max( a.getId(), lastActionId);
	    String aid = a.getAid();
	    if (aid==null) continue; // skip PREV_PAGE etc
	    if (!plid2accept.accept(em, a))  continue; // wrong sug list
	    alTable.add(aid,a);
	}
	fillMap(alTable);
    }

    private PPPFeedback(int _sugListId, User.ActionListTable alTable ) {
	super();
	sugListId =  _sugListId;
	fillMap(alTable);
	lastActionId = alTable.getLastActionId();
    }
 
    private void fillMap(User.ActionListTable alTable) {
 	for(String aid: alTable.keySet()) {
	    PPPActionSummary q = PPPActionSummary.summarize(alTable.get(aid));
	    if (q==null) {
		Logging.warning("No useful actions found for page " + aid);
	    } else {
		put( aid, q);
	    }
	}
    }
   
    boolean wasViewed(String aid) {
	PPPActionSummary q = get(aid);
	return q!=null && q==PPPActionSummary.VIEWED;
    }
    boolean wasViewedOrPromoted(String aid) {
	PPPActionSummary q = get(aid);
	return q!=null && (q==PPPActionSummary.VIEWED || q==PPPActionSummary.PROMOTED);
    }
    boolean wasPromoted(String aid) {
	PPPActionSummary q = get(aid);
	return q!=null &&  q==PPPActionSummary.PROMOTED;
    }
    boolean wasDemoted(String aid) {
	PPPActionSummary q = get(aid);
	return q!=null &&  q==PPPActionSummary.DEMOTED;
    }

     /** An AcceptMap is used to hash information as to whether a
	 particular Action belongs with a given suggestion list.
	 The underlying structure for an AcceptMap is a HashMap that
	 maps PresentedList ids to booleans that indicated
	 whteher the PresentedList belongs to the given suggestion list.
     */
     private static class AcceptMap extends HashMap<Long, Boolean> {
	 private final int  sugListId;
	 AcceptMap(int  _sugListId) {
	     super();
	     sugListId = _sugListId;
	 }
	 /** Checks if the specified action belongs to the sug list, and
	     caches this information.
	 */
	 boolean accept(EntityManager em, Action a) {
	    long plid = a.getPresentedListId();
	    if (plid==0) return false;
	    Long key = new Long(plid);
	    Boolean val = this.get(key);
	    if (val==null) {
		PresentedList pl = (PresentedList)em.find(PresentedList.class, plid);
		val = new Boolean(pl!=null && pl.getDataFileId()==sugListId);
		this.put(key, val);
	    }
	    return val.booleanValue();
	}
     }

     /** A mutable aid-to-real-value map */
     static class CoMap extends HashMap<String,MutableDouble> { 
	 synchronized void addCo(String aid, double inc) {
	     MutableDouble z=get(aid);
	     if (z==null) {
		 put(aid,  new MutableDouble(inc));
	     } else {
		 z.add(inc);
	     }
	 }
     }

     private static boolean areUnique(Vector<ArticleEntry> entries) {     
	 HashSet<String> h=new  HashSet<String>();
	 for(ArticleEntry e: entries) {
	     if (e==null) continue;
	     String aid = e.id;
	     if (h.contains(aid)) return false;
	     h.add(aid);
	 }
	 return true;
     }

    /** Converts the user feedback summary into a list of coefficients
	for a Rocchio-style update.

	@param entries0 The original suggestion list
	@param actionSummary Summary of user feedback on that list
     */
    HashMap<String,MutableDouble> getRocchioUpdateCoeff( boolean topOrphan, final Vector<ArticleEntry> entries0) {

	CoMap updateCo = new CoMap();

	Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	for(ArticleEntry ae: entries0) { 
	    entries.add(ae); 
	}
	if (!areUnique(entries)) throw new IllegalArgumentException("Not unique A");
	// 1. feedback from VIEWED
	for(int j=( topOrphan? 1 : 0); j+1 < entries.size(); j+=2) {
	    String aid1 = entries.elementAt(j).id;
	    String aid2 = entries.elementAt(j+1).id;
	    if  (wasViewedOrPromoted(aid2) && !wasViewedOrPromoted(aid1)) {
		// swap
		ArticleEntry q =  entries.elementAt(j);
		entries.set(j, entries.elementAt(j+1));
		entries.set(j+1, q);
	    } else if (wasViewedOrPromoted(aid1)&& !wasViewedOrPromoted(aid2)){
		// positive feedback, as proposed by TJ 2013-06-26
		// Here, we can't work via reordering, so we
		// compute additions immediately
		double inc = UserProfile.getGamma(j)-UserProfile.getGamma(j+1);
		updateCo.addCo( aid1, inc);
		updateCo.addCo( aid2, inc);
	    }
	}

	if (!areUnique(entries)) throw new IllegalArgumentException("Not unique B");
	// 2. promotions
	// entries2 will contain the promoted articles, and entries2b, the rest
	Vector<ArticleEntry> entries2 =  new Vector<ArticleEntry>(),
	    entries2b =  new Vector<ArticleEntry>();
	for(ArticleEntry ae: entries) {
	    String aid = ae.id;
	    (wasPromoted(aid) ? entries2 : entries2b).add(ae);
	}

	if (!areUnique(entries2)) throw new IllegalArgumentException("Not unique C");

	if (!areUnique(entries2b)) throw new IllegalArgumentException("Not unique D");


	// 3. demotions
	int Q = 10; // how far down do we move demoted articles
	// rev2 will contain all non-promoted articles, in reverse order, with the demoted ones moved appropriately
	Vector<ArticleEntry> rev2 =  new Vector<ArticleEntry>();

	for(int j=entries2b.size()-1; j>=0 && j>=entries2b.size()-Q; j--) {
	    ArticleEntry ae = entries2b.elementAt(j);
	    if (wasDemoted(ae.id)) {
		rev2.add(ae);
		entries2b.set(j, null);
	    }
	}
	for(int j=entries2b.size()-1; j>=0; j--) {
	    if (j>=Q) {
		int zj = j-Q;
		ArticleEntry zae = entries2b.elementAt(zj);
		if (zae != null && wasDemoted(zae.id)) {
		    rev2.add(zae);
		    entries2b.set(zj, null);
		}
	    }
	    ArticleEntry ae = entries2b.elementAt(j);
	    if (ae!=null) {
		rev2.add(ae);
	    }
	}
	if (!areUnique(rev2)) throw new IllegalArgumentException("Not unique E");
	// merge (in reverse), into entries2
	for(int j=rev2.size()-1; j>=0; j--) {	    
	    entries2.add(rev2.elementAt(j));
	}
	if (!areUnique(entries2)) throw new IllegalArgumentException("Not unique F");
	if (entries2.size() != entries0.size()) {
	    throw new AssertionError("Error in list reordering: size " +entries2.size()+ " != " + entries0.size());
	}
	HashMap<String,Integer> aid2newrank = new HashMap<String,Integer>();
	for(int j=0; j<entries2.size(); j++) {
	    String aid = entries2.elementAt(j).id;
	    if (aid2newrank.get(aid)!=null) {
		throw new AssertionError("Error in list reordering: duplicate aid=" + aid);
	    }
	    aid2newrank.put(aid, new Integer(j));	    
	}	

 	for(int j=0; j<entries0.size(); j++) {
	    String aid = entries0.elementAt(j).id;
	    int jNew = aid2newrank.get(aid).intValue();
	    if (jNew != j) {
		double inc =UserProfile.getGamma(jNew)-UserProfile.getGamma(j);
		updateCo.addCo( aid, inc);
	    }
	}
	return updateCo;
   }

    /** Scans the entire history of the user's interaction with the
	system; selects usable feedback, and splits it by suggestion
	list id. This method is used in retroactive profile generation.
	
       @return An array of PPPFeedback objects, one per suggestion list,
       ordered chronologically (actually, by suggestion list DataFile id)
     */
    static PPPFeedback[] allFeedbacks(EntityManager em, User actor) {
	// 0 is stored to mean "presented list is no good"
	HashMap<Long,Long> plid2dfid = new HashMap<Long,Long>();
	final Long zero = new Long(0);

	// sug list ID to ActionListTable
	HashMap<Long,User.ActionListTable> alTables = new HashMap<Long,User.ActionListTable>();
	
	Set<Action> actions = actor.getActions();
	for(Action a: actions) {
	    //lastActionId = Math.max( a.getId(), lastActionId);
	    String aid = a.getAid();
	    if (aid==null) continue; // skip PREV_PAGE etc

	    long plid = a.getPresentedListId();
	    if (plid==0) continue;
	    Long key = new Long(plid);
	    Long df = plid2dfid.get(key);
	    if (df==null) {
		PresentedList pl=(PresentedList)em.find(PresentedList.class,plid);
		if (pl.getType()== Action.Source.MAIN_SL ||
		    pl.getType()== Action.Source.EMAIL_SL) {
		    df = new Long( pl.getDataFileId());
		} else {
		    df=zero;
		}
		plid2dfid.put(key, df);
	    } 
	    if (df.intValue()==0) continue; // wrong type of sug list
	    
	    User.ActionListTable alTable = alTables.get(df);
	    if (alTable==null) alTables.put(df, alTable=new User.ActionListTable());
	    alTable.add(aid,a);
	}

	Long[] sugListIds = (Long[])alTables.keySet().toArray(new Long[0]);
	Arrays.sort(sugListIds);
	PPPFeedback[] results = new PPPFeedback[sugListIds.length];
	int cnt=0;
	for(Long df:  sugListIds) {
	    results[cnt++] = new PPPFeedback(df.intValue(), alTables.get(df));
	}
	return results;
    }
   

}