package edu.rutgers.axs.ucb;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.recommender.*;
import edu.rutgers.axs.ee4.DenseDataPoint;
import edu.rutgers.axs.ee5.Vocabulary;
import edu.rutgers.axs.ee5.ArticleDenseDataPoint;


/** Daily updates for the underlying data structures, and
    recommendation generation, for Peter Frazier's and Chen Bangrui's
    UCB (Upper Confidence Bound) recommender.
 */
public class Daily {

   /** Maximum number of documents to be retrieved by Lucene
	searches.  This parameter is required by Lucene searches. We
	don't actually want to impose a restriction, so set the value
	siginificantly higher than the total number of articles in the
	index.
     */
    static  final int maxlen = 10000000;

    /** Time horizon in days. (28 days, as per CBR) */
    public static final int TIME_HORIZON_DAY=28;

   /** Command-line options, used to explicitly specify the document
	date range for the suggestion generator. They are used to
	emulate the operation of the suggestion generator at some
	earlier date */
    static private Date forcedSince=null, forcedToDate=null;


 
    private static void reportEx(Exception ex) {
	Logging.error(ex.toString());
	System.out.println(ex);
	ex.printStackTrace(System.out);
    }

  /** The main "daily updates" method. Calls all other
	methods. Operations involved are: <ul> <li>Create suggestion
	lists for all UCB users </ul>

	@param onlyUser If not null, only generate sug list for this user
	(rather than for every user in program UCB)

     */
    static void updates(String onlyUser, Vocabulary voc) throws IOException {

	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);
	EntityManager em  = Main.getEM();

	final int days = EE5DocClass.TIME_HORIZON_DAY;
	Date since = SearchResults.daysAgo( days );

	final User.Program program = User.Program.UCB;

	if (onlyUser!=null) {
	    User user = User.findByName( em, onlyUser);
	    if (user==null) throw new IllegalArgumentException("User " + onlyUser + " does not exist");
	    if (!user.getProgram().equals(program)) throw new IllegalArgumentException("User " + onlyUser + " is not enrolled in program " + program);
	    try {
		makeUCBSug(em, searcher, since,  user, voc);
	    } catch(Exception ex) {
		reportEx(ex);
	    }
	} else {
	    List<Integer> lu = User.selectByProgram( em, program);
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    makeUCBSug(em, searcher, since,  user, voc);
		} catch(Exception ex) {
		    reportEx(ex);
		}
	    }
	}
	em.close();

	    
    }


    static void  //DataFile
	makeUCBSug(EntityManager em,  IndexSearcher searcher, Date since, 
		   User user, Vocabulary voc) throws IOException {
	int uid = user.getId();

	// restricting the scope by category and date,
	// as per Thorsten 2012-06-18
	String[] cats = user.getCats().toArray(new String[0]);
	final String uname = user.getUser_name();
	if (cats.length==0) {
	    String msg = " User "+uname+" has not chosen any categories of interest. ";
	    Logging.warning(msg);
	    return;
	}

	final DataFile.Type ptype = DataFile.Type.UCB_USER_PROFILE;
	// update the user's profile based on his recent activity
	DataFile oldProfileFile = DataFile.getLatestFile(em, uname, ptype);
	System.out.println("Old user profile = " + oldProfileFile);

	boolean blankPage = (oldProfileFile == null);

	// get suggestions from among recent ArXiv docs

	SearchResults sr = 
	    SubjectSearchResults.orderedSearch(searcher,user,since, 
					       forcedToDate, maxlen);

  
	ArxivScoreDoc[] sd= ArxivScoreDoc.toArxivScoreDoc( sr.scoreDocs);

	IndexReader reader = searcher.getIndexReader();
	for(int i=0; i<sd.length; i++) {
	    int docno = sd[i].doc;
	    DenseDataPoint p = ArticleDenseDataPoint.readArticle(docno,voc,reader);
	}
	

	/*
	EE5User ee5u = EE5User.getAlways( em, uid, true);
	int lai = updateUserVote(em, id2dc, user, ee5u);
	boolean nofile = false;
	return updateSugList(em, searcher, since, id2dc, user, ee5u, lai, nofile);
	*/
    }

    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	
	//	if (argv.length == 0) {
	//    System.out.println("Usage: Daily [init|update]");
	//    return;
	//}

	String onlyUser = ht.getOption("user", null);
	forcedSince=ht.getOptionDate("since",null);
	forcedToDate=ht.getOptionDate("until",null);
	//	newProfID=ht.getOptionLong("newProfID", 0);
	//newSugID=ht.getOptionLong("newSugID", 0);

	//String stoplist = "WEB-INF/stop200.txt";
	//stoplist = ht.getOption("stoplist",stoplist);
	//UserProfile.setStoplist(new Stoplist(new File(stoplist)));

	String altVoc = ht.getOption("voc",null);
	Vocabulary voc = altVoc==null? Vocabulary.readVocabulary():
	    Vocabulary.readVocabulary(new File(altVoc));


  }


}