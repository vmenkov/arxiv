package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import org.apache.lucene.index.*;
import org.apache.lucene.document.*;

import edu.rutgers.axs.indexer.*;


/** Statistical information about an article's content, used for
    ranking search results etc. The idea is we compute such info for 
    all docs in the index, store it in the SQL database, and later
    re-use as needed for individual queries. */
@SuppressWarnings("unchecked")
@Entity  
    public class ArticleStats extends OurTable 
{

    /** Same key as in the Article table */
   @Id 
   //@GeneratedValue(strategy=GenerationType.IDENTITY)
       @Display(editable=false, order=1)
       private int id;

    public int getId() {        return id;    }
    void setId(int x) {         id=x;    }

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
	based on dot products. Note that since the norm is IDF-weighted,
	it becomes somewhat incorrect when more documents are added into 
	the index and 
    */
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
    private double getBoost0() { return boost0; }
    private void setBoost0(double x) {boost0 =x;}
     @Basic	@Display(editable=false, order=11)
	 double boost1;
    private double getBoost1() { return boost1; }
    private void setBoost1(double x) {boost1 =x;}
    @Basic	@Display(editable=false, order=12)
	double boost2;
    private double getBoost2() { return boost2; }
    private void setBoost2(double x) {boost2 =x;}
    @Basic	@Display(editable=false, order=13)
	double boost3;
    private double getBoost3() { return boost3; }
    private void setBoost3(double x) {boost3 =x;}


    /** FIXME: should have used an array instead... */
    private double getBoost(int i) {
	return (i<2) ? (i==0 ? getBoost0() : getBoost1()) :
	    (i==2 ? getBoost2() : getBoost3());
    }

    private void setBoost(int i, double x) {
	if (i<2) {
	    if (i==0) setBoost0(x); else setBoost1(x);
	} else {
	    if (i==2) setBoost2(x); else setBoost3(x);
	}
    }

    public void setRawBoost(int i, double x) {
	setBoost(i,x);
    }

    public double getRawBoost(int i) {
	return getBoost(i);
    }

    public double getNormalizedBoost(int i) {
	return getBoost(i)/getNorm();
    }

    /** The document similarity array has been created by means of
	scanning all existing document entries through simsThru. Ths
	semnatics are as follows: 
	<ul>

	<li>If simsThru is set, this row of the similarity table has
	been initialized by computing sims of this document to all
	docs through simThru. Later on, as more docs were added to the
	database, similarities of those docs to this doc may have been
	computed and added to this row as well; thus, this row is 
	also guaranteed to contain sims with all docs that have simsThru set,
	and whose simsThru is at least as high as the ArticleStats.id for
	this document.

	<li> If simsThru is not set, it means that there was a never a
	full inverse-index scan for this row, i.e. we have never
	computed similarities of this row to <em>all</em> other rows.



	</ul>
    */
    public long simsThru;
    public long getSimsThru() {        return simsThru;    }
    public void setSimsThru(long x) {       simsThru=x;    }


    /** When were the similarities last recomputed in their entirety? 
     (A partial update does not count). */
    @Basic 	@Display(editable=false, order=21)
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date simsTime;
    public  Date getSimsTime() { return simsTime; }
    public void setSimsTime(       Date x) { simsTime = x; }


    //@Lob    @Basic(fetch=FetchType.LAZY)
     //(fetch=FetchType.LAZY) 
    //@Embedded (fetch=FetchType.LAZY)

    @Embedded 
	private SimRow sims;
    public void setSims(SimRow x) { sims=x; }
    public SimRow getSims() { return sims; }


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

    /** Just for openjpa.Enhance, to keep it from complaining */
    private ArticleStats() {}

    public ArticleStats(Article a) {
	setId(a.getId());
	setAid(a.getAid());
	setTime( new Date());
    }

    /** Does it look like this ArticleStats entry is older than the
	current Lucene-stored document? If it does, it may be out
	of date, and may need to be recomputed!
	@param doc The Lucene-stored document
     */
    public boolean mayBeOutOfDate(Document doc) {

	String docDateString = doc.get(ArxivFields.DATE_INDEXED);
	if (docDateString==null) return false;

	Date docDate = null;
	try {
	    docDate=DateTools.stringToDate(docDateString);
	} catch(java.text.ParseException ex) {
	    return false;
	}
			
	Date astDate = getTime();
	return (astDate==null || docDate.after(astDate));
    }

    /*
    private static class FsAidOnly implements FieldSelector {
	public FieldSelectorResult accept(String fieldName) {
	    return fieldName.equals(ArxivFields.PAPER) ?
		FieldSelectorResult.LOAD : 
		FieldSelectorResult.NO_LOAD;
	}
    }
    */
    /** Used as a cost-saving measure when we only need to retrieve
     * the Article ID from Lucene */
    public final static FieldSelector fieldSelectorAid = 
	new OneFieldSelector(ArxivFields.PAPER);

    /** Reads the entire table of pre-computed data from the SQL database.
	@return An array including all ArticleStats entries in our
	database. The index into the array is Lucene's current
	internal doc id */
    public static ArticleStats[] getArticleStatsArray( EntityManager em,
						       IndexReader reader)
	throws org.apache.lucene.index.CorruptIndexException, IOException
 {
	List<ArticleStats> aslist = ArticleStats.getAll( em);
	HashMap<String, ArticleStats> h=new HashMap<String, ArticleStats>();
	for(ArticleStats as: aslist) {
	    h.put(as.getAid(), as);
	}
	int maxdoc = reader.maxDoc();
	ArticleStats[] all = new ArticleStats[maxdoc];
	int foundCnt=0, deletedCnt=0, nullCnt=0;
	for(int pos=0; pos<maxdoc; pos++) {
	    if (reader.isDeleted(pos)) {
		deletedCnt++;
		continue;
	    }
	    Document doc = reader.document(pos,fieldSelectorAid);
	    String aid = doc.get(ArxivFields.PAPER);	    
	    ArticleStats as = h.get(aid);
	    if (as!=null) {
		foundCnt++;
		all[pos] = as;
	    } else {
		nullCnt++;
	    }
	}
	Logging.info("Found pre-computed ArticleStats for " + foundCnt + " docs; no stats found for " + nullCnt + " docs");
	return all;
    }

    /** Retrieves the minimum stored value of simsThru.  The meaning of this value is as follows:
	all docs that have simsThru set are guaranteed to have sims computed with all docs thru this value.

	It was 751587 on cactuar.
     */
    public static long minSimsThru( EntityManager em) {
	Query q = em.createQuery("select min(m.simsThru) from ArticleStats m where m.simsTime is not null");
	List<Long> res = (List<Long>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next().longValue();
	} else {
	    Logging.warning("No min(simsThru) found!");
	    return 0;
	}
    }    



}