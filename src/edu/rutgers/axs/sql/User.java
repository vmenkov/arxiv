package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import javax.servlet.http.Cookie;

import org.apache.catalina.realm.RealmBase;

import edu.rutgers.axs.web.EditUser;
import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.web.WebException;
import edu.rutgers.axs.bernoulli.Bernoulli;


@Entity 
    @Table(name="arxiv_users", 
	   uniqueConstraints=@UniqueConstraint(name="arxiv_user_name_cnstrt", columnNames="user_name") )
    public class User extends OurTable 
{
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1.1)
    	private long id;

    //    public void setId(long val) {        id = val;    }
    /** This is the internal ID automatically assigned by the database
      to each entry upon creation. It is important within the database
      (e.g., to associate PhoneCall entries with respondent entries,
      but has no meaning outside of it.
     */
    public long getId() {        return id;    }

   
    //@Id 
	@Display(editable=false, order=1.1) 	@Column(length=15) 
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

    @Basic  @Column(length=64) @Display(order=3, alt="First (and middle) name")
	String firstName;
    public  String getFirstName() { return firstName; }
    public void setFirstName(       String x) { firstName = x; }
 
    @Basic  @Column(length=64) @Display(order=4, alt="Last name")
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

    @Basic  @Column(length=64) @Display(order=5)
	String email = ""; 
    public  String getEmail() { return email; }
    public void setEmail(       String x) { email = x; }

    /** Encrypted temporary password, used for extended (persistent)
	sessions of this user (those started using the "Remember me"
	checkbox). */
    @Basic  @Column(length=64) @Display(editable=false, order=7)
	String encEsPass="";
    public  String getEncEsPass() { return encEsPass; }
    public void setEncEsPass(       String x) { encEsPass = x; }


    @Basic @Display(order=9.1, alt="Agreed to participate in follow-up telephone survey")
        @Column(nullable=false) boolean survey;
    public  boolean getSurvey() { return survey; }
    public void setSurvey( boolean x) { survey = x; }


    @Basic @Display(order=9.2,rp=true,alt="Phone number (e.g. 732-932-0000)") 
    //@Usedin(phone=true)
        @Column(length=32)      String phoneNumber;
    public void setPhoneNumber(String val) {  phoneNumber        = val;    }
    public String getPhoneNumber() {        return phoneNumber ;    }


    @Basic @Display(order=9.3,rp=true,alt="Best times to reach you (e.g. 'weekday evenings')") 
         @Column(length=64)      String timesToReach;
    public void setTimesToReach(String val) {      timesToReach    = val;    }
    public String getTimesToReach() {        return timesToReach ;    }

  
    @Basic @Display(order=9.4, alt="Do you approve the use of audio recording for the telephone interview?")
        @Column(nullable=false) boolean approvedAudio;
    public  boolean getApprovedAudio() { return approvedAudio; }
    public void setApprovedAudio( boolean x) { approvedAudio = x; }



    /** End time for the current extended (persistent) sessions. If
	null, or in the past, then there is such session in effect for
	this user now. */
    @Display(editable=false, order=10) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
       Date esEnd;
    public  Date getEsEnd() { return esEnd; }
    public void setEsEnd(       Date x) { esEnd = x; }

    /** Describes what kind of research program the user is currently enrolled in */
    public static enum Program {
	/** Thorsten Joachims' set-based */
	SET_BASED, 
	    /** Peter Frazeer's Exploration Engine: Exploration mode */
	    BERNOULLI_EXPLORATION,
	    /** Peter Frazeer's Exploration Engine: Exploration mode */
	    BERNOULLI_EXPLOITATION;

	/** Do we need to use the Bernoulli Exploration Engine with
	 * this program?	 */
	public boolean needBernoulli() {
	    return this==BERNOULLI_EXPLORATION || this==BERNOULLI_EXPLOITATION;
	}
    }

    @Display(editable=false, order=11)	@Column(nullable=false,length=24)
	@Enumerated(EnumType.STRING)     
	private Program program;

    public Program getProgram() { return program; }
    public void setProgram(Program x) { program=x; }

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

       @names An array of roles. It must be non-null, but may be empty
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
	@Display(editable=true, order=11.1) 
    private User.Day day;
    
    public User.Day getDay() { return day; }
    void setDay(User.Day x) { day = x; }
	
    @Display(editable=false, order=11.2) 
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
	Date now = new Date();
	if (getDay()==null || getDayStart()==null ||
	    getDayStart().getTime() <= now.getTime() - 24 * 3600L * 1000L) {
	    forceNewDay();
	    return true;
	}
	else return false;
    }

    /** This should be called once a profile has been updated */
    public Day forceNewDay() {
	return  forceNewDay( Math.random() < 0.5 ? Day.LEARN : Day.EVAL);
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

    /** Human-readable printable message about this user's current
     * experimental day
     */
    public String dayMsg() {
	return ""+ getDay() + " mode since " + getDayStart();
    }

    /** The "time horizon" for selecting "recent articles" (for
	suggestion lists, etc). Default is 7 days.
     */
    @Basic  @Display(order=12,editable=true, alt=
		     "Time horizon (days). Recommendations will be selected from articles submited within so many days, or since your last visit to the site, whichever is earlier")

	private int days;
    public  int getDays() { return days; }
    public void setDays( int x) { days = x; }
 

    /** This exclusion only applies to suggestion lists, not to search results */
    @Basic @Display(order=13, alt="Exclude already-viewed articles from the list of recommendations")
        @Column(nullable=false) boolean excludeViewedArticles;
    public  boolean getExcludeViewedArticles() { return excludeViewedArticles; }
    public void setExcludeViewedArticles(boolean x) {excludeViewedArticles= x;}

    @Basic   @Display(order=14,editable=true)  @Column(nullable=false)
	private int cluster;
    public  int getCluster() { return cluster; }
    public void setCluster( int x) { cluster = x; }

    public User() {
	setCluster( Bernoulli.defaultCluster);
	setProgram( Program.SET_BASED);
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

    public String reflectToString() {
	String s = Reflect.customizedReflect(this, 
					     new PairFormatter.CompactPairFormatter());
	//s += " <strong>Roles: </strong>";
	//for(Role r: getRoles()) {	    s += " " + r;	}
	return s;
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
	    "<th>Detail (password is encrypted)</th>\n" +
	    "<th>Roles</th>\n" +
	    "<th>Enabled</th>\n" +
	    "<th>Interests</th>\n";
    }

   /** HTML: name, details, roles, enabled ; personal name, email*/
    public String to4cells() {
	return
	    "<td>" + getUser_name() + "</td>\n" +
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

    /** Scans the list of the user's action, finding the most recent
	(according to the action ID). Note that this may perhaps be done more
	efficiently with a SQL query instead.
	
	@return The highest recorded action id, or 0 (if none is recorded)
     */
    public long getLastActionId() {
	long lai = 0;
	if (getActions()==null) return 0;
	for(Action a: getActions()) {
	    if (a.getId() > lai) lai = a.getId();
	}
	return lai;
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
	for( Action a: actions) {
	    String aid = a.getArticle();
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

	@param dayType If not null, only actions on days of these
	types are taken into consideration
     */
    public HashMap<String, Vector<Action>> getAllActionsSince(long id0, User.Day[] allowedDayTypes) {

	HashMap<String, Vector<Action>> h = new HashMap<String, Vector<Action>>();

	int inRangeCnt=0, acceptedCnt=0;
	if (getActions()==null) return h;
	for( Action a: getActions()) {
	    if (a.getId()<=id0) continue;
	    inRangeCnt++;
	    if (!isAllowedType(a.getDay(), allowedDayTypes)) continue;
	    String aid = a.getArticle();
	    Vector<Action> b = h.get(aid);
	    if (b==null) {
		h.put(aid, b = new Vector<Action>());
		b.add( a);
	    } else {
		int pos = b.size(); // ensure ascending order
		while( pos > 0 && b.elementAt(pos-1).after(a)) pos--;
		b.insertElementAt(a, pos);
	    }
	    acceptedCnt++;
	}
	Logging.info("getAllActionsSince("+id0+",{"+
		     Util.join(",", allowedDayTypes) +    "}): out of " + 
		     inRangeCnt + " actions, accepted " + acceptedCnt);
	return h;
    }

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
	Action.Op[] ops =  {Action.Op.COPY_TO_MY_FOLDER,
			    Action.Op.REMOVE_FROM_MY_FOLDER};   
	HashMap<String, Action> folder = getActionHashMap(ops);
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

    public Action addAction(EntityManager em, String p, Action.Op op, ActionSource a  ) {
	Action r = new  Action( this, p, op); 
	r.setActionSource(a);
        actions.add(r);
	em.persist(r);
	r.bernoulliFeedback(em); // only affects Bernoulli users
	return r;
    }

    public int actionCnt(EntityManager em) {
	return actionCnt(em, -1);
    }
   /** How many operations have been recorded for a given user? 
	@param maxId : max action id. If negative, means "all".
     */
    public int actionCnt(EntityManager em, long maxId) {

	String qs= 	    "select count(a) from Action a where a.user.id=:uid" ;
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

    public EnteredQuery addQuery(String p, int maxlen, int found) {
	EnteredQuery r = new  EnteredQuery( this, p, maxlen, found); 
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


}
