package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

@SuppressWarnings("unchecked")
public class ListUsers extends ResultsBase {

    public Vector<User> list = new Vector<User>();

    /** A field in the "create new user" section */
    public String enterProgramRow = "";

    public ListUsers(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	Logging.info("LU  sd=" + sd);

	try {
	    EntityManager em = sd.getEM();

	    Query q = em.createQuery("select m from User m order by m.user_name");
	    for (User m : (List<User>) q.getResultList()) {
		list.add(m);
	    }

	    em.close(); 

	    Reflect.Entry e= Reflect.getReflect(User.class).getEntry("program");
	    enterProgramRow = EntryForms.mkTableRow(EntryFormTag.PREFIX,null,e);


	}  catch (Exception _e) {
	    setEx(_e);
	}
    }

}
