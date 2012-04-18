package edu.rutgers.axs.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import org.apache.openjpa.persistence.jdbc.*;
import java.lang.reflect.*;
import java.lang.annotation.*;


import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


//import org.apache.lucene.document.*;
import edu.rutgers.axs.indexer.ArxivFields;

import edu.rutgers.axs.recommender.ArticleAnalyzer;
import edu.rutgers.axs.recommender.UserProfile;
import edu.rutgers.axs.recommender.ArxivScoreDoc;



/** One row of the similarity table */
@Embeddable
public class SimRow implements Serializable {

    //@Lob    @ElementCollection(fetch=FetchType.LAZY)
    @ElementCollection(fetch=FetchType.LAZY)
	  Vector<SimRowEntry> entries;
    public Vector<SimRowEntry> getEntries() { return entries; }
    public void setEntries(Vector<SimRowEntry> x) {	entries=x; }

    private static class CompareByAstid implements Comparator<SimRowEntry> {
	public int 	compare(SimRowEntry o1, SimRowEntry o2) {
	    return o1.getAstid() - o2.getAstid();
	}
    }

    private static CompareByAstid compareByAstid=new  CompareByAstid();



    public SimRow() {}

    /** Computes similarities of a given document (d1) to all other
	docs in the database. Used for Bernoulli rewards.

	@param cat If not null, restrict matches to docs from the specified category
    */
    public SimRow( int docno, ArticleStats[] allStats, EntityManager em,ArticleAnalyzer z) throws IOException {
	entries =new Vector<SimRowEntry>();
	HashMap<String, Double> doc1 = z.getCoef(docno, null);		
	Document doc = z.reader.document(docno);
	String cats =doc.get(ArxivFields.CATEGORY);
	CatInfo catInfo=new CatInfo(cats);
	Logging.info("Doing sims for doc " + allStats[docno].getAid() +", cats=" + cats + ", cat bases=" + catInfo);

	final double threshold = 0.1;
	final double thresholds[] = {threshold};

	double norm1=z.tfNorm(doc1);

	int numdocs = z.reader.numDocs() ;
	double scores[] = new double[numdocs];	
	int tcnt=0,	missingStatsCnt=0;

 	for(String t: doc1.keySet()) {
	    double q= doc1.get(t).doubleValue();

	    double qval = q * z.idf(t);
	    for(int i=0; i<ArticleAnalyzer.upFields.length; i++) {
		String f= ArticleAnalyzer.upFields[i];
		Term term = new Term(f, t);
		TermDocs td = z.reader.termDocs(term);
		td.seek(term);
		while(td.next()) {
		    int p = td.doc();
		    int freq = td.freq();			

		    double normFactor = 0;
		    if (allStats[p]!=null) {			
			normFactor = allStats[p].getBoost(i);
		    } else {
			missingStatsCnt++;
		    }

		    double w =qval * normFactor * freq;
		    scores[p] += w;
		}
		td.close();
	    } // for fields
	    tcnt++;		
	} // for terms 	    
	ArxivScoreDoc[] sd = new ArxivScoreDoc[numdocs];
	int  nnzc=0, abovecnt[]= new int[thresholds.length];

	for(int k=0; k<scores.length; k++) {
	    if (scores[k]>0) {
		Document doc2 = z.reader.document(k);
		String cat2 =doc2.get(ArxivFields.CATEGORY);
		boolean catMatch = catInfo.match(cat2);

		if (catMatch) {
		    double q = scores[k]/norm1;
		    for(int j=0; j<thresholds.length; j++) {
			if (q>= thresholds[j]) abovecnt[j]++;
		    }

		    if (q>=threshold && k!=docno) {
			sd[nnzc++] = new ArxivScoreDoc(k, q);
		    }
		}		
	    }
	}
	String msg="nnzc=" + nnzc;
	for(int j=0; j<thresholds.length; j++) {
	    msg += "; ";
	    msg += "above("+thresholds[j]+")="+ abovecnt[j];
	}
	Logging.info(msg);
	if (missingStatsCnt>0) {
	    Logging.warning("used zeros for " + missingStatsCnt + " values, because of missing stats");
	}

	for(int i=0; i<nnzc; i++) {
	    SimRowEntry e = SimRowEntry.mkEntry(sd[i],  allStats);
	    if (e!=null ) 	    entries.add(e);
	}
	sort();
    }

    /** Sort the entries in this row by ArticleStat id.
     @return this row (for the convenience of chaining function calls). */
    public SimRow sort() {
	SimRowEntry [] ea =  entries.toArray(new SimRowEntry[0]);
	Arrays.sort(ea, compareByAstid);
	entries.setSize(0);
	for(SimRowEntry e: ea) 	entries.add(e);	
	return this;
    }

      /** Merges the other SimRow's list of entries into this one. Both
	  are assumed to be sorted. In the case of an overlap, values from
	  the other list overwrite those from this list.
	  @return this row (for the convenience of chaining function calls). */
    public SimRow mergeFrom(SimRow other) {
	if (entries==null) entries=new Vector<SimRowEntry>();
	Vector<SimRowEntry> v = new Vector<SimRowEntry>( entries.size() + other.entries.size());
	int i=0, j=0;
	while(i<entries.size() && j<entries.size()) {
	    int d=entries.elementAt(i).getAstid() -
		other.entries.elementAt(j).getAstid();
	    if (d>=0) {
		v.add( other.entries.elementAt(j++));
		if (d==0) i++;	    
	    } else {
		v.add( entries.elementAt(i++));
	    }
	}
	while(i<entries.size()) {
	    v.add( entries.elementAt(i++));
	}
	while(j<other.entries.size()) {
	    v.add( other.entries.elementAt(j++));
	}
	entries = v;
	return this;
    }

    /** Category matcher tool */
    private static class CatInfo {
   
	private String[] bases;

	CatInfo(String cats) {
	    bases=catBases(cats);
	}

	public String toString() {
	    String s="(";
	    for(int i=0; i< bases.length; i++) {
		s += (i==0)? "" : ", ";
		s += bases[i];
	    }
	    return s+")";
	}

	boolean match(String otherCats) {
	    String otherBases[]= catBases(otherCats);
	    for(String a: bases) for(String b: otherBases) {
		    if (a.equals(b)) return true;
		}
	    return false;
	}

	private static String[] catBases(String cats) {
	    if (cats==null) return new String[0];
	    String[] x = cats.split("\\s+");
	    String [] bases = new String[x.length];
	    for(int i=0;i<x.length;i++) {
		bases[i] = catBase(x[i]);
	    }
	    return bases;
	}

	/** converts "cat.subcat" to "cat"
	 */
	private static String catBase(String cat) {
	    if (cat==null) return null;
	    int p = cat.indexOf(".");
	    return (p<0)? cat: cat.substring(0, p);
	}
    }
 

}