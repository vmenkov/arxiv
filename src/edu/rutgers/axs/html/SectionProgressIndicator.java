package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

/** Displays the progress within one section of the activity of
    a larger-scale ProgressIndicator */
public class SectionProgressIndicator extends ProgressIndicator {
    private final ProgressIndicator parent;
    final int parentFrom, parentTo;
    /** The range [0;n] of this indicator will map to 
	[_parentFrom,  _parentTo] of the parent indicator 
    */
    public  SectionProgressIndicator(ProgressIndicator _parent,
				     int _parentFrom, int _parentTo,
				     int _n) {
	super(_n, _parent.pct);
	parent =  _parent;
	parentFrom = _parentFrom;
	parentTo = _parentTo;	
	if (!(0<=parentFrom && parentFrom<=parentTo &&
	      parentTo <= parent.n)) throw new IllegalArgumentException("Section ranges set incorrectly: need 0 &le; "+parentFrom+" &le; "+parentTo+" &le; "+parent.n);    
    }
  
    private int computeParentK() {
	return parentFrom + (k * (parentTo-parentFrom))/n;
    }

    /** @param _k Between 0 an n */
    public void setK(int _k) {
	k = _k;
	parent.setK( computeParentK());
    }
    public void addK(int x) {
	k += x;
	parent.setK( computeParentK());
    }
}
