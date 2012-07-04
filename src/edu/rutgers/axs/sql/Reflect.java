package edu.rutgers.axs.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;

import java.lang.reflect.*;

/** A bunch of methods to figure what fields a class has, and how to
 * print them out in a more or less sensible way.
 */
public class Reflect {

    public static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static public String makeGetMethodName(String name) {
	String capName = name.substring(0,1).toUpperCase() + name.substring(1);
	return "get" + capName; 
    }
    static public String  makeSetMethodName(String name) {
	String capName = name.substring(0,1).toUpperCase() + name.substring(1);
	return "set" + capName; 
    }

    /** An entry describes one field of the class, complete with its
     * access methods and the display hints
     */
    static public class Entry implements Comparable {
	public String name;
	public boolean editable, rp, payment;
	public Field f;
	public Method g, s;
	double order;
	public int 	compareTo(Object _o) {
	    if (!(_o instanceof Entry)) throw new IllegalArgumentException();
	    Entry o = (Entry)_o;
	    if (order == o.order) return 0;
	    else if (order == 0) return +1;
	    else if (o.order == 0) return -1;
	    else return (order  - o.order > 0) ? 1 : -1;
	}

	/** Returns true if this is an enum field which is stored in
	    the SQL database as a string (rather than int)
	*/
	private boolean enumAsString() {
	    Enumerated anno = (Enumerated)f.getAnnotation(Enumerated.class);
	    return anno!=null && anno.value()==EnumType.STRING;
	}

	/** The name of the field, or the alt value, if provided */
	String compactTitle() {
	    Display anDisplay = (Display)f.getAnnotation(Display.class);
	    return (anDisplay!=null && anDisplay.alt()!=null && anDisplay.alt().length()>0) ?
		anDisplay.alt() :    name;
	}

	/** Does this field store an MD5 digest, rather than the actual value?
	 */
	public boolean isDigest() {
	    Display anDisplay = (Display)f.getAnnotation(Display.class);
	    return (anDisplay!=null) && anDisplay.digest();
	}

	/** Class.field, e.g. "Respondent.first_name" */
	public String destName() {
	    return f.getDeclaringClass().getSimpleName()+"."+ f.getName();
	}
	

    }


    public Entry[] entries = null;
    private  HashMap<String,Entry> entryTable = new HashMap<String,Entry>();
    public Entry getEntry(String name) { return entryTable.get(name);}

    /** Finds the entry that describes the field whose type is the the
     * enumerated class for which e is one of the values. This method only 
     * makes sense to use if the class has only field with that enum type;
     * otherwise, an error will be thrown
     @return A matching Entry object, or null
     */
    public Entry getOwningEntry(Enum e) throws IllegalArgumentException {
	Class ec = e.getClass();
	Entry z = null;
	for(Entry entry: entries) {
	    if (ec.equals(entry.f.getType())) {
		if (z!=null) throw new IllegalArgumentException("The class has multiple fields of the enum type of "+e+": " + z.f +", " + entry.f);
		z = entry;
	    }
	} 
	return z;
    }

    private static HashMap<Class, Reflect> table = new HashMap<Class,Reflect>();
    /** Looks up or creates a Reflect instance for a specified class.

	@param c Class to analyze. We reduce it to an existing basic
	class, if possible (in case it is of an automatically derived
	type, such as
	org.apache.openjpa.enhance.edu.rutgers.axs.sql$Respondent$pcsubclass
	)
    */
    static public synchronized Reflect getReflect(Class c) {
	Class basics [] = {//Respondent.class, PhoneCall.class, Response.class,
			   User.class, Action.class};
	for(Class b: basics) {
	    if (b.isAssignableFrom(c)) {
		c = b;
		break;
	    }
	}

	Reflect r = table.get(c);
	if (r==null) {
	    r = new Reflect(c);
	    table.put(c,r);
	}
	return r;
    }

    private Reflect(Class c) {
	//Logging.info("Reflect(" + c +")");
	Vector<Entry> v = new Vector<Entry>();
	for(Field f: c.getDeclaredFields()) {
	    Entry e = new Entry();
	    e.f = f;
	    e.name = f.getName();

	    String gn = makeGetMethodName(e.name), sn=makeSetMethodName(e.name);
	    e.g = e.s = null;
	    try {
		e.g =c.getMethod(gn);
		e.s =c.getMethod(sn, e.f.getType() );	      
	    } catch (Exception ex) { 
		//Logging.warning("Reflect(" + c +"): failure for " + e.name);
		continue;
	    }

	    Display anno = (Display)e.f.getAnnotation(Display.class);
	    e.editable = (anno==null) || anno.editable(); // default yes
	    e.rp = (anno!=null) && anno.rp(); // default no
	    e.payment = (anno!=null) && anno.payment(); // default no
	    e.order = (anno==null) ? 0 : anno.order();
	    v.addElement(e);
	    entryTable.put(e.name, e);
	}
	entries = v.toArray(new Entry[v.size()]);
	Arrays.sort(entries);
	//Logging.info("Reflect(" + c +") successful, e.length="+entries.length);	
    } 

    /** Prints all appropriate fields of the specified object in the default
	(toString) format
     */
    public static String reflectToString(Object o) {
	return reflectToString(o, true); 
    }

    
    /** Compact human readable format, with no extra quotes, for various
	HTML tables
    */
    public static String compactFormat(Object val) {
	String s;
	if (val==null) return "null";
	else if (val instanceof Date) {
	    s = sqlDf.format((Date)val);
	    final String suffix = " 00:00:00";
	    if (s.endsWith(suffix)) s = s.substring(0, s.length()-suffix.length());
	} else {
	    s = val.toString();
	}
	return s;
    }

    public static String reflectToString(Object o, boolean skipNulls) {
	
	StringBuffer b = new StringBuffer();
	Reflect r = Reflect.getReflect(  o.getClass());
	//Logging.info("Reflecting on " + o.getClass() +"; reflect=" + r + ", has " + r.entries.length + " entries");
	for(Reflect.Entry e: r.entries) {
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    if (skipNulls && val==null || val.toString().equals("")) continue;
	    if (skipNulls && e.name.equals("version")) continue;
	    b.append(e.name+"=" + compactFormat(val) +"; ");
	}
	return b.toString();
    }

    /** More pretty version of {@link #reflectToString()} */
    public static String  customizedReflect(Object o, PairFormatter f) {
	StringBuffer b = new StringBuffer();
	Reflect r = Reflect.getReflect(  o.getClass());

	// the rest of the fields
	for(Reflect.Entry e: r.entries) {
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    if (val==null || val.toString().equals("")) continue;
	    if (e.name.equals("version")) continue;

	    //	    if (o instanceof PhoneCall) {
	    //		if (e.name.equals("resume") && val.toString().equals("0")) continue;
	    //}

	    b.append(f.row(e.compactTitle(), Reflect.compactFormat(val)));
	}
	return b.toString();
    }


    public static String csvRow(Object o) {
	return csvRow(o, "\n");
    }

    /** Saves the object as a row of comma-separated file
	@param end The text to append to the end (CR, or "")
     */
    public static String csvRow(Object o, String end) {
	Vector<String> row = asStringVector(o, "\"");
	StringBuffer b = new StringBuffer();
	for(String s: row) {
	    if (b.length()>0) b.append(",");
	    b.append(s);
	}
	b.append(end);
	return b.toString();
    }

    /** Returns a complete TR  element, or just a bunch of TD cells  **/
    public static String htmlRow(Object o, boolean TR) {
	Vector<String> row = asStringVector(o, "");
	StringBuffer b = new StringBuffer("");
	if (TR) b.append("<tr>");
	for(String s: row) {
	    b.append("<td>"+ s +"</td>");
	}
	if (TR) b.append("</tr>");
	return b.toString();
    }

   public static String htmlHeaderRow(Class c, boolean TR) {
	StringBuffer b = new StringBuffer("");
	if (TR) b.append("<tr>");
	Reflect r = Reflect.getReflect(c);
	for(Reflect.Entry e: r.entries) {
	    b.append("<th>"+ e.name +"</th>");
	}
	if (TR) b.append("</tr>");
	return b.toString();
    }


    /** @param quote the string to use for quotes (may be an empty string, if no quotes are needed) */
    public static Vector<String>  asStringVector(Object o, String quote) {

	Vector<String> v = new Vector<String>();

	Reflect r = Reflect.getReflect(  o.getClass());
	
	for(Reflect.Entry e: r.entries) {
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    v.addElement( formatAsString(val,quote));
	}
	return v;
    }

    /** Formats a single field of an object.

	Note the somewhat peculiar treatment of boolean values in
	OpenJPA. If the object has been retreived with a query
	obtained with createQuery() (i.e., a JPQL query), then a
	boolean value will be retrieved as Boolean object. But if
	createNativeQuery() (over MySQL, at any rate) has been used -
	i.e., we have a SQL query - then boolean values will appear as
	strings, one character long, containing char(0) or char(1)!
	This is because in MySQL booleans are "synonyms for TINYINT(1)".
	http://dev.mysql.com/doc/refman/5.0/en/numeric-type-overview.html

     */
    public static String formatAsString(Object val, String quote) {
	boolean needQuotes = 
	    !(val instanceof Enum || val instanceof Number || val instanceof Boolean);
	if (val==null) {
	    return "";
	}

	String s;
	if (val instanceof OurTable) s = "" + ((OurTable)val).getId();
	else if (val instanceof Date) {
	    s =  sqlDf.format((Date)val);
	} else if (val instanceof String) {
	    s = (String) val;
	    if (s.length()==1) {
		// special treatment is needed for booleans in native
		// (SQL) queries over MySQL, to make them human-readable
		char x = s.charAt(0);
		if (x==(char)0) s = "false";
		else if (x==(char)1) s = "true";
	    }
	} else {
	    // FIXME: there should be a better way to escape double quotes
	    s = // "OTHER["+val.getClass()+"]:" + 
		val.toString().replace('"', '\'');		
	}
	
	if (needQuotes) s = quote + s + quote;
	return s;
    }

    /** Saves the class description as the header line of a comma-separated file
     */
    public static String csvHeader(Class c) {	
	StringBuffer b = new StringBuffer();

	Reflect r = Reflect.getReflect(c);
	
	for(Reflect.Entry e: r.entries) {
	    if (b.length()>0) b.append(",");
	    b.append( e.name);
	}
	return b.toString();
    }

    /** Returns the array of field names */
    public static String[] getNames(Class c) {	
	Reflect r = Reflect.getReflect(c);
	String a[] = new String[r.entries.length];
	int i=0;
	for(Reflect.Entry e: r.entries) {
	    a[i++] = e.name;
	}
	return a;
    }

    /** FIXME: should get info from the @Entity annotation instead */
    static String getTableName(Object val) {
	//if (val instanceof Respondent) return "Respondent";
	//else if (val instanceof PhoneCall) return "PhoneCall";
	//else if (val instanceof Response) return "Response";
	//else
	    throw new IllegalArgumentException("Don't know what table stores objects of the type " + val.getClass() + ". It's time to learn about the 'Entity' annotation");
    }

    // FIXME: there should be a better way to escape special chars
    private static String  escapeStringForSQL(String s) {
	StringBuffer b = new StringBuffer(s.length());
	for(int i=0; i<s.length(); i++) {
	    char x = s.charAt(i);
	    if (x == '\'') b.append("''");
	    else if (Character.isWhitespace(x)) b.append(" ");
	    else if (x=='%' || x=='\\') b.append( "\\" + x);
	    else b.append(x);
	}
	return b.toString();
    }
    
    /** Saves the object as a MySQL "INSERT" statement
     */
    public static String saveAsInsert(Object o) {
	
	Reflect r = Reflect.getReflect(  o.getClass());

	StringBuffer b = new StringBuffer("INSERT INTO " + getTableName(o) );

	StringBuffer names = new StringBuffer(), values = new StringBuffer();

	for(Reflect.Entry e: r.entries) {

	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    if (val==null) continue;

	    if (names.length()>0) names.append(",");

	    // the name of SQL table column
	    String name = e.name;
	    if (val instanceof OurTable) name += "_id";
	    names.append(name);

	    boolean needQuotes = 
		(val instanceof Enum) ? e.enumAsString() :
		(val instanceof String || val instanceof Date);

	    if (values.length()>0) values.append(",");

	    if (needQuotes) values.append("'");
	    String s;
	    if (val instanceof OurTable) s = "" + ((OurTable)val).getId();
	    else if (val instanceof Number) s = val.toString();
	    else if (val instanceof Boolean) s = ((Boolean)val).booleanValue()? "1":"0";
	    else if (val instanceof Enum) {
		s = e.enumAsString()? val.toString() : ""+((Enum)val).ordinal();
	    } else if (val instanceof Date) {
		s = sqlDf.format((Date)val);
	    } else if (val instanceof String) {
		// FIXME: there should be a better way to escape double quotes
		s = escapeStringForSQL((String)val);
	    } else {
		throw new IllegalArgumentException("Data type " + val.getClass() + " is not supported. Field = " + e.name);
	    }
	    values.append(s);
	    if (needQuotes) values.append("'");
	}
	b.append( " ("+names+") VALUES ("+values+")");
	return b.toString();
    }


}