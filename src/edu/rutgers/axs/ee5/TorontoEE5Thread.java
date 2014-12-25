package edu.rutgers.axs.ee5;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.commons.lang.mutable.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.persistence.*;

import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.search.*;
import edu.rutgers.axs.upload.*;
import edu.rutgers.axs.indexer.*;

import edu.rutgers.axs.upload.BackgroundThread;

public class TorontoEE5Thread extends BackgroundThread {

    public String getProgressText() {
	String s = (startTime == null ?
		    "Data processing is about to start...\n" :
		    "Data processing started at " + startTime + "\n");
	s += progressText.toString();
	if (progressTextMore != null) {
	    s += progressTextMore + "\n";
	}
	if (endTime != null) {
	    s += "\n\nData processing completed at " + endTime;
	}
	return s;
    }

    private final String user;

    /** Creates a thread which will create the initial user profile
	and sug list.
     */
    public TorontoEE5Thread(String _user) {
	user = _user;
    }

    public void run()  {
	startTime = new Date();
	EntityManager em = null;
	IndexReader reader =  null;
	try {
	    
	    em  = Main.getEM();
	    reader = Common.newReader();
	    IndexSearcher searcher = new IndexSearcher( reader );
	   
	    final User.Program program = User.Program.EE5;
	    User u = User.findByName( em, user);
	    if (u==null) {
		error("User " + user + " does not exist");
		return;
	    }
	    if (!u.getProgram().equals(program)) {
		error("User " +user+ " is not enrolled in program " + program);
	    }

	    final int days = EE5DocClass.TIME_HORIZON_DAY;
	    Date since = SearchResults.daysAgo( days );

	    HashMap<Integer,EE5DocClass> id2dc = EE5DocClass.CidMapper.readDocClasses(em);
	   
	    DataFile df = Daily.makeEE5Sug(em,  searcher, since, id2dc, u);

	} catch(Exception ex) {
	    String errmsg = ex.getMessage();
	    error("Exception for TorontoEE5Thread " + getId() + ": " + errmsg);
	    ex.printStackTrace(System.out);
	} finally {
	    ResultsBase.ensureClosed( em, true);
	    if (reader != null) {
		try {
		    reader.close();
		} catch(IOException ex) {}
	    }
	    endTime = new Date();
	}

    }

    

}