/** A temporary object, stores a semi-computed term vector 
    during TJ Algorithm 1.
*/
class TjA1DocEntry {

    static class Coef { //implements Comparable<Coef>  {
	int i;
	double value;
	public Coef(int _i, double v) { i =i; value=v;}
	/** Copy constructor */
	//public Coef(Coef c) { i =c.i; value=c.value;}
	//public void setValue(Coef c) {
	//    value = c.value;
	//}

	/** Compares column position */
	//public int compareTo(Coef x) {
	//    return icla - x.icla;
	//}
    }


    ArticleEntry ae;
    /** ti[j]=k */
    //int ti[];
    /** tw[j] = w[k]^2 * idf(k)^2 * d[k] */
    //double tw[];
    Coef[] qplus, qminus;

    /** (w1,d) */
    double sum1;
    double maxSum2contrib;
    double lastGamma;

    TjA1DocEntry(int docno, ArticleEntry _ae,  ArticleStats as, UserProfile upro) {
	ae = _ae;
	double w1sum = 0;
	double w2sum = 0;

	double[] w2plus =  new double[terms.length],
	    w2minus =  new double[terms.length];	
	for(int j=0; j<upro.dfc.fields.length;  j++) {	
	    TermFreqVector tfv=reader.getTermFreqVector(docno, upro.dfc.fields[j]);
	    if (tfv==null) continue;
	    double boost =  as.getBoost(j);

	    //System.out.println("--Terms--");
	    int[] freqs=tfv.getTermFrequencies();
	    String[] terms=tfv.getTerms();	    

	    for(int i=0; i<terms.length; i++) {
		UserProfile.TwoVal q=hq.get(terms[i]);
		if (q==null) continue;

		double z = freqs[i];
		double idf = idf(terms[i]);	       

		w1sum += z * boost * q.w1 *idf;

		double r = idf*q.w2;
		double w2q = z * boost * r*r;
		if (q.w2 >= 0) {
		    w2plus[i] += w2q;
		} else {
		    w2minus[i] += w2q;
		}
	    }
	}
	
	Vector<Coef> vplus = new Vector<Coef> (terms.length),
	    vminus = new Vector<Coef> (terms.length);
	for(int i=0; i<terms.length; i++) {
	    if (w2plus[i]!=0) {
		vplus.add(new Coef(i, w2plus[i]));
	    } else if (w2minus[i]!=0) {
		vminus.add(new Coef(i, w2plus[i]));
	    } 
	}
	   



    }
}


