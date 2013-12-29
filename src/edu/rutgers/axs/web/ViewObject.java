package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Retrieves an object such as a historical presented list
 */
public class ViewObject extends ResultsBase {
     
    public OurTable li=null;

    public ViewObject(HttpServletRequest _request, HttpServletResponse _response) {
	this(_request,  _response, null);
    }


    public ViewObject(HttpServletRequest _request, HttpServletResponse _response, Class c) {
	super(_request,_response);
	
	//	actorUserName = self ? user :  getString(USER_NAME, null);
	long listId = getLong("id", -1);

	EntityManager em = sd.getEM();
	try {
	    if (c==null) {
		String cName = getString("class", null);
		if (cName==null) throw new WebException("No class specified");
		if (cName.indexOf(".")<0) {
		    cName = "edu.rutgers.axs.sql." + cName;
		}
		c = Class.forName(cName);
	    }

	    if (listId > 0) {
		li = (OurTable)em.find((Class<? extends OurTable>)c, listId);
		if (li==null)  throw new WebException("No object of the type "+c+
						  " with id=" + listId + " has been found");
	    } else if (c.equals(User.class)) {
		String name = getString("name", null);
		if (name != null) {
		    li = User.findByName(em, name);
		} else throw new WebException("Object id (or name) not specified");
	    } else {
		throw new WebException("Object id (or name) not specified");
	    }

	    li.fetchVecs();
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    em.close(); 
	}
    }

}
