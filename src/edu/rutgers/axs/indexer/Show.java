package edu.rutgers.axs.indexer;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;

import org.apache.lucene.document.*;
import org.apache.lucene.analysis.Analyzer;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import edu.rutgers.axs.web.Search;
import edu.rutgers.axs.web.SearchResults;


/** Used to look up article information in our Lucene data store (a
 * delayed copy of sorts of what arxiv.org has)
 */
class Show {

    private IndexReader reader;
    
    public Show()  throws IOException {
	this(null, true);

    }
    public Show(boolean verbose)  throws IOException {
	this(null, verbose);
    }
    public Show(IndexReader _reader, boolean verbose)  throws IOException {
	reader = _reader;
	if (reader==null) {
	    Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	    reader =  IndexReader.open( indexDirectory);  
	}
	if (verbose) {
	    int numdocs = reader.numDocs();
	    int maxdoc = reader.maxDoc();
	    //	    System.out.println("numdocs=" + numdocs+", maxdoc=" + maxdoc);
	}
    }

    /** Interpret a given string (a command line argument) as an
	integer Lucene doc number or as a string ArXiv doc id.
     */
    int figureDocno(String v) throws IOException  {
	// is it numeric?
	try {
	    int docno = Integer.parseInt(v);
	    if  (v.equals("" + docno)) return docno;  // numeric id requested
	} catch(Exception ex) {}
	return find(v);
    }



    /** Finds Lucene document (its internal integer id) by Arxiv id */
    private int find(String id) throws IOException{

	IndexSearcher s = new IndexSearcher( reader );
	return Common.find(s, id);
   }


    /**
       @param id Article id in the Arxiv system
     */
    void show(String id) throws IOException {
	int docno = find(id);
	show(docno);
    }

    void show(int docno) throws IOException {
	Document doc = reader.document(docno);
	System.out.println("Doc no.=" + docno);
	System.out.println("dateIndexed=" + doc.get(ArxivFields.DATE_INDEXED));
	System.out.println("date       =" + doc.get(ArxivFields.DATE));
	System.out.println("authors    =" + doc.get(ArxivFields.AUTHORS));
	System.out.println("Document=" + doc);
    }

   void showTitle(int docno) throws IOException {
	Document doc = reader.document(docno);
	String s = "[" +doc.get(ArxivFields.PAPER)+ "] " + 
			   doc.get(ArxivFields.CATEGORY) + "; " +
			   doc.get(ArxivFields.TITLE);
	s = s.replaceAll("\\s+", " ");
	System.out.println(s);
    }

    /** Specifically for David Blei's request, 2013-10-11
	<pre>
	here is the form of data that i would like.

	1. a file with article abstracts.  each line contains

	&lt;arxiv id&gt;, &lt;the text of the abstract in quotes&gt;
	</pre>
    */
    void showAbstract(int docno) throws IOException {
	Document doc = reader.document(docno);
	String a = doc.get(ArxivFields.ABSTRACT);
	a = a.replaceAll("\"", " ").replaceAll("\\s+", " ").trim();
	String s = doc.get(ArxivFields.PAPER)+ ",\"" + a + "\"";
	System.out.println(s);
    }


    /** Shows the terms and coefficients for a particular document.

	<p>Note: using 
	long utc = reader.getUniqueTermCount();
	won't do, as we get an java.lang.UnsupportedOperationException: 
	"this reader does not implement getUniqueTermCount()".
	This is why we use subreaders.

	@param is The ArXiv document ID
    */
   void showCoef(String id) throws IOException {
	int docno = find(id);
	System.out.println("Document info for id=" + id +", doc no.=" + docno);
	showCoef(docno);
    }

    /** Shows the terms and coefficients for a particular document.

	@param docno The internal Lucene document ID
    */
   void showCoef(int docno) throws IOException {

	//long utc = reader.getUniqueTermCount();
	//System.out.println("Index has "+utc +" unique terms");

	IndexReader[] surs = reader.getSequentialSubReaders();
	for(IndexReader sur: surs) {	    
	    long utc = sur.getUniqueTermCount();
	    System.out.println("Subindex has "+utc +" unique terms");
	}

	Document doc = reader.document(docno);
 	for(String name: SearchResults.searchFields) {
	    showField(docno, doc, name);
	}
	showField(docno, doc,ArxivFields.CATEGORY);
    }

    /** Shows the terms and coefficients for a particular field of a
     * particular document.

	@param docno The internal Lucene document ID
	@param doc The Lucen document object
	@param name The field name
    */
    void showField(int docno,	Document doc, String name)throws IOException  {
	Fieldable f = doc.getFieldable(name);
	System.out.println("["+name+"]="+f);
	TermFreqVector tfv=reader.getTermFreqVector(docno, name);
	if (tfv==null) {
	    System.out.println("--No terms--");
	    return;
	}
	System.out.println("--Terms--");
	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();
	for(int i=0; i<terms.length; i++) {
	    Term term = new Term(name, terms[i]);
	    System.out.println(" " + terms[i] + " : " + freqs[i] + "; df=" +reader.docFreq(term) );
	}
    }

  /** The concise version of {@link #showCoef(String)}; used for
	producing CSV output
     */
    void showCoef2(String aid) throws IOException {
	int docno = find(aid);
	Document doc = reader.document(docno);
	showField2(aid, docno, doc,ArxivFields.CATEGORY);
 	for(String name: SearchResults.searchFields) {
	    showField2(aid, docno, doc, name);
	}
    }
    
    static void showFieldHeaders2() {
	System.out.println("#ArxivID,FieldName,Term,DF,TF");
    }


    /** The concise version of {@link #showField(int, doc, name)}; used for
	producing CSV output
     */
    void showField2(String aid, int docno, Document doc, String name)throws IOException  {
	Fieldable f = doc.getFieldable(name);
	//System.out.println("["+name+"]="+f);
	TermFreqVector tfv=reader.getTermFreqVector(docno, name);
	if (tfv==null) {
	    //System.out.println("--No terms--");
	    return;
	}
	//System.out.println("--Terms--");
	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();
	String q = "" + '"';
	for(int i=0; i<terms.length; i++) {
	    Term term = new Term(name, terms[i]);
	    System.out.println(q + aid + q + "," +
			       q + name + q +"," +
			       q + terms[i] + q + "," + 
			       freqs[i] + "," +
			       reader.docFreq(term) );
	}
    }

}