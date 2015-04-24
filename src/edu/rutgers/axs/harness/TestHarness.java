package edu.rutgers.axs.harness;

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
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.util.Util;

public class TestHarness {

    /** Creates a persistent User entry for a dummy user, if it does
	not exist already, or returns the already existing one.
     */
    public static User createDummyUser(EntityManager em, String uname) throws WebException {
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

	User.Program program = (User.Program)ht.getEnum(User.Program.class, "program", User.Program.EE5);
	
	System.out.println("Program=" + program);

	EntityManager em  = Main.getEM();
	String uname = "simulated_u";
	User user= createDummyUser( em, uname);

	int inCnt=0;
	FileIterator it = FileIterator.createFileIterator(infile); 
	while(it.hasNext()) {
	    String aids[] = it.next().split("\\s+");
	    inCnt ++;
	    for(String aid: aids) {
		// add new action to the SQL database and to the generator
		/*
		g.createAction(em, aid);
		g.recordLinkedAid(aid);
		*/
	    }
	    // trigger update thread
	    
	    String s= "["+inCnt+"] Added "+aids.length+" user action(s): article view " + Util.join(",", aids);
	    out.println(s);
	    /*

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
	    */
	}
	it.close();
	out.close();
   }


}