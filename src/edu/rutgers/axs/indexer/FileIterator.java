package edu.rutgers.axs.indexer;


import java.util.*;
import java.io.*;

/** An utility class for reading in the content of a file. The file is expected to contain one word per line. This more or less corresponds to the perl construct,
    for(`cat filename`)
*/
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
    
    public boolean 	hasNext()  {
	if (savedNext!=null) return true;
	String s=null;
	do  {
	    try {
		s=r.readLine();
	    } catch(IOException ex) { 
		return false; 
	    }
	    if (s==null) return false;
	    s = s.trim();
	} while (s.equals("") || s.startsWith("#"));
	savedNext = s;
	return true;
    }

    public String next() throws NoSuchElementException {
	if (savedNext==null) throw new NoSuchElementException();
	String s = savedNext;
	savedNext=null;
	return s;
    }
    
    public void remove() throws UnsupportedOperationException {
	throw new  UnsupportedOperationException();
    }

    public void close() throws IOException {
	r.close();
    }
}

