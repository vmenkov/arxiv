package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import javax.servlet.http.Cookie;

import org.apache.catalina.realm.RealmBase;

@Entity 
    @Table(name="arxiv_users", 
	   uniqueConstraints=@UniqueConstraint(name="arxiv_user_name_cnstrt", columnNames="user_name") )
    public class User implements OurTable 
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
    public void setUser_name(       String x) { user_name = x; }
    
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

    @Basic  @Column(length=64) @Display(order=3)
	String firstName;
    public  String getFirstName() { return firstName; }
    public void setFirstName(       String x) { firstName = x; }
 
   @Basic  @Column(length=64) @Display(order=4)
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

    /** End time for the current extended (persistent) sessions. If
	null, or in the past, then there is such session in effect for
	this user now. */
    @Display(editable=false, order=6) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
       Date esEnd;
    public  Date getEsEnd() { return esEnd; }
    public void setEsEnd(       Date x) { esEnd = x; }

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
	for(Role r: getRoles()) {
	    if (r.getERole() == name) return true;
	}
	return false;
    }

    public boolean hasAnyRole(Role.Name[] names) {
	for(Role.Name r: names) {
	    if (hasRole(r)) return true;
	}
	return false;
    }

    public boolean isAdmin() {
	return hasRole(Role.Name.admin);
    }

    public boolean isResearcher() {
	return hasRole(Role.Name.researcher);
    }

    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	    return true; 
    }

    @PostLoad
	void postLoad() {
	int n = 0;
	for(Role r: getRoles()) {
	    n++;
	}
	//Logging.info("Loaded a user entry with "+n +" roles: " + reflectToString());
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
	    "<th>Enabled</td>\n";
    }

   /** HTML: name, details, roles, enabled ; personal name, email*/
    public String to4cells() {
	return
	    "<td>" + getUser_name() + "</td>\n" +
	    "<td>" + reflectToString()  + "</td>\n" +
	    "<td>" + listRoles()  + "</td>\n" +
	    "<td>" + (isEnabled()? "Yes" : "No")  + "</td>\n" ;
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
        private Set<Action> actions = new LinkedHashSet<Action>();

    public Set<Action> getActions() {
        return actions;
    }

    public long getLastActionId() {
	long lai = 0;
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

    /** Produces a HashMap which, for each article id (the key) contains
	a sorted vector of all actions with that page. The vector is sorted by
	timestamp, in ascending order.
     */
    public HashMap<String, Vector<Action>> getAllActionsHashMap() {
	HashMap<String, Vector<Action>> h = new HashMap<String, Vector<Action>>();

	for( Action a: actions) {
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
	}
	return h;
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


    public Action addAction(String p, Action.Op op  ) {
        Action r = new  Action( this, p, op); //, now);
        actions.add(r);
        return r;
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

}
