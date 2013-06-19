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
	JSONArray jsa = jsoOuter.getJSONArray("entries");
	int len = jsa.length();
	System.out.println("Length of the JSON data array = " + len);

	Categorizer catz = new Categorizer(true);

	// Major category info for each document ID. Null is stored for invalid 
	// Arxiv IDs, or for those with no valid major cat 
	HashMap<String, Categories.Cat> catAsg= new HashMap<String, Categories.Cat>();

	//	HashMap<String, HistoryMatrix> matrixAssemblers = 
	//	    new HashMap<String, HistoryMatrix>();
	DataSaver saver = new DataSaver();

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

    /** The location of the temporary CSV file into which "ip_hash,aid" pairs
	are saved for a particular major category. With 1.5 million action per 
	week in all categories (i.e., some 0.15 mln per cat per week), each 
	such CSV file would have around 40 million lines.
     */
    private static File catFile(String majorCat) {
	File d = new File( DataFile.getMainDatafileDirectory(), "tmp");
	d = new File(d, "hc");
	d.mkdirs();
	return new File(d, majorCat + ".csv");
    }

    /** Used to save relevant data in a compact format (CSV) in
	separate files (one per major category).
     */
    private static class DataSaver {
	HashMap<String,PrintWriter> writers = new HashMap<String,PrintWriter>();
	void save(String majorCat, String ip_hash, String aid) throws IOException {
	    PrintWriter w = writers.get(majorCat);
	    if (w==null) {
		File f = catFile( majorCat);
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

   static void usage() {
	System.out.println("Usage: HistoryClustering [split|...]");
	System.exit(0);
    }


    public static void main(String [] argv) throws IOException, JSONException {

	if (argv.length != 1) {
	    usage();
	} else if (argv[0].equals("split")) {
	    // String fname = "../json/user_data_0/" + "100510_user_data.json";
	    String fname = "../json/user_data/" + "110301_user_data.json";

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