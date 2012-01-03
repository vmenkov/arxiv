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
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import edu.cornell.cs.osmot.options.Options;


import edu.rutgers.axs.sql.*;

/** Retrieves the list of artciles on which the user performed various actions,
    and ranks them based on these actions.
 */
public class ViewRankedPages extends ResultsBase {
    
    public final String actorUserName;
    public User actor;

    public UserPageScore ups[] = new  UserPageScore[0];
    public Vector<ArticleEntry> entries = new Vector<ArticleEntry>();

    final public String USER_NAME = "user_name";

    public ViewRankedPages(HttpServletRequest _request, HttpServletResponse _response, boolean self) {
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

	    // descending score order
	    ups =  UserPageScore.rankPagesForUser(actor);

	    IndexSearcher s = null;
	    try {
		Directory indexDirectory =  
		    FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
		s = new IndexSearcher( indexDirectory);
	    } catch(Exception ex) {}

	    for(int cnt=0; cnt<ups.length; cnt++) {
		ArticleEntry e=
		    ArticleEntry.getArticleEntry(s, ups[cnt].getArticle(), cnt+1);
		entries.add(e); 
	    }
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

    

}
