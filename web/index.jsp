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
   ViewSuggestionsBase main = ViewSuggestionsBase.getMainPageSuggestions(request,response);
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

<!-- Use "simple_scroll" to get rid of frames moving within frames,
and other "cleverness".  --> 

<!-- link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" /--> 

<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all_simple_scroll.css" />

<link rel="icon" type="image/x-icon" href="favicon.ico" />
<script type="text/javascript" src="_technical/scripts/jquery.js"></script>

<!-- The script jquery-transitions.js is commented out, because it causes a problem in 
  Firefox: index.jsp#anchor does not work properly. See discussion e.g. at
  https://support.mozilla.org/en-US/questions/929028 -->
<!-- script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script -->
<script type="text/javascript" src="scripts/blur.js"></script>
<script type="text/javascript" src="scripts/buttons_control.js"></script>

<!-- Code for Moveable Session Buddy
<% 	    if (main.needSBNow()) { %>

<script src="scripts/jquery-1.9.1.js"></script>
<script src="scripts/jquery-ui.js"></script>
<link rel="stylesheet" href="http://code.jquery.com/ui/1.10.4/themes/smoothness/jquery-ui.css">

<script>
function newPopup(url) {
		popupWindow = window.open(
		url,'popUpWindow','height=700,width=800,left=10,top=10,resizable=yes,scrollbars=yes,toolbar=yes,menubar=no,location=no,directories=no,status=yes');

window.parent.document.getElementById("drag").style.display='none';
window.parent.document.getElementById("iframe6").style.display='block';

}
</script>

<IFRAME NAME="iframe6" ID="iframe6" STYLE="width:40px;height:50px;position:fixed;bottom:5px;right:5px;"
SRC="min.html"></IFRAME>

<DIV name = "drag" id = "drag" class = "ui-widget-content" STYLE="width:295px;height:400px;position:fixed;bottom:5px;right:5px;
background-color:#FBF2EF;border:1px solid black;z-index:99;">
<TABLE ID="toolbar" BORDER="0" WIDTH="100%" BGCOLOR="red" HEIGHT="10"><TR><TD>&nbsp;</TD></TR></TABLE>
	<IFRAME NAME="iframe3" ID="iframe3" STYLE="width:80%;height:70%;position:relative;top:10px;left:27px;" 			SRC="sessionBased.jsp?popout=false"></IFRAME>
	<button onClick="JavaScript:newPopup('sessionBased.jsp?popout=true');" NAME="POPOUT" 				      	STYLE="position:relative;top:5%;left:40%;"> Popout </button>
</DIV>

<SCRIPT> 
$(function() {
$('#drag').draggable({iframeFix:true});
$('#drag').resizable({
maxWidth: 370,
minWidth: 100,
minHeight: 100
});
});

window.document.getElementById("iframe6").style.display='none'; 
document.getElementById("iframe6").style.zIndex="99";
</SCRIPT>

<% } %>	

End of Session Buddy Code -->




<!-- #BeginEditable "Styles" -->
<link rel="stylesheet" type="text/css" href="styles/index.css" />
<!-- ***  [if IE]>
<link rel="stylesheet" type="text/css" href="styles/index-ie.css" />
<[endif] ***  -->
<!-- script type="text/javascript">
function StartScripts() { BlurLinks();}
window.onload = StartScripts;
</script -->

<!-- this script is here so that we can have an "open SB" button -->
<script type="text/javascript" src="/arxiv/scripts/filterServletSB.js"></script>

<script type="text/javascript">
<%= main.headJS() %>
</script>


<!-- #EndEditable -->
</head>

<body onload="<%=main.onLoadJsCode()%>">

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

      <a class="button" href="personal/viewFolder.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Your personal folder&nbsp;<span id="folderSize">(<%=main.folderSize%>)</span></a>

     <a class="button" href="personal/viewActionsSelfDetailed.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Past activity</a>

      <a class="button" href="personal/editUserFormSelf.jsp"><span style="color:#ee0000">&nbsp;&diams;&nbsp;</span>Your account settings</a>

<%  if (main.runByResearcher()) {%>
    <span class="researcher">
    <a href="tools/"><span>&nbsp;&diams;&nbsp;</span>
    Research tools (staff only)</a>
    </span>

    <br>
    <span class="researcher">
    <a href="tools/"><span>&nbsp;&diams;&nbsp;</span>
    <a onclick="javascript:window.onload=openSBMovingPanelNow('<%=main.cp%>');">
    Show Session-Based suggestions (in a pop-up window)</a>
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

      <div style="text-align: center">My.ArXiv Version <%=Version.getInfo()%></div>

       <div style="text-align: center"><strong>This project is supported by the National Science Foundation:<br>
 (#NSF IIS-1142251)<br>
 (BIGDATA: Mid-Scale: ESCE: Collaborative Research: Discovery and Social Analytics for Large-Scale Scientific Literature. Award Number:1247696)
</strong></div>

    </div>
    <!-- Left Frame -->

    <div id="right_frame">
    <% if (main.error) { %>
    <p>Error message: <%= main.errmsg %></p> 
    <p><pre> <%= main.stackTrace() %></pre></p> 
     <% } else if (main.user==null) { %> <!-- Not logged in -->
    <% } else { 
  	  SearchResults sr = main.sr; 
%>
<h2><a name="sug">Recent articles recommended for you</a></h2>
<%= (main.noList()) ? main.noListMsg() : main.describeList() %>
<% for( ArticleEntry e: sr.entries) { %><%= main.resultsDivHTML(e) %><% }%>
<%= main.prevNextLinks(sr) %> 
<%= main.excludedPagesMsg() %>
<%= main.researcherP( main.actor.dayMsg()) %>

<%  } %>
<jsp:include page="index-catlist.html" />

<small><%= main.researcherP( main.infomsg) %></small>

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
