package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

//import edu.rutgers.axs.web.EditUser;
import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.web.WebException;


/** User-specific information needed  for Exploration Engine ver 4 (EE4).
 */
@Entity 
    public class EE4User extends OurTable 
{
    @Id @Display(editable=false, order=1.1)
    	private int id;

    public void setId(int val) {        id = val;    }
    /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate PhoneCall entries with respondent entries,
      but has no meaning outside of it.
     */
    public int getId() {        return id;    }

    //    @Basic 	double c;
    //    public double getC() { return c; }
    //    void setC(double x) { c=x; }

    public static enum CCode  {
	@EA("Selective: 1/2")  C2, 
	@EA("1/4")  C4, 
	@EA("Balanced: 1/8") C8, 
	@EA("1/16")  C16, 
	@EA("Inclusive: 1/32")  C32, 
	@EA("Show all")  ALL;
	public double doubleValue() {
	    if (this==ALL) return 0;
	    else if (this==C32) return 1.0/32;
	    else if (this==C16) return 1.0/16;
	    else if (this==C8) return 1.0/8;
	    else if (this==C4) return 1.0/4;
	    else if (this==C2) return 0.5;
	    return 0;
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
	private Set<EE4Uci> uci; //  = new HashSet<EE4Uci>();

    public Set<EE4Uci> getUci() { return uci; }
    void setUci( Set<EE4Uci> x) {  uci=x; }





}
  