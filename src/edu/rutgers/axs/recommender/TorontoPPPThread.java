package edu.rutgers.axs.recommender;

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

public class TorontoPPPThread extends BackgroundThread {

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
    public TorontoPPPThread(String _user) {
	user = _user;
    }

    public void run()  {
	startTime = new Date();
	EntityManager em = null;
	IndexReader reader =  null;
	try {
	    
	    em  = Main.getEM();
	    reader = Common.newReader();
	    
	    String [] fields = ArticleAnalyzer.upFields;
	    final String [] xFields =  {ArxivFields.ARTICLE};

	    ArticleAnalyzer3 aa = new ArticleAnalyzer3( reader, xFields);
	    IndexSearcher searcher = new IndexSearcher( reader );

	    final User.Program program = User.Program.PPP;

	    User u = User.findByName( em, user);
	    if (u==null) {
		error("User " + user + " does not exist");
		return;
	    }
	    if (!u.getProgram().equals(program)) {
		error("User " + user + " is not enrolled in program " + program);
	    }

	    TorontoPPP.initP3Sum( em,  (ArticleAnalyzer3)aa,  searcher, u, this);
	
	} catch(Exception ex) {
	    String errmsg = ex.getMessage();
	    error("Exception for TorontoPPPThread " + getId() + ": " + errmsg);
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