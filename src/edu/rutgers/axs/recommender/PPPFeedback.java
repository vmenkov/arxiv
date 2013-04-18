package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.ArticleEntry;

/** Part of the 3PR (aka PPP) algorithm, as per TJ's Coactive Learning paper,
 * with additional provisions for document promotions and demotions.
 */
class PPPFeedback extends  HashMap<String,PPPActionSummary> {	
     private long lastActionId = 0;
     long getLastActionId() { return lastActionId; }
     

/** Analyzes feedback on a particular suggestion list
	 @param  sugListId DataFile.getId() for the appropriate sugg list
     */
     PPPFeedback(EntityManager em, User actor, int sugListId) {
	 super();
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

     /** Is used to hash information as to whether a particular Action
	 belongs with a given suggestion list */
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
    
 
    /** Converts the user feedback summary into a list of coefficients
     * for a Rocchio-style update

	@param entries The original suggestion list
	@param actionSummary Summary of user feedback on that list
     */
    HashMap<String,Double> getRocchioUpdateCoeff( boolean topOrphan, final Vector<ArticleEntry> entries0) {
	Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
	for(ArticleEntry ae: entries0) { entries.add(ae); }
	// 1. feedback from VIEWED
	for(int j=( topOrphan? 1 : 0); j+1 < entries.size(); j+=2) {
	    String aid1 = entries.elementAt(j).id;
	    String aid2 = entries.elementAt(j+1).id;
	    if  (wasViewedOrPromoted(aid2) && !wasViewedOrPromoted(aid1)) {
		// swap
		entries.set(j, entries0.elementAt(j+1));
		entries.set(j+1, entries0.elementAt(j));
	    }
	}
	// 2. promotions
	// entries2 will contain the promoted articles, and entries2b, the rest
	Vector<ArticleEntry> entries2 =  new Vector<ArticleEntry>(),
	    entries2b =  new Vector<ArticleEntry>();
	for(ArticleEntry ae: entries) {
	    String aid = ae.id;
	    (wasPromoted(aid) ? entries2 : entries2b).add(ae);
	}
	// 3. demotions
	int Q = 10; // how far down do we move demote articles
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
	// merge (in reverse), into entries2
	for(int j=rev2.size()-1; j>=0; j--) {	    
	    entries2.add(rev2.elementAt(j));
	}
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
	HashMap<String,Double> updateCo = new HashMap<String,Double>();
 	for(int j=0; j<entries0.size(); j++) {
	    String aid = entries0.elementAt(j).id;
	    int jNew = aid2newrank.get(aid).intValue();
	    if (jNew != j) {
		updateCo.put(aid, new Double(UserProfile.getGamma(jNew) -UserProfile.getGamma(j)));
	    }
	}
	return updateCo;
   }

}