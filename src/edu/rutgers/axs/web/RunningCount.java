 package edu.rutgers.axs.web;

import java.io.*;
import java.util.*;

/** This class is used to count "events" that have happened within 
    a certain recent time interval.
*/
class RunningCount {
    /** The size, in seconds, of the "bucket unit" -- the main unit of time in
	our counter. */
    final int bucketLengthSec;
    /** Number of buckets.  */
    final int NB;
    /** The buckets. Each one contains the count of events that occurred
	within a time slice of length  bucketLengthSec */
    final int[] buckets;
    /** The time stamp, in "bucket units", corresponding to the last
	access to the counter.     */
    private long lastT;

    RunningCount(int totalScopeSec, int _bucketLengthSec) {
	bucketLengthSec = _bucketLengthSec;
	NB = totalScopeSec/bucketLengthSec;
	buckets = new int[NB];
	lastT = getT();
    }

    /** Maps the current clock time to bucket number */
    private  long getT() {
	long msec = (new Date()).getTime();
	return  msec / (1000 * bucketLengthSec);
    }
    private int getJ() {
	return (int) (getT() % NB);
    }

    
    synchronized private long advanceT() {
	long t = getT();
	// how many buckets do we need to empty?
	long needToClean = t - lastT; 
	int m = (needToClean>=NB) ? NB : (int)needToClean;
	for(int i=0; i<m; i++) {
	    buckets[(int)((t-i)%NB)]=0;
	}
	lastT = t;
	return lastT;
    }
    
    /** Records one more event */
    synchronized void inc() {
	long t = advanceT();
	buckets[(int)(t%NB)]++;
    }
    
    /** How many values are stored, in total, in the buckets corresponding
	to the last sec seconds? */
    synchronized int recentCount(int sec) {
	long t = advanceT();
	int nb = (sec / bucketLengthSec);
	if (nb * bucketLengthSec < sec) nb ++;
	if (nb > NB) throw new IllegalArgumentException("Time interval " + sec + " sec is too long. We only keep stats for the last " + nb + "*" + bucketLengthSec + " sec");
	int sum=0;
	for(int i=0; i<nb; i++) {
	    sum += buckets[(int)((t-i)%NB)];
	}
	return sum;
    }

}

