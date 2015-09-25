package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
//import edu.rutgers.axs.web.*;
//import edu.rutgers.axs.html.*;
//import edu.rutgers.axs.search.*;
//import edu.rutgers.axs.recommender.*;
//import edu.rutgers.axs.ee4.Categorizer;
import edu.rutgers.axs.ee4.DenseDataPoint;
//import edu.rutgers.axs.upload.BackgroundThread;

/** Document exporter (for Chen Bangrui, 2015-09). Exports documents
    described as vectors in a low-dimensional space (words having
    been mapped to word clusters).
 */
public class LowDimDocumentExporter {

  static public void main(String[] argv) throws IOException, java.text.ParseException {
	ParseConfig ht = new ParseConfig();      

	IndexReader reader = Common.newReader();
	IndexSearcher searcher = new IndexSearcher( reader );

	Vocabulary voc = Vocabulary.readVocabulary();

	int ja=0;
	//String cmd = args[ja++];
	//	if (cmd.equals("aids")) {
	for(ArgvIterator it=new ArgvIterator(argv,ja); it.hasNext();){
	    String aid = it.next();
	    //System.out.println("docno=" + docno);
	    int docno = Common.find(searcher, aid);
 
	    //	    Classifier.Article
	    DenseDataPoint p = Classifier.readArticle(docno, voc.L, voc, reader);
	    System.out.print(aid + "\t");
	    p.printFloat(new PrintWriter(System.out));
	    System.out.println();


   
	}


	reader.close();

  }

}
