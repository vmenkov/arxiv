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



class IndexList {

  // Where our index lives.
    private Directory indexDirectory;
    
    
    public IndexList()  throws IOException {
	indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
    }
    
    void list(int max) throws IOException{

	IndexReader r =  IndexReader.open( indexDirectory); 


	IndexReader[] surs = r.getSequentialSubReaders();
	for(IndexReader sur: surs) {	    
	    long utc = sur.getUniqueTermCount();
	    System.out.println("subindex has "+utc +" unique terms");
	}
	int n=	r.numDocs() ;
	System.out.println("index has "+n +" docs");
	if (max<0) max=n;
	for(int i=0; i<n && i<max; i++){
	    Document d=r.document(i);
	    System.out.println(d.get(ArxivFields.PAPER));
	}
	if (max>=0 &&  max<n) System.out.println("    ... etc ...");
    }


}