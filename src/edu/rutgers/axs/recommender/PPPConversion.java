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
import edu.rutgers.axs.indexer.Common;

/** 
 */
public class PPPConversion {

    /*
    static public List<DataFile> getAllRelevantSugLists(EntityManager em, int uid) {

	String qs = 
	    "select distinct df from DataFile df, PresentedList pl, Action a "+
	    "where df.id = pl.dataFileId and pl.id = a.presentedListId "+
	    "and a.user.id=:uid " + 
	    "and (a.src = :src1 or a.src = :src2) and a.presentedListId>0 "+
	    "order by df.id";

	Query q = em.createQuery(qs);

	q.setParameter("uid", uid);
	q.setParameter("src1", Action.Source.MAIN_SL );
	q.setParameter("src2", Action.Source.EMAIL_SL );

	return (List<DataFile>)q.getResultList();
    }
    */


  /** Updates and saved the user profile for the specified user, as
	long as it makes sense to do it (i.e., there is no profile yet,
	or there has been some usable activity since the existing profile
	has been created)
     */
    private static void recreateP3Profile(EntityManager em,  
				      IndexSearcher searcher, 
				      User u)  throws IOException {

	IndexReader reader =searcher.getIndexReader();
	//	final DataFile.Type ptype = DataFile.Type.PPP_USER_PROFILE, 
	//	    stype =  DataFile.Type.PPP_SUGGESTIONS;
	final String uname = u.getUser_name();

	PPPFeedback[] allFeed = PPPFeedback.allFeedbacks(em, u);

	//List<DataFile> sugLists = getAllRelevantSugLists( em, u.getId());

	System.out.println("Found " + allFeed.length + " relevant suggestion lists");
	int cnt=0;
	for(PPPFeedback actionSummary: allFeed) {
	    System.out.println("Sug list ["+cnt+"](id="+actionSummary.sugListId +"), actions on " + actionSummary.size() + " pages");
	    cnt ++;
	}

	UserProfile upro = new 	UserProfile(reader);


	cnt = 0;
	int rocchioCnt = 0; // how many doc vectors added to profile?
	long lid = 0;
	for(PPPFeedback actionSummary : allFeed) {
	    System.out.println("Applying updates from sug list ["+(cnt++)+"](id="+ actionSummary.sugListId +")");

	    if (actionSummary.size() == 0) continue;
	    DataFile df = (DataFile)em.find(DataFile.class, actionSummary.sugListId);
	    rocchioCnt += actionSummary.size();
	    lid = Math.max(lid,  actionSummary.getLastActionId());

	    boolean topOrphan = df.getPppTopOrphan();

	    File f = df.getFile();
	    Vector<ArticleEntry> entries = ArticleEntry.readFile(f);
	    HashMap<String,MutableDouble> updateCo = actionSummary.getRocchioUpdateCoeff(topOrphan, entries);
	    System.out.println("The update will be a linear combination of " + updateCo.size() + " documents:");
	    for(String aid: updateCo.keySet()) {
		System.out.println("w["+aid + "]=" +  updateCo.get(aid));
	    }
	    //upro.rocchioUpdate(updateCo );
 	    //upro.setTermsFromHQ();
	}



	if (rocchioCnt==0 ) {
	    //	    System.out.println("There is no need to update the existing profile " + oldProfileFile +", because there no important actions based on it have been recorded");
	    return;
	}
	/*
	DataFile outputFile=upro.saveToFile(uname, 0, ptype);
	if (oldProfileFile!=null) {
	    outputFile.setInputFile(oldProfileFile);
	}
	outputFile.setLastActionId(lid);

	em.getTransaction().begin(); 
	em.persist(outputFile);
	em.getTransaction().commit();
	Logging.info("Saved profile: " + outputFile);
	*/
     }
    


}
