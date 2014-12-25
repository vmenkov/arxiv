package edu.rutgers.axs.upload;


import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.commons.lang.mutable.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.SessionData;
//import edu.rutgers.axs.search.*;
import edu.rutgers.axs.indexer.*;
//import edu.rutgers.axs.html.*;

/** Auxiliary code for creating entries in the Article table for
    user-uploaded docs. We need this primarily so that we can later
    store additional information (such as the doc cluster id for EE5)
    in the Article table. This is something we can't do in Lucene,
    since Lucene entries are not modifiable.
 */
public class ToArticle {

    /** Finds all docs uploaded by a specified user in Lucene data store, and
	creates an Article object in the SQL database for each one.
    */
    static void oneUser(EntityManager em,  IndexSearcher searcher, User u) throws IOException {       
	String uname = u.getUser_name();

	Set<Action> sa = u.getActions();

	ScoreDoc[] uuSd = Common.findAllUserFiles(searcher, uname);
	int nUploaded = uuSd.length; 
	Logging.info("Checking " + nUploaded + " docs uploaded by " + uname);
	for(int k=0; k < nUploaded; k++) {
	    int docno =  uuSd[k].doc;
	    Document doc = searcher.doc(docno);
	    Article art =  Article.getUUDocAlways(em, doc);
	    if (!hasUploadAction(sa, art)) {
		ActionSource asrc = new ActionSource(Action.Source.UNKNOWN,0);
		Action a=SessionData.addNewAction(em,u,Action.Op.UPLOAD,art,null,asrc,null);
	    }
	}
    }

    /** Have we already recorded the fact that the user uploaded this 
	particular article? (We may have if the user did it previously).
    */
    private static boolean hasUploadAction(Set<Action> sa,  Article art) {
	for(Action a: sa) {
	    if (a.getOp() == Action.Op.UPLOAD && a.getArticle().equals(art)) return true;
	}
	return false;
    }

    static public void main(String argv[]) throws IOException {
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );
	EntityManager em  = Main.getEM();
	User.Program programs[] = { User.Program.PPP, User.Program.EE5};
	for(User.Program program: programs) {
	    List<Integer> lu = User.selectByProgram( em, program);
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    oneUser(em, searcher, user);
		} catch(Exception ex) {
		    Logging.error(ex.toString());
		    System.out.println(ex);
		    ex.printStackTrace(System.out);
		}
	    }
	}
	em.close();
	reader.close();
    }

}