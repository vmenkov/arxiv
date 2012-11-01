package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.*;

/** The cumulative "vote" of given user in favor of a particular article,
    in terms of the   Exploration Engine.  */
@Entity  
    @Table(uniqueConstraints=@UniqueConstraint(columnNames={"aid","user"}), name="BernoulliVoteUniqueConstraint")
    public class BernoulliVote extends OurTable 
{

   @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
       private long id;

    /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate ListEntry entries with User entries) but has
      no meaning outside of it.
     */
    public long getId() {        return id;    }


  /** ArXiv Article ID.

	See 
http://openjpa.apache.org/builds/1.0.4/apache-openjpa-1.0.4/docs/manual/ref_guide_mapping_jpa.html#ref_guide_mapping_jpa_unique about the '@Index' and '@Unique' annotation.

<p>
 FIXME: when we have multiple clusters, we'll will have to switch to
 a composite unique index, like this:

*/
    @Basic      @Column(length=16)
	@Display(editable=false, order=2)
	String aid=null;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}

    /** User id (numeric) */
    @Basic 
	@Display(editable=false, order=3)
	int user;
    public int getUser() { return user ;}
    public void setUser(int x) { user =x;}

    @Basic 
	@Display(editable=false, order=4)
	int vote;
    public int getVote() { return vote ;}
    public void setVote(int x) { vote =x;}

}