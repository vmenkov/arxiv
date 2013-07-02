package edu.rutgers.axs.ee4;

import java.util.*;
import java.io.*;
import java.text.*;

/** A feature dictionary... */
class DocSet {
    private Vector<String> words=new Vector<String>();
    private HashMap<String,Integer> word2pos = new HashMap<String,Integer>();
    /** String to int */
    int enter(String word) {
	Integer pos = word2pos.get(word);
	if (pos==null) {
	    word2pos.put(word, pos=new Integer(words.size()));
	    words.add(word);
	}
	return pos.intValue();
    }

    String getWord(int i) {
	return words.elementAt(i) ;
    }

    int size() { return words.size(); }


}