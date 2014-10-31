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
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.Common;

/** Code for initializing 3PR (aka PPP) user profiles using user-uploaded
    documents (aka the "Toronto System").

    <p>The algorithm is based on Thorsten's 2014-09-25 message, "Using
    the "Toronto System" with 3PR (or other recommenders)?"

    <blockquote> 
    Let D_Toronto be the documents uploaded into the Toronto system,
    and let D_ArXiv be all documents in Arxiv consistent with the
    categories the user indicated as relevant. We then simulate
    |D_Toronto| "days" of interaction, iterating through the
    documents from D_Toronto. On each day, we pick a random subset of
    D_Arxiv of size 9 and one document from D_Toronto, sort them
    according to the current ranking function (without perturbation,
    just the old co-active learning algorithm from the 2012 paper,
    since we don't have noise on user clicks in our simulation), and
    simulate that the user gives "Interesting" feedback for the
    D_Toronto document. We then compute the coactive learning update
    and get a new w. The final w is the initialization of the 3PR
    algorithm.  
    </blockquote>


 */
public class TorontoPPP {

    private static void makeP3SugX(EntityManager em,  UserProfile upro,
				   ArticleAnalyzer aa, IndexSearcher searcher, User u) 
    throws IOException {
	String msg="";
	//Vector<DataFile> ptr = new  Vector<DataFile>(0);

	//	DataFile inputFile = ptr.elementAt(0);

	String uname = u.getUser_name();

	// restricting the scope by category and date,
	// as per Thorsten 2012-06-18
	String[] cats = u.getCats().toArray(new String[0]);
	if (cats.length==0) msg += " User "+uname+" has not chosen any categories of interest. ";

	// Let's have a very wide range
	//	Date since = Scheduler.chooseSince( em, u);
	int days = 365 * 3; 
	Date since  = SearchResults.daysAgo(days);

	int maxlen = 10000;

	// User-uploaded examples 
	ScoreDoc[] uuSd = Common.findAllUserFiles(searcher, uname);

	int nUploaded = uuSd.length; 
	int standardSugListSize = 10;

	Date toDate = new Date();

	SearchResults sr = 
	    SubjectSearchResults.orderedSearch(searcher,u,since,null, maxlen);

	// random selection
	int nCatSearch = sr.scoreDocs.length;
	int nEachRandomSample = (standardSugListSize - 1);
	if (nEachRandomSample > nCatSearch) {
	    Logging.warning("TorontoPPP: There are very few cat search results: " + nCatSearch + ", less than the desired " + nEachRandomSample);
	    nEachRandomSample = nCatSearch;
	}

	Logging.info("TorontoPPP("+uname+"): integrating pseudo-feedback from " + nUploaded + " docs, each one combined with " + nEachRandomSample + " random docs (from a pool of " + nCatSearch + ")");
	for(int k=0; k < nUploaded; k++) {

	    // A suggestion list made of 9 random articles and one
	    // user-uploaded article
	    ArxivScoreDoc[] sd = new ArxivScoreDoc[nEachRandomSample+1];
	    int [] selected = Util.randomSample(nCatSearch, nEachRandomSample);
	    for(int i=0; i <nEachRandomSample; i++) {
		sd[i] = new ArxivScoreDoc( sr.scoreDocs[ selected[i]]);
	    }

	    int uDocno = uuSd[k].doc;
	    sd[nEachRandomSample] = new ArxivScoreDoc( uuSd[k] ); 

	    //searcher.close();
	    TjAlgorithm1 algo = new TjAlgorithm1();
	    // rank by TJ Algo 1
	    sd = algo.rank( upro, sd,  em, standardSugListSize, false);
		
	    Vector<ArticleEntry> entries = upro.packageEntries(sd);
	    
	    String m = "TorontoPPP("+uname+":"+k+"), packageEntries = (";
	    for(int i=0; i<entries.size(); i++) {
		m += " " + entries.elementAt(i);
	    }
	    m += ")";
	    Logging.info(m);

	    // simulated feedback
	    HashMap<Integer,MutableDouble> updateCo = getRocchioUpdateCoeff(sd, uDocno);

	    // apply the feedback to the user profile
	    upro.rocchioUpdate2(updateCo );
 	    upro.setTermsFromHQ();

	}

	// save the profile
	final DataFile.Type ptype = DataFile.Type.PPP_USER_PROFILE;
	DataFile outputFile=upro.saveToFile(uname, 0, ptype);

	outputFile.setLastActionId(0);

	em.getTransaction().begin(); 
	em.persist(outputFile);
	em.getTransaction().commit();
	Logging.info("Saved profile: " + outputFile);
 

	// prepare suggestion list based on the final profile
	DailyPPP.makeP3Sug( em,  aa, searcher, u);

    }

    static class CoMap extends HashMap<Integer, MutableDouble> { 

	synchronized void addCo(Integer docno, double inc) {
		MutableDouble z=get(docno);
		if (z==null) {
		    put(docno,  new MutableDouble(inc));
		} else {
		    z.add(inc);
		}
	    }
    }


    
    /** This produces the same type of data as PPPFeedback.getRocchioUpdateCoeff() */
    private static HashMap<Integer,MutableDouble> getRocchioUpdateCoeff(ArxivScoreDoc[] sd, int uDocno) {
	
	CoMap updateCo = new CoMap();
	
	boolean over=false;
	for(int j=0; !over && j<sd.length; j++) {
	    int docno = sd[j].doc;
	    over = (docno == uDocno);
	    int jNew = (over) ? 0: j+1;
	    double inc =UserProfile.getGamma(jNew)-UserProfile.getGamma(j);
	    updateCo.addCo( docno, inc);
	}
	return updateCo;
    }


    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	
	if (argv.length == 0) {
	    System.out.println("Usage: Daily [init|update]");
	    return;
	}

	String onlyUser = ht.getOption("user", null);
	/*
	refined = ht.getOption("refined", refined);
	doProf = ht.getOption("prof", doProf);
	doSug  = ht.getOption("sug", doSug);


	basedon = ht.getOption("basedon", basedon);
	forcedSince=ht.getOptionDate("since",null);
	forcedToDate=ht.getOptionDate("until",null);
	newProfID=ht.getOptionLong("newProfID", 0);
	newSugID=ht.getOptionLong("newSugID", 0);
	*/

	String stoplist = "WEB-INF/stop200.txt";
	stoplist = ht.getOption("stoplist",stoplist);
	UserProfile.setStoplist(new Stoplist(new File(stoplist)));


	String cmd = argv[0];
	if (cmd.equals("init")) {
	    if (onlyUser==null) throw new IllegalArgumentException("No user specified");

	    EntityManager em  = Main.getEM();
	    IndexReader reader = Common.newReader();
	    ArticleAnalyzer aa =  new ArticleAnalyzer2( reader);
	    IndexSearcher searcher = new IndexSearcher( reader );

	    final User.Program program = User.Program.PPP;

	    User u = User.findByName( em, onlyUser);
	    if (u==null) throw new IllegalArgumentException("User " + onlyUser + " does not exist");
	    if (!u.getProgram().equals(program)) throw new IllegalArgumentException("User " + onlyUser + " is not enrolled in program " + program);

	    UserProfile upro =  new UserProfile(aa);

	    makeP3SugX( em,   upro,  aa,  searcher, u);
	    
	    /*
	    throw new IllegalArgumentException("not needed!");
	} else if (cmd.equals("update")) {
	    updates();
	    */
	} else {
	    System.out.println("Unknown command: " + cmd);
	}
    }


}
