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
import edu.rutgers.axs.sb.SBRGeneratorCmdLine;
import edu.rutgers.axs.upload.BackgroundThread;
import edu.rutgers.axs.upload.UploadProcessingThread;
import edu.rutgers.axs.recommender.UserProfile;
import edu.rutgers.axs.recommender.Stoplist;


/** A single instance of this class is associated with a particular
    session of the My.ArXiv web application. It contains information
    about the logged-in user, various session-scope data structures
    (in particular, those used for SB recommendations), and a link to
    the database object (Session) corresponding to the session.

    <p>There is normally a one-to-one association between our
    SessionData objects and HttpSession objects of the servlet
    container (Tomcat). The latter stores the former as a property,
    and the formar back-links to the latter as well.

    <p>It is also possible to create a SessionData object in a command line
    application; this is used e.g. as part of the test harness for SBRG.
 */
public class SessionData {

    /** Back-pointer to the web server's object associated with this session. */
    final private HttpSession session;

    static private EntityManagerFactory factory;// = null;

    /** The ID of the Session object in the SQL database
     */
    final private long sqlSessionId;

    /** Used for debugging, to figure who has hogged the static-synchronized
	methods */
    private static String whoSync = null;
    static String getWhoSync() { return whoSync; }

    private static void addSync(String msg) {
	Logging.info(msg);
	whoSync += "; " + msg;	
    }


    public  long getSqlSessionId() { return sqlSessionId;}

    public String toString() {
	return "(SD: sqlSessionID=" + getSqlSessionId()+")";
    }

    /** The session-based recommendation generator. It's created together
	with the session, but is not actually used until the flag sd.needSBNow
	is set.
    */
    final public SBRGenerator sbrg; 

    /** The most recent uploaded document processsing thread (for the Toronto
	system) in this session. */
    UploadProcessingThread upThread = null;
    //    TorontoPPPThread or TorontoEE5Thread 
    BackgroundThread upInitThread = null;

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
   
    /** Creates a SessionData object for a new Session. It also makes
	a record of the session in the SQL database... 

	FIXME: Occasionally, a "synchronization block" appears to
	happen here, which may be triggered by Transaction.commit()
	failure to return (??)

	@param _session The underlying web session object (in a web
	 app), or null (in a command line app)
     */
    private SessionData( HttpSession _session) throws WebException, IOException {

	long tid=Thread.currentThread().getId();

	whoSync = "SessionData.in("+tid+")";
	try {
	    session = _session;
	    sbrg=  (session!=null) ? new SBRGenerator(this, true) :
		new SBRGeneratorCmdLine(this);
	    addSync("SD.SD.A");
	    initFactory( session );
	    addSync("SD.SD.B");
	    // record the session in the database
	    EntityManager em=null;
	    try {
		addSync("SD.SD.C");
		em = getEM();
		addSync("SD.SD.D");
		em.getTransaction().begin(); 
		Session s = new Session(_session);
		em.persist(s);
		commitWithRetry(em);

		addSync("SD.SD.E");
		String msg ="Recorded session " +s + ";";
		msg += (session!=null)?
		    " web server session id=" + _session.getId() :
		    " simulated session";
		addSync(msg);
		sqlSessionId = s.getId();
	    //	} catch (IOException ex) {
	    //	    ex.printStackTrace(System.out); // just for debugging
	    //	    throw ex;
	    } finally {
		ResultsBase.ensureClosed( em, true);
	    }
	} finally {
	    whoSync = "SessionData.out";
	}
    }

    /** This method solves the following problem. A java.lang.ExceptionInInitializerError is sometimes thrown in em.getTransaction().commit(). It comes from org.apache.openjpa.lib.util.ConcreteClassGenerator.newInstance, from java.io.EOFException when talking to the MySQL server. This usually happens when the My.ArXiv server is accessed the first time after a longish period of inactivity. 

	In the past (until 2016-10) such an exception would come as a
	visible error to the user; the problem would disappear when
	you reloaded the page. Using this method (retrying commit
	after a failure) appears to have eliminated this problem.
    */
    private static void commitWithRetry(EntityManager em) {

	long tid=Thread.currentThread().getId();

	for(int tryCnt = 0; tryCnt < 2; ) {
	    tryCnt++;
	    try {
		em.getTransaction().commit();	
		Logging.info("CWT("+tid+"): Commit ("+ tryCnt+") successful");
		return;
	    } catch (ExceptionInInitializerError ex) {
		final long sleepMsec = 100;
		Logging.info( "SessionData.commitWithRetryT("+tid+"): commit ("+ tryCnt+") failed; will wait "+sleepMsec +" msec and retry");
		try {
		    Thread.sleep(sleepMsec);
		} catch (java.lang.InterruptedException e) {}
	    }
	}
	// final try
	em.getTransaction().commit();	
	Logging.info("CWT("+tid+"): Commit (last attempt) successful");
    }


    /** Creates a new EntityManagerFactory using the System
	properties. The properties are accessed via the servlet
	context (in a web application) or directly (in a command line
	application, for a simulated session).
	@param session The relevant HttpSession, or null (in a simulated
	session)
     */
    private static synchronized void initFactory(HttpSession session) 
	throws IOException, WebException {
	whoSync = "initFactory.in";
	try {
	if (factory!=null) return;
	if (session==null) {
	    factory = Main.getFactory();
	} else {
	    Properties p = getProperties(session.getServletContext());     
	    factory = Persistence.
		createEntityManagerFactory(Main.persistenceUnitName,p);
	}
	} finally {
	    whoSync = "initFactory.out";
	}
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


    private final static String ATTRIBUTE_SD = "sd";

    /** Looks up the SessionData object already associated with the
	current session, or creates a new one. This is done atomically,
        synchronized on the session object.
	
	<p>This can also be used in command-like app with simulated
	session, when this method will simply create a new dummy SessionData
	object.

	@param request The current HTTP request (or null in a command-like app
	with simulated session).
     */
    static public synchronized SessionData getSessionData(HttpServletRequest request) 
	throws WebException, IOException {

	whoSync = "getSessionData.in";
	try {

	    addSync("SD.gSD.A, request is " + (request==null? "null" : "not null"));
	    if (request==null) return new SessionData(null);

	    HttpSession session = request.getSession();
	    SessionData sd  = null;
	    addSync("SD.gSD.B");
	    synchronized(session) {
		addSync("SD.gSD.C");
		sd  = ( SessionData) session.getAttribute(ATTRIBUTE_SD);

		addSync("SD.gSD.D, old sd= " + sd);
		if (sd == null) {
		    sd = new SessionData(session);
		    session.setAttribute(ATTRIBUTE_SD, sd);
		}
	    }
	    return sd;
	} finally {
	    whoSync = "getSessionData.out";
	}

    }

    static synchronized SessionData getSimulatedSessionData() 
					throws WebException, 
					IOException {

	    SessionData sd  = new SessionData(null);
	    return sd;
    }


    /** Discards the SessionData object (if any) associated with the
	current web session; then creates a new one and stores it.
	This method is primarily used for the "change focus"
	functionality in SB.
	
	<p>When no SessionData object exists yet, this method is
	similar to getSessionData()
     */
    static synchronized SessionData replaceSessionData(HttpServletRequest request) 
	throws WebException, IOException {

	whoSync = "replaceSD.in";
	try {
	HttpSession session = request.getSession();
	SessionData sd  = null;
	synchronized(session) {  
	    sd = new SessionData(session);
	    session.setAttribute(ATTRIBUTE_SD, sd);
	}
	return sd;

	} finally {
	    whoSync = "replaceSD.out";
	}

     }

    /** Simply invalidates the HttpSession. This means that the associated 
	SessionData object will be discarded as well. 
     */
    static synchronized void discardSessionData(HttpServletRequest request) 
	throws WebException, IOException {
	whoSync = "discardSD.in";
	try {
	    HttpSession session = request.getSession();
	    session.invalidate();
	} finally {
	    whoSync = "discardSD.out";
	}

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

	whoSync = "getProperties.in";
	try {

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
	} finally {
	    whoSync = "getProperties.out";
	}
    }

    static private boolean stoplistLoaded = false;


    /** Loads a stoplist file, which sits under WEB-INF in the web app's 
	directory.
	<P> FIXME: The assumption is that all applications that need a stoplist
	need the same stoplist...
	<p> FIXME: This ought to be synchronized on a static lock var.
    */
    void loadStoplist() throws IOException {
	if (!stoplistLoaded) {
	    String path = "WEB-INF/stop200.txt";
	    InputStream is=getServletContext().getResourceAsStream(path);
	    UserProfile.setStoplist(new Stoplist(is));
	    stoplistLoaded = true;
	}
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
    public void storeUserName(String u) {
	storedUserName = u;
    }

    /** Records the session-user association in the SQL server (as
	part of the associated persistent Session object). Note that
	when a person works for a while without logging in, and then
	logs in, the user name supplied on login will become
	associated with the entire session.
     */
    public void storeUserInfoInSQL(EntityManager em, User user) {
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

	<p>FIXME: Obviously, no password protection of any kind is provided
	for *.html pages (since they are served by Tomcat without invoking
	any of our code, unlike *.jsp or servlet pages)

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

    /** Creates and "persists" an Action object; adds an Action object
	to the record of the specified user's activity (unless it's an
	anon session).

	<p>
	This method has no transaction begin/end calls in it; it normally
	should appear inside a transaction.
	
	@param art ArXiv article involved in this action. Should be non-null, unless op is NEXT_PAGE or PREV_PAGE	
	@param u User object. May be null (for anon user actions)
	@param art An Article object referring to the document on
	which the action was carried out.  Usually, this refers to an
	ArXiv article, or, for UPLOAD operation, to a user-uploaded
	document. This parameter is for several non-article-specific
	action types, such as NEXT_PAGE, PREV_PAGE, and REORDER.
	@param reorderedAids The list of articles reordered by the
	user. This is supplied for the REORDER action (where art=null)
	@throws WebException On illegal argument combinations
    */
    public Action addNewAction(EntityManager em,  User u, Action.Op op, 
			       Article art, String reorderedAids[],
			       ActionSource asrc)  {
	return addNewAction(em, u,  op, art, reorderedAids, asrc, this);
    }

    /** Similar to its non-static sibling, this method is only needed
	to be used when we need to create an Action entry from a
	command-line application. (This may be done retroactively to 
	"fix" things after some reorganizations, or it can be done
	in test harness experiments, when we are emulating online user activity
	in a command-line tool).

	2017-04-17: On REORDER operations, the topmost promoted article
	is now recorded in the Article field of the Action object.
	This is for use in COACCESS2 SB recommender.
     */
    public static Action addNewAction(EntityManager em,  User u, Action.Op op, 
				      Article art, String reorderedAids[],
				      ActionSource asrc, SessionData sd)  {

	long newPLid = 0; // for REORDER op only
	long sid = (sd==null)? 0: sd.sqlSessionId;
	if (art==null) { // article ID is usually required, except for..
	    if (op==Action.Op.REORDER) {
		// have a user-reordered list to save
		long oplid = asrc.presentedListId;
		if (oplid==0) throw new IllegalArgumentException("No original presented list ID is supplied");

		PresentedList original=(PresentedList)em.find(PresentedList.class,oplid);
		if (original==null)  throw new IllegalArgumentException("No presented list for the supplied ID=" + oplid + " can be found!");
		if (sd==null) throw new IllegalArgumentException("Cannot create a REORDER action outside of a web app!");
		PresentedList newPL = sd.saveReorderedPresentedList(em,original,u,reorderedAids,sid); 
		newPLid = newPL.getId();

		String promotedAid = original.whoWasPromoted(newPL);
		if (promotedAid!=null) {
		    art = Article.getArticleAlways(em, promotedAid,false);
		}

		// on SB lists, make in-memory record in the SB generator,
		// for future use in "maintain stable order" procedures
		if (original.getType()==Action.Source.SB) {
		    sd.sbrg.receiveReorderedList(oplid, reorderedAids);
		}
	    } else if (op==Action.Op.NEXT_PAGE || op==Action.Op.PREV_PAGE) {
	    } else {
		throw new IllegalArgumentException("Cannot create an action with op code " + op + " without an article ID!");
	    }
	}
	Action r = new Action(u, sd, art, op); 
	r.setActionSource(asrc);
        if (u!=null) u.addAction(r); 
	if (op==Action.Op.REORDER) r.setNewPresentedListId(newPLid);
	em.persist(r);
	if (u!=null) r.bernoulliFeedback(em); // only affects Bernoulli users
	if (sd!=null && op!=Action.Op.UPLOAD) {
	    sd.sbrg.addAction(r); // updates the session history for sb
	}
	return r;
    }


    /** Creates a new PresentedList object which describes the user-actuated
	reordering of a previously exiting ("original") PresentedList.

	There is never a DataFile link in this object; this info has to
	be accessed through the original PresentedList object.
    */
    PresentedList saveReorderedPresentedList(EntityManager em, PresentedList original, User u, String aids[], long session) {
	//Action.Source type = original.getType();
	Action.Source type = Action.Source.REORDER;
	PresentedList plist = new PresentedList(type, u, session);
	plist.setUserReorderingOfPresentedListId(original.getId());
	plist.fillArticleList(aids);	
	//	if (df!=null) plist.setDataFileId( df.getId());
	//if (eq!=null) plist.setQueryId( eq.getId());
	//em.getTransaction().begin(); // *****
	em.persist(plist);
	//em.getTransaction().commit();
	return plist;
    }

 
}
