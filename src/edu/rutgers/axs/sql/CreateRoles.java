package edu.rutgers.axs.sql;

import java.util.*;
import java.lang.reflect.*;
import javax.persistence.*;


/** 
 * A very simple, stand-alone program that creates necessary Role
 * entities in the roles table, and at least one User entity.
 */
public class CreateRoles {

    /** This name will be used to configure the EntityManagerFactory
	based on the corresponding name in the
	META-INF/persistence.xml file
     */
    final public static String persistenceUnitName = "arxiv";

    private static EntityManagerFactory factory = null;

    //    public
    static synchronized EntityManager getEM() {
        // Create a new EntityManagerFactory using the System properties.
        // The "icd" name will be used to configure based on the
        // corresponding name in the META-INF/persistence.xml file
	if (factory == null) {
	    factory = Persistence.
		createEntityManagerFactory(persistenceUnitName,
					   System.getProperties());
	}

        // Create a new EntityManager from the EntityManagerFactory. The
        // EntityManager is the main object in the persistence API, and is
        // used to create, delete, and query objects, as well as access
        // the current transaction
        EntityManager em = factory.createEntityManager();
	return em;
    }


    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
	EntityManager em = getEM();
	try {

	    for(Role.Name name: Role.Name.class.getEnumConstants()) {
		em.getTransaction().begin();
		Role r = (Role)em.find(Role.class, name.toString());
		if (r==null) {
		    System.out.println("Creating role: " + name );
		    r=new Role();
		    r.setRole(name);
		    em.persist(r);
		    em.getTransaction().commit();
		} else {
		    System.out.println("Role '" + name + "' already exists");
		    em.getTransaction().rollback();
		}
	    }

	    String[] users = {"vmenkov", "anonymous"};
	    for(String un: users) {
	    em.getTransaction().begin();

	    String pw = "xxx";
	    User u = User.findByName(em, un);

	    if (u == null) {
		System.out.println("Creating user: " + un + ", password=" + pw);
		u = new User();
		u.setUser_name(un);
		u.encryptAndSetPassword(pw);
		em.persist(u);
	    } else {
		System.out.println("User already exists: " + un );
		System.out.println("Setting his password to " + pw);
		u.encryptAndSetPassword(pw);
		em.persist(u);
	    }
	    em.getTransaction().commit();

	    em.getTransaction().begin();
	    u = User.findByName(em, un);
	    System.out.println("Reading back user record: " + u.reflectToString() );

	    for( Role.Name rn : new Role.Name[] { Role.Name.subscriber, 
						  Role.Name.researcher,
						  Role.Name.admin}) {
		Role r = (Role)em.find(Role.class, rn.toString());
		if (r == null) {
		    System.out.println("No role found: " + rn);
		} else {	    
		    System.out.println("Adding role to user: "+un+ ", role=" + r);
		    u.addRole(r);
		}
	    }

	    em.persist(u);
	    em.getTransaction().commit();
	}


	    System.out.println("-----------------------------------");
	    System.out.println("Reading all user records:");
	    Query q = em.createQuery("select m from User m order by m.user_name");
	    for (User m : (List<User>) q.getResultList()) {
		System.out.println("User record: " + m.reflectToString() );
	    }


	} finally {
	    try {		em.getTransaction().commit();	    } catch (Exception _e) {}
	    em.close();
	}

    }
}
