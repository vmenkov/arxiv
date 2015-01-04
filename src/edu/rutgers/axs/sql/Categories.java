package edu.rutgers.axs.sql;

import java.util.*;

import edu.rutgers.axs.web.EditUser;
import edu.rutgers.axs.web.Tools;
import edu.rutgers.axs.ee5.Classifier;

/**

Check often here, http://jp.arxiv.org/new/ , for new cats!

<p>
As per Simeon Warner, 2012-12-07:

<pre>
Long ago ao-sci was moved to physics.ao-ph, see http://arxiv.org/list/physics.ao-ph/recent for recent subs.
*/

public class Categories {

    static class CategoryException extends Exception {
	CategoryException(String s) { super(s);}
    }


    public static class Cat {
	/** Short name. For a major cat, that's its complete name; for a minor
	 cat, just the second component. */
	public String name;
	/** Long human-readable name */
	public String desc;
	public Vector<Cat> subcats = new Vector<Cat>();	
	/** The parent category (for minor cats only) */
	public Cat parent = null;
	/** Creates a new category and, if it's a minor cat, links it to 
	    the specified (major) parent cat.
	    @param _parent Null for a major cat, or the parent cat for
	    a minor cat.
	    @param _name Full name of a major cat, or the second
	    component of the name for a minor cat
	*/
	private Cat(Cat _parent, String _name, String _desc) {
	    parent = _parent;
	    name = _name;
	    desc = _desc;
	}
	public boolean hasSubs() {
	    return subcats.size()>0;
	}
	public boolean hasSub(String subCatName) {
	    for(Cat x: subcats) {
		if (x.name.equals(subCatName)) return true;
	    }
	    return false;
	}

	public String fullName() {
	    return parent==null? name : parent + "." + name;
	}

	public String toString() { return fullName(); }
    }

    static public Vector<Cat> majors = new Vector<Cat>();
    static HashMap<String,Cat> majorMap=new HashMap<String,Cat>();
    /** Maps "major.minor" to the Cat object */
    private static HashMap<String,Cat> minorMap=new HashMap<String,Cat>();

    private static void addMajor(String name, String desc) throws CategoryException{
	if (majorMap.containsKey(name)) {
	    throw new CategoryException("Major category '"+name+"' already exists");
	}
	Cat c = new Cat(null, name,desc);
	majors.add(c);
	majorMap.put(name,c);
    }    
    

    private static void addMinor(String parent, String name, String desc) throws CategoryException{
	Cat major = majorMap.get(parent);
	if (major==null) {
	    throw new CategoryException("Parent (major) category '"+parent+"' does not exist!");
	}
	if (major.hasSub(name)) {
	    throw new CategoryException("Major category '"+parent+"' already has subcat '"+name+"'");
	}
	Cat newcat = new Cat(major, name, desc);
	major.subcats.add(newcat);
	minorMap.put(newcat.fullName(), newcat);
    }

  
    private static void init()  throws CategoryException{
addMajor("astro-ph","Astrophysics");
//addMajor("acc-phys","Accelerator Physics");
addMajor("cond-mat","Condensed Matter");
//addMajor("chem-ph","Chemical Physics");
addMajor("gr-qc","General Relativity and Quantum Cosmology");
addMajor("hep-ex","High Energy Physics - Experiment");
addMajor("hep-ph","High Energy Physics - Phenomenology");
addMajor("hep-lat","High Energy Physics - Lattice");
addMajor("hep-th","High Energy Physics - Theory");
addMajor("math-ph","Mathematical Physics");
addMajor("nucl-th","Nuclear Theory");
addMajor("nucl-ex","Nuclear Experiment");
addMajor("physics","Physics");
addMajor("quant-ph","Quantum Physics");
//addMajor("ao-sci","Atmospheric-Oceanic Sciences");
//addMajor("atom-ph","Atomic, Molecular and Optical Physics");
//addMajor("bayes-an","Bayesian Analysis");
//addMajor("mtrl-th","Materials Theory");
//addMajor("plasm-ph","Plasma Physics");
//addMajor("supr-con","Superconductivity");
addMajor("math","Mathematics");
//addMajor("alg-geom","Algebraic Geometry");
//addMajor("dg-ga","Differential Geometry");
//addMajor("funct-an","Functional Analysis");
//addMajor("q-alg","Quantum Algebra and Topology");
addMajor("cs","Computer Science");
//addMajor("cmp-lg","Computation and Language");
addMajor("nlin","Nonlinear Sciences");
//addMajor("adap-org","Adaptation, Noise, and Self-Organizing Systems");
//addMajor("chao-dyn","Chaotic Dynamics");
//addMajor("comp-gas","Cellular Automata and Lattice Gases");
//addMajor("patt-sol","Pattern Formation and Solitons");
//addMajor("solv-int","Exactly Solvable and Integrable Systems");
addMajor("q-bio","Quantitative Biology");
addMajor("stat","Statistics");
addMajor("q-fin","Quantitative Finance");

addMinor("nlin","AO","Adaptation and Self-Organizing Systems");
addMinor("nlin","CD","Chaotic Dynamics");
addMinor("nlin","CG","Cellular Automata and Lattice Gases");
addMinor("nlin","PS","Pattern Formation and Solitons");
addMinor("nlin","SI","Exactly Solvable and Integrable Systems");
addMinor("astro-ph","CO","Cosmology and Extragalactic Astrophysics");
addMinor("astro-ph","EP","Earth and Planetary Astrophysics");
addMinor("astro-ph","GA","Galaxy Astrophysics");
addMinor("astro-ph","HE","High Energy Astrophysical Phenomena");
addMinor("astro-ph","IM","Instrumentation and Methods for Astrophysics");
addMinor("astro-ph","SR","Solar and Stellar Astrophysics");
addMinor("physics","acc-ph","Accelerator Physics");
addMinor("physics","ao-ph","Atmospheric and Oceanic Physics");
addMinor("physics","atm-clus","Atomic and Molecular Clusters");
addMinor("physics","atom-ph","Atomic Physics");
addMinor("physics","bio-ph","Biological Physics");
addMinor("physics","chem-ph","Chemical Physics");
addMinor("physics","class-ph","Classical Physics");
addMinor("physics","comp-ph","Computational Physics");
addMinor("physics","data-an","Data Analysis, Statistics and Probability");
addMinor("physics","flu-dyn","Fluid Dynamics");
addMinor("physics","gen-ph","General Physics");
addMinor("physics","geo-ph","Geophysics");
addMinor("physics","hist-ph","History and Philosophy of Physics");
addMinor("physics","ins-det","Instrumentation and Detectors");
addMinor("physics","med-ph","Medical Physics");
addMinor("physics","optics","Optics");
addMinor("physics","ed-ph","Physics Education");
addMinor("physics","soc-ph","Physics and Society");
addMinor("physics","plasm-ph","Plasma Physics");
addMinor("physics","pop-ph","Popular Physics");
addMinor("physics","space-ph","Space Physics");
addMinor("cond-mat","dis-nn","Disordered Systems and Neural Networks");
addMinor("cond-mat","mtrl-sci","Materials Science");
addMinor("cond-mat","mes-hall","Mesoscale and Nanoscale Physics");
addMinor("cond-mat","other","Other Condensed Matter");
addMinor("cond-mat","quant-gas","Quantum Gases");
addMinor("cond-mat","soft","Soft Condensed Matter");
addMinor("cond-mat","stat-mech","Statistical Mechanics");
addMinor("cond-mat","str-el","Strongly Correlated Electrons");
addMinor("cond-mat","supr-con","Superconductivity");
addMinor("math","AC","Commutative Algebra");
addMinor("math","AG","Algebraic Geometry");
addMinor("math","AP","Analysis of PDEs");
addMinor("math","AT","Algebraic Topology");
addMinor("math","CA","Classical Analysis and ODEs");
addMinor("math","CT","Category Theory");
addMinor("math","CO","Combinatorics");
addMinor("math","CV","Complex Variables");
addMinor("math","DG","Differential Geometry");
addMinor("math","DS","Dynamical Systems"); //###
addMinor("math","FA","Functional Analysis");
addMinor("math","GM","General Mathematics");
addMinor("math","GN","General Topology");
addMinor("math","GR","Group Theory");
addMinor("math","GT","Geometric Topology");
addMinor("math","HO","History and Overview");
addMinor("math","IT","Information Theory");
addMinor("math","KT","K-Theory and Homology");
addMinor("math","LO","Logic");
addMinor("math","MP","Mathematical Physics");
addMinor("math","MG","Metric Geometry");
addMinor("math","NA","Numerical Analysis");
addMinor("math","NT","Number Theory");
addMinor("math","OA","Operator Algebras");
addMinor("math","OC","Optimization and Control");
addMinor("math","PR","Probability");
addMinor("math","RA","Rings and Algebras");
addMinor("math","QA","Quantum Algebra");
addMinor("math","RT","Representation Theory");
addMinor("math","SG","Symplectic Geometry");
addMinor("math","SP","Spectral Theory");
addMinor("math","ST","Statistics Theory");
addMinor("cs","AR","Hardware Architecture");
addMinor("cs","AI","Artificial Intelligence");
addMinor("cs","CC","Computational Complexity");
addMinor("cs","CE","Computational Engineering, Finance, and Science");
addMinor("cs","CG","Computational Geometry");
addMinor("cs","CL","Computation and Language");
addMinor("cs","CV","Computer Vision and Pattern Recognition");
addMinor("cs","CY","Computers and Society");
addMinor("cs","CR","Cryptography and Security");
addMinor("cs","DB","Databases");
addMinor("cs","DS","Data Structures and Algorithms");
addMinor("cs","DL","Digital Libraries");
addMinor("cs","DM","Discrete Mathematics");
addMinor("cs","DC","Distributed, Parallel, and Cluster Computing");
addMinor("cs","ET","Emerging Technologies");
addMinor("cs","FL","Formal Languages and Automata Theory");
addMinor("cs","GL","General Literature");
addMinor("cs","GR","Graphics");
addMinor("cs","GT","Computer Science and Game Theory");
addMinor("cs","HC","Human-Computer Interaction");
addMinor("cs","IR","Information Retrieval");
addMinor("cs","IT","Information Theory");
addMinor("cs","LG","Learning");
addMinor("cs","LO","Logic in Computer Science");
addMinor("cs","MS","Mathematical Software");
addMinor("cs","MM","Multimedia");
addMinor("cs","MA","Multiagent Systems");
addMinor("cs","NE","Neural and Evolutionary Computing");
addMinor("cs","NI","Networking and Internet Architecture");
addMinor("cs","NA","Numerical Analysis");
addMinor("cs","OS","Operating Systems");
addMinor("cs","OH","Other Computer Science");
addMinor("cs","PF","Performance");
addMinor("cs","PL","Programming Languages");
addMinor("cs","RO","Robotics");
addMinor("cs","SD","Sound");
addMinor("cs","SE","Software Engineering");
addMinor("cs","SC","Symbolic Computation");
addMinor("cs","SI","Social and Information Networks");
addMinor("cs","SY","Systems and Control");
addMinor("q-bio","BM","Biomolecules");
addMinor("q-bio","GN","Genomics");
addMinor("q-bio","MN","Molecular Networks");
addMinor("q-bio","SC","Subcellular Processes");
addMinor("q-bio","CB","Cell Behavior");
addMinor("q-bio","NC","Neurons and Cognition");
addMinor("q-bio","TO","Tissues and Organs");
addMinor("q-bio","PE","Populations and Evolution");
addMinor("q-bio","QM","Quantitative Methods");
addMinor("q-bio","OT","Other Quantitative Biology");
addMinor("stat","AP","Applications");
addMinor("stat","CO","Computation");
addMinor("stat","ML","Machine Learning");
addMinor("stat","ME","Methodology");
addMinor("stat","OT","Other Statistics");
addMinor("stat","TH","Statistics Theory");
addMinor("q-fin","CP","Computational Finance");
addMinor("q-fin","EC","Economics");
addMinor("q-fin","GN","General Finance");
addMinor("q-fin","MF","Mathematical Finance");
addMinor("q-fin","PM","Portfolio Management");
addMinor("q-fin","PR","Pricing of Securities");
addMinor("q-fin","RM","Risk Management");
addMinor("q-fin","ST","Statistical Finance");
addMinor("q-fin","TR","Trading and Market Microstructure");
    }

    static {
	try {
	    init();
	} catch( CategoryException e) {
	    System.out.println("Category problem: "+e);
	    System.exit(1);
	}
    }

    /** Abolished categories */
    private static HashMap<String,String> subsumed = new HashMap<String,String>();
    static {
	subsumed.put("cmp-lg","cs.CL");
	subsumed.put("adap-org","nlin.AO");
	subsumed.put("comp-gas","nlin.CG");
	subsumed.put("chao-dyn","nlin.CD");
	subsumed.put("solv-int","nlin.SI");
	subsumed.put("patt-sol","nlin.PS");
	subsumed.put("alg-geom","math.AG");
	subsumed.put("dg-ga","math.DG");
	subsumed.put("funct-an","math.FA");
	subsumed.put("q-alg","math.QA");
	subsumed.put("mtrl-th","cond-mat.mtrl-sci");
	subsumed.put("supr-con","cond-mat.supr-con");
	subsumed.put("acc-phys","physics.acc-ph");
	subsumed.put("ao-sci","physics.ao-ph");
	subsumed.put("atom-ph","physics.atom-ph");
	subsumed.put("bayes-an","physics.data-an");
	subsumed.put("chem-ph","physics.chem-ph");
	subsumed.put("plasm-ph","physics.plasm-ph");
    }

    static public Vector<String> listAllStorableCats() {
	Vector<String> v = new  Vector<String> ();
	for(Categories.Cat major: Categories.majors) {
	    if (major.hasSubs()) {
		for(Categories.Cat minor: major.subcats) {
		    String name=major.name +"."+ minor.name;
		    v.add(name);
		}
	    } else {
		String name = major.name;
		v.add(name);
	    }
	}
	return v;
    }

    /** Looks up the active category for the specified full name, if
	one exists.  (An active cat is a cat to which new articles may
	be assigned).  We assume that all minor cats are active, while
	only major cats without subcats are active.

	@param fullname = "major.minor", or just "major" (if "major" has no 
     subcats) */
    public static Cat findActiveCat(String fullname) {
	Cat c = minorMap.get(fullname);
	if (c!=null) return c;
	c = majorMap.get(fullname);
	if (c!=null && !c.hasSubs()) return c;
	return null;
    }

   /** Looks up the major category within which the category with the
       specified full name.

	@param fullname = "major.minor", or just "major" (if "major" has no 
     subcats) */
    public static Cat findMajorCat(String fullname) {
	Cat c = majorMap.get(fullname);
	//System.out.println("FMC: major=" + c);
	if (c!=null) return c;
	c = minorMap.get(fullname);	
	//System.out.println("FMC: " + fullname + " --> minor=" + c + " --> parent = " + c.parent);
	if (c!=null) return c.parent;
	return null;
    }


    /** Creates set of radio buttons reflecting the ArXiv categories
    the specified user is interested in. 

    @param u A user entry. If null, then no boxes will be checked  */
    static public String mkCatBoxes(User u) {
	//final String space = "&nbsp;&nbsp;";
	StringBuffer b = new StringBuffer();
	b.append("<table border=\"1\">\n");
	for(Categories.Cat major: Categories.majors) {
	    b.append("<tr><th valign='top' align='left'>" + 
		     //major.name + " : " + 
		     major.desc + "</th>\n");
	    b.append("<td align='left'>\n");
	    if (major.hasSubs()) {
		for(Categories.Cat minor: major.subcats) {
		    String name=major.name +"."+ minor.name;
		    boolean has = u!=null && u.hasCat(name);
		    b.append( "<strong>" +Tools.checkbox(EditUser.CAT_PREFIX + name, 
							 "on", name, has)
			      + "</strong> " + minor.desc+ "<br>\n");
		    
		}
	    } else {
		String name = major.name;
		boolean has = u!=null && u.hasCat(name);
		b.append( "<strong>" +
			  Tools.checkbox(EditUser.CAT_PREFIX + name, 
					 "on", name, has)
			   + "</strong> " + major.desc+ "<br>\n");
	    }
	    b.append("</td>\n");
	}
	b.append("</table>\n");
	return b.toString();
    }


    /** Prepares a special-purpose box set for the "add discovered categories" feature
	in EE5 */
    static public String mkCatBoxes2(User u, Classifier.CategoryAssignmentReport report) {
	if (u==null) throw new IllegalArgumentException("mkCatBoxes2: no user specified!");

	Vector<String> allCats = Categories.listAllStorableCats();

	//final String space = "&nbsp;&nbsp;";
	StringBuffer b = new StringBuffer();
	b.append("<table border=\"0\">\n");
	for(String name: allCats) {
	    Cat cat = findActiveCat(name);    
	    if (u.hasCat(name)) {
		b.append( "<tr><td valign=top><strong>[X] " + name + "</strong> <td valign=top>(" + cat.desc+
			  ") <td valign=top> Already in your profile" + 
			  "</tr>\n");
	    } else if (report.containsKey(name)) {		
		b.append( "<tr><td valign=top><strong>" +Tools.checkbox(EditUser.CAT_PREFIX + name, 
							     "on", name, true)
			  + "</strong> <td valign=top>(" + cat.desc+ 
			  ") <td valign=top> Suggested because of articles you have uploaded : " +
			  report.why(name) +
			  "</tr>\n");
	    }
	}
	b.append("</table>\n");
	return b.toString();
    }


    /** Checks if this is one of the "abolished" categories, and returns
	the modern substitute, if known. */
    static public String subsumedBy(String s) {
	return subsumed.get(s);
    }

    /** Testing */
    public static void main(String[] argv) {
	for(String full: argv) {
	    Cat major = findMajorCat(full);
	    if (major == null) {
		System.out.println("No major cat found for : " +full);
	    } else {
		System.out.println(full + " --> " +  major.fullName());
	    }

	}
    }


}