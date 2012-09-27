package edu.rutgers.axs.web;

import java.net.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.rutgers.axs.indexer.*;
import edu.rutgers.axs.sql.*;
//import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.ParseConfig;

/** Our interface for Lucene searches: Full-text search */
public class TextSearchResults extends SearchResults {
	/**@param query Text that the user typed into the Search box
	   @param startat How many top search results to skip (0, 25, ...)
	 */
    TextSearchResults(IndexSearcher searcher, String query,  int maxlen) 
	throws Exception {
	
	query=query.trim();
	boolean isPhrase= query.length()>=2 &&
	    query.startsWith("\"") && query.endsWith("\"");
	if (isPhrase) query = query.substring(1, query.length()-1);
	
	// ":", "-", "." and "*" are allowed in terms, for later processing
	String terms[]= query.split("[^a-zA-Z0-9_:\\.\\*\\-]+");
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
		org.apache.lucene.search.Query zq= mkWordClause(t);
		if (zq == null) continue;
		q.add( zq,  BooleanClause.Occur.MUST);
		tcnt++;
	    }
	    if (tcnt==0) throw new WebException("Empty query");
	}	
	
	numdocs = searcher.getIndexReader().numDocs();
	System.out.println("index has "+numdocs +" documents");
	
	reportedLuceneQuery=q;
	TopDocs 	 top = searcher.search(q, maxlen + 1);
	scoreDocs = top.scoreDocs;
	mayHaveBeenTruncated= (scoreDocs.length >= maxlen+1);
    

    }


    static private org.apache.lucene.search.Query mkWordClause(String t) {
	
	String f0 = ArxivFields.CATEGORY;
	String prefix = f0 + ":";
	if (t.startsWith(prefix)) {
	    String w = t.substring(prefix.length());
	    return mkTermOrPrefixQuery(f0, w);
	}
	
	for(String f: searchFields) {
	    prefix = f + ":";
	    if (t.startsWith(prefix)) {
		String w = t.substring(prefix.length());
		return mkTermOrPrefixQuery(f, w.toLowerCase());
		}
	}
	
	f0 = Search.DAYS;
	prefix = f0 + ":";
	if (t.startsWith(prefix)) {
	    String s = t.substring(prefix.length());
	    try {
		int  days = Integer.parseInt(s);
		Date since = daysAgo(days);
		return mkSinceDateQuery(since);
	    } catch (Exception ex) { return null; }
	} else {
	    BooleanQuery b = new BooleanQuery(); 
	    for(String f: searchFields) {
		b.add(  mkTermOrPrefixQuery(f, t.toLowerCase()),
			BooleanClause.Occur.SHOULD);		
	    }
	    return b;
	}
    }
}
