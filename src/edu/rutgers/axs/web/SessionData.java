package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.PageContext;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** A single instance of this class is associated with a particular
 * session of the ICD application

 */
public class SessionData {

    /** Back-pointer */
    final private HttpSession session;

    final private EntityManagerFactory factory;// = null;

    /** Generates proper link URLs */
    //final public Link link;
    
    private SessionData( HttpSession _session,HttpServletRequest request ) throws WebException,
					      IOException {
	session = _session;

	//-- not used any more: use persistence.xml instead
	Properties p = //new Properties();  
	readProperties();

        // Create a new EntityManagerFactory using the System properties.
        // The "icd" name will be used to configure based on the
        // corresponding name in the META-INF/persistence.xml file
	factory = Persistence.
	    createEntityManagerFactory(Main.persistenceUnitName,p);

	//link = new Link( request.getContextPath());
    }


    public EntityManager getEM() {
        // Create a new EntityManager from the EntityManagerFactory. The
        // EntityManager is the main object in the persistence API, and is
        // used to create, delete, and query objects, as well as access
        // the current transaction
        EntityManager em = factory.createEntityManager();
  	return em;
    }  

    /** Looks up the DemoSessionData already associated with the
     * current session, or creates a new one. This is done atomically,
     * synchronized on the session object.
     */
    static synchronized SessionData getSessionData(HttpServletRequest request) 
				    throws WebException,IOException
{

	HttpSession session = request.getSession();
	String name = "sd";
	SessionData sd  = null;
	synchronized(session) {
	    /*
	    for(Enumeration en = session.getAttributeNames(); en.hasMoreElements(); ) {
		String key=(String)en.nextElement();
		Object val=session.getAttribute(key);
		//Logging.info("session["+key+"]="+val);
	    }
	    */

	    sd  = ( SessionData) session.getAttribute(name);
	    if (sd == null) {
		sd = new SessionData(session,request);
		session.setAttribute(name, sd);
	    }
	    //sd.update(request);

	}
	return sd;
     }

    /*
    public static SessionData getSessionData(PageContext context) throws WebException,IOException{
	return getSessionData(context.getSession());
    }
    */

    /*
    static  SessionData getSessionData(HttpSession session )  
    throws WebException,IOException
    {
	String name = "sd";
	SessionData sd  = null;
	synchronized(session) {
	    for(Enumeration en = session.getAttributeNames(); en.hasMoreElements(); ) {
		String key=(String)en.nextElement();
		Object val=session.getAttribute(key);
		//Logging.info("session["+key+"]="+val);
	    }


	    sd  = ( SessionData) session.getAttribute(name);
	    if (sd == null) {
		sd = new SessionData(session);
		session.setAttribute(name, sd);
	    }
	    //sd.update(request);

	}
	return sd;
    }
    */

    ServletContext getServletContext() {
	return session.getServletContext(); 
    }


    //final static NumberFormat pcfmt = new DecimalFormat("#0.##");
    //final static NumberFormat ratefmt = new DecimalFormat("0.###");

    /** Really should be session-scope, rather than static read*/
    private Properties readProperties() 
	throws WebException, IOException {

	ServletContext context = session.getServletContext(); 

	Properties p = new Properties(); // or we can pass system prop?
	String path = "/WEB-INF/connection.properties";
	edu.rutgers.axs.sql.Logging.info("SessionData.readProperties(): Trying to read properties from " + path);
	InputStream is = context.getResourceAsStream(path);
	if (is==null) throw new WebException("Cannot find file '"+path+"' at the server's application context.");

	p.load(is);
	is.close();

	edu.rutgers.axs.sql.Logging.info("SessionData.readProperties() - loaded");
	for(Object k:p.keySet()) {
	    edu.rutgers.axs.sql.Logging.info("Prop["+k+"]='"+p.get(k)+"'");}

	return p;
    }


    //    static final String defaultUser = "anonymous";
    static final String defaultUser = null;
    
    /** Are we relying on context.xml and Tomcat being properly deployed, eh? */
    static final boolean relyOnTomcat=false;

    private String storedUserName = null;

    String getRemoteUser(HttpServletRequest request) {    
	String u;
	if (relyOnTomcat) {
	    u = request.getRemoteUser();
	} else {
	    // first, check this server session
	    u = storedUserName;
	    if (u==null) {
		// maybe there is an extended session?
		Cookie cookie =  ExtendedSessionManagement.findCookie(request);
		if(cookie!=null) {
		    EntityManager em = getEM();
		    User user=  ExtendedSessionManagement.getValidEsUser( em, cookie);
		    if (user!=null)  u = user.getUser_name();
		    em.close();
		}
	    }
	}
	return  (u!=null)? u : 		defaultUser;
    }

    /** Returns the user object for the currently logged-in user */
    public User getUserEntry(String user) {
	EntityManager em = getEM();
	User u = User.findByName(em, user);
	em.close();
	return u;
    }

    void storeUserName(String u) {
	storedUserName = u;
    }

    /** Gets the list of authorized roles for this URL, from a
	hard-coded list. This is a poor substitute for specifiying
	them in a set of "security-constraint" elements in web.xml

	@return null if no restriction is imposed, or a list of
	allowed roles (may be empty) otherwise
     */
    static Role.Name[] authorizedRoles(String sp) {
	if (sp.startsWith("/personal")) return new Role.Name[] 
					    {Role.Name.subscriber,
					     Role.Name.researcher,
					     Role.Name.admin};
	else if (sp.startsWith("/tools")) return new Role.Name[] 
					      {Role.Name.admin,
					       Role.Name.researcher};
	else if (sp.startsWith("/admin")) return new Role.Name[] 
					      {Role.Name.admin};
	else return null;
    }

    /** Is this user authorized to access this url? */
    boolean isAuthorized(HttpServletRequest request) {
	return isAuthorized(request, getRemoteUser(request));
    }

    boolean isAuthorized(HttpServletRequest request, String user) {
	String sp = request.getServletPath();
	Role.Name[] ar = authorizedRoles(sp);
	if (ar==null) return true; // no restrictions
	if (user==null) return false; // no user 
	User u = getUserEntry(user);
	return u!=null && u.hasAnyRole(ar);
    }

}
