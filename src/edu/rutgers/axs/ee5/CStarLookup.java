package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;

import edu.rutgers.axs.sql.Logging;

/** Looking up C-Star values in the random access binary files
    prepared by RandomAccessConverter. The content of the files have
    one-to-one correspondence to that in *.out files prepared
    by XTZ; details are EE5_documentation.pdf.
 */
class CStarLookup {

    /** Presently we use this one single value of gamma for all clusters */
    static final double onlyGamma = 0.99; 
    /** The only values supported now */
    static final double onlyAlpha0 = 1, onlyBeta0 = 1;

    /** An auxiliary structure for linear interpolation */
    private static class Interpolation {
	final int j0;
	final double weights[];
	private Interpolation( int _j0, double w0) {
	    j0 = _j0;
	    weights = (w0==1.0) ? new double[]{w0} : new double[]{w0, 1-w0};
	}
	/* What are the neighbors of xi in array allXi[]? Find them
	   using linear search.
	   @param allXi Arrays of asecending values?
	 */
	static Interpolation interpolate(double xi, double[] allXi) {
	    if (xi <= allXi[0]) {
		return new Interpolation(0, 1.0);
	    }
	    for(int j=1; j<allXi.length; j++) {
		if (xi < allXi[j]) {
		    double q0 = (allXi[j]-xi)/(allXi[j]-allXi[j-1]);
		    return new Interpolation(j-1, q0);
		} else if (xi == allXi[j]) {
		    return new Interpolation(j, 1.0);
		}
	    }
	    return new Interpolation(allXi.length-1, 1.0);
	}
    }

    /** Returns number x in the range (0;1] such as x==a, modulo 1
     */
    private static double getA0(double a) {
	a -= Math.floor(a);
	return (a==0) ? 1 : a;
    }


    /** All values for a particular gamma */
    class CstarGamma {
	double gamma;	
	double allXi[];
	CstarXi cstarXi[];
	CstarGamma(double _gamma) {
	    gamma = _gamma;
	}
	
	/** Finds the closest xi using linear search, and returns the
	    corresponding CstarXi object */
	CstarXi getCStarXi(double xi) {
	    Interpolation q = Interpolation.interpolate(xi, allXi);
	    return cstarXi[q.j0];
	}

	/** Table lookup + linear interpolation over xi */
	double lookup(double xi, double alpha, double beta, int u) throws IOException {
	    Interpolation q = Interpolation.interpolate(xi, allXi);
	    double alpha0 = getA0(alpha);
	    double beta0 = getA0(beta);
	    int da = (int)Math.rint( alpha - alpha0);
	    int db = (int)Math.rint( beta - beta0);
	    double sum = 0;
	    
	    String msg ="xi=" +(float)xi+"; da=" + da + ", db="+ db + ", u=" + u +"; [";


	    for(int k=0; k<q.weights.length; k++) {
		CstarAB cstarAB = cstarXi[q.j0 + k].getCstarAB( alpha0,  beta0);
		double c = cstarAB.getC(da, db, u);
		sum += c * q.weights[k];
		//msg += "(xi"+cstarXi[q.j0 + k].xi  +": "+(float)c+"*"+(float)q.weights[k]+")";
	    }
	    msg += "] => " + sum;
	    //System.out.println(msg);
	    return sum;
	}
   

	/** Temporary structure, only used during filling the matrix */
	private HashMap<Double, CstarXi> tmpCstarXi = new HashMap<Double, CstarXi>();
	void add(File f, double xi, double alpha, double beta) throws IOException {
	    Double key = new Double(xi);
	    CstarXi c = tmpCstarXi.get(key);
	    if (c==null) tmpCstarXi.put(key, c=new CstarXi(f, xi, alpha, beta));
	    c.add(f, alpha, beta);
	}
	/** Call this after all calls to add() */
	void makeReady() {
	    allXi = new double[tmpCstarXi.size()];
	    int i=0;
	    for(Double xi: tmpCstarXi.keySet()) {
		allXi[i++] = xi.doubleValue();
	    }
	    Arrays.sort(allXi);
	    cstarXi = new CstarXi[allXi.length];
	    String msg = "";
	    for(i=0; i<allXi.length; i++) {
		msg += " " + allXi[i];
		Double key = new Double(allXi[i]);
		cstarXi[i] = tmpCstarXi.get(key);
	    }
	    tmpCstarXi=null; 
	    Logging.info("CStar(gamma="+gamma+") has tables for " + allXi.length + " distinct values of xi: " + msg);
	}
    }

    /** All values for a particular xi */
    class CstarXi {
	double xi;	
	CstarAB  getCstarAB(double alpha0, double beta0) {
	    if (alpha0 == 1 && beta0 == 1) return cstarAB;
	    else throw new IllegalArgumentException("CstarXi: only alpha0=beta0=1 are supported");
	    //	    int i, j;
	    //	    if (alpha0==0) i==0;
	    //	    else if (alpha0==0) i==0; 
	}
	CstarAB  cstarAB;

	CstarXi(File f, double _xi, double alpha, double beta)  throws IOException{
	    xi = _xi;
	    add(f, alpha, beta);
	}
	void add(File f,  double alpha0, double beta0)  throws IOException{
	    if (alpha0!=onlyAlpha0 || beta0 !=onlyBeta0) return;
	    cstarAB = new CstarAB(f, alpha0, beta0);
	}
    }

    /** Stores the data from one file, i.e. all data for a given 
	tuple (gamma, xi, alpha0, beta0) */
    class CstarAB {
	File f;
	private RandomAccessFile raf;
	final double alpha0, beta0;
	CstarAB(File f,  double _alpha0, double _beta0) throws IOException {
	    this.f = f;
	    alpha0 = _alpha0;
	    beta0 = _beta0;
	    raf = new RandomAccessFile(f, "r");
  	}
	private int recentDa = -1, recentDb = -1;
	private float[] recentReads = new float[R+1];
	/** Retrives the appropriate element of the stored C-star
	    table. As per XTZ specs, if da+db &gt; N, the most
	    appropriate value with da+db=N is returned; if u &gt; R,
	    the last (R-th) value is returned.
	 */
	double getC(int da, int db, int u) throws IOException {
	    if (da < 0 || db < 0) throw new IllegalArgumentException("CstarAB.getC: dA, dB must be non-negative!");
	    if (da + db > N) {
		double ra = (double)da/(double)(da + db);
		da = (int) Math.round( ra * N);
		db = N - da;
	    }

	    if (recentDa != da || recentDb != db) {
		int index = elementIndex(da,db,0);
		//System.out.println("da="+da+", db="+db+", index=" + index);
		raf.seek(RandomAccessConverter.SIZEOF * index);
		String msg="";
		for(int i=0; i<=R; i++) {
		    recentReads[i] = raf.readFloat();
		    msg += " " + recentReads[i];
		}
		//System.out.println("da="+da+", db="+db+", index=" + index+", vals=("+msg+")");
		recentDa = da;
		recentDb = db;
	    }
	    if (u>R) u=R; 
	    return recentReads[u];
	}
	/** Closes the underlying file */
	void close() throws IOException{ 
	    raf.close(); raf=null; 
	}
   }

    /** Each files conatain (N+1)*(N+2)/2 rows, with the values for
	all pairs (alpha=alpha0+i, beta=beta0+j) with 
	0 &le; i &le; N, 0 &le; j &le; N, i+j &le; N.       
     */
    final int N;

    /** Each row of each data file contains R+1 values, for 0 &le; u &le; R
     */
    final int R;

    /** The row number (0-based) in the data file */
    private int rowIndex(int deltaAlpha, int deltaBeta) {
	return (deltaAlpha*(2*N + 3 - deltaAlpha))/2 + deltaBeta;
    }


    private int elementIndex(int deltaAlpha, int deltaBeta, int u) {
	return rowIndex(deltaAlpha,  deltaBeta) * (R+1) + u;
    }

    private CstarGamma cstar = new  CstarGamma(onlyGamma);

    double lookup(double gamma, double xi, double alpha, double beta, int u) throws IOException {
	if (gamma != onlyGamma) throw new IllegalArgumentException("Only support gamma=" + onlyGamma +", not="+gamma);

	double c = cstar.lookup( xi, alpha, beta, u);
	/*
	double alpha0 = getA0(alpha);
	double beta0 = getA0(beta);
	int da = (int)Math.rint( alpha - alpha0);
	int db = (int)Math.rint( beta - beta0);
	CstarXi cstarXi = cstar.getCStarXi(xi);
	CstarAB cstarAB = cstarXi.getCstarAB( alpha0,  beta0);
	double c = cstarAB.getC(da, db, u);
	*/
	return c;
    }


    /** Prepares the list of available files, etc

	<p>File names look like this: 
	gamma0_0.800_xi0_0.10_alpha0_1.00_beta0_1.00.bin
    */
    CStarLookup(int _N, int _R)  throws  IOException {
	N = _N;
	R = _R;
	File[] files = Files.listCstarBinaryFiles();
	int fileCnt = 0;

	for(File f: files) {
	    String fname = f.getName();
	    fname = fname.substring(0,fname.length()-Files.SUFFIX_BIN.length());
	    String[] q = fname.split("_");
	    if (q.length!=8) throw new IOException("Cannot parse file name: " + fname);
	    String names[] = {"gamma0", "xi0", "alpha0", "beta0"};
	    double values[] = new double[names.length];
	    int k =0;
	    for(int i=0; i<names.length; i++) {
		if (!q[k++].equals(names[i])) throw new IOException("Cannot parse file name: " + fname +" ; did not find " +names[i]);
		
		values[i] = Double.parseDouble(q[k++]);		
	    }
	    if (values[0] !=  onlyGamma) {
		Logging.info("Ignoring file " + f + " with gamma=" + values[0]);
		continue;
	    } 
	    Logging.info("Using file " + f);
	    double xi = values[1], alpha0 = values[2], beta0 = values[3];
	    cstar.add( f, xi, alpha0, beta0);
	    fileCnt++;
	}
	Logging.info("Opened " + fileCnt + " cstar files");
	cstar.makeReady();
	Logging.info("CStar ready for lookup");
    }
    

}

    