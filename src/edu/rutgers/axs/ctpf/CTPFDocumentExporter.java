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

/** Exports document information in a format suitable for reading by the
    Multiclass SVM tool. Only exports the terms present in a specified
    pre-read vocabulary.
*/
class DocumentExporter {

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

    /**
       @param w The training file for SVM will be written here
       @param wasg The list of docs described in w will be written here
     */
    void exportAll(CTPFUpdateFit.Vocabulary voc, Vector<String> aids, PrintWriter w)  throws IOException {

	IndexReader reader = Common.newReader2();
  	IndexSearcher searcher = new IndexSearcher( reader );	
	int missingCnt=0;

	for( String aid: aids) {
	    int docno = 0;
	    try {
		docno =	Common.find(searcher, aid);
	    } catch(IOException ex) {
		missingCnt ++;
		continue;
	    }
	    Document doc = reader.document(docno);
	    int [] results = new int[voc.size()];


	    for(String name: fields) {
		processField( results, reader,  voc,  docno,  doc,  name);
	    }

	    int clu = map.map.get(aid).intValue();
	    w.print("" + clu);
	    for(Pair p: pairs) {
		w.print(" " + p.key + ":");
		if (normalize) {
		    w.print(p.val/norm);
		} else {
		    w.print(p.val);
		}
	    }
	    w.println( " # " + aid);
	    if (wasg!=null) {
		wasg.println(aid + "," + clu);
	    }
	}
	System.out.println("Out of " + map.list.size() + " documents, " + missingCnt + " not found in Lucene; ignored due to poor category information, " + ignoreCnt);
    }

}