package edu.rutgers.axs.ee4;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import org.json.*;

//import cern.colt.matrix.*;
//import cern.colt.matrix.impl.SparseDoubleMatrix2D;
//import cern.colt.matrix.linalg.SingularValueDecomposition;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/** Exports document information in a format suitable for reading by the
    Multiclass SVM tool. */
class DocumentExporter {

    class Dictionary {
	/** One-based, for the SVM tool */
	private Vector<String> v=new Vector<String>();
	private HashMap<String,Integer> map=new HashMap<String,Integer>();

	private final int numdocs = reader.numDocs();
	private final int MINDF = 5, MAXDF = numdocs/2;

	Dictionary() {
	    v.add("null");
	    for(String cat: Categories.listAllStorableCats()) {
		get0(PRIMARY_CATEGORY, cat);
		get0(ArxivFields.CATEGORY, cat);
	    }
	}

	/** Returns the index for the existing record or a new one,
	 as appropriate. */
	synchronized int get0(String name, String term) {
	    return get0(name + ":" + term);
	}
	synchronized int get0(String s) {
	    Integer x = map.get(s);
	    return  (x==null) ?  add(s) : x.intValue();
	}

	private int add(String s) {
	    int pos=v.size();
	    map.put(s, new Integer(pos));
	    v.add(s);
	    return pos;
	}

	/** We put common words here, to reduce the number of
	    calls to IndexReader.docFreq() */
	private HashSet<String> moreStopWords = new HashSet<String>();

	/** @return The stored or new record index, if the term is not
	    ignorable; 0 otherwise.
	 */
	synchronized int get1(String name, String word) throws IOException {
	    String key = name + ":" + word;
	    if (name==ArxivFields.CATEGORY) return get0(key);
	    int has = map.get(key);
	    if (has>0) return has;
	    if (UserProfile.isUseless(word) ||
		moreStopWords.contains(key)) return 0;
	    Term term = new Term(name, word);
	    int df = reader.docFreq(term);
	    if (df > MAXDF) {
		moreStopWords.add(key);
		return 0;
	    }
	    return  (df < MINDF) ?  0 : add(key);
	}

	void save(File f) throws IOException {
	    PrintWriter w= new PrintWriter(new FileWriter(f));
	    for(int i=1; i<v.size(); i++) {
		w.println("" + i + "," + v.elementAt(i));
	    }
	    w.close();
	}
    }

    Dictionary dic=new Dictionary();

    static private class Pair implements Comparable<Pair> {
	int key,  val;
	Pair(	int _key, int _val) {
	    key=_key;
	    val=_val;
	}
	public int compareTo(Pair other) {
	    return key-other.key;
	}
    }
	
    private void processField(Vector<Pair> h,
			      //StringBuffer b, 
			      int docno, Document doc, String name) throws IOException {
	//Fieldable f = doc.getFieldable(name);
	
	TermFreqVector tfv=reader.getTermFreqVector(docno, name);
	if (tfv==null) {
	    //System.out.println("--No terms--");
	    return;
	} 
	
	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();
	for(int i=0; i<terms.length; i++) {
	    int key=dic.get1(name, terms[i]);
	    if (key>0) h.add(new Pair(key, freqs[i]));
	}
    }

    /** Fields we're exporting */
    static private final String fields[] =  {
	ArxivFields.CATEGORY,
	ArxivFields.TITLE, 
	ArxivFields.AUTHORS, ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE};

    static private final String PRIMARY_CATEGORY = "primary_"+ArxivFields.CATEGORY;

    private final IndexReader reader = Common.newReader2();

    /*
    private boolean ignorable(String name, String word) throws IOException {
	String key = name + ":" + word;
	if (name==ArxivFields.CATEGORY ||
	    dic.contains(key)) return false;
	if (UserProfile.isUseless(word) ||
	    moreStopWords.contains(key)) return true;
	Term term = new Term(name, word);
    	int df = reader.docFreq(term);
	if (df > MAXDF) {
	    moreStopWords.add(key);
	    return true;
	}
	return  (df < MINDF);
    }
    */
    public void finalize()  {
	try {
	    reader.close();
	} catch (IOException ex) {}
    }

    /**
       @param w The training file for SVM will be written here
       @param wasg The list of docs described in w will be written here
     */
    void exportAll(File asgFile, PrintWriter w, PrintWriter wasg)  throws IOException {
       	AsgMap map = new AsgMap(asgFile);
	IndexSearcher searcher = new IndexSearcher( reader );
	
	Categorizer catz = new Categorizer(false); // no conversion to major
	int ignoreCnt=0;
	for( String aid: map.list) {
	    int docno = Common.find(searcher, aid);
	    Document doc = reader.document(docno);

	    Vector<Pair> h = new Vector<Pair>();
 
	    Categories.Cat c = catz.categorize(docno, doc);
	    if (c==null) {
		System.out.println("Ignoring document " + aid + ": primary cat is not valid");
		ignoreCnt++;
		continue;
	    }
	    String primaryCat = c.fullName();
	    int key =dic.get0(PRIMARY_CATEGORY, primaryCat);
	    h.add(new Pair(key, 1));

	    for(String name: fields) {
		processField( h, docno, doc, name);
	    }
	    Pair[] pairs = h.toArray(new Pair[0]);
	    Arrays.sort(pairs); // sort by feature number, as required by SVM

	    int clu = map.map.get(aid).intValue();
	    w.print("" + clu);
	    for(Pair p: pairs) {
		w.print(" " + p.key + ":" + p.val);
	    }
	    w.println( " # " + aid);
	    if (wasg!=null) {
		wasg.println(aid + "," + clu);
	    }
	}
	System.out.println("Out of " + map.list.size() + " documents, ignored " + ignoreCnt);
    }

}