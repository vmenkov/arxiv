package edu.rutgers.axs.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.analysis.Analyzer;

import org.apache.lucene.analysis.standard.StandardAnalyzer;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import java.util.*;
import java.util.regex.*;
import java.io.*;

/** A utility class for listing all Arxiv article IDs in the Lucene datastore */
public class IndexList {

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
	    //  System.out.println("subindex has "+utc +" unique terms");
	}
	int n=	r.numDocs() ;
	//	System.out.println("index has "+n +" docs");
	if (max<0) max=n;
	for(int i=0; i<n && i<max; i++){
	    Document d=r.document(i);
	    System.out.println(d.get(ArxivFields.PAPER));
	}
	//	if (max>=0 &&  max<n) System.out.println("    ... etc ...");
    }

    /** Returns the list of all ArXiv article IDs, as a HashSet */
    public HashSet<String> listAsSet() throws IOException{
	return listAsSet(-1);
    }

    public HashSet<String> listAsSet(int max) throws IOException{

	IndexReader r =  IndexReader.open( indexDirectory); 

	IndexReader[] surs = r.getSequentialSubReaders();
	int n=	r.numDocs() ;
	//	System.out.println("index has "+n +" docs");
	if (max<0 || max>n) max=n;
	HashSet<String> s = new HashSet<String>(n);
	for(int i=0; i<max; i++){
	    Document d=r.document(i);	    
	    s.add(d.get(ArxivFields.PAPER));
	}
	return s;
    }


}