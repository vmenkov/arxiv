<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.html.*" %>
<%@ page import="edu.rutgers.axs.sql.Action" %>
<%@ page import="org.apache.lucene.search.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	Search main=new Search(request,response);
	SearchResults sr = main.sr;
%>
<!-- © 2011 by AEP -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>My.arXiv - Search results</title>
<!-- #EndEditable -->
<meta name="Keywords" content="" />
<meta name="Description" content="Results of a search against the local copy of the arxiv.org database" />
<meta name="Author" content="pichugin@eden.rutgers.edu" />
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />

<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_ie.css" /><![endif]-->
<link rel="stylesheet" type="text/css" href="styles/results.css" />
<!--[if IE]>
<link rel="stylesheet" type="text/css" href="styles/results_ie.css" />
<![endif]-->
<link rel="icon" type="image/x-icon" href="favicon.ico" />

<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="scripts/blur.js"></script>
<script type="text/javascript" src="scripts/buttons_control.js"></script>
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
	<div style="position:absolute; top:10px; left:10px; font-size:4em; font-weight:bold; text-shadow: 2px 2px 2px #000; right: 813px;"><a href="index.jsp"><span style="color:#CCCCCC; font-weight:normal;">my:</span><span style="color:#FFFFFF">arXiv</span></a></div>
	
	<div style="width:100%; text-align:center; position:relative; top:25px;">
		<form name="simple_search" style="position:relative; text-align:center;" action="">
		
			<input type="text" name="simple_search" size="25" id="simple_search" value="<%=main.query%>" />
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

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
	
		<div id="wrapping"> 
		<!-- #BeginEditable "Body" -->	
	
		<h1>Search Results</h1>
		
		<p>Query: <%= main.query %>
		<br>Lucene query: <%= sr.reportedLuceneQuery%>
		</p>
		<p>Searched the collection of <%= sr.numdocs %> documents; found <%= sr.atleast %> 
		<%= sr.reportedLength %> matching documents.
		</p>

		<% for( ArticleEntry e: sr.entries) { %>
<%= main.resultsDivHTML(e,main.user!=null ) %>	
		<% }%>		

		<div> <!-- Links to prev/next pages, if necessary -->
	<% if (sr.needPrev) {	%> 
	<a href="search.jsp?simple_search=<%=main.queryEncoded%>&startat=<%=sr.prevstart%>">[PREV PAGE]</a> 
	<% }	
	 if (sr.needNext) { %>	
	<a href="search.jsp?simple_search=<%=main.queryEncoded%>&startat=<%=sr.nextstart%>">[NEXT PAGE]</a> 	 
	<% }	 %>
		</div>
		
		<% if (sr.excludedEntries.size()>0) { %>
		<div><small>We have excluded <%=sr.excludedEntries.size()%> articles from the list, because you have earlier asked not to show them anymore.</small></div>
		<% } %>

		<div><small>System message: <%= main.infomsg%> </small></div>

		<!-- #EndEditable -->	
<icd:RU/>
	</div> <!-- Wrapping -->

<% }     %>	


	</div> <!-- Middle frame ends-->

<!-- <div id="lower_frame"> -->
	
<!-- /div --> <!-- Lower frame ends -->		

<!-- #BeginEditable "Scripts" -->
<!-- #EndEditable -->

</body>

<!-- #EndTemplate -->

</html>
