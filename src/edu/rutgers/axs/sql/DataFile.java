package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import edu.cornell.cs.osmot.options.Options;
import edu.rutgers.axs.web.ArticleEntry;
import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.ParseConfig;


/** Each DataFile instance contains information about one external
    data file. Such data file may store e.g. a user profile, or a
    suggestion list.
 */
@Entity
    public class DataFile extends OurTable implements Serializable  {

  /** SQL Object ID */
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) 
	@Display(editable=false, order=1, link="../personal/viewSuggestions.jsp")  
	private int id;
    public void setId(int val) {        id = val;    }
    public int getId() {        return id;    }

    /** Link to the user on wose behalf the task is conducted */
    //@ManyToOne
    @Column(nullable=false)
	@Display(editable=false, order=2, link="viewUser.jsp?name=") 
    //User user;
	@Basic String user;
 
    public String getUser() {
	return user;
    }

    public void setUser(String c) {
	user=c;
    }

    /** When was this file generated? */
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


    /** The task id of the Task pursuant to which the file was
	created.  If the file was not created via the TaskManager, -1
	(or 0) will be stored here.
     */
    @Basic @Display(editable=false, order=4, link="viewObject.jsp?class=Task&id=") @Column(nullable=false)
	long task;
    public  long getTask() { return task; }
    public void setTask(long x) { task = x; }

  
   /** Various supported data file content types.  */
    public static enum Type {
	/** This is just used instead of null */
	//NONE,
	/** Basic user profile, based directly on the user's entire 
	    interaction history */
	USER_PROFILE,
	    /** A user profile that has been obtained by updating
		another user profile, using some recent user activity,
		by Thorsten's Algorithm 2. */
	    TJ_ALGO_2_USER_PROFILE,
	    /** Selection based on dot products with the USER_PROFILE */
	    LINEAR_SUGGESTIONS_1,
	    /** Selection based on dot products with the USER_PROFILE */
	    LOG_SUGGESTIONS_1,
	    /** Selection based on a sublinear utility function based
		on a user profile of some kind */
	    TJ_ALGO_1_SUGGESTIONS_1,
	    /** Peter Frazier's algorithm v.2 - "Bernoulli Rewards" */
	    BERNOULLI_SUGGESTIONS,
	    /** Peter Frazier's Explolration Engine ver 4 */
	    EE4_SUGGESTIONS,
	    /**  Thorsten's Perturbed Preference Perceptron */
	    PPP_USER_PROFILE,
	    PPP_SUGGESTIONS;

	/** Is this a user profile file? */
	public boolean isProfile() {
	    return this==USER_PROFILE ||
		this==TJ_ALGO_2_USER_PROFILE ||
		this==PPP_USER_PROFILE;
	}

	/** What task should we run to produce this kind of data? */
	public Task.Op producerFor() {
	    if (this == USER_PROFILE) return Task.Op.HISTORY_TO_PROFILE;
	    else if (this == LINEAR_SUGGESTIONS_1) return Task.Op.LINEAR_SUGGESTIONS_1;
	    else if (this == LOG_SUGGESTIONS_1) return Task.Op.LOG_SUGGESTIONS_1;
	    else if (this == TJ_ALGO_1_SUGGESTIONS_1) return Task.Op.TJ_ALGO_1_SUGGESTIONS_1;
	    else if (this == TJ_ALGO_2_USER_PROFILE) return Task.Op.TJ_ALGO_2_USER_PROFILE;
	    //	    else if (this == PPP_SUGGESTIONS) return Task.Op.PPP_SUGGESTIONS;
	    //	    else if (this == PPP_USER_PROFILE) return Task.Op.PPP_USER_PROFILE;

	    else throw new IllegalArgumentException("Don't know what task could produce file type=" +this);
	}

	/** Helps to form data file name for various file types.
	   
	   @return "profile", "linsug1", etc when files are needed;
	   null on errors
	*/
	String givePrefix() {
	    if (this == USER_PROFILE) {
		return "profile";
	    } else if (this == TJ_ALGO_2_USER_PROFILE) {
		return "algo2profile";
	    } else if (this == LINEAR_SUGGESTIONS_1) {
		return "linsug1";
	    } else if (this == LOG_SUGGESTIONS_1) {
		return "logsug1";
	    } else if (this == TJ_ALGO_1_SUGGESTIONS_1) {
		return "algo1sug1";
	    } else if (this == EE4_SUGGESTIONS) {
		return "ee4sug";
	    } else if (this == PPP_USER_PROFILE) {
		return "p3profile";
	    } else if (this == PPP_SUGGESTIONS) {
		return "p3sug";
	    } else {
		return null;
	    }	 
	}

	/** A human-readable description of the type of content this
	 * file includes.
	 */
	public String description() {
	    if (this == USER_PROFILE) return "User profile directly based on all the documents the user interacted with.";
	    else if (this == LINEAR_SUGGESTIONS_1) return "Linear similarity: dot product of documents (normalized) with the user profile. Reported scores are dot products";	    
	    else if (this == LOG_SUGGESTIONS_1) return "Log similarity: dot product of documents' log(TF) with the user profile. Reported scores are dot products";
	    else if (this == TJ_ALGO_1_SUGGESTIONS_1) return  "Joachims' Algorithm 1: ranking documents to maximize the utility function. Reported 'scores' are documents' increments to the utility function.";
	    else if (this == TJ_ALGO_2_USER_PROFILE) return "Joachims' Alghorithm 2: user profile updated based by updating the previously current user profile using the user's recent activity";
	    else return "Unknown";
	}

   }

    @Display(editable=false, order=6) 	@Column(length=32) 
    	@Enumerated(EnumType.STRING) 
    	private Type type;   

    public Type getType() { return type; }
    public void setType(Type x) { type = x; }

   /** The time range, in days, is used on certain suggestion list files,
       as when suggestions need to be generated only from the recently
	added articles (with dates in this ranged). 0 means "unlimited".
	Mostly superseded by the "since" field in suggestion lists, and is
	always ignored in other file types.
    */
    @Basic @Display(editable=false, order=6.1)     @Column(nullable=false)
    	private int days;   
    public int getDays() { return days; }
    public void setDays(int x) { days = x; }

   /** Includes articles selected among those added since that
       date. (Only applies to suggestion lists).
    */
    @Display(editable=false, order=6.2) 
	@Temporal(TemporalType.TIMESTAMP)     @Column(nullable=true)
	Date since;
    public  Date getSince() { return since; }
    public void setSince(       Date x) { since = x; }

    /** This flag is only applicable to PPP suggestion lists. If it is true, the
	top "pair" is (1), rather than (1,2).
     */
    @Basic  @Display(editable=false, order=6.3)    boolean pppTopOrphan = false;
    public boolean getPppTopOrphan() { return pppTopOrphan; }
    public void setPppTopOrphan( boolean x) {pppTopOrphan  = x; }



    /** Has the physical file been deleted? */
    @Basic  @Display(editable=false, order=6.4)    boolean deleted = false;
    public boolean getDeleted() { return deleted; }
    public void setDeleted( boolean x) {  deleted = x; }

   /** Has the physical file not been created yet? In this case, we read the
       data from the ListEntry array. */
    /*
    @Basic  @Display(editable=false, order=6.4)    boolean nofile = false;
    public boolean getNofile() { return nofile; }
    public void setNofile( boolean x) {  nofile = x; }
    */

    /** The input file based on which (if applicable) this one has
	been generated */
    /*
    @Basic      @Column(length=64) @Display(order=8, editable=false)
	String inputFile;
    public String getInputFile() { return inputFile; }
    public void setInputFile( String x) { inputFile = x; }
    */
 
    /** Set by the web server, if applicable */
    @ManyToOne @Display(order=8, editable=false, link="viewObject.jsp?class=DataFile&id=")
	private DataFile inputFile=null;
    public DataFile getInputFile() { return inputFile; }
    public void setInputFile( DataFile x) { inputFile = x; }



    /** This file's path name, relative to $DATAFILE_DIRECTORY/$user_ */
    @Basic      @Column(length=64) @Display(order=9, editable=false)
	String thisFile;
    public String getThisFile() { return thisFile; }
    public void setThisFile( String x) { thisFile = x; }

    /** In the case of a suggestion list, this refers to the list of
	article info entries; otherwise (i.e., for a user profile), it
	is empty */
    @OneToMany(cascade=CascadeType.ALL)
    //        private Set<ListEntry> docs = new LinkedHashSet<ListEntry>();
        private Vector<ListEntry> docs = new Vector<ListEntry>();

    //    public  Set<ListEntry> getDocs() {
    public Vector<ListEntry> getDocs() {
        return docs;
    }

    /** Fills this DataFile's list of ListEntries based on the specified list of
	ArticleEntries.
     */
    public void fillArticleList(Vector<ArticleEntry> entries, EntityManager em) {
	for(int rank=0; rank<entries.size(); rank++) {
	    ListEntry le = null;
	    try {
		le = new ListEntry( em, this, entries.elementAt(rank), rank);
	    } catch(Exception ex) {
		Logging.error("DataFile.initList: Can't record data for article  " + entries.elementAt(rank).getAid() + " (rank="+rank+"), as it may not be in Lucene yet");
		continue;
	    }
	    docs.add( le ); // FIXME: should not we use get/set?
	}
    }

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
	qs += " order by  m.lastActionId desc, m.time desc";
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

    /** This version is used in Algo 2: looking not just for any old
	suggestion file, but one tnat was generated based on a profile
	of a particular type.
     */
    static public DataFile getLatestFile(EntityManager em, String  username, 
					 Type t, Type parentType, int days) {
	String qs = "select m from DataFile m where m.user=:u and  m.type=:t and m.deleted=FALSE and m.inputFile.type=:p";
	if (days>=0) qs += " and m.days=:d";
	qs += " order by  m.lastActionId desc, m.time desc";

	Query q = em.createQuery(qs);

	q.setParameter("u", username);
	q.setParameter("t", t);
	q.setParameter("p", parentType);
	if (days>=0) 	q.setParameter("d", days);

	q.setMaxResults(1);
	List<DataFile> res = (List<DataFile>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    /** Finds the latest file of a given type (probably, sugg list)
	that's based on a particular input file (probably, a user profile file)

	@param days The value in the DataFile.days of the file to be
	retrieved. 

	@param inputFile Input file name (as recorded in
	DataFile.inputFile) based on which the file we're retrieving
	must be based.
     */
    static public DataFile getLatestFileBasedOn(EntityManager em, String username, 
						Type t, int days, String inputFile) {
	String qs = "select m from DataFile m where m.user=:u and  m.type=:t and m.deleted=FALSE";
	qs += " and m.inputFile.thisFile=:i";
	if (days>=0) qs += " and m.days=:d";
	qs += " order by  m.lastActionId desc, m.time desc";

	Query q = em.createQuery(qs);

	q.setParameter("u", username);
	q.setParameter("t", t);
	q.setParameter("i", inputFile);
	if (days>=0) 	q.setParameter("d", days);

	q.setMaxResults(1);
	List<DataFile> res = (List<DataFile>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    /** Finds the latest file of a given type (probably, sugg list)
	that's based on an input file of a particular type (probably,
	a user profile type)
   
	@param t Looking for a file of this type
	@param sourceType Looking for a file whose source is of that type
     */
    static public DataFile getLatestFileBasedOn(EntityManager em, String  username, 
						Type t, int days, Type sourceType) {
	String qs = "select m from DataFile m " +
	    "where m.inputFile.type=:st " +
	    "and m.user=:u and m.type=:t and m.deleted=FALSE";
	if (days>=0) qs += " and m.days=:d";
	qs += " order by  m.lastActionId desc, m.time desc";

	Query q = em.createQuery(qs);

	q.setParameter("u", username);
	q.setParameter("t", t);
	q.setParameter("st", sourceType);
	if (days>=0) 	q.setParameter("d", days);

	q.setMaxResults(1);
	List<DataFile> res = (List<DataFile>)q.getResultList();

	Logging.info("DataFile.getLatestFileBasedOn(user="+username+", t=" + t+", days=" + days+", sourceType=" + sourceType +") gives " + res.size() + " results");

	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }

    /** Lists all DataFile objects of a particular type based on a specified data file. 
	This can be used e.g. to find all suggestion lists build based on a particular 
	user profile.
	@param srcFile The "parent" DataFile object. It can be null,
	in which case DataFile objects with a null for inputFile are retrieved.
     */ 
    static public List<DataFile> getAllFilesBasedOn(EntityManager em, String  username, 
						    Type t, DataFile srcFile) {
	String qs = "select m from DataFile m " +
	    "where m.user=:u and m.type=:t and m.deleted=FALSE";
	if (srcFile==null)  {
	    qs += " and m.inputFile is null";
	} else {
	    qs += " and m.inputFile=:sf";
	}

	Query q = em.createQuery(qs);

	q.setParameter("u", username);
	q.setParameter("t", t);
	if (srcFile != null) { q.setParameter("sf", srcFile); }

	return (List<DataFile>)q.getResultList();
    }


    /** Finds the DataFile entry with a matching name */
    static public DataFile findFileByName(EntityManager em, String  username, String file) {
	String qs = "select m from DataFile m where m.user=:u and m.thisFile=:f";
	Query q = em.createQuery(qs);

	q.setParameter("u", username);
	q.setParameter("f", file);

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
	s += "user"  +	File.separator;
	return s + getUser() + File.separator + getThisFile();
    }

    static File getMainDatafileDirectory()  {
	String s = "";
	try {
	    s = Options.get("DATAFILE_DIRECTORY") +	File.separator;
	} catch(Exception ex) {
	    Logging.error(ex.getMessage());
	    ex.printStackTrace(System.err);
	}
	return new File(s);
    }

    // FIXME: READABLE  by EVERYONE, eh?
   public void makeReadable() {
	try {
	    File mainDir = getMainDatafileDirectory();
	    String mainPath = mainDir.getCanonicalPath();
	    for(File g=getFile(); !g.getCanonicalPath().equals( mainDir); g=g.getParentFile()) {
		g.setReadable(true, false);
	    }
	} catch(Exception ex) {
	    Logging.error(ex.getMessage());
	    ex.printStackTrace(System.err);
	}

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
    public DataFile(Task task) {
    	this(task, task.getOp().outputFor()); 
    }

    /**
       @param type : the file type
     */
    public DataFile(Task task, Type type) {
	this(task.getUser(), task.getId(), type);	    
    }
    
    public static String mkFileName(Type type, Date now) {
	String prefix = type.givePrefix();
	if ( prefix==null ||  prefix.equals(""))  {
	    throw new IllegalArgumentException("File type " + type + " not supported for file creation!");
	} else {
	    int pid = Main.getMyPid();
	    return prefix + File.separator + dayFmt.format(now) + "." +
		fmt1.format(pid) + "." + fmt2.format(fileCnt++) + ".txt";
	}
   }

    public DataFile(String user, long taskId, Type type) {
	this();
	Date now = new Date();
	String f = mkFileName(type, now);
	setType(type);
	setUser(user);
	setTask(taskId);	    
	setTime( now);
	setThisFile(f);
   }


    /** List DataFile objects that should have disk files but don't.
	(They probably were created from a servlet, when writing
	files was not allowed due to the file permissions situation)
     */
    static public List<DataFile> listMissingFiles(EntityManager em) {
	//	String qs = "select m from DataFile m where m.thisFile IS NULL and  m.type in (:t0, :t1, :t2) and m.deleted=FALSE";
	String qs = "select m from DataFile m where m.thisFile IS NULL and  m.type in (:tlist) and m.deleted=FALSE";

	//	if (days>=0) qs += " and m.days=:d";
	Query q = em.createQuery(qs);
	
	Type[] types = { Type.TJ_ALGO_1_SUGGESTIONS_1, 
		       Type.BERNOULLI_SUGGESTIONS,
		       Type.EE4_SUGGESTIONS};
	//	for(int i=0; i<types.length; i++) {
	//	    q.setParameter("t" + i, types[i]);
	//	}
	q.setParameter("tlist", Arrays.asList(types));

	//if (days>=0) 	q.setParameter("d", days);

	List<DataFile> res = (List<DataFile>)q.getResultList();
	return res;
    }

    /** Unit test */
    static public void main(String[] argv) throws Exception {
	ParseConfig ht = new ParseConfig();
	
	if (argv.length == 0) {
	    System.out.println("This is a unit test application");
	    System.out.println("Usage: java -Duser=xxx DataFile [basedon id]");
	    return;
	}

	String user = ht.getOption("user", "vmenkov");
	EntityManager em  = Main.getEM();

	String cmd = argv[0];
	if (cmd.equals("basedon")) {
	    int srcId = Integer.parseInt(argv[1]);
	    DataFile srcFile = (srcId==0)? null : (DataFile)em.find(DataFile.class, srcId);
	    System.out.println("user=" + user + ", src file = " + srcFile);
	    DataFile.Type types[] = {DataFile.Type.LINEAR_SUGGESTIONS_1,
				     DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1,
				     DataFile.Type.PPP_SUGGESTIONS};
	    for(DataFile.Type type: types) {
		System.out.println("Looking for files of the type " + type);
		List<DataFile> list = DataFile.getAllFilesBasedOn(em,  user, 
								  type, srcFile);
		if (list==null) {
		    System.out.println("Found no results");
		} else {
		    System.out.println("Found " +list.size() + " results");
		    int cnt=0;
		    for(DataFile f: list) {
			System.out.println("Result["+cnt+"]=" + f);
			cnt ++;
		    }
		}
	    }
	} else {
	    System.out.println("Invalid command: " + cmd);
	}
	 


    }


}
