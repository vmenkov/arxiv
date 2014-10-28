package edu.rutgers.axs.search;

import java.io.*;
import java.util.*;
import java.text.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.ArxivFields;

/** Useful Lucene queries for working with My.ArXiv's Lucene data store
 */
public class Queries {
    /** Generates eitehr a term query (usually) or a prefix query (if there is
	an asterisk in the query string)
     */
    static org.apache.lucene.search.Query mkTermOrPrefixQuery(String field, String t) {
	int pos = t.indexOf('*');
	return (pos<0) ? 
	    new TermQuery(new Term(field, t)) :
	    new PrefixQuery(new Term(field, t.substring(0,pos))); 
	
    }

    /** Creates a date range clause. This was originally based on
	ArxivFields.DATE, but since 2012-11 we're phasing in 
     	ArxivFields.DATE_FIRST_MY
     */
    public static  org.apache.lucene.search.Query mkSinceDateQuery(Date since) {
	return mkDateRangeQuery(since, null);
    }

    public static  org.apache.lucene.search.Query mkDateRangeQuery(Date since, Date toDate) {
	String lower = 	DateTools.timeToString(since.getTime(), DateTools.Resolution.MINUTE);
	String upper = 	(toDate==null)? null: DateTools.timeToString(toDate.getTime(), DateTools.Resolution.MINUTE);
	//System.out.println("date range: from " + lower);
	//	return new TermRangeQuery(ArxivFields.DATE,lower,null,true,false);

	BooleanQuery bq = new BooleanQuery();
	bq.setMinimumNumberShouldMatch(1);
	String fields[] = {ArxivFields.DATE, ArxivFields.DATE_FIRST_MY};
	for( String field: fields) {
	    bq.add( new TermRangeQuery(field ,lower,upper,true,false),
		   BooleanClause.Occur.SHOULD );
	}
	return bq;
    }


    private static boolean isAnAndQuery(org.apache.lucene.search.Query q) {
	if (!(q instanceof BooleanQuery)) return false;
	for(BooleanClause c: ((BooleanQuery)q).clauses()) {
	    if (c.getOccur()!= BooleanClause.Occur.MUST) return false;
	}
	return true;
    }


    public static BooleanQuery andQuery( org.apache.lucene.search.Query q1, 
				  org.apache.lucene.search.Query q2) {
	if (isAnAndQuery(q1)) {
	    BooleanQuery b = (BooleanQuery)q1;
	    b.add( q2,  BooleanClause.Occur.MUST);
	    return b;
	} else if  (isAnAndQuery(q2)) {
	    BooleanQuery b = (BooleanQuery)q2;
	    b.add( q1,  BooleanClause.Occur.MUST);
	    return b;
	} else {
	    BooleanQuery b = new BooleanQuery();
	    b.add( q1,  BooleanClause.Occur.MUST);
	    b.add( q2,  BooleanClause.Occur.MUST);
	    return b;
	}
    }

    /** Creates a query that returns true on any ArXiv document (that is
	a document that has an ArXiv ID, i.e. the "paper" field). This can be
	used to distinguish ArXiv documents from user-uploaded documents.
     */
    static TermRangeQuery hasAidQuery() {
	return new TermRangeQuery(ArxivFields.PAPER,null,null,true,true);
    }

}