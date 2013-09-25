package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** An auxiliary class used when reading in and preprocessing the
    (user,page) matrix in HistoryClustering. For each user id, we
    store a vector of pages he's accessed. 
*/
class U2PL  {

    /** This is meant to be, on average, more memory-efficient than HashSet<Integer>
     */
    static private class IntStore {

	private int n=0;
	private int store[] = new int[32];
	private boolean dirty = false;
	
	int[] getArray() { return store; }

	synchronized void add(Integer q) {
	    if (n==store.length) {
		// sort and resize;
		clean(store.length*2);
	    }
	    dirty=true;	
	    store[n++] = q.intValue();
	}

	/** Resizes the array to specified new  length, sorts values,
	    and removes duplicates
	 */
	synchronized private void clean(int newLen) {
	    Arrays.sort(store,0,n); 
	    int[] w = new int[newLen];
	    int n0 = n;
	    n=0;
	    for(int i=0; i<n0; i++) {
		int x=store[i];
		if (n==0 || x>w[n]) w[n++] = x;
	    }
	    store = w;	    
	    dirty=false;
	}

	synchronized void clean() {
	    if (dirty || n!=store.length) clean(n);	    
	}
	
	synchronized int[] getStore() {
	    clean();
	    return store;
	}
    }

    private HashMap<String, IntStore> map = new HashMap<String, IntStore>();
    
    /** This map is used during the table-building process only */
    private Vector<String> origno2aid = new Vector<String>();
    private HashMap<String, Integer> aid2origno=new HashMap<String, Integer>();

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
	    origno2aid.add(p);
	    aid2origno.put(p,q);
	}
	return q;
    }

    void add(String u, String p) {
	IntStore v = map.get(u);
	if (v==null) map.put(u, v = new IntStore());
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
	    z.clean();
	    int c = z.getStore().length;
	    origCnt += c;
	    if (c< user_thresh) {
		it.remove();
	    }
	}

	System.out.println("Processing the view matrix. Out of "+origMapSize +" users, with "+origCnt+" page views, only " + map.size() + " users have at least " + user_thresh + " page views");
	
	// view count for each page
	int[] viewCnt = new int[origno2aid.size()];
	
	for(IntStore v: map.values()) {
	    for(int origno: v.getStore()) {
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
	    for(int origno: map.get(u).getStore()) {
		if (viewCnt[origno] >= paper_thresh) cnt++;
	    }
	    int pos[] = new int[cnt];
	    
	    k=0;
	    for(int origno: map.get(u).getStore()) {
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


