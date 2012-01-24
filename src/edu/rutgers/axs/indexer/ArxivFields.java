package edu.rutgers.axs.indexer;

/** Names of fields we use in the Lucene index */
public class ArxivFields {
    /** This field contains arXiv article id */
    final static public String PAPER = "paper";    
    final static public String 
	ARTICLE = "article", // body
	ABSTRACT = "abstract",
	TITLE="title",
	AUTHORS="authors",
	COMMENTS="comments",
	CATEGORY="category";
}