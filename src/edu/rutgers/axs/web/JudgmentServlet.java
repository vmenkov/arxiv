package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.search.IndexSearcher;

import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.*;


/** This servlet the user's "judgment" (explicit feedback) about an
    article. A "judgment" results from the user's clicking on one of
    the action buttons, such as rating an article, "copying" it in
    one's personal folder, asking the server not to show it again,
    etc.

    <p>
    The "page" returned by this servlet is not actually
    displayed to the user, because this servlet is invoked
    asynchronously (with something like jQuery's $.get(url); see
    http://api.jquery.com/jQuery.get/ ). However, the return text may be
    a piece of JavaScript, which the caller may choose to evaluate
    in order to dynamically update the appearance of the page (e.g.,
    show the current user folder size).

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
	String js="";

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


	    if (op==Action.Op.REORDER) {
		// supplies a column-separated list of IDs (maybe with prefixes)
		String prefix = request.getParameter(PREFIX);
		String ids = request.getParameter(ID);
		if (ids==null) throw new WebException("No article id list supplied");
		String q[] = ids.split(":");
		if (q.length==0) throw new WebException("Empty article id list supplied");
		// remove prefix from each id
		if (prefix !=null && prefix.length()>0) {
		    for(int i=0; i<q.length; i++) {
			if (!q[i].startsWith(prefix)) throw new WebException("No prefix '"+prefix+"' was found in article id '"+q[i]+"'");
			q[i] = q[i].substring(prefix.length());
		    }
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
		Action a= sd.addNewAction(em, u, op, id, null, asrc);
		//em.persist(u);	       
		em.getTransaction().commit(); 
	    }

	    js= (u!=null)? responseJS(em, u, op, asrc.presentedListId) : "";

	    em.close();

	    response.setContentType("text/plain");
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
	executed there. There are two situations when we need to do
	it: (a) Folder size changed; (b) Page not-quite-reload in
	Chrome (it happens when you use the "Back" button in the
	browser), when we need to make sure that all recent activity
	is properly reflected.
     */
    private String responseJS(EntityManager em, User u, Action.Op op, long presentedListId ) {
	String js="";
	RatingButton [] buttons = RatingButton.chooseRatingButtonSet(u.getProgram());
      
	try {
	    int fs = u.getFolderSize();
	    String q = "("+fs+")";
	    js += //"alert(' +"+q+"'); "+
		"setFolderSize('" +q+ "');\n";
	    if (op != Action.Op.NONE || presentedListId==0) return js;

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
		    js += e.hideJS() +"\n";
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
	    //js += "alert('Cnt = " + cnt + "')";
	} catch(Exception ex) {
	    Logging.error("JS.responseJS: " + ex);
	}	
	return js;
    }
    

     /** Returns a URL for this servlet */
    //static String mkUrl(String cp, String id, Action.Op op) {
    //	return cp + "/ArticleServlet?" +  ID +"="+id + "&"+ ACTION+ "="+op;
    //}

}
