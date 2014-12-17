package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.web.WebException;


/** User-specific information needed  for Exploration Engine ver 4 (EE5).
 */
@Entity 
    public class EE5User extends OurTable 
{
    /** The numeric id is the same value as for the corresponding User
	object. (A one-to-one relation).
     */
    @Id @Display(editable=false, order=1.1)
    	private int id;

    public void setId(int val) {        id = val;    }
    public int getId() {        return id;    }

    @ElementCollection
    //@CollectionTable(name="user_address")
	private Set<EE5Uci> uci; //  = new HashSet<EE5Uci>();

    public Set<EE5Uci> getUci() { return uci; }
    public void setUci( Set<EE5Uci> x) {  uci=x; }


    /** Looks up an existing entry, or creates and nitializes a new
	one.
	@param commit Use true unless this call is already enclosed inside
	a transaction begin/commit pair
    */
    static public EE5User getAlways(EntityManager em, int id, boolean commit) {
	EE5User a = (EE5User)em.find(EE5User.class,id);
	if (a !=null) return a;
	if (commit) em.getTransaction().begin();
	a = new EE5User();
	a.setId(id);
	em.persist(a);
	if (commit) em.getTransaction().commit();	
	return a;
    }

    public HashMap<Integer,EE5Uci> getUciAsHashMap() {
	HashMap<Integer,EE5Uci> h = new HashMap<Integer,EE5Uci>();
	for(EE5Uci w: getUci())  {
	    h.put( w.getClassId(), w);
	}
	return h;
    }


}
  