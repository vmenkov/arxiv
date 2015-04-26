package edu.rutgers.axs.indexer;


import java.util.*;
import java.io.*;

/** An utility class for reading in the content of a file. The file is
    expected to contain one word per line, but we don't actually
    check. This more or less corresponds to the perl construct,
    for(`cat filename`) */
public class FileIterator implements Iterator<String> {
    private String savedNext=null;
    private LineNumberReader r;
    /** Will read data from standard input */
    public FileIterator() {
	r = new LineNumberReader(new InputStreamReader(System.in));
    }
    public FileIterator(File f) throws IOException {
	r = new LineNumberReader(new FileReader(f));
    }
    /** Creates a new FileIterator, which will iterate over the content of a file or stdin.
	@param fname File name, or "-" for stdin 
    */
    public static FileIterator createFileIterator(String fname) throws IOException {
	if (fname.equals("-")) return new FileIterator();
	File f = new File(fname);
	if (!f.exists() || !f.canRead()) throw new IOException("File " + f + " does not exist, or cannot be read");
	return new FileIterator(f);
    }
    
    /** Is there one more non-blank line to read? */
    public boolean hasNext()  {
	while (savedNext==null)  {
	    String s=null;
	    try {
		s=r.readLine();
	    } catch(IOException ex) { 
		return false; 
	    }
	    if (s==null) return false;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    savedNext = s;
	}
	return true;
    }

    /** Returns the content of the next non-blank line (with the
	leading and trailing whitespace removed */
    public String next() throws NoSuchElementException {
	if (!hasNext()) throw new NoSuchElementException();
	String s = savedNext;
	savedNext=null;
	return s;
    }
    
    public void remove() throws UnsupportedOperationException {
	throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
	r.close();
    }

    /** Reads in the list of AIDs 
	@param infile Name of a file with 1 or several AIDs per line
	@param All AIDs in a 2D array (one row per line of input file)
    */
    public static Vector<String[]> readAids(String  infile)  throws IOException{
	Vector<String[]> aidsList = new Vector<String[]>();
	FileIterator it = FileIterator.createFileIterator(infile); 
	while(it.hasNext()) {
	    aidsList.add( it.next().split("\\s+"));
	}
	it.close();
	return aidsList;
    }

    /** Reads in the list of AIDs
	@param infile Name of a file with 1 or several AIDs per line
	@param All AIDs in a single array
    */
    public static String[] readAidsFlat(String  infile) throws IOException {
       Vector<String[]> lists = readAids(infile);
       int n = 0;
       for(String aids[] : lists) {
	   n += aids.length;
       }
       String[] q = new String[n];
       int k=0;
       for(String aids[] : lists) {
	    for(String aid: aids) {
		q[k++] = aid;
	    }
       }
       return q;
   }

}

