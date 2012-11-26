package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;


public class EditInvitationForm extends ResultsBase {

    public long id=0;

    public Invitation o=null;


    public EditInvitationForm(HttpServletRequest _request, HttpServletResponse _response) { //, EditInvitation.Mode mode) {
	super(_request,_response);

	if (!runByAdmin()) {
	    error = true;
	    errmsg = "Only administrators can use this tool\n";
	    return;
	}

	id = getLong(EditInvitation.ID, 0);
	EditInvitation.Mode mode = (id<=0) ? EditInvitation.Mode.CREATE:
	    EditInvitation.Mode.EDIT;

	try {

	    if (id<=0) {
	    // Blank form
		o = new Invitation();
	    } else {
		EntityManager em = sd.getEM();
		o = (Invitation)em.find(Invitation.class, id);
	    }
	} catch (Exception _e) {
	    setEx(_e);
	} 
    


    }    

    public String entryForm() throws WebException {
	StringBuffer s=new StringBuffer();

	try {
	    Class c = Invitation.class;

	    s.append("<table border=1>");
	    s.append("<tr><th>Invitation details</th></tr>");
	    
	    for(Reflect.Entry e: Reflect.getReflect(c).entries) {
		if (!e.editable) continue;
		s.append( EntryForms.mkTableRow(EntryFormTag.PREFIX, o,e));
	    }
	    s.append("</table>");
	} catch (Exception ex) {
	    System.out.println(ex);
	    ex.printStackTrace(System.out);
	    throw new WebException("IO problems: " + ex);
	} 
	
	return s.toString();
    }

}