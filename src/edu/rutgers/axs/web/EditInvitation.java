package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import java.net.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;

/** An administartor-level tool for creating an new invitation, or modifiying 
    an existing one.
 */
public class EditInvitation extends ResultsBase {

    public static final String ID = "id";
    
    /** Invitation id. */
    public long id=0;

    public Invitation o=null;
    
    public int userCnt = 0;

    public String regUrl = "?";
    
    Vector<String> warnings = new  Vector<String>();
    
    /** What is being done now? */
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
	infomsg += "EditInvitaion: mode=" + mode+"\n";
	EntityManager em =null;
	try {

	    em = sd.getEM();
	    em.getTransaction().begin();

	    if (mode==EditInvitation.Mode.CREATE) {// Creation mode
		o = new Invitation();
		o.setCreator(user);
	    } else {		// Update mode
		o = (Invitation)em.find(Invitation.class, id);
		if (o==null) {
		    throw new WebException("Invitation no. " + id + " does not exists");
		}
		userCnt = User.invitedUserCount(  em, id);
	    }

	    // set various fields
	    editInvitation(o, request);
	    if (error) return;

	    if (o.getMaxUsers()<0) o.setMaxUsers(0);

	    o.validityTest(em,false); // no nested persist is needed here

	    em.persist(o);
	    em.getTransaction().commit();

	    id = o.getId();

	    if (o.getMaxUsers()<=0) {
		warnings.add("This invitation has the limit of 0 users; no accounts can be created\n");
	    }

	    if (mode==EditInvitation.Mode.CREATE) {
		infomsg += "id was " + id + "\n";
		// read the object back, to get the new ID (?)
		System.out.println("Created a new invitation with id=" + id);
	    }

	    regUrl = Invitation.registrationUrlBase +  o.getCode();
	    URL full = new URL(thisUrl(), regUrl);
	    regUrl = full.toString();

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