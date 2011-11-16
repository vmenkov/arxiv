package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Retrieves the action history of a user
 */
public class ViewActions extends ResultsBase {
    
    public final String actorUserName;
    public User actor;

    public Vector<Action> list = new Vector<Action>();
    public Vector<EnteredQuery> qlist = new Vector<EnteredQuery>();

    final public String USER_NAME = "user_name";

    public ViewActions(HttpServletRequest _request, HttpServletResponse _response, boolean self) {
	super(_request,_response);

	actorUserName = self ? user :  getString(USER_NAME, null);

	EntityManager em = sd.getEM();
	try {

	    if (actorUserName==null) throw new WebException("No user name specified!");

	    actor = User.findByName(em, actorUserName);
  
	    if (actor == null) {
		error = true;
		errmsg = "No user with user_name="+ actorUserName+" has been registered";
		return;
	    }

	    for (Action m : actor.getActions()) {
		list.add(m);
	    }
	    for (EnteredQuery m : actor.getQueries()) {
		qlist.add(m);
	    }

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

}
