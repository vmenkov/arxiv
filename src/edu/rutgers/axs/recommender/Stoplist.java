package edu.rutgers.axs.recommender;


import java.util.*;
import java.io.*;


public class Stoplist extends TreeSet<String>{
    public Stoplist(File f) throws IOException {
	if (!f.exists()) throw new IllegalArgumentException("File '"+f+"' does not exist");
	FileReader fr = new FileReader(f);
	LineNumberReader r =  new LineNumberReader(fr);
	String s=null;
	while((s = r.readLine())!=null) {
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    add(s);
	}

    }
}
