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
    PresentedListEntry(ArticleEntry e)    {
	setRank(e.i);
	setAid(e.id);
	setScore(e.score);
	ArticleEntry.Provenance prov = e.getProv();
	if (prov!=null) {
	    setArank(prov.arank);
	    setBrank(prov.brank);
	    setFromA(prov.fromA);
	}
    }

    /** This is used when recording a user-reordered list.
	@param rank The new position, 1-based (to be consistent
	with the semantics used elsewhere)
    */
    PresentedListEntry(int rank, String aid)    {
	setRank(rank);
	setAid(aid);
	setScore(0);
	// FIXME: maybe we should copy this info from the original list?
	/*
	setScore(e.score);
	ArticleEntry.Provenance prov = e.getProv();
	if (prov!=null) {
	    setArank(prov.arank);
	    setBrank(prov.brank);
	    setFromA(prov.fromA);
	}
	*/
    }

    /** Returns a "dummy" ArticleEntry based on this PresentedListEntry
     */
    ArticleEntry toArticleEntry() {
	ArticleEntry ae = new ArticleEntry(getRank(), getAid());
	ae.setScore( getScore());
	return ae;
    }
 
    /** Applies in case of team-draft interleaving. Represents the
      rank of the article in the A-list. Rank is 1-based; 0 means the
      page was not present in the A-list */
    @Basic  @Column(nullable=false)
	int arank;
    public void setArank(int x) {        arank = x;    }
    public int getArank() {        return arank;    }
   
    @Basic  @Column(nullable=false)
	int brank;
    public void setBrank(int x) {        brank = x;    }
    public int getBrank() {        return brank;    }

    /** Was this page brought in from list A (typically, the
	experimental list) during the team-draft merging?
     */
    @Basic  @Column(nullable=false)
	boolean fromA;
    public void setFromA(boolean x) {        fromA = x;    }
    public boolean getFromA() {        return fromA;    }

    /** The article score, as shown to the user in the web page */
   @Basic  @Column(nullable=false)
       double score;
    public void setScore(double x) {        score = x;    }
    public double getScore() {        return score;    }
 
}