
package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;


/** Information related to a document cluster, as per the EE ver 5 writeup.
 */
@Entity 
    public class EE5DocClass extends OurTable 
{
  @Id 
  //@GeneratedValue(strategy=GenerationType.IDENTITY)
   @Display(editable=false, order=1.1)
      private int id;

    public void setId(int val) {        id = val;    }
    public int getId() {        return id;    }

    /** The full name of the subcategory within which this 
	cluster is created */
    @Display(editable=false) 	@Column(length=32) 
	String category; 
    public  String getCategory() { return category; }
    public void setCategory(String x)  { category=x; }

    /** Cluster sequential number within its own subcategory
      (zero-bases) */
    @Basic int localCid;
    public void setLocalCid(int val) {    localCid = val;    }
    public int getLocalCid() {        return localCid;    }

  /** Average number of article submissions, in this cluster, per day */
    @Basic double l;
    public void setL(double val) {    l = val;    }
    public double getL() {        return l;    }

    @Basic 	double alpha0;
    public double getAlpha0() { return alpha0; }
    public void setAlpha0(double x) { alpha0=x; }
    
    @Basic 	double beta0;
    public double getBeta0() { return beta0; }
    public void setBeta0(double x) { beta0=x; }
  
    /** Time horizon in days */
    public static final int TIME_HORIZON_DAY=90;
 
    @SuppressWarnings("unchecked")
    public static List<EE5DocClass> getAll( EntityManager em) {
	Query q = em.createQuery("select m from EE5DocClass m");
	List<EE5DocClass> res = (List<EE5DocClass>)q.getResultList();
	return res;
    }

    public static int maxId( EntityManager em) {
	Query q =em.createQuery("select max(m.id) from EE5DocClass m");
	Integer r = (Integer)q.getSingleResult();
	return r.intValue();
    }

    /** How many cluster records exist? */
    public static int count( EntityManager em) {
	Query q =em.createQuery("select count(m) from EE5DocClass m");
	Number r = (Number)q.getSingleResult();
	return r.intValue();
    }

    public String toString() {
	return "(Cluster "+getId()+": " + getCategory() +"("+getLocalCid()+"))";
    }


    /** Auxiliary structure which contains information about all document
	clusters. It is used to map a local cluster ID within a category to
	a global cluster ID. */
    public static class CidMapper {

	/** Maps cluster id to EE5DocClass object */
	public final HashMap<Integer,EE5DocClass> id2dc;

	/** For each subcategory name, the table stores an array of
	    EE5DocClass objects representing clusters in that subcat. */
	private HashMap<String, Vector<EE5DocClass>> h = new HashMap<String, Vector<EE5DocClass>>();

	/** Reads in the entire EE5DocClass table */
	static public HashMap<Integer,EE5DocClass> readDocClasses(EntityManager em) {
	    List<EE5DocClass> docClasses = EE5DocClass.getAll(em);
	    HashMap<Integer,EE5DocClass> id2dc = new HashMap<Integer,EE5DocClass>();
	    for(EE5DocClass c: docClasses) {
		id2dc.put(new Integer(c.getId()), c);
	    }
	    return id2dc;
	}

	public CidMapper(EntityManager em) {
	    id2dc = readDocClasses(em);
	    for(Integer cid: id2dc.keySet()) {
		EE5DocClass c = id2dc.get(cid);
		String cat = c.getCategory();
		Vector<EE5DocClass> v = h.get(cat);
		if (v==null) h.put(cat, v = new Vector<EE5DocClass>());
		int localCid = c.getLocalCid();
		
		if (localCid >= v.size()) {
		    v.setSize(localCid+1);
		} else if (v.elementAt(localCid) != null) {
		    throw new IllegalArgumentException("Cluster list contains duplicates for cat=" + cat+", local id=" + localCid);
		}
		v.set(localCid, c);
	    }
	    // validate
	    for(String cat: h.keySet()) {
		Vector<EE5DocClass> v = h.get(cat);
		for(int i=0; i<v.size(); i++) {
		    if (v.elementAt(i)==null) {
			throw new IllegalArgumentException("Found no EE5DocClass entry for cat=" + cat+", local cid=" + i);
		    }
		}
	    }
	}
	
	public EE5DocClass getCluster(String cat, int localCid) {
	    Vector<EE5DocClass> v = h.get(cat);
	    if (v==null) throw new IllegalArgumentException("No clustering information for category " + cat + " is recorded in EE5DocClass. Do you need to re-run init?");
	    return v.elementAt(localCid);
	}

	/** Max class ID found in this table */
	public int maxId() {
	    int m = 0;
	    for(Integer x:  id2dc.keySet()) {
		int id = x.intValue();
		if (id>m) m = id;
	    }
	    return m;
	}

    }
		


}