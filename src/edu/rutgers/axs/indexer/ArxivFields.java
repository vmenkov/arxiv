package edu.rutgers.axs.indexer;

/** Names of fields we use in the Lucene index.

    <p>The Lucene index has been originally designed to only store
    ArXiv documents.  Since 2014-10, we also use it for documents
    uploaded by users during registration (via the "Toronto System").
    Unlike ArXiv docs, the user-uploaded documents do not have the PAPER
    field; they have UPLOAD_USER and UPLOAD_FILE fields instead. 
    All of the content of these 
 */
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

    final static public String 
	ARTICLE_LENGTH = "articleLength";

    /** When this article body was indexed.
     */
    final static public String DATE_INDEXED="dateIndexed";
    /** The date when this article was first imported into my.arxiv's
	Lucene store. If the document is re-imported, the original
	date is kept.*/
    final static public String DATE_FIRST_MY="dateFirstMy";

    /** User name for user-uploaded ("Toronto") documents */
    final static public String UPLOAD_USER= "uploadUser";
    /** File name, without extension (e.g. "foo" for "foo.pdf") for
	user-uploaded ("Toronto") documents */
    final static public String UPLOAD_FILE = "uploadFile";
}

