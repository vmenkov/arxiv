package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Adds a new User entry to the database, or edits an existing one.
    This depends on whether a (non-negative) id has been supplied.
 */
public class AddUser extends ResultsBase {

    public User reRead;

    public AddUser(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	if (error) return;
	// if nonnegative, it means that we're updating an entry
	String uname = getString("user_name", "").trim();

	if (uname.equals("")) {
	    error = true;
	    errmsg = "Username must be provided";
	    return;
	}

	try {

	    EntityManager em = sd.getEM();
 
	    // Begin a new local transaction so that we can persist a new entity
	    em.getTransaction().begin();
	    
	    User r = User.findByName(em, uname);
	    if (r==null) {
		error = true;
		errmsg = "Can't edit user '"+uname+"', because that username does not exist";
	    }

	    editUser(r, request);
	    if (error) return;

	    StringBuffer b = new 	StringBuffer();
	    if (!r.validate(em,b)) {
		error = true;
		errmsg = b.toString();
		return;
	    }

	    em.persist(r);
	    em.getTransaction().commit();
	    em.close();
	    
	    // read back
	    em = sd.getEM();	    
	    reRead = User.findByName(em, uname);
	    em.close();
	    
	    if (reRead==null) {
		error = true;
		errmsg = "Could not re-read the modified entry with user_name=" + uname;
	    }
	}  catch (Exception _e) {
	    setEx(_e);
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

    final public static String ENABLED = "enabled", NEW_PASSWORD = "password";

    public static String mkRadioSet(User u) {
	boolean en = u.isEnabled();
	return 
	    Tools.radio(ENABLED, PseudoBoolean.True,"enabled", en) + 
	    Tools.radio(ENABLED, PseudoBoolean.False,"disabled", !en);

    }


}
