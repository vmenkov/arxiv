package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;

/** Retrieves the action history of a user.
 */
public class ViewActions extends ResultsBase {
    
    public final String actorUserName;
    public User actor;

    public Vector<Action> list = new Vector<Action>();
    public Vector<EnteredQuery> qlist = new Vector<EnteredQuery>();

    /** List scrolling */
    public int startat = 0;

    static final int M = 25;


    public ViewActions(HttpServletRequest _request, HttpServletResponse _response, boolean self) {
	super(_request,_response);

	actorUserName = self ? user :  getString(USER_NAME, null);
	startat = (int)Tools.getLong(request, STARTAT,0);

	EntityManager em = sd.getEM();
	try {

	    if (actorUserName==null) throw new WebException("No user name specified!");

	    actor = User.findByName(em, actorUserName);
  
	    if (actor == null) {
		error = true;
		errmsg = "No user with user_name="+ actorUserName+" has been registered";
		return;
	    }

	    // reverse order. There is an assumption here that the
	    // elements of the set will be retrieved in the ID order.
	    Action[] ar =actor.getActions().toArray(new Action[0]); 
	    for (int i=ar.length-1; i>=0; i--) {
		list.add(ar[i]);
	    }
	    EnteredQuery[] eqr = actor.getQueries().toArray(new EnteredQuery[0]); 
	    for (int i=eqr.length-1; i>=0; i--) {
		qlist.add(eqr[i]);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

    /** Links to prev/next pages; only applies to the "entries" array */
    public int prevstart, nextstart;
    public boolean needPrev=false, needNext=false;
    

    /** This is only loaded if you call loadArticleInfo(). */
    public Vector<ArticleEntry> entries = new Vector<ArticleEntry>();

    /** Get ArticleEntry instances for all pages, for more detailed
     * and user-friendly display,
     */
    public void loadArticleInfo() {
	if (error) return; // won't do!
	EntityManager em = sd.getEM();
	try {

	    actor = User.findByName(em, actorUserName);
	    IndexSearcher s=  new IndexSearcher( Common.newReader() );
	    int cnt=0;

	    prevstart = Math.max(startat - M, 0);
	    nextstart = startat + M;
	    needPrev = (prevstart < startat);
	
	    for( Action m:  list) {
		if (cnt < startat) {
		    // skip
		} else if (cnt < startat + M) {
		    ArticleEntry e=
			ArticleEntry.getArticleEntry(s, m.getAid(), list.size()-cnt);
		    // A somewhat cludgy way of presenting the added-to-folder date
		    e.appendComment( "(Your action: '" + m.getOp() + "' at " + m.getTime() + ")");
		    entries.add(e); 
		} else {
		    needNext = true;
		    break;
		}
		cnt++;       
	    }


	    // Mark pages currently in the user's folder, or rated by the user
	    ArticleEntry.markFolder(entries, actor.getFolder());
	    ArticleEntry.markRatings(entries, actor.getActionHashMap(Action.ratingOps));

	    // Do NOT remove "excluded" pages for this display!
	    //ArticleEntry.applyUserSpecifics(entries, actor);

 

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }


    /** Overrides the method in ResultsBase */
    void customizeSrc() {
	asrc= new ActionSource( Action.Source.HISTORY, 0);
    }



    public String resultsDivHTML(ArticleEntry e) {
	return resultsDivHTML( e, true, RatingButton.NEED_FOLDER);
    } 
}
