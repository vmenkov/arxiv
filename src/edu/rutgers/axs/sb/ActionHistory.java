package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import javax.persistence.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;

/** An auxiliary class representing the list of articles viewed or
    "rated" by the user in this session so far. (Of course, if this is
    an anonymous session, there is only one type of "ratings"
    collected and recorded: "Don't show again").

    <P>Among all viewed articles, we identify a subset of "actionable"
    ones.  This includes those articles that have been viewed in any
    context other than the "main page" (the main suggestion list) and
    its derivatives. This restriction is as per the conference call on
    2014-05-20; its purpose it to ensure the "orthogonality" of the
    two rec lists (the main [nightly] rec list and the SBRL).

    <p> FIXME: one can save on this one SQL call by storing the
    list in the SessionData object; but we already make so many
    SQL calls anyway...
    */
class ActionHistory {

    /** The number of actions in the session so far      */
    int actionCount=0;
    /** The number of viewed articles in the session so far. (It may be
	smaller than actionCount, because the user may have viewed the
	same page multiple times). */
    int articleCount=0;
    
    /** Lists  all viewed articles (both actionable and not) */
    Vector<String> viewedArticlesAll = new Vector<String>();
    /** Lists viewed articles that are "actionable" (i.e., based on which we
	will compute suggestions). The array is in the reverse chronological 
	order (most recent first), so that we could easily report "age". */
    Vector<String> viewedArticlesActionable = new Vector<String>();
    /** Lists  "prohibited" articles (don't show) */
    Vector<String> prohibitedArticles = new Vector<String>();


    /** Retrieves and processes the session's entire list of actions
	from the SQL database.

	@param sqlSessionID  Session ID (obtained with SessionData.getSqlSessionId())
     */
    ActionHistory(EntityManager em, long sqlSessionID) {
	Vector<Action> va = Action.actionsForSession( em, sqlSessionID);
	actionCount = va.size();

	// lists of viewed articles; a true value is stored
	// for "actionable" ones
	HashMap<String, Boolean> viewedH = new HashMap<String, Boolean>();
	// lists of "prohibited" articles
	HashSet<String> prohibitedH = new HashSet<String>();
	
	// scan in the reverse order, with the most recent actions first 
	for(int i=va.size()-1; i>=0; i--) {
	    Action a = va.elementAt(i);
	    Article art = a.getArticle();
	    // Ignore non-article-specific actions such as "NEXT PAGE" etc
	    if (art==null) continue;
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
		
		if (val==null) {
		    viewedArticlesAll.add(aid);
		}
		
		boolean a0 = (val!=null && val.booleanValue());
		boolean a1 = (a0  || actionable);
		if (a1 && !a0) {
		    viewedArticlesActionable.add(aid);
		}
		
		viewedH.put(aid, new Boolean(a1));
	    }
	}
	    
	articleCount=viewedArticlesActionable.size();
    }

    /** Creates an empty history, which can be later augmented 
	incrementally */
    ActionHistory() {
    }

    /** Adds one more action to the history. Ensures that there are
	no duplicates.

	<p>This method should be called every time a new action is
	recorded in this session.
	
	<p>FIXME: inserting into and deleting from arrays is very inefficient.
	Let's use hash tables instead...
     */
    void augment(Action a) {
	actionCount++;
	Article art = a.getArticle();
	// Ignore non-article-specific actions such as "NEXT PAGE" etc
	if (art==null) return;
	String aid = art.getAid();
	Action.Op op=a.getOp();

	if (op.isHideSB()) {
	    if (prohibitedArticles.contains(aid)) return;
	    prohibitedArticles.add(aid);
	    viewedArticlesAll.removeElement( aid);
	    viewedArticlesActionable.removeElement( aid);
	} else {
	    Action.Source src = a.getSrc();
	    boolean actionable = (src!=null && !src.isMainPage());
	    if (actionable) { //reposition to the beginning
		viewedArticlesActionable.removeElement( aid);
		viewedArticlesActionable.insertElementAt( aid, 0);
	    }
	    viewedArticlesAll.removeElement( aid);
	    viewedArticlesAll.insertElementAt( aid, 0);
	}

	articleCount=viewedArticlesActionable.size();
    }


}
