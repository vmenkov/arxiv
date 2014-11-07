package edu.rutgers.axs.recommender;


import java.util.*;
import java.io.*;


public class Stoplist extends TreeSet<String>{
    /** Loads a stop list from a local file. This constructor can be
	conveniently used in a command-line application. */
    public Stoplist(File f) throws IOException {
	if (!f.exists() || !f.canRead()) throw new IllegalArgumentException("Stoplist file '"+f+"' does not exist, or cannot be read");
	init( new FileReader(f));
    }

    /** Loads a stop list from an InputStream. This constructor can be
	conveniently used within a web application, where the
	InputStream can be opened with
	ServletContext.getResourceAsStream(path).
    */
    public Stoplist(InputStream is) throws IOException {
	init( new InputStreamReader(is));
    }


    /** Reads the file from the Reader, and then closes the Reader */
    private void init(Reader fr) throws IOException {
	LineNumberReader r =  new LineNumberReader(fr);
	String s=null;
	while((s = r.readLine())!=null) {
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    add(s);
	}
	r.close();
    }

 
}
