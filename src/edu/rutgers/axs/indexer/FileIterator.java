package edu.rutgers.axs.indexer;


import java.util.*;
import java.io.*;

/** This more or less corresponds to the perl construct,
    for(`cat filename`)
*/
class FileIterator implements Iterator<String> {
    private String savedNext=null;
    private LineNumberReader r;
    FileIterator() {
	r = new LineNumberReader(new InputStreamReader(System.in));
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
}

