package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import java.util.regex.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.Version;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.sb.SBRGWorkerCTPF;

//<link rel="icon" type="image/x-icon" href="favicon.ico" />

 /** The  base class for all our servlets 
 */
public class BaseArxivServlet extends HttpServlet {

    /** Artcile ID, in the format used arxiv.org */
    final static public String ID=ServletConstants.ID, 
	ACTION=ServletConstants.ACTION; 

    /** E.g., "/arxiv". It is set in init(); */
    private String cp;
  

    static private final String lock="LOCK";
    static Date startTime = null;
    static long acceptedRequestCnt=0, rejectedRobotRequestCnt=0;
    static long filterServletRequestCnt=0, judgmentServletRequestCnt=0;
    static long rejectedOverloadRequestCnt=0;


    /** This would be a good place to do <pre>
	//cp= context.getContextPath(); 
	</pre>

	except that that method is only available since Servlet API
	2.5 (= Tomcat 6). So we initialize <tt>cp</tt> in reinit() instead.
	Cludgy, eh?
    */
    public void init(ServletConfig config)     throws ServletException {
	super.init(config);

	synchronized(lock) {
	    if (startTime==null) startTime = new Date();
	}

	ServletContext context=config.getServletContext();
	// This is only available since Servlet API 2.5 (= Tomcat 6);
	cp= context.getContextPath(); 
	Logging.info("BaseArxivServlet.init() for servlet with name=" + 
		     getServletName() +", info='"+getServletInfo()+"'. cp=" + cp);
	try {
	    edu.cornell.cs.osmot.options.Options.init(context);
	} catch(IOException ex) {
	    Logging.error("Exception in Options.init(): " + ex);
	}

	// Starts the time-consuming loading thread needed for
	// the CTPF SB recommender, if it has not been started yet.
	// (Note that this can only be started after Options.init()!
	SBRGWorkerCTPF.loadFitIfNeeded();

    }

    /** Every child servlet must call reinit() from its service()
	method. This is a cludge, set up due to the absence of
	ServletContext.getContextPath() in early versions of the
	Servlet API.
     */
    synchronized void reinit(HttpServletRequest request) {
	//cp=  request.getContextPath();	
	acceptedRequestCnt++;	

    }

    String getContextPath() { return cp; }

    public void	service(HttpServletRequest request, HttpServletResponse response
) {
	reinit(request);
	try {
	    
	response.setContentType( "text/plain");

	OutputStream ostream = response.getOutputStream();
	PrintWriter out = new PrintWriter(ostream);

	out.println("My.ArXiv server status report, generated on " + new Date());
	String hline ="-----------------------------";
	out.println(hline);

	out.println("getContextPath()=" + request.getContextPath()  );
	out.println("getMethod()=" + request.getMethod()  );
	out.println("getPathInfo()=" + request.getPathInfo()  );
	out.println("getPathTranslated()=" + request.getPathTranslated()  );
	out.println("getQueryString()=" + request.getQueryString()  );
	out.println("getRequestURI()=" + request.getRequestURI()  );
	out.println("getRequestURL()=" + request.getRequestURL()  );
	out.println("getServletPath()=" + request.getServletPath()  );

	out.println("");
	out.println("My.ArXiv server ver. " + Version.getInfo() + " up and running since " + startTime);
	out.println("Memory use stats: " +  Main.memoryInfo(null, false));
	out.println("");
	out.println("Operation statistics since the last restart:");
	out.println("Accepted requests         : " + acceptedRequestCnt + 
		    " (including: FilterServlet: " +filterServletRequestCnt +
		    ", JudgmentServlet: " +judgmentServletRequestCnt +")");

	out.println("Rejected requests (robots): " +rejectedRobotRequestCnt);
	out.println("Rejected reqs (load cntrl): " +rejectedOverloadRequestCnt);

	out.println("");

	int n1=FilterServlet.rc.recentCount(60);
	int n10=FilterServlet.rc.recentCount(10*60);

	out.println("Recent FilterServlet requests: " +n1 + " in 1 min, " + n10 + " in 10 min");

	out.println("");
	out.println("Note: FilterServlet requests are page views; JudgmentServlet requests are recorded judgments");

	out.println(FilterServlet.report());

	out.println("");
	out.println(hline);
	out.println("whoSync=" + SessionData.getWhoSync());


	// The answer (on my dev machine) is, /var/lib/tomcat6
	//String path = (new File(".").getCanonicalPath();
	//out.println("Current work directory: " + path);


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

    /** Returns true if we believe that the request has come from a robot */
    static boolean isRobot(HttpServletRequest request) {
	String agent=request.getHeader("User-Agent");
	if (agent!=null &&
	    (agent.indexOf("Baiduspider")>=0 ||
	     agent.indexOf("ahrefs.com")>=0 )) return true;
	String ip=request.getRemoteAddr();
	if (ip!=null &&
	    (ip.startsWith("180.76.5") ||     // baidu
	     ip.startsWith("180.76.6") ||
	     ip.startsWith("185.10.104"))) {  // baidu
	    return true;
	}
	return false;
    }

    /** Checks if the request has apparently come from a robot whom we
	don't want to serve. If it has, sends a rejection request and
	closes connection.
	@return true If this has been a robot request and has been rejected.
	If true has been returned, you need to return from service() right 
	away.
     */
   public boolean robotRequestRejected(HttpServletRequest request, HttpServletResponse response) {
       if (!isRobot(request)) return false;
       rejectedRobotRequestCnt++;
       String msg = "Sorry, we do not want this site indexed. This is just a mirror of http://arxiv.org ";
       try {
	   response.sendError(HttpServletResponse.SC_FORBIDDEN, msg);
       } catch (java.io.IOException ex) {}
       return true;    
   }

  public String getServletInfo() {
	return "My.ArXiv BaseArxivServlet or a derived servlet";
    }

}
