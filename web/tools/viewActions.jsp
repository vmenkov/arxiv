<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewActions main=new ViewActions(request,response, false);
   User actor = main.actor;
%>

<html>
<head>
<link rel="stylesheet" type="text/css" media="screen" href="../styles/survey.css">
<title>Interaction history for user <%= main.actorUserName %>
</title>
</head>
<body>

<h1>Interaction history for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>

<h2>Page actions recorded</h2>

<table border=1>
 	<%= Reflect.htmlHeaderRow(Action.class, true) %>
<%	
	for(int i=0; i<main.list.size(); i++) {
	Action a =  main.list.elementAt(i);
%>
	<%= Reflect.htmlRow(a, true) %>
 <%  }  %>
</table></p>

<h2>Queries recorded</h2>

<table border=1>
 	<%= Reflect.htmlHeaderRow(EnteredQuery.class, true) %>
<%	
	for(int i=0; i<main.qlist.size(); i++) {
	EnteredQuery a =  main.qlist.elementAt(i);
%>
	<%= Reflect.htmlRow(a, true) %>
 <%  }  %>
</table></p>

<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>