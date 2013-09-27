package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import org.json.*;

//import cern.colt.matrix.*;
//import cern.colt.matrix.impl.SparseDoubleMatrix2D;
//import cern.colt.matrix.linalg.SingularValueDecomposition;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;


/** Document clustering based on user-ArXiv.org interaction history, for
    Peter Frazier's Exploration Engine ver. 4. For details, see PF's
    2013-06-07 message, "new clustering scheme (was Re: EE4 developments)".
    
 */
public class HistoryClustering {

    /** It seems to be more efficient to pre-compile the pattern than
	to do String.split() */
    private static final Pattern commaPattern = Pattern.compile(",");

    /** Reads split files for a particular category generated by DataSaver.
	All files found in the category's splt file directory will be read;
	so make sure you have the correct selection of files there.
     */
    static U2PL	readSplitFiles(String majorCat, ArxivUserInferrer inferrer) throws IOException {
	File dir = Json.catDir(majorCat);
	File[] files=dir.listFiles(); 
	U2PL   user2pageList = new U2PL();

	for(File f: files) {
	    System.out.println("Reading split file " + f + ", at "+ new Date());
	    FileReader fr = new FileReader(f);
	    LineNumberReader r = new LineNumberReader(fr);
	    String s = null;
	    while((s=r.readLine())!=null) {
		s = s.trim();
		if (s.equals("")) continue;
		//		String q[] = s.split(",");
		String q[] = commaPattern.split(s);
		if (q.length!=3) throw new IOException("Could not parse line no. " + r.getLineNumber() + " in file " + f + " as an (ip,cookie,page) tuple:\n" + s);
		String user = inferrer.inferUser(q[0],q[1]);
		if (user==null) { 
		    // let's ignore no-cookie entries (which, actually,
		    // don't exist)
		} else {
		    user2pageList.add(user, q[2]);
		}
	    }
	    r.close();
	}
	System.out.println( inferrer.report());
	return user2pageList;
    }

    static void doSvd(String majorCat, 	ArxivUserInferrer inferrer)  throws IOException {
	SparseDoubleMatrix2Dx mat;
	String[] no2aid;
	{
	    U2PL user2pageList = readSplitFiles(majorCat, inferrer);
	    mat = user2pageList.toMatrix();
	    no2aid = user2pageList.no2aid;
	}

	//boolean useMySVD = false;
	boolean useMySVD = true;

	System.out.println("Doing SVD");
	int keepSvd = k_svd;

	double[] sval;
	double[][] qq;

	if (useMySVD) {
	    // testing our own code
	    SVD svd = new SVD(mat);
	    svd.findTopSingularVectors(k_svd);
	    sval = svd.getSingularValues();
	    qq = svd.vIntoArrayOfRows();
	} else {
	    throw new AssertionError("COLT SVD no longer supported");
	    /*
	    SingularValueDecomposition svd=new SingularValueDecomposition(mat);

	    sval = svd.getSingularValues();
	    DoubleMatrix2D v = svd.getV();

	    // cluster *rows* of V
	    qq = intoArrayOfRows(v, keepSvd);
	    */
	}

	keepSvd = (k_svd < sval.length)? k_svd : sval.length;
	System.out.print("Top " +  keepSvd + " singular values:");
	for(int i=0; i< keepSvd ; i++) System.out.print(" " +sval[i]);
	System.out.println();

	Vector<DenseDataPoint> vdoc = new Vector<DenseDataPoint>(qq.length);
	for(double[] q: qq) {
	    vdoc.add(new DenseDataPoint(q));
	}

	// Set the desired number of clusters (if not specified in options)
	if (k_kmeans <= 0) {
	    k_kmeans = (int)Math.sqrt(  (double)vdoc.size()/200.0);
	    if (k_kmeans <=1) k_kmeans = 1;
	    System.out.println("Will create " + k_kmeans + " clusters");
	}
	KMeansClustering clu = KMeansClustering.findBestClustering(vdoc,
								   keepSvd,
								   k_kmeans);

	int id0 = 1;
	saveAsg( clu, no2aid, majorCat, id0);
    }

    /** Converts a section (first keepSvd columns) of a DoubleMatrix2D
	into a 2D array of doubles.  The first keepSvd elements of each row
	will be packaged into an arrray of doubles. */
    /*
    static private double[][] intoArrayOfRows(SparseDoubleMatrix2D v, int keepSvd) {
	
	int ndoc  = v.rows();
	double[][] z = new double[ndoc][];

	for(int i=0; i<ndoc; i++) {
	    double[] q= new double[keepSvd];
	    for(int j=0; j<q.length; j++) {
		q[j] = 	v.getQuick(i, j); 
	    }
	    z[i] = q;
	}
	return z;
    }
    */
    static private File getAsgDirPath(String cat)  {
	File d = DataFile.getMainDatafileDirectory();
	d = new File(d,  "tmp");
	d = new File(d,  "svd-asg");
	d = new File(d, cat);
	return d;
    }


    private static void saveAsg(KMeansClustering clu, String[] no2aid, String cat, int id0) throws IOException {
	File catdir =  getAsgDirPath(cat);
	catdir.mkdirs();
	File f = new File(catdir, "asg.dat");
	PrintWriter w= new PrintWriter(new FileWriter(f));
	for(int i=0; i<no2aid.length; i++) {
	    int id = id0 + clu.asg[i];
	    String aid = no2aid[i];
	    w.println(aid + "," + id);
	}
	w.close();
    }

    static void usage() {
	usage(null);
    }

    static void usage(String m) {
	System.out.println("Usage: HistoryClustering [split filename|svd cat]");
	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }

    /** The number of singular vectors to keep */
    static private int k_svd = 5;
    /** The number of clusters to create. 0 means using an adaptive formula.
     */
    static private int k_kmeans = 0;

    public static void main(String [] argv) throws IOException, JSONException {
	ParseConfig ht = new ParseConfig();
	k_kmeans = ht.getOption("k_kmeans", k_kmeans);
	k_svd = ht.getOption("k_svd", k_svd);

	if (argv.length < 1) {
	    usage("Command not specified");
	} else if (argv[0].equals("split")) {
	    if (argv.length < 2) {
		usage("File name not specified");
	    }
	    // String fname = "../json/user_data_0/" + "100510_user_data.json";
	    //String fname = "../json/user_data/" + "110301_user_data.json";
	    String fname = argv[1];
	    Json.splitJsonFile(fname);
	} else if (argv[0].equals("svd")) {
	    if (argv.length < 2) {
		usage("Category name not specified");
	    }
	    String majorCat = argv[1];	
	    final boolean useCookies=true;

	    final String tcPath = "/data/json/usage/tc.json.gz";
	    ArxivUserInferrer inferrer = useCookies?
		new CookieArxivUserInferrer(new ArxivUserTable(tcPath)):
		new IPArxivUserInferrer();
	    doSvd(majorCat,inferrer);
	} else {
	    usage();
	}
    }

}