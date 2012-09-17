package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
//import java.lang.reflect.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Retrieves an object such as a historical presented list
 */
public class ViewObject extends ResultsBase {
    
    //public final String actorUserName;
    //public User actor;
    public OurTable li=null;

    //final public static String USER_NAME = "user_name";

    public ViewObject(HttpServletRequest _request, HttpServletResponse _response, Class c) {
	super(_request,_response);

	//	actorUserName = self ? user :  getString(USER_NAME, null);
	long listId = getLong("id", -1);
	if (listId<0) {
	    errmsg="PresentedList ID not specified";
	    error=true;
	    return;
	}

	EntityManager em = sd.getEM();
	try {
	    //main.request.getParameter(name) 
	    //if (actorUserName==null) throw new WebException("No user name specified!");
	    //	    actor = User.findByName(em, actorUserName);
  
	    //	    if (actor == null) {
	    //		error = true;
	    //		errmsg = "No user with user_name="+ actorUserName+" has been registered";
	    //		return;
	    //	    }

	    li = (OurTable)em.find(c, listId);
	    if (li==null)  throw new WebException("No object of the type "+c+
						  " with id=" + listId + " has been found");

	    li.fetchVecs();
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

}
