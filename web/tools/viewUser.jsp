<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewObject main=new ViewObject(request,response, User.class);
   User li = (User)main.li;
%>

<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>User's data
</title>
</head>
<body>


<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<h1>Error</h1>
<%   } else {      %>
<p>
<h1>User  <em><%= li.getId() %></em></h1>

<h2>Summary</h2>
<p>
<table border=1>
<%= Reflect.htmlHeaderRow(li.getClass(), true) %>
<%= Reflect.htmlRow(li, true) %>
</table></p>


<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>