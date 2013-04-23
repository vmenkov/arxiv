package edu.rutgers.axs.sql;

import javax.persistence.*;
import edu.rutgers.axs.ee4.EE4Mu;

 /** Information about the user's "attitudes" toward classes.

     <p>Designed similarly to the sample at
     http://openjpa.apache.org/embeddable-samples.html
    */
@Embeddable
public class EE4Uci {
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
    public EE4Uci() {}
	
    public EE4Uci(int _classId, double _alpha, double _beta){
	setClassId(_classId);
	setAlpha(_alpha);
	setBeta(_beta);
    }

    /** Decision-related numbers for this (user,cluster) pair on a
	particular run.  On a given run, they can be precomputed once
	and then used for all documents from this cluster when adjudicated
	for this user.
     */
    public class Stats {
	final public double mu;
	final public double cStar;
	final public boolean admit;
	private Stats(int m, EE4User.CCode  cCode) {
	    double alpha=getAlpha(),  beta=getBeta(); 
	    mu =EE4Mu.getMu(alpha, beta, cCode, m);	    
	    double score = alpha/(alpha + beta);
	    admit = (score >= mu);
	    if (admit) { // add to list
		cStar =  EE4Mu.thresholdC( alpha,  beta, m);
	    } else {
		cStar = 0;
	    }
	}	
    }
    
    @Transient 
    private Stats stats = null;
    public Stats getStats(int m, EE4User.CCode  cCode) {
	if (stats == null) stats = new Stats(m, cCode);
	return stats;
    }

}