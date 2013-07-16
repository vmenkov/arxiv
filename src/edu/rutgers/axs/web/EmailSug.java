package edu.rutgers.axs.web;


import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import javax.mail.*;
import javax.mail.internet.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.ParseConfig;


/** Email the user about his suggestion list.

  @author Vladimir Menkov, Ziyu Fan
*/


public class EmailSug extends ResultsBase {
    //public String email;
    /** The name of the user to whom we send email */
	
    public String uname=null;
    /** The user object */
    private User r=null;

    /** Sending status */
    public boolean sent = false;

    /** For command-line testing on machines that don't allow email sending */
    private static boolean dontSend = false;



    /** Email the user his suggestion list. */
     public EmailSug(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	EntityManager em = sd.getEM();
	try {
	
	    uname = getString(USER_NAME,null);
	    boolean force = getBoolean("force", false);
	    if (uname==null) {
		error=true;
		errmsg = "No user name supplied";
		return;
	    }

	    r = User.findByName(em, uname);
	    if (r==null) {
		error=true;
		errmsg = "No user account named '"+uname+"' exists in my.arxiv";
	    } else if (!hasEmail(r)) {
		error=true;
		errmsg = "No valid email address is stored for user " + uname;
	    }
	    if (error) return;

	    boolean must = force || needEmail(em);
	    if (must && approvedOnly && !approvedUsersSet.contains(uname)) {
		must = false;
		error=true;
		errmsg = "Email not sent to user " + user +", because he's not on the internal approved list";
	    }
	    if (error || !must) return;
	    
	    sent = doEmailSug(em);
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ensureClosed(em);
	}
    }

  
    private EmailSug(User _r) {
	r = _r;
	uname = r.getUser_name();
    }

    /** Email the user his suggestion list.
	@return success status
     */
    boolean doEmailSug(EntityManager em) throws javax.mail.MessagingException,  javax.mail.internet.AddressException, Exception   {

	Logging.info("emailSug for user " + r);

	try {
	    // Begin a new local transaction 
	    em.getTransaction().begin();
	    
	    if (r==null) throw new WebException("There is no account with the user name '" + uname + "' in our records");
	    String realName =  r.getFirstName() + " " + r.getLastName();

	    if (r.getEmail()==null) throw new WebException("There is no email address corresponding to the user name '" + uname + "' in our records"); 

            String email = r.getEmail();
	    
	    if (!r.isEnabled())  throw new WebException("User account with the name '" + uname + "' is currently disabled. If this is your account, please contact a site administrator to enable it and reset the password");

	    Logging.info("Sending email to " + email);

	    // we commit before sending the message, because no
	    // information about the success of the actual message is stored
	    // in the database
	    em.persist(r);
	    em.getTransaction().commit();

	    if (dontSend) {		
		System.out.println("Not sending email (dontSend flag on)");
		return false;
	    } else {
		return sendMail(uname, email, realName);
	    }

	} catch(WebException ex) {
	    Logging.error(errmsg = ex.getMessage());
	    error = true;
	    return false;
	}
	
		
    }

    /**  Sending mail.
	 The hostname for the SMTP server. The typical values are "smtp" (on
	 Telus ADSL machines) or "localhost" (on hosting.ca, or on cactuar).  
    */
    //    static String smtp = "smtp";
    static String smtp = "localhost";

    /** This is a mailing list address. To manage, login to 
	https://www.list.cornell.edu:9443/
	as one of the mailing list administrators.
     */
    public static String businessEmail = "myarxiv-admin-l@list.cornell.edu";

    /** The gmail mode is used for local testing only */
    static boolean gmail = isLocal();

    /** Sends a message to the customer service */
    static private boolean sendMail(String uname, String email, String realName)
	throws javax.mail.MessagingException,  javax.mail.internet.AddressException, IOException, WebException {

	Properties props = System.getProperties();
	// XXX - could use Session.getTransport() and Transport.connect()
	// XXX - assume we're using SMTP
	String mailhost = smtp;

	if (gmail) {
	    mailhost = "smtp.gmail.com";
	    props.put("mail.smtps.auth", "true");
	}

	if (mailhost != null)    props.put("mail.smtp.host", mailhost);
	
	// Get a Session object
	Session session = Session.getDefaultInstance(props, null);
	
	// construct the message
	javax.mail.Message msg = new MimeMessage(session);
	
	InternetAddress from =
	    InternetAddress.parse( businessEmail, false)[0];
	msg.setFrom(from);
	
	msg.setRecipients(javax.mail.Message.RecipientType.TO,
			  InternetAddress.parse(email, false));
	
	String firm = "My.ArXiv";
	
	String link = cp + "/#MIDDLE";
	String accountSettingLink = cp + "/personal/editUserFormSelf.jsp";
	String unsubscribeLink = cp + "/unsubscribe.jsp?" + ResultsBase.USER_NAME + "=" + uname;
	
	// Get the suggestion list for this user.
	boolean dryRun = false;
	ViewSuggestions vs = new ViewSuggestions(uname, dryRun);

	String text = "";
	text += 
	    (realName==null || realName.trim().equals(""))?
	    "<p>Dear " + firm + " user,</p>\n" :
	    "<p>Dear " + realName + ",</p>\n";
	

	text += "<p>These are some of the papers posted on My.ArXiv within the last " + vs.estimateEffectiveDays() +
	    " days. The list is ordered based on your My.ArXiv profile and preferences.</p>\n";

	text += "<p><b>"
	    + "<a href=" + link + ">" + "Click here to view the most up-to-date recommendations in My.ArXiv." + "</a></b></p>";
	
	
	SearchResults sr = vs.sr;
	int i = 1;
	//	int dfid = vs.getDfid();
	long plid = vs.plid;
		
	for( ArticleEntry e: sr.entries) {
	    String s =   formatArticleEntryForEmail( e, plid);
	    String color = (i % 2 == 0)? "#aad8ff" : "#ffb46e";
	    s = "<p style=\"background-color:" + color + ";padding:10px\">" + s + "</p>\n";
	    text +=  s;
	    i++;
	    /*text = text + "<p>" 
	      + i + ".<br>" + "<a href=" + link + ">" + e.titline + "</a>" + "<br>" 
	      + e.authline + "<br>Subjects: " + e.subjline + "</p>";*/
	    
	}
	
	String subject = "My.ArXiv has new recommendations for you";
	
	msg.setSubject(subject);
	

	//String font = "<font color=\"gray\" size=\"1\">";

	text += "<p>This message was sent to My.ArXiv user <strong>"+uname+"</strong>, at the following e-mail address: <strong>" + email +"</strong></p>\n";

	text += "<p>You can " 
	    + "<a href=" + unsubscribeLink + ">" + "unsubscribe from these emails" + "</a>" 
	    + " or change your " 
	    + "<a href=" + accountSettingLink + ">" + "preference settings" + "</a>"  
	    + ".</p>\n";

	    
	//msg.setContent(text, "text/html");
	msg.setContent(text, "text/html; charset=utf-8" );
	
	String mailer = firm + " Web Server";
	msg.setHeader("X-Mailer", mailer);
	msg.setSentDate(new java.util.Date());
	
	// send the thing off
	if (gmail) {
	    Transport t = session.getTransport("smtps");
	    try {
		String username = "my.arxiv.recommender";
		String password = "my arxiv mail";
		t.connect(mailhost, username, password);
		t.sendMessage(msg, msg.getAllRecipients());
	    } finally {
		t.close();
	    }
	} else {
	    Transport.send(msg);	
	}
	Logging.info("Mail to "+uname+" ("+email+") was sent successfully.");
	return true;
    }

    static void usage() {
	usage(null);
    }


    static void usage(String m) {
	System.out.println("Email Suggestion List Utility");
	System.out.println("Usage: java [options] emailSug username");
	/*
	System.out.println("Options:");
	System.out.println(" [-Dtoken=xxx]");
	*/
	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }


    static final String cp = determineContextPath();

    /** Returns true if this is a test run on a home PC. The list of
     such machines' host names is hard-coded inside this method. */
    static private boolean isLocal() {
	try {
	    String hostname = InetAddress.getLocalHost().getHostName();
	    System.out.println("running on host = " + hostname);
	    return hostname.equals("CC2239-Ubuntu") ||
		hostname.equals("qilin");
	} catch( 	java.net.UnknownHostException ex) {
	    return false;
	}
    }

    /** Cludgy... */
    static String determineContextPath() {
	String host;
	int port;
	if (isLocal()) {
	    host = "localhost";
	    port = 8080;
	} else { 
	    host =  "my.arxiv.org";
	    port = 80;
	}
	return "http://" + host + ":" + port + "/arxiv";
    }

    static String formatArticleEntryForEmail(ArticleEntry e, long plid) {
	/*
	String rt = "[score="+e.score+ "]";
	if (e.researcherCommline!=null && e.researcherCommline.length()>0) {
	    rt += "["+e.researcherCommline+"]";
	}
	rt += " ";
	*/

				//s = "<p style=\"background-color:#e5e5e5\">" + s + "</p>";

	/*String s = 
	    "<div class=\"result\" id=\"" + e.resultsDivId() + "\">\n" +
	    "<div class=\"document\">\n" +
		"<font size=\"3\">" +
	    e.i + "." + 
	    //researcherSpan(rt)+ 
	    //e.idline + "; "+e.formatDate()+"\n" +
	    "<br>\n<b>" +
	    e.titline + "</b></font><br>\n" +
	    e.authline+ "<br>\n" +
	    e.subjline+ "<br>\n" +
		"[" + a( urlAbstract(e.id), "View Details") + "]\n" +
	    "[" + a( urlPDF(e.id), "Download") + "]\n";
	s += "</div></div>\n";
	return s;*/

	String url =  cp + "/index.jsp";
	//	if (dfid>0) url += "?id="+dfid;
	if (plid>0) url += "?plid="+plid;
	url += "#article_" + e.id;
	
        String s = 
		//"<p style=\"background-color:#e5e5e5\">" +
		"<font size=\"3\">" +
	    e.i + ". " + 
	    //researcherSpan(rt)+ 
	    //e.idline + "; "+e.formatDate()+"\n" +
	    "<b>" +
	    e.titline + "</b></font><br>\n" +
	    e.authline+ "<br>\n" +
	    e.subjline+ "<br>\n" +
	    "[" + a( url, "View online") + "]\n";
		//s += "</p>\n";
		return s;	
    }

    private boolean needEmail(EntityManager em) {
	int emailDays = r.getEmailDays();
	if (emailDays==0) {
	    String msg = "User " + r + " opted out of email";
	    Logging.info(msg);
	    infomsg += msg;
	    return false;
	}
	PresentedList pl1 = PresentedList.findLatestEmailSugList(em,  uname);
	PresentedList pl2 = PresentedList.findLatestPresentedSugList(em,uname);
	if (pl1==null && pl2==null) {
	    String msg = "User " + r + " never was sent an email, or viewed the main page, before";
	    Logging.info(msg);
	    infomsg += msg;
	    return true;
	}


	Date threshold = SearchResults.daysAgo( emailDays );
	Date d1 = (pl1==null)? null: pl1.getTime();
	Date d2 = (pl2==null)? null: pl2.getTime();
	if (d1 != null && d1.after(threshold)) {
	    String msg = "User " + r + " was last sent an email on " + d1 +", which was less than " +  emailDays + " days ago; not eligible for new mail";
	    Logging.info(msg);
	    infomsg += msg;
	    return false;	    
	} else if (d2 != null && d2.after(threshold)) {
	    String msg ="User " + r + " last viewed the main page on " + d2 +", which was less than " +  emailDays + " days ago; not eligible for new mail";
	    Logging.info(msg);
	    infomsg += msg;
	    return false;	    
	}  else {
	    String msg ="User " + r + " was last sent an email on " + d1 +", or viewed the main page on " + d2 + ", which was more than " +  emailDays + " days ago; eligible for new mail";
	    infomsg += msg;
	    return true;	        
	}
    }

    /** Does it look like the user has a more or less valid email address? */
    static private boolean hasEmail(User user) {
	String email = user.getEmail();
	if (email==null) return false;
	return (email.indexOf("@") > 0);
    }

    private static void doAllEmails(EntityManager em, User.Program program, boolean force) {
	List<Integer> lu = User.selectByProgram(em, program);

	for(int uid: lu) {
	    try {
		User user = (User)em.find(User.class, uid);
		if (user==null) {
		    // this ought not happen in normal operation
		    Logging.info("User id " + uid + " is invalid!");
		    continue; 
		}
		if (!hasEmail(user)) {
		    Logging.info("No valid email address is stored for user " + user);
		    continue;
		}
		EmailSug q = new EmailSug(user);
		boolean must = force || q.needEmail(em);
		if (must && approvedOnly && !approvedUsersSet.contains(user.getUser_name())) {
		    must = false;
		    Logging.info("Email not sent to user " + user +", because he's not on the internal approved list");
		}

		if (must) {
		    boolean sent  = q.doEmailSug(em);
		    if (q.error || !sent) continue;
		} else {
		    Logging.info("No email needed for user " + user);
		}
	    } catch(Exception ex) {
		Logging.error(ex.toString());
		System.out.println(ex);
		ex.printStackTrace(System.out);
	    }
	}
    }

    /** On testing stage, only send emails to users in this list */
    static final private String[] approvedUsers = {"vmenkov", "tj"};
    static HashSet<String> approvedUsersSet= new HashSet<String>();
    static {
	for(String x: approvedUsers) approvedUsersSet.add(x);
    }
    
    static boolean approvedOnly = false;

    /** For testing */
    static public void main(String[] argv) throws Exception {

	
	ParseConfig ht = new ParseConfig();
	//int maxDocs = ht.getOption("maxDocs", -1);
	smtp = ht.getOption("smtp", smtp);
	dontSend = ht.getOption("dontSend", false);
	businessEmail = ht.getOption("from", businessEmail);
	approvedOnly =ht.getOption("approvedOnly", approvedOnly);
	boolean force =  ht.getOption("force", false);

	EntityManager em = Main.getEM();
	try {

	    if (argv.length ==0) {
		User.Program programs[] = { User.Program.PPP, User.Program.EE4};
		for( User.Program program: programs) {
		    Logging.info("Checking all users in program " + program);
		    doAllEmails( em,  program,  force);
		}
	    } else if (argv.length == 1) {
		String uname = argv[0];
		
		User r = User.findByName(em, uname);
		if (r==null) usage("User account '" +uname+ "' does not exist");
		EmailSug q = new EmailSug(r);
		boolean sent = q.doEmailSug(em);
	    } else 	{
		usage();
	    }
	} finally {
	    ensureClosed(em, false);
	}

    }

}
