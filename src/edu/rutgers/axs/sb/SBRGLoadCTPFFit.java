package edu.rutgers.axs.sb;
import edu.rutgers.axs.sql.Logging;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.persistence.*;

import org.apache.commons.math3.special.Gamma;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

/** An auxiliary class used to load precomputed CTPF fit data into the web application.
    The loading is carried out in a separate thread.
 */
class SBRGLoadCTPFFit extends Thread {

    String path;
    CTPFFit ctpffit; 

    private boolean error = false;
    private String errmsg = null;

    SBRGLoadCTPFFit(String _path, CTPFFit _ctpffit)
    {
        path = _path; 
        ctpffit = _ctpffit;
    }

    public void run()  {
        loadFit(path);
	if (error) {
	    ctpffit.setError(errmsg);
	} else {
	    ctpffit.loaded= true; 
	}
    }

    // load data 
    private void loadFit(String path) {

        try { 
           
            //Logging.info("loading epsilon shape"); 
            //float [][] epsilon_shape = load(path + "epsilon_shape.tsv.gz"); 
            //Logging.info("loading epsilon rate"); 
            //float [][] epsilon_rate = load(path + "epsilon_scale.tsv.gz"); // actually a rate

            Logging.info("Loading fit ");
            ctpffit.epsilonlog = load(path + "epsilon_log_10K.tsv.gz");
            ctpffit.thetalog = load(path + "theta_log_10K.tsv.gz");
            ctpffit.epsilon_plus_theta = load(path + "epsilon_plus_theta_10K.tsv.gz"); 

            // updateExpectationsEpsilonTheta(epsilon_shape, epsilon_rate, epsilonlog);

            // Logging.info("loading theta shape"); 
            // float [][] theta_shape = load(path + "theta_shape.tsv.gz"); 
            // Logging.info("loading theta rate"); 
            // float [][] theta_rate = load(path + "theta_scale.tsv.gz"); // actually a rate

            //theta = new float[theta_shape.length][theta_shape[0].length];
            // thetalog = new float[theta_shape.length][theta_shape[0].length];

            // updateExpectationsEpsilonTheta(theta_shape, theta_rate, thetalog);

            //epsilon_plus_theta = load(path + "epsilon_plus_theta.tsv.gz"); 
            // epsilon_plus_theta = new float[thetalog.length][thetalog[0].length]; 
            // for(int i=0; i<thetalog.length; ++i)
            //     for(int j=0; j<thetalog[0].length; ++j)
            //         epsilon_plus_theta[i][j] = epsilon_shape[i][j]/epsilon_rate[i][j] + theta_shape[i][j]/theta_rate[i][j]; 

            // load map 
            loadMap(path + "items_10K.tsv.gz");

        } catch(Exception ex) { 
            // TODO: put this back
	    error = true;
	    String msg = "Exception when loading fit:" + ex.getMessage();
	    errmsg = msg;
	    Logging.info(msg);
	    ex.printStackTrace(System.out);
        }
    } 

    private void loadMap(String file) throws Exception { 
        ctpffit.internalID_to_aID = new HashMap<Integer, String>();
        ctpffit.aID_to_internalID = new HashMap<String, Integer>();
        GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(file));
        BufferedReader br = new BufferedReader(new InputStreamReader(gzip));

        String line; 
        int k=0; 
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");
            ctpffit.internalID_to_aID.put(Integer.parseInt(parts[0]), parts[1]);
            ctpffit.aID_to_internalID.put(parts[1], Integer.parseInt(parts[0]));
        }
        Logging.info("size of internalID_to_aID: " + Integer.toString(ctpffit.internalID_to_aID.size()));
        Logging.info("size of aID_to_internalID: " + Integer.toString(ctpffit.aID_to_internalID.size()));

        Logging.info("fit loaded"); 

    }

    private float[][] load(String file) throws Exception {
        float[][] d;
        
        Logging.info("Loading " + file); 

        // List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(file));
        BufferedReader br = new BufferedReader(new InputStreamReader(gzip));

        //d = new float[SBRGWorkerCTPF.num_docs][];  // !!! 825708
        d = new float[SBRGWorkerCTPF.num_docs][SBRGWorkerCTPF.num_components];
        String line; 
        int k=0; 
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");
            if ((k+1) % 100000 == 0)  {
                Logging.info("d: " + Integer.toString(k+1) + " length: " + Integer.toString(d[k].length)); 
            }
            int j=0;
            for(String part: parts) { 
                if(j>=2)
                    d[k][j-2] = Float.parseFloat(part);
                ++j;
            }
            ++k;

        }
        return d;
    }

    // TODO: Consider doing it offline and then loading epsilon_plus_theta, epsilon_log and theta_log. 
    //private static void updateExpectationsEpsilonTheta(float[][] a_shape, float[][] a_rate, float[][] a, float[][] alog) {
    private void updateExpectationsEpsilonTheta(float[][] a_shape, float[][] a_rate, float[][] alog) {
        for(int i=0; i<alog.length; ++i) { 
            for(int j=0; j<alog[0].length; ++j) { 
                //a[i][j] = a_shape[i][j]/a_rate[i][j]; 
                alog[i][j] = (float)(Gamma.digamma(a_shape[i][j]) - Math.log(a_rate[i][j]));
            }
        }
    }

}

