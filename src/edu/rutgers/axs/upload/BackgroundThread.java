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
    
    /** Human-readable text used to display this thread's progress. */
    protected StringBuffer progressText = new StringBuffer();
    /** The last line, which may be replaced by a later call, in order
	to achieve a more concise report on the user's screen. No
	trailing LF attached yet. */
    protected String progressTextMore = null;

    public void progress(String text) {
	progress(text, false, false);
    }

    public void error(String text) {
	progress(text, true, false);
    }

    /** Adds a line of text to the progress text visible to the user.
       @param replace If true, this line replaces the last line.
     */
    protected void progress(String text, boolean isError, boolean replace) {
	if (progressTextMore != null  && !replace) {
	    progressText.append(progressTextMore + "\n");
	}
	progressTextMore = text;
	
	if (isError) {
	    Logging.error(text);
	} else {
	    Logging.info(text);
	}
    }

    public abstract String getProgressText();


    /** Displayable progress indicator. Only used during the
	processing of HTML docs */
    public ProgressIndicator pin = null;

    /** An HTML table that displays a graphic progress indicator */
    public String getProgressIndicatorHTML(String cp) {
	return pin==null? "" : pin.toHTML(cp);
    }


}