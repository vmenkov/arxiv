package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.ArxivFields;
import edu.rutgers.axs.ee4.DenseDataPoint;

/** Represents the content of the article as a DenseDataPoint,
    as well as some additional information about the provenance of 
    the data. The readArticle() methods provide tools for the conversion
    of a Lucene documents to this dense representation, using a pre-loaded
    Voabulary object.
*/
public class ArticleDenseDataPoint extends DenseDataPoint {
    /** True if the vector was based only on the metadata, because
	the article body was not stored in the database */
    final boolean missingBody;
    ArticleDenseDataPoint(double z[], boolean _missingBody) {
	super(z);
	missingBody = _missingBody;
    }

    /** Fields whose content we use to cluster documents */
    final static String fields[] = {
	ArxivFields.TITLE,
	ArxivFields.AUTHORS,
	ArxivFields.ABSTRACT,
	ArxivFields.ARTICLE
    };
    
    /** Reads in all relevant fields of the specified article from
	Lucene, and converts them into a single vector in the
	L-dimensional word2vec word cluster space.

	@param docno Lucen internal doc number
	@param L the dimension of the destination space
	@param voc The vocabulary used to conver text to vectors
     */
    static public ArticleDenseDataPoint readArticle(int docno,  Vocabulary voc, IndexReader reader) throws IOException {
	Document doc = reader.document(docno);
	return readArticle(doc,  voc);
    }

    static ArticleDenseDataPoint readArticle(Document doc,  Vocabulary voc) throws IOException {

	boolean missingBody  = false;
	double[] v = new double[voc.L];
	for(String field: fields) {
	    String s = doc.get(field);
	    if (s==null) {
		if (field.equals(ArxivFields.ARTICLE))missingBody=true;
		continue;
	    }
	    voc.textToVector(s, v);	    
	}
	return new ArticleDenseDataPoint(v, missingBody);
    }

}

