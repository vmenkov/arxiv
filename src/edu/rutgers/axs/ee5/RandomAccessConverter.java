package edu.rutgers.axs.ee5;

import java.io.*;
import java.util.*;

import edu.rutgers.axs.sql.Logging;

/** This is an auxiliary application converting text files prepared by XTZ
    to random access binary files. The input file format is explained in 
    EE5_documentation.pdf
 */
class RandomAccessConverter {
    /** The size, in bytes, of the data type (float) we use to store data
	in the binary file. */
    static final int SIZEOF = 4;

    /** Converts a text file to binary file of float values.
	@return the number of values processed 
     */
    static int convert(File input, File output) throws IOException {
	RandomAccessFile out = new RandomAccessFile(output, "rw");
	out.setLength(0); // truncate
	FileReader fr = new FileReader(input);
	LineNumberReader r = new LineNumberReader(fr);
 	String s;
	int linecnt = 0, cnt=0;
	
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();	
	    String q[] = s.split(",\\s*");
	    cnt += q.length;
	    for(String token: q) {
		double val = Double.parseDouble(token);
		out.writeFloat((float)val);
	    }
	}
	r.close();
	long outlen = out.length();
	Logging.info("Read " + cnt + " values ("+linecnt+" lines) from " + input +"; wrote binary file "+ output+",  length=" + outlen + " bytes");
	if (outlen != cnt * SIZEOF) throw new IllegalArgumentException("Some kind of error has happened: the length of the output file ("+output+") does not match the number of values in the input file");
	return cnt;
    }
    
    static public void main(String argv[])  throws IOException {
	File input = new File(argv[0]);
	File output = new File(argv[1]);
	RandomAccessConverter.convert(input, output);
    }

}