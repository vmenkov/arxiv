package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
//import java.text.*;
//import java.net.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.*;
//import java.lang.reflect.*;
import java.lang.annotation.*;

//import edu.rutgers.axs.indexer.ArxivFields;

import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.recommender.ArxivScoreDoc;

@Embeddable
    public class SimRowEntry implements Serializable{
    /** Refers to the ArticleStat.id of the relevant page. This is
	    truncated from long to int, as we don't expect the id to 
	    go over 2^31 */
    @Basic
	private  int astid;
    public int getAstid() {        return astid;    }
    public void setAstid(int x) {        astid=x;    }


    /** The actual cosine similarity */
    @Basic
	public  float sim;
   public float getSim() {        return sim;    }
    public void setSim(float x) {        sim=x;    }


    SimRowEntry(long _astid, double _sim) {
	setAstid( (int)_astid);
	setSim((float)_sim);
    }
    
    /** Just for the enchancer */
    SimRowEntry() { } 
    static SimRowEntry mkEntry(ArxivScoreDoc x,  ArticleStats[] allStats) {
	ArticleStats a = allStats[x.doc];
	if (a==null) {
	    Logging.warning("No ArticleStats entry for doc no=" + x.doc);
	    return null;
	}
	return new SimRowEntry ( a.getId(), x.score);
    }
    public SimRowEntry transpose(int _astid) {
	return new SimRowEntry(_astid, sim);
    }
}

