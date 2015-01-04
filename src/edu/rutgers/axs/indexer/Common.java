package edu.rutgers.axs.indexer;

import java.io.*;
import java.util.*;

import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import edu.cornell.cs.osmot.options.Options;
import edu.rutgers.axs.search.Queries;

public class Common {
    
    static final Version LuceneVersion = Version.LUCENE_33; 

    /** Creates a new Lucene reader */
    static public IndexReader newReader()  throws IOException {
	Directory indexDirectory =  FSDirectory.open(new File(Options.get("INDEX_DIRECTORY")));
	IndexReader reader =  IndexReader.open( indexDirectory);            
	return reader;
    }

    /** A cludgy way of calling newReader() without having to do an explicit
	exception catch */
    static public IndexReader newReader2()  throws AssertionError {
	try {
	    return newReader();
	} catch(IOException ex) {
	    ex.printStackTrace(System.err);
	    throw new AssertionError("IOException in newReader(): " + ex);
	}
    }

    /** Finds a document by its ArXiv article ID
     @return Lucene internal doc id.
     @throws IOException if no document with the specified ID was  found, or on Lucene errors
    */
    static public int find(IndexSearcher s, String aid) throws IOException {
	int docno = findOrMinus(s, aid);
        if (docno < 0) {
            //System.out.println("No document found with paper="+aid);
            throw new IOException("No document found with paper="+aid);
        } else return docno;
    }

    /** Finds a document by its ArXiv article ID
	@return Lucene internal doc id.
	@throws IOException if no document with the specified ID was  found, or on Lucene errors
     */
    static public int find(IndexReader reader, String aid) throws IOException{
	IndexSearcher s = new IndexSearcher( reader );
	return Common.find(s, aid);
    }


   /** Find a document by article ID, using a given searcher. 
     @return Lucene internal doc id (which is a non-negative number; it may be 0!), or -1 if none found.
     @throws IOException On Lucene errors (the index is not there, etc; this is passed fro IndexSearcher.search)
    */
    static public int findOrMinus(IndexSearcher s, String aid) throws IOException {
	TermQuery tq = new TermQuery(new Term(ArxivFields.PAPER, aid));
	//System.out.println("query=("+tq+")");
	TopDocs 	 top = s.search(tq, 1);
	ScoreDoc[] 	scoreDocs = top.scoreDocs;
	return  (scoreDocs.length < 1) ? -1 : scoreDocs[0].doc;
    }

    /** Add (or subtract) so many days to the given date */
    static public Date plusDays(Date d, int days) {
	long msec = d.getTime() + days * 24L * 3600L * 1000L;
	return new Date(msec);
    }

    /** Creates a query which can be used to find a specific
	user-uploaded document */
    public static BooleanQuery userFileQuery(String user, String file) {
	TermQuery tq1 =  new TermQuery(new Term(ArxivFields.UPLOAD_USER,user));
	TermQuery tq2 =  new TermQuery(new Term(ArxivFields.UPLOAD_FILE,file));
	return Queries.andQuery(tq1, tq2);
    }


    /** Finds a specific uploaded document 
	@return Lucene document number (a non-negative integer), or -1 if none found
     */
    static public int findUserFile(IndexSearcher s, String user, String file) throws IOException{
	Query q = userFileQuery(user,file);
	TopDocs  top = s.search(q, 1);
	ScoreDoc[] scoreDocs = top.scoreDocs;
	return  (scoreDocs.length < 1) ? -1 : scoreDocs[0].doc;
    }

    static public ScoreDoc[] findAllUserFiles(IndexSearcher s, String user) throws IOException{
	Query q = new TermQuery(new Term(ArxivFields.UPLOAD_USER,user));
	final int M = 10000;
	TopDocs  top = s.search(q, M);
	ScoreDoc[] scoreDocs = top.scoreDocs;
	return  scoreDocs;
    }



}