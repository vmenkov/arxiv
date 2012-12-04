package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

public class Participation extends ResultsBase {

    Invitation inv = null;
    public String code=null;
    public boolean bernoulli = false;

    public Participation(HttpServletRequest _request, HttpServletResponse _response) {
	this( _request, _response, EditUser.Mode.CREATE_SELF);
    }

    /** Checks the invitation code, and the invitation rules.
	@param mode Checks are skipped unless this is EditUser.Mode.CREATE_SELF
     */
    Participation(HttpServletRequest _request, HttpServletResponse _response,  EditUser.Mode mode) {
	super(_request,_response);

	// Codes, invitations, etc. only apply in the CREATE_SELF mode
	if (mode != EditUser.Mode.CREATE_SELF) return;


	code = request.getParameter("code");   
	if (isBlank(code)) code=null;


	if (code==null) {
	    error=true;
	    errmsg="Presently, new user enrollment is by invitation only. You need to receive an invitation, and then register using the URL provided";
	    return;
	}
	EntityManager em=null;
	try {
	    em = sd.getEM();
	    inv = Invitation.findByCode(em, code); 
	    if (inv==null) {
		error=true;
		errmsg="Invitation code " + code + " is not valid";
		return;		
	    }
	    if (!inv.validityTest(em)) {
		error=true;
		errmsg="We are sorry, but invitation no. " + inv.getId() + " (code=" + inv.getCode() + ") is presently closed, either because we have already recruited the desired number of users, or because the enrollment period has ended. You may want to receive a new invitation from the organizers of the experiment";
		return;		
	    }

	    bernoulli = inv.getProgram().needBernoulli();

	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ensureClosed(em);
	}
    }


}
