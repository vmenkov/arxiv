package edu.rutgers.axs.recommender;

import java.util.*;
import java.io.*;
import org.apache.commons.lang.mutable.*;


/** A mutable aid-to-real-value map */
public class CoMap<Key> extends HashMap<Key,MutableDouble> { 
    public void addCo(Key aid, double inc) {
	MutableDouble z=get(aid);
	if (z==null) {
	    put(aid,  new MutableDouble(inc));
	} else {
	    z.add(inc);
	}
    }
}
