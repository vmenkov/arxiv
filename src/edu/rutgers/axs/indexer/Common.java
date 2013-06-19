package edu.rutgers.axs.indexer;

import java.io.*;
import java.util.*;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.options.Options;


public class Common {
    
    static final Version LuceneVersion = Version.LUCENE_33; 

    /** Creates a new Lucene reader */
    static public IndexReader newReader()  throws IOException {
	Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	IndexReader reader =  IndexReader.open( indexDirectory);            
	return reader;
    }

   /** Find a document by article ID, using a given searcher.
     @return Lucene internal doc id.
     @throws IOException if not found.
    */
    static public int find(IndexSearcher s, String aid) throws IOException {
	TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, aid));
	//System.out.println("query=("+tq+")");
	TopDocs 	 top = s.search(tq, 1);
	ScoreDoc[] 	scoreDocs = top.scoreDocs;
	if (scoreDocs.length < 1) {
	    //System.out.println("No document found with paper="+aid);
	    throw new IOException("No document found with paper="+aid);
	}
	return scoreDocs[0].doc;
    }

    /** Add (or subtract) so many days to the given date */
    static public Date plusDays(Date d, int days) {
	long msec = d.getTime() + days * 24L * 3600L * 1000L;
	return new Date(msec);
    }



}