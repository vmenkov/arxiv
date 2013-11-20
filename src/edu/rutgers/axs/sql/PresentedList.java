package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import org.apache.lucene.document.*;
import org.apache.lucene.search.IndexSearcher;

import edu.cornell.cs.osmot.options.Options;
import edu.rutgers.axs.web.ArticleEntry;
import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** Each PresentedList instance contains information about one list of
    articles that was presented to the user within a single web page.   
    This is typically a section (10-25 articles) of the search results
    list produced for a particular query (or for the user's categories
    of interest), or of a suggestion list produced by one algorithm or another.

    <p> Links to a PresentedList are contained in the "action source"
    information of all user actions where it makes sense. This allows
    one to reconstruct the "context" ("what the user saw") within which the 
    user performed a particular action.
 */
@Entity
    public class PresentedList extends OurTable implements Serializable  {

  /** SQL Object ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	@Display(editable=false, order=1, link="viewPresentedList.jsp")  
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    
    /** Link to the JPA entity for the user to whom the list was  presented. 
	Since 2013-11-19, this is nullable, so that we can record  SB lists
	for anonymous users.
    */
  @ManyToOne
    @Column(nullable=true)
	@Display(editable=false, order=1, link="viewUser.jsp") 
	User user;

    public User getUser() {	return user;    }
    private void setUser(User c) {	user=c;    }

    /** Session id. Especially useful for anon users' activity. 
	Introduced 2013-11-19. */
    @Column(nullable=false)
    @Display(editable=false, order=1.1) 
	long session;
    public long getSession() {
	return session;
    }

    private void setSession(long  c) {
	session=c;
    }
    /** When was this list (first) presented? */
    @Display(editable=false, order=3) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** The situation in which the list was generated and presented
	(search results, suggestion list of one kind or another,
	ect.). This corresponds to various screens of the My.ArXiv Web
	UI. This is normally the same value as recoreded for the user
	actions that originated from the context of this presented
	list. */
    @Column(nullable=true,length=24) @Enumerated(EnumType.STRING) 
	@Display(editable=false, order=5.2) 
    private Action.Source type;
    
    public Action.Source getType() { return type; }
    public void setType(Action.Source x) { type = x; }

    /** Optional link to the DataFile which contains the (original,
	Algo-2-generated) suggestion list on which this presented list
	was based (directly or via Team-Draft merger). This is only
	applicable to lists presenting a (section of a) suggestion list
	of some kind.
     */
    @Column(nullable=true) 	
	@Display(editable=false, order=5.3, link="../personal/viewSuggestions.jsp")  
	@Basic
	private long dataFileId;
    public void setDataFileId(long val) {     dataFileId    = val;    }
    public long getDataFileId() {        return  dataFileId;    }

    /** Optional link to the EnteredQuery in response to which this list
	was generated. This only applies to lists presenting a (section of)
	search result list.
     */
    @Column(nullable=true)
 	@Display(editable=false, order=5.4, link="viewEnteredQuery.jsp") 
	@Basic
	private long queryId;
    public void setQueryId(long val) {     queryId    = val;    }
    public long getQueryId() {        return  queryId;    }

    /** In the case of a suggestion list, this refers to the list of
	article info entries; otherwise (i.e., for a user profile), it
	is empty */
    @OneToMany(cascade=CascadeType.ALL)
    //        private Set<ListEntry> docs = new LinkedHashSet<ListEntry>();
        private Vector<PresentedListEntry> docs = new Vector<PresentedListEntry>();
    public Vector<PresentedListEntry> getDocs() { return docs; }

    /** Looks up the entry for the article with the specified ArXiv id
     */
    public PresentedListEntry getDocByAid(String aid) {
	for(PresentedListEntry ple: getDocs()) {
	    if (aid.equals(ple.getAid())) return ple;
	}
	return null;
    }

    /** Don't use this. It exists just to keep the enhancer from complaining */
    private PresentedList() {}

    /** The basic constructor. Should be followed by calling set
	methods for the data list source (data file or query), and
	filling the actual list. 
     */
    public PresentedList(Action.Source type, User u, long sid) {
	setType(type);
	setUser(u);
	setTime( new Date());
	setSession(sid);
    }


    /** Fills this DataFile's list of ListEntries based on the specified
	list of ArticleEntries.
    */
    public void fillArticleList(Vector<ArticleEntry> entries) {
	docs.setSize(0);
	for(int rank=0; rank<entries.size(); rank++) {
	    PresentedListEntry le=new PresentedListEntry(entries.elementAt(rank));
	    docs.add( le ); // FIXME: should not we use get/set?
	}
    }

    /**  Copies the content of this PresentedList into a vector of
	 ArticleEntry objects.
	 @param entries The vector to be filled. It also becomes the return value
    */
    public Vector<ArticleEntry> toArticleList( Vector<ArticleEntry> entries, IndexSearcher searcher) throws IOException {
	if (entries==null) entries=new  Vector<ArticleEntry>();
	entries.setSize(0);
	for(PresentedListEntry le: getDocs()) {	    
	    ArticleEntry e = le.toArticleEntry();
	    e.populateOtherFields(searcher);
	    entries.add(e);
	}
	return entries;
    }

    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	return true; 
    }
    
    /** Causes JPA fill in the docs array from the database.
     */
    public void fetchVecs() {
	Vector<PresentedListEntry> dummy = new Vector<PresentedListEntry>();	
	for (PresentedListEntry m : getDocs()) {
	    dummy.add(m);
	}
    }

 
    /** Find the PresentedList object corresponding to the most recent
	"set based" suggestion list seen by the specified user. In
	regard to "recency", what matters for us is not how recently
	the list was viewed, but how recently the suggestion list
	involved was *generated*. In production mode the two criteria
	are identical.
     */
    static public PresentedList findLatestPresentedSugList(EntityManager em, String  username) {
	String qs = "select m from PresentedList m where m.user is not null and m.user.user_name=:u and m.type in (:tlist) order by m.dataFileId desc, m.id desc";
	Query q = em.createQuery(qs);
	q.setParameter("u", username);
	q.setParameter("tlist", Arrays.asList(Action.Source.mainPageSources));

	q.setMaxResults(1);
	List<PresentedList> res = (List<PresentedList>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    static public PresentedList findLatestEmailSugList(EntityManager em, String  username) {
	String qs = "select m from PresentedList m where m.user is not null and m.user.user_name=:u and m.type in (:tlist) order by m.dataFileId desc,  m.id desc";
	Query q = em.createQuery(qs);
	q.setParameter("u", username);
	q.setParameter("tlist", Arrays.asList(Action.Source.emailPageSources));

	q.setMaxResults(1);
	List<PresentedList> res = (List<PresentedList>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }



}