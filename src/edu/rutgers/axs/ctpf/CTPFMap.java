package edu.rutgers.axs.ctpf;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import edu.rutgers.axs.sql.Logging;
import edu.rutgers.axs.indexer.*;

/** Maps Arxiv artice IDs (AIDs) to CTPF internal IDs and vice versa */
public class CTPFMap  {

    /** A Descriptor object describes how the range of "raw internal IDs"
	(numeric IDs found in a *.tsv file) is to be mapped to the range
	of "internal IDs" (used inside our CTPF code).
     */
    static class Descriptor {
	/** Needs to be added to the raw IID (found in the file)
	    to obtain the ID in the map (i.e., value's position in the array).
	    The value may be negative (-1) on the first file, and positive
	    on successive files.
	*/
	final int offset;
	/** Expected first raw IID to be found in the data file. (Any raws with
	    smaller raw IID will be discarded).
	*/
	final int r0;
	/** One plus the last raw  IID to be found in the data file. This
	    may be adjusted downwards if the data file contains few lines
	    than expected.
	*/
	int r1;
	Descriptor(int _offset, int _r0, int _r1) {
	    offset = _offset;
	    r0 = _r0;
	    r1 = _r1;
	}
	public String toString() {
	    return "Descriptor maps ["+r0+":"+r1+") to  ["+
		(r0+offset)+":"+(r1+offset)+")";
	}
    }

    /** All Arxiv artice IDs (AIDs) in the map. The position in the array is
	the CTPF internal ID */
    private Vector<String> aids = new  Vector<String>();
    /** Maps CTPF internal article ID to AID */
    public String  getInternalID_to_aID(int i) {
	//	return internalID_to_aID.get(i);
	return aids.elementAt(i);
    }

    public int aid2iid(String aid) {
	Integer q  =  aID_to_internalID.get(aid);
	if (q==null) throw new IllegalArgumentException("The CTPFMap has no mapping for AID=" + aid);
	return q.intValue();
    }

    /** Maps AID to CTPF internal ID */
    private HashMap<String, Integer> aID_to_internalID  = new HashMap<String, Integer>();

    /** @return The value N such that valid internal IDs range from 0 to N-1
     */
    public int size() { return aids.size(); }

    /** This is the value which should be added to the iid listed in file
	to obtain our iid. The idea is,  iid=1 will be converted to aids.size();
    */
    /*
    int futureOffset() {
	return size() - 1;
    }
    */

    public boolean containsAid(String aid) {
	return aID_to_internalID.containsKey(aid);
    }

 
    /** Temporary structure to store lines of the input file */
    /*
    private static class MapLine { 
	int readIid; String aid;
	MapLine(int _readIid, String _aid) {
	    readIid = _readIid;
	    aid = _aid;
	}
    }
    */

    public CTPFMap() {}
   

    Descriptor addFromFile(File file, boolean expectLinear,boolean validateAids) throws IOException { 
	return  addFromFile( file,  expectLinear, validateAids, false);
    }

    /** Loads the map which associates CTPF internal doc ids with
	Arxiv article IDs (AIDs). We ignore a few "fake" AIDs that may
	appear in the map file but are not actually present in 
	our Lucene data store.

	@param num_docs Expected number of documents (size of map,
	inlcuding the "fake" document with the internal ID=0). The
	internal IDs are supposed to range from 1 thru num_docs. If a
	negative value is supplied, the size will be determined
	dynamically, based on the content of the data file.1

	@param  expectLinear If true, this method will also validate the
	input data: namely, check that IDs start with 1 and increase
	monotonously and with no gaps. This is used when we read in
	a data file produced by our own export routine, and want to
	make sure that the LDA output can be easily mapped to this doc list.
	(Note that the input file, generally does not have to be ordered;
	if it isn't set the flag to false!)

	@param validateAids If true (recommended), AIDs will be checked
	against Lucene.

	@param allowGaps If true, loading will not be aborted even if "gaps" (e.g. due to invalid AIDs) are found. 

	@return A Descriptor structure which describes how the range of
	internal doc IDs found in the input file (the "raw IIDs") map 
	to the internal doc IDs of our CTPF application.	
     */

    Descriptor addFromFile(File file, boolean expectLinear,boolean validateAids, boolean allowGaps) throws IOException { 

	Logging.info("CTPFMap: reading AIDs list from Lucene");
	HashSet<String> allAids = null;
	if (validateAids) {
	    IndexList il = new IndexList();
	    allAids = il.listAsSet(); // all articles in Lucene
	}

	Logging.info("CTPFMap: loading document map from " + file);

	Reader fr = file.getPath().endsWith(".gz") ?
	    new InputStreamReader(new GZIPInputStream(new FileInputStream(file))) :
	    new FileReader(file);

        LineNumberReader br = new LineNumberReader(fr);

	int n = 0;
	//	Vector<String> vAIDs = new Vector<String>(n);

        String line; 
	final int M = 10;
	int prevReadIid = 0;
	int r0 = 0;
	int lineCnt=0;

	Vector<String> v  = new	Vector<String>();

        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");
	    int readIid = Integer.parseInt(parts[0]);
	    //	    int iid = readIid + offset;
	    String aid = parts[1];

	    if (expectLinear) {
		if (readIid  == 0) {
		    String msg = "CPPFFit.loadMap("+file+", line="+br.getLineNumber()+"): found unexpected useless map entry with read iid="+readIid+", aid=" + aid;
		    Logging.error(msg);
		    throw new IllegalArgumentException(msg);
		} else if (readIid != prevReadIid+1) {
		    String msg = "CPPFFit.loadMap("+file+", line="+br.getLineNumber()+"): read entry with riid=" + readIid + ", aid=" + aid  + " following riid="+prevReadIid;
		    Logging.error(msg);
		    throw new IllegalArgumentException(msg);
		}
	    }
	    if (readIid < v.size() && v.elementAt(readIid)!=null) {
		String msg = "CPPFFit.loadMap("+file+", line="+br.getLineNumber()+"): found entry with duplicate readIid=" + readIid + "), aid=" + aid;	
		Logging.error(msg);
		throw new IllegalArgumentException(msg);
	    } else if (readIid >= v.size()) {
		v.setSize(readIid+1);
	    }
	    v.set(readIid, aid);
	    if (lineCnt==0 || readIid<  r0) {   r0 = readIid;}
	    prevReadIid=readIid;	    
	    lineCnt++;
	}

	
	Descriptor d = new Descriptor(aids.size() - r0, r0, v.size());
	Logging.info("Range descriptor: " + d);

	int invalidAidCnt = 0;
	String invalidAidTxt = "";
	//String tmpMsg = "";
	for(int r=r0; r<d.r1; r++) {
	    String aid = v.elementAt(r);
	    int iid = r + d.offset;
	    if (validateAids && !allAids.contains(aid)) {
		invalidAidCnt++;
		if (invalidAidCnt<M) invalidAidTxt += " " + aid;
		else if (invalidAidCnt==M) invalidAidTxt += " ...";
		continue;
	    } 

	    if (iid >= aids.size()) aids.setSize(iid+1);
	    aids.set(iid, aid);
	    //tmpMsg += "("+iid+":"+aid+") ";
            aID_to_internalID.put(aid, new Integer(iid));
        }

	//Logging.info("Loaded mappings: " + tmpMsg);

	if (invalidAidCnt>0) {
	    Logging.warning("CTPFFit.loadMap("+file+"): " + invalidAidCnt + " lines have been ignored, because they contained AIDs not existing in our data store, such as:  " + invalidAidTxt);
	}

	if (!gapCheck() && !allowGaps) {
	    String msg = "Gaps found in IID list";
	    Logging.error(msg);
	    throw new IllegalArgumentException(msg);	   	    
	}
	return d;
    }

    /** @return true if there are no gaps */
    private boolean gapCheck() {

	int num_docs = aids.size(); 
	String iList = "";

	// basic validation
	int gapCnt=0;
	for(int i=0; i<num_docs; i++) {
	    if (aids.elementAt(i)==null) {
		iList += " " + i;
		gapCnt++;
	    }
	}

	//        Logging.info("CTPFFit: size of internalID_to_aID: " + internalID_to_aID.size());
        Logging.info("CTPFFit.loadMap(): size of aID_to_internalID: " + aID_to_internalID.size());

	String msg = (gapCnt==0)? " AID values have been loaded for all internal IDs" :
	    " AID values are missing for " + gapCnt + " internal IDs! " + iList;
	Logging.info("CTPFFit.loadMap: for 0<=internalID<" +num_docs +", " +msg);
	return gapCnt==0;
    }

    /** Removes some of the last elements from the array of AIDs, to match
	the size in the descriptor. This may be done if the map read from
	the map file turned out to be bigger than the epsilon etc files.
	(This happened in Laurent's early runs, the items file having
	the ID range [1:50000], and epsilon etc files having [0:49999])
     */
    void possibleShrink(Descriptor desc) {
	int properSize = desc.offset + desc.r1;
	if (aids.size()<properSize) {
	    throw new IllegalArgumentException("possibleShrink(" + desc +"), impossible curent size=" + aids.size());
	} else if (aids.size()==properSize) {
	    return;
	}
	for(int j=properSize; j<aids.size(); j++) {
	    aID_to_internalID.remove( aids.elementAt(j));	    
	    String msg= "removed AID=" +aids.elementAt(j)+ ", iid=" +j+", from the map";
	    Logging.info(msg);

	}
	aids.setSize(properSize);
    }

} 
