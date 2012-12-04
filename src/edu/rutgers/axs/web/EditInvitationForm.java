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
    public int userCnt = 0;
    EditInvitation.Mode mode;

    public EditInvitationForm(HttpServletRequest _request, HttpServletResponse _response) { //, EditInvitation.Mode mode) {
	super(_request,_response);

	if (!runByAdmin()) {
	    error = true;
	    errmsg = "Only administrators can use this tool\n";
	    return;
	}

	id = getLong(EditInvitation.ID, 0);
	mode = (id<=0) ? EditInvitation.Mode.CREATE: EditInvitation.Mode.EDIT;
	EntityManager em=null;
	try {

	    em = sd.getEM();
	    if (id<=0) {
	    // Blank form
		o = new Invitation();
	    } else {
		o = (Invitation)em.find(Invitation.class, id);
	    }
	    userCnt = User.invitedUserCount(  em, id);
	} catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ensureClosed(em, false);
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

	    if (mode == EditInvitation.Mode.CREATE) {
		
		// code prefix
		s.append("<tr>");
		s.append("<td valign=top>Code prefix. This will be concatenated with a random string to form the invitation code, which you then will email to prospective users. If you leave this field blank, the experiment name will be used as the code prefix.</td>\n");
		s.append("<td>");
		s.append(Tools.inputText(EditInvitation.CODE_PREFIX, 20));
		s.append("</td>\n");
		s.append("</tr>\n");
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