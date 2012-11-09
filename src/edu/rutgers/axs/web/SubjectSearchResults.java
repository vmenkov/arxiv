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

/** Subject search */
public  class SubjectSearchResults extends SearchResults {
    /**Performs a Lucene search. Creates an SearchResults object
       that's a wrapper around the results array produced by Lucene (without
       My.ArXiv's own re-ordering - that is to be done separately).

       @param cat
       @param startat How many top search results to skip (0, 25, ...)
    */
    public SubjectSearchResults(IndexSearcher searcher, String[] cats, 
				Date rangeStartDate, int maxlen) 
    throws IOException 
{

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

	    if ( rangeStartDate!=null) {
		q = andQuery( q, mkSinceDateQuery(rangeStartDate));
	    }

	    System.out.println("Lucene query: " +q);

	    numdocs = searcher.getIndexReader().numDocs() ;
	    System.out.println("index has "+numdocs +" documents");
	    
	    reportedLuceneQuery=q;
	    TopDocs 	 top = searcher.search(q, maxlen+1);
	    scoreDocs = top.scoreDocs;
	    mayHaveBeenTruncated= (scoreDocs.length >= maxlen+1);

    }

    static SubjectSearchResults 
	orderedSearch(IndexSearcher searcher, User actor, int days,
		      int maxlen) throws Exception {

	String[] cats = actor.getCats().toArray(new String[0]);
	Date since = SearchResults.daysAgo( days );
	SubjectSearchResults sr = new SubjectSearchResults(searcher, cats, since, maxlen);
	sr.reorderCatSearchResults(searcher.getIndexReader(), cats, since);
	return sr;
    }

}

 