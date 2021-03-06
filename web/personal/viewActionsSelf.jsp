<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewActions main=new ViewActions(request, response, true);
   User actor = main.actor;
%>

<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Interaction history for user <%= main.actorUserName %>
</title>

<script type="text/javascript">
<%= main.headJS() %>
</script>
</head>

<body onload="<%=main.onLoadJsCode()%>" >

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
	<%= Reflect.htmlRow(a, true, false) %>
 <%  }  %>
</table></p>

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