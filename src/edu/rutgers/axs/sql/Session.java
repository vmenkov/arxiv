package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.net.*;
import javax.persistence.*;
import javax.servlet.http.*;

import org.apache.lucene.index.*;

/** Information about an HttpSession stored in the SQL server. 

    <p> FIXME: We probably should also record the IP address from
    which the first request in the session came.  */
@Entity  
     public class Session extends OurTable 
{

    /** Persistent numeric id we associate with each session. This goes into
     Action logs etc. */
    @Id 
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Display(editable=false, order=1)
       private int id;

    public int getId() {        return id;    }
    /** Don't call this */
    public void setId(int x) {        id=x;    }

  
    /** Not used? */
    Session() {
    }

    /** Used to a create a Session object for a real session (in a web
	application) or a simulated session (in a command-line
	application)
	@param sess An underlying HttpSession object (or null, if in a
	command-line app)
    */
    public Session(HttpSession sess) {
	Date t = (sess==null)? new Date() :new Date(sess.getCreationTime());
	setStartTime(t);
    }

    @ManyToOne
    @Column(nullable=true)
      @Display(editable=false,order=3) 
	User user;

    public User getUser() {
	return user;
    }

    public void setUser(User c) {
	user=c;
    }
    
    /** The value from HttpSession.getCreationTime() */
   @Display(editable=false, order=2) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=false)
  	Date startTime;
    public Date getStartTime() { return startTime; }
    public void setStartTime( Date x) { startTime = x; }


    //    static public void main(String[] argv) throws IOException {   }

    public String toString() {
	return "[Recorded Session no. " + getId() +", created at " + getStartTime()+"]";
    }
}
