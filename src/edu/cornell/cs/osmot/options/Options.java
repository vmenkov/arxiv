package edu.cornell.cs.osmot.options;

import java.io.*;
import java.util.Date;
import java.util.Hashtable;
import javax.servlet.ServletContext;

import edu.cornell.cs.osmot.logger.Logger;
//import edu.cornell.cs.osmot.searcher.Searcher;

/**
 * This class collects all the high level options of the search engine in one
 * place. The values are all ready from a config file.
 * 
 * @author Filip Radlinski
 * @version 1.1, May 2005
 */
@SuppressWarnings("unchecked")
public class Options {

    private static Hashtable h;
    /** The time when loadOptions() was last successfully
     * performed. Null means never (so far).  lastWebUpdate trumps lastUpdate
     */
    private static Date lastUpdate=null, lastWebUpdate=null;

	// Options that must not be blank
    private static String requiredOptions[] = { "OSMOT_ROOT", "OPTIONS_LIFETIME",
			"INDEX_DIRECTORY", "UNIQ_ID_FIELD",
			"SEARCHER_NUM_FIELD_RESULTS", "SEARCHER_LIFETIME", "SEARCHER_URL", 
			"SEARCHER_BASE_URL", "SNIPPETER_FIELDS", "SNIPPETER_SNIPPET_LENGTH", 
			"SNIPPETER_CONTEXT_LENGTH", "SNIPPETER_MAX_DOC_LENGTH", 
			"SNIPPETER_PLAIN_TEXT", "RERANKER_CHAINS_MODEL_FILE",
			"RERANKER_CHAINS_USE_SCORES", "RERANKER_CHAINS_ONE_VALUE",
			"RERANKER_NOCHAINS_MODEL_FILE", "RERANKER_NOCHAINS_USE_SCORES",
			"RERANKER_NOCHAINS_ONE_VALUE", "RERANKER_FEATURE_FILE",
			"CACHE_DIRECTORY", "CACHE_DEFAULT_LENGTH", "INDEXER_MAX_LENGTH",
			"LEARNER_WMIN", "LEARNER_CLICK_PATTERN", "SVM_LEARN_PATH", 
			"LEARNER_COST", "LEARNER_N_RANKS", "SEARCHER_TYPE", "DEBUG",
			"LOG_DB", "LOG_USER", "LOG_PWD", "LOG_DIRECTORY", "LOG_PREFIX"};

    // Options that must be set, but can also be blank
    private static String requiredBlankableOptions[] = { 
	"SEARCHER_BASE_VISIBLE_URL"};
	
    private static String paths[] = { "INDEX_DIRECTORY", "LOG_DIRECTORY",
				      "CACHE_DIRECTORY" };

    public static boolean debug = false;

    static {
	//loadOptions(false); //VM		
    }

    synchronized public static String get(String name) throws IOException {

	if (lastUpdate==null) {
	    init();
	    //} else if (new Date().getTime() - lastUpdate.getTime() > 
	    //1000*60*Integer.parseInt((String)h.get("OPTIONS_LIFETIME"))) {
	    //loadOptions(true);
	}
	
	if (h.get(name) == null) {
	    System.err.println("Error: Option " + name + " is not set.");
	    Logger.log("Error: Option " + name + " is not set.");
	    // Funny hack to get a stack trace in a string so we can send it to the log file
	    CharArrayWriter c = new CharArrayWriter();
	    new Exception("StackTrace").printStackTrace(new PrintWriter(c));
	    Logger.log(c.toString());
	    //System.exit(1);
	    throw new IOException("Error: Option " + name + " is not set.");
	}
	return (String) h.get(name);
    }

    public static int getInt(String name) 	throws IOException{
	String s = get(name);
	return Integer.parseInt(s);
    }

    public static String[] getStrArr(String name) 	throws IOException{
	String s = get(name);
	return s.split(",");
    }

    public static double getDouble(String name) 	throws IOException{
	String s = get(name);
	return Double.parseDouble(s);
    }
	
    public static boolean getBool(String name) 
	throws IOException{
	String s = get(name);
	if (s.toLowerCase().equals("true"))
	    return true;
	return false;
    }

    private static Hashtable readOptionsToTable(boolean reload, BufferedReader in)
    throws IOException {
	// Initialize the hash table
	Hashtable hNew = reload ?
	    new Hashtable(h.size()) :
	    new Hashtable(requiredOptions.length);

	for (int i = 0; i < requiredOptions.length; i++)
	    hNew.put(requiredOptions[i], "");
  
	while (in.ready()) {
	    String line = in.readLine().trim();
	    if (line.length() == 0)
		continue;
	    if (line.charAt(0) == '#')
		continue;
	    line = line.replaceFirst("=", " ");
	    String parts[] = line.split(" ");
	    if (parts.length > 1)
		hNew.put(parts[0], parts[parts.length - 1]);
	    else
		hNew.put(parts[0], "");
	}    
	in.close();
	return hNew;
    }


    /** Conditional initialization - command-line application version */
    synchronized public static void init() throws IOException  {
	if (lastUpdate!=null) return;

	// Check if there is a global variable that contains the path to the
	// options.
	String configPath = "";
	if (System.getProperty("OSMOT_CONFIG") != null)
	    configPath = System.getProperty("OSMOT_CONFIG");
	if (configPath.length() > 0 && !configPath.endsWith("/"))
	    configPath += "/";

	//System.out.println("Current working directory is "+System.getProperty("user.dir"));

	//if (reload)
	//    System.out.println("Osmot reloading config file from "+configPath+"osmot.conf");
	//else
	//    System.out.println("Osmot loading config file from "+configPath+"osmot.conf");

	File f = new File(configPath+"osmot.conf");
	if (!f.exists()) {
	    throw new IOException("Config file " + f + " does not exist");
	}
	BufferedReader in = new BufferedReader(new FileReader(f));
	loadOptions(in, false, false);
    }

    /** Conditional initialization - web application version. If you're using
	Options in a web application, you need to make a call to
	edu.cornell.cs.osmot.options.Options.init(sd.getServletContext() )
	from one of your servlets early on.
    */
    synchronized public static void init(ServletContext context) throws IOException  {

	if (lastWebUpdate!=null) return;

	String path =  "/WEB-INF/osmot.conf";
	InputStream is = context.getResourceAsStream(path);
	if (is==null) throw new IOException("Cannot find file '"+path+"' at the server's application context.");
	BufferedReader in = new	BufferedReader(new InputStreamReader(is));
	loadOptions(in, false, true);

    }
	
    static private synchronized void loadOptions(BufferedReader in , boolean reload, boolean setWeb) 
	throws IOException
    {
	boolean error = false;
	Hashtable hNew=new Hashtable();
    
	// Open the options file. Will throw exception if not found
	hNew =    readOptionsToTable( reload, in); 
	
	// Check we don't have any values that aren't set correctly.
	for (int i=0; i<requiredOptions.length; i++) {
	    String key = requiredOptions[i];
	    String value = (String) hNew.get(key);
	    if (value == null || value.equals("")) {
		System.err.println("Error: Option " + key + " is not set.");
		error = true;
	    }
	}

	for (int i=0; i<requiredBlankableOptions.length; i++) {
	    String key = requiredOptions[i];
	    String value = (String) hNew.get(key);
	    if (value == null) {
		System.err.println("Error: Option " + key + " is not set (set it to blank if you want it blank).");
		error = true;
	    }
	}
	
	// Check that we have all mode probabilities 
	/*
	  for (int i=0; i<Searcher.modes.length; i++) {
	  String key = "SEARCHER_MODE_"+Searcher.modes[i];
	  String value = (String) hNew.get(key);
	  if (value == null) {
	  System.err.println("Error: Option "+ key +" is not set. It must be a positive number.");
	  error = true;
	  }
	  }
	*/
	// All errors in loading the options are fatal. Reloading can fail
	// without being fatal.
	if (error) {
	    if (reload) {
		System.err.println("RELOAD OF OSMOT OPTIONS ABORTED!");
		return;
	    } else {
		//System.err.println("OSMOT EXITING!");
		//System.exit(1);
		throw new IOException("Error: Option loading failed; see logs.");
	    }
	}
	
	// Prepend the root directory to most paths (except svm_learn path),
	// make sure a directory or file at each path exists. Create any that
	// don't.
	String root = hNew.get("OSMOT_ROOT").toString();
	if (!root.endsWith("/"))
	    root += "/";
	
	for (int i = 0; i < paths.length; i++) {
	    String path = hNew.get(paths[i]).toString();
	    if (!path.startsWith("/")) {
		path = root + path;
		hNew.put(paths[i], path);
	    }
	    
	    File f = new File(path);
	    if (f.exists()) {
		if (f.isDirectory()) {
		    //System.err.println("For "+paths[i]+", directory "+path + " already exists");		
		} else {
		    throw new  IOException("Path " +path+ " already exists, but is not a directory");
		}
	    } else if (f.mkdirs()) {
		System.err.println("Created "+paths[i]+" directory "+path);
	    }
	}

	debug = Boolean.parseBoolean((String)hNew.get("DEBUG"));
	
	if (error) {
	    if (!reload) {
		//System.exit(1);
		throw new IOException("Error: Option loading faied; see logs.");
	    }
	} else {
	    h = hNew;
	    lastUpdate = new Date();
	    if (setWeb) lastWebUpdate=lastUpdate;
	}
    }
}
