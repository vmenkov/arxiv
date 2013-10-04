package edu.rutgers.axs.ee4;

//import org.apache.lucene.document.*;
//import org.apache.lucene.index.*;
//import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;

import edu.rutgers.axs.sql.DataFile;

/** Auxiliary class used for reading in assignment files */
class AsgMap {
    HashMap<String,Integer> map = new 	HashMap<String,Integer>();
    /** Used to map int value to unique Integer objects (space-saving) */
    private Vector<Integer> integers = new Vector<Integer>();
    
    /** Reads the map from a file */
    AsgMap(String cat) throws IOException {
	File catdir =  getAsgDirPath(cat);
	File f = new File(catdir, "asg.dat");
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
	String s;
	//int linecnt = 0, cnt=0;
	while((s=r.readLine())!=null) {
	    s = s.trim();
		String q[] = s.split(",");
		int x =	Integer.parseInt(q[1]);
		while (integers.size() <= x) {
		    integers.add(integers.size(), new Integer(integers.size()));
		}
		map.put(q[0], integers.elementAt(x));
	}
	r.close();
    }


    static  File getAsgMainDir()  {
	File d = DataFile.getMainDatafileDirectory();
	d = new File(d,  "tmp");
	d = new File(d,  "svd-asg");
	return d;
    }
   
    static  File getAsgDirPath(String cat)  {
	File d = getAsgMainDir();
	d = new File(d, cat);
	return d;
    }
    
    /** Saves a specific assigment map (e.g., generated by a
	clustering algorithm) into a data file.
	@param asg The map to be saved (maps an integer doc id to an
	integer cluster id)
	@param no2aid The map used to convert integer doc ids to
	human-readable permanent ArXiv IDs.
	@param id0 Is added to each cluster id.
    */
    static void saveAsg(int asg[], String[] no2aid, String cat, int id0) throws IOException {
	File catdir =  getAsgDirPath(cat);
	catdir.mkdirs();
	File f = new File(catdir, "asg.dat");
	PrintWriter w= new PrintWriter(new FileWriter(f));
	for(int i=0; i<no2aid.length; i++) {
	    int id = id0 + asg[i];
	    String aid = no2aid[i];
	    w.println(aid + "," + id);
	}
	w.close();
    }

}

