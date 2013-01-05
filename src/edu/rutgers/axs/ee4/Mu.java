package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;

//import edu.rutgers.axs.ParseConfig;

/** Based on  EE4_DocRevVal_simp.m. Uses 0-based array indexes (Java style)
    instead of 1-based indexes (Matlab style).
 */
public class Mu {

    static double [][] matrix(int n1, int n2) {
	return matrix(n1,n2,0.0);
    }
    static double [][] matrix(int n1, int n2, double val) {
	double [][] v = new double[n1][];
	for(int i=0; i<n1; i++) {
	    v[i] = new double[n2];
	    for(int j=0; j<n2; j++) v[i][j]=val;
	}
	return v;
    }
 
    static class MuBounds { 
	double u=1, l=0; 
	void adjust(double m, boolean idx) {
	    if (idx) {
		u = Math.min(u, m); // gen
	    } else {
		l = Math.max(l, m); // gen2
	    }
	}
	double star() { return (u+l)/2; }
    }

    //function [mu_star]=EE4_DocRevVal_simp(alpha0, beta0, N, gamma,c)

    static double[] EE4_DocRevVal_simp(double alpha0, double beta0, int N, 
			      double gamma, double c) {

	double[][]  V=matrix(N+1, N+1);

	double []   mu_star=new double[N];
	MuBounds muBounds = new MuBounds();

	for(int i=0; i<=N; i++) { //  the total number of samples taken is n=N
	    double m =(alpha0+i)/(alpha0+beta0+N);
	    //EfReward[N-i][i]=0;
	    // This value is a lower bound, which comes from the better of two policies:
	    // don't forward anything (value of 0)
	    // forward now, and forever into the future (value of M-c, summed over the geometric series of gamma^n)
	    // [V(N-i+1,i+1),idx(N-i+1,i+1)]=max([0,M(N-i+1,i+1)-c]/(1-gamma)); 
	    double q = (m-c)/(1-gamma);
	    muBounds.adjust(m, q>0);
	    V[N-i][i] = Math.max(q,0);
	}
	mu_star[N-1] = muBounds.star();
    
	//for n=N-1:-1:0   % n is the total number of samples taken
	//for i=0:1:n  % i is the number of sucesses, so n-i is the number of failures

	//%mu_star_u gives the upper bound for mu_star
	//%mu_star_l gives the lower bound for mu_star

	for(int n=N-1; n>=0; n--) {
	    muBounds = new MuBounds();
	    for(int i=0; i<=n; i++) {
		double m=(alpha0+i)/(alpha0+beta0+n);
		//EfReward(n-i+1,i+1)=gamma*(M(n-i+1,i+1)*V(n-i+1,i+2)+(1-M(n-i+1,i+1))*V(n-i+2,i+1));
		double efReward=gamma*   (m*V[n-i][i+1]+  (1-m)*V[n-i+1][i]);
		//  [V(n-i+1,i+1),idx(n-i+1,i+1)]=max([0,M(n-i+1,i+1)-c+EfReward(n-i+1,i+1)]);
		double q =m-c+efReward;
		V[n-i][i] = Math.max(q,0); 
		muBounds.adjust(m, q>0);
  	    }
	    if (n>0) mu_star[n-1] = muBounds.star();
   	}
    
	return mu_star;
    }

    static double avg(double[] x) {
	double sum=0;
	for(double q:x) sum += q;
	return sum/x.length;
    }

    /** Testing */
    static public void main(String[] argv) {
	if (argv.length<4 ||argv.length>5) {
	    System.out.println("Usage: java edu.rutgers.axs.ee4.Mu alpha beta c gamma [N]");
	    return;
	}
	double alpha = Double.parseDouble(argv[0]),
	    beta = Double.parseDouble(argv[1]),
	    c = Double.parseDouble(argv[2]),
	    gamma = Double.parseDouble(argv[3]);
	int N = (argv.length>4) ? Integer.parseInt(argv[4]) : 1000;
	double[] mu =  EE4_DocRevVal_simp( alpha, beta, N, gamma, c);
	System.out.print("mu(a="+alpha+",b="+beta+",c="+c+",gamma="+gamma+
			   ") = {");
	for(double q: mu) System.out.print( " " + q);
	System.out.println( "}");
	System.out.println( "<mu>=" + avg(mu));
    }

}
