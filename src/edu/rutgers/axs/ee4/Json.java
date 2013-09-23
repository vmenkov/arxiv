package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import org.json.*;

import edu.rutgers.axs.indexer.Common;
import edu.rutgers.axs.sql.Categories;
import edu.rutgers.axs.sql.DataFile;


/** Auxiliary methods for processing usage logs in Json format
 */
class Json {

    static JSONObject readJsonFile(String fname) throws IOException, JSONException {
	Reader fr = fname.endsWith(".gz") ?
	    new InputStreamReader(new GZIPInputStream(new FileInputStream(fname))) :
	    new FileReader(fname);
	JSONTokener tok = new JSONTokener(fr);
	JSONObject jsoOuter = new JSONObject(tok);
	fr.close();
	return jsoOuter;
    }


    /**  Old format
      {
            "referrer": "http://arxiv.org/find", 
            "ip_hash": "30505f2428eb9b6dd2617307ced6d8b3", 
            "arxiv_id": "0609223", 
            "datetime": 1272844866, 
            "cookie": "293365f47a5597355e2d6a53360d6846", 
            "entry": 2, 
            "type": "abstract"
        },

	New format
   {
      "arxiv_id": "0902.0656", 
      "cookie_hash": "a1bab5ac50bfd9aa", 
      "entry": 3, 
      "ip_hash": "414a807fcda84084", 
      "type": "txt", 
      "user_agent": "Mozilla/5.0 (X11; U; Linux i686; en-GB; rv:1.9.0.10) Gecko/2009042523", "utc": 126230400
2
    }

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
    static void splitJsonFile(String fname) throws IOException, JSONException {

	JSONObject jsoOuter = Json.readJsonFile(fname);
	JSONArray jsa = jsoOuter.getJSONArray("entries");
	int len = jsa.length();
	System.out.println("Length of the JSON data array = " + len);

	Categorizer catz = new Categorizer(true);

	// Major category info for each document ID. Null is stored for invalid 
	// Arxiv IDs, or for those with no valid major cat 
	HashMap<String, Categories.Cat> catAsg= new HashMap<String, Categories.Cat>();

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

	    String ip_hash = jso.getString("ip_hash");
	    String aid = canonicAid(jso.getString( "arxiv_id"));
	    String cookie = jso.getString("cookie_hash");
	    if (cookie==null) cookie = jso.getString("cookie");
	    if (cookie==null) cookie = "";

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
		saver.save( c.fullName(),  ip_hash, cookie, aid);
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
    static File catDir(String majorCat) throws IOException {
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
	void save(String majorCat, String ip_hash, String cookie, String aid) throws IOException {
	    PrintWriter w = writers.get(majorCat);
	    if (w==null) {
		File f = catFile( majorCat, origFile);
		w = new PrintWriter(new FileWriter(f));
		writers.put(majorCat, w);
	    }
	    w.println(ip_hash + "," + cookie + "," + aid);
	}
	
	void closeAll() {
	    for(PrintWriter w: writers.values()) {
		w.flush();
		w.close();
	    }
	}
    }

 
}