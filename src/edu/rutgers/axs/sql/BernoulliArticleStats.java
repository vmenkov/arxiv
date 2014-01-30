package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.*;

/** Information about articles and users' interaction with them for the
    Exploration Engine. A nightly update script should create an entry 
    in this table for every new article in any of the eligible categories.
*/
@Entity  
    public class BernoulliArticleStats extends OurTable 
{

    /** The categories to which the experiment is presently restricted */
    static final public  String[] cats = {"cs.AI", "cs.IR", "stat.ML"};

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
	double norm;
    public double getNorm() { return norm; }
    public void setNorm(double x) {norm =x;}

    @Basic	@Display(editable=false, order=5)
	double ptilde;
    public double getPtilde() { return ptilde; }
    public void setPtilde(double x) {ptilde =x;}

    @Basic	@Display(editable=false, order=6)
	double alphaTrain;
    public double getAlphaTrain() { return alphaTrain; }
    public void setAlphaTrain(double x) {alphaTrain =x;}
   
    @Basic	@Display(editable=false, order=6.1)
	double betaTrain;
    public double getBetaTrain() { return betaTrain; }
    public void setBetaTrain(double x) {betaTrain =x;}
 
   @Basic	@Display(editable=false, order=7)
	double alpha;
    public double getAlpha() { return alpha; }
    public void setAlpha(double x) {alpha =x;}
   
    @Basic	@Display(editable=false, order=8)
	double beta;
    public double getBeta() { return beta; }
    public void setBeta(double x) {beta =x;}
 
  /** Find the matching record.
     @return The ArticleStats object with  the matching name, or null if none is found */
    @SuppressWarnings("unchecked")
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

    public void updateStats(EntityManager em) {
	String qs = "select count(m) from BernoulliVote m, User u where m.user = u.id and m.aid=:a and u.cluster=:c and m.vote=:v";
	int v[] = {-1,1};
	int s[] = {0,0};
	Query q = em.createQuery(qs);
	for(int z=0; z<2; z++) {
	    q.setParameter("a", getAid());
	    q.setParameter("c", getCluster());
	    q.setParameter("v", v[z]);
	    try {
		Object o = q.getSingleResult();
		s[z] = ((Number)o).intValue();
	    } catch(NoResultException ex) { 
		Logging.error("BAS.updateStats: NoResultException " + ex);
	    }  catch(NonUniqueResultException ex) {
		// this should not happen, as we have a uniqueness constraint
		Logging.error("BAS.updateStats: NonUniqueResultException " + ex);
	    }
	}
	Logging.info("BAS.updateStats(" + getAid() + ", "+getCluster()+") : " + s[0] + ", " + s[1]);
	setBeta( getBetaTrain() + s[0]);
	setAlpha( getAlphaTrain() + s[1]);
	em.persist(this);
    }


}
