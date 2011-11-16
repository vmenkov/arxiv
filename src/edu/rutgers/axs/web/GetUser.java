package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Looks up an existing user record, or creates a new one, as requested.
    Usage: <icd:UserEntryForm user_name="<%=id%>"/>
 */
public class GetUser extends ResultsBase {

    public User u;

    final public static String CREATE = "create";

    public GetUser(HttpServletRequest _request, HttpServletResponse _response) {
	/*
	this(_request,  Tools.getBoolean(request, CREATE, false));
    }

    GetUser((HttpServletRequest _request, HttpServletResponse _response), boolean create) {
	*/
	super(_request,_response);
	boolean create= Tools.getBoolean(request, CREATE, false);

	try {
	    EntityManager em = sd.getEM();

	    String uname = request.getParameter(EditUser.USER_NAME);	    
	    if (create) {
		em.getTransaction().begin();
		u = User.findByName(em, uname);
		if (u != null)  {
		    error = true;
		    errmsg = "Cannot add a new user named '"+ uname+"', as a user record with this name already exists!";
		} else {
		    u = new User();
		    u.setUser_name(uname);
		    u.disable();
		    em.persist(u);
		}
		em.getTransaction().commit();
	    } else {
		u = User.findByName(em, uname);
		if (u==null) {
		    error = true;
		    errmsg = "No user exists: " + uname;
		}
	    }

	    em.close(); 
	}  catch (Exception _e) {
	    setEx(_e);
	}
    }

    /** Creates set of radio buttons reflecting the user's current status */
    public  String mkRadioSet() {
	boolean en = u.isEnabled();
	return 
	    Tools.radio(EditUser.ENABLED, new Boolean(true),"enabled", en) + 
	    Tools.radio(EditUser.ENABLED, new Boolean(false),"disabled", !en);
    }

    /** Creates set of radio buttons reflecting the user's current roles */
    public String mkRoleBoxes() {
	StringBuffer b = new StringBuffer();
	for(Role.Name name: Role.Name.class.getEnumConstants()) {
	    b.append( Tools.checkbox(EditUser.ROLE_PREFIX + name, "on", name.toString(), u.hasRole(name)) + "<br>\n");
	}
	return b.toString();
    }


}
