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


 /** The  base class for all our servlets 
 */
public class BaseArxivServlet extends HttpServlet {

    /** Artcile ID, in the format used arxiv.org */
    final static public String ID="id", ACTION="action";

    final static String ARXIV_BASE = "http://arxiv.org";

    /** E.g., "/arxiv". It is set in init(); */
    private String cp;
  
    public void init(ServletConfig config)     throws ServletException {
	super.init(config);
	ServletContext context=config.getServletContext();
	cp= context.getContextPath(); 
    }

    String getContextPath() { return cp; }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	try {

	response.setContentType( "text/plain");

	OutputStream ostream = response.getOutputStream();
	PrintWriter out = new PrintWriter(ostream);

	out.println("getContextPath()=" + request.getContextPath()  );
	out.println("getMethod()=" + request.getMethod()  );
	out.println("getPathInfo()=" + request.getPathInfo()  );
	out.println("getPathTranslated()=" + request.getPathTranslated()  );
	out.println("getQueryString()=" + request.getQueryString()  );
	out.println("getRequestURI()=" + request.getRequestURI()  );
	out.println("getRequestURL()=" + request.getRequestURL()  );
	out.println("getServletPath()=" + request.getServletPath()  );

	out.flush();
	ostream.flush();
	ostream.close();
	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in SLS: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	}
    }

}