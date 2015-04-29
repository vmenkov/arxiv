package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import javax.servlet.http.Cookie;

import org.apache.catalina.realm.RealmBase;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.sb.SBRGenerator;
import edu.rutgers.axs.bernoulli.Bernoulli;

/** A User entry contains basic information about one My.ArXiv
    user. Unlike most other persistent (OpenJPA) classes, the underlying
    table for this class is not named the same as the class; instead, it
    is named "arxiv_users". This inconsistency comes from an early attempt
    to share this table with  Tomcat's authentication system. That attempt 
    was later abandoned (as I could not get it to work quite right on all
    systems).
 */
@Entity 
    @Table(name="arxiv_users", 
	   uniqueConstraints=@UniqueConstraint(name="arxiv_user_name_cnstrt", columnNames="user_name") )
    public class User extends OurTable 
{
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1.1)
    	private int id;

    //    public void setId(int val) {        id = val;    }
    /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate PhoneCall entries with respondent entries,
      but has no meaning outside of it.
     */
    public int getId() {        return id;    }

   
    //@Id 
	@Display(editable=false, order=1.2) 	@Column(length=15) 
	String user_name; 
    /** user_name is used as the primary key and is not editable.
     */
    public  String getUser_name() { return user_name; }
    public void setUser_name(String x) throws WebException { 
	if (x.length()>15) {
	    throw new WebException("User name '"+x+"' is too long. User names cannot be longer than 15 characters");
	}
	user_name = x; 
    }
    
    @Basic      @Column(length=64) 
	@Display(order=2, editable=false, text="encrypted password", digest=true) 
	String user_pass;
    /** Encrypted password (or, more precisely, the MD5 digest of the
     password). If an empty string is stored here, it means that the entry is
     disabled, because the digest of any string is a non-empty string. */
    public  String getUser_pass() { return user_pass; }
    public void setUser_pass(       String x) { user_pass = x; }
    
    /** Encrypts the passed password, and stores the encrypted
     * value. This enables the user for logging in */
    public void encryptAndSetPassword( String clearPassword) {
	String x = org.apache.catalina.realm.RealmBase.Digest(clearPassword, "MD5", "utf-8" );
	setUser_pass( x);
    }

   public boolean checkPassword( String clearPassword) {
	String x = org.apache.catalina.realm.RealmBase.Digest(clearPassword, "MD5", "utf-8" );
	return x.equals(getUser_pass());
    }


    /** Disables the user entry, i.e. prohibits login. This is done by storing
	an empy string in the password digest field.
     */
    public void disable() {
	setUser_pass( "");
    }

    public boolean isEnabled() {
	return getUser_pass() !=null && getUser_pass().length()>0;
    }

    @Basic  @Column(length=64) @Display(order=3, editable=true, alt="First (and middle) name")
	String firstName;
    public  String getFirstName() { return firstName; }
    public void setFirstName(       String x) { firstName = x; }
 
    @Basic  @Column(length=64) @Display(order=4, editable=true,  alt="Last name")
	String lastName;
    public  String getLastName() { return lastName; }
    public void setLastName(       String x) { lastName = x; }

    public String getPrintableName() {
	String s = "";
	if (getFirstName()!=null) s+= getFirstName();
	if (getLastName()!=null) {
	    if (s.length()>0) s+= " ";
	    s+= getLastName();
	}
	return (s.length()>0)? s : getUser_name();
    }

    @Basic  @Column(length=64) @Display(order=5, editable=true )
	String email = ""; 
    public  String getEmail() { return email; }
    public void setEmail(       String x) { email = x; }

    /** Encrypted temporary password, used for extended (persistent)
	sessions of this user (those started using the "Remember me"
	checkbox). */
    /*
    @Basic  @Column(length=64) @Display(editable=false, order=7)
	String encEsPass="";
    public  String getEncEsPass() { return encEsPass; }
    public void setEncEsPass(       String x) { encEsPass = x; }
    */

    @Basic @Display(order=9.1,  editable=true, alt="Agreed to participate in follow-up telephone survey")
        @Column(nullable=false) boolean survey;
    public  boolean getSurvey() { return survey; }
    public void setSurvey( boolean x) { survey = x; }


    @Basic @Display(order=9.2,rp=true, editable=true, alt="Phone number (e.g. 732-932-0000)") 
    //@Usedin(phone=true)
        @Column(length=32)      String phoneNumber;
    public void setPhoneNumber(String val) {  phoneNumber        = val;    }
    public String getPhoneNumber() {        return phoneNumber ;    }


    @Basic @Display(order=9.3,rp=true, editable=true, alt="Best times to reach you (e.g. 'weekday evenings')") 
         @Column(length=64)      String timesToReach;
    public void setTimesToReach(String val) {      timesToReach    = val;    }
    public String getTimesToReach() {        return timesToReach ;    }

  
    @Basic @Display(order=9.4, editable=true,  alt="Do you approve the use of audio recording for the telephone interview?")
        @Column(nullable=false) boolean approvedAudio;
    public  boolean getApprovedAudio() { return approvedAudio; }
    public void setApprovedAudio( boolean x) { approvedAudio = x; }



    /** End time for the current extended (persistent) sessions. If
	null, or in the past, then there is such session in effect for
	this user now. */
    /*
    @Display(editable=false, order=10) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
       Date esEnd;
    public  Date getEsEnd() { return esEnd; }
    public void setEsEnd(       Date x) { esEnd = x; }
    */

    @ElementCollection
	private Set<ExtendedSession> es; //  = new HashSet<EE4Uci>();

    public Set<ExtendedSession> getEs() { return es; }
    public void setEs( Set<ExtendedSession> x) {  es=x; }


    /** Describes what kind of research program the user is currently enrolled in */
    public static enum Program {
	/** Thorsten Joachims' set-based */
	SET_BASED, 
	    /** Peter Frazeer's Exploration Engine v2: Exploration mode */
	    BERNOULLI_EXPLORATION,
	    /** Peter Frazeer's Exploration Engine v2: Exploration mode */
	    BERNOULLI_EXPLOITATION,
	    /** Peter Frazeer's Exploration Engine v4 */
	    EE4,
	    EE5,
	    /** Thorsten Joachim's Perturbed Preference Perceptron */
	    PPP,
	    /** This is not an actual program, but simply a code to 
		use when displaying a session-based suggestion list 
		for an anonymous user*/
	    SB_ANON;

	/** Do we need to use the Bernoulli Exploration Engine with
	 * this program?	 */
	public boolean needBernoulli() {
	    return this==BERNOULLI_EXPLORATION || this==BERNOULLI_EXPLOITATION;
	}
    }

    @Display(editable=false, order=11.1, alt="Experiment plan")
	@Column(nullable=false,length=24)
	@Enumerated(EnumType.STRING)     
	private Program program;

    public Program getProgram() { return program; }
    public void setProgram(Program x) { program=x; }

    /** The ID of the invitation (if any) pursuant to which the user
	entry was created. */
    @Basic  @Display(order=11.2,editable=false, link="viewObject.jsp?class=Invitation&id=") @Column(nullable=true)
	private long invitation;
    public  long getInvitation() { return invitation; }
    public void setInvitation( long x) { invitation = x; }


    /** This is how it's described in context.xml:
    //	     userRoleTable="user_roles" roleNameCol="role_name"
    create table user_roles (
			     user_name         varchar(15) not null,
			     role_name         varchar(15) not null)
    */
   @ManyToMany(cascade=CascadeType.ALL)
       @JoinTable(name="arxiv_user_roles",
		 joinColumns=@JoinColumn(name="user_name", 
					 referencedColumnName="user_name",
					 nullable=false,
					 columnDefinition="varchar(15)"),
		 inverseJoinColumns=
		  @JoinColumn(name="role_name", referencedColumnName="role",
			      nullable=false,
			      columnDefinition="varchar(15)"  //   length=15
			      ))
	private Set<Role> roles = new LinkedHashSet<Role>();

    public Set<Role> getRoles() {
	return roles;
    }


    synchronized public void addRole(Role r) {
	if (getRoles().contains(r)) return;
	else roles.add(r);
    }
    synchronized public void removeRole(Role r) {
	roles.remove(r);
    }

    public boolean hasRole(Role.Name name) {
	if (getRoles()==null) Logging.warning(" hasRole() : getRoles==null!");
	for(Role r: getRoles()) {
	    if (r.getERole() == name) return true;
	}
	return false;
    }

   /** Does this user have any of the roles in the specifed list? 

       @param names An array of roles. It must be non-null, but may be empty
       (in which case, of course, false will be returned). 

       @return True if the user has any of the listed roles. 
   */
     public boolean hasAnyRole(Role.Name[] names) {
	for(Role.Name r: names) {
	    if (hasRole(r)) return true;
	}
	return false;
    }

    /** Does this user have the "admin" role? */
    public boolean isAdmin() {
	return hasRole(Role.Name.admin);
    }

    /** Does this user have the "researcher" role? */
    public boolean isResearcher() {
	return hasRole(Role.Name.researcher);
    }


    /** What kind of "experimental day" we have for this user now? 
	"Learning" or "Evaluation" as per TJ+PK June 2012 scheme.
	By default, null is stored here. */
    public static enum Day {
	LEARN, EVAL;
    }

    @Column(nullable=true,length=6) @Enumerated(EnumType.STRING) 
	@Display(editable=false, order=11.3) 
    private User.Day day;
    
    public User.Day getDay() { return day; }
    void setDay(User.Day x) { day = x; }
	
    @Display(editable=false, order=11.4) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
       Date dayStart;
    Date getDayStart() { return dayStart; }
    void setDayStart(       Date x) { dayStart = x; }

    /** Sets the day type in the entry, if the previous day is
	over. Once should call persist() thereafter.

	@return True if the new day has been started. In this case,
	one should call persist(). False if the current day continues.
    */
    public boolean changeDayIfNeeded(){
	if (getDay()==null || getDayStart()==null ||
	    getDayStart().before(  SearchResults.daysAgo( 1 ))) {
	    forceNewDay();
	    return true;
	}
	else return false;
    }

    /** This should be called once a profile has been updated.

	<p> As per TJ: 

	<ol> <li>"I recommend we reduce the frequency of evaluation
	days from 1/2 to 1/3", 2013-01-09. The rationale is that we want
	users to see that their actions change the system, which only
	happens on learning days. Some of our current users always
	happened to draw evaluation days so far, so they think our
	system does no learning at all.

	<li> For the same reason, I suggest that the first two days of
        a new user are always learning days, never evaluation days."
        </ol>
    */
    public Day forceNewDay() {
	Date[] x = getActionTimeRange();
	Day d;
	if (x==null || x[0].after( SearchResults.daysBefore(x[1], 1))) {
	    d = Day.LEARN;
	} else {
	    d = 3*Math.random() < 1 ? Day.EVAL: Day.LEARN;
	}
	return  forceNewDay( d );
    }
    /** Sets the new day type to the specified value, and the day start time
	to "now". This can be called e.g. for a new user.
    */
    public Day forceNewDay(Day val) {
	Date now = new Date();
	Logging.info("Setting day type for user "+ getUser_name()+" to "+ val);
	setDay( val);
	setDayStart(now);	
	return val;
    }

    /** Sets day=LEARN. Can be used on new user entries. */
    public Day initialNewDay() {
	return forceNewDay(Day.LEARN);
    }

    /** Human-readable printable message about this user's current
     * experimental day
     */
    public String dayMsg() {
	return (getProgram()==Program.SET_BASED) ?
	    ""+ getDay() + " mode since " + getDayStart() :
	    "<!-- All days are the same for users in program="+getProgram()+"->";
    }

    /** The "time horizon" for selecting "recent articles" (for
	suggestion lists, etc). Default is 7 days.
     */
    @Basic  @Display(order=12,editable=true, alt=
		     "Time horizon (days). Recommendations will be selected from articles submited within so many days, or since your last visit to the site, whichever is earlier")

	private int days;
    public  int getDays() { return days; }
    public void setDays( int x) { days = x; }
 
    /** How frequently (= once in how many days) does this user want to receive email?
     */
    @Basic  @Display(order=12.2,editable=true, alt=
		     "How frequently (once in how many days) do you want to be notified, by email, about new articles? Enter 0 if you don't want to receive email")

    private int emailDays;
    public  int getEmailDays() { return  emailDays; }
    public void setEmailDays( int x) { emailDays = x; }
 
    /** This exclusion only applies to suggestion lists, not to search
     * results */
    @Basic @Display(order=13,  editable=true, alt="Exclude already-viewed articles from the list of recommendations")
        @Column(nullable=false) boolean excludeViewedArticles;
    public  boolean getExcludeViewedArticles() { return excludeViewedArticles; }
    public void setExcludeViewedArticles(boolean x) {excludeViewedArticles= x;}

    /** For Bernoulli Rewards only */
    @Basic   @Display(order=14,editable=false)  @Column(nullable=false)
	private int cluster;
    public  int getCluster() { return cluster; }
    public void setCluster( int x) { cluster = x; }

    public User() {
	setCluster( Bernoulli.defaultCluster);
	setProgram( Program.PPP);
    }


    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	    return true; 
    }

    /** For some reason, the getRoles call here seems to be essential to ensure
     that roles are available later! */
    @PostLoad
	void postLoad() {
	int n = 0;	for(Role r: getRoles()) {	    n++;	}
	//	Logging.info("Loaded a user entry with "+n +" roles: " + reflectToString());
    }

    public String listRoles() {
	String s="";
	for(Role r: getRoles()) {
	    s += " " + r;
	}
	return s; 	
    }

    /** HTML: header for (name, details, roles, enabled) */
    public static String header4cells() {
	return
	    "<th>User name</th>\n" +
	    "<th>id</th>\n" +
	    "<th>Detail (password is encrypted)</th>\n" +
	    "<th>Roles</th>\n" +
	    "<th>Enabled</th>\n" +
	    "<th>Interests</th>\n";
    }

   /** HTML: name, details, roles, enabled ; personal name, email*/
    public String to4cells() {
	return
	    "<td>" + getUser_name() + "</td>\n" +
	    "<td>" + getId() + "</td>\n" +
	    "<td>" + reflectToString()  + "</td>\n" +
	    "<td>" + listRoles()  + "</td>\n" +
	    "<td>" + (isEnabled()? "Yes" : "No")  + "</td>\n" +
	    "<td>" + listCats()  + "</td>\n" ;
    }

    /** Can be used instead of (User)em.find(User.class, un);
     @return The User object with  the matching name, or null if none is found */
    public static User findByName( EntityManager em, String username) {
	Query q = em.createQuery("select m from User m where m.user_name=:c");
	q.setParameter("c", username);
	try {
	    return (User)q.getSingleResult();
	} catch(NoResultException ex) { 
	    // no such user
	    return null;
	}  catch(NonUniqueResultException ex) {
	    // this should not happen, as we have a uniqueness constraint
	    Logging.error("Non-unique user entry for username='"+username+"'!");
	    return null;
	}
    }


    /** User's article-related "actions", such as viewing an article's abstract
        or making a judgment on an article */
    @OneToMany(cascade=CascadeType.ALL)
        private LinkedHashSet<Action> actions = new LinkedHashSet<Action>();

    public Set<Action> getActions() {
        return actions;
    }

    public void addAction(Action a) {
	actions.add(a);
    }


    /** Scans the list of the user's action, finding the most recent
	(according to the action ID). Note that this may perhaps be done more
	efficiently with a SQL query instead.
	
	@return The highest recorded action id, or 0 (if none is recorded)
     */
    public long getLastActionId() {
	Action la=getLastAction();
	return la==null? 0 : la.getId();
    }

    public Action getLastAction() {
	Action la = null;
	if (getActions()==null) return null;
	for(Action a: getActions()) {
	    if (la==null || a.getId() > la.getId()) la = a;
	}
	return la;
    }
  
    /** Scans the list of the user's action, taking only "main page"
	and "email page" actions into consideration, and finding the
	most recent (according to the action ID). Note that this may
	perhaps be done more efficiently with a SQL query instead.
	
	<p>FIXME: Using the "most recent action id" may sometimes be
	deceptive, since the user may sometimes be accessing old
	presented lists (thanks to the email interface). We may want
	to use a different criterion when we really are concerned with
	the most recented suggestion list...

	@return The highest recorded action id, or 0 (if none is recorded)
     */
     public Action getLastMainPageAction() {
	Action la = null;
	if (getActions()==null) return null;
	//int noSrcCnt=0, noMainCnt=0, mainCnt=0;
	for(Action a: getActions()) {
	    if (a.getSrc()==null) {
		// This is an old record, from before the Action.src
		// field was introduced. Such records aren't generated
		// anymore.
		//System.out.println("No src in Action=" + a);
		//noSrcCnt++;
		continue;
	    }
	    if (!a.getSrc().isMainPage() && 
		!a.getSrc().isEmailPage()) {
		//System.out.println("Not an MP Action=" + a);
		continue;
	    }
	    if (la==null || a.getId() > la.getId()) la = a;
	}
	return la;
    }
   
  
    /** @return [firstDate, lastDate] or null
     */
    private Date[] getActionTimeRange() {
	if (getActions()==null || getActions().size()==0) return null;
       Date x[] = new Date[2];
       for(Action a: getActions()) {
	   Date q = a.getTime();
	   if (x[0]==null || q.before(x[0])) x[0] = q;
	   if (x[1]==null || q.after(x[1])) x[1] = q;
       }
       return x;
    }
    


    /** Returns the list of actions, as a HashMap with the article ID
     * being a key */
    public HashMap<String, Action> getActionHashMap() {
	return getActionHashMap(null);
    }

    /** Returns the list of actions with the specified operations. For
	each article, only the most recent action (of one of the
	specified types) on that article is included.  

	@param ops List of specified operations. If null, all operations 
	are included.
	@return a HashMap with the article ID being a key, and the most recent operation (of one of the specified type) on that article being the value.
    */
    public HashMap<String, Action> getActionHashMap(Action.Op[] ops) {
	HashMap<String, Action> h = new HashMap<String, Action>();
	if (getActions()==null) {
	    throw new IllegalArgumentException("actions==null! user name = " + getUser_name());
	}
	for(Action a: getActions()) {
	    String aid = a.getAid();
	    if (aid==null) continue;
	    if (a.opInList(ops))  {
		Action b = h.get(aid);
		if (b==null || a.after(b)) {
		    h.put(aid, a);
		}
	    }
	}
	return h;
    }

    /** Similar to getAllActionsHashMap(), but only looks at actions with id
	greater than id0.
     */
    public HashMap<String, Vector<Action>> getAllActionsSince(long id0) {
	return getAllActionsSince(id0, null);
    }

    public HashMap<String, Vector<Action>> getAllActionsSince1(long id0, User.Day allowedType) {
	return getAllActionsSince(id0, new User.Day[] {allowedType});
    }

    /** Similar to getAllActionsHashMap(), but only looks at actions with id
	greater than id0.

	@param allowedDayTypes If not null, only actions on days of these
	types are taken into consideration
     */
    public HashMap<String, Vector<Action>> getAllActionsSince(long id0, User.Day[] allowedDayTypes) {

	ActionListTable h = new ActionListTable();

	int inRangeCnt=0, acceptedCnt=0;
	if (getActions()==null) return h;
	for( Action a: getActions()) {
	    if (a.getId()<=id0) continue;
	    inRangeCnt++;
	    if (!isAllowedType(a.getDay(), allowedDayTypes)) continue;
	    String aid = a.getAid();
	    if (aid==null) continue; // skip PREV_PAGE etc
	    h.add(aid, a);
	    acceptedCnt++;
	}
	Logging.info("getAllActionsSince("+id0+",{"+
		     Util.join(",", allowedDayTypes) +    "}): out of " + 
		     inRangeCnt + " actions, accepted " + acceptedCnt);
	return h;
    }

    static public class ActionListTable extends HashMap<String, Vector<Action>> {
	public void add(String aid, Action a) {
	    Vector<Action> b = this.get(aid);
	    if (b==null) {
		this.put(aid, b = new Vector<Action>());
		b.add( a);
	    } else {
		int pos = b.size(); // ensure ascending order
		while( pos > 0 && b.elementAt(pos-1).after(a)) pos--;
		b.insertElementAt(a, pos);
	    }
	}

	public long getLastActionId() {
	    long lid=0;
	    for(Vector<Action> v: values()) {
		for(Action a: v) {
		    lid = Math.max(lid, a.getId());
		}
	    }
	    return lid;
	}
    }

    /** Checks if the day type d is among the listed types
     */
    boolean isAllowedType(User.Day d, User.Day[] types) {
	if (types==null) return true;
	for(User.Day q: types) { if (d==q) return true; }
	return false;
    }

    /** Produces a HashMap which, for each article id (the key) contains
	a sorted vector of all actions with that page. The vector is sorted by
	timestamp, in ascending order.
     */
    public HashMap<String, Vector<Action>> getAllActionsHashMap() {
	return getAllActionsSince(0);
    }



    /** Only articles presently in the user's personal folder (i.e., added
	but not removed).
    */
    public HashMap<String, Action> getFolder() {
	HashMap<String,Action> folder=getActionHashMap(Action.Op.ALL_FOLDER_OPS);
	Set<String> keys = folder.keySet();
	//for(String aid: keys) {
	for( Iterator<String> it=keys.iterator(); it.hasNext(); ) {
	    String aid = it.next();
	    // removal from the key set is supposed to remove the element
	    // the underlying HashMap, as per the API
	    if (folder.get(aid).getOp()==Action.Op.REMOVE_FROM_MY_FOLDER) {
		it.remove();
	    }
	}
	return folder;
    }

    /** The same set pages as returned by getFolder(), but ordered by
	date, most recent first. This is suitable for display in
	folder list, as per TJ, 2013-08-20
     */
    public Action[] getFolderRecentFirst() {
	HashMap<String, Action> h = getFolder();
	Action[] arr = h.values().toArray(new Action[0]);
	Arrays.sort(arr); // ascending date order
	// reverse order
	for(int i=0; i*2 < arr.length-1; i++) {
	    Action tmp = arr[i];
	    arr[i] = arr[arr.length-1-i];
	    arr[arr.length-1-i] = tmp;
	}
	return arr;
    }


    public int getFolderSize() {
	return getFolder().size(); 
    }


    public int actionCnt(EntityManager em) {
	return actionCnt(em, -1);
    }
   /** How many operations have been recorded for a given user? 
	@param maxId : max action id. If negative, means "all".
     */
    public int actionCnt(EntityManager em, long maxId) {

	String qs=  "select count(a) from Action a where a.user.id=:uid" ;
	if (maxId>=0) qs += " and a.id<=:m";
	Query q = em.createQuery(qs);
	
	q.setParameter("uid",getId());
	if (maxId>=0) q.setParameter("m",  maxId);

	try {
	    Object o = q.getSingleResult();
	    return ((Number)o).intValue();
	} catch(Exception ex) { return 0; }
    }


    /** User's search queries */
    @OneToMany(cascade=CascadeType.ALL)
	private Set<EnteredQuery> queries = new LinkedHashSet<EnteredQuery>();

    public Set<EnteredQuery> getQueries() {
	return queries;
    }

    public EnteredQuery addQuery(String p, SessionData sd, int maxlen, int found) {
	EnteredQuery r = new EnteredQuery( this, sd, p, maxlen, found); 
	queries.add(r);	
	return r;
    }
  
    /** For some reason, EAGER seems to be necessary here! LAZY results in the
	data not being read, ever!
    */
    @Display(editable=false, order=20)
    @ElementCollection(fetch=FetchType.EAGER)
	  private Set<String> cats = new LinkedHashSet<String>();
    public Set<String> getCats() { 
	//Logging.info("getCats: cats=" + cats);
	return cats; 
    }

    public void setCats(Set<String> x) {	cats=x; }

    public boolean hasCat(String cat) {
	return getCats()!=null && getCats().contains(cat);

    }

    public int catCnt() {
	return getCats()==null? 0 : getCats().size();
    }

    public String listCats() {
	if (getCats()==null) return "[none]";
	String s="";
	for(String r: getCats()) {
	    s += " " + r;
	}
	return s; 	
    }

    /*
    synchronized public void addCat(String cat) {
	if (getCats().contains(cat)) return;
	HashSet<String> c = getCats();
	c.add(cat);
	setCats(c);
    }

    synchronized public void removeRole(String cat) {
	if (!getCats().contains(cat)) return;
	HashSet<String> c = getCats();
	c.remove(cat);
	setCats(c);

    }
    */

   /** Creates set of radio buttons reflecting the ArXiv categories this user
    is interested in. */
    public String mkCatBoxes() {
	return Categories.mkCatBoxes(this);
    }

    /** Given two Action maps, produce a union of sorts, each page being 
	mapped to the more recent action applied to it.
     */
    public static HashMap<String, Action> union(  HashMap<String, Action> a,
					    HashMap<String, Action> b) {
	HashMap<String, Action> c = new HashMap<String, Action>(a);
	for(String key: b.keySet()) {
	    Action z = b.get(key);
	    Action y = a.get(key);
	    if (y==null || z.after(y)) {
		c.put(key,z);
	    }
	}
	return c;
    }

    /** How many user entries have been created pursuant to a particular invitation?
	@param  invId invitation id
	@return User count
    */
    public static int invitedUserCount( EntityManager em, long invId) 
    //throws Exception	
    {
	Query q = em.createQuery("select count(m) from User m where m.invitation=:i");
	q.setParameter("i", invId);
	try {
	    return ((Long)q.getSingleResult()).intValue();
	} catch(Exception ex) { 
	    //throw ex;
	    Logging.error("exception in invitedUserCount: " + ex);
	    return 0;
	}
    }

    static private Integer long2int(Long x) {
	return new Integer( x.intValue());
    }

    public static HashMap<Long,Integer> invitedUserCounts( EntityManager em) 
throws Exception	
    {
	Query q = em.createQuery("select m.invitation,count(m) from User m group by m.invitation");
	HashMap<Long,Integer> h= new HashMap<Long,Integer> ();
	try {
	    List list = q.getResultList();
	    for(Object o: list) {
		Object[] z = (Object[]) o;
		h.put((Long)(z[0]), long2int((Long)(z[1])));
	    }
	} catch(Exception ex) { 
	    throw ex;
	    //Logging.error("exception in invitedUserCount: " + ex);
	    //return 0;
	}
	return h;
    }

    @SuppressWarnings("unchecked")
    /** Returns the list of users enrolled in a specified research program. 
     */
    static public List<Integer> selectByProgram(EntityManager em, User.Program program) {
	String qs = "select u.id from User u where u.program=:p";
	Query q = em.createQuery(qs);
	q.setParameter("p",program);
	List<Integer> lu  =  (List<Integer>) q.getResultList();
	return lu;
    }

  /** In some classes, certain fields should not be displayed or
	modified unless certain conditions apply. E.g., some fields of
	a User object only applies in certain programs. Implementing
	classes override this method as needed to impose appropriate
	restrictions.	
     */
    public boolean ignores(String fieldName) {
	if (fieldName.equals("days") ||
	    fieldName.equals("day") ||
	    fieldName.equals("excludeViewedArticles")) {	    
	    return getProgram()!=Program.SET_BASED &&
		getProgram()!=Program.PPP;
	} else if  (fieldName.equals("cluster")) {
	    return !getProgram().needBernoulli();
	}
	return false;
    }

    /** Creates a list of articles that are to be excluded from the suggestion
     list display.
    
    FIXME: It would be better to carry out folder-based exclusions based on the
    type of the action that put a document into the folder (COPY vs MOVE). Of
    course, doing it would require a retroactive change to the recorded
    actions for SET_BASED users. (Based on TJ's request, 2013-08-20). */
    public HashMap<String, Action> listExclusions() {
	if (getProgram()==User.Program.EE4) 
	    return union(getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN, Action.Op.INTERESTING_AND_NEW}),
			 getFolder());
	    
	// SET_BASED: we don't exclude based on the folder content (TJ, 2013-08-20)
	return getExcludeViewedArticles()?    getActionHashMap() :
	    getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN});

	//	    union(getActionHashMap(new Action.Op[] {Action.Op.DONT_SHOW_AGAIN}),
	//		  getFolder());

    }


    /** Link to the matching EE4User object, when applicable. 
	@return The matching object, or null.
     */
    //    public EE4User findEE4User() {
    //	return  (EE4User)em.find(EE4User.class, getId());
    //    }

    public String toString() {
	return "No. " + getId() + "("+getUser_name()+")";
    }

}
