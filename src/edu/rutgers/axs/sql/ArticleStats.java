package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.Unique;
import org.apache.openjpa.persistence.jdbc.Index;
import java.lang.reflect.*;
import java.lang.annotation.*;

/** Statistical information about an article's content, used for dot
 * products etc */
@Entity  
    public class ArticleStats implements OurTable 
{

   @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
       private long id;

    /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate PhoneCall entries with respondent entries,
      but has no meaning outside of it.
     */
    public long getId() {        return id;    }

    /** ArXiv Article ID.

	See 
http://openjpa.apache.org/builds/1.0.4/apache-openjpa-1.0.4/docs/manual/ref_guide_mapping_jpa.html#ref_guide_mapping_jpa_unique about the '@Index' and '@Unique' annotation.
*/
    @Basic      @Column(length=16) @Index(unique=true)
	String aid=null;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}
    /** pre-computed two-norm for the article's feature vectors, used
	in dot products */
    @Basic
	double norm;
    public double getNorm() { return norm; }
    public void setNorm(double x) { norm=x;}
    /** Distinct term count. The same word in different fields (title,
	abstract, body) is only counted ones. "Funky" tokens and stop
	words are excluded. These are exactly the terms over which the
	norm is computed.
    */
    @Basic 
	int termCnt;
   public void setTermCnt(int x) { termCnt=x;}
    /** The sum of the raw term frequencies of the "counted" terms -
	i.e., the length of the article, in word tokens, after all
	invalid tokens and stop words have been excluded. */
    @Basic 
	int length;
    public int getLength() { return length;}
    public void setLength(int x) { length=x;}

    /** When this was last updated */
    @Display(editable=false) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    

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

    /** Retrieves them all from the database. May be expensive. */
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