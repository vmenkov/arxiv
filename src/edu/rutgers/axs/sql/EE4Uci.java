package edu.rutgers.axs.sql;
import javax.persistence.*;

 /** Information about the user's "attitudes" toward classes.

     <p>Designed similarly to the sample at
     http://openjpa.apache.org/embeddable-samples.html
    */
@Embeddable
public class EE4Uci {
    @Basic
	private int classId;
    @Basic
	private double alpha;
    @Basic
	private double beta;

    public EE4Uci(){
    }
}