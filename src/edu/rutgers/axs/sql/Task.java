package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

/** The Task table serves as the queue for communication between the
    web server and the computation-oriented process running in
    background.

    <p>Some fields are boolean; since MySQL treats them as TINYINT(1),
    to view them in a MySQL command-line client, one has to go through
    certain contortions, e.g.
   
    <pre>
select  IF(failed, 'true', 'false'), count(*) from Task group by failed;
    </pre>
 */
@SuppressWarnings("unchecked")
@Entity
    public class Task   extends OurTable implements Serializable {

    /** Transaction ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    /** Link to the user on whose behalf the task is conducted */
    //@ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
    //User
	String 	user;
 
    public String getUser() {
	return user;
    }

    public void setUser(String c) {
	user=c;
    }

   /** Various supported task types.  */
    public static enum Op {
	/** (Try to) stop the backgorund process */
	STOP,
	/** Review user's action history, generate profile */
	HISTORY_TO_PROFILE,
	    /** Linear model suggestions */
	    LINEAR_SUGGESTIONS_1,
	    LOG_SUGGESTIONS_1,
        /** Thorsten Joachims' Algorithm 1 */
	    TJ_ALGO_1_SUGGESTIONS_1,
        /** Thorsten Joachims' Algorithm 2 */
	    TJ_ALGO_2_USER_PROFILE;
	    //	    PPP_SUGGESTIONS,
	    //	    PPP_USER_PROFILE;

	/** What kind of file is created by each task? (Some tasks may
	 * create more than one file on occasions, but this method
	 * just returns the "main" type). */
	public DataFile.Type outputFor() {
	    if (this == Task.Op.HISTORY_TO_PROFILE) {
		return DataFile.Type.USER_PROFILE;
	    } else if (this == Task.Op.LINEAR_SUGGESTIONS_1) {
		return DataFile.Type.LINEAR_SUGGESTIONS_1;
	    } else if (this == Task.Op.LOG_SUGGESTIONS_1) {
		return DataFile.Type.LOG_SUGGESTIONS_1;
	    } else if (this == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
		return DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1;
	    } else if (this == Task.Op.TJ_ALGO_2_USER_PROFILE) {
		return DataFile.Type.TJ_ALGO_2_USER_PROFILE;
	    } else {
		throw new IllegalArgumentException("Don't know what kind of file create for task type=" +this);
	    }
	}


    }

    @Display(editable=false, order=3) 
  	@Enumerated(EnumType.STRING)  	@Column(length=32) 
    	private Op op;   

    public Op getOp() { return op; }
    public void setOp(Op _op) { op = _op; }


    /** The time range, in days, used to sub-class certain task, such 
	as when suggestions need to be generated only from the recently
	added articles (with dates in this ranged). 0 means "unlimited".
	Ignored by most other tasks.
    */
    @Basic @Display(editable=false, order=4)  @Column(nullable=false)
    	private int days;   
    public int getDays() { return days; }
    public void setDays(int x) { days = x; }

  /** Includes articles selected among those added since that
       date. (Only applies to suggestion lists). If this field is non-null,
       it overrides the "days" field.
    */
    @Display(editable=false, order=4.2) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date since;
    public  Date getSince() { return since; }
    public void setSince(       Date x) { since = x; }



    /** When the web server creates this request. */
    @Display(editable=false, order=5) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date requestTime;
    public  Date getRequestTime() { return requestTime; }
    public void setRequestTime(       Date x) { requestTime = x; }

    /** When the background thread accepts the request and starts
	working on the task. Not yet started tasks have null. */
    @Display(editable=false, order=6) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date startTime;
    public Date getStartTime() { return startTime; }
    public void setStartTime( Date x) { startTime = x; }

   /** When the background thread completes the request. Not yet
    * completed tasks have null. */
    @Display(editable=false, order=7) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date completeTime;
    public Date getCompleteTime() { return completeTime; }
    public void setCompleteTime( Date x) { completeTime = x; }

   
    /** The process ID of the TaskMaster process who did, or is doing,
	this task.

	FIXME: If the TaskMaster becomes multithreaded, we will need
	the thread ID as well.
     */
    @Basic @Display(editable=false, order=8) 
    	private int pid=0;   

    public int getPid() { return pid; }
    public void setPid(int x) { pid = x; }

    @Basic     @Column(nullable=false) @Display(order=8, editable=false)
	boolean canceled = false;
    public boolean getCanceled() { return canceled; }
    public void setCanceled( boolean x) {  canceled = x; }


   /** This field is usually 0. It will be incremented if it's 
	discovered that the TaskMaster has tried to do this task
	before but failed. This way the TaskMaster may learn about
	previous errors, and avoid trying to re-do the same task
	an infinite number of times.
    */
    @Basic @Column(nullable=false)    @Display(editable=false, order=9) 
    	private boolean failed=false;   

    public boolean getFailed() { return failed; }
    public void setFailed(boolean x) { failed = x; }



    /** Set by the web server, if applicable */
    /*
    @Basic      @Column(length=64) @Display(order=10, editable=false)
	private String inputFile=null;
    public String getInputFile() { return inputFile; }
    public void setInputFile( String x) { inputFile = x; }
    */

    /** Set by the computational thread, if applicable */
    /*
    @Basic      @Column(length=64) @Display(order=11, editable=false)
	private String outputFile=null;
    public String getOutputFile() { return outputFile; }
    public void setOutputFile( String x) { outputFile = x; }
    */

    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	return true; 
    }

    public String toString() {
	String s = "(Task(id="+getId()+", "+getOp()+ " | " + getDays()+" days)";
	s += ", canceled="+  getCanceled()+", failed="+getFailed()+
	    ", request=" +	    getRequestTime()+
	    ", start=" +	    getStartTime()+
	    ", done="+getCompleteTime()+"; pid="+  getPid()+
	    ", input=" + getInputFile()+
	    ", output=" + getOutputFile();
	return s;
    }

    /** Gets the next task from the queue.  If there is none, returns
	null.
     */
    static public Task getNextTask(EntityManager em) {
	Query q = em.createQuery("select m from Task m where m.startTime is null and m.canceled=FALSE  and m.failed=FALSE order by m.requestTime asc");
	q.setMaxResults(1);
	List<Task> res = (List<Task>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    public Task() {}

    public Task(String username, Op op) {
	setUser(username);
	setOp(op);
	setRequestTime(new Date());
    }

    /** Find all "outstanding" (neither fulfilled, nor canceled) tasks
     * of a certain type for a certain user.
     */
    static public List<Task> findOutstandingTasks(EntityManager em, String username, Op op) {
	Query q = em.createQuery("select m from Task m where m.user=:u and m.completeTime is null and m.canceled=FALSE and m.failed=FALSE and m.op=:op order by m.requestTime asc");
	q.setParameter("u", username);
	q.setParameter("op", op);
	List<Task> res = (List<Task>)q.getResultList();
	return res;
    }

    /** Started, but not yet completed */
    public boolean appearsActive() {
	return !getCanceled() && getStartTime()!=null && getCompleteTime()==null;
    }

    /** Marks apparently "stale" tasks as failed. Those are
	tasks that were started a long(ish) time ago, by a process
	different from this one, and never completed.
	null.
     */
    /*
    static public Task cancelStalledTasks(EntityManager em) {
	Query q = em.createQuery("update Task set .... ....  where m.startTime is null and m.canceled=FALSE  and m.failed=FALSE");
	q.setMaxResults(1);
	List<Task> res = (List<Task>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }
    */

    /** Set by the web server, if applicable */
    @ManyToOne @Display(order=10, editable=false, link="viewObject.jsp?class=DataFile&id=")
	private DataFile inputFile=null;
    public DataFile getInputFile() { return inputFile; }
    public void setInputFile( DataFile x) { inputFile = x; }

    /** Set by the computational thread, if applicable */
    @OneToOne  @Display(order=11, editable=false, link="viewObject.jsp?class=DataFile&id=")
	private DataFile outputFile=null;
    public DataFile getOutputFile() { return outputFile; }
    public void setOutputFile( DataFile x) { outputFile = x; }


}
