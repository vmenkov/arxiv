package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.persistence.*;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;

/** Handling statistics for SB experiments, as proposed by Paul Kantor in
    a 2014-06-04 email ("Session Buddy. First round of experiments") and refined
    in a later email discussion.
 */
public class SBStats {

    static private Integer long2int(Long x) {
	return new Integer( x.intValue());
    }

    /** Lists relevant sessions (SB) for a specified  user */
    static Vector<Long[]> listSessionsForUser( EntityManager em, int uid) {
	return listSessionsForUser( em, uid, null);
    }

    static public Vector<Long[]> listSessionsForUser( EntityManager em, int uid, String uname) {
	if (uid != 0 && uname != null) throw new IllegalArgumentException("Exactly one of uid or uname must be specified");

	String qs1 = "select x.session, count(x) from PresentedList x where x.time > '2014-08-22' and ",
	    qz1 = "x.user.id=:uid",
	    qz2 = "x.user.user_name=:uname",
	    qs2 = " and x.type = :type and x.hidden=FALSE group by x.session order by x.session";

	String query = qs1 + (uname==null ? qz1: qz2) + qs2;	   
	Query q = em.createQuery(query);
	if (uname==null) q.setParameter("uid", uid);
	else  q.setParameter("uname", uname);
	q.setParameter("type", edu.rutgers.axs.sql.Action.Source.SB);

	Vector<Long[]> v= new 	Vector<Long[]>();
	List list = q.getResultList();
	for(Object o: list) {
	    Object[] z = (Object[]) o;
	    v.add(new Long[] { (Long)(z[0]), (Long)(z[1])});
	}
	return v;
     }

    /** Report on one web session */
    static public void sessionStats(PrintStream out, EntityManager em, long sqlSessionID, boolean html) {

	RatedActionHistory his = new RatedActionHistory(em, sqlSessionID);
	String query = "select x from PresentedList x where x.session = :sid and x.type = :type and x.hidden=:h order by x.id";
	Query q = em.createQuery(query);

	q.setParameter("type", edu.rutgers.axs.sql.Action.Source.SB);
	q.setParameter("sid", sqlSessionID);
	q.setParameter("h", Boolean.FALSE);
	Vector<PresentedList> v1 = new	Vector<PresentedList>();
	List list = q.getResultList();
	for(Object o: list) {
	    v1.add((PresentedList)o);
	}


	
	//query = "select x from PresentedList x where x.session = :sid and x.type = :type and x.hidden=:h order by x.id";
	Query q2 = em.createQuery(query);
	q2.setParameter("type", edu.rutgers.axs.sql.Action.Source.SB);
	q2.setParameter("sid", sqlSessionID);
	q2.setParameter("h", Boolean.TRUE);
	list = q2.getResultList();
	Vector<PresentedList> v2 = new	Vector<PresentedList>();
	for(Object o: list) {
	    v2.add((PresentedList)o);
	}

	if (html) {
	    out.println("<table border=1>");
	    for(PresentedList x: v1) {
		out.println("<tr>");
		out.println("<td><pre>");
		//out.println("PresentedList " + x.getId() + ", sbMethod=" + x.getSbMethod());
		plStats(out, x, his);
		out.println("</pre></td>");

		// show the two underlying lists in the other two columns 
		long u[] = new long[] { x.getMergePlid1(), x.getMergePlid2()};
		for(long plid : u) {
		    if (plid==0) continue;
		    PresentedList y = null; 
		    for(PresentedList z: v2) {
			if (z.getId() == plid) { y = z; break; }
		    }
		    if (y==null) continue;
		    out.println("<td><pre>");
		    //out.println("PresentedList " + y.getId() + ", sbMethod=" + y.getSbMethod());
		    plStats(out, y, his);
		    out.println("</pre></td>");	    
		}


		out.println("</tr>");
	    }
	    	    
	    out.println("</table>");
	} else {
	    out.println("Displayed lists for session " +  sqlSessionID);
	    for(PresentedList x: v1) {
		out.println("PresentedList " + x.getId() + ", sbMethod=" + x.getSbMethod());
		plStats(out, x, his);
	    }
	    
	    out.println("Hidden lists for session " +  sqlSessionID);
	    for(PresentedList x: v2) {
		out.println("PresentedList " + x.getId() + ", sbMethod=" + x.getSbMethod());
		plStats(out, x, his);
		
	    }
	}
    }

    /** Report on one presented list.

$$Score(L)= \sum {i in L} {v(i)w(i)},

 1/{w(i)}= 1+ \sum_{j < i} { \delta_{v(j),-1} + \delta_{v(j),0}
$$

 */
    static void plStats(PrintStream out, PresentedList pl, RatedActionHistory his) {
	out.println("PresentedList no. " + pl.getId());
	out.println("sbMethod= " + pl.getSbMethod());
	out.println(pl.getTime());
	Vector<PresentedListEntry> docs = pl.getDocs();
	double ow = 1, sum=0;
	for(int i=0; i<docs.size(); i++) {	    
	    String aid = docs.elementAt(i).getAid();
	    Integer o = his.get(aid);  // page score
	    int v = (o==null) ? 0 : o;
	    out.println(aid + "\t" + v);
	    sum += v / ow;
	    if (v < 1) ow += 1;	    
	}
	out.println("Score(L)=" + sum);
    }

    /** Looks up the "score" for pages based on user actions within
	the specified session. */
    static private class RatedActionHistory extends HashMap<String, Integer> {
	RatedActionHistory(EntityManager em, long sqlSessionID) {
	    Vector<Action> va = Action.actionsForSession( em, sqlSessionID);
	    //HashMap<String, Integer> q = new HashMap<String, Integer>();
	    // scan in the reverse order, with the most recent actions first 
	    for(int i=va.size()-1; i>=0; i--) {
		Action a = va.elementAt(i);
		Article art = a.getArticle();
		// Ignore non-article-specific actions such as "NEXT PAGE" etc
		if (art==null) continue;
		String aid = art.getAid();
	    
		// more recent operations override earlier ones
		if (this.get(aid)!=null) continue; 

		Action.Op op=a.getOp();
		boolean negative = op.isNegativeSBStats();	
		boolean positive = op.isPositiveSBStats();
		int score = (negative? -1 : positive? 1 : 0);
		if (score != 0) {
		    this.put(aid, score);
		}
	    }
	}
    }


    public static void main(String[] args) throws Exception {
	EntityManager em = Main.getEM();

	PrintStream out = System.out;

	for(String q: args) {
	    int uid=0;
	    String uname=null;
	    try {
		uid = Integer.parseInt(q);
		out.println("Sessions for user no. "  + uid + " :");
	    } catch (Exception ex) {
		uname = q;
		out.println("Sessions for user '"  + uname + "' :");
	    }

	    Vector<Long[]> v= SBStats.listSessionsForUser( em, uid, uname);
	    for(Long[] z : v) {
		out.println("-----------------------------------------------");
		out.println("Session " +z[0]+", " + z[1] + " lists presented");
		long sqlSessionID=z[0];
		sessionStats(out, em, sqlSessionID, false);
	    }




	}
    }

}