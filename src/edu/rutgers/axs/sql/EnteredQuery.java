package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;
import  edu.rutgers.axs.web.SessionData;

/** An EnteredQuery instance records a single search query entered by the user.
 */
@Entity
    public class EnteredQuery  extends OurTable implements Serializable  {

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=2)
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    /** Link to the user whose action this is. For anonymous users'
	activity, this is null. */
    @ManyToOne
    @Column(nullable=true)
	@Display(editable=false, order=1, link="viewUser.jsp") 
	User user;
    public User getUser() {	return user;    }
    public void setUser(User c) { user=c;    }
      
    @Column(nullable=false)
    @Display(editable=false, order=1.2) 
	long session;
      public long getSession() {	return session;    }
    private void setSession(long  c) {	session=c;    }
   

    /** The text of the query */
    @Basic @Column(nullable=false)
    @Display(editable=false, order=2) 
	String query;  
    public String getQuery() {	return query;    }
    public void setQuery(String query) {   this.query = query;    }

    
    @Display(editable=false, order=3) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** How many results found */
    @Basic @Column(nullable=false)
    @Display(editable=false, order=4) 
	int foundCnt;
    public int getFoundCnt() {	return foundCnt;    }
    public void setFoundCnt(int x) {   this.foundCnt = x;    }

   /** Max requested number of search results  */
    @Basic @Column(nullable=false)
    @Display(editable=false, order=5) 
	int maxLen;
    public int getMaxLen() {	return maxLen;    }
    public void setMaxLen(int x) {   this.maxLen = x;    }

 
    /** Any text from an entry box, or the text value of an enum 
	response. Only some responses may have this. */
    //    @Basic   @Column(nullable=true) String text;
    //public String getText() { return text; }
    //public void setText(String x) { text = x; }

    static {
	Reflect.getReflect(EnteredQuery.class);
    }

    public EnteredQuery() {}

    public EnteredQuery(User u, SessionData sd, String _query, int _maxLen, int _foundCnt){ 
	setUser(u);
	setQuery(_query);
	setMaxLen(_maxLen);
	setFoundCnt(_foundCnt);
	setSession(sd.getSqlSessionId());
	Date now = (new GregorianCalendar()).getTime();
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

    /** Was this query recorded after the other query? */
    public boolean after(EnteredQuery other) {
	Date a = getTime(), b = other.getTime();
	if (a==null || b==null) throw new IllegalArgumentException("EnteredQuery.moreRecentThan(): Only responses with endTime can be compared");
	return a.after(b);
    }

}
