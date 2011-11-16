package dd.web;

import java.io.*;
import java.util.*;
import java.text.*;

import java.awt.*;


import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.disk.*;

import dd.engine.*;
import dd.gui.*;

/** A single instance of this class is associated with a particular
 * session of the Frontier Finder Lite Application (via
 * HttpSession.setAttribute), and stores all the information that may
 * be set via one http request and needs to be accessible during the
 * processing of other requests.

 * There are two purposes for storing data like this: 
 
 <ol> <li> Not having to pass information about sensor, budget,
 etc. from call to call via e.g. hidden parameters.

 <li> Logically integrating the production of an image and its text
 description - the two things that are logically connected, but have
 toi be served by two seprate servlets.

 </ol>
 */
public class FFSessionData {

    /** Back-pointer */
    final private HttpSession session;

    /** Max number of sensors allowed */
    public final static int maxNS = 6;
    public static int colspan = maxNS;//, sensorRows = 1;
    
    /** Widths of columns, in percents */
    public static int w[] = new int[maxNS];
    static {
	/*
	if (maxNS > 4) {
	    colspan = (maxNS+1)/2;
	    sensorRows = 2;
	}
	*/
	int w0 = 100/maxNS;
	for(int i=0; i<w.length-1; i++) w[i]= w0;
	w[w.length-1] = 100 - (maxNS-1)*w0;
    }


    /* The set of sensors that we display and analyse. Elements beyond
     * a certain point may be nulls. */
    final public Test[] sensors=new Test[maxNS];
    public int nSensors() {
	int n=0;
	while(n<sensors.length && sensors[n]!=null) n++;
	return n;
    }
    //public String sensorFileName = null;
    //public boolean sensorFromTextarea = false;
    /** elements at and beyond sensors.length will be null */
    final public PresentedSensor[] presentedSensors =new PresentedSensor[maxNS];

    public PresentedFrontier presentedFrontier = null;

    /** Includes pi, eps etc. */
    public FrontierContext context = new FrontierContext(false,0,VSMethod.VM1,1e-3);

    FFSessionData( HttpSession _session) throws WebException,IOException {
	session = _session;
	initSensorsBlank();
    }

    synchronized void initSensorsAF()   throws WebException, IOException , DDParseException {
	String names[] = {"sensorA.txt", "sensorB.txt", "sensorC.txt", 
			  "sensorD.txt", "sensorE.txt", "sensorF.txt"};
	initSensors( "/WEB-INF/sensors/a/",names);
    }

    synchronized void initSensorsSS()   throws WebException, IOException , DDParseException  {
	String names[] = {"sensorSS1g.txt", "sensorSS2g.txt", "sensorSS3g.txt", 
			  "sensorSS4g.txt"};
	initSensors( "/WEB-INF/sensors/SS/",names);
    }

    synchronized void initSensorsBlank() {
	for(int i=0; i<maxNS; i++) {
	    sensors[i] = null;
	}
    }

    synchronized void initSensors(String prefix, String[] names)
	throws WebException, IOException, DDParseException{
	initSensorsBlank();
	ServletContext context = session.getServletContext(); 
	int n = (names.length < maxNS) ? names.length : maxNS;

	for(int i=0; i<n; i++) {
	    String path = prefix + names[i];
	    InputStream is = context.getResourceAsStream(path);
	    if (is==null) throw new WebException("Cannot find file '"+path+"' at the server's application context.");
	    sensors[i] = new Test(new BufferedReader(new InputStreamReader(is)),
				  names[i], 1);
	    
	}
    }

    void update(HttpServletRequest request) {
	/*
	String s = request.getParameter("pi");
        if (s!=null) {
            try {
                double pi = Double.valueOf(s);
                if (pi>=0 && pi<=1) {
		    context = context.changePi(pi);
		}
            } catch(Exception ex) {}
	}

	s = request.getParameter("budget");
        if (s!=null) {
            try {
                Double b = new Double(s);
                if (b.doubleValue()>=0 &&b.doubleValue() <=1) {
		    budget = b;
		}
            } catch(Exception ex) {}
	}

	s = request.getParameter("stage");
        if (s!=null) { 
	    // only use the first char of the string, for IE compatibility
	    s = s.trim();
	    if (s.length()>0) s = s.substring(0,1);
            try {
                int x = Integer.valueOf(s);
                if (x>=1 && x<=3) {
		    stage = x;
		}
            } catch(Exception ex) {}
	}
	*/
    }


    /** Generates the description of the data that we may need to plot */
    /*
    public void makePresentedData() {
	if (stage==1 ||stage==2 ) {		
	    presented = new PresentedSensor(q, context, stage, budget);
	} else if (stage==3) {
	    
	    Calendar  startTime=Calendar.getInstance();
	    Test[] actualSensors  = {q};
	    
	    Frontier f = new Frontier(q,  context);
	    AnnotatedFrontier frontier=new AnnotatedFrontier(f,1,startTime);
	    
	    //AnnotatedFrontier frontier =  Frontier.buildFrontier(actualSensors, otherFrontiers);

	    presented = new 
		PresentedFullMixing(actualSensors, frontier, budget);

	} else {
	    // can't do anything
	}
    }	
    */     

    /** Looks up the DemoSessionData already associated with the
     * current session, or creates a new one. This is done atomically,
     * synchronized on the session object.
     */
    static FFSessionData getFFSessionData(HttpServletRequest request) throws WebException,IOException{
	HttpSession session = request.getSession();

	String name = "ff";
	FFSessionData sd  = null;
	synchronized(session) {
	    sd  = ( FFSessionData) session.getAttribute(name);
	    if (sd == null) {
		sd = new FFSessionData(session);
		session.setAttribute(name, sd);
	    }
	    sd.update(request);

	}
	return sd;
    }

    final static NumberFormat pcfmt = new DecimalFormat("#0.##");
    final static NumberFormat ratefmt = new DecimalFormat("0.###");

    /** synchronize presentation with stored sensors */
    synchronized void synchronizeSensorPresentation() {
	for(int i=0; i<maxNS; i++) {
	    if (sensors[i]==null) {
		presentedSensors[i]=null;
	    } else if (presentedSensors[i]!=null && presentedSensors[i].sensor== sensors[i]) {
	    } else {
		presentedSensors[i] = new PresentedSensor(sensors[i], context, 2, null);
		// w h 
		presentedSensors[i].setRecommendedDim(new Dimension(200, 200));
	    }
	}
    }


    void setMulti(HttpServletRequest request) throws WebException {
	if (nSensors()==0) {
	    throw new WebException("No sensors has been supplied. Perhaps your session has expired; in this case, you must restart from the <a href=\"index.html\">start page</a>");
	}


	for(int i=0; i<nSensors(); i++) {
	    String s = request.getParameter("multi" + i);
	    if (s != null) {
		int m = 1;
		try {
		    m = Integer.parseInt(s);
		} catch (Exception e) {}
		if (m <0) throw new WebException("Negative 'number of copies' for given for sensor No. " + (i+1) + ". Please go back and enter a positive number (or 0, to exclude the sensor)");
		sensors[i].setNCopies(m);
	    }
	}
	int sum =0;
	for(int i=0; i<nSensors(); i++) {
	    int n = sensors[i].getNCopies();
	    sum += sensors[i].getNCopies();
	}
	if (sum <= 0) {
	    throw new WebException("Please go back and make sure to enter at least one positive number for a sensor's number of copies. (Or maybe your session has expired? In that case, please restart from the <a href=\"index.html\">start page</a>. (Currently, stored "+nSensors()+" sensors)");
	}
	
    }

    void computeFrontier() throws WebException, DDException {
	int n = nSensors();
	if (n == 0) {
	    throw new WebException("No sensors have been specified! Please go back and make sure that at least one sensor has been supplied");
	}


	// stored copy
	Test[] actualSensors = new Test[n];
	for(int i=0; i<n; i++)  actualSensors[i] = sensors[i];
	actualSensors =  Main.approximateSensors(actualSensors);
	AnnotatedFrontier frontier =
	    Frontier.buildFrontier(actualSensors, context, -1, null);

	presentedFrontier =new PresentedFrontier(actualSensors, frontier, null);
    }
	
    synchronized void deleteSensor(int id)  throws WebException, DDException {
	int  n = nSensors();
	if (id < 0 || id >= n) throw new  WebException("Illegal or missing sensor id ("+id+")");
	int i=id;
	for(;i < n-1; i++) {
	    sensors[i] = sensors[i+1];
	    presentedSensors[i] = presentedSensors[i+1];	    
	}
	sensors[n-1] = null;
	presentedSensors[n-1] = null;
    }


}
