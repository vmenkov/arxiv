package edu.rutgers.axs;

/*
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
*/

import java.util.*;
import java.io.*;

//import javax.persistence.*;

//import edu.cornell.cs.osmot.options.Options;

//import edu.rutgers.axs.indexer.*;
//import edu.rutgers.axs.sql.*;
//import edu.rutgers.axs.web.Search;
//import edu.rutgers.axs.web.ArticleEntry;

public class Stoplist extends TreeSet<String>{
    Stoplist(File f) throws IOException {
	if (!f.exists()) throw new IllegalArgumentException("File '"+f+"' does not exist");
	FileReader fr = new FileReader(f);
	LineNumberReader r =  new LineNumberReader(fr);
	String s=null;
	while((s = r.readLine())!=null) {
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    add(s);
	}

    }
}
