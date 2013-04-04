package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.Common;

/** The nightly updater for Thorsten's PPP plan.
 */
public class DailyPPP {
 
    static private Random gen = new  Random();

    static void updates() throws Exception {

	//ArticleAnalyzer.setMinDf(10); // as PG suggests, 2013-02-06
	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));

	ArticleAnalyzer z = new ArticleAnalyzer();
	IndexReader reader = z.reader;

	// Read article stats stored in the SQL database
	Main.memory("main:calling CASA");
	CompactArticleStatsArray.CASReader asr = new CompactArticleStatsArray.CASReader(reader);
	//CompactArticleStatsArray casa = null;
	asr.start(); CompactArticleStatsArray casa = asr.getResults();

	// Use run() instead of start() for single-threading
	//asr.run();	

	EntityManager em  = Main.getEM();
	IndexSearcher searcher = new IndexSearcher( reader );
	    
	final User.Program program = User.Program.PPP;

	if (onlyUser!=null) {
	    User user = User.findByName( em, onlyUser);
	    if (user==null) throw new IllegalArgumentException("User " + onlyUser + " does not exist");
	    if (!user.getProgram().equals(program)) throw new IllegalArgumentException("User " + onlyUser + " is not enrolled in program " + program);
	    makeP3Sug(em, casa, searcher, user);
	} else {
	    List<Integer> lu = User.selectByProgram( em, program);
	    
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    makeP3Sug(em, casa, searcher, user);
		} catch(Exception ex) {
		    Logging.error(ex.toString());
		    System.out.println(ex);
		    ex.printStackTrace(System.out);
		}
	    }
	}
	em.close();
    }

    /** Max size of the list to generate */
    static final int maxDocs=200;

    /** Generates a suggestion list for the specified user, using PPP.
     */
    static void makeP3Sug(EntityManager em,  CompactArticleStatsArray casa, IndexSearcher searcher, User u) 
    throws IOException {
	Vector<DataFile> ptr = new  Vector<DataFile>(0);

	// The list (possibly empty) of pages that the user does
	// not want ever shown.
	// FIXME: strictly speaking, a positive rating should perhaps
	// "cancel" a "Don't show again" request
	HashMap<String, Action> exclusions = u.listExclusions();


	UserProfile upro = 
	    getSuitableUserProfile(u, ptr, em, searcher.getIndexReader());
	DataFile inputFile = ptr.elementAt(0);

	// restricting the scope by category and date,
	// as per Thorsten 2012-06-18
	String[] cats = u.getCats().toArray(new String[0]);
	Date since = Scheduler.chooseSince( em, u);

	int maxlen = 10000;
	SearchResults sr = 
	    SubjectSearchResults.orderedSearch(searcher,u,since, maxlen);
	
	ArxivScoreDoc[] sd= ArxivScoreDoc.toArxivScoreDoc( sr.scoreDocs);
	Logging.info("since="+since+", cat search got " +sd.length+ " results");
	//searcher.close();
	// rank by TJ Algo 1
	TjAlgorithm1 algo = new TjAlgorithm1();
	sd = algo.rank( upro, sd, casa, em, maxDocs, false);
		
	Vector<ArticleEntry> entries = upro.packageEntries(sd);

	// FIXME: it would be nice to apply exclusions earlier, but it
	// does not matter much in PPP, since the utility is linear
	ArticleEntry.applyUserSpecifics(sr.entries, u);
	ArticleEntry.refreshOrder(sr.entries);

	Logging.info("since="+since+", |sd|=" +sd.length+", |entries|=" + entries.size());

	// FIXME: is there a better place for the day-setting?
	boolean isTrivial = (upro.terms.length==0);
	if (isTrivial) {
	    u.forceNewDay(User.Day.LEARN);
	} else {
	    u.forceNewDay();
	}
	em.persist(u);

		
	DataFile outputFile=new DataFile(u.getUser_name(), 0, DataFile.Type.PPP_USER_PROFILE);	    
  
	//	outputFile.setDays(0);
	if (since!=null) outputFile.setSince(since);
	outputFile.setLastActionId(upro.getLastActionId());
	if (inputFile!=null) {
	    outputFile.setInputFile(inputFile);
	}

	// is the pairing {(1),(2,3)...} or {(1,2),(3,4),...}?
	boolean topOrphan = gen.nextBoolean();
	outputFile.setPppTopOrphan( topOrphan);
	double p = 0.5; // probability of swap
	perturb( entries, p, topOrphan);

	ArticleEntry.save(entries, outputFile.getFile());
	// Save the sugg list to the SQL database
	//ArticleAnalyzer aa=new ArticleAnalyzer();
	outputFile.fillArticleList(entries,  em);
    }

    private static void perturb(Vector<ArticleEntry> entries, double p, boolean topOrphan ) {
	for(int i=0; i< entries.size(); i ++) {
	    entries.elementAt(i).iUnperturbed=i+1;
	}
	for(int i = (topOrphan? 1: 0);  i+1  < entries.size(); i += 2) {
	    if (gen.nextDouble() < p) {
		ArticleEntry q = entries.elementAt(i);
		entries.set(i, entries.elementAt(i+1));
		entries.set(i+1, q);
	    }
	}
    }


    private static UserProfile 
	getSuitableUserProfile(User user, Vector<DataFile> ptr, 
			       EntityManager em, IndexReader reader)
	throws IOException {

	if (ptr.size()>0) { //explicitly specified input file
	    return new UserProfile(ptr.elementAt(0), reader);
	}

	DataFile.Type ptype =  DataFile.Type.PPP_USER_PROFILE;
	DataFile.Type ptype2 =  DataFile.Type.TJ_ALGO_2_USER_PROFILE;
	
	DataFile inputFile = 
	    DataFile.getLatestFile(em, user.getUser_name(), ptype);

	UserProfile upro;
	if (inputFile != null) {
	    // read it
	    upro = new UserProfile(inputFile, reader);
	} else if ((inputFile = 
		    DataFile.getLatestFile(em,  user.getUser_name(), ptype2))!=null) {
	    // compatibility step (may be used at the moment when the user 
	    // is switched from SET_BASED to PPP
	    upro = new UserProfile(inputFile, reader);
	} else {
	    // generate it
	    // empty profile, as TJ wants (06-2012)
	    upro = new UserProfile(reader);
	    DataFile uproFile=upro.saveToFile(user.getUser_name(), 0, ptype);

	    em.persist(uproFile);
	    inputFile = uproFile;
	}
	ptr.add(inputFile);
	return upro;
    }

    static String onlyUser = null;
    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	
	if (argv.length == 0) {
	    System.out.println("Usage: Daily [init|update]");
	    return;
	}

	onlyUser = ht.getOption("user", null);

	String cmd = argv[0];
	if (cmd.equals("init")) {
	    throw new IllegalArgumentException("not needed!");
	} else if (cmd.equals("update")) {
	    updates();
	} else {
	    System.out.println("Unknown command: " + cmd);
	}

    }


}