package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import org.apache.lucene.index.*;


import org.apache.lucene.document.*;
import edu.rutgers.axs.indexer.ArxivFields;


/**  */
@Entity  
   @Table(uniqueConstraints=@UniqueConstraint(name="article_id_cnstrt",columnNames="id"))
    public class Article extends OurTable 
{
    @Id 
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Display(editable=false, order=1)
       private int id;

    public int getId() {        return id;    }
    public void setId(int x) {        id=x;    }

    @Basic  @Display(editable=true, order=2)
    private int ee4classId;

    public int getEe4classId() {        return ee4classId;    }
    public void settEe4classId(int x) {        ee4classId=x;    }


   /** ArXiv Article ID.

	See 
http://openjpa.apache.org/builds/1.0.4/apache-openjpa-1.0.4/docs/manual/ref_guide_mapping_jpa.html#ref_guide_mapping_jpa_unique about the '@Index' and '@Unique' annotation.
*/
    @Basic      @Column(length=16) @Index(unique=true)
	@Display(editable=false, order=2)
	String aid=null;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}

  /** Find the matching record.
     @return The Article object with  the matching name, or null if none is found */
    public static Article findByAid( EntityManager em, String _aid) {
	Query q = em.createQuery("select m from Article m where m.aid=:c");
	q.setParameter("c", _aid);
	List<Article> res = (List<Article>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }    

    Article() {}

    Article(int _id, String _aid) {
	setId( id);
	setAid( aid);
    }

    /** Looks up an existing entry, or creates a new one */
    static public Article getArticleAlways(EntityManager em, String aid) {
	return getArticleAlways(em, aid, true);
    }   

    /** Looks up an existing entry, or creates a new one.
	@param commit Use true unless this call is already enclosed inside
	a transaction begin/commit pair
     */
    static public Article getArticleAlways(EntityManager em, String aid, boolean commit) {
	Article have = findByAid(em,aid);
	if (have !=null) return have;
	Article a = new Article();
	a.setAid(aid);
	if (commit) em.getTransaction().begin();
	em.persist(a);
	if (commit) em.getTransaction().commit();	
	return a;
    }

   /** A one-off process, initializing this table from ArticleStats. 
     */
    static void initFromArticleStats() {
	EntityManager em  = Main.getEM();
	List<ArticleStats> all = ArticleStats.getAll(em);
	int createCnt=0, haveCnt=0;
	System.out.println("Has read in ArticleStats...");
	for(ArticleStats ast: all) {
	    int id = (int)ast.getId();
	    String aid=ast.getAid();
	    /*
	    Article have = (Article)em.find(Article.class, id);
	    if (have != null) {
		if (!have.getAid().equals(aid)) {
		    throw new RuntimeException("Article aid mismatch: entry no. " + id + 
					       "has aid=" + aid + " in ArticleStats, but aid=" + have.getAid() + " in Article");
		}
		haveCnt++;
		continue; // proper match
	    }
	    */
	    if (findByAid(em,aid)!=null) {
		haveCnt++; continue;
		/*
		    throw new RuntimeException("SQL aid mismatch: entry no. " + id + 
					       "has aid=" + aid + " in ArticleStats, but a differently-numbered entry has the same article id in Article");
		*/
	    }
	    //	    Article a = new Article(id, aid);
	    Article a = new Article();
	    a.setAid(aid);
	    em.getTransaction().begin();
	    em.persist(a);
	    em.getTransaction().commit();
	    createCnt++;
	    if (createCnt % 1000==0) System.out.println("Done " + createCnt + "...");
	}
	System.out.println("Created " + createCnt + " Article entries; skipped already existing " + haveCnt);
	em.close();
    }

    static public void main(String[] argv) throws IOException {
	initFromArticleStats();
    }

}