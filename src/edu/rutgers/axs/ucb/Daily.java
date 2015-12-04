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
import edu.rutgers.axs.ucb.math.*;


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
	UCBProfile upro =
	    getUpdatedProfile( em,  searcher, since, user, voc);
	if (upro==null) return;


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
	
	//uprof.updateProfile(double [][] X, double [] Y);


	/*
	EE5User ee5u = EE5User.getAlways( em, uid, true);
	int lai = updateUserVote(em, id2dc, user, ee5u);
	boolean nofile = false;
	return updateSugList(em, searcher, since, id2dc, user, ee5u, lai, nofile);
	*/


    }


    /** Returns to user profile, updated as needed. This involves
	finding the most recent user profile (or creating an initial
	one, if none exists), and updating it using any recent user 
	activity since the stored profile has been created.
     */
    private static UCBProfile
	getUpdatedProfile(EntityManager em,  IndexSearcher searcher, Date since, 
		   User user, Vocabulary voc) throws IOException {


	/** The type of user profile data files */
	//static private 
	final DataFile.Type ptype = DataFile.Type.UCB_USER_PROFILE;
	final DataFile.Type stype = DataFile.Type.UCB_SUGGESTIONS;

	int uid = user.getId();

	// restricting the scope by category and date,
	// as per Thorsten 2012-06-18
	String[] cats = user.getCats().toArray(new String[0]);
	final String uname = user.getUser_name();
	if (cats.length==0) {
	    String msg = " User "+uname+" has not chosen any categories of interest. ";
	    Logging.warning(msg);
	    return null;
	}

	// Update the user's profile based on his recent activity.
	// First, find the most recent user profile in existence.
	DataFile oldProfileFile = DataFile.getLatestFile(em, uname, ptype);
	System.out.println("Old user profile = " + oldProfileFile);


	boolean blankPage = (oldProfileFile == null);

	long lastActionId = user.getLastActionId();

	if (blankPage) {
	    lastActionId = 0;
	    UCBProfile upro = initProfile(voc.L);
	    DataFile outputFile=saveToFile(upro, uname, ptype, lastActionId);
	    outputFile.setLastActionId(0);
	    
	    em.getTransaction().begin(); 
	    em.persist(outputFile);
	    em.getTransaction().commit();
	    Logging.info("Saved initial profile: " + outputFile);
	    return upro;
	}	

	// Find all sug lists based on this old profile.
	List<DataFile> sugLists = DataFile.getAllFilesBasedOn(em, uname, stype, oldProfileFile);

	System.out.println("Found " +sugLists.size() + " applicable suggestion lists based on the user's most recent profile");
	int cnt=0;
	for(DataFile df: sugLists) {
	    System.out.println("Sug list ["+cnt+"](id="+df.getId()+")=" + df);
	    cnt ++;
	}

	cnt = 0;
	int rocchioCnt = 0; // how many doc vectors added to profile?
	long lid = 0;

	// Feedback map: (false=0, true=1, null=presented but given no feedback)
	HashMap<String,Boolean> fbmap = new HashMap<String,Boolean>();

	for(DataFile df: sugLists) {

	    System.out.println("Applying updates from sug list ["+(cnt++)+"](id="+df.getId()+")=" + df);

	    List<PresentedList> pls = PresentedList.findPresentedListsForSugList(em, uname,  df.getId());
	    if (pls==null) continue; // no lists presented, so probably no feedback either	    
	    for(PresentedList pl: pls) {
		Vector<PresentedListEntry> ples = pl.getDocs();
		if (ples==null) continue;
		for(PresentedListEntry e: ples) {
		    String aid = e.getAid();
		    if (!fbmap.containsKey(aid)) fbmap.put(aid, null);
		}
	    }

	    PPPFeedback actionSummary = new PPPFeedback(em, user, df.getId());
	    System.out.println("Found useable actions on " +  actionSummary.size() + " pages");
	    if (actionSummary.size() == 0) continue;
	    rocchioCnt += actionSummary.size();
	    lid = Math.max(lid,  actionSummary.getLastActionId());

	    for(String aid: actionSummary.keySet()) { 
		PPPActionSummary actions = actionSummary.get(aid);
		boolean positive = (actions!=PPPActionSummary.DEMOTED);
		fbmap.put(aid, positive);
	    }
	}

	UCBProfile upro =UCBProfile.readProfile(oldProfileFile.getFile(),voc.L);

	if (fbmap.size()==0) {
	    System.out.println("No useful feedback found for user " + uname + "; no need to update user profile");
	    return upro;
	}

	// Create the list of presented pages in X (with the 
	// pages on which we have positive feedabck -- those for Y --
	// in the front of the list)
	String[] xAids = new String[ fbmap.size()];
	int pos = 0;
	for(String aid: fbmap.keySet()) {
	    Boolean val = fbmap.get(aid);
	    if (val!=null && val.booleanValue()) xAids[pos++] = aid;
	}
	final int xCnt = pos;
	for(String aid: fbmap.keySet()) {
	    Boolean val = fbmap.get(aid);
	    if (val==null || !val.booleanValue()) xAids[pos++] = aid;
	}

	// update the profile
	// ....
	
	return upro;

    }

    /** Initializes user profile with mu_0 and Sigma_0 computed by CBR
	// FIXME: need to read pre-created data
     */
    static UCBProfile initProfile(int L) {
	double mu[] = new double[L];
	double sigma[][] = new double[L][];
	for(int i=0; i<L; i++) sigma[i] =  new double[L];
	//throw new IllegalArgumentException("Not supported yet");
	return new UCBProfile(sigma,mu);
    }

  
   /** Creates a disk file and a matching DataFile object to store 
       a UCBProfile

   */
    static DataFile saveToFile(UCBProfile upro, String username, DataFile.Type type, long lastActionId) 
	throws IOException {

	DataFile uproFile=  new DataFile(username, 0, type);
	uproFile.setLastActionId( lastActionId);
	upro.save(uproFile.getFile());
	return uproFile;
    }

   /** Sets the ID of the new DataFile, if appropriate.
	@param id The new id. If 0, it is ignored and nothing is done.
     */
    private static void setDataFileId(EntityManager em, DataFile outputFile, long id) {
	if (id != 0) {
	    if (em.find(DataFile.class,id)!=null) {
		Logging.warning("DataFile with ID="+id +" already exists; ignore the request to set new file ID this way");
	    } else { 
		Logging.info("Setting new DataFile's ID=" + id +", as per command-line request");
		outputFile.setId((int)id);
	    }
	}
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