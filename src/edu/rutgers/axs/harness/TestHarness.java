package edu.rutgers.axs.harness;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.util.Util;

/** This class provides a platform for online experimentation with the
  recommender engines that are used to provide daily recommendation
  lists for registered users. As of April 2015, only EE5 is supported.

  <p>The overall framework and the command line interface for this
  class is fairly similar to those for {@link
  edu.rutgers.axs.sb.SBRGeneratorCmdLine}; however, there are
  substantial differences due to the fact that the daily recommenders are
  designed to serve registered users, and make significant use of the context 
  in which user actions have occurred.

  <h3>How to use the program</h3>

<p>A command line shell script is provided for convenience. Usage:

<pre>
 ~/arxiv/arxiv/simulate-daily.sh -dir output-directory input-file.dat 
</pre>

  <h3>Overview of the process</h3>

  <p>This is how the test platform works, in case of EE5.

  <ul> <li>A "dummy user" (<tt>simulated_u</tt>) is created. The
  simulated user is assigned to he EE5 experimental plan ({@link
  edu.rutgers.axs.sql.User.Program#EE5}). The user is given the set of
  categories of interest consisisting of the primary categories of all
  articles in the input file.

  <li>The simulated user's old action history, is exists, is
erased. For each article listed in the input file, we add a
VIEW_ABSTRACT action ({@link edu.rutgers.axs.sql.Action}) to the
user's (simulated) action history. In order for EE5 recommender to
accept these actions as actionable user feedback, we set each action's
source as if the user had visited the article's page by following the
link from an earlier EE5 rec list (even though in reality, of course,
that page would not have appeared on any EE5 rec list).

<li>After each addition of an action (or of several actions, if
several article IDs were listed on the same line in the input file),
the EE5 recommendation engine is run to produce the recommendation
list for the dummy user based on its recorded simulated action
history.  The resulted recommendation list is reported in the output
file.
  </ul>

<h3>Input file format</h3>

<p>Normally, one ArXiv article ID (AID) per line. You can also have
several AIDs on a single line, in which case the recommendation
generator will be run only once after the simulated actions for all
AIDs have been added to the simulated user's action history.

<h3>Output file format</h3>

<p>After a brief information about the input (an added actions, etc),
the list of recommended articles is printed. Besides the AID and the
score, each line also contains brief information about the article
(categories, title), and some numbers explaining its origin on the
list (doc cluster ID, &xi;, and the value of <em>c<sup>*</em></sup>(<em>u</em>,&alpha;,&beta;)).

 */
public class TestHarness {

    /** Creates a persistent User entry for a dummy user, if it does
	not exist already, or modifies and returns the already existing one.

	@param program The experiment plan to enroll the user into. For SB this does not matter; for nightly recommenders, this should match the recommender type.
	@param cats The set of categories of interest to assign to the user. (This is needed for recommenders such as PPP or EE5)
     */
    public static User createDummyUser(EntityManager em, String uname, User.Program program, Set<String> cats) throws WebException {
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
	u.setProgram(program);
	if (cats==null) cats = new HashSet<String>();
	u.setCats(cats);
	em.persist(u);
	em.getTransaction().commit();
	return u;
    }

    /** Deletes all actions for a user. This only should be used for dummy users, 
	whose accounts we may reset and refill with dummy actions, in order
	to use them for testing a recommendation engine.	
     */
    public static void deleteActionsForUser(EntityManager em, User user)  throws IOException {
	String query = 	    "delete from Action a where a.user.id=:u";
	javax.persistence.Query q = em.createQuery(query);
	Logging.info("Query: " + query);
	q.setParameter("u", user.getId());
	em.getTransaction().begin();
	int n= q.executeUpdate();
	em.getTransaction().commit();
	Logging.info("" + n + " rows updated");
    }

    static void createActionEE5(EntityManager em, User user, String aid) {

	em.getTransaction().begin();		
	// no commit needed, since we're inside a transaction already
	//Article art=Article.getArticleAlways(em, aid,false);
	Article art = Article.findByAid( em, aid);
	if (art == null) throw new IllegalArgumentException("Article " + aid + " is not in My.ArXiv's local database; won't proceed");

	ActionSource asrc = new ActionSource(Action.Source.MAIN_EE5,0);
	Action a = SessionData.addNewAction(em,  user,  Action.Op.VIEW_ABSTRACT,
					    art, null, asrc, null);
	em.getTransaction().commit(); 
    }

    private static HashSet<String> prepareCatSet(IndexSearcher searcher, Vector<String[]> aidsList) throws IOException {
	HashSet<String> set = new  HashSet<String>();
	for(String aids[] : aidsList ) {
	    for(String aid: aids) {
		int docno = Common.find(searcher, aid);		    
		Document doc = searcher.doc(docno);
		String subj = doc.get(ArxivFields.CATEGORY);
		String[] cats =	CatInfo.split(subj);
		for(String cat: cats) set.add(cat);
	    }
	}
	return set;
    }
    
    static void usage() {
	System.out.println("Usage: java [options] TestHarness aid_list_file [out_file]");
	System.exit(1);
    }

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
	boolean showTitles = ht.getOption("showTitles", true);

	// special params for EE5
	String basedir = ht.getOption("basedir", edu.rutgers.axs.ee5.Files.getBasedir());
	edu.rutgers.axs.ee5.Files.setBasedir(basedir);
	edu.rutgers.axs.ee5.Files.mode2014 = ht.getOption("mode2014", edu.rutgers.axs.ee5.Files.mode2014);


	User.Program program = (User.Program)ht.getEnum(User.Program.class, "program", User.Program.EE5);
	
	System.out.println("Program=" + program);
	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);
	EntityManager em  = Main.getEM();
	
	// read in the list of AIDs
	Vector<String[]> aidsList = FileIterator.readAids(infile); 

	HashSet<String> catSet = prepareCatSet(searcher, aidsList);
	System.out.println("The input list contains " + catSet.size() + " categories: " +catSet+". Will assign them as cats of interest to the simulated user");

	String uname = "simulated_u";
	User user= createDummyUser( em, uname, program, catSet);
	deleteActionsForUser(em, user);

	int inCnt=0;
	for(String aids[] : aidsList ) {
	    inCnt ++;
	    for(String aid: aids) {
		if (program==User.Program.EE5) {
		    createActionEE5( em, user, aid);
		} else {
		    throw new IllegalArgumentException("program " + program + " not supported");
		}
	    }

	    
	    String s= "["+inCnt+"] Added "+aids.length+" user action(s): article view " + Util.join(",", aids);
	    out.println(s);
	    DataFile df=null;
	    if (program==User.Program.EE5) {
		df = edu.rutgers.axs.ee5.Daily.simulatedUserUpdates(em, searcher, user);
	    } else {
		throw new IllegalArgumentException("program " + program + " not supported");
	    }

	    if (df!=null) {
		DataFile.Type type = df.getType();
		//		if (type.isProfile()) {}
		SearchResults sr = new SearchResults(df, searcher);

		s = "Rec list updated, "+
		    sr.entries.size() + " articles";
		out.println(s);

		final int M=30;
		int cnt=0;
		for(ArticleEntry e: sr.entries) {
		    s = e.getAid();
		    if (showScores) s += "\t" + e.getScore();
		    if (showTitles) {
			// int docno = Common.find(searcher, e.getAid());
			// Document doc = searcher.doc(docno);
			e.populateOtherFields(searcher);
			s += "\t" + e.subj + "\t" + e.titline;
			s += "\t" + "; " + e.researcherCommline;
		    }
		    out.println(s);
		    if (cnt++ >= M) break;
		}


	    } else { // there must have been an error
		s = "No rec list produced. Error?";
		out.println(s);
	    }


	}
	out.close();
   }


}
