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


/** Very basic information about an ArXiv article or a user-uploaded document stored
    in the Lucene datastore. */
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

    /** Document cluster assignment for EE4 */
    @Basic  @Display(editable=true, order=2)
        private int ee4classId;    
    public int getEe4classId() {        return ee4classId;    }
    public void setEe4classId(int x) {        ee4classId=x;    }

    /** Document cluster assignment for EE5 */
    @Basic  @Display(editable=true, order=3)
        private int ee5classId;    
    public int getEe5classId() {        return ee5classId;    }
    public void setEe5classId(int x) {        ee5classId=x;    }

    /** This flag is set to true if the document body was 
	not available when cluster assignment for EE5 
	was carried out. This means that if the document
	body becomes available in Lucene, we may want to 
	re-assign the document.
     */
    @Basic  @Display(editable=true, order=4)
        private boolean ee5missingBody;    
    public boolean getEe5missingBody() { return ee5missingBody;    }
    public void setEe5missingBody(boolean x) {  ee5missingBody=x;  }


   /** ArXiv Article ID. This is used to locate the article in the Lucene
       doc store.
       
       <p>For user-uploaded docs this field contains null.

       <p>See 
http://openjpa.apache.org/builds/1.0.4/apache-openjpa-1.0.4/docs/manual/ref_guide_mapping_jpa.html#ref_guide_mapping_jpa_unique about the '@Index' and '@Unique' annotation.
*/
    @Basic      @Column(length=16)	@Index(unique=true)
	@Display(editable=false, order=2,
		 link="../FilterServlet/my.src:RESEARCH/abs/")
	String aid=null;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}

    /** Uploader's user name (for user-uploaded docs only) */
    @Basic      @Column(length=15)
	@Display(editable=false, order=9)
	String user=null;
    public String getUser() { return user; }
    public void setUser(String x) { user=x;}

   /** Uploaded file's file name (for user-uploaded docs only). It can
       be used, together with the user field, to locate the document
       in Lucene doc store.  For ArXiv articles this field is null. 

       FIXME: bad things happen if the length of the file name is longer
       than this limit. Some checks could be imposed...
   */
    @Basic      @Column(length=256)
	@Display(editable=false, order=10)
	String file=null;
    public String getFile() { return file; }
    public void setFile(String x) { file=x;}

    /** Find the matching record for an ArXiv article.
	@return The Article object with the matching name, or null if none is found */
@SuppressWarnings("unchecked")
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

    /** Finds the matching record for a user-uploaded doc.
	@return The Article object with the matching name, or null if none is found */
    @SuppressWarnings("unchecked")
	public static Article findUploaded( EntityManager em, String user, String file) {
	Query q = em.createQuery("select m from Article m where m.user=:u and m.file=:f");
	q.setParameter("u", user);
	q.setParameter("f", file);
	List<Article> res = (List<Article>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }    

    /** Finds the matching record for an ArXiv article or a user-uploaded doc (based on whichever fields are set in the document).
  	@return The Article object with the matching attributes, or null if none is found */
    public static Article findAny(EntityManager em, Document doc) throws IOException {
	String aid = doc.get(ArxivFields.PAPER);
	Article a = null;
	if (aid!=null)  return Article.findByAid(em, aid);
	String user = doc.get(ArxivFields.UPLOAD_USER);
	String file = doc.get(ArxivFields.UPLOAD_FILE);
	return Article.findUploaded(em, user, file);
    }

    Article() {}

    Article(int _id, String _aid) {
	setId( id);
	setAid( aid);
    }

    /** Looks up an existing entry, or creates a new one, for a
	specified ArXiv ID. */
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

    static public Article getUUDocAlways(EntityManager em, Document doc) {
	String uname = doc.get(ArxivFields.UPLOAD_USER);
	String file = doc.get(ArxivFields.UPLOAD_FILE);
	return Article.getUUDocAlways(em, uname, file);
    }

    static public Article getUUDocAlways(EntityManager em, String uname, String file) {
	em.getTransaction().begin(); 
	Article a = Article.findUploaded(em, uname, file);
	if (a==null) {
	    a = new Article();
	    a.setUser(uname);
	    a.setFile(file);
	    em.persist(a);		
	    Logging.info("Created entry for article " + a);
	}
	em.getTransaction().commit(); 
	return a;
    }

    /** Checks if the other object is also an Article object describing the
	same Lucene document. The comparison is based on the fact that
	in our data store the ArXiv ID is a unique identifier for ArXiv 
	articles, and the (user, file) tuple is a  unique identifier for
	user-uploaded documents.
     */
    public boolean equals(Object _o) {
	if (!(_o instanceof Article)) return false;
	Article o = (Article)_o;
	return  (getAid()!=null) ?
	    same(getAid(), o.getAid()) :
	    same(getUser(), o.getUser()) && same(getFile(), o.getFile());
    }
       
    private static boolean same(String x, String y) {
	return x==y || x!=null  && y!=null && x.equals(y);
    }

    public String toString() { 
	String s = "["+getId()+"]";
	return (getAid() != null) ? s + "arXiv:" + getAid()  :
	    s + "UU:" + getUser() + ":" + getFile();	    	
    }

    /** A short human-readable identifier of a Lucene document */
    public static String shortName(Document doc) throws IOException {
	String aid = doc.get(ArxivFields.PAPER);
	if (aid!=null)  return aid;
	String user = doc.get(ArxivFields.UPLOAD_USER);
	String file = doc.get(ArxivFields.UPLOAD_FILE);
	return   "UU:" + user + ":" + file;
    }


   /** A one-off process, initializing this table from ArticleStats. This
       is not needed in subsequent server operation.
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

    //   @Display(editable=false, order=20)   @Lob private short[] classIds;

}