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
    public class Action  implements Serializable, OurTable  {

    /** Transaction ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=2)
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    // Link to the user whose action this is
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
	    VIEW_ABSTRACT, VIEW_FORMATS, VIEW_PDF, VIEW_PS,
	    RESERVED_2,
	    RESERVED_1,
	    /** Feedback actions */
	    INTERESTING_AND_NEW,
	    INTERESTING_BUT_SEEN_TODAY,
	    INTERESTING_BUT_KNOWN,
	    USELESS,
	    COPY_TO_MY_FOLDER,
	    DONT_SHOW_AGAIN,
	    /** Activated thru the "view folder" screen */
	    REMOVE_FROM_MY_FOLDER;
	
    };

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

    public Action(User u, String _article, Op _op){ //, Date t) {	
	setUser(u);
	setArticle(_article);
	setOp(_op);
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

}
