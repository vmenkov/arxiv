package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.ScoreDoc;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import org.json.*;

import cern.colt.matrix.*;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.recommender.*;

import edu.cornell.cs.osmot.options.Options;

/** Document clustering based on user-ArXiv interaction history, for
    Peter Frazier's Exploration Engine ver. 4. For details, see PF's
    2013-06-07 message.

    
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
	final String types[] = {"abstract", "download", "ftp_download"};
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

    /** Reads a JSON file, extracts relevants entries, and writes them
	into separate files (one per category).
     */
    private static void splitJsonFile(String fname) throws IOException, JSONException {
	
	FileReader fr = new FileReader(fname);
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

	int cnt=0, invalidAidCnt = 0, unexpectedActionCnt=0;
	for(int i=0; i< len; i++) {
	    JSONObject jso = jsa.getJSONObject(i);
	    String type =  jso.getString( "type");
	    if (!typeIsAcceptable(type)) {
		if (jso.has("aid")) 		    unexpectedActionCnt++;
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
	
	System.out.println("Analyzable action entries count = " + cnt);
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
	    dir name and the extension removed. 
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
		w.close();
	    }
	}
    }

    /** An auxiliary class used when reading in and preprocessing the
	(user,page) matrix. For each user id, we store a vector of
	pages he's accessed.
     */
    static private class U2PL extends HashMap<String, Vector<String>> {
	void add(String u, String p) {
	    Vector<String> v = get(u);
	    if (v==null) put(u, v = new Vector<String>());
	    for(String z: v) {
		if (z.equals(p)) return;
	    }
	    v.add(p);
	}

	/** The numeric map for article aids.
	 */
	String[] no2aid;
	HashMap<String, Integer> aid2no;

	SparseDoubleMatrix2D toMatrix() {
	    final int user_thresh=2,  paper_thresh=2;

	    System.out.println("Processing the view matrix. Originally, there are " + size() + " users");

	    for(String u: keySet()) {
		if (get(u).size()< user_thresh) remove(u);
	    }

	    System.out.println("Only " + size() + " users have at least " + user_thresh + " page views");

	    HashMap<String, Integer> viewCnt = new HashMap<String, Integer>();

	    for(Vector<String> v: values()) {
		for(String aid: v) {
		    Integer z = viewCnt.get(aid);
		    int c = (z==null)? 0 : z.intValue();
		    viewCnt.put(aid, new Integer(z+1));
		}
	    }

	    System.out.println("There are " + viewCnt.size() + " papers");
	    int cap = 0;

	    for(String aid: viewCnt.keySet()) {
		Integer z = viewCnt.get(aid);
		if (z.intValue() < paper_thresh)  viewCnt.remove(aid);
		else cap += viewCnt.get(aid).intValue();
	    }

	    System.out.println("But only " + viewCnt.size() + " papers with at least " + paper_thresh + " views. The view matrix will have " + cap + " nonzeros");

	    no2aid = new String[ viewCnt.size() ];
	    aid2no = new HashMap<String, Integer>();
	    int k = 0;
	    for(String aid: viewCnt.keySet()) {
		aid2no.put(aid, new Integer(k));
		no2aid[k++] = aid;
	    }


	    SparseDoubleMatrix2D mat = new SparseDoubleMatrix2D(size(), no2aid.length);
	    for(String u: keySet()) {
		//mat.setQuick(row,  int column,1.0);
	    }

	    return mat;
	}
    }


    /** Reads split files for a particular category generated by DataSaver
     */
    static void readSplitFiles(String majorCat) throws IOException {
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
		user2pageList.add(q[0].intern(), q[1].intern());
	    }
	    r.close();
	}

    }

    static void usage() {
	usage(null);
    }

    static void usage(String m) {
	System.out.println("Usage: HistoryClustering [split filename|...]");
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