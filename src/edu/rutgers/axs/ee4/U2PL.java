package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.document.*;

import edu.rutgers.axs.indexer.*;


/** An auxiliary class used when reading in and preprocessing the
    coaccess matrix (the matrix of {user,page} pairs) in
    HistoryClustering. For each user id, we store a vector of pages
    he's accessed.

    <p>
    Since the matrices are very big, memory use is of a major concern in 
    this class, and it is optimized accordingly.
*/
class U2PL  {

    /** This is meant to be, on average, a more memory-efficient way
	to store a list of integers than HashSet<Integer>. The data 
	are stored as sorted arrays of int.
     */
    static private class IntStore {

	private int n=0;
	private int store[] = new int[8];
	private boolean dirty = false;
	
	synchronized void add(Integer q) {
	    if (n==store.length) {
		// sort and resize;
		clean(store.length*2);
	    }
	    dirty=true;	
	    store[n++] = q.intValue();
	}

	/** Resizes the array to specified new length, sorts values,
	    and removes duplicates
	    @param newLen The desired array length. If 0, it means use 
	    just as much memory as needed to fit what's there
	 */
	synchronized private void clean(int newLen) {
	    Arrays.sort(store,0,n); 
	    if (newLen<=0) {
		newLen=0;
		for(int i=0; i<n; i++) {
		    if (i==0 || store[i]>store[i-1]) newLen++;
		}
	    }

	    int[] w = new int[newLen];
	    int n0 = n;
	    n=0;
	    for(int i=0; i<n0; i++) {
		int x=store[i];
		if (n==0 || x>w[n-1]) w[n++] = x;
	    }
	    store = w;	    
	    dirty=false;
	}

	synchronized void clean() {
	    if (dirty || n!=store.length) clean(n);	    
	}
	
	/** Gets the data out, as an int[] */
	synchronized int[] getStore() {
	    clean();
	    return store;
	}
    }

    /** For each user, we store a list of pages he's accessed. */
    private HashMap<String, IntStore> map = new HashMap<String, IntStore>();
    
    /** This map is used during the table-building process only: add(), registerPage() */
    private Vector<String> origno2aid = new Vector<String>();
    private HashMap<String, Integer> aid2origno=new HashMap<String, Integer>();

    /** The final numeric map for article IDs.	 */
    String[] no2aid;
    HashMap<String, Integer> aid2no;
    
    /** The numeric map for IP hash values (which are a surrogate
	for user identifiers). 	 */
    String[] no2u;
    HashMap<String, Integer> u2no;
    
    private final IndexReader reader = Common.newReader2();
    private final IndexSearcher searcher = new IndexSearcher( reader );

    final private static FieldSelector fieldSelectorDate = 
	new OneFieldSelector(ArxivFields.DATE);

    /** Only articles with submission dates in this range (date1 to date2) are accepted */
    private  Date date1, date2;
    /** 0-based month, 1-based day */
    /*
	date1 = new GregorianCalendar(2010, 0, 1).getTime(),
	date2 = new GregorianCalendar(2012, 0, 1).getTime();
    */

    void setArticleDateRange(Date articleDateFrom,  Date articleDateTo) {
	date1 = articleDateFrom;
	date2 = articleDateTo;
	System.out.println("Accepting articles dated "+date1+ " to " +date2);
	//	System.out.println("msec: "+	date1.getTime() +" : " +
	//		   date2.getTime());
	if (date1==null && date2 != null ||
	    date1!=null && date2 == null) {
	    throw new IllegalArgumentException("Only one of the articleDateFrom  and articleDateTo dates have been provided. You must provide either both, or neither");
	}
    }


    int ignoreCnt = 0;

    final Pattern pat = Pattern.compile("([0-9][0-9])([0-9][0-9])\\.[0-9][0-9][0-9][0-9]");

    /** Checks if the page is acceptable for our analysis.

	<p>
	"Let Items be the set of papers meeting the following criteria (as per
	 PF's specs):
        # primary category is in the super-category
        # submitted in the given time-period (2010-2011)"
     */
    private boolean acceptable(String aid) throws IOException {
	if (date1==null && date2==null) return true;
	if (date1==null || date2==null) throw new IllegalArgumentException();

	// some heuristics first (determining approximate date based
	// on doc name)
	Matcher mat = pat.matcher(aid);
	if (mat.matches()) {
	    String yy = mat.group(1);
	    String mm = mat.group(2);
	    yy = yy.startsWith("9") ? "19" + yy : "20" + yy;

	    int y = Integer.parseInt(yy);
	    int m = Integer.parseInt(mm);
	    //Month value is 0-based. e.g., 0 for January.
	    GregorianCalendar cal = new GregorianCalendar(y,m-1,1);
	    Date d1 = cal.getTime();
	    cal.add( Calendar.MONTH, 1);
	    Date d2 = cal.getTime();
	    // We know that the article time is somewhere between d1 and d2...
	    // The entire intervale (d1,d2) is within the permitted range
	    if (!d1.before(date1) && !d2.after(date2)) return true;
	    // The entire intervale (d1,d2) is outside the permitted range
	    if (!d2.after(date1) || !d1.before(date2)) {
		//		System.out.println("Date reject: "+aid+" --> ("+d1+" : "+d2+")");
		return false;
	    }
	}

	// Validate the article ID
	int docno = Common.findOrMinus(searcher, aid);
	if (docno<0) return false;
	Document doc = reader.document(docno, fieldSelectorDate);
	if (doc==null) return false;
	String dateString = doc.get(ArxivFields.DATE);
	if (dateString==null) return false;
	try {
	    Date date= DateTools.stringToDate(dateString);
	    boolean r=(date!=null && !date.before(date1) && date.before(date2));
	    if (!r) {
		//		System.out.println("Date reject: " + date);
	    }
	    return r;
	} catch (java.text.ParseException ex) {
	    //	    System.out.println("Date reject: parse exception on " + dateString);
	    return false;
	}
    }

    /** List of articles known to be inaccepatable (for date reasons etc)
     */
    private HashSet<String> inacceptableArticles = new HashSet<String>();

    /** Checks is the article is acceptable based on our criteria,
	and if yes, puts it into the table (unless it's there already)
	@return An Integer object, or null
     */
    private Integer registerPage(String aid)  throws IOException {
	Integer q = aid2origno.get(aid);
	if (q==null) {
	    if (inacceptableArticles.contains(aid)) {
		ignoreCnt++;
		return null;
	    } else if (!acceptable(aid)) {
		inacceptableArticles.add(aid);
		ignoreCnt++;
		return null;
	    }
	    q = new Integer(origno2aid.size());
	    origno2aid.add(aid);
	    aid2origno.put(aid,q);
	}
	return q;
    }

    void add(String u, String aid) throws  IOException{
	IntStore v = map.get(u);
	if (v==null) map.put(u, v = new IntStore());
	Integer q = registerPage(aid);
	if (q!=null) v.add(q);
    }


    /** Removes "low activity" users and papers; converts the rest
	into a SparseDoubleMatrix2Dx object, for use with SVD.
    */
    SparseDoubleMatrix2Dx toMatrix(int user_thresh, int paper_thresh) {
	//	final int user_thresh=2,  paper_thresh=2;
	
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


