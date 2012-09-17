package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
//import java.lang.reflect.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Retrieves a historical presented list
 */
public class ViewPresentedList extends ResultsBase {
    
    //public final String actorUserName;
    //public User actor;
    public PresentedList li=null;
    //public Vector<PresentedListEntry> docs = new  Vector<PresentedListEntry>();

    //final public static String USER_NAME = "user_name";

    public ViewPresentedList(HttpServletRequest _request, HttpServletResponse _response) {
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

	    li = (PresentedList)em.find(PresentedList.class, listId);
	    if (li==null)  throw new WebException("No PresentedList with id=" + listId + " has been found");

	    li.fetchVecs();

	    //    for (PresentedListEntry m : li.getDocs()) {
	    //	docs.add(m);
	    //}

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

}
