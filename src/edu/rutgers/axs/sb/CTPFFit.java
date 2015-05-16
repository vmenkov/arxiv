package edu.rutgers.axs.sb;

import java.io.*;
import java.lang.Math;
import java.nio.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.indexer.*;

/** The fit data for CTPF */
class CTPFFit  {

    //public float[][] theta, epsilon; 
    public float[][] epsilon_plus_theta;
    public float[][] epsilonlog, thetalog; 
    //public float[][] epsilon_shape, epsilon_rate;
    //public float[][] theta_shape, theta_rate; 
    //    private HashMap<Integer, String> internalID_to_aID = new HashMap<Integer, String>();
    private String[] aIDs;
    //  void storeInternalID_to_aID(int i, String aid) {
    //	internalID_to_aID.put(new Integer(i), aid);
    //    }
    String  getInternalID_to_aID(int i) {
	//	return internalID_to_aID.get(i);
	return aIDs[i];
    }

    public HashMap<String, Integer> aID_to_internalID  = new HashMap<String, Integer>();
    public boolean loaded;

    boolean error = false;
    String errmsg = null;
    void setError(String msg) {
	error = true;
	errmsg = msg;
    }

    /** Loads the map which associates CTPF internal doc ids with
	Arxiv article IDs (AIDs). We ignore a few "fake" AIDs that may
	appear in the map file but are not actually present in 
	our Lucene data store.
     */
    void loadMap(File file, int num_docs) throws Exception { 
	IndexList il = new IndexList();
	HashSet<String> allAIDs = il.listAsSet();


	Logging.info("CTPFFit: loading document map from " + file);
	GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(file));
        BufferedReader br = new BufferedReader(new InputStreamReader(gzip));

	aIDs = new String[num_docs];

        String line; 
	int invalidAidCnt = 0;
	String invalidAidTxt = "";
	final int M = 10;
        while ((line = br.readLine()) != null) {
	    if (error) return;
            String[] parts = line.split("\t");
	    int iid = Integer.parseInt(parts[0]);
	    String aid = parts[1];
	    if (iid == 0) {
		Logging.warning("CPPFFit.loadMap("+file+"): entering useless map entry for iid=" + iid + ", aid=" + aid);
	    } else if (iid >= num_docs) {
		Logging.warning("CPPFFit.loadMap("+file+"): ignoring useless map entry for iid=" + iid + ", aid=" + aid);
		continue;
	    } else if (!allAIDs.contains(aid)) {
		invalidAidCnt++;
		if (invalidAidCnt<M) invalidAidTxt += " " + aid;
		else if (invalidAidCnt==M) invalidAidTxt += " ...";		
		continue;
	    }

            //internalID_to_aID.put(iid, aid);
	    aIDs[iid] = aid;
            aID_to_internalID.put(aid, new Integer(iid));
        }

	// basic validation
	int gapCnt=0;
	for(int i=1; i<num_docs; i++) {
	    if (aIDs[i]==null) gapCnt++;
	}

	if (invalidAidCnt>0) {
	    Logging.warning("CTPFFit.loadMap("+file+"): " + invalidAidCnt + " lines have been ignored, because they contained apparently invalid AIDs, such as:  " + invalidAidTxt);
	}

	//        Logging.info("CTPFFit: size of internalID_to_aID: " + internalID_to_aID.size());
        Logging.info("CTPFFit.loadMap("+file+"): size of aID_to_internalID: " + aID_to_internalID.size());

	String msg = (gapCnt==0)? " AID values have been loaded for all internal IDs" :
	    " AID values are missing for " + gapCnt + " internal IDs!";
	Logging.info("CTPFFit.loadMap: for 0<internalID<" + num_docs +", " +msg);
    }

    float avgScores[];
    
    /** Input file format (TAB-separated):
	<pre>
0	0.000139
1	0.001495
2	0.000314
...
</pre>
    */
    void loadAvgScores(File file, int num_docs) throws Exception { 
	Logging.info("CTPFFit: loading avg scores from " + file);
	InputStream fis = new FileInputStream(file);
	if (file.getName().endsWith(".gz")) fis = new GZIPInputStream(fis);
	BufferedReader br = new BufferedReader(new InputStreamReader(fis));

	avgScores = new float[num_docs];

        String line; 
	int lineCnt=0;
        while ((line = br.readLine()) != null) {
	    if (error) return;
	    lineCnt++;
            String[] parts = line.split("\t");
	    int iid = Integer.parseInt(parts[0]);
	    float score = (float)Double.parseDouble(parts[1]);
	    if (iid == 0) {
		Logging.warning("CPPFFit.loadAvgScores("+file+"): entering useless map entry for iid=" + iid + ", score=" + score);
	    } else if (iid >= num_docs) {
		Logging.warning("CPPFFit.loadAvgScores("+file+"): ignoring useless map entry for iid=" + iid + ", score=" + score);
		continue;
	    } 

 	    avgScores[iid] = score;
        }

        Logging.info("CTPFFit: read " + lineCnt + " lines from " + file);
    }

} 
