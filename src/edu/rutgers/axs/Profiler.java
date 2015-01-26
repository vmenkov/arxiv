package  edu.rutgers.axs;

import java.util.*;

/** Auxiliary class used to report time spent by the computer
 * executing various parts of a Java application. */
public class Profiler {

    private boolean on = true;

    /** Identifying codes for various sections of the application
	being profiled. Modify this as required. */
    public enum Code {
	OTHER,
	    CLUSTERING,
	    CLU_Voronoi,
	    CLU_fc,
	    CLU_sumDif,
	    AA_getTVF,
	    AA_df,
	    AA_getCoef;
    };
    
    private long[] accounts= new long[ Profiler.Code.class.getEnumConstants().length];
    
    private Date started = null;
    static final int N = 10000;
    private Code[] stack = new Code[N];
    private int n = 0;

    String stackToString() {
	StringBuffer b=new StringBuffer("(");
	for(int i=0; i<n; i++) 	    b.append(" " + stack[i]);
	b.append(")");
	return b.toString();
    }

    /** Beginning of a particular profiling section.
	@param x identifying code for the secion. */
    public void push(Code x) {
	if (!on) return;
	Date now = new Date();
	if (n>0) {
	    if (started==null) throw new AssertionError("Profiler: started==null");
	    accounts[stack[n-1].ordinal()] += now.getTime() - started.getTime();
	}
	if (n>=N) throw new AssertionError("Profiler: stack oveflow");
	stack[n++] = x;
	started = now;
    }

    /** End of a particular profiling section.
	@param x identifying code for the secion; it should be the same as the code in the matching push() call. */
    public void pop(Code x) {
	if (!on) return;
	Date now = new Date();
	if (n==0 || stack[n-1]!=x) throw new AssertionError("Profiler: pop " + x + " without matching push; stack=" +stackToString());
	accounts[stack[n-1].ordinal()] += now.getTime() - started.getTime();
	n--;	
	started = now;
    }

    /** End of section x and beginning of section y */
    public void replace(Code x, Code y) {
	if (!on) return;
	Date now = new Date();
	if (n==0 || stack[n-1]!=x) throw new AssertionError("Profiler: replace " + x + " without matching push; stack=" +stackToString() );
	accounts[stack[n-1].ordinal()] += now.getTime() - started.getTime();
	stack[n-1] = y;
	started = now;
    }

    /** Produces a report that can be printed at the end of the program's execution. */
    public String report() {
	if (!on) return "Profiling OFF";
	String s = "";
	long total =0;
	for(Code x:Profiler.Code.class.getEnumConstants()) {
	    total += accounts[x.ordinal()];
	    s += x + " " + accounts[x.ordinal()] + " msec\n";
	}
	s += "--- Total " + total + " msec\n";
	return s;
    }

    public static Profiler profiler = new Profiler();

}