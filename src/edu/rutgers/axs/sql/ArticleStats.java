package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.Unique;
import org.apache.openjpa.persistence.jdbc.Index;
import java.lang.reflect.*;
import java.lang.annotation.*;

/** Statistical information about an article's content, used for
    ranking search results etc. The idea is we compute such info for 
    all docs in the index, store it in the SQL database, and later
    re-use as needed for individual queries. */
@Entity  
    public class ArticleStats implements OurTable 
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
*/
    @Basic      @Column(length=16) @Index(unique=true)
	@Display(editable=false, order=2)
	String aid=null;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}
    /** Pre-computed idf-weighted two-norm for the article's feature
	vectors. It is used e.g. when computing a cosine similarity
	based on dot products, */
    @Basic
	@Display(editable=false, order=3)
	double norm;
    public double getNorm() { return norm; }
    public void setNorm(double x) { norm=x;}
    /** Distinct term count. The same word in different fields (title,
	abstract, body) is only counted ones. "Funky" tokens and stop
	words are excluded. These are exactly the terms over which the
	norm is computed.
    */
    @Basic 
	@Display(editable=false, order=4)
	int termCnt;
   public void setTermCnt(int x) { termCnt=x;}
    /** The sum of the raw term frequencies of the "counted" terms -
	i.e., the length of the article, in word tokens, after all
	invalid tokens and stop words have been excluded. */
    @Basic 
	@Display(editable=false, order=5)
	int length;
    public int getLength() { return length;}
    public void setLength(int x) { length=x;}

    /** When this was last updated */
    @Basic 	@Display(editable=false, order=20)
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** Field boost factor divided by the vector norm. The order is as per 
	ArticleStats.upFields
    */
    @Basic	@Display(editable=false, order=10)
	double boost0;
    public double getBoost0() { return boost0; }
    public void setBoost0(double x) {boost0 =x;}
     @Basic	@Display(editable=false, order=11)
	 double boost1;
    public double getBoost1() { return boost1; }
    public void setBoost1(double x) {boost1 =x;}
    @Basic	@Display(editable=false, order=12)
	double boost2;
    public double getBoost2() { return boost2; }
    public void setBoost2(double x) {boost2 =x;}
    @Basic	@Display(editable=false, order=13)
	double boost3;
    public double getBoost3() { return boost3; }
    public void setBoost3(double x) {boost3 =x;}


    /** FIXME: should have used an array instead... */
    public double getBoost(int i) {
	return (i<2) ? (i==0 ? boost0 : boost1) :
	    (i==2 ? boost2 : boost3);
    }

    public void setBoost(int i, double x) {
	if (i<2) {
	    if (i==0) setBoost0(x); else setBoost1(x);
	} else {
	    if (i==2) setBoost2(x); else setBoost3(x);
	}
    }

    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	return true; 
    }

   /** Find the matching record.
     @return The ArticleStats object with  the matching name, or null if none is found */
    public static ArticleStats findByAid( EntityManager em, String _aid) {
	Query q = em.createQuery("select m from ArticleStats m where m.aid=:c");
	q.setParameter("c", _aid);
	List<ArticleStats> res = (List<ArticleStats>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    /** Retrieves them all from the database. May be expensive. 
	FIXME: we do get an OutOfMemoryError here sometimes. 
	It may make sense to store the stats data in an array of sorts instead,
	e.g. norms[], boosts[][]...
     */
    public static List<ArticleStats> getAll( EntityManager em) {
	Query q = em.createQuery("select m from ArticleStats m");
	List<ArticleStats> res = (List<ArticleStats>)q.getResultList();
	return res;
    }

    public ArticleStats() {
	setTime( new Date());
    }



    /*ArticleStats(String _aid) {
	aid=_aid;
	}*/

}