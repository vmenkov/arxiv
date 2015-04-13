package edu.rutgers.axs.sb;

import java.io.*;
import java.lang.Math;
import java.nio.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.util.Random.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.commons.math3.special.Gamma;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;

class CTPFFit extends Object {

    //public float[][] theta, epsilon; 
    public float[][] epsilon_plus_theta;
    public float[][] epsilonlog, thetalog; 
    //public float[][] epsilon_shape, epsilon_rate;
    //public float[][] theta_shape, theta_rate; 
    public HashMap<Integer, String> internalID_to_aID;
    public HashMap<String, Integer> aID_to_internalID;
    public boolean loaded;

    boolean error = false;
    String errmsg = null;
    void setError(String msg) {
	error = true;
	errmsg = msg;
    }

} 

/** This is a derivative class of SBRGWorker for the CTPF
    SB-recommendation method
 */
public class SBRGWorkerCTPF extends  SBRGWorker  {

    static long seed = 3; 
    //static String path = "/home/lc629/arxiv/fits/nusers120298-ndocs825708-nvocab14000-k250-batch-bin-vb-fa-ldainit-fdp/";

    /**  The directory where the precomputed fit data files are. 
	 We have a soft link, ldainit to 
	 /home/lc629/arxiv/fits/nusers120298-ndocs825708-nvocab14000-k250-batch-bin-vb-fa-ldainit/
    */
    static String path = "/data/arxiv/ctpf/ldainit/";

    // The fit itself (not including the user)
    static CTPFFit ctpffit = new CTPFFit(); 

    // User representation 
    float[] x, xlog, x_shape, x_rate;

    /** There's also the issue of how to infer a user's eta (x in the
	code). The way it's currently done isn't very sensitive and
	will require too many clicks before producing personalized
	recommendations. I suggest trying out one of two things: 1)
	Set the prior shape on x (x_shape_prior) to be much smaller.
	Something on the order of 10^(-6) 2) Set the factor variable
	(in updateUserProfileWithNewClick()) to be much higher (at
	least 10^4)
    */
    //float x_shape_prior = (float)0.1; 
    float x_shape_prior = (float)0.1; 
    float x_rate_prior = (float)0.1; 
    TreeSet<String> viewedArticles;
    Random rnd; 

    // For convenience 
    float[] epsilon_plus_theta_sum;

    SBRGWorkerCTPF(SBRGenerator _parent,
                    int _sbStableOrderMode) {
        super(SBRGenerator.Method.CTPF, _parent, _sbStableOrderMode);

	init();
	if (ready) {
	    Logging.info("Constructed and initialized SBRGWorkerCTPF object"); 
	} else {
	    Logging.info("Constructed an empty SBRGWorkerCTPF object"); 
	}
    }

    /** Has this object been properly initialized yet? (Normally this is done in the
	constructor, but may happen later, if the data were not availabe at the time)
    */
    private boolean ready = false;

    /** Initializes this object's data if this has not been done yet.
	This is first tried in the constructor, but may be repeated
	later, if the data were not availabe on the first call. 
	Therefore this method is called both from the constructor and 
	from the work() method.
     */
    synchronized private void init() {
	if (ready) return;
	boolean wasError = error; // to prevent repetitive duplicate msgs
	if (ctpffit.loaded) {

	    rnd = new Random(SBRGWorkerCTPF.seed);
	    viewedArticles = new TreeSet<String>(); 
	    
	    // Xs
	    int num_components=ctpffit.epsilon_plus_theta[0].length;
	    x = new float[num_components]; 
	    xlog = new float[num_components]; 
	    x_shape = new float[num_components]; 
	    x_rate = new float[num_components]; 
	    
            // 
	    epsilon_plus_theta_sum = new float[num_components];
	    for (int j=0; j < epsilon_plus_theta_sum.length; ++j) {
		epsilon_plus_theta_sum[j] = 0;
		for (int i=0; i < ctpffit.epsilon_plus_theta.length; ++i) { 
		    epsilon_plus_theta_sum[j] += ctpffit.epsilon_plus_theta[i][j]; 
		}
	    }
	    
	    initializeX();
	    updateExpectationsX(); 
	    Logging.info("Initialized SBRGWorkerCTPF object"); 
	    error = false;
	    errmsg = "";
	    ready = true;
	} else if (ctpffit.error) {
	    error = true;
	    errmsg = "CTPF worker won't be initialized due to a data loading error: " + ctpffit.errmsg;	
	    if (!wasError) Logging.info(errmsg);
	}  else {
	    error = true;
	    errmsg = "CTPF worker won't be initialized now, because the fit data have not been loaded yet";
	    if (!wasError) Logging.info(errmsg);
	}

    }

    /** The thread created during the fit loading. This is kept
	so that we can check on it later.
     */
    private static SBRGLoadCTPFFit ctpfLoadThread = null;

    /** This method spawns the thread which will load the
	SBRGLoadCTPFFit data, which are to be used by all
	SBRGWorkerCTPF instances in the future.  It is essential that
	this method be called only once in the web application's
	life. This is done when the ResultsBase class load,
	i.e. pretty soon after the web app has been reloaded.

	<p>This method must be called only once during the life of the
	web app or the command line app.
    */
    public static synchronized boolean loadFit() {
	if (ctpfLoadThread!=null) {
	    String msg = "CTPFFit.loadFit() must have been called repeatedly - no good!";	    
	    Logging.error(msg);
	    throw new IllegalArgumentException(msg);
	}
        // load data files 
        Logging.info("Loading CTPF fit data"); 
        //SBRGLoadCTPFFit lf = new SBRGLoadCTPFFit(path, ctpffit);;
	//        Logging.info("Created SBRGLoadCTPFFit obj"); 
        ctpffit.loaded = false; 
        ctpfLoadThread = new SBRGLoadCTPFFit(path, ctpffit);
        ctpfLoadThread.start();
        Logging.info("Thread loading fit launched: " + ctpfLoadThread); 
        return true;
    }

    /** Waits for the CTPF fit load thread to complete.
     */
    public static void waitForLoading() {
	if (ctpfLoadThread==null) throw new IllegalArgumentException("No CTPF load thread has been created!");
	while (true) {
	    try {		    
		ctpfLoadThread.join();
		return;
	    } catch ( InterruptedException ex) {}
	}
    }

   public static void cancelLoading() {
       ctpfLoadThread.cancel();
   }

    private void initializeX() { 
	for(int i=0; i<x.length; ++i) { 
	    x_shape[i] = x_shape_prior; //  + (float)0.001 * rnd.nextFloat(); // TODO: removed for debugging. May be useful.
	    x_rate[i]  = x_rate_prior  + epsilon_plus_theta_sum[i]; // (float)0.001 * rnd.nextInt(1);
	}
	print1DArray(x_shape,"x_shape");
	print1DArray(x_rate,"x_rate");
    } 

    private void updateExpectationsX() {
        for(int i=0; i<x.length; ++i) { 
            x[i] = x_shape[i]/x_rate[i]; 
            xlog[i] = (float)(Gamma.digamma(x_shape[i]) - Math.log(x_rate[i]));
        }
    }


    synchronized void work(EntityManager em, IndexSearcher searcher, int runID, ActionHistory _his)  {
	init(); 
	if (error) return; // error from the constructor or initializer
        Logging.info("SBRGWorkerCTPF: start"); 
	excludedList = "";
        updateUserProfileWithNewClick(_his);
        computeCTPFRecList(em, searcher, runID); 
        super.plid = super.saveAsPresentedList(em).getId();
    }

    //int factor = 1; // !! hack
    static final private double factor = 1e8;

    synchronized void updateUserProfileWithNewClick(ActionHistory _his) { 

        int internalID; 
        float[] x2 = new float[x.length*2]; 
        Logging.info("SBRGWorkerCTPF: Number of articles viewed by user: " + Integer.toString(viewedArticles.size()) ); 

        for(String aid: _his.viewedArticlesActionable) { // process articles in user history 
            if(!viewedArticles.contains(aid)) {
                // obtain article internal ID
                //Logging.info("Looking up article" + aid);
                if(ctpffit.aID_to_internalID.containsKey(aid)) {
                    internalID = ctpffit.aID_to_internalID.get(aid);
                    if (internalID > ctpffit.thetalog.length) {
                        Logging.warning("SBRGWorkerCTPF: internalID out of range: " + internalID); 
                        continue; 
                    }
                } else { 
                    Logging.info("SBRGWorkerCTPF: Article unknown: " + aid); 
                    continue;
                }
                Logging.info("SBRGWorkerCTPF: Viewed article: " + internalID + " " + aid); 

                // update X's shape 
                // need to create an array twice the length of X. 
                int inference_iterations = 1; 
                for (int ii=0; ii<inference_iterations; ++ii) { 
                    if (ii % 1000 == 0) 
                        Logging.info("SBRGWorkerCTPF: Iteration: " + ii); 
                for (int k=0; k < x2.length; ++k) { 
                    if(k < x.length) { 
                        //Logging.info("SBRGWorkerCTPF: thetalog: " + k + " " + ctpffit.thetalog[internalID][k]); 
                        x2[k] = xlog[k] + (float)(factor*ctpffit.thetalog[internalID][k]); 
                    } else {
                        int t = x.length - (x2.length - k);
                        //Logging.info("SBRGWorkerCTPF: epsilonlog: " + t + " " + ctpffit.epsilonlog[internalID][t]); 
                        x2[k] = xlog[t] + (float)(factor*ctpffit.epsilonlog[internalID][t]); 
                    } 
                    //Logging.info("SBRGWorkerCTPF: x2: " + k + " " +x2[k]); 
                }
 
                normalize(x2); 
                updateXshape(x2);
                if (ii % 1000 == 0) 
                    print1DArray(x_shape,"x_shape"); 
                updateExpectationsX();
                if (ii % 1000 == 0) 
                    print1DArray(x, "x"); 

                }
                // no need to update the rate unless epsilon_plus_theta changes
                // updateXrate(ctpffit.epsilon_plus_theta); 
                // print1DArray(x_rate,"x_rate"); 
                viewedArticles.add(aid); 
            } 
            // update X's expectation
            updateExpectationsX();
            print1DArray(x, "x"); 
        }
    } 

    private void updateXshape(float[] x2) {
        for (int i=0; i<x2.length; ++i) { 
            x_shape[i % x.length] += x2[i]; 
        }
    }

    private float logsum(float[] a) { 
        float r = (float)0.;
        for (int i=0; i<a.length; ++i) { 
            if (a[i] < r)
              r = r + (float)Math.log(1 + Math.exp(a[i] - r));
            else
              r = a[i] + (float)Math.log(1 + Math.exp(r - a[i]));
        }
        return r;
    } 


    private void normalize(float[] a) { 
            float sum = logsum(a); 
            for(int i=0; i<a.length; ++i) { 
                a[i] = (float)Math.exp(a[i] - sum);  
            }
    }

    /* Double check before using.
    private void updateXrate(float[] updater) {
        for(int i=0; i<updater.length; ++i) 
            x_rate[i] += updater[i];
    }
    */

    private void print1DArray(float[] a, String str) { 
        String xs = "["; 
        for (int i=0; i<a.length; ++i) {
            if(i == 163)
                xs += "****";
            xs += Float.toString(a[i]);
            if (i != (a.length-1))
                xs += ", ";
        }
        xs += ']';
        Logging.info("SBRGWorkerCTPF: " + str + ": " + xs); 
    }

    /** Generates the list of recommendations based on CTPF */
    private void computeCTPFRecList(EntityManager em, IndexSearcher searcher, int runID) {

	try {
	    HashSet<String> exclusions = findExclusions();
	    Logging.info("SBRGWorkerCTPF: Calculating Scores. |exclusions|=" + exclusions.size()); 
            // Do x^T * (epsilon + theta)
            TreeMap<Float,String> scores = new TreeMap<Float,String>();
            //String old_value = "";
            float e; 
            for (int i=0; i<ctpffit.epsilon_plus_theta.length; ++i) {
                e = (float)0.;
                for (int j=0; j<ctpffit.epsilon_plus_theta[0].length; ++j) { 
                    e += x[j]*(ctpffit.epsilon_plus_theta[i][j]);
                }
                //Logging.info("SBRGWorkerCTPF: (i,e): (" + i + "," + e + ") scores size: " + scores.size() + " " + old_value); 
                scores.put(e, ctpffit.internalID_to_aID.get(i));
            }

            // Get the results 
            //NavigableSet sorted_articles = scores.descendingKeySet();

	    int k=0;
	    //int maxAge = his.viewedArticlesActionable.size();

            Logging.info("SBRGWorkerCTPF: Formatting results for SB output"); 
            //Logging.info("SBRGWorkerCTPF: scores size: " + scores.size());
            Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
            String aid; // article id
            int topK=20; 
            String reco_articles = ""; 
            for(float score: scores.descendingKeySet()) {
                aid = scores.get(score);
                if (aid != null) {

		    // check this article against the exclusion list
		    if (exclusions.contains(aid)) {
			excludedList += " " + aid;
			continue;
		    }
                    reco_articles += scores.get(score) + " (" + score + ") | ";

                    ArticleEntry ae = new ArticleEntry(++k, scores.get(score));
                    ae.setScore(score);
                    ae.age = k-1; 
                    entries.add(ae);
                    // ++k; // No need to increment k again!
                } else { 
                    Logging.warning("SBRGWorkerCTPF: article id is null " + score); 
                }
                if(k>topK) // TODO: hack-ish, make better
                    break; 
	    }
            Logging.info("SBRGWorkerCTPF: adding articles id:" + reco_articles + "; Excluded articles: " + excludedList); 
            if(entries.size() > 0) {
                Logging.info("SBRGWorkerCTPF: Adding entries:" +entries.size());
                sr = new SearchResults(entries); 
                super.addArticleDetails(searcher);
            } else {
                Logging.info("SBRGWorkerCTPF: No new entries to add to the recommender list."); 
            }
	    //sr.saveAsPresentedList(em,Action.Source.SB,null,null, null);

	}  catch (Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    Logging.error(""+ex);
	    System.out.println("SBRGWorkerCTPF: Exception for SBRG thread "); //  + super.getId());
	    ex.printStackTrace(System.out);
	}

    }

    /** Produces a human-readable description of this worker's particulars. The
	method may be overriden by derivative classes, to report more details. */
    public String description() {
	String s = super.description();
	s += "; x_shape_prior="+ x_shape_prior +", x_rate_prior=" + x_rate_prior +", factor=" + factor;
 
	return s;
    }
   
}
