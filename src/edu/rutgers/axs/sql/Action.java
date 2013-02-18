package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

/** An Action instance records such an event as a user's viewing an
    article's abstract, or making a judgment about the article's
    usefulness for him. Most actions are article-specific, but the user's
    following the "next page" or "prev page" links, which is not page specific,
    is recorded as an "action" as well.
 */
@Entity
    public class Action extends OurTable 
    implements Serializable, Comparable<Action>  {

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
  
    /** The arXiv article id of the article involved. This must be
	present in all Action entries, except for those of the
	NEXT_PAGE and PREV_PAGE types, which are not article-specific.
     */
    //    @Basic @Column(nullable=false)
    //    @Display(editable=false, order=5) 
    //	String article;
    @ManyToOne
	@Column(nullable=false)
	@Display(editable=false, order=2, link="viewObject.jsp?class=Article&id=") 
	Article article;

    public String getAid() {
	Article a =  getArticle();
	return a==null? null: a.getAid();
    }

    /*
    public void setArticle(String article) {
	this.article = article;
    }
    */

    public Article getArticle() {
	return article;
    }

    public void setArticle(Article article) {
	this.article = article;
    }

    public User getUser() {
	return user;
    }

    private void setUser(User c) {
	user=c;
    }
    
    @Display(editable=false, order=3) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** Various supported action types. Typically, there is a distinct
	action type for every button or link type the user may click on. 
	<p>
	NOTE: New types must be added to the ADD of the list, because we store
	the numeric code, not string, in the SQL database!
    */
    public static enum Op {
	/** No operation - this is just used to get a response, and
	 * should not be recorded */
	NONE,
	    /** "Expand details" in a list page */
	    EXPAND_ABSTRACT,  // +5   (no. 1)
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
	    DONT_SHOW_AGAIN,            // -50   (no. 13)
	    /** Activated thru the "view folder" screen */
	    REMOVE_FROM_MY_FOLDER,      // see "copy"	

	    /** Navigation links in the main page. */
	    PREV_PAGE, 
	    NEXT_PAGE;

	/** Data types for which FilterServlet does not "filter", but
	 * redirects to the source, as per Simeon Warner, 2012-01-04 */
	static public final Op[] VIEW_ARTICLE_BODY_TYPES = 
	    {VIEW_PDF,    
	     VIEW_PS,
	     VIEW_HTML,
	     VIEW_OTHER};

	public boolean isViewArticleBody() {
	    for(Op q: VIEW_ARTICLE_BODY_TYPES) {
		if (this==q) return true;
	    }
	    return false;
	}

    };


    public static Op[] ratingOps = {Op.INTERESTING_AND_NEW, 
				  Op.INTERESTING_BUT_SEEN_TODAY,
				  Op.INTERESTING_BUT_KNOWN, 
				  Op.USELESS };

    @Display(editable=false, order=4, text="Action type") 
	@Enumerated(EnumType.ORDINAL) 
    	private Op op;   

    public Op getOp() { return op; }
    public void setOp(Op _op) { op = _op; }

    /** On Thorsten's request (as discussed in June-July 2012) we record
	the general context wherein the user effected a particular action.
    */
    public static enum Source {
	UNKNOWN,
	/** Main page: suggestion list produced by Thorsten's Algorithm 2;
	    or a "view abstract" page reached from the above.	    
	 */
	MAIN_SL,
	    /** Main page: suggestion list produced by the
		"team-draft" merge of the list from Thorsten's
		Algorithm 2, and the one from a simple user cat
		search; or a "view abstract" page reached from the
		above.
	    */
	    MAIN_MIX,
	    /** The "view suggestion" screen NOT generated in the main page.
		This will never be presented to regular users in production.
	     */
	    VIEW_SL,
	    /** The search results list produced by My.ArXiv's own
	     search tool; or a "view abstract" page reached from the
	     above. */
	    SEARCH,
	    /** User's own personal folder; or a "view abstract" page reached from the
	     above. */
	    FOLDER,
	    /** User's own history page; or a "view abstract" page reached from the
	     above. */
	    HISTORY,
	    /** Some kind of page (probably, "view abstract") reached as
		a result of the user's own free browsing via arxiv.org,
		served via My.ArXiv.org/arxiv/FilterServlet
	     */
	    FILTER,
	    /** Peter Frazier's Exploration Engine ver 2 */
	    BERNOULLI_EXPLORATION,	    BERNOULLI_EXPLOITATION,
	    /** Peter Frazier's Exploration Engine ver 4 */
	    MAIN_EE4;

	/** List of sources that are considered "main page" */
	static Source[] mainPageSources = { 	
	    MAIN_SL,
	    MAIN_MIX,
	    BERNOULLI_EXPLORATION,	    
	    BERNOULLI_EXPLOITATION,
	    MAIN_EE4 };

	boolean isMainPage() {
	    for(Source x: mainPageSources) {
		if (this==x) return true;
	    } 
	    return false;
	}

    };


    /** The type of experimental context wherein the action occurred */
    @Column(nullable=true,length=24) @Enumerated(EnumType.STRING) 
	@Display(editable=false, order=5.2) 
    private Source src;
    
    public Source getSrc() { return src; }
    public void setSrc(Source x) { src = x; }

    /** Optional link to the DataFile which contains the (original,
	Algo-2-generated) suggestion list from whose context the user
	effected this action. This field is only applicable to actions
	whose source is Source.MAIN_SL or Source.MAIN_MIX. Even then,
	it may not be set if it is the very first suggestion list
	presentation done by the system for this user, and the SL is
	generated on the fly (because it is known to be identical to the 
	user cat search results).
     */

    @Column(nullable=true) 
	@Display(editable=false, order=5.3, link="viewPresentedList.jsp")  
	@Basic
	private long presentedListId;
    public void setPresentedListId(long val) {     presentedListId    = val;    }
    public long getPresentedListId() {        return  presentedListId;    }

    public void setActionSource(ActionSource a) {
	setSrc(a.src);
	setPresentedListId(a.presentedListId);
    }

    /** The type of "experimental day" during which the action occurred */
    @Column(nullable=true,length=6) @Enumerated(EnumType.STRING) 
	@Display(editable=false, order=3.2, alt="Type of experiment day") 
    private User.Day day;
    
    public User.Day getDay() { return day; }
    public void setDay(User.Day x) { day = x; }




    /** Any text from an entry box, or the text value of an enum 
	response. Only some responses may have this. */
    //    @Basic   @Column(nullable=true) String text;
    //public String getText() { return text; }
    //public void setText(String x) { text = x; }

    static {
	Reflect.getReflect(Action.class);
    }

    public Action() {}

    /*
    public Action(User u, String _article, Op _op){
	setUser(u);
	setDay(u.getDay());
	setArticle(_article);
	setOp(_op);
	Date now = new Date();
	setTime(now);
    }
    */

    public Action(User u, Article _article, Op _op){
	setUser(u);
	setDay(u.getDay());
	setArticle(_article);
	setOp(_op);
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

	String qtext = "select astat.id, astat.aid from Action a, ArticleStats astat where a.article.id = astat.aid " +
	    (missingSimsOnly? "and astat.simsTime is null " : "") +
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
	    (missingSimsOnly?"and le.astat.simsTime is null ": "")+
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

	String qtext = "select astat.id, astat.aid from ArticleStats astat where astat.id> :m" +
	    " and astat.simsTime is null";
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

    /** This is invoked for users enrolled in Exploration Engine
     * experiments, to have the action affect the Bernoulli stats.
     */
    public void bernoulliFeedback(EntityManager em) {
	User.Program program = getUser().getProgram();
	if (!program.needBernoulli()) return;
	boolean positive = (getOp()!=Op.USELESS) && 
	    (getOp() != Op.DONT_SHOW_AGAIN);
	int val1 = positive? 1: -1;
	int val0 = 0;
	String aid =  getAid();
	BernoulliVote vote =  BernoulliVote.find(em, getUser().getId(), aid);
	int cluster = getUser().getCluster();
	if (vote==null) {
	    vote=new BernoulliVote();
	    vote.setUser(getUser().getId());
	    vote.setAid(aid);
	} else {
	    val0 = vote.getVote();
	}
	vote.setVote(val1);
	em.persist(vote);
	
	if (val1 != val0) {
	    Logging.info("Need to update vote stats using vote " + vote);
	    BernoulliArticleStats bas = BernoulliArticleStats.findByAidAndCluster(em, aid, cluster);
	    if (bas==null) {
		Logging.warning("No BernoulliArticleStats stored for aid=" + aid + ", cluster=" + cluster);
	    } else {
		bas.updateStats(em);
	    }
	}

	Logging.info("Persisted vote " + vote);
    }


}
