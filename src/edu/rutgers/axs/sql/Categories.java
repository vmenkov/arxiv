package edu.rutgers.axs.sql;

import java.util.*;

import edu.rutgers.axs.web.EditUser;
import edu.rutgers.axs.web.Tools;


public class Categories {

    static class CategoryException extends Exception {
	CategoryException(String s) { super(s);}
    }


    public static class Cat {
	/** Short name */
	public String name;
	/** Long human-readable name */
	public String desc;
	public Vector<Cat> subcats = new Vector<Cat>();	
	private Cat(String _name, String _desc) {
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

    }

    static public Vector<Cat> majors = new Vector<Cat>();
    static HashMap<String,Cat> majorMap=new HashMap<String,Cat>();

    private static void addMajor(String name, String desc) throws CategoryException{
	if (majorMap.containsKey(name)) {
	    throw new CategoryException("Major category '"+name+"' already exists");
	}
	Cat c = new Cat(name,desc);
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
	major.subcats.add(new Cat(name, desc));

    }

    private static void init()  throws CategoryException{
addMajor("astro-ph","Astrophysics");
addMajor("acc-phys","Accelerator Physics");
addMajor("cond-mat","Condensed Matter");
addMajor("chem-ph","Chemical Physics");
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
addMajor("ao-sci","Atmospheric-Oceanic Sciences");
addMajor("atom-ph","Atomic, Molecular and Optical Physics");
addMajor("bayes-an","Bayesian Analysis");
addMajor("mtrl-th","Materials Theory");
addMajor("plasm-ph","Plasma Physics");
addMajor("supr-con","Superconductivity");
addMajor("math","Mathematics");
addMajor("alg-geom","Algebraic Geometry");
addMajor("dg-ga","Differential Geometry");
addMajor("funct-an","Functional Analysis");
addMajor("q-alg","Quantum Algebra and Topology");
addMajor("cs","Computer Science");
addMajor("cmp-lg","Computation and Language");
addMajor("nlin","Nonlinear Sciences");
addMajor("adap-org","Adaptation, Noise, and Self-Organizing Systems");
addMajor("chao-dyn","Chaotic Dynamics");
addMajor("comp-gas","Cellular Automata and Lattice Gases");
addMajor("patt-sol","Pattern Formation and Solitons");
addMajor("solv-int","Exactly Solvable and Integrable Systems");
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
addMinor("q-fin","GN","General Finance");
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

    /** Creates set of radio buttons reflecting the ArXiv categories
    the specified user is interested in. 


    @param u A user entry. If null, then no boxes will be checked . */
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


}