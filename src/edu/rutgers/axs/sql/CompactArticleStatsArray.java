package edu.rutgers.axs.sql;

import java.util.*;
import java.io.*;

import javax.persistence.*;
import org.apache.openjpa.jdbc.conf.*;
import org.apache.openjpa.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
//import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.ArxivFields;
import edu.rutgers.axs.web.ResultsBase;
import edu.rutgers.axs.recommender.ArticleAnalyzer;

/** A compact alternative to ArticleStats[]; the idea is to save memory. */
public class CompactArticleStatsArray  {
 
    static final int NB=ArticleAnalyzer.upFields.length;
    float [][] boost = new float[NB][];
    
    public int size() { 
	return boost[0].length;
    }

    public float getBoost(int docno, int i) {
	return boost[i][docno];
    }

    /** An auxiliary thread for the asynchronous reading of the
	allStats[] array on start-up. The idea is that if the first
	tasks to process do not require allStats[], the main thread
	can start working on them even as allStats[] is loading.
     */


    static public class CASReader extends Thread {
	private CompactArticleStatsArray casa = new CompactArticleStatsArray();
	boolean error = false;
	Exception ex=null;
	private IndexReader reader;
	public CASReader(IndexReader _reader) { reader= _reader;}

	public void 	run() {
	    try {

		int n=	reader.numDocs() ;
		for(int i=0; i<NB; i++) casa.boost[i] = new float[n];

		HashMap<String,Integer> aidToDocno = new HashMap<String,Integer>();
		for(int docno=0; docno<n; docno++){
		    Document doc = reader.document(docno,ArticleStats.fieldSelectorAid);
		    String aid = doc.get(ArxivFields.PAPER);
		    aidToDocno.put(aid, new Integer(docno));
		}


		EntityManager em = Main.getEM();
		JDBCConfiguration conf = (JDBCConfiguration)
		    ((OpenJPAEntityManagerSPI)em).getConfiguration(); 
		conf.setFetchBatchSize(1000); // trying to avoid OOM
		List<ArticleStats> aslist = ArticleStats.getAll( em);

		for(ArticleStats as: aslist) {
		    Integer tmp =  aidToDocno.get(as.getAid());
		    if (tmp==null) {
			Logging.warning("unexpectedly missing aid=" +as.getAid() + " in Lucene?");
			continue;
		    }
		    int docno = tmp.intValue();
		    for(int i=0; i<NB; i++) {
			casa.boost[i][docno] = (float)as.getBoost(i);
		    }
		}
		ResultsBase.ensureClosed( em, false);
	    } catch(Exception _ex) {
		ex = _ex;
		error=true;
		Logging.error("Failed to read pre-computed AllStats. exception=" + ex);
	    }
	}

	/** This is a blocking method, which is called from the
	    parent's thread. It waits for this thread to complete, 
	    and return the results. */
	public CompactArticleStatsArray getResults() throws Exception {
	    while(getState()!=Thread.State.TERMINATED) {
		Logging.info("Waiting for CASR");
		try {	 // it is the parent thread who waits!	    
		    Thread.sleep(10 * 1000);
		} catch ( InterruptedException ex) {}			
	    }
	    if (casa==null) {
		Logging.error("CASR: Ouch, no allStats! See the error message someplace above...");
		throw (ex!=null)? ex:
		    new IOException("CASR: Somehow could not read allStats from the SQL server");		
	    } else {
		return casa;
	    }
	}
    }
}

