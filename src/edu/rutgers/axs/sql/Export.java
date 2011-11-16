package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import javax.persistence.*;


/** Exports the content of main ICD data tables
 */
public class Export {

    private static EntityManagerFactory factory = null;

    //    public
    static synchronized EntityManager getEM() {
	if (factory == null) {
	    factory = Persistence.
		createEntityManagerFactory(Main.persistenceUnitName,
					   System.getProperties());
	}

        EntityManager em = factory.createEntityManager();
	return em;
    }


    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException {
	EntityManager em = getEM();
	try {
	    //dumpTable(em,"Respondent");
	    //dumpTable(em,"PhoneCall");
	    //dumpTable(em,"Response");
	    dumpTable(em,"Action");
	    
	} finally {
	    em.close();
	}

    }

    @SuppressWarnings("unchecked")
     private static void dumpTable(EntityManager em, String name) 
	throws IOException  {
	  Query q = em.createQuery("select m from " + name + " m");
  
	  PrintWriter w = new PrintWriter(new FileWriter(name + ".sql"));
	  for (Object m : q.getResultList()) {
	      w.println(Reflect.saveAsInsert(m) + ";");
	  }
	  w.close();
    
    }

}
