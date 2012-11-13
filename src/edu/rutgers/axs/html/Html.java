package edu.rutgers.axs.html;

import java.io.*;
import java.util.*;

import edu.rutgers.axs.web.*;
import edu.rutgers.axs.sql.*;


public class Html {
    public static String a(String url, String text) {
	return "<a href=\""+url+"\">" + text + "</a>";
    }
}