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

/** This is a derivative class of SBRGWorker for the CTPF SB-recommendation method 
 */
public class SBRGWorkerCTPF extends  SBRGWorker  {

    static long seed = 3; 
    //static String path = "/home/lc629/arxiv/fits/nusers120298-ndocs825708-nvocab14000-k250-batch-bin-vb-fa-ldainit-fdp/";

    /**  We have a soft link,
 ldainit to /home/lc629/arxiv/fits/nusers120298-ndocs825708-nvocab14000-k250-batch-bin-vb-fa-ldainit/
    */
    static String path = "/data/arxiv/ctpf/ldainit/";
    static int num_docs = 10000; // 825708; // 1000; // At the very least this should be read from some config file
    static int num_components = 250; // same as above

    // The fit itself (not including the user)
    static CTPFFit ctpffit = new CTPFFit(); 

    // User representation 
    float[] x, xlog, x_shape, x_rate;
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
	}

	/*
        if (ctpffit.loaded) { 
     	} else if (ctpffit.error) {
	    Logging.info("Constructed empty SBRGWorkerCTPF object (error in data loading: "+ctpffit.errmsg+")"); 
	    error = true;
	    errmsg = "CTPF worker won't start due to a data loading error: " + ctpffit.errmsg;	
	} else {
	    error = true;
	    errmsg = "CTPF worker won't start, because the fit data have not been loaded yet";
	    Logging.info("Constructed empty SBRGWorkerCTPF object (no data loaded yet)"); 
	}
	*/

    }

    private boolean ready = false;
    synchronized private void init() {
	if (ready) return;
	boolean wasError = error; // to prevent repetitive duplicate msgs
	if (ctpffit.loaded) {

	    rnd = new Random(SBRGWorkerCTPF.seed);
	    viewedArticles = new TreeSet<String>(); 
	    
	    // Xs
	    x = new float[ctpffit.epsilon_plus_theta[0].length]; 
	    xlog = new float[ctpffit.epsilon_plus_theta[0].length]; 
	    x_shape = new float[ctpffit.epsilon_plus_theta[0].length]; 
	    x_rate = new float[ctpffit.epsilon_plus_theta[0].length]; 
	    
            // 
	    epsilon_plus_theta_sum = new float[ctpffit.epsilon_plus_theta[0].length];
	    for (int j=0; j < epsilon_plus_theta_sum.length; ++j) {
		epsilon_plus_theta_sum[j] = (float)0.;
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
	    if (!wasError) Logging.info("errmsg");
	}  else {
	    error = true;
	    errmsg = "CTPF worker won't be initialized now, because the fit data have not been loaded yet";
	    if (!wasError) Logging.info("errmsg");
	}

    }

    //public static void loadFit();
    //public static boolean myVar = loadFit();

    public static boolean loadFit() {
        // load data files 
        Logging.info("Loading fit"); 
        //SBRGLoadCTPFFit lf = new SBRGLoadCTPFFit(path, ctpffit);;
        Logging.info("Created SBRGLoadCTPFFit obj"); 
        ctpffit.loaded = false; 
        Thread t = new Thread(new SBRGLoadCTPFFit(path, ctpffit));
        t.start();
        Logging.info("Thread loading fit launched."); 
        return true;
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
	if (error) return; // error from the constructor
        Logging.info("CTPF worker working"); 
        updateUserProfileWithNewClick(_his);
        computeCTPFRecList(em, searcher, runID); 
        super.plid = super.saveAsPresentedList(em).getId();
    }

    synchronized void updateUserProfileWithNewClick(ActionHistory _his) { 

        int internalID; 
        float[] x2 = new float[x.length*2]; 
        Logging.info("Number of articles viewed by user: " + Integer.toString(viewedArticles.size()) ); 

        for(String aid: _his.viewedArticlesActionable) { // process articles in user history 
            if(!viewedArticles.contains(aid)) {
                // obtain article internal ID
                //Logging.info("Looking up article" + aid);
                if(ctpffit.aID_to_internalID.containsKey(aid)) {
                    internalID = ctpffit.aID_to_internalID.get(aid);
                    if (internalID > ctpffit.thetalog.length) {
                        Logging.info("internalID out of range: " + Integer.toString(internalID)); 
                        continue; 
                    }
                } else { 
                    Logging.info("Article unknown: " + aid); 
                    continue;
                }
                Logging.info("Viewed article: " + Integer.toString(internalID) + " " + aid); 

                // update X's shape 
                // need to create an array twice the length of X. 
                int factor = 1; // !! hack
                int inference_iterations = 1; 
                for (int ii=0; ii<inference_iterations; ++ii) { 
                    if (ii % 1000 == 0) 
                        Logging.info("Iteration: " + Integer.toString(ii)); 
                for (int k=0; k < x2.length; ++k) { 
                    if(k < x.length) { 
                        //Logging.info("thetalog: " + k + " " + Float.toString(ctpffit.thetalog[internalID][k])); 
                        x2[k] = xlog[k] + factor*ctpffit.thetalog[internalID][k]; 
                    } else {
                        int t = x.length - (x2.length - k);
                        //Logging.info("epsilonlog: " + t + " " + Float.toString(ctpffit.epsilonlog[internalID][t])); 
                        x2[k] = xlog[t] + factor*ctpffit.epsilonlog[internalID][t]; 
                    } 
                    //Logging.info("x2: " + k + " " + Float.toString(x2[k])); 
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
        Logging.info(str + ": " + xs); 
    }

    /** Generates the list of recommendations based on CTPF */
    private void computeCTPFRecList(EntityManager em, IndexSearcher searcher, int runID) {

	try {

            Logging.info("Calculating Scores"); 
            // Do x^T * (epsilon + theta)
            TreeMap<Float,String> scores = new TreeMap<Float,String>();
            //String old_value = "";
            float e; 
            for (int i=0; i<ctpffit.epsilon_plus_theta.length; ++i) {
                e = (float)0.;
                for (int j=0; j<ctpffit.epsilon_plus_theta[0].length; ++j) { 
                    e += x[j]*(ctpffit.epsilon_plus_theta[i][j]);
                }
                //Logging.info("(i,e): (" + Integer.toString(i) + "," + float.toString(e) + ") scores size: " + Integer.toString(scores.size()) + " " + old_value); 
                scores.put(e, ctpffit.internalID_to_aID.get(i));
            }

            // Get the results 
            //NavigableSet sorted_articles = scores.descendingKeySet();

	    int k=0;
	    //int maxAge = his.viewedArticlesActionable.size();

            Logging.info("Formatting results for SB output"); 
            //Logging.info("scores size: " + Integer.toString(scores.size()));
            Vector<ArticleEntry> entries = new Vector<ArticleEntry>();
            String aid; // article id
            int topK=20; 
            String reco_articles = ""; 
            for(float score: scores.descendingKeySet()) {
                aid = scores.get(score);
                if(aid != null) {
                    reco_articles += scores.get(score) + " (" + Float.toString(score) + ") | ";
                    ArticleEntry ae = new ArticleEntry(++k, scores.get(score));
                    ae.setScore(score);
                    ae.age = k-1; 
                    entries.add(ae);
                    ++k;
                } else { 
                    Logging.info("article id is null " + score); 
                }
                if(k>topK) // TODO: hack-ish, make better
                    break; 
	    }
            Logging.info("adding articles id:" + reco_articles); 
            if(entries.size() > 0) {
                Logging.info("Adding entries:" + Integer.toString(entries.size())); 
                sr = new SearchResults(entries); 
                super.addArticleDetails(searcher);
            } else {
                Logging.info("No new entries to add to the recommender list."); 
            }
	    //sr.saveAsPresentedList(em,Action.Source.SB,null,null, null);

	}  catch (Exception ex) {
	    error = true;
	    errmsg = ex.getMessage();
	    Logging.error(""+ex);
	    System.out.println("Exception for SBRG thread "); //  + super.getId());
	    ex.printStackTrace(System.out);
	}

        // apply exclusion list? 


    }
}
