package edu.rutgers.axs.sql;
import java.util.*;
import javax.persistence.*;

 /** 
     <p>Designed similarly to the sample at
     http://openjpa.apache.org/embeddable-samples.html
    */
@Embeddable
public class ExtendedSession {
    @Basic  @Column(length=64)
	private String encEsPass;
    public  String getEncEsPass() { return encEsPass; }
    public void setEncEsPass(       String x) { encEsPass = x; }

    @Temporal(TemporalType.TIMESTAMP)
	private Date esEnd;
    public  Date getEsEnd() { return esEnd; }
    public void setEsEnd(       Date x) { esEnd = x; }

    /** Dummy constructor, just because OpenJPA requires it */
    public ExtendedSession() {	
    }
 
    public ExtendedSession(String _ep, Date _ed) {	
	setEncEsPass( _ep);
	setEsEnd( _ed);
    }



	

}