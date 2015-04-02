package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import java.nio.charset.Charset;
import java.net.URLEncoder;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.FileIterator;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.html.RatingButton;
import edu.rutgers.axs.web.*;

/** An extension of SBRGenerator provided with a test harness, so that
    it can be tested from command line (instead of being part of the
    My.ArXiv web application).
*/
public class SBRGeneratorCmdLine extends SBRGenerator {

    /** Initializes the SBR generator based on command line parameters
	(supplied as system properties)
	@param ht Provides access to system properties. The properties,
	which typically are supplied on the command line with the
	standard -Dname=val syntax, should have the same name as the query 
	string parameters that would be used to control SBRG in the web 
	application.     
     */
    private synchronized void init(ParseConfig ht) throws WebException {
	setAllowedSB(true);
	sbStableOrderMode = ht.getOption("sbStableOrder", sbStableOrderMode);
	validateSbStableOrderMode();
	Method m = null;
	// the same param initializes both vars now
	sbDebug = ht.getOption("sbDebug", sbDebug);
	researcherSB = ht.getOption("sbDebug", researcherSB);
	m = (SBRGenerator.Method)ht.getOptionEnum(SBRGenerator.Method.class, "sbMethod", null);
	
	final boolean nothingDoneYet = sbrRunning==null && sbrReady==null;

	if (requestedSbMethod == null ||
	    (m!=null && nothingDoneYet)) { //has not been set before, must set now
	    requestedSbMethod = (m==null) ? Method.ABSTRACTS : m;
	    if (sbMethod!=null && !nothingDoneYet) {
		throw new IllegalArgumentException("Somehow we have already set the SB method, and cannot change it anymore!");
	    }
	    sbMethod = (requestedSbMethod == Method.RANDOM)?
		pickRandomMethod() : requestedSbMethod;

	    sbMergeWithBaseline = ht.getOption("sbMergeWithBaseline", false);

	    Logging.info("SBRG(session="+sd.getSqlSessionId() +").init(): requested method=" + requestedSbMethod +"; effective  method=" + sbMethod + ". Merge with baseline = " + sbMergeWithBaseline);

	    worker =createWorker(this);

	} else if  (m==null || requestedSbMethod == m ) {
	    // OK: has already been set, and no attempt to change it now 
	} else  {  // prohibited attempt to re-set it differently
	    String msg = "Cannot change the SB method to " + m + " now, since "  + requestedSbMethod + " already was requested before";
	    Logging.error(msg);
	    throw new IllegalArgumentException(msg);
	}
	em = sd.getEM();
	String uname = "simulated_sb";
	user= createDummyUser( em, uname);
	sd.storeUserName(uname);
	sd.storeUserInfoInSQL(em, user);
    }

    private User user;
    /** Stays on for the duration of this object's existence */
    private EntityManager em;

    public SBRGeneratorCmdLine(SessionData _sd) throws WebException, IOException {
	super(_sd, false);
    }

    /** Creates a SBR generator configured based on command line
	parameters (supplied as system properties)
	@param ht Provides access to system properties. The properties,
	which typically are supplied on the command line with the
	standard -Dname=val syntax, should have the same name as query 
	string parameters that would be used to control SBRG in the web 
	application.     
     */
    public static SBRGeneratorCmdLine create(ParseConfig ht) throws WebException, IOException {
	SessionData sd = SessionData.getSessionData(null);
	SBRGeneratorCmdLine g = (SBRGeneratorCmdLine)sd.sbrg;
	g.init(ht);
	return g;
    }

    /** Creates a persistent User entry for a dummy user, if it does
	not exist already, or returns the already existing one.
     */
    private static User createDummyUser(EntityManager em, String uname) throws WebException {
	em.getTransaction().begin();
	
	String pw = "xxx";
	User u = User.findByName(em, uname);

	if (u == null) {
	    System.out.println("Creating user: " + uname + ", password=" + pw);
	    u = new User();
	    u.setUser_name(uname);
	    u.encryptAndSetPassword(pw);
	    em.persist(u);
	} else {
	    System.out.println("User "+uname+" already exists; no need to create");
	}
	em.getTransaction().commit();
	
	em.getTransaction().begin();
	u = User.findByName(em, uname);
	// System.out.println("Reading back user record: " + u.reflectToString() );

	Role.Name rn = Role.Name.subscriber;
	Role r = (Role)em.find(Role.class, rn.toString());
	if (r == null) throw new IllegalArgumentException("No Role object found: " + rn);
	System.out.println("Adding role to user: "+uname+ ", role=" + r);
	u.addRole(r);
	em.persist(u);
	em.getTransaction().commit();
	return u;
    }

    /** Carries out essentially the same set of operations that
	FilterServlet does when a page is viewed. This also 
	causes an indirect call to SBRGenerator.addAction()
     */
    void createAction(EntityManager em, String aid) {

	em.getTransaction().begin();		
	// no commit needed, since we're inside a transaction already
	//Article art=Article.getArticleAlways(em, aid,false);
	Article art = Article.findByAid( em, aid);
	if (art == null) throw new IllegalArgumentException("Article " + aid + " is not in My.ArXiv's local database; won't proceed");

	ActionSource asrc = new ActionSource(Action.Source.SEARCH,0);
	Action a = sd.addNewAction(em, user, Action.Op.VIEW_ABSTRACT,
				   art, null, asrc);

	em.getTransaction().commit(); 
    }

    static void usage() {
	System.out.println("Usage: java [options] SBRGeneratorCmdLine aid_list_file [out_file]");
	System.exit(1);
    }


   /** This is designed for command line testing. The parameters which are
	normally passed to the SBRGenerator via the URL query string 
	are expected to be supplied as command line options (system properties),
	e.g. -DsbXXXX=YYYY
    */
   static public void main(String[] argv) throws Exception {
       ParseConfig ht = new ParseConfig();
	
	int ia = 0;
	if (ia >= argv.length) {
	    usage();
	    return;
	}
	String infile = argv[ia++];

	PrintStream out = System.out;
	if (ia < argv.length) {
	    String outfile = argv[ia++];
	    if (!outfile.equals("-")) {
		File f2 = new File(outfile);	    
		System.out.println("Results will be written to file " + f2);
		out = new PrintStream(new FileOutputStream(f2));
	    }
	}

	SBRGeneratorCmdLine g = SBRGeneratorCmdLine.create(ht);

	// figuring if we need the CTPF data
	if (g.sbMethod == SBRGenerator.Method.CTPF) {
	    Logging.info("Waiting on CTPF data load (needed for method=" + g.sbMethod + ")");
	    SBRGWorkerCTPF.waitForLoading();
	} else {
	    Logging.info("Canceling CTPF data load (not needed for method=" + g.sbMethod + ")");
	    SBRGWorkerCTPF.cancelLoading();
	}

	EntityManager em = g.em;
	int inCnt=0;
	FileIterator it = FileIterator.createFileIterator(infile); 
	while(it.hasNext()) {
	    String aid = it.next();
	    inCnt ++;
	    // add new action to the SQL database and to the generator
	    g.createAction(em, aid);
	    // trigger update thread

	    String s= "["+inCnt+"] Added user action: article view " + aid;
	    out.println(s);

	    SBRGThread th = g.sbCheck();
	    if (th == null) {	
		// no update after the 1st page
		out.println("No rec list update happens at this point");
		continue;
	    }

	    waitToEnd(th);

	    if (th.sr!=null) {
		if (th != g.sbrReady) throw new AssertionError("sbrReady!=th");
		s = "Rec list updated, "+
		    th.sr.entries.size() + " articles, " + th.msecLine() +":";
		out.println(s);

		for(ArticleEntry e: th.sr.entries) {
		    out.println(e.getAid());
		}


	    } else { // there must have been an error
		s = "No rec list produced. Error=" + th.error + ": " +th.errmsg;
		out.println(s);
	    }

	    String desc = th.description();
	    s = "Excludable articles count: " + g.linkedAids.size();
	    if (th.excludedList!=null) {
		s += "\nActually excluded: " + th.excludedList;
	    }
	    out.println(s);
	}
	it.close();
	out.close();
   }

    /** Waits for the completion of the computation thread */
    private static void waitToEnd(Thread th) {
	while (true) {
	    try {		    
		th.join();
		return;
	    } catch ( InterruptedException ex) {}
	}
    }
}
