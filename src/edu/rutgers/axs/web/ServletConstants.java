package edu.rutgers.axs.web;

import edu.rutgers.axs.sql.*;


/** This class stores some constants and static methods that are used
    both by servlets and other classes. Originally they were in 
    BaseArxivServlets, but they have been moved here so that they
    can be used in command-line applications without loading Servlet API
    jars.
*/

class ServletConstants {

    /** Artcile ID, in the format used by arxiv.org */
    final static public String ID="id", ACTION="action";

    /** Returns a URL for ArticleServlet */
    static String articleServletUrl(String cp, String id, Action.Op op, 
			ActionSource asrc) {
	return cp + "/ArticleServlet?" +  ID +"="+id + "&"+ ACTION+ "="+op +
	    asrc.toQueryString();
    }

    /** Returns a URL for ArticleServlet, but with no Article ID
	entered. The URL ends in "id=", and the ID will need to be
	appended in JavaScript in browser's runtime */
    /*
    static String articleServletUrlPrefix(String cp, Action.Op op, 
			ActionSource asrc) {
	return cp + "/ArticleServlet?" +   ACTION+ "="+op +
	    asrc.toQueryString()	+    "&"+ 	    ID +"=";
    }
    */


}