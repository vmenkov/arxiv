<!-- © 2011 by AEP -->
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.Version" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>arXiv - Main Page</title>
<!-- #EndEditable -->
<meta name="Keywords" content="" />
<meta name="Description" content="Here goes the description of arXiv for search engines" />
<meta name="Author" content="pichugin@eden.rutgers.edu" />
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />

<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_ie.css" /><![endif]-->

<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script>

<script type="text/javascript">
<!--
function BlurLinks(){
lnks=document.getElementsByTagName('a');
for(i=0;i<lnks.length;i++){
lnks[i].onfocus=new Function("if(this.blur)this.blur()");
}
}
//-->
</script>

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
    
    <h1>arXiv Research</h1>
    <div id="left_frame">
      <a class="button" href="#physics" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Physics</a>
      <a class="button" href="#mathematics" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Mathematics</a>
      <a class="button" href="#non-linear_sciences" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Non-linear Sciences</a>
      <a class="button" href="#computer_science" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Computer Science</a>
      <a class="button" href="#quantitative_biology" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Quantitative Biology</a>
      <a class="button" href="#quantitative_finance" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Quantitative Finance</a>
      <a class="button" href="#statistics" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Statistics</a>
      <br />
      <a class="button" href="participation.html" target="_self" style="text-align:center"><span style="color:#ee0000; font-weight:bold">Archive Research</span></a>
      
      <br />

      <div style="text-align:center"><a href="results.html">Results Page<br />(Temporary link)</a></div>
			
      <br />
      
      <div style="text-align: center"><a href="interview.html">Interview Page<br /> (Temporary link)</a></div>
      <br />
      
      <div style="text-align: center"><a href="participation_login.html">Login-based Participation Page<br /> (Temporary link)</a></div>
      <br />
      
      <div style="text-align: center">Other Links Here - ver <%=Version.version%></div>
    </div>
    <!-- Left Frame -->

    <div id="right_frame" style="padding-top: 0px;">
      <p class="white_dot" style="margin: 0px;"><a name="Physics"></a>.</p>
      <h2>Physics</h2>
      <p>Astrophysics (astro-ph new, recent, find)</p>
      <p>includes: Cosmology and Extragalactic Astrophysics; Earth and Planetary 
	Astrophysics; Galaxy Astrophysics; High Energy Astrophysical Phenomena; 
	Instrumentation and Methods for Astrophysics; Solar and Stellar Astrophysics</p>
      <p>Condensed Matter (cond-mat new, recent, find)</p>
			Etc. (Or as a table) <span style="font-size: 1px;">
	<br clear="all" />
      </span>
      <p class="white_dot" style="margin: 0px;"><a name="mathematics"></a>
	.</p>
      <h2>Mathematics</h2>
			<p>Sim.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<span style="font-size: 1px;"><br clear="all" />
			</span>
			<p class="white_dot" style="margin: 0px;">
			<a name="non-linear_sciences"></a>.</p>
			<h2>Non-linear Sciences</h2>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<span style="font-size: 1px;"><br clear="all" />
			</span>
			<p class="white_dot" style="margin: 0px;">
			<a name="computer_science"></a>.</p>
			<h2>Computer Science</h2>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<span style="font-size: 1px;"><br clear="all" />
			</span>
			<p class="white_dot" style="margin: 0px;">
			<a name="quantitative_biology"></a>.</p>
			<h2>Quantitative Biology</h2>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<span style="font-size: 1px;"><br clear="all" />
			</span>
			<p class="white_dot" style="margin: 0px;">
			<a name="quantitative_finance"></a>.</p>
			<h2>Quantitative Finance</h2>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<p>.</p>
			<span style="font-size: 1px;"><br clear="all" />
			</span>
			<p class="White_Dot" style="margin: 0px;"><a name="statistics"></a>.</p>
			<h2>Statistics</h2>
			<div class="adjust_height">
				<p>.</p>
				<p>.</p>
				<p>.</p>
				<p>.</p>
				<p>.</p>
				<p>.</p>
				<p>.</p>
				<p>.</p>
				<p>.</p>
			</div>
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
