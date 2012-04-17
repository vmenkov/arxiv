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

/** Computing document similarities, for the Bernoulli Rewards unit */
public class Similarities {

    static class Transposer {
	private HashMap<Integer, ArticleStats> byAstid;
	EntityManager em;
	Transposer(HashMap<Integer, ArticleStats> _byAstid,EntityManager _em) {
	    byAstid=_byAstid;
	    em = _em;
	}


	private int cnt=0;
	/** Maps ArticleStats id to SimsRow */
	HashMap<Integer, SimRow> map = new HashMap<Integer, SimRow>();
	void add(int astid, SimRow x) {
	    for(SimRowEntry e: x.entries) {
		Integer key  = new Integer(e.astid);
		SimRow r = map.get(key);
		if (r==null) { map.put(key, r=new SimRow()); }
		r.entries.add( e.transpose( astid));
		cnt++;
	    }
	    if (cnt>10000) flush();
	}
	/** Saves accummulated data into the database.  This includes
	 setSims() (sort of pointless, but we do the "set" as required by
	 OpenJPA docs), and persist()
	*/
	void flush() {
	    for(Integer key: map.keySet()) {
		ArticleStats q = byAstid.get(key);		
		em.getTransaction().begin();
		SimRow sr = q.getSims();
		if (sr==null) {
		    q.setSims( map.remove(key).sort());
		} else {
		    sr.mergeFrom(map.get(key).sort());
		    q.setSims(sr); 
		}
		em.persist(q);
		em.getTransaction().commit();	
	    }
	    map.clear();
	    cnt=0;
	}
    }



    static void allSims() throws IOException {
	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	
	ArticleAnalyzer z = new ArticleAnalyzer();
	EntityManager em  = Main.getEM();
	
	// array arranged by docno
	ArticleStats[] allStats = ArticleStats.getArticleStatsArray(em, z.reader); 

	String[] aids = Action.getAllPossiblyRatedDocs( em);
	HashMap<String, ArticleStats> h=new HashMap<String, ArticleStats>(); // map article id to ArticleStat entry
	HashMap<Integer, ArticleStats> byAstid = new HashMap<Integer, ArticleStats>();
	for(ArticleStats as: allStats) {
	    h.put(as.getAid(), as);
	    byAstid.put(new Integer((int)as.getId()), as);//FIXME: long?
	}

  	Logging.info("There are " + aids.length + " possibly rated docs");

	Transposer transposer=new 	Transposer(byAstid, em);

	int cnt=0;

	for(String aid: aids) {
	    int docno = -1;
	    try {
		docno = z.find(aid);
	    } catch(Exception ex) {
		Logging.warning("No document found in Lucene data store for id=" + aid +"; skipping");

		continue;
	    }

	    ArticleStats as = allStats[docno];
	    as.setSimsTime(new Date());

	    SimRow sr = new SimRow( docno, allStats, em,  z);
	    as.setSims(sr);
	    em.getTransaction().begin();
	    Logging.info("Persisting SimRow, |entries|=" + as.getSims().entries.size());
	    em.persist(as);
	    em.getTransaction().commit();	
	    cnt++;
	    if (cnt>3) break; // FIXME
	}
	transposer.flush();
    }


   static public void main(String[] argv) throws IOException {
       if (argv.length < 1) {
	   System.out.println("Command?");
	   return;
       }
       String cmd = argv[0];
       if (cmd.equals("view")) {
	   EntityManager em  = Main.getEM();
	   for(int j=1; j<argv.length; j++) {
	       String aid=argv[j];
	       ArticleStats as =  ArticleStats.findByAid(em, aid);
	       if (as==null) {
		   System.out.println("There is no document with id=" + aid );
		   continue;
	       }
	       SimRow sims = as.getSims();
	       if (sims==null) {
		   System.out.println("Document " + aid +" ("+as.getId()+") has no sims data");
		   continue;
	       } else if (sims.entries==null)  {
		   System.out.println("Document " + aid +" ("+as.getId()+") has no sims.entries");
		   continue;
	       } 
	       System.out.println("Document " + aid +" ("+as.getId()+") has sims with " + sims.entries.size() + " other docs");
	       for(SimRowEntry e : sims.entries) {
		   ArticleStats other = (ArticleStats)em.find( ArticleStats.class, new Integer(e.astid));
		   System.out.print(" ("+other.getAid()+ ": " +e.sim+")");
	       }
	       System.out.println();
	   }


	   em.close();
       } else {
  	   System.out.println("Unknown command: "+cmd);
     }
   }

}