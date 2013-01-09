package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.bernoulli.*;
import edu.rutgers.axs.indexer.Common;

/** Retrieves and displays a "suggestion list": a list of articles which some
    kind of automatic process has marked as potentially interesting to the user.
 */
public class BernoulliViewSuggestions extends ViewSuggestionsBase {

    public User.Program mode;
    public boolean exploitation = false;
    // FIXME: eventually, we'll have multiple clusters
    final int cluster = 0;

    /** Time horizon = 28 days, as per PF's writeup ver 3 */
    int days=Bernoulli.horizon;

    /** The list for the main page.
     */
    BernoulliViewSuggestions(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	if (error) return; // authentication error?

	EntityManager em = sd.getEM();
	try {
	    
	    if (actorUserName==null) {
		error = true;
		errmsg = "No user name specified!";
		return;
	    }

	    startat = (int)Tools.getLong(request, STARTAT,0);
	    actor=User.findByName(em, actorUserName);


	    folderSize=actor.getFolderSize();

	    mode = (User.Program)getEnum(User.Program.class, MODE, actor.getProgram());
	    if (!mode.needBernoulli()) mode= User.Program.BERNOULLI_EXPLORATION;

	    exploitation = (mode == User.Program.BERNOULLI_EXPLOITATION);

	    //	    days =actor.getDays();
	    //	    if (days<=0) days = Search.DEFAULT_DAYS;
	    days = (int)getLong(DAYS, days); // can override via cmd line
	    
	    final int maxDays=30;
	    
	    if (days < 0 || days >maxDays) throw new WebException("The date range must be a positive number (no greater than " + maxDays+"), or 0 (to mean 'all dates')");
	    initList( startat, em, actor.getCluster());

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	}
    }

    /**
       @param em Just so that we could save the list
     */
    private void initList(int startat, EntityManager em, int cluster) throws Exception {

	IndexReader reader=Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	int M = 10; //page size
	
	// All viewed (and judged) articles are excluded from presentation
	HashMap<String, Action> exclusions = 	    actor.getActionHashMap();

	// eligible candidates: based on categories and date range
	sr = Bernoulli.catSearch(searcher, days);    
	// FIXME: what if there are no BAS for some articles (yet)?
	// score based ordering	
	Bernoulli.sort(sr, em, reader, mode, cluster);
	
	sr.setWindow( searcher, startat, M, exclusions);
	ArticleEntry.applyUserSpecifics(sr.entries, actor);
	
	// In docs to be displayed, populate other fields from Lucene
	for(int i=0; i<sr.entries.size(); i++) {
	    ArticleEntry e = sr.entries.elementAt(i);
	    int docno = e.getCorrectDocno(searcher);
	    Document doc = reader.document(docno);
	    e.populateOtherFields(doc);
	}
	searcher.close();
	reader.close();

	// Save the presented (section of the) suggestion list in the
	// database, and set ActionSource appropriately (to be
	// embedded in the HTML page)
 	Action.Source srcType = 	    exploitation?
	    Action.Source.BERNOULLI_EXPLOITATION :
	    Action.Source.BERNOULLI_EXPLORATION;	   

	PresentedList plist=sr.saveAsPresentedList(em, srcType, actorUserName);
	asrc= new ActionSource(srcType, plist.getId());
    }
		    
}