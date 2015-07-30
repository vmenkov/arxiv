package edu.rutgers.axs.ctpf;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.Logging;
import edu.rutgers.axs.indexer.*;

/** Maps Arxiv artice IDs (AIDs) to CTPF internal IDs and vice versa */
public class CTPFMap  {

    /** All Arxiv artice IDs (AIDs) in the map. The position in the array is
	the CTPF internal ID */
    private Vector<String> aIDs;
    /** Maps CTPF internal article ID to AID */
    public String  getInternalID_to_aID(int i) {
	//	return internalID_to_aID.get(i);
	return aIDs.elementAt(i);
    }

    public int aid2iid(String aid) {
	Integer q  =  aID_to_internalID.get(aid);
	if (q==null) throw new IllegalArgumentException("The CTPFMap has no mapping for AID=" + aid);
	return q.intValue();
    }

    /** Maps AID to CTPF internal ID */
    private HashMap<String, Integer> aID_to_internalID  = new HashMap<String, Integer>();

    /** @return The value N such that  valid internal IDs range from 1 to N-1
	(and there is also the dummy value 0)
     */
    public int size() { return aIDs.size(); }
   
    public boolean containsAid(String aid) {
	return aID_to_internalID.containsKey(aid);
    }

    /** Loads the map which associates CTPF internal doc ids with
	Arxiv article IDs (AIDs). We ignore a few "fake" AIDs that may
	appear in the map file but are not actually present in 
	our Lucene data store.

	@param num_docs Expected number of documents (size of map,
	inlcuding the "fake" document with the internal ID=0). The
	internal IDs are supposed to range from 1 thru num_docs. If a
	negative value is supplied, the size will be determined
	dynamically, based on the content of the data file.1

	@param  expectLinear If true, this method will also validate the
	input data: namely, check that IDs start with 1 and increase
	monotnously and with no gaps. This is used when we read in
	a data file produced by our own export routine, and want to
	make sure that the LDA output can be easily mapped to this doc list.
     */
    public CTPFMap(File file, int num_docs, boolean expectLinear) throws IOException { 
	IndexList il = new IndexList();
	HashSet<String> allAIDs = il.listAsSet(); // all articles in Lucene

	Logging.info("CTPFFit: loading document map from " + file);


	Reader fr = file.getPath().endsWith(".gz") ?
	    new InputStreamReader(new GZIPInputStream(new FileInputStream(file))) :
	    new FileReader(file);

        LineNumberReader br = new LineNumberReader(fr);

	int n = (num_docs < 0) ? 0 : num_docs;
	Vector<String> vAIDs = new Vector<String>(n);

        String line; 
	int invalidAidCnt = 0;
	String invalidAidTxt = "";
	final int M = 10;
	int prevIid = 0;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");
	    int iid = Integer.parseInt(parts[0]);
	    String aid = parts[1];
	    if (iid == 0) {
		if (expectLinear) {
		    String msg = "CPPFFit.loadMap("+file+", line="+br.getLineNumber()+"): found unexpected useless map entry for iid=" + iid + ", aid=" + aid;
		    Logging.error(msg);
		    throw new IllegalArgumentException(msg);
		}
		Logging.warning("CPPFFit.loadMap("+file+"): entering useless map entry for iid=" + iid + ", aid=" + aid);		
	    } else if (num_docs >=0 && iid >= num_docs) {
		Logging.warning("CPPFFit.loadMap("+file+"): ignoring useless map entry for iid=" + iid + ", aid=" + aid);
		continue;
	    } else if (expectLinear && iid != prevIid+1) {
		String msg = "CPPFFit.loadMap("+file+", line="+br.getLineNumber()+"): unexpected entry for iid=" + iid + ", aid=" + aid  + " following iid="+prevIid;
		Logging.error(msg);
		throw new IllegalArgumentException(msg);
	    } else if (!allAIDs.contains(aid)) {
		invalidAidCnt++;
		if (invalidAidCnt<M) invalidAidTxt += " " + aid;
		else if (invalidAidCnt==M) invalidAidTxt += " ...";
		continue;
	    }

            //internalID_to_aID.put(iid, aid);
	    //aIDs[iid] = aid;
	    if (iid >= vAIDs.size()) vAIDs.setSize(iid+1);
	    vAIDs.set(iid, aid);
            aID_to_internalID.put(aid, new Integer(iid));
	    prevIid=iid;
        }

	if (num_docs < 0) num_docs = vAIDs.size(); 
	aIDs = vAIDs;


	// basic validation
	int gapCnt=0;
	for(int i=1; i<num_docs; i++) {
	    if (aIDs.elementAt(i)==null) gapCnt++;
	}

	if (invalidAidCnt>0) {
	    Logging.warning("CTPFFit.loadMap("+file+"): " + invalidAidCnt + " lines have been ignored, because they contained AIDs not existing in our data store, such as:  " + invalidAidTxt);
	}

	//        Logging.info("CTPFFit: size of internalID_to_aID: " + internalID_to_aID.size());
        Logging.info("CTPFFit.loadMap("+file+"): size of aID_to_internalID: " + aID_to_internalID.size());

	String msg = (gapCnt==0)? " AID values have been loaded for all internal IDs" :
	    " AID values are missing for " + gapCnt + " internal IDs!";
	Logging.info("CTPFFit.loadMap: for 0<internalID<" +num_docs +", " +msg);
    }

} 
