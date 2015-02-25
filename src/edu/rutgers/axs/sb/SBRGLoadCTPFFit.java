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

import edu.rutgers.axs.util.Hosts;

/** An auxiliary class used to load precomputed CTPF fit data into the web application.
    The loading is carried out in a separate thread.
 */
class SBRGLoadCTPFFit extends Thread {

    String path;
    CTPFFit ctpffit; 
    /** The number of lines in each data file (which corresponds to
	the number D of documents based on which the matrices have
	been calculated). This number will be set based on the length
	of the first data file to be read. Our two sample sets have
	the sizes D=825708 and D=10000.
    */
    private int num_docs = 0; 
    /** The number of columns in each data file, not counting the
	first 2 "header" columns. This number will be set based on the
	content of the first data file to be read. This corresponds to
	the dimension K of the topic space. In our sample set K=250.
    */
 
    private int num_components = 0; 


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

	    boolean atHome = Hosts.atHome();

            Logging.info("SBRGLoadCTPFFit: Loading started. Will use " +
			 (atHome? "the 10K sample" : "the full data set"));

	    // Modifies data file names, to refer to the full data set or the 10K subset 
	    final String suffix  = atHome ? "_10K" : "";

            //Logging.info("loading epsilon shape"); 
            //float [][] epsilon_shape = load(path + "epsilon_shape.tsv.gz"); 
            //Logging.info("loading epsilon rate"); 
            //float [][] epsilon_rate = load(path + "epsilon_scale.tsv.gz"); // actually a rate

            ctpffit.epsilonlog = load(path + "epsilon_log" + suffix + ".tsv.gz");
            ctpffit.thetalog = load(path + "theta_log" + suffix + ".tsv.gz");
            ctpffit.epsilon_plus_theta = load(path + "epsilon_plus_theta"+suffix+".tsv.gz"); 

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
            loadMap(path + "items"+suffix+".tsv.gz");
            Logging.info("SBRGLoadCTPFFit: Loading finished");
        } catch(Exception ex) { 
            // TODO: put this back
	    error = true;
	    String msg = "Exception when loading fit:" + ex.getMessage();
	    errmsg = msg;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
        }
    } 

    private void loadMap(String file) throws Exception { 
	Logging.info("SBRGLoadCTPFFit: loading document map from " + file);
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
        Logging.info("SBRGLoadCTPFFit: size of internalID_to_aID: " + ctpffit.internalID_to_aID.size());
        Logging.info("SBRGLoadCTPFFit: size of aID_to_internalID: " + ctpffit.aID_to_internalID.size());

	//        Logging.info("fit loaded"); 

    }

    private float[][] load(String file) throws Exception {
        
        Logging.info("SBRGLoadCTPFFit: Loading data from file " + file); 

        // List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(file));
        BufferedReader br = new BufferedReader(new InputStreamReader(gzip));


	Vector<float[]> dvec = new Vector<float[]>();
        String line; 

        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");

	    int len = parts.length-2;
	    int k = dvec.size();
	    if (len <= 0) {
		throw new IOException("Parsing error on file " + file + ", line "+(k+1)+": num_components=" + len); 
	    } else if (num_components==0) {
		num_components = len;
	    } else if (num_components != len) {
		throw new IOException("Parsing error on file " + file + ", line "+(k+1)+": num_components changing from " + num_components + " to " + len); 
	    }

	    float[] row = new float[num_components];
            for(int j=0; j<row.length; j++) {
		row[j] = Float.parseFloat(parts[j+2]);
	    }
	    dvec.add(row);

	    if (dvec.size() % 100000 == 0)  {
                Logging.info("SBRGLoadCTPFFit("+file+"): " + dvec.size() + " rows...");
            }
	}

	Logging.info("SBRGLoadCTPFFit("+file+"): total of " + dvec.size() + " rows, " + num_components + " columns");

	int nrows = dvec.size();
	if (nrows == 0) {
	    throw new IOException("Parsing error on file " + file + ": zero rows!");
	} else if (num_docs==0) {
		num_docs = nrows;
	} else if (num_docs != nrows) {
	    throw new IOException("Parsing error on file " + file + ", : found " + nrows + " rows, vs. " + num_docs + " in previously processed files!");	    
	}
	
        //d = new float[num_docs][num_components];	
        float[][] d = (float[][])dvec.toArray(new float[0][]);
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

