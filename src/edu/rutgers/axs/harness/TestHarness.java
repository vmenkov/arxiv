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
	Vector<String[]> aidsList = new 	Vector<String[]>();
	FileIterator it = FileIterator.createFileIterator(infile); 
	while(it.hasNext()) {
	    aidsList.add( it.next().split("\\s+"));
	}

	HashSet<String> catSet = prepareCatSet(searcher, aidsList);
	System.out.println("The input list contains " + catSet.size() + " categories: " + catSet +". Will assign them as cats of interest to the simulated user");

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
	it.close();
	out.close();
   }


}