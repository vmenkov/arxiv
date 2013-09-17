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
    session of the My.ArXiv web application.
 */
public class SessionData {

    /** Back-pointer */
    final private HttpSession session;

    static private EntityManagerFactory factory;// = null;

    /** The ID of the Session object in the SQL database
     */
    final private long sqlSessionId;

    public  long getSqlSessionId() { return sqlSessionId;}

    /** Generates proper link URLs */
    //final public Link link;
    
    private SessionData( HttpSession _session,HttpServletRequest request )
	throws WebException, IOException {
	session = _session;

	//-- not used any more: use persistence.xml instead
	Properties p = //new Properties();  
	readProperties();     

        // The "arxiv" name will be used to configure based on the
        // corresponding name in the META-INF/persistence.xml file
	initFactory(p);
	// record the session in the database
	EntityManager em=null;
	try {
	    em = getEM();
	    em.getTransaction().begin(); 
	    Session s = new Session(_session);
	    em.persist(s);
	    em.getTransaction().commit(); 
	    Logging.info("Recorded session " + s + "; web server session id=" + _session.getId());
	    sqlSessionId = s.getId();
	    //	} catch (IOException ex) {
	    //	    ex.printStackTrace(System.out); // just for debugging
	    //	    throw ex;
	} finally {
	    if (em!=null) em.close();
	}
    }

    /** Create a new EntityManagerFactory using the System properties.
     */
    private static synchronized void initFactory(Properties p) {
	if (factory!=null) return;
	factory = Persistence.
	    createEntityManagerFactory(Main.persistenceUnitName,p);
    }

    /** Creates a new EntityManager from the EntityManagerFactory. The
	EntityManager is the main object in the Java persistence API, and is
        used to create, delete, and query objects, as well as access
        the current transaction.
    */
    public EntityManager getEM() {
	return getEM0();
    }

    private static EntityManager getEM0() {
	if (factory==null) throw new AssertionError("SessionData.getEM() should not be called until at least one  SessionData object has been created");
        EntityManager em = factory.createEntityManager();
  	return em;
    }  


    /** Looks up the SessionData object already associated with the
	current session, or creates a new one. This is done atomically,
        synchronized on the session object.
     */
    static synchronized SessionData getSessionData(HttpServletRequest request) 
	throws WebException, IOException {

	HttpSession session = request.getSession();
	SessionData sd  = null;
	synchronized(session) {
  
	    final String ATTRIBUTE_SD = "sd";
	    sd  = ( SessionData) session.getAttribute(ATTRIBUTE_SD);
	    if (sd == null) {
		sd = new SessionData(session,request);
		session.setAttribute(ATTRIBUTE_SD, sd);
		

	    }
	    //sd.update(request);

	}
	return sd;
     }

 
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
	//	for(Object k:p.keySet()) {
	    //edu.rutgers.axs.sql.Logging.info("Prop["+k+"]='"+p.get(k)+"'");}

	return p;
    }


    //    static final String defaultUser = "anonymous";
    static final String defaultUser = null;
    
    /** Are we relying on context.xml and Tomcat being properly deployed, eh? */
    static final boolean relyOnTomcat=false;

    private String storedUserName = null;

    /** Gets the user name associated with this session. Originally,
	this method relied on Tomcat keeping track of this stuff, but
	as Tomcat is not always deployed quite right, we don't do it
	this way anymore. Instead, an instance variable
	(storedUserName) in this SessionData object is used to keep
	track of the user name within the current Tomcat
	session. Between Tomcat sessions (e.g., after server restart),
	the ExtendedSessionManagement module is used to find the user
	name based on a cookie sent by the browser.

	<p>
	This method also starts the new experimental day, whenever necessary. 
     */
    String getRemoteUser(HttpServletRequest request) {    
	String u;
	if (relyOnTomcat) {
	    // FIXME: if we do this (we don't now), how will we control the
	    // LEARN/EVAL day type setting?
	    u = request.getRemoteUser();
	} else {
	    // first, check this server session
	    u = storedUserName;
	    if (u==null) {
		// maybe there is an extended session?
		Cookie cookie =  ExtendedSessionManagement.findCookie(request);
		if (cookie!=null) {
		    EntityManager em = getEM();
		    try {
			User user=  ExtendedSessionManagement.getValidEsUser( em, cookie);
			if (user!=null) {
			    u = user.getUser_name();
			    boolean newDay = user.changeDayIfNeeded();
			    // Logging.info("getRemoteUser("+u+"); change day=" + newDay +"; now day=" + user.getDay());
			    if (newDay)  {
				Logging.info("getRemoteUser("+u+"); changed day to "+user.getDay());
				em.getTransaction().begin(); 
				em.persist(user);
				em.getTransaction().commit(); 
			    }
			    //			    Logging.info("getRemoteUser("+u+"); committed");
			    storeUserName(u);
			    storeUserInfoInSQL(em, user);
			}
		    } finally {
			em.close();
		    }
		}
	    }
	}
	return  (u!=null)? u : 		defaultUser;
    }

    /** Returns the user object for the currently logged-in
     user.*/
    User getUserEntry(String user) {
	EntityManager em = getEM();
	User u = User.findByName(em, user);
	em.close();
	return u;
    }

    void storeUserName(String u) {
	storedUserName = u;
    }

    void storeUserInfoInSQL(EntityManager em, User user) {
	em.getTransaction().begin(); 
	Session s = (Session)em.find(Session.class, sqlSessionId);
	s.setUser(user);
	em.persist(s);
	em.getTransaction().commit(); 	
    }

    /** Gets the list of authorized roles for the specified URL, from a
	hard-coded list. This is a poor substitute for specifiying
	them in a set of "security-constraint" elements in web.xml

	<p>
	Note that the URL of the form "index.jsp?..." requires login;
	this is for the benefit of EmailSug.jsp.

	@return null if no restriction is imposed, or a list of
	allowed roles (may be empty) otherwise
     */
    static Role.Name[] authorizedRoles(String sp, String qs) {
	if (sp.startsWith("/personal")  ||
	    sp.equals("/index.jsp") && qs!=null && qs.length()>0) 
	    return new Role.Name[] 
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

    /** Is the current user authorized to access this url? */
    boolean isAuthorized(HttpServletRequest request) {
	return isAuthorized(request, getRemoteUser(request));
    }

    /** Is the specified user authorized to access this url?
    */
    boolean isAuthorized(HttpServletRequest request, String user) {
	String sp = request.getServletPath();
	String qs = request.getQueryString();
	//	Logging.info("isAuthorized("+user+", " + sp + ")?");
	Role.Name[] ar = authorizedRoles(sp,qs);
	if (ar==null) return true; // no restrictions
	if (user==null) return false; // no user 
	User u = getUserEntry(user);
	boolean b = u!=null && u.hasAnyRole(ar);
	//	Logging.info("isAuthorized("+user+", " + sp + ")=" + b);
	return b;
    }

}
