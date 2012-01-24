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

	UserProfile.stoplist = new Stoplist(new File("WEB-INF/stop200.txt"));
	System.out.println("maxTerms=" + UserProfile.maxTerms +", raw=" + raw + ", maxDocs=" + maxDocs +"; stoplist.size=" + UserProfile.stoplist.size());

	Test x = new Test();
	UserProfile.setDebug(x.reader, ht);

	EntityManager em = Main.getEM();

	ArticleStats[] allStats = 
	    ArticleAnalyzer.getArticleStatsArray(em, x.reader);	    

	for(String uname: argv) {
	    System.out.println("User=" + uname);
	    UserProfile upro = new UserProfile(uname, em, x.reader);	   

	    Vector<ArticleEntry> entries=
		raw ? upro.luceneRawSearch(maxDocs, allStats ):
		upro.luceneQuerySearch(maxDocs);

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