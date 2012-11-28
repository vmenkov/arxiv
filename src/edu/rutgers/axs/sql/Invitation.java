package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
//import javax.persistence.criteria.CriteriaBuilder;
import java.lang.reflect.*;
import java.lang.annotation.*;

import org.apache.catalina.realm.RealmBase;

import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.web.WebException;

@Entity  
  @Table( uniqueConstraints=@UniqueConstraint(name="invitation_code_cnstrt", columnNames="code") )
public class Invitation extends OurTable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
    	private long id;

  /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate PhoneCall entries with respondent entries,
      but has no meaning outside of it.
     */
    public long getId() {        return id;    }
    /** Don't use it. It's here just for reflection. */
    //private void setId(long x) {id=x;}

    static public final String registrationUrlBase = "../participation.jsp?code=";
    @Display(editable=false, order=2,  link=registrationUrlBase) 	
	@Column(length=48) 


	String code; 
    public  String getCode() { return code; }
    public void setCode(String x) { 	code = x;     }

    /** Into which research program will the users be enrolled? */
    @Display(editable=true, order=3, text="Into which research program will the users be enrolled?")
	@Column(nullable=false,length=24)
	@Enumerated(EnumType.STRING)     
	private User.Program program;

    public User.Program getProgram() { return program; }
    public void setProgram(User.Program x) { program=x; }


    /** Max number of users allowed to create accounts pursuant to this
	invitation. */
    @Basic  @Display(order=4,editable=true, text="What is the maximum number of accounts that can be created pursuant to this invitation?")
	@Column(nullable=false)
	private int maxUsers;
    public  int getMaxUsers() { return  maxUsers; }
    public void setMaxUsers( int x) {  maxUsers = x; }
 
    /** Expiration date of this invitation.
     */
    @Display(editable=true, order=5, text="No new accounts can be created pursuant this invitation on or after this date") 
	@Temporal(TemporalType.DATE)     @Column(nullable=false)
       Date expiration;
    public  Date getExpiration() { return expiration; }
    public void setExpiration(       Date x) { expiration = x; }

    @Basic @Display(order=6, editable=true, text="Set this to 'Yes' in order to allow users to use this invitation, or to 'No' to disable account creation. The flag will automatically flip to 'No' after the expiration date, or after the maxUsers limit has been achieved.") 
        @Column(nullable=false) boolean open;
    public  boolean getOpen() { return open; }
    public void setOpen( boolean x) { open = x; }

    public String reflectToString() {
	String s = Reflect.customizedReflect(this, 
					     new PairFormatter.CompactPairFormatter());
	return s;
    }

    /** The user name of the administrator who created the invitation */
    @Display(editable=false, order=7) 	@Column(length=15) 
	String Creator; 
    public  String getCreator() { return Creator; }
    public void setCreator(String x) { 	Creator = x;     }


   public static Invitation findByCode( EntityManager em, String code) {

	Query q = em.createQuery("select m from Invitation m where m.code=:c");
	q.setParameter("c", code);
	try {
	    return (Invitation)q.getSingleResult();
	} catch(NoResultException ex) { 
	    // no such invitation code
	    return null;
	}  catch(NonUniqueResultException ex) {
	    // this should not happen, as we have a uniqueness constraint
	    Logging.error("Non-unique invitation code: '"+code+"'!");
	    return null;
	}
    }

    /** Checks if the invitation still stands (based on the expiration date
	and the number of users already registered pursuant to it),
	and if it does not, closes it.
    */
    public boolean validityTest( EntityManager em) {
	if (!getOpen()) return false;
	if (getExpiration()==null || getExpiration().before(new Date()) ||
	    User.invitedUserCount(em, getId()) > getMaxUsers())  {
	    setOpen(false);
	    em.persist(this);	      
	    return false;
	}
	return true;	
    }



}