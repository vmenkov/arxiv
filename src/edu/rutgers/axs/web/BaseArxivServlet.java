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

    /** May be changed in init(), by means of parameters supplied from web.xml
     */
    String ARXIV_BASE = "http://arxiv.org";

    /** E.g., "/arxiv". It is set in init(); */
    private String cp;
  
    /** This would be a good place to do <pre>
	//cp= context.getContextPath(); 
	</pre>

	except that that method is only available since Servlet API
	2.5 (= Tomcat 6). So we initialize <tt>cp</tt> in reinit() instead.
	Cludgy, eh?
    */
    public void init(ServletConfig config)     throws ServletException {
	super.init(config);
	ServletContext context=config.getServletContext();

	String s  = config.getInitParameter("ArxivBaseURL");
	if (s!=null && !s.equals("")) ARXIV_BASE = s;

	// Alas, this is only available since Servlet API 2.5 (= Tomcat 6);
	//cp= context.getContextPath(); 
    }

    /** Every child servlet must call reinit() from its service()
	method. This is a cludge, set up due to the absence of
	ServletContext.getContextPath() in early versions of the
	Servlet API.
     */
    synchronized void reinit(HttpServletRequest request) {
	cp=  request.getContextPath();	
    }

    String getContextPath() { return cp; }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);
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