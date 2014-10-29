package edu.rutgers.axs.search;

import java.io.*;
import java.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.web.SearchResults;
import edu.rutgers.axs.web.Search;

/** Our interface for Lucene searches: Full-text search */
public class TextSearchResults extends SearchResults {

    /** Does nothing. Only is used (implicitly) by the derived classes.  */
    protected TextSearchResults() {} 

    /**Parses the user's text query, creates a Lucene query based on it,
       and obtains the results.

       <p>
       As we split query into terms, we preserve all valid Unicode letters
       (\p{L}) and decimal digits (\p{Nd}). For details on Unicode character 
       categories, see http://www.unicode.org/reports/tr18/

       <p> Before feeding the query to Lucene, we strip from the query
       words that occur in StandardAnalyzer.STOP_WORDS_SET, because
       those aren't stored in our Lucene index.

       @param query Text that the user typed into the Search box
    */
    public TextSearchResults(IndexSearcher searcher, String query,  int maxlen) 
	throws Exception {
	
	query=query.trim();
	boolean isPhrase= query.length()>=2 &&
	    query.startsWith("\"") && query.endsWith("\"");
	if (isPhrase) query = query.substring(1, query.length()-1);
	
	// ":", "-", "." and "*" are allowed in terms, for later processing
	String terms[]= query.split("[^\\p{L}\\p{Nd}_:\\.\\*\\-]+");
	terms = dropStopWords(terms);

	BooleanQuery q = new BooleanQuery();
	
	if (isPhrase) {
	    // the entire phrase must occur... somewhere
	    for(String f: searchFields) {
		PhraseQuery ph = new PhraseQuery();
		int tcnt=0;
		for(String t: terms) {
		    if (t.trim().length()==0) continue;
		    ph.add( new Term(f, t.toLowerCase()));
		    tcnt++;
		}
		q.add( ph, BooleanClause.Occur.SHOULD);
	    }
	} else {
	    // each term must occur... somewhere
	    int tcnt=0;
	    for(String t: terms) {
		if (t.trim().length()==0) continue;
		org.apache.lucene.search.Query zq= mkWordClause(t,searchFields);
		if (zq == null) continue;
		q.add( zq,  BooleanClause.Occur.MUST);
		tcnt++;
	    }
	    if (tcnt==0) throw new IllegalArgumentException("Empty query");
	}	

	// to ensure that only ArXiv docs (and not user-uploaded docs)
	// are retrieved (2014-10-29)
	q.add( Queries.hasAidQuery(), BooleanClause.Occur.MUST);
	
	numdocs = searcher.getIndexReader().numDocs();
	//System.out.println("index has "+numdocs +" documents");
	
	reportedLuceneQuery=q;
	TopDocs 	 top = searcher.search(q, maxlen + 1);
	scoreDocs = top.scoreDocs;
	mayHaveBeenTruncated= (scoreDocs.length >= maxlen+1);
    }

    /** Parses a query entered by a user via the search.jsp web UI */
    static org.apache.lucene.search.Query mkWordClause(String t, String [] onlySearchInTheseFields) {
	String f0 = ArxivFields.CATEGORY;
	String prefix = f0 + ":";
	if (t.startsWith(prefix)) {
	    String w = t.substring(prefix.length());
	    return Queries.mkTermOrPrefixQuery(f0, w);
	}
	
	for(String f: searchFields) {
	    prefix = f + ":";
	    if (t.startsWith(prefix)) {
		String w = t.substring(prefix.length());
		return Queries.mkTermOrPrefixQuery(f, w.toLowerCase());
	    }
	}
	
	f0 = Search.DAYS;
	prefix = f0 + ":";
	if (t.startsWith(prefix)) {
	    String s = t.substring(prefix.length());
	    try {
		int  days = Integer.parseInt(s);
		Date since = daysAgo(days);
		return Queries.mkSinceDateQuery(since);
	    } catch (Exception ex) { return null; }
	} else {
	    BooleanQuery b = new BooleanQuery(); 
	    for(String f:  onlySearchInTheseFields) {
		b.add(  Queries.mkTermOrPrefixQuery(f, t.toLowerCase()),
			BooleanClause.Occur.SHOULD);		
	    }
	    return b;
	}
    }

    /** The set of words that are known to be never stored in our Lucene index,
	because they are the stop words of StandardAnalyzer.
    */
    private static HashSet<String> stopWordsSet = fillStopWordsSet();

    /** Converts StandardAnalyzer's stop word list (where words are
	represented as arrays of chars, as it happens) into a more
	convenient set of Strings.
     */
    private static HashSet<String> fillStopWordsSet() {
	HashSet<String> q = new HashSet <String>();
	for(Object o: org.apache.lucene.analysis.standard.StandardAnalyzer.STOP_WORDS_SET) {
	    q.add(new String( (char[]) o));
	}
	return q;
    }

    String[]  dropStopWords(String [] terms) {
	Vector<String> v=new Vector<String>();
	int rmCnt=0;
	for(String t: terms) {
	    if (stopWordsSet.contains(t)) rmCnt++;
	    else v.add(t);
	}
	return (rmCnt>0) ? v.toArray(new String[v.size()]) : terms;
    }



}
