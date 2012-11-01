package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.*;

/** Information about articles and users' interaction with them for the
    Exploration Engine.
*/
@Entity  
    public class BernoulliArticleStats extends OurTable 
{

   @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
       private long id;

    /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate ListEntry entries with User entries) but has
      no meaning outside of it.
     */
    public long getId() {        return id;    }


  /** ArXiv Article ID.

	See 
http://openjpa.apache.org/builds/1.0.4/apache-openjpa-1.0.4/docs/manual/ref_guide_mapping_jpa.html#ref_guide_mapping_jpa_unique about the '@Index' and '@Unique' annotation.

<p>
 FIXME: when we have multiple clusters, we'll will have to switch to
 a composite unique index, like this:

@Table(uniqueConstraints=@UniqueConstraint(columnNames={"aid","cluster"}), name="myUniqueConstraint")
*/
    @Basic      @Column(length=16) @Index(unique=true)
	@Display(editable=false, order=2)
	String aid=null;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}

    /** Set to 1 everywhere for now */
   @Basic 
	@Display(editable=false, order=3)
	int cluster;
    public int getCluster() { return cluster ;}
    public void setCluster(int x) { cluster =x;}

    @Basic	@Display(editable=false, order=4)
	double ptilde;
    private double getPtilde() { return ptilde; }
    private void setPtilde(double x) {ptilde =x;}

    @Basic	@Display(editable=false, order=5)
	double alphaTrain;
    private double getAlphaTrain() { return alphaTrain; }
    private void setAlphaTrain(double x) {alphaTrain =x;}
   
    @Basic	@Display(editable=false, order=6)
	double betaTrain;
    private double getBetaTrain() { return betaTrain; }
    private void setBetaTrain(double x) {betaTrain =x;}
 
   @Basic	@Display(editable=false, order=7)
	double alpha;
    private double getAlpha() { return alpha; }
    private void setAlpha(double x) {alpha =x;}
   
    @Basic	@Display(editable=false, order=8)
	double beta;
    private double getBeta() { return beta; }
    private void setBeta(double x) {beta =x;}
 
  /** Find the matching record.
     @return The ArticleStats object with  the matching name, or null if none is found */
    public static BernoulliArticleStats findByAidAndCluster( EntityManager em, String _aid, int _cluster) {
	Query q = em.createQuery("select m from BernoulliArticleStats m where m.aid=:a and m.cluster=:c");
	q.setParameter("a", _aid);
	q.setParameter("c", _cluster);
	List<BernoulliArticleStats> res = (List<BernoulliArticleStats>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }    

}
