package edu.rutgers.axs.sql;

import java.io.*;
//import java.util.*;
//import java.text.*;
//import javax.persistence.*;

//import java.lang.reflect.*;

public class ActionSource {
    public Action.Source src=Action.Source.UNKNOWN;
    public long dataFileId=0;
    public ActionSource( Action.Source _src, long _dataFileId) {
	src = _src;
	dataFileId= _dataFileId;
    }
}