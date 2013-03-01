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
    public String email;
    public String uname;

    /** For command-line testing on machines that don't allow email sending */
    private static boolean dontSend = false;

    /** Email the user his suggestion list. */
    public EmailSug(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	try {
	    email=getString("email",null);
	    if (email==null) {
		error=true;
		errmsg = "No email supplied";
		return;
	    } else if (email.indexOf("@")<=0) {
		error=true;
		errmsg = "Invalid email address: '"+email+"'";
		return;
	    }

	    uname = getString(USER_NAME,null);
	    if (uname==null) {
		error=true;
		errmsg = "No user name supplied";
		return;
	    }

	    doEmailSug(sd.getEM(), uname);
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {}
    }

    /** Email the user his suggestion list.
	
	@param uname The name of the user whose password is to be reset.
     */
    static void doEmailSug(EntityManager em, String uname) throws WebException, javax.mail.MessagingException,  javax.mail.internet.AddressException, Exception   {

	try {
	    // Begin a new local transaction 
	    em.getTransaction().begin();
	    
	    User r = User.findByName(em, uname);

	    if (r==null) throw new WebException("There is no account with the user name '" + uname + "' in our records");

	    if (r.getEmail()==null) throw new WebException("There is no email address corresponding to the user name '" + uname + "' in our records"); 

            String email = r.getEmail();
	    
	    if (!r.isEnabled())  throw new WebException("User account with the name '" + uname + "' is currently disabled. If this is your account, please contact a site administrator to enable it and reset the password");

	    Logging.info("Sending email to " + email);

	    if (dontSend) {		
		System.out.println("Not sending email (dontSend flag on)");
	    } else {
		sendMail(uname, email);
	    }
	    // don't commit until an email message has been sent!
	    em.persist(r);
	    em.getTransaction().commit();

	} finally {
	    ensureClosed(em, false);
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


    /** Sends a message to the customer service */
    static private void sendMail(String uname, String email)
	throws javax.mail.MessagingException,  javax.mail.internet.AddressException, Exception {

	//	try {


	    Properties props = System.getProperties();
	    // XXX - could use Session.getTransport() and Transport.connect()
	    // XXX - assume we're using SMTP
	    String mailhost = smtp;
	    if (mailhost != null)
		props.put("mail.smtp.host", mailhost);


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

	    String text = "<p>My.ArXiv has new recommendations for you.</p>";

		// Get the suggestion list for this user.
		boolean dryRun = true;
		ViewSuggestions vs = new ViewSuggestions(uname, dryRun);
		SearchResults sr = vs.sr;
		int i = 1;
		String firstArticle = "";
		String link = "http://my.arxiv.org/arxiv/#MIDDLE";
		String accountSettingLink = "http://my.arxiv.org/arxiv/personal/editUserFormSelf.jsp";
		for( ArticleEntry e: sr.entries) {
			if(i == 1){
				firstArticle  = e.titline;
				String s =   vs.formatArticleEntryForEmail( e);
				text = text + s;
				i++;
			}
			else{
				String s =   vs.formatArticleEntryForEmail( e);
				text = text + "<br>" + s;
				i++;
			}
			/*text = text + "<p>" 
				+ i + ".<br>" + "<a href=" + link + ">" + e.titline + "</a>" + "<br>" 
				+ e.authline + "<br>Subjects: " + e.subjline + "</p>";*/
			
		}

		String subject = "My.ArXiv recommends \"" + firstArticle + "\"";
	    
	    msg.setSubject(subject);
		
		text = text + "<p>"
					+ "<a href=" + link + ">" + "Update Ranking to Most Current Recommendations" + "</a></p>"
					+ "<p><br>You can " 
					+ "<a href=" + accountSettingLink + ">" + "unsubscribe from these emails" + "</a>" 
					+ " or change your " 
					+ "<a href=" + accountSettingLink + ">" + "account settings" + "</a>"  
					+ ".";
		text = text + "<br>Please note that this message was sent to the following e-mail address: " + email +"</p>";
	    
	    msg.setContent(text, "text/html");
 
	    String mailer = firm + " Web Server";
	    msg.setHeader("X-Mailer", mailer);
	    msg.setSentDate(new java.util.Date());
	    
	    // send the thing off
	    Transport.send(msg);
	    
	    //System.out.println("\nMail was sent successfully.");

	    /*
	} catch (Exception e) {
	    error = true;
	    errmsg = e.toString();
	    this.e = e;
	    //e.printStackTrace();
	}
	    */

    }

    static void usage() {
	usage(null);
    }


    static void usage(String m) {
	System.out.println("Email Suggestion List Utility");
	System.out.println("Usage: java [options] emailSug username");
	/*
	System.out.println("Optons:");
	System.out.println(" [-Dtoken=xxx]");
	*/
	if (m!=null) {
	    System.out.println(m);
	}
	System.exit(1);
    }


    /** For testing */
    static public void main(String[] argv) throws Exception {

	ParseConfig ht = new ParseConfig();
	//int maxDocs = ht.getOption("maxDocs", -1);
	smtp = ht.getOption("smtp", smtp);
	dontSend = ht.getOption("dontSend", false);
	businessEmail = ht.getOption("from", businessEmail);

	if (argv.length != 1) usage();
	String uname = argv[0];

	EntityManager em = Main.getEM();
	doEmailSug(em, uname);
    }

}
