package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** Edits an existing invitation entry, or creates a new one.

 */
public class EditInvitation extends ResultsBase {

    public static final String ID = "id";
    
    public long id=0;

    public Invitation o=null;
 

    public static enum Mode {
	EDIT, CREATE;
    }

    public EditInvitation.Mode mode;

    public EditInvitation(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);

	if (!runByAdmin()) {
	    error = true;
	    errmsg = "Only administrators can use this tool\n";
	    return;
	}

	id = getLong(EditInvitation.ID, 0);
	mode = (id<=0) ? EditInvitation.Mode.CREATE:  EditInvitation.Mode.EDIT;
	EntityManager em =null;
	try {

	    em = sd.getEM();
	    em.getTransaction().begin();

	    if (id<=0) {
		// Creation mode
		o = new Invitation();
	    } else {
		// Update mode
		o = (Invitation)em.find(Invitation.class, id);
		if (o==null) {
		    throw new WebException("Invitation no. " + id + " does not exists");
		}
	    }

	    // set various fields
	    editInvitation(o, request);
	    if (error) return;


	    em.persist(o);
	    em.getTransaction().commit();

	} catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ensureClosed(em, false);
	}
   

    }    

   /** @return true if the user's preferences have changed in such a way that
	it may be appropriate to update the user's suggestion list.
     */
    private void  editInvitation(Invitation o, HttpServletRequest request)
    throws IllegalInputException {

	try {
	    Tools.editEntity(EntryFormTag.PREFIX, o, request);
	    if (mode == Mode.CREATE) {		
		String newCode = "" + o.getProgram() + "_" + 
		    ResetPassword.generatePassword() +
		    ResetPassword.generatePassword();
		o.setCode(newCode);
	    }
	} catch(IllegalAccessException ex) {
	    setEx(ex);
	} catch( java.lang.reflect.InvocationTargetException ex) {
	    setEx(ex);
	}		
    }



}