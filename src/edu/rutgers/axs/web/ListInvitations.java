package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

public class ListInvitations extends ResultsBase {

    public Vector<Invitation> list = new Vector<Invitation>();

    public ListInvitations(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	try {
	    EntityManager em = sd.getEM();

	    Query q = em.createQuery("select m from Invitation m order by m.id");
	    for (Invitation m : (List<Invitation>) q.getResultList()) {
		list.add(m);
	    }

	    em.close(); 
	}  catch (Exception _e) {
	    setEx(_e);
	}
    }

}
