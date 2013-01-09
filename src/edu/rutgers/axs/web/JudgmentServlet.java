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
    
    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);

	Action.Op op = (Action.Op)Tools.getEnum(request, Action.Op.class,
					 ACTION, Action.Op.NONE);	 
	ActionSource asrc = new ActionSource(request);
	String js="";

	EntityManager em = null;
	try {


	    SessionData sd =  SessionData.getSessionData(request);
	    edu.cornell.cs.osmot.options.Options.init(sd.getServletContext());
	    String user = sd.getRemoteUser(request);

	    if (user!=null) {
		em = sd.getEM();
		// Begin a new local transaction so that we can persist a new entity
		
		User u = User.findByName(em, user);
		
		if (op!=Action.Op.NONE) {
		    String id = request.getParameter(ID);
		    if (id==null) throw new WebException("No aticle id supplied");
		    em.getTransaction().begin();
		    Action a = u.addAction(em, id, op, asrc);
		    //em.persist(u);	       
		    em.getTransaction().commit(); 
		}

		js= responseJS(em, u, op, asrc.presentedListId);

		em.close();
	    }

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
	try {
	    int fs = u.getFolderSize();
	    String q = "("+fs+")";
	    js += //"alert('Hi +"+q+"'); "+
		"setFolderSize('" +q+ "');\n";
	    if (op != Action.Op.NONE || presentedListId==0) return js;
	    PresentedList plist = (PresentedList)em.find( PresentedList.class, presentedListId);
	    IndexSearcher searcher=  new IndexSearcher( Common.newReader() );
	    Vector<ArticleEntry> entries = plist.toArticleList(null, searcher);
	    User.Program program = u.getProgram();

	    for(ArticleEntry e: entries) {
		//		boolean hidden = (program==User.Program.EE4)? 		 e.isInFolder,

	    }
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
