package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.search.IndexSearcher;

import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;


/** This servlet processes the user's "judgment" (explicit feedback) about an
    article. A "judgment" results from the user's clicking on one of
    the action buttons, such as rating an article, "copying" it in
    one's personal folder, asking the server not to show it again,
    etc.

    <p>This servlet also processes the REORDER feedback (user-initiated
    list reordering), introduced by David Desimone in the summer of
    2014.

    <p>A call to JudgmentServlet normally results in an Action object
    being created in the database. Sometimes other data objects are
    created as well: specifically, a REORDER event causes a
    PresentedList object to be created, to hold the reordered article
    list.

    <p> The "page" returned by this servlet is not actually displayed
    to the user, because this servlet is invoked asynchronously (with
    something like jQuery's $.get(url); see
    http://api.jquery.com/jQuery.get/ ). However, the return text may
    be a piece of JavaScript, which the caller may choose to evaluate
    (function "eval" inside the get()), in order to dynamically update
    the appearance of the page (e.g., show the current user folder
    size).

 */
public class JudgmentServlet extends BaseArxivServlet {

    /** Can be used for the REORDER action */
    static public final String PREFIX = "prefix";

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);
	judgmentServletRequestCnt++;

	Action.Op op = (Action.Op)Tools.getEnum(request, Action.Op.class,
					 ACTION, Action.Op.NONE);	 
	ActionSource asrc = new ActionSource(request);

	EntityManager em = null;
	try {

	    SessionData sd =  SessionData.getSessionData(request);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext());
	    String user = sd.getRemoteUser(request);

	    em = sd.getEM();

	    User u= (user!=null) ? User.findByName(em, user) : null;
		
	    //   /arxiv/JudgmentServlet?id=1406.2398&action=INTERESTING_AND_NEW&src=MAIN_MIX&pl=3456

	    // /arxiv/JudgmentServlet?id=0805.2417&action=INTERESTING_BUT_KNOWN&src=SB&pl=3463

	    // /arxiv/JudgmentServlet?prefix=xxx-&id=xxx-q-bio/0611055:xxx-q-bio/0701040:xxx-0904.1959&action=REORDER&src=SB&pl=3463

	    Logging.info("JudgmentServlet: op=" + op + ", " + ID+ "=" +  request.getParameter(ID));

	    if (op==Action.Op.REORDER) {
		// supplies a column-separated list of IDs (maybe with prefixes)
		String prefix = request.getParameter(PREFIX);
		String ids = request.getParameter(ID);
		if (ids==null) throw new WebException("No article id list supplied");
		String q[] = ids.split(":");
		if (q.length==0) throw new WebException("Empty article id list supplied");
		// remove prefix (if any) from each id, and decode special chars
		for(int i=0; i<q.length; i++) {
		    q[i] = ArticleEntry.extractAidFromResultsTableId(q[i], prefix);
		}
	
		// Begin a new local transaction so that we can persist a new entity	
		em.getTransaction().begin();
		Action a= sd.addNewAction(em, u, op, null, q, asrc);
		//em.persist(u);	       
		em.getTransaction().commit(); 
		
	    } else if (op!=Action.Op.NONE) {
		String id = request.getParameter(ID);
		if (id==null) throw new WebException("No aticle id supplied");
		// Record the user's desire not to see it page again. This is only used in SB.
		if (op.isHideSB()) {
		    Logging.info("Marking page " + id + " as one to be hidden in session " +  sd.getSqlSessionId());
		    sd.recordLinkedAid(id);
		}
		
		// Begin a new local transaction so that we can persist a new entity	
		em.getTransaction().begin();
		// record the action and the new PresentedList object
		Action a= sd.addNewAction(em, u, op, id, null, asrc);
		//em.persist(u);	       
		em.getTransaction().commit(); 
	    }

	    // start SB computation, if appropriate. (This is mostly useful
	    // for Op.EXPAND_ABSTRACT)
	    if (sd.sbrg!=null) sd.sbrg.sbCheck();

	    String js=(u!=null)? responseJS(sd, em,u,op,asrc.presentedListId) : "";

	    em.close();

	    // It seems like the "Expand" button works better with text/plain!

	    response.setContentType("text/plain");
	    //response.setContentType("application/javascript");

	    OutputStream aout = response.getOutputStream();
	    PrintWriter w = new PrintWriter(aout);

	    w.println(js);
	    w.close();

	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in ArticleServer: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	    ResultsBase.ensureClosed( em, false);
	}

    }

    /** Generates JS code that will be sent back to the client to be
	executed there. There are three situations when we need to do
	it: 

	(a) Folder size changed.

	(b) Page not-quite-reload in Chrome (it happens when you use
	the "Back" button in the browser), when we need to make sure
	that all recent activity is properly reflected. This is
	triggered by Action.Op.NONE, from
	ResultsBase.refreshEverythingJsCode()

	(c) On "EXPAND_ABSTRACT" action, activating (possibly delayed) reload
	in the SB popup window.

	FIXME: (c) probably should also apply to main-window rating
	ops.  The proper criterion should involve the operation's
	countext (Action.Src).

     */
    private String responseJS(SessionData sd, EntityManager em, User u, Action.Op op, long presentedListId ) {
	String js="";
	RatingButton [] buttons = RatingButton.chooseRatingButtonSet(u.getProgram());
      
	Logging.info("JudgmentServlet.responseJS(op=" + op+")");
	try {

	    if (op!=Action.Op.EXPAND_ABSTRACT) {
		// it is important not to activate this code
		// when it is not needed, so that it won't 
		// trigger a JS error.
		int fs = u.getFolderSize();
		String q = "("+fs+")";
		js += //"alert(' +"+q+"'); "+
		    "setFolderSize('" +q+ "');\n";
	    }

	    if (op == Action.Op.NONE && presentedListId>0) {
		// the "refresh everything" routine for Chrome

		HashMap<String, Action> exclusions = u.listExclusions();


		PresentedList plist = (PresentedList)em.find( PresentedList.class, presentedListId);
		IndexSearcher searcher=  new IndexSearcher( Common.newReader() );
		Vector<ArticleEntry> entries = plist.toArticleList(null, searcher);
		//  ArticleEntry.applyUserSpecifics(entries, u); // don't do this - it will actually remove some entries!
		
		// Mark pages currently in the user's folder, or rated by the user
		ArticleEntry.markFolder(entries, u.getFolder());
		ArticleEntry.markRatings(entries, u.getActionHashMap(Action.ratingOps));
 

		User.Program program = u.getProgram();
		
		int cnt=0;
		for(ArticleEntry e: entries) {
		    boolean hidden = exclusions.containsKey(e.id);
		    if (hidden) {
			js += e.hideJS(false) +"\n";
			cnt++;
			continue;
		    }
		    for(RatingButton b: buttons) {
			boolean checked= e.buttonShouldBeChecked(b.op);
			String sn = b.sn(e);	 	
			if (checked) {
			    js += "flipCheckedOn('#"+sn+"');\n";
			    cnt++;
			} else {
			    js += "flipCheckedOff('#"+sn+"');\n";
			    cnt++;
			}
		    }
		}
	    } else if (op==Action.Op.EXPAND_ABSTRACT && sd.sbrg!=null) {
		// see if we need to load the SB popup
		js += sd.sbrg.mkJS(getContextPath());
		//js += "alert('Running response code!'); ";
	    }

	    Logging.info("JudgmentServlet.responseJS(op=" + op+") : " + js);

	} catch(Exception ex) {
	    ex.printStackTrace(System.out);
	    Logging.error("JudgmentServlet.responseJS: " + ex);
	}	
	return js;
    }
    

     /** Returns a URL for this servlet */
    //static String mkUrl(String cp, String id, Action.Op op) {
    //	return cp + "/ArticleServlet?" +  ID +"="+id + "&"+ ACTION+ "="+op;
    //}

}
