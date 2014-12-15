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

    //    @Basic 	double c;
    //    public double getC() { return c; }
    //    void setC(double x) { c=x; }

    private static String ccMsg(int x) {
	return "at least 1 in "+x+" papers shown should be interesting";
    }

    public static enum CCode  {
	@EA("Selective: at least 1 in 2 papers shown should be interesting") C2,
	    @EA("at least 1 in 4 papers shown should be interesting")  C4, 
	    @EA("Balanced: at least 1 in 8 papers shown should be interesting") C8, 
	    @EA("at least 1 in 16 papers shown should be interesting")  C16, 
	    @EA("Inclusive: at least 1 in 32 papers shown should be interesting")  C32, 
	    @EA("Show all: show all papers in my selected categories")  ALL;
	static final public double[] allValues={0.5, 0.25, 0.125, 1.0/16, 1.0/32, 0};

	public double doubleValue() {
	    return allValues[ this.ordinal()];
	}
    }

    @Display(editable=true, order=11, alt="Selectivity")
	@Column(nullable=false,length=4)
	@Enumerated(EnumType.STRING)     
	private CCode cCode;

    public CCode getCCode() { return cCode; }
    public void setCCode(CCode x) { cCode=x; }


    public double getC() { 
	return  getCCode().doubleValue();
    }

    /** Information about the user's "attitudes" toward classes.
     */
    /*    static class Ci { 
	int classId;
	double alpha, beta;
    }
    */
    /** Information about the user's "attitudes" toward classes.
     */
    /*
    @OneToMany(cascade=CascadeType.ALL)
        private LinkedHashSet<Ci> ci = new LinkedHashSet<Ci>();

    public Set<Ci> getCi() {
        return ci;
    }
    */

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
	a.setCCode(CCode.C32);
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
  