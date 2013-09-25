package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** An auxiliary class used when reading in and preprocessing the
    (user,page) matrix in HistoryClustering. For each user id, we
    store a vector of pages he's accessed. 
*/
class U2PL  {

    /** This is meant to be, on average, more efficient than HashSet<Integer>
     */
    static private class IntStore {
	final Vector<Integer> integers;
	IntStore(Vector<Integer> _integers) {
	    integers = _integers;
	}
	private HashSet<Integer> h = null;
	final int N=32;
	private int n=0;
	int store[] = new int[N];

	HashSet<Integer> convertToHash() {
	    if (h==null) {
		h = new HashSet<Integer>();
		for(int x: store) {
		    h.add(integers.elementAt(x));
		}
		store=null; // save space
	    }
	    return h;
	}

	int[] convertToArray() {
	    if (h==null) convertToHash();
	    store = new int[h.size()];
	    int i=0;	    
	    for(Integer x: h) {
		store[i++] = x.intValue();		
	    }
	    Arrays.sort(store); 
	    h = null; // save space
	    return store;
	}

	int[] getArray() { return store; }

	void add(Integer q) {
	    if (h==null) {
		if (n<store.length) {
		    store[n++] = q.intValue();
		} else {
		    convertToHash();
		    h.add(q);
		}
	    } else {
		h.add(q);
	    }
	}
    }

    private HashMap<String, IntStore> map = new HashMap<String, IntStore>();
    
    /** This map is used during the table-building process only */
    private Vector<String> origno2aid = new Vector<String>();
    private HashMap<String, Integer> aid2origno=new HashMap<String, Integer>();
    /** Just a list of unique integer objects */
    private Vector<Integer> integers = new  Vector<Integer>();

    /** The final numeric map for article IDs.	 */
    String[] no2aid;
    HashMap<String, Integer> aid2no;
    
    /** The numeric map for IP hash values (which are a surrogate
	for user identifiers). 	 */
    String[] no2u;
    HashMap<String, Integer> u2no;
    
    private Integer registerPage(String p) {
	Integer q = aid2origno.get(p);
	if (q==null) {
	    q = new Integer(origno2aid.size());
	    integers.add(q);
	    origno2aid.add(p);
	    aid2origno.put(p,q);
	}
	return q;
    }

    void add(String u, String p) {
	IntStore v = map.get(u);
	if (v==null) map.put(u, v = new IntStore(integers));
	v.add(registerPage(p));
    }


    /** Removes "low activity" users and papers; converts the rest
	into a SparseDoubleMatrix2Dx object, for use with SVD.
    */
    SparseDoubleMatrix2Dx toMatrix() {
	final int user_thresh=2,  paper_thresh=2;
	
	long origCnt = 0;
	final int origMapSize = map.size();

	for(Iterator<Map.Entry<String,IntStore>> it = 
		map.entrySet().iterator();   it.hasNext(); ) {	    
	    IntStore z = it.next().getValue();
	    int c = z.convertToHash().size();
	    origCnt += c;
	    if (c< user_thresh) {
		it.remove();
	    } else {
		z.convertToArray();
	    }
	}

	System.out.println("Processing the view matrix. Out of "+origMapSize +" users, with "+origCnt+" page views, only " + map.size() + " users have at least " + user_thresh + " page views");
	
	// view count for each page
	int[] viewCnt = new int[origno2aid.size()];
	
	for(IntStore v: map.values()) {
	    for(int origno: v.getArray()) {
		viewCnt[origno] ++;
	    }
	}
	
	int viewedPagesCnt=0, popularPagesCnt=0;
	int cap = 0;
	for(int vc:  viewCnt) {
	    if (vc>0)  viewedPagesCnt++;
	    if (vc>=paper_thresh)  {
		popularPagesCnt++;
		cap += vc;
	    }
	}
	
	System.out.println("There are " + viewCnt.length + " papers; among them, " + viewedPagesCnt + " have been viewed by at least 1 'relevant' user, and " +  popularPagesCnt  + " have been viewed by at least " + paper_thresh + " relevant users");

	System.out.println("The view matrix will have " +cap+ " nonzeros");

	// create the permanent page map (only including "popular" pages)
	no2aid = new String[  popularPagesCnt ];
	aid2no = new HashMap<String, Integer>();
	int k = 0;
	for(int i=0; i<viewCnt.length; i++) {		
	    if (viewCnt[i]<paper_thresh)  continue;
	    String aid = origno2aid.elementAt(i);
	    aid2no.put(aid, new Integer(k));
	    no2aid[k++] = aid;
	}
	
	no2u = new String[ map.size() ];
	u2no = new HashMap<String, Integer>();
	k = 0;
	for(String u: map.keySet()) {
	    u2no.put(u, new Integer(k));
	    no2u[k++] = u;
	}
	
	SparseDoubleMatrix2Dx mat2 = new SparseDoubleMatrix2Dx(map.size(), no2aid.length);
	for(String u: map.keySet()) {
	    int row = u2no.get(u).intValue();
	    int cnt = 0;
	    for(int origno: map.get(u).getArray()) {
		if (viewCnt[origno] >= paper_thresh) cnt++;
	    }
	    int pos[] = new int[cnt];
	    
	    k=0;
	    for(int origno: map.get(u).getArray()) {
		if (viewCnt[origno] < paper_thresh)  continue;
		String aid = origno2aid.elementAt(origno);
		int col = aid2no.get(aid).intValue();
		//mat2.setQuick(row, col, 1.0);
		pos[k++] = col;
	    }
	    mat2.setRowSameValue(row, pos, 1.0);
	}
	System.out.println("Have a " + mat2.rows() + " by " + mat2.columns() + " matrix with " + mat2.cardinality()  + " non-zeros");
	return mat2;
    }
}


