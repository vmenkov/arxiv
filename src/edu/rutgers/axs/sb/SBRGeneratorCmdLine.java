package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.FileIterator;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.util.Util;
import edu.rutgers.axs.harness.TestHarness;


/** SB recommendation generator test harness. 

    <p> An extension of SBRGenerator provided with a test harness, so
    that it can be tested from command line (instead of being part of
    the My.ArXiv web application).

    <h3>Usage - via a wrapper shell script</h3>

    <p>Here's a brief user guide.

<pre>
cd ~/arxiv/arxiv
./sb-test-harness.sh -sbMethod SUBJECTS sb.in.dat sb.SUBJECTS.out.tmp
./sb-test-harness.sh -sbMethod COACCESS sb.in.dat sb.COACCESS.out.tmp
./sb-test-harness.sh -sbMethod ABSTRACTS sb.in.dat sb.ABSTRACTS.out.tmp
./sb-test-harness.sh -sbMethod CTPF sb.in.dat sb.CTPF.out.tmp
... etc ...
</pre>

<p>
The input file (sb.in.dat) contains a list of ArXiv article IDs, normally one per line; the output file will contain the recommendation lists generated by the specified recommendation engine after it has seen 2, 3, 4, etc inputs article IDs.

<p>(One can have several AIDs on the same line; in this case, only one
list generation will be triggered after all these AIDs have been entered.)

<p>
The name of the SB recommendation generation method (one of those listed at 
{@link edu.rutgers.axs.sb.SBRGenerator.Method}; they also appear in the URLs linked to from the first column of the table at  
<a href="/arxiv/tools/index.jsp#sb">http://my.arxiv.org/arxiv/tools/index.jsp#sb</a>) is specified with the option -sbMethod. One can also supply all other sb-related parameters to the test harness class, but at the moment this is not exposed at the shell script command line level (see below).

<p>There is also a script with a loop, testing several recommenders on the same input file. All output files go to the same specified directory.
<pre>
./sb-ctpf-d.sh -dir ../runs/sb.out sb.in.dat
</pre>

<h3>Usage - directly using the Java app</h3>

<p>You can of course use the Java application directly. To make sure you use a correct class path etc, you can take the code in sb-test-harness.sh as the example.

<p>Using the Java command line application:
<pre>
java [java_options] [options] edu.rutgers.axs.sb.SBRGeneratorCmdLine in_file out_file
</pre>
The following options are available
<pre>
-DsbMethod=SUBJECTS|COACCCESS|CTPF|...       <em>(the SBRL generation method)</em>
-DsbStableOrderMode=0|1|2 <em>(0=no stable order; 1,2=various modes)</em>
-DsbMergeWithBaseline=false|true  <em>(if true the generated list will be 
                                 team-draft merged with the baseline, SUBJECTS)</em>
-Dsb.CTPF.T=Infinity|1.0|10.0|... <em>"Temperature" for CTPF</em>
-DsbDebug=false|true
-DshowScores=false|true           <em>Show scores along with ArXiv article IDs in the output file?</em>
</pre>

<h3>Details</h3>

<p>
The application will create a simulated session in the database, with one user action ({@link edu.rutgers.axs.sql.Action.Op#VIEW_ABSTRACT}) per each article ID in the input file. The session will be associated with the artificial user names "simulated_sb".

<p>Every time an article view is added to the simulated session, the application will request the SBRGenerator to generate a session-based recommendation list, just like it happens in a real-life session. As usual, SBRL generation is only triggered once at least 2 pages have been "viewed" by the simulated users.

<h3>Differences between simulated and real sessions</h3> 

<p> There may be a few differences between the SBR lists generated
using a "live" web application and the ones produced in a simulated session. 

<ul>
<li>In a real session, you may not have a new SBR list generated after every new viewed page, due to system load. E.g., if list L1 has been generated after the user has viewed the first 2 articles (p1, p2), and then the user has viewed three more articles (p3,p4,p5) while L1 was generated, then the next generated list, L2, will be based on all 5 articles (p1 thru p5). On the other hand, the command line application will always wait for the completion of the rec list generation before adding one more article; thus, for an input list of <em>N</em> articles, you always will have  <em>N</em>-1 rec lists produced.  (One could think that skipping some lists during a real web session would not affect the lists which <em>are</em> generated, but this is not quite so; remember the "stable order" role!)

<li>The set of excluded pages may be different. In the real web environment, all articles viewed by the user, as well as all articles links to which appeared in various pages (search results, ArXiv "Recent Articles" pages, etc) seen by the user, will be excluded from the rec list. The command line application does not have this kind of context (search results pages etc), so the exclusion list is simply the same as the input list.

</ul>

*/
public class SBRGeneratorCmdLine extends SBRGenerator {

    private User user;
    /** Stays on for the duration of this object's existence */
    private EntityManager em;

    public SBRGeneratorCmdLine(SessionData _sd) throws WebException, IOException {
	super(_sd, false);
    }
    
    /** The user name under which the command line operator
	operates. This user's recorded activity can be later excluded
	from various analytics, such as computing local coaccess numbers.
     */
    static final String simulatedUname ="simulated_sb";

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
	g.init(ht, true, true);

	g.em = sd.getEM();
	g.user= TestHarness.createDummyUser( g.em, simulatedUname, User.Program.SB_ANON, null);
	g.sd.storeUserName(simulatedUname);
	g.sd.storeUserInfoInSQL(g.em, g.user);

	return g;
    }

    /** Carries out essentially the same set of operations that
	FilterServlet does when a page is viewed. This also 
	causes an indirect call to SBRGenerator.addAction()
     */
    void createActionSB(EntityManager em, String aid) {

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
	System.out.println("A dash ('-') can be used instead of the input file name, to indicate stdin");
	System.exit(1);
    }


   /** This is designed for command line driven testing of the SB
	recommendation engines. The parameters which are normally
	passed to the SBRGenerator via the URL query string are
	expected to be supplied as command line options (system
	properties), e.g. -DsbMethod=COACCESS -DminArticleCount=5.
	For more information on paramters, see methods SBRGenerator.init(...).


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

	boolean showScores = ht.getOption("showScores", true);

	SBRGeneratorCmdLine g = SBRGeneratorCmdLine.create(ht);
	

	// figuring if we need the CTPF data
	if (g.sbMethod == SBRGenerator.Method.CTPF) {
	    SBRGWorkerCTPF.loadFitIfNeeded();
	    Logging.info("Waiting on CTPF data load (needed for method=" + g.sbMethod + ")");
	    SBRGWorkerCTPF.waitForLoading();
	} else {
	    Logging.info("Canceling CTPF data load (not needed for method=" + g.sbMethod + ")");
	    SBRGWorkerCTPF.cancelLoading();
	}

	//out.println(g.description());

	EntityManager em = g.em;
	int inCnt=0;
	FileIterator it = FileIterator.createFileIterator(infile); 
	while(it.hasNext()) {
	    String aids[] = it.next().split("\\s+");
	    inCnt ++;
	    for(String aid: aids) {
		// add new action to the SQL database and to the generator
		g.createActionSB(em, aid);
		g.recordLinkedAid(aid);
	    }
	    // trigger update thread
	    
	    String s= "["+inCnt+"] Added "+aids.length+" user action(s): article view " + Util.join(",", aids);
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
		    s = e.getAid();
		    if (showScores) s += "\t" + e.getScore();
		    out.println(s);
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
