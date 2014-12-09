package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;

/** Location of input files related to EE5 clustering */
class Files {

    /** All input files are somewhere under this directory */
    private static final String basedir = "/data/arxiv/ee5";
    private static final String docclusterdir = basedir +  "/docclusters";

    /** The file that lists all multiwords, and assigns each
	one to one of the L word cluster */
    static File getWordClusterFile() {
	File d = new File(basedir);
	return new File(d, "kmeans1024.csv");
    }


    /** Category-specific file that lists all clusters in the
	category, and describes each one by an L-dimensional
	vector in the word2vec word cluster space. */       
    static File getDocClusterFile(String fullCatName) {
	String cdir = docclusterdir + "/" + fullCatName;
	File d = new File(cdir);
	return new File(d, "comm_clus_probs.dat");
    }

    /** Lists all categories for which doc cluster subdirs exist */
    static String[] listCats() {
	File d=new File(docclusterdir);
	Vector<String> v = new Vector<String>();
	for(File f: d.listFiles()) {
	    if (f.isDirectory()) v.add( f.getName());
	}
	return (String[])v.toArray(new String[0]);
    }

}