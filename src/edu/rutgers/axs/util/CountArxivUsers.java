package edu.rutgers.axs.util;

import java.io.*;
import java.util.*;
import java.util.regex.*;
//import java.text.*;

import javax.persistence.*;

import org.apache.commons.lang.mutable.*;
import org.json.*;

import edu.rutgers.axs.ee4.Json;

/** Count distinct users in a JSON usage file. */
public class CountArxivUsers  {

    CountArxivUsers(String fname) throws IOException, JSONException {

	JSONObject jsoOuter = Json.readJsonFile(fname);
	JSONArray jsa = jsoOuter.getJSONArray("entries");
	int len = jsa.length();
	System.out.println("Length of the JSON data array = " + len);

	HashMap<String, MutableInt> cookie2cnt = new HashMap<String, MutableInt>();

	int cnt=0;
	for(int i=0; i< len; i++) {
	    JSONObject jso = jsa.getJSONObject(i);
	    String type =  jso.getString( "type");
	    String ip_hash = jso.getString("ip_hash");
	    String aid = jso.has( "arxiv_id") ?
		Json.canonicAid(jso.getString( "arxiv_id")) : null; 
	    String cookie = null;
	    for(String key: new String[] { "cookie_hash", "cookie"}) {
		if (jso.has(key)) {
		    cookie = jso.getString(key);
		    break;
		}
	    }
	    if (cookie==null) continue;

	    cnt ++;
	    MutableInt ox = cookie2cnt.get(cookie);
	    if (ox==null) cookie2cnt.put(cookie, new MutableInt(1));
	    else ox.add(1);
	}

	final int minCnt = 2;
	int userCnt = 0;
	for(MutableInt v: cookie2cnt.values()) {
	    if (v.intValue() >= minCnt) userCnt ++;
	}
	System.out.println("Usage: found " + cnt + " cookie-carrying actions, with "+cookie2cnt.size()+" distinct cookies; of them, " + userCnt+ " cookies with at least " + minCnt + " actions");
 
    }

    public static void main(String [] argv) throws IOException, JSONException {
	  
	if (argv.length != 1) {
	    System.out.println("Usage: ArxivUserTable filename.json");
	    return;
	}
	CountArxivUsers t = new  CountArxivUsers(argv[0]);
    }


}