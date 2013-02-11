package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;


/** Information related to a class of documents. as per the EE ver 4 writeup.
 */
@Entity 
    public class EE4DocClass extends OurTable 
{
  @Id 
  //@GeneratedValue(strategy=GenerationType.IDENTITY)
   @Display(editable=false, order=1.1)
      private int id;

    public void setId(int val) {        id = val;    }
    public int getId() {        return id;    }

    /** Average number of article submissions, in this cluster, per week */
    int m;
    public void setM(int val) {        m = val;    }
    public int getM() {        return m;    }

    public void incM() { setM(getM()+1);}


    @Basic 	double alpha0;
    public double getAlpha0() { return alpha0; }
    public void setAlpha0(double x) { alpha0=x; }
    
    @Basic 	double beta0;
    public double getBeta0() { return beta0; }
    public void setBeta0(double x) { beta0=x; }
  
    /** Time horizon in weeks */
    public static final int T=12;

    public static List<EE4DocClass> getAll( EntityManager em) {
	Query q = em.createQuery("select m from EE4DocClass m");
	List<EE4DocClass> res = (List<EE4DocClass>)q.getResultList();
	return res;
    }

    public static int maxId( EntityManager em) {
	Query q = em.createQuery("select max(m.id) from EE4DocClass m");
	Integer r = (Integer)q.getSingleResult();
	return r.intValue();
    }


}