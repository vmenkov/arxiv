package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import edu.rutgers.axs.sql.*;


/** Terminates the current web session */
public class LogoutServlet extends HttpServlet {

    public void	service(HttpServletRequest request,HttpServletResponse response) {

	try {
	    SessionData sd = SessionData.getSessionData(request);	  
	    sd.storeUserName(null);
	    request.getSession().invalidate();
	    String redirect = "index.jsp";	    
	    RequestDispatcher dis = request.getRequestDispatcher(redirect);
	    Logging.info("Logout: forward to=" + redirect);	    
	    dis.forward(request, response);

	} catch (Exception e) {
	    try {
		e.printStackTrace(System.out);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in LogoutServlet: " + e); //e.getMessage());
	    } catch(IOException ex) {};
	} finally {
	}
    }

}
