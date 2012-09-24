package edu.rutgers.axs.web;


import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
//import java.lang.reflect.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import javax.mail.*;
import javax.mail.internet.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.ParseConfig;


/** Resets the user's password and lets him know the new password.

  @author Vladimir Menkov
  @date Nov-14-2001 */


public class ResetPassword extends ResultsBase {
    public String email;
    public String uname;
    //public boolean passwordReset=false;

    /** For command-line testing on machines that don't allow email sending */
    private static boolean dontSend = false;

    /** sets the new password and sends it to the user by e-mail */
    public ResetPassword(HttpServletRequest _request, HttpServletResponse _response) {
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

	    doResetPassword(sd.getEM(), uname, email, null);
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {}
    }

    /** Resets the password for a specified user, and sends the new
	password to him by email.
	
	@param uname The name of the user whose password is to be reset.
	@param email The email address to which the new password will be set. This must match the address in the user's database record.
	@param password If not null, this will be the new password; otherwise, the new password will be generated randomly. 
     */
    static void doResetPassword(EntityManager em, String uname, String email, String password) throws WebException, javax.mail.MessagingException,  javax.mail.internet.AddressException   {

	try {
	    // Begin a new local transaction 
	    em.getTransaction().begin();
	    
	    User r = User.findByName(em, uname);

	    if (r==null) throw new WebException("There is no account with the user name '" + uname + "' in our records");

	    if (r.getEmail()==null || !r.getEmail().equals(email)) throw new WebException("There is no account with the user name '" + uname + "' and email address '" + email + "' in our records"); 
	    
	    if (!r.isEnabled())  throw new WebException("User account with the name '" + uname + "' is currently disabled. If this is your account, please contact a site administrator to enable it and reset the password");

	    if (password==null) {  password = generatePassword(); }
	    System.out.println("New password=" + password);

	    r.encryptAndSetPassword( password);
	    Logging.info("Reset password for user " + uname + "; sending email to " + email);

	    if (dontSend) {		
		System.out.println("Not sending email (dontSend flag on)");
	    } else {
		sendMail(uname, email, password);
	    }
	    // don't commit until an email message has been sent!
	    em.persist(r);
	    em.getTransaction().commit();

	} finally {
	    ensureClosed(em, false);
	}
		
    }

    /** Generates the new password, randomly */
    private static String generatePassword() {
	StringBuffer p = new StringBuffer(6);
	for(int i=0; i<6; i++) {
	    int z = (int)(36 * Math.random());
	    char c = (z < 10) ? 
		(char)('0' + z) :    
		(char)('a' + (z - 10));
	    p.append(c);		    
	}
	return p.toString();
    }


    /**  Sending mail.
	 The hostname for the SMTP server. The typical values are "smtp" (on
	 Telus ADSL machines) or "localhost" (on hosting.ca, or on cactuar).  
    */
    //    static String smtp = "smtp";
    static String smtp = "localhost";
    //    static String businessEmail = "vmenkov@gmail.com";
    public static String businessEmail = "vmenkov@rci.rutgers.edu";

    /** Sends a message to the customer service */
    static private void sendMail(String uname, String email, String password)
	throws javax.mail.MessagingException,  javax.mail.internet.AddressException {

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
	    
	    String subject = "New password with My.ArXiv";
	    
	    msg.setSubject(subject);
	    
	    String firm = "My.ArXiv";

	    String text = 
	    "At your request, we have reset your password for accessing \n" +
	    "the " + firm + " web site. The new password for account '"+uname+"' is:\n"+
	    "\n" +
	    password +
	    "\n" +
	    "This message has been automatically generated by the\n" + 
	    firm + " Web Server. Thank you.\n";
	    
	    msg.setText(text);
 
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
	System.out.println("Reset Password Utility");
	System.out.println("Usage: java [options] ResetPassword username email");
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

	if (argv.length<2) usage();
	String uname = argv[0];
	String email = argv[1];
	
	String password = (argv.length < 3)? null: argv[2];
	EntityManager em = Main.getEM();
	doResetPassword(em, uname, email, password);
    }

}
