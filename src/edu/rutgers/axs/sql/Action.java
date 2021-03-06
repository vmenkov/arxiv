package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

import  edu.rutgers.axs.web.SessionData;

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

    /** Link to the user whose action this is. This can be null for anon users */

    @ManyToOne
    @Column(nullable=true)
	@Display(editable=false, order=1, link="viewUser.jsp") 
	User user;

    public User getUser() {	return user;    }
    private void setUser(User c) {	user=c;    }

    /*  
    @ManyToOne
    @Column(nullable=true)
    @Display(editable=false, order=1.2) 
	Session session;
    */
    @Column(nullable=false)
    @Display(editable=false, order=1.2) 
	long session;
  
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

    /** ArXiv:id or UU:uname:file */
    public String describeArticle() {
	Article a =  getArticle();
	return a==null? "no_article": a.toString();
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

    
    public long getSession() {
	return session;
    }

    private void setSession(long  c) {
	session=c;
    }
    

    @Display(editable=false, order=3) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** Various supported action types. Typically, there is a distinct
	action type for every button or link type the user may click on. 
	<p>
	NOTE: New types must be added to the END of the list, because we store
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
	    MOVE_TO_MY_FOLDER,          // +1000, if not removed
	    /** goes with ratings */
	    DONT_SHOW_AGAIN,            // -50   (no. 13)
	    /** Activated thru the "view folder" screen */
	    REMOVE_FROM_MY_FOLDER,      // see "copy"	

	    /** Navigation links in the main page. */
	    PREV_PAGE, 
	    NEXT_PAGE,

	    /** User reorders list items in a recommendation list,
		using the jQuery "Sortable" feature.   */
	    REORDER,
	    /** User uploads a document. In this case, the Article link
		refers to a user-uploaded document in Lucene, rather than
		an ArXiv article. */
	    UPLOAD;

	/** Data types for which FilterServlet does not "filter", but
	 * redirects to the source, as per Simeon Warner, 2012-01-04 */
	static public final Op[] VIEW_ARTICLE_BODY_TYPES = 
	    {VIEW_PDF,    
	     VIEW_PS,
	     VIEW_HTML,
	     VIEW_OTHER};

	private boolean isInList(Op [] z) {
	    for(Op q: z) {
		if (this==q) return true;
	    }
	    return false;
	}

	public boolean isViewArticleBody() {
	    return isInList(VIEW_ARTICLE_BODY_TYPES);
	}

	/** Any kind of article "view" */
	public boolean isAnyViewArticleBody() {
	    return  isViewArticleBody() ||
		isInList(new Op[] {EXPAND_ABSTRACT,
				   VIEW_ABSTRACT, 
				   VIEW_FORMATS});
	}

	static public final Op[] ALL_FOLDER_OPS =  
	    {COPY_TO_MY_FOLDER,
	     MOVE_TO_MY_FOLDER,
	     REMOVE_FROM_MY_FOLDER};   

	public boolean isToFolder() {
	    return (this==COPY_TO_MY_FOLDER) ||
		(this==MOVE_TO_MY_FOLDER);
	}

	/** Returns true if this operation, in the SB context,
	    should cause the article not to be shown again.
	 */
	public boolean isHideSB() {
	    return isInList(new Op[] {INTERESTING_BUT_KNOWN,
				      DONT_SHOW_AGAIN,
				      USELESS});	
	}
	
	/** Is this operation considered a "negative" for the purposes of SBStats? */
	public boolean isNegativeSBStats() {
	    return 
		this==DONT_SHOW_AGAIN ||
		this==USELESS;		    
	}

	/** Is this operation considered a "positive" for the purposes of SBStats? */
	public boolean isPositiveSBStats() {
	    return isAnyViewArticleBody() ||
		isToFolder() ||
		isInList(new Op[] {
			INTERESTING_AND_NEW,        
			INTERESTING_BUT_SEEN_TODAY, 
			INTERESTING_BUT_KNOWN});
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
	This is also stored as the "type" field of each PresentedList object.
    */
    public static enum Source {
	UNKNOWN,
	/** Main page: suggestion list produced by Thorsten's Algorithm 1;
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
	    MAIN_EE4,  MAIN_EE4_MIX,
	    MAIN_EE5,  MAIN_EE5_MIX,

	    /** Body of the email messages */
	    EMAIL_SL, EMAIL_MIX, EMAIL_EE4, EMAIL_EE4_MIX, EMAIL_EE5, EMAIL_EE5_MIX,
	    
	    /** Session-based recommendation list (typically, offered
		to anon users) */
	    SB,
	    /** An action from a "research page". Regular users won't have it. */
	    RESEARCH,
	    /** This is not used to tag actions, but to indicate the "type"
	     of PresentedList objects that record lists reordered by the user
	     (rather than system-generated lists). */
	    REORDER;
	


	/** List of sources that are considered "main page" */
	static Source[] mainPageSources = { 	
	    MAIN_SL,
	    MAIN_MIX,
	    BERNOULLI_EXPLORATION,	    
	    BERNOULLI_EXPLOITATION,
	    MAIN_EE4,
	    MAIN_EE4_MIX,
	};

	static Source[] emailPageSources = { 
	    EMAIL_SL, EMAIL_MIX, EMAIL_EE4, EMAIL_EE4_MIX,	
	};


	public boolean isMainPage() {
	    for(Source x: mainPageSources) {
		if (this==x) return true;
	    } 
	    return false;
	}

	boolean isEmailPage() {
	    for(Source x: emailPageSources) {
		if (this==x) return true;
	    } 
	    return false;
	}

	public Source mainToEmail() {
	    if (this==MAIN_SL) return EMAIL_SL;
	    else if (this==MAIN_MIX) return EMAIL_MIX;
	    else if (this==MAIN_EE4) return EMAIL_EE4;
	    else if (this==MAIN_EE4_MIX) return EMAIL_EE4_MIX;
	    else return this;
	}

	/** Is this an interleaved PresentedList? */
	public boolean isMix() {
	    return (this==MAIN_MIX || this==MAIN_EE4_MIX ||
		    this==EMAIL_MIX || this==EMAIL_EE4_MIX);
	}


    };


    /** The type of experimental context wherein the action occurred */
    @Column(nullable=true,length=24) @Enumerated(EnumType.STRING) 
	@Display(editable=false, order=5.2) 
    private Source src;
    
    public Source getSrc() { return src; }
    public void setSrc(Source x) { src = x; }

    /** Optional link to the PresentedList object that describes the 
	list of articles presented to the user in whose context the
	user action was carried out. The action, then, can be interpreted
	as a user feedback in response to that list.
     */

    @Column(nullable=true) 
	@Display(editable=false, order=5.3, link="viewPresentedList.jsp")  
	@Basic
	private long presentedListId;
    public void setPresentedListId(long val) {     presentedListId    = val;    }
    public long getPresentedListId() {        return  presentedListId;    }

    /** @param a An ActionSource object describing the "context" of the action.
	Must be non-null */
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

    /** Only used in RELOAD actions, refers to the new
	(user-reordered) presented list reported by the user agent.
     */
    @Column(nullable=true) 
	@Display(editable=false, order=5.3, link="viewPresentedList.jsp")  
	@Basic
	private long newPresentedListId;
    public void setNewPresentedListId(long val) {  newPresentedListId = val;  }
    public long getNewPresentedListId() {  return  newPresentedListId;    }



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

    /**
       @param u The user object. May be null, in case of anon actions.
       @param sd Used to obtain session info. This is never null in
       the web app; but it may be null in some command-line
       applications that are used for a one-off "data repair"
       (retroactively adding entries to the Action table).
     */
    public  Action(User u, SessionData sd, Article _article, Op _op){     
	setUser(u);
	if (u!=null) {
	    setDay(u.getDay());
	}
	setArticle(_article);
	setOp(_op);
	if (sd!=null) {
	    setSession(sd.getSqlSessionId());
	}
	Date now = new Date();
	setTime(now);
    }

    public String reflectToString() {
	return Reflect.reflectToString(this);
    }

    public String toString() {
	String s = "(id="+getId()+", u="+getUser().getId()+", op="+getOp();
	s += ", "+ describeArticle();
	s += ", src="+getSrc()+ ", pl=" +  getPresentedListId()+")";
	return s;
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

	String qtext = "select astat.id, astat.aid from Action a, ArticleStats astat where a.article.aid = astat.aid " +
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

    /** How many actions has been recorded in a particular session? */
    static public int actionCntForSession( EntityManager em, long sid) {
	String qtext = "select count(a) from Action a where a.session = :s";
	Query q = em.createQuery(qtext);
	q.setParameter("s", sid);
	return ((Long)q.getSingleResult()).intValue();
    }

    /** Retrieves all actions for a given session */
    static public Vector<Action> actionsForSession( EntityManager em, long sid) {
	String qtext = "select a from Action a where a.session = :s";
	Query q = em.createQuery(qtext);
	q.setParameter("s", sid);
	List list =q.getResultList();
	Vector<Action> v = new Vector<Action>();
	for(Object o: list) {
	    v.add((Action)o);
	}
	return v;
    }
}
