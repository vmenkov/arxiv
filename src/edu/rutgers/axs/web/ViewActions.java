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

    public ViewActions(HttpServletRequest _request, HttpServletResponse _response, boolean self) {
	super(_request,_response);

	actorUserName = self ? user :  getString(USER_NAME, null);

	EntityManager em = sd.getEM();
	try {

	    if (actorUserName==null) throw new WebException("No user name specified!");

	    actor = User.findByName(em, actorUserName);
  
	    if (actor == null) {
		error = true;
		errmsg = "No user with user_name="+ actorUserName+" has been registered";
		return;
	    }

	    for (Action m : actor.getActions()) {
		list.add(m);
	    }
	    for (EnteredQuery m : actor.getQueries()) {
		qlist.add(m);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

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
	    for( Action m:  list) {
		ArticleEntry e=
		    ArticleEntry.getArticleEntry( s, m.getArticle(), cnt+1);
		// A somewhat cludgy way of presenting the added-to-folder date
		e.appendComment( "(" + m.getOp() + " at " + m.getTime() + ")");
		entries.add(e); 
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
