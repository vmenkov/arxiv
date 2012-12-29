<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewObject main=new ViewObject(request,response);
   OurTable li = main.li;
%>

<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Object view
</title>
</head>
<body>


<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<h1>Error</h1>
<%   } else {      %>
<p>
<h1>Object view</h1>

<p>Class <%= li.getClass().getName() %>, id = <em><%= li.getLongId() %></em></p>

<h2>Summary</h2>
<p>
<table border=1>
<%= Reflect.htmlHeaderRow(li.getClass(), true) %>
<%= Reflect.htmlRow(li, true) %>
</table></p>

<p>
<% if (li instanceof Invitation) { %>
<a href="../admin/editInvitationForm.jsp?id=<%=li.getLongId()%>">
Edit this invitation</p></a>
<% } %>

<hr> <p><small>System message: <%= main.infomsg%> </small></p> 
<% } %>

<icd:RU/>

</body>
</html>