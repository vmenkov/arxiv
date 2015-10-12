package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import org.json.*;

/** Information about registered Arxiv users, from tc.json. It links
    a registered user to the list of cookies known to be associated
    with him, and allows the reverse lookup (cookie to user) as well.
*/
class ArxivUserTable  {
    HashMap<String,Vector<String>> user2cookies = new HashMap<String,Vector<String>>();
    HashMap<String,String> cookie2user =  new HashMap<String,String>();
    
    ArxivUserTable(String fname) throws IOException, JSONException {
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
	System.out.println("User activity file "+fname+" contains "+cnt+" cookies for " + names.length + " users");
    }

   public static void main(String [] argv) throws IOException, JSONException {

	if (argv.length != 1) {
	    System.out.println("Usage: ArxivUserTable filename.js");
	    return;
	}
	ArxivUserTable t = new  ArxivUserTable(argv[0]);
    }

}

