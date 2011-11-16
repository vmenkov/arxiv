package edu.rutgers.axs.sql;

import javax.persistence.*;

/** All our entities (corresponding to table rows) implement  this.
 */
public interface OurTable {
    public long getId();

    /** Validates a recently created entry, before it has been put into the 
	database. Adds any error message text to errmsg.
	@return true if ok */
    public boolean validate(EntityManager em,StringBuffer errmsg);

}