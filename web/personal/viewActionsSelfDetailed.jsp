<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewActions main=new ViewActions(request, response, true);
   main.loadArticleInfo();
   User actor = main.actor;
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Interaction history for user <%= main.actorUserName %>
</title>


<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_all.css" />
<link rel="stylesheet" type="text/css" href="../styles/results.css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />

<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="../scripts/blur.js"></script>
<script type="text/javascript" src="../scripts/buttons_control.js"></script>


</head>
<body>

<h1>Page interaction history for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>

<p>This list contains the list of your actions (article views,
ratings, etc) on My.ArXiv, in reverse chronological order (most recent
first). All actions, even clicking the "Don't show this article again" button are shown here, for your complete reference.
</p>

	 <!-- Links to prev/next pages, if necessary -->
<p>
<% if (main.needPrev) {	%> 
<a href="<%= main.repageUrl(main.prevstart)%>">[PREV PAGE (LATER)]</a> 
<% } else { %> [THIS IS THE TOP OF THE LIST] <% } 	
 if (main.needNext) { %>	
<a href="<%= main.repageUrl(main.nextstart)%>">[NEXT PAGE (EARLIER)]</a>
<% }	 %>
</p>

<p>
<%
	for(int i=0; i<main.entries.size(); i++) {
	ArticleEntry e = main.entries.elementAt(i);
%>
<%= main.resultsDivHTML(e) %>	
<% } %>
</p>

	 <!-- Links to prev/next pages, if necessary -->
<p>
<% if (main.needPrev) {	%> 
<a href="<%= main.repageUrl(main.prevstart) %>">[PREV PAGE (LATER)]</a> 
<% }
 if (main.needNext) { %>	
<a href="<%= main.repageUrl(main.nextstart) %>">[NEXT PAGE (EARLIER)]</a>
<% } else { %> [THIS IS THE END OF THE LIST] <% } 	%>
</p>


<!--
<h2>Queries recorded</h2>

<table border=1>
 	<%= Reflect.htmlHeaderRow(EnteredQuery.class, true) %>
<%	
	for(int i=0; i<main.qlist.size(); i++) {
	EnteredQuery a =  main.qlist.elementAt(i);
%>
-->
	<!-- %= Reflect.htmlRow(a, true, false) % -->
<!--
 <%  }  %>
</table></p>
-->

<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>