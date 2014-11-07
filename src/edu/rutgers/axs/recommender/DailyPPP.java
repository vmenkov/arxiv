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
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.indexer.Common;

/** The nightly updater for Thorsten's 3PR (a.k.a. PPP) experiment plan.
    
    <p>This application main operation mode is the nightly update of
    the user profiles and suggestion lists for all 3PR users who need
    them. Besides this main mode, this application also has a variety
    of additional functions, controlled with a number of command-line
    options, which can be used for various experiments or for
    problem-fixing. This includes the ability to update only user
    profiles or only suggestion lists; updating the user profile
    and/or suggestion list for a single user; as well as the ability to 
    customize the process whereby the e user profile(s)
    and/or suggestion list(s) are updated.

    <p> The underlying code (hidden in ArticleAnalyzer and its
    subclass ArticleAnalyzer2) supports two different document
    representations: the original "flattened" (2011) and "refined" (2013-14).
    The choice is controlled with the flag called "refined".

 */
public class DailyPPP {
 
    /** Controls document representation (via the choice of
	ArticleAnalyzer class and DocumentFile.version)
     */
    static private boolean refined=true;

    public static int myVersion() {
	return refined? 2:1;
    }

    /** Figures which DataFile versions you need for the current document
	representation. */
    public static int[] allowedFileVersions() {
	return allowedFileVersions(refined);
    }

    public static int[] allowedFileVersions(boolean refined) {
	return refined? new int[] {2} : new int[] {0, 1};
    }

    /** Carry out profile and rec list updates for all users for whom
	this is needed */
    static void updates() throws Exception {

	Logging.info("refined=" + refined);
	//ArticleAnalyzer.setMinDf(10); // as PG suggests, 2013-02-06
	IndexReader reader = Common.newReader();

	ArticleAnalyzer z = refined? 
	    new ArticleAnalyzer2( reader) :
	    new ArticleAnalyzer1( reader, ArticleAnalyzer.upFields);

	if (!refined) ((ArticleAnalyzer1)z).readCasa();

	EntityManager em  = Main.getEM();
	IndexSearcher searcher = new IndexSearcher( reader );
	    
	final User.Program program = User.Program.PPP;

	if (onlyUser!=null) {
	    User user = User.findByName( em, onlyUser);
	    if (user==null) throw new IllegalArgumentException("User " + onlyUser + " does not exist");
	    if (!user.getProgram().equals(program)) throw new IllegalArgumentException("User " + onlyUser + " is not enrolled in program " + program);
	    oneUser(em, z, searcher, user);
	} else {
	    List<Integer> lu = User.selectByProgram( em, program);
	    
	    for(int uid: lu) {
		try {
		    User user = (User)em.find(User.class, uid);
		    oneUser(em, z, searcher, user);
		} catch(Exception ex) {
		    Logging.error(ex.toString());
		    System.out.println(ex);
		    ex.printStackTrace(System.out);
		}
	    }
	}
	em.close();
    }

    /** Just a wrapper around 2 function calls: profile and rec list
	updates for one user */
    private static void oneUser(EntityManager em,  ArticleAnalyzer aa, IndexSearcher searcher, User user) 
	throws IOException {
	if (doProf) {
	    Logging.info("Updating profile for user " + user);
	    updateP3Profile(em, aa, searcher, user);
	} else {
	    Logging.info("Skip updating profile for user " + user);
	}
	if (doSug) {
	    Logging.info("Updating suggestions for user " + user);
	    makeP3Sug(em, aa, searcher, user);
	} else {
	    Logging.info("Skip updating suggestions for user " + user);
	}
    }

    /** Updates and saves the user profile for the specified user, as
	long as it makes sense to do it (i.e., either there is no
	profile yet, or there has been some usable activity since the
	existing profile has been created).

	<p>Since Jan 2014, multiple document representations are
	supported, with two entirely different (independent) series of
	user profiles.  In order to only look at the relevant
	profiles, selection is done by DataFile.version.
     */
    private static void updateP3Profile(EntityManager em, 
					ArticleAnalyzer aa,
					IndexSearcher searcher, 
					User u)  throws IOException {

	IndexReader reader =searcher.getIndexReader();
	final DataFile.Type ptype = DataFile.Type.PPP_USER_PROFILE, 
	    stype =  DataFile.Type.PPP_SUGGESTIONS;
	final String uname = u.getUser_name();
	// Find the latest profile (with the right version)
	DataFile oldProfileFile = DataFile.getLatestFileByVersion(em, uname, ptype, allowedFileVersions());
	System.out.println("Old user profile = " + oldProfileFile);

	boolean blankPage = (oldProfileFile == null);
	
	if (blankPage) {
	    UserProfile upro =  new UserProfile(aa);
	    DataFile outputFile=upro.saveToFile(uname, 0, ptype);
	    setDataFileId(em, outputFile, newProfID);
	    outputFile.setLastActionId(0);
	    
	    em.getTransaction().begin(); 
	    em.persist(outputFile);
	    em.getTransaction().commit();
	    Logging.info("Saved initial profile: " + outputFile);
	    return;
	}	

	UserProfile upro = blankPage?
	    new UserProfile(aa) :  new UserProfile(oldProfileFile, aa);

	// Find all sug lists based on this old profile
	List<DataFile> sugLists = DataFile.getAllFilesBasedOn(em, uname, stype, oldProfileFile);

	System.out.println("Found " +sugLists.size() + " applicable suggestion lists");
	int cnt=0;
	for(DataFile df: sugLists) {
	    System.out.println("Sug list ["+cnt+"](id="+df.getId()+")=" + df);
	    cnt ++;
	}

	cnt = 0;
	int rocchioCnt = 0; // how many doc vectors added to profile?
	long lid = 0;
	for(DataFile df: sugLists) {
	    System.out.println("Applying updates from sug list ["+(cnt++)+"](id="+df.getId()+")=" + df);
	    PPPFeedback actionSummary = new PPPFeedback(em, u, df.getId());
	    System.out.println("Found useable actions on " +  actionSummary.size() + " pages");
	    if (actionSummary.size() == 0) continue;
	    rocchioCnt += actionSummary.size();
	    lid = Math.max(lid,  actionSummary.getLastActionId());

	    boolean topOrphan = df.getPppTopOrphan();
	    File f = df.getFile();
	    Vector<ArticleEntry> entries = ArticleEntry.readFile(f);
	    HashMap<String,?extends Number> updateCo = actionSummary.getRocchioUpdateCoeff(topOrphan, entries);
	    System.out.println("The update will be a linear combination of " + updateCo.size() + " documents:");
	    for(String aid: updateCo.keySet()) {
		System.out.println("w["+aid + "]=" +  updateCo.get(aid));
	    }
	    upro.rocchioUpdate(updateCo );
 	    upro.setTermsFromHQ();
	}
	if (rocchioCnt==0 && !blankPage) {
	    System.out.println("There is no need to update the existing profile " + oldProfileFile +", because no important action based on it has been recorded");
	    return;
	}
	DataFile outputFile=upro.saveToFile(uname, 0, ptype);
	setDataFileId(em, outputFile, newProfID);

	if (!blankPage) {
	    outputFile.setInputFile(oldProfileFile);
	}
	outputFile.setLastActionId(lid);

	em.getTransaction().begin(); 
	em.persist(outputFile);
	em.getTransaction().commit();
	Logging.info("Saved profile: " + outputFile);
     }
    

    /** Max size of the sugg list to generate */
    private static final int maxDocs=200;

    /** Generates a suggestion list for the specified user, using the
	3PR (PPP) algorithm, based on the most recent available 3PR
	profile. The process involves:
	<ul>
	<li> Looking up the user's categories of interest and appropriate 
	     date range;
	<li> Lucene search based on these criteria;
	<li> Ranking based on TJ's Algorithm 1 (in practice, simply
	     the dot product with the user profile)
	</ul>
     */
    static void makeP3Sug(EntityManager em,  ArticleAnalyzer aa, IndexSearcher searcher, User u) 
    throws IOException {
	String msg="";
	Vector<DataFile> ptr = new  Vector<DataFile>(0);

	// The list (possibly empty) of pages that the user does
	// not want ever shown.
	// FIXME: strictly speaking, a positive rating should perhaps
	// "cancel" a "Don't show again" request
	//HashMap<String, Action> exclusions = u.listExclusions();

	UserProfile upro = 
	    getSuitableUserProfile(u, ptr, em, aa);
	DataFile inputFile = ptr.elementAt(0);

	// Use a random generator seed that would be the same on re-run
	long seed = (u.getUser_name().hashCode() << 16) + inputFile.getId();
	Random gen = new  Random(seed);


	// restricting the scope by category and date,
	// as per Thorsten 2012-06-18
	String[] cats = u.getCats().toArray(new String[0]);
	if (cats.length==0) msg += " User "+u.getUser_name()+" has not chosen any categories of interest. ";

	// Set the appropriate date range
	Date since = (forcedSince!=null)? forcedSince: Scheduler.chooseSince( em, u);

	int maxlen = 10000;
	SearchResults sr = 
	    SubjectSearchResults.orderedSearch(searcher,u,since, forcedToDate, maxlen);
	
	ArxivScoreDoc[] sd= ArxivScoreDoc.toArxivScoreDoc( sr.scoreDocs);

	Date reportedToDate = (forcedToDate!=null)? forcedToDate: new Date();
	Logging.info("since="+since+", cat search got " +sd.length+ " results");
	if (sd.length==0) msg += " No matching articles in the specified categories were posted within the specified date range ("+since+ " to " +reportedToDate +")";
	else msg += " At least " +sd.length+ " articles in the specified categories were posted within the specified date range ("+since+ " to " + reportedToDate+")";

	//searcher.close();
	TjAlgorithm1 algo = new TjAlgorithm1();
	// rank by TJ Algo 1
	sd = algo.rank( upro, sd,  em, maxDocs, false);
		
	Vector<ArticleEntry> entries = upro.packageEntries(sd);

	String m = "packageEntries = (";
	for(int i=0; i<4 && i<entries.size(); i++) {
	    m += " " + entries.elementAt(i);
	}
	m += ")";
	Logging.info(m);

	// FIXME: it would be nice to apply exclusions earlier, but it
	// does not matter much in PPP, since the utility is linear
	ArticleEntry.applyUserSpecifics(sr.entries, u);
	ArticleEntry.refreshOrder(sr.entries);

	Logging.info("since="+since+", |sd|=" +sd.length+", |entries|=" + entries.size());

	m = "applyUserSpec  = (";
	for(int i=0; i<4 && i<entries.size(); i++) {
	    m += " " + entries.elementAt(i);
	}
	m += ")";
	Logging.info(m);


	// FIXME: is there a better place for the day-setting?
	boolean isTrivial = (upro.terms.length==0);
	if (isTrivial) {
	    u.initialNewDay();
	} else {
	    u.forceNewDay();
	}
	em.persist(u);

	String uname = u.getUser_name();
		
	DataFile outputFile=new DataFile(uname, 0, DataFile.Type.PPP_SUGGESTIONS);	    
	setDataFileId(em, outputFile, newSugID);

	if (msg!=null && msg.length()>0) {
	    outputFile.setMessage(msg);
	}
  
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
	perturb( entries, p, topOrphan, gen);

	// save the sug list to a text file
	ArticleEntry.save(entries, outputFile.getFile());
	// Save the sugg list to the SQL database
	outputFile.fillArticleList(entries,  em);
	outputFile.setVersion(myVersion());
	em.getTransaction().begin(); 
	em.persist(outputFile);
	em.getTransaction().commit();
	Logging.info("Saved sug list: " + outputFile);
 
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


    private static void perturb(Vector<ArticleEntry> entries, double p, boolean topOrphan, Random gen ) {
	for(int i=0; i< entries.size(); i ++) {
	    entries.elementAt(i).iUnperturbed=entries.elementAt(i).i = i+1;
	}
	for(int i = (topOrphan? 1: 0);  i+1  < entries.size(); i += 2) {
	    if (gen.nextDouble() < p) {
		ArticleEntry q = entries.elementAt(i);
		entries.set(i, entries.elementAt(i+1));
		entries.set(i+1, q);
	    }
	}
	for(int i=0; i< entries.size(); i ++) {
	    entries.elementAt(i).i = i+1;
	}
    }

    /** Retrieves the appropriate user profile to use for suggestion list 
	generation. This is the most recent profile for the specified user,
	with the right DataFile type and version number. If no appropriate
	user profile DataFile exists, an empty user profile is created on
	the fly.
    */
    private static UserProfile 
	getSuitableUserProfile(User user, Vector<DataFile> ptr, 
			       EntityManager em, ArticleAnalyzer aa)
	throws IOException {

	if (ptr.size()>0) { //explicitly specified input file
	    return new UserProfile(ptr.elementAt(0), aa);
	}

	DataFile.Type ptype =  DataFile.Type.PPP_USER_PROFILE;
	
	String uname = user.getUser_name();

	DataFile inputFile;
	if (basedon>0) {
	    inputFile = (DataFile)em.find(DataFile.class, basedon);
	    Logging.info("Directed to use profile: " + inputFile);
	    if (inputFile==null) throw new IllegalArgumentException("User Profile DataFle no. " + basedon + " does not exist!");
	    if (!inputFile.getUser().equals(uname)) throw new IllegalArgumentException("User Profile DataFle no. " + basedon + " is not for user "+uname+"!");
	} else {
	    inputFile = DataFile.getLatestFileByVersion(em, uname, ptype, allowedFileVersions());
	    Logging.info("Choose to use profile: " + inputFile);
	}
	
	UserProfile upro;
	if (inputFile != null) {
	    // read it
	    upro = new UserProfile(inputFile, aa);
	} else {
	    // Generate it.
	    // empty profile, as TJ wants (2012-06; 2013-04-03)
	    upro = new UserProfile(aa);
	    DataFile uproFile=upro.saveToFile(uname, 0, ptype);

	    em.persist(uproFile);
	    inputFile = uproFile;
	}
	ptr.add(inputFile);
	return upro;
    }

    /** This is set if we're carrying out this run just for one user */
    static private String onlyUser = null;
    /** Special modes: only computing user profiles, or only suggestion lists */
    static private boolean doProf = true, doSug = true;
    /** This is supplied on command line if we want to specifically
	create a sug list based on a specific profile. This option can
	only be used in the "onlyUser" mode; the profile specified by it
	should belong to that user.
    */
    static private int basedon = 0;
    /** Command-line options, used to explicitly specify the document
	date range for the suggestion generator. They are used to
	emulate the operation of the suggestion generator at some
	earlier date */
    static private Date forcedSince=null, forcedToDate=null;
    /** The IDs for the new user profile  and/or suggestion list DataFiles.
	These can only be supplied if we're in the "onlyUser" mode. */
    static private long newProfID = 0, newSugID=0;

    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	
	if (argv.length == 0) {
	    System.out.println("Usage: Daily [init|update]");
	    return;
	}

	onlyUser = ht.getOption("user", null);
	refined = ht.getOption("refined", refined);
	doProf = ht.getOption("prof", doProf);
	doSug  = ht.getOption("sug", doSug);
	basedon = ht.getOption("basedon", basedon);
	forcedSince=ht.getOptionDate("since",null);
	forcedToDate=ht.getOptionDate("until",null);
	newProfID=ht.getOptionLong("newProfID", 0);
	newSugID=ht.getOptionLong("newSugID", 0);

	String stoplist = "WEB-INF/stop200.txt";
	stoplist = ht.getOption("stoplist",stoplist);
	UserProfile.setStoplist(new Stoplist(new File(stoplist)));


	if (basedon>0) {
	    if (doProf)  throw new IllegalArgumentException("-Dbasedon=... can only be used with -Dprof=false");
	    if (onlyUser==null) throw new IllegalArgumentException("-Dbasedon=... cannot be used without -Duser=....");
	}
	    
	if (newProfID!=0) {
	    if (!doProf)  throw new IllegalArgumentException("-DnewProfID=... cannot be used with -Dprof=false");
	    if (onlyUser==null) throw new IllegalArgumentException("-DnewProfID=... cannot be used without -Duser=....");
	}

	if (newSugID!=0) {
	    if (!doSug)  throw new IllegalArgumentException("-DnewSugID=... cannot be used with -Dsug=false");
	    if (onlyUser==null) throw new IllegalArgumentException("-DnewSugID=... cannot be used without -Duser=....");
	}


	if (forcedSince != null || 	forcedToDate!=null) {
	    Logging.info("Forced date range: " + forcedSince + " to " + forcedToDate);
	}

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