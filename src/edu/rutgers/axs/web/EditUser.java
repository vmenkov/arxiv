package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Edits an existing user entry. Only admin-level users can modify
    other user's entries. 

 <p>There is also a mode for creating a new entry and filling the
 fields right away. This is used as a self-service tool for new users.
 */
public class EditUser extends ResultsBase {

    /** The user name of the record to modify. It may be supplied with
	the request, or, in the case of a user editing his own record,
	is inferred from the session data.
     */
    public final String uname;
    /** The user entry being modified or created created */
    public User r=null;

    public static enum Mode {
	EDIT_ANY, EDIT_SELF, CREATE_SELF;
    }



    /** @param selfForm Set this to true when invoking this
	constructor from the form for editing one's own entry (the
	only form that can be used by non-admins)
	@param create Set this param  to true (and selfForm=false)
	when invoking the constructor from the page that creates
	a new user entry and fills its fields at the same time.
     */
    public EditUser(HttpServletRequest _request, HttpServletResponse _response, Mode mode) {
	super(_request,_response);
	
	uname = (mode==Mode.EDIT_SELF) ? user: 
	    Tools.getString(request,USER_NAME, "").trim();
	
	if (uname.equals("")) {
	    error = true;
	    errmsg = "Username must be provided";
	    return;
	}

	boolean self = uname.equals(user);

	if (!runByAdmin() && mode==Mode.EDIT_ANY) {
	    error = true;
	    errmsg = "You, user '" + user + "' don't have the 'admin' role, and cannot modify other users' entries";
	    return;
	}

	EntityManager em=null;
	try {

	    em = sd.getEM();
 
	    // Begin a new local transaction so that we can persist a new entity
	    em.getTransaction().begin();
	    
	    r = User.findByName(em, uname);

	    if (mode==Mode.CREATE_SELF) {
		if (r!=null) {
		    error = true;
		    errmsg = "Cannot create a user account named '"+uname+"', because the name is already in use";
		    return;
		} else {
		    r = new User();
		    r.setUser_name(uname);
		    r.disable();
		}
	    } else {
		if (r==null) {
		    error = true;
		    errmsg = "Can't edit user '"+uname+"', because that username does not exist";
		    return;
		}
	    }

	    // set various fields
	    editUser(r, request);
	    if (error) return;

	    StringBuffer b = new 	StringBuffer();
	    if (!r.validate(em,b)) {
		error = true;
		errmsg = b.toString();
		return;
	    }

	    boolean wasEnabled = r.isEnabled();
	    String pass = Tools.getString(request,NEW_PASSWORD, "");
	    String passRetyped = Tools.getString(request,NEW_PASSWORD_RETYPED, "");
	    boolean reqEnable = (request.getParameter(ENABLED)!=null);
	    boolean enable = Tools.getBoolean(request, ENABLED, false);

	    if (mode==Mode.EDIT_SELF) {
		// change password (optionally)
		if (!validateAndSetPassword( pass, passRetyped, false)) return;
	    } else if ( mode==Mode.CREATE_SELF) {
		// Must set password
		if (!validateAndSetPassword( pass, passRetyped,true))return;
		// Self-service creation always sets only the subscriber role
		Role role = (Role)em.find(Role.class, Role.Name.subscriber);
		r.addRole(role);
	    } else if (!reqEnable) {  	
		error = true;
		errmsg = "What has happened to the enable/disable buttons?";
		return;		
	    } else {
		// runByAdmin(), Enable or Disable button checked

		// Set roles
		for(Role.Name name: Role.Name.class.getEnumConstants()) {
		    String sent=request.getParameter(EditUser.ROLE_PREFIX+name);
		    Role role = (Role)em.find(Role.class, name);
		    if (sent == null) {
			r.removeRole(role);
		    } else {
			r.addRole(role);
		    }
		}
		
		boolean pwSet = false;
		
		if (pass.length() + passRetyped.length() >0) {
		    // pw entered
		    if (!pass.equals(passRetyped)) {
			error = true;
			errmsg = "Password in the 'retype' box does not match the one in the box above. Please go back and correct that";
			return;
		    } else if (!enable) {
			error = true;
			errmsg = "Password should NOT be entered if you want to disable login for the user!";
			return;			
		    }
		    r.encryptAndSetPassword( pass );
		    Logging.info("Admin '"+user+"' has set password and enabled logins for user "+uname);
		    pwSet = true;
		} else {
		    // no pw entered
		    if (enable) {
			if (wasEnabled) { 
			    // no change, nothing to do
			} else {
			    error = true;
			    errmsg = "Password SHOULD be entered if you want to enable login for the user!";
			    return;			
			}
		    } else {
			// disable
			r.disable();
			Logging.info("Admin '"+user+"' has disabled logins for user " + uname);
		    }
		} 	  
	    }

	    // only SOME forms enter this, namely, participation_login.html
	    String confirmEmail = Tools.getString(request,CONFIRM_EMAIL, null);
	    if (confirmEmail!=null &&  
		!confirmEmail.equals( Tools.getString(request,EMAIL, null))) {
		error = true;
		errmsg = "The email address should be entered correctly in both boxes";
		return;	
	    }

	    // saving data into the database
	    em.persist(r);
	    em.getTransaction().commit();
	    em.close();
	    
	    // read back
	    em = sd.getEM();	    
	    r = User.findByName(em, uname);
	    em.close();
	    
	    if (r==null) {
		error = true;
		errmsg = "Could not re-read the modified entry with user_name=" + uname;
	    }
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ensureClosed(em, false);
	}
    }

    private void editUser(User r, HttpServletRequest request)
    throws IllegalInputException {

	try {
	    Tools.editEntity(EntryFormTag.PREFIX, r, request);
	} catch(IllegalAccessException ex) {
	    setEx(ex);
	} catch( java.lang.reflect.InvocationTargetException ex) {
	    setEx(ex);
	}	
    }

    /** Various params that may come from HTML forms */
    final public static String 
	USER_NAME = "user_name",
	ENABLED = "enabled", 
	NEW_PASSWORD = "password", 
	NEW_PASSWORD_RETYPED = "confirm_password",
	EMAIL = EntryFormTag.PREFIX + "email",
	CONFIRM_EMAIL = "confirm_email",
	ROLE_PREFIX = "role."
	;

    public static String pwTable() {
	return 
	    "<table><tr><td>New password: <td>" + 
	    Tools.inputText(NEW_PASSWORD, null, 32) + "</tr>\n" +
	    "<tr><td>Retype new password: <td>" + 
	    Tools.inputText(NEW_PASSWORD_RETYPED, null, 32) + "</tr></table>";
    }

    /** @return false if error is set */
    private boolean validateAndSetPassword(String  pass, String passRetyped,
					boolean mustEnter) {

	final int M=5;
	if (pass.length() + passRetyped.length() ==0) {
	    if (mustEnter) {
		error = true;
		errmsg = "No password entered.  Please go back and enter a password.";
		return false;
	    } else {
		return true;
	    }
	}

	if (!pass.equals(passRetyped)) {
	    error = true;
	    errmsg = "Password in the 'retype' box does not match the one in the box above. Please go back and correct that";
	    return false;
	} else if (pass.length() < M) {
	    error = true;
	    errmsg = "Password must be at least "+M+" characters long. Please go back and correct that";
	    return false;	    
	}
	r.encryptAndSetPassword( pass );
	Logging.info("User '"+uname+"' has set or changed own password");  
	return true;
    } 


}
