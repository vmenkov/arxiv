package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

/** An instance of this class represents an inferred "score" measuring
    the importance of a page for a given user, and the list of reasons
    while it is so important.
 */
public class UserPageScore implements Comparable<UserPageScore> {

    /** Article id */
    private String aid;
    public String getArticle() { return aid; }
    /** How important is this page */
    public int score=0;
    public int getScore() { return score; }
    /** Why is it so important */
    public Vector<Action> reasons=new Vector<Action>();

    /** Numeric values (used for ranking purposes) of judgment buttons */
    private static EnumMap< Action.Op, Integer> jvMap = new EnumMap< Action.Op, Integer>(Action.Op.class);    
    private static EnumMap< Action.Op, Integer> vvMap = new EnumMap< Action.Op, Integer>(Action.Op.class);
    static {
	jvMap.put( Action.Op.INTERESTING_AND_NEW, new Integer(200));
	jvMap.put( Action.Op.INTERESTING_BUT_SEEN_TODAY, new Integer(150));
	jvMap.put( Action.Op.INTERESTING_BUT_KNOWN, new Integer(100));
	jvMap.put( Action.Op.USELESS, new Integer(-200));
	jvMap.put( Action.Op.DONT_SHOW_AGAIN, new Integer(-50));

	vvMap.put( Action.Op.VIEW_ABSTRACT,  new Integer(10));
	vvMap.put( Action.Op.VIEW_FORMATS,  new Integer(20));
	for(Action.Op q: Action.Op.VIEW_ARTICLE_BODY_TYPES) {
	    vvMap.put( q,  new Integer(30));
	}
    }

    

    /** Computes the "score" of a page for a user based on the user's
	actions for that page. The score is computed as the sum of
	terms determined by the following factors:

	@param actions A sorted (in ascending date order) array of the
	user's actions with respect to the page.

	<ol>
	<li>Current inclusion in the folder (if included)  
	<li>Most recent explicit judgment (or the "rate" type, or "don't show")	
	<li>The "strongest" view type
	</ol>

	@param outReasons An output parameter: the actions that contributed to the score
    */
    public UserPageScore(String _aid, Vector<Action> actions) {
	aid= _aid;

	boolean inFolder=false;
	int lastJudgmentValue = 0; 
	int maxViewValue = 0;
	Action fReason=null, jReason=null, vReason=null;

	for(Action a: actions) {
	    Action.Op op = a.getOp();
	    if (op == Action.Op.COPY_TO_MY_FOLDER) {
		inFolder=true;
		fReason = a;
	    } else if (op == Action.Op.REMOVE_FROM_MY_FOLDER) {
		inFolder=false;
		fReason = null;
	    } 

	    Integer jv = jvMap.get( op );
	    if (jv != null) {
		lastJudgmentValue = jv.intValue();
		jReason = a;
	    }
	
	    Integer vv = vvMap.get( op);
	    if (vv != null && vv.intValue() >= maxViewValue) {
		maxViewValue=vv.intValue();
		vReason = a;
	    }
	}

	// How important?
	score= (inFolder ? 1000 : 0) + lastJudgmentValue + maxViewValue;
	// Why important?
	if (fReason!=null) reasons.add(fReason);
	if (jReason!=null) reasons.add(jReason);
	if (vReason!=null) reasons.add(vReason);
    }
    
    /** For descending sort based on the score (and the timestamp of the most
     * important contributing action as the secondary key).
     */
    public int compareTo(UserPageScore other) {
	int d = other.score - score;
	return (d==0 && reasons.size()>0 && other.reasons.size()>0) ?
	    other.reasons.elementAt(0).compareTo(reasons.elementAt(0)) : d;
    }

    static public UserPageScore[] rankPagesForUser(User actor) {
	return rankPagesForUserSince(actor, 0);
    }

    /** Generates an ordered list of pages with which the user has interacted.

	@param id0 Only actions with ids greater than this are taken
	into account.

	@return A list of pages with their scores, ordered (ranked) by
	score, in descending order. Some scores may be negative.	
     */
    static public UserPageScore[] rankPagesForUserSince(User actor, long id0) {
	HashMap<String,Vector<Action>> ahm=actor.getAllActionsSince(id0);
	Vector<UserPageScore> vups = new Vector<UserPageScore>();
	for(String aid: ahm.keySet()) {
	    UserPageScore q	 = new UserPageScore(aid, ahm.get(aid));
	    if (q.score!=0) vups.add(q);
	}

	UserPageScore[] ups = vups.toArray(new  UserPageScore[0]);
	// descending score order
	Arrays.sort(ups);
	return ups;
    }  



}