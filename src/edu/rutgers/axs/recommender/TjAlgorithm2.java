package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;

class TjAlgorithm2 {
    private UserProfile upro;
    private IndexSearcher searcher;
    private HashMap<String,Integer> termMapper;

    /**
       @param upro The last user profile (will be updated in updateProfile())
    */
    TjAlgorithm2(    UserProfile _upro) {
	upro = _upro;
	searcher = new IndexSearcher( upro.dfc.reader);	
	termMapper=upro.mkTermMapper();
    }

    private int skipCnt=0;

    /** Not passing //ArticleStats as, 
     */
    private void addToPhi(double [] phi, double gamma,  ArxivScoreDoc sd) 
	throws IOException
    {
	HashMap<String, ?extends Number> h = upro.dfc.getCoef(sd.doc);

	double f = gamma;

	for(Map.Entry<String, ?extends Number> e: h.entrySet()) {
	    double q = e.getValue().doubleValue();
	    String term = e.getKey();
	    if (termMapper.get(term)==null) {
		//Logging.warning("No TM("+term+")!");
		skipCnt++;
	    } else {
		int iterm = termMapper.get(term).intValue();
		phi[iterm] += f * q;
	    }
	}
    }


    /** Computes the vector phi=sum( gamma_i d_i), for Algorithm 2.
	@return Vector phi, aligned with upro.terms[]
     */
    private double[] computePhi(ArxivScoreDoc[] sd, EntityManager em)  
	throws IOException {
	skipCnt=0;
	double[] phi = new double[ upro.terms.length];
	for(int i=0; i<sd.length; i++) {
	    addToPhi( phi, upro.getGamma(i), sd[i]);
	}
	Logging.warning("ComputePhi: " + skipCnt + " elements have been skipped, as the terms appear only in artificially suggested docs, not in UserProfile");
	return phi;
    }

    /**
       @param sda0 The previous suggestion list that was built for
       that profile.  User's activity since then will be used as the
       source of the updates.
     */
    void updateProfile(String uname, EntityManager em,
		       ArxivScoreDoc[] sda0)  throws IOException {

	// Add zero entries for the terms that have occurred in docs viewed
	// since the UserProfile was last created or updated
	UserPageScore[]	ups = upro.updateVocabulary(uname, em);
	termMapper=upro.mkTermMapper(); // must update

	double[] phi0 = computePhi( sda0, em);
	
	// v will contain (in this order): the docs the user has
	// recently interacted with in a "positive" way, followed
	// by all docs from sd0 with which the user has not
	// recently interacted in any way. In other words,
	//  v = frontListed (+) (sd0 \ frontListed \ negativeSet)
	Vector<ArxivScoreDoc> v = new 	Vector< ArxivScoreDoc>();
	HashSet<Integer> frontListed = new HashSet<Integer>();
	HashSet<Integer> negativeSet = new HashSet<Integer>();

	int cnt=0;
	for(UserPageScore up : ups) {
	    String aid = up.getArticle();
	    int docno;
	    try {
		docno = Common.find( searcher,aid);
	    } catch (IOException ex) {
		Logging.info("A2: ignoring document named " + aid + ", as it's not found in the index");
		continue;
	    }

	    if (up.getScore() <=0) {
		negativeSet.add( new Integer(docno));
		Logging.info("A2: will exclude doc " + docno + ", due to negative feedback");
	    } else {
		ArxivScoreDoc sd = new ArxivScoreDoc(docno, 0); // dummy score
		v.add(sd);
		frontListed.add(new Integer(docno));
		Logging.info("A2: promoting doc " + docno + ", due to positive feedback");
		cnt++;
	    }
	}

	for(ArxivScoreDoc sd: sda0) {
	    Integer key =new Integer(sd.doc);
	    if (frontListed.contains(key)) continue;
	    // also. exclude docs for which we got *negative* feedback
	    if (negativeSet.contains(key)) continue;
	    v.add(sd);
	}

	ArxivScoreDoc[] sda1= v.toArray(new ArxivScoreDoc[0]);
	double[] phi1 = computePhi( sda1, em);

	for(int i=0; i<upro.terms.length; i++) {
	    //Logging.info("A2: Adding to "+upro.terms[i]+ "("+
	    //		 (phi1[i] - phi0[i]) + " , " + 
	    //		 (Math.sqrt(phi1[i]) - Math.sqrt(phi0[i])) + ")");

	    upro.add( upro.terms[i],    phi1[i] - phi0[i],
		      Math.sqrt(phi1[i]) - Math.sqrt(phi0[i]));
	}
	upro.setTermsFromHQ(); // re-sort the upro.terms[] array
	//return upro;
    }

}