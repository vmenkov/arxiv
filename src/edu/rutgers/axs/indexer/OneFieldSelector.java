package edu.rutgers.axs.indexer;

import java.io.*;
//import java.util.*;
//import java.text.*;
//import java.net.*;
//import javax.persistence.*;

//import org.apache.openjpa.persistence.jdbc.*;

import org.apache.lucene.index.*;

import org.apache.lucene.document.*;
//import edu.rutgers.axs.indexer.ArxivFields;

public class OneFieldSelector implements FieldSelector {
    private final String acceptableField;
    public OneFieldSelector(String af) {
	acceptableField = af;
    }
    public FieldSelectorResult accept(String fieldName) {
	return fieldName.equals(acceptableField ) ?
	    FieldSelectorResult.LOAD : 
	    FieldSelectorResult.NO_LOAD;
    }
}
