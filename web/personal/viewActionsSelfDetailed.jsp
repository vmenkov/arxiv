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
<<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Interaction history for user <%= main.actorUserName %>
</title>


<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_ie.css" /><![endif]-->
<link rel="stylesheet" type="text/css" href="../styles/results.css" />
<!--[if IE]>
<link rel="stylesheet" type="text/css" href="../styles/results_ie.css" />
<![endif]-->
<link rel="icon" type="image/x-icon" href="../favicon.ico" />

<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="../scripts/blur.js"></script>
<script type="text/javascript" src="../scripts/buttons_control.js"></script>




</head>
<body>

<h1>Interaction history for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>

<h2>Page interactions recorded</h2>

<%
	for(int i=main.list.size()-1; i>=0; i--) {
	Action a =  main.list.elementAt(i);
	ArticleEntry e = main.entries.elementAt(i);
%>
<%= main.resultsDivHTML(e) %>	
<% } %>


<h2>Queries recorded</h2>

<table border=1>
 	<%= Reflect.htmlHeaderRow(EnteredQuery.class, true) %>
<%	
	for(int i=0; i<main.qlist.size(); i++) {
	EnteredQuery a =  main.qlist.elementAt(i);
%>
	<%= Reflect.htmlRow(a, true, false) %>
 <%  }  %>
</table></p>

<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>