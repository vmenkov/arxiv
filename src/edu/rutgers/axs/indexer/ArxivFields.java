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
	CATEGORY="category",
	DATE = "date";
    /** When this article body was indexed.
     */
    final static public String DATE_INDEXED="dateIndexed";
    /** The date when this article was first imported into my.arxiv's
	Lucene store. If the document is re-imported, the original
	date is kept.*/
    final static public String DATE_FIRST_MY="dateFirstMy";

}

