package edu.rutgers.axs.sb;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.util.regex.*;
import javax.persistence.*;

//import org.apache.lucene.document.*;
//import org.apache.lucene.index.*;
//import org.apache.lucene.search.IndexSearcher;
//import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.*;
import edu.rutgers.axs.web.*;
import edu.rutgers.axs.indexer.*;

/** Handling statistics for SB experiments */
public class SBStats {

    static private Integer long2int(Long x) {
	return new Integer( x.intValue());
    }

    /** Lists relevant sessions (SB) for a specified  user */
    static Vector<Long[]> listSessionsForUser( EntityManager em, int uid) {
	return listSessionsForUser( em, uid, null);
    }

    static Vector<Long[]> listSessionsForUser( EntityManager em, int uid, String uname) {
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

	//	HashMap<Long,Integer> h= new HashMap<Long,Integer> ();
	Vector<Long[]> v= new 	Vector<Long[]>();
	List list = q.getResultList();
	for(Object o: list) {
	    Object[] z = (Object[]) o;
	    //h.put((Long)(z[0]), long2int((Long)(z[1])));
	    v.add(new Long[] { (Long)(z[0]), (Long)(z[1])});
	}
	return v;
 
    }

    static void sessionStats(EntityManager em, long sqlSessionID) {
	PrintStream out = System.out;
	ActionHistory his = new ActionHistory(em, sqlSessionID);
	String query = "select x from PresentedList x where x.session = :sid and x.type = :type and x.hidden=:h order by x.id";
	Query q = em.createQuery(query);

	q.setParameter("type", edu.rutgers.axs.sql.Action.Source.SB);
	q.setParameter("sid", sqlSessionID);
	q.setParameter("h", Boolean.FALSE);
	Vector<PresentedList> v = new	Vector<PresentedList>();
	List list = q.getResultList();
	for(Object o: list) {
	    v.add((PresentedList)o);
	}
	out.println("Displayed lists for session " +  sqlSessionID);
	for(PresentedList x: v) {
	    out.println("PresentedList " + x.getId() + ", sbMethod=" + x.getSbMethod());
	}
	
	//query = "select x from PresentedList x where x.session = :sid and x.type = :type and x.hidden=:h order by x.id";
	Query q2 = em.createQuery(query);
	q2.setParameter("type", edu.rutgers.axs.sql.Action.Source.SB);
	q2.setParameter("sid", sqlSessionID);
	q2.setParameter("h", Boolean.TRUE);
	list = q2.getResultList();
	v = new	Vector<PresentedList>();
	for(Object o: list) {
	    v.add((PresentedList)o);
	}
	out.println("Hidden lists for session " +  sqlSessionID);
	for(PresentedList x: v) {
	    out.println("PresentedList " + x.getId() + ", sbMethod=" + x.getSbMethod());
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
		out.println("Session " + z[0] +", " + z[1] + " lists presented");
		//	    }

		//	    for(Long[] z : v) {
		long sqlSessionID=z[0];
		sessionStats(em, sqlSessionID);
	    }




	}
    }

}