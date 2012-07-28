<!-- © 2011 by AEP -->
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.Version" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="org.apache.lucene.search.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
//        ResultsBase main=new ResultsBase(request,response);
  	  ViewSuggestions main=new  ViewSuggestions(request,response,true);
	  SearchResults sr = main.sr;
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>arXiv - Main Page</title>
<!-- #EndEditable -->
<meta name="Keywords" content="" />
<meta name="Description" content="Personalized web interface for arxiv.org, the international open-access archive of scientific articles"/>
<meta name="Author" content="pichugin@eden.rutgers.edu" />
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />

<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_ie.css" /><![endif]-->
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="scripts/blur.js"></script>
<script type="text/javascript" src="scripts/buttons_control.js"></script>


<!-- #BeginEditable "Styles" -->
<style type="text/css">
#left_frame {
	width: 192px;
}
#right_frame {
	left: 202px;
	padding-top: 0px;
	padding-bottom: 0px;
}
.button {
	width: 170px;
}
.address {
	width: 260px;
	margin: 0px auto;
}
.double_address {
	width: 530px;
	margin: 0px auto;
}
h2 {
	margin-top: 10px;
}
.adjust_height {
	min-height: 100%;
}
.duties_1 {
	margin: 0 0 0 50px;
	text-indent: -50px;
}
.duties {
	margin: 0 0 0 50px;
	text-indent: -8px;
}
.icon {
	width: 16px;
	height: 16px;
	margin: 2px 6px -2px 4px;
	border: 0px;
}
</style>
<!--[if IE]>
<style type="text/css">
#left_frame {
	width: 195px;
}
.button {
	width: 175px;
}
#right_frame {
	left: 202px;
	width: expression((document.body.clientWidth -221) + "px");
}
.adjust_height {
	height: expression((document.getElementById('Right_Frame').offsetHeight - 47) + "px");
}
</style>
<![endif]-->
<script type="text/javascript">

function StartScripts() { 
BlurLinks();
}

window.onload = StartScripts;
</script>

<!-- #EndEditable -->

</head>

<body>
<!--
<script type="text/javascript">function Gomenu(){return}</script>
<script type="text/javascript" src="../menu/menu_var.js"></script>
<script type="text/javascript" src="../menu/menu_com.js"></script>
<noscript>Your browser does not support scripts in our web site.</noscript>
-->

<div id="upper_frame">

	<!-- <div id="MenuPos" style="position:absolute; left: 86px; top: 42px; z-index:5000;"></div> -->
	<img src="_technical/images/bar-rutgers.jpg" style="border:none; width:1000px; height:75px; position:absolute; top:0px; left:0px;" alt="" />
	<div style="position:absolute; top:10px; left:10px; font-size:4em; font-weight:bold; text-shadow: 2px 2px 2px #000; right: 813px;"><span style="color:#CCCCCC; font-weight:normal;">my:</span>arXiv</div>
	
	<div style="width:100%; text-align:center; position:relative; top:25px;">
		<form name="simple_search" style="position:relative; text-align:center;" action="search.jsp">
		
			<input type="text" name="simple_search" size="25" id="simple_search" />
			<input type="submit" value="Search" id="search_button_" class="button" style="font-weight:bold; padding:0px; width:75px; height:23px; text-align:center; position:relative; display:inline;" />
			<a href="search_help.jsp">Search help</a>

		</form>
	</div>

	<!-- <img src="../images/face.jpg" style="border:none; position:absolute; left:0px; top:5px;" alt="" /> -->
	<div id="layer_page_title" style="z-index: 10">
		<!-- #BeginEditable "Page_Title" -->

		
		<div id="status" style="position: absolute; right: 65px; top: 2px;">
		  <icd:YouAre/>
		  </div>
		  <!-- this has been moved inside the tag, icd:YouAre -->
		  <!-- div class="button_div" style="position: absolute; right: -8px; top: 0px; width: 65px; height: 15px;">
			<a href="participation.html" target="_self">More Info</a></div -->

		<!-- #EndEditable -->
		</div>
	
	<div id="validator" style="width:3px; height:3px; position:absolute; top:0px; right:0px; color:blue;">
		<a target="_blank" href="http://validator.w3.org/check?uri=referer" style="color:#e51937; background:inherit;">.</a>
	</div>

	<div id="validator_css" style="width:3px; height:3px; position:absolute; top:0px; left:0px;">
		<a target="_blank" href="http://jigsaw.w3.org/css-validator/check/referer?profile=css3" style="color:#e51937; background:inherit;">.</a>
	</div>

</div>


<div id="middle_frame">
	
  <div id="wrapping"> 
    <!-- #BeginEditable "Body" -->	
    
    <h1>arXiv</h1>
    <div id="left_frame">

  <h2>Personalization tools</h2>


   <% if (main.user==null) { %>
  
    <a class="button" href="participation.html""><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Join</a>

      <% } else { %>
    
      <a class="button" href="#sug" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Recommended for you</a>

      <a class="button" href="personal/viewFolder.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Your personal folder</a>

      <a class="button" href="personal/index.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Your account and personalization tools </a>


 <% } %>
 
    <h2>Articles by category</h2>
      <a class="button" href="#physics" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Physics</a>
      <a class="button" href="#math" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Mathematics</a>
      <a class="button" href="#nlin" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Non-linear Sciences</a>
      <a class="button" href="#cs" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Computer Science</a>
      <a class="button" href="#q-bio" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Quantitative Biology</a>
      <a class="button" href="#q-fin" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Quantitative Finance</a>
      <a class="button" href="#stat" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Statistics</a>
      <br />

<!--
      <h2>Personalization tools</h2>

      <% if (main.user==null) { %>
      <div style="text-align:center"><a href="participation.html">Join
</a> <br />
</div>		
      <br /> 
      <% } else { %>


      <div style="text-align:center"><a href="personal/index.jsp">Your account and personalization tools</a><br /> </div>		
      <br /> 
      
      <div style="text-align:center"><a href="personal/viewFolder.jsp">Your personal folder</a><br /> </div>		
      <br /> 
      
      <% }  %>

<!--
      <div style="text-align: center"><a href="interview.html">Interview Page<br /> (do we need this?)</a></div>
      <br />
      
      <div style="text-align: center"><a href="participation_login.html">Login-based Participation Page<br /> (do we need this?)</a></div>
      <br />

-->
      
      <div style="text-align: center">My.ArXiv Version <%=Version.getInfo()%></div>

       <div style="text-align: center"><strong>This project is supported by the National Science Foundation (#NSF IIS-1142251)</strong></div>

    </div>
    <!-- Left Frame -->

    <div id="right_frame" style="padding-top: 0px;">
  <% if (main.user!=null) { 
%>
    
<h2><a name="sug">New articles recommended for you</a></h2>

<% if (main.df == null && !main.onTheFly) {
   %> 
<p>
Presently, no recommendations are available for you.
</p>

<%    if (main.activeTask!=null) {%> 
<p>
Presently, My.Arxiv's recommendation engine is working on 
generating a suggestion list for you (task no. <%= main.activeTask.getId()%>). You may want to wait for a couple of minutes, and then refresh this page to see if the list is ready.</p>
<%      
      } else if (main.queuedTask!=null) {
%>
<p>
Presently,  a task is queued to generate a suggestion list for you (task no. <%= main.queuedTask.getId()%>. You may wait for a few minutes, and then refresh this page to see if it has started and completed.</p>
<% 
} else if (main.actor.catCnt()==0) {
%>
<p>
It appears that you have not specified any subject categories of interest. Please <a href="personal/editUserFormSelf.jsp">modify your user profile</a>, adding some categories!
</p>
<%
} else {
%> 
Perhaps you need to wait for a while for a recommendation list to be generated, and then reload this page.
<%
}%>
 
<%
} else {
 if (main.onTheFly) {
%>
<p>Initial suggestion list generated in runtime.</p>
<%
 } else {
%>


<p>Suggestion list <%=main.df.getThisFile() %> was generated for user
<%=main.df.getUser() %> at: <%=Util.ago(main.df.getTime())%>. 

<p>The list was 
generated by applying the user profile 
<a href="<%=main.viewProfileLink(main.df.getInputFile())%>"><%=main.df.getInputFile()%></a>
to the 
<%= (main.days>0)? "articles from the last " + main.days + " days" :
    "entire article archive (all times)" %>
<p>Merge=<%=main.teamDraft%></p>

<% } %>

<p>The entire list contains at least <%= sr.reportedLength %>
articles; articles ranked from <%= sr.entries.elementAt(0).i %>
through <%= sr.entries.lastElement().i %> are shown below.  </p>

<% 
for( ArticleEntry e: sr.entries) { %>
<%= main.resultsDivHTML(e) %>	
		<% }%>		


<% } %>
<p><small>(<%= main.actor.dayMsg()%>)</small>
<% } %>

<h2><a name="physics">Physics</a></h2>
<ul>
<li><a href="FilterServlet/archive/astro-ph">Astrophysics</a> (<b>astro-ph</b> <a href="FilterServlet/list/astro-ph/new">new</a>, <a href="FilterServlet/list/astro-ph/recent">recent</a>, <a href="FilterServlet/find/astro-ph">find</a>)<br />
includes: <a href="FilterServlet/list/astro-ph.CO/recent">Cosmology and Extragalactic Astrophysics</a>; <a href="FilterServlet/list/astro-ph.EP/recent">Earth and Planetary Astrophysics</a>; <a href="FilterServlet/list/astro-ph.GA/recent">Galaxy Astrophysics</a>; <a href="FilterServlet/list/astro-ph.HE/recent">High Energy Astrophysical Phenomena</a>; <a href="FilterServlet/list/astro-ph.IM/recent">Instrumentation and Methods for Astrophysics</a>; <a href="FilterServlet/list/astro-ph.SR/recent">Solar and Stellar Astrophysics</a></li>

<li><a href="FilterServlet/archive/cond-mat">Condensed Matter</a> (<b>cond-mat</b> <a href="FilterServlet/list/cond-mat/new">new</a>, <a href="FilterServlet/list/cond-mat/recent">recent</a>, <a href="FilterServlet/find/cond-mat">find</a>)<br />
includes: <a href="FilterServlet/list/cond-mat.dis-nn/recent">Disordered Systems and Neural Networks</a>; <a href="FilterServlet/list/cond-mat.mtrl-sci/recent">Materials Science</a>; <a href="FilterServlet/list/cond-mat.mes-hall/recent">Mesoscale and Nanoscale Physics</a>; <a href="FilterServlet/list/cond-mat.other/recent">Other Condensed Matter</a>; <a href="FilterServlet/list/cond-mat.quant-gas/recent">Quantum Gases</a>; <a href="FilterServlet/list/cond-mat.soft/recent">Soft Condensed Matter</a>; <a href="FilterServlet/list/cond-mat.stat-mech/recent">Statistical Mechanics</a>; <a href="FilterServlet/list/cond-mat.str-el/recent">Strongly Correlated Electrons</a>; <a href="FilterServlet/list/cond-mat.supr-con/recent">Superconductivity</a></li>

<li><a href="FilterServlet/archive/gr-qc">General Relativity and Quantum Cosmology</a> (<b>gr-qc</b> <a href="FilterServlet/list/gr-qc/new">new</a>, <a href="FilterServlet/list/gr-qc/recent">recent</a>, <a href="FilterServlet/find/gr-qc">find</a>)</li>
<li><a href="FilterServlet/archive/hep-ex">High Energy Physics - Experiment</a> (<b>hep-ex</b> <a href="FilterServlet/list/hep-ex/new">new</a>, <a href="FilterServlet/list/hep-ex/recent">recent</a>, <a href="FilterServlet/find/hep-ex">find</a>)</li>

<li><a href="FilterServlet/archive/hep-lat">High Energy Physics - Lattice</a> (<b>hep-lat</b> <a href="FilterServlet/list/hep-lat/new">new</a>, <a href="FilterServlet/list/hep-lat/recent">recent</a>, <a href="FilterServlet/find/hep-lat">find</a>)</li>
<li><a href="FilterServlet/archive/hep-ph">High Energy Physics - Phenomenology</a> (<b>hep-ph</b> <a href="FilterServlet/list/hep-ph/new">new</a>, <a href="FilterServlet/list/hep-ph/recent">recent</a>, <a href="FilterServlet/find/hep-ph">find</a>)</li>

<li><a href="FilterServlet/archive/hep-th">High Energy Physics - Theory</a> (<b>hep-th</b> <a href="FilterServlet/list/hep-th/new">new</a>, <a href="FilterServlet/list/hep-th/recent">recent</a>, <a href="FilterServlet/find/hep-th">find</a>)</li>
<li><a href="FilterServlet/archive/math-ph">Mathematical Physics</a> (<b>math-ph</b> <a href="FilterServlet/list/math-ph/new">new</a>, <a href="FilterServlet/list/math-ph/recent">recent</a>, <a href="FilterServlet/find/math-ph">find</a>)</li>

<li><a href="FilterServlet/archive/nucl-ex">Nuclear Experiment</a> (<b>nucl-ex</b> <a href="FilterServlet/list/nucl-ex/new">new</a>, <a href="FilterServlet/list/nucl-ex/recent">recent</a>, <a href="FilterServlet/find/nucl-ex">find</a>)</li>
<li><a href="FilterServlet/archive/nucl-th">Nuclear Theory</a> (<b>nucl-th</b> <a href="FilterServlet/list/nucl-th/new">new</a>, <a href="FilterServlet/list/nucl-th/recent">recent</a>, <a href="FilterServlet/find/nucl-th">find</a>)</li>

<li><a href="FilterServlet/archive/physics">Physics</a> (<b>physics</b> <a href="FilterServlet/list/physics/new">new</a>, <a href="FilterServlet/list/physics/recent">recent</a>, <a href="FilterServlet/find/physics">find</a>)<br />
includes: <a href="FilterServlet/list/physics.acc-ph/recent">Accelerator Physics</a>; <a href="FilterServlet/list/physics.ao-ph/recent">Atmospheric and Oceanic Physics</a>; <a href="FilterServlet/list/physics.atom-ph/recent">Atomic Physics</a>; <a href="FilterServlet/list/physics.atm-clus/recent">Atomic and Molecular Clusters</a>; <a href="FilterServlet/list/physics.bio-ph/recent">Biological Physics</a>; <a href="FilterServlet/list/physics.chem-ph/recent">Chemical Physics</a>; <a href="FilterServlet/list/physics.class-ph/recent">Classical Physics</a>; <a href="FilterServlet/list/physics.comp-ph/recent">Computational Physics</a>; <a href="FilterServlet/list/physics.data-an/recent">Data Analysis, Statistics and Probability</a>; <a href="FilterServlet/list/physics.flu-dyn/recent">Fluid Dynamics</a>; <a href="FilterServlet/list/physics.gen-ph/recent">General Physics</a>; <a href="FilterServlet/list/physics.geo-ph/recent">Geophysics</a>; <a href="FilterServlet/list/physics.hist-ph/recent">History and Philosophy of Physics</a>; <a href="FilterServlet/list/physics.ins-det/recent">Instrumentation and Detectors</a>; <a href="FilterServlet/list/physics.med-ph/recent">Medical Physics</a>; <a href="FilterServlet/list/physics.optics/recent">Optics</a>; <a href="FilterServlet/list/physics.ed-ph/recent">Physics Education</a>; <a href="FilterServlet/list/physics.soc-ph/recent">Physics and Society</a>; <a href="FilterServlet/list/physics.plasm-ph/recent">Plasma Physics</a>; <a href="FilterServlet/list/physics.pop-ph/recent">Popular Physics</a>; <a href="FilterServlet/list/physics.space-ph/recent">Space Physics</a></li>

<li><a href="FilterServlet/archive/quant-ph">Quantum Physics</a> (<b>quant-ph</b> <a href="FilterServlet/list/quant-ph/new">new</a>, <a href="FilterServlet/list/quant-ph/recent">recent</a>, <a href="FilterServlet/find/quant-ph">find</a>)</li>
</ul>
<h2><a name="math">Mathematics</a></h2>
<ul>
<li><a href="FilterServlet/archive/math">Mathematics</a> (<b>math</b> <a href="FilterServlet/list/math/new">new</a>, <a href="FilterServlet/list/math/recent">recent</a>, <a href="FilterServlet/find/math">find</a>)<br />

includes (see <a href="FilterServlet/new/math.html">detailed description</a>): <a href="FilterServlet/list/math.AG/recent">Algebraic Geometry</a>; <a href="FilterServlet/list/math.AT/recent">Algebraic Topology</a>; <a href="FilterServlet/list/math.AP/recent">Analysis of PDEs</a>; <a href="FilterServlet/list/math.CT/recent">Category Theory</a>; <a href="FilterServlet/list/math.CA/recent">Classical Analysis and ODEs</a>; <a href="FilterServlet/list/math.CO/recent">Combinatorics</a>; <a href="FilterServlet/list/math.AC/recent">Commutative Algebra</a>; <a href="FilterServlet/list/math.CV/recent">Complex Variables</a>; <a href="FilterServlet/list/math.DG/recent">Differential Geometry</a>; <a href="FilterServlet/list/math.DS/recent">Dynamical Systems</a>; <a href="FilterServlet/list/math.FA/recent">Functional Analysis</a>; <a href="FilterServlet/list/math.GM/recent">General Mathematics</a>; <a href="FilterServlet/list/math.GN/recent">General Topology</a>; <a href="FilterServlet/list/math.GT/recent">Geometric Topology</a>; <a href="FilterServlet/list/math.GR/recent">Group Theory</a>; <a href="FilterServlet/list/math.HO/recent">History and Overview</a>; <a href="FilterServlet/list/math.IT/recent">Information Theory</a>; <a href="FilterServlet/list/math.KT/recent">K-Theory and Homology</a>; <a href="FilterServlet/list/math.LO/recent">Logic</a>; <a href="FilterServlet/list/math.MP/recent">Mathematical Physics</a>; <a href="FilterServlet/list/math.MG/recent">Metric Geometry</a>; <a href="FilterServlet/list/math.NT/recent">Number Theory</a>; <a href="FilterServlet/list/math.NA/recent">Numerical Analysis</a>; <a href="FilterServlet/list/math.OA/recent">Operator Algebras</a>; <a href="FilterServlet/list/math.OC/recent">Optimization and Control</a>; <a href="FilterServlet/list/math.PR/recent">Probability</a>; <a href="FilterServlet/list/math.QA/recent">Quantum Algebra</a>; <a href="FilterServlet/list/math.RT/recent">Representation Theory</a>; <a href="FilterServlet/list/math.RA/recent">Rings and Algebras</a>; <a href="FilterServlet/list/math.SP/recent">Spectral Theory</a>; <a href="FilterServlet/list/math.ST/recent">Statistics Theory</a>; <a href="FilterServlet/list/math.SG/recent">Symplectic Geometry</a></li>

</ul>
<h2><a name="nlin">Nonlinear Sciences</a></h2>
<ul>
<li><a href="FilterServlet/archive/nlin">Nonlinear Sciences</a> (<b>nlin</b> <a href="FilterServlet/list/nlin/new">new</a>, <a href="FilterServlet/list/nlin/recent">recent</a>, <a href="FilterServlet/find/nlin">find</a>)<br />
includes (see <a href="FilterServlet/new/nlin.html">detailed description</a>): <a href="FilterServlet/list/nlin.AO/recent">Adaptation and Self-Organizing Systems</a>; <a href="FilterServlet/list/nlin.CG/recent">Cellular Automata and Lattice Gases</a>; <a href="FilterServlet/list/nlin.CD/recent">Chaotic Dynamics</a>; <a href="FilterServlet/list/nlin.SI/recent">Exactly Solvable and Integrable Systems</a>; <a href="FilterServlet/list/nlin.PS/recent">Pattern Formation and Solitons</a></li>

</ul>
<h2><a name="cs">Computer Science</a></h2>
<ul>
<li><a href="FilterServlet/corr">Computing Research Repository</a> (<b>CoRR</b> <a href="FilterServlet/list/cs/new">new</a>, <a href="FilterServlet/list/cs/recent">recent</a>, <a href="FilterServlet/find/cs">find</a>)<br />
includes (see <a href="FilterServlet/corr/subjectclasses">detailed description</a>): <a href="FilterServlet/list/cs.AI/recent">Artificial Intelligence</a>; <a href="FilterServlet/list/cs.CL/recent">Computation and Language</a>; <a href="FilterServlet/list/cs.CC/recent">Computational Complexity</a>; <a href="FilterServlet/list/cs.CE/recent">Computational Engineering, Finance, and Science</a>; <a href="FilterServlet/list/cs.CG/recent">Computational Geometry</a>; <a href="FilterServlet/list/cs.GT/recent">Computer Science and Game Theory</a>; <a href="FilterServlet/list/cs.CV/recent">Computer Vision and Pattern Recognition</a>; <a href="FilterServlet/list/cs.CY/recent">Computers and Society</a>; <a href="FilterServlet/list/cs.CR/recent">Cryptography and Security</a>; <a href="FilterServlet/list/cs.DS/recent">Data Structures and Algorithms</a>; <a href="FilterServlet/list/cs.DB/recent">Databases</a>; <a href="FilterServlet/list/cs.DL/recent">Digital Libraries</a>; <a href="FilterServlet/list/cs.DM/recent">Discrete Mathematics</a>; <a href="FilterServlet/list/cs.DC/recent">Distributed, Parallel, and Cluster Computing</a>; <a href="FilterServlet/list/cs.ET/recent">Emerging Technologies</a>; <a href="FilterServlet/list/cs.FL/recent">Formal Languages and Automata Theory</a>; <a href="FilterServlet/list/cs.GL/recent">General Literature</a>; <a href="FilterServlet/list/cs.GR/recent">Graphics</a>; <a href="FilterServlet/list/cs.AR/recent">Hardware Architecture</a>; <a href="FilterServlet/list/cs.HC/recent">Human-Computer Interaction</a>; <a href="FilterServlet/list/cs.IR/recent">Information Retrieval</a>; <a href="FilterServlet/list/cs.IT/recent">Information Theory</a>; <a href="FilterServlet/list/cs.LG/recent">Learning</a>; <a href="FilterServlet/list/cs.LO/recent">Logic in Computer Science</a>; <a href="FilterServlet/list/cs.MS/recent">Mathematical Software</a>; <a href="FilterServlet/list/cs.MA/recent">Multiagent Systems</a>; <a href="FilterServlet/list/cs.MM/recent">Multimedia</a>; <a href="FilterServlet/list/cs.NI/recent">Networking and Internet Architecture</a>; <a href="FilterServlet/list/cs.NE/recent">Neural and Evolutionary Computing</a>; <a href="FilterServlet/list/cs.NA/recent">Numerical Analysis</a>; <a href="FilterServlet/list/cs.OS/recent">Operating Systems</a>; <a href="FilterServlet/list/cs.OH/recent">Other Computer Science</a>; <a href="FilterServlet/list/cs.PF/recent">Performance</a>; <a href="FilterServlet/list/cs.PL/recent">Programming Languages</a>; <a href="FilterServlet/list/cs.RO/recent">Robotics</a>; <a href="FilterServlet/list/cs.SI/recent">Social and Information Networks</a>; <a href="FilterServlet/list/cs.SE/recent">Software Engineering</a>; <a href="FilterServlet/list/cs.SD/recent">Sound</a>; <a href="FilterServlet/list/cs.SC/recent">Symbolic Computation</a>; <a href="FilterServlet/list/cs.SY/recent">Systems and Control</a></li>

</ul>
<h2><a name="q-bio">Quantitative Biology</a></h2>
<ul>
<li><a href="FilterServlet/archive/q-bio">Quantitative Biology</a> (<b>q-bio</b> <a href="FilterServlet/list/q-bio/new">new</a>, <a href="FilterServlet/list/q-bio/recent">recent</a>, <a href="FilterServlet/find/q-bio">find</a>)<br />
includes (see <a href="FilterServlet/new/q-bio.html">detailed description</a>): <a href="FilterServlet/list/q-bio.BM/recent">Biomolecules</a>; <a href="FilterServlet/list/q-bio.CB/recent">Cell Behavior</a>; <a href="FilterServlet/list/q-bio.GN/recent">Genomics</a>; <a href="FilterServlet/list/q-bio.MN/recent">Molecular Networks</a>; <a href="FilterServlet/list/q-bio.NC/recent">Neurons and Cognition</a>; <a href="FilterServlet/list/q-bio.OT/recent">Other Quantitative Biology</a>; <a href="FilterServlet/list/q-bio.PE/recent">Populations and Evolution</a>; <a href="FilterServlet/list/q-bio.QM/recent">Quantitative Methods</a>; <a href="FilterServlet/list/q-bio.SC/recent">Subcellular Processes</a>; <a href="FilterServlet/list/q-bio.TO/recent">Tissues and Organs</a></li>

</ul>
<h2><a name="q-fin">Quantitative Finance</a></h2>
<ul>
<li><a href="FilterServlet/archive/q-fin">Quantitative Finance</a> (<b>q-fin</b> <a href="FilterServlet/list/q-fin/new">new</a>, <a href="FilterServlet/list/q-fin/recent">recent</a>, <a href="FilterServlet/find/q-fin">find</a>)<br />
includes (see <a href="FilterServlet/new/q-fin.html">detailed description</a>): <a href="FilterServlet/list/q-fin.CP/recent">Computational Finance</a>; <a href="FilterServlet/list/q-fin.GN/recent">General Finance</a>; <a href="FilterServlet/list/q-fin.PM/recent">Portfolio Management</a>; <a href="FilterServlet/list/q-fin.PR/recent">Pricing of Securities</a>; <a href="FilterServlet/list/q-fin.RM/recent">Risk Management</a>; <a href="FilterServlet/list/q-fin.ST/recent">Statistical Finance</a>; <a href="FilterServlet/list/q-fin.TR/recent">Trading and Market Microstructure</a></li>

</ul>
<h2><a name="stat">Statistics</a></h2>
<ul>
<li><a href="FilterServlet/archive/stat">Statistics</a> (<b>stat</b> <a href="FilterServlet/list/stat/new">new</a>, <a href="FilterServlet/list/stat/recent">recent</a>, <a href="FilterServlet/find/stat">find</a>)<br />
includes (see <a href="FilterServlet/new/stat.html">detailed description</a>): <a href="FilterServlet/list/stat.AP/recent">Applications</a>; <a href="FilterServlet/list/stat.CO/recent">Computation</a>; <a href="FilterServlet/list/stat.ML/recent">Machine Learning</a>; <a href="FilterServlet/list/stat.ME/recent">Methodology</a>; <a href="FilterServlet/list/stat.OT/recent">Other Statistics</a>; <a href="FilterServlet/list/stat.TH/recent">Statistics Theory</a></li>

</ul>

		</div>
		<!-- Right Frame -->
	
		<!-- #EndEditable -->	
	</div> <!-- Wrapping -->
	
	</div> <!-- Middle frame ends-->

<div id="lower_frame">
	
</div> <!-- Lower frame ends -->		

<!-- #BeginEditable "Scripts" -->
<!-- #EndEditable -->

</body>

<!-- #EndTemplate -->

</html>
