<%@page contentType="text/html; charset=UTF-8" %> 
<!-- © 2011 by AEP -->
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.Version" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="org.apache.lucene.search.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	  ViewSuggestions mainA=new  ViewSuggestions(request,response,true);
	  BernoulliViewSuggestions mainB = (mainA.needBernoulliInstead ?
	     new  BernoulliViewSuggestions(request,response) : null);
  	  PersonalResultsBase main =  (mainA.needBernoulliInstead ? mainB : mainA);
	  
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

<link rel="icon" type="image/x-icon" href="favicon.ico" />
<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="scripts/blur.js"></script>
<script type="text/javascript" src="scripts/buttons_control.js"></script>


<!-- #BeginEditable "Styles" -->
<link rel="stylesheet" type="text/css" href="styles/index.css" />
<!-- ***  [if IE]>
<link rel="stylesheet" type="text/css" href="styles/index-ie.css" />
<[endif] ***  -->
<script type="text/javascript">

function StartScripts() { 
BlurLinks();
}

window.onload = StartScripts;
</script>

<!-- #EndEditable -->
</head>

<body>

<div id="upper_frame">

	<!-- <div id="MenuPos" style="position:absolute; left: 86px; top: 42px; z-index:5000;"></div> -->
	<img src="_technical/images/bar-rutgers.jpg" style="border:none; width:100%; height:75px; position:absolute; top:0px; left:0px;" alt="" />
	<div style="position:absolute; top:10px; left:10px; font-size:4em; font-weight:bold; text-shadow: 2px 2px 2px #000; right: 813px;"><a href="index.jsp"><span style="color:#CCCCCC; font-weight:normal;">my:</span><span style="color:#FFFFFF">arXiv</span></a></div>
	
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
	
		<!-- #EndEditable -->
		</div>
	
	<div id="validator" style="width:3px; height:3px; position:absolute; top:0px; right:0px; color:blue;">
		<a target="_blank" href="http://validator.w3.org/check?uri=referer" style="color:#e51937; background:inherit;">.</a>
	</div>

	<div id="validator_css" style="width:3px; height:3px; position:absolute; top:0px; left:0px;">
		<a target="_blank" href="http://jigsaw.w3.org/css-validator/check/referer?profile=css3" style="color:#e51937; background:inherit;">.</a>
	</div>

</div>


<!-- div id="middle_frame" -->
	
  <!-- div id="wrapping" --> 
    <!-- #BeginEditable "Body" -->	
    
    <!--h1>arXiv</h1-->
    <div id="left_frame">

  <h2>Personalization tools</h2>


   <% if (main.user==null) { %>
  
    <a class="button" href="participation.jsp""><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Join</a>

      <% } else { %>
    
      <a class="button" href="#sug" target="_self"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Recommended for you</a>

      <a class="button" href="personal/viewFolder.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Your personal folder</a>

     <a class="button" href="personal/viewActionsSelfDetailed.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Past activity</a>

      <a class="button" href="personal/editUserFormSelf.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Your account settings</a>
<!--      
     <a class="button" href="personal/index.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>All personalization tools</a> -->
<%  if (main.runByResearcher()) {%>
	<span class="researcher">
    <a href="tools/"><span>&nbsp;&diams;&nbsp;</span>Research tools (staff only)</a>
	</span>
 <% }
 } %>
 
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
      <div style="text-align:center"><a href="participation.jsp">Join
</a> <br />
</div>		
      <br /> 
      <% } else if (main.user!=null) { %>


      <div style="text-align:center"><a href="personal/index.jsp">Your account and personalization tools</a><br /> </div>		
      <br /> 
      
      <div style="text-align:center"><a href="personal/viewFolder.jsp">Your personal folder</a><br /> </div>		
      <br /> 
      
      <% }  %>

-->
      
      <div style="text-align: center">My.ArXiv Version <%=Version.getInfo()%></div>

       <div style="text-align: center"><strong>This project is supported by the National Science Foundation (#NSF IIS-1142251)</strong></div>

    </div>
    <!-- Left Frame -->

    <div id="right_frame">
      <% if (main.error) { %>
<!-- <p>Error message: <%= main.errmsg %></p> -->
      <% if (main.e!=null) {
      StringWriter sw=new StringWriter();
      main.e.printStackTrace(new PrintWriter(sw));
 %>
<!--     <p><pre>
Exception: <%= main.e %>
<%= sw %>
</pre></p> -->
      <% }} else if (main.user!=null && mainB==null) { 
     	  DataFile df = mainA.df;
  	  SearchResults sr = mainA.sr;
%>
    
<h2><a name="sug">New articles recommended for you</a></h2>

<% if (mainA.noList()) {   %> 
   <%= mainA.noListMsg() %>
<% } else { %>
   <%= mainA.describeList() %>
<% 
for( ArticleEntry e: sr.entries) { %><%= main.resultsDivHTML(e) %><% }%>
<%= main.prevNextLinks(sr) %> 
<%= mainA.excludedPagesMsg() %>
<%= main.researcherP( main.actor.dayMsg()) %>
<%    }
} else if (mainB!=null) { 
  	  SearchResults sr = mainB.sr;
%> 
  
<h2><a name="sug">Recent articles recommended for you</a></h2>

<p>Suggestion list generated by the Exploration Engine.</p>

<p>The entire list contains <%= sr.atleast%>  <%= sr.reportedLength %>
articles.
<% if (sr.entries.size()==0) { %> There is nothing to display.<% } else { %>
 Articles ranked from <%= sr.entries.elementAt(0).i %>
through <%= sr.entries.lastElement().i %> are shown below.  
<% } %>
</p>

<% 
for( ArticleEntry e: sr.entries) { %><%= main.resultsDivHTML(e) %><% }%>
<%= main.prevNextLinks(sr) %> 

<% } %>
<jsp:include page="index-catlist.html" />
	</div>
		<!-- Right Frame -->
	
		<!-- #EndEditable -->	
	<!-- /div --> <!-- Wrapping -->	
	<!-- /div --> <!-- Middle frame ends-->

<!-- #BeginEditable "Scripts" -->
<!-- #EndEditable -->

</body>

<!-- #EndTemplate -->

</html>
