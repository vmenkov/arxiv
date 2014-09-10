<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>

<%@ page import="edu.rutgers.axs.web.*" %>
<!-- %@ page import="edu.rutgers.axs.sql.*" % -->
<!-- %@ page import="edu.rutgers.axs.recommender.*" % -->
<%@ page import="edu.rutgers.axs.html.*" %>

<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	SessionBased main=new SessionBased(request,response);
	SearchResults sr = main.sr;
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
<title>My.ArXiv quick recommendations</title>
<%   if (main.wantReload) {      %>
<!-- reload the page after a few seconds, hoping that the list is generated -->
<meta http-equiv="refresh" content="5">
<%   }  %>


<!-- script type="text/javascript">
</script -->
<!-- styles_all.css controls appearance ; we use a somewhat abbreviated
     styles_sb.css instead -->
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_sb.css"/>

<!-- Scripts needed for RatingButtons to work -->
<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="scripts/buttons_control.js"></script>
<script src="scripts/jquery-1.10.2.js"></script>
<script src="scripts/jquery-ui-1.10.4.custom.js"></script>
<!-- script src="scripts/dragging.js"></script -->
<script>
// Some advice (on "stop" instead of "change") from here : 
// http://stackoverflow.com/questions/6564316/jquery-ui-sortable-toarray-skips-1-item
 $(function() {
	    $( "#sortable" ).sortable({stop: function( event, ui ) {
			var sortedIDs = $("#sortable").sortable("toArray");
			var articles = sortedIDs.join(':');		
			$.get('<%=main.urlReorderPrefix()%>' + articles);
		    }});
	    $( "#sortable" ).disableSelection();
	    $( "#sortable" ).on( "sortchange", function( event, ui ) {} );

	});
</script>

<!-- End Scripts -->

<!-- results.css does not seem to matter -->
<!-- link rel="stylesheet" type="text/css" href="styles/results.css" / -->
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<link rel="stylesheet" href="scripts/jquery-ui-1.10.4.custom.css" />
</head>
<body>
<h1>Session-based recommendations</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (sr==null || sr.entries.size()==0) {      %>
<p>No recommendations available yet.
<%   } else {      %>
<p>

<p>Based on the articles you have seen so far, we think that you may be interested in some of the following <%= sr.entries.size()%> articles.
<% if (main.user==null) { %> 
If you enjoy Session-Based Recommendations, please 
<a name="register" title="Sign up with My.ArXiv!"
onclick="javascript:window.opener.location.href='participation.jsp?code=SET_BASED_o1yaw7gslplj';">sign-up</a> to preserve your suggestions and gain additional options! (Or <a name="register" title="Sign up with My.ArXiv!"
onclick="javascript:window.opener.location.href='login2.jsp';">log in</a>, if
you are already registered). 
<% } %>

The list below well automatically update as you visit more pages. As
articles get older, they will turn darker grey over time.  You can
reorder the list by dragging article entries up or down.
</p>

<% double largest = 0; 
   for(ArticleEntry e: sr.entries) {
      if(e.score > largest) { largest = e.score; }   
  }  %>
<table><tr><td>
<div class="result" style="width:85px;"> <div class="document"><table style="width:100%;text-align:center;"><tr><td>Relevance</td></tr>
</table>
</div>
</div> 
<td style="width:100%"><div class="result" id="result">
<div class="document">
<table style="width:100%;text-align:center;"><tr><td>
Article Suggestions</td></tr>
</table>
</div>
</div>
</td>
</tr>
</table>
<ul id="sortable">
<%
   for(ArticleEntry e: sr.entries) {
%>
<%= main.resultsDivHTMLLite(e, largest) %>	
 <%  }  %>

<%   }   %>
</ul>

<hr>

<p>
<form method=post>
<input type="button" value="Close"
title="Close this window. Your browsing history will be remembered, and this window will reappear with updated recommendatin after you view more articles."
onclick="window.close()">

<input type="button" value="Change focus"
title="Close this window. Your previous browsing history will be forgotten; once you view more articles, this window will reapper with new recommendations."
onclick="javascript:window.opener.location.href='LogoutServlet?stay=ON&redirect=<%=main.sd.sbrg.encodedChangeFocusURL()%>'; window.close()">
</form>

<hr>
<small>
<%= main.researcherDiv(main.infomsg, main.sd.sbrg.researcherSB) %>
</small>


</body>
</html>
