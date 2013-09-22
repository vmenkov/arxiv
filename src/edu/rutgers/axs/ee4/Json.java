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

    /** Information about registered Arxiv users, from tc.json */
    static class ArxivUsersTable  {
	HashMap<String,Vector<String>> user2cookies = new HashMap<String,Vector<String>>();
	HashMap<String,String> cookie2user =  new HashMap<String,String>();

	ArxivUsersTable(String fname) throws IOException, JSONException {
	    JSONObject jsoOuter = Json.readJsonFile(fname);
	    String [] names = JSONObject.getNames(jsoOuter);
	    System.out.println("User activity file has data for " + names.length + " users");
	    int cnt=0;
	    for(String u: names) {
		JSONArray a = jsoOuter.getJSONArray(u);
		final int n=a.length();
		Vector<String> v= new Vector<String>(n); 
		for(int i=0; i<n; i++) {
		    String cookie = a.getString(i);
		    v.add(cookie);
		    cookie2user.put(cookie, u);
		}
		user2cookies.put(u,v);
		cnt += n;
	    }
	    System.out.println("User activity file contains "+cnt+" cookies for " + names.length + " users");
	}
    }

    public static void main(String [] argv) throws IOException, JSONException {

	if (argv.length != 1) {
	    System.out.println("Usage: Json filename.js");
	    return;
	}
	ArxivUsersTable t = new  ArxivUsersTable(argv[0]);
    }



}