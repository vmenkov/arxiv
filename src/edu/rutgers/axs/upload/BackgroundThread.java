package edu.rutgers.axs.upload;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.regex.*;
//import javax.persistence.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.lucene.index.*;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.ParseConfig;
import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.indexer.UploadImporter;
import edu.rutgers.axs.html.ProgressIndicator;

/** General framework for a task that starts when processing  one
    HTTP requests, and whose progress and completion can then
    be monitored via a number of subsequent requests.
*/
public abstract class BackgroundThread extends Thread {
    
    /** When the list generation started and ended. We keep this 
     for statistics. These values are set in run(). */
    protected Date startTime, endTime;

    static class ProgressLine {
	String text;
	//boolean iserror;
	boolean permanent;
	boolean strong;
	ProgressLine(String s, boolean _permanent, boolean _strong) {
	    text = s;
	    permanent = _permanent;
	    strong = _strong;
	};
	public String toString() { 
	    return strong?  "<strong>"  + text+  "</strong>" : text;
	}
    }
    
    /** Human-readable text used to display this thread's progress. */
    //    protected StringBuffer progressText = new StringBuffer();
    Vector<ProgressLine>  progressTextLines = new Vector<ProgressLine>();
     
    /** The last line, which may be replaced by a later call, in order
	to achieve a more concise report on the user's screen. No
	trailing LF attached yet. */
    //    protected String progressTextMore = null;

    /** This is set if a fatal error occurrs during the thread's
	operation */
    public boolean error = false;

    public void progress(String text) {
	progress(text, false, false, false);
    }

    public void error(String text) {
	error = true;
	progress(text, true, false, false);
    }

    /** Adds a line of text to the progress text visible to the user.
       @param replace If true, this line replaces the last line.
     */
    public void progress(String text, boolean isError, boolean replace, boolean strong) {
	boolean perm = strong;
	ProgressLine line = new ProgressLine(text, perm, strong);
	ProgressLine lastLine = (progressTextLines.size()>0) ? 
	    progressTextLines.lastElement() : null;
	if (replace && lastLine!=null && !lastLine.permanent) {
	    progressTextLines.set(progressTextLines.size()-1, line);
	} else {
	    progressTextLines.add(line);
	}

	/*
	if (progressTextMore != null  && !replace) {
	    progressText.append(progressTextMore + "\n");
	}
	progressTextMore = text;
	*/

	if (isError) {
	    Logging.error(text);
	} else {
	    Logging.info(text);
	}
    }

  
    String getProgressTextBasic(boolean strongOnly) {
	StringBuffer s= new StringBuffer();
	for(ProgressLine q: progressTextLines) {
	    if (strongOnly && !q.strong) continue;
	    s.append(q.toString() + "\n");
	}
	return s.toString();
    }

    public String getProgressText() {
	String about = taskName();
	String s = about + (startTime == null ?
		    " is about to start...\n" :
		    " started at " + startTime + "\n") +
	    getProgressTextBasic(false);
	if (endTime != null) {
	    s += about + " completed at " + endTime;
	}
	return s;	
    }

    public String getProgressTextBrief() {
	String about = taskName();
	String s = about + (startTime == null ?
		    " is about to start...\n" :
		    " started at " + startTime + "\n") +
	    getProgressTextBasic(true);
	if (endTime != null) {
	    s += about + " completed at " + endTime;
	}
	return s;	
    }


    /** Subclass-specific name to use in progress messages, such as
     * 'XXXX started', 'XXXX completed' */
    protected String taskName() {
	return "Processing";
    }

    /** Displayable progress indicator. Only used during the
	processing of HTML docs */
    public ProgressIndicator pin = null;

    /** An HTML table that displays a graphic progress indicator */
    public String getProgressIndicatorHTML(String cp) {
	return pin==null? "" : pin.toHTML(cp);
    }


}