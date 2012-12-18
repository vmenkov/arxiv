package edu.rutgers.axs.sql;

import java.lang.reflect.*;
import javax.persistence.*;

/** All our entities (corresponding to table rows) implement  this.
 */
public abstract class OurTable {
    //abstract public long getId();

    /** Validates a recently created entry, before it has been put into the 
	database. Adds any error message text to errmsg.
	@return true if ok */
    public boolean validate(EntityManager em,StringBuffer errmsg) {
	return true;
    }


    /** This is meant to cause JPA load vectors that are not
	automatically loaded. The idea is, they can be accessible
	through this object even after the EntityManager is closed.
     */
    public void fetchVecs() {}

    /** A very cludgy way to call getId() regardless of whether it's
	returning int or long */
    public long getLongId() {
	Class c = getClass();
	try {
	    Method m = c.getMethod("getId");
	    return ((Number)m.invoke(this)).longValue();
	} catch (Exception ex) {
	    return 0;
	}

    }

}