package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

@SuppressWarnings("unchecked")
public class ListInvitations extends ResultsBase {

    public Vector<Invitation> list = new Vector<Invitation>();

    public HashMap<Long,Integer> userCounts = new  HashMap<Long,Integer>();

    public ListInvitations(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	try {
	    EntityManager em = sd.getEM();

	    Query q = em.createQuery("select m from Invitation m order by m.id");
	    for (Invitation m : (List<Invitation>) q.getResultList()) {
		list.add(m);
	    }

	    userCounts = User.invitedUserCounts(em);

	    em.close(); 
	}  catch (Exception _e) {
	    setEx(_e);
	}
    }

    public int userCnt(long id) {
	Integer q = userCounts.get(id);
	return q==null? 0: q.intValue();
    }

}
