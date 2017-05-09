package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

import edu.rutgers.axs.web.WebException;


/** Creating an auxiliary table for experiments with computing recent-coaccess
    data in JPQL (2017-05-02) */
public class CreateAux
{

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
		double val = ActionWeight.valueForOp(op);
		if (val<=0) continue;
		    
		em.getTransaction().begin();

		String qs=  "select aw from ActionWeight aw where aw.op=:op" ;
		Query q = em.createQuery(qs);	
		q.setParameter("op",op);
		try {
		    Object o = q.getSingleResult();
		    System.out.println("ActionWeight for '" + op + "' already exists");
		    em.getTransaction().rollback();
		    continue;
		} catch (NoResultException ex) {}
		//} catch (Exception ex) {}

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

