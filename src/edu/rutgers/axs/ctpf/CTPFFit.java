package edu.rutgers.axs.ctpf;

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

/** The fit data for CTPF. */
public class CTPFFit  {

    //public float[][] theta, epsilon; 
    public Vector<float[]> epsilon_plus_theta = new Vector<float[]>(), 
	epsilonlog= new Vector<float[]>(), thetalog= new Vector<float[]>(); 
    //public float[][] epsilon_shape, epsilon_rate;
    //public float[][] theta_shape, theta_rate; 
    //    private HashMap<Integer, String> internalID_to_aID = new HashMap<Integer, String>();
 
    /** Maps AID to CTPF internal ID and vice versa */
    public CTPFMap map;

    public boolean loaded;

    public boolean error = false;
    public String errmsg = null;
    void setError(String msg) {
	error = true;
	errmsg = msg;
    }

    private float avgScores[];
    
    public float getAvgScore(int i) {
	return i<avgScores.length? avgScores[i] : 0;
    }


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
	int ignoreCnt = 0;
        while ((line = br.readLine()) != null) {
	    if (error) return;
	    lineCnt++;
            String[] parts = line.split("\t");
	    int iid = Integer.parseInt(parts[0]);
	    float score = (float)Double.parseDouble(parts[1]);
	    if (iid == 0) {
		Logging.warning("CPPFFit.loadAvgScores("+file+"): entering useless map entry for iid=" + iid + ", score=" + score);
	    } else if (iid >= num_docs) {
		//		Logging.warning("CPPFFit.loadAvgScores("+file+"): ignoring useless map entry for iid=" + iid + ", score=" + score);
		ignoreCnt++;
		continue;
	    } 

 	    avgScores[iid] = score;
        }

        Logging.info("CTPFFit: read " + lineCnt + " lines from " + file);
	if (ignoreCnt>0) {
	    Logging.warning("CTPFFit: ignored " + ignoreCnt + " useless map entries");
	}
    }

} 
