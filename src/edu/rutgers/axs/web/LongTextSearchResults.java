package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.Logging;

/** Our interface for Lucene searches: searching article abstracts,
    matching to a long query (another article's abstract). The purpose
    is finding articles whose abstracts are similar to that of a given
    article. We use this in Session-Based Recommendations.  */
public class LongTextSearchResults extends TextSearchResults {

    /** We only search in these fields. */
    private static final String[] fields = { ArxivFields.TITLE, 
					     ArxivFields.ABSTRACT};

    /**Parses the user's text query, creates a Lucene query based on it,
       and obtains the results.

       <p>
       As we split query into terms, we preserve all valid Unicode letters
       (\p{L}) and decimal digits (\p{Nd}). For details on Unicode character 
       categories, see http://www.unicode.org/reports/tr18/

       <p>Duplicate words are removed from the query.

       <p> Before feeding the query to Lucene, we strip from the query
       words that occur in StandardAnalyzer.STOP_WORDS_SET, because
       those aren't stored in our Lucene index.

       @param query Text that the user typed into the Search box
    */
    public LongTextSearchResults(IndexSearcher searcher, String query,  int maxlen) 
	throws Exception {
	
	query=query.trim();
	
	// ":", "-", "." and "*" are allowed in terms, for later processing
	//String terms[]= query.split("[^\\p{L}\\p{Nd}_:\\.\\*\\-]+");

	// no longer
	String terms[]= query.split("[^\\p{L}\\p{Nd}]+");
	terms = dropStopWords(terms);

	BooleanQuery q = new BooleanQuery();
	
	// each term must occur... somewhere
	int tcnt=0;
	HashSet<String> h = new HashSet<String>();
	for(String t: terms) {
	    t=t.trim();
	    if (t.length()==0) continue;
	    else if (h.contains(t)) continue; 
	    else h.add(t);
	    org.apache.lucene.search.Query zq= mkWordClause(t, fields);
	    if (zq == null) continue;
	    q.add( zq,  BooleanClause.Occur.SHOULD);
	    tcnt++;
	}
	if (tcnt==0) throw new WebException("Empty query");
	
	numdocs = searcher.getIndexReader().numDocs();
	//System.out.println("index has "+numdocs +" documents");
	Logging.info("LongTextSearchResults: query=" + q);

	reportedLuceneQuery=q;
	TopDocs 	 top = searcher.search(q, maxlen + 1);
	scoreDocs = top.scoreDocs;
	Logging.info("LongTextSearchResults: " +scoreDocs.length + " results");
	mayHaveBeenTruncated= (scoreDocs.length >= maxlen+1);
    }

}
