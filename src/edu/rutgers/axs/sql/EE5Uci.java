package edu.rutgers.axs.sql;

import javax.persistence.*;

 /** Information about the user's "attitudes" toward classes.

     <p>Designed similarly to the sample at
     http://openjpa.apache.org/embeddable-samples.html
    */
@Embeddable
public class EE5Uci {
    @Basic
	private int classId;
    public int getClassId() {        return classId;    }
    public void setClassId(int x) {         classId=x;    }
    @Basic
	private double alpha;
    public double getAlpha() { return alpha; }
    public void setAlpha(double x) { alpha=x; }

    @Basic
	private double beta;
    public double getBeta() { return beta; }
    public void setBeta(double x) { beta=x; }
 
    /** Dummy constructor, just because OpenJPA requires it */
    public EE5Uci() {}
	
    public EE5Uci(int _classId, double _alpha, double _beta){
	setClassId(_classId);
	setAlpha(_alpha);
	setBeta(_beta);
    }

 
}