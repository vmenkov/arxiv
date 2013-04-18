package edu.rutgers.axs.recommender;

import java.util.*;

import edu.rutgers.axs.sql.*;

/** A single-word summary of the sense of the user's feedback
    represented by a given chronological sequence of operation(s).
    This is used during processing user feedback in the 3PR (aka PPP)
    recommendation engine.  */
enum PPPActionSummary {
    PROMOTED, VIEWED, DEMOTED;
    
    /** Summarizes a sequence of operations. An explicit judgment, a
	folder addition, or a "don't show" request all trump simple
	views; among these operations (summarized as PROMOTED or
	DEMOTED), more recent operations supersede preceding ones. If
	no such operations took place, but the user viewed the
	document, that's treated as VIEWED.
    */
    static PPPActionSummary summarize( Vector<Action> v) {
	PPPActionSummary result = null;
	for(int j=v.size()-1; j>=0; j--) {
	    Action.Op op = v.elementAt(j).getOp();
	    int val = UserPageScore.pppValue(op);
	    if (val==0) continue;
	    else if (val<0) {
		return DEMOTED;
	    } else if (val>=100) {
		return PROMOTED;
	    } else if (val>0) {
		result = VIEWED;
	    }
	}
	return result;
    }
};
    