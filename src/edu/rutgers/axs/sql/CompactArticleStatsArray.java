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

	/** See some advice on fetch options here: 
	    http://openjpa.apache.org/builds/1.0.0/apache-openjpa-1.0.0/docs/manual/ref_guide_dbsetup_lrs.html
	*/
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
		Main.memory("CASA.run: have map");

		EntityManager em = Main.getEM();
		JDBCConfiguration conf = (JDBCConfiguration)
		    ((OpenJPAEntityManagerSPI)em).getConfiguration(); 
		final int BS=1;
		Logging.info("Default batch size="+conf.getFetchBatchSize()+"; change to " + BS);
		conf.setFetchBatchSize(BS); // trying to avoid OOM

		final boolean mode2 = true; // alternatives for reading data

		if (mode2) {
		    String sq = "select m.id, m.aid, m.boost0, m.boost1,  m.boost2,  m.boost3 from ArticleStats m";
		    Query q = em.createQuery(sq);
		    Main.memory("CASA.run: have query");
		    List res = q.getResultList();
		    Main.memory("CASA.run: have RL");
		    
		    for(Object o: res) {			    
			if (!(o instanceof Object[])) continue;
			Object[] oa = (Object[])o;
			String aid=(String)oa[1];
			Integer tmp =  aidToDocno.get(aid);
			if (tmp==null) {
			    Logging.warning("unexpectedly missing aid=" +aid + " in Lucene?");
			    continue;
			}
			int docno = tmp.intValue();
			for(int i=0; i<NB; i++) {
			    casa.boost[i][docno] = (float)((Double)oa[i+2]).doubleValue();
			}			    
		    }
		} else {

		    List<ArticleStats> aslist = ArticleStats.getAll( em);
		    
		    for(ArticleStats as: aslist) {
			String aid = as.getAid();
			Integer tmp = aidToDocno.get(aid);
			if (tmp==null) {
			    Logging.warning("unexpectedly missing aid=" +aid + " in Lucene?");
			    continue;
			}
			int docno = tmp.intValue();
			for(int i=0; i<NB; i++) {
			    casa.boost[i][docno] = (float)as.getBoost(i);
			}
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

