package edu.rutgers.axs.indexer;

import java.io.*;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.*;

import edu.cornell.cs.osmot.options.Options;


public class Common {
    
    static final Version LuceneVersion = Version.LUCENE_33; 

    /** Creates a new Lucene reader */
    static public IndexReader newReader()  throws IOException {
	Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	IndexReader reader =  IndexReader.open( indexDirectory);            
	return reader;
    }


}