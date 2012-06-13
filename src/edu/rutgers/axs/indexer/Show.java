package edu.rutgers.axs.indexer;

import java.util.Collection;
import java.util.Iterator;

import java.util.Date;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;


import org.apache.lucene.document.*;
import org.apache.lucene.analysis.Analyzer;

//import org.apache.lucene.analysis.standard.StandardAnalyzer;
/*
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
*/

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import edu.rutgers.axs.web.Search;


/** Used to look up article information in our Lucene data store (a
 * delayed copy of sorts of what arxiv.org has)
 */
class Show {

    private IndexReader reader;
    
    public Show()  throws IOException {
	Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	reader =  IndexReader.open( indexDirectory);     
    }

    /** Finds Lucene document (its internal integer id) by Arxiv id */
    private int find(String id) throws IOException{

	//IndexSearcher s = new IndexSearcher( indexDirectory);
	IndexSearcher s = new IndexSearcher( reader );
	TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, id));
	System.out.println("query=("+tq+")");
	TopDocs 	 top = s.search(tq, 1);
	ScoreDoc[] 	scoreDocs = top.scoreDocs;
	if (scoreDocs.length < 1) {
	    System.out.println("No document found with paper="+id);
	    throw new IOException("No document found with paper="+id);
	}
	return scoreDocs[0].doc;
	/*
	Document doc = s.doc(scoreDocs[0].doc);
	if (doc==null) {
	    System.out.println("s.doc() failed");
	    throw new IOException("s.doc() failed");
	} else {
	    return doc;
	}
	*/
    }


    /**
       @param id Article id in the Arxiv system
     */
    void show(String id) throws IOException {
	int docno = find(id);
	Document doc = reader.document(docno);
	System.out.println("Doc no.=" + docno);
	System.out.println("dateIndexed=" + doc.get(ArxivFields.DATE_INDEXED));
	System.out.println("Document=" + doc);
    }

    /** Note: using 
	long utc = reader.getUniqueTermCount();
	won't do, as we get an java.lang.UnsupportedOperationException: 
	"this reader does not implement getUniqueTermCount()".
	This is why we use subreaders.
    */
    void showCoef(String id) throws IOException {

	//long utc = reader.getUniqueTermCount();
	//System.out.println("Index has "+utc +" unique terms");

	IndexReader[] surs = reader.getSequentialSubReaders();
	for(IndexReader sur: surs) {	    
	    long utc = sur.getUniqueTermCount();
	    System.out.println("Subindex has "+utc +" unique terms");
	}

	int docno = find(id);
	Document doc = reader.document(docno);
	System.out.println("Document info for id=" + id +", doc no.=" + docno);
 	for(String name: Search.searchFields) {
	    showField(docno, doc, name);
	}
	showField(docno, doc,ArxivFields.CATEGORY);
    }

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
    
}