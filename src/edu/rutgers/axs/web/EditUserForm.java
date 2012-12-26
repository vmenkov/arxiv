package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;

import edu.rutgers.axs.html.Html;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.recommender.*;

/**  */
public class EditUserForm extends PersonalResultsBase {

    public  EditUserForm(HttpServletRequest _request, HttpServletResponse _response) {
	super(_request,_response);
	if (error) return;
    }


    /** Extra form for EE4 users. This is only used by editFormUserSelf.jsp, and
     really can be moved there. */
    public String ee4form() {
	// Logging.info("EUF: Displaying EE4User object with id=" + ee4u.getId() +", cc=" + ee4u.getCCode());
       return  Tools.mkSelector(EditUser.EE4_PREFIX + "cCode", EE4User.CCode.class, ee4u.getCCode());
    }

}
