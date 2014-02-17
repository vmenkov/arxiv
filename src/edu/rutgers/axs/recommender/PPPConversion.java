package edu.rutgers.axs.recommender;

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
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.Common;

/** 
 */
public class PPPConversion {

  /** Updates and saves the user profile for the specified user, as
	long as it makes sense to do it (i.e., there is no profile yet,
	or there has been some usable activity since the existing profile
	has been created)
     */
    private static void recreateP3Profile(EntityManager em,  
					  ArticleAnalyzer aa2,
					  User u) throws IOException{

	//Logging.info("Done doc norms");

	//	final DataFile.Type ptype = DataFile.Type.PPP_USER_PROFILE, 
	//	    stype =  DataFile.Type.PPP_SUGGESTIONS;
	final String uname = u.getUser_name();


	PPPFeedback[] allFeed = doZero? new PPPFeedback[0]: PPPFeedback.allFeedbacks(em, u, minActionID, maxActionID);

	//List<DataFile> sugLists = getAllRelevantSugLists( em, u.getId());

	Logging.info("For user "+uname+", found " + allFeed.length + " relevant suggestion lists");
	int cnt=0;
	for(PPPFeedback actionSummary: allFeed) {
	    Logging.info("Sug list ["+cnt+"](id="+actionSummary.sugListId +"), actions on " + actionSummary.size() + " pages");
	    cnt ++;
	}

	UserProfile upro = new 	UserProfile(aa2);

	cnt = 0;
	int rocchioCnt = 0; // how many doc vectors added to profile?
	long lid = 0;
	for(PPPFeedback actionSummary : allFeed) {
	    Logging.info("Applying updates from sug list ["+(cnt++)+"](id="+ actionSummary.sugListId +")");

	    if (actionSummary.size() == 0) continue;
	    DataFile df = (DataFile)em.find(DataFile.class, actionSummary.sugListId);
	    rocchioCnt += actionSummary.size();
	    lid = Math.max(lid,  actionSummary.getLastActionId());

	    boolean topOrphan = df.getPppTopOrphan();

	    File f = df.getFile();
	    Vector<ArticleEntry> entries = ArticleEntry.readFile(f);

	    
	    Vector<ArticleEntry> v = unique(entries);
	    if (v.size()!=entries.size()) {
		Logging.warning("Duplicates found: out of " + entries.size() + " sug list entries, only kept " + v.size());
	    }

	    HashMap<String,MutableDouble> updateCo = actionSummary.getRocchioUpdateCoeff(topOrphan, v);
	    Logging.info("The update will be a linear combination of " + updateCo.size() + " documents:");
	    for(String aid: updateCo.keySet()) {
		System.out.println("w["+aid + "]=" +  updateCo.get(aid));
	    }
	    upro.rocchioUpdate(updateCo );
 	    upro.setTermsFromHQ();
	}

	//if (rocchioCnt==0 ) {    return;	}

	final DataFile.Type ptype = DataFile.Type.PPP_USER_PROFILE;
	DataFile outputFile=upro.saveToFile(uname, 0, ptype);
	outputFile.setLastActionId(lid);

	em.getTransaction().begin(); 
	em.persist(outputFile);
	em.getTransaction().commit();
	Logging.info("Saved profile: " + outputFile);
    }
    
    /** Strips non-unique elements from an entry list. This is only
	needed with very old (2012) lists, which may have been
	composed slightly incorrectly.
    */
    private static Vector<ArticleEntry> unique(Vector<ArticleEntry> entries) {
	Vector<ArticleEntry> v= new Vector<ArticleEntry>();
	 HashSet<String> h=new  HashSet<String>();
	 for(ArticleEntry e: entries) {
	     if (e==null) continue;
	     String aid = e.id;
	     if (h.contains(aid)) {
		 continue;
	     } else {
		 h.add(aid);
		 v.add(e);
	     }
	 }
	 return v;
     }

    static void conversions() throws IOException {

	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	IndexReader reader = Common.newReader();

	Logging.info("3PR profile conversion. Refined=" + refined);

	Logging.info("Computing doc norms...");
	ArticleAnalyzer aa2 = refined? 
	    new ArticleAnalyzer2( reader) :
	    new ArticleAnalyzer( reader, ArticleAnalyzer.upFields);

	if (!refined) aa2.readCasa();
	Logging.info("Done doc norms...");

	EntityManager em  = Main.getEM();
	IndexSearcher searcher = new IndexSearcher( reader );
	    
	final User.Program program = User.Program.PPP;

	if (onlyUser!=null) {
	    Logging.info("Processing profile for a single user: "+ onlyUser);
	    User user = User.findByName( em, onlyUser);
	    if (user==null) throw new IllegalArgumentException("User " + onlyUser + " does not exist");
	    if (!user.getProgram().equals(program)) throw new IllegalArgumentException("User " + onlyUser + " is not enrolled in program " + program);

	    recreateP3Profile(em,  aa2, user);
	} else {
	    List<Integer> lu = User.selectByProgram( em, program);

	    Logging.info("Processing profiles for all "+lu.size()+" 3RP users");
	    
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    recreateP3Profile(em,  aa2, user);
		} catch(Exception ex) {
		    Logging.error(ex.toString());
		    System.out.println(ex);
		    ex.printStackTrace(System.out);
		}
	    }
	}
	em.close();
    }


    private static String onlyUser = null;

    /** Controls document representation (via the choice of
	ArticleAnalyzer class and DocumentFile.version)
     */
    static private boolean refined=false;

    /** If true, create empty profiles, instead of doing a replay */
    static private boolean doZero=false;

    /** Only actions starting from this one may be taken into account */
    static private long  minActionID=0;
    /** Only actions earlier than this one may be taken into account. The values 0 means that there is no upper limit. */
    static private long  maxActionID=0;

    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	
	//	if (argv.length == 0) {
	//	    System.out.println("Usage: Daily [init|update]");
	//	    return;
	//	}

	onlyUser = ht.getOption("user", null);
	refined = ht.getOption("refined", refined);
	doZero = ht.getOption("zero", false);
	minActionID=ht.getOptionLong("minActionID", minActionID);
	maxActionID=ht.getOptionLong("maxActionID", maxActionID);
	conversions();


	/*
	String cmd = argv[0];
	if (cmd.equals("init")) {
	    throw new IllegalArgumentException("not needed!");
	} else if (cmd.equals("update")) {
	    updates();
	} else {
	    System.out.println("Unknown command: " + cmd);
	}
	*/
    }

}
