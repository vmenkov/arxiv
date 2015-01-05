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

/** An auxiliary tool for creating entries in the Article and Action tables for
    user-uploaded docs. We need Article entries primarily so that we can later
    store additional information (such as the doc cluster id for EE5)
    in the Article table. This is something we can't do in Lucene,
    since Lucene entries are not modifiable. 

    <p>
    Another task this application has is re-creating Lucene entries based on the 
    txt files, in order to re-index documents.
 */
public class ToArticle {

    /** Finds all docs uploaded by a specified user in Lucene data store, and
	creates an Article object in the SQL database for each one.

	@param writer If true, we also re-create Lucene entries (which gives
	them new document numbers, of course). This is done if we want to 
	make sure everything is indexed the way we want it now (in particular,
	the document body is stored), and not how it was originally done.

    */
    static void oneUser(EntityManager em,  IndexSearcher searcher, 
			IndexWriter writer,
			User u) throws IOException {       
	String uname = u.getUser_name();
	Set<Action> sa = u.getActions();
	ScoreDoc[] uuSd = Common.findAllUserFiles(searcher, uname);
	int nUploaded = uuSd.length; 
	Logging.info("Checking " + nUploaded + " docs uploaded by " + uname);
	for(int k=0; k < nUploaded; k++) {
	    int docno =  uuSd[k].doc;
	    Document doc = searcher.doc(docno);

	    if (writer!=null) {
		DataFile g = whereIsFile(doc);
		Logging.info("Will re-import text from " + g.getFile());
		doc=UploadImporter.importFile(uname,g.getFile(),writer);
		Logging.info("Replaced doc no. " + docno + " with ??? for " + Article.shortName(doc));
	    }
	    
	    Article art =  Article.getUUDocAlways(em, doc);
	    if (!hasUploadAction(sa, art)) {
		ActionSource asrc = new ActionSource(Action.Source.UNKNOWN,0);
		Action a=SessionData.addNewAction(em,u,Action.Op.UPLOAD,art,null,asrc,null);
	    }
	}
    }

    /** Looking at a Lucene entry for a user-uploaded document, figure
	where the txt file was, so that we can re-read it.
     */
    private static DataFile whereIsFile(Document doc) throws IOException {
	String uname = doc.get(ArxivFields.UPLOAD_USER),
	    file = doc.get(ArxivFields.UPLOAD_FILE);

	String txtFileName = file + ".txt";
	DataFile txtDf = new DataFile(uname, DataFile.Type.UPLOAD_TXT, txtFileName);
	File f = txtDf.getFile();
	if (!f.exists() || !f.canRead()) throw new IOException("File " + f + " does not exist or cannot be read");
	return txtDf;
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
	
	final boolean rewriteLucene = true;
	IndexWriter writer = rewriteLucene? UploadImporter.makeWriter() : null;
	Logging.info("writer="+writer);

	EntityManager em  = Main.getEM();
	User.Program programs[] = { User.Program.PPP, User.Program.EE5};
	for(User.Program program: programs) {
	    List<Integer> lu = User.selectByProgram( em, program);
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    oneUser(em, searcher, writer, user);
		} catch(Exception ex) {
		    Logging.error(ex.toString());
		    System.out.println(ex);
		    ex.printStackTrace(System.out);
		}
	    }
	}
	em.close();
	writer.close();
	reader.close();
	
    }

}