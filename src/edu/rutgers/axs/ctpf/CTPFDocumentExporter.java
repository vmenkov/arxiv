package edu.rutgers.axs.ctpf;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import org.json.*;

import edu.rutgers.axs.*;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/** Exporting document information in a format suitable for reading by 
    David Blei's LDA (LATENT DIRICHLET ALLOCATION) application. The
    format looks somewhat similar to that used by Thorsten's
    Multiclass SVM tool, but isn't really the same. 
    
    <p> We only export the terms present in a specified pre-read
    vocabulary.

    <P>The output file format is as outlined in http://www.cs.princeton.edu/~blei/lda-c/readme.txt :
    <blockquote>
    <p>
Under LDA, the words of each document are assumed exchangeable.  Thus,
each document is succinctly represented as a sparse vector of word
counts. The data is a file where each line is of the form:
<pre>
     [M] [term_1]:[count] [term_2]:[count] ...  [term_N]:[count]
</pre>
where [M] is the number of unique terms in the document, and the
[count] associated with each term is how many times that term appeared
in the document.  Note that [term_1] is an integer which indexes the
term; it is not a string.
</p>
    </blockquote>

*/
class CTPFDocumentExporter {

   /** Adds weighted TF values for a specified field of a specified document
	to the list of pairs h.	
     */
    private static void processField(int [] results, IndexReader reader,  CTPFUpdateFit.Vocabulary voc,
			      int docno, Document doc, String name) throws IOException {	
	TermFreqVector tfv=reader.getTermFreqVector(docno, name);
	if (tfv==null)     return; 
	
	int[] freqs=tfv.getTermFrequencies();
	String[] terms=tfv.getTerms();
	for(int i=0; i<terms.length; i++) {
	    String word =  terms[i];
	    if (!voc.containsWord(word)) continue;
	    int pos = voc.word2pos(word);
	    results[pos] += freqs[i];
	}
    }

   /** Fields we're exporting. (Abstract only, as per LC, 2015-05-27) */
    static private final String fields[] =  {
	//	ArxivFields.CATEGORY,
	//ArxivFields.TITLE, 
	//ArxivFields.AUTHORS, 
	ArxivFields.ABSTRACT,
	//	ArxivFields.ARTICLE
    };


    /**
       @param voc The vocabulary lists the "important" terms. Only the term frequency for these terms will be exported; all other terms will be simply ignored.
       @param w The input file for LDA will be written here
       @param itemsW The list of AIDs wil be written here. (There is no place for the in the LDA file). Each document will have a base-1 internal id, which is the same as the line number in this file.
       @param aids List of Arxive document IDs (AIDs) to export
     */
    static void exportAll(CTPFUpdateFit.Vocabulary voc, Vector<String> aids, PrintWriter w, PrintWriter itemsW)  throws IOException {

	IndexReader reader = Common.newReader2();
  	IndexSearcher searcher = new IndexSearcher( reader );	
	int missingCnt=0, cnt=0;

	for( String aid: aids) {
	    int docno = 0;
	    Logging.info("aid="+ aid);
	    try {
		docno =	Common.find(searcher, aid);
	    } catch(IOException ex) {
		missingCnt ++;
		continue;
	    }
	    Document doc = reader.document(docno);
	    int [] results = new int[voc.size()]; // 0-based indexes


	    for(String name: fields) {
		processField( results, reader,  voc,  docno,  doc,  name);
	    }


	    int nnz = 0;
	    for(int v: results) { if (v!=0) nnz++; }

	    w.print("" + nnz);
	    
	    for(int i=0; i< results.length; i++) { 
		int v = results[i];
		if (v!=0) w.print(" " + i + ":" + v); 
	    }
	    w.println();
	    cnt++; // 1-based
	    itemsW.println( cnt +  "\t" + aid);
	}
	
	if (missingCnt>0) {
	    throw new IOException("Out of " + aids.size() + " documents, " + missingCnt + " not found in Lucene. Should not proceed.");
	}
    }
}