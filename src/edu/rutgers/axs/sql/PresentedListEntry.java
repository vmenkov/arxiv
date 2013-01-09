package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import org.apache.lucene.search.IndexSearcher;
import edu.rutgers.axs.web.ArticleEntry;

import edu.cornell.cs.osmot.options.Options;
import edu.rutgers.axs.recommender.ArticleAnalyzer;


/** An entry in a presented suggestion list. We store those in order
    to be able, later on, to infer a "negative ranking" from the
    fact that a link was presented (at a certain high-enough-ranked
    position) to a user, but not clicked on by him.

<p>
    This, of course, is a duplication of data being saved in disk files;
    see DataFile.
 */
@Entity
    public class PresentedListEntry extends OurTable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
	private long id;
    public void setId(long x) {        id = x;    }
    public long getId() {        return id;    }

    /** Link back to the suggestion list of which this entry is a part */
    /*
    @ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
	private DataFile df;
    public void setDf(DataFile x) {        df = x;    }
    public DataFile getDf() {        return df;    }
    */

    /** 0-based position of this entry in the list */
    @Basic  @Column(nullable=false)
	int rank;
    public void setRank(int x) {        rank = x;    }
    public int getRank() {        return rank;    }

    /** ArXiv Article ID.
     */
    @Basic @Column(length=16,nullable=false) @Display(editable=false, order=2)
	String aid;
    public String getAid() { return aid; }
    public void setAid(String x) { aid=x;}


    /** This constructor does nothing. It has been added to avoid
	Enhancer's warning. */
    public PresentedListEntry() {};


    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	return true; 
    }

    /**
       @param aa Supplied in case the ArticleStats for the doc is
       missing (which is, generally, unlikely)
     */
    PresentedListEntry(
		       //ArticleAnalyzer aa, EntityManager em, 
		       ArticleEntry e)
    //throws  org.apache.lucene.index.CorruptIndexException, IOException 
    {
	setRank(e.i);
	setAid(e.id);
    }

    /** Returns a "dummy" ArticleEntry based on this PresentedListEntry
     */
    ArticleEntry toArticleEntry() {
	return new ArticleEntry(getRank(), getAid());
    }
    

}