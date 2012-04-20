package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

/** An Action instance records such an event as a user's viewing an
 * article's abstract, or making a judgment about the article's
 * usefulness for him. 
 */
@Entity
    public class Action  implements Serializable, OurTable, 
				    Comparable<Action>  {

    /** Transaction ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=2)
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    /** Link to the user whose action this is */
    @ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=1) 
	User user;
  
    /** The arXiv article id of the article involved */
    @Basic @Column(nullable=false)
    @Display(editable=false, order=5) 
	String article;
   
    public String getArticle() {
	return article;
    }

    public void setArticle(String article) {
	this.article = article;
    }

    public User getUser() {
	return user;
    }

    public void setUser(User c) {
	user=c;
    }
    
    @Display(editable=false, order=3) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** Various supported action types. Typically, there is a distinct
     * action type for every button or link type the user may click on. */
    public static enum Op {
	/** No operation - this should not normally be recorded */
	NONE,
	    /** "View" actions */
	    VIEW_ABSTRACT,  // +10
	    VIEW_FORMATS,   // +20
	    VIEW_PDF,       // +30
	    VIEW_PS,        // +30
	    VIEW_HTML,      // +30
	    VIEW_OTHER,     // (dvi etc.) +30

	    /** Feedback actions: only the most recent of this group counts */
	    INTERESTING_AND_NEW,        // +200
	    INTERESTING_BUT_SEEN_TODAY, // +150
	    INTERESTING_BUT_KNOWN,      // +100
	    USELESS,                    // -200
	    /** Only counts if not canceled by "remove" */
	    COPY_TO_MY_FOLDER,          // +1000, if not removed
	    /** goes with ratings */
	    DONT_SHOW_AGAIN,            // -50
	    /** Activated thru the "view folder" screen */
	    REMOVE_FROM_MY_FOLDER;      // see "copy"	

	/** Data types for which FilterServlet does not "filter", but
	 * redirects to the source, as per Simeon Warner, 2012-01-04 */
	static public final Op[] VIEW_ARICLE_BODY_TYPES = 
	    {VIEW_PDF,    
	     VIEW_PS,
	     VIEW_HTML,
	     VIEW_OTHER};

	public boolean isViewArticleBody() {
	    for(Op q: VIEW_ARICLE_BODY_TYPES) {
		if (this==q) return true;
	    }
	    return false;
	}

    };


    public static Op[] ratingOps = {Op.INTERESTING_AND_NEW, 
				  Op.INTERESTING_BUT_SEEN_TODAY,
				  Op.INTERESTING_BUT_KNOWN, 
				  Op.USELESS };

    @Display(editable=false, order=4) 
	@Enumerated(EnumType.ORDINAL) 
    	private Op op;   

    public Op getOp() { return op; }
    public void setOp(Op _op) { op = _op; }

    /** Any text from an entry box, or the text value of an enum 
	response. Only some responses may have this. */
    //    @Basic   @Column(nullable=true) String text;
    //public String getText() { return text; }
    //public void setText(String x) { text = x; }

    static {
	Reflect.getReflect(Action.class);
    }

    public Action() {}

    public Action(User u, String _article, Op _op){
	setUser(u);
	setArticle(_article);
	setOp(_op);
	//	Date now = (new GregorianCalendar()).getTime();
	Date now = new Date();
	setTime(now);
    }

    public String reflectToString() {
	return Reflect.reflectToString(this);
    }

 
    /** @return "(hh:mm:ss)", or an empty string */
    private String formatRelTime(long sec) {
	if (sec<0) return "";
	final DecimalFormat ddFmt = new DecimalFormat("00");
	String x=	    ddFmt.format(sec % 60);
	long m = sec/60;
	x = ddFmt.format(m/60) + ":" + ddFmt.format(m % 60) + ":" + x;
	return "(" + x + ")";
    }

    /** Shows time and offset  */
    private String tad(Date t) {
	if (t==null) return "";
	return Reflect.compactFormat(t);
	//+ " " + formatRelTime( secFromRecStart(t));
    }

    public String  presentTime() {
	return tad( getTime());
    }


    /** Always true */
    public boolean validate(EntityManager em,StringBuffer errmsg) {
	return true; 
    }

    /** Was this action recorded after the other response? */
    public boolean after(Action other) {
	Date a = getTime(), b = other.getTime();
	if (a==null || b==null) throw new IllegalArgumentException("Action.moreRecentThan(): Only responses with endTime can be compared");
	return a.after(b);
    }

    /** Is the operation of this action in the specified list? 
	@param ops The list of operations. Null means "all".
     */
    public boolean opInList(Op ops[]) {
	if (ops==null) return true;
	for(Op x: ops) {
	    if (op.equals(x)) return true;
	}
	return false;
    }

    /** Comparison is by date (in the normal, i.e. ascending,
     * order). This is used in sorting. */
    public int compareTo(Action other) {
	return getTime().compareTo(other.getTime());
    }

    public static String[] getAllPossiblyRatedDocs( EntityManager em) {
        return  getAllPossiblyRatedDocs(  em, false);
    }


    /**

       Useful JPQL queries:

select astat.id, astat.aid from Action a, ArticleStats astat where a.article = astat.aid and astat.simsTime is not null group by astat.id, astat.aid 

select le.astat.id, le.astat.aid from ListEntry le where le.rank<5 group by le.astat.id, le.astat.aid

select le.astat.id, le.astat.aid from ListEntry le where le.rank<5 and le.astat.simsTime is not null group by le.astat.id, le.astat.aid 

select count(distinct astat.id) from Action a, ArticleStats astat where a.article = astat.aid and astat.simsTime is not null


     */

    public static String[] getAllPossiblyRatedDocs( EntityManager em, boolean missingSimsOnly)  {
	//Query q = em.createQuery("select distinct(a.article) from Action a");

	String qtext = "select astat.id, astat.aid from Action a, ArticleStats astat where a.article = astat.aid " +
	    (missingSimsOnly? "and astat.simsTime is not null " : "") +
	    "group by astat.id, astat.aid";
	Query q = em.createQuery(qtext);


	Set<String> s = new HashSet<String>();
	List list =q.getResultList();
	int cnt1=0, cnt2=0;
	for(Object o: list) {
	    //	    s.add((String)o);

	    if (o instanceof Object[]) {
		Object[] oa = (Object[])o;
		s.add((String)oa[1]);
	    }

	    cnt1++;
	}
	//	q = em.createQuery("select distinct le.astat.aid from ListEntry le where le.rank< :r");

	qtext = "select le.astat.id, le.astat.aid from ListEntry le where le.rank< :r "+
	    (missingSimsOnly?"and le.astat.simsTime is not null ": "")+
	    "group by le.astat.id, le.astat.aid";
	q = em.createQuery(qtext);


	q.setParameter("r", 5);
	list =q.getResultList();
	for(Object o: list) {
	    //	    s.add((String)o);
	    if (o instanceof Object[]) {
		Object[] oa = (Object[])o;
		s.add((String)oa[1]);
	    }
	    cnt2++;
	}       
	String[] a = s.toArray(new String[0]);
	Arrays.sort(a); // just for human readers
	Logging.info("Action.getAllPossiblyRatedDocs: cnt1=" + cnt1+", cnt2="+cnt2+", total=" + a.length);
	return a;
    }


    public static String[] getRecentDocsWithoutSims( EntityManager em)  {
	//Query q = em.createQuery("select distinct(a.article) from Action a");

	long minSimsThru = ArticleStats.minSimsThru(em);

	String qtext = "select astat.id, astat.aid from ArticleStats astat where astat.aid> :m ";
	Query q = em.createQuery(qtext);

	q.setParameter("m", minSimsThru);

	Vector<String> s = new Vector<String>();
	List list =q.getResultList();
	int cnt1=0;
	for(Object o: list) {
	    //	    s.add((String)o);

	    if (o instanceof Object[]) {
		Object[] oa = (Object[])o;
		s.add((String)oa[1]);
	    }

	    cnt1++;
	}
	return s.toArray(new String[0]);

    }
}
