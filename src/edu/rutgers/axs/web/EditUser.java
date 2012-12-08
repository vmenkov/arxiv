package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;


import javax.servlet.*;
import javax.servlet.http.*;

import javax.persistence.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.Search;

/** Edits an existing user entry. Only admin-level users can modify
    other user's entries. 

 <p>There is also a mode for creating a new entry and filling the
 fields right away. This is used as a self-service tool for new users.
 */
public class EditUser extends Participation  {

     /** The user name of the record to modify. It may be supplied with
	the request, or, in the case of a user editing his own record,
	is inferred from the session data.
     */
    public final String uname;
    /** The user entry being modified or created created */
    public User r=null;

    public static enum Mode {
	EDIT_ANY, EDIT_SELF, CREATE_SELF;
    }

    /** Boolean parameter in user-creation process */
    public final static String SURVEY="survey";

    /** @param selfForm Set this to true when invoking this
	constructor from the form for editing one's own entry (the
	only form that can be used by non-admins)
	@param create Set this param  to true (and selfForm=false)
	when invoking the constructor from the page that creates
	a new user entry and fills its fields at the same time.
    */
    public EditUser(HttpServletRequest _request, HttpServletResponse _response, Mode mode) {
	super(_request,_response,mode);

	// Did we come from a CREATE_SELF form that talked about an agreement to pariticipate in a follow-up interview?
	boolean survey = getBoolean(EntryFormTag.PREFIX +EditUser.SURVEY,false);
	
	uname = (mode==Mode.EDIT_SELF) ? user: 
	    Tools.getString(request,USER_NAME, "").trim();
	
	if (uname.equals("")) {
	    error = true;
	    errmsg = "Username must be provided";
	    return;
	}
	if (error) return; // for the super()

	boolean self = uname.equals(user);

	if (!runByAdmin() && mode==Mode.EDIT_ANY) {
	    error = true;
	    errmsg = "You, user '" + user + "' don't have the 'admin' role, and cannot modify other users' entries";
	    return;
	}

	EntityManager em=null;
	try {

	    em = sd.getEM();
 
	    // Begin a new local transaction so that we can persist a new entity
	    em.getTransaction().begin();
	    
	    r = User.findByName(em, uname);

	    if (mode==Mode.CREATE_SELF) {
		if (r!=null) {
		    error = true;
		    errmsg = "Cannot create a user account named '"+uname+"', because the name is already in use";
		    return;
		} else {
		    r = new User();
		    r.setUser_name(uname);
		    r.disable();
		}
	    } else {
		if (r==null) {
		    error = true;
		    errmsg = "Can't edit user '"+uname+"', because that username does not exist";
		    return;
		}
	    }

	    // set various fields
	    boolean prefChanged = editUser(r, request);
	    if (error) return;

	    // In the CREATE_SELF mode, require the "code" parameter
	    if (mode==Mode.CREATE_SELF) {
		r.setInvitation( inv.getId());
		// code is already validated by the Participation() constructor
		User.Program program=inv.getProgram();
		if (program==null) {
		    error = true;
		    errmsg="Sorry, we are not very organized, and have not configured this invitation properly. Please report the problem to "+ResetPassword.businessEmail+", and try again later";
		    return;
		}
		r.setProgram(program);
	    }

	    StringBuffer b = new 	StringBuffer();
	    if (!r.validate(em,b)) {
		error = true;
		errmsg = b.toString();
		return;
	    }

	    boolean wasEnabled = r.isEnabled();
	    String pass = Tools.getString(request,NEW_PASSWORD, "");
	    String passRetyped = Tools.getString(request,NEW_PASSWORD_RETYPED, "");
	    boolean reqEnable = (request.getParameter(ENABLED)!=null);
	    boolean enable = Tools.getBoolean(request, ENABLED, false);

	    if (mode==Mode.EDIT_SELF) {
		// change password (optionally)
		if (!validateAndSetPassword( pass, passRetyped, false)) return;
	    } else if ( mode==Mode.CREATE_SELF) {
		// Must set password
		if (!validateAndSetPassword( pass, passRetyped,true))return;
		// Self-service creation always sets only the subscriber role
		Role role = (Role)em.find(Role.class, Role.Name.subscriber);
		r.addRole(role);
	    } else if (!reqEnable) {  	
		error = true;
		errmsg = "What has happened to the enable/disable buttons?";
		return;		
	    } else {
		// runByAdmin(), Enable or Disable button checked

		// Set roles
		for(Role.Name name: Role.Name.class.getEnumConstants()) {
		    String sent=request.getParameter(EditUser.ROLE_PREFIX+name);
		    Role role = (Role)em.find(Role.class, name);
		    if (sent == null) {
			r.removeRole(role);
		    } else {
			r.addRole(role);
		    }
		}
		
		boolean pwSet = false;
		
		if (pass.length() + passRetyped.length() >0) {
		    // pw entered
		    if (!pass.equals(passRetyped)) {
			error = true;
			errmsg = "Password in the 'retype' box does not match the one in the box above. Please go back and correct that";
			return;
		    } else if (!enable) {
			error = true;
			errmsg = "Password should NOT be entered if you want to disable login for the user!";
			return;			
		    }
		    r.encryptAndSetPassword( pass );
		    Logging.info("Admin '"+user+"' has set password and enabled logins for user "+uname);
		    pwSet = true;
		} else {
		    // no pw entered
		    if (enable) {
			if (wasEnabled) { 
			    // no change, nothing to do
			} else {
			    error = true;
			    errmsg = "Password SHOULD be entered if you want to enable login for the user!";
			    return;			
			}
		    } else {
			// disable
			r.disable();
			Logging.info("Admin '"+user+"' has disabled logins for user " + uname);
		    }
		} 	  
	    }

	    // only SOME forms enter this, namely, registration.jsp ( former participation_login.html)
	    String confirmEmail = Tools.getString(request,CONFIRM_EMAIL, null);
	    if (confirmEmail!=null &&  
		!confirmEmail.equals( Tools.getString(request,EMAIL, null))) {
		error = true;
		errmsg = "The email address should be entered correctly in both boxes";
		return;	
	    }

	    
	    if ( mode==Mode.CREATE_SELF && survey) {
		// if (!getApprovedAudio()) {	... }
		if (isBlank(r.getEmail()) && isBlank(r.getPhoneNumber())) {
		    error = true;
		    errmsg = "You have agreed to participate in a follow-up interview, but have not provided any contact information. Please go back and enter your email address and/or telephone number";
		    return;		    
		}
	    }

	    if (r.catCnt()==0 && !r.getProgram().needBernoulli()) {
		error = true;
		errmsg = "You must specify some categories of interest, in order for the system to be able to generate recommendations. Please go back and check boxes next to at least one category!";
		return;		    
	    }

	    int days = r.getDays();

	    // saving data into the database
	    em.persist(r);


	    // check if the invitation needs to be closed right away
	    if (inv!=null) {
		inv.validityTest(em);
	    }

	    em.getTransaction().commit();
	    
	    // read back
	    //em = sd.getEM();	    
	    //r = User.findByName(em, uname);
	    //em.close();
	    
	    //	    if (r==null) {
	    //		error = true;
	    //		errmsg = "Could not re-read the modified entry with user_name=" + uname;
	    //	    }

	    // Trying to update the profile and re-create sug list, 
	    // because the user's declared preferences have changed.
	    // We don't do it on initial creation (CREATE_SELF), because
	    // it is supposed to be taken care of in the Scheduler.
	    if (prefChanged && mode!=Mode.CREATE_SELF) {
		scheduleUpdate(em, uname, Task.Op.TJ_ALGO_2_USER_PROFILE, days);
		scheduleUpdate(em, uname, Task.Op.TJ_ALGO_1_SUGGESTIONS_1, days);
	    } else {
		Logging.info("There seem to be no need to schedule updates for user=" + uname +"; mode=" +mode+", prefChanged=" + prefChanged);
	    }
	    em.close();
	    
	}  catch (Exception _e) {
	    setEx(_e);
	} finally {
	    ensureClosed(em, false);
	}
    }

    /** @return true if the user's preferences have changed in such a way that
	it may be appropriate to update the user's suggestion list.
     */
    private boolean  editUser(User r, HttpServletRequest request)
    throws IllegalInputException {

	int changeCnt=0;
	try {
	    int days0 = r.getDays();
	    Tools.editEntity(EntryFormTag.PREFIX, r, request);
	    if (r.getDays()<=0 || r.getDays()>90) r.setDays(Search.DEFAULT_DAYS);
	    if (r.getDays() != days0)  changeCnt++;

	    // Set subject categories
	    Set<String> c = r.getCats();
	    if (c==null) c = new HashSet<String> ();
	    for(String name: Categories.listAllStorableCats()) {
		String sent=request.getParameter(EditUser.CAT_PREFIX+name);
		if (sent == null) {
		    if (c.contains(name)) {
			Logging.info("Removing cat " + name);
			c.remove(name);
			changeCnt++;
		    }
		} else {
		    if (!c.contains(name)) {
			Logging.info("Adding cat " + name);
			c.add(name);
			changeCnt++;
		    }
		}
	    }
	    r.setCats(c);
		
	} catch(IllegalAccessException ex) {
	    setEx(ex);
	} catch( java.lang.reflect.InvocationTargetException ex) {
	    setEx(ex);
	}		
	Logging.info("EditUser: changeCnt=" + changeCnt);
	return (changeCnt!=0);
	    
    }

    /** Various params that may come from HTML forms */
    final public static String 
	USER_NAME = "user_name",
	ENABLED = "enabled", 
	NEW_PASSWORD = "password", 
	NEW_PASSWORD_RETYPED = "confirm_password",
	EMAIL = EntryFormTag.PREFIX + "email",
	CONFIRM_EMAIL = "confirm_email",
	ROLE_PREFIX = "role.",
	CAT_PREFIX = "cat."
	;

    public static String pwTable() {
	return 
	    "<table><tr><td>New password: <td>" + 
	    Tools.inputText(NEW_PASSWORD, null, 32) + "</tr>\n" +
	    "<tr><td>Retype new password: <td>" + 
	    Tools.inputText(NEW_PASSWORD_RETYPED, null, 32) + "</tr></table>";
    }

    /** @return false if error is set */
    private boolean validateAndSetPassword(String  pass, String passRetyped,
					boolean mustEnter) {

	final int M=5;
	if (pass.length() + passRetyped.length() ==0) {
	    if (mustEnter) {
		error = true;
		errmsg = "No password entered.  Please go back and enter a password.";
		return false;
	    } else {
		return true;
	    }
	}

	if (!pass.equals(passRetyped)) {
	    error = true;
	    errmsg = "Password in the 'retype' box does not match the one in the box above. Please go back and correct that";
	    return false;
	} else if (pass.length() < M) {
	    error = true;
	    errmsg = "Password must be at least "+M+" characters long. Please go back and correct that";
	    return false;	    
	}
	r.encryptAndSetPassword( pass );
	Logging.info("User '"+uname+"' has set or changed own password");  
	return true;
    } 

    /** Schedules an update task of a requested type
     */
    private static boolean scheduleUpdate(EntityManager em, String uname, Task.Op taskOp, int days) {
	Task activeTask=null, queuedTask=null;
	List<Task> tasks = Task.findOutstandingTasks(em, uname, taskOp);
	if (tasks != null) {
	    for(Task t: tasks) {
		if (days>=0 && t.getDays()!=days) continue; // range mismatch
		if (t.appearsActive()) {
		    if ( activeTask==null) 	activeTask=t; 
		    //break;
		} else if (!t.getCanceled()) {
		    if (queuedTask==null) queuedTask = t;
		    //break;
		}
	    }
	}

	if (activeTask!=null) {
	    Logging.info("Did not schedule op="+taskOp+" for user " + uname +", because a task (no. "+activeTask.getId()+") is already in progress");
	    return false;
	} else if (queuedTask!=null) {
	    Logging.info("Did not schedule op="+taskOp+" for user " + uname +", because a task (no. "+queuedTask.getId()+") is already scheduled");
	    return false;
	} else {
	    em.getTransaction().begin();
	    Task newTask = new Task(uname, taskOp);
	    newTask.setDays(days);
	    //if (basedon!=null) newTask.setInputFile(basedon);
	    em.persist(newTask);
	    em.getTransaction().commit();
	    Logging.info( "Scheduled task="+newTask+" for user " + uname +", because preferences have changed" );
	    return true;
	}
    }

}
