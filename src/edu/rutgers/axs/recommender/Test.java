package edu.rutgers.axs.recommender;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.ArticleEntry;

public class Test {

    // Where our index lives.
    //private Directory indexDirectory;
    private IndexReader reader;

    public Test()  throws IOException {
	reader =  ArticleAnalyzer.getReader();
    }
   
    static int maxDocs = 100;

    /**
       -Dwatch=docid1[:docid2:....]
     */
    static public void main(String[] argv) throws IOException {
	ParseConfig ht = new ParseConfig();
	UserProfile.maxTerms = ht.getOption("maxTerms", UserProfile.maxTerms);
	maxDocs = ht.getOption("maxDocs", maxDocs);
	boolean raw = ht.getOption("raw", true);

	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	System.out.println("maxTerms=" + UserProfile.maxTerms +", raw=" + raw + ", maxDocs=" + maxDocs +"; stoplist.size=" + UserProfile.getStoplist().size());

	Test x = new Test();
	UserProfile.setDebug(x.reader, ht);

	EntityManager em = Main.getEM();

	ArticleStats[] allStats = ArticleStats.getArticleStatsArray(em, x.reader);	    

	for(String uname: argv) {
	    System.out.println("User=" + uname);
	    UserProfile upro = new UserProfile(uname, em, x.reader);	   

	    ArxivScoreDoc[] sd =
		raw ? upro.luceneRawSearch(maxDocs *10, allStats, em, 0 ):
		upro.luceneQuerySearch(maxDocs * 10, 0);

	    ArticleEntry.save(upro.packageEntries(sd), new File("linsug.txt"));

	    TjAlgorithm1 algo = new TjAlgorithm1();
	    sd = algo.rank( upro, sd, allStats, em, maxDocs);
	    Vector<ArticleEntry> entries = upro.packageEntries(sd);

	    ArticleEntry.save(entries, new File("algo1.txt"));

	    int startat=0;
	    int pos = startat+1;
	    for(int i=startat; i< entries.size() ; i++) {
		ArticleEntry ae= entries.elementAt(i);

		//		System.out.println("("+(i+1)+") internal id=" + scoreDocs[i].doc +", id=" +aid);
		System.out.println("["+ae.i+"] (score="+ae.score+
				   ") arXiv:" + ae.id + ", " + ae.titline);
		System.out.println(ae.authline);
	    }
	}
    }

}