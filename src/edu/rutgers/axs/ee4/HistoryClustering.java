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

    private static final Pattern datePattern = Pattern.compile("[0-9][0-9][0-9][0-9][0-9][0-9]");

    /** Checks input file date, if a range restriction has been requested 
     */
    private static boolean dateIsAcceptable(File f) {
	if (usageFrom==null && usageTo==null) return true; 
	//	e.g.    cs.110413_usage.csv
	Matcher m = datePattern.matcher(f.getName());
	if (!m.find()) {
	    System.out.println("Unable to find the date in the name of the file " + f + "; skipping the file");
	    return false;
	} 
	String d = m.group();
	if (usageFrom != null && d.compareTo(usageFrom) < 0) return false;
	if (usageTo != null && d.compareTo(usageTo) >= 0) return false;
	return true;
    }

    /** Reads split files for a particular category generated by DataSaver.
	All files found in the category's split file directory will be read;
	so make sure you have the correct selection of files there.
     */
    private static U2PL	readSplitFiles(String majorCat, ArxivUserInferrer inferrer) throws IOException {
	File dir = Json.catDir(majorCat);
	File[] files=dir.listFiles(); 
	Arrays.sort(files);
	U2PL   user2pageList = new U2PL();
	user2pageList.setArticleDateRange(articleDateFrom,  articleDateTo);

	for(File f: files) {

	    if (!dateIsAcceptable(f)){
		System.out.println("Skip file " + f );
		continue;
	    }

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
		    String aid=q[2];
		    user2pageList.add(user, aid);
		}
	    }
	    r.close();
	}
	System.out.println( inferrer.report());
	System.out.println( "Ignored " +  user2pageList.ignoreCnt + " log entries for out-of-date-range articles");
	return user2pageList;
    }

    /** Builds the coaccess matrix for the specified major category
       from the pre-split usage files, and carries out incomplete SVD
       decomposition of that matrix.

       @param normalize Normalize object vectors in the reduced-dim space,
       as a (silly) experiment.
    */
    static void doSvd(String majorCat, 	ArxivUserInferrer inferrer,
		      boolean normalize)  throws IOException {
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
	    DenseDataPoint p = new DenseDataPoint(q);
	    if (normalize) { 
		p.normalize();
	    }
	    vdoc.add(p);
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

	String asgPath = ht.getOption("asgPath", null);
	if (asgPath=="null") asgPath = null;
	AsgMap.saveAsg( clu.asg, no2aid, majorCat, id0, asgPath);
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

    /** Prepares input files for the SVM application, based on the
	assignment map file and the data in the Lucene data store.
	@param asgFile The cluster assignment map file to be used
	@param d Directory for output files
    */
    private static void doSvm(File asgFile, File d)  throws IOException {
	// FIXME: is there a nicer way, without hogging the static space?
	UserProfile.setStoplist(new Stoplist(new File("WEB-INF/stop200.txt")));
	DocumentExporter de = new DocumentExporter();
	
	File g = new File(d, "train.dat");
	PrintWriter w= new PrintWriter(new FileWriter(g));

	File h = new File(d, "exported.asg");
	PrintWriter wasg = new PrintWriter(new FileWriter(h));

	System.out.println("Saving training set to " + g + "; list of exported docs, to " + h);

	de.exportAll( asgFile,w, wasg);
	w.close();
	wasg.close();

	File f = new File(d, "asg.dic");
	System.out.println("Saving dictionary to " + f);
	de.dic.save(f);

    }

    /** The number of singular vectors to keep */
    static private int k_svd = 5;
    /** The number of clusters to create. 0 means using an adaptive formula.
     */
    static private int k_kmeans = 0;
    
    /** Strings such as "20100101" or "20120101", specifying the
	temporal range (d1 &le; date &lt; d2) of the user activity data that we
	use in the construction of the coaccess matrix. A null value
	means that we don't select based on that criterion.
     */
    static private String usageFrom=null, usageTo=null;

    static private Date articleDateFrom=null,  articleDateTo=null;

    /** Returns a string in the format "YYMMDD". We have the "reverse year 2000
	problem" here, meaning that dates before 2000 can't be represented :-)
     */
    private static String getDateStringOption(ParseConfig ht, String name) {
	String x= ht.getOption(name, null);
	if (x==null) return x;
	if (x!=null && x.equals("null")) return null;
	if (x.length()==6) return x;
	if (x.length()==8 && x.startsWith("20")) return x.substring(2);
	usage("Option " + name + " must be in the format YYMMDD or YYYYMMDD");
	return null;
    }
					      

    private static ParseConfig ht = null;
    public static void main(String [] argv) throws IOException, java.text.ParseException, JSONException {

	final String tcPath = "/data/json/usage/tc.json.gz";
	final boolean useCookies=true;



	ht = new ParseConfig();
	// options for SVD run
	k_kmeans = ht.getOption("k_kmeans", k_kmeans);
	k_svd = ht.getOption("k_svd", k_svd);
	boolean normalize = ht.getOption("normalize", false);

	usageFrom=getDateStringOption(ht,"usageFrom");
	usageTo  =getDateStringOption(ht,"usageTo");

	articleDateFrom = ht.getOptionDate("articleDateFrom", "20100101");
	articleDateTo = ht.getOptionDate("articleDateTo", "20120101");

	System.out.println("Reading pre-split usage files dated " +
			   usageFrom + " to " + usageTo);

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
	    
	    ArxivUserInferrer inferrer = useCookies?
		new CookieArxivUserInferrer(new ArxivUserTable(tcPath)):
		new IPArxivUserInferrer();
	    if (normalize) System.out.println("Will normalize document vectors in reduced-dim space");
	    doSvd(majorCat,inferrer,normalize);
	} else if (argv[0].equals("svm")) {
	    // one of the two must be supplied
	    final String svmDir = ht.getOption("svmDir", null);
	    final String majorCat = (argv.length < 2)? null : argv[1];	
	    if (svmDir==null && majorCat==null) usage("Either -DsvmDir=... must be set, or majorCat must be supplied");
	    // directory for output files
	    final File d =  (svmDir!=null) ?
		new File(svmDir) : AsgMap.getAsgDirPath(majorCat);

	    // The file to process
	    String asgPath = ht.getOption("asgPath", null);
	    
	    File asgFile =  (asgPath!=null)?	    
		new File(asgPath) :
		new File(d,  "asg.dat");

	    doSvm(asgFile, d);
	} else if (argv[0].equals("blei")) {	   
	    // output for David Blei's team, as per his 2013-11-11 msg
	    if (argv.length != 3) usage("Command 'blei' needs infile outfile");
	    ArxivUserInferrer inferrer = useCookies?
		new CookieArxivUserInferrer(new ArxivUserTable(tcPath)):
		new IPArxivUserInferrer();
	    String fname = argv[1];
	    File outfile = new File(argv[2]);

	    Json.convertJsonFileBlei(fname, inferrer,  outfile);
	} else {
	    usage();
	}
    }

}