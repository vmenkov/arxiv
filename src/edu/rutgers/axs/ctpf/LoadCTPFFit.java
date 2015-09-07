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

    /** Creates an object, but does not read any data yet. */
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
	loadFit(path, new String[0]);
    }

    /** Loads everything. 
	@param path The directory in which the required data files (with expected names)
	are to be found.
	@param otherPaths A list of additional load paths (zero or more), each one of which will contain fit data for an addtional set of documents. Those sets (usually, just 1) are produced during data updates after the main LDA init run.
     */
    private void loadFit(String path, String otherPaths[]) {

        try { 

	    boolean atHome = Hosts.atHome();

	    if (checkCancel()) return;
            Logging.info("LoadCTPFFit: Loading started. Will use " +
			 (atHome? "the 10K sample" : "the full data set"));

	    // Modifies data file names, to refer to the full data set or the 10K subset 
	    final String suffix  = atHome ? "_10K" : "";

	    File dir = new File(path);

            // load map 
            ctpffit.map = new CTPFMap();
	    CTPFMap.Descriptor desc =
		ctpffit.map.addFromFile(new File(dir,  "items"+suffix+".tsv.gz"),  false, true, true);
	    Logging.info("Loaded map: " + desc);


            //Logging.info("loading epsilon shape"); 
            //float [][] epsilon_shape = load(path + "epsilon_shape.tsv.gz"); 
            //Logging.info("loading epsilon rate"); 
            //float [][] epsilon_rate = load(path + "epsilon_scale.tsv.gz"); // actually a rate

	    // after the first load call, set this.num_docs
	    load0(new File(dir, "epsilon_log" + suffix +".tsv.gz"), desc, ctpffit.epsilonlog);
	    num_docs = ctpffit.epsilonlog.size();
	    Logging.info("LoadCTPFFit: determined num_docs=" + num_docs);
	    if (error || checkCancel()) return;

            load0(new File(dir, "theta_log" + suffix + ".tsv.gz"), desc, ctpffit.thetalog);
	    if (error || checkCancel()) return;
            load0(new File(dir, "epsilon_plus_theta"+suffix+".tsv.gz"), desc, ctpffit.epsilon_plus_theta); 
	    if (error || checkCancel()) return;

	    ctpffit.map.possibleShrink(desc);


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

	    if (num_docs !=  ctpffit.map.size()) {
		error = true;
		errmsg = "Data mismatch: num_doc=" + num_docs + " (based on array files); map.size=" +ctpffit.map.size();
		Logging.error(errmsg);
		return;
	    }

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
	@param desc Contains offset, which is to be added to the
	internal ID value found in the first 2 columns of the input
	file. A null object can be passed, in which case a desriptor can be created.
     */
    private float[][] load1(File file, CTPFMap.Descriptor desc) throws IOException {
	Vector<float[]> dvec = new Vector<float[]>();
	load0(file, desc, dvec);
	return (float[][])dvec.toArray(new float[0][]);
	//return finalizeLoad(dvec);  	
    }

    /** Adds data from a file to a vector.
	@param file An input file. It is expected that the first two 
	columns of it contain the same number: the (raw) internal document
	id.
	@param desc Describes how the range of (raw) internal article
	IDs found in the file are to be mapped to the internally
	stored IDs. If the ID range in the file is shorter than in the 
	descriptor, the descriptor will be modified (shrunk). If null is passed,
	a descriptor will be created based on the file content.
	@param dvec A vector of arrays (one array per document) to
	which data are to be appended.
     */
    private CTPFMap.Descriptor load0(File file, CTPFMap.Descriptor desc, Vector<float[]> dvec) throws IOException {        
	Logging.info("LoadCTPFFit: Loading data from file " + file); 

 	boolean mustDescribe = (desc==null);

	Reader fr = file.getPath().endsWith(".gz") ?
	    new InputStreamReader(new GZIPInputStream(new FileInputStream(file))) :
	    new FileReader(file);
        LineNumberReader br = new LineNumberReader(fr);

	String line; 

        while ((line = br.readLine()) != null) {
	    if (error || checkCancel()) return null;
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

	    if (mustDescribe) {
		if (desc==null) {
		    // creating the descriptor based on the first line
		    // of the file. Are the IDs 0- or 1-based?		    
		    desc=new CTPFMap.Descriptor(-storedIid,  storedIid, storedIid);
		}
		if (storedIid != desc.r1) {
		    throw new IOException("Data error on file " + file + ", line "+br.getLineNumber()+": unexpected iid increment (found "+storedIid+" instead of expected "+desc.r1+")");
		} else {
		    desc.r1++;
		}
	    }
	
	    if (storedIid < desc.r0)  {
		// We should ignore this and keep working. This is because
		// Laurent's theta files sometimes start with 0, even though
		// items files start with 1. As per Laurent's advice,
		// these iid=0 entries should simply be discarded.
		String msg = "Ignoring unexpected IID in file " + file + ", line "+br.getLineNumber()+": found stored iid="+storedIid + ", below the expected range ("+desc+")";
		Logging.info(msg);
		continue;
	    } else if ( storedIid >= desc.r1) {
		// This may happen when some of the last entries have
		// been stripped from the map file (items.tsv) due to
		// invalid AIDs (and the AID validity check was in
		// effect). In those cases, the corresponding entries
		// of the epsilon file should be discarded as well.
		String msg = "Ignoring unexpected IID in file " + file + ", line "+br.getLineNumber()+": found stored iid="+storedIid + ", above the expected range ("+desc+")";
	    //throw new IOException(msg);
		
	    }

	    int iid = storedIid + desc.offset;
	    if (iid != dvec.size()) throw  new IOException("Data alignment error in file " + file + ", line "+br.getLineNumber()+": found stored iid="+storedIid + "(converted to "+iid+"), != dvec.size=" + dvec.size());

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

	if (dvec.size() < desc.offset + desc.r1) {
	    int r1new = dvec.size() - desc.offset;
	    String msg = "As a result of reading file " +file+ ", shrinking range descriptor from (" + desc + ") ";
	    desc.r1 = r1new;
	    msg += "to (" + desc + ")";
	    Logging.info(msg);
	}
	return desc;
    }

    /*
    private float[][]  finalizeLoad(Vector<float[]> dvec) {
	return (float[][])dvec.toArray(new float[0][]);
    }
    */

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

    private static NumberFormat tsvFmt = new DecimalFormat( "0.000000");

    /** As per LC, 2015-08-20:
 E[theta]        = shape / scale
 E[log(theta)] = diagamma(shape) - ln(scale)

     */
    static void computeShapeScaleRatio(File shapeFile, File scaleFile, 
				File outThetaFile, File outLogThetaFile) throws IOException {   

	CTPFFit dummy = new CTPFFit();
	LoadCTPFFit loader = new LoadCTPFFit(null, dummy);

	Vector<float[]> shape = new Vector<float[]>();
	Vector<float[]> scale = new Vector<float[]>();

	CTPFMap.Descriptor desc = loader.load0(shapeFile, null, shape);
	loader.load0(scaleFile, desc, scale);

	PrintWriter thetaW = new PrintWriter(new FileWriter(outThetaFile));
	PrintWriter logThetaW = new PrintWriter(new FileWriter(outLogThetaFile));
	
	if (shape.size() != scale.size()) throw new IllegalArgumentException("Array sizes (row count) don't match");
	for(int i=0; i<shape.size(); i++) {
	    int r = i - desc.offset;
	    thetaW.print( r + "\t" + r);
	    logThetaW.print( r + "\t" + r);
	    float[] shapeI = shape.elementAt(i), scaleI = scale.elementAt(i);
	    if (shapeI.length != scaleI.length) throw new IllegalArgumentException("Array sizes don't match for i=" + i);
	    for(int j=0; j<shapeI.length; ++j) { 
                double a = shapeI[j]/scaleI[j]; 
                double alog = (float)(Gamma.digamma(shapeI[j]) - Math.log(scaleI[j]));
		thetaW.print("\t" + tsvFmt.format(a));			   
		logThetaW.print("\t" + tsvFmt.format(alog));
	    }
     
	    thetaW.println();			   
	    logThetaW.println();
	}
	thetaW.close();
	logThetaW.close();
    }

}

