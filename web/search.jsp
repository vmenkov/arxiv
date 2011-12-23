<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.Action" %>
<%@ page import="org.apache.lucene.search.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	Search main=new Search(request,response);
	Search.SearchResults sr = main.sr;
%>
<!-- © 2011 by AEP -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>arXiv - Main Page</title>
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
		<form name="simple_search" style="position:relative; text-align:center;" action="">
		
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
		</p>
		<p>Searched the collection of <%= sr.numdocs %> documents; found <%= sr.atleast %> 
		<%= sr.reportedLength %> matching documents.
		</p>

		<% for( ArticleEntry e: sr.entries) { %>

		<div class="result" id="result<%=e.i%>">
			<div class="document">
			<%= e.i %>. <%= e.idline %> 
[<a href="<%=main.urlAbstract(e.id)%>">Abstract</a>]
[<a href="<%=main.urlPDF(e.id)%>">PDF/PS/etc</a>]
 <br />
			<%= e.titline %><br />
			<%= e.authline %><br />
			<% if (!e.commline.equals("")) { %>
			<%= e.commline %><br />  
			<%}%>
			<%= e.subjline %><br />
			</div>

			<% if (main.user!=null) { %>				
			<div class="bar_instructions">	

			<% if (e.isInFolder) { %>
			(Already in your folder)
			<% } else { %>
			<a class="add" id="add<%=e.i%>" href="#" 
title="Copy this document to your personal folder"
onclick="$.get('<%=e.judge(Action.Op.COPY_TO_MY_FOLDER)%>',
function(data) { $('#add<%=e.i%>').replaceWith('Copied to your folder!');} )"
><img src="_technical/images/folder_page.png" class="icon_instruction">&nbsp;Copy&nbsp;to&nbsp;my&nbsp;folder</a>&nbsp;&nbsp;
			<% }  %>

			<a id="rate<%=e.i%>" href="#" title="Rate this document."
onclick="$(this).hide(100);    $('#ratings<%=e.i%>').show(500);"
><img longdesc="Rate this document." src="_technical/images/page_question.png" class="icon_instruction">&nbsp;Rate</a>			
			<span id="ratings<%=e.i%>" style="display: none;">
				<a class="interesting" href="#" title="Mark this document as interesting, relevant, and new"
onclick="$.get('<%=e.judge(Action.Op.INTERESTING_AND_NEW)%>');"
><img  longdesc="Mark this document as interesting, relevant, and new." src="_technical/images/page_up.png" class="icon_instruction">&nbsp;Interesting&nbsp;&amp;&nbsp;new</a>&nbsp;&nbsp;
				<a class="seen_today" href="#" title="Mark this if you have already seen a similar interesting and relevant document during this search session."
onclick="$.get('<%=e.judge(Action.Op.INTERESTING_BUT_SEEN_TODAY)%>');"
><img  longdesc="Mark this if you have already seen a similar interesting and relevant document during this search session." src="_technical/images/pages.png" class="icon_instruction">&nbsp;Interesting, but seen today</a>&nbsp;&nbsp;
				<a class="known" href="#" title="Mark this if document is interesting, but contains known information."
onclick="$.get('<%=e.judge(Action.Op.INTERESTING_BUT_KNOWN)%>');"
><img alt="Mark this if document is interesting, but contains known information." longdesc="Mark this if document is interesting, but contains known information" src="_technical/images/page_ok.png" class="icon_instruction">&nbsp;Interesting,&nbsp;but&nbsp;known</a>&nbsp;&nbsp;
				<a class="useless" href="#" title="Mark this document as useles or irrelevant for you."
onclick="$.get('<%=e.judge(Action.Op.USELESS)%>');"
><img alt="Mark this document as useles or irrelevant for you" longdesc="Mark this document as useles or irrelevant for you" src="_technical/images/page_down.png" class="icon_instruction">&nbsp;Useless&nbsp;/&nbsp;Irrelevant</a>&nbsp;&nbsp;
			</span>

				<a class="remove" id="remove<%=e.i%>"
href="#" title="Permanently remove this document from the search results"
onclick="$.get('<%=e.judge(Action.Op.DONT_SHOW_AGAIN)%>', 
function(data) { $('#result<%=e.i%>').hide(100);} )"
><img alt="Permanently remove this document from the search results" longdesc="Permanently remove this document from the search results" src="_technical/images/bin.png" class="icon_instruction">&nbsp;Don't&nbsp;show&nbsp;again</a>&nbsp;&nbsp;

			</div>
			<% }  %>	<!-- main.user!=null -->
		</div>
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
	</div> <!-- Wrapping -->

<% }     %>	
	</div> <!-- Middle frame ends-->

<div id="lower_frame">
	
</div> <!-- Lower frame ends -->		

<!-- #BeginEditable "Scripts" -->
<!-- #EndEditable -->

</body>

<!-- #EndTemplate -->

</html>
