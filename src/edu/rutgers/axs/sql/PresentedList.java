package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;
import edu.rutgers.axs.web.ArticleEntry;
import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** Each PresentedList instance contains information about one list of
    arfticles that was presented to the user within a single web page.   
    This is typically a section (10-25 articles) of the search results
    list produced for a particular query (or for the user's categories
    of interest), or a suggestion list produced by one algorithm or another.

    <p> Links to a PresentedList are contained in the "action source"
    information of all user actions where it makes sense. This allows
    one to reconstruct the "context" ("what the user saw") within which the 
    user performed a particular action.
 */
@Entity
    public class PresentedList extends OurTable implements Serializable  {

  /** SQL Object ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    /** Name of the user to whom the list was presented. (It would have been
	more efficient to store the numeric user id...)
     */
    //@ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
    //User user;
	@Basic String user;

    public String getUser() { return user;}
    public void setUser(String x) { user=x;}


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

    /** Don't use this. It exists just to keep the enhancer from complaining */
    private PresentedList() {}

    /** The basic constructor. Should be followed by calling set
	methods for the data list source (data file or query), and
	filling the actual list. 
     */
    public PresentedList(Action.Source type, String username) {
	setType(type);
	setUser(username);
	setTime( new Date());
    }


  /** Fills this DataFile's list of ListEntries based on the specified list of
	ArticleEntries.
     */
    public void fillArticleList(Vector<ArticleEntry> entries) {
	for(int rank=0; rank<entries.size(); rank++) {
	    PresentedListEntry le =  
		new PresentedListEntry(entries.elementAt(rank));
	    docs.add( le ); // FIXME: should not we use get/set?
	}
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

}