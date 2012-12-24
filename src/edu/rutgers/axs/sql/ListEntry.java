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

    This, of course, is a duplication of data being saved in disk files;
    see DataFile.
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

    /** 0-based position of this entry in the list */
    @Basic  @Column(nullable=false)
	int rank;
    public void setRank(int x) {        rank = x;    }
    public int getRank() {        return rank;    }

    /** By referring to an Article entry, we also have access to the
	article's ArXiv id */
    @ManyToOne
    @Column(nullable=false)
    @Display(editable=false, order=2) 
	Article article;
    public void setArticle(Article x) {        article = x;    }
    public Article getArticle() {        return article;    }

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
	setDf(_df);	
	Article a = Article.getArticleAlways(em, e.getAid(), false);
	setArticle(a);
    }


}