package edu.rutgers.axs.ctpf;
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
    This class extends Thread, so that loading can be carried out in a separate thread,
    which is essential in a web app.
 */
public class LoadCTPFFit extends Thread {

    String path;
    public CTPFFit ctpffit; 
    /** The number of lines in each data file (which corresponds to
	the number D of documents based on which the matrices have
	been calculated). This number will be set based on the length
	of the first data file to be read. Our two sample sets have
	the sizes D=825708 and D=10000.

	<p> The range of the internal IDs actually occurring in matrix
	files (theta etc) is 0 thru num_docs-1; however, as per conversation
	with Laurent (2015-04-17), the ID=0 document is actually a dummy one,
	and is in practice disregarded. The document map file would typically
	have IDs ranging from 1 thru num_docs-1; IDs outside of this 
	range can be safely disregarded.
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

    public LoadCTPFFit(String _path, CTPFFit _ctpffit)
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

    private boolean wantToCancel = false;

    public void cancel() {
	wantToCancel = true;
    }

    private boolean checkCancel() {
	if (wantToCancel) {
	    error = true;
	    String msg = "CTPF data loading cancelled by the main app";
	    Logging.warning(msg);
	    errmsg = msg;	    
	}
	return wantToCancel;
    }

    /** Loads everything. 
	@param path The directory in which the required data files (with expected names)
	are to be found.
     */
    private void loadFit(String path) {

        try { 

	    boolean atHome = Hosts.atHome();

	    if (checkCancel()) return;
            Logging.info("LoadCTPFFit: Loading started. Will use " +
			 (atHome? "the 10K sample" : "the full data set"));

	    // Modifies data file names, to refer to the full data set or the 10K subset 
	    final String suffix  = atHome ? "_10K" : "";

	    File dir = new File(path);

            //Logging.info("loading epsilon shape"); 
            //float [][] epsilon_shape = load(path + "epsilon_shape.tsv.gz"); 
            //Logging.info("loading epsilon rate"); 
            //float [][] epsilon_rate = load(path + "epsilon_scale.tsv.gz"); // actually a rate

	    // the first load call will also set this.num_docs
            ctpffit.epsilonlog = load(new File(dir, "epsilon_log" + suffix +".tsv.gz"), 0);
	    Logging.info("LoadCTPFFit: determined num_docs=" + num_docs);
	    if (error || checkCancel()) return;

            ctpffit.thetalog = load(new File(dir, "theta_log" + suffix + ".tsv.gz"), 0);
	    if (error || checkCancel()) return;
            ctpffit.epsilon_plus_theta = load(new File(dir, "epsilon_plus_theta"+suffix+".tsv.gz"), 0); 
	    if (error || checkCancel()) return;

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
            ctpffit.map = new CTPFMap(new File(dir,  "items"+suffix+".tsv.gz"), num_docs, false);
	    ctpffit.loadAvgScores(new File(dir,  "mean_paper_scores.tsv"), num_docs);
	    if (error || checkCancel()) return;
            Logging.info("LoadCTPFFit: Loading finished");
        } catch(Exception ex) { 
            // TODO: put this back
	    error = true;
	    String msg = "Exception when loading fit:" + ex.getMessage();
	    errmsg = msg;
	    Logging.error(msg);
	    ex.printStackTrace(System.out);
        }
    } 
 

    /** Loads a matrix with num_docs rowd and num_components columns.	
	@param offset This is to be added to the internal ID value
	found in the first 2 columns of the input file.
     */
    private float[][] load(File file, int offset) throws Exception {
	Vector<float[]> dvec = new Vector<float[]>();
	load0(file, offset, dvec);
	return finalizeLoad(dvec);  	
    }

    private void load0(File file, int offset, Vector<float[]> dvec ) throws Exception {
        
        Logging.info("LoadCTPFFit: Loading data from file " + file); 

        // List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(file));
        LineNumberReader br = new LineNumberReader(new InputStreamReader(gzip));

	String line; 

        while ((line = br.readLine()) != null) {
	    if (error || checkCancel()) return;
            String[] parts = line.split("\t");

	    int len = parts.length-2;
	    int k = dvec.size();
	    if (len <= 0) {
		throw new IOException("Parsing error on file " + file + ", line "+br.getLineNumber()+": num_components=" + len); 
	    } else if (num_components==0) {
		num_components = len;
	    } else if (num_components != len) {
		throw new IOException("Parsing error on file " + file + ", line "+br.getLineNumber()+": num_components changing from " + num_components + " to " + len); 
	    }

	    int storedIid = Integer.parseInt(parts[0]);
	    int iid = storedIid + offset;
	    if (iid != dvec.size()) throw  new IOException("Data alignment error in file " + file + ", line "+br.getLineNumber()+": num_components changing from " + num_components + " to " + len); 

	    float[] row = new float[num_components];
            for(int j=0; j<row.length; j++) {
		row[j] = Float.parseFloat(parts[j+2]);
	    }
	    dvec.add(row);

	    if (dvec.size() % 100000 == 0)  {
                Logging.info("LoadCTPFFit("+file+"): " + dvec.size() + " rows...");
            }
	}

	Logging.info("LoadCTPFFit("+file+"): total of " + dvec.size() + " rows, " + num_components + " columns");

	int nrows = dvec.size();
	if (nrows == 0) {
	    throw new IOException("Parsing error on file " + file + ": zero rows found!");
	} else if (num_docs==0) {  // first file
		num_docs = nrows;
	} else if (num_docs != nrows) {
	    throw new IOException("Parsing error on file " + file + ", : found " + nrows + " rows, vs. " + num_docs + " in previously processed files!");
	}
	
    }

    private float[][]  finalizeLoad(Vector<float[]> dvec) {
	return (float[][])dvec.toArray(new float[0][]);
    }

    /** TODO: Consider doing it offline and then loading epsilon_plus_theta, epsilon_log and theta_log. 
     */
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

