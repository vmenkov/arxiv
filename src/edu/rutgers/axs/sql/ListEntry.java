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

    <p> This, of course, is a duplication of data being saved in disk
    files; see DataFile. But it this kind of duplication makes it
    possible to create a more robust system. For example, if a
    suggestion list is generated "on the fly" (from a servlet, rather
    than from a seprate process), it may choose to avoid writing a disk
    file, to avoid problems with file permissions etc. The file can
    then be created later, from the ListEntry table stored in the SQL server.
 */
@Entity
    public class ListEntry  extends OurTable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Display(editable=false, order=1)
	private long id;
    public void setId(long x) {        id = x;    }
    public long getId() {        return id;    }

    /** Link back to the suggestion list of which this entry is a part */
    @ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
	private DataFile df;
    public void setDf(DataFile x) {        df = x;    }
    public DataFile getDf() {        return df;    }

    /** By referring to an Article entry, we also have access to the
	article's ArXiv id */
    @ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
	private	Article article;
    public void setArticle(Article x) {        article = x;    }
    public Article getArticle() {        return article;    }

    /** 0-based position of this entry in the list. (This contrasts with ArticleEntry.i, which is 1-based). */
    @Basic  @Column(nullable=false)
	private int rank;
    public void setRank(int x) {        rank = x;    }
    public int getRank() {        return rank;    }

    /** Only relevant for PPP sugg lists, this contained (0-based)
	rank of the document before the list was perturbed.
     */
    @Basic  @Column(nullable=false)
	private int unperturbedRank;
    public void setUnperturbedRank(int x) {         unperturbedRank = x;    }
    public int getUnperturbedRank() {        return  unperturbedRank;    }


    /** 0-based position of this entry in the list */
    @Basic  @Column(nullable=false)
	private	double score;
    public void setScore(double x) {        score = x;    }
    public double  getScore() {        return score;    }

  
    /** This constructor does nothing. It has been added to avoid
	Enhancer's warning. */
    public ListEntry() {};

    //@ManyToOne
    //    @Basic      @Column(length=16)
    //	@Display(editable=false, order=2)
    //	String aid=null;
    //    public String getAid() { return aid; }
    //    public void setAid(String x) { aid=x;}
  
    public boolean validate(EntityManager em, StringBuffer errmsg) { 
	return true; 
    }

    /**
       @param aa Supplied in case the ArticleStats for the doc is
       missing (which is, generally, unlikely)
     */
    ListEntry( EntityManager em, DataFile _df, ArticleEntry e, int _rank) throws  org.apache.lucene.index.CorruptIndexException, IOException {
	setRank(_rank);
	setUnperturbedRank(e.iUnperturbed-1);
	setDf(_df);	
	Article a = Article.getArticleAlways(em, e.getAid(), false);
	setArticle(a);
	setScore(e.score);
    }


}