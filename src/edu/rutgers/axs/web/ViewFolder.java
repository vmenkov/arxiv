package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.html.RatingButton;

/** Retrieves the list of artciles in the user's personal folder
 */
public class ViewFolder extends ResultsBase {
    
    public final String actorUserName;
    public User actor;

    public Vector<Action> list = new Vector<Action>();
    public Vector<ArticleEntry> entries = new Vector<ArticleEntry>();

    final public String USER_NAME = "user_name";

    public ViewFolder(HttpServletRequest _request, HttpServletResponse _response, boolean self) {
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

	    IndexSearcher s=null; 

	    try {
		s =  new IndexSearcher( Common.newReader() );
	    } catch(Exception ex) {}

	    int cnt=0;

	    HashMap<String, Action> folder = actor.getFolder();

	    for (Action m : folder.values()) {
		list.add(m);
		ArticleEntry e=
		    ArticleEntry.getArticleEntry( s, m.getArticle(), cnt+1);
		entries.add(e); 
		cnt++;
	    }

	    ArticleEntry.applyUserSpecifics(entries, actor);

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

    /** Overrides the method in ResultsBase */
    void customizeSrc() {
	asrc= new ActionSource( Action.Source.FOLDER, 0);
    }


    public String resultsDivHTML(ArticleEntry e) {
	return resultsDivHTML( e, true,  RatingButton.NEED_RM_FOLDER);
    } 

}
