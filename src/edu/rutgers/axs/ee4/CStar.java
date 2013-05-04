package edu.rutgers.axs.ee4;

import java.util.*;
import java.text.*;
import java.io.*;

public class CStar {
    
    private double[][] table;
    
    private CStar(int N, int G, double gamma) {
	table =  cStar_m2(N,G,gamma);
    }

    /** Simple table lookup, with an asymptotic formula used if outside the
	table range.
     */
    double getValue(int alpha, int beta) {
	if (alpha <=0 || beta<=0) throw new IllegalArgumentException("alpha, beta must be positive");
	if (alpha + beta <= table.length + 1) {
	    return table[beta - 1][alpha - 1];
	} else {
	    return (double)alpha/(double)(alpha+beta);
	}
    }

    /** Table lookup with interpolation used when needed.
     */
    double getValueGeneral(double alpha, double beta) {
	if (alpha < 1 || beta < 1) throw new IllegalArgumentException("alpha, beta must be at least 1.0");
	int a = (int)alpha, b = (int)beta;
	double xa = alpha - a,  xb = beta - b;
	return  (xa==0 && xb==0) ? getValue(a,b) :
	    (1-xa)*( (1-xb) * getValue(a,b) + xb * getValue(a,b+1)) +
	    xa * ( (1-xb) * getValue(a+1,b) + xb * getValue(a+1,b+1));
    }


    static double[][] cStar_m2(int N, int G, double gamma) {

	//%set initial alpha and beta values to be 1. 
	double alpha0=1; 
	double beta0=1;
	// %1000 for time horizon (i.e. number of 1000 samples collected)
        //%it should be large enough to approximate the dynamic programming
	//int N=1000;

	// %start with the matrix of forwarding all at c=0;
        // c_star=tril(ones(N+1,N+1)); 

	double[][] c_star = Mu.matrix(N+1,N+1);
	for(int i=0; i< c_star.length; i++) {
	    for(int j=0; j< c_star[i].length; j++) {
		c_star[i][j]  = (j<i ? 1 : 0);
	    }	   
	}


	//for k=1:gridSize
	for(int k=1; k<=G; k++) {
	    //%set c and resolve the DP and found the forwarding
	    //%decision at each state (1 for forwarding and 0 for not
	    //%forwarding)
	    //  c=k/gridSize;
	    final double c= k/(double)G;
	    //V=zeros(N+1,N+1); %value function at each state
	    double[][] V = Mu.matrix(N+1,N+1);
	    //M=zeros(N+1,N+1); %mean mu_n at each state

	    //idx=ones(N+1,N+1); %optimal choice (1 to forward and 0 for not forward) at each state

	    //for i=N:-1:0     % the total number of samples taken is n=N
	    for(int i=N; i>=0; i--) {
		//M(i+1,i+1)=(alpha0+i)/(alpha0+beta0+N); %mean at termination state
		double m =(alpha0+i)/(alpha0+beta0+N); 
		//EfReward(i+1,i+1)=0;
		//% This value is a lower bound, which comes from the
		//% better of two policies: don't forward anything
		//% (value of 0) forward now, and forever into the
		//% future (value of M-c, summed over the geometric
		//% series of gamma^n)
		// [V(i+1,i+1),idx(i+1,i+1)]=max([0,M(i+1,i+1)-c]/(1-gamma));
		double q = m-c;
		if (q>0) {
		    V[i][i] = q/(1-gamma);
		    c_star[i][i] += 1;
		}
	    }
	    //    for n=N-1:-1:0   % n is the total number of samples taken
	    for(int n=N-1; n>=0; n--) {
		//for i=0:1:n  % i is the number of sucesses, so n-i is the number of failures
		for(int i=0;i<=n; i++) {
		    //M(i+N-n+1,i+1)=(alpha0+i)/(alpha0+beta0+n); %mean calculated at each state
		    double m = (alpha0+i)/(alpha0+beta0+n);
		    //EfReward(i+N-n+1,i+1)=gamma*(
		    //(1-M(i+N-n+1,i+1))*V(i+N-n,i+1)+M(i+N-n+1,i+1)*V(i+N-n+1,i+2)
		    //);
     
		    double efReward=gamma*((1-m)*V[i+N-n-1][i]+m*V[i+N-n][i+1]);
		    //[V(i+N-n+1,i+1),idx(i+N-n+1,i+1)]=
		    //      max([0,M(i+N-n+1,i+1)-c+EfReward(i+N-n+1,i+1)]);
		    double q = m - c + efReward;
		    if (q>0) {
			V[i+N-n][i] = q;
			c_star[i+N-n][i] += 1;
		    }
		}
	    }

	    //    idx=idx-ones(N+1,N+1); %idx tells us the optimal decision at each state, 1 for forwarding and 0 for not
    
	    //c_star=c_star+idx; %accumulate optimal decisions at every state to figure out c*
	}

	// c_star=c_star/gridSize; %compute c* by normalizing it
	for(int i=0; i< c_star.length; i++) {
	    for(int j=0; j< c_star[i].length; j++) {
		c_star[i][j]  /= G;
	    }	   
	}
	// reverse order
	double [][] tmp = new double[c_star.length][];
	for(int i=0; i< c_star.length; i++) {
	    tmp[i] = c_star[c_star.length-1-i];
	}	
	return tmp;
    }

    /** Maps m to the cStar(alpha,beta) table */
    static HashMap<Integer,CStar> h=new HashMap<Integer,CStar>();

    public static synchronized double get(double alpha, double beta, int m) {
	Integer key = new Integer(m);
	CStar c = h.get(key);
	if (c==null) {
	    final int N=1000, G=1000;
	    double gamma = EE4Mu.gamma(m);
	    h.put(key, c = new CStar(N, G, gamma));
	}
	return c.getValueGeneral(alpha,beta);
    }

    static public void main(String[] argv) {
       	if (argv.length <3 || argv.length>3) {
	    System.out.println("Usage: java CStar N gridSize gamma");
	    return;
	}
	int k=0;
	//	int m = Integer.parseInt(argv[k++]);
	//	double gamma = EE4Mu.gamma(m);
	//	System.out.println("gamma(m="+m+")="+ gamma);

	final int N = Integer.parseInt(argv[k++]);
	final int G = Integer.parseInt(argv[k++]);
	double gamma = Double.parseDouble(argv[k++]);
	System.out.println("N="+N+", gridSize="+G+", gamma="+ gamma);

	double[][] cStar =  cStar_m2( N, G, gamma);
	System.out.println(" --- c* ----");	
	NumberFormat fmt = new DecimalFormat("0.###");

	for(int i=0; i< cStar.length; i++) {
	    System.out.print("(beta="+(1+i)+")");	    
	    for(int j=0; j< cStar[i].length; j++) {
		System.out.print(" " + 	fmt.format(cStar[i][j]));
	    }	   
	    System.out.println();
	}
   }

}
