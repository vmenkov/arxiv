package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.ParseConfig;

/** Category (subject) search; used to retrieve articles in particular
    categories. */
public  class SubjectSearchResults extends SearchResults {

    /**Performs a Lucene search. Creates an SearchResults object
       that's a wrapper around the results array produced by Lucene (without
       My.ArXiv's own re-ordering - that is to be done separately).

       @param cats Array of categories.
    */
    public SubjectSearchResults(IndexSearcher searcher, String[] cats, 
				Date since, int maxlen) 
    throws IOException {
	this( searcher, cats, since, null, maxlen);
    }

    /**Performs a Lucene search. Creates an SearchResults object
       that's a wrapper around the results array produced by Lucene (without
       My.ArXiv's own re-ordering - that is to be done separately).

       @param cats Array of categories.
       @param since Date range start (may be null if n/a)
       @param toDate Date range end (usually null). 
    */
    SubjectSearchResults(IndexSearcher searcher, String[] cats, 	
				Date since, Date toDate, int maxlen) 
    throws IOException {

	org.apache.lucene.search.Query q;
	if (cats.length<1) {
	    reportedLuceneQuery=null;
	    scoreDocs = new ScoreDoc[0];
	    Logging.warning("No categories specified for cat search!");
	    return;
	} else if (cats.length==1) {
	    q =   mkTermOrPrefixQuery(ArxivFields.CATEGORY, cats[0]);
	} else {
	    BooleanQuery bq = new BooleanQuery();
	    for(String t: cats) {
		t = t.trim();
		if (t.equals("")) continue;
		bq.add(  mkTermOrPrefixQuery(ArxivFields.CATEGORY, t),
			 BooleanClause.Occur.SHOULD);
	    }
	    q = bq;
	}
	
	if ( since!=null) {
	    q = andQuery( q, mkDateRangeQuery(since,toDate));
	} else if (toDate!=null) {
	    throw new IllegalArgumentException("toDate can't be set without since");
	}
	
	Logging.info("Lucene query: " +q);
	
	numdocs = searcher.getIndexReader().numDocs() ;
	
	reportedLuceneQuery=q;
	TopDocs 	 top = searcher.search(q, maxlen+1);
	scoreDocs = top.scoreDocs;
	mayHaveBeenTruncated= (scoreDocs.length >= maxlen+1);
	Logging.info("index has "+numdocs +" documents; |scoreDocs|= "+ scoreDocs.length);
	
    }

    public static SubjectSearchResults 
	orderedSearch(IndexSearcher searcher, User actor, Date since,
		      int maxlen) throws IOException {
	return 	orderedSearch( searcher,  actor, since, null,maxlen);
    }

    public static SubjectSearchResults 
	orderedSearch(IndexSearcher searcher, User actor, Date since,
		      Date toDate,
		      int maxlen) throws IOException {

	String[] cats = actor.getCats().toArray(new String[0]);
	return 	orderedSearch( searcher,  cats, since, toDate, maxlen);
    }
   

    public static SubjectSearchResults 
	orderedSearch(IndexSearcher searcher, String[] cats, Date since,
		      Date toDate,
		      int maxlen) throws IOException {

	SubjectSearchResults sr = new SubjectSearchResults(searcher, cats, since, toDate, maxlen);
	sr.reorderCatSearchResults(searcher.getIndexReader(), cats);
	return sr;
    }


}

 