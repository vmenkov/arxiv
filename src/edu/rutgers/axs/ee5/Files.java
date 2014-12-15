package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;

/** Location of input files related to EE5 clustering */
class Files {

    /** All input files are somewhere under this directory */
    private static final String basedir = "/data/arxiv/ee5/20141201";
    private static final String docclusterdir = basedir +  "/docclusters";
    private static final String cstarbindir = basedir +  "/cstarbin";

    /** The file that lists all multiwords, and assigns each
	one to one of the L word cluster */
    static File getWordClusterFile() {
	File d = new File(basedir);
	return new File(d, "kmeans1024.csv");
    }

    static File getDocClusterDir() {
	return new File(docclusterdir);
    }


    /** Category-specific file that lists all clusters in the
	category, and describes each one by an L-dimensional
	vector in the word2vec word cluster space. */       
    static File getDocClusterFile(String fullCatName) {
	File d0=getDocClusterDir();
	File d = new File(d0,  fullCatName);
	return new File(d, "comm_clus_probs.dat");
    }

    /** Lists all categories for which doc cluster subdirs exist */
    static String[] listCats() {
	File d=getDocClusterDir();
	Vector<String> v = new Vector<String>();
	for(File f: d.listFiles()) {
	    if (f.isDirectory()) v.add( f.getName());
	}
	return (String[])v.toArray(new String[0]);
    }

    static final String SUFFIX_BIN = ".bin";

    /** Lists all binary files produced with RandomAccessConverter */
    static File[] listCstarBinaryFiles() {
	File d=new File(cstarbindir);
	Vector<File> v = new Vector<File>();
	for(File f: d.listFiles()) {
	    if (f.isFile() && f.getName().endsWith(SUFFIX_BIN)) v.add( f);
	}
	return (File[])v.toArray(new File[0]);
    }
    

}