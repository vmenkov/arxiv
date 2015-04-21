package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;

/** Location of input files related to EE5 clustering */
class Files {

    /** All input files are somewhere under this directory */
    private static String basedir = "/data/arxiv/ee5/20141201";

    /** Set the basedir for this application */
    static void setBasedir(String x) { 
	basedir = x;
	System.out.println("Using basedir="+ basedir+" for all EE5 files");
    }

    static String getBasedir() { return basedir; }


    /** Which dir structure is in use? (Year 2014 directory structure
	vs year 2015 structure) */
    static boolean mode2014=true;

    /*
    private static final String docclusterdir() {
	return basedir +  "/docclusters";
    }
    */
    static File getDocClusterDir() {
	return new File(basedir +  "/docclusters");
    }

    private static final File cstarbindir() {
	return new File(basedir +  "/cstarbin");
    }

    /** The file that lists all multiwords, and assigns each
	one to one of the L word cluster */
    static File getWordClusterFile() {
	File d = new File(basedir);
	return new File(d, "kmeans1024.csv");
    }

    /** In 2015, cat file names end in this suffix */
    private static final String year2015_catFileSuffix ="_docClusProbs.dat";

    /** Category-specific file that lists all clusters in the
	category, and describes each one by an L-dimensional
	vector in the word2vec word cluster space. 
	
	<p>The directory structure changed between 2014-11 and 2015-04.
	2015:
	casb_communities_20150415/astro-ph.CO_docClusProbs.dat
    */       
    static File getDocClusterFile(String fullCatName) {
	File d0=getDocClusterDir();
	if (mode2014) { // dir structure used in 2014
	    File d = new File(d0,  fullCatName);
	    return new File(d, "comm_clus_probs.dat");
	} else { // dir structure used in 2015-04-15
	    return new File(d0, fullCatName + year2015_catFileSuffix);
	}
    }

    /** Lists all categories for which doc cluster subdirs (in 2014) or
	doc cluster files (in 2015) exist */
    static String[] listCats() {
	File d=getDocClusterDir();
	Vector<String> v = new Vector<String>();
	if (mode2014) { // dir structure used in 2014: one dir per cat
	    for(File f: d.listFiles()) {
		if (f.isDirectory()) v.add( f.getName());
	    }
	} else  { // dir structure used in 2015: one file per cat
	    for(File f: d.listFiles()) {
		if (!f.isFile()) continue;
		String s =f.getName();
		if (!s.endsWith(year2015_catFileSuffix)) continue;
		s = s.substring(0,s.length() - year2015_catFileSuffix.length());
		v.add(s);
	    }
	} 
	return (String[])v.toArray(new String[0]);
    }

    static final String SUFFIX_BIN = ".bin";

    /** Lists all binary files produced with RandomAccessConverter */
    static File[] listCstarBinaryFiles() {
	File d = cstarbindir();
	Vector<File> v = new Vector<File>();
	for(File f: d.listFiles()) {
	    if (f.isFile() && f.getName().endsWith(SUFFIX_BIN)) v.add( f);
	}
	return (File[])v.toArray(new File[0]);
    }
    

}