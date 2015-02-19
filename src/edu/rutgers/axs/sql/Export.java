package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import javax.persistence.*;


/** An aixiliary utility, exports the content of some My.ArXiv data tables.
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
	    dumpTable(em,"User");	    
	    dumpTable(em,"Action");	    
	} finally {
	    em.close();
	}

    }

    /** Reads the entire content of a SQL table corresponding to a particular class, 
	and saves it in a SQL file (as a huge "insert" statement). This may or
	may not be usable, depending on the data types in use. This code has 
	not been maintained for a long time.
	@param name The name of the Java class corresponding to a SQL table.
     */
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
