package edu.rutgers.axs.sql;


import java.util.*;
import java.text.*;
import javax.persistence.*;
//import javax.jdo.annotations.Index;
//import javax.jdo.annotations.Unique;
import java.lang.reflect.*;
import java.lang.annotation.*;

import edu.rutgers.axs.web.WebException;


/** This is an auxiliary table for experiments with computing recent-coaccess
    data in JPQL (2017-05-02) */
//@Table(indexes = {@Index(columnList="op")})
@Entity       public class ActionWeight
{

    //@Id  
    @Display(editable=false, order=1, text="Action type") 
    @Enumerated(EnumType.ORDINAL) 
    //    @Index(unique=true)
    private Action.Op op;   
    public Action.Op getOp() { return op; }
    public void setOp(Action.Op _op) { op = _op; }

    @Display(editable=true, order=2, text="Weight") 
    private double weight;
    public double getWeight() { return weight; }
    public void setWeight(double _weight) { weight = _weight; }
  
    static double valueForOp( Action.Op op) {
	double val = 0;
	if ( op.isPositiveSBStats()) {
	    val = op.isAnyViewArticleBody()? 1 : 5;
	} else if (op == Action.Op.REORDER) val= 5;
	return val;
    }

    /** For each relevant Action type, checks if an ActionWeight
	object with that name already exists in the database, and if
	not, creates it.
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) //throws WebException 
    {
	EntityManager em = Main.getEM();
	try {
	    for(Action.Op op: Action.Op.class.getEnumConstants()) {
		double val = valueForOp(op);
		if (val<=0) continue;
		    
		em.getTransaction().begin();


		String qs=  "select aw from ActionWeight aw where a.op=:op" ;
		Query q = em.createQuery(qs);	
		q.setParameter("op",op);
		try {
		    Object o = q.getSingleResult();
		    System.out.println("ActionWeight for '" + op + "' already exists");
		    em.getTransaction().rollback();
		    continue;
		} catch (NoResultException ex) {}

		System.out.println("Creating entry for: " + op);
		ActionWeight r=new ActionWeight();
		r.setOp(op);
		r.setWeight(val);
		em.persist(r);
		em.getTransaction().commit();
	    }
	} finally {
	    try {		em.getTransaction().commit();	    } catch (Exception _e) {}
	    em.close();
	}
    }
}

