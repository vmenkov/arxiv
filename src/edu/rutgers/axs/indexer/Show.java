package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
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

import java.util.Collection;
import java.util.Iterator;

import java.util.Date;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;


/** Used to look up article information in our Lucene data store (a
 * delayed copy of sorts of what arxiv.org has)
 */
class Show {

    // Where our index lives.
    private Directory indexDirectory;
    
    
    public Show()  throws IOException {
	indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
    }

    /**
       @param id Article id in the Arxiv system
     */
    void show(String id) throws IOException{

	IndexSearcher s = new IndexSearcher( indexDirectory);
	TermQuery tq = new TermQuery(new Term("paper", id));
	System.out.println("query=("+tq+")");
	TopDocs 	 top = s.search(tq, 1);
	ScoreDoc[] 	scoreDocs = top.scoreDocs;
	if (scoreDocs.length < 1) {
	    System.out.println("No document found with paper="+id);
	    return;
	}
	Document doc = s.doc(scoreDocs[0].doc);
	if (doc==null) {
	    System.out.println("s.doc() failed");
	    return;
	}
	System.out.println("Document=" + doc);
    }


}