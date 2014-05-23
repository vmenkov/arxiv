package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.PageContext;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.sb.SBRGenerator;

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

    /** Whether this session needs a "moving panel" with session-based 
	recommendations (aka "session buddy") */
    private boolean allowedSB = false; 
    boolean needSBNow = false;

    /** Additional mode parameters for the SB generator */
    public boolean sbDebug = false;
    /** This controls the way of achieving "stable order" of the
	articles in the rec list */
    public int sbMergeMode = 1;

    void validateSbMergeMode() throws WebException {
	if (sbMergeMode<0 || sbMergeMode>2) throw new WebException("Illegal SB merge mode = " + sbMergeMode);
    }

    /** If true, the SB moving panel will be displayed in "researcher mode".
	Since SB is only shown to users who have not logged in, we can't
	use the usual researcher flag, but rather set this flag via a 
	"secret" query string parameter.
     */
    public boolean researcherSB = false; 

    /** The session-based recommendation generator. It's created together
	with the session, but is not actually used until the needSBNow flag 
	is set.
    */
    final SBRGenerator sbrg=new SBRGenerator(this);

    /** Used to record the ArXiv article ID of an article linked from
	a viewed page, or of any other article that the SB user does
	not want to see.
     */
    void recordLinkedAid(String aid) {
	sbrg.recordLinkedAid(aid);
    }
    /** Used to record the the ArXiv article IDs of several articles
	linked from a viewed page, or of any other articles that the
	SB user does not want to see.
     */
    void recordLinkedAids(Vector<ArticleEntry> entries) {
	sbrg.recordLinkedAids(entries);
    }
   
    private SessionData( HttpSession _session,HttpServletRequest request )
	throws WebException, IOException {
	session = _session;
	initFactory( session );
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
	    ResultsBase.ensureClosed( em, true);
	}
    }

    /** Create a new EntityManagerFactory using the System properties.
     */
    private static synchronized void initFactory(HttpSession session) 
	throws IOException, WebException {
	if (factory!=null) return;
	Properties p = getProperties(session.getServletContext());     
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


    private static Properties ourProperties=null;

    /** Really should be session-scope, rather than static read. (?) */
    private static synchronized Properties getProperties(ServletContext context) 
	throws WebException, IOException {

	if (ourProperties!=null) return ourProperties;
	ourProperties= new Properties(); // or we can pass system prop?
	String path = "/WEB-INF/connection.properties";
	edu.rutgers.axs.sql.Logging.info("SessionData.getProperties(): Trying to read properties from " + path);
	InputStream is = context.getResourceAsStream(path);
	if (is==null) throw new WebException("Cannot find file '"+path+"' at the server's application context.");

	ourProperties.load(is);
	is.close();

	edu.rutgers.axs.sql.Logging.info("SessionData.getProperties() - loaded");
	//	for(Object k:p.keySet()) {
	    //edu.rutgers.axs.sql.Logging.info("Prop["+k+"]='"+p.get(k)+"'");}

	return ourProperties;
    }


    //    static final String defaultUser = "anonymous";
    static final String defaultUser = null;
    
    /** Are we relying on context.xml and Tomcat being properly deployed, eh? */
    static final boolean relyOnTomcat=false;

    private String storedUserName = null;

    /** Unlike getRemoteUser(), this method does not recheck the
	extended  session cookie. It is safe to use if we know
	that  getRemoteUser() has been recently called.
     */
    public String getStoredUserName() {
	return storedUserName;
    }

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
			ResultsBase.ensureClosed( em, true);
 		    }
		}
	    }
	}
	return  (u!=null)? u : 		defaultUser;
    }


    /** Returns the user object for the specified   user.*/
    /*
    static User getUserEntry(EntityManager em, String user) {
	return User.findByName(em, user);
    }
    */

    /** Saves the user name (received from the [validated] login form, 
	or recovered via a persistent cookie) into the session's memory.
     */
    void storeUserName(String u) {
	storedUserName = u;
    }

    /** Records the session-user association in the SQL server. Note that
	when a person works for a while without logging in, and then logs in,
	the user name will be associated with the entire session.
     */
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
	Note that the URLs of the form "index.jsp?..." requires login;
	this is for the benefit of EmailSug.jsp. An exception is 
	index.jsp?sb=true, used to activate Session-Based Recommendations

	@return null if no restriction is imposed, or a list of
	allowed roles (may be empty) otherwise
     */
    static Role.Name[] authorizedRoles(String sp, String qs) {
	if (sp.startsWith("/personal")  ||
	    sp.equals("/index.jsp") && qs!=null && qs.length()>0  &&
	    !qs.startsWith("sb=true")) 
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

    /** Is the specified user authorized to access the url requested
	in the given request?
    */
    boolean isAuthorized(HttpServletRequest request, String user) {
	String sp = request.getServletPath();
	String qs = request.getQueryString();
	//	Logging.info("isAuthorized("+user+", " + sp + ")?");
	Role.Name[] ar = authorizedRoles(sp,qs);
	if (ar==null) return true; // no restrictions
	if (user==null) return false; // no user 
	EntityManager em = getEM();
	User u = User.findByName(em, user);
	em.close();
	boolean b = u!=null && u.hasAnyRole(ar);
	//	Logging.info("isAuthorized("+user+", " + sp + ")=" + b);
	return b;
    }

    /** Turns the flag on to activate the moving panel for the
	Session-Based recommendations. Requests the suggestion
	list generation.
     */
    synchronized void sbCheck(EntityManager em) {
	if (allowedSB) {
	    // count the actions in this session...
	    int actionCnt = Action.actionCntForSession( em,  sqlSessionId);
	    needSBNow = 	     (actionCnt>=2);
	    if (needSBNow) {
		sbrg.requestRun(actionCnt);
	    }
	}
    }

    /** This is invoked from the ResultsBase constructor to see if the
	user has requested the SB to be activated.  Once requested, it
	will stay on for the rest of the session.

	@rb The ResultsBase object for the web page; used to access
	command line parameters
     */
    void setSBFromRequest(ResultsBase rb) throws WebException {
	boolean sb = rb.getBoolean("sb", false);
	if (sb) {
	    turnSBOn(rb);
	}
    }
    
    /** Used instead of setSBFromRequest() if we know that SB must be
	turned on. This happens when the user explicitly loads sessionBased.jsp
     */
    void turnSBOn(ResultsBase rb) throws WebException {
	allowedSB = true;
	sbMergeMode = rb.getInt("sbMerge", sbMergeMode);
	validateSbMergeMode();
	// the same param initializes both vars now
	sbDebug = rb.getBoolean("sbDebug", sbDebug);
	researcherSB = rb.getBoolean("sbDebug", researcherSB || rb.runByResearcher());
    }


}
