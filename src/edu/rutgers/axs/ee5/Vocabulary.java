package edu.rutgers.axs.ee5;

import java.util.*;
import java.io.*;
import edu.rutgers.axs.sql.Logging;

/** Multiword vocabulary, as prepared by Alex Alemi. A vocabulary is a
    list of "multiwords" (word sequences). In the vocabulary, each
    multiword is assigned to one of L (L=1024) "word clusters"; this mapping is
    used to create a low-dimensional (L-dimensional) representation of
    a text.
 */

public class Vocabulary {

    /** The number of word clusters (= the dimension of the
	low-dimensional feature space, into which documents 
	are mapped).
    */
    public final int L;

    /** A Multiword instance describes a single "multiword" (a
     * sequence of 1 or more words).
     */
    static class Multiword {
	/** Sequence of 1 or more words forming this "multiword" */
	String [] seq;
	/** The ID of the word cluster to which this Multiword is
	    assigned. The value is in the range [0:L-1] */
	int wcid;
	Multiword(String [] _seq, int _wcid) {
	    seq = _seq;
	    wcid = _wcid;
	}
	int length() { return seq.length; }

	/** Does this multiword matches the same-length 
	    subsequence starting at text[start]?
	 */
	boolean matches(String [] text, int start) {
	    return matches(text, start, 0);
	}
	/** Does this multiword matches the same-length 
	    subsequence starting at text[start]?
	    @param text a section of which we want to match against 
	    this multiword
	    @param start starting position of the text section to be matched
	    @param pos skip so many words from the beginning of the matched
	    section and this multiword (because we know that so many words
	    already match)
	 */
	boolean matches(String [] text, int start, int pos) {
	    if (start + seq.length > text.length) return false;
	    for(int j=pos; j<seq.length; j++) {
		if (!seq[j].equals(text[start+j]))  return false;
	    }
	    return true;
	}
    }

    //    final private Vector<Multiword> multiwords;
    /** Used to look up all multiwords starting with a particular word */
    private HashMap<String, Vector<Multiword>> firstWordToMultiwords;
    /** How many multiwords are in this vocabulary? */
    int size() { 
	//return multiwords.size();
	return mwCnt;
    }
    private int mwCnt=0;

    /**Creates a Vocabulary file based on a multiword cluster
       assignment file. 
       @param f Vocabulary+cluster file, such as
       arxiv/ee5/kmeans1024.csv. It is in csv format; in each line,
       the first column containing a multiword (one or several words
       joined with a '_'); the second column, the word cluster id
       (in the range [0..L-1]).   Any additional columns after the
       first two are disregarded.
       @param _L expected number of word clusters (e.g. 1024). If 0 is 
       passed, the dimension is set as the max cluster id value 
       in the input file plus 1.
    */
    Vocabulary(File f, int _L) throws IOException {

	//multiwords = new Vector<Multiword>(2000000);
	firstWordToMultiwords =new HashMap<String,Vector<Multiword>>();
	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
 	String s;
	int linecnt = 0, cnt=0;
	boolean lFromFile = (_L==0);
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;	    
	    String q[] = s.split("\\s+");
	    if (q==null || q.length != 1) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f + " : " + s);
	    }

	    q = s.split(",");
	    if (q==null || q.length < 2) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f + " : " + s);
	    }

	    int wcid = Integer.parseInt(q[1]);

	    if (wcid < 0 || !lFromFile && wcid >= _L)  {
		throw new IOException("Word cluster id " + wcid + " is out of range. Line " + linecnt + " in file " + f + " : " + s);
	    }
	    if (lFromFile && wcid >= _L) _L = wcid + 1;
	    String words[] = q[0].split("_");
	    if (words==null || words.length < 1) {
		throw new IOException("Cannot parse line " + linecnt + " in file " + f + " : " + s);
	    } 
	    Multiword m = new Multiword(words,wcid);
	    //	    multiwords.add(m);  // OOM here! (Only when used in a web application)
	    mwCnt++;
	    String key = words[0];
	    Vector<Multiword> z = firstWordToMultiwords.get(key);
	    if (z==null) {
		firstWordToMultiwords.put(key,z=new Vector<Multiword>());
	    }
	    z.add(m);	
	    if (linecnt % 100000 == 0) Logging.info("Voc: linecnt=" + linecnt);
	}
	r.close();
	L = _L;
    }
    
    /** Finds the longest multiword (from our list of multiwors)
	that starts at text[start];
	@return the longest matching Multiword, or null if none
	matches
     */
    Multiword findLongestMatch(String [] text, int start) {
	Vector<Multiword> q = firstWordToMultiwords.get(text[start]);
	if (q==null) return null;
	int bestlen = 0;
	Multiword best = null;
	for(Multiword m: q) {
	    if (m.length() > bestlen && m.matches(text,start,1)) {
		best = m;
		bestlen = m.length();
	    }
	}
	return best;
    }

    /** Converts a document (a string of text) to a vector in the
	word2vec word cluster space. A greedy method is used: a
	longest multiword starting at the beginning of the text is found
	and removed, with the appropriate component of the
	result vector incremented; then the process is repeated on the
	remainder of the document, and so on.
	@param text The document's text to parse. 
	@param v Output parameter: the document vector is added to v. If v is null, a new array of the size this.L is allocated.
    */
    double[] textToVector(String text, double[] v) {
	if (v==null) v = new double[L];
	text=text.toLowerCase().trim();
	String [] s = text.split("[^a-z0-9]+");
	StringBuffer ignorable = new StringBuffer();
	for(int pos=0; pos< s.length; ) {
	    Multiword m = findLongestMatch(s, pos);
	    if (m==null) {
		// ignore this word, which is not in vocabulary
		if (ignorable.length()>0) ignorable.append(" ");
		ignorable.append(s[pos]);
		pos ++;
	    } else {
		v[m.wcid] += 1.0;
		pos += m.length();
	    }
	}
	return v;
    }

    /** Reads a vocabulary file (actually, a word - to - word cluster assignment file) */
    static public Vocabulary readVocabulary()  throws IOException{
	return readVocabulary(null);
    }

    static public Vocabulary readVocabulary(File f)  throws IOException{
	if (f==null)  f = Files.getWordClusterFile();
	if (!f.canRead()) {
	    throw new IOException("Vocabulary clustering file " + f + " does not exist or is not readable");
	}
	Logging.info("Reading vocabulary from file "+ f);
	return new Vocabulary(f, 0);
    }

  

}
