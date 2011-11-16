package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import java.util.regex.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;


 /** This servlet saves any submitted updates to the relevant
  * Respondent or PhoneCall entry, and redirects to the appropriate JSP page
 */
public class QueryServlet extends HttpServlet {

    final static public String QUERY="query", FORMAT="format", 
	LANGUAGE="language", TO_FILE="to_file", MAX_RESULT = "maxResult",
	HEADER="header";
    
    public static enum Language {
	JPQL, SQL;
    };

    public static enum Format {
	HTML,   CSV;
    };

    public static enum ToFile {
	@EA(alt="View in browser") Browser,  
	    @EA(alt="Download") Download;
	boolean toFile() {
	    return this==Download;
	}
    };


    private void addToRow(StringBuffer b, String name , boolean html) {
	if (!html && b.length()>0) b.append(",");
	if (html) b.append("<th>");
	b.append(name);
	if (html) b.append("</th>");
    }

    protected String makeHeader1(String[] colNames, boolean html) {
	StringBuffer b= new StringBuffer();
	for(int j=0; j<colNames.length; j++) {
	    addToRow(b, colNames[j], html);			
	}
	return html? "<tr>"+b+"</tr>" : b.toString();
    }


    protected String makeHeader2(String[] colNames, Object rowData, boolean html) {
	StringBuffer b= new StringBuffer();

	Object[] arr =  (rowData instanceof Object[]) ?
	    (Object[]) rowData : 
	    new Object[] {rowData};

	for(int j=0; j<colNames.length; j++) {
	    if (j<arr.length && arr[j] instanceof OurTable) {
		// add header names
		String fnames[] = Reflect.getNames(arr[j].getClass());
		for(String x: fnames) {
		    addToRow(b, x, html);
		}
	    } else {
		// add the extracted name from the SQL text
		addToRow(b, colNames[j], html);
	    }			
	}
	return html? "<tr>"+b+"</tr>" : b.toString();
    }



    public void	doPost(HttpServletRequest request,HttpServletResponse response) {
	EntityManager em = null;

	try {
	    String query = request.getParameter(QUERY);
	    if (query==null) throw new WebException("No quuery supplied");
	    query = query.trim();
	    if (query.equals("")) throw new WebException("Empty query");

	    Format format = (Format)Tools.getEnum(request, Format.class, FORMAT, Format.HTML);
	    boolean html = (format==Format.HTML);
	    
	    Language language = (Language)Tools.getEnum(request, Language.class, LANGUAGE, Language.JPQL);
	    boolean jpql = language ==Language.JPQL;

	    boolean toFile = ((ToFile)Tools.getEnum(request, ToFile.class, TO_FILE, ToFile.Browser)).toFile();

	    boolean header = Tools.getBoolean(request, HEADER, false);

	    int maxResult = (int)Tools.getLong(request, MAX_RESULT, -1);
   
	    String p = html? "<p>":"";
	    String ep = html? "</p>":"";


	    SessionData sd =  SessionData.getSessionData(request);
	    em = sd.getEM();

	    String[] colNames = extractColumnNames(query);

	    Query q = jpql? em.createQuery(query) :
		em.createNativeQuery(query);

	    if (maxResult>0) q.setMaxResults(maxResult);

	    List list = q.getResultList();

	    response.setContentType(html? "text/html" :
				    toFile? "text/csv" : "text/plain");

	    if (toFile) {
		String f = html? "query-results.html" : "query-results.csv";
		response.setHeader("Content-Disposition",
				   "attachment; filename=" + f);
	    }


	    OutputStream ostream = response.getOutputStream();
	    PrintWriter out = new PrintWriter(ostream);


	    if (html) {
		out.println("<html>");
		out.println("<head><title>Query results</title></head>");
		out.println("<body>");
		out.println(p + language +" query:<br>");
		out.println(query);
		out.println(ep);
		out.print("<p>Max number of result rows: ");
		if (maxResult>0)  out.print(maxResult);
		else   out.print("All");
		out.println(ep);
	    }


	    if (html) out.println("<TABLE border=1>");
   
	    int cnt=0;
	    for (Object  m : list) {
		if (cnt==0 && header) {
		    out.println(makeHeader2(colNames, m, html));
		}
		if (html) out.println("<tr>");
		String colSep = html? "" : ",";
		if (m instanceof Object[]) {
		    int j=0;
		    for(Object o: (Object[]) m) {
			if (j>0) out.print(colSep);
			printObject(out, html, o);
			j++;
		    }
		} else {
		    printObject(out, html, m);
		}

		if (html) out.println("</tr>");
		else out.println("");

		cnt++;
	    }

	    if (html) out.println("</TABLE>");

	    if (cnt==0) {
		out.println(p + "No results" + ep);	
	    }

	    if (html) out.println("</body></html>");

	    out.flush();
	    ostream.flush();
	    ostream.close();


	    /*
	} catch (IllegalInputException e) {
	    try {

		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
				   "illegal input: " + e.getMessage() + 
				   ". Please go back, correct the appropriate field, and resubmit.");		
	    } catch(IOException ex) {};
	    */
	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in SLS: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	    if (em!=null) {
		em.close();
	    }
	}
    }

    /** Prints out a single result of a SELECT */
    protected static void printObject(PrintWriter out, boolean html, Object q) {
	if (q instanceof OurTable) {
	    out.print( html? Reflect.htmlRow(q, false):  Reflect.csvRow(q, ""));
	} else {
	    if (html) out.print("<td>");
	    out.print(  Reflect.formatAsString(q, html? "": "\""));
	    if (html) out.print("</td>");
	}
	
    }

    public static final String mkFormatSelector() {
	return EntryForms.mkRadioSet( FORMAT, Format.class);
    }

    public static final String mkLanguageSelector() {
	return EntryForms.mkRadioSet( LANGUAGE, Language.class);
    }

    public static final String mkDownloadSelector() {
	return EntryForms.mkRadioSet( TO_FILE, ToFile.class);
    }


    public static final String mkHeadBox() {
	return Tools.checkbox(HEADER, "true", "Show column headers<br>(This does not work with the * syntax in SQL)", true);
    }

    /** Save the context and reset the buffer */
    private static String saveB(StringBuffer b) {
	if (b.length()==0) return "EMPTY";
	else {
	    String x = b.toString().trim();
	    b.setLength(0);
	    return x;
	}
    }

    /** split by commas, with a primitive attempt to take care of
     * nested parens */
    private static String[] splitList(String s) {
	Vector<String> v = new 	Vector<String>();
	int depth = 0;
	StringBuffer b = new StringBuffer();
	for(int j=0; j<s.length(); j++ ) {
	    char c = s.charAt(j);
	    if (c == '(') {
		depth ++;
	    } else if (c == ')') {
		depth --;
	    } else if (c == ',' && depth == 0) {
		v.add(saveB(b));
		continue;
	    } else if (c == '\r' || c== '\n' ) {
		c = ' ';
	    }
	    b.append(c);
	}
	v.add(saveB(b));
	return v.toArray(new String[0]);
    }

    /** A remarkably cludgy attempt to extract column names from the
     * query text. We resort to this because JPA does not seem to
     * provide access to anything like ResultSetMetaData in plain JDBC!
     */
    protected static String[] extractColumnNames(String query) {
	query=query.replaceAll("\n", " ").replaceAll("\r", " ");    
	//Pattern.compile(regex).matcher(str).replaceAll(repl)

	Pattern p1 = Pattern.compile("\\s*SELECT\\s+(.*\\S)\\s+FROM\\b.*", 
				     Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	Matcher m = p1.matcher(query);
	if (!m.matches()) {
	    Logging.warning("Could not find match the query to 'SELECT ... FROM ... '. Query: " + query);
	    return new String[0];
	}
	String list[]  = splitList( m.group(1));

	Pattern p2 = Pattern.compile(".*[A-Z0-9_\\)]\\s+([A-Z0-9_]+)", 
				     Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);


	for(int i=0; i<list.length; i++) {
	    // isolate the name part (the last token), if possible
	    m = p2.matcher(list[i]);
	    if (m.matches()) {
		Logging.info("("+i+") extract [" + m.group(1) + "] from token ["+list[i]+"]");
		list[i] = m.group(1);

	    } else {
		Logging.info("("+i+") use complete token ["+list[i]+"]");
	    }
	}
	return list;
    }


}
