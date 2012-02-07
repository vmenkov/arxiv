package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

import edu.cornell.cs.osmot.options.Options;


/** Each DataFile instance contains information about one external
    data file, 
 */
@Entity
    public class DataFile  implements Serializable, OurTable {

  /** Transaction ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
	private long id;
    public void setId(long val) {        id = val;    }
    public long getId() {        return id;    }

    /** Link to the user on whose behalf the task is conducted */
    //@ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
    //User user;
	@Basic String user;
 
    public String getUser() {
	return user;
    }

    public void setUser(String c) {
	user=c;
    }

    /** When generated. */
    @Display(editable=false, order=3) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date time;
    public  Date getTime() { return time; }
    public void setTime(       Date x) { time = x; }

    /** What is the Action id of the most recent user action which was used 
	to generate this file? This is direct impact for user profiles, and
	indirect (via the user profile) for suggestion lists. */
    @Basic @Display(editable=false, order=3.2)
	@Column(nullable=false)
	long lastActionId;
    public  long getLastActionId() { return lastActionId; }
    public void setLastActionId(long x) { lastActionId = x; }


    /** The task id of the Task pursuant to which the file was created,
	If the file was not created via the TaskManager, -1 (or 0) will be stored here.
     */
    @Basic @Display(editable=false, order=4 )
	@Column(nullable=false)
	long task;
    public  long getTask() { return task; }
    public void setTask(long x) { task = x; }

  
   /** Various supported task types.  */
    public static enum Type {
	USER_PROFILE,
	    /** Selection based on dot products with the USER_PROFILE */
	    LINEAR_SUGGESTIONS_1,
	    TJ_ALGO_1_SUGGESTIONS_1;

	/** What task should we run to produce this kind of data? */
	public Task.Op producerFor() {
	    if (this == USER_PROFILE) return Task.Op.HISTORY_TO_PROFILE;
	    else if (this == LINEAR_SUGGESTIONS_1) return Task.Op.LINEAR_SUGGESTIONS_1;
	    else if (this == TJ_ALGO_1_SUGGESTIONS_1) return Task.Op.TJ_ALGO_1_SUGGESTIONS_1;
	    else throw new IllegalArgumentException("Don't know what task could produce file type=" +this);
	}

	/**
	   @return "profile" etc when files are needed; null on errors
	*/
	String givePrefix() {
	    if (this == USER_PROFILE) {
		return "profile";
	    } else 	if (this == LINEAR_SUGGESTIONS_1) {
		return "linsug1";
	    } else 	if (this == TJ_ALGO_1_SUGGESTIONS_1) {
		return "algo1sug1";
	    } else {
		return null;
	    }	 
	}

	/** A human-readable description of the type of content this
	 * file includes.
	 */
	public String description() {
	    if (this == USER_PROFILE) return "User profile, which includes terms from the documents the user interacted with.";
	    else if (this == LINEAR_SUGGESTIONS_1) return "Linear similarity: dot product of documents with the user profile. Reported scores are dot products";
	    else if (this == TJ_ALGO_1_SUGGESTIONS_1) return  "Joachims' Algorithm 1: ranking documents to maximize the utility function. Reported 'scores' are documents' increments to the utility function.";
	    else return "unknown";
	}

   }

    @Display(editable=false, order=6) 	@Column(length=32) 
    	@Enumerated(EnumType.STRING) 
    	private Type type;   

    public Type getType() { return type; }
    public void setType(Type x) { type = x; }

   /** The time range, in days, used to sub-class certain task, such 
	as when suggestions need to be generated only from the recently
	added articles (with dates in this ranged). 0 means "unlimited".
	Ignored by most other tasks.
    */
    @Basic @Display(editable=false, order=6.1)     @Column(nullable=false)
    	private int days=0;   
    public int getDays() { return days; }
    public void setDays(int x) { days = x; }


    /** Has the physical file been deleted? */
    @Basic  @Display(editable=false, order=6.2)    boolean deleted = false;
    public boolean getDeleted() { return deleted; }
    public void setDeleted( boolean x) {  deleted = x; }


    /** The input file based on which (if applicable) this one has
	been generated */
    @Basic      @Column(length=64) @Display(order=8, editable=false)
	String inputFile=null;
    public String getInputFile() { return inputFile; }
    public void setInputFile( String x) { inputFile = x; }

    /** This file's path name, relative to $DATAFILE_DIRECTORY/$user_ */
    @Basic      @Column(length=64) @Display(order=9, editable=false)
	String thisFile=null;
    public String getThisFile() { return thisFile; }
    public void setThisFile( String x) { thisFile = x; }

    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	return true; 
    }

    public String toString() {
	return thisFile;
    }

    /** Gets the most recently generated non-deleted file by a given
	type for a given username.	
     */
    static public DataFile getLatestFile(EntityManager em, String  username, Type t) {
	return  getLatestFile( em, username,  t, -1);
    }

    /** Gets the most recently generated non-deleted file by a given
	type for a given username, with a given days range.	
	@param days The day range. If a negative value is given, it's
	ignored.
     */
    static public DataFile getLatestFile(EntityManager em, String  username, Type t, int days) {
	String qs = "select m from DataFile m where m.user=:u and  m.type=:t and m.deleted=FALSE";
	if (days>=0) qs += " and m.days=:d";
	qs += " order by m.time desc";
	Query q = em.createQuery(qs);

	q.setParameter("u", username);
	q.setParameter("t", t);
	if (days>=0) 	q.setParameter("d", days);

	q.setMaxResults(1);
	List<DataFile> res = (List<DataFile>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    /** Maps to a file system file. */
    public File  getFile() {
	return new File(getPath());
    }

    /** Maps to a full file system path. */
    String  getPath()  {
	String s = "";
	try {
	    s = Options.get("DATAFILE_DIRECTORY") +	File.separator;
	} catch(IOException ex) {
	    Logging.error("Don't know where DATAFILE_DIRECTORY is");
	}
	return s + getUser() + File.separator + 	    getThisFile();
    }

    public DataFile() {}
    
    /**
       return "" When no files needed to be created; "profile" etc
       when files are needed; null on errors
     */
    /*
    static String givePrefix(Task.Op op) {
  	if (op == Task.Op.STOP) {
	    return "";
	} else 	if (op == Task.Op.HISTORY_TO_PROFILE) {
	    return "profile";
	} else 	if (op == Task.Op.LINEAR_SUGGESTIONS_1) {
	    return "linsug1";
	} else 	if (op == Task.Op.TJ_ALGO_1_SUGGESTIONS_1) {
	    return "algo1sug1";
	} else {
	    return null;
	}	 
    }
    */
    private static int fileCnt = 1;

    private static NumberFormat fmt1 = new DecimalFormat("00000");
    private static NumberFormat fmt2 = new DecimalFormat("000000");
    public static final DateFormat dayFmt = new SimpleDateFormat("yyyyMMdd");

    /** Creates a DataFile object describing the output that should be
	created for the specified task. Picks more or less unique name
	for it.

	The method is static-synchronized to ensure that the
	"fileCnt++" thing is atomic.

	FIXME: the name won't be unique if we have 2 processes - some hours
	apart - with the same pid!
	
	@return The DataFile object to be created for the task, or
	null if no file needs to be created. The object so created
	needs to be "persisted" later.
    */
    static synchronized public DataFile newOutputFile(Task task) {
    	return  newOutputFile(task, task.getOp().outputFor()); 
    }

    /**
       @param type : the file type
     */
    static synchronized public DataFile newOutputFile(Task task, Type type) {

	String prefix = type.givePrefix();
	String f = null;
	Date now = new Date();
	if ( prefix==null)  {
	    throw new IllegalArgumentException("File type " + type + " not supported for file creation!");
	} else if ( prefix.equals(""))  { // nothing
	    return null;
	} else {
	    int pid = Main.getMyPid();
	    f = prefix + File.separator + dayFmt.format(now) + "." +
		fmt1.format(pid) + "." + fmt2.format(fileCnt++) + ".txt";
	}
	DataFile df = new DataFile();
	df.setType(type);
	df.setUser(task.getUser());
	df.setTask(task.getId());	    
	df.setTime( now);
	df.setThisFile(f);
	return df;
    }


 

}
