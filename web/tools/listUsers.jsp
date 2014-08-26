<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ListUsers main=new ListUsers(request,response);
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>List of Users</title>
</head>
<body>

<h1>List of Users</h1>

<p>Listed below are all users of the system. Passwords are encrypted.</p>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>
<table border=1>
<tr>
<th></th>
<%= User.header4cells()  %> 
<th>View user's activity</th>
</tr>
</tr>
<%	
	for(int i=0; i<main.list.size(); i++) {
	User u =  main.list.elementAt(i);
%><tr>
<td><%= i+1 %>
<%= u.to4cells()  %>
<td>
<a href="viewActions.jsp?user_name=<%=u.getUser_name()%>">All activity</a>
<br>
<a href="SBStatsServlet?user_name=<%=u.getUser_name()%>">SB stats</a>
</td>
</tr>
<%  }  %>
</table></p>
<hr>
<p>System message: <%= main.infomsg%> </p>
<% }     %>

<icd:RU/>

</body>
</html>