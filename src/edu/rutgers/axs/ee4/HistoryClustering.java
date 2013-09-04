package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.ScoreDoc;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import org.json.*;

import cern.colt.matrix.*;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.linalg.SingularValueDecomposition;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.recommender.*;

import edu.cornell.cs.osmot.options.Options;

/** Document clustering based on user-ArXiv interaction history, for
    Peter Frazier's Exploration Engine ver. 4. For details, see PF's
    2013-06-07 message, "new clustering scheme (was Re: EE4 developments)".
    
 */
public class HistoryClustering {
    /**
      {
            "referrer": "http://arxiv.org/find", 
            "ip_hash": "30505f2428eb9b6dd2617307ced6d8b3", 
            "arxiv_id": "0609223", 
            "datetime": 1272844866, 
            "cookie": "293365f47a5597355e2d6a53360d6846", 
            "entry": 2, 
            "type": "abstract"
        },
    */

    /** Do we process JSON records with this particular action type?
     */
    static private boolean typeIsAcceptable(String x) {
	final String types[] = {
	    "abstract", "download", "ftp_download",
	    //  as seen in /data/json/usage/2010/100101_usage.json.gz  :
	    "txt", "abs", "src"
	};
	for(String q: types) { 
	    if (x.equals(q)) return true;
	} 
	return false;
    }

    private static String canonicAid(String aid) {
	aid = aid.replaceAll("v(\\d+)$", "");
	//if (aid.endsWith("v")) return aid.substring(0, aid.length()-1);
	return aid;
    } 

    /** Reads a JSON file, extracts relevant entries, and writes them
	into separate files (one per category).

	@param fname The input file name (complete path)
     */
    private static void splitJsonFile(String fname) throws IOException, JSONException {
	
	FileInputStream fis = new FileInputStream(fname);
	InputStream is = (fname.endsWith(".gz") ?
			  new GZIPInputStream(fis) : fis);
	
	InputStreamReader fr = new InputStreamReader(is);
	//FileReader fr = new FileReader(fname);
	JSONTokener tok = new JSONTokener(fr);
	JSONObject jsoOuter = new JSONObject(tok);
	fr.close();

	JSONArray jsa = jsoOuter.getJSONArray("entries");
	int len = jsa.length();
	System.out.println("Length of the JSON data array = " + len);

	Categorizer catz = new Categorizer(true);

	// Major category info for each document ID. Null is stored for invalid 
	// Arxiv IDs, or for those with no valid major cat 
	HashMap<String, Categories.Cat> catAsg= new HashMap<String, Categories.Cat>();

	//	HashMap<String, HistoryMatrix> matrixAssemblers = 
	//	    new HashMap<String, HistoryMatrix>();
	DataSaver saver = new DataSaver(new File(fname));

	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher(reader);

	int cnt=0, ignorableActionCnt=0, invalidAidCnt = 0, unexpectedActionCnt=0;
	for(int i=0; i< len; i++) {
	    JSONObject jso = jsa.getJSONObject(i);
	    String type =  jso.getString( "type");
	    if (!typeIsAcceptable(type)) {
		ignorableActionCnt++;
		if (jso.has("arxiv_id"))    unexpectedActionCnt++;
		continue;		
	    }

	    String ip_hash = jso.getString("ip_hash").intern();
	    String aid = canonicAid(jso.getString( "arxiv_id")).intern();
	    cnt ++;

	    Categories.Cat c = null;
	    if (catAsg.containsKey(aid)) {
		c = catAsg.get(aid);
	    } else {
		int docno = 0;
		try {
		    docno = Common.find(searcher, aid);
		} catch(IOException ex) {}
		if (docno <= 0) {
		    invalidAidCnt++;
		} else {
		    Document doc = reader.document(docno);
		    c = catz.categorize(docno, doc);
		    if (c==null) {
			System.out.println("aid=" + aid +", docno=" + docno +", no cats!");
		    } else {
			// System.out.println("aid=" + aid +", docno=" + docno +", major cat = " + c);
		    }
		}
		catAsg.put(aid, c);
	    }
	    if (c!=null) {
		saver.save( c.fullName(),  ip_hash, aid);
	    }
	}
	saver.closeAll();
	
	System.out.println("Analyzable action entries count = " + cnt);
	System.out.println("Ignorable  action entries count = " + ignorableActionCnt);

	if (unexpectedActionCnt>0) {
	    System.out.println("There were also " + unexpectedActionCnt + " entries with an arxiv_id field, but with an unacceptable action type");
	}
	System.out.println("Category stats: " + catz.stats());

	System.out.println("Category counts:\n" +  catz.catSizesStats());
	System.out.println("Invalid Arxiv ID count = " +  invalidAidCnt);

	Vector<Integer> nocatMembers= catz.nocatMembers;
	PrintWriter w = new PrintWriter(new FileWriter("nocat.tmp"));
	for(Integer x:  nocatMembers) {
	    w.println(x);
	}
	w.close();
    }

    
    /** The directory for the category-specific files we produce for 
	a particular category from the original JSON file.
     */
    private static File catDir(String majorCat) throws IOException {
	File d = new File( DataFile.getMainDatafileDirectory(), "tmp");
	d = new File(d, "hc");
	d = new File(d, majorCat);
	if (d.exists() && d.isDirectory()) {
	} else {
	    if (!d.mkdirs()) throw new IOException("Failed to create directory " + d);
	}
	return d;
    }


    /** The location of the temporary CSV file into which "ip_hash,aid" pairs
	are saved for a particular major category. With 1.5 million action per 
	week in all categories (i.e., some 0.15 mln per cat per week), each 
	such CSV file would have around 40 million lines.

	@param origFile The name of the source file. It will be
	incorporated into the name of the new (category-specific) file.
     */
    private static File catFile(String majorCat, String origFile) throws IOException {
	File d = catDir(majorCat);
	return new File(d, majorCat + "." + origFile + ".csv");
    }

    /** Used to save relevant data in a compact format (CSV) in
	separate files (one per major category).
     */
    private static class DataSaver {
	/** The name of the original (non-split) data file, with the
	    dir name and the extension (including any ".gz") removed. 
	 */
	private final String origFile;	
	DataSaver(File _origFile) {
	    String name = _origFile.getName();
	    name = name.replaceAll("\\..*", ""); // strip the extension
	    origFile = name;
	}

	HashMap<String,PrintWriter> writers = new HashMap<String,PrintWriter>();
	void save(String majorCat, String ip_hash, String aid) throws IOException {
	    PrintWriter w = writers.get(majorCat);
	    if (w==null) {
		File f = catFile( majorCat, origFile);
		w = new PrintWriter(new FileWriter(f));
		writers.put(majorCat, w);
	    }
	    w.println(ip_hash + "," + aid);
	}
	
	void closeAll() {
	    for(PrintWriter w: writers.values()) {
		w.flush();
		w.close();
	    }
	}
    }

    /** An auxiliary class used when reading in and preprocessing the
	(user,page) matrix. For each user id, we store a vector of
	pages he's accessed.
     */
    static private class U2PL  {

	private HashMap<String, HashSet<Integer>> map = new HashMap<String, HashSet<Integer>>();

	/** This map is used during the table-building process only */
	private Vector<String> origno2aid = new Vector<String>();
	private HashMap<String, Integer> aid2origno = new HashMap<String, Integer>();
	
	/** The final numeric map for article IDs.	 */
	String[] no2aid;
	HashMap<String, Integer> aid2no;

	/** The numeric map for IP hash values (which are a surrogate
	    for user identifiers). 	 */
	String[] no2u;
	HashMap<String, Integer> u2no;

	private Integer registerPage(String p) {
	    Integer q = aid2origno.get(p);
	    if (q==null) {
		q = new Integer(origno2aid.size());
		origno2aid.add(p);
		aid2origno.put(p,q);
	    }
	    return q;
	}

	void add(String u, String p) {
	    HashSet<Integer> v = map.get(u);
	    if (v==null) map.put(u, v = new HashSet<Integer>());
	    v.add(registerPage(p));
	}


	/** Removes "low activity" users and papers; converts the rest
	    into a SparseDoubleMatrix2D object, for use with SVD.
	*/
	SparseDoubleMatrix2D toMatrix() {
	    final int user_thresh=2,  paper_thresh=2;

	    System.out.println("Processing the view matrix. Originally, there are " + map.size() + " users");


	    for(Iterator<Map.Entry<String,HashSet<Integer>>> it = map.entrySet().iterator();
		it.hasNext(); ) {
		if (it.next().getValue().size()< user_thresh) it.remove();
	    }

	    System.out.println("Only " + map.size() + " users have at least " + user_thresh + " page views");
	    
	    // view count for each page
	    int[] viewCnt = new int[origno2aid.size()];

	    for(HashSet<Integer> v: map.values()) {
		for(Integer origno: v) {
		    viewCnt[origno.intValue()] ++;
		}
	    }
	    
	    int viewedPagesCnt=0, popularPagesCnt=0;
	    int cap = 0;
	    for(int vc:  viewCnt) {
		if (vc>0)  viewedPagesCnt++;
		if (vc>=paper_thresh)  {
		    popularPagesCnt++;
		    cap += vc;
		}
	    }

	    System.out.println("There are " + viewCnt.length + " papers; among them, " + viewedPagesCnt + " have been viewed by at least 1 'relevant' user, and " +  popularPagesCnt  + " have been viewed by at least " + paper_thresh + " relevant users");

	    System.out.println("The view matrix will have " +cap+ " nonzeros");

	    // create the permanent page map (only including "popular" pages)
	    no2aid = new String[  popularPagesCnt ];
	    aid2no = new HashMap<String, Integer>();
	    int k = 0;
	    for(int i=0; i<viewCnt.length; i++) {		
		if (viewCnt[i]<paper_thresh)  continue;
		String aid = origno2aid.elementAt(i);
		aid2no.put(aid, new Integer(k));
		no2aid[k++] = aid;
	    }
	
	    no2u = new String[ map.size() ];
	    u2no = new HashMap<String, Integer>();
	    k = 0;
	    for(String u: map.keySet()) {
		u2no.put(u, new Integer(k));
		no2u[k++] = u;
	    }

	    SparseDoubleMatrix2D mat2 = new SparseDoubleMatrix2D(map.size(), no2aid.length);
	    for(String u: map.keySet()) {
		int row = u2no.get(u).intValue();
		for(Integer _origno: map.get(u)) {
		    int origno = _origno.intValue();
		    if (viewCnt[origno] < paper_thresh)  continue;
		    String aid = origno2aid.elementAt(origno);
		    int col = aid2no.get(aid).intValue();
		    mat2.setQuick(row, col, 1.0);
		}
	    }
	    System.out.println("Have a " + mat2.rows() + " by " + mat2.columns() + " matrix with " + mat2.cardinality()  + " non-zeros");
	    return mat2;
	}
    }


    /** Reads split files for a particular category generated by DataSaver.
	All files found in the category's splt file directory will be read;
	so make sure you have the correct selection of files there.
     */
    static 
    //SparseDoubleMatrix2D 
	U2PL
	readSplitFiles(String majorCat) throws IOException {
	File dir = catDir(majorCat);
	File[] files=dir.listFiles(); 
	U2PL   user2pageList = new U2PL();
	for(File f: files) {
	    System.out.println("Reading split file " + f);
	    FileReader fr = new FileReader(f);
	    LineNumberReader r = new LineNumberReader(fr);
	    String s = null;
	    while((s=r.readLine())!=null) {
		s = s.trim();
		if (s.equals("")) continue;
		String q[] = s.split(",");
		if (q.length!=2) throw new IOException("Could not parse line no. " + r.getLineNumber() + " in file " + f + " as a (user,page) pair:\n" + s);
		user2pageList.add(q[0], q[1]);
	    }
	    r.close();
	}
	return user2pageList;
    }

    static void doSvd(String majorCat) throws IOException {
	SparseDoubleMatrix2D mat;
	String[] no2aid;
	{
	    U2PL user2pageList = readSplitFiles(majorCat);
	    mat = user2pageList.toMatrix();
	    no2aid = user2pageList.no2aid;
	}

	//boolean useMySVD = false;
	boolean useMySVD = true;

	System.out.println("Doing SVD");
	final int k_svd = 5; // number of singular vectors to keep
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
	    SingularValueDecomposition svd=new SingularValueDecomposition(mat);

	    sval = svd.getSingularValues();
	    DoubleMatrix2D v = svd.getV();

	    // cluster *rows* of V
	    qq = intoArrayOfRows(v, keepSvd);
	}

	keepSvd = (k_svd < sval.length)? k_svd : sval.length;
	System.out.print("Top " +  keepSvd + " singular values:");
	for(int i=0; i< keepSvd ; i++) System.out.print(" " +sval[i]);
	System.out.println();

	Vector<DenseDataPoint> vdoc = new Vector<DenseDataPoint>(qq.length);
	for(double[] q: qq) {
	    vdoc.add(new DenseDataPoint(q));
	}

	// desired number of clusters
	int k_kmeans = (int)Math.sqrt(  (double)vdoc.size()/200.0);
	if (k_kmeans <=1) k_kmeans = 1;

	KMeansClustering clu = KMeansClustering.findBestClustering(vdoc,
								   keepSvd,
								   k_kmeans);

	int id0 = 1;
	saveAsg( clu, no2aid, majorCat, id0);
    }

    /** Converts a section (first keepSvd columns) of a DoubleMatrix2D
	into a 2D array of doubles.  The first keepSvd elements of each row
	will be packaged into an arrray of doubles. */
    static private double[][] intoArrayOfRows(DoubleMatrix2D v, int keepSvd) {
	
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


    public static void main(String [] argv) throws IOException, JSONException {

	if (argv.length < 1) {
	    usage("Command not specified");
	} else if (argv[0].equals("split")) {
	    if (argv.length < 2) {
		usage("File name not specified");
	    }
	    // String fname = "../json/user_data_0/" + "100510_user_data.json";
	    //String fname = "../json/user_data/" + "110301_user_data.json";
	    String fname = argv[1];
	    splitJsonFile(fname);
	} else if (argv[0].equals("svd")) {
	    if (argv.length < 2) {
		usage("Category name not specified");
	    }
	    String majorCat = argv[1];	
	    doSvd(majorCat);
	} else {
	    usage();
	}


    }

    /*
    static class HistoryMatrix {
	HashMap<String, Integer> u2pageCnt = new HashMap<String, Integer>;
	
    }
    */

}