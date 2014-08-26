package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.URLEncoder;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.sb.SBStats;

/** Web interface to SBStats
*/
public class SBStatsServlet extends HttpServlet {

  public void service(HttpServletRequest request,HttpServletResponse response) {

	EntityManager em = null;

	try {
	
    	    String uname = request.getParameter("user_name");
	    if (uname==null) throw new WebException("user_name not specified");

	    QueryServlet.Format format = (QueryServlet.Format)Tools.getEnum(request, QueryServlet.Format.class, QueryServlet.FORMAT, QueryServlet.Format.HTML);
	    boolean html = (format==QueryServlet.Format.HTML);

	    boolean toFile = ((QueryServlet.ToFile)Tools.getEnum(request, QueryServlet.ToFile.class, QueryServlet.TO_FILE, QueryServlet.ToFile.Browser)).toFile();

	    SessionData sd =  SessionData.getSessionData(request);

	    if (!sd.isAuthorized( request)) {
		String xsp=URLEncoder.encode(request.getServletPath(), "UTF-8");
		xsp = "/tools"; // LoginServlet's forwarding does not support POST

		String redirect =
		    request.getContextPath() + "/login2.jsp?sp=" + xsp;
		response.sendRedirect(redirect);
		return;
	    }

	    em = sd.getEM();

	    response.setContentType(html? "text/html" :
				    toFile? "text/csv" : "text/plain");
	    response.setCharacterEncoding("UTF-8");
	    if (toFile) {
		// suggest the destination file name to the user's web browser
		String f = html? "query-results.html" : "query-results.csv";
		response.setHeader("Content-Disposition",
				   "attachment; filename=" + f);
	    }


	    OutputStream ostream = response.getOutputStream();
	    PrintStream out = new PrintStream(ostream);

	    String p = html? "<p>":"";
	    String ep = html? "</p>":"";
	    String h2 = html? "<h2>":"";
	    String eh2 = html? "</h2>":"";

	    if (html) {
		out.println("<html>");
		out.println("<head><title>SB stats for user "+uname+"</title>");
		out.println("<link rel=\"icon\" type=\"image/x-icon\" href=\"../favicon.ico\"/>");
		out.println("</head>");
		out.println("<body>");
	    }

	    out.println("<h1>SB stats for user "+uname+"</h1>");

	    Vector<Long[]> v= SBStats.listSessionsForUser( em, 0, uname);
	    for(Long[] z : v) {
		out.println(h2 + "Session " +z[0]+", " + z[1] + " list(s) presented"+eh2);
		long sqlSessionID=z[0];
		out.println("<pre>");
		SBStats.sessionStats(out, em, sqlSessionID, html);
		out.println("</pre>");
	    }

	    if (html) out.println("</body></html>");

	    
	    out.flush();
	    out.close();


	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in SBStatsServlet: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	    if (em!=null) {
		em.close();
	    }
	}


  }

}