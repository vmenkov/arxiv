package edu.rutgers.axs.sql; //.web;

import java.util.logging.*;
import java.io.*;

/** Methods used by the My.Arxiv webapp's classes to log warning and error
 * messages. These methdos are simply wrappers around the respective
 * methods of  java.util.logging.Logging.
 */
public class Logging {
    public final static String NAME = "arxiv";

    static {
        try {
            Handler fh = new FileHandler("/mnt/cactext/home/SCILSNET/vmenkov/logs/arxiv1.log");
            fh.setFormatter(new SimpleFormatter());

            Logger.getLogger(NAME).addHandler(fh);
        } catch (IOException e) {}
    }

    public static void error(String msg) {
	Logger logger = Logger.getLogger(NAME);
	logger.severe(msg);
    }

    public static void warning(String msg) {
	Logger logger = Logger.getLogger(NAME);
	logger.warning(msg);
    }

    public static void info(String msg) {
	Logger logger = Logger.getLogger(NAME);
	logger.info(msg);
    }

    public static void setLevel(java.util.logging.Level newLevel) {
	Logger logger = Logger.getLogger(NAME);
	logger.setLevel(newLevel);
    }


}

